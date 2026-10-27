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
import com.minimalpairs.app.data.local.SpeakingRecordEntity
import com.minimalpairs.app.data.local.TrialRecordEntity
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class HistoryPagesTest {
    @Test
    fun listeningHistoryCanReachEveryAnswerAndResetForFilters() = verifyHistory(speaking = false)

    @Test
    fun speakingHistoryCanReachEveryRecordingAndResetForFilters() = verifyHistory(speaking = true)

    private fun verifyHistory(speaking: Boolean) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val count = mutableStateOf(45)
                val historyKey = mutableStateOf("all")
                val noun = if (speaking) "recordings" else "answers"
                fun rowText(index: Int) = if (speaking) "word$index · /iː/" else "Correct · word$index"
                val scene = ImageComposeScene(width = 430, height = 1100) {
                    MinimalPairsTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            ProgressDetailSection("History", "Saved results") {
                                if (speaking) {
                                    SpeakingProgressCard(
                                        records = (count.value downTo 1).map { index ->
                                            SpeakingRecordEntity(index.toLong(), "i_vs_I", "word$index", "iː", 80f, 200, index * 1000L)
                                        },
                                        summaries = emptyList(), nowEpochMs = 50_000,
                                        onStartSpeaking = {}, onPractice = { _, _ -> }, historyKey = historyKey.value
                                    )
                                } else {
                                    PracticeHistory((count.value downTo 1).map { index ->
                                        TrialRecordEntity(index.toLong(), "i_vs_I", "word$index", "word$index", true, 1000,
                                            "FORCED_CHOICE", index * 1000L)
                                    }, historyKey = historyKey.value)
                                }
                            }
                        }
                    }
                }
                try {
                    var frame = 0L
                    fun settle() { repeat(40) { scene.render(++frame * 16_666_667L).close() } }
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                    fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    fun button(label: String) = nodes().first { it.text() == label && it.config.contains(SemanticsActions.OnClick) }
                    fun click(label: String) {
                        val node = button(label)
                        assertFalse(node.config.contains(SemanticsProperties.Disabled), label)
                        node.config[SemanticsActions.OnClick].action!!.invoke()
                        settle()
                    }
                    fun assertRows(expected: IntRange) {
                        val actual = nodes().map { it.text() }.filter {
                            if (speaking) it.startsWith("word") && it.endsWith(" · /iː/") else it.startsWith("Correct · word")
                        }
                        assertEquals(expected.reversed().map(::rowText), actual)
                    }
                    settle()
                    click("Show history")
                    assertRows(41..45)
                    click("Browse all 45 $noun")
                    assertRows(26..45)
                    assertTrue(button("Newer $noun").config.contains(SemanticsProperties.Disabled))
                    click("Older $noun")
                    assertRows(6..25)
                    click("Older $noun")
                    assertRows(1..5)
                    assertTrue(button("Older $noun").config.contains(SemanticsProperties.Disabled))
                    click("Newer $noun")
                    assertRows(6..25)
                    click("Hide history")
                    click("Show history")
                    assertRows(6..25)

                    // Reset even when the filter changes while the section is absent.
                    click("Hide history")
                    historyKey.value = "last-week"
                    settle()
                    click("Show history")
                    assertRows(41..45)
                    click("Browse all 45 $noun")
                    click("Older $noun")
                    historyKey.value = "this-month"
                    settle()
                    assertRows(41..45)

                    // A shrinking result set must never leave an empty out-of-range page.
                    click("Browse all 45 $noun")
                    click("Older $noun")
                    click("Older $noun")
                    count.value = 8
                    settle()
                    assertRows(1..8)
                    assertFalse(nodes().any { it.text() == "Older $noun" })
                    count.value = 0
                    settle()
                    assertRows(IntRange.EMPTY)
                    count.value = 45
                    settle()
                    assertRows(26..45)
                    click("Show fewer $noun")
                    assertRows(41..45)
                } finally { scene.close() }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
