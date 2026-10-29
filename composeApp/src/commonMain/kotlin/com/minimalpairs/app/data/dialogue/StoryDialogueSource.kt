package com.minimalpairs.app.data.dialogue

import com.minimalpairs.app.core.model.ComicStory
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import minimalpairs.composeapp.generated.resources.Res
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val MAX_STORY_TURNS = 3

class StoryDialogueException(message: String) : Exception(message)

@Serializable
data class StoryDialogueHistoryTurn(
    val prompt: String,
    val branch: String,
    val consequence: String,
    val example: Boolean
)

/** An immutable catalog target accompanies every generated narrative. No model-authored IPA is accepted. */
@Serializable
data class StoryDialogueTarget(
    @SerialName("story_id") val storyId: String,
    @SerialName("contrast_id") val contrastId: String,
    val title: String,
    val premise: String,
    val character: String,
    @SerialName("intended_word") val intendedWord: String,
    @SerialName("intended_ipa") val intendedIpa: String,
    @SerialName("trap_word") val trapWord: String,
    @SerialName("trap_ipa") val trapIpa: String,
    val phrase: String,
    @SerialName("phrase_ipa") val phraseIpa: String,
    @SerialName("target_phoneme_index") val targetPhonemeIndex: Int,
    @SerialName("alternative_phoneme") val alternativePhoneme: String
) {
    companion object {
        fun from(story: ComicStory): StoryDialogueTarget {
            val intended = story.intendedIpa.split(" ")
            val alternate = story.trapIpa.split(" ")
            val difference = intended.indices.single { intended[it] != alternate[it] }
            return StoryDialogueTarget(story.id, story.contrastId, story.title, story.premise,
                story.promptCharacter, story.intendedWord, story.intendedIpa, story.trapWord,
                story.trapIpa, story.targetPhrase, story.targetPhraseIpa, story.targetPhonemeIndex,
                alternate[difference])
        }
    }
}

@Serializable
data class StoryNarrative(
    val scene: String,
    val prompt: String,
    @SerialName("intended_dialogue") val intendedDialogue: String,
    @SerialName("alternate_dialogue") val alternateDialogue: String
)

@Serializable
data class StoryDialogueResponse(
    val target: StoryDialogueTarget,
    @SerialName("turn_number") val turnNumber: Int,
    val narrative: StoryNarrative,
    @SerialName("content_version") val contentVersion: String
) {
    fun validatedStory(storyId: String, expectedTurn: Int): ComicStory {
        val base = MeaningTrapCatalog.stories.singleOrNull { it.id == storyId }
            ?: throw StoryDialogueException("Choose an available story.")
        fun invalid(): Nothing = throw StoryDialogueException(
            "The next turn didn't preserve this story's learning target. Retry or restart the story.")
        if (target != StoryDialogueTarget.from(base) || turnNumber != expectedTurn ||
            turnNumber !in 2..MAX_STORY_TURNS || contentVersion.isBlank() || contentVersion.length > 200) invalid()
        for ((text, limit) in listOf(narrative.scene to 400, narrative.prompt to 600,
            narrative.intendedDialogue to 600, narrative.alternateDialogue to 600)) {
            if (text.isBlank() || text.length > limit || text.any { it.code < 32 && it !in "\n\t" }) invalid()
        }
        for ((text, words) in listOf(narrative.intendedDialogue to (base.intendedWord to base.trapWord),
            narrative.alternateDialogue to (base.trapWord to base.intendedWord))) {
            fun contains(word: String) = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
            if (!contains(words.first) || contains(words.second)) invalid()
        }
        return base.copy(
            premise = narrative.scene,
            promptDialogue = narrative.prompt,
            promptInstruction = "Say: ‘${base.targetPhrase}’",
            intendedOutcome = base.intendedOutcome.copy(dialogue = narrative.intendedDialogue,
                outcomeDescription = "Example consequence for “${base.intendedWord}”."),
            trapOutcome = base.trapOutcome.copy(dialogue = narrative.alternateDialogue,
                outcomeDescription = "Example consequence for “${base.trapWord}”.")
        )
    }
}

fun interface StoryDialogueSource {
    suspend fun continueStory(storyId: String, history: List<StoryDialogueHistoryTurn>): StoryDialogueResponse
}

@Serializable
private data class StoryDialogueDocument(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("content_version") val contentVersion: String,
    val provenance: String,
    @SerialName("max_turns") val maxTurns: Int,
    val stories: List<StoryDialogueEntry>
) {
    fun validate() {
        require(schemaVersion == 1 && maxTurns == MAX_STORY_TURNS && provenance.isNotBlank())
        val catalog = MeaningTrapCatalog.stories
        require(stories.size == catalog.size && stories.map { it.target.storyId }.toSet() == catalog.map { it.id }.toSet())
        val paths = setOf("intended", "alternate", "intended/intended", "intended/alternate",
            "alternate/intended", "alternate/alternate")
        for (entry in stories) {
            require(entry.turns.size == paths.size && entry.turns.map { it.path }.toSet() == paths)
            for (turn in entry.turns) {
                StoryDialogueResponse(entry.target, turn.path.split("/").size + 1, turn.narrative, contentVersion)
                    .validatedStory(entry.target.storyId, turn.path.split("/").size + 1)
            }
        }
    }
}

@Serializable
private data class StoryDialogueEntry(val target: StoryDialogueTarget, val turns: List<StoryDialogueNode>)

@Serializable
private data class StoryDialogueNode(val path: String, val narrative: StoryNarrative)

/** AI-authored, bundled conversation branches. Loading may fail; retry keeps the current reply intact. */
class BundledStoryDialogueSource(
    private val readDocument: suspend () -> String = {
        Res.readBytes("files/stories/dialogue.json").decodeToString()
    }
) : StoryDialogueSource {
    override suspend fun continueStory(storyId: String, history: List<StoryDialogueHistoryTurn>): StoryDialogueResponse {
        require(history.size in 1 until MAX_STORY_TURNS)
        val document = Json.decodeFromString<StoryDialogueDocument>(readDocument()).also { it.validate() }
        val entry = document.stories.single { it.target.storyId == storyId }
        var previous = MeaningTrapCatalog.stories.single { it.id == storyId }
        val path = mutableListOf<String>()
        for ((index, completed) in history.withIndex()) {
            require(completed.branch in setOf("intended", "alternate"))
            val outcome = if (completed.branch == "intended") previous.intendedOutcome else previous.trapOutcome
            require(completed.prompt == previous.promptDialogue && completed.consequence == outcome.dialogue)
            path += completed.branch
            val node = entry.turns.single { it.path == path.joinToString("/") }
            val response = StoryDialogueResponse(entry.target, index + 2, node.narrative, document.contentVersion)
            previous = response.validatedStory(storyId, index + 2)
            if (index == history.lastIndex) return response
        }
        error("A completed turn is required")
    }
}
