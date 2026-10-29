package com.minimalpairs.app.ui.screens

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.use
import androidx.compose.ui.semantics.*
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.domain.StoryContrastEvidence
import com.minimalpairs.app.domain.recommendStory
import com.minimalpairs.app.ui.navigation.MeaningTrapComponent
import com.minimalpairs.app.ui.navigation.MeaningTrapUiState
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class StoryRecommendationScreenTest {
    @Test
    fun recommendationExplainsTheContrastAndOpensItsStoryWithoutRemovingManualSelection() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val component = Component()
                val recommendation = component.state.value.recommendation!!
                ImageComposeScene(width = 430, height = 1500) {
                    MinimalPairsTheme { MeaningTrapScreen(component) }
                }.use { scene ->
                    var frame = 0L
                    fun render() { repeat(20) { scene.render(++frame * 16_666_667L).close() } }
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                    fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    fun action() = nodes().first {
                        it.text() == "Practise ${recommendation.contrastLabel}" && it.config.contains(SemanticsActions.OnClick)
                    }
                    render()
                    assertTrue(nodes().any { it.text() == recommendation.explanation })
                    assertTrue(nodes().any { it.text() == "Recommended practice" })
                    assertTrue(nodes().any { it.text() == "sheep vs ship" })
                    action().config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals("pen_vs_pan", component.selected)
                    component.state.value = component.state.value.copy(isRecording = true)
                    render()
                    assertTrue(action().config.contains(SemanticsProperties.Disabled))
                }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    private class Component : MeaningTrapComponent {
        override val availableStories = MeaningTrapCatalog.stories
        override val state = MutableStateFlow(MeaningTrapUiState(availableStories.first(), recommendation =
            recommendStory(availableStories, mapOf("ae_vs_e" to StoryContrastEvidence(4, 3)))))
        var selected: String? = null
        override fun selectStory(storyId: String) { selected = storyId }
        override fun playPromptAudio() {}
        override fun playTargetPhraseAudio() {}
        override fun playOutcomeAudio() {}
        override fun playRecording() {}
        override fun continueConversation() {}
        override fun toggleRecording() {}
        override fun triggerSimulatedOutcome(isCorrect: Boolean) {}
        override fun resetCurrentStory() {}
        override fun onBack() {}
    }
}
