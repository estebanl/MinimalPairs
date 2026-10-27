package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.minimalpairs.app.data.progress.ContrastProgress
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SoundConfusionMatrixTest {
    @Test
    fun matrixSwitchesContrastsAndPracticesSelectedSounds() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                for ((width, fontScale) in listOf(430 to 1f, 320 to 2f)) {
                    var selected: String? = null
                    val contrasts = listOf(
                        ContrastProgress("i_vs_I", 4, 3, null, mapOf("iː" to mapOf("iː" to 3, "ɪ" to 1))),
                        ContrastProgress("p_vs_b", 2, 0, null, mapOf("p" to mapOf("b" to 2)))
                    )
                    val displayedContrasts = mutableStateOf(contrasts)
                    val scene = ImageComposeScene(width = width, height = 1400, density = Density(1f, fontScale)) {
                        MinimalPairsTheme(darkTheme = width == 430) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                ProgressDetailSection("Sound patterns", "Choose a contrast") {
                                    SoundConfusionMatrix(displayedContrasts.value) { selected = it }
                                }
                            }
                        }
                    }
                    try {
                        var frame = 0L
                        fun settle() { repeat(40) { scene.render(++frame * 16_666_667L).close() } }
                        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                        fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                        fun descriptions() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                        fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                        fun click(label: String) {
                            nodes().first { it.text() == label && it.config.contains(SemanticsActions.OnClick) }
                                .config[SemanticsActions.OnClick].action!!.invoke()
                            settle()
                        }
                        settle()
                        click("Show sound patterns")
                        assertTrue(descriptions().contains("Heard /iː/, chose /iː/: 3 of 4 answers, 75 percent, correct"))
                        assertTrue(descriptions().contains("Heard /iː/, chose /ɪ/: 1 of 4 answers, 25 percent, mixed up"))
                        assertTrue(descriptions().contains("Heard /ɪ/, chose /ɪ/: not practiced"))
                        displayedContrasts.value = contrasts.reversed()
                        settle()
                        assertTrue(descriptions().contains("Heard /iː/, chose /iː/: 3 of 4 answers, 75 percent, correct"))
                        click("Practice these sounds")
                        assertEquals("i_vs_I", selected)
                        val directory = File("build/reports/progress").apply { mkdirs() }
                        scene.render(++frame * 16_666_667L).use { image ->
                            image.encodeToData()!!.use { File(directory, "matrix-$width.png").writeBytes(it.bytes) }
                        }
                        click("pear / bear")
                        assertTrue(descriptions().contains("Heard /p/, chose /p/: 0 of 2 answers, 0 percent, correct"))
                        assertTrue(descriptions().contains("Heard /p/, chose /b/: 2 of 2 answers, 100 percent, mixed up"))
                        assertFalse(descriptions().any { it.contains("/iː/") })
                        click("Practice these sounds")
                        assertEquals("p_vs_b", selected)
                        displayedContrasts.value = contrasts
                        settle()
                        click("Hide sound patterns")
                        assertFalse(descriptions().any { it.contains("Heard /p/") })
                        click("Show sound patterns")
                        assertTrue(descriptions().contains("Heard /p/, chose /b/: 2 of 2 answers, 100 percent, mixed up"))
                        click("Practice these sounds")
                        assertEquals("p_vs_b", selected)
                        displayedContrasts.value = contrasts.take(1)
                        settle()
                        click("Practice these sounds")
                        assertEquals("i_vs_I", selected)
                        displayedContrasts.value = contrasts.reversed()
                        settle()
                        click("Practice these sounds")
                        assertEquals("i_vs_I", selected)
                    } finally { scene.close() }
                }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
