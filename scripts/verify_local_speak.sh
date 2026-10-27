#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export MINIMAL_PAIRS_SCORER_URL=http://127.0.0.1:8000
export MINIMAL_PAIRS_LIVE_SCORING=1
cd "$project_dir"
exec ./gradlew :composeApp:jvmTest --rerun --tests '*LiveSpeakScoringTest'
