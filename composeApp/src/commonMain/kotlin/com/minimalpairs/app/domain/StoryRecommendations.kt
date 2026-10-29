package com.minimalpairs.app.domain

import com.minimalpairs.app.core.model.ComicStory
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog

/** Counts from saved listening answers and evaluated Stories replies, before Progress filters. */
data class StoryContrastEvidence(val attempts: Int = 0, val confusions: Int = 0)

data class StoryRecommendation(
    val storyId: String,
    val contrastLabel: String,
    val explanation: String,
    val basedOnConfusions: Boolean
)

/** Rank only available stories with observed confusions; ties retain catalog order. */
fun recommendStory(
    stories: List<ComicStory>,
    evidence: Map<String, StoryContrastEvidence>
): StoryRecommendation? {
    val ranked = stories.withIndex().filter { (_, story) ->
        val observed = evidence[story.contrastId]
        observed != null && observed.attempts > 0 && observed.confusions > 0
    }.sortedWith(compareByDescending<IndexedValue<ComicStory>> {
        val observed = evidence.getValue(it.value.contrastId)
        observed.confusions.toDouble() / observed.attempts
    }.thenByDescending { evidence.getValue(it.value.contrastId).confusions }.thenBy { it.index })
    val story = ranked.firstOrNull()?.value ?: stories.firstOrNull() ?: return null
    val contrast = VowelContrastsCatalog.getContrastById(story.contrastId)
    val label = if (contrast != null) "/${contrast.phoneme1}/ and /${contrast.phoneme2}/"
        else "${story.intendedWord} and ${story.trapWord}"
    val observed = evidence[story.contrastId]
    val confused = ranked.isNotEmpty()
    return StoryRecommendation(
        storyId = story.id,
        contrastLabel = label,
        explanation = if (confused && observed != null) {
            "You mixed $label in ${observed.confusions} of ${observed.attempts} saved listening and Stories attempts."
        } else {
            "Try $label in “${story.title}”."
        },
        basedOnConfusions = confused
    )
}
