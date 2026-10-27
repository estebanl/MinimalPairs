package com.minimalpairs.app.sdd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.ui.components.OralCavityReferenceCard
import com.minimalpairs.app.ui.components.MouthPositionReferenceCard
import com.minimalpairs.app.ui.navigation.ArticulatorLabComponent
import com.minimalpairs.app.ui.navigation.ArticulatorLabUiState
import com.minimalpairs.app.ui.reference.VocalTractLabMeshes
import com.minimalpairs.app.ui.reference.MouthPositionReferences
import com.minimalpairs.app.ui.screens.ArticulatorLabScreen
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class MouthPositionUiTest {
    @Test
    fun cardRendersWithoutTextOverflowAtNarrowAndWideSizesInBothThemes() {
        for ((width, fontScale) in listOf(272 to 1f, 272 to 1.8f, 720 to 1f)) {
            for (dark in listOf(false, true)) {
                ImageComposeScene(width, 1400, density = Density(1f, fontScale)) {
                    MinimalPairsTheme(darkTheme = dark) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize().padding(8.dp)) {
                                MouthPositionReferenceCard("uː", "fool")
                            }
                        }
                    }
                }.use { scene ->
                    scene.render().close()
                    val image = scene.render()
                    val file = File("build/reports/mouth-position/card-$width-$fontScale-$dark.png")
                    file.parentFile.mkdirs()
                    file.writeBytes(image.encodeToData()!!.bytes)
                    image.close()
                    val nodes = scene.nodes()
                    assertEquals(2, nodes.count { it.config.getOrNull(SemanticsProperties.ContentDescription) != null })
                    val layouts = mutableListOf<TextLayoutResult>()
                    nodes.forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
                    assertTrue(layouts.isNotEmpty())
                    layouts.forEach { layout ->
                        val context = "$width / $fontScale / $dark: ${layout.layoutInput.text} (${layout.size})"
                        for (line in 0 until layout.lineCount) {
                            assertFalse(layout.isLineEllipsized(line), context)
                            // Skia's fractional line metrics can exceed rounded bounds by < 1 px.
                            assertTrue(layout.getLineRight(line) <= layout.size.width + 1f, context)
                            assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f, context)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun soundSwitchingUpdatesBothViewsAndRecordingKeepsTheReference() {
        val component = ReferenceScreenComponent()
        ImageComposeScene(390, 1800) {
            MinimalPairsTheme { ArticulatorLabScreen(component) }
        }.use { scene ->
            scene.render().close()
            assertTrue(scene.descriptions().isEmpty(), "Sound map remains the default")
            scene.click("Mouth position")
            scene.render().close()
            val preview = scene.render()
            val previewFile = File("build/reports/mouth-position/speak-screen.png")
            previewFile.parentFile.mkdirs()
            previewFile.writeBytes(preview.encodeToData()!!.bytes)
            preview.close()
            for (contrast in VowelContrastsCatalog.defaultContrasts) {
                component.state.value = ArticulatorLabUiState(contrast)
                for (first in listOf(true, false)) {
                    component.switchTargetPhoneme(first)
                    scene.render().close()
                    val phoneme = if (first) contrast.phoneme1 else contrast.phoneme2
                    val word = if (first) contrast.pairs.first().word1 else contrast.pairs.first().word2
                    val reference = MouthPositionReferences.forPhoneme(phoneme)!!
                    assertTrue(scene.nodes().any { it.text() == "$word /$phoneme/" })
                    assertEquals(
                        listOf("Front view. ${reference.frontDescription}", "Side view. ${reference.sideDescription}"),
                        scene.descriptions()
                    )
                    val beforeRecording = scene.descriptions()
                    component.state.value = component.state.value.copy(
                        recorderState = AudioRecorderState.RECORDING, liveF1 = 700f, liveF2 = 1200f
                    )
                    scene.render().close()
                    assertEquals(beforeRecording, scene.descriptions())
                }
            }
            scene.click("Sound map")
            scene.render().close()
            assertTrue(scene.descriptions().isEmpty())
        }
    }

    @Test
    fun interactiveModelRendersAndViewControlsChangeTheScene() {
        for ((width, fontScale) in listOf(272 to 1f, 272 to 1.8f, 720 to 1f)) {
            for (dark in listOf(false, true)) {
                ImageComposeScene(width, 1600, density = Density(1f, fontScale)) {
                    MinimalPairsTheme(darkTheme = dark) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            OralCavityReferenceCard("uː", "fool", Modifier.padding(8.dp))
                        }
                    }
                }.use { scene ->
                    scene.render().close()
                    scene.awaitModel("uː")
                    for (preset in listOf("Reset view", "Front", "Side", "Zoom in")) {
                        scene.click(preset)
                        scene.render().close()
                        val image = scene.render()
                        val file = File("build/reports/mouth-position/model-$width-$fontScale-$dark-${preset.replace(' ', '-')}.png")
                        file.parentFile.mkdirs()
                        file.writeBytes(image.encodeToData()!!.bytes)
                        image.close()
                        val model = scene.nodes().single {
                            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("3D mouth reference") } == true
                        }
                        val description = model.config[SemanticsProperties.StateDescription]
                        if (preset == "Front") assertTrue(description.contains("Rotation 0 degrees") && description.contains("Cutaway off"))
                        if (preset == "Side") assertTrue(description.contains("Rotation 90 degrees") && description.contains("Cutaway on"))
                        if (preset == "Zoom in") assertTrue(description.contains("115 percent"))
                        val layouts = mutableListOf<TextLayoutResult>()
                        scene.nodes().forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) }
                        layouts.forEach { layout ->
                            for (line in 0 until layout.lineCount) {
                                assertFalse(layout.isLineEllipsized(line), layout.layoutInput.text.text)
                                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f, layout.layoutInput.text.text)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun modelFollowsTheSelectedPhonemeAndRemainsAReferenceDuringRecording() {
        val component = ReferenceScreenComponent()
        ImageComposeScene(390, 1800) {
            MinimalPairsTheme { ArticulatorLabScreen(component) }
        }.use { scene ->
            scene.render().close()
            scene.click("3D model")
            for (contrast in VowelContrastsCatalog.defaultContrasts) {
                component.state.value = ArticulatorLabUiState(contrast)
                for (first in listOf(true, false)) {
                    component.switchTargetPhoneme(first)
                    scene.render().close()
                    val phoneme = if (first) contrast.phoneme1 else contrast.phoneme2
                    if (phoneme !in VocalTractLabMeshes.sourceShapes) {
                        assertTrue(scene.nodes().any { it.text()?.contains("does not yet have a matching VocalTractLab pose") == true })
                        assertEquals(2, scene.descriptions().size)
                        assertFalse(scene.nodes().any {
                            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("3D mouth reference") } == true
                        })
                        continue
                    }
                    scene.awaitModel(phoneme)
                    assertEquals(VocalTractLabMeshes.isDerived(phoneme),
                        scene.nodes().any { it.text() == "Derived teaching pose" })
                    if (VocalTractLabMeshes.isDerived(phoneme)) {
                        assertTrue(scene.nodes().any { it.text()?.contains("Approximate and unvalidated") == true })
                    }
                    val preview = scene.render()
                    val previewFile = File("build/reports/mouth-position/model-vowel-${VocalTractLabMeshes.sourceShapes.keys.indexOf(phoneme)}.png")
                    previewFile.parentFile.mkdirs()
                    previewFile.writeBytes(preview.encodeToData()!!.bytes)
                    preview.close()
                    fun modelDescription() = scene.nodes().flatMap {
                        it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                    }.single { it.startsWith("3D mouth reference") }
                    val description = modelDescription()
                    assertTrue(description.startsWith("3D mouth reference for /$phoneme/"))
                    component.state.value = component.state.value.copy(
                        recorderState = AudioRecorderState.RECORDING, liveF1 = 700f, liveF2 = 1200f)
                    scene.render().close()
                    assertEquals(description, modelDescription())
                }
            }
            scene.click("Mouth position")
            scene.render().close()
            assertEquals(2, scene.descriptions().size)
            scene.click("Sound map")
            scene.render().close()
            assertTrue(scene.descriptions().isEmpty())
        }
    }

    private fun ImageComposeScene.awaitModel(phoneme: String) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            render().close()
            if (nodes().any { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.any {
                    it.startsWith("3D mouth reference for /$phoneme/")
                } == true
            }) return
            Thread.sleep(10)
        }
        error("VocalTractLab mesh did not load for $phoneme")
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }

    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    private fun ImageComposeScene.descriptions() = nodes().flatMap {
        it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }.filter { it.startsWith("Front view.") || it.startsWith("Side view.") }

    private fun ImageComposeScene.click(text: String) {
        val node = nodes().first { it.text() == text && it.config.getOrNull(SemanticsActions.OnClick) != null }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }

    private class ReferenceScreenComponent : ArticulatorLabComponent {
        override val state = MutableStateFlow(ArticulatorLabUiState(VowelContrastsCatalog.defaultContrasts.first()))
        override fun switchTargetPhoneme(isPhoneme1: Boolean) {
            state.value = state.value.copy(isTargetPhoneme1 = isPhoneme1)
        }
        override fun toggleRecording() = Unit
        override fun testSynthesizedVowel(isPhoneme1: Boolean) = Unit
        override fun onBack() = Unit
    }
}
