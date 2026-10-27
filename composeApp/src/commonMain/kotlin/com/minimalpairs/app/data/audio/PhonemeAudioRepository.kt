package com.minimalpairs.app.data.audio

import com.minimalpairs.app.core.dsp.WavCodec
import kotlinx.coroutines.CancellationException
import minimalpairs.composeapp.generated.resources.Res

/** ElevenLabs-generated isolated vowel examples. See audio/phonemes/NOTICE.md for attribution. */
object PhonemeAudioRepository {
    private val files = mapOf(
        "iː" to "i_long", "ɪ" to "ih", "æ" to "ae", "e" to "eh",
        "ɑː" to "ah_long", "ʌ" to "uh", "ʊ" to "oo_short", "uː" to "oo_long",
        "ɒ" to "o_short", "ɔː" to "aw_long"
    )

    // /e/ is the catalog's broad transcription for DRESS, realized here as [ɛ].
    fun resourcePath(phoneme: String): String? = files[phoneme]?.let { "files/audio/phonemes/$it.wav" }

    suspend fun loadPcm(phoneme: String): ShortArray? {
        val path = resourcePath(phoneme) ?: return null
        return try {
            WavCodec.decodeWavToPcm(Res.readBytes(path)).takeIf { it.isNotEmpty() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
