package com.minimalpairs.app.data.remote

import com.minimalpairs.app.core.dsp.LpcFormantExtractor
import com.minimalpairs.app.core.dsp.WavCodec
import com.minimalpairs.app.core.dsp.WordSimilarityScorer
import com.minimalpairs.app.data.audio.ElevenLabsAudioRepository
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.minimalpairs.app.core.model.PhonemeScore
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
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
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    },
    private val loadWordPcm: suspend (String) -> ShortArray? = ElevenLabsAudioRepository::loadWordPcm
) : PhonemeScorerApi {

    override suspend fun scoreAudio(
        audioSamples: ShortArray,
        targetWord: String,
        expectedIpa: String,
        contrastId: String?
    ): PhonemeScoringResponse {
        val practice = contrastId?.let(ListeningContrastsCatalog::getContrastById)
        if (practice != null && practice.vowelReference == null) {
            val first = when (expectedIpa) {
                practice.phoneme1 -> true
                practice.phoneme2 -> false
                else -> throw PracticeScoringException("The selected sound does not match this sound pair.")
            }
            val pair = practice.pairs.firstOrNull {
                (if (first) it.word1 else it.word2).equals(targetWord, ignoreCase = true)
            } ?: throw PracticeScoringException("The selected word does not match this sound pair.")
            if (!WordSimilarityScorer.hasUsableAudio(audioSamples)) {
                throw PracticeScoringException("No clear sound detected. Record the whole word and try again.")
            }
            val target = loadWordPcm(if (first) pair.word1 else pair.word2)
            currentCoroutineContext().ensureActive()
            val alternative = loadWordPcm(if (first) pair.word2 else pair.word1)
            currentCoroutineContext().ensureActive()
            if (target == null || alternative == null || !WordSimilarityScorer.hasUsableAudio(target) ||
                !WordSimilarityScorer.hasUsableAudio(alternative)) {
                throw PracticeScoringException("The example audio needed for scoring is unavailable. Please try again.")
            }
            val score = WordSimilarityScorer.score(audioSamples, target, alternative)
            currentCoroutineContext().ensureActive()
            return PhonemeScoringResponse(
                overallScore = score,
                phonemes = emptyList(), // Whole-word comparison has no isolated phoneme scores or boundaries.
                scoringMethod = "word_similarity",
                feedback = "Compare your recording with “$targetWord”, then try again. Keep the whole word clear and focus on /$expectedIpa/."
            )
        }
        // Remote failure remains visible when falling back to the local estimate.
        var fallbackMessage: String? = null
        if (!baseUrl.isNullOrBlank()) {
            try {
                val wavBytes = WavCodec.encodePcmToWav(audioSamples)
                val response: PhonemeScoringResponse = client.post("${baseUrl.trimEnd('/')}/v1/score/phoneme") {
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
                require(response.overallScore.isFinite() && response.overallScore in 0f..100f)
                require(response.scoringMethod == "neural_ctc" && !response.modelId.isNullOrBlank())
                val expectedTokens = expectedIpa.trim().trim('/').split(Regex("\\s+")).filter { it.isNotBlank() }
                require(response.phonemes.map { it.phoneme } == expectedTokens)
                require(response.phonemes.zipWithNext().all { (before, after) -> before.endTime <= after.startTime })
                require(response.phonemes.isNotEmpty() && response.phonemes.all {
                    it.gopScore.isFinite() && it.gopScore in 0f..100f &&
                        it.startTime.isFinite() && it.endTime.isFinite() &&
                        it.startTime >= 0f && it.endTime > it.startTime &&
                        it.endTime <= audioSamples.size / 16000f + .001f
                })
                return response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
}

expect fun defaultPhonemeScorerBaseUrl(): String?
