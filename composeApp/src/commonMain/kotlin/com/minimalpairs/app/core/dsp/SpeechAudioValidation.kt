package com.minimalpairs.app.core.dsp

import kotlin.math.pow
import kotlin.math.sqrt

/** Non-DC audio gate for 16 kHz PCM; does not require voicing or vowel formants. */
object SpeechAudioValidation {
    fun hasUsableAudio(samples: ShortArray): Boolean {
        if (samples.size !in 1280..480000) return false
        var audibleFrames = 0
        for (start in 0..samples.size - 400 step 160) {
            val mean = (0 until 400).sumOf { samples[start + it].toDouble() } / 400
            val rms = sqrt((0 until 400).sumOf { ((samples[start + it] - mean) / 32768).pow(2) } / 400)
            if (rms >= .002 && ++audibleFrames >= 6) return true
        }
        return false
    }
}
