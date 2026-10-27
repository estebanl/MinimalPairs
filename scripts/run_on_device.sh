#!/usr/bin/env bash
# ==============================================================================
# run_on_device.sh
#
# Builds, installs, and launches Minimal Pairs on a connected iOS device
# (defaults to "esteban.lopez") with Azure phoneme scoring configured at launch.
#
# Usage:
#   ./scripts/run_ios.sh [OPTIONS]
#
# Options:
#   -d, --device <name|udid>       Device name or UDID (default: "esteban.lopez")
#   -c, --configuration <config>   Build configuration: Debug or Release (default: Debug)
#   --skip-build                   Skip xcodebuild; install and launch existing app
#   --clean                        Clean build artifacts before building
#   --console                      Attach console to stream app stdout/stderr
#   --azure-scorer                 Configure Azure scoring at launch (default; requires az login)
#   --no-azure-scorer              Use the saved connection or caller-provided scorer variables
#   -h, --help                     Show this help message
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

# ------------------------------------------------------------------------------
# Colors for logging
# ------------------------------------------------------------------------------
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

info()    { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ------------------------------------------------------------------------------
# Defaults & Configurations
# ------------------------------------------------------------------------------
DEFAULT_DEVICE="esteban.lopez"
TARGET_DEVICE="$DEFAULT_DEVICE"
CONFIGURATION="Debug"
SKIP_BUILD=false
CLEAN_BUILD=false
ATTACH_CONSOLE=false
AZURE_SCORER=true

PROJECT_PATH="$ROOT_DIR/iosApp/iosApp.xcodeproj"
SCHEME="iosApp"
XCCONFIG_PATH="$ROOT_DIR/iosApp/Configuration/Config.xcconfig"
DERIVED_DATA_PATH="$ROOT_DIR/build/DerivedData"

# Resolve Bundle Identifier from Config.xcconfig if possible
BUNDLE_ID="com.estebanl.minimalpairs"
if [[ -f "$XCCONFIG_PATH" ]]; then
  EXTRACTED_BUNDLE_ID=$(grep '^PRODUCT_BUNDLE_IDENTIFIER=' "$XCCONFIG_PATH" | cut -d'=' -f2 | tr -d ' ' || true)
  if [[ -n "$EXTRACTED_BUNDLE_ID" ]]; then
    BUNDLE_ID="$EXTRACTED_BUNDLE_ID"
  fi
fi

# ------------------------------------------------------------------------------
# Parse CLI arguments
# ------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    -d|--device)
      TARGET_DEVICE="$2"
      shift 2
      ;;
    -c|--configuration)
      CONFIGURATION="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    --clean)
      CLEAN_BUILD=true
      shift
      ;;
    --console)
      ATTACH_CONSOLE=true
      shift
      ;;
    --azure-scorer)
      AZURE_SCORER=true
      shift
      ;;
    --no-azure-scorer)
      AZURE_SCORER=false
      shift
      ;;
    -h|--help)
      sed -ne '/^#/!q;s/^# //;2,$p' "$0"
      exit 0
      ;;
    *)
      error "Unknown option: $1"
      echo "Use --help to view available options."
      exit 1
      ;;
  esac
done

# ------------------------------------------------------------------------------
# Pre-flight checks
# ------------------------------------------------------------------------------
info "Running pre-flight checks..."

if ! command -v xcodebuild >/dev/null 2>&1; then
  error "'xcodebuild' not found. Please install Xcode Command Line Tools."
  exit 1
fi

if ! xcrun devicectl --version >/dev/null 2>&1; then
  error "'devicectl' not found. Xcode 15+ is required."
  exit 1
fi

# Ensure OpenJDK 17 is found for Kotlin Multiplatform Gradle build task
if [[ -z "${JAVA_HOME:-}" ]]; then
  if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    info "Set JAVA_HOME to $JAVA_HOME"
  elif /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home)"
    info "Set JAVA_HOME to $JAVA_HOME"
  fi
fi

# ------------------------------------------------------------------------------
# Resolve Device & Verify Connectivity
# ------------------------------------------------------------------------------
info "Looking up device: ${BOLD}${TARGET_DEVICE}${NC}..."

DEVICE_UDID=""
# If TARGET_DEVICE matches UDID pattern directly:
if [[ "$TARGET_DEVICE" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}$ ]] || [[ "$TARGET_DEVICE" =~ ^[0-9A-Fa-f]{40}$ ]]; then
  DEVICE_UDID="$TARGET_DEVICE"
else
  # Query devicectl device list
  DEVICE_UDID=$(xcrun devicectl list devices 2>/dev/null | grep "$TARGET_DEVICE" | awk '{print $2}' | head -n 1 || true)
fi

if [[ -z "$DEVICE_UDID" ]]; then
  error "Could not find connected device matching '$TARGET_DEVICE'."
  echo "Available devices from 'xcrun devicectl list devices':"
  xcrun devicectl list devices || true
  exit 1
fi

info "Resolved device: ${BOLD}${TARGET_DEVICE}${NC} (UDID: ${DEVICE_UDID})"

APP_BUNDLE_PATH="$DERIVED_DATA_PATH/Build/Products/${CONFIGURATION}-iphoneos/MinimalPairs.app"

# ------------------------------------------------------------------------------
# Build Phase
# ------------------------------------------------------------------------------
if [[ "$SKIP_BUILD" = false ]]; then
  if [[ "$CLEAN_BUILD" = true ]]; then
    info "Cleaning build directory ($DERIVED_DATA_PATH)..."
    rm -rf "$DERIVED_DATA_PATH"
  fi

  info "Building ${SCHEME} (${CONFIGURATION}) for destination id=${DEVICE_UDID}..."
  xcodebuild -project "$PROJECT_PATH" \
    -scheme "$SCHEME" \
    -configuration "$CONFIGURATION" \
    -destination "id=$DEVICE_UDID" \
    -derivedDataPath "$DERIVED_DATA_PATH" \
    -allowProvisioningUpdates \
    build

  success "Build completed successfully."
else
  info "Skipping build phase (--skip-build)."
fi

if [[ ! -d "$APP_BUNDLE_PATH" ]]; then
  error "App bundle not found at: $APP_BUNDLE_PATH"
  echo "Run without --skip-build to build the application."
  exit 1
fi

# ------------------------------------------------------------------------------
# Install Phase
# ------------------------------------------------------------------------------
info "Installing MinimalPairs.app on device (${DEVICE_UDID})..."
xcrun devicectl device install app --device "$DEVICE_UDID" "$APP_BUNDLE_PATH"
success "App installed successfully."

# ------------------------------------------------------------------------------
# Launch Phase
# ------------------------------------------------------------------------------
info "Launching ${BUNDLE_ID} on device..."
if [[ "$AZURE_SCORER" = true ]]; then
  SCORER_RUNTIME="${SCORER_PYTHON:-$ROOT_DIR/build/phoneme-model-venv/bin/python}"
  if [[ ! -x "$SCORER_RUNTIME" ]]; then
    error "Scorer Python runtime not found. Set SCORER_PYTHON to a Python with httpx installed."
    exit 1
  fi
  SCORER_ARGS=(ios --device "$DEVICE_UDID" --bundle-id "$BUNDLE_ID")
  if [[ "$ATTACH_CONSOLE" = true ]]; then
    SCORER_ARGS+=(--console)
  fi
  exec "$SCORER_RUNTIME" "$ROOT_DIR/scripts/azure_speak.py" "${SCORER_ARGS[@]}"
fi

# Shell variables are not automatically inherited by an app on the phone.
if [[ -n "${MINIMAL_PAIRS_SCORER_URL:-}" ]]; then
  export DEVICECTL_CHILD_MINIMAL_PAIRS_SCORER_URL="$MINIMAL_PAIRS_SCORER_URL"
  export DEVICECTL_CHILD_MINIMAL_PAIRS_SCORER_KEY="${MINIMAL_PAIRS_SCORER_KEY:-}"
fi
LAUNCH_ARGS=(device process launch --device "$DEVICE_UDID" --terminate-existing)

if [[ "$ATTACH_CONSOLE" = true ]]; then
  info "Attaching console (press Ctrl+C to stop streaming)..."
  LAUNCH_ARGS+=(--console)
fi
LAUNCH_ARGS+=("$BUNDLE_ID")

xcrun devicectl "${LAUNCH_ARGS[@]}"

success "Minimal Pairs is now running on ${TARGET_DEVICE}!"
