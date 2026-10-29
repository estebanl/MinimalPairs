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
import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.core.model.PhonemeScore
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.audio.StoryAudioSource
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.data.remote.PracticeScoringException
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StoryReplyReplayTest {
    @Test
    fun completedRepliesReplayExactlyAfterBothEndingsUncertaintyAndServiceFailureWithoutChangingProgress() = runTest {
        for (outcome in Outcome.entries) {
            val fixture = Fixture(testScheduler, outcome)
            try {
                val samples = voicedReply()
                fixture.component.toggleRecording(); runCurrent()
                // Two recorder buffers must replay as one unmodified reply.
                fixture.recorder.emit(samples.copyOfRange(0, samples.size / 2))
                fixture.recorder.emit(samples.copyOfRange(samples.size / 2, samples.size)); runCurrent()
                fixture.component.toggleRecording(); runCurrent()
                val before = fixture.component.state.value
                assertTrue(before.hasRecording, outcome.name)
                assertFalse(before.isScoring)
                assertEquals(when (outcome) {
                    Outcome.SUCCESS -> MeaningTrapBranch.INTENDED_SUCCESS
                    Outcome.TRAP -> MeaningTrapBranch.TRAP_TRIGGERED
                    else -> MeaningTrapBranch.PENDING
                }, before.branch)
                assertEquals(outcome == Outcome.FAILURE, before.feedbackIsError)
                assertEquals(if (outcome in setOf(Outcome.SUCCESS, Outcome.TRAP)) 1 else 0, fixture.saved.size)
                val calls = fixture.scorer.calls
                val saved = fixture.saved.toList()
                val confusions = fixture.tracker.getAllConfusions()
                val weights = fixture.tracker.getAllWeights()

                repeat(2) {
                    fixture.component.playRecording(); runCurrent()
                    assertContentEquals(samples, fixture.player.played.last())
                    assertEquals(before, fixture.component.state.value)
                }
                assertEquals(calls, fixture.scorer.calls)
                assertEquals(saved, fixture.saved)
                assertEquals(confusions, fixture.tracker.getAllConfusions())
                assertEquals(weights, fixture.tracker.getAllWeights())
            } finally { fixture.close(); runCurrent() }
        }
    }

    @Test
    fun recordingStopsReplayAndReplayIsBlockedDuringCaptureAndScoring() = runTest {
        val fixture = Fixture(testScheduler)
        try {
            fixture.component.toggleRecording(); runCurrent()
            fixture.recorder.emit(voicedReply()); runCurrent()
            fixture.component.toggleRecording(); runCurrent()
            fixture.player.hold = true
            fixture.component.playRecording(); runCurrent()
            assertTrue(fixture.component.state.value.isPlayingAudio)
            val stopped = fixture.player.stops
            fixture.component.toggleRecording(); runCurrent()
            assertTrue(fixture.recorder.capturing)
            assertFalse(fixture.player.isPlaying())
            assertTrue(fixture.player.stops > stopped)
            assertFalse(fixture.component.state.value.hasRecording)
            val plays = fixture.player.played.size
            fixture.component.playRecording(); runCurrent()
            assertEquals(plays, fixture.player.played.size)

            fixture.player.hold = false
            fixture.scorer.gate = CompletableDeferred()
            fixture.recorder.emit(voicedReply()); runCurrent()
            fixture.component.toggleRecording(); runCurrent()
            assertTrue(fixture.component.state.value.isScoring)
            assertTrue(fixture.component.state.value.hasRecording)
            fixture.component.playRecording(); runCurrent()
            assertEquals(plays, fixture.player.played.size)
            fixture.scorer.gate!!.complete(Unit); runCurrent()
            fixture.component.playRecording(); runCurrent()
            assertContentEquals(voicedReply(), fixture.player.played.last())
        } finally { fixture.close(); runCurrent() }
    }

    @Test
    fun newTakeReplacesTheReplyAndEmptyCaptureDoesNotReplayAnOlderTake() = runTest {
        val fixture = Fixture(testScheduler)
        try {
            val first = voicedReply()
            val second = FormantSynthesizer.synthesizeVowel(420f, 1900f, durationMs = 240)
            for (samples in listOf(first, second)) {
                fixture.component.toggleRecording(); runCurrent()
                assertFalse(fixture.component.state.value.hasRecording)
                fixture.recorder.emit(samples); runCurrent()
                fixture.component.toggleRecording(); runCurrent()
            }
            fixture.component.playRecording(); runCurrent()
            assertContentEquals(second, fixture.player.played.last())
            fixture.component.toggleRecording(); runCurrent()
            fixture.component.toggleRecording(); runCurrent()
            assertFalse(fixture.component.state.value.hasRecording)
            val plays = fixture.player.played.size
            fixture.component.playRecording(); runCurrent()
            assertEquals(plays, fixture.player.played.size)
        } finally { fixture.close(); runCurrent() }
    }

    @Test
    fun rejectedSilenceRemainsReplayableButDoesNotCallTheScorerOrSaveProgress() = runTest {
        val fixture = Fixture(testScheduler)
        try {
            val silence = ShortArray(16000)
            fixture.component.toggleRecording(); runCurrent()
            fixture.recorder.emit(silence); runCurrent()
            fixture.component.toggleRecording(); runCurrent()
            assertTrue(fixture.component.state.value.feedbackIsError)
            assertTrue(fixture.component.state.value.hasRecording)
            fixture.component.playRecording(); runCurrent()
            assertContentEquals(silence, fixture.player.played.last())
            assertEquals(0, fixture.scorer.calls)
            assertTrue(fixture.saved.isEmpty())
        } finally { fixture.close(); runCurrent() }
    }

    @Test
    fun storyChangeResetAndDestructionClearTheReplyAndStopReplay() = runTest {
        for (action in listOf("change", "reset", "destroy")) {
            val fixture = Fixture(testScheduler)
            try {
                fixture.component.toggleRecording(); runCurrent()
                fixture.recorder.emit(voicedReply()); runCurrent()
                fixture.component.toggleRecording(); runCurrent()
                fixture.player.hold = true
                fixture.component.playRecording(); runCurrent()
                assertTrue(fixture.player.isPlaying())
                fixture.player.hold = false
                when (action) {
                    "change" -> fixture.component.selectStory("pen_vs_pan")
                    "reset" -> fixture.component.resetCurrentStory()
                    else -> fixture.close()
                }
                runCurrent()
                assertFalse(fixture.component.state.value.hasRecording, action)
                assertFalse(fixture.component.state.value.isPlayingAudio, action)
                assertFalse(fixture.player.isPlaying(), action)
                val plays = fixture.player.played.size
                fixture.component.playRecording(); runCurrent()
                assertEquals(plays, fixture.player.played.size)
            } finally { fixture.close(); runCurrent() }
        }
    }

    @Test
    fun replayFailureOffersRetryAndPreservesTheRecordingAndScoringFeedback() = runTest {
        val fixture = Fixture(testScheduler)
        try {
            fixture.component.toggleRecording(); runCurrent()
            fixture.recorder.emit(voicedReply()); runCurrent()
            fixture.component.toggleRecording(); runCurrent()
            val before = fixture.component.state.value
            fixture.player.fail = true
            fixture.component.playRecording(); runCurrent()
            assertTrue(fixture.component.state.value.playbackError.orEmpty().contains("tap Replay your reply to retry"))
            assertFalse(fixture.component.state.value.isPlayingAudio)
            assertEquals(before, fixture.component.state.value.copy(playbackError = null))
            fixture.player.fail = false
            fixture.component.playRecording(); runCurrent()
            assertContentEquals(voicedReply(), fixture.player.played.last())
            assertEquals(before, fixture.component.state.value)
            assertEquals(1, fixture.saved.size)
        } finally { fixture.close(); runCurrent() }
    }

    @Test
    fun examplesDoNotInventOrReplaceARealRecordingAndStoppingTheScreenCancelsReplay() = runTest {
        val fixture = Fixture(testScheduler)
        try {
            fixture.component.triggerSimulatedOutcome(true); runCurrent()
            assertFalse(fixture.component.state.value.hasRecording)
            fixture.component.toggleRecording(); runCurrent()
            fixture.recorder.emit(voicedReply()); runCurrent()
            fixture.component.toggleRecording(); runCurrent()
            fixture.component.triggerSimulatedOutcome(false); runCurrent()
            fixture.player.hold = true
            fixture.component.playRecording(); runCurrent()
            assertContentEquals(voicedReply(), fixture.player.played.last())
            fixture.lifecycle.stop(); runCurrent()
            assertFalse(fixture.player.isPlaying())
            assertFalse(fixture.component.state.value.isPlayingAudio)
            fixture.lifecycle.resume(); runCurrent()
            assertTrue(fixture.component.state.value.hasRecording)
        } finally { fixture.close(); runCurrent() }
    }

    private fun voicedReply() = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 200)

    private enum class Outcome { SUCCESS, TRAP, UNCERTAIN, FAILURE }

    private class Fixture(scheduler: TestCoroutineScheduler, outcome: Outcome = Outcome.SUCCESS) {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val tracker = PhonemeConfusionTracker()
        val saved = mutableListOf<ModulePracticeRecordEntity>()
        val recorder = Recorder()
        val player = Player { recorder.capturing }
        val scorer = Scorer(outcome)
        private val dispatcher = StandardTestDispatcher(scheduler)
        val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), tracker, scorer, {},
            recorder, player, dispatcher, dispatcher, onScored = saved::add,
            storyAudioSource = StoryAudioSource { _, _ -> shortArrayOf(7, 8) })

        init { recorder.playing = player::isPlaying }
        fun close() = lifecycle.destroy()
    }

    private class Recorder : AudioRecorder {
        override val state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
        var capturing = false
        var playing: () -> Boolean = { false }
        private var callback: ((ShortArray) -> Unit)? = null
        fun emit(samples: ShortArray) { callback?.invoke(samples) }
        override suspend fun prepare(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean {
            check(!playing()) { "Capture must not overlap playback" }
            callback = onBuffer; capturing = true; state.value = AudioRecorderState.RECORDING; return true
        }
        override suspend fun stop(): Boolean { capturing = false; state.value = AudioRecorderState.READY; return true }
        override suspend fun release() { capturing = false; state.value = AudioRecorderState.UNINITIALIZED }
        override suspend fun pause() = false
        override suspend fun resume() = false
    }

    private class Player(private val capturing: () -> Boolean) : AudioPlayer {
        val played = mutableListOf<ShortArray>()
        var hold = false
        var fail = false
        var stops = 0
        private var playing = false
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) {
            check(!capturing()) { "Playback must not overlap capture" }
            if (fail) error("Audio output unavailable")
            played += samples.copyOf()
            playing = hold
            if (!hold) onComplete()
        }
        override suspend fun stop() { stops++; playing = false }
        override fun isPlaying() = playing
    }

    private class Scorer(private val outcome: Outcome) : PhonemeScorerApi {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun scoreAudio(audioSamples: ShortArray, targetWord: String, expectedIpa: String, contrastId: String?) =
            error("Stories require phrase scoring")

        override suspend fun scorePhraseAudio(audioSamples: ShortArray, transcript: String, expectedIpa: String,
            targetPhonemeIndex: Int, alternativePhoneme: String?): PhonemeScoringResponse {
            calls++
            gate?.await()
            if (outcome == Outcome.FAILURE) throw PracticeScoringException("Service unavailable. Please try again.")
            val intended = alternativePhoneme == "ɪ"
            val score = when (outcome) {
                Outcome.SUCCESS -> if (intended) 88f else 20f
                Outcome.TRAP -> if (intended) 20f else 88f
                else -> 50f
            }
            return PhonemeScoringResponse(score, expectedIpa.split(" ").mapIndexed { index, phoneme ->
                PhonemeScore(phoneme, if (index == targetPhonemeIndex) score else 90f, index * .005f, (index + 1) * .005f)
            }, scoringMethod = "neural_ctc_phrase_contrast", modelId = "test-model",
                targetPhonemeIndex = targetPhonemeIndex, alternativePhoneme = alternativePhoneme, contrastEvidence = .5f)
        }
    }
}
