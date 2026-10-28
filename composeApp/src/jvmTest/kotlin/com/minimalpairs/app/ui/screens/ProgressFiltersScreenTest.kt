package com.minimalpairs.app.ui.screens

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import com.minimalpairs.app.data.local.*
import com.minimalpairs.app.data.progress.*
import com.minimalpairs.app.ui.navigation.ConfusionMatrixComponent
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import javax.swing.SwingUtilities
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ProgressFiltersScreenTest {
    @Test
    fun filterActionsShowScopedEmptyStatesAndKeepGlobalActivity() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val today = Instant.parse("2026-10-26T12:00:00Z").toEpochMilliseconds()
                val calendar = PracticeCalendar(LocalDate(2026, 10, 26), TimeZone.UTC)
                val snapshot = ProgressSnapshot(listOf(
                    TrialRecordEntity(1, "i_vs_I", "sheep", "sheep", true, 200, "FORCED_CHOICE", today)
                ), listOf(Sm2CardEntity("i_vs_I", 1, 1, 2.5f, .5f, 0)), emptyList())
                var filters = ProgressFilters()
                val component = object : ConfusionMatrixComponent {
                    override val state = MutableStateFlow(summarizeFilteredProgress(snapshot, calendar, filters, today))
                    fun refresh() { state.value = summarizeFilteredProgress(snapshot, calendar, filters, today) }
                    override fun setDateRange(range: ProgressDateRange) { filters = filters.copy(dateRange = range); refresh() }
                    override fun setContrastFilter(contrastId: String?) { filters = filters.copy(contrastId = contrastId); refresh() }
                    override fun clearFilters() { filters = ProgressFilters(); refresh() }
                    override fun practiceContrast(id: String) {}
                    override fun practiceSpeaking(id: String, phoneme: String) {}
                    override fun startSpeaking() {}
                    override fun retrySaves() {}
                    override fun retryLoading() {}
                    override fun onBack() {}
                }
                val scene = ImageComposeScene(width = 430, height = 1400) {
                    MinimalPairsTheme { ConfusionMatrixScreen(component) }
                }
                try {
                    var frame = 0L
                    fun settle() { repeat(40) { scene.render(++frame * 16_666_667L).close() } }
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                    fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                        ?: config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    fun has(text: String) = nodes().any { node ->
                        node.text() == text || node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
                    }
                    fun click(text: String) {
                        nodes().first { it.text() == text && it.config.contains(SemanticsActions.OnClick) }
                            .config[SemanticsActions.OnClick].action!!.invoke()
                        settle()
                    }
                    settle()
                    assertFalse(has("Last 7 days"))
                    click("Show filters")
                    click("Last 7 days")
                    assertEquals(ProgressDateRange.LAST_7_DAYS, filters.dateRange)
                    assertTrue(has("Last 7 days · All contrasts"))
                    click("Contrast: All contrasts")
                    click("pear / bear")
                    assertEquals("p_vs_b", filters.contrastId)
                    click("Hide filters")
                    assertTrue(has("Last 7 days · pear / bear"))
                    assertFalse(has("Last 7 days"))
                    assertTrue(has("No listening answers match these filters."))
                    assertFalse(has("Your progress starts here"))
                    assertTrue(has("All listening practice"))
                    assertTrue(has("Current streak: 1 day"))
                    assertTrue(has("Review sheep / ship"))
                    click("Show speaking progress")
                    assertTrue(has("No speaking recordings match these filters."))
                    assertFalse(has("Start speaking"))
                    click("Clear filters")
                    assertEquals(ProgressFilters(), filters)
                    assertTrue(has("100% accuracy"))
                    assertFalse(has("No listening answers match these filters."))
                    assertTrue(has("Start speaking"))
                } finally { scene.close() }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
