package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.local.*
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Worker-confined cache. Emitted collections are immutable snapshots, never mutated in place. */
internal class ProgressAggregator {
    private val order = compareByDescending<TrialRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id }
    private var sourceTrials: List<TrialRecordEntity>? = null
    private var sourceSpeaking: List<SpeakingRecordEntity>? = null
    private var sourceModules: List<ModulePracticeRecordEntity>? = null
    private var selected: ProgressFilters? = null
    private var day: PracticeCalendar? = null
    private var indexed = emptyMap<Long, TrialRecordEntity>()
    private val history = mutableListOf<TrialRecordEntity>()
    private val dates = mutableMapOf<Long, Int>()
    private val totals = mutableMapOf<String, Counts>()
    private var publishedHistory: List<TrialRecordEntity> = emptyList()
    private var speaking: List<SpeakingRecordEntity> = emptyList()
    private var speakingSummaries: List<SpeakingSoundSummary> = emptyList()
    private var activity = PracticeActivity()
    private var modules: List<ModulePracticeRecordEntity> = emptyList()
    private var moduleSummaries: List<ModuleTargetSummary> = emptyList()

    private class Counts {
        var attempts = 0
        var correct = 0
        val cells = mutableMapOf<String, MutableMap<String, Int>>()
    }

    fun summarize(snapshot: ProgressSnapshot, calendar: PracticeCalendar, filters: ProgressFilters, nowEpochMs: Long): ProgressState {
        try {
            return calculate(snapshot, calendar, filters, nowEpochMs)
        } catch (error: Exception) {
            // A failed calculation must not leave half-applied deltas for the next retry.
            sourceTrials = null
            sourceSpeaking = null
            sourceModules = null
            selected = null
            day = null
            throw error
        }
    }

    private fun calculate(snapshot: ProgressSnapshot, calendar: PracticeCalendar, filters: ProgressFilters, now: Long): ProgressState {
        val selectionChanged = filters != selected || calendar != day
        val trialsChanged = sourceTrials !== snapshot.trials
        if (selectionChanged || trialsChanged) {
            val next = snapshot.trials.associateBy { it.id }
            // Custom stores may supply duplicate legacy IDs. Keep the reference calculation correct.
            if (next.size != snapshot.trials.size) {
                sourceTrials = null
                selected = null
                return summarizeFilteredProgress(snapshot, calendar, filters, now)
            }
            if (selectionChanged || sourceTrials == null) {
                history.clear()
                totals.clear()
                dates.clear()
                next.values.forEach { change(it, 1, calendar, filters, updateHistory = false) }
                history.addAll(next.values.filter { filters.includes(it.contrastId, it.timestampEpochMs, calendar) }.sortedWith(order))
            } else {
                indexed.forEach { (id, old) -> if (next[id] != old) change(old, -1, calendar, filters) }
                next.forEach { (id, record) -> if (indexed[id] != record) change(record, 1, calendar, filters) }
            }
            indexed = next
            publishedHistory = history.toList()
            activity = summarizeActivityCounts(dates, calendar)
        }
        if (selectionChanged || sourceSpeaking !== snapshot.speaking) {
            speaking = snapshot.speaking.filter { filters.includes(it.contrastId, it.timestampEpochMs, calendar) }
                .sortedWith(compareByDescending<SpeakingRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id })
            speakingSummaries = summarizeSpeaking(speaking)
        }
        if (selectionChanged || sourceModules !== snapshot.modules) {
            modules = filterModuleRecords(snapshot.modules, calendar, filters)
            moduleSummaries = summarizeModules(modules)
        }
        val cards = snapshot.cards.associateBy { it.contrastId }
        val contrasts = totals.filterValues { it.attempts > 0 }.map { (id, counts) ->
            ContrastProgress(id, counts.attempts, counts.correct, cards[id], counts.cells.mapValues { it.value.toMap() })
        }.sortedWith(compareBy<ContrastProgress> { it.correct.toDouble() / it.attempts }.thenBy { it.contrastId })
        val confusion = mutableMapOf<String, MutableMap<String, Int>>()
        contrasts.forEach { contrast -> contrast.confusion.forEach { (target, row) ->
            val combined = confusion.getOrPut(target) { mutableMapOf() }
            row.forEach { (chosen, count) -> combined[chosen] = (combined[chosen] ?: 0) + count }
        } }
        fun sample(records: List<TrialRecordEntity>) = AccuracySample(records.size, records.count { it.isCorrect })
        sourceTrials = snapshot.trials
        sourceSpeaking = snapshot.speaking
        sourceModules = snapshot.modules
        selected = filters
        day = calendar
        return ProgressState(
            loading = false, hasLoadedProgress = true, filters = filters, contrasts = contrasts,
            confusion = confusion.mapValues { it.value.toMap() },
            trend = AccuracyTrend(sample(publishedHistory.take(20)), sample(publishedHistory.drop(20).take(20))),
            recentTrials = publishedHistory.take(20), listeningHistory = publishedHistory,
            activity = activity, reviews = reviewSchedule(snapshot.cards, now), speaking = speaking,
            speakingSummaries = speakingSummaries, modules = modules, moduleSummaries = moduleSummaries, nowEpochMs = now
        )
    }

    private fun change(record: TrialRecordEntity, direction: Int, calendar: PracticeCalendar, filters: ProgressFilters, updateHistory: Boolean = true) {
        val date = Instant.fromEpochMilliseconds(record.timestampEpochMs).toLocalDateTime(calendar.zone).date.toEpochDays()
        dates[date] = (dates[date] ?: 0) + direction
        if (!filters.includes(record.contrastId, record.timestampEpochMs, calendar)) return
        if (updateHistory) {
            val position = history.binarySearch(record, order)
            if (direction > 0) history.add(if (position < 0) -position - 1 else position, record)
            else if (position >= 0) history.removeAt(position)
        }
        val counts = totals.getOrPut(record.contrastId) { Counts() }
        counts.attempts += direction
        if (record.isCorrect) counts.correct += direction
        val contrast = ListeningContrastsCatalog.getContrastById(record.contrastId) ?: return
        val pair = contrast.pairs.firstOrNull { it.word1 == record.targetWord || it.word2 == record.targetWord } ?: return
        val target = if (record.targetWord == pair.word1) contrast.phoneme1 else contrast.phoneme2
        val chosen = if (record.isCorrect) target else if (target == contrast.phoneme1) contrast.phoneme2 else contrast.phoneme1
        val row = counts.cells.getOrPut(target) { mutableMapOf() }
        val count = (row[chosen] ?: 0) + direction
        if (count == 0) row.remove(chosen) else row[chosen] = count
        if (row.isEmpty()) counts.cells.remove(target)
    }
}
