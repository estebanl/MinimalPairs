package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.audio.ElevenLabsAudioRepository
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import com.minimalpairs.app.data.remote.PracticeScoringException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ConsonantScoringTest {
    @Test
    fun everyConsonantPairScoresBothBundledWordsOfflineAndDistinguishesTheOtherWord() = runBlocking {
        val http = HttpClient(MockEngine { error("Consonant word comparison must work offline") })
        try {
            val scorer = PhonemeScorerClient("https://unused.example", http)
            for (contrast in ListeningContrastsCatalog.defaultContrasts.filter { it.vowelReference == null }) {
                for (pair in contrast.pairs) {
                    val a = assertNotNull(ElevenLabsAudioRepository.loadWordPcm(pair.word1))
                    val b = assertNotNull(ElevenLabsAudioRepository.loadWordPcm(pair.word2))
                    for (first in listOf(true, false)) {
                        val word = if (first) pair.word1 else pair.word2
                        val phoneme = if (first) contrast.phoneme1 else contrast.phoneme2
                        val result = scorer.scoreAudio(if (first) a else b, word, phoneme, contrast.id)
                        assertEquals(100f, result.overallScore, .01f, word)
                        assertEquals("word_similarity", result.scoringMethod)
                        assertTrue(result.phonemes.isEmpty(), "No invented phoneme alignment")
                        assertTrue(result.scoringDescription.contains("whole recording"))
                        val wrong = scorer.scoreAudio(if (first) b else a, word, phoneme, contrast.id)
                        assertTrue(wrong.overallScore < 1f, word)
                    }
                }
            }
        } finally { http.close() }
    }

    @Test
    fun unavailableReferencesAndCancellationNeverBecomePlaceholderScores() = runBlocking {
        val audio = assertNotNull(ElevenLabsAudioRepository.loadWordPcm("pear"))
        val http = HttpClient(MockEngine { error("Unexpected network request") })
        try {
            val missing = PhonemeScorerClient(null, http, loadWordPcm = { null })
            assertFailsWith<PracticeScoringException> { missing.scoreAudio(audio, "pear", "p", "p_vs_b") }
            val cancelled = PhonemeScorerClient(null, http, loadWordPcm = { throw CancellationException() })
            assertFailsWith<CancellationException> { cancelled.scoreAudio(audio, "pear", "p", "p_vs_b") }
            assertFailsWith<PracticeScoringException> { missing.scoreAudio(audio, "pear", "b", "p_vs_b") }
            Unit
        } finally { http.close() }
    }
}
