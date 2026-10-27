package com.minimalpairs.app.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.*
import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.hal.*
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ModuleProgressComponentTest {
    @Test
    fun storiesSaveRealScoresButExcludeSilenceInvalidAndDemonstrations() = runTest {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val recorder = Recorder()
        val saved = mutableListOf<ModulePracticeRecordEntity>()
        val scorer = Scorer()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), PhonemeConfusionTracker(), scorer, {},
            recorder, Player(), dispatcher, dispatcher, onScored = saved::add)
        fun record(samples: ShortArray) {
            component.toggleRecording(); runCurrent()
            recorder.emit(samples); runCurrent()
            component.toggleRecording(); runCurrent()
        }
        try {
            assertEquals(0, recorder.prepares)
            component.triggerSimulatedOutcome(true); runCurrent()
            assertTrue(saved.isEmpty())
            record(ShortArray(3200))
            assertTrue(saved.isEmpty())
            assertEquals(0, scorer.calls)
            record(voice())
            assertEquals(1, saved.size)
            assertEquals("STORIES", saved.single().module)
            assertEquals(component.state.value.activeStory.id, saved.single().targetId)
            assertEquals(88f, saved.single().score)
            assertTrue(saved.single().succeeded)
            scorer.score = Float.NaN
            record(voice())
            assertEquals(1, saved.size)
        } finally { lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun cancellingStoryScoreOnTargetChangeNeverWritesIt() = runTest {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val recorder = Recorder()
        val saved = mutableListOf<ModulePracticeRecordEntity>()
        val scorer = Scorer().apply { gate = CompletableDeferred() }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), PhonemeConfusionTracker(), scorer, {},
            recorder, Player(), dispatcher, dispatcher, onScored = saved::add)
        try {
            component.toggleRecording(); runCurrent()
            recorder.emit(voice()); runCurrent()
            component.toggleRecording(); runCurrent()
            component.selectStory(component.availableStories.last().id); runCurrent()
            scorer.gate!!.complete(Unit); runCurrent()
            assertTrue(saved.isEmpty())
            assertNull(component.state.value.gopScore)
        } finally { lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun rhythmSavesOnlyVoicedRecordingsAndCancelsOnStop() = runTest {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val recorder = Recorder()
        val saved = mutableListOf<ModulePracticeRecordEntity>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val component = DefaultProsodyComponent(DefaultComponentContext(lifecycle), {}, recorder, Player(), dispatcher, dispatcher,
            onScored = saved::add)
        fun record(samples: ShortArray) {
            component.toggleRecording(); runCurrent()
            recorder.emit(samples); runCurrent()
            component.toggleRecording(); runCurrent()
        }
        try {
            component.triggerSimulatedProsody(true); runCurrent()
            assertTrue(saved.isEmpty())
            record(ShortArray(3200))
            assertTrue(saved.isEmpty())
            assertNotNull(component.state.value.error)
            record(voice())
            assertEquals(1, saved.size)
            assertEquals("RHYTHM", saved.single().module)
            assertNull(saved.single().contrastId)
            assertEquals(component.state.value.activePattern.id, saved.single().targetId)
            assertEquals(component.state.value.evaluation!!.overallScore, saved.single().score)
            component.toggleRecording(); runCurrent()
            recorder.emit(voice()); runCurrent()
            lifecycle.stop(); runCurrent()
            recorder.emit(voice()); runCurrent()
            assertFalse(component.state.value.isRecording)
            assertEquals(AudioRecorderState.UNINITIALIZED, recorder.state.value)
            assertEquals(1, saved.size)
        } finally { lifecycle.destroy(); runCurrent() }
    }

    private fun voice() = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 200)
    private class Scorer : PhonemeScorerApi {
        var score = 88f
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun scoreAudio(audioSamples: ShortArray, targetWord: String, expectedIpa: String, contrastId: String?): PhonemeScoringResponse {
            calls++
            gate?.await()
            return PhonemeScoringResponse(score, emptyList())
        }
    }
    private class Player : AudioPlayer {
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) { onComplete() }
        override suspend fun stop() {}
        override fun isPlaying() = false
    }
    private class Recorder : AudioRecorder {
        override val state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
        var prepares = 0
        private var callback: ((ShortArray) -> Unit)? = null
        fun emit(samples: ShortArray) { callback?.invoke(samples) }
        override suspend fun prepare(): Boolean { prepares++; state.value = AudioRecorderState.READY; return true }
        override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean { callback = onBuffer; state.value = AudioRecorderState.RECORDING; return true }
        override suspend fun stop(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun release() { state.value = AudioRecorderState.UNINITIALIZED }
        override suspend fun pause() = false
        override suspend fun resume() = false
    }
}
