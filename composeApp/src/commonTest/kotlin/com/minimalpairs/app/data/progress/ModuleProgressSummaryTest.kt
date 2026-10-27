package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import kotlin.test.*

class ModuleProgressSummaryTest {
    @Test
    fun summariesKeepModulesAndTargetsSeparateAndUseLatestFive() {
        val records = (1..6).map { index ->
            ModulePracticeRecordEntity(index.toLong(), "STORIES", "a", "Story A", "i_vs_I", index * 10f,
                false, 200, index * 1000L)
        } + listOf(
            ModulePracticeRecordEntity(7, "RHYTHM", "a", "Rhythm A", null, 90f, true, 300, 7000),
            ModulePracticeRecordEntity(8, "STORIES", "b", "Story B", "ae_vs_e", 20f, false, 300, 8000),
            ModulePracticeRecordEntity(9, "STORIES", "a", "Invalid", "i_vs_I", Float.NaN, false, 200, 9000))
        val summaries = summarizeModules(records)
        assertEquals(3, summaries.size)
        val story = summaries.single { it.latest.module == "STORIES" && it.latest.targetId == "a" }
        assertEquals(6, story.attempts)
        assertEquals(60, story.bestScore)
        assertEquals(40, story.recentAverage)
        assertEquals(5, story.recentCount)
        assertEquals(6L, story.latest.id)
        assertEquals(90, summaries.single { it.latest.module == "RHYTHM" }.recentAverage)
    }
}
