package com.minimalpairs.app.core.cv

import com.minimalpairs.app.core.model.VisualIndicators
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f
) {
    fun distanceTo(other: LandmarkPoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

data class ArticulatoryBiometrics(
    val lipApertureRatio: Float,
    val jawDropMm: Float,
    val lipRoundingIndex: Float,
    val isApertureCompliant: Boolean,
    val isJawDropCompliant: Boolean
)

object FacialLandmarkAnalyzer {

    // Standard MediaPipe Face Mesh landmark indices
    const val IDX_UPPER_LIP = 13
    const val IDX_LOWER_LIP = 14
    const val IDX_MOUTH_LEFT = 61
    const val IDX_MOUTH_RIGHT = 291
    const val IDX_NOSE_BASE = 2
    const val IDX_CHIN = 152
    const val IDX_LEFT_EYE_OUTER = 33
    const val IDX_RIGHT_EYE_OUTER = 263

    /**
     * Extracts articulatory metrics from 468+ facial landmark points.
     */
    fun analyze(
        landmarks: Map<Int, LandmarkPoint>,
        targetIndicators: VisualIndicators? = null
    ): ArticulatoryBiometrics {
        val pUpperLip = landmarks[IDX_UPPER_LIP] ?: LandmarkPoint(0.5f, 0.6f)
        val pLowerLip = landmarks[IDX_LOWER_LIP] ?: LandmarkPoint(0.5f, 0.65f)
        val pMouthLeft = landmarks[IDX_MOUTH_LEFT] ?: LandmarkPoint(0.4f, 0.62f)
        val pMouthRight = landmarks[IDX_MOUTH_RIGHT] ?: LandmarkPoint(0.6f, 0.62f)
        val pNose = landmarks[IDX_NOSE_BASE] ?: LandmarkPoint(0.5f, 0.45f)
        val pChin = landmarks[IDX_CHIN] ?: LandmarkPoint(0.5f, 0.85f)

        // 1. Lip Aperture Ratio
        val lipVerticalDist = pUpperLip.distanceTo(pLowerLip)
        val mouthWidth = pMouthLeft.distanceTo(pMouthRight).coerceAtLeast(0.01f)
        val apertureRatio = lipVerticalDist / mouthWidth

        // 2. Jaw Drop (calibrated to approximate mm, normalized by face scale)
        val pLeftEye = landmarks[IDX_LEFT_EYE_OUTER] ?: LandmarkPoint(0.35f, 0.35f)
        val pRightEye = landmarks[IDX_RIGHT_EYE_OUTER] ?: LandmarkPoint(0.65f, 0.35f)
        val faceWidthRef = pLeftEye.distanceTo(pRightEye).coerceAtLeast(0.01f)

        // Normal adult inter-pupil/eye distance ~ 95mm outer corner distance
        val scaleMmPerUnit = 95.0f / faceWidthRef
        val rawJawDrop = pNose.distanceTo(pChin)
        // Baseline closed mouth nose-to-chin distance is ~ 60mm
        val baselineJawMm = 62.0f
        val currentJawMm = rawJawDrop * scaleMmPerUnit
        val estimatedJawDropMm = (currentJawMm - baselineJawMm).coerceIn(3.0f, 35.0f)

        // 3. Lip Rounding Index
        val rounding = ((mouthWidth / (lipVerticalDist.coerceAtLeast(0.005f) * 3.5f))).coerceIn(0.2f, 1.0f)

        val apertureCompliant = targetIndicators?.let {
            abs(apertureRatio - it.lipApertureRatio) <= 0.12f
        } ?: true

        val jawCompliant = targetIndicators?.let {
            estimatedJawDropMm in (it.jawDropMinMm - 4f)..(it.jawDropMaxMm + 4f)
        } ?: true

        return ArticulatoryBiometrics(
            lipApertureRatio = apertureRatio,
            jawDropMm = estimatedJawDropMm,
            lipRoundingIndex = rounding,
            isApertureCompliant = apertureCompliant,
            isJawDropCompliant = jawCompliant
        )
    }
}
