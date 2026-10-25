#!/usr/bin/env bash
# ==============================================================================
# publish_testflight.sh
#
# Automates building, archiving, exporting, and publishing Minimal Pairs to
# Apple TestFlight via xcodebuild and the App Store Connect CLI (asc).
#
# Usage:
#   ./scripts/publish_testflight.sh [OPTIONS]
#
# Options:
#   -b, --build-number <num>  Explicit build number (CFBundleVersion)
#   -v, --version <semver>    Marketing version (CFBundleShortVersionString, e.g. 1.0.0)
#   -a, --app-id <id>         App Store Connect App ID (default: 6826850734)
#   -p, --profile <name>      Provisioning Profile name (default: "Minimal Pairs App Store")
#   --skip-build              Skip xcodebuild and upload existing build/ios/output/MinimalPairs.ipa
#   -h, --help                Show this help message
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
NC='\033[0m'

info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ------------------------------------------------------------------------------
# Defaults & Configurations
# ------------------------------------------------------------------------------
APP_ID="6826850734"
BUNDLE_ID="com.estebanl.minimalpairs"
PROFILE_NAME="Minimal Pairs App Store"
TEAM_ID="336DRM5VLX"
SIGNING_IDENTITY="iPhone Distribution: Esteban Lopez (${TEAM_ID})"

XCCONFIG_PATH="$ROOT_DIR/iosApp/Configuration/Config.xcconfig"
EXPORT_OPTIONS_PATH="$ROOT_DIR/iosApp/exportOptions.plist"
ARCHIVE_PATH="$ROOT_DIR/build/ios/MinimalPairs.xcarchive"
OUTPUT_DIR="$ROOT_DIR/build/ios/output"
IPA_PATH="$OUTPUT_DIR/MinimalPairs.ipa"

SPECIFIED_BUILD_NUMBER=""
SPECIFIED_VERSION=""
SKIP_BUILD=false

# ------------------------------------------------------------------------------
# Parse CLI arguments
# ------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    -b|--build-number)
      SPECIFIED_BUILD_NUMBER="$2"
      shift 2
      ;;
    -v|--version)
      SPECIFIED_VERSION="$2"
      shift 2
      ;;
    -a|--app-id)
      APP_ID="$2"
      shift 2
      ;;
    -p|--profile)
      PROFILE_NAME="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=true
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
info "Running pre-flight environment checks..."

if ! command -v asc >/dev/null 2>&1; then
  error "App Store Connect CLI ('asc') not found in PATH. Install via Homebrew: brew install asc"
  exit 1
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  error "'xcodebuild' not found. Please install and select Xcode command line tools."
  exit 1
fi

# Ensure OpenJDK 17 is found for Kotlin Multiplatform Gradle task
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
# Resolve Marketing Version & Build Number
# ------------------------------------------------------------------------------
CURRENT_CONFIG_VERSION=$(grep '^MARKETING_VERSION=' "$XCCONFIG_PATH" | cut -d'=' -f2 | tr -d ' ' || echo "1.0.0")
VERSION="${SPECIFIED_VERSION:-$CURRENT_CONFIG_VERSION}"

if [[ -n "$SPECIFIED_BUILD_NUMBER" ]]; then
  BUILD_NUMBER="$SPECIFIED_BUILD_NUMBER"
else
  info "Fetching next build number from App Store Connect for app $APP_ID (v$VERSION)..."
  NEXT_JSON=$(asc builds next-build-number --app "$APP_ID" --version "$VERSION" --platform IOS --output json 2>/dev/null || true)
  if [[ -n "$NEXT_JSON" ]] && echo "$NEXT_JSON" | grep -q '"nextBuildNumber"'; then
    BUILD_NUMBER=$(echo "$NEXT_JSON" | sed -E 's/.*"nextBuildNumber":"?([0-9]+)"?.*/\1/')
  else
    CURRENT_CONFIG_BUILD=$(grep '^CURRENT_PROJECT_VERSION=' "$XCCONFIG_PATH" | cut -d'=' -f2 | tr -d ' ' || echo "1")
    BUILD_NUMBER=$((CURRENT_CONFIG_BUILD + 1))
  fi
fi

info "Publish Target: App ID=$APP_ID ($BUNDLE_ID) | Version=$VERSION | Build=$BUILD_NUMBER"

# Update Config.xcconfig with resolved numbers
sed -i '' -e "s/^MARKETING_VERSION=.*/MARKETING_VERSION=${VERSION}/" "$XCCONFIG_PATH"
sed -i '' -e "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=${BUILD_NUMBER}/" "$XCCONFIG_PATH"

# ------------------------------------------------------------------------------
# Build & Archive Step
# ------------------------------------------------------------------------------
if [[ "$SKIP_BUILD" = false ]]; then
  info "Cleaning previous build outputs..."
  rm -rf "$ARCHIVE_PATH" "$OUTPUT_DIR"
  mkdir -p "$OUTPUT_DIR"

  info "Archiving iOS target with xcodebuild..."
  xcodebuild -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Release \
    -destination 'generic/platform=iOS' \
    -archivePath "$ARCHIVE_PATH" \
    CODE_SIGN_STYLE=Manual \
    CODE_SIGN_IDENTITY="$SIGNING_IDENTITY" \
    PROVISIONING_PROFILE_SPECIFIER="$PROFILE_NAME" \
    DEVELOPMENT_TEAM="$TEAM_ID" \
    CURRENT_PROJECT_VERSION="$BUILD_NUMBER" \
    MARKETING_VERSION="$VERSION" \
    archive

  success "Archive completed at $ARCHIVE_PATH"

  info "Exporting archive to IPA..."
  xcodebuild -exportArchive \
    -archivePath "$ARCHIVE_PATH" \
    -exportOptionsPlist "$EXPORT_OPTIONS_PATH" \
    -exportPath "$OUTPUT_DIR"

  if [[ ! -f "$IPA_PATH" ]]; then
    error "IPA export failed: $IPA_PATH not found"
    exit 1
  fi
  success "IPA exported: $IPA_PATH ($(du -h "$IPA_PATH" | cut -f1))"
else
  info "Skipping build phase (--skip-build). Using existing IPA at $IPA_PATH"
  if [[ ! -f "$IPA_PATH" ]]; then
    error "IPA file not found at $IPA_PATH. Run without --skip-build to create one."
    exit 1
  fi
fi

# ------------------------------------------------------------------------------
# Upload to App Store Connect
# ------------------------------------------------------------------------------
info "Uploading IPA to App Store Connect..."
asc builds upload \
  --app "$APP_ID" \
  --ipa "$IPA_PATH" \
  --version "$VERSION" \
  --build-number "$BUILD_NUMBER" \
  --verify-timeout 60s

success "Upload accepted by App Store Connect."

# ------------------------------------------------------------------------------
# Post-Processing: Compliance & Distribution
# ------------------------------------------------------------------------------
info "Waiting for App Store Connect binary ingestion (polling up to 10 minutes)..."
MAX_ATTEMPTS=40
ATTEMPT=1
BUILD_ID=""

while [[ $ATTEMPT -le $MAX_ATTEMPTS ]]; do
  BUILD_INFO=$(asc builds info --app "$APP_ID" --build-number "$BUILD_NUMBER" --version "$VERSION" --platform IOS --output json 2>/dev/null || true)
  if [[ -n "$BUILD_INFO" ]] && echo "$BUILD_INFO" | grep -q '"id"'; then
    BUILD_ID=$(echo "$BUILD_INFO" | sed -E 's/.*"id":"?([a-zA-Z0-9-]+)"?.*/\1/' | head -n1)
    PROCESSING_STATE=$(echo "$BUILD_INFO" | sed -E 's/.*"processingState":"?([A-Z_]+)"?.*/\1/' | head -n1)
    
    if [[ "$PROCESSING_STATE" == "VALID" ]]; then
      success "Build $BUILD_NUMBER (ID: $BUILD_ID) is VALID and ready!"
      break
    elif [[ "$PROCESSING_STATE" == "FAILED" || "$PROCESSING_STATE" == "INVALID" ]]; then
      error "Build processing failed with state: $PROCESSING_STATE"
      exit 1
    else
      info "Current processing state: $PROCESSING_STATE (attempt $ATTEMPT/$MAX_ATTEMPTS)..."
    fi
  else
    info "Waiting for build to become visible on ASC (attempt $ATTEMPT/$MAX_ATTEMPTS)..."
  fi
  sleep 15
  ATTEMPT=$((ATTEMPT + 1))
done

if [[ -n "$BUILD_ID" ]]; then
  info "Setting export compliance (uses-non-exempt-encryption=false)..."
  asc builds update --app "$APP_ID" --latest --uses-non-exempt-encryption=false >/dev/null 2>&1 || true

  info "Ensuring build is linked to Internal Testers group..."
  asc builds add-groups --app "$APP_ID" --latest --group "Internal Testers" >/dev/null 2>&1 || true

  success "Publication complete!"
  echo ""
  echo "=============================================================================="
  echo "  Minimal Pairs - TestFlight Release"
  echo "  App ID:        $APP_ID"
  echo "  Version:       $VERSION ($BUILD_NUMBER)"
  echo "  Build ID:      $BUILD_ID"
  echo "  TestFlight:    https://appstoreconnect.apple.com/apps/$APP_ID/testflight/ios"
  echo "=============================================================================="
else
  warn "Build upload succeeded, but ingestion is still in progress on Apple's servers."
  warn "Check status shortly with: asc builds list --app \"$APP_ID\""
  warn "Once valid, run: asc builds update --app \"$APP_ID\" --latest --uses-non-exempt-encryption=false"
fi
