# Articulator Lab physical-device validation

Compilation and JVM screenshots do not validate camera capture. Use this procedure
on physical Android and iOS devices before release. Do not erase app data or reset
progress to run it.

## Evidence from 2026-10-27

Tests used the paired iPhone 13 mini running iOS 27.2. No Android hardware was
attached. XCTest now drives the installed app; the earlier Device Hub UI-tool
failure is no longer a blocker for navigation and lifecycle checks.

| Check | Observed result |
| --- | --- |
| Physical iOS build/install/launch | PASS: latest signed Debug app installed and launched in place. |
| Speak accessibility navigation | PASS: selected Speak and observed its heading. |
| Camera capture without a visible face | PASS: native analysis produced timed results and displayed Bring your face into view. |
| Concurrent microphone capture | PASS for recording state/duration: recording reached four seconds while camera analysis continued. Audio quality and replay were not evaluated. |
| Background/foreground during camera + recording | PASS after the host lifecycle fix: recording returned idle, camera showed paused, and restart required an explicit action. |
| Explicit camera restart/stop | PASS after foregrounding; teardown leaves the app idle. |
| Repeated Camera/Sound map changes | PASS for eight settled round trips on the lifecycle-fix build plus two on the final pacing-fix build; native timed results resumed each time. Not ten consecutive or rapid cycles. |
| Permission denial/grant/retry | NOT RUN: existing permissions allowed capture; no permission settings were reset or toggled. |
| Face tracking, mirroring, low-light recovery | NOT RUN: observed status was no face, not a tracked person. |
| Sustained 30 FPS with face tracking | NOT ESTABLISHED: short no-face windows delivered approximately 22.4–23.9 results/s under XCTest. |
| Landscape camera journey | PASS for landscape layout/session survival: bounds changed from 375×812 to 812×375; record control remained reachable, portrait restored, camera stopped. Face contour orientation remains untested. |
| TrueDepth capability discovery | PASS for enumeration only: one front TrueDepth device and supported depth formats; no calibrated depth frames or jaw distances measured. |

The latest build log is
`build/ArticulatorDeviceTests/final-pacer-device-build.log`; the passing
camera/microphone lifecycle run is
`build/ArticulatorDeviceTests/final-pacer-validation.log`. These are local
artifacts. The app was not uninstalled, progress was not reset, and recording was
cancelled rather than scored. XCTest teardown terminates capture and relaunches
the app idle.

A portrait screenshot of the earlier updated app's Listen landing page was
inspected at `build/reports/articulator-device/portrait-baseline.png`; this
establishes launch layout only. The earlier CLI landscape request returned
success but still reported portrait, so command exit alone was not treated as a
rotation pass. Portrait was restored, without changing rotation-lock settings. A later XCTest
orientation run did change app bounds to 812×375, kept the record control
reachable, returned to portrait, and stopped capture successfully
(`tabs-rotation-invocation.log`). No tracked face was available to assess contour
rotation or mirroring.

## XCTest automation

`scripts/run_articulator_device_tests.sh DEVICE_UDID` generates a standalone
XCTest UI runner under `build/ArticulatorDeviceTests/`. It requires Xcode,
XcodeGen, and the signing team already configured in the iOS host. The runner
launches the already-installed `com.estebanl.minimalpairs` app by bundle ID;
it does not rebuild the host or reset its data. Only camera/microphone permission
prompts, if presented, may be accepted. Existing permission settings are not
reset or toggled. Recording is cancelled through backgrounding rather than
scored into practice history, and teardown relaunches the app idle.

This follows Apple's documented
[installed-app lookup for XCUIApplication](https://developer.apple.com/documentation/xcuiautomation/xcuiapplication/init%28bundleidentifier%3A%29).
Optional trailing `xcodebuild` test filters can select one journey, for example
`-only-testing:ArticulatorDeviceTests/ArticulatorDeviceTests/testCameraAndMicrophoneLifecycle`.

The runner records accessibility observations and sampled analysis timings, not
an assertion that a face is present. XCTest result bundles can include screenshots
and remain ignored local build artifacts. The read-only TrueDepth discovery test
enumerates device/format capabilities without opening a second capture session.

On the paired iPhone, the initial navigation test passed: the runner launched
the app, selected Speak, and observed its heading. A subsequent camera run
observed **Bring your face into view**, 30 timed results, 23.9 delivered analyses/s,
and 5 ms average / 11 ms maximum analysis duration. With microphone recording
at four seconds, the camera still reported no face, 23.1 analyses/s, and 5 ms
average / 9 ms maximum. These short sampled windows were collected under XCTest;
they do not validate sustained 30 FPS or face-landmark inference performance.

The first concurrent-capture run exposed a real iOS lifecycle failure:
microphone recording continued after background/foreground. The host had resumed
Essenty once without forwarding app background events. `MainViewController` now
remembers its root and forwards the Compose host lifecycle through
`HostLifecycleBridge`, including stop/destroy. Three shared regression tests cover
that bridge. The physical rerun passed: after foregrounding, **Record your sound**
returned and **Camera paused** was visible; explicit restart and stop also passed.
The earlier failure remains in `lifecycle4-invocation.log` and the verified fix in
`lifecycle-fixed-invocation.log` under `build/ArticulatorDeviceTests/`.

The final pacing-fix build passed the lifecycle journey again in 59.1 seconds.
It sampled no-face analysis at 23.9 results/s (4 ms average, 8 ms maximum) and
concurrent microphone capture at 22.4 results/s (5 ms average, 11 ms maximum).
These are rolling windows over 30 delivered results, not sustained sensor FPS or
face-landmark benchmarks. The deterministic pacing correction did not establish
a device-rate improvement in these samples.

Repeated-tab evidence is split across two runs: `tabs-retry-invocation.log`
verified eight round trips before its 240-second runner limit interrupted the
test; `final-pacer-validation.log` then passed two additional round trips in
86.8 seconds on the final build. Each cycle returned from Sound map to Camera,
explicitly started capture, and observed timed native results. These were settled
UI transitions, not rapid switching or ten consecutive cycles. The runner now
allows 600 seconds for a future full ten-cycle journey. Both final tests passed,
and teardown relaunched the app with camera and microphone idle.

Read-only discovery found one front TrueDepth device with 640×480 video formats
supporting 160×120, 320×240, and 640×480 depth/disparity formats, in Float16 and
Float32. This proves capability enumeration only; no synchronized depth frames,
absolute-accuracy metadata, or calibrated jaw distance were collected.

## Capture an inventory

From the repository root:

```sh
python3 scripts/articulator_device_inventory.py
```

The script has a 25-second timeout per command, does not install an app, and writes
local evidence under `build/reports/articulator-device/inventory.json`. The report
contains device identifiers; keep it out of source control. Record the app build,
device model, OS version, and date with each manual run.

For iOS, build and launch on the intended device with:

```sh
./scripts/run_on_device.sh --device '<device name or UDID>'
```

Android currently ships as a library. Use an Android host Activity rendering
`MinimalPairsApp(componentContext)` as described in README.md; a successful library
compile is not an installable launcher or a hardware test. Confirm that the host
forwards lifecycle events and declares the library's camera/microphone permissions.

## Manual acceptance run

For every row below record PASS, FAIL, or NOT RUN and the concrete observation.
Use a dedicated test device or change permission toggles in Settings without
uninstalling the app. Restore prior permissions after testing.

| Journey | Expected observation |
| --- | --- |
| Open Speak and select every contrast/example | Word labels, selected target, illustrations, and 3D reference match; changing selection clears a prior score/recording. |
| Camera permission denied, then retry after enabling | Denial guidance appears, no live measurements remain; starting again after enabling opens a preview. |
| Microphone permission denied, then retry after enabling | Helpful error and retry remain available; no speaking result is saved for the denied attempt. |
| Start camera with one face centered | Mirrored preview, lip outline, opening ratio and circularity update; left/right movement agrees with a mirror. |
| Open/close and round/spread lips | Measurements respond without nonfinite values or old values sticking; these are estimates, not verified pronunciation scores. |
| Capture personal reference, then change sound | Comparison changes with lip pose; the personal reference clears when the phoneme changes or camera session restarts. |
| Leave the frame, cover mouth, turn head, add a second face | No-face/adjust-position guidance replaces unreliable measurements; return to a frontal single face recovers tracking. |
| Low light and long camera stall | Invalid/stale measurements clear within the documented two-second freshness period. |
| Rotate portrait to both landscape orientations and back | Preview and contour remain upright and mirrored; labels and controls remain reachable. |
| Background while camera runs, then foreground | Camera pauses, stale metrics disappear, and capture resumes only after explicit Start camera. |
| Switch Camera ↔ Sound map ten times | Camera releases on exit and restarts cleanly without duplicate previews or a stuck privacy indicator. |
| Record while camera tracks, stop, replay | Lip measurements continue during capture; recording duration advances, score returns, and replay is audible. |
| Stop/leave Speak during recording or scoring | Audio/camera resources release; late callbacks do not revive the session or attach results to another target. |
| Record silence, then a clear short vowel | Silence produces no score; valid input can recover without reopening the app. |
| Switch to 3D while recording and stop speaking | Live acoustic estimate updates and clears on silent/stalled input; anatomy remains explicitly a teaching reference. |
| Deny network/model service | Any acoustic fallback is labeled as an estimate; it does not claim a neural score. |

Avoid saving facial screenshots or audio as routine test evidence. Textual
observations and timestamps are sufficient; if media is needed, use a consenting
tester and store the evidence privately.

## Execution notes

The latest automated batch passed 182 JVM tests, 125 Android host tests, 32
backend tests, and three calibration-report tests, plus Android and iOS simulator
compilation. Those checks exercise
controlled inputs and shared logic, not physical camera/microphone correctness.

`./scripts/run_on_device.sh --device '<user iPhone UDID>'` exited 0: Xcode reported
`BUILD SUCCEEDED`, `devicectl` installed `com.estebanl.minimalpairs`, then confirmed
launch of that bundle. The signed Debug app is at
`build/DerivedData/Build/Products/Debug-iphoneos/MinimalPairs.app`. This was an
in-place update; no uninstall, progress reset, permission reset, or unrelated
device change was performed.

The depth-source requirements and paired-measurement procedure for calibrated
jaw feedback are in [ARTICULATOR_CALIBRATION.md](ARTICULATOR_CALIBRATION.md).
Its statistics helper is tested independently; real jaw accuracy remains untested.
