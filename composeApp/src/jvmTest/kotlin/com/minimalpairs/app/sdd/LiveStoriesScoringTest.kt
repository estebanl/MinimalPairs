package com.minimalpairs.app.sdd

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.use
import androidx.room3.Room
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.*
import com.minimalpairs.app.core.dsp.WavCodec
import com.minimalpairs.app.core.hal.*
import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.data.audio.ElevenLabsAudioRepository
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.local.AppDatabase
import com.minimalpairs.app.data.local.createDatabase
import com.minimalpairs.app.data.progress.*
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import com.minimalpairs.app.data.remote.defaultPhonemeScorerBaseUrl
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import com.minimalpairs.app.ui.navigation.DefaultMeaningTrapComponent
import com.minimalpairs.app.ui.screens.MeaningTrapScreen
import com.minimalpairs.app.ui.screens.ModuleProgressCard
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Real Stories composable, HTTP scorer, outcome audio and isolated Room storage.
 * Only recorder PCM and speaker output are controlled; scores are never mocked.
 */
@OptIn(ExperimentalComposeUiApi::class)
class LiveStoriesScoringTest {
    @Test
    fun allCatalogStoriesScoreRenderPlayAndPersistAgainstTheConfiguredService() = runBlocking {
        assumeTrue("Set MINIMAL_PAIRS_LIVE_STORIES=1 to run live Stories verification",
            System.getenv("MINIMAL_PAIRS_LIVE_STORIES") == "1")
        val expectedUrl = System.getenv("MINIMAL_PAIRS_LIVE_SCORER_EXPECTED_URL") ?: "http://127.0.0.1:8000"
        assertEquals(expectedUrl, defaultPhonemeScorerBaseUrl())
        withContext(Dispatchers.Main) {
            val output = File(System.getenv("MINIMAL_PAIRS_LIVE_STORIES_REPORT_DIR") ?: "build/reports/stories-live").apply { mkdirs() }
            File(output, "endpoint.txt").writeText(expectedUrl)
            val databaseFile = File(output, "progress-${System.currentTimeMillis()}.db")
            fun open() = createDatabase(Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath))
            var database = open()
            val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val repository = ProgressRepository(RoomProgressStore(database), progressScope)
            val lifecycle = LifecycleRegistry().apply { resume() }
            val tracker = PhonemeConfusionTracker()
            val recorder = ControlledRecorder()
            val player = ControlledPlayer()
            val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), tracker,
                PhonemeScorerClient(), {}, recorder, player, progressRepository = repository)
            var expectedRecords = 0
            val timings = linkedMapOf<String, Long>()
            val evaluations = mutableListOf<JsonObject>()
            val rejectedReplies = mutableListOf<JsonObject>()
            val controls = System.getenv("MINIMAL_PAIRS_STORY_CONTROLS_DIR")?.let(::File)
            try {
                eventually { !repository.state.value.loading }
                ImageComposeScene(430, 1500) { MinimalPairsTheme { MeaningTrapScreen(component) } }.use { scene ->
                    suspend fun record(samples: ShortArray) {
                        scene.click("Record your reply")
                        eventually { component.state.value.isRecording }
                        recorder.emit(samples)
                        delay(50)
                        scene.click("Stop and review")
                        eventually { !component.state.value.isScoring &&
                            (component.state.value.gopScore != null || component.state.value.feedbackIsError) }
                    }
                    suspend fun verify(storyId: String, samples: ShortArray, success: Boolean) {
                        val story = MeaningTrapCatalog.getStoryById(storyId)!!
                        scene.click("${story.intendedWord} vs ${story.trapWord}")
                        eventually { component.state.value.activeStory.id == storyId && !component.state.value.isPlayingAudio }
                        val reference = assertNotNull(ElevenLabsAudioRepository.loadMeaningTrapPhrasePcm(storyId))
                        scene.click("Hear phrase")
                        eventually { player.played.lastOrNull()?.contentEquals(reference) == true }
                        val started = System.nanoTime()
                        record(samples)
                        val state = component.state.value
                        assertFalse(state.feedbackIsError, "$storyId: ${state.feedbackText}")
                        assertFalse(state.isSimulated)
                        assertEquals(if (success) MeaningTrapBranch.INTENDED_SUCCESS else MeaningTrapBranch.TRAP_TRIGGERED,
                            state.branch, "$storyId: ${state.feedbackText}")
                        val intended = assertNotNull(state.gopScore)
                        val alternate = assertNotNull(state.alternateScore)
                        assertTrue(if (success) intended >= 80f && intended - alternate >= 15f
                            else alternate >= 80f && alternate - intended >= 15f)
                        val label = "$storyId-${if (success) "intended" else "trap"}"
                        timings[label] = (System.nanoTime() - started) / 1_000_000
                        evaluations += buildJsonObject {
                            put("story_id", storyId)
                            put("branch", state.branch.name)
                            put("intended_score", intended)
                            put("alternate_score", alternate)
                            put("scoring_time_ms", timings.getValue(label))
                        }
                        val outcome = assertNotNull(ElevenLabsAudioRepository.loadMeaningTrapOutcomePcm(storyId, success))
                        eventually { player.played.lastOrNull()?.contentEquals(outcome) == true }
                        repeat(15) { scene.render(System.nanoTime()).close(); delay(25) }
                        assertTrue(scene.nodes().any { it.text() == "Sound confidence · ${story.intendedWord}: ${intended.toInt()}/100" })
                        assertTrue(scene.nodes().any { it.text() == "Sound confidence · ${story.trapWord}: ${alternate.toInt()}/100" })
                        assertTrue(scene.nodes().any { it.text().contains("not pronunciation accuracy") })
                        expectedRecords++
                        eventually { repository.state.value.modules.size == expectedRecords && repository.state.value.pendingAnswers == 0 }
                        val saved = repository.state.value.modules.first()
                        assertEquals(storyId, saved.targetId)
                        assertEquals(success, saved.succeeded)
                        assertEquals(intended, saved.score)
                        assertTrue(state.hasRecording)
                        scene.click("Replay your reply")
                        eventually { player.played.lastOrNull()?.contentEquals(samples) == true }
                        assertEquals(state.feedbackText, component.state.value.feedbackText)
                        assertEquals(intended, component.state.value.gopScore)
                        assertEquals(alternate, component.state.value.alternateScore)
                        assertEquals(expectedRecords, repository.state.value.modules.size)
                        scene.capture(output, label)
                        println("STORIES_LIVE $label intended=$intended alternate=$alternate saved=true outcome_audio=true reply_replay=true")
                    }
                    for (story in MeaningTrapCatalog.stories) {
                        verify(story.id, assertNotNull(ElevenLabsAudioRepository.loadMeaningTrapPhrasePcm(story.id)), true)
                    }
                    if (controls != null) {
                        for (story in MeaningTrapCatalog.stories) {
                            val voice = if (story.id in setOf("pen_vs_pan", "pot_vs_port")) "professor_smith" else "tutor"
                            val fixture = File(controls, "$voice/${story.id}_trap.wav")
                            assertTrue(fixture.isFile, "Missing generated control: $fixture")
                            verify(story.id, WavCodec.decodeWavToPcm(fixture.readBytes()), false)
                        }
                        val negativeControls = listOf(
                            "heart_vs_hut" to "tutor/heart_vs_hut_outside.wav",
                            "pot_vs_port" to "professor_smith/pot_vs_port_outside.wav",
                            "pot_vs_port" to "professor_smith/pot_vs_port_unrelated.wav"
                        )
                        for ((storyId, path) in negativeControls) {
                            val story = MeaningTrapCatalog.getStoryById(storyId)!!
                            scene.click("${story.intendedWord} vs ${story.trapWord}")
                            eventually { !component.state.value.isPlayingAudio }
                            val confusionsBefore = tracker.getAllConfusions()
                            val rejectedAudio = WavCodec.decodeWavToPcm(File(controls, path).readBytes())
                            record(rejectedAudio)
                            assertEquals(MeaningTrapBranch.PENDING, component.state.value.branch)
                            assertTrue(component.state.value.feedbackIsError)
                            assertNull(component.state.value.gopScore)
                            assertEquals(expectedRecords, repository.state.value.modules.size)
                            assertEquals(confusionsBefore, tracker.getAllConfusions())
                            val feedback = component.state.value.feedbackText
                            assertTrue(component.state.value.hasRecording)
                            scene.click("Replay your reply")
                            eventually { player.played.lastOrNull()?.contentEquals(rejectedAudio) == true }
                            assertEquals(feedback, component.state.value.feedbackText)
                            assertEquals(expectedRecords, repository.state.value.modules.size)
                            rejectedReplies += buildJsonObject {
                                put("story_id", storyId)
                                put("control", path)
                                put("branch", component.state.value.branch.name)
                                put("feedback", component.state.value.feedbackText)
                                put("saved_results", repository.state.value.modules.size)
                            }
                            scene.capture(output, File(path).nameWithoutExtension + "-retry")
                        }
                    }
                    record(ShortArray(16000))
                    assertTrue(component.state.value.feedbackText.contains("No clear voice"))
                    assertEquals(MeaningTrapBranch.PENDING, component.state.value.branch)
                    assertEquals(expectedRecords, repository.state.value.modules.size)
                    scene.capture(output, "silence-retry")
                    val confusions = tracker.getAllConfusions()
                    val weights = tracker.getAllWeights()
                    scene.click("+ Explore example endings")
                    scene.click("Other sound (/${component.state.value.activeStory.trapIpa}/)")
                    assertTrue(component.state.value.isSimulated)
                    assertNull(component.state.value.gopScore)
                    assertEquals(expectedRecords, repository.state.value.modules.size)
                    assertEquals(confusions, tracker.getAllConfusions())
                    assertEquals(weights, tracker.getAllWeights())
                }
                lifecycle.destroy()
                assertFalse(component.state.value.hasRecording)
                progressScope.coroutineContext[Job]!!.cancelAndJoin()
                database.close()
                database = open()
                val saved = database.progressDao().readModules()
                assertEquals(expectedRecords, saved.size)
                assertEquals(MeaningTrapCatalog.stories.map { it.id }.toSet(), saved.map { it.targetId }.toSet())
                assertTrue(saved.all { it.module == "STORIES" })
                if (controls != null) assertTrue(saved.groupBy { it.targetId }.values.all { records -> records.map { it.succeeded }.toSet() == setOf(true, false) })
                File(output, "saved-scores.json").writeText(Json.encodeToString(saved))
                File(output, "scoring-times-ms.json").writeText(Json.encodeToString(timings))
                File(output, "evaluations.json").writeText(Json.encodeToString(evaluations))
                File(output, "negative-controls.json").writeText(Json.encodeToString(rejectedReplies))
                var practiceTarget: String? = null
                ImageComposeScene(430, 1500) {
                    MinimalPairsTheme { ModuleProgressCard("Stories", saved, summarizeModules(saved), System.currentTimeMillis(), onPractice = { practiceTarget = it }) }
                }.use { scene ->
                    scene.click("Show all ${MeaningTrapCatalog.stories.size} activities")
                    for (story in MeaningTrapCatalog.stories) {
                        scene.click("Practice ${story.title}")
                        assertEquals(story.id, practiceTarget)
                    }
                    scene.capture(output, "persisted-progress")
                }
            } finally {
                lifecycle.destroy()
                progressScope.cancel()
                database.close()
            }
        }
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        withTimeout(150_000) { while (!predicate()) delay(25) }
    }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
    private suspend fun ImageComposeScene.click(label: String) {
        var horizontalStep = 250f
        var previousOffset = Float.NaN
        withTimeout(5_000) {
            while (true) {
                render(System.nanoTime()).close()
                val node = nodes().firstOrNull { it.text() == label && it.config.getOrNull(SemanticsActions.OnClick) != null &&
                    !it.config.contains(SemanticsProperties.Disabled) }
                if (node != null) {
                    assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke(), label)
                    break
                }
                val storyIndex = MeaningTrapCatalog.stories.indexOfFirst { "${it.intendedWord} vs ${it.trapWord}" == label }
                if (storyIndex >= 0) {
                    nodes().firstOrNull { it.config.getOrNull(SemanticsActions.ScrollToIndex) != null }
                        ?.config?.getOrNull(SemanticsActions.ScrollToIndex)?.action?.invoke(storyIndex)
                    delay(100)
                    continue
                }
                // Lazy story chips may need horizontal scrolling to be composed.
                val row = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null }
                val offset = row?.config?.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.value?.invoke()
                if (offset != null) {
                    if (offset == previousOffset) horizontalStep = -horizontalStep
                    previousOffset = offset
                    row.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(horizontalStep, 0f)
                }
                delay(25)
            }
        }
        delay(100)
        render(System.nanoTime()).close()
    }
    private fun ImageComposeScene.capture(output: File, name: String) {
        render(System.nanoTime()).use { image ->
            image.encodeToData()!!.use { File(output, "$name.png").writeBytes(it.bytes) }
        }
    }
    private class ControlledRecorder : AudioRecorder {
        override val state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
        private var callback: ((ShortArray) -> Unit)? = null
        fun emit(samples: ShortArray) { callback!!(samples) }
        override suspend fun prepare(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean { callback = onBuffer; state.value = AudioRecorderState.RECORDING; return true }
        override suspend fun stop(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun release() { state.value = AudioRecorderState.UNINITIALIZED }
        override suspend fun pause() = false
        override suspend fun resume() = false
    }
    private class ControlledPlayer : AudioPlayer {
        val played = mutableListOf<ShortArray>()
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) { played += samples.copyOf(); onComplete() }
        override suspend fun stop() {}
        override fun isPlaying() = false
    }
}
