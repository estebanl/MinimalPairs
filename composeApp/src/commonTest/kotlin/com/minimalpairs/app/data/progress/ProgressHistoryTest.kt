package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.TrialRecordEntity
import com.minimalpairs.app.ui.screens.answerAge
import kotlin.test.*

class ProgressHistoryTest {
    @Test
    fun trendUsesOnlyTwoMostRecentNonOverlappingSamples() {
        // Input deliberately oldest-first; older errors must not enter either sample.
        val trials = (1..45).map { index -> trial(index, index > 25) }
        val state = summarizeProgress(trials, emptyList())
        assertEquals(AccuracySample(20, 20), state.trend.recent)
        assertEquals(AccuracySample(20, 0), state.trend.previous)
        assertEquals(100, state.trend.changePoints)
        assertEquals((45L downTo 26L).toList(), state.recentTrials.map { it.id })
        assertEquals(45, state.attempts)
        assertEquals(44, state.accuracy)
    }

    @Test
    fun incompleteSamplesDoNotClaimImprovement() {
        for (size in listOf(0, 1, 19, 20, 21, 39)) {
            val state = summarizeProgress((1..size).map { trial(it, it > 20) }, emptyList())
            assertNull(state.trend.changePoints, "Sample size $size")
            assertEquals(minOf(size, 20), state.trend.recent.attempts)
            assertEquals(maxOf(size - 20, 0), state.trend.previous.attempts)
        }
        assertNull(summarizeProgress(emptyList(), emptyList()).trend.recent.accuracy)
    }

    @Test
    fun trendCanDecreaseOrStayUnchangedAndTimestampTiesUseNewestId() {
        val decreasing = summarizeProgress((1..40).map { trial(it, it <= 20) }, emptyList())
        assertEquals(-100, decreasing.trend.changePoints)
        val unchanged = summarizeProgress((1..40).map { trial(it, it % 2 == 0) }, emptyList())
        assertEquals(0, unchanged.trend.changePoints)
        val ties = summarizeProgress(listOf(trial(1, true), trial(2, false)).map { it.copy(timestampEpochMs = 100) }, emptyList())
        assertEquals(listOf(2L, 1L), ties.recentTrials.map { it.id })
    }

    @Test
    fun historyAgeHandlesUnitsAndFutureDeviceTimestamps() {
        assertEquals("Just now", answerAge(100, 0))
        assertEquals("Just now", answerAge(0, 59_999))
        assertEquals("1 minute ago", answerAge(0, 60_000))
        assertEquals("2 minutes ago", answerAge(0, 120_000))
        assertEquals("1 hour ago", answerAge(0, 3_600_000))
        assertEquals("1 day ago", answerAge(0, 86_400_000))
    }

    private fun trial(index: Int, correct: Boolean) = TrialRecordEntity(
        id = index.toLong(), contrastId = "i_vs_I", targetWord = "sheep",
        selectedWord = if (correct) "sheep" else "ship", isCorrect = correct,
        latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = index * 1000L
    )
}
