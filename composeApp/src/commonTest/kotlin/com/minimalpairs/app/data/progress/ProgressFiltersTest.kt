package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.test.*

class ProgressFiltersTest {
    private val calendar = PracticeCalendar(LocalDate.parse("2026-03-10"), TimeZone.of("America/Toronto"))
    private fun ms(value: String) = Instant.parse(value).toEpochMilliseconds()
    private fun trial(id: Long, at: String, contrast: String = "i_vs_I", correct: Boolean = true) = TrialRecordEntity(
        id, contrast, "sheep", if (correct) "sheep" else "ship", correct, 200, "FORCED_CHOICE", ms(at))

    @Test
    fun rangesUseInclusiveLocalDatesAcrossDaylightSavingAndRejectFutureDates() {
        val week = ProgressFilters(ProgressDateRange.LAST_7_DAYS)
        assertFalse(week.includes("i_vs_I", ms("2026-03-04T04:59:59Z"), calendar))
        assertTrue(week.includes("i_vs_I", ms("2026-03-04T05:00:00Z"), calendar))
        assertTrue(week.includes("i_vs_I", ms("2026-03-11T03:59:59Z"), calendar))
        assertFalse(week.includes("i_vs_I", ms("2026-03-11T04:00:00Z"), calendar))
        val month = ProgressFilters(ProgressDateRange.LAST_30_DAYS)
        assertFalse(month.includes("i_vs_I", ms("2026-02-09T04:59:59Z"), calendar))
        assertTrue(month.includes("i_vs_I", ms("2026-02-09T05:00:00Z"), calendar))
        assertTrue(ProgressFilters().includes("i_vs_I", 0, calendar))
    }

    @Test
    fun filtersScopeResultsAndHistoriesButPreserveActivityAndReviews() {
        val trials = listOf(
            trial(1, "2026-02-01T12:00:00Z"),
            trial(2, "2026-03-09T12:00:00Z", "p_vs_b"),
            trial(3, "2026-03-10T12:00:00Z", correct = false))
        val speaking = trials.map { SpeakingRecordEntity(it.id, it.contrastId, "sheep", "iː", it.id * 10f, 200, it.timestampEpochMs) }
        val snapshot = ProgressSnapshot(trials, listOf(Sm2CardEntity("p_vs_b", 1, 1, 2.5f, .5f, 0)), speaking)
        val filtered = summarizeFilteredProgress(snapshot, calendar,
            ProgressFilters(ProgressDateRange.LAST_7_DAYS, "i_vs_I"), ms("2026-03-10T12:00:00Z"))
        assertEquals(1, filtered.attempts)
        assertEquals(0, filtered.accuracy)
        assertEquals(listOf(3L), filtered.listeningHistory.map { it.id })
        assertEquals(listOf(3L), filtered.speaking.map { it.id })
        assertEquals(30, filtered.speakingSummaries.single().recentAverage)
        assertEquals(2, filtered.activity.currentStreak)
        assertEquals("p_vs_b", filtered.reviews.due.single().contrastId)
        assertEquals(1, filtered.trend.recent.attempts)
        assertEquals(setOf("iː"), filtered.confusion.keys)
        val empty = summarizeFilteredProgress(snapshot, calendar, ProgressFilters(contrastId = "ae_vs_e"), ms("2026-03-10T12:00:00Z"))
        assertEquals(0, empty.attempts)
        assertTrue(empty.speaking.isEmpty())
        assertEquals(filtered.activity, empty.activity)
        assertEquals(filtered.reviews, empty.reviews)
    }

    @Test
    fun fullHistoryIsRetainedAndOrderedWhileTrendUsesMatchingSamplesOnly() {
        val trials = (1L..45L).map { trial(it, "2026-03-10T12:00:00Z", correct = it > 25) }
        val state = summarizeFilteredProgress(ProgressSnapshot(trials, emptyList(), emptyList()), calendar,
            ProgressFilters(contrastId = "i_vs_I"), ms("2026-03-10T12:00:00Z"))
        assertEquals((45L downTo 1L).toList(), state.listeningHistory.map { it.id })
        assertEquals(20, state.recentTrials.size)
        assertEquals(100, state.trend.changePoints)
    }
}
