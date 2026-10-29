package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import kotlinx.coroutines.runBlocking
import minimalpairs.composeapp.generated.resources.Res
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeaningTrapResourcesTest {
    @Test
    fun everyStoryHasFourNonemptyPcmReferenceClips() = runBlocking {
        for (story in MeaningTrapCatalog.stories) {
            for (kind in listOf("prompt", "phrase", "intended", "trap")) {
                val path = "files/audio/meaning_trap/${story.id}_$kind.wav"
                val bytes = Res.readBytes(path)
                AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { audio ->
                    val format = audio.format
                    assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.encoding, path)
                    assertEquals(16000f, format.sampleRate, path)
                    assertEquals(16, format.sampleSizeInBits, path)
                    assertEquals(1, format.channels, path)
                    assertTrue(!format.isBigEndian, path)
                    val samples = audio.readBytes()
                    assertTrue(samples.size >= 16000 * 2 / 10, "$path needs at least 100 ms of audio")
                    assertTrue(samples.any { it != 0.toByte() }, "$path must not contain only silence")
                }
            }
        }
    }
}
