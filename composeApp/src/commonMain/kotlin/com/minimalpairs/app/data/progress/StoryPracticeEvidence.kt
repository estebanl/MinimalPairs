package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.ProgressSnapshot
import com.minimalpairs.app.domain.StoryContrastEvidence

/** Examples, pending writes, speaking scores, and Rhythm have no confusion observations here. */
internal fun storyPracticeEvidence(snapshot: ProgressSnapshot): Map<String, StoryContrastEvidence> {
    val evidence = mutableMapOf<String, StoryContrastEvidence>()
    fun add(contrastId: String, confused: Boolean) {
        val previous = evidence[contrastId] ?: StoryContrastEvidence()
        evidence[contrastId] = StoryContrastEvidence(previous.attempts + 1, previous.confusions + if (confused) 1 else 0)
    }
    snapshot.trials.forEach { add(it.contrastId, !it.isCorrect) }
    snapshot.modules.filter { it.module == "STORIES" && it.contrastId != null }.forEach {
        add(requireNotNull(it.contrastId), !it.succeeded)
    }
    return evidence
}
