#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
backend="${1:?Pass a directory containing the extracted pinned upstream-source.tar.gz}"
task_root="$(cd "$(dirname "$0")/../.." && pwd)"
# Refuse modified input so the provenance manifest cannot name a different revision.
python3 - "$backend" "$task_root/third_party/vocaltractlab/upstream-source.tar.gz" <<'PY'
from pathlib import Path
import sys
import tarfile
backend = Path(sys.argv[1])
with tarfile.open(sys.argv[2]) as archive:
    for entry in archive.getmembers():
        if entry.isfile():
            path = backend / entry.name
            if not path.is_file() or path.read_bytes() != archive.extractfile(entry).read():
                raise SystemExit(f'Pinned source mismatch: {entry.name}')
PY
mkdir -p "$task_root/build/vtl-tools" "$task_root/design/vocaltractlab-preview/meshes"
clang++ -std=c++17 -O2 -Wno-return-stack-address -Wno-deprecated-declarations \
  -I"$backend/include" "$task_root/scripts/vtl/export_meshes.cpp" \
  "$backend"/src/VocalTractLabBackend/*.cpp -o "$task_root/build/vtl-tools/export_meshes"
"$task_root/build/vtl-tools/export_meshes" "$backend/resources/JD3.speaker" \
  "$task_root/design/vocaltractlab-preview/meshes"
python3 "$task_root/scripts/vtl/render_preview.py"
python3 "$task_root/scripts/vtl/package_assets.py"
