package com.minimalpairs.app.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.*
import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.hal.*
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.core.model.PhonemeScore
import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.data.remote.PracticeScoringException
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
            assertTrue(component.state.value.feedbackIsError)
            assertTrue(component.state.value.feedbackText.contains("Couldn't align"))
        } finally { lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun storiesTrackTheDifferingVowelInBothDirectionsAndNeverTrackExamples() = runTest {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val recorder = Recorder()
        val tracker = PhonemeConfusionTracker()
        val saved = mutableListOf<ModulePracticeRecordEntity>()
        val scorer = Scorer()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), tracker, scorer, {},
            recorder, Player(), dispatcher, dispatcher, onScored = saved::add)
        fun record() {
            component.toggleRecording(); runCurrent()
            recorder.emit(voice()); runCurrent()
            component.toggleRecording(); runCurrent()
        }
        try {
            val expected = listOf("iː" to "ɪ", "e" to "æ", "ɑː" to "ʌ", "ʊ" to "uː")
            component.availableStories.zip(expected).forEach { (story, vowels) ->
                component.selectStory(story.id); runCurrent()
                component.triggerSimulatedOutcome(true); runCurrent()
                component.triggerSimulatedOutcome(false); runCurrent()
                assertNull(component.state.value.gopScore)
                assertTrue(component.state.value.isSimulated)
                assertEquals(0, tracker.getConfusionCount(vowels.first, vowels.second))
                scorer.score = 88f; scorer.alternateScore = 20f
                record()
                assertEquals(MeaningTrapBranch.INTENDED_SUCCESS, component.state.value.branch)
                assertFalse(component.state.value.isSimulated)
                assertEquals(1, tracker.getConfusionCount(vowels.first, vowels.first))
                scorer.score = 25f; scorer.alternateScore = 90f
                record()
                assertEquals(MeaningTrapBranch.TRAP_TRIGGERED, component.state.value.branch)
                assertEquals(1, tracker.getConfusionCount(vowels.first, vowels.second))
            }
            assertEquals(8, saved.size)
            assertEquals(setOf("iː", "e", "ɑː", "ʊ"), tracker.getAllConfusions().keys)
            assertTrue(saved.filterNot { it.succeeded }.all { it.score == 25f })
        } finally { lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun uncertainScoresAndScorerErrorsLeaveTheStoryOpenWithoutChangingProgress() = runTest {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val recorder = Recorder()
        val tracker = PhonemeConfusionTracker()
        val saved = mutableListOf<ModulePracticeRecordEntity>()
        val scorer = Scorer()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), tracker, scorer, {},
            recorder, Player(), dispatcher, dispatcher, onScored = saved::add)
        fun record() {
            component.toggleRecording(); runCurrent()
            recorder.emit(voice()); runCurrent()
            component.toggleRecording(); runCurrent()
        }
        try {
            scorer.score = 35f; scorer.alternateScore = 40f
            record()
            assertEquals(MeaningTrapBranch.PENDING, component.state.value.branch)
            assertTrue(component.state.value.feedbackText.contains("couldn't distinguish"))
            assertEquals(35f, component.state.value.gopScore)
            assertEquals(40f, component.state.value.alternateScore)
            scorer.score = 85f; scorer.alternateScore = 80f
            record()
            assertEquals(MeaningTrapBranch.PENDING, component.state.value.branch)
            scorer.error = PracticeScoringException("The phoneme service rejected its access key. Check the scorer configuration.")
            record()
            assertTrue(component.state.value.feedbackIsError)
            assertTrue(component.state.value.feedbackText.contains("access key"))
            assertFalse(component.state.value.feedbackText.contains("microphone"))
            assertNull(component.state.value.gopScore)
            assertFalse(component.state.value.isScoring)
            assertTrue(saved.isEmpty())
            assertTrue(tracker.getAllConfusions().isEmpty())
        } finally { lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun scoringIsVisibleAndCancellationDuringEitherAlignmentNeverWritesAResult() = runTest {
        for (cancelAt in listOf(1, 2)) {
            for (cancelBy in listOf("story", "reset", "stop", "back", "example")) {
                val lifecycle = LifecycleRegistry().apply { resume() }
                val recorder = Recorder()
                val tracker = PhonemeConfusionTracker()
                val saved = mutableListOf<ModulePracticeRecordEntity>()
                val scorer = Scorer().apply { gate = CompletableDeferred(); gateAt = cancelAt }
                val dispatcher = StandardTestDispatcher(testScheduler)
                val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), tracker, scorer, {},
                    recorder, Player(), dispatcher, dispatcher, onScored = saved::add)
                try {
                    component.toggleRecording(); runCurrent()
                    recorder.emit(voice()); runCurrent()
                    component.toggleRecording(); runCurrent()
                    assertTrue(component.state.value.isScoring)
                    assertFalse(component.state.value.isRecording)
                    component.toggleRecording(); runCurrent()
                    assertEquals(cancelAt, scorer.calls)
                    when (cancelBy) {
                        "story" -> component.selectStory(component.availableStories.last().id)
                        "reset" -> component.resetCurrentStory()
                        "stop" -> lifecycle.stop()
                        "back" -> component.onBack()
                        "example" -> component.triggerSimulatedOutcome(true)
                    }
                    runCurrent()
                    scorer.gate!!.complete(Unit); runCurrent()
                    assertFalse(component.state.value.isScoring)
                    assertNull(component.state.value.gopScore)
                    assertTrue(saved.isEmpty())
                    assertTrue(tracker.getAllConfusions().isEmpty())
                } finally { lifecycle.destroy(); runCurrent() }
            }
        }
    }

    @Test
    fun microphoneFailureIsVisibleAndASecondRecordingCanRecover() = runTest {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val recorder = Recorder().apply { canPrepare = false }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), PhonemeConfusionTracker(), Scorer(), {},
            recorder, Player(), dispatcher, dispatcher)
        try {
            component.toggleRecording(); runCurrent()
            assertTrue(component.state.value.feedbackIsError)
            assertTrue(component.state.value.feedbackText.contains("microphone access"))
            assertFalse(component.state.value.isRecording)
            recorder.canPrepare = true
            component.toggleRecording(); runCurrent()
            assertTrue(component.state.value.isRecording)
            assertFalse(component.state.value.feedbackIsError)
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
        var alternateScore = 20f
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        var gateAt: Int? = null
        var error: Exception? = null
        override suspend fun scoreAudio(audioSamples: ShortArray, targetWord: String, expectedIpa: String, contrastId: String?): PhonemeScoringResponse {
            error("Stories must use selected-phoneme phrase scoring")
        }
        override suspend fun scorePhraseAudio(audioSamples: ShortArray, transcript: String, expectedIpa: String, targetPhonemeIndex: Int, alternativePhoneme: String?): PhonemeScoringResponse {
            calls++
            if (gateAt == null || calls == gateAt) gate?.await()
            error?.let { throw it }
            val story = MeaningTrapCatalog.stories.first { it.targetPhrase == transcript ||
                it.targetPhrase.replace(it.intendedWord, it.trapWord) == transcript }
            assertEquals(story.targetPhonemeIndex, targetPhonemeIndex)
            assertEquals(if (transcript == story.targetPhrase) story.trapIpa.split(" ")[1]
                else story.intendedIpa.split(" ")[1], alternativePhoneme)
            val selectedScore = if (transcript == story.targetPhrase) score else alternateScore
            return PhonemeScoringResponse(selectedScore, expectedIpa.split(" ").mapIndexed { index, phoneme ->
                PhonemeScore(phoneme, if (index == story.targetPhonemeIndex) selectedScore else 90f,
                    index * .005f, (index + 1) * .005f)
            }, scoringMethod = "neural_ctc_phrase_contrast", modelId = "test-model", targetPhonemeIndex = targetPhonemeIndex,
                alternativePhoneme = alternativePhoneme, contrastEvidence = .5f)
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
        var canPrepare = true
        private var callback: ((ShortArray) -> Unit)? = null
        fun emit(samples: ShortArray) { callback?.invoke(samples) }
        override suspend fun prepare(): Boolean { prepares++; if (canPrepare) state.value = AudioRecorderState.READY; return canPrepare }
        override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean { callback = onBuffer; state.value = AudioRecorderState.RECORDING; return true }
        override suspend fun stop(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun release() { state.value = AudioRecorderState.UNINITIALIZED }
        override suspend fun pause() = false
        override suspend fun resume() = false
    }
}
