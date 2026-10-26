package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.TrialRecordEntity
import kotlin.test.*

class ContrastConfusionTest {
    @Test
    fun sharedSoundsStaySeparateAndUntestedDirectionRemainsAbsent() {
        fun trial(id: String, target: String, chosen: String) = TrialRecordEntity(
            contrastId = id, targetWord = target, selectedWord = chosen, isCorrect = target == chosen,
            latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = 1000
        )
        val state = summarizeProgress(listOf(
            trial("i_vs_I", "ship", "sheep"),
            trial("i_vs_I", "ship", "ship"),
            trial("e_vs_I", "pin", "pen")
        ), emptyList())
        val first = state.contrasts.single { it.contrastId == "i_vs_I" }
        assertEquals(mapOf("iː" to 1, "ɪ" to 1), first.confusion["ɪ"])
        assertNull(first.confusion["iː"])
        val second = state.contrasts.single { it.contrastId == "e_vs_I" }
        assertEquals(1, second.confusion["ɪ"]?.values?.sum())
        assertEquals(3, state.confusion["ɪ"]?.values?.sum())
    }
}
