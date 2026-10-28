package com.minimalpairs.app.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.pause
import com.arkivanov.essenty.lifecycle.stop
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.dsp.TimeStretchProcessor
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.local.ProgressSnapshot
import com.minimalpairs.app.data.local.TrialRecordEntity
import com.minimalpairs.app.data.local.SpeakingRecordEntity
import com.minimalpairs.app.data.progress.ProgressRepository
import com.minimalpairs.app.data.progress.ProgressStore
import kotlinx.coroutines.flow.flowOf
import com.minimalpairs.app.domain.srs.ModifiedSm2Engine
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import com.minimalpairs.app.domain.srs.Sm2State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EarTrainerComponentTest {
    @Test
    fun enablingDuringPlaybackDoesNotDuplicateAudioAndStillWaitsForAnAnswer() = runTest {
        withTrainer {
            component.playPrompt()
            runCurrent()
            component.setAutoChangeEnabled(true)
            runCurrent()
            assertEquals(1, audio.callbacks.size)
            finishPlayback()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(0L, state.trialId)
            assertNull(state.lastResultCorrect)
            assertEquals(1, audio.callbacks.size)
            component.setAutoChangeEnabled(false)
            component.setAutoChangeEnabled(true)
            runCurrent()
            assertEquals(2, audio.callbacks.size, "Enabling on an unanswered trial replays its prompt")
        }
    }

    @Test
    fun destroyingExerciseCancelsItsPendingAdvance() = runTest {
        withTrainer {
            component.setAutoChangeEnabled(true)
            runCurrent()
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            runCurrent()
            lifecycle.destroy()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(0L, state.trialId)
            assertEquals(1, audio.callbacks.size)
            assertFalse(state.isPlayingAudio)
        }
    }

    @Test
    fun autoChangePlaysImmediatelyAndAdvancesBothAnswerResultsAfterDelay() = runTest {
        withTrainer {
            assertFalse(state.autoChangeEnabled)
            component.setAutoChangeEnabled(true)
            component.setAutoChangeEnabled(true)
            runCurrent()
            assertEquals(1, audio.callbacks.size)
            component.onSelectAnswer(state.targetWord)
            assertNull(state.lastResultCorrect)
            for (correct in listOf(true, false)) {
                finishPlayback()
                val previous = state
                component.onSelectAnswer(if (correct) state.targetWord else otherWord)
                component.onSelectAnswer(state.targetWord)
                assertEquals(correct, state.lastResultCorrect)
                assertEquals(0f, state.autoChangeProgress)
                runCurrent()
                advanceTimeBy(750)
                runCurrent()
                assertEquals(0.5f, state.autoChangeProgress, "The bar uses the advancement timer's elapsed time")
                advanceTimeBy(749)
                runCurrent()
                assertEquals(previous.trialId, state.trialId)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(previous.trialId + 1, state.trialId)
                assertEquals(previous.targetWord, state.targetWord, "Repeated targets still create a new trial")
                assertFalse(state.hasPlayedPrompt)
                assertNull(state.lastResultCorrect)
                assertTrue(state.isPlayingAudio)
                assertNull(state.autoChangeProgress)
                audio.callbacks.first().invoke()
                runCurrent()
                assertFalse(state.hasPlayedPrompt, "Stale callbacks cannot finish the new prompt")
            }
            assertEquals(3, audio.callbacks.size)
        }
    }

    @Test
    fun disablingCancelsAdvanceAndKeepsPlayingAudioWhileManualNextAutoplays() = runTest {
        withTrainer {
            component.setAutoChangeEnabled(true)
            runCurrent()
            component.setAutoChangeEnabled(false)
            assertTrue(state.isPlayingAudio)
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            advanceTimeBy(5_000)
            assertEquals(0L, state.trialId)
            component.setAutoChangeEnabled(true)
            runCurrent()
            advanceTimeBy(500)
            component.setAutoChangeEnabled(false)
            assertNull(state.autoChangeProgress)
            advanceTimeBy(2_000)
            assertEquals(0L, state.trialId)
            component.setAutoChangeEnabled(true)
            runCurrent()
            advanceTimeBy(500)
            component.nextTrial()
            runCurrent()
            assertEquals(1L, state.trialId)
            assertTrue(state.isPlayingAudio)
            finishPlayback()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(1L, state.trialId)
            assertEquals(2, audio.callbacks.size)
        }
    }

    @Test
    fun replayComparisonAndTipsRestartTheFullAdvanceDelay() = runTest {
        withTrainer {
            component.setAutoChangeEnabled(true)
            runCurrent()
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            runCurrent()
            advanceTimeBy(1_000)
            for (comparison in listOf(false, true)) {
                if (comparison) component.playOption(otherWord) else component.playPrompt()
                runCurrent()
                assertNull(state.autoChangeProgress)
                advanceTimeBy(5_000)
                assertEquals(0L, state.trialId)
                finishPlayback()
                assertEquals(0f, state.autoChangeProgress)
                advanceTimeBy(1_499)
                runCurrent()
                assertEquals(0L, state.trialId)
            }
            component.setAutoChangePaused(true)
            assertNull(state.autoChangeProgress)
            advanceTimeBy(5_000)
            assertEquals(0L, state.trialId)
            component.setAutoChangePaused(false)
            assertEquals(0f, state.autoChangeProgress)
            runCurrent()
            advanceTimeBy(1_499)
            runCurrent()
            assertEquals(0L, state.trialId)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1L, state.trialId)
        }
    }

    @Test
    fun lifecyclePausesAutoChangeAndReturnRestartsInterruptedPromptsOrFeedback() = runTest {
        withTrainer {
            component.setAutoChangeEnabled(true)
            runCurrent()
            lifecycle.pause()
            runCurrent()
            finishPlayback()
            assertFalse(state.hasPlayedPrompt)
            assertFalse(state.isPlayingAudio)
            advanceTimeBy(5_000)
            lifecycle.resume()
            runCurrent()
            assertTrue(state.isPlayingAudio)
            assertEquals(2, audio.callbacks.size)
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            runCurrent()
            advanceTimeBy(1_000)
            lifecycle.stop()
            assertNull(state.autoChangeProgress)
            advanceTimeBy(5_000)
            assertEquals(0L, state.trialId)
            lifecycle.resume()
            runCurrent()
            advanceTimeBy(1_499)
            runCurrent()
            assertEquals(0L, state.trialId)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1L, state.trialId)
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            component.onBack()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(1L, state.trialId)
            assertFalse(state.isPlayingAudio)
        }
    }

    @Test
    fun autoPromptFailuresWaitForExplicitRetryEvenAfterTipsAndLifecycleResume() = runTest {
        withTrainer {
            failLoading = true
            component.setAutoChangeEnabled(true)
            runCurrent()
            assertNotNull(state.playbackError)
            component.setAutoChangePaused(true)
            component.setAutoChangePaused(false)
            lifecycle.stop()
            lifecycle.resume()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(1, loadedWords.size)
            assertFalse(state.hasPlayedPrompt)
            failLoading = false
            component.playPrompt()
            runCurrent()
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            runCurrent()
            advanceTimeBy(1_500)
            runCurrent()
            assertEquals(1L, state.trialId)
            audio.failPlayback = true
            // Retry a failed automatic prompt through the same existing playback path.
            lifecycle.stop()
            lifecycle.resume()
            runCurrent()
            assertNotNull(state.playbackError)
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(1L, state.trialId)
        }
    }

    @Test
    fun autoChangeRecordsEachAnswerOnceAndAllowsRetryingFailedSaves() = runTest {
        withTrainer(recordProgress = true) {
            component.setAutoChangeEnabled(true)
            runCurrent()
            finishPlayback()
            failSaving = true
            component.onSelectAnswer(state.targetWord)
            component.onSelectAnswer(otherWord)
            runCurrent()
            assertNotNull(state.progressSaveError)
            advanceTimeBy(1_500)
            runCurrent()
            assertEquals(1L, state.trialId)
            failSaving = false
            component.retryProgressSaves()
            runCurrent()
            assertEquals(1, savedTrials.size)
            finishPlayback()
            component.onSelectAnswer(otherWord)
            component.onSelectAnswer(state.targetWord)
            runCurrent()
            assertEquals(2, savedTrials.size)
            assertEquals(listOf(true, false), savedTrials.map { it.isCorrect })
        }
    }

    @Test
    fun dashboardExposesEveryContrastAndNewDrillsSupportThePracticeFlow() = runTest {
        val lifecycle = LifecycleRegistry()
        var selectedId: String? = null
        val dashboard = DefaultDashboardComponent(DefaultComponentContext(lifecycle)) { selectedId = it }
        assertEquals(ListeningContrastsCatalog.defaultContrasts, dashboard.contrasts)
        for (contrast in dashboard.contrasts) {
            dashboard.onSelectContrast(contrast.id)
            assertEquals(contrast.id, selectedId)
            withTrainer(contrast.id) {
                assertEquals(contrast.id, state.contrast.id)
                val target = state.targetWord
                val expectedPhoneme = if (target == state.currentPair.word1) contrast.phoneme1 else contrast.phoneme2
                assertEquals(expectedPhoneme, state.targetPhoneme)
                component.playPrompt()
                runCurrent()
                assertEquals(target, loadedWords.single())
                finishPlayback()
                component.onSelectAnswer(target)
                assertEquals(true, state.lastResultCorrect)
                component.playOption(otherWord)
                runCurrent()
                assertEquals(otherWord, loadedWords.last())
                finishPlayback()
                component.nextTrial()
                assertEquals(contrast.id, state.contrast.id)
                assertTrue(state.currentPair in contrast.pairs)
                assertFalse(state.hasPlayedPrompt)
                assertNull(state.lastResultCorrect)
            }
        }
        lifecycle.destroy()
    }

    @Test
    fun optionPlaybackRequiresAnAnswerAndNeverChangesTheResult() = runTest {
        withTrainer {
            val options = listOf(state.currentPair.word1, state.currentPair.word2)
            options.forEach(component::playOption)
            runCurrent()
            assertTrue(loadedWords.isEmpty())

            component.playPrompt()
            runCurrent()
            options.forEach(component::playOption)
            finishPlayback()
            options.forEach(component::playOption)
            runCurrent()
            assertEquals(1, loadedWords.size)
            component.onSelectAnswer(otherWord)
            val answered = state
            component.playOption("not an option")
            assertEquals(answered, state)

            for (word in options) {
                val countBefore = audio.recordings.size
                component.playOption(word.uppercase())
                component.playOption(word)
                component.playPrompt()
                component.nextTrial()
                component.onSelectAnswer(state.targetWord)
                runCurrent()
                assertEquals(countBefore + 1, audio.recordings.size)
                assertEquals(word, loadedWords.last())
                assertEquals(word, state.playingOptionWord)
                assertTrue(state.isPlayingAudio)
                assertEquals(answered.selectedWord, state.selectedWord)
                assertEquals(answered.sm2State, state.sm2State)
                finishPlayback()
                assertEquals(answered, state)
            }

            component.setPlaybackSpeed(PlaybackSpeed.SLOW_DIAGNOSTIC)
            val recording = ShortArray(1600) { (it * 7).toShort() }
            wordPcm = recording
            component.playOption(options.last())
            runCurrent()
            assertContentEquals(TimeStretchProcessor.process(recording, 0.75f), audio.recordings.last())
            finishPlayback()
            component.nextTrial()
            val next = state
            options.forEach(component::playOption)
            runCurrent()
            assertEquals(next, state)
            assertNull(state.playingOptionWord)
            assertFalse(state.hasPlayedPrompt)
        }
    }

    @Test
    fun comparisonFailureCanBeRetriedWithoutLosingFeedback() = runTest {
        withTrainer {
            component.playPrompt()
            runCurrent()
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            val answered = state
            wordPcm = null
            component.playOption(otherWord)
            runCurrent()
            assertNotNull(state.playbackError)
            assertFalse(state.isPlayingAudio)
            assertNull(state.playingOptionWord)
            assertEquals(answered.selectedWord, state.selectedWord)
            assertEquals(answered.lastResultCorrect, state.lastResultCorrect)
            assertEquals(answered.sm2State, state.sm2State)
            assertEquals(1, audio.recordings.size)

            wordPcm = shortArrayOf(100, -100)
            assertTrue(state.sm2State.masteryScore > 0.7f)
            component.playOption(otherWord)
            runCurrent()
            assertContentEquals(wordPcm, audio.recordings.last(), "Comparison must stay clear at high mastery")
            finishPlayback()
            assertEquals(answered, state)
        }
    }

    @Test
    fun interruptedComparisonCannotUnlockANewTrial() = runTest {
        withTrainer {
            component.playPrompt()
            runCurrent()
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            component.playOption(otherWord)
            runCurrent()
            lifecycle.stop()
            runCurrent()
            assertNull(state.playingOptionWord)
            assertFalse(state.isPlayingAudio)
            assertTrue(audio.stopCount > 0)
            lifecycle.resume()
            component.nextTrial()
            finishPlayback() // Late completion from the cancelled comparison.
            assertFalse(state.hasPlayedPrompt)
            assertNull(state.lastResultCorrect)
            assertNull(state.playingOptionWord)
        }
    }

    @Test
    fun playsCompleteTargetRecordingAndReplaysItAtEitherSpeed() = runTest {
        withTrainer {
            val recording = ShortArray(16_000) { ((it * 37) % 20_000 - 10_000).toShort() }
            wordPcm = recording
            component.playPrompt()
            runCurrent()
            assertEquals(listOf(state.targetWord), loadedWords)
            assertContentEquals(recording, audio.recordings.single())
            finishPlayback()

            component.playPrompt()
            runCurrent()
            assertContentEquals(recording, audio.recordings.last())
            finishPlayback()

            component.setPlaybackSpeed(PlaybackSpeed.SLOW_DIAGNOSTIC)
            component.playPrompt()
            runCurrent()
            assertEquals(List(3) { state.targetWord }, loadedWords)
            assertContentEquals(TimeStretchProcessor.process(recording, 0.75f), audio.recordings.last())
            assertTrue(audio.recordings.last().size > recording.size)
        }
    }

    @Test
    fun promptsAndComparisonsStayCleanAfterCorrectAnswersAtBothSpeeds() = runTest {
        withTrainer {
            val recording = ShortArray(1600) { ((it * 37) % 20_000 - 10_000).toShort() }
            wordPcm = recording

            repeat(3) {
                component.setPlaybackSpeed(PlaybackSpeed.NORMAL)
                component.playPrompt()
                runCurrent()
                assertContentEquals(recording, audio.recordings.last())
                finishPlayback()
                component.onSelectAnswer(state.targetWord)
                assertTrue(state.sm2State.masteryScore > 0.7f)

                for (speed in PlaybackSpeed.entries) {
                    component.setPlaybackSpeed(speed)
                    val expected = if (speed == PlaybackSpeed.NORMAL) recording
                    else TimeStretchProcessor.process(recording, 0.75f)

                    component.playPrompt()
                    runCurrent()
                    assertContentEquals(expected, audio.recordings.last(), "Prompt replay must stay clear at high mastery")
                    finishPlayback()

                    for (word in listOf(state.currentPair.word1, state.currentPair.word2)) {
                        component.playOption(word)
                        runCurrent()
                        assertContentEquals(expected, audio.recordings.last())
                        finishPlayback()
                    }
                }
                component.nextTrial()
            }
        }
    }

    @Test
    fun missingOrEmptyWordAudioNeverFallsBackToAVowelAndCanBeRetried() = runTest {
        for (unavailable in listOf(null, ShortArray(0))) {
            withTrainer {
                wordPcm = unavailable
                component.playPrompt()
                runCurrent()
                assertNotNull(state.playbackError)
                assertFalse(state.isPlayingAudio)
                assertFalse(state.hasPlayedPrompt)
                assertTrue(audio.recordings.isEmpty())
                component.onSelectAnswer(state.targetWord)
                assertNull(state.lastResultCorrect)

                wordPcm = shortArrayOf(100, -200, 300)
                component.playPrompt()
                runCurrent()
                assertNull(state.playbackError)
                assertFalse(state.hasPlayedPrompt)
                finishPlayback()
                assertTrue(state.hasPlayedPrompt)
                component.onSelectAnswer(state.targetWord)
                assertEquals(true, state.lastResultCorrect)
            }
        }
    }

    @Test
    fun playbackIsExplicitAndAnswersAreGuarded() = runTest {
        withTrainer {
            val initial = state
            assertEquals(0, audio.callbacks.size)
            component.onSelectAnswer(initial.targetWord)
            component.nextTrial()
            assertEquals(initial, state)

            component.playPrompt()
            component.playPrompt()
            component.setPlaybackSpeed(PlaybackSpeed.SLOW_DIAGNOSTIC)
            component.onSelectAnswer(initial.targetWord)
            runCurrent()
            assertEquals(1, audio.callbacks.size)
            assertTrue(state.isPlayingAudio)
            assertFalse(state.hasPlayedPrompt)
            assertEquals(PlaybackSpeed.NORMAL, state.playbackSpeed)
            assertNull(state.lastResultCorrect)

            finishPlayback()
            component.onSelectAnswer("not an option")
            assertNull(state.lastResultCorrect)
            component.onSelectAnswer(initial.targetWord.uppercase())
            val answered = state
            component.onSelectAnswer(otherWord)
            assertEquals(answered, state)
            assertEquals(initial.targetWord, state.selectedWord)
            assertEquals(true, state.lastResultCorrect)
            assertEquals(1, tracker.getConfusionCount(initial.targetPhoneme, initial.targetPhoneme))
        }
    }

    @Test
    fun wrongAnswerRecordsActualSelectionAndTriggersSlowModeAfterThreeMisses() = runTest {
        withTrainer {
            repeat(3) {
                component.playPrompt()
                runCurrent()
                finishPlayback()
                val wrongWord = otherWord
                component.onSelectAnswer(wrongWord)
                assertEquals(wrongWord, state.selectedWord)
                assertEquals(false, state.lastResultCorrect)
                if (it < 2) component.nextTrial()
            }
            assertTrue(state.showFormantHint)
            assertEquals(PlaybackSpeed.SLOW_DIAGNOSTIC, state.playbackSpeed)
            component.setPlaybackSpeed(PlaybackSpeed.NORMAL)
            assertEquals(PlaybackSpeed.NORMAL, state.playbackSpeed)
            component.dismissFormantHint()
            assertFalse(state.showFormantHint)
        }
    }

    @Test
    fun timerStartsAfterPlaybackAndIgnoresDuplicateCompletion() = runTest {
        withTrainer {
            advanceTimeBy(60_000)
            component.playPrompt()
            runCurrent()
            advanceTimeBy(5_000)
            finishPlayback()
            advanceTimeBy(2_000)
            finishPlayback() // A late duplicate must not restart the timer.
            component.onSelectAnswer(state.targetWord)
            assertEquals(ModifiedSm2Engine.review(Sm2State(), 4), state.sm2State)
        }
    }

    @Test
    fun replayDoesNotResetTimerAndCannotChangeAnAnsweredTrial() = runTest {
        withTrainer {
            component.playPrompt()
            runCurrent()
            finishPlayback()
            advanceTimeBy(4_000)
            component.playPrompt()
            runCurrent()
            component.onSelectAnswer(state.targetWord)
            assertNull(state.lastResultCorrect)
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            assertEquals(ModifiedSm2Engine.review(Sm2State(), 3), state.sm2State)

            val result = state
            component.playPrompt()
            runCurrent()
            component.nextTrial()
            assertEquals(result.selectedWord, state.selectedWord)
            finishPlayback()
            assertEquals(result, state)
        }
    }

    @Test
    fun repeatedWordStillRequiresNewPlaybackAndIgnoresOldCallback() = runTest {
        withTrainer {
            component.playPrompt()
            runCurrent()
            finishPlayback()
            component.onSelectAnswer(state.targetWord)
            val previous = state
            component.nextTrial()
            assertEquals(previous.currentPair, state.currentPair)
            assertEquals(previous.targetWord, state.targetWord)
            assertFalse(state.hasPlayedPrompt)
            assertNull(state.selectedWord)
            assertNull(state.lastResultCorrect)
            assertNull(state.playbackError)
            assertEquals(1, audio.callbacks.size)
            component.playPrompt()
            runCurrent()
            audio.callbacks.first().invoke()
            runCurrent()
            assertTrue(state.isPlayingAudio)
            assertFalse(state.hasPlayedPrompt)
            finishPlayback()
            assertTrue(state.hasPlayedPrompt)
        }
    }

    @Test
    fun loadingAndPlaybackFailuresCanBeRetried() = runTest {
        withTrainer {
            failLoading = true
            component.playPrompt()
            runCurrent()
            assertNotNull(state.playbackError)
            assertFalse(state.isPlayingAudio)
            assertFalse(state.hasPlayedPrompt)
            assertEquals(0, audio.callbacks.size)

            failLoading = false
            audio.failPlayback = true
            component.playPrompt()
            assertNull(state.playbackError)
            runCurrent()
            assertNotNull(state.playbackError)
            assertFalse(state.hasPlayedPrompt)

            audio.failPlayback = false
            component.playPrompt()
            runCurrent()
            finishPlayback()
            assertNull(state.playbackError)
            assertTrue(state.hasPlayedPrompt)
        }
    }

    @Test
    fun backStopsPlaybackAndIgnoresLateCompletion() = runTest {
        withTrainer {
            component.playPrompt()
            runCurrent()
            component.onBack()
            component.onBack()
            runCurrent()
            finishPlayback()
            component.playPrompt()
            assertEquals(1, backCount)
            assertTrue(audio.stopCount > 0)
            assertFalse(state.isPlayingAudio)
            assertFalse(state.hasPlayedPrompt)
            assertEquals(1, audio.callbacks.size)
        }
    }

    @Test
    fun lifecycleStopCancelsPlaybackAndAllowsExplicitReplayOnReturn() = runTest {
        withTrainer {
            component.playPrompt()
            runCurrent()
            lifecycle.stop()
            runCurrent()
            finishPlayback()
            assertFalse(state.hasPlayedPrompt)
            assertFalse(state.isPlayingAudio)
            assertTrue(audio.stopCount > 0)
            lifecycle.resume()
            component.playPrompt()
            runCurrent()
            finishPlayback()
            assertTrue(state.hasPlayedPrompt)
            lifecycle.destroy()
            runCurrent()
            component.playPrompt()
            assertFalse(state.isPlayingAudio)
        }
    }

    private suspend fun TestScope.withTrainer(contrastId: String = "i_vs_I", recordProgress: Boolean = false, block: suspend Fixture.() -> Unit) {
        val fixture = Fixture(this, contrastId, recordProgress)
        try {
            fixture.block()
        } finally {
            fixture.lifecycle.destroy()
            runCurrent()
        }
    }

    private class Fixture(private val testScope: TestScope, contrastId: String, recordProgress: Boolean) {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val audio = FakeAudioPlayer()
        val tracker = PhonemeConfusionTracker()
        var backCount = 0
        var failLoading = false
        var failSaving = false
        val savedTrials = mutableListOf<TrialRecordEntity>()
        var wordPcm: ShortArray? = ShortArray(1600)
        val loadedWords = mutableListOf<String>()
        val component = DefaultEarTrainerComponent(
            componentContext = DefaultComponentContext(lifecycle),
            contrastId = contrastId,
            confusionTracker = tracker,
            onBackClicked = { backCount++ },
            audioPlayer = audio,
            dispatcher = StandardTestDispatcher(testScope.testScheduler),
            audioDispatcher = StandardTestDispatcher(testScope.testScheduler),
            timeSource = testScope.testScheduler.timeSource,
            random = object : Random() { override fun nextBits(bitCount: Int) = 0 },
            progressRepository = if (recordProgress) ProgressRepository(
                store = object : ProgressStore {
                    override fun observe() = flowOf(ProgressSnapshot(emptyList(), emptyList(), emptyList()))
                    override suspend fun record(trial: TrialRecordEntity, quality: Int) {
                        check(!failSaving)
                        savedTrials += trial
                    }
                    override suspend fun recordSpeaking(record: SpeakingRecordEntity) = Unit
                },
                scope = testScope.backgroundScope,
                refresh = flowOf(Unit),
                aggregationDispatcher = StandardTestDispatcher(testScope.testScheduler)
            ) else null,
            loadWordPcm = { word ->
                loadedWords += word
                check(!failLoading)
                wordPcm
            }
        )
        val state get() = component.state.value
        val otherWord get() = if (state.targetWord == state.currentPair.word1) state.currentPair.word2 else state.currentPair.word1

        fun finishPlayback() {
            audio.callbacks.last().invoke()
            testScope.runCurrent()
        }
    }

    private class FakeAudioPlayer : AudioPlayer {
        val callbacks = mutableListOf<() -> Unit>()
        val recordings = mutableListOf<ShortArray>()
        var failPlayback = false
        var stopCount = 0
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) {
            check(!failPlayback)
            recordings += samples.copyOf()
            callbacks += onComplete
        }
        override suspend fun stop() { stopCount++ }
        override fun isPlaying() = false
    }
}
