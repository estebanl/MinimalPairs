package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.cv.*
import kotlin.math.*
import kotlin.test.*

class LipTrackingTest {
    private fun ellipse(width: Float, height: Float) = List(20) { i ->
        val angle = i * 2 * PI.toFloat() / 20
        LipPoint(cos(angle) * width / 2, sin(angle) * height / 2)
    }
    private val outer = ellipse(100f, 40f)
    private val inner = ellipse(80f, 20f)
    private val sample get() = LipShapeAnalyzer.measure(outer, inner)!!

    @Test
    fun circularityDistinguishesRoundAndSpreadContoursWithoutDependingOnScale() {
        val round = LipShapeAnalyzer.circularity(ellipse(100f, 100f))
        val spread = LipShapeAnalyzer.circularity(outer)
        assertTrue(round > .95f)
        assertTrue(spread < round)
        val moved = outer.map { LipPoint(300f + it.x * 3f, 100f + it.y * 3f) }
        assertEquals(spread, LipShapeAnalyzer.circularity(moved), .00001f)
        assertEquals(spread, LipShapeAnalyzer.circularity(outer.reversed()), .00001f)
        assertEquals(0f, LipShapeAnalyzer.circularity(List(4) { LipPoint(0f, 0f) }))
    }

    @Test
    fun metricsSmoothJitterButTrackingLossResetsTheFilter() {
        val controller = LipCameraController()
        controller.start()
        val token = controller.state.value.session
        controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample, capturedAtSeconds = 0.0))
        val changed = sample.copy(openingRatio = .4f, roundingIndex = .9f)
        controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, changed, capturedAtSeconds = .1))
        assertTrue(controller.state.value.measurement!!.openingRatio in .201f.. .399f)
        assertTrue(controller.state.value.measurement!!.roundingIndex < .9f)
        controller.accept(token, LipCameraFrame(LipCameraStatus.NO_FACE))
        controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, changed))
        assertEquals(changed, controller.state.value.measurement)
        controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample.copy(openingRatio = Float.NaN)))
        assertNull(controller.state.value.measurement)
        assertEquals(LipCameraStatus.ADJUST_POSITION, controller.state.value.status)
    }

    @Test
    fun personalComparisonReportsSignedChangesWithoutAssigningCorrectness() {
        val comparison = sample.copy(openingRatio = .35f, roundingIndex = .8f).compareWith(
            sample.copy(openingRatio = .2f, roundingIndex = .9f))
        assertEquals(.15f, comparison.openingChange, .00001f)
        assertEquals(-.1f, comparison.roundingChange, .00001f)
    }

    @Test
    fun openingIsScaleTranslationAndSmallRollIndependent() {
        assertEquals(.2f, sample.openingRatio, .001f)
        val angle = .15f
        fun transform(p: LipPoint) = LipPoint(300 + 2 * (p.x * cos(angle) - p.y * sin(angle)),
            200 + 2 * (p.x * sin(angle) + p.y * cos(angle)))
        val moved = LipShapeAnalyzer.measure(outer.map(::transform), inner.map(::transform))!!
        assertEquals(sample.openingRatio, moved.openingRatio, .001f)
    }

    @Test
    fun missingDegenerateAndInvalidLandmarksNeverProduceMeasurements() {
        assertNull(LipShapeAnalyzer.measure(emptyList(), inner))
        assertNull(LipShapeAnalyzer.measure(outer, emptyList()))
        assertNull(LipShapeAnalyzer.measure(List(8) { LipPoint(1f, 1f) }, inner))
        assertNull(LipShapeAnalyzer.measure(outer + LipPoint(Float.NaN, 1f), inner))
        assertNull(LipShapeAnalyzer.measure(outer, inner.map { LipPoint(it.x, it.y + 500) }))
        assertNull(LipShapeAnalyzer.measure(outer.map { LipPoint(it.y, it.x) }, inner))
    }

    @Test
    fun closedLipsAreAValidZeroOpening() {
        val result = LipShapeAnalyzer.measure(outer, inner.map { it.copy(y = 0f) })!!
        assertEquals(0f, result.openingRatio, .000001f)
    }

    @Test
    fun lostTrackingClearsMeasurementsAndCanRecover() {
        val controller = LipCameraController()
        controller.start()
        val token = controller.state.value.session
        controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample))
        assertNotNull(controller.state.value.measurement)
        for (status in listOf(LipCameraStatus.NO_FACE, LipCameraStatus.ADJUST_POSITION)) {
            controller.accept(token, LipCameraFrame(status, sample))
            assertNull(controller.state.value.measurement)
            controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample))
            assertNotNull(controller.state.value.measurement)
        }
        controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING))
        assertEquals(LipCameraStatus.ADJUST_POSITION, controller.state.value.status)
        assertNull(controller.state.value.measurement)
    }

    @Test
    fun oldCallbacksCannotReviveStoppedDeniedOrRestartedSessions() {
        for (terminal in listOf(LipCameraStatus.PERMISSION_DENIED, LipCameraStatus.ERROR, LipCameraStatus.UNAVAILABLE)) {
            val controller = LipCameraController()
            controller.start()
            val oldToken = controller.state.value.session
            controller.accept(oldToken, LipCameraFrame(terminal))
            controller.accept(oldToken, LipCameraFrame(LipCameraStatus.TRACKING, sample))
            assertEquals(terminal, controller.state.value.status)
            controller.start()
            val newToken = controller.state.value.session
            controller.accept(oldToken, LipCameraFrame(LipCameraStatus.TRACKING, sample))
            assertEquals(LipCameraStatus.STARTING, controller.state.value.status)
            controller.accept(newToken, LipCameraFrame(LipCameraStatus.TRACKING, sample))
            controller.stop(LipCameraStatus.PAUSED)
            controller.accept(newToken, LipCameraFrame(LipCameraStatus.TRACKING, sample))
            assertEquals(LipCameraStatus.PAUSED, controller.state.value.status)
            assertNull(controller.state.value.measurement)
        }
    }

    @Test
    fun identicalFramesStillAdvanceTheFreshnessCounter() {
        val controller = LipCameraController()
        controller.start()
        val token = controller.state.value.session
        repeat(3) { controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample)) }
        assertEquals(3, controller.state.value.frameIndex)
    }

    @Test
    fun framePacerAdmitsThirtyFpsAndSkipsExtraOrInvalidFrames() {
        val pacer = LipFramePacer()
        var accepted = 0
        repeat(120) { if (pacer.shouldAnalyze(it / 60.0)) accepted++ }
        assertEquals(60, accepted)
        assertFalse(pacer.shouldAnalyze(Double.NaN))
        assertFalse(pacer.shouldAnalyze(-1.0))
        assertFalse(pacer.shouldAnalyze(.1))
        assertTrue(pacer.shouldAnalyze(10.0))
        assertFalse(pacer.shouldAnalyze(10.001))
    }

    @Test
    fun nominalThirtyHzCaptureJitterDoesNotHalveAnalysisRate() {
        // Regression: the former last-start + 33.333ms gate admitted only 151/300
        // of these frames because every other interval is 0.4ms shorter.
        for (jitter in listOf(.0004, -.0004)) {
            val pacer = LipFramePacer()
            val accepted = (0 until 300).count { frame ->
                pacer.shouldAnalyze(10 + frame / 30.0 + if (frame % 2 == 1) jitter else 0.0)
            }
            assertEquals(300, accepted, "A 30Hz source with submillisecond jitter should retain its cadence")
        }
    }

    @Test
    fun jitteredSixtyHzInputRemainsBoundedToThirtyHzWithoutBursts() {
        val pacer = LipFramePacer()
        val accepted = (0 until 600).map { frame ->
            10 + frame / 60.0 + (frame % 5 - 2) * .0002
        }.filter(pacer::shouldAnalyze)
        assertEquals(300, accepted.size)
        assertTrue(accepted.zipWithNext().all { (a, b) -> b - a >= 1.0 / 30 - .002 - 1e-9 })
        // A faster producer cannot exploit the jitter allowance repeatedly.
        val fastPacer = LipFramePacer()
        val fastAccepted = (0 until 10000).count { fastPacer.shouldAnalyze(it / 1000.0) }
        assertTrue(fastAccepted in 300..301, "Expected ~30Hz over 10s, got $fastAccepted")
    }

    @Test
    fun framePacerDiscardsMissedSlotsAndRejectsOutOfOrderCaptureTimes() {
        val pacer = LipFramePacer()
        assertTrue(pacer.shouldAnalyze(0.0))
        assertTrue(pacer.shouldAnalyze(1.0 / 30))
        assertTrue(pacer.shouldAnalyze(10.0))
        assertFalse(pacer.shouldAnalyze(10.001), "A stall must not allow a catch-up burst")
        assertFalse(pacer.shouldAnalyze(9.0))
        assertFalse(pacer.shouldAnalyze(10.0))
        assertFalse(pacer.shouldAnalyze(Double.POSITIVE_INFINITY))
        assertFalse(pacer.shouldAnalyze(Double.NaN))
        assertTrue(pacer.shouldAnalyze(10.0 + 1.0 / 30 - .0004))
        assertFalse(pacer.shouldAnalyze(10.0 + 1.0 / 30 - .0005))
    }

    @Test
    fun slowUiReceivesOnlyLatestResultAndCannotLoseTerminalErrors() {
        val scheduled = mutableListOf<() -> Unit>()
        val delivered = mutableListOf<LipCameraFrame>()
        val mailbox = LipFrameMailbox(scheduled::add, delivered::add)
        repeat(60) { mailbox.offer(LipCameraFrame(LipCameraStatus.TRACKING, sample, it / 30.0)) }
        assertEquals(1, scheduled.size)
        scheduled.removeAt(0).invoke()
        assertEquals(59 / 30.0, delivered.single().capturedAtSeconds)
        mailbox.offer(LipCameraFrame(LipCameraStatus.ERROR))
        mailbox.offer(LipCameraFrame(LipCameraStatus.TRACKING, sample, 3.0))
        assertEquals(1, scheduled.size)
        scheduled.removeAt(0).invoke()
        assertEquals(LipCameraStatus.ERROR, delivered.last().status)
    }

    @Test
    fun oneEuroFilterUsesElapsedTimeAndResetsAfterLongGap() {
        val fast = LipOneEuroFilter()
        val slow = LipOneEuroFilter()
        fast.filter(0f, 0.0)
        slow.filter(0f, 0.0)
        val at30Fps = fast.filter(.5f, 1.0 / 30)
        val at10Fps = slow.filter(.5f, .1)
        assertTrue(at30Fps > 0f && at30Fps < at10Fps)
        assertTrue(at10Fps < .5f)
        assertEquals(.8f, fast.filter(.8f, 1.0))
        fast.reset()
        assertEquals(.2f, fast.filter(.2f, 1.1))
    }

    @Test
    fun captureOrderingAndPerformanceWindowReflectProcessedFramesOnly() {
        val controller = LipCameraController()
        controller.start()
        val token = controller.state.value.session
        repeat(40) { frame ->
            controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample,
                capturedAtSeconds = frame / 30.0, inferenceDurationMs = 20.0))
        }
        val performance = controller.state.value.performance!!
        assertEquals(30, performance.sampleCount)
        assertEquals(30.0, performance.framesPerSecond!!, .0001)
        assertEquals(20.0, performance.meanInferenceMs)
        assertEquals(20.0, performance.maxInferenceMs)
        val previous = controller.state.value
        controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample.copy(openingRatio = .9f),
            capturedAtSeconds = .01, inferenceDurationMs = 1.0))
        assertEquals(previous, controller.state.value)
        controller.accept(token, LipCameraFrame(LipCameraStatus.NO_FACE))
        assertNull(controller.state.value.performance, "Freshness timeout must not retain old FPS")
        controller.accept(token, LipCameraFrame(LipCameraStatus.TRACKING, sample,
            capturedAtSeconds = 3.0, inferenceDurationMs = 25.0))
        assertEquals(1, controller.state.value.performance!!.sampleCount)
        assertNull(controller.state.value.performance!!.framesPerSecond)
        controller.stop()
        assertNull(controller.state.value.performance)
        controller.start()
        controller.accept(controller.state.value.session, LipCameraFrame(LipCameraStatus.NO_FACE,
            capturedAtSeconds = 4.0, inferenceDurationMs = 40.0))
        assertNull(controller.state.value.performance!!.framesPerSecond)
        assertEquals(1, controller.state.value.performance!!.sampleCount)
    }
}
