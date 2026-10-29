package com.minimalpairs.app.ui.screens

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.dialogue.StoryDialogueHistoryTurn
import com.minimalpairs.app.ui.navigation.MeaningTrapComponent
import com.minimalpairs.app.ui.navigation.MeaningTrapUiState
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class MeaningTrapScreenTest {
    @Test
    fun continuationRetryHistoryExampleModeAndCompletionAreVisibleAndActionable() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val component = Component()
                val base = component.availableStories.first()
                val scene = ImageComposeScene(width = 430, height = 1600) {
                    MinimalPairsTheme { MeaningTrapScreen(component) }
                }
                try {
                    var frame = 0L
                    fun render() { repeat(40) { scene.render(++frame * 16_666_667L).close() } }
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                    fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    fun button(label: String) = nodes().first { it.text() == label && it.config.contains(SemanticsActions.OnClick) }
                    fun capture(name: String) {
                        val directory = File("build/reports/stories").apply { mkdirs() }
                        scene.render(++frame * 16_666_667L).use { image ->
                            image.encodeToData()!!.use { data -> File(directory, "$name.png").writeBytes(data.bytes) }
                        }
                    }
                    render()
                    assertTrue(button("Continue story").config.contains(SemanticsProperties.Disabled))
                    component.state.value = component.state.value.copy(branch = MeaningTrapBranch.INTENDED_SUCCESS)
                    render()
                    assertFalse(button("Continue story").config.contains(SemanticsProperties.Disabled))
                    button("Continue story").config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(1, component.continuations)
                    component.state.value = component.state.value.copy(isLoadingNextTurn = true, hasRecording = true)
                    render()
                    assertTrue(nodes().any { it.text() == "Preparing the next turn…" })
                    assertTrue(button("Continue story").config.contains(SemanticsProperties.Disabled))
                    assertTrue(button("Record your reply").config.contains(SemanticsProperties.Disabled))
                    assertTrue(button("Replay your reply").config.contains(SemanticsProperties.Disabled))
                    assertFalse(button("Restart story").config.contains(SemanticsProperties.Disabled))

                    component.state.value = component.state.value.copy(isLoadingNextTurn = false,
                        continuationError = "Couldn't load the next turn. Retry or restart the story; your reply and progress are unchanged.",
                        gopScore = 90f, alternateScore = 10f, feedbackText = "Your reply matched the sound in “sheep”.")
                    render()
                    assertFalse(button("Retry next turn").config.contains(SemanticsProperties.Disabled))
                    assertTrue(nodes().any { it.text() == "Sound confidence · sheep: 90/100" })
                    button("Retry next turn").config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(2, component.continuations)
                    nodes().first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                        .config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10_000f)
                    render()
                    capture("conversation-retry")

                    val history = StoryDialogueHistoryTurn(base.promptDialogue, "alternate", base.trapOutcome.dialogue, true)
                    component.state.value = MeaningTrapUiState(base.copy(
                        premise = "The captain is waiting at the farm returns desk.",
                        promptDialogue = "Tell me what you wanted to adopt instead."),
                        conversation = listOf(history), isExampleConversation = true)
                    render()
                    assertTrue(nodes().any { it.text() == "Turn 2 of 3 · Follow-up" })
                    assertTrue(nodes().any { it.text() == "Example conversation · no personal scores" })
                    assertTrue(nodes().any { it.text().contains("Tell me what you wanted to adopt instead") })
                    assertTrue(button("Record your reply").config.contains(SemanticsProperties.Disabled))
                    assertFalse(button("Hear phrase").config.contains(SemanticsProperties.Disabled))
                    assertFalse(nodes().any { it.text() == "Listen" }, "Generated text must not offer the original prompt recording")
                    capture("conversation-follow-up")
                    button("+ Conversation so far").config[SemanticsActions.OnClick].action!!.invoke()
                    render()
                    assertTrue(nodes().any { it.text() == base.trapOutcome.dialogue })
                    assertTrue(nodes().any { it.text().startsWith("Example reply:") })

                    component.state.value = component.state.value.copy(conversation = listOf(history, history),
                        branch = MeaningTrapBranch.INTENDED_SUCCESS, isSimulated = true)
                    render()
                    assertTrue(nodes().any { it.text() == "Conversation complete. Restart to practise again." })
                    assertFalse(nodes().any { it.text() == "Continue story" })
                    button("Restart story").config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(1, component.resets)
                } finally { scene.close() }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    @Test
    fun feedbackErrorsAndScoresAreVisibleAndScoringDisablesRecordingAndPlayback() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val component = Component()
                val scene = ImageComposeScene(width = 430, height = 1500) {
                    MinimalPairsTheme { MeaningTrapScreen(component) }
                }
                try {
                    var frame = 0L
                    fun render() { repeat(40) { scene.render(++frame * 16_666_667L).close() } }
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                    fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    fun button(label: String) = nodes().first { it.text() == label && it.config.contains(SemanticsActions.OnClick) }
                    fun screenshot(name: String) {
                        val directory = File("build/reports/stories").apply { mkdirs() }
                        scene.render(++frame * 16_666_667L).use { image ->
                            image.encodeToData()!!.use { data -> File(directory, "$name.png").writeBytes(data.bytes) }
                        }
                    }
                    render()
                    assertFalse(button("Record your reply").config.contains(SemanticsProperties.Disabled))
                    assertTrue(button("Replay your reply").config.contains(SemanticsProperties.Disabled))
                    component.state.value = component.state.value.copy(isRecording = true, feedbackText = "Listening… Speak the full phrase, then stop to review.")
                    render()
                    assertTrue(button("Hear phrase").config.contains(SemanticsProperties.Disabled))
                    assertTrue(button("Replay your reply").config.contains(SemanticsProperties.Disabled))
                    assertFalse(button("Stop and review").config.contains(SemanticsProperties.Disabled))

                    component.state.value = component.state.value.copy(isRecording = false, isScoring = true, hasRecording = true,
                        feedbackText = "Checking the sounds in your reply…")
                    render()
                    assertTrue(nodes().any { it.text() == "Checking your reply" })
                    assertTrue(nodes().any { it.text() == "Checking the sounds in your reply…" })
                    assertTrue(button("Checking your reply…").config.contains(SemanticsProperties.Disabled))
                    assertTrue(button("Hear phrase").config.contains(SemanticsProperties.Disabled))
                    assertTrue(button("Replay your reply").config.contains(SemanticsProperties.Disabled))
                    screenshot("scoring")

                    component.state.value = component.state.value.copy(isScoring = false, feedbackIsError = true,
                        feedbackText = "No clear voice detected. Try the full phrase again. No result was saved.")
                    render()
                    assertTrue(nodes().any { it.text().startsWith("No clear voice detected") })
                    assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite })
                    assertFalse(button("Record your reply").config.contains(SemanticsProperties.Disabled))
                    assertFalse(button("Replay your reply").config.contains(SemanticsProperties.Disabled))
                    button("Replay your reply").config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(1, component.replays)
                    screenshot("recording-error")

                    component.state.value = component.state.value.copy(branch = MeaningTrapBranch.INTENDED_SUCCESS,
                        feedbackIsError = false, gopScore = 88f, alternateScore = 25f,
                        feedbackText = "Your reply matched the sound in “sheep”.")
                    render()
                    assertTrue(nodes().any { it.text() == "Sound confidence · sheep: 88/100" })
                    assertTrue(nodes().any { it.text() == "Sound confidence · ship: 25/100" })
                    assertTrue(nodes().any { it.text().contains("not pronunciation accuracy") })
                    assertFalse(nodes().any { it.text().contains("Perfect /iː/") })
                    screenshot("scored-reply")
                    screenshot("recorded-reply-replay")
                    component.state.value = component.state.value.copy(isPlayingAudio = true)
                    render()
                    assertTrue(button("Replay your reply").config.contains(SemanticsProperties.Disabled))
                    component.state.value = component.state.value.copy(isPlayingAudio = false)

                    component.state.value = component.state.value.copy(
                        playbackError = "Couldn't play the story ending audio. Tap Listen to retry."
                    )
                    render()
                    assertTrue(nodes().any { it.text() == "Audio unavailable" })
                    assertTrue(nodes().any { it.text().contains("Couldn't play the story ending audio") })
                    assertTrue(nodes().any { it.text() == "Sound confidence · sheep: 88/100" })
                    assertFalse(button("Hear phrase").config.contains(SemanticsProperties.Disabled))
                    screenshot("playback-error")

                    component.state.value = component.state.value.copy(
                        playbackError = "Couldn't replay your reply. Check your audio output, then tap Replay your reply to retry."
                    )
                    render()
                    assertTrue(nodes().any { it.text().contains("tap Replay your reply to retry") })
                    assertFalse(button("Replay your reply").config.contains(SemanticsProperties.Disabled))
                    assertTrue(nodes().any { it.text() == "Sound confidence · sheep: 88/100" })

                    component.state.value = component.state.value.copy(isSimulated = true, gopScore = null, alternateScore = null,
                        feedbackText = "Example ending — no recording was evaluated and your progress was not changed.")
                    render()
                    assertTrue(nodes().any { it.text() == "Example ending" })
                    assertFalse(nodes().any { it.text().startsWith("Sound confidence") })
                } finally { scene.close() }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    private class Component : MeaningTrapComponent {
        override val availableStories = MeaningTrapCatalog.stories
        override val state = MutableStateFlow(MeaningTrapUiState(availableStories.first()))
        var replays = 0
        var continuations = 0
        var resets = 0
        override fun selectStory(storyId: String) {}
        override fun playPromptAudio() {}
        override fun playTargetPhraseAudio() {}
        override fun playOutcomeAudio() {}
        override fun playRecording() { replays++ }
        override fun continueConversation() { continuations++ }
        override fun toggleRecording() {}
        override fun triggerSimulatedOutcome(isCorrect: Boolean) {}
        override fun resetCurrentStory() { resets++ }
        override fun onBack() {}
    }
}
