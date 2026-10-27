package com.minimalpairs.app.core.dsp

import kotlin.math.*

/**
 * Experimental whole-word reference comparison for 16 kHz mono PCM.
 * Compares normalized log-mel spectra with dynamic time warping. It does not
 * recognize words or isolate a consonant, and is not a calibrated pronunciation grade.
 */
object WordSimilarityScorer {
    private const val fftSize = 512
    private const val frameSize = 400
    private const val hop = 160
    private const val bands = 26
    private const val maxFrames = 200
    private val window = DoubleArray(frameSize) { .5 - .5 * cos(2 * PI * it / (frameSize - 1)) }
    private val edges = DoubleArray(bands + 2) { index ->
        val minMel = 2595 * log10(1 + 80.0 / 700)
        val maxMel = 2595 * log10(1 + 7600.0 / 700)
        700 * (10.0.pow((minMel + (maxMel - minMel) * index / (bands + 1)) / 2595) - 1)
    }

    fun hasUsableAudio(samples: ShortArray): Boolean {
        if (samples.size !in 1280..480000) return false
        val energies = frameEnergies(samples)
        // Require at least 80 ms of non-DC audio; do not require voicing or vowel peaks.
        return energies.count { it >= .002 } >= 6
    }

    fun score(samples: ShortArray, target: ShortArray, alternative: ShortArray): Float {
        require(hasUsableAudio(samples)) { "Record an audible word for at least 80 ms, then try again." }
        require(hasUsableAudio(target) && hasUsableAudio(alternative)) { "The reference audio is unavailable." }
        val observed = features(samples)
        val targetDistance = distance(observed, features(target))
        val alternativeDistance = distance(observed, features(alternative))
        // Equal distances yield 50 before the absolute-match penalty. Unrelated audio
        // must not earn a high score merely because it is marginally nearer one reference.
        val relativeMatch = if (targetDistance + alternativeDistance < 1e-9) .5
            else alternativeDistance / (targetDistance + alternativeDistance)
        return (100 * relativeMatch * exp(-targetDistance)).toFloat().coerceIn(0f, 100f)
    }

    private fun frameEnergies(samples: ShortArray): DoubleArray =
        DoubleArray((samples.size - frameSize).coerceAtLeast(0) / hop + 1) { frame ->
            val start = frame * hop
            val count = min(frameSize, samples.size - start)
            val mean = (0 until count).sumOf { samples[start + it].toDouble() } / count
            sqrt((0 until count).sumOf { ((samples[start + it] - mean) / 32768).pow(2) } / count)
        }

    private fun features(samples: ShortArray): List<DoubleArray> {
        val energies = frameEnergies(samples)
        val threshold = max(.002, energies.maxOrNull()!! * .03)
        // Trim leading/trailing silence only, retaining closures inside a word.
        val start = energies.indexOfFirst { it >= threshold }.coerceAtLeast(0)
        val end = energies.indexOfLast { it >= threshold }.coerceAtMost(energies.lastIndex)
        val spectra = (start..end).map { frame ->
            val real = DoubleArray(fftSize)
            val imaginary = DoubleArray(fftSize)
            val offset = frame * hop
            val mean = (0 until frameSize).sumOf { samples[offset + it].toDouble() } / frameSize
            for (i in 0 until frameSize) real[i] = (samples[offset + i] - mean) / 32768 * window[i]
            fft(real, imaginary)
            val power = DoubleArray(fftSize / 2 + 1) { real[it] * real[it] + imaginary[it] * imaginary[it] }
            val spectrum = DoubleArray(bands) { band ->
                var energy = 0.0
                for (bin in power.indices) {
                    val frequency = bin * 16000.0 / fftSize
                    val weight = when {
                        frequency < edges[band] || frequency > edges[band + 2] -> 0.0
                        frequency <= edges[band + 1] -> (frequency - edges[band]) / (edges[band + 1] - edges[band])
                        else -> (edges[band + 2] - frequency) / (edges[band + 2] - edges[band + 1])
                    }
                    energy += weight * power[bin]
                }
                energy
            }
            val peak = spectrum.maxOrNull()!!.coerceAtLeast(1e-12)
            // Relative log energy removes recording gain; the floor limits noise influence.
            DoubleArray(bands) { ln((spectrum[it] / peak).coerceAtLeast(1e-4)) / ln(1e4) }
        }
        if (spectra.size <= maxFrames) return spectra
        return List(maxFrames) { index -> spectra[index * (spectra.size - 1) / (maxFrames - 1)] }
    }

    private fun distance(a: List<DoubleArray>, b: List<DoubleArray>): Double {
        var previous = DoubleArray(b.size + 1) { Double.POSITIVE_INFINITY }.also { it[0] = 0.0 }
        for (i in a.indices) {
            val current = DoubleArray(b.size + 1) { Double.POSITIVE_INFINITY }
            for (j in b.indices) {
                val cost = sqrt(a[i].indices.sumOf { (a[i][it] - b[j][it]).pow(2) } / bands)
                current[j + 1] = cost + minOf(previous[j], previous[j + 1], current[j])
            }
            previous = current
        }
        return previous.last() / max(a.size, b.size)
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        var j = 0
        for (i in 1 until fftSize) {
            var bit = fftSize shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val value = real[i]; real[i] = real[j]; real[j] = value }
        }
        var length = 2
        while (length <= fftSize) {
            val angle = -2 * PI / length
            for (start in 0 until fftSize step length) {
                for (k in 0 until length / 2) {
                    val even = start + k
                    val odd = even + length / 2
                    val c = cos(angle * k)
                    val s = sin(angle * k)
                    val r = real[odd] * c - imaginary[odd] * s
                    val im = real[odd] * s + imaginary[odd] * c
                    real[odd] = real[even] - r
                    imaginary[odd] = imaginary[even] - im
                    real[even] += r
                    imaginary[even] += im
                }
            }
            length *= 2
        }
    }
}
