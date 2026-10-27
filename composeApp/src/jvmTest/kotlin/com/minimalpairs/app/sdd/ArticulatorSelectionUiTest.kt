package com.minimalpairs.app.sdd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
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
    fun contentRespectsSafeAreaWithoutHeaderOrApplyingConsumedInsetsTwice() {
        var baselineContentTop = 0f
        var baselineContentLeft = 0f
        for ((top, side, parentConsumes) in listOf(
            Triple(0, 0, false), Triple(44, 0, false), Triple(59, 24, false), Triple(59, 24, true)
        )) {
            val insets = WindowInsets(left = side.dp, top = top.dp, right = side.dp)
            ImageComposeScene(390, 844) {
                MinimalPairsTheme(darkTheme = true) {
                    Box(if (parentConsumes) Modifier.windowInsetsPadding(insets) else Modifier) {
                        ArticulatorLabScreen(SelectionComponent(), contentWindowInsets = insets)
                    }
                }
            }.use { scene ->
                scene.render().close()
                val content = scene.nodes().first { it.text() == "Sound pair" }.boundsInRoot
                assertTrue(scene.nodes().none { it.text() == "Speak" || it.matches("Back") || it.text() == "Camera practice" })
                if (top == 0) {
                    baselineContentTop = content.top
                    baselineContentLeft = content.left
                    assertEquals(0f, content.top, "Content should use the space freed by the header")
                }
                assertEquals(baselineContentTop + top, content.top, "Safe area must be applied exactly once")
                assertEquals(baselineContentLeft + side, content.left)
                assertTrue(content.top >= top)
                if (top == 59 && !parentConsumes) {
                    val output = File("build/reports/articulator/content-safe-area.png")
                    output.parentFile.mkdirs()
                    scene.render().use { output.writeBytes(it.encodeToData()!!.bytes) }
                }
            }
        }
    }

    @Test
    fun cameraPracticeOpensWithCameraOffAndExamplePlaybackAvailable() {
        val component = SelectionComponent()
        ImageComposeScene(430, 932) { MinimalPairsTheme(darkTheme = false) { ArticulatorLabScreen(component) } }.use { scene ->
            scene.render().close()
            scene.click("Camera")
            scene.render().close()
            assertTrue(scene.nodes().any { it.text() == "Camera off" })
            assertTrue(scene.nodes().any { it.text() == "Start camera" })
            assertTrue(scene.nodes().any { it.text() == "Match the lip shape" })
            scene.click("Example")
            assertEquals(1, component.examplePlays)
            val image = scene.render()
            val output = File("build/reports/articulator/camera-practice.png")
            output.parentFile.mkdirs()
            output.writeBytes(image.encodeToData()!!.bytes)
            image.close()
        }
    }

    @Test
    fun mirrorLayoutKeepsIconTabsInOneRowAndSupportsLargeText() {
        for ((width, scale) in listOf(320 to 1f, 390 to 1f, 320 to 1.8f)) {
            for (dark in listOf(false, true)) {
                ImageComposeScene(width, 932, density = Density(1f, scale)) {
                    MinimalPairsTheme(darkTheme = dark) { ArticulatorLabScreen(SelectionComponent().apply { state.value = state.value.copy(hasRecording = true) }) }
                }.use { scene ->
                    scene.render().close()
                    val output = File("build/reports/articulator/mirror-$width-$scale-$dark.png")
                    output.parentFile.mkdirs()
                    scene.render().use { output.writeBytes(it.encodeToData()!!.bytes) }
                    val tabs = listOf("Sound map", "Mouth position", "3D model", "Camera").map { label ->
                        scene.nodes().first { it.matches(label) && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    }
                    assertEquals(if (scale > 1.3f) 2 else 1, tabs.map { it.boundsInRoot.top }.distinct().size)
                    assertTrue(tabs.all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width })
                    val layouts = mutableListOf<TextLayoutResult>()
                    scene.nodes().forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
                    for (layout in layouts) for (line in 0 until layout.lineCount) {
                        assertTrue(!layout.isLineEllipsized(line), layout.layoutInput.text.text)
                        assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f, "$width / $scale / ${layout.layoutInput.text}: bottom ${layout.getLineBottom(line)} > ${layout.size.height}")
                        assertTrue(layout.getLineRight(line) <= layout.size.width + 1f, "$width / $scale / ${layout.layoutInput.text}: ${layout.getLineRight(line)} > ${layout.size.width}")
                    }

                }
            }
        }
    }

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
                it.text() == "Stop" && it.config.getOrNull(SemanticsActions.OnClick) != null
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
            scene.click("Stop recording")
            assertEquals(1, component.recordingToggles)
            val image = scene.render()
            val output = File("build/reports/articulator/recording-control.png")
            output.parentFile.mkdirs()
            output.writeBytes(image.encodeToData()!!.bytes)
            image.close()
        }
    }

    @Test
    fun selectingSoundPairsUpdatesVowelPlaybackAndRecordingDisablesSelectors() {
        val component = SelectionComponent()
        ImageComposeScene(390, 1800) { MinimalPairsTheme { ArticulatorLabScreen(component) } }.use { scene ->
            scene.render().close()
            scene.click("Change sound pair")
            scene.render().close()
            assertTrue(scene.nodes().none { it.matches("Example words") || it.matches("feet / fit") })
            scene.click("bad / bed")
            scene.render().close()
            assertEquals(0, component.state.value.selectedPairIndex)
            assertEquals("ae_vs_e", component.state.value.contrast.id)
            scene.click("Example")
            assertEquals(true, component.lastPlayedFirst)
            scene.click("bed /e/")
            scene.render().close()
            assertTrue(!component.state.value.isTargetPhoneme1)
            scene.click("Example")
            assertEquals(false, component.lastPlayedFirst)
            component.state.value = component.state.value.copy(recorderState = AudioRecorderState.RECORDING)
            scene.render().close()
            for (label in listOf("Change sound pair", "bed /e/", "Example")) {
                assertNotNull(scene.nodes().first { it.matches(label) }.config.getOrNull(SemanticsProperties.Disabled))
            }
            val image = scene.render()
            val output = File("build/reports/articulator/selection.png")
            output.parentFile.mkdirs()
            output.writeBytes(image.encodeToData()!!.bytes)
            image.close()
        }
    }

    @Test
    fun catAndCutCanBeSelectedWithBothVowelExamples() {
        val component = SelectionComponent()
        ImageComposeScene(390, 1800) { MinimalPairsTheme { ArticulatorLabScreen(component) } }.use { scene ->
            scene.render().close()
            scene.click("Change sound pair")
            scene.render().close()
            scene.click("cat / cut")
            scene.render().close()
            assertEquals("ae_vs_uh", component.state.value.contrast.id)
            scene.click("Example")
            assertEquals(true, component.lastPlayedFirst)
            scene.click("cut /ʌ/")
            scene.render().close()
            scene.click("Example")
            assertEquals(false, component.lastPlayedFirst)
            assertTrue(scene.nodes().any { it.matches("Hear /ʌ/ example") })
            val output = File("build/reports/articulator/cat-cut-selection.png")
            output.parentFile.mkdirs()
            scene.render().use { output.writeBytes(it.encodeToData()!!.bytes) }
        }
    }

    @Test
    fun menuIncludesEveryListenPairAndConsonantsUseWordGuidance() {
        val component = SelectionComponent()
        ImageComposeScene(390, 1800) { MinimalPairsTheme { ArticulatorLabScreen(component) } }.use { scene ->
            scene.render().close()
            scene.click("Change sound pair")
            scene.render().close()
            for (contrast in ListeningContrastsCatalog.defaultContrasts) {
                val pair = contrast.pairs.first()
                assertTrue(scene.nodes().any { it.matches("${pair.word1} / ${pair.word2}") }, contrast.id)
            }
            scene.click("wet / vet")
            scene.render().close()
            assertEquals("w_vs_v", component.state.value.contrast.id)
            scene.click("vet /v/")
            scene.render().close()
            assertTrue(scene.nodes().any { it.matches("Hear vet example") })
            assertTrue(scene.nodes().any { it.text()?.contains("Say “vet” and focus on /v/") == true })
            assertTrue(scene.nodes().none { it.matches("Sound map") || it.matches("3D model") })
            scene.click("Camera")
            scene.render().close()
            assertTrue(scene.nodes().any { it.matches("Camera off") })
            assertTrue(scene.nodes().none { it.text()?.contains("Say /v/ on its own") == true })
        }
        component.selectContrast("p_vs_b")
        component.state.value = component.state.value.copy(
            overallGopScore = 72f, hasRecording = true,
            scoringDescription = "Selected-phoneme model confidence (experimental).")
        ImageComposeScene(390, 844) { MinimalPairsTheme { ArticulatorLabScreen(component) } }.use { scene ->
            scene.render().close()
            assertTrue(scene.nodes().any { it.matches("Selected sound /p/ · 72/100") })
            assertTrue(scene.nodes().any { it.matches("Selected-phoneme model confidence (experimental).") })
            scene.nodes().first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                .config[SemanticsActions.ScrollBy].action!!.invoke(0f, 600f)
            val start = System.nanoTime()
            repeat(15) { scene.render(start + it * 50_000_000L).close() }
            val output = File("build/reports/articulator/consonant-practice.png")
            output.parentFile.mkdirs()
            scene.render().use { output.writeBytes(it.encodeToData()!!.bytes) }
        }
    }

    @Test
    fun recordedPlaybackAppearsInBottomControlsOnlyAfterRecording() {
        val component = SelectionComponent()
        ImageComposeScene(320, 640) { MinimalPairsTheme { ArticulatorLabScreen(component) } }.use { scene ->
            scene.render().close()
            assertTrue(scene.nodes().none { it.matches("Play my recording") })
            component.state.value = component.state.value.copy(hasRecording = true)
            scene.render().close()
            val playback = scene.nodes().single {
                it.matches("Play my recording") && it.config.getOrNull(SemanticsActions.OnClick) != null
            }
            assertTrue(playback.boundsInRoot.top >= 0 && playback.boundsInRoot.bottom <= 640)
            val controls = listOf("Example", "Play mine", "Record").map { label ->
                scene.nodes().first { it.matches(label) && it.config.getOrNull(SemanticsActions.OnClick) != null }.boundsInRoot
            }
            assertEquals(1, controls.map { it.top }.distinct().size)
            assertTrue(controls.all { it.left >= 0 && it.right <= 320 })
            val before = playback.boundsInRoot
            val scroll = scene.nodes().first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
            assertTrue(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 1000f))
            val start = System.nanoTime()
            repeat(15) { scene.render(start + it * 50_000_000L).close() }
            assertEquals(before, scene.nodes().first {
                it.matches("Play my recording") && it.config.getOrNull(SemanticsActions.OnClick) != null
            }.boundsInRoot)
            scene.click("Play my recording")
            assertEquals(1, component.recordingPlays)
            scene.click("Example")
            assertEquals(1, component.examplePlays)
            assertTrue(scene.nodes().any { it.matches("Record") })
            val output = File("build/reports/articulator/recording-playback.png")
            output.parentFile.mkdirs()
            scene.render().use { output.writeBytes(it.encodeToData()!!.bytes) }
            component.state.value = component.state.value.copy(isBusy = true, isScoring = true)
            scene.render().close()
            assertNotNull(scene.nodes().first { it.matches("Play my recording") }.config.getOrNull(SemanticsProperties.Disabled))
            component.state.value = component.state.value.copy(isBusy = false, isScoring = false, recorderState = AudioRecorderState.RECORDING)
            scene.render().close()
            assertTrue(scene.nodes().none { it.matches("Play my recording") })
            scene.click("Stop recording")
            assertEquals(1, component.recordingToggles)
            component.state.value = ArticulatorLabUiState(ListeningContrastsCatalog.defaultContrasts.first())
            scene.render().close()
            assertTrue(scene.nodes().none { it.matches("Play my recording") })
        }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    private fun SemanticsNode.matches(label: String) =
        config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
            config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
    private fun ImageComposeScene.click(text: String) {
        val node = nodes().first { it.matches(text) && it.config.getOrNull(SemanticsActions.OnClick) != null }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private class SelectionComponent : ArticulatorLabComponent {
        var recordingToggles = 0
        var recordingPlays = 0
        override fun playRecording() { recordingPlays++ }
        var examplePlays = 0
        var lastPlayedFirst: Boolean? = null
        override val state = MutableStateFlow(ArticulatorLabUiState(ListeningContrastsCatalog.defaultContrasts.first()))
        override fun selectContrast(contrastId: String) {
            state.value = ArticulatorLabUiState(ListeningContrastsCatalog.getContrastById(contrastId)!!)
        }
        override fun switchTargetPhoneme(isPhoneme1: Boolean) { state.value = state.value.copy(isTargetPhoneme1 = isPhoneme1) }
        override fun toggleRecording() { recordingToggles++ }
        override fun playPhonemeExample(isPhoneme1: Boolean) { examplePlays++; lastPlayedFirst = isPhoneme1 }
        override fun onBack() = Unit
    }
}
