package com.minimalpairs.app.core.cv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/** Points in image coordinates with equal units on both axes (pixels, for example). */
data class LipPoint(val x: Float, val y: Float)
data class LipMeasurement(val openingRatio: Float, val outerContour: List<LipPoint>, val innerContour: List<LipPoint>,
    /** Projected contour circularity, not lip protrusion or a pronunciation score. */
    val roundingIndex: Float = LipShapeAnalyzer.circularity(outerContour))

data class LipComparison(val openingChange: Float, val roundingChange: Float)

/** A learner-selected pose is a repeatability reference, never a validated target. */
fun LipMeasurement.compareWith(reference: LipMeasurement) = LipComparison(
    openingRatio - reference.openingRatio, roundingIndex - reference.roundingIndex)


enum class LipCameraStatus(val active: Boolean) {
    OFF(false), STARTING(true), TRACKING(true), NO_FACE(true), ADJUST_POSITION(true),
    PERMISSION_DENIED(false), UNAVAILABLE(false), ERROR(false), PAUSED(false)
}
data class LipCameraFrame(val status: LipCameraStatus, val measurement: LipMeasurement? = null,
    val capturedAtSeconds: Double? = null, val inferenceDurationMs: Double? = null)
data class LipCameraPerformance(val framesPerSecond: Double?, val meanInferenceMs: Double,
    val maxInferenceMs: Double, val sampleCount: Int)
data class LipCameraState(val session: Long = 0, val status: LipCameraStatus = LipCameraStatus.OFF,
    val measurement: LipMeasurement? = null, val frameIndex: Long = 0,
    val performance: LipCameraPerformance? = null)

/** One pending UI callback, with the freshest result; a terminal error cannot be overwritten. */
class LipFrameMailbox(private val schedule: (() -> Unit) -> Unit, private val deliver: (LipCameraFrame) -> Unit) {
    private data class Pending(val frame: LipCameraFrame? = null, val scheduled: Boolean = false)
    private val pending = MutableStateFlow(Pending())
    fun offer(frame: LipCameraFrame) {
        while (true) {
            val current = pending.value
            val newest = current.frame?.takeIf { !it.status.active } ?: frame
            if (pending.compareAndSet(current, Pending(newest, true))) {
                if (!current.scheduled) schedule {
                    while (true) {
                        val ready = pending.value
                        if (pending.compareAndSet(ready, Pending())) {
                            ready.frame?.let(deliver)
                            break
                        }
                    }
                }
                return
            }
        }
    }
}

/** Phase-aware ~30Hz admission with 1ms capture jitter tolerance and no catch-up bursts. */
class LipFramePacer {
    private var lastAccepted: Double? = null
    private var nextDeadline: Double? = null
    private val intervalSeconds = 1.0 / 30.0
    private val jitterToleranceSeconds = .001
    fun shouldAnalyze(timestamp: Double): Boolean {
        if (!timestamp.isFinite() || timestamp < 0.0) return false
        val previous = lastAccepted
        if (previous != null) {
            // Preserve cadence phase rather than moving the deadline to each jittered
            // arrival. Also bound adjacent starts even after a delayed frame.
            if (timestamp <= previous || timestamp - previous < intervalSeconds - 2 * jitterToleranceSeconds - 1e-9) return false
            if (timestamp < nextDeadline!! - jitterToleranceSeconds - 1e-9) return false
        }
        val followingDeadline = nextDeadline?.plus(intervalSeconds) ?: (timestamp + intervalSeconds)
        // Discard missed slots after a stall; never accumulate a catch-up allowance.
        nextDeadline = if (followingDeadline <= timestamp + jitterToleranceSeconds) timestamp + intervalSeconds else followingDeadline
        lastAccepted = timestamp
        return true
    }
}

/** Timestamp-aware 1-Euro filter (min cutoff 1Hz, beta .05, derivative cutoff 1Hz). */
class LipOneEuroFilter {
    private var timestamp: Double? = null
    private var previousRaw = 0.0
    private var filtered = 0.0
    private var derivative = 0.0
    fun reset() { timestamp = null }
    fun filter(value: Float, time: Double): Float {
        require(value.isFinite() && time.isFinite())
        val previousTime = timestamp
        if (previousTime == null || time <= previousTime || time - previousTime > .5) {
            timestamp = time
            previousRaw = value.toDouble()
            filtered = value.toDouble()
            derivative = 0.0
            return value
        }
        val dt = time - previousTime
        fun alpha(cutoff: Double) = 1.0 / (1.0 + 1.0 / (2.0 * PI * cutoff * dt))
        val rawDerivative = (value - previousRaw) / dt
        derivative += alpha(1.0) * (rawDerivative - derivative)
        filtered += alpha(1.0 + .05 * abs(derivative)) * (value - filtered)
        timestamp = time
        previousRaw = value.toDouble()
        return filtered.toFloat()
    }
}

/** Session tokens prevent delayed permission or detector callbacks from reopening a stopped session. */
class LipCameraController {
    private val mutableState = MutableStateFlow(LipCameraState())
    private val openingFilter = LipOneEuroFilter()
    private val roundingFilter = LipOneEuroFilter()
    private val timings = mutableListOf<Pair<Double, Double>>()
    private var lastTimestamp: Double? = null
    val state = mutableState.asStateFlow()
    private fun resetMetrics() {
        openingFilter.reset()
        roundingFilter.reset()
        timings.clear()
        lastTimestamp = null
    }
    fun start() {
        if (mutableState.value.status.active) return
        resetMetrics()
        mutableState.value = LipCameraState(mutableState.value.session + 1, LipCameraStatus.STARTING)
    }
    fun stop(status: LipCameraStatus = LipCameraStatus.OFF) {
        require(!status.active)
        resetMetrics()
        mutableState.value = LipCameraState(mutableState.value.session + 1, status)
    }
    fun accept(session: Long, frame: LipCameraFrame) {
        val current = mutableState.value
        if (current.session != session || !current.status.active) return
        val time = frame.capturedAtSeconds?.takeIf { it.isFinite() && it >= 0.0 }
        if (frame.capturedAtSeconds != null && time == null) return
        if (time != null && lastTimestamp?.let { time <= it } == true) return
        if (time != null && lastTimestamp?.let { time - it > .5 } == true) timings.clear()
        if (time != null) lastTimestamp = time
        val raw = frame.measurement?.takeIf {
            frame.status == LipCameraStatus.TRACKING && it.openingRatio.isFinite() &&
                it.openingRatio in 0f..1.2f && it.roundingIndex.isFinite() && it.roundingIndex in 0f..1f &&
                it.outerContour.isNotEmpty() && it.innerContour.isNotEmpty() &&
                (it.outerContour + it.innerContour).all { point -> point.x.isFinite() && point.y.isFinite() }
        }
        // Filter metrics with capture timestamps. Missing timing or tracking loss resets history.
        val sample = if (raw != null && time != null) raw.copy(
            openingRatio = openingFilter.filter(raw.openingRatio, time),
            roundingIndex = roundingFilter.filter(raw.roundingIndex, time)
        ) else {
            openingFilter.reset()
            roundingFilter.reset()
            raw
        }
        val duration = frame.inferenceDurationMs?.takeIf { it.isFinite() && it >= 0.0 }
        if (time != null && duration != null) {
            timings += time to duration
            if (timings.size > 30) timings.removeAt(0)
        } else if (frame.status != LipCameraStatus.TRACKING) timings.clear()
        val performance = timings.takeIf { it.isNotEmpty() }?.let {
            val elapsed = it.last().first - it.first().first
            LipCameraPerformance(if (it.size > 1 && elapsed > 0) (it.size - 1) / elapsed else null,
                it.sumOf { timing -> timing.second } / it.size, it.maxOf { timing -> timing.second }, it.size)
        }
        val status = if (frame.status == LipCameraStatus.TRACKING && sample == null) LipCameraStatus.ADJUST_POSITION else frame.status
        mutableState.value = LipCameraState(session, status, sample, current.frameIndex + 1, performance)
    }
}

object LipShapeAnalyzer {
    /** 4πA/P² of the ordered outer contour. Scale and in-plane rotation independent. */
    fun circularity(contour: List<LipPoint>): Float {
        if (contour.size < 3 || contour.any { !it.x.isFinite() || !it.y.isFinite() }) return 0f
        var twiceArea = 0.0
        var perimeter = 0.0
        contour.indices.forEach { index ->
            val a = contour[index]
            val b = contour[(index + 1) % contour.size]
            twiceArea += a.x.toDouble() * b.y - b.x.toDouble() * a.y
            val dx = a.x.toDouble() - b.x
            val dy = a.y.toDouble() - b.y
            perimeter += sqrt(dx * dx + dy * dy)
        }
        if (perimeter <= 0.0) return 0f
        return (2 * PI * abs(twiceArea) / (perimeter * perimeter)).toFloat().coerceIn(0f, 1f)
    }

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
