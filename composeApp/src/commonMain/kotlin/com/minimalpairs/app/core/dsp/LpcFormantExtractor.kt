package com.minimalpairs.app.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Acoustic Formant Extractor using Linear Predictive Coding (LPC)
 * via Levinson-Durbin recursion and spectral peak picking on 16kHz PCM.
 */
object LpcFormantExtractor {

    data class FormantResult(
        val f1Hz: Float,
        val f2Hz: Float,
        val f3Hz: Float,
        val durationMs: Int,
        val voicedRatio: Float
    )

    private const val SAMPLE_RATE = 16000
    private const val LPC_ORDER = 14
    private const val FRAME_LEN = 400 // 25ms at 16kHz
    private const val HOP_LEN = 160   // 10ms

    fun extract(samples: ShortArray): FormantResult {
        if (samples.size < FRAME_LEN) {
            return FormantResult(f1Hz = 350f, f2Hz = 2200f, f3Hz = 2800f, durationMs = 0, voicedRatio = 0.0f)
        }

        // 1. Calculate energy across frames to identify voiced vowel core
        val numFrames = (samples.size - FRAME_LEN) / HOP_LEN + 1
        val frameEnergies = FloatArray(numFrames)
        var maxEnergy = 0.0f

        for (f in 0 until numFrames) {
            val start = f * HOP_LEN
            var e = 0.0f
            for (i in 0 until FRAME_LEN) {
                val s = samples[start + i].toFloat()
                e += s * s
            }
            frameEnergies[f] = e / FRAME_LEN
            if (frameEnergies[f] > maxEnergy) maxEnergy = frameEnergies[f]
        }

        // Reject silence and very low level input before attempting LPC.
        if (maxEnergy < 100f) {
            return FormantResult(0f, 0f, 0f, 0, 0f)
        }
        val energyThreshold = maxEnergy * 0.15f
        var voicedFrames = 0
        var f1Sum = 0.0f
        var f2Sum = 0.0f
        var f3Sum = 0.0f
        var validFormantCount = 0

        // 2. Analyze voiced frames
        val window = FloatArray(FRAME_LEN) { i ->
            (0.54 - 0.46 * cos(2.0 * PI * i / (FRAME_LEN - 1))).toFloat()
        }

        for (f in 0 until numFrames) {
            if (frameEnergies[f] < energyThreshold) continue
            voicedFrames++

            val start = f * HOP_LEN
            val frame = FloatArray(FRAME_LEN)
            // Pre-emphasis
            var prev = samples[start].toFloat()
            frame[0] = prev * window[0]
            for (i in 1 until FRAME_LEN) {
                val curr = samples[start + i].toFloat()
                val preemph = curr - 0.97f * prev
                frame[i] = preemph * window[i]
                prev = curr
            }

            // Autocorrelation
            val r = DoubleArray(LPC_ORDER + 1)
            for (k in 0..LPC_ORDER) {
                var sum = 0.0
                for (n in 0 until (FRAME_LEN - k)) {
                    sum += frame[n] * frame[n + k]
                }
                r[k] = sum
            }

            if (r[0] < 1e-4) continue

            // Levinson-Durbin Recursion
            val a = DoubleArray(LPC_ORDER + 1)
            a[0] = 1.0
            var error = r[0]

            for (i in 1..LPC_ORDER) {
                var lambda = 0.0
                for (j in 0 until i) {
                    lambda -= a[j] * r[i - j]
                }
                val ki = lambda / error
                val aPrev = a.copyOf()
                a[i] = ki
                for (j in 1 until i) {
                    a[j] = aPrev[j] + ki * aPrev[i - j]
                }
                error *= (1.0 - ki * ki)
                if (error <= 0.0) break
            }

            // Spectral peak picking from 200 Hz to 3500 Hz
            val peaks = findSpectralPeaks(a, LPC_ORDER)
            if (peaks.size >= 2) {
                f1Sum += peaks[0]
                f2Sum += peaks[1]
                f3Sum += if (peaks.size >= 3) peaks[2] else 2800f
                validFormantCount++
            }
        }

        val durationMs = (voicedFrames * HOP_LEN * 1000) / SAMPLE_RATE
        val voicedRatio = if (numFrames > 0) voicedFrames.toFloat() / numFrames else 0.0f

        val avgF1 = if (validFormantCount > 0) f1Sum / validFormantCount else 350f
        val avgF2 = if (validFormantCount > 0) f2Sum / validFormantCount else 2200f
        val avgF3 = if (validFormantCount > 0) f3Sum / validFormantCount else 2800f

        return FormantResult(
            f1Hz = avgF1,
            f2Hz = avgF2,
            f3Hz = avgF3,
            durationMs = durationMs,
            voicedRatio = voicedRatio
        )
    }

    private fun findSpectralPeaks(lpcA: DoubleArray, order: Int): List<Float> {
        // Evaluate frequency response |1 / A(e^jw)| every 20 Hz between 200 Hz and 3500 Hz
        val stepHz = 20
        val minHz = 200
        val maxHz = 3500
        val numSteps = (maxHz - minHz) / stepHz + 1
        val spectrum = FloatArray(numSteps)

        for (i in 0 until numSteps) {
            val freq = minHz + i * stepHz
            val omega = 2.0 * PI * freq / SAMPLE_RATE

            var re = 1.0
            var im = 0.0
            for (k in 1..order) {
                val theta = k * omega
                re += lpcA[k] * cos(theta)
                im -= lpcA[k] * sin(theta)
            }
            val magSq = re * re + im * im
            // Inverse magnitude (resonance peak)
            spectrum[i] = if (magSq > 1e-9) (1.0 / magSq).toFloat() else 0.0f
        }

        // Detect local maxima
        val peaks = mutableListOf<Float>()
        for (i in 1 until numSteps - 1) {
            if (spectrum[i] > spectrum[i - 1] && spectrum[i] > spectrum[i + 1]) {
                val peakFreq = (minHz + i * stepHz).toFloat()
                // Avoid redundant peaks too close to each other
                if (peaks.isEmpty() || peakFreq - peaks.last() > 150f) {
                    peaks.add(peakFreq)
                }
            }
        }

        return peaks
    }
}
