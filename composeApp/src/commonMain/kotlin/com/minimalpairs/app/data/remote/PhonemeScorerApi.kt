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

    suspend fun scorePhraseAudio(
        audioSamples: ShortArray,
        transcript: String,
        expectedIpa: String,
        targetPhonemeIndex: Int,
        alternativePhoneme: String? = null
    ): PhonemeScoringResponse {
        throw PracticeScoringException("Phrase scoring is unavailable. Check the phoneme service connection and try again. No result was saved.")
    }
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
    ): PhonemeScoringResponse = scoreRequest(audioSamples, targetWord, expectedIpa, contrastId)

    override suspend fun scorePhraseAudio(
        audioSamples: ShortArray,
        transcript: String,
        expectedIpa: String,
        targetPhonemeIndex: Int,
        alternativePhoneme: String?
    ): PhonemeScoringResponse {
        val tokens = expectedIpa.trim().split(Regex("\\s+"))
        require(tokens.size > 1 && targetPhonemeIndex in tokens.indices)
        require(alternativePhoneme == null || (alternativePhoneme.isNotBlank() &&
            alternativePhoneme.split(Regex("\\s+")).size == 1 && alternativePhoneme != tokens[targetPhonemeIndex]))
        return scoreRequest(audioSamples, transcript, expectedIpa, null, targetPhonemeIndex, alternativePhoneme)
    }

    private suspend fun scoreRequest(
        audioSamples: ShortArray,
        targetWord: String,
        expectedIpa: String,
        contrastId: String?,
        phraseTargetIndex: Int? = null,
        alternativePhoneme: String? = null
    ): PhonemeScoringResponse {
        val phrase = phraseTargetIndex != null
        val practice = contrastId?.let(ListeningContrastsCatalog::getContrastById)
        val consonant = practice != null && practice.vowelReference == null
        val strict = consonant || phrase
        val unit = if (phrase) "phrase" else "word"
        val retrySuffix = if (phrase) "No result was saved." else "No score was saved; your recording can still be replayed."
        val target = if (consonant) {
            ConsonantScoringTargets.find(practice.id, targetWord, expectedIpa)
                ?: throw PracticeScoringException("The selected sound has no matching word transcript.")
        } else null
        if (strict) {
            if (!SpeechAudioValidation.hasUsableAudio(audioSamples)) {
                throw PracticeScoringException("No clear sound detected. Record the whole $unit and try again.")
            }
            if (baseUrl.isNullOrBlank()) {
                if (phrase) throw PracticeScoringException("Phrase scoring needs the phoneme service. Configure its address, then record again. No result was saved.")
                throw PracticeScoringException("Consonant scoring needs the phoneme service. Configure its address, then record again. Your recording can still be replayed.")
            }
        }
        val requestIpa = target?.ipa ?: expectedIpa
        val selectedIndex = phraseTargetIndex ?: target?.index
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
                                selectedIndex?.let { append("target_phoneme_index", it.toString()) }
                                if (phrase) append("utterance_kind", "phrase")
                                alternativePhoneme?.let { append("alternative_phoneme", it) }
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
                    "The phoneme service is taking too long to start. Try again shortly. $retrySuffix")
                if (httpResponse.status.value == 401 || httpResponse.status.value == 403) {
                    throw PracticeScoringException("The phoneme service rejected its access key. Check the scorer configuration and try again. $retrySuffix")
                }
                if (strict && httpResponse.status.value == 400) {
                    val code = runCatching {
                        Json.parseToJsonElement(httpResponse.bodyAsText()).jsonObject["detail"]
                            ?.jsonObject?.get("code")?.jsonPrimitive?.content
                    }.getOrNull()
                    val guidance = when (code) {
                        "no_clear_speech" -> "No clear voice was captured. Check your microphone and record the whole $unit again."
                        "noisy_audio" -> "Background noise is too strong for a reliable score. Move closer to the microphone and try in a quieter place."
                        "clipped_audio" -> "The microphone is clipping. Move farther away or lower its input level."
                        "multiple_utterances" -> "Say the selected $unit once, without other speech."
                        "uncertain_alignment" -> if (phrase) "The model could not locate the target sound reliably. Record the full phrase again."
                            else "The model could not identify /$expectedIpa/ reliably. Record the word again."
                        else -> null
                    }
                    if (guidance != null) throw PracticeScoringException("$guidance ${if (phrase) retrySuffix else "No score was saved. You can replay your recording."}")
                }
                if (httpResponse.status.value != 200 && strict) {
                    throw PracticeScoringException("The phoneme service returned HTTP ${httpResponse.status.value}. Try again shortly. $retrySuffix")
                }
                require(httpResponse.status.value == 200)
                val response: PhonemeScoringResponse = httpResponse.body()
                require(response.overallScore.isFinite() && response.overallScore in 0f..100f)
                val method = if (alternativePhoneme != null) "neural_ctc_phrase_contrast"
                    else if (phrase) "neural_ctc_phrase_phoneme" else if (consonant) "neural_ctc_phoneme" else "neural_ctc"
                require(response.scoringMethod == method && !response.modelId.isNullOrBlank())
                if (alternativePhoneme != null) {
                    require(response.alternativePhoneme == alternativePhoneme)
                    require(response.contrastEvidence?.let { it.isFinite() && it in .1f..1f } == true)
                }
                val expectedTokens = requestIpa.trim().trim('/').split(Regex("\\s+")).filter { it.isNotBlank() }
                require(response.phonemes.map { it.phoneme } == expectedTokens)
                require(response.phonemes.zipWithNext().all { (before, after) -> before.endTime <= after.startTime })
                require(response.phonemes.isNotEmpty() && response.phonemes.all {
                    it.gopScore.isFinite() && it.gopScore in 0f..100f &&
                        it.startTime.isFinite() && it.endTime.isFinite() &&
                        it.startTime >= 0f && it.endTime > it.startTime &&
                        it.endTime <= audioSamples.size / 16000f + .001f
                })
                if (selectedIndex != null) {
                    require(response.targetPhonemeIndex == selectedIndex)
                    val selected = response.phonemes[selectedIndex]
                    require(abs(response.overallScore - selected.gopScore) < .001f)
                    if (phrase) return response
                    require(selected.phoneme == expectedIpa)
                    return response.copy(overallScore = selected.gopScore,
                        feedback = "Selected /$expectedIpa/ in “$targetWord”. Listen to the example and practise that sound within the word.")
                }
                return response
            } catch (e: CancellationException) {
                throw e
            } catch (e: PracticeScoringException) {
                throw e
            } catch (e: Exception) {
                if (phrase) throw PracticeScoringException("The phoneme service could not align this phrase. Check that the service supports phrase scoring and try again. No result was saved.")
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
