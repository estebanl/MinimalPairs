#!/usr/bin/env python3
"""Audit a docker-save archive without logging credential values.

Checks every saved filesystem layer (including files hidden by later layers),
image metadata, expected application file paths, and exact current deployment
credentials. Run Trivy's secret scanner separately for general secret patterns.
"""
import argparse
import json
from pathlib import Path
import subprocess
import tarfile
import httpx
import hashlib
import io

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('archive', type=Path)
parser.add_argument('--report', type=Path, required=True)
args = parser.parse_args()

def command_json(command, input=None):
    result = subprocess.run(command, input=input, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('Credential lookup failed; output withheld')
    return json.loads(result.stdout)

secrets = []
for server in ('https://index.docker.io/v1/', 'https://index.docker.io/v1/access-token',
               'https://index.docker.io/v1/refresh-token'):
    credential = command_json(['docker-credential-desktop', 'get'], server + '\n')
    if credential.get('Secret'):
        secrets.append(('Docker Hub credential', credential['Secret'].encode()))
deployment = command_json(['az', 'containerapp', 'show', '-g', 'minimalpairs-scorer-rg',
    '-n', 'minimalpairs-scorer', '--only-show-errors', '-o', 'json'])
hostname = deployment['properties']['configuration']['ingress']['fqdn']
response = httpx.get('https://' + hostname + '/health', timeout=180)
assert response.status_code == 401, 'Expected key-protected Azure host'
keys = command_json(['az', 'containerapp', 'function', 'keys', 'list', '-g',
    'minimalpairs-scorer-rg', '-n', 'minimalpairs-scorer', '--key-type', 'hostKey',
    '--only-show-errors', '-o', 'json'])
for key in keys['value']['keys']:
    secrets.append(('Azure Function host key', key['value'].encode()))
assert secrets and all(len(value) >= 16 for _, value in secrets)

findings = []
public_fixtures = json.loads(Path(__file__).with_name('public_scorer_fixtures.json').read_text())
verified_fixtures = []
files = 0
byte_count = 0
app_paths = set()
model_paths = set()
expected_app = {'host.json', 'function_app.py', 'app/main.py', 'app/ctc_scorer.py', 'app/ctc_evaluation.py'}

def scan_stream(stream, location):
    global byte_count
    overlap = max(len(value) for _, value in secrets) - 1
    tail = b''
    while block := stream.read(1024 * 1024):
        byte_count += len(block)
        data = tail + block
        for label, value in secrets:
            if value in data:
                findings.append({'location': location, 'type': label})
        tail = data[-overlap:]

with tarfile.open(args.archive) as archive:
    manifest = json.load(archive.extractfile('manifest.json'))
    assert len(manifest) == 1
    image = manifest[0]
    config_file = archive.extractfile(image['Config'])
    scan_stream(config_file, 'image configuration/history')
    for layer_name in image['Layers']:
        with tarfile.open(fileobj=archive.extractfile(layer_name), mode='r|*') as layer:
            for member in layer:
                path = member.name.lstrip('./')
                if not member.isfile():
                    continue
                files += 1
                if path.startswith('home/site/wwwroot/'):
                    relative = path.removeprefix('home/site/wwwroot/')
                    app_paths.add(relative)
                    if relative not in expected_app:
                        findings.append({'location': path, 'type': 'Unexpected application file'})
                if path.startswith('opt/phoneme-model/'):
                    model_paths.add(path.removeprefix('opt/phoneme-model/'))
                    if '/.cache/' in path:
                        findings.append({'location': path, 'type': 'Download cache included'})
                if path.startswith(('root/.azure/', 'root/.docker/', 'root/.ssh/', 'Users/')) or path.endswith(('local.settings.json', '.env', '.wav', '.mp3')):
                    fixture = public_fixtures.get(path)
                    fixture_bytes = layer.extractfile(member).read() if fixture else None
                    if fixture and hashlib.sha256(fixture_bytes).hexdigest() == fixture['sha256']:
                        verified_fixtures.append(path)
                    else:
                        findings.append({'location': path, 'type': 'Potential private configuration or recording'})
                    # Streaming tar members cannot be reread after checking the hash.
                    if fixture:
                        scan_stream(io.BytesIO(fixture_bytes), f'{layer_name}:{path}')
                        continue
                scan_stream(layer.extractfile(member), f'{layer_name}:{path}')
assert expected_app <= app_paths, 'Missing required backend files'
assert {'config.json','vocab.json','pytorch_model.bin','notices/LICENSE-2.0.txt','notices/MODEL_CARD.md'} <= model_paths
report = {'archive':str(args.archive), 'layers':len(image['Layers']), 'files_scanned':files,
          'bytes_scanned':byte_count, 'known_credentials_checked':len(secrets),
          'application_files':sorted(app_paths), 'model_files':sorted(model_paths),
          'verified_public_fixtures':verified_fixtures, 'findings':findings, 'passed':not findings}
args.report.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
if findings:
    raise SystemExit('Public-image audit failed; do not publish')
