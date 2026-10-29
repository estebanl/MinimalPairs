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
import com.minimalpairs.app.data.audio.StoryAudioClip
import com.minimalpairs.app.data.audio.StoryAudioSource
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.dialogue.*
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.data.remote.PracticeScoringException
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StoryConversationTest {
    @Test
    fun immediateContinuationAfterAnExampleWaitsForRecordingCleanupInsteadOfDroppingTheAction() = runTest {
        val f = Fixture(testScheduler)
        try {
            f.component.triggerSimulatedOutcome(false)
            f.component.continueConversation()
            assertTrue(f.component.state.value.isLoadingNextTurn)
            runCurrent()
            assertEquals(2, f.component.state.value.turnNumber)
            assertTrue(f.component.state.value.isExampleConversation)
            assertEquals("alternate", f.requests.single().single().branch)
            assertFalse(f.recorder.capturing)
            assertTrue(f.scorer.calls.isEmpty())
            assertTrue(f.saved.isEmpty())
        } finally { f.close(); runCurrent() }
    }

    @Test
    fun realConversationCarriesBothPronunciationConsequencesAndKeepsTheSameScoringTarget() = runTest {
        val f = Fixture(testScheduler)
        try {
            f.component.continueConversation(); runCurrent()
            assertTrue(f.requests.isEmpty(), "An unresolved reply cannot advance")
            f.record(); runCurrent()
            assertEquals(1, f.saved.size)
            val before = f.tracker.getAllConfusions()
            f.component.continueConversation(); runCurrent()
            val next = f.component.state.value
            assertEquals(2, next.turnNumber)
            assertEquals("intended", f.requests.single().single().branch)
            assertEquals(f.base.intendedOutcome.dialogue, next.conversation.single().consequence)
            assertEquals(f.base.targetPhraseIpa, next.activeStory.targetPhraseIpa)
            assertEquals(f.base.targetPhonemeIndex, next.activeStory.targetPhonemeIndex)
            assertEquals(MeaningTrapBranch.PENDING, next.branch)
            assertFalse(next.hasRecording)
            assertEquals(before, f.tracker.getAllConfusions())
            assertEquals(1, f.saved.size)
            f.scorer.intendedWins = false
            f.record(); runCurrent()
            assertEquals(MeaningTrapBranch.TRAP_TRIGGERED, f.component.state.value.branch)
            f.component.continueConversation(); runCurrent()
            assertEquals(3, f.component.state.value.turnNumber)
            assertEquals(listOf("intended", "alternate"), f.requests.last().map { it.branch })
            assertEquals(next.activeStory.trapOutcome.dialogue, f.requests.last().last().consequence)
            assertEquals(2, f.saved.size)
            assertEquals(4, f.scorer.calls.size)
            assertEquals(f.base.targetPhrase, f.scorer.calls[2].first)
            assertEquals(f.base.targetPhraseIpa, f.scorer.calls[2].second)
            assertEquals(f.base.targetPhonemeIndex, f.scorer.calls[2].third)
        } finally { f.close(); runCurrent() }
    }

    @Test
    fun loadingFailurePreservesReplyScoresAndHistoryAndRetryDoesNotDuplicateATurn() = runTest {
        val f = Fixture(testScheduler)
        try {
            f.record(); runCurrent()
            val before = f.component.state.value
            f.fail = true
            f.component.continueConversation(); runCurrent()
            val failed = f.component.state.value
            assertFalse(failed.isLoadingNextTurn)
            assertNotNull(failed.continuationError)
            assertEquals(before.branch, failed.branch)
            assertEquals(before.gopScore, failed.gopScore)
            assertEquals(before.feedbackText, failed.feedbackText)
            assertEquals(before.conversation, failed.conversation)
            assertTrue(failed.hasRecording)
            assertEquals(1, f.saved.size)
            f.component.playRecording(); runCurrent()
            assertContentEquals(f.samples, f.played.last())
            f.fail = false
            f.component.continueConversation(); runCurrent()
            assertEquals(f.requests[0], f.requests[1])
            assertEquals(2, f.component.state.value.turnNumber)
            assertNull(f.component.state.value.continuationError)
            assertEquals(1, f.saved.size)
        } finally { f.close(); runCurrent() }
    }

    @Test
    fun invalidGeneratedMetadataIsRejectedEvenWhenAnInjectedSourceReturnsIt() = runTest {
        val f = Fixture(testScheduler)
        try {
            f.record(); runCurrent()
            val previous = f.component.state.value
            f.corruptTarget = true
            f.component.continueConversation(); runCurrent()
            assertEquals(previous.activeStory, f.component.state.value.activeStory)
            assertEquals(1, f.component.state.value.turnNumber)
            assertTrue(f.component.state.value.continuationError!!.contains("learning target"))
            assertTrue(f.component.state.value.hasRecording)
            assertEquals(1, f.saved.size)
            assertEquals(2, f.scorer.calls.size)
        } finally { f.close(); runCurrent() }
    }

    @Test
    fun exampleConversationsNeverScoreOrRecordAndResetRestoresPersonalPractice() = runTest {
        val f = Fixture(testScheduler)
        try {
            repeat(MAX_STORY_TURNS) { turn ->
                f.component.triggerSimulatedOutcome(turn % 2 == 0); runCurrent()
                assertTrue(f.component.state.value.isSimulated)
                f.component.continueConversation(); runCurrent()
                if (turn < MAX_STORY_TURNS - 1) {
                    assertTrue(f.component.state.value.isExampleConversation)
                    f.component.toggleRecording(); runCurrent()
                    assertFalse(f.recorder.capturing)
                }
            }
            assertEquals(MAX_STORY_TURNS - 1, f.requests.size)
            assertTrue(f.requests.flatten().all { it.example })
            assertTrue(f.saved.isEmpty())
            assertTrue(f.scorer.calls.isEmpty())
            assertTrue(f.tracker.getAllConfusions().isEmpty())
            assertTrue(f.tracker.getAllWeights().isEmpty())
            f.component.resetCurrentStory(); runCurrent()
            assertEquals(f.base, f.component.state.value.activeStory)
            assertEquals(1, f.component.state.value.turnNumber)
            assertFalse(f.component.state.value.isExampleConversation)
            f.record(); runCurrent()
            assertEquals(1, f.saved.size)
        } finally { f.close(); runCurrent() }
    }

    @Test
    fun generatingDisablesCaptureExamplesAndDuplicateContinuation() = runTest {
        val f = Fixture(testScheduler)
        try {
            f.record(); runCurrent()
            f.gate = CompletableDeferred()
            f.component.continueConversation(); runCurrent()
            assertTrue(f.component.state.value.isLoadingNextTurn)
            f.component.continueConversation()
            f.component.toggleRecording()
            f.component.triggerSimulatedOutcome(false); runCurrent()
            assertFalse(f.recorder.capturing)
            assertFalse(f.component.state.value.isSimulated)
            assertEquals(1, f.requests.size)
            f.gate!!.complete(Unit); runCurrent()
            assertFalse(f.component.state.value.isLoadingNextTurn)
            assertEquals(2, f.component.state.value.turnNumber)
        } finally { f.close(); runCurrent() }
    }

    @Test
    fun switchingResetStoppingBackAndDestructionDiscardLateContinuationResults() = runTest {
        for (action in listOf("switch", "reset", "stop", "back", "destroy")) {
            val f = Fixture(testScheduler)
            try {
                f.record(); runCurrent()
                f.gate = CompletableDeferred()
                f.ignoreCancellation = true
                f.component.continueConversation(); runCurrent()
                when (action) {
                    "switch" -> f.component.selectStory("pot_vs_port")
                    "reset" -> f.component.resetCurrentStory()
                    "stop" -> f.lifecycle.stop()
                    "back" -> f.component.onBack()
                    "destroy" -> f.close()
                }
                runCurrent()
                val state = f.component.state.value
                assertFalse(state.isLoadingNextTurn, action)
                f.gate!!.complete(Unit); runCurrent()
                assertEquals(state, f.component.state.value, action)
                assertEquals(1, state.turnNumber, action)
                assertEquals(if (action == "switch") "pot_vs_port" else f.base.id, state.activeStory.id, action)
            } finally { f.close(); runCurrent() }
        }
    }

    @Test
    fun generatedTurnsKeepPhraseAudioAndReplayButNeverPlayAnUnrelatedBundledNarrative() = runTest {
        val f = Fixture(testScheduler)
        try {
            f.record(); runCurrent()
            f.component.continueConversation(); runCurrent()
            f.audioClips.clear()
            f.component.playPromptAudio()
            f.component.playOutcomeAudio(); runCurrent()
            assertTrue(f.audioClips.isEmpty())
            f.component.playTargetPhraseAudio(); runCurrent()
            assertEquals(listOf(StoryAudioClip.PHRASE), f.audioClips)
            f.record(); runCurrent()
            f.component.playRecording(); runCurrent()
            assertContentEquals(f.samples, f.played.last())
            assertEquals(listOf(StoryAudioClip.PHRASE), f.audioClips)
        } finally { f.close(); runCurrent() }
    }

    @Test
    fun uncertainFailedAndUnsupportedScoresCannotAdvanceOrSaveAFollowUp() = runTest {
        for (failure in listOf("uncertain", "service", "method", "evidence")) {
            val f = Fixture(testScheduler)
            try {
                f.record(); runCurrent()
                f.component.continueConversation(); runCurrent()
                val evidence = f.tracker.getAllConfusions()
                f.scorer.failure = failure
                f.record(); runCurrent()
                assertEquals(MeaningTrapBranch.PENDING, f.component.state.value.branch, failure)
                assertTrue(f.component.state.value.hasRecording, failure)
                f.component.continueConversation(); runCurrent()
                assertEquals(1, f.requests.size, failure)
                assertEquals(1, f.saved.size, failure)
                assertEquals(evidence, f.tracker.getAllConfusions(), failure)
            } finally { f.close(); runCurrent() }
        }
    }

    private class Fixture(scheduler: TestCoroutineScheduler) {
        val base = MeaningTrapCatalog.stories.first()
        val lifecycle = LifecycleRegistry().apply { resume() }
        val tracker = PhonemeConfusionTracker()
        val saved = mutableListOf<ModulePracticeRecordEntity>()
        val recorder = Recorder()
        val scorer = Scorer()
        val requests = mutableListOf<List<StoryDialogueHistoryTurn>>()
        val played = mutableListOf<ShortArray>()
        val audioClips = mutableListOf<StoryAudioClip>()
        val samples = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 200)
        var fail = false
        var corruptTarget = false
        var gate: CompletableDeferred<Unit>? = null
        var ignoreCancellation = false
        private val dispatcher = StandardTestDispatcher(scheduler)
        val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), tracker, scorer, {},
            recorder, object : AudioPlayer {
                override suspend fun play(samples: ShortArray, onComplete: () -> Unit) { played += samples.copyOf(); onComplete() }
                override suspend fun stop() {}
                override fun isPlaying() = false
            }, dispatcher, dispatcher, onScored = saved::add,
            storyAudioSource = StoryAudioSource { _, clip -> audioClips += clip; shortArrayOf(1, 2) },
            dialogueSource = StoryDialogueSource { _, history ->
                requests += history.toList()
                if (ignoreCancellation) withContext(NonCancellable) { gate?.await() } else gate?.await()
                if (fail) error("Loading failed")
                val target = StoryDialogueTarget.from(base).let { if (corruptTarget) it.copy(targetPhonemeIndex = 0) else it }
                StoryDialogueResponse(target, history.size + 1,
                    StoryNarrative("The farm after turn ${history.size}.", "Please repeat your adoption request.",
                        "The sheep is waiting by the gate.", "The ship is waiting beside the pond."), "test-content-v1")
            })

        fun record() {
            component.toggleRecording()
            schedulerRun()
            recorder.emit(samples)
            schedulerRun()
            component.toggleRecording()
        }
        private val schedulerRun: () -> Unit = { scheduler.runCurrent() }
        fun close() { if (lifecycle.state != com.arkivanov.essenty.lifecycle.Lifecycle.State.DESTROYED) lifecycle.destroy() }
    }

    private class Recorder : AudioRecorder {
        override val state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
        var capturing = false
        private var callback: ((ShortArray) -> Unit)? = null
        fun emit(samples: ShortArray) { callback?.invoke(samples) }
        override suspend fun prepare(): Boolean { state.value = AudioRecorderState.READY; return true }
        override suspend fun start(onBuffer: (ShortArray) -> Unit): Boolean {
            callback = onBuffer; capturing = true; state.value = AudioRecorderState.RECORDING; return true
        }
        override suspend fun stop(): Boolean { capturing = false; state.value = AudioRecorderState.READY; return true }
        override suspend fun release() { capturing = false; state.value = AudioRecorderState.UNINITIALIZED }
        override suspend fun pause() = false
        override suspend fun resume() = false
    }

    private class Scorer : PhonemeScorerApi {
        var intendedWins = true
        var failure: String? = null
        val calls = mutableListOf<Triple<String, String, Int>>()
        override suspend fun scoreAudio(audioSamples: ShortArray, targetWord: String, expectedIpa: String, contrastId: String?) =
            error("Stories require phrase scoring")
        override suspend fun scorePhraseAudio(audioSamples: ShortArray, transcript: String, expectedIpa: String,
            targetPhonemeIndex: Int, alternativePhoneme: String?): PhonemeScoringResponse {
            calls += Triple(transcript, expectedIpa, targetPhonemeIndex)
            if (failure == "service") throw PracticeScoringException("Service unavailable. Try again.")
            val intended = alternativePhoneme == "ɪ"
            val score = if (failure == "uncertain") 50f else if (intended == intendedWins) 90f else 10f
            return PhonemeScoringResponse(score, expectedIpa.split(" ").mapIndexed { index, token ->
                PhonemeScore(token, if (index == targetPhonemeIndex) score else 90f, index * .005f, (index + 1) * .005f)
            }, scoringMethod = if (failure == "method") "acoustic_fallback" else "neural_ctc_phrase_contrast",
                modelId = "test-scorer", targetPhonemeIndex = targetPhonemeIndex,
                alternativePhoneme = alternativePhoneme, contrastEvidence = if (failure == "evidence") .01f else .5f)
        }
    }
}
