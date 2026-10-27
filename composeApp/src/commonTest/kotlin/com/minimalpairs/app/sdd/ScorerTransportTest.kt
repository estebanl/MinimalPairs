package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScorerTransportTest {
    private val samples get() = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 160)
    private val validResponse = """{"overall_score":88.0,"phonemes":[{"phoneme":"iː","gop_score":88.0,"start_time":0.0,"end_time":0.15}],"scoring_method":"neural_ctc","model_id":"test-model"}"""

    @Test
    fun snakeCaseContractDecodesAndDisplaysProvenance() {
        val result = Json.decodeFromString<PhonemeScoringResponse>(validResponse)
        assertEquals(88f, result.overallScore)
        assertEquals(.15f, result.phonemes.single().endTime)
        assertTrue(result.scoringDescription.contains("test-model"))
    }

    @Test
    fun validRemoteModelResponseIsUsed() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("https://scorer.example/v1/score/phoneme", request.url.toString())
            respond(validResponse, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }
        try {
            val result = PhonemeScorerClient("https://scorer.example/", client).scoreAudio(samples, "sheep", "iː", "i_vs_I")
            assertEquals("neural_ctc", result.scoringMethod)
            assertEquals(88f, result.overallScore)
        } finally { client.close() }
    }

    @Test
    fun unavailableAndInvalidRemoteResultsExplainOfflineFallback() = runTest {
        for (body in listOf("unavailable", validResponse.replace("88.0", "188.0"),
            validResponse.replace("neural_ctc", "unknown"), validResponse.replace("0.15", "10.0"),
            validResponse.replace("iː", "ɪ"))) {
            val client = HttpClient(MockEngine {
                respond(body, status = if (body == "unavailable") HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { install(ContentNegotiation) { json() } }
            try {
                val result = PhonemeScorerClient("https://scorer.example", client).scoreAudio(samples, "sheep", "iː", "i_vs_I")
                assertEquals("acoustic_estimate", result.scoringMethod)
                assertTrue(result.scoringDescription.contains("model service was unavailable"))
            } finally { client.close() }
        }
    }

    @Test
    fun overlappingReturnedSegmentsFallBack() = runTest {
        val body = """{"overall_score":88.0,"phonemes":[{"phoneme":"iː","gop_score":88.0,"start_time":0.0,"end_time":0.12},{"phoneme":"p","gop_score":88.0,"start_time":0.1,"end_time":0.15}],"scoring_method":"neural_ctc","model_id":"test-model"}"""
        val client = HttpClient(MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json")) }) {
            install(ContentNegotiation) { json() }
        }
        try {
            val result = PhonemeScorerClient("https://scorer.example", client).scoreAudio(samples, "sheep", "iː p", "i_vs_I")
            assertEquals("acoustic_estimate", result.scoringMethod)
        } finally { client.close() }
    }

    @Test
    fun cancellationNeverBecomesFallback() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("Leaving screen") })
        try {
            assertFailsWith<CancellationException> {
                PhonemeScorerClient("https://scorer.example", client).scoreAudio(samples, "sheep", "iː", "i_vs_I")
            }
        } finally { client.close() }
    }
}
