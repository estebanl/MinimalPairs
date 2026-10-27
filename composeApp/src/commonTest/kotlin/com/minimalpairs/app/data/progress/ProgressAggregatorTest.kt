package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.*

class ProgressAggregatorTest {
    private val calendar = PracticeCalendar(LocalDate(1970, 1, 10), TimeZone.UTC)
    private fun trial(id: Long, correct: Boolean = true) = TrialRecordEntity(
        id, "i_vs_I", "sheep", if (correct) "sheep" else "ship", correct, 200, "FORCED_CHOICE", id * 86_400_000)

    @Test
    fun incrementalChangesMatchReferenceIncludingEditsDeletesFiltersAndReset() {
        val aggregator = ProgressAggregator()
        var records = listOf(trial(1), trial(2, false), trial(3))
        var snapshot = ProgressSnapshot(records, emptyList(), emptyList())
        fun verify(filters: ProgressFilters = ProgressFilters(), day: PracticeCalendar = calendar) {
            assertEquals(summarizeFilteredProgress(snapshot, day, filters, 10_000),
                aggregator.summarize(snapshot, day, filters, 10_000))
        }
        verify()
        val old = aggregator.summarize(snapshot, calendar, ProgressFilters(), 10_000)
        records = records + trial(4, false)
        snapshot = snapshot.copy(trials = records)
        verify()
        assertEquals(3, old.listeningHistory.size)
        assertEquals(2, old.contrasts.single().correct)
        snapshot = snapshot.copy(trials = listOf(trial(2), trial(4, false).copy(timestampEpochMs = 100)))
        verify()
        verify(ProgressFilters(ProgressDateRange.LAST_7_DAYS))
        verify(ProgressFilters(contrastId = "p_vs_b"))
        verify(day = calendar.copy(zone = TimeZone.of("America/Toronto")))
        snapshot = snapshot.copy(cards = listOf(Sm2CardEntity("i_vs_I", 1, 2, 2.5f, .5f, 100)))
        verify()
        snapshot = ProgressSnapshot(emptyList(), emptyList(), emptyList())
        verify()
        snapshot = snapshot.copy(trials = listOf(trial(6)))
        verify()
    }

    @Test
    fun unrelatedSpeakingUpdatesDoNotScanListeningHistoryAgain() {
        var reads = 0
        val trials = object : AbstractList<TrialRecordEntity>() {
            override val size = 10_000
            override fun get(index: Int): TrialRecordEntity { reads++; return trial(index.toLong()) }
        }
        val aggregator = ProgressAggregator()
        val snapshot = ProgressSnapshot(trials, emptyList(), emptyList())
        val first = aggregator.summarize(snapshot, calendar, ProgressFilters(), 10_000)
        reads = 0
        val speaking = snapshot.copy(speaking = listOf(SpeakingRecordEntity(1, "i_vs_I", "sheep", "iː", 80f, 200, 100)))
        val second = aggregator.summarize(speaking, calendar, ProgressFilters(), 10_000)
        assertEquals(0, reads)
        assertSame(first.listeningHistory, second.listeningHistory)
        assertEquals(80, second.speakingSummaries.single().recentAverage)
    }

    @Test
    fun moduleFiltersMatchReferenceAndDoNotAffectListening() {
        val modules = listOf(
            ModulePracticeRecordEntity(1, "STORIES", "story", "Story", "i_vs_I", 90f, true, 200, 1),
            ModulePracticeRecordEntity(2, "RHYTHM", "rhythm", "Rhythm", null, 70f, true, 200, 1))
        val snapshot = ProgressSnapshot(listOf(trial(1)), emptyList(), emptyList(), modules)
        val filters = ProgressFilters(contrastId = "p_vs_b")
        val result = ProgressAggregator().summarize(snapshot, calendar, filters, 10_000)
        assertEquals(summarizeFilteredProgress(snapshot, calendar, filters, 10_000), result)
        assertEquals(listOf("RHYTHM"), result.modules.map { it.module })
        assertEquals(0, result.attempts)
    }
}
