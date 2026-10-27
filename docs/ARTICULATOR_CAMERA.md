# Articulator camera measurements

The live Android ML Kit and iOS Vision adapters supply ordered outer and inner
lip contours. Measurements use equal-scale image coordinates and compensate for
small head roll. They reject missing, nonfinite, tiny and implausible contours.

The Camera tab displays inner-lip opening divided by outer mouth width and outer
contour circularity (`4π × area / perimeter²`). Circularity describes the projected
outline; it cannot measure protrusion or establish whether a vowel is correct.
Opening and circularity use a timestamp-aware 1-Euro filter with minimum cutoff
1 Hz, beta 0.05, and derivative cutoff 1 Hz. The filter uses monotonic camera
capture timestamps, so its response adapts to elapsed time rather than assuming
a fixed frame rate. Tracking loss, missing timestamps and gaps over 500 ms reset
smoothing. Session tokens reject old sessions, timestamp ordering rejects late
frames, and the existing two-second timeout clears stalled results.

A learner may capture a temporary personal pose and compare signed changes in
opening and circularity against it. The pose is explicitly not a verified target.
It can be replaced or cleared, and resets on phoneme change or camera-session
change. It is held only in memory; frames and reference poses are not persisted.
The selected vowel's illustration and phoneme-specific placement cues remain the
teaching target. There are no invented numerical pass/fail thresholds.

Calibrated jaw distance, tongue position and lip protrusion remain unavailable.
The current camera adapters do not provide metric depth or a calibrated jaw
reference. The unused legacy `FacialLandmarkAnalyzer` assumes anatomical sizes
and must not be used to fill these gaps. A future implementation needs a validated
calibration protocol, suitable tracking inputs and physical-device measurements.
The shared placement card no longer presents contrast-level assumed jaw distances
or infers lip rounding from aperture.

## Analysis scheduling and diagnostics

Both adapters target approximately 30 analyses per second, replacing the former
180 ms throttle. The pacer preserves a 33.3 ms deadline phase with 1 ms capture
timestamp tolerance; adjacent accepted starts remain at least 31.3 ms apart.
Missed slots are discarded after stalls, so no catch-up bursts accumulate.
The tolerance prevents small timestamp jitter from repeatedly dropping every
second frame: a regression sequence at nominal 30 Hz with alternating 0.4 ms
jitter admitted 151/300 frames under the previous strict elapsed-time gate and
admits 300/300 with phase-aware pacing. This controlled result does not identify
the cause of a particular physical device's lower delivered rate. Android requests a
640×480 analysis resolution and retains CameraX `KEEP_ONLY_LATEST` backpressure;
the image stays open until detector completion so inference does not build a
queue. ML Kit keeps ACCURATE mode because the existing pose guards depend on
Euler angles that some FAST/contour-only configurations suppress. iOS keeps a
640×480 session and performs synchronous Vision analysis on one serial queue
with `alwaysDiscardsLateVideoFrames` enabled. Slower inference lowers the result
rate naturally; neither platform queues work to catch up.

A one-slot mailbox also coalesces results waiting for the UI, preventing a blocked
main thread from accumulating stale outlines. Terminal errors take precedence
over subsequent tracking frames. The newest delivered result includes its capture
timestamp and measured analysis duration. Duration includes detector processing
and contour extraction, but excludes waiting for the UI mailbox to deliver it.

Optional **Tracking details** shows delivered analysis-result FPS and mean/max
analysis duration over the last 30 timed results. These are observed values, not
camera sensor FPS, pure neural inference benchmarks, or a promise of 30 FPS.
Coalescing under UI load can lower the delivered rate. The first result has no
rate estimate; session changes, gaps over 500 ms and freshness timeouts clear old
timing history. Record these diagnostics on physical devices while tracking a
face, with and without simultaneous microphone capture. The 33 ms processing
budget and sustained 30 FPS remain unverified until that profiling is performed.

Regression coverage: `LipTrackingTest` checks circularity invariance, finite
measurements, signed comparisons, filter timing/reset, 30 Hz admission, mailbox
backpressure, timestamp ordering and measured-rate resets. `LipCameraUiTest` checks
reference clearing across sounds/sessions and renders narrow light/dark layouts
with enlarged text. Real-device validation remains separate from these tests.

Implementation references: [ML Kit real-time detection guidance](https://developers.google.com/ml-kit/vision/face-detection/android),
[ML Kit Euler-angle limitations](https://developers.google.com/ml-kit/vision/face-detection/face-detection-concepts),
[Apple frame-drop guidance](https://developer.apple.com/library/archive/technotes/tn2445/_index.html),
and the [1-Euro filter authors' implementation](https://github.com/casiez/OneEuroFilterArduino).
