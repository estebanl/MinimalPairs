package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.model.PhonemeScore
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.catalog.ConsonantScoringTargets
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import com.minimalpairs.app.data.remote.PracticeScoringException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ConsonantScoringTest {
    private val samples = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 500)

    @Test
    fun qualityRejectionsGiveRecordingGuidanceInsteadOfServiceFailureOrScore() = runBlocking {
        for ((code, guidance) in listOf("noisy_audio" to "Background noise", "clipped_audio" to "clipping",
            "no_clear_speech" to "Check your microphone", "multiple_utterances" to "once",
            "uncertain_alignment" to "could not identify /p/ reliably")) {
            val http = HttpClient(MockEngine {
                respond("""{"detail":{"code":"$code","message":"untrusted server text"}}""",
                    HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { expectSuccess = true; install(ContentNegotiation) { json() } }
            try {
                val error = assertFailsWith<PracticeScoringException> {
                    PhonemeScorerClient("https://scorer.example", http).scoreAudio(samples, "pear", "p", "p_vs_b")
                }
                assertTrue(error.message!!.contains(guidance))
                assertTrue(error.message!!.contains("No score was saved"))
                assertFalse(error.message!!.contains("untrusted server text"))
            } finally { http.close() }
        }
    }

    @Test
    fun everyConsonantTargetSendsFullWordAndSelectsItsExactOccurrence() = runBlocking {
        for (contrast in ListeningContrastsCatalog.defaultContrasts.filter { it.vowelReference == null }) {
            for (pair in contrast.pairs) for ((word, phoneme) in listOf(pair.word1 to contrast.phoneme1, pair.word2 to contrast.phoneme2)) {
                val target = assertNotNull(ConsonantScoringTargets.find(contrast.id, word, phoneme), "$word /$phoneme/")
                assertTrue(target.tokens.size > 1)
                assertEquals(phoneme, target.tokens[target.index])
                val response = PhonemeScoringResponse(72f, target.tokens.mapIndexed { i, token ->
                    PhonemeScore(token, if (i == target.index) 72f else 10f, i * .05f, (i + 1) * .05f)
                }, "neural_ctc_phoneme", "test-model", targetPhonemeIndex = target.index)
                val http = HttpClient(MockEngine { request ->
                    val body = request.body.toByteArray().decodeToString()
                    assertTrue(body.contains("name=expected_ipa") || body.contains("name=\"expected_ipa\""))
                    assertTrue(body.contains(target.ipa))
                    assertTrue(body.contains("target_phoneme_index"))
                    assertTrue(body.contains("\r\n${target.index}\r\n"))
                    respond(Json.encodeToString(response), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }) { install(ContentNegotiation) { json() } }
                try {
                    val result = PhonemeScorerClient("https://scorer.example", http).scoreAudio(samples, word, phoneme, contrast.id)
                    assertEquals(72f, result.overallScore, "$word must use selected phoneme, not mean")
                    assertEquals(target.index, result.targetPhonemeIndex)
                    assertTrue(result.scoringDescription.contains("Selected-phoneme"))
                } finally { http.close() }
            }
        }
    }

    @Test
    fun initialMedialAndFinalTargetsHaveCorrectPositions() {
        assertEquals(0, ConsonantScoringTargets.find("p_vs_b", "pear", "p")!!.index)
        assertEquals(2, ConsonantScoringTargets.find("sh_vs_zh", "measure", "ʒ")!!.index)
        assertEquals(2, ConsonantScoringTargets.find("p_vs_b", "rope", "p")!!.index)
        assertEquals(1, ConsonantScoringTargets.find("l_vs_r", "glass", "l")!!.index)
        assertNull(ConsonantScoringTargets.find("p_vs_b", "pear", "b"))
    }

    @Test
    fun unavailableMalformedOrWholeWordResponsesNeverBecomeFallbackScores() = runBlocking {
        val good = PhonemeScoringResponse(72f, listOf(PhonemeScore("p", 72f, 0f, .1f),
            PhonemeScore("eə", 10f, .1f, .3f)), "neural_ctc_phoneme", "test", targetPhonemeIndex = 0)
        val bodies = listOf("unavailable", Json.encodeToString(good.copy(targetPhonemeIndex = 1)),
            Json.encodeToString(good.copy(scoringMethod = "word_similarity")),
            Json.encodeToString(good.copy(overallScore = 41f)),
            Json.encodeToString(good.copy(phonemes = good.phonemes.reversed())),
            Json.encodeToString(good.copy(modelId = null)))
        for (body in bodies) {
            val http = HttpClient(MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json")) }) {
                install(ContentNegotiation) { json() }
            }
            try {
                assertFailsWith<PracticeScoringException> { PhonemeScorerClient("https://scorer.example", http).scoreAudio(samples, "pear", "p", "p_vs_b") }
                assertFailsWith<PracticeScoringException> { PhonemeScorerClient(null, http).scoreAudio(samples, "pear", "p", "p_vs_b") }
            } finally { http.close() }
        }
        val http = HttpClient(MockEngine { throw CancellationException() })
        try {
            assertFailsWith<CancellationException> { PhonemeScorerClient("https://scorer.example", http).scoreAudio(samples, "pear", "p", "p_vs_b") }
            Unit
        } finally { http.close() }
    }
}
