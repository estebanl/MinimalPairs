package com.minimalpairs.app.core.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Acoustic Formant Synthesizer generating 16kHz 16-bit PCM speech waveforms
 * from target vowel formant frequencies (F1, F2, F3) and duration.
 */
object FormantSynthesizer {

    const val SAMPLE_RATE = 16000

    /**
     * Synthesizes a vowel sound with fundamental frequency [f0] (default 135Hz),
     * formants [f1], [f2], [f3] in Hz, and duration in [durationMs].
     */
    fun synthesizeVowel(
        f1: Float,
        f2: Float,
        f3: Float = 2800f,
        f0: Float = 135f,
        durationMs: Int = 180,
        bandwidth1: Float = 80f,
        bandwidth2: Float = 100f,
        bandwidth3: Float = 120f
    ): ShortArray {
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(totalSamples)

        val r1 = exp(-PI * bandwidth1 / SAMPLE_RATE)
        val r2 = exp(-PI * bandwidth2 / SAMPLE_RATE)
        val r3 = exp(-PI * bandwidth3 / SAMPLE_RATE)

        val w1 = 2.0 * PI * f1 / SAMPLE_RATE
        val w2 = 2.0 * PI * f2 / SAMPLE_RATE
        val w3 = 2.0 * PI * f3 / SAMPLE_RATE

        var y1_1 = 0.0
        var y1_2 = 0.0
        var y2_1 = 0.0
        var y2_2 = 0.0
        var y3_1 = 0.0
        var y3_2 = 0.0

        val pitchPeriod = (SAMPLE_RATE / f0).roundToInt()

        for (n in 0 until totalSamples) {
            // Glottal source impulse train with envelope
            val isGlottalPulse = (n % pitchPeriod) == 0
            val envelope = when {
                n < totalSamples * 0.1 -> n / (totalSamples * 0.1)
                n > totalSamples * 0.85 -> (totalSamples - n) / (totalSamples * 0.15)
                else -> 1.0
            }
            val pulse = if (isGlottalPulse) 1.0 * envelope else 0.0

            // Second-order digital resonator for each formant
            // y[n] = x[n] + 2*r*cos(w)*y[n-1] - r^2*y[n-2]
            val y1 = pulse + 2.0 * r1 * kotlin.math.cos(w1) * y1_1 - r1 * r1 * y1_2
            y1_2 = y1_1
            y1_1 = y1

            val y2 = pulse * 0.7 + 2.0 * r2 * kotlin.math.cos(w2) * y2_1 - r2 * r2 * y2_2
            y2_2 = y2_1
            y2_1 = y2

            val y3 = pulse * 0.4 + 2.0 * r3 * kotlin.math.cos(w3) * y3_1 - r3 * r3 * y3_2
            y3_2 = y3_1
            y3_1 = y3

            val combined = (y1 * 0.5 + y2 * 0.35 + y3 * 0.15) * 16000.0 * envelope
            out[n] = combined.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return out
    }
}
