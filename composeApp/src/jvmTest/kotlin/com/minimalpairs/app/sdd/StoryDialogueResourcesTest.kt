package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.model.ComicStory
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.dialogue.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import minimalpairs.composeapp.generated.resources.Res
import kotlin.test.*

class StoryDialogueResourcesTest {
    @Test
    fun allSevenStoriesHaveCompleteDistinctThreeTurnConversationsForEveryPronunciationPath() = runBlocking {
        val source = BundledStoryDialogueSource()
        val uniquePrompts = mutableSetOf<String>()
        for (base in MeaningTrapCatalog.stories) {
            for (first in listOf("intended", "alternate")) {
                val history = listOf(completed(base, first))
                val second = source.continueStory(base.id, history)
                val secondStory = second.validatedStory(base.id, 2)
                assertEquals(StoryDialogueTarget.from(base), second.target)
                assertEquals(base.targetPhrase, secondStory.targetPhrase)
                assertEquals(base.targetPhraseIpa, secondStory.targetPhraseIpa)
                assertEquals(base.targetPhonemeIndex, secondStory.targetPhonemeIndex)
                assertEquals(base.semanticTakeaway, secondStory.semanticTakeaway)
                assertEquals(base.promptCharacter, secondStory.promptCharacter)
                assertTrue(uniquePrompts.add(secondStory.promptDialogue), "Each path needs its own prompt")
                for (next in listOf("intended", "alternate")) {
                    val third = source.continueStory(base.id, history + completed(secondStory, next))
                    val thirdStory = third.validatedStory(base.id, 3)
                    assertEquals(second.target, third.target)
                    assertEquals(second.contentVersion, third.contentVersion)
                    assertEquals(base.targetPhraseIpa, thirdStory.targetPhraseIpa)
                    assertEquals(base.targetPhonemeIndex, thirdStory.targetPhonemeIndex)
                    assertTrue(uniquePrompts.add(thirdStory.promptDialogue))
                    for (final in listOf("intended", "alternate")) {
                        assertFailsWith<IllegalArgumentException> {
                            source.continueStory(base.id, history + completed(secondStory, next) + completed(thirdStory, final))
                        }
                    }
                }
            }
        }
        assertEquals(MeaningTrapCatalog.stories.size * 6, uniquePrompts.size)
    }

    @Test
    fun corruptedTargetsMissingBranchesDuplicateStoriesAndUnexpectedFieldsAreRejected() = runBlocking {
        val original = Json.parseToJsonElement(Res.readBytes("files/stories/dialogue.json").decodeToString()).jsonObject
        val entries = original.getValue("stories").jsonArray
        val first = entries.first().jsonObject
        val target = first.getValue("target").jsonObject
        val nodes = first.getValue("turns").jsonArray
        fun replaceEntry(value: JsonObject) = JsonObject(original + ("stories" to JsonArray(listOf(value) + entries.drop(1))))
        val corruptions = listOf(
            JsonObject(original + ("schema_version" to JsonPrimitive(99))),
            JsonObject(original + ("max_turns" to JsonPrimitive(20))),
            JsonObject(original + ("stories" to JsonArray(entries.drop(1)))),
            JsonObject(original + ("stories" to JsonArray(listOf(first) + entries))),
            replaceEntry(JsonObject(first + ("target" to JsonObject(target + ("target_phoneme_index" to JsonPrimitive(0)))))),
            replaceEntry(JsonObject(first + ("turns" to JsonArray(nodes.drop(1))))),
            replaceEntry(JsonObject(first + ("turns" to JsonArray(listOf(nodes.first()) + nodes)))),
            JsonObject(original + ("score" to JsonPrimitive(100)))
        )
        for (corrupt in corruptions) {
            assertFails {
                BundledStoryDialogueSource { corrupt.toString() }.continueStory("sheep_vs_ship",
                    listOf(completed(MeaningTrapCatalog.stories.first(), "intended")))
            }
        }
    }

    @Test
    fun narrativeValidationRejectsWrongTargetsSwappedMeaningsAndInvalidText() {
        val base = MeaningTrapCatalog.stories.first()
        val valid = StoryDialogueResponse(StoryDialogueTarget.from(base), 2,
            StoryNarrative("At the farm gate.", "Which companion would you like?", "Your sheep is ready.", "Your ship is ready."),
            "authored-content-v1")
        val invalid = listOf(
            valid.copy(target = valid.target.copy(alternativePhoneme = "æ")),
            valid.copy(target = valid.target.copy(phraseIpa = "ʃ iː p")),
            valid.copy(target = valid.target.copy(phrase = "A different sheep phrase.")),
            valid.copy(target = valid.target.copy(contrastId = "ae_vs_e")),
            valid.copy(target = valid.target.copy(storyId = "pot_vs_port")),
            valid.copy(turnNumber = 3), valid.copy(contentVersion = ""),
            valid.copy(narrative = valid.narrative.copy(prompt = " ")),
            valid.copy(narrative = valid.narrative.copy(prompt = "x".repeat(601))),
            valid.copy(narrative = valid.narrative.copy(scene = "bad\u0000text")),
            valid.copy(narrative = valid.narrative.copy(intendedDialogue = "A ship arrives.")),
            valid.copy(narrative = valid.narrative.copy(alternateDialogue = "A sheep and a ship arrive."))
        )
        for (response in invalid) assertFailsWith<StoryDialogueException> { response.validatedStory(base.id, 2) }
    }

    @Test
    fun missingResourceCanBeRetriedAndHistoryMustMatchTheCompletedConversation() = runBlocking {
        var fail = true
        val source = BundledStoryDialogueSource {
            if (fail) error("Missing resource")
            Res.readBytes("files/stories/dialogue.json").decodeToString()
        }
        val base = MeaningTrapCatalog.stories.first()
        val real = listOf(completed(base, "alternate"))
        assertFails { source.continueStory(base.id, real) }
        fail = false
        val response = source.continueStory(base.id, real)
        assertEquals(response, source.continueStory(base.id, real.map { it.copy(example = true) }))
        assertFails { source.continueStory(base.id, real.map { it.copy(prompt = "Not this conversation") }) }
        assertFails { source.continueStory(base.id, real.map { it.copy(consequence = "Changed outcome") }) }
        assertFails { source.continueStory(base.id, real.map { it.copy(branch = "uncertain") }) }
        Unit
    }

    private fun completed(story: ComicStory, branch: String) = StoryDialogueHistoryTurn(story.promptDialogue, branch,
        if (branch == "intended") story.intendedOutcome.dialogue else story.trapOutcome.dialogue, example = false)
}
