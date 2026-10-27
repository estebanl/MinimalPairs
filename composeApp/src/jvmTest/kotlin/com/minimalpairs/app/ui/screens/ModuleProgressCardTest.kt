package com.minimalpairs.app.ui.screens

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import com.minimalpairs.app.data.progress.summarizeModules
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ModuleProgressCardTest {
    @Test
    fun separateStorySummaryNavigatesToItsSavedTarget() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val records = listOf(
                    ModulePracticeRecordEntity(2, "STORIES", "sheep_vs_ship", "At the shop", "i_vs_I", 90f, true, 200, 2000),
                    ModulePracticeRecordEntity(1, "STORIES", "sheep_vs_ship", "At the shop", "i_vs_I", 50f, false, 200, 1000))
                var selected: String? = null
                val scene = ImageComposeScene(width = 430, height = 1400) {
                    MinimalPairsTheme {
                        ModuleProgressCard("Stories", records, summarizeModules(records), 3000, onPractice = { selected = it })
                    }
                }
                try {
                    repeat(40) { scene.render((it + 1) * 16_666_667L).close() }
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    val nodes = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                    fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    assertTrue(nodes.any { it.text() == "2 scored attempts" })
                    assertTrue(nodes.any { it.text() == "Attempts: 2 · Latest: 90/100 · Best: 90/100" })
                    assertTrue(nodes.any { it.text() == "Intended outcome" })
                    assertTrue(nodes.any { it.text() == "Meaning trap" })
                    assertFalse(nodes.any { it.text().contains("accuracy", ignoreCase = true) && !it.text().contains("do not change") })
                    nodes.first { it.text() == "Practice At the shop" && it.config.contains(SemanticsActions.OnClick) }
                        .config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals("sheep_vs_ship", selected)
                } finally { scene.close() }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
