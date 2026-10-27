package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.cv.*
import kotlin.test.*

class SurfaceSpanCalibrationTest {
    // Synthetic test policy only; these values are not device accuracy thresholds.
    private val policy = SurfaceCalibrationPolicy(3, .19, .3, 1.0, .005)
    private val identity = SurfaceDepthIdentity("depth-camera", "640x480", "session1", "face1", "calibration1", "rectified1")
    private fun frame(time: Double, spanMm: Double = 60.0) = RegisteredSurfaceSpanFrame(identity, "video-$time", "depth-$time", time, time,
        MetricSurfacePoint(0.0, 0.0, .5), MetricSurfacePoint(0.0, spanMm / 1000, .5),
        absoluteDepth = true, synchronized = true, rectifiedAndRegistered = true)
    private fun calibrated(): SurfaceSpanCalibrator = SurfaceSpanCalibrator(policy).apply {
        beginNeutralCalibration()
        assertEquals(SurfaceCalibrationStatus.COLLECTING_NEUTRAL, accept(frame(0.0)).status)
        assertEquals(SurfaceCalibrationStatus.COLLECTING_NEUTRAL, accept(frame(.1)).status)
        assertEquals(SurfaceCalibrationStatus.READY, accept(frame(.2)).status)
    }

    @Test
    fun backProjectionUsesPixelIntrinsicsAndMetresWithoutAssumedFaceSize() {
        val camera = RectifiedDepthIntrinsics(640, 480, 500.0, 500.0, 320.0, 240.0)
        val nose = camera.backProject(320.0, 240.0, .5)!!
        val chin = camera.backProject(320.0, 300.0, .5)!!
        assertEquals(.06, nose.distanceTo(chin), 1e-9)
        assertEquals(.12, camera.backProject(320.0, 240.0, 1.0)!!
            .distanceTo(camera.backProject(320.0, 300.0, 1.0)!!), 1e-9)
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(camera.backProject(320.0, 240.0, invalid))
        }
        assertNull(camera.backProject(640.0, 240.0, .5))
        assertNull(camera.backProject(320.0, -1.0, .5))
        assertNull(camera.copy(focalX = 0.0).backProject(320.0, 240.0, .5))
    }

    @Test
    fun explicitStableNeutralProducesSignedSurfaceSpanChange() {
        val calibrator = SurfaceSpanCalibrator(policy)
        assertEquals(SurfaceCalibrationStatus.NEEDS_NEUTRAL, calibrator.accept(frame(0.0)).status)
        calibrator.beginNeutralCalibration()
        calibrator.accept(frame(0.0, 59.8))
        calibrator.accept(frame(.1, 60.0))
        val baseline = calibrator.accept(frame(.2, 60.2))
        assertEquals(60.0, baseline.neutralSpanMm!!, 1e-9)
        val opened = calibrator.accept(frame(.3, 70.0))
        assertEquals(10.0, opened.changeMm!!, 1e-9)
        assertEquals(-5.0, calibrator.accept(frame(.4, 55.0)).changeMm!!, 1e-9)
    }

    @Test
    fun spanDoesNotTurnRigidHeadTranslationOrRotationIntoJawChange() {
        val calibrator = calibrated()
        // Rotate both surface points 90 degrees around Z, then translate together.
        val moved = frame(.3).copy(nose = MetricSurfacePoint(.1, .2, .7),
            chin = MetricSurfacePoint(.04, .2, .7))
        assertEquals(0.0, calibrator.accept(moved).changeMm!!, 1e-9)
    }

    @Test
    fun relativeUnsynchronizedOrUnregisteredFramesInvalidateTheNeutralPose() {
        for (invalid in listOf(frame(.3).copy(absoluteDepth = false),
            frame(.3).copy(synchronized = false), frame(.3).copy(rectifiedAndRegistered = false),
            frame(.3).copy(depthCapturedAtSeconds = .31), frame(.3).copy(depthFrameId = ""),
            frame(.3).copy(chin = MetricSurfacePoint(0.0, Double.NaN, .5)))) {
            val calibrator = calibrated()
            val result = calibrator.accept(invalid)
            assertNull(result.changeMm)
            assertNull(result.neutralSpanMm)
            assertEquals(SurfaceCalibrationStatus.NEEDS_NEUTRAL, calibrator.accept(frame(.4)).status)
        }
    }

    @Test
    fun everyTrackingAndCalibrationIdentityChangeRequiresNewNeutral() {
        for (changed in listOf(identity.copy(camera = "other"), identity.copy(format = "other"),
            identity.copy(session = "other"), identity.copy(face = "other"), identity.copy(calibration = "other"),
            identity.copy(registration = "other"))) {
            val result = calibrated().accept(frame(.3).copy(identity = changed))
            assertEquals(SurfaceCalibrationStatus.TRACKING_CHANGED, result.status)
            assertNull(result.changeMm)
        }
    }

    @Test
    fun trackingLossGapsAndLateFramesCannotKeepAnOldCalibration() {
        for (time in listOf(.1, .2, 1.0, Double.NaN)) {
            val result = calibrated().accept(frame(time))
            assertEquals(SurfaceCalibrationStatus.STALE_FRAME, result.status)
            assertNull(result.changeMm)
        }
        val calibrator = calibrated()
        assertEquals(SurfaceCalibrationStatus.STALE_FRAME,
            calibrator.accept(frame(.3).copy(depthFrameId = "depth-0.2")).status)
        calibrator.accept(null)
        assertNull(calibrator.accept(frame(.3)).changeMm)
        calibrator.beginNeutralCalibration()
        assertEquals(SurfaceCalibrationStatus.COLLECTING_NEUTRAL, calibrator.accept(frame(.4)).status)
    }

    @Test
    fun reusedVideoOrDepthObservationsCannotCountTowardTheNeutralWindow() {
        val repeatedInputs = listOf(
            frame(.001).copy(videoFrameId = "video-0.0"),
            frame(.001).copy(depthFrameId = "depth-0.0"),
            frame(.001).copy(depthCapturedAtSeconds = 0.0)
        )
        for (repeated in repeatedInputs) {
            val calibrator = SurfaceSpanCalibrator(policy)
            calibrator.beginNeutralCalibration()
            calibrator.accept(frame(0.0))
            val rejected = calibrator.accept(repeated)
            assertEquals(SurfaceCalibrationStatus.STALE_FRAME, rejected.status)
            assertEquals(0, rejected.acceptedSamples)
            assertNull(rejected.neutralSpanMm)
            assertEquals(SurfaceCalibrationStatus.NEEDS_NEUTRAL, calibrator.accept(frame(.1)).status)
        }
        // Different frame IDs do not make an older depth timestamp a new observation.
        assertEquals(SurfaceCalibrationStatus.STALE_FRAME,
            calibrated().accept(frame(.202).copy(depthCapturedAtSeconds = .199)).status)
    }

    @Test
    fun unstableCalibrationIsRejectedRatherThanAveragedIntoAFalseBaseline() {
        val calibrator = SurfaceSpanCalibrator(policy)
        calibrator.beginNeutralCalibration()
        calibrator.accept(frame(0.0))
        val result = calibrator.accept(frame(.1, 65.0))
        assertEquals(SurfaceCalibrationStatus.UNSTABLE_NEUTRAL, result.status)
        assertNull(result.neutralSpanMm)
        assertEquals(SurfaceCalibrationStatus.NEEDS_NEUTRAL, calibrator.accept(frame(.2)).status)
    }
}
