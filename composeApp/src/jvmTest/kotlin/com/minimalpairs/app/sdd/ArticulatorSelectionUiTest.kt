package com.minimalpairs.app.sdd

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.ui.navigation.ArticulatorLabComponent
import com.minimalpairs.app.ui.navigation.ArticulatorLabUiState
import com.minimalpairs.app.ui.screens.ArticulatorLabScreen
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class ArticulatorSelectionUiTest {
    @Test
    fun recordingControlStaysVisibleWhenReferenceContentScrolls() {
        val component = SelectionComponent()
        ImageComposeScene(320, 640) { MinimalPairsTheme { ArticulatorLabScreen(component) } }.use { scene ->
            scene.render().close()
            scene.click("Mouth position")
            scene.render().close()
            component.state.value = component.state.value.copy(recorderState = AudioRecorderState.RECORDING)
            scene.render().close()
            fun recordingButton() = scene.nodes().single {
                it.text() == "Stop and review" && it.config.getOrNull(SemanticsActions.OnClick) != null
            }
            val before = recordingButton().boundsInRoot
            assertTrue(before.height >= 48f && before.top >= 0f && before.bottom <= 640f)
            val scroll = scene.nodes().first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
            assertTrue(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 1000f))
            val start = System.nanoTime()
            repeat(15) { scene.render(start + it * 50_000_000L).close() }
            assertTrue(scene.nodes().any {
                (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f) > 0f
            }, "Reference content must actually scroll before checking the pinned control")
            assertEquals(before, recordingButton().boundsInRoot)
            scene.click("Stop and review")
            assertEquals(1, component.recordingToggles)
            val image = scene.render()
            val output = File("build/reports/articulator/recording-control.png")
            output.parentFile.mkdirs()
            output.writeBytes(image.encodeToData()!!.bytes)
            image.close()
        }
    }

    @Test
    fun selectingWordsUpdatesTargetLabelsAndRecordingDisablesSelectors() {
        val component = SelectionComponent()
        ImageComposeScene(390, 1800) { MinimalPairsTheme { ArticulatorLabScreen(component) } }.use { scene ->
            scene.render().close()
            scene.click("feet / fit")
            scene.render().close()
            assertEquals(1, component.state.value.selectedPairIndex)
            scene.click("fit /ɪ/")
            scene.render().close()
            assertTrue(!component.state.value.isTargetPhoneme1)
            component.state.value = component.state.value.copy(recorderState = AudioRecorderState.RECORDING)
            scene.render().close()
            for (label in listOf("feet / fit", "fit /ɪ/", "Contrast: /iː/ and /ɪ/")) {
                assertNotNull(scene.nodes().first { it.text() == label }.config.getOrNull(SemanticsProperties.Disabled))
            }
            val image = scene.render()
            val output = File("build/reports/articulator/selection.png")
            output.parentFile.mkdirs()
            output.writeBytes(image.encodeToData()!!.bytes)
            image.close()
        }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    private fun ImageComposeScene.click(text: String) {
        val node = nodes().first { it.text() == text && it.config.getOrNull(SemanticsActions.OnClick) != null }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private class SelectionComponent : ArticulatorLabComponent {
        var recordingToggles = 0
        override val state = MutableStateFlow(ArticulatorLabUiState(VowelContrastsCatalog.defaultContrasts.first()))
        override fun selectExamplePair(index: Int) { state.value = state.value.copy(selectedPairIndex = index) }
        override fun switchTargetPhoneme(isPhoneme1: Boolean) { state.value = state.value.copy(isTargetPhoneme1 = isPhoneme1) }
        override fun toggleRecording() { recordingToggles++ }
        override fun testSynthesizedVowel(isPhoneme1: Boolean) = Unit
        override fun onBack() = Unit
    }
}
