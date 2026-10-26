package com.minimalpairs.app.ui.screens

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.ui.navigation.EarTrainerComponent
import com.minimalpairs.app.ui.navigation.EarTrainerUiState
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Compose renders plus semantics checks; PNGs are review artifacts, not golden baselines. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class EarTrainerScreenTest {
    @Test
    fun renderStatesAcrossSizesThemesAndLargeText() = onUiThread {
        val configurations = listOf(
            Configuration("compact", 320, 640),
            Configuration("phone", 430, 820),
            Configuration("wide", 1000, 800),
            Configuration("large-text", 320, 640, 2f)
        )
        val ready = initialState()
        val answerable = ready.copy(hasPlayedPrompt = true)
        val correct = answerable.copy(selectedWord = ready.targetWord, lastResultCorrect = true)
        val wrongWord = ready.currentPair.word2
        val states = mapOf(
            "ready" to ready,
            "playing" to ready.copy(isPlayingAudio = true),
            "answerable" to answerable,
            "correct" to correct,
            "incorrect" to answerable.copy(selectedWord = wrongWord, lastResultCorrect = false),
            "slow" to answerable.copy(playbackSpeed = PlaybackSpeed.SLOW_DIAGNOSTIC, showFormantHint = true),
            "retry" to ready.copy(playbackError = "Couldn't play the word. Try again.")
        )
        for (configuration in configurations) {
            for (dark in listOf(false, true)) {
                for ((name, state) in states) {
                    Harness(configuration, dark, state).use { harness ->
                        harness.settle()
                        harness.capture(name)
                        assertFalse(harness.nodes().any { it.text().contains("Sound mastery") })
                        if (state.lastResultCorrect != null) {
                            val next = harness.button("Next word")
                            assertTrue(next.boundsInRoot.height >= 48f)
                            assertTrue(next.boundsInRoot.bottom <= configuration.height)
                            val feedback = harness.nodes().first {
                                it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite && it.text().contains("You heard", ignoreCase = true)
                            }
                            assertTrue(feedback.boundsInRoot.height > 0f, "Feedback must scroll into view")
                            assertTrue(feedback.boundsInRoot.bottom <= next.boundsInRoot.top, "Next must not obscure feedback")
                        }
                        val words = listOf(state.currentPair.word1, state.currentPair.word2).map { word ->
                            harness.button(word.replaceFirstChar(Char::uppercaseChar), exact = false)
                        }
                        val shouldEnable = state.hasPlayedPrompt && !state.isPlayingAudio && state.lastResultCorrect == null
                        words.forEach { assertEquals(!shouldEnable, it.config.contains(SemanticsProperties.Disabled)) }
                        if (name == "ready" && configuration.name != "large-text") {
                            assertTrue(words.all { it.boundsInRoot.height >= 48f })
                        }
                    }
                }
                Harness(configuration, dark, answerable).use { harness ->
                    harness.settle()
                    harness.click("Sound tips")
                    harness.settle()
                    assertTrue(harness.button("More details").config.getOrNull(SemanticsProperties.StateDescription) == "Collapsed")
                    assertFalse(harness.nodes().any { it.text().contains("Sound mastery") })
                    harness.capture("tips")
                    harness.click("More details")
                    harness.settle()
                    assertTrue(harness.nodes().any { it.text().contains("Sound mastery") })
                    harness.capture("details")
                }
            }
        }
    }

    @Test
    fun keyboardPlaybackAndNextReturnToStartOfExercise() = onUiThread {
        Harness(Configuration("interaction", 320, 640, 2f), false, initialState()).use { harness ->
            harness.settle()
            // Tab through the actual focus chain to Play, then activate it with Enter.
            var tabs = 0
            while (harness.button("Play word").config.getOrNull(SemanticsProperties.Focused) != true && tabs++ < 8) {
                harness.key(Key.Tab)
            }
            assertTrue(harness.button("Play word").config.getOrNull(SemanticsProperties.Focused) == true)
            harness.key(Key.Enter)
            assertEquals(1, harness.component.playCount)
            harness.component.state.value = initialState().copy(hasPlayedPrompt = true)
            harness.settle()
            harness.component.state.value = harness.component.state.value.copy(
                selectedWord = initialState().targetWord, lastResultCorrect = true
            )
            harness.settle()
            harness.click("Next word")
            harness.settle()
            val play = harness.button("Play word")
            assertTrue(play.boundsInRoot.height >= 48f)
            assertTrue(play.boundsInRoot.top >= 0f)
            assertTrue(play.config.getOrNull(SemanticsProperties.Focused) == true)
            assertFalse(harness.nodes().any { it.text() == "Next word" })
        }
    }

    private data class Configuration(val name: String, val width: Int, val height: Int, val fontScale: Float = 1f)

    private class Harness(
        private val configuration: Configuration,
        private val dark: Boolean,
        state: EarTrainerUiState
    ) : AutoCloseable {
        val component = PreviewComponent(state)
        private var frame = 0L
        private val scene = ImageComposeScene(
            width = configuration.width,
            height = configuration.height,
            density = Density(1f, configuration.fontScale)
        ) {
            MinimalPairsTheme(darkTheme = dark) { EarTrainerScreen(component) }
        }

        fun settle() {
            repeat(40) { scene.render(++frame * 16_666_667L).close() }
        }

        fun nodes(): List<SemanticsNode> {
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            return scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
        }

        fun button(label: String, exact: Boolean = true): SemanticsNode = nodes().first {
            it.config.contains(SemanticsActions.OnClick) && if (exact) it.text() == label else it.text().startsWith(label)
        }

        fun click(label: String) {
            assertTrue(button(label).config[SemanticsActions.OnClick].action!!.invoke())
        }

        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            settle()
        }

        fun capture(name: String) {
            val directory = File("build/reports/ear-trainer").apply { mkdirs() }
            scene.render(++frame * 16_666_667L).use { image ->
                image.encodeToData()!!.use { data ->
                    File(directory, "${configuration.name}-${if (dark) "dark" else "light"}-$name.png").writeBytes(data.bytes)
                }
            }
        }

        override fun close() = scene.close()
    }

    private class PreviewComponent(initial: EarTrainerUiState) : EarTrainerComponent {
        override val state = MutableStateFlow(initial)
        var playCount = 0
        override fun playPrompt() { playCount++; state.value = state.value.copy(isPlayingAudio = true) }
        override fun onSelectAnswer(chosenWord: String) {
            state.value = state.value.copy(selectedWord = chosenWord, lastResultCorrect = chosenWord == state.value.targetWord)
        }
        override fun setPlaybackSpeed(speed: PlaybackSpeed) { state.value = state.value.copy(playbackSpeed = speed) }
        override fun dismissFormantHint() { state.value = state.value.copy(showFormantHint = false) }
        override fun nextTrial() { state.value = initialState() }
        override fun onBack() = Unit
    }

    companion object {
        private fun initialState(): EarTrainerUiState {
            val contrast = VowelContrastsCatalog.getContrastById("i_vs_I")!!
            val pair = contrast.pairs.first()
            return EarTrainerUiState(contrast, pair, pair.word1, contrast.phoneme1)
        }

        private fun SemanticsNode.text(): String = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

        private fun onUiThread(block: () -> Unit) {
            var failure: Throwable? = null
            SwingUtilities.invokeAndWait {
                try { block() } catch (error: Throwable) { failure = error }
            }
            failure?.let { throw it }
        }
    }
}
