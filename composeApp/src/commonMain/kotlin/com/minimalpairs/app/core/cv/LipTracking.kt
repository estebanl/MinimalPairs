package com.minimalpairs.app.core.cv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/** Points in image coordinates with equal units on both axes (pixels, for example). */
data class LipPoint(val x: Float, val y: Float)
data class LipMeasurement(val openingRatio: Float, val outerContour: List<LipPoint>, val innerContour: List<LipPoint>)

enum class LipCameraStatus(val active: Boolean) {
    OFF(false), STARTING(true), TRACKING(true), NO_FACE(true), ADJUST_POSITION(true),
    PERMISSION_DENIED(false), UNAVAILABLE(false), ERROR(false), PAUSED(false)
}
data class LipCameraFrame(val status: LipCameraStatus, val measurement: LipMeasurement? = null)
data class LipCameraState(val session: Long = 0, val status: LipCameraStatus = LipCameraStatus.OFF,
    val measurement: LipMeasurement? = null, val frameIndex: Long = 0)

/** Session tokens prevent delayed permission or detector callbacks from reopening a stopped session. */
class LipCameraController {
    private val mutableState = MutableStateFlow(LipCameraState())
    val state = mutableState.asStateFlow()
    fun start() {
        if (mutableState.value.status.active) return
        mutableState.value = LipCameraState(mutableState.value.session + 1, LipCameraStatus.STARTING)
    }
    fun stop(status: LipCameraStatus = LipCameraStatus.OFF) {
        require(!status.active)
        mutableState.value = LipCameraState(mutableState.value.session + 1, status)
    }
    fun accept(session: Long, frame: LipCameraFrame) {
        val current = mutableState.value
        if (current.session != session || !current.status.active) return
        val sample = frame.measurement?.takeIf { frame.status == LipCameraStatus.TRACKING }
        val status = if (frame.status == LipCameraStatus.TRACKING && sample == null) LipCameraStatus.ADJUST_POSITION else frame.status
        mutableState.value = LipCameraState(session, status, sample, current.frameIndex + 1)
    }
}

object LipShapeAnalyzer {
    /** No fallback points: a missing, nonfinite, tiny, or implausible contour is not a measurement. */
    fun measure(outer: List<LipPoint>, inner: List<LipPoint>): LipMeasurement? {
        if (outer.size < 6 || inner.size < 4 || (outer + inner).any { !it.x.isFinite() || !it.y.isFinite() }) return null
        // Mouth corners are the most widely separated outer-lip points for a near-frontal face.
        val pair = outer.flatMap { a -> outer.map { b -> a to b } }.maxByOrNull { (a, b) ->
            (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
        } ?: return null
        val left = if (pair.first.x <= pair.second.x) pair.first else pair.second
        val right = if (pair.first.x <= pair.second.x) pair.second else pair.first
        val dx = right.x - left.x
        val dy = right.y - left.y
        val width = sqrt(dx * dx + dy * dy)
        if (width < 8f || abs(dy) > abs(dx) * .6f) return null
        val ux = dx / width
        val uy = dy / width
        val cx = (left.x + right.x) / 2f
        val cy = (left.y + right.y) / 2f
        // Mirror horizontally to match the front-camera preview.
        fun normalize(p: LipPoint) = LipPoint(-((p.x - cx) * ux + (p.y - cy) * uy) / width,
            (-(p.x - cx) * uy + (p.y - cy) * ux) / width)
        val normalizedOuter = outer.map(::normalize)
        val normalizedInner = inner.map(::normalize)
        val opening = normalizedInner.maxOf { it.y } - normalizedInner.minOf { it.y }
        if (opening !in 0f..1.2f || normalizedInner.any { abs(it.x) > .65f || abs(it.y) > .65f }) return null
        return LipMeasurement(opening, normalizedOuter, normalizedInner)
    }
}
