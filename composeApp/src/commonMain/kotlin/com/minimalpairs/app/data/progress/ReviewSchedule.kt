package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.local.Sm2CardEntity

private const val DAY_MS = 86_400_000L

data class ScheduledReview(val contrastId: String, val dueEpochMs: Long)
data class ReviewSchedule(val due: List<ScheduledReview> = emptyList(), val next: ScheduledReview? = null)

fun reviewDueAt(card: Sm2CardEntity): Long = card.lastReviewedEpochMs + card.intervalDays.coerceAtLeast(1).toLong() * DAY_MS

fun reviewSchedule(cards: List<Sm2CardEntity>, nowEpochMs: Long): ReviewSchedule {
    val scheduled = cards.filter { ListeningContrastsCatalog.getContrastById(it.contrastId) != null }
        .map { ScheduledReview(it.contrastId, reviewDueAt(it)) }
        .sortedWith(compareBy<ScheduledReview> { it.dueEpochMs }.thenBy { it.contrastId })
    return ReviewSchedule(scheduled.filter { it.dueEpochMs <= nowEpochMs }, scheduled.firstOrNull { it.dueEpochMs > nowEpochMs })
}

fun reviewTiming(dueEpochMs: Long, nowEpochMs: Long): String {
    val remaining = dueEpochMs - nowEpochMs
    if (remaining <= 0) return "Review due"
    val days = (remaining + DAY_MS - 1) / DAY_MS
    return "Review in $days ${if (days == 1L) "day" else "days"}"
}
