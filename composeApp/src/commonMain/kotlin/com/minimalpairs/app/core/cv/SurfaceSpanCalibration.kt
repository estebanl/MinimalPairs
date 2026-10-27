package com.minimalpairs.app.core.cv

import kotlin.math.sqrt
import kotlin.math.abs

/** Camera-space metres from an explicitly registered, rectified, absolute-depth source. */
data class MetricSurfacePoint(val x: Double, val y: Double, val z: Double) {
    fun isValid(): Boolean = x.isFinite() && y.isFinite() && z.isFinite() && z > 0.0
    fun distanceTo(other: MetricSurfacePoint): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/** Intrinsics must use the SAME rectified pixel coordinates as the supplied sample. */
data class RectifiedDepthIntrinsics(
    val width: Int, val height: Int,
    val focalX: Double, val focalY: Double, val principalX: Double, val principalY: Double
) {
    fun backProject(x: Double, y: Double, depthMetres: Double): MetricSurfacePoint? {
        if (width <= 0 || height <= 0 || !focalX.isFinite() || !focalY.isFinite() ||
            focalX <= 0.0 || focalY <= 0.0 || !principalX.isFinite() || !principalY.isFinite() ||
            !x.isFinite() || !y.isFinite() || x < 0 || y < 0 || x >= width || y >= height ||
            !depthMetres.isFinite() || depthMetres <= 0.0) return null
        return MetricSurfacePoint((x - principalX) * depthMetres / focalX,
            (y - principalY) * depthMetres / focalY, depthMetres).takeIf { it.isValid() }
    }
}

/** Native registration owns these identities; changing any one invalidates a neutral baseline. */
data class SurfaceDepthIdentity(val camera: String, val format: String, val session: String,
    val face: String, val calibration: String, val registration: String) {
    fun isValid(): Boolean = listOf(camera, format, session, face, calibration, registration).all { it.isNotBlank() }
}

/**
 * The native caller asserts the provenance flags after checking synchronized frame metadata and
 * transforms. This shared type cannot certify those assertions or the sensor's physical accuracy.
 */
data class RegisteredSurfaceSpanFrame(
    val identity: SurfaceDepthIdentity,
    val videoFrameId: String,
    val depthFrameId: String,
    val capturedAtSeconds: Double,
    val depthCapturedAtSeconds: Double,
    val nose: MetricSurfacePoint,
    val chin: MetricSurfacePoint,
    val absoluteDepth: Boolean,
    val synchronized: Boolean,
    val rectifiedAndRegistered: Boolean
)

/** No population defaults: thresholds must come from an independently validated device protocol. */
data class SurfaceCalibrationPolicy(val minimumSamples: Int, val minimumWindowSeconds: Double,
    val maximumGapSeconds: Double, val maximumNeutralSpreadMm: Double,
    val maximumSynchronizationSkewSeconds: Double) {
    init {
        require(minimumSamples in 2..300)
        require(minimumWindowSeconds.isFinite() && minimumWindowSeconds > 0.0)
        require(maximumGapSeconds.isFinite() && maximumGapSeconds > 0.0)
        require(maximumNeutralSpreadMm.isFinite() && maximumNeutralSpreadMm > 0.0)
        require(maximumSynchronizationSkewSeconds.isFinite() && maximumSynchronizationSkewSeconds >= 0.0)
    }
}

enum class SurfaceCalibrationStatus {
    NEEDS_NEUTRAL, COLLECTING_NEUTRAL, READY, INVALID_DEPTH, INVALID_REGISTRATION,
    TRACKING_CHANGED, STALE_FRAME, UNSTABLE_NEUTRAL
}

/** This measures surface nose-to-chin span change, NOT jaw-bone displacement or dental separation. */
data class SurfaceSpanResult(val status: SurfaceCalibrationStatus, val acceptedSamples: Int = 0,
    val neutralSpanMm: Double? = null, val currentSpanMm: Double? = null, val changeMm: Double? = null)

/** UI callers must request a neutral pose explicitly. Missing/tracking-loss input invalidates it. */
class SurfaceSpanCalibrator(private val policy: SurfaceCalibrationPolicy) {
    private var collecting = false
    private var identity: SurfaceDepthIdentity? = null
    private var lastTime: Double? = null
    private var lastDepthTime: Double? = null
    private var lastVideoFrameId: String? = null
    private var lastDepthFrameId: String? = null
    private var firstTime: Double? = null
    private val neutralSamples = mutableListOf<Double>()
    private var smallestNeutral = Double.POSITIVE_INFINITY
    private var largestNeutral = Double.NEGATIVE_INFINITY
    private var neutral: Double? = null

    fun beginNeutralCalibration() {
        invalidate()
        collecting = true
    }

    fun invalidate() {
        collecting = false
        identity = null
        lastTime = null
        lastDepthTime = null
        lastVideoFrameId = null
        lastDepthFrameId = null
        firstTime = null
        neutralSamples.clear()
        smallestNeutral = Double.POSITIVE_INFINITY
        largestNeutral = Double.NEGATIVE_INFINITY
        neutral = null
    }

    private fun unavailable(status: SurfaceCalibrationStatus): SurfaceSpanResult {
        invalidate()
        return SurfaceSpanResult(status)
    }

    fun accept(frame: RegisteredSurfaceSpanFrame?): SurfaceSpanResult {
        if (frame == null) return unavailable(SurfaceCalibrationStatus.NEEDS_NEUTRAL)
        if (!frame.absoluteDepth || !frame.nose.isValid() || !frame.chin.isValid()) {
            return unavailable(SurfaceCalibrationStatus.INVALID_DEPTH)
        }
        if (!frame.synchronized || !frame.rectifiedAndRegistered || !frame.identity.isValid() ||
            frame.videoFrameId.isBlank() || frame.depthFrameId.isBlank()) {
            return unavailable(SurfaceCalibrationStatus.INVALID_REGISTRATION)
        }
        val time = frame.capturedAtSeconds
        if (!time.isFinite() || time < 0.0 || lastTime?.let { time <= it || time - it > policy.maximumGapSeconds } == true) {
            return unavailable(SurfaceCalibrationStatus.STALE_FRAME)
        }
        if (frame.videoFrameId == lastVideoFrameId || frame.depthFrameId == lastDepthFrameId) {
            return unavailable(SurfaceCalibrationStatus.STALE_FRAME)
        }
        val depthTime = frame.depthCapturedAtSeconds
        if (!depthTime.isFinite() || depthTime < 0.0 || abs(time - depthTime) > policy.maximumSynchronizationSkewSeconds) {
            return unavailable(SurfaceCalibrationStatus.INVALID_REGISTRATION)
        }
        if (lastDepthTime?.let { depthTime <= it } == true) {
            return unavailable(SurfaceCalibrationStatus.STALE_FRAME)
        }
        if (identity?.let { it != frame.identity } == true) return unavailable(SurfaceCalibrationStatus.TRACKING_CHANGED)
        val span = frame.nose.distanceTo(frame.chin) * 1000.0
        if (!span.isFinite() || span <= 0.0) return unavailable(SurfaceCalibrationStatus.INVALID_DEPTH)
        identity = frame.identity
        lastTime = time
        lastDepthTime = depthTime
        lastVideoFrameId = frame.videoFrameId
        lastDepthFrameId = frame.depthFrameId
        if (collecting) {
            if (firstTime == null) firstTime = time
            neutralSamples += span
            // Bound memory even when the caller requests a long stable collection window.
            if (neutralSamples.size > 300) neutralSamples.removeAt(0)
            smallestNeutral = minOf(smallestNeutral, span)
            largestNeutral = maxOf(largestNeutral, span)
            if (largestNeutral - smallestNeutral > policy.maximumNeutralSpreadMm) {
                return unavailable(SurfaceCalibrationStatus.UNSTABLE_NEUTRAL)
            }
            if (neutralSamples.size < policy.minimumSamples || time - firstTime!! < policy.minimumWindowSeconds) {
                return SurfaceSpanResult(SurfaceCalibrationStatus.COLLECTING_NEUTRAL, neutralSamples.size)
            }
            val sorted = neutralSamples.sorted()
            neutral = if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                else sorted[sorted.size / 2]
            collecting = false
        }
        val baseline = neutral ?: return SurfaceSpanResult(SurfaceCalibrationStatus.NEEDS_NEUTRAL)
        return SurfaceSpanResult(SurfaceCalibrationStatus.READY, neutralSamples.size, baseline, span, span - baseline)
    }
}
