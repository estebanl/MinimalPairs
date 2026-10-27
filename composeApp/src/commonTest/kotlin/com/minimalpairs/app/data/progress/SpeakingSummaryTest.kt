package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.SpeakingRecordEntity
import kotlin.test.*

class SpeakingSummaryTest {
    private fun record(index: Int, score: Float, contrast: String = "i_vs_I", phoneme: String = "iː") =
        SpeakingRecordEntity(index.toLong(), contrast, "sheep", phoneme, score, 200, index * 1000L)

    @Test
    fun selectedPhonemeScoresDoNotMixWithOldWordSimilarityOrUnknownScores() {
        val records = listOf(
            record(1, 100f, "p_vs_b", "p"),
            record(2, 90f, "p_vs_b", "p").copy(scoringMethod = "word_similarity"),
            record(3, 60f, "p_vs_b", "p").copy(scoringMethod = "neural_ctc_phoneme")
        )
        val summaries = summarizeSpeaking(records)
        assertEquals(3, summaries.size)
        assertTrue(summaries.all { it.attempts == 1 && it.changePoints == null })
    }

    @Test
    fun comparesTwoRecentSamplesAndKeepsBestAcrossAllAttempts() {
        val records = listOf(record(0, 100f)) + (1..10).map { record(it, if (it <= 5) 40f else 80f) }
        val summary = summarizeSpeaking(records.shuffled()).single()
        assertEquals(11, summary.attempts)
        assertEquals(100, summary.bestScore)
        assertEquals(10L, summary.latest.id)
        assertEquals(80, summary.recentAverage)
        assertEquals(40, summary.previousAverage)
        assertEquals(40, summary.changePoints)
    }

    @Test
    fun incompleteSamplesDoNotClaimAChange() {
        assertTrue(summarizeSpeaking(emptyList()).isEmpty())
        for (count in 1..9) {
            val summary = summarizeSpeaking((1..count).map { record(it, 70f) }).single()
            assertNull(summary.changePoints)
            assertEquals(minOf(5, count), summary.recentCount)
            assertEquals(maxOf(0, count - 5), summary.previousCount)
        }
    }

    @Test
    fun differentSoundsAndContrastsStaySeparateAndLowestAverageComesFirst() {
        val summaries = summarizeSpeaking(listOf(
            record(1, 90f), record(2, 50f, phoneme = "ɪ"), record(3, 70f, contrast = "e_vs_I", phoneme = "ɪ")
        ))
        assertEquals(3, summaries.size)
        assertEquals(listOf(50, 70, 90), summaries.map { it.recentAverage })
        assertTrue(summaries.all { it.attempts == 1 })
    }

    @Test
    fun supportsDecliningAndUnchangedScoresAndIgnoresInvalidValues() {
        val down = summarizeSpeaking((1..10).map { record(it, if (it <= 5) 80f else 40f) }).single()
        assertEquals(-40, down.changePoints)
        val same = summarizeSpeaking((1..10).map { record(it, 70f) } + record(11, Float.NaN) + record(12, 101f)).single()
        assertEquals(0, same.changePoints)
        assertEquals(10, same.attempts)
    }
}
