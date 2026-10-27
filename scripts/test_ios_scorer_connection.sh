#!/usr/bin/env bash
# Run Swift configuration regression tests without accessing real Keychain items.
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
test_dir="$project_dir/build/scorer-connection-tests"
mkdir -p "$test_dir"
xcrun --sdk macosx swiftc \
    -module-cache-path "$test_dir/module-cache" \
    iosApp/iosApp/ScorerConnection.swift scripts/tests/ScorerConnectionTest.swift \
    -o "$test_dir/run"
"$test_dir/run"
