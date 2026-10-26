package com.minimalpairs.app.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.dsp.TimeStretchProcessor
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.core.model.DrillType
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
            assertEquals(DrillType.MASKING_NOISE, state.drillType)
            component.playOption(otherWord)
            runCurrent()
            assertContentEquals(wordPcm, audio.recordings.last(), "Comparison must stay clear even in noise drills")
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

    private suspend fun TestScope.withTrainer(block: suspend Fixture.() -> Unit) {
        val fixture = Fixture(this)
        try {
            fixture.block()
        } finally {
            fixture.lifecycle.destroy()
            runCurrent()
        }
    }

    private class Fixture(private val testScope: TestScope) {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val audio = FakeAudioPlayer()
        val tracker = PhonemeConfusionTracker()
        var backCount = 0
        var failLoading = false
        var wordPcm: ShortArray? = ShortArray(1600)
        val loadedWords = mutableListOf<String>()
        val component = DefaultEarTrainerComponent(
            componentContext = DefaultComponentContext(lifecycle),
            contrastId = "i_vs_I",
            confusionTracker = tracker,
            onBackClicked = { backCount++ },
            audioPlayer = audio,
            dispatcher = StandardTestDispatcher(testScope.testScheduler),
            audioDispatcher = StandardTestDispatcher(testScope.testScheduler),
            timeSource = testScope.testScheduler.timeSource,
            random = object : Random() { override fun nextBits(bitCount: Int) = 0 },
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
