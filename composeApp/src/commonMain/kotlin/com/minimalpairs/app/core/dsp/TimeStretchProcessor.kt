package com.minimalpairs.app.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Pure-Kotlin pitch-preserving time-stretching processor using WSOLA
 * (Waveform Similarity Overlap-Add) on 16kHz 16-bit mono PCM.
 *
 * Preserves fundamental frequency (F0) and formant trajectories (F1, F2)
 * without pitch shifts.
 */
object TimeStretchProcessor {

    private const val FRAME_SIZE = 512 // 32ms at 16kHz
    private const val SYNTHESIS_HOP = 256 // 16ms
    private const val SEARCH_RANGE = 64

    private val HANN_WINDOW = FloatArray(FRAME_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (FRAME_SIZE - 1)))).toFloat()
    }

    /**
     * Stretches audio by [speedMultiplier].
     * Example: speedMultiplier = 0.75 slows speech down to 75% speed (output is 1.33x longer).
     * speedMultiplier = 1.0 returns a copy of input.
     */
    fun process(input: ShortArray, speedMultiplier: Float = 0.75f): ShortArray {
        if (speedMultiplier <= 0.05f || input.isEmpty()) return input
        if (kotlin.math.abs(speedMultiplier - 1.0f) < 0.01f) {
            return input.copyOf()
        }

        val analysisHopNominal = (SYNTHESIS_HOP * speedMultiplier).roundToInt().coerceAtLeast(32)
        val estimatedOutputLen = (input.size / speedMultiplier).roundToInt() + FRAME_SIZE
        val outputAccum = FloatArray(estimatedOutputLen)
        val weightAccum = FloatArray(estimatedOutputLen)

        var synthPos = 0
        var naturalAnalysisPos = 0

        // Seed with first frame
        val initialCopyLen = FRAME_SIZE.coerceAtMost(input.size)
        for (i in 0 until initialCopyLen) {
            outputAccum[i] = input[i].toFloat() * HANN_WINDOW[i]
            weightAccum[i] = HANN_WINDOW[i]
        }
        synthPos += SYNTHESIS_HOP
        naturalAnalysisPos += analysisHopNominal

        while (naturalAnalysisPos + FRAME_SIZE <= input.size) {
            // Find optimal offset delta via cross-correlation with previous frame
            var bestDelta = 0
            var maxCorrelation = -Float.MAX_VALUE

            val prevSynthRef = synthPos - SYNTHESIS_HOP

            for (delta in -SEARCH_RANGE..SEARCH_RANGE) {
                val candidatePos = naturalAnalysisPos + delta
                if (candidatePos < 0 || candidatePos + FRAME_SIZE > input.size) continue

                var correlation = 0.0f
                var normCand = 0.0f

                // Stride by 2 for fast correlation calculation
                var i = 0
                while (i < FRAME_SIZE) {
                    val sampleCand = input[candidatePos + i].toFloat()
                    val samplePrev = if (prevSynthRef + i in 0 until outputAccum.size) outputAccum[prevSynthRef + i] else 0.0f
                    correlation += sampleCand * samplePrev
                    normCand += sampleCand * sampleCand
                    i += 2
                }

                val score = if (normCand > 1e-4f) correlation / kotlin.math.sqrt(normCand) else 0.0f
                if (score > maxCorrelation) {
                    maxCorrelation = score
                    bestDelta = delta
                }
            }

            val chosenAnalysisPos = (naturalAnalysisPos + bestDelta).coerceIn(0, input.size - FRAME_SIZE)

            // Overlap-add windowed frame into output
            for (i in 0 until FRAME_SIZE) {
                val outIdx = synthPos + i
                if (outIdx < outputAccum.size) {
                    val w = HANN_WINDOW[i]
                    outputAccum[outIdx] += input[chosenAnalysisPos + i].toFloat() * w
                    weightAccum[outIdx] += w
                }
            }

            synthPos += SYNTHESIS_HOP
            naturalAnalysisPos += analysisHopNominal
        }

        val totalValidOutput = (synthPos + FRAME_SIZE).coerceAtMost(outputAccum.size)
        val result = ShortArray(totalValidOutput)

        for (i in 0 until totalValidOutput) {
            val weight = weightAccum[i]
            val normalized = if (weight > 1e-4f) outputAccum[i] / weight else outputAccum[i]
            result[i] = normalized.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return result
    }
}
