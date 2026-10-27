package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.ProgressSnapshot
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class ProgressDateRange(val label: String, val days: Int?) {
    ALL_TIME("All time", null),
    LAST_7_DAYS("Last 7 days", 7),
    LAST_30_DAYS("Last 30 days", 30)
}

data class ProgressFilters(
    val dateRange: ProgressDateRange = ProgressDateRange.ALL_TIME,
    val contrastId: String? = null
) {
    val isActive: Boolean get() = dateRange != ProgressDateRange.ALL_TIME || contrastId != null
    val historyKey: String get() = "${dateRange.name}:${contrastId.orEmpty()}"

    fun includes(id: String, timestampEpochMs: Long, calendar: PracticeCalendar): Boolean {
        if (contrastId != null && contrastId != id) return false
        val days = dateRange.days ?: return true
        val date = Instant.fromEpochMilliseconds(timestampEpochMs).toLocalDateTime(calendar.zone).date.toEpochDays()
        val today = calendar.today.toEpochDays()
        return date in (today - days + 1)..today
    }
}

/** Filters affect learning results; activity and review scheduling retain all practice. */
fun summarizeFilteredProgress(
    snapshot: ProgressSnapshot,
    calendar: PracticeCalendar,
    filters: ProgressFilters,
    nowEpochMs: Long
): ProgressState {
    val trials = snapshot.trials.filter { filters.includes(it.contrastId, it.timestampEpochMs, calendar) }
    val speaking = snapshot.speaking.filter { filters.includes(it.contrastId, it.timestampEpochMs, calendar) }
        .sortedWith(compareByDescending<com.minimalpairs.app.data.local.SpeakingRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id })
    return summarizeProgress(trials, snapshot.cards, calendar, nowEpochMs).copy(
        filters = filters,
        activity = summarizeActivity(snapshot.trials, calendar),
        speaking = speaking,
        speakingSummaries = summarizeSpeaking(speaking)
    )
}
