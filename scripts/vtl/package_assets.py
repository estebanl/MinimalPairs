"""Package reproducible VocalTractLab geometry, source, and attribution."""
# SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'third_party/vocaltractlab'
PREVIEW = ROOT / 'design/vocaltractlab-preview'
DEST = ROOT / 'composeApp/src/commonMain/composeResources/files/vtl'

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

DEST.mkdir(parents=True, exist_ok=True)
CONSONANTS = ('bilabial-closure', 'alveolar-closure', 'velar-closure',
              'labiodental-fricative', 'dental-fricative', 'alveolar-fricative',
              'postalveolar-fricative', 'postalveolar-closure', 'alveolar-lateral')
files = {}
for name in ('dress', 'fleece', 'foot', 'goose', 'kit', 'thought', 'trap', 'palm', 'strut', 'lot') + CONSONANTS:
    path = PREVIEW / 'meshes' / f'{name}.vtlm'
    data = path.read_bytes()
    assert data[:8] == b'VTLM\x01\x00\x00\x00'
    files[path.name] = {'bytes': len(data), 'sha256': digest(path)}
    shutil.copyfile(path, DEST / path.name)
assert len({entry['sha256'] for entry in files.values()}) == 19, 'Exported gestures must be distinct; phonemes may share a gesture'
recipes = json.loads((PREVIEW / 'meshes' / 'derived-poses.json').read_text())
manifest = {
    'upstream': 'https://github.com/TUD-STKS/VocalTractLabBackend-dev',
    'commit': 'df30392f18dc5e175b577c3ba734caaa65a3927f',
    'speaker': 'resources/JD3.speaker',
    'speaker_sha256': digest(SOURCE / 'JD3.speaker'),
    'source_archive_sha256': digest(SOURCE / 'upstream-source.tar.gz'),
    'exporter_sha256': digest(ROOT / 'scripts/vtl/export_meshes.cpp'),
    'license': 'GPL-3.0-or-later (upstream source; no separate asset exception found)',
    'format_version': 1,
    'files': files,
    'derived_teaching_poses': recipes,
    'consonant_teaching_poses': json.loads((PREVIEW / 'meshes' / 'consonant-poses.json').read_text()),
    'consonant_pose_status': 'Original JD3 articulatory snapshots in a-context; unreviewed English references, not word-synchronized motion or visible voicing',
    'derived_pose_status': 'Unvalidated illustrative parameter recipes, not exact English presets or learner anatomy',
}
for folder in (PREVIEW, DEST):
    (folder / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
for source, target in (
    (SOURCE / 'COPYING', 'COPYING.txt'),
    (SOURCE / 'README.md', 'NOTICE.md'),
    (SOURCE / 'JD3.speaker', 'JD3.speaker'),
    (SOURCE / 'upstream-source.tar.gz', 'upstream-source.tar.gz'),
):
    shutil.copyfile(source, DEST / target)
for name in ('export_meshes.cpp', 'regenerate.sh', 'package_assets.py', 'render_preview.py'):
    shutil.copyfile(ROOT / 'scripts/vtl' / name, DEST / name)
print(f'Packaged {len(files)} distinct meshes and their source in {DEST.relative_to(ROOT)}')
