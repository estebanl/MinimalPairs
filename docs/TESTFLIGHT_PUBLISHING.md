# TestFlight Publishing Guide

This guide describes how to build, archive, export, and publish the **Minimal Pairs** Kotlin Multiplatform (KMP) iOS application to Apple TestFlight using the automated script and the App Store Connect CLI (`asc`).

---

## 1. App Store Connect & Signing Overview

| Parameter | Value | Description |
|---|---|---|
| **App Name** | `Minimal Pairs - Pronunciation` | App display name in App Store Connect & TestFlight |
| **Bundle ID** | `com.estebanl.minimalpairs` | Primary application bundle identifier |
| **App ID** | `6826850734` | App Store Connect numeric resource ID |
| **SKU** | `MINIMALPAIRS01` | App unique stock-keeping unit |
| **Team ID** | `336DRM5VLX` | Apple Developer Account Team ID (`Esteban Lopez`) |
| **Provisioning Profile** | `Minimal Pairs App Store` | App Store Distribution Profile (UUID: `459c1f02-a14b-4452-82d2-56a4dff82da4`) |
| **Signing Certificate** | `iPhone Distribution: Esteban Lopez (336DRM5VLX)` | Distribution identity installed in macOS Keychain |

---

## 2. Prerequisites

1. **macOS Host & Xcode**:
   - Xcode 15+ with Command Line Tools (`xcode-select --install`).
   - Valid distribution certificate installed in Keychain (`security find-identity -v -p codesigning`).
2. **Java Runtime (JDK 17)**:
   - Kotlin Multiplatform requires JDK 17 during Xcode's Gradle build phase (`/usr/libexec/java_home -v 17`).
3. **App Store Connect CLI (`asc`)**:
   - Install via Homebrew:
     ```bash
     brew install asc
     ```
   - Authenticate your session:
     ```bash
     asc auth status
     # If authentication is needed:
     asc auth login
     # Or for 2FA web session:
     asc web auth login
     ```
4. **Provisioning Profile Installed**:
   - The provisioning profile must be installed at `~/Library/MobileDevice/Provisioning Profiles/`:
     ```bash
     asc profiles download --name "Minimal Pairs App Store" --output ~/Library/MobileDevice/Provisioning\ Profiles/8F37UTQB8N.mobileprovision
     cp ~/Library/MobileDevice/Provisioning\ Profiles/8F37UTQB8N.mobileprovision ~/Library/MobileDevice/Provisioning\ Profiles/459c1f02-a14b-4452-82d2-56a4dff82da4.mobileprovision
     ```

---

## 3. Automated 1-Command Publishing (Recommended)

A production-ready publishing script is available at [`scripts/publish_testflight.sh`](file:///Users/estebanlopez/dev/MinimalPairs/scripts/publish_testflight.sh). It automatically increments the build number from App Store Connect, compiles the Kotlin framework, archives the app, exports the signed IPA, uploads it, sets export compliance, and adds it to the **Internal Testers** group.

### Basic Usage

```bash
./scripts/publish_testflight.sh
```

### Custom Version / Build Number

```bash
# Publish version 1.0.1 with build 2
./scripts/publish_testflight.sh --version 1.0.1 --build-number 2
```

### Re-upload Existing IPA

If you already built the IPA and only want to re-upload:

```bash
./scripts/publish_testflight.sh --skip-build
```

### Script CLI Options

| Flag | Description | Default |
|---|---|---|
| `-b, --build-number <num>` | Explicit build number (`CFBundleVersion`) | Auto-queried from ASC |
| `-v, --version <semver>` | Marketing version (`CFBundleShortVersionString`) | Read from `Config.xcconfig` |
| `-a, --app-id <id>` | App Store Connect App ID | `6826850734` |
| `-p, --profile <name>` | Provisioning profile name | `Minimal Pairs App Store` |
| `--skip-build` | Skip `xcodebuild` and upload existing IPA | `false` |
| `-h, --help` | Show command usage and options | — |

---

## 4. Manual Step-by-Step Publishing

If you prefer running each step manually, follow these commands from the repository root:

### Step 1: Update Version & Build Numbers

Edit [`iosApp/Configuration/Config.xcconfig`](file:///Users/estebanlopez/dev/MinimalPairs/iosApp/Configuration/Config.xcconfig):

```properties
TEAM_ID=336DRM5VLX
PRODUCT_NAME=MinimalPairs
PRODUCT_BUNDLE_IDENTIFIER=com.estebanl.minimalpairs
CURRENT_PROJECT_VERSION=2
MARKETING_VERSION=1.0.0
```

### Step 2: Archive with xcodebuild

```bash
mkdir -p build/ios/output

xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/ios/MinimalPairs.xcarchive \
  CODE_SIGN_STYLE=Manual \
  CODE_SIGN_IDENTITY="iPhone Distribution: Esteban Lopez (336DRM5VLX)" \
  PROVISIONING_PROFILE_SPECIFIER="Minimal Pairs App Store" \
  DEVELOPMENT_TEAM="336DRM5VLX" \
  archive
```

### Step 3: Export Archive to IPA

Using [`iosApp/exportOptions.plist`](file:///Users/estebanlopez/dev/MinimalPairs/iosApp/exportOptions.plist):

```bash
xcodebuild -exportArchive \
  -archivePath build/ios/MinimalPairs.xcarchive \
  -exportOptionsPlist iosApp/exportOptions.plist \
  -exportPath build/ios/output
```

This outputs `build/ios/output/MinimalPairs.ipa`.

### Step 4: Upload to App Store Connect

```bash
asc builds upload \
  --app "6826850734" \
  --ipa "build/ios/output/MinimalPairs.ipa" \
  --verify-timeout 60s
```

### Step 5: Check Processing Status

Wait 2–5 minutes for Apple's ingestion servers to process the binary:

```bash
asc builds list --app "6826850734"
```

Once the state displays `VALID`, proceed to Step 6.

### Step 6: Mark Encryption Exemption (Export Compliance)

To make the build available to testers immediately without manual web form confirmation:

```bash
asc builds update --app "6826850734" --latest --uses-non-exempt-encryption=false
```

### Step 7: Manage Beta Groups & Testers

List existing groups:
```bash
asc testflight groups list --app "6826850734"
```

Add the latest build to a group:
```bash
asc builds add-groups --app "6826850734" --latest --group "Internal Testers"
```

Invite an individual tester:
```bash
asc testflight testers add --app "6826850734" --email "tester@example.com" --group "Internal Testers"
```

---

## 5. Troubleshooting & Architecture Notes

### Room 3 KMP iOS `@ConstructedBy` Requirement
In Kotlin Multiplatform, Room databases compiled for iOS targets require the `@ConstructedBy` annotation:
```kotlin
@Database(entities = [...], version = 1)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() { ... }

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
```
The Room KSP compiler generates the concrete `actual` constructor for iOS automatically.

### ExperimentalForeignApi in iOS Platform Calls
Any native iOS Foundation calls that take C-pointers (such as `NSFileManager.URLForDirectory(..., error = null)`) require opt-in:
```kotlin
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
```

### Provisioning Profile UUID Resolution
If `xcodebuild` reports that a profile cannot be found, ensure the `.mobileprovision` file is named after its UUID inside `~/Library/MobileDevice/Provisioning Profiles/`:
```bash
UUID=$(security cms -D -i ~/Library/MobileDevice/Provisioning\ Profiles/8F37UTQB8N.mobileprovision | grep -A1 '<key>UUID</key>' | tail -n1 | sed -E 's/.*<string>(.*)<\/string>.*/\1/')
cp ~/Library/MobileDevice/Provisioning\ Profiles/8F37UTQB8N.mobileprovision ~/Library/MobileDevice/Provisioning\ Profiles/${UUID}.mobileprovision
```
