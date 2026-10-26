package com.minimalpairs.app.data.remote

import com.minimalpairs.app.core.dsp.LpcFormantExtractor
import com.minimalpairs.app.core.dsp.WavCodec
import com.minimalpairs.app.core.model.PhonemeScore
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

interface PhonemeScorerApi {
    suspend fun scoreAudio(
        audioSamples: ShortArray,
        targetWord: String,
        expectedIpa: String,
        contrastId: String? = null
    ): PhonemeScoringResponse
}

class PhonemeScorerClient(
    private val baseUrl: String? = null,
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
) : PhonemeScorerApi {

    override suspend fun scoreAudio(
        audioSamples: ShortArray,
        targetWord: String,
        expectedIpa: String,
        contrastId: String?
    ): PhonemeScoringResponse {
        // If remote URL is provided, attempt remote call; fallback to local acoustic GOP
        if (!baseUrl.isNullOrBlank()) {
            try {
                val wavBytes = WavCodec.encodePcmToWav(audioSamples)
                val response: PhonemeScoringResponse = client.post("$baseUrl/v1/score/phoneme") {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("target_transcript", targetWord)
                                append("expected_ipa", expectedIpa)
                                append("audio_file", wavBytes, Headers.build {
                                    append(HttpHeaders.ContentType, "audio/wav")
                                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                                })
                            }
                        )
                    )
                }.body()
                return response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Graceful fallback to offline local acoustic evaluation
            }
        }

        return evaluateLocalAcousticGop(audioSamples, targetWord, expectedIpa, contrastId)
    }

    /**
     * Local deterministic GOP evaluation based on LPC Formants (F1, F2) and Duration
     * mapped to the SDD YAML acoustic bounds.
     */
    fun evaluateLocalAcousticGop(
        samples: ShortArray,
        targetWord: String,
        expectedIpa: String,
        contrastId: String?
    ): PhonemeScoringResponse {
        val formantResult = LpcFormantExtractor.extract(samples)

        val contrast = if (contrastId != null) {
            VowelContrastsCatalog.getContrastById(contrastId)
        } else {
            VowelContrastsCatalog.defaultContrasts.firstOrNull { c ->
                c.pairs.any { it.word1.equals(targetWord, true) || it.word2.equals(targetWord, true) }
            }
        }

        val targetBounds = if (contrast != null) {
            val isWord1 = contrast.pairs.any { it.word1.equals(targetWord, true) }
            if (isWord1) contrast.boundsPhoneme1 else contrast.boundsPhoneme2
        } else {
            null
        }

        val targetPhoneme = if (contrast != null) {
            val isWord1 = contrast.pairs.any { it.word1.equals(targetWord, true) }
            if (isWord1) contrast.phoneme1 else contrast.phoneme2
        } else {
            expectedIpa.split(" ").firstOrNull { it.isNotBlank() } ?: expectedIpa
        }

        val f1 = formantResult.f1Hz
        val f2 = formantResult.f2Hz

        val gopScore: Float = if (targetBounds != null) {
            val midF1 = (targetBounds.f1MinHz + targetBounds.f1MaxHz) / 2f
            val midF2 = (targetBounds.f2MinHz + targetBounds.f2MaxHz) / 2f

            // Normalized Euclidean distance in formant space
            val normD1 = abs(f1 - midF1) / ((targetBounds.f1MaxHz - targetBounds.f1MinHz) / 2f).coerceAtLeast(50f)
            val normD2 = abs(f2 - midF2) / ((targetBounds.f2MaxHz - targetBounds.f2MinHz) / 2f).coerceAtLeast(100f)
            val distance = sqrt(normD1 * normD1 + normD2 * normD2)

            // Duration penalty if duration constraints exist
            var durScore = 1.0f
            targetBounds.minDurationMs?.let { minDur ->
                if (formantResult.durationMs < minDur) {
                    durScore *= (formantResult.durationMs.toFloat() / minDur).coerceIn(0.5f, 1.0f)
                }
            }
            targetBounds.maxDurationMs?.let { maxDur ->
                if (formantResult.durationMs > maxDur) {
                    durScore *= (maxDur.toFloat() / formantResult.durationMs).coerceIn(0.5f, 1.0f)
                }
            }

            val acousticQuality = exp(-0.6 * distance).toFloat() * durScore
            (acousticQuality * 100f).coerceIn(10.0f, 98.5f)
        } else {
            75.0f
        }

        val durationSec = (formantResult.durationMs / 1000f).coerceAtLeast(0.12f)
        val phonemeScores = listOf(
            PhonemeScore(
                phoneme = targetPhoneme,
                gopScore = gopScore,
                startTime = 0.05f,
                endTime = 0.05f + durationSec
            )
        )

        return PhonemeScoringResponse(
            overallScore = gopScore,
            phonemes = phonemeScores
        )
    }
}
