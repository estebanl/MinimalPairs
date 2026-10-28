package com.minimalpairs.app.ui.screens

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import com.minimalpairs.app.data.local.Sm2CardEntity
import com.minimalpairs.app.data.local.SpeakingRecordEntity
import com.minimalpairs.app.data.local.TrialRecordEntity
import com.minimalpairs.app.data.progress.*
import com.minimalpairs.app.ui.navigation.ConfusionMatrixComponent
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CompactProgressOverviewTest {
    @Test
    fun overviewFitsPhoneWidthsAndPreservesNestedHistoryWhenCollapsed() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                for ((width, fontScale) in listOf(430 to 1f, 320 to 1f, 320 to 2f)) {
                    var selected: String? = null
                    val component = object : ConfusionMatrixComponent {
                        override val state = MutableStateFlow(overviewFixture())
                        override fun practiceContrast(id: String) { selected = id }
                        override fun practiceSpeaking(id: String, phoneme: String) {}
                        override fun startSpeaking() {}
                        override fun retrySaves() {}
                        override fun retryLoading() {}
                        override fun onBack() {}
                    }
                    val scene = ImageComposeScene(width = width, height = if (fontScale == 1f) 820 else 1500,
                        density = Density(1f, fontScale)) {
                        MinimalPairsTheme(darkTheme = width == 430) { ConfusionMatrixScreen(component) }
                    }
                    try {
                        var frame = 0L
                        fun settle() { repeat(40) { scene.render(++frame * 16_666_667L).close() } }
                        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                        fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                        fun hasText(text: String) = nodes().any { node ->
                            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true ||
                                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true
                        }
                        fun action(label: String) = nodes().first { node ->
                            node.config.contains(SemanticsActions.OnClick) && (
                                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
                                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true)
                        }
                        fun click(label: String) { action(label).config[SemanticsActions.OnClick].action!!.invoke(); settle() }
                        settle()
                        assertTrue(hasText("83% accuracy"))
                        assertTrue(hasText("Answers: 48"))
                        assertTrue(hasText("Current streak: 3 days"))
                        assertTrue(hasText("12 scored recordings · Latest 88/100"))
                        assertTrue(hasText("7 scored attempts"))
                        assertFalse(hasText("Last 7 days"))
                        assertFalse(hasText("Show daily activity"))
                        assertFalse(hasText("Show stories progress"))
                        assertFalse(hasText("Scores are estimates out of 100."))
                        assertFalse(hasText("Progress"))
                        assertFalse(hasText("Back"))
                        for (label in listOf("Review sheep / ship", "Review bad / bed", "Show manage progress")) {
                            val node = action(label)
                            assertTrue(node.size.width > 0 && node.size.height >= 48, "$label needs a usable touch target at $width / $fontScale")
                            if (fontScale == 1f) {
                                val bounds = node.boundsInRoot
                                assertTrue(bounds.left >= 0 && bounds.right <= width, "$label exceeds screen width at $width / $fontScale")
                                assertTrue(bounds.bottom <= 820, "$label should be visible without scrolling")
                            }
                        }
                        val directory = File("build/reports/progress").apply { mkdirs() }
                        scene.render(++frame * 16_666_667L).use { image ->
                            image.encodeToData()!!.use { data ->
                                File(directory, "compact-overview-$width-${fontScale.toInt()}x.png").writeBytes(data.bytes)
                            }
                        }
                        click("Review bad / bed")
                        assertEquals("ae_vs_e", selected)
                        click("Show more listening details")
                        click("Show practice history")
                        click("Browse all 48 answers")
                        assertTrue(hasText("Show fewer answers"))
                        click("Hide more listening details")
                        assertFalse(hasText("Show fewer answers"))
                        click("Show more listening details")
                        assertTrue(hasText("Show fewer answers"), "Nested history expansion should survive collapsing its parent")
                    } finally { scene.close() }
                }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    private fun overviewFixture(): ProgressState {
        val now = 1_793_188_800_000L
        val cards = listOf("i_vs_I", "ae_vs_e").map { Sm2CardEntity(it, 1, 1, 2.5f, .5f, now - 172_800_000L) }
        val trials = listOf(Triple("i_vs_I", 10, 7), Triple("ae_vs_e", 12, 9), Triple("p_vs_b", 14, 12), Triple("s_vs_z", 12, 12))
            .flatMap { (id, count, correct) -> (1..count).map { index ->
                TrialRecordEntity(contrastId = id, targetWord = "sheep", selectedWord = "sheep", isCorrect = index <= correct,
                    latencyMs = 500, drillType = "FORCED_CHOICE", timestampEpochMs = now - index * 1000L)
            } }.mapIndexed { index, trial -> trial.copy(id = index + 1L) }
        val speaking = (12 downTo 1).map { index -> SpeakingRecordEntity(index.toLong(), "i_vs_I", "sheep", "iː", 88f, 500, now - index) }
        val modules = (1..7).map { index -> ModulePracticeRecordEntity(index.toLong(), if (index <= 4) "STORIES" else "RHYTHM",
            "sample", "Practice", null, 80f, true, 500, now - index) }
        return summarizeProgress(trials, cards, nowEpochMs = now).copy(
            activity = PracticeActivity(currentStreak = 3, longestStreak = 5), speaking = speaking,
            speakingSummaries = summarizeSpeaking(speaking), modules = modules, moduleSummaries = summarizeModules(modules))
    }
}
