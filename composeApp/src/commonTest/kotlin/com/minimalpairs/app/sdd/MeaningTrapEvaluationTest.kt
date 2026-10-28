package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.model.PhonemeScore
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import com.minimalpairs.app.data.remote.PracticeScoringException
import com.minimalpairs.app.domain.MeaningTrapEvaluator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class MeaningTrapEvaluationTest {
    private val samples = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 3000)

    @Test
    fun everyStoryAlignsTheFullPhraseAndChangesOnlyTheTargetVowel() = runTest {
        for (story in MeaningTrapCatalog.stories) {
            var calls = 0
            val tokens = story.targetPhraseIpa.split(" ")
            val index = story.targetPhonemeIndex
            val trapVowel = story.trapIpa.split(" ")[1]
            val trapTokens = tokens.toMutableList().apply { this[index] = trapVowel }
            val trapPhrase = story.targetPhrase.replace(story.intendedWord, story.trapWord)
            val http = HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val intended = calls++ == 0
                    val expectedTokens = if (intended) tokens else trapTokens
                    val expectedPhrase = if (intended) story.targetPhrase else trapPhrase
                    val body = request.body.toByteArray().decodeToString()
                    assertTrue(body.contains("\r\n$expectedPhrase\r\n"))
                    assertTrue(body.contains("\r\n${expectedTokens.joinToString(" ")}\r\n"))
                    assertTrue(body.contains("target_phoneme_index"))
                    assertTrue(body.contains("\r\n$index\r\n"))
                    assertTrue(body.contains("utterance_kind"))
                    assertTrue(body.contains("\r\nphrase\r\n"))
                    val alternative = if (intended) trapVowel else tokens[index]
                    assertTrue(body.contains("alternative_phoneme"))
                    assertTrue(body.contains("\r\n$alternative\r\n"))
                    respond(Json.encodeToString(response(expectedTokens, index, if (intended) 85f else 25f,
                        alternative = alternative, surroundingScore = if (intended) 10f else 99f)),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            })) { install(ContentNegotiation) { json() } }
            try {
                val result = MeaningTrapEvaluator(PhonemeScorerClient("https://scorer.example", http)).evaluate(samples, story)
                assertEquals(MeaningTrapBranch.INTENDED_SUCCESS, result.branch)
                assertEquals(85f, result.intendedScore, "The phrase average must not choose the ending")
                assertEquals(25f, result.trapScore)
                assertEquals(story.intendedIpa.split(" ")[1], result.intendedPhoneme)
                assertEquals(trapVowel, result.trapPhoneme)
                assertEquals(2, calls)
            } finally { http.close() }
        }
    }

    @Test
    fun onlyAStrongDistinctCandidateChoosesAnEnding() = runTest {
        val story = MeaningTrapCatalog.stories.first()
        for ((intended, trap, branch) in listOf(
            Triple(80f, 65f, MeaningTrapBranch.INTENDED_SUCCESS),
            Triple(65f, 80f, MeaningTrapBranch.TRAP_TRIGGERED),
            Triple(79f, 20f, MeaningTrapBranch.PENDING),
            Triple(20f, 79f, MeaningTrapBranch.PENDING),
            Triple(78f, 22f, MeaningTrapBranch.PENDING),
            Triple(22f, 78f, MeaningTrapBranch.PENDING),
            Triple(85f, 71f, MeaningTrapBranch.PENDING),
            Triple(71f, 85f, MeaningTrapBranch.PENDING),
            Triple(95f, 95f, MeaningTrapBranch.PENDING),
            Triple(10f, 15f, MeaningTrapBranch.PENDING)
        )) {
            val scorer = Scorer(story.targetPhonemeIndex, intended, trap)
            assertEquals(branch, MeaningTrapEvaluator(scorer).evaluate(samples, story).branch, "$intended vs $trap")
        }
    }

    @Test
    fun estimatesUnknownModelsAndInvalidAlignmentsNeverChooseAnEnding() = runTest {
        val story = MeaningTrapCatalog.stories.first()
        val mutations: List<(PhonemeScoringResponse) -> PhonemeScoringResponse> = listOf(
            { it.copy(scoringMethod = "acoustic_estimate") },
            { it.copy(scoringMethod = "unknown") },
            { it.copy(scoringMethod = "neural_ctc") },
            { it.copy(scoringMethod = "neural_ctc_phoneme") },
            { it.copy(scoringMethod = "neural_ctc_phrase_phoneme") },
            { it.copy(modelId = null) },
            { it.copy(alternativePhoneme = null) },
            { it.copy(alternativePhoneme = "uː") },
            { it.copy(contrastEvidence = null) },
            { it.copy(contrastEvidence = Float.NaN) },
            { it.copy(contrastEvidence = .09f) },
            { it.copy(contrastEvidence = 1.01f) },
            { it.copy(overallScore = Float.NaN) },
            { it.copy(overallScore = 0f) },
            { it.copy(targetPhonemeIndex = 0) },
            { it.copy(phonemes = it.phonemes.drop(1)) },
            { it.copy(phonemes = it.phonemes.reversed()) },
            { it.copy(phonemes = it.phonemes.map { p -> p.copy(gopScore = Float.NaN) }) },
            { it.copy(phonemes = it.phonemes.map { p -> p.copy(gopScore = 101f) }) },
            { it.copy(phonemes = it.phonemes.map { p -> p.copy(startTime = -1f) }) },
            { it.copy(phonemes = it.phonemes.map { p -> p.copy(endTime = 4f) }) },
            { it.copy(phonemes = it.phonemes.map { p -> p.copy(startTime = 0f) }) }
        )
        for (mutate in mutations) for (invalidCall in listOf(1, 2)) {
            val scorer = Scorer(story.targetPhonemeIndex, 90f, 20f).apply {
                transform = { call, result -> if (call == invalidCall) mutate(result) else result }
            }
            assertFailsWith<PracticeScoringException> { MeaningTrapEvaluator(scorer).evaluate(samples, story) }
        }
    }

    @Test
    fun differentModelsCannotBeCompared() = runTest {
        val story = MeaningTrapCatalog.stories.first()
        val scorer = Scorer(story.targetPhonemeIndex, 90f, 20f).apply {
            transform = { call, result -> result.copy(modelId = "model-$call") }
        }
        assertFailsWith<PracticeScoringException> { MeaningTrapEvaluator(scorer).evaluate(samples, story) }
    }

    @Test
    fun differentRegionsOrEvidenceCannotChooseAnEnding() = runTest {
        val story = MeaningTrapCatalog.stories.first()
        val changes: List<(PhonemeScoringResponse) -> PhonemeScoringResponse> = listOf(
            { it.copy(contrastEvidence = .4f) },
            { it.copy(phonemes = it.phonemes.mapIndexed { index, phoneme ->
                if (index == story.targetPhonemeIndex) phoneme.copy(startTime = phoneme.startTime + .01f) else phoneme
            }) }
        )
        for (change in changes) {
            val scorer = Scorer(story.targetPhonemeIndex, 90f, 10f).apply {
                transform = { call, result -> if (call == 2) change(result) else result }
            }
            val error = assertFailsWith<PracticeScoringException> { MeaningTrapEvaluator(scorer).evaluate(samples, story) }
            assertTrue(error.message!!.contains("same part of your reply"))
        }
    }

    private class Scorer(val index: Int, val intended: Float, val trap: Float) : PhonemeScorerApi {
        var calls = 0
        var transform: (Int, PhonemeScoringResponse) -> PhonemeScoringResponse = { _, result -> result }
        override suspend fun scoreAudio(audioSamples: ShortArray, targetWord: String, expectedIpa: String, contrastId: String?): PhonemeScoringResponse {
            error("Stories must use phrase scoring")
        }
        override suspend fun scorePhraseAudio(audioSamples: ShortArray, transcript: String, expectedIpa: String, targetPhonemeIndex: Int, alternativePhoneme: String?): PhonemeScoringResponse {
            calls++
            assertEquals(index, targetPhonemeIndex)
            return transform(calls, response(expectedIpa.split(" "), index, if (calls == 1) intended else trap,
                alternative = assertNotNull(alternativePhoneme)))
        }
    }

    companion object {
        private fun response(tokens: List<String>, index: Int, score: Float, alternative: String, surroundingScore: Float = 50f) =
            PhonemeScoringResponse(score, tokens.mapIndexed { i, phoneme ->
                PhonemeScore(phoneme, if (i == index) score else surroundingScore, i * .05f, (i + 1) * .05f)
            }, "neural_ctc_phrase_contrast", "test-model", targetPhonemeIndex = index,
                alternativePhoneme = alternative, contrastEvidence = .5f)
    }
}
