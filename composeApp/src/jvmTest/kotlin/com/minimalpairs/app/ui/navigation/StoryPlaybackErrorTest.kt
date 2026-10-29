package com.minimalpairs.app.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.hal.AudioRecorder
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.core.model.PhonemeScore
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.audio.StoryAudioClip
import com.minimalpairs.app.data.audio.StoryAudioSource
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StoryPlaybackErrorTest {
    @Test
    fun everyAudioControlReportsMissingAudioAndCanRetryWithoutChangingProgress() = runTest {
        for (clip in StoryAudioClip.entries) {
            val lifecycle = LifecycleRegistry().apply { resume() }
            val tracker = PhonemeConfusionTracker()
            val saved = mutableListOf<ModulePracticeRecordEntity>()
            val player = Player()
            var available = false
            val requested = mutableListOf<StoryAudioClip>()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), tracker, Scorer(), {},
                Recorder(), player, dispatcher, dispatcher, onScored = saved::add,
                storyAudioSource = StoryAudioSource { _, kind ->
                    requested += kind
                    if (available) shortArrayOf(1, 2) else null
                })
            fun play() = when (clip) {
                StoryAudioClip.PROMPT -> component.playPromptAudio()
                StoryAudioClip.PHRASE -> component.playTargetPhraseAudio()
                StoryAudioClip.INTENDED -> component.triggerSimulatedOutcome(true)
                StoryAudioClip.TRAP -> component.triggerSimulatedOutcome(false)
            }
            try {
                play(); runCurrent()
                assertEquals(clip, requested.last())
                assertTrue(component.state.value.playbackError.orEmpty().contains("audio is unavailable"))
                assertFalse(component.state.value.isPlayingAudio)
                val branch = component.state.value.branch
                available = true
                if (clip == StoryAudioClip.INTENDED || clip == StoryAudioClip.TRAP) component.playOutcomeAudio()
                else play()
                runCurrent()
                assertNull(component.state.value.playbackError)
                assertFalse(component.state.value.isPlayingAudio)
                assertEquals(branch, component.state.value.branch)
                assertEquals(1, player.plays)
                assertTrue(saved.isEmpty())
                assertTrue(tracker.getAllConfusions().isEmpty())
            } finally { lifecycle.destroy(); runCurrent() }
        }
    }

    @Test
    fun loadFailureIsVisibleAndPlaybackFailurePreservesAScoredEnding() = runTest {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val tracker = PhonemeConfusionTracker()
        val saved = mutableListOf<ModulePracticeRecordEntity>()
        val player = Player()
        val recorder = Recorder()
        var failLoad = true
        val dispatcher = StandardTestDispatcher(testScheduler)
        val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), tracker, Scorer(), {},
            recorder, player, dispatcher, dispatcher, onScored = saved::add,
            storyAudioSource = StoryAudioSource { _, _ ->
                if (failLoad) error("Cannot read resource")
                shortArrayOf(1, 2)
            })
        try {
            component.playTargetPhraseAudio(); runCurrent()
            assertTrue(component.state.value.playbackError.orEmpty().contains("Couldn't load the example phrase"))
            failLoad = false
            player.fail = true
            component.toggleRecording(); runCurrent()
            recorder.emit(FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 200)); runCurrent()
            component.toggleRecording(); runCurrent()
            val state = component.state.value
            assertEquals(MeaningTrapBranch.INTENDED_SUCCESS, state.branch)
            assertEquals(88f, state.gopScore)
            assertEquals(20f, state.alternateScore)
            assertTrue(state.feedbackText.contains("matched the sound"))
            assertFalse(state.feedbackIsError)
            assertTrue(state.playbackError.orEmpty().contains("Couldn't play the story ending"))
            assertFalse(state.isPlayingAudio)
            assertFalse(state.isScoring)
            assertEquals(1, saved.size)
            assertEquals(1, tracker.getConfusionCount("iː", "iː"))
            player.fail = false
            component.playOutcomeAudio(); runCurrent()
            assertNull(component.state.value.playbackError)
            assertEquals(88f, component.state.value.gopScore)
            assertEquals(1, saved.size)
            assertEquals(1, tracker.getConfusionCount("iː", "iː"))
        } finally { lifecycle.destroy(); runCurrent() }
    }

    private class Player : AudioPlayer {
        var fail = false
        var plays = 0
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) {
            plays++
            if (fail) error("No output device")
            onComplete()
        }
        override suspend fun stop() {}
        override fun isPlaying() = false
    }

    private class Recorder : AudioRecorder {
        override val state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
        private var callback: ((ShortArray) -> Unit)? = null
        fun emit(samples: ShortArray) { callback?.invoke(samples) }
        override suspend fun prepare(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean {
            callback = onBuffer; state.value = AudioRecorderState.RECORDING; return true
        }
        override suspend fun stop(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun release() { state.value = AudioRecorderState.UNINITIALIZED }
        override suspend fun pause() = false
        override suspend fun resume() = false
    }

    private class Scorer : PhonemeScorerApi {
        override suspend fun scoreAudio(audioSamples: ShortArray, targetWord: String, expectedIpa: String, contrastId: String?) =
            error("Stories require phrase scoring")

        override suspend fun scorePhraseAudio(audioSamples: ShortArray, transcript: String, expectedIpa: String,
            targetPhonemeIndex: Int, alternativePhoneme: String?): PhonemeScoringResponse {
            val selected = if (alternativePhoneme == "ɪ") 88f else 20f
            return PhonemeScoringResponse(selected, expectedIpa.split(" ").mapIndexed { index, phoneme ->
                PhonemeScore(phoneme, if (index == targetPhonemeIndex) selected else 90f, index * .005f, (index + 1) * .005f)
            }, scoringMethod = "neural_ctc_phrase_contrast", modelId = "test-model",
                targetPhonemeIndex = targetPhonemeIndex, alternativePhoneme = alternativePhoneme, contrastEvidence = .5f)
        }
    }
}
