package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.audio.PhonemeAudioRepository
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlinx.coroutines.runBlocking
import minimalpairs.composeapp.generated.resources.Res
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhonemeAudioResourcesTest {
    @Test
    fun everyConsonantTargetHasABundledWordExample() = runBlocking {
        val contrasts = com.minimalpairs.app.data.catalog.ListeningContrastsCatalog.defaultContrasts
            .filter { it.vowelReference == null }
        for (contrast in contrasts) {
            for (pair in contrast.pairs) {
                for (word in listOf(pair.word1, pair.word2)) {
                    val samples = assertNotNull(
                        com.minimalpairs.app.data.audio.ElevenLabsAudioRepository.loadWordPcm(word), word)
                    assertTrue(samples.any { it.toInt() != 0 }, "$word must not be silent")
                }
            }
        }
    }

    @Test
    fun everyLabPhonemeLoadsFromPackagedResourcesAsCompletePcm() = runBlocking {
        val words = VowelContrastsCatalog.defaultContrasts
            .flatMap { listOf(it.phoneme1, it.phoneme2) }
            .toSet()
        assertTrue(words.isNotEmpty())
        for (word in words) {
            val bytes = Res.readBytes(assertNotNull(PhonemeAudioRepository.resourcePath(word)))
            val samples = assertNotNull(PhonemeAudioRepository.loadPcm(word), word)
            assertTrue(samples.isNotEmpty(), "$word must contain audio")
            assertTrue(samples.any { it.toInt() != 0 }, "$word must not be silent")
            AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { audio ->
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, audio.format.encoding, word)
                assertEquals(16_000f, audio.format.sampleRate, word)
                assertEquals(16, audio.format.sampleSizeInBits, word)
                assertEquals(1, audio.format.channels, word)
                assertEquals(audio.frameLength, samples.size.toLong(), "$word must decode in full")
            }
        }
    }
}
