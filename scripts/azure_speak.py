#!/usr/bin/env python3
"""Launch or verify Speak with Azure credentials held only in process memory."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import httpx

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('mode', choices=['run', 'verify'])
parser.add_argument('--cold-start', action='store_true',
                    help='Wait for zero replicas before Speak sends its first scoring request')
args = parser.parse_args()
if args.cold_start and args.mode != 'verify':
    parser.error('--cold-start requires verify mode')
root = Path(__file__).resolve().parents[1]
resource_args = ['-g', 'minimalpairs-scorer-rg', '-n', 'minimalpairs-scorer']

def azure(*command):
    attempts = 3 if 'keys' in command else 1
    for attempt in range(attempts):
        result = subprocess.run(['az', *command, '--only-show-errors', '-o', 'json'],
                                capture_output=True, text=True)
        if not result.returncode:
            return json.loads(result.stdout)
        if attempt + 1 < attempts:
            print('Azure key management is not ready; retrying.', flush=True)
            time.sleep(5)
    # Do not expose command output which might include credential material.
    sys.exit('Azure configuration lookup failed. Check az login and access to minimalpairs-scorer-rg.')

app = azure('containerapp', 'show', *resource_args)
base = 'https://' + app['properties']['configuration']['ingress']['fqdn']
print(f'Connecting to {base}; a scaled-to-zero host may take time to start.', flush=True)
# An HTTP request wakes a scaled-to-zero host before key management needs a replica.
response = httpx.get(base + '/health', timeout=180)
if response.status_code != 401:
    sys.exit(f'Azure host not ready or key protection missing (HTTP {response.status_code}).')
keys = azure('containerapp', 'function', 'keys', 'list', *resource_args,
             '--key-type', 'hostKey')['value']['keys']
key = next(item['value'] for item in keys if item['name'] == 'default')
response = httpx.get(base + '/health', headers={'x-functions-key': key}, timeout=180)
response.raise_for_status()
assert response.json()['status'] == 'ok'
if args.cold_start:
    # Keep the key in memory and use management-plane reads only while waiting.
    # No health request may warm the host between zero replicas and the UI test.
    revision = azure('containerapp', 'show', *resource_args)['properties']['latestReadyRevisionName']
    started = time.monotonic()
    observations = []
    while True:
        replicas = azure('containerapp', 'replica', 'list', *resource_args, '--revision', revision)
        elapsed = round(time.monotonic() - started, 2)
        observations.append({'seconds_since_health': elapsed, 'replicas': len(replicas)})
        print(f'Waiting for scale-to-zero: {len(replicas)} replicas after {elapsed}s', flush=True)
        if not replicas:
            break
        if elapsed > 600:
            sys.exit('Backend did not scale to zero within 10 minutes; cold-start verification aborted.')
        time.sleep(15)
    report = root / 'composeApp/build/reports/articulator-azure/cold-start.json'
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps({'revision': revision, 'observations': observations,
                                 'zero_replicas_before_speak': True}, indent=2) + '\n')
child_env = os.environ.copy()
child_env['MINIMAL_PAIRS_SCORER_URL'] = base
child_env['MINIMAL_PAIRS_SCORER_KEY'] = key
# Azure verification uses bundled PCM only, not previously retained private WAVs.
child_env.pop('MINIMAL_PAIRS_MIC_REPLAY_WAV', None)
child_env.pop('MINIMAL_PAIRS_PHYSICAL_MIC', None)
command = [str(root / 'gradlew'), '--no-daemon']
if args.mode == 'verify':
    child_env['MINIMAL_PAIRS_LIVE_SCORING'] = '1'
    child_env['MINIMAL_PAIRS_LIVE_SCORER_EXPECTED_URL'] = base
    child_env['MINIMAL_PAIRS_LIVE_REPORT_DIR'] = str(root / 'composeApp/build/reports/articulator-azure')
    command += [':composeApp:jvmTest', '--rerun', '--tests', '*LiveSpeakScoringTest']
else:
    child_env.pop('MINIMAL_PAIRS_LIVE_SCORING', None)
    command += [':composeApp:run']
print(f'{args.mode.capitalize()} Speak using {base} (key not displayed or saved)', flush=True)
os.chdir(root)
os.execvpe(command[0], command, child_env)
