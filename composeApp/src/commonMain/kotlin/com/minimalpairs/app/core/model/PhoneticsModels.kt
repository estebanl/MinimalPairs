package com.minimalpairs.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PhoneticBounds(
    val f1MinHz: Float,
    val f1MaxHz: Float,
    val f2MinHz: Float,
    val f2MaxHz: Float,
    val minDurationMs: Int? = null,
    val maxDurationMs: Int? = null
) {
    fun containsFormants(f1: Float, f2: Float): Boolean {
        return f1 in f1MinHz..f1MaxHz && f2 in f2MinHz..f2MaxHz
    }
}

@Serializable
data class VisualIndicators(
    val lipApertureRatio: Float,
    val jawDropMinMm: Float,
    val jawDropMaxMm: Float
)

@Serializable
data class MinimalPairWord(
    val word1: String,
    val word2: String
)

@Serializable
data class PhoneticContrast(
    val id: String,
    val phoneme1: String,
    val phoneme2: String,
    val description: String,
    val pairs: List<MinimalPairWord>,
    val boundsPhoneme1: PhoneticBounds,
    val boundsPhoneme2: PhoneticBounds,
    val visualIndicators: VisualIndicators
)

enum class DrillType {
    FORCED_CHOICE,
    ABX
}

enum class PlaybackSpeed(val multiplier: Float, val label: String) {
    NORMAL(1.0f, "1.0x"),
    SLOW_DIAGNOSTIC(0.75f, "0.75x")
}

@Serializable
data class PhonemeScore(
    val phoneme: String,
    @SerialName("gop_score") val gopScore: Float,
    @SerialName("start_time") val startTime: Float,
    @SerialName("end_time") val endTime: Float
)

@Serializable
data class PhonemeScoringResponse(
    @SerialName("overall_score") val overallScore: Float,
    val phonemes: List<PhonemeScore>,
    @SerialName("scoring_method") val scoringMethod: String = "unknown",
    @SerialName("model_id") val modelId: String? = null,
    val feedback: String? = null
) {
    val scoringDescription: String get() = when (scoringMethod) {
        "neural_ctc" -> "Model confidence (experimental) · ${modelId ?: "phoneme CTC"}. ${feedback ?: "Not validated for isolated-vowel coaching or calibrated pronunciation grading."}"
        "acoustic_estimate" -> "Offline acoustic estimate. ${feedback ?: "Based on vowel formants and duration, not word recognition."}"
        else -> "Scoring method unavailable."
    }
}
