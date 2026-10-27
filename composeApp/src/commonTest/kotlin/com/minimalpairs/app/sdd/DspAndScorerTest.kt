package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.dsp.LpcFormantExtractor
import com.minimalpairs.app.core.dsp.TimeStretchProcessor
import com.minimalpairs.app.core.dsp.WavCodec
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DspAndScorerTest {

    @Test
    fun inputWithoutFormantPeaksDoesNotInventAMeasurement() {
        // An isolated impulse has energy but no pair of vowel resonances.
        for (samples in listOf(ShortArray(100), ShortArray(3200),
            ShortArray(400) { if (it == 399) 10000 else 0 })) {
            val result = LpcFormantExtractor.extract(samples)
            assertEquals(0f, result.f1Hz)
            assertEquals(0f, result.f2Hz)
            assertEquals(0f, result.f3Hz)
            assertEquals(0f, result.voicedRatio)
        }
    }

    @Test
    fun testWavCodecRoundTrip() {
        val originalSamples = FormantSynthesizer.synthesizeVowel(
            f1 = 300f,
            f2 = 2300f,
            durationMs = 100
        )
        val wavBytes = WavCodec.encodePcmToWav(originalSamples)
        assertTrue(wavBytes.size > 44, "WAV must contain 44-byte RIFF header + PCM payload")

        val decoded = WavCodec.decodeWavToPcm(wavBytes)
        assertEquals(originalSamples.size, decoded.size)
        for (i in originalSamples.indices) {
            assertEquals(originalSamples[i], decoded[i])
        }
    }

    @Test
    fun testTimeStretchProcessorSlowsAudioPreservingLength() {
        val originalSamples = FormantSynthesizer.synthesizeVowel(
            f1 = 320f,
            f2 = 2250f,
            durationMs = 150
        )
        // 0.75x speed means duration is 1.0 / 0.75 = 1.33x longer
        val stretched = TimeStretchProcessor.process(originalSamples, 0.75f)

        val ratio = stretched.size.toFloat() / originalSamples.size.toFloat()
        assertTrue(ratio > 1.15f, "Stretched 0.75x audio must expand duration (actual ratio: $ratio)")
    }

    @Test
    fun testLpcFormantExtractionAndAcousticGopScoring() {
        // Synthesize /iː/ vowel (F1 ~300 Hz, F2 ~2300 Hz)
        val sheepVowel = FormantSynthesizer.synthesizeVowel(
            f1 = 310f,
            f2 = 2350f,
            durationMs = 160
        )
        val analysis = LpcFormantExtractor.extract(sheepVowel)
        assertTrue(analysis.f1Hz in 200f..500f, "F1 must be in expected close vowel range (got ${analysis.f1Hz})")
        assertTrue(analysis.f2Hz in 1800f..2800f, "F2 must be in expected front vowel range (got ${analysis.f2Hz})")
        assertTrue(analysis.durationMs > 80, "Duration must be detected")

        // Evaluate with PhonemeScorerClient local GOP engine
        val scorer = PhonemeScorerClient()
        val scoreResult = scorer.evaluateLocalAcousticGop(
            samples = sheepVowel,
            targetWord = "sheep",
            expectedIpa = "ʃ iː p",
            contrastId = "i_vs_I"
        )

        assertTrue(scoreResult.overallScore in 50f..100f, "Well-targeted /iː/ must achieve high GOP score (got ${scoreResult.overallScore})")
        assertEquals(1, scoreResult.phonemes.size)
        assertEquals("iː", scoreResult.phonemes.first().phoneme)
    }
}
