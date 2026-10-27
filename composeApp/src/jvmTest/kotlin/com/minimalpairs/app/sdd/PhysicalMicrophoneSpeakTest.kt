package com.minimalpairs.app.sdd

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.dsp.WavCodec
import com.minimalpairs.app.core.hal.AudioRecorder
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.data.local.createDatabase
import com.minimalpairs.app.data.local.getDatabaseBuilder
import com.minimalpairs.app.core.hal.JvmAudioRecorder
import com.minimalpairs.app.core.hal.JvmAudioPlayer
import javax.sound.sampled.AudioSystem
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

/** Opt-in, operator-gated test using the actual microphone, speakers, service and app database. */
@OptIn(ExperimentalComposeUiApi::class)
class PhysicalMicrophoneSpeakTest {
    @Test
    fun recordsPhysicalMicrophonePlaysItAndPersistsSelectedConsonant() = runBlocking {
        assumeTrue("Requires an operator and MINIMAL_PAIRS_PHYSICAL_MIC=1",
            System.getenv("MINIMAL_PAIRS_PHYSICAL_MIC") == "1")
        assertEquals("http://127.0.0.1:8000", defaultPhonemeScorerBaseUrl())
        withContext(Dispatchers.Main) {
            val output = File(requireNotNull(System.getenv("MINIMAL_PAIRS_MIC_REPORT_DIR"))).apply { mkdirs() }
            fun open() = createDatabase(getDatabaseBuilder())
            var database = open()
            val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val repository = ProgressRepository(RoomProgressStore(database), progressScope)
            val lifecycle = LifecycleRegistry().apply { resume() }
            val recorder = ObservedMicrophone()
            val player = ObservedSpeakers()
            val component = DefaultArticulatorLabComponent(DefaultComponentContext(lifecycle), null,
                PhonemeScorerClient(), {}, recorder = recorder, audioPlayer = player,
                progressRepository = repository)
            try {
                eventually { !repository.state.value.loading }
                val previousIds = repository.state.value.speaking.map { it.id }.toSet()
                File(output, "devices.txt").writeText(AudioSystem.getMixerInfo().joinToString("\n") { "${it.name}: ${it.description}" })
                check(!File(output, "start").exists()) { "Use a fresh report directory; refusing a stale recording signal." }
                File(output, "ready").writeText("Waiting for start signal; no capture yet.")
                withTimeout(300_000) { while (!File(output, "start").isFile) delay(100) }
                ImageComposeScene(430, 1000) { MinimalPairsTheme { ArticulatorLabScreen(component) } }.use { scene ->
                    scene.render(System.nanoTime()).close()
                    val cases = listOf(Case("pear / bear", "pear", "p"))
                    for (case in cases) {
                        scene.click("Change sound pair")
                        scene.render(System.nanoTime()).close()
                        scene.click(case.menu)
                        // The UI uses the first reference pair; exercise the component's
                        // supported alternate-word selection for a final consonant too.
                        if (case.pairIndex != 0) component.selectExamplePair(case.pairIndex)
                        scene.render(System.nanoTime()).close()
                        scene.click("${case.word} /${case.phoneme}/")
                        scene.render(System.nanoTime()).close()
                        delay(3000)
                        if (System.getenv("MINIMAL_PAIRS_MIC_START_CUE") == "1") {
                            withContext(Dispatchers.IO) {
                                ProcessBuilder("/usr/bin/afplay", "/System/Library/Sounds/Glass.aiff").start().waitFor()
                            }
                        }
                        scene.click("Record your sound")
                        eventually { component.state.value.recorderState == AudioRecorderState.RECORDING || component.state.value.error != null }
                        assertNull(component.state.value.error, component.state.value.error)
                        eventually { !component.state.value.isBusy }
                        File(output, "recording").writeText("Recording pear for 10 seconds.")
                        delay(10_000)
                        eventually { component.state.value.liveDurationMs > 0 }
                        scene.render(System.nanoTime()).close()
                        scene.click("Stop recording")
                        // Explicit opt-in diagnostic retention, including when scoring fails.
                        if (System.getenv("MINIMAL_PAIRS_RETAIN_MIC_WAV") == "1") {
                            val pcm = synchronized(recorder.captured) { recorder.captured.toShortArray() }
                            File(output, "pear-microphone.wav").writeBytes(WavCodec.encodePcmToWav(pcm))
                        }
                        eventually {
                            val state = component.state.value
                            !state.isBusy && (state.overallGopScore != null || state.error != null)
                        }
                        File(output, "capture.txt").writeText("Microphone frames: ${recorder.captured.size}; duration: ${component.state.value.liveDurationMs} ms; error: ${component.state.value.error}")
                        val state = component.state.value
                        assertNull(state.error, "${case.word}: ${state.error}")
                        val score = assertNotNull(state.overallGopScore)
                        assertTrue(score in 0f..100f)
                        assertTrue(state.scoringDescription!!.contains("Selected-phoneme model confidence"))
                        assertTrue(state.feedback!!.contains("/${case.phoneme}/"))
                        scene.render(System.nanoTime()).close()
                        assertTrue(scene.nodes().any { it.text() == "Selected sound /${case.phoneme}/ · ${score.toInt()}/100" })
                        assertTrue(scene.nodes().any { it.matches("Play my recording") })
                        File(output, "playback-started").writeText("Playing the physical microphone capture.")
                        scene.click("Play my recording")
                        eventually { !component.state.value.isBusy }
                        assertTrue(player.observedPlaying, "Speaker output line must enter playing state")
                        val captured = synchronized(recorder.captured) { recorder.captured.toShortArray() }
                        assertTrue(captured.size >= player.played.last().size)
                        assertContentEquals(captured.copyOf(player.played.last().size), player.played.last())
                        File(output, "playback-finished").writeText("Output completed; operator must confirm audibility.")
                        eventually { repository.state.value.speaking.size == previousIds.size + 1 && repository.state.value.pendingAnswers == 0 }
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
                }
                lifecycle.destroy()
                progressScope.coroutineContext[Job]!!.cancelAndJoin()
                database.close()
                database = open()
                val saved = database.progressDao().readSpeaking().filter { it.id !in previousIds }
                assertEquals(1, saved.size)
                assertEquals("pear", saved.single().targetWord)
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
                    scene.click("Practice /p/ · pear")
                    assertEquals("p_vs_b" to "p", target)
                    scene.render(System.nanoTime()).use { File(output, "persisted-progress.png").writeBytes(it.encodeToData()!!.bytes) }
                }
                File(output, "passed").writeText("Recorded physical PCM, scored /p/, sent the exact capture to the speakers, and restored its saved Progress record.")
            } catch (error: Throwable) {
                File(output, "failed").writeText(error.stackTraceToString())
                throw error
            } finally {
                lifecycle.destroy()
                progressScope.cancel()
                database.close()
            }
        }
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        withTimeout(60_000) { while (!predicate()) delay(25) }
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
    private class ObservedMicrophone : AudioRecorder {
        private val microphone = JvmAudioRecorder(System.getenv("MINIMAL_PAIRS_MIC_DEVICE"))
        val captured = mutableListOf<Short>()
        override val state get() = microphone.state
        override suspend fun prepare() = microphone.prepare()
        override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean = microphone.start { samples ->
            synchronized(captured) { captured.addAll(samples.toList()) }
            onBuffer(samples)
        }
        override suspend fun stop() = microphone.stop()
        override suspend fun release() = microphone.release()
        override suspend fun pause() = microphone.pause()
        override suspend fun resume() = microphone.resume()
    }
    private class ObservedSpeakers : AudioPlayer {
        private val speakers = JvmAudioPlayer()
        val played = mutableListOf<ShortArray>()
        var observedPlaying = false
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) = coroutineScope {
            played += samples.copyOf()
            val observer = launch {
                while (isActive) {
                    if (speakers.isPlaying()) observedPlaying = true
                    delay(10)
                }
            }
            try { speakers.play(samples, onComplete) } finally { observer.cancelAndJoin() }
        }
        override suspend fun stop() = speakers.stop()
        override fun isPlaying() = speakers.isPlaying()
    }
}
