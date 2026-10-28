package com.minimalpairs.app.sdd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import com.minimalpairs.app.ui.components.OralCavityReferenceCard
import com.minimalpairs.app.ui.reference.ConsonantModelReferences
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ConsonantModelUiTest {
    @Test fun sourceGesturesRenderInBothViewsAndAffricatePhasesRemainIndependentOfRecording() {
        var selection by mutableStateOf("p" to "pear")
        var recording by mutableStateOf(false)
        ImageComposeScene(390, 1100) {
            MinimalPairsTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OralCavityReferenceCard(selection.first, selection.second,
                        observedF1 = if (recording) 700f else null,
                        observedF2 = if (recording) 1200f else null, isRecording = recording)
                }
            }
        }.use { scene ->
            for ((sound, word) in listOf("p" to "pear", "t" to "two", "k" to "coat", "f" to "fan",
                "θ" to "thin", "s" to "sue", "ʃ" to "shoe", "tʃ" to "chair", "l" to "light", "w" to "wet", "r" to "right")) {
                selection = sound to word
                val phase = ConsonantModelReferences.forPhoneme(sound)!!.phases.first()
                scene.awaitModel(sound, phase.label)
                for (view in listOf("Side", "Front")) {
                    scene.click(view)
                    scene.render().close()
                    scene.save("${phase.assetKey}-${view.lowercase()}")
                }
                val before = scene.description()
                recording = true
                scene.render().close()
                assertEquals(before, scene.description(), "Recording must not deform or relabel $sound")
                recording = false
            }
            selection = "tʃ" to "chair"
            scene.awaitModel("tʃ", "Affricate closure")
            scene.click("Fricative release")
            scene.awaitModel("tʃ", "Fricative release")
            scene.save("affricate-release")
            // A rapid selection burst must never retain a model labeled for an earlier sound.
            selection = "p" to "pear"; selection = "s" to "sue"; selection = "l" to "light"
            scene.awaitModel("l", "Lateral tongue contact")
            selection = "r" to "right"
            scene.awaitModel("r", "Bunched")
            scene.click("Tip-up")
            scene.awaitModel("r", "Tip-up")
            scene.click("Side")
            scene.save("rhotic-tip-up-side")
            scene.click("Front")
            scene.save("rhotic-tip-up-front")
            scene.click("Side")
            scene.render().close()
            scene.click("Tongue section")
            scene.render().close()
            assertTrue(scene.nodes().any { it.config.getOrNull(SemanticsProperties.StateDescription)?.contains("Surface only") == true })
            scene.click("Tongue section")
            scene.render().close()
            repeat(4) {
                scene.click("Bunched"); scene.click("Tip-up")
                scene.awaitModel("r", "Tip-up")
            }
            selection = "unknown" to "unsupported"
            scene.render().close(); scene.render().close()
            assertNull(scene.description())
            assertTrue(scene.nodes().any { it.text()?.contains("does not yet have a matching 3D reference") == true })
            scene.save("unsupported-fallback")
        }
    }

    private fun ImageComposeScene.awaitModel(sound: String, label: String) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            render().close()
            if (description()?.startsWith("3D mouth reference for /$sound/.") == true && description()!!.contains(label)) return
            Thread.sleep(10)
        }
        fail("No matching model for $sound / $label")
    }
    private fun ImageComposeScene.save(name: String) {
        val file = File("build/reports/consonant-models/$name.png")
        file.parentFile.mkdirs()
        render().use { file.writeBytes(it.encodeToData()!!.bytes) }
    }
    private fun ImageComposeScene.description(): String? = nodes().flatMap {
        it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }.singleOrNull { it.startsWith("3D mouth reference") }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    private fun ImageComposeScene.click(label: String) {
        val node = nodes().first { it.text() == label && it.config.getOrNull(SemanticsActions.OnClick) != null }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
}
