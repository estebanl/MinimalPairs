package com.minimalpairs.app.domain.srs

import com.minimalpairs.app.core.model.DrillType
import com.minimalpairs.app.core.model.PlaybackSpeed
import kotlin.math.max
import kotlin.math.roundToInt

data class Sm2State(
    val repetitions: Int = 0,
    val intervalDays: Int = 1,
    val easeFactor: Float = 2.5f,
    val masteryScore: Float = 0.0f
)

object ModifiedSm2Engine {

    /**
     * Derives SM-2 quality grade (0 to 5) from correctness, latency in seconds,
     * and consecutive errors.
     */
    fun calculateQuality(
        isCorrect: Boolean,
        latencySeconds: Float,
        consecutiveErrors: Int
    ): Int {
        if (!isCorrect) {
            return when {
                consecutiveErrors >= 3 -> 0
                consecutiveErrors == 2 -> 1
                else -> 2
            }
        }
        return when {
            latencySeconds <= 1.5f -> 5
            latencySeconds <= 3.0f -> 4
            else -> 3
        }
    }

    /**
     * Calculates the next SM-2 state based on quality grade [q] (0-5).
     */
    fun review(currentState: Sm2State, q: Int): Sm2State {
        val grade = q.coerceIn(0, 5)
        var reps = currentState.repetitions
        var interval = currentState.intervalDays
        var ef = currentState.easeFactor

        if (grade < 3) {
            reps = 0
            interval = 1
        } else {
            interval = when (reps) {
                0 -> 1
                1 -> 6
                else -> (interval * ef).roundToInt().coerceAtLeast(1)
            }
            reps += 1
        }

        // Standard SM-2 EF update formula
        val deltaEf = 0.1f - (5 - grade) * (0.08f + (5 - grade) * 0.02f)
        ef = max(1.3f, ef + deltaEf)

        // Continuous mastery score between 0.0 and 1.0
        val mastery = ((reps * 0.2f) + (ef / 3.0f * 0.4f) + (grade / 5.0f * 0.4f)).coerceIn(0.0f, 1.0f)

        return Sm2State(
            repetitions = reps,
            intervalDays = interval,
            easeFactor = ef,
            masteryScore = mastery
        )
    }
}

class PhonemeConfusionTracker {
    // Directional confusion map: targetPhoneme -> (chosenPhoneme -> count)
    private val confusionMatrix = mutableMapOf<String, MutableMap<String, Int>>()

    // Confidence weight per phoneme: 0.0 to 1.0
    private val confidenceWeights = mutableMapOf<String, Float>()

    fun recordTrial(targetPhoneme: String, chosenPhoneme: String, isCorrect: Boolean) {
        val targetMap = confusionMatrix.getOrPut(targetPhoneme) { mutableMapOf() }
        targetMap[chosenPhoneme] = (targetMap[chosenPhoneme] ?: 0) + 1

        val currentWeight = confidenceWeights.getOrElse(targetPhoneme) { 0.5f }
        if (isCorrect) {
            confidenceWeights[targetPhoneme] = (currentWeight + 0.1f).coerceAtMost(1.0f)
        } else {
            confidenceWeights[targetPhoneme] = (currentWeight - 0.15f).coerceAtLeast(0.0f)
        }
    }

    fun getConfusionCount(target: String, chosen: String): Int {
        return confusionMatrix[target]?.get(chosen) ?: 0
    }

    fun getConfidenceWeight(phoneme: String): Float {
        return confidenceWeights[phoneme] ?: 0.5f
    }

    fun getAllConfusions(): Map<String, Map<String, Int>> {
        return confusionMatrix.mapValues { it.value.toMap() }
    }

    fun getAllWeights(): Map<String, Float> {
        return confidenceWeights.toMap()
    }
}

class DrillProgressionManager {
    var consecutiveFailures: Int = 0
        private set

    var playbackSpeed: PlaybackSpeed = PlaybackSpeed.NORMAL
        private set

    var showFormantVisualizerHint: Boolean = false
        private set

    fun onTrialCompleted(isCorrect: Boolean): TrialFeedback {
        if (isCorrect) {
            consecutiveFailures = 0
            playbackSpeed = PlaybackSpeed.NORMAL
            showFormantVisualizerHint = false
            return TrialFeedback(
                isCorrect = true,
                speed = playbackSpeed,
                showFormantHint = false,
                consecutiveFailures = 0
            )
        } else {
            consecutiveFailures++
            if (consecutiveFailures >= 3) {
                playbackSpeed = PlaybackSpeed.SLOW_DIAGNOSTIC
                showFormantVisualizerHint = true
            }
            return TrialFeedback(
                isCorrect = false,
                speed = playbackSpeed,
                showFormantHint = showFormantVisualizerHint,
                consecutiveFailures = consecutiveFailures
            )
        }
    }

    fun resolveDrillType(masteryScore: Float): DrillType {
        return when {
            masteryScore > 0.7f -> DrillType.MASKING_NOISE
            masteryScore > 0.4f -> DrillType.ABX
            else -> DrillType.FORCED_CHOICE
        }
    }

    fun setSpeedManual(speed: PlaybackSpeed) {
        playbackSpeed = speed
    }

    fun dismissFormantHint() {
        showFormantVisualizerHint = false
    }

    data class TrialFeedback(
        val isCorrect: Boolean,
        val speed: PlaybackSpeed,
        val showFormantHint: Boolean,
        val consecutiveFailures: Int
    )
}
