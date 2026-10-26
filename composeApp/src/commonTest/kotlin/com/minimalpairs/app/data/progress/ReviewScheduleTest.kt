package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.Sm2CardEntity
import kotlin.test.*

class ReviewScheduleTest {
    private fun card(id: String, reviewed: Long, interval: Int = 1) = Sm2CardEntity(id, 1, interval, 2.5f, .5f, reviewed)

    @Test
    fun dueBoundaryIsInclusiveAndOldestReviewsComeFirst() {
        val now = 3 * 86_400_000L
        val schedule = reviewSchedule(listOf(
            card("p_vs_b", 2 * 86_400_000L),
            card("i_vs_I", 0),
            card("e_vs_I", now),
            card("unknown", 0)
        ), now)
        assertEquals(listOf("i_vs_I", "p_vs_b"), schedule.due.map { it.contrastId })
        assertEquals("e_vs_I", schedule.next?.contrastId)
    }

    @Test
    fun clockTickMovesUpcomingReviewIntoDueList() {
        val cards = listOf(card("i_vs_I", 0))
        assertTrue(reviewSchedule(cards, 86_399_999).due.isEmpty())
        assertEquals(1, reviewSchedule(cards, 86_400_000).due.size)
        assertNull(reviewSchedule(cards, 86_400_000).next)
    }

    @Test
    fun timingRoundsUpAndUsesTheSameDueBoundary() {
        assertEquals("Review in 1 day", reviewTiming(1, 0))
        assertEquals("Review in 1 day", reviewTiming(86_400_000, 0))
        assertEquals("Review in 2 days", reviewTiming(86_400_001, 0))
        assertEquals("Review due", reviewTiming(0, 0))
        assertEquals("Review due", reviewTiming(0, 1))
        assertEquals(ReviewSchedule(), reviewSchedule(emptyList(), 0))
    }
}
