package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.TrialRecordEntity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.test.*

class PracticeActivityTest {
    private fun calendar(date: String, zone: String = "UTC") = PracticeCalendar(LocalDate.parse(date), TimeZone.of(zone))
    private fun trial(at: String) = TrialRecordEntity(
        contrastId = "i_vs_I", targetWord = "sheep", selectedWord = "ship", isCorrect = false,
        latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = Instant.parse(at).toEpochMilliseconds()
    )

    @Test
    fun duplicatesCountAsOneDayAndIncorrectAnswersCountAsPractice() {
        val activity = summarizeActivity(listOf(
            trial("2026-10-24T12:00:00Z"), trial("2026-10-25T12:00:00Z"),
            trial("2026-10-26T12:00:00Z"), trial("2026-10-26T13:00:00Z")
        ), calendar("2026-10-26"))
        assertEquals(3, activity.currentStreak)
        assertEquals(3, activity.longestStreak)
        assertEquals(3, activity.activeDays)
        assertEquals(2, activity.days.last().answers)
        assertTrue(activity.practicedToday)
        assertEquals(7, activity.days.size)
        assertEquals(LocalDate.parse("2026-10-20"), activity.days.first().date)
    }

    @Test
    fun yesterdayStreakSurvivesUntilTheNextDayThenResets() {
        val trials = listOf(trial("2026-10-24T12:00:00Z"), trial("2026-10-25T12:00:00Z"))
        val today = summarizeActivity(trials, calendar("2026-10-26"))
        assertEquals(2, today.currentStreak)
        assertFalse(today.practicedToday)
        val tomorrow = summarizeActivity(trials, calendar("2026-10-27"))
        assertEquals(0, tomorrow.currentStreak)
        assertEquals(2, tomorrow.longestStreak)
    }

    @Test
    fun gapsResetCurrentButPreserveHistoricalBestAndIgnoreFutureDates() {
        val trials = listOf("20", "21", "22", "25", "26", "27").map { trial("2026-10-${it}T12:00:00Z") }
        val activity = summarizeActivity(trials, calendar("2026-10-26"))
        assertEquals(2, activity.currentStreak)
        assertEquals(3, activity.longestStreak)
        assertEquals(5, activity.activeDays)
    }

    @Test
    fun usesLocalMidnightInsteadOfUtcMidnight() {
        val trials = listOf(trial("2026-10-26T03:59:00Z"), trial("2026-10-26T04:01:00Z"))
        val toronto = summarizeActivity(trials, calendar("2026-10-26", "America/Toronto"))
        assertEquals(2, toronto.currentStreak)
        assertEquals(1, toronto.days.last().answers)
        val utc = summarizeActivity(trials, calendar("2026-10-26"))
        assertEquals(1, utc.currentStreak)
        assertEquals(2, utc.days.last().answers)
    }

    @Test
    fun daylightSavingAndLeapDayStillUseConsecutiveCalendarDates() {
        val spring = summarizeActivity(listOf(
            trial("2026-03-07T17:00:00Z"), trial("2026-03-08T16:00:00Z"), trial("2026-03-09T16:00:00Z")
        ), calendar("2026-03-09", "America/Toronto"))
        assertEquals(3, spring.currentStreak)
        val fall = summarizeActivity(listOf(
            trial("2026-11-01T05:30:00Z"), trial("2026-11-01T06:30:00Z"), trial("2026-11-02T17:00:00Z")
        ), calendar("2026-11-02", "America/Toronto"))
        assertEquals(2, fall.currentStreak)
        assertEquals(2, fall.days[5].answers)
        val leap = summarizeActivity(listOf(
            trial("2024-02-28T12:00:00Z"), trial("2024-02-29T12:00:00Z"), trial("2024-03-01T12:00:00Z")
        ), calendar("2024-03-01"))
        assertEquals(3, leap.currentStreak)
    }

    @Test
    fun emptyHistoryHasSevenInactiveDays() {
        val activity = summarizeActivity(emptyList(), calendar("2026-01-01"))
        assertEquals(0, activity.currentStreak)
        assertEquals(0, activity.longestStreak)
        assertEquals(7, activity.days.size)
        assertTrue(activity.days.all { it.answers == 0 })
    }
}
