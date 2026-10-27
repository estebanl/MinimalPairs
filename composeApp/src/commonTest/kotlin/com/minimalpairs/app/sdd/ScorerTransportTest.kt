package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import com.minimalpairs.app.data.remote.PracticeScoringException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertFalse

class ScorerTransportTest {
    // Keep mock responses and retry deadlines on the same virtual clock.
    private fun TestScope.engine(handler: MockRequestHandler) = MockEngine(MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler(handler)
    })
    private val samples get() = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 160)
    private val validResponse = """{"overall_score":88.0,"phonemes":[{"phoneme":"iː","gop_score":88.0,"start_time":0.0,"end_time":0.15}],"scoring_method":"neural_ctc","model_id":"test-model"}"""

    @Test
    fun startupGatewayFailuresRetryTheRecordingAndReturnTheModelScore() = runTest {
        var requests = 0
        val client = HttpClient(engine { request ->
            assertEquals("test-only-key", request.headers["x-functions-key"])
            requests++
            when (requests) {
                1 -> respond("starting", HttpStatusCode.BadGateway)
                2 -> respond("starting", HttpStatusCode.ServiceUnavailable)
                3 -> respond("starting", HttpStatusCode.GatewayTimeout)
                else -> respond(validResponse, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) { install(ContentNegotiation) { json() } }
        try {
            val result = PhonemeScorerClient("https://scorer.example", client, "test-only-key")
                .scoreAudio(samples, "sheep", "iː", "i_vs_I")
            assertEquals("neural_ctc", result.scoringMethod)
            assertEquals(88f, result.overallScore)
            assertEquals(4, requests)
        } finally { client.close() }
    }

    @Test
    fun startupWaitHasOneOverallDeadline() = runTest {
        val client = HttpClient(engine {
            delay(121_000)
            respond(validResponse, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }
        try {
            val error = assertFailsWith<PracticeScoringException> {
                PhonemeScorerClient("https://scorer.example", client)
                    .scoreAudio(samples, "pear", "p", "p_vs_b")
            }
            assertTrue(error.message!!.contains("taking too long"))
            assertTrue(error.message!!.contains("No score was saved"))
        } finally { client.close() }
    }

    @Test
    fun persistentStartupFailureHasBoundedRetriesAndDoesNotInventAConsonantScore() = runTest {
        var requests = 0
        val client = HttpClient(engine { requests++; respond("starting", HttpStatusCode.ServiceUnavailable) })
        try {
            val error = assertFailsWith<PracticeScoringException> {
                PhonemeScorerClient("https://scorer.example", client)
                    .scoreAudio(samples, "pear", "p", "p_vs_b")
            }
            assertEquals(8, requests)
            assertTrue(error.message!!.contains("No score was saved"))
        } finally { client.close() }
    }

    @Test
    fun functionKeyIsSentOnlyAsHeaderAndBlankKeyIsOmitted() = runTest {
        for (key in listOf("test-only-key", "  ", null)) {
            val client = HttpClient(engine { request ->
                assertEquals(key?.takeIf { it.isNotBlank() }, request.headers["x-functions-key"])
                assertFalse(request.url.toString().contains("test-only-key"))
                assertNull(request.url.parameters["code"])
                respond(validResponse, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { install(ContentNegotiation) { json() } }
            try {
                assertEquals(88f, PhonemeScorerClient("https://scorer.example", client, key)
                    .scoreAudio(samples, "sheep", "iː", "i_vs_I").overallScore)
            } finally { client.close() }
        }
    }

    @Test
    fun keyIsNeverUploadedToPlainHttp() = runTest {
        var requests = 0
        val client = HttpClient(engine { requests++; error("Must not upload") })
        try {
            val error = assertFailsWith<PracticeScoringException> {
                PhonemeScorerClient("http://scorer.example", client, "test-only-key")
                    .scoreAudio(samples, "sheep", "iː", "i_vs_I")
            }
            assertTrue(error.message!!.contains("HTTPS"))
            assertEquals(0, requests)
        } finally { client.close() }
    }

    @Test
    fun rejectedKeyIsActionableWithoutBecomingAScoreOrExposingCredentials() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
            var requests = 0
            val client = HttpClient(engine { requests++; respond("private server text", status) })
            try {
                val error = assertFailsWith<PracticeScoringException> {
                    PhonemeScorerClient("https://scorer.example", client, "test-only-key")
                        .scoreAudio(samples, "pear", "p", "p_vs_b")
                }
                assertTrue(error.message!!.contains("rejected its access key"))
                assertFalse(error.message!!.contains("test-only-key"))
                assertFalse(error.message!!.contains("private server text"))
                assertEquals(1, requests)
            } finally { client.close() }
        }
    }

    @Test
    fun snakeCaseContractDecodesAndDisplaysProvenance() {
        val result = Json.decodeFromString<PhonemeScoringResponse>(validResponse)
        assertEquals(88f, result.overallScore)
        assertEquals(.15f, result.phonemes.single().endTime)
        assertTrue(result.scoringDescription.contains("test-model"))
    }

    @Test
    fun validRemoteModelResponseIsUsed() = runTest {
        val client = HttpClient(engine { request ->
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
            val client = HttpClient(engine {
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
        val client = HttpClient(engine { respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json")) }) {
            install(ContentNegotiation) { json() }
        }
        try {
            val result = PhonemeScorerClient("https://scorer.example", client).scoreAudio(samples, "sheep", "iː p", "i_vs_I")
            assertEquals("acoustic_estimate", result.scoringMethod)
        } finally { client.close() }
    }

    @Test
    fun cancellationNeverBecomesFallback() = runTest {
        val client = HttpClient(engine { throw CancellationException("Leaving screen") })
        try {
            assertFailsWith<CancellationException> {
                PhonemeScorerClient("https://scorer.example", client).scoreAudio(samples, "sheep", "iː", "i_vs_I")
            }
        } finally { client.close() }
    }
}
