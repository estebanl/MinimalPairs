# Calibrated jaw feedback: implementation and validation plan

The current app cannot report jaw millimetres from its captured data. Android
passes ML Kit lip contours to shared code; iOS passes Vision lip contours from
the front wide-angle camera. Neither adapter currently delivers nose/chin
landmarks, registered metric depth, or a neutral-jaw calibration. Adding a
constant face size or subtracting a population-average nose-to-chin length would
produce assumed anatomy rather than a measurement.

## What the platform APIs can support

| Input | Defensible output | Limit |
| --- | --- | --- |
| Current lip contours | Lip opening ratio and projected circularity | No jaw position or metric scale |
| ARKit `jawOpen` | Relative expression coefficient, optionally relative to a personal neutral pose | Coefficient is not millimetres or a jaw angle |
| ML Kit face mesh | Image-space face landmarks and relative depth | Mesh Z uses image scale, not millimetres |
| Synchronized calibrated depth + facial landmarks | Candidate surface-landmark displacement after head-motion compensation | Requires physical accuracy validation before a metric UI claim |

Apple describes [`jawOpen`](https://developer.apple.com/documentation/arkit/arfaceanchor/blendshapelocation/jawopen)
as an expression coefficient, with examples at 0 and 1. A relative ARKit mode
would be a separate camera backend; it must replace the current camera owner,
not open a second front-camera session. Check
[`ARFaceTrackingConfiguration.isSupported`](https://developer.apple.com/documentation/arkit/arfacetrackingconfiguration)
at runtime. Face-tracking support alone does not establish metric depth support.

Google's [`FaceMeshPoint`](https://developers.google.com/android/reference/com/google/mlkit/vision/facemesh/FaceMeshPoint)
reports X/Y pixel positions and Z in the same image-scaled units. Migrating to
its 468-point mesh would supply more landmarks but would not solve physical
calibration. The [Android face-mesh API](https://developers.google.com/ml-kit/vision/face-mesh-detection/android)
is also a different, beta dependency from the bundled face-contour detector.

For a depth path, inspect each frame's
[`AVDepthData.depthDataAccuracy`](https://developer.apple.com/documentation/avfoundation/avdepthdata/accuracy/absolute)
and calibration metadata. Reject relative-only depth for metric reporting.
Do not infer accuracy from a device name or from a depth pixel format alone.
The installed iOS SDK also exposes nullable `cameraCalibrationData`, so absence
must be a normal unavailable state rather than an assumed camera matrix.

## Proposed depth implementation

1. Define the quantity before coding: **surface chin displacement from a neutral
   pose, in the head coordinate frame**, rather than dental separation or an
   internal jaw angle. The UI must use that name. This is a proposed proxy for
   opening, not a measurement of internal bone motion.
2. Add a capability-negotiated native backend. On iOS select a supported depth
   capture format and synchronize color/depth frames with
   `AVCaptureDataOutputSynchronizer`. Preserve the existing single camera owner,
   main-thread callbacks, microphone ownership, and lifecycle teardown. On
   Android require an actual front-facing calibrated depth source; ordinary
   CameraX/ML Kit contours stay in the existing nonmetric mode.
3. Register landmarks into the depth image using the capture calibration,
   orientation, and crop transforms. Reject missing/nonfinite depth and invalid
   registration. Back-project with the correct camera intrinsics. Metric points
   must carry source, units, frame timestamp, tracking identity, and calibration
   identity; a normalized image point cannot satisfy this contract.
4. Fit a rigid head reference from stable upper-face observations, then measure
   chin movement in that reference. Collect a short stable neutral window and
   use a robust baseline. Do not use the moving lower jaw in the head alignment.
   Reject a calibration with motion, missing landmarks, excessive spread, or
   inconsistent depth. Numerical tolerances must be selected from measured
   device data, not chosen to make synthetic tests pass.
5. Clear baseline and live values after face-identity changes, session restarts,
   camera/format changes, tracking loss that breaks correspondence, and failed
   registration. A new person cannot inherit someone else's baseline. Preserve
   the existing qualitative teaching references when a metric is unavailable.

The live synchronized-depth backend is not implemented yet. The strict geometry
and neutral-baseline components below are now implemented, but they do not
substitute for camera registration or validate physical accuracy. The backend
still needs a tester, reliable external reference measurements, and
device-specific depth validation. The existing
personal lip pose remains useful for repeatability but is not a jaw calibration.
No front-camera/depth path here observes internal tongue surfaces; internal
vocal-tract reconstruction remains a separate research/model-validation task.

## Implemented geometry and availability contract

`SurfaceSpanCalibration.kt` provides a bounded neutral-calibration state machine
and pinhole back-projection for caller-supplied rectified, registered metric
samples. Its quantity is **change in surface nose-to-chin span**, rather than
head-frame chin displacement, bone rotation, or dental separation. Euclidean
span is invariant to rigid motion of both landmarks, so a translated/rotated
head alone cannot create an opening measurement. Surface expression changes and
landmark/depth error still affect the value and require validation.

The source contract requires separate video/depth frame IDs and timestamps,
camera/format/session/face/calibration/registration identities, and explicit
absolute-depth, synchronization and rectification assertions. Relative depth,
nonfinite points, missing provenance, mismatched timing, repeated frame IDs,
non-increasing video or depth timestamps, and source changes invalidate the
baseline. Tracking loss must be delivered as
`accept(null)` or `invalidate()`; no old baseline may survive it. The state
machine only collects after `beginNeutralCalibration()`. It uses a stable-window
median and returns signed changes, preserving closing movements rather than
clamping them into a positive jaw-drop value.

No device accuracy thresholds are supplied. Callers must explicitly provide the
neutral sample count/window, tolerated neutral spread, timestamp gap, and
synchronization skew from their validation protocol. Memory is bounded to 300
neutral samples, while full-window extrema still guard against instability.
These components check supplied metadata and numerical validity; they cannot
verify that a caller's registration assertions are physically correct. They are
not connected to a live metric UI.

`inspectDepthCaptureSupport()` is a read-only platform adapter. Android's current
contour pipeline and desktop return `NO_CALIBRATED_SOURCE`. iOS checks for a
front TrueDepth device with depth-capable formats and can report only
`CANDIDATE_REQUIRES_VALIDATION`. Discovery never starts a second camera or claims
measurement readiness. `assessIosDepthMetadata()` rejects relative-only depth,
missing calibration, invalid reference dimensions, or absent distortion data;
its best result still requires synchronization, registration and validation.

Read-only XCTest discovery on the paired iPhone 13 mini (2026-10-27) found one
front TrueDepth device. Its 640×480 video formats expose 160×120, 320×240 and
640×480 depth formats; 1280×720 video formats expose 160×90, 320×180 and 640×360.
The discovered FourCC types include `hdis`, `fdis`, `hdep` and `fdep` (half/single
precision disparity/depth). This confirms candidate hardware, not per-frame
absolute accuracy, color-depth registration, or calibrated jaw measurements.
No synchronized depth frame or physical reference measurement was collected by
that discovery test. Local evidence is in
`build/ArticulatorDeviceTests/lifecycle2-invocation.log` (`ARTICULATOR_TRUEDEPTH`).

Apple documents that [calibration data](https://developer.apple.com/documentation/avfoundation/avcameracalibrationdata)
provides camera geometry and distortion information. The SDK's point-mapping
reference specifies the inverse lookup table for mapping a distorted landmark
to rectified coordinates; scaling/orientation must preserve the calibration's
coordinate system. [Depth accuracy](https://developer.apple.com/documentation/avfoundation/avdepthdata/depthdataaccuracy)
is frame metadata, not a guarantee inferred from a device model. A future native
adapter must use [synchronized depth delivery](https://developer.apple.com/documentation/avfoundation/avcapturesynchronizeddepthdata),
reject dropped samples, and perform these transforms before feeding the helper.

`SurfaceSpanCalibrationTest` tests projection units, signed baseline-relative
change, rigid-motion invariance, unstable calibration, source identity changes,
tracking loss, bad registration/depth, repeated frames and timestamp gaps. These
eight tests use synthetic geometry and a test-only policy; they establish
algorithm behavior, not real sensor accuracy. Parent integration runs them with
the common JVM/Android test suites and compiles the iOS metadata adapter.

## Paired-measurement validation

Collect repeated neutral/opening/closing samples across supported devices,
distances, lighting, head orientations, and people. Use an independently
measured reference for the *same quantity and coordinate frame*. An inter-incisor
distance cannot be treated as ground truth for surface nose-to-chin span change
or head-frame chin displacement.
Keep calibration samples separate from evaluation samples and repeat the neutral
pose after the session to quantify drift. Include failed/unavailable estimates
in the dataset so filtering does not manufacture good accuracy.

`scripts/validate_articulator_calibration.py` summarizes paired observations with
bias, MAE, RMSE, nearest-rank p95 absolute error, maximum error, and availability,
both overall and per device/condition. It neither fits a calibration nor declares
a pass threshold. Set acceptance criteria with the intended teaching use and
measurement uncertainty before evaluating a held-out dataset.

Input CSV columns:

```text
sample_id,device,condition,reference_mm,estimated_mm
```

Use anonymous sample IDs, a device model or test-device label, and explicit
conditions such as `neutral_start`, `frontal_open`, `oblique`, or `neutral_end`.
Leave `estimated_mm` blank when the app cannot measure; a missing observation is
not zero. Every row needs a valid independent reference, and duplicate sample
IDs and nonfinite values are rejected. Signed displacements are allowed.

```sh
python3 scripts/validate_articulator_calibration.py /path/to/paired-measurements.csv \
  --output build/reports/articulator-device/calibration-report.json
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests \
  -p 'test_articulator_calibration.py'
```

The three report tests use synthetic numbers to validate the statistics,
unavailable coverage, grouping, and invalid-input handling. They do not validate
sensor accuracy. No real paired jaw measurements have been collected in this
session, so no metric-accuracy result is claimed.
