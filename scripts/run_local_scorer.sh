#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scorer_python="${SCORER_PYTHON:-$project_dir/build/phoneme-model-venv/bin/python}"
export PHONEME_MODEL_DIR="${PHONEME_MODEL_DIR:-$project_dir/build/models/phoneme-ctc}"
export PHONEME_MODEL_ID="${PHONEME_MODEL_ID:-facebook/wav2vec2-xlsr-53-espeak-cv-ft@2c733782}"
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1
export PYTHONDONTWRITEBYTECODE=1
if [[ ! -x "$scorer_python" || ! -f "$PHONEME_MODEL_DIR/vocab.json" ]]; then
    echo "Missing local Python environment or phoneme model. See docs/ARTICULATOR_SCORING.md." >&2
    exit 1
fi
cd "$project_dir/backend"
exec "$scorer_python" -m uvicorn app.main:app --host 127.0.0.1 --port "${SCORER_PORT:-8000}"
