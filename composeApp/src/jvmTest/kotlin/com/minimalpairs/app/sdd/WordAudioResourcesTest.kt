package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.audio.ElevenLabsAudioRepository
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

class WordAudioResourcesTest {
    @Test
    fun everyPracticeWordLoadsFromPackagedResourcesAsCompletePcm() = runBlocking {
        val words = VowelContrastsCatalog.defaultContrasts
            .flatMap { it.pairs }
            .flatMap { listOf(it.word1, it.word2) }
            .toSet()
        assertTrue(words.isNotEmpty())
        for (word in words) {
            val bytes = Res.readBytes("files/audio/words/$word.wav")
            val samples = assertNotNull(ElevenLabsAudioRepository.loadWordPcm(word), word)
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
