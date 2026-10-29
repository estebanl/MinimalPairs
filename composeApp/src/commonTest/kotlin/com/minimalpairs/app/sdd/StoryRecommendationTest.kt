package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import com.minimalpairs.app.data.local.ProgressSnapshot
import com.minimalpairs.app.data.local.TrialRecordEntity
import com.minimalpairs.app.data.progress.storyPracticeEvidence
import com.minimalpairs.app.domain.StoryContrastEvidence
import com.minimalpairs.app.domain.recommendStory
import kotlin.test.*

class StoryRecommendationTest {
    private val stories = MeaningTrapCatalog.stories

    @Test
    fun noHistoryAndCorrectOnlyPracticeOfferANeutralStarter() {
        for (evidence in listOf(emptyMap(), mapOf("ae_vs_e" to StoryContrastEvidence(10, 0)))) {
            val recommendation = assertNotNull(recommendStory(stories, evidence))
            assertEquals(stories.first().id, recommendation.storyId)
            assertFalse(recommendation.basedOnConfusions)
            assertFalse(recommendation.explanation.contains("mixed"))
            assertTrue(recommendation.explanation.contains("/iː/"))
            assertTrue(recommendation.explanation.contains("/ɪ/"))
        }
        assertNull(recommendStory(emptyList(), emptyMap()))
    }

    @Test
    fun availableStoryWithHighestObservedErrorRateIsRecommended() {
        val recommendation = assertNotNull(recommendStory(stories, mapOf(
            "i_vs_I" to StoryContrastEvidence(10, 2),
            "ae_vs_e" to StoryContrastEvidence(4, 3),
            "missing_contrast" to StoryContrastEvidence(20, 20)
        )))
        assertEquals("pen_vs_pan", recommendation.storyId)
        assertTrue(recommendation.basedOnConfusions)
        assertTrue(recommendation.explanation.contains("3 of 4"))
        assertTrue(recommendation.contrastLabel.contains("/æ/"))
        assertTrue(recommendation.contrastLabel.contains("/e/"))
    }

    @Test
    fun tiesUseErrorCountThenStableCatalogOrderAndOnlyAvailableContrasts() {
        val tiedRates = mapOf("i_vs_I" to StoryContrastEvidence(2, 1), "ae_vs_e" to StoryContrastEvidence(4, 2))
        assertEquals("pen_vs_pan", recommendStory(stories, tiedRates)?.storyId)
        val tiedCounts = tiedRates + ("i_vs_I" to StoryContrastEvidence(4, 2))
        assertEquals("sheep_vs_ship", recommendStory(stories, tiedCounts)?.storyId)
        assertEquals("sheep_vs_ship", recommendStory(stories.take(1), tiedRates)?.storyId)
    }

    @Test
    fun savedEvidenceCombinesListeningAndStoriesButExcludesRhythmAndSpeaking() {
        val snapshot = ProgressSnapshot(
            trials = listOf(
                TrialRecordEntity(1, "ae_vs_e", "pen", "pan", false, 500, "FORCED_CHOICE", 1000),
                TrialRecordEntity(2, "ae_vs_e", "pen", "pen", true, 500, "FORCED_CHOICE", 2000)
            ),
            cards = emptyList(), speaking = emptyList(),
            modules = listOf(
                ModulePracticeRecordEntity(3, "STORIES", "pen_vs_pan", "Exam", "ae_vs_e", 10f, false, 1000, 3000),
                ModulePracticeRecordEntity(4, "RHYTHM", "stress", "Rhythm", "i_vs_I", 10f, false, 1000, 4000)
            )
        )
        assertEquals(mapOf("ae_vs_e" to StoryContrastEvidence(3, 2)), storyPracticeEvidence(snapshot))
    }
}
