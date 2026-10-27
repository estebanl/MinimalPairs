package com.minimalpairs.app.sdd

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import androidx.room3.Room
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.hal.AudioRecorder
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.data.audio.ElevenLabsAudioRepository
import com.minimalpairs.app.data.local.AppDatabase
import com.minimalpairs.app.data.local.createDatabase
import com.minimalpairs.app.data.progress.ProgressRepository
import com.minimalpairs.app.data.progress.RoomProgressStore
import com.minimalpairs.app.data.progress.summarizeSpeaking
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import com.minimalpairs.app.data.remote.defaultPhonemeScorerBaseUrl
import com.minimalpairs.app.ui.navigation.DefaultArticulatorLabComponent
import com.minimalpairs.app.ui.screens.ArticulatorLabScreen
import com.minimalpairs.app.ui.screens.SpeakingProgressCard
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Opt-in integration test: real Speak UI, HTTP model service, and Room persistence.
 * Only microphone PCM and speaker playback are controlled; no scores are mocked.
 */
@OptIn(ExperimentalComposeUiApi::class)
class LiveSpeakScoringTest {
    @Test
    fun speaksScoresAndRestoresInitialMedialAndFinalConsonants() = runBlocking {
        assumeTrue("Start the local service and set MINIMAL_PAIRS_LIVE_SCORING=1",
            System.getenv("MINIMAL_PAIRS_LIVE_SCORING") == "1")
        val expectedUrl = System.getenv("MINIMAL_PAIRS_LIVE_SCORER_EXPECTED_URL") ?: "http://127.0.0.1:8000"
        assertEquals(expectedUrl, defaultPhonemeScorerBaseUrl())
        withContext(Dispatchers.Main) {
            val output = File(System.getenv("MINIMAL_PAIRS_LIVE_REPORT_DIR") ?: "build/reports/articulator-live").apply { mkdirs() }
            File(output, "endpoint.txt").writeText(expectedUrl)
            val databaseFile = File(output, "progress-${System.currentTimeMillis()}.db")
            fun open() = createDatabase(Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath))
            var database = open()
            val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val repository = ProgressRepository(RoomProgressStore(database), progressScope)
            val lifecycle = LifecycleRegistry().apply { resume() }
            val recorder = ControlledRecorder()
            val player = ControlledPlayer()
            val component = DefaultArticulatorLabComponent(DefaultComponentContext(lifecycle), null,
                PhonemeScorerClient(), {}, recorder = recorder, audioPlayer = player,
                progressRepository = repository)
            try {
                eventually { !repository.state.value.loading }
                ImageComposeScene(430, 1000) { MinimalPairsTheme { ArticulatorLabScreen(component) } }.use { scene ->
                    scene.render(System.nanoTime()).close()
                    val cases = listOf(
                        Case("pear / bear", "pear", "p"),
                        Case("shoe / measure", "measure", "ʒ"),
                        Case("pear / bear", "rope", "p", pairIndex = 3)
                    )
                    val scoringTimesMs = linkedMapOf<String, Long>()
                    for ((index, case) in cases.withIndex()) {
                        scene.click("Change sound pair")
                        scene.render(System.nanoTime()).close()
                        scene.click(case.menu)
                        // The UI uses the first reference pair; exercise the component's
                        // supported alternate-word selection for a final consonant too.
                        if (case.pairIndex != 0) component.selectExamplePair(case.pairIndex)
                        scene.render(System.nanoTime()).close()
                        scene.click("${case.word} /${case.phoneme}/")
                        scene.render(System.nanoTime()).close()
                        val example = assertNotNull(ElevenLabsAudioRepository.loadWordPcm(case.word))
                        val retainedMic = System.getenv("MINIMAL_PAIRS_MIC_REPLAY_WAV")
                        val expected = if (case.word == "pear" && retainedMic != null)
                            com.minimalpairs.app.core.dsp.WavCodec.decodeWavToPcm(File(retainedMic).readBytes())
                        else example
                        scene.click("Example")
                        eventually { !component.state.value.isBusy && player.played.isNotEmpty() }
                        assertContentEquals(example, player.played.last())
                        scene.click("Record your sound")
                        eventually { component.state.value.recorderState == AudioRecorderState.RECORDING && !component.state.value.isBusy }
                        recorder.emit(expected)
                        eventually { component.state.value.liveDurationMs > 0 }
                        scene.render(System.nanoTime()).close()
                        val scoringStarted = System.nanoTime()
                        scene.click("Stop recording")
                        eventually {
                            val state = component.state.value
                            !state.isBusy && (state.overallGopScore != null || state.error != null)
                        }
                        val state = component.state.value
                        assertNull(state.error, "${case.word}: ${state.error}")
                        val score = assertNotNull(state.overallGopScore)
                        scoringTimesMs[case.word] = (System.nanoTime() - scoringStarted) / 1_000_000
                        assertTrue(score in 0f..100f)
                        assertTrue(state.scoringDescription!!.contains("Selected-phoneme model confidence"))
                        assertTrue(state.feedback!!.contains("/${case.phoneme}/"))
                        scene.render(System.nanoTime()).close()
                        assertTrue(scene.nodes().any { it.text() == "Selected sound /${case.phoneme}/ · ${score.toInt()}/100" })
                        assertTrue(scene.nodes().any { it.matches("Play my recording") })
                        scene.click("Play my recording")
                        eventually { !component.state.value.isBusy }
                        assertContentEquals(expected, player.played.last())
                        eventually { repository.state.value.speaking.size == index + 1 && repository.state.value.pendingAnswers == 0 }
                        val record = repository.state.value.speaking.first()
                        assertEquals(case.word, record.targetWord)
                        assertEquals(case.phoneme, record.targetPhoneme)
                        assertEquals(score, record.score)
                        assertEquals("neural_ctc_phoneme", record.scoringMethod)
                        scene.nodes().first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                            .config[SemanticsActions.ScrollBy].action!!.invoke(0f, 1000f)
                        repeat(12) { scene.render(System.nanoTime()).close(); delay(25) }
                        scene.render(System.nanoTime()).use { File(output, "${case.word}-score.png").writeBytes(it.encodeToData()!!.bytes) }
                    }
                    File(output, "scoring-times-ms.json").writeText(Json.encodeToString(scoringTimesMs))
                    // Real HTTP quality rejection must clear the old grade, keep
                    // replay available, and leave Progress untouched.
                    val random = kotlin.random.Random(42)
                    val noisy = ShortArray(16000) { random.nextInt(-1000, 1001).toShort() }
                    component.toggleRecording()
                    eventually { component.state.value.recorderState == AudioRecorderState.RECORDING && !component.state.value.isBusy }
                    recorder.emit(noisy)
                    eventually { component.state.value.liveDurationMs > 0 }
                    component.toggleRecording()
                    eventually { !component.state.value.isBusy && component.state.value.error != null }
                    assertTrue(component.state.value.error!!.contains("Background noise"))
                    assertNull(component.state.value.overallGopScore)
                    scene.render(System.nanoTime()).use { File(output, "noise-retry.png").writeBytes(it.encodeToData()!!.bytes) }
                    component.playRecording()
                    eventually { !component.state.value.isBusy }
                    assertContentEquals(noisy, player.played.last())
                    assertEquals(3, repository.state.value.speaking.size)
                }
                lifecycle.destroy()
                progressScope.coroutineContext[Job]!!.cancelAndJoin()
                database.close()
                database = open()
                val saved = database.progressDao().readSpeaking()
                assertEquals(3, saved.size)
                assertEquals(setOf("pear", "measure", "rope"), saved.map { it.targetWord }.toSet())
                assertTrue(saved.all { it.scoringMethod == "neural_ctc_phoneme" })
                File(output, "saved-scores.json").writeText(Json.encodeToString(saved))
                var target: Pair<String, String>? = null
                ImageComposeScene(430, 1400) {
                    MinimalPairsTheme {
                        SpeakingProgressCard(saved, summarizeSpeaking(saved), System.currentTimeMillis(), {},
                            { contrast, phoneme -> target = contrast to phoneme })
                    }
                }.use { scene ->
                    scene.render(System.nanoTime()).close()
                    assertTrue(scene.nodes().any { it.text() == "Selected-phoneme confidence · experimental" })
                    scene.click("Practice /ʒ/ · measure")
                    assertEquals("sh_vs_zh" to "ʒ", target)
                    scene.render(System.nanoTime()).use { File(output, "persisted-progress.png").writeBytes(it.encodeToData()!!.bytes) }
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

    private data class Case(val menu: String, val word: String, val phoneme: String, val pairIndex: Int = 0)

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    private fun SemanticsNode.matches(label: String) = config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
        config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
    private suspend fun ImageComposeScene.click(label: String) {
        try {
            withTimeout(5_000) {
                while (true) {
                    render(System.nanoTime()).close()
                    if (nodes().any { it.matches(label) && it.config.getOrNull(SemanticsActions.OnClick) != null }) break
                    delay(25)
                }
            }
        } catch (error: TimeoutCancellationException) {
            fail("Missing clickable $label; visible: ${nodes().mapNotNull { it.text() }}")
        }
        val node = nodes().first { it.matches(label) && it.config.getOrNull(SemanticsActions.OnClick) != null }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke(), label)
        delay(100)
        render(System.nanoTime()).close()
    }
    private class ControlledRecorder : AudioRecorder {
        override val state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
        private var callback: ((ShortArray) -> Unit)? = null
        fun emit(samples: ShortArray) { callback!!(samples) }
        override suspend fun prepare(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean {
            callback = onBuffer; state.value = AudioRecorderState.RECORDING; return true
        }
        override suspend fun stop(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun release() { state.value = AudioRecorderState.UNINITIALIZED }
        override suspend fun pause() = false
        override suspend fun resume() = false
    }
    private class ControlledPlayer : AudioPlayer {
        val played = mutableListOf<ShortArray>()
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) { played += samples.copyOf(); onComplete() }
        override suspend fun stop() = Unit
        override fun isPlaying() = false
    }
}
