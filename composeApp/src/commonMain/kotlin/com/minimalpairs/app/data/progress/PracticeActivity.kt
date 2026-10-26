package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.TrialRecordEntity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

data class PracticeDay(val date: LocalDate, val answers: Int)

data class PracticeActivity(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val days: List<PracticeDay> = emptyList()
) {
    val practicedToday: Boolean get() = days.lastOrNull()?.answers?.let { it > 0 } ?: false
    val activeDays: Int get() = days.count { it.answers > 0 }
}

/** Dates are interpreted in the device's current time zone, including after travel. */
data class PracticeCalendar(val today: LocalDate, val zone: TimeZone) {
    companion object {
        fun current(): PracticeCalendar {
            val zone = TimeZone.currentSystemDefault()
            return PracticeCalendar(Clock.System.now().toLocalDateTime(zone).date, zone)
        }
    }
}

fun summarizeActivity(trials: List<TrialRecordEntity>, calendar: PracticeCalendar): PracticeActivity {
    val today = calendar.today.toEpochDays()
    val counts = trials.groupingBy {
        Instant.fromEpochMilliseconds(it.timestampEpochMs).toLocalDateTime(calendar.zone).date.toEpochDays()
    }.eachCount().filterKeys { it <= today }
    val active = counts.keys.sorted()
    var longest = 0
    var run = 0
    var previous: Long? = null
    active.forEach { day ->
        run = if (previous == day - 1) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }
    var cursor = if (today in counts) today else today - 1
    var current = 0
    while (cursor in counts) {
        current++
        cursor--
    }
    return PracticeActivity(
        currentStreak = current,
        longestStreak = longest,
        days = (today - 6..today).map { PracticeDay(LocalDate.fromEpochDays(it), counts[it] ?: 0) }
    )
}
