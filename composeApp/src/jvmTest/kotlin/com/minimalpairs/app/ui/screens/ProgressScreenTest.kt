package com.minimalpairs.app.ui.screens

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.local.TrialRecordEntity
import com.minimalpairs.app.data.local.Sm2CardEntity
import com.minimalpairs.app.data.local.SpeakingRecordEntity
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
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
                var selectedPhoneme: String? = null
                var retries = 0
                var reloads = 0
                var speakingStarts = 0
                var selectedStory: String? = null
                var selectedRhythm: String? = null
                val component = object : ConfusionMatrixComponent {
                    override val state = MutableStateFlow(ProgressState())
                    override fun practiceContrast(id: String) { selected = id }
                    override fun practiceSpeaking(id: String, phoneme: String) { selected = id; selectedPhoneme = phoneme }
                    override fun startSpeaking() { speakingStarts++ }
                    override fun practiceStory(id: String) { selectedStory = id }
                    override fun practiceRhythm(id: String) { selectedRhythm = id }
                    override fun onBack() {}
                    override fun retrySaves() { retries++ }
                    override fun retryLoading() { reloads++ }
                }
                val scene = ImageComposeScene(width = 430, height = 1100) {
                    MinimalPairsTheme { ConfusionMatrixScreen(component) }
                }
                try {
                    var frame = 0L
                    fun settle() { repeat(40) { scene.render(++frame * 16_666_667L).close() } }
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                    fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                        ?: config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    fun hasTextStarting(prefix: String) = nodes().any { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith(prefix) } == true
                    }
                    fun click(label: String) {
                        nodes().first { it.text() == label && it.config.contains(SemanticsActions.OnClick) }
                            .config[SemanticsActions.OnClick].action!!.invoke()
                        settle()
                    }
                    settle()
                    assertTrue(nodes().any { it.text() == "Loading your progress…" })
                    component.state.value = ProgressState(loading = false, error = "1 answer hasn't been saved.", pendingAnswers = 1, canRetrySave = true)
                    settle()
                    nodes().first { it.config.contains(SemanticsActions.OnClick) && it.text() == "Retry saving" }
                        .config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(1, retries)
                    assertFalse(nodes().any { it.text() == "Your progress starts here" })
                    component.state.value = ProgressState(loading = false, error = "Couldn't load progress. Try again.", canRetryLoad = true)
                    settle()
                    nodes().first { it.config.contains(SemanticsActions.OnClick) && it.text() == "Retry loading" }
                        .config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals(1, reloads)
                    assertFalse(nodes().any { it.text() == "Your progress starts here" })
                    assertFalse(nodes().any { it.text() == "Show speaking progress" })
                    assertFalse(hasTextStarting("0 scored recordings"))
                    component.state.value = component.state.value.copy(isRefreshing = true)
                    settle()
                    assertTrue(nodes().any { it.text() == "Refreshing progress…" })
                    assertFalse(nodes().any { it.text() == "Show speaking progress" })
                    component.state.value = summarizeProgress(emptyList(), emptyList())
                    settle()
                    assertTrue(nodes().any { it.text() == "Your progress starts here" })
                    assertTrue(nodes().any { it.text() == "Show speaking progress" })
                    assertTrue(hasTextStarting("0 scored recordings"))
                    click("Show speaking progress")
                    click("Start speaking")
                    assertEquals(1, speakingStarts)
                    assertNull(selected)
                    click("Hide speaking progress")
                    component.state.value = summarizeProgress(listOf(TrialRecordEntity(
                        contrastId = "i_vs_I", targetWord = "sheep", selectedWord = "ship", isCorrect = false,
                        latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = 1000
                    )), listOf(Sm2CardEntity("i_vs_I", 0, 1, 2.3f, .47f, 1000)))
                    settle()
                    assertTrue(nodes().any { it.text() == "0% accuracy" })
                    assertFalse(nodes().any { it.text() == "0-day streak" })
                    assertFalse(nodes().any { it.text() == "Recent accuracy" })
                    click("Show more listening details")
                    click("Show daily activity")
                    assertTrue(nodes().any { it.text() == "0-day streak" })
                    assertTrue(nodes().any { it.text() == "Longest streak: 1 day" })
                    assertEquals(7, nodes().count {
                        it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { description ->
                            description.contains(": 0 answers")
                        } == true
                    })
                    assertTrue(nodes().any { it.text() == "Mastery estimate · 47%" })
                    assertTrue(nodes().any { it.text() == "Review due" })
                    assertTrue(nodes().any { it.text() == "1 due" })
                    nodes().first { it.config.contains(SemanticsActions.OnClick) && it.text() == "Review sheep / ship" }
                        .config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals("i_vs_I", selected)
                    assertFalse(nodes().any { it.text() == "Your progress starts here" })
                    val practice = nodes().first { it.config.contains(SemanticsActions.OnClick) && it.text().startsWith("Practice ") }
                    practice.config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals("i_vs_I", selected)
                    click("Hide daily activity")
                    assertFalse(nodes().any { it.text() == "0-day streak" })
                    click("Hide more listening details")
                    val directory = File("build/reports/progress").apply { mkdirs() }
                    scene.render(++frame * 16_666_667L).use { image ->
                        image.encodeToData()!!.use { data -> File(directory, "progress.png").writeBytes(data.bytes) }
                    }
                    component.state.value = summarizeProgress((1..40).map { index ->
                        TrialRecordEntity(
                            id = index.toLong(), contrastId = "i_vs_I", targetWord = "sheep", selectedWord = "sheep",
                            isCorrect = true, latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = index * 1000L
                        )
                    }, emptyList())
                    settle()
                    click("Show more listening details")
                    click("Show accuracy trends")
                    click("Show practice history")
                    assertTrue(nodes().any { it.text() == "Unchanged from the previous 20 answers." })
                    assertTrue(nodes().any { it.text() == "No reviews due" })
                    assertEquals(5, nodes().count { it.text() == "Correct · sheep" })
                    nodes().first { it.config.contains(SemanticsActions.OnClick) && it.text() == "Browse all 40 answers" }
                        .config[SemanticsActions.OnClick].action!!.invoke()
                    settle()
                    assertEquals(20, nodes().count { it.text() == "Correct · sheep" })
                    click("Hide practice history")
                    assertEquals(0, nodes().count { it.text() == "Correct · sheep" })
                    click("Show practice history")
                    assertEquals(20, nodes().count { it.text() == "Correct · sheep" })
                    nodes().first { it.config.contains(SemanticsActions.OnClick) && it.text() == "Show fewer answers" }
                        .config[SemanticsActions.OnClick].action!!.invoke()
                    settle()
                    assertEquals(5, nodes().count { it.text() == "Correct · sheep" })
                    click("Hide practice history")
                    assertEquals(0, nodes().count { it.text() == "Correct · sheep" })
                    assertTrue(nodes().any { it.text() == "Unchanged from the previous 20 answers." })
                    val contrasts = ListeningContrastsCatalog.defaultContrasts.take(5)
                    component.state.value = summarizeProgress(contrasts.map { contrast ->
                        val pair = contrast.pairs.first()
                        TrialRecordEntity(contrastId = contrast.id, targetWord = pair.word1, selectedWord = pair.word1,
                            isCorrect = true, latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = 1000)
                    }, emptyList())
                    settle()
                    fun practiceButtons() = nodes().filter { it.config.contains(SemanticsActions.OnClick) && it.text().startsWith("Practice ") }
                    assertEquals(3, practiceButtons().size)
                    click("Show all 5 contrasts")
                    assertEquals(5, practiceButtons().size)
                    click("Show fewer contrasts")
                    assertEquals(3, practiceButtons().size)
                    val firstRecording = listOf(
                        SpeakingRecordEntity(-1, "i_vs_I", "sheep", "iː", 88f, 200, 1000)
                    )
                    component.state.value = ProgressState(loading = false, nowEpochMs = 2000,
                        speaking = firstRecording, speakingSummaries = summarizeSpeaking(firstRecording))
                    settle()
                    assertTrue(nodes().any { it.text() == "No listening answers yet." })
                    assertFalse(nodes().any { it.text() == "Your progress starts here" })
                    click("Show speaking progress")
                    assertTrue(nodes().any { it.text() == "Score: 88/100" })
                    assertFalse(nodes().any { it.text() == "Start speaking" })
                    assertTrue(nodes().any { it.text() == "Recent average: 88/100" })
                    assertTrue(nodes().any { it.text() == "Record this sound 9 more times to compare two sets of five." })
                    assertFalse(nodes().any { it.text() == "0% accuracy" })
                    click("Practice speaking again")
                    assertEquals("i_vs_I", selected)
                    component.state.value = component.state.value.copy(
                        canRetryLoad = true, error = "Couldn't refresh progress. Showing the last loaded totals.")
                    settle()
                    assertTrue(nodes().any { it.text() == "Retry loading" })
                    assertTrue(nodes().any { it.text() == "Score: 88/100" })
                    assertTrue(nodes().any { it.text() == "Recent average: 88/100" })
                    component.state.value = component.state.value.copy(canRetryLoad = false, error = null)
                    settle()
                    scene.render(++frame * 16_666_667L).use { image ->
                        image.encodeToData()!!.use { data -> File(directory, "speaking-progress.png").writeBytes(data.bytes) }
                    }
                    val speaking = (1..10).map { index ->
                        SpeakingRecordEntity(index.toLong(), "i_vs_I", "sheep", "iː", if (index <= 5) 40f else 80f, 200, index * 1000L)
                    } + listOf(
                        SpeakingRecordEntity(11, "i_vs_I", "ship", "ɪ", 50f, 200, 11000),
                        SpeakingRecordEntity(12, "ae_vs_e", "bad", "æ", 60f, 200, 12000),
                        SpeakingRecordEntity(13, "ae_vs_e", "bed", "e", 70f, 200, 13000)
                    )
                    component.state.value = component.state.value.copy(speaking = speaking, speakingSummaries = summarizeSpeaking(speaking))
                    settle()
                    assertFalse(nodes().any { it.text() == "Up 40 points from the previous five recordings." })
                    click("Show all 4 sounds")
                    assertTrue(nodes().any { it.text() == "Up 40 points from the previous five recordings." })
                    click("Show latest 13 recordings")
                    click("Hide speaking progress")
                    assertFalse(nodes().any { it.text() == "Up 40 points from the previous five recordings." })
                    click("Show speaking progress")
                    assertTrue(nodes().any { it.text() == "Up 40 points from the previous five recordings." })
                    assertTrue(nodes().any { it.text() == "Show fewer recordings" })
                    click("Practice /æ/ · bad")
                    assertEquals("ae_vs_e", selected)
                    assertEquals("æ", selectedPhoneme)
                    click("Practice /ɪ/ · ship")
                    assertEquals("i_vs_I", selected)
                    assertEquals("ɪ", selectedPhoneme)
                    click("Show fewer sounds")
                    assertFalse(nodes().any { it.text() == "Up 40 points from the previous five recordings." })
                    val consonantRecords = listOf(SpeakingRecordEntity(14, "p_vs_b", "bear", "b", 72f, 800, 14000, scoringMethod = "neural_ctc_phoneme"))
                    component.state.value = component.state.value.copy(
                        speaking = consonantRecords, speakingSummaries = summarizeSpeaking(consonantRecords))
                    settle()
                    assertTrue(nodes().any { it.text() == "Selected-phoneme confidence · experimental" })
                    click("Practice /b/ · bear")
                    assertEquals("p_vs_b", selected)
                    assertEquals("b", selectedPhoneme)
                    click("Practice speaking again")
                    assertEquals("p_vs_b", selected)
                    val modules = listOf(
                        ModulePracticeRecordEntity(1, "STORIES", "sheep_vs_ship", "Story practice", "i_vs_I", 80f, true, 200, 1000),
                        ModulePracticeRecordEntity(2, "RHYTHM", "ship_or_sheep_question", "Rhythm practice", null, 70f, true, 200, 2000))
                    component.state.value = ProgressState(loading = false, hasLoadedProgress = true,
                        modules = modules, moduleSummaries = summarizeModules(modules))
                    settle()
                    assertTrue(nodes().any { it.text() == "No listening answers yet." })
                    assertFalse(nodes().any { it.text() == "Your progress starts here" })
                    click("Show stories & rhythm")
                    click("Show stories progress")
                    click("Practice Story practice")
                    assertEquals("sheep_vs_ship", selectedStory)
                    click("Show rhythm progress")
                    click("Practice Rhythm practice")
                    assertEquals("ship_or_sheep_question", selectedRhythm)
                } finally { scene.close() }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
