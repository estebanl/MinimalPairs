package com.minimalpairs.app.core.model

import kotlinx.serialization.Serializable

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
    val gopScore: Float,
    val startTime: Float,
    val endTime: Float
)

@Serializable
data class PhonemeScoringResponse(
    val overallScore: Float,
    val phonemes: List<PhonemeScore>
)
