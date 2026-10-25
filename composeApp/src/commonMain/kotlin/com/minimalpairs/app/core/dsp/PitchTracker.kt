package com.minimalpairs.app.core.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sqrt

data class PitchFrame(
    val timeSeconds: Float,
    val f0Hz: Float,
    val isVoiced: Boolean
)

data class ProsodyEvaluation(
    val intonationScore: Float, // 0 to 100
    val rhythmScore: Float,     // 0 to 100
    val overallScore: Float,    // 0 to 100
    val meanPitchHz: Float,
    val pitchRangeHz: Float,
    val userContour: List<PitchFrame>,
    val feedback: String
)

object PitchTracker {

    private const val SAMPLE_RATE = 16000
    private const val FRAME_LEN = 512 // 32ms
    private const val HOP_LEN = 160   // 10ms
    private const val MIN_F0 = 75.0f  // Hz
    private const val MAX_F0 = 450.0f // Hz
    private const val YIN_THRESHOLD = 0.18f

    /**
     * Extracts continuous F0 pitch track across the audio buffer.
     */
    fun trackPitch(samples: ShortArray): List<PitchFrame> {
        if (samples.size < FRAME_LEN) return emptyList()

        val numFrames = (samples.size - FRAME_LEN) / HOP_LEN + 1
        val frames = mutableListOf<PitchFrame>()

        val tauMin = (SAMPLE_RATE / MAX_F0).toInt().coerceAtLeast(20)
        val tauMax = (SAMPLE_RATE / MIN_F0).toInt().coerceAtMost(FRAME_LEN / 2)

        for (f in 0 until numFrames) {
            val start = f * HOP_LEN
            val timeSec = (start + FRAME_LEN / 2).toFloat() / SAMPLE_RATE

            // 1. Energy check
            var energySum = 0.0
            for (i in 0 until FRAME_LEN) {
                val s = samples[start + i].toDouble()
                energySum += s * s
            }
            val rms = sqrt(energySum / FRAME_LEN)
            if (rms < 300.0) { // Silence or unvoiced noise
                frames.add(PitchFrame(timeSec, 0.0f, false))
                continue
            }

            // 2. YIN Difference Function
            val d = DoubleArray(tauMax + 1)
            for (tau in 1..tauMax) {
                var sumDiff = 0.0
                for (j in 0 until (FRAME_LEN - tau)) {
                    val diff = samples[start + j].toDouble() - samples[start + j + tau].toDouble()
                    sumDiff += diff * diff
                }
                d[tau] = sumDiff
            }

            // 3. Cumulative Mean Normalized Difference
            val dPrime = DoubleArray(tauMax + 1)
            dPrime[0] = 1.0
            var runningSum = 0.0
            for (tau in 1..tauMax) {
                runningSum += d[tau]
                dPrime[tau] = if (runningSum > 1e-6) d[tau] / (runningSum / tau) else 1.0
            }

            // 4. Absolute thresholding for first minimum
            var bestTau = -1
            for (tau in tauMin..tauMax) {
                if (dPrime[tau] < YIN_THRESHOLD) {
                    while (tau + 1 <= tauMax && dPrime[tau + 1] < dPrime[tau]) {
                        // descend to local minimum
                        bestTau = tau + 1
                        break
                    }
                    if (bestTau == -1) bestTau = tau
                    break
                }
            }

            // Fallback to global minimum if threshold not crossed but signal is voiced
            if (bestTau == -1) {
                var minVal = Double.MAX_VALUE
                for (tau in tauMin..tauMax) {
                    if (dPrime[tau] < minVal) {
                        minVal = dPrime[tau]
                        bestTau = tau
                    }
                }
                if (minVal > 0.45) { // Not sufficiently periodic
                    bestTau = -1
                }
            }

            if (bestTau in (tauMin + 1) until tauMax) {
                // Parabolic interpolation for sub-sample accuracy
                val s0 = dPrime[bestTau - 1]
                val s1 = dPrime[bestTau]
                val s2 = dPrime[bestTau + 1]
                val denom = 2.0 * (s0 - 2.0 * s1 + s2)
                val delta = if (abs(denom) > 1e-6) (s0 - s2) / denom else 0.0
                val preciseTau = (bestTau + delta).toFloat().coerceIn(tauMin.toFloat(), tauMax.toFloat())
                val f0 = SAMPLE_RATE / preciseTau
                frames.add(PitchFrame(timeSec, f0, true))
            } else {
                frames.add(PitchFrame(timeSec, 0.0f, false))
            }
        }

        return frames
    }

    /**
     * Evaluates user's pitch contour against target reference contour points.
     * [referencePoints] is a list of (normalizedTime 0.0..1.0, targetF0Hz).
     */
    fun evaluateProsody(
        userFrames: List<PitchFrame>,
        referencePoints: List<Pair<Float, Float>>
    ): ProsodyEvaluation {
        val voiced = userFrames.filter { it.isVoiced && it.f0Hz > 0 }
        if (voiced.isEmpty() || referencePoints.isEmpty()) {
            return ProsodyEvaluation(
                intonationScore = 0f,
                rhythmScore = 0f,
                overallScore = 0f,
                meanPitchHz = 0f,
                pitchRangeHz = 0f,
                userContour = userFrames,
                feedback = "No voiced speech detected. Speak louder into the microphone."
            )
        }

        val totalDuration = userFrames.last().timeSeconds - userFrames.first().timeSeconds
        val startTime = userFrames.first().timeSeconds

        var semitoneDistSum = 0.0
        var evalCount = 0

        val pitches = voiced.map { it.f0Hz }
        val meanF0 = pitches.average().toFloat()
        val minF0 = pitches.minOrNull() ?: meanF0
        val maxF0 = pitches.maxOrNull() ?: meanF0
        val pitchRange = maxF0 - minF0

        // Map reference points to user pitch at corresponding normalized time
        for (ref in referencePoints) {
            val normT = ref.first.coerceIn(0f, 1f)
            val targetF0 = ref.second
            val targetTime = startTime + normT * totalDuration

            // Find closest voiced frame
            val closest = voiced.minByOrNull { abs(it.timeSeconds - targetTime) }
            if (closest != null && abs(closest.timeSeconds - targetTime) < 0.25f) {
                // Distance in semitones = 12 * log2(userF0 / targetF0)
                val semitoneDiff = abs(12.0 * log2(closest.f0Hz.toDouble() / targetF0.toDouble()))
                semitoneDistSum += semitoneDiff
                evalCount++
            }
        }

        val avgSemitoneDiff = if (evalCount > 0) semitoneDistSum / evalCount else 6.0
        // 0 semitones diff -> 100%, 6 semitones (tritone) diff -> ~45%
        val intonationScore = (100.0 * kotlin.math.exp(-0.13 * avgSemitoneDiff)).toFloat().coerceIn(15f, 98f)

        // Rhythm / Stress timing score: check if user exhibits dynamic pitch contour variation
        val rhythmRatio = (pitchRange / meanF0.coerceAtLeast(100f)).coerceIn(0.1f, 1.2f)
        val rhythmScore = (rhythmRatio * 100f).coerceIn(30f, 95f)

        val overall = (intonationScore * 0.65f + rhythmScore * 0.35f).coerceIn(15f, 98f)

        val feedback = when {
            overall >= 85f -> "Superb intonation! Your pitch rise and fall matches native speaker prosody."
            overall >= 70f -> "Good intonation! Pay attention to the terminal falling pitch on the final word."
            else -> "Try exaggerating the pitch jump on the stressed words to create English melody."
        }

        return ProsodyEvaluation(
            intonationScore = intonationScore,
            rhythmScore = rhythmScore,
            overallScore = overall,
            meanPitchHz = meanF0,
            pitchRangeHz = pitchRange,
            userContour = userFrames,
            feedback = feedback
        )
    }
}
