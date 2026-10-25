package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MeaningTrapStoriesSpecTest {

    @Test
    fun testStoriesCatalogCompleteness() {
        val stories = MeaningTrapCatalog.stories
        assertEquals(4, stories.size, "Should contain 4 core minimal pair narrative scenarios")

        val ids = stories.map { it.id }.toSet()
        assertTrue(ids.contains("sheep_vs_ship"))
        assertTrue(ids.contains("pen_vs_pan"))
        assertTrue(ids.contains("heart_vs_hut"))
        assertTrue(ids.contains("fool_vs_full"))
    }

    @Test
    fun testStoryContrastLinkage() {
        for (story in MeaningTrapCatalog.stories) {
            val contrast = VowelContrastsCatalog.getContrastById(story.contrastId)
            assertNotNull(contrast, "Story ${story.id} must link to a valid vowel contrast ${story.contrastId}")

            assertTrue(story.intendedWord.isNotBlank())
            assertTrue(story.trapWord.isNotBlank())
            assertTrue(story.intendedOutcome.illustrationEmoji.isNotBlank())
            assertTrue(story.trapOutcome.illustrationEmoji.isNotBlank())
            assertTrue(story.intendedOutcome.soundEffect.isNotBlank())
            assertTrue(story.trapOutcome.soundEffect.isNotBlank())
            assertTrue(story.semanticTakeaway.isNotBlank())
        }
    }

    @Test
    fun testSheepVsShipComicNarrativeBranching() {
        val story = MeaningTrapCatalog.getStoryById("sheep_vs_ship")
        assertNotNull(story)
        assertEquals("sheep", story.intendedWord)
        assertEquals("ship", story.trapWord)

        // Intended outcome contains pasture/meadow context
        assertTrue(story.intendedOutcome.dialogue.contains("Barnaby") || story.intendedOutcome.dialogue.contains("meadow"))

        // Trap outcome contains cargo freighter collision context
        assertTrue(story.trapOutcome.dialogue.contains("ocean freighter") || story.trapOutcome.dialogue.contains("freighter"))
    }

    @Test
    fun testMeaningTrapOutcomeFeedsSrsConfusionTracker() {
        val tracker = PhonemeConfusionTracker()
        val initialShipWeight = tracker.getConfidenceWeight("ɪ")

        // 1. User mispronounces "sheep" as "ship" -> trap triggered
        tracker.recordTrial("iː", "ɪ", isCorrect = false)

        assertEquals(1, tracker.getConfusionCount("iː", "ɪ"))
        val updatedIWeight = tracker.getConfidenceWeight("iː")
        assertTrue(updatedIWeight < 0.5f, "Target phoneme confidence weight must decrease when trap triggered")

        // 2. User successfully corrects pronunciation
        tracker.recordTrial("iː", "iː", isCorrect = true)
        assertTrue(tracker.getConfidenceWeight("iː") > updatedIWeight)
    }
}
