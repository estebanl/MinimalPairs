# iOS mouth-position prototype

The Speak screen keeps Sound map as the default. Mouth position offers an
Illustration / 3D prototype choice on iOS for **sheep /iː/** and **ship /ɪ/** only.
The existing native A/B target selectors drive both representations. Native
Front, Side, Reset view, tissue opacity, and Listen controls are outside the web
view. Listen reuses the bundled whole-word WAV files; it does not animate speech.
Illustration remains available on every platform. Other words and phonemes,
VoiceOver, viewer errors, process termination, and a missing ready handshake use
the existing image/text fallback. Recording and Sound map do not feed the model.

## Provenance and teaching scope

The source is the user-provided `vocal-tract-study.zip`, Library item
`libfile_84586ca5915481918bf2c7d9dd3f0e0b`, version 1. Its version-2 GLB is
5,527,848 bytes, with 65 meshes and no animation clips. The original MIT model
license and Three.js license are retained in `scripts/mouth-position/`.
The original Blender source and generator remain in the supplied ZIP; the app
needs only the generated, embedded model. The source rendered preview was
visually inspected during implementation. This is not live WebGL verification.

`poses.mjs` authors two distinct teaching references. They do **not** rename the
source viewer's illustrative A/I/U/Curl presets as reviewed IPA targets.

| Reference | Tongue raise | Advance | Tip raise | Jaw | Lip round |
| --- | ---: | ---: | ---: | ---: | ---: |
| sheep /iː/ | 0.72 | 0.78 | 0 | 0.12 | 0 |
| ship /ɪ/ | 0.42 | 0.48 | 0 | 0.24 | 0 |

These unitless weights are artistic controls, not measured articulator positions
or acoustic targets. Sheep places the tongue higher and farther forward than
ship, with a smaller jaw opening; both are unrounded. The tip is not curled.
The placement text and descriptions reuse `MouthPositionReferences`, retaining
the app's British-English phonemic conventions. The
[International Phonetic Association vowel chart](https://www.internationalphoneticassociation.org/content/ipa-chart)
provides the relative height/frontness/rounding framework; the
[BBC British-English symbol sheet](https://downloads.bbc.co.uk/learningenglish/features/themed_weeks/day_2_5_days_to_improve_your_pronunciation.pdf)
provides the app's phonemic-label context. These sources inform the teaching
intent; they do not validate this geometry or its numeric weights.

**Phonetic review gate:** neither model nor poses has independent specialist
validation. Before expanding beyond this pair or removing the prototype label,
a British-English phonetics specialist must inspect both rendered views and the
transition, tongue/palate clearance, tip placement, jaw/teeth/lip relationships,
and agreement with the cues and bundled audio. Record reviewer, date, dialect,
findings, and any changed weights. A static vowel reference does not encode
coarticulation, individual anatomy, or the possible dynamic realization of
FLEECE. F1/F2 cannot determine a learner's visible tongue pose here.

## Offline architecture and lifecycle

The generated `iosApp/iosApp/MouthPosition/mouth-position.html` contains pinned
Three.js 0.180.0 and the GLB bytes. No CDN, local server, account, upload, or
recording bridge is needed. `MouthWebSession` loads the bundled file once per
viewport. Compose recomposition sends state, not a new document.

The version-1 typed bridge accepts only sheep/ship, front/side, bounded opacity,
active state, and reduced-motion preference, plus disposal. A ready handshake
follows model setup and the first render; native code queues only the latest
selection until then. JS error, native navigation/process failure, or a 12-second
readiness timeout returns to illustrations. Navigation is restricted to the
single bundled document, capture permission is denied, and CSP prevents network
connections, external scripts, frames, forms, and objects. The WK store is
nonpersistent. Microphone samples never enter JavaScript.

Only dirty frames and short 220 ms pose transitions request animation frames.
Idle poses have no continuous render loop. Reduced Motion applies the final
pose immediately. Native foreground notifications and clipped layout visibility
pause rendering; page visibility provides an additional stop. Disposal removes
notifications, web message handlers, DOM listeners, resize observation, pending
animation frames, geometry, textures, materials, and the renderer/context. Late
loads cannot revive a disposed JS session. Capture buffers are disabled and
pixel ratio is capped at 2.

`window.mouthPositionDiagnostics()` exposes render count, geometry/texture
counts, current weights, and lifecycle state for local QA. No frame-rate claim
is made. On a compatible device, profile load time, main-thread work, GPU/frame
time, memory across repeated navigation, and idle/background rendering before
choosing further optimizations.

## Reproduction and verification

From the repository root:

```sh
cd scripts/mouth-position
npm ci --no-audit --no-fund
node build.mjs
node test.mjs
node test-lifecycle.mjs
```

The generated HTML is checked in so normal app builds need no Node or network
viewer dependency. Regenerate it after changing viewer code, weights, or model.
The geometry test checks the actual GLB, 65 meshes, required tongue morphs,
distinct poses, finite geometry at 21 transition steps, broad model bounds,
and embedded/offline packaging. Bounds checks are not anatomical collision
certification. The DOM lifecycle test uses explicit renderer/model-load doubles;
it tests readiness, rapid selection, idle frames, pause/resume, reduced motion,
unsupported state and disposal across ten sessions. It does **not** test WebGL.

```sh
./gradlew :composeApp:jvmTest :composeApp:compileKotlinIosArm64 \
  :composeApp:compileKotlinIosSimulatorArm64 --max-workers=1 --no-parallel
```

JVM tests cover the scoring regression, bridge queue/disposal, unsupported poses,
existing image/resource coverage, and existing Speak/Sound map/recording UI flows.
The original regression was observed failing for both the selected-IPA label and
identical scores across A/B before the fix. Local scoring now uses an exact
selected IPA first, then known whole-word lookup. An unknown word no longer
silently chooses phoneme 2. Whole-word transcripts keep their word lookup.

### Runtime review still required

This connected Mac is Intel with Xcode 16.0 and iOS 18.0 simulator runtimes.
Compose 1.12.2 does not publish an Intel iOS variant; the project's supported
simulator target is ARM64. No physical iPhone was connected during verification.
The attempted Intel simulator configuration was removed. Browser local-file
preview was blocked by browser URL policy and was not bypassed.

On a supported ARM simulator, then a real iPhone, verify actual rendered pixels
for both poses/views; rapid A/B changes before and after ready; reset/opacity;
repeated entry/exit; scroll offscreen; background/resume; VoiceOver and enlarged
text; Reduced Motion changes; bundled word playback; microphone flow remaining
independent; offline loading; missing/corrupt model and WebGL/context/process
failure returning to illustrations. The unsigned local ARM64 app build confirms that `mouth-position.html` is
packaged at the bundle root. Confirm the WK ready/error handshake on device. Until then, live rendering,
performance, native lifecycle behavior and device accessibility remain open
review gates despite unit/compile checks.

## Verification record — 27 October 2026

- `:composeApp:jvmTest`: **51 passed**, zero failures/errors/skips, including
  three scoring regressions and four bridge tests. JVM Compose reference
  screenshots were inspected; they verify desktop illustration behavior only.
- `:composeApp:compileKotlinIosArm64` and
  `:composeApp:compileKotlinIosSimulatorArm64`: passed. Compilation is not a
  simulator run.
- `:composeApp:compileAndroidMain`: passed; Android retains static references.
- Unsigned generic iOS ARM64 Debug app build: **BUILD SUCCEEDED**. Confirmed
  `MinimalPairs.app/mouth-position.html` exists with the generated viewer bytes.
  No device installation or release occurred. Xcode 16.0 warns that the existing
  18.2 deployment target is newer than its 18.0 SDK, and Kotlin emits its bundle-ID
  inference warning; neither prevented the build.
- `node test.mjs`: passed; glTF validator zero errors/warnings, 65 meshes,
  21 transition steps, distinct finite pose geometry, offline embedded package.
- `node test-lifecycle.mjs`: passed with renderer/model doubles; ten independent
  navigation sessions plus unsupported state and disposal-before-load cases.
- Browser: **no live WebGL run**, local-file navigation denied by browser policy.
- Simulator/device: **no runtime verification**, for the compatibility and
  connected-device reasons above. Phonetic specialist review remains pending.
