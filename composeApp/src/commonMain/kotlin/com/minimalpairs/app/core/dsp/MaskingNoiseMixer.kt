package com.minimalpairs.app.core.dsp

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Generates and mixes masking noise (white/pink/babble) into 16kHz PCM speech
 * at calibrated Signal-to-Noise Ratios (SNR dB).
 */
object MaskingNoiseMixer {

    enum class NoiseType {
        WHITE,
        PINK,
        BABBLE_SIMULATED
    }

    /**
     * Mixes masking noise into [signal] at [snrDb] (e.g. +10dB, +5dB, 0dB).
     * Returns a new ShortArray containing speech + masking noise.
     */
    fun mix(
        signal: ShortArray,
        snrDb: Float = 10.0f,
        noiseType: NoiseType = NoiseType.PINK,
        seed: Long = 42L
    ): ShortArray {
        if (signal.isEmpty()) return signal

        // 1. Calculate Signal Power (RMS^2)
        var signalPowerSum = 0.0
        for (sample in signal) {
            val v = sample.toDouble()
            signalPowerSum += v * v
        }
        val signalPower = signalPowerSum / signal.size
        if (signalPower < 1.0) return signal.copyOf()

        // 2. Generate raw noise buffer of same length
        val noiseFloats = generateNoise(signal.size, noiseType, seed)

        // 3. Calculate raw Noise Power
        var noisePowerSum = 0.0
        for (v in noiseFloats) {
            noisePowerSum += v * v
        }
        val rawNoisePower = noisePowerSum / noiseFloats.size
        if (rawNoisePower < 1e-9) return signal.copyOf()

        // 4. Calculate required scale factor: SNR_lin = P_signal / P_noise = 10^(snrDb/10)
        // => targetNoisePower = P_signal / 10^(snrDb/10)
        // => scale = sqrt(targetNoisePower / rawNoisePower)
        val snrLinear = 10.0.pow(snrDb / 10.0)
        val targetNoisePower = signalPower / snrLinear
        val scale = sqrt(targetNoisePower / rawNoisePower)

        // 5. Mix signal + scale * noise
        val mixed = ShortArray(signal.size)
        for (i in signal.indices) {
            val combined = signal[i] + (noiseFloats[i] * scale)
            mixed[i] = combined.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return mixed
    }

    private fun generateNoise(length: Int, type: NoiseType, seed: Long): FloatArray {
        val random = Random(seed)
        val out = FloatArray(length)

        when (type) {
            NoiseType.WHITE -> {
                for (i in 0 until length) {
                    out[i] = (random.nextFloat() * 2.0f - 1.0f) * 1000.0f
                }
            }
            NoiseType.PINK -> {
                // Paul Kellet's filter for 1/f Pink Noise
                var b0 = 0.0f
                var b1 = 0.0f
                var b2 = 0.0f
                var b3 = 0.0f
                var b4 = 0.0f
                var b5 = 0.0f
                var b6 = 0.0f
                for (i in 0 until length) {
                    val white = random.nextFloat() * 2.0f - 1.0f
                    b0 = 0.99886f * b0 + white * 0.0555179f
                    b1 = 0.99332f * b1 + white * 0.0750759f
                    b2 = 0.96900f * b2 + white * 0.1538520f
                    b3 = 0.86650f * b3 + white * 0.3104856f
                    b4 = 0.55000f * b4 + white * 0.5329522f
                    b5 = -0.7616f * b5 - white * 0.0168980f
                    val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
                    b6 = white * 0.115926f
                    out[i] = pink * 300.0f
                }
            }
            NoiseType.BABBLE_SIMULATED -> {
                // Simulated multi-talker babble using modulated multi-frequency pink noise
                val base = generateNoise(length, NoiseType.PINK, seed)
                for (i in 0 until length) {
                    val mod = 0.7f + 0.3f * kotlin.math.sin(2.0 * kotlin.math.PI * 4.0 * i / 16000.0).toFloat()
                    out[i] = base[i] * mod
                }
            }
        }
        return out
    }
}
