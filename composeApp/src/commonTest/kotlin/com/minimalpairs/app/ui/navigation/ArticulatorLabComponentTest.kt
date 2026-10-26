package com.minimalpairs.app.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.hal.AudioRecorder
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ArticulatorLabComponentTest {
    @Test
    fun recordsSelectedWordAndLocksTargetUntilReviewCompletes() = runTest {
        val f = Fixture(this)
        try {
            for (first in listOf(true, false)) {
                f.component.switchTargetPhoneme(first)
                f.component.toggleRecording()
                runCurrent()
                f.component.switchTargetPhoneme(!first)
                assertEquals(first, f.state.isTargetPhoneme1)
                f.recorder.emit(f.samples)
                runCurrent()
                assertEquals(200, f.state.liveDurationMs)
                f.component.toggleRecording()
                runCurrent()
                val pair = f.state.contrast.pairs.first()
                assertEquals(if (first) pair.word1 else pair.word2, f.scorer.words.last())
                assertEquals(if (first) f.state.contrast.phoneme1 else f.state.contrast.phoneme2, f.scorer.phonemes.last())
                assertEquals(88f, f.state.overallGopScore)
                assertNotNull(f.state.feedback)
                assertTrue(f.state.hasRecording)
                f.component.playRecording()
                runCurrent()
                assertContentEquals(f.samples, f.player.samples.last())
            }
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun silenceIsNotScoredAndNewAttemptsClearPreviousResults() = runTest {
        val f = Fixture(this)
        try {
            f.record(f.samples)
            assertNotNull(f.state.overallGopScore)
            f.component.toggleRecording()
            runCurrent()
            assertNull(f.state.overallGopScore)
            assertNull(f.state.feedback)
            assertFalse(f.state.hasRecording)
            f.recorder.emit(ShortArray(3200))
            runCurrent()
            f.component.toggleRecording()
            runCurrent()
            assertEquals(1, f.scorer.words.size)
            assertNotNull(f.state.error)
            assertNull(f.state.overallGopScore)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun generatedExamplePlaysWithoutReplacingUserScore() = runTest {
        val f = Fixture(this)
        try {
            f.record(f.samples)
            val score = f.state.overallGopScore
            f.component.testSynthesizedVowel(true)
            runCurrent()
            assertTrue(f.player.samples.single().isNotEmpty())
            assertEquals(1, f.scorer.words.size)
            assertEquals(score, f.state.overallGopScore)
            assertTrue(f.state.hasRecording)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun scoringCannotOverlapAnotherRecordingAndIsCancelledOnExit() = runTest {
        val f = Fixture(this)
        try {
            f.scorer.gate = CompletableDeferred()
            f.record(f.samples)
            assertTrue(f.state.isScoring)
            f.component.toggleRecording()
            f.component.testSynthesizedVowel(false)
            runCurrent()
            assertEquals(1, f.recorder.starts)
            assertTrue(f.player.samples.isEmpty())
            f.component.onBack()
            runCurrent()
            f.scorer.gate!!.complete(Unit)
            runCurrent()
            assertNull(f.state.overallGopScore)
            assertEquals(AudioRecorderState.UNINITIALIZED, f.recorder.state.value)
            assertFalse(f.state.isBusy)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun retryPreparationAndIgnoreBuffersAfterLifecycleStop() = runTest {
        val f = Fixture(this)
        try {
            f.recorder.prepareSucceeds = false
            f.component.toggleRecording()
            runCurrent()
            assertNotNull(f.state.error)
            f.recorder.prepareSucceeds = true
            f.component.toggleRecording()
            runCurrent()
            assertEquals(AudioRecorderState.RECORDING, f.state.recorderState)
            f.lifecycle.stop()
            runCurrent()
            f.recorder.emit(f.samples)
            runCurrent()
            assertEquals(0, f.state.liveDurationMs)
            assertEquals(AudioRecorderState.UNINITIALIZED, f.recorder.state.value)
            assertTrue(f.player.stops > 0)
            f.lifecycle.resume()
            f.component.toggleRecording()
            runCurrent()
            assertEquals(AudioRecorderState.RECORDING, f.state.recorderState)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun scorerFailureIsRecoverable() = runTest {
        val f = Fixture(this)
        try {
            f.scorer.fail = true
            f.record(f.samples)
            assertNotNull(f.state.error)
            assertFalse(f.state.isBusy)
            f.scorer.fail = false
            f.record(f.samples)
            assertEquals(88f, f.state.overallGopScore)
            assertNull(f.state.error)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun recordingStopsAtThirtySecondsAndIgnoresLateBuffers() = runTest {
        val f = Fixture(this)
        try {
            f.component.toggleRecording()
            runCurrent()
            f.recorder.emit(ShortArray(16000 * 31))
            runCurrent()
            assertEquals(AudioRecorderState.READY, f.state.recorderState)
            assertEquals(30000, f.state.liveDurationMs)
            assertNotNull(f.state.error)
            f.recorder.emit(f.samples)
            runCurrent()
            f.component.playRecording()
            runCurrent()
            assertEquals(16000 * 30, f.player.samples.single().size)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun targetChangeClearsReviewAndDuplicateStartsAreIgnored() = runTest {
        val f = Fixture(this)
        try {
            f.component.toggleRecording()
            f.component.toggleRecording()
            runCurrent()
            assertEquals(1, f.recorder.starts)
            f.recorder.emit(f.samples)
            runCurrent()
            f.component.toggleRecording()
            runCurrent()
            assertNotNull(f.state.overallGopScore)
            f.component.switchTargetPhoneme(false)
            assertNull(f.state.overallGopScore)
            assertNull(f.state.feedback)
            assertFalse(f.state.hasRecording)
            assertEquals(0f, f.state.liveF1)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    private class Fixture(val scope: TestScope) {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val recorder = FakeRecorder()
        val player = FakePlayer()
        val scorer = FakeScorer()
        val samples = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 200)
        val component = DefaultArticulatorLabComponent(DefaultComponentContext(lifecycle), null, scorer, {},
            recorder, player, StandardTestDispatcher(scope.testScheduler), StandardTestDispatcher(scope.testScheduler))
        val state get() = component.state.value
        fun record(samples: ShortArray) {
            component.toggleRecording()
            scope.runCurrent()
            recorder.emit(samples)
            scope.runCurrent()
            component.toggleRecording()
            scope.runCurrent()
        }
    }

    private class FakeScorer : PhonemeScorerApi {
        val words = mutableListOf<String>()
        val phonemes = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        override suspend fun scoreAudio(audioSamples: ShortArray, targetWord: String, expectedIpa: String,
            contrastId: String?): PhonemeScoringResponse {
            words += targetWord
            phonemes += expectedIpa
            gate?.await()
            check(!fail)
            return PhonemeScoringResponse(overallScore = 88f, phonemes = emptyList())
        }
    }

    private class FakePlayer : AudioPlayer {
        val samples = mutableListOf<ShortArray>()
        var stops = 0
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) {
            this.samples += samples.copyOf()
            onComplete()
        }
        override suspend fun stop() { stops++ }
        override fun isPlaying() = false
    }

    private class FakeRecorder : AudioRecorder {
        override val state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
        var prepareSucceeds = true
        var starts = 0
        private var callback: ((ShortArray) -> Unit)? = null
        fun emit(samples: ShortArray) { callback?.invoke(samples) }
        override suspend fun prepare(): Boolean {
            state.value = if (prepareSucceeds) AudioRecorderState.READY else AudioRecorderState.ERROR
            return prepareSucceeds
        }
        override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean {
            starts++
            callback = onBuffer
            state.value = AudioRecorderState.RECORDING
            return true
        }
        override suspend fun stop(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun release() { state.value = AudioRecorderState.UNINITIALIZED }
        override suspend fun pause() = false
        override suspend fun resume() = false
    }
}
