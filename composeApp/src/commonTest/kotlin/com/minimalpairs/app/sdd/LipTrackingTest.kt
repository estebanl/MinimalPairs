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
}
