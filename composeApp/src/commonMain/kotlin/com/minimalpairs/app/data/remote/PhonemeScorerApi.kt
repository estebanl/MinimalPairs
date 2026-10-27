package com.minimalpairs.app.data.remote

import com.minimalpairs.app.core.dsp.LpcFormantExtractor
import com.minimalpairs.app.core.dsp.WavCodec
import com.minimalpairs.app.core.dsp.SpeechAudioValidation
import com.minimalpairs.app.data.catalog.ConsonantScoringTargets
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.core.model.PhonemeScore
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

class PracticeScoringException(message: String) : Exception(message)

interface PhonemeScorerApi {
    suspend fun scoreAudio(
        audioSamples: ShortArray,
        targetWord: String,
        expectedIpa: String,
        contrastId: String? = null
    ): PhonemeScoringResponse
}

class PhonemeScorerClient(
    private val baseUrl: String? = defaultPhonemeScorerBaseUrl(),
    private val client: HttpClient = HttpClient {
        expectSuccess = true
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    },
    private val functionKey: String? = defaultPhonemeScorerFunctionKey()
) : PhonemeScorerApi {

    override suspend fun scoreAudio(
        audioSamples: ShortArray,
        targetWord: String,
        expectedIpa: String,
        contrastId: String?
    ): PhonemeScoringResponse {
        val practice = contrastId?.let(ListeningContrastsCatalog::getContrastById)
        val consonant = practice != null && practice.vowelReference == null
        val target = if (consonant) {
            ConsonantScoringTargets.find(practice.id, targetWord, expectedIpa)
                ?: throw PracticeScoringException("The selected sound has no matching word transcript.")
        } else null
        if (consonant) {
            if (!SpeechAudioValidation.hasUsableAudio(audioSamples)) {
                throw PracticeScoringException("No clear sound detected. Record the whole word and try again.")
            }
            if (baseUrl.isNullOrBlank()) {
                throw PracticeScoringException("Consonant scoring needs the phoneme service. Configure its address, then record again. Your recording can still be replayed.")
            }
        }
        val requestIpa = target?.ipa ?: expectedIpa
        // Remote failure remains visible when falling back to the local estimate.
        var fallbackMessage: String? = null
        if (!baseUrl.isNullOrBlank()) {
            try {
                val key = functionKey?.trim()?.takeIf { it.isNotEmpty() }
                if (key != null && !baseUrl.startsWith("https://", ignoreCase = true)) {
                    throw PracticeScoringException("A secured phoneme service requires an HTTPS address. No recording was uploaded.")
                }
                val wavBytes = WavCodec.encodePcmToWav(audioSamples)
                suspend fun upload() = client.post("${baseUrl.trimEnd('/')}/v1/score/phoneme") {
                    expectSuccess = false
                    key?.let { header("x-functions-key", it) }
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("target_transcript", targetWord)
                                append("expected_ipa", requestIpa)
                                target?.let { append("target_phoneme_index", it.index.toString()) }
                                append("audio_file", wavBytes, Headers.build {
                                    append(HttpHeaders.ContentType, "audio/wav")
                                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                                })
                            }
                        )
                    )
                }
                // Azure can return a gateway error while a zero-replica host starts.
                // Scoring is stateless; replay the same WAV, with one total deadline.
                val httpResponse = withTimeoutOrNull(120_000) {
                    var result = upload()
                    repeat(7) { attempt ->
                        if (result.status.value !in setOf(502, 503, 504)) return@withTimeoutOrNull result
                        result.bodyAsText()
                        delay(minOf(2_000L shl attempt, 8_000L))
                        result = upload()
                    }
                    result
                } ?: throw PracticeScoringException(
                    "The phoneme service is taking too long to start. Try again shortly. No score was saved; your recording can still be replayed.")
                if (httpResponse.status.value == 401 || httpResponse.status.value == 403) {
                    throw PracticeScoringException("The phoneme service rejected its access key. Check the scorer configuration and try again. No score was saved; your recording can still be replayed.")
                }
                if (consonant && httpResponse.status.value == 400) {
                    val code = runCatching {
                        Json.parseToJsonElement(httpResponse.bodyAsText()).jsonObject["detail"]
                            ?.jsonObject?.get("code")?.jsonPrimitive?.content
                    }.getOrNull()
                    val guidance = when (code) {
                        "no_clear_speech" -> "No clear voice was captured. Check your microphone and record the whole word again."
                        "noisy_audio" -> "Background noise is too strong for a reliable score. Move closer to the microphone and try in a quieter place."
                        "clipped_audio" -> "The microphone is clipping. Move farther away or lower its input level."
                        "multiple_utterances" -> "Say the selected word once, without other speech."
                        "uncertain_alignment" -> "The model could not identify /$expectedIpa/ reliably. Record the word again."
                        else -> null
                    }
                    if (guidance != null) throw PracticeScoringException("$guidance No score was saved. You can replay your recording.")
                }
                if (httpResponse.status.value != 200 && consonant) {
                    throw PracticeScoringException("The phoneme service returned HTTP ${httpResponse.status.value}. Try again shortly. No score was saved; your recording can still be replayed.")
                }
                require(httpResponse.status.value == 200)
                val response: PhonemeScoringResponse = httpResponse.body()
                require(response.overallScore.isFinite() && response.overallScore in 0f..100f)
                require(response.scoringMethod == (if (consonant) "neural_ctc_phoneme" else "neural_ctc") && !response.modelId.isNullOrBlank())
                val expectedTokens = requestIpa.trim().trim('/').split(Regex("\\s+")).filter { it.isNotBlank() }
                require(response.phonemes.map { it.phoneme } == expectedTokens)
                require(response.phonemes.zipWithNext().all { (before, after) -> before.endTime <= after.startTime })
                require(response.phonemes.isNotEmpty() && response.phonemes.all {
                    it.gopScore.isFinite() && it.gopScore in 0f..100f &&
                        it.startTime.isFinite() && it.endTime.isFinite() &&
                        it.startTime >= 0f && it.endTime > it.startTime &&
                        it.endTime <= audioSamples.size / 16000f + .001f
                })
                if (target != null) {
                    require(response.targetPhonemeIndex == target.index)
                    val selected = response.phonemes[target.index]
                    require(selected.phoneme == expectedIpa)
                    require(abs(response.overallScore - selected.gopScore) < .001f)
                    return response.copy(overallScore = selected.gopScore,
                        feedback = "Selected /$expectedIpa/ in “$targetWord”. Listen to the example and practise that sound within the word.")
                }
                return response
            } catch (e: CancellationException) {
                throw e
            } catch (e: PracticeScoringException) {
                throw e
            } catch (e: Exception) {
                if (consonant) throw PracticeScoringException(
                    "The phoneme service could not score /$expectedIpa/ in this word. Check the service and try recording again. No word-similarity score was substituted.")
                fallbackMessage = "The model service was unavailable or returned an invalid result; used formants and duration instead."
            }
        }

        return evaluateLocalAcousticGop(audioSamples, targetWord, expectedIpa, contrastId).let {
            if (fallbackMessage == null) it else it.copy(feedback = fallbackMessage)
        }
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

        val durationSec = audioSamplesDuration(samples)
        val phonemeScores = listOf(
            PhonemeScore(
                phoneme = targetPhoneme,
                gopScore = gopScore,
                startTime = 0f,
                endTime = durationSec
            )
        )

        return PhonemeScoringResponse(
            overallScore = gopScore,
            phonemes = phonemeScores,
            scoringMethod = "acoustic_estimate"
        )
    }

    private fun audioSamplesDuration(samples: ShortArray): Float = samples.size / 16000f
}

/** Hosts may set this before creating the root; no endpoint is configured by default. */
object PhonemeScorerConfiguration {
    var baseUrl: String? = null
    /** Runtime credential supplied by the host; never bundle it in app resources. */
    var functionKey: String? = null
}

expect fun defaultPhonemeScorerBaseUrl(): String?
expect fun defaultPhonemeScorerFunctionKey(): String?
