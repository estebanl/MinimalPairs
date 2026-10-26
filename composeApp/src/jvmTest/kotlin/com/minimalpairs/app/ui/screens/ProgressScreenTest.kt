package com.minimalpairs.app.ui.screens

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.minimalpairs.app.data.local.TrialRecordEntity
import com.minimalpairs.app.data.local.Sm2CardEntity
import com.minimalpairs.app.data.progress.*
import com.minimalpairs.app.ui.navigation.ConfusionMatrixComponent
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ProgressScreenTest {
    @Test
    fun progressUpdatesAndPracticeTargetsTheContrast() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                var selected: String? = null
                val component = object : ConfusionMatrixComponent {
                    override val state = MutableStateFlow(ProgressState())
                    override fun practiceContrast(id: String) { selected = id }
                    override fun onBack() {}
                }
                val scene = ImageComposeScene(width = 430, height = 1100) {
                    MinimalPairsTheme { ConfusionMatrixScreen(component) }
                }
                try {
                    var frame = 0L
                    fun settle() { repeat(40) { scene.render(++frame * 16_666_667L).close() } }
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                    fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    settle()
                    assertTrue(nodes().any { it.text() == "Loading your progress…" })
                    component.state.value = summarizeProgress(emptyList(), emptyList())
                    settle()
                    assertTrue(nodes().any { it.text() == "Your progress starts here" })
                    component.state.value = summarizeProgress(listOf(TrialRecordEntity(
                        contrastId = "i_vs_I", targetWord = "sheep", selectedWord = "ship", isCorrect = false,
                        latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = 1000
                    )), listOf(Sm2CardEntity("i_vs_I", 0, 1, 2.3f, .47f, 1000)))
                    settle()
                    assertTrue(nodes().any { it.text() == "0% accuracy" })
                    assertTrue(nodes().any { it.text() == "Mastery estimate · 47%" })
                    assertTrue(nodes().any { it.text() == "Review due" })
                    assertFalse(nodes().any { it.text() == "Your progress starts here" })
                    val practice = nodes().first { it.config.contains(SemanticsActions.OnClick) && it.text().startsWith("Practice ") }
                    practice.config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals("i_vs_I", selected)
                    val directory = File("build/reports/progress").apply { mkdirs() }
                    scene.render(++frame * 16_666_667L).use { image ->
                        image.encodeToData()!!.use { data -> File(directory, "progress.png").writeBytes(data.bytes) }
                    }
                } finally { scene.close() }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
