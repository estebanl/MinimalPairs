package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.use
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class StoryIllustrationTest {
    private val illustratedIds = setOf("sheep_vs_ship", "pen_vs_pan", "heart_vs_hut", "fool_vs_full")

    @Test
    fun allStoryBranchesHaveDistinctDescriptionsAndFitPhoneAndDesktopWidths() {
        onUiThread {
            val stories = MeaningTrapCatalog.stories.filter { it.id in illustratedIds }
            assertEquals(4, stories.size)
            for (width in listOf(320, 1100)) {
                var selectedStory by mutableStateOf(stories.first())
                var branch by mutableStateOf(MeaningTrapBranch.PENDING)
                ImageComposeScene(width = width, height = 550) {
                    MinimalPairsTheme { StoryIllustration(selectedStory, branch) }
                }.use { scene ->
                    var frame = 0L
                    for (story in stories) {
                        val descriptions = MeaningTrapBranch.entries.map { storyIllustrationDescription(story, it) }
                        assertEquals(3, descriptions.distinct().size, "${story.id} must explain its different scenes")
                        for (outcome in MeaningTrapBranch.entries) {
                            selectedStory = story
                            branch = outcome
                            repeat(3) { scene.render(++frame * 16_666_667L).close() }
                            val expected = storyIllustrationDescription(story, outcome)
                            val illustration = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                                .single { it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(expected) }
                            val bounds = illustration.boundsInRoot
                            assertTrue(bounds.left >= 0f && bounds.right <= width + 1f)
                            assertTrue(bounds.width <= 761f, "Artwork is capped for large desktop windows")
                            assertTrue(bounds.height > 0f && bounds.bottom <= 550f)
                            assertEquals(2f, bounds.width / bounds.height, .02f)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun newStoriesHaveAccessibleFallbackScenes() {
        val story = MeaningTrapCatalog.stories.first().copy(id = "future_story")
        for (branch in MeaningTrapBranch.entries) {
            val description = storyIllustrationDescription(story, branch)
            assertTrue(description.startsWith("Story illustration:"))
            assertTrue(description.contains(when (branch) {
                MeaningTrapBranch.PENDING -> story.promptCharacter
                MeaningTrapBranch.INTENDED_SUCCESS -> story.intendedWord
                MeaningTrapBranch.TRAP_TRIGGERED -> story.trapWord
            }))
        }
    }

    @Test
    fun renderAllTwelveScenesForVisualReview() {
        onUiThread {
            val stories = MeaningTrapCatalog.stories.filter { it.id in illustratedIds }
            ImageComposeScene(width = 1200, height = 800) {
                MinimalPairsTheme {
                    Column {
                        stories.forEach { story ->
                            Row {
                                MeaningTrapBranch.entries.forEach { branch ->
                                    StoryIllustration(story, branch, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }.use { scene ->
                repeat(3) { scene.render(it * 16_666_667L).close() }
                val directory = File("build/reports/stories").apply { mkdirs() }
                scene.render(50_000_000L).use { rendered ->
                    rendered.encodeToData()!!.use { data -> File(directory, "illustration-scenes.png").writeBytes(data.bytes) }
                }
            }
        }
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
}
