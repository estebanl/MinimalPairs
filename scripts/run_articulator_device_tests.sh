#!/usr/bin/env bash
# Drive the already-installed app with a separate XCTest runner; never reset app data.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE_ID="${1:?Usage: scripts/run_articulator_device_tests.sh DEVICE_UDID [xcodebuild test options]}"
shift
cd "$ROOT_DIR"
TEAM_ID=$(awk -F= '/^TEAM_ID=/{print $2; exit}' iosApp/Configuration/Config.xcconfig)
if [[ -z "$TEAM_ID" ]]; then
  echo 'TEAM_ID is missing from iosApp/Configuration/Config.xcconfig' >&2
  exit 1
fi
RUN_ROOT="$ROOT_DIR/build/ArticulatorDeviceTests"
RUN_STAMP=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$RUN_ROOT"
cp scripts/ios-camera-tests/project.yml scripts/ios-camera-tests/ArticulatorDeviceTests.swift "$RUN_ROOT/"
xcodegen generate --spec "$RUN_ROOT/project.yml"
xcodebuild test \
  -project "$RUN_ROOT/ArticulatorDeviceTests.xcodeproj" \
  -scheme ArticulatorDeviceTests \
  -destination "platform=iOS,id=$DEVICE_ID" \
  -derivedDataPath "$RUN_ROOT/DerivedData" \
  -resultBundlePath "$RUN_ROOT/$RUN_STAMP.xcresult" \
  -allowProvisioningUpdates "DEVELOPMENT_TEAM=$TEAM_ID" \
  -test-timeouts-enabled YES -maximum-test-execution-time-allowance 600 "$@" \
  2>&1 | tee "$RUN_ROOT/$RUN_STAMP.log"
