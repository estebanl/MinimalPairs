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
import com.minimalpairs.app.data.local.SpeakingRecordEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalCoroutinesApi::class)
class ArticulatorLabComponentTest {
    @Test
    fun scoreExplainsItsSourceAndSelectionClearsTheExplanation() = runTest {
        val f = Fixture(this)
        try {
            f.scorer.method = "acoustic_estimate"
            f.scorer.feedback = "Remote scoring was unavailable."
            f.record(f.samples)
            assertTrue(f.state.scoringDescription!!.contains("Offline acoustic estimate"))
            assertTrue(f.state.scoringDescription!!.contains("Remote scoring was unavailable"))
            f.component.selectExamplePair(1)
            assertNull(f.state.scoringDescription)
            f.scorer.method = "neural_ctc"
            f.scorer.feedback = null
            f.record(f.samples)
            assertTrue(f.state.scoringDescription!!.contains("Model confidence"))
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun shortOrAbandonedRecordingDoesNotDisplayARecordedPlacement() = runTest {
        val f = Fixture(this)
        try {
            f.record(ShortArray(100))
            assertEquals(0f, f.state.liveF1)
            assertEquals(0f, f.state.liveF2)
            assertTrue(f.scorer.words.isEmpty())
            f.component.toggleRecording()
            runCurrent()
            f.recorder.emit(f.samples)
            runCurrent()
            assertTrue(f.state.liveF1 > 0f)
            f.lifecycle.stop()
            runCurrent()
            assertEquals(0f, f.state.liveF1)
            assertEquals(0f, f.state.liveF2)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

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
                assertEquals(f.scorer.words.last(), f.saved.last().targetWord)
                assertEquals(88f, f.saved.last().score)
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
            assertEquals(1, f.saved.size)
            assertNotNull(f.state.error)
            assertNull(f.state.overallGopScore)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun recordedExamplePlaysWithoutReplacingUserScore() = runTest {
        val f = Fixture(this)
        try {
            f.record(f.samples)
            val score = f.state.overallGopScore
            f.component.playPhonemeExample(true)
            runCurrent()
            assertContentEquals(f.exampleSamples, f.player.samples.single())
            assertEquals(listOf("iː"), f.requestedPhonemes)
            assertEquals(1, f.scorer.words.size)
            assertEquals(1, f.saved.size)
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
            f.component.playPhonemeExample(false)
            runCurrent()
            assertEquals(1, f.recorder.starts)
            assertTrue(f.player.samples.isEmpty())
            f.component.onBack()
            runCurrent()
            f.scorer.gate!!.complete(Unit)
            runCurrent()
            assertNull(f.state.overallGopScore)
            assertTrue(f.saved.isEmpty())
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

    @Test
    fun invalidScoresAreNotSaved() = runTest {
        val f = Fixture(this)
        try {
            for (score in listOf(Float.NaN, -1f, 101f)) {
                f.scorer.score = score
                f.record(f.samples)
                assertTrue(f.saved.isEmpty())
                assertNull(f.state.overallGopScore)
                assertNotNull(f.state.error)
            }
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun targetedPracticeStartsWithSecondSoundAndScoresThatTarget() = runTest {
        val f = Fixture(this, "ɪ")
        try {
            assertFalse(f.state.isTargetPhoneme1)
            f.record(f.samples)
            assertEquals("ship", f.scorer.words.single())
            assertEquals("ɪ", f.scorer.phonemes.single())
            assertEquals("ɪ", f.saved.single().targetPhoneme)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun retainedSpeakingScreenReselectsRequestedSoundAfterCleanup() = runTest {
        val f = Fixture(this, "ɪ")
        try {
            f.component.switchTargetPhoneme(true)
            f.record(f.samples)
            f.lifecycle.stop()
            f.component.preparePracticeTarget("ɪ")
            f.lifecycle.resume()
            runCurrent()
            assertFalse(f.state.isTargetPhoneme1)
            assertNull(f.state.overallGopScore)
            assertFalse(f.state.hasRecording)
            f.component.preparePracticeTarget("unknown")
            runCurrent()
            assertFalse(f.state.isTargetPhoneme1)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun speakingRouteRestoresTargetAndAcceptsOldRoutes() {
        val target = RootConfig.ArticulatorLab("i_vs_I", "ɪ")
        assertEquals(target, Json.decodeFromString<RootConfig.ArticulatorLab>(Json.encodeToString(target)))
        val old = Json.decodeFromString<RootConfig.ArticulatorLab>("""{"contrastId":"i_vs_I"}""")
        assertNull(old.targetPhoneme)
    }

    @Test
    fun choosingContrastAndExampleScoresAndSavesTheSelectedWord() = runTest {
        val f = Fixture(this)
        try {
            f.component.selectContrast("ae_vs_e")
            f.component.selectExamplePair(1)
            f.component.switchTargetPhoneme(false)
            f.record(f.samples)
            assertEquals("men", f.scorer.words.single())
            assertEquals("e", f.scorer.phonemes.single())
            assertEquals("ae_vs_e", f.saved.single().contrastId)
            assertEquals("men", f.saved.single().targetWord)
            f.component.selectExamplePair(2)
            assertNull(f.state.overallGopScore)
            assertNull(f.state.feedback)
            assertFalse(f.state.hasRecording)
            assertEquals(0f, f.state.liveF1)
            assertFalse(f.state.isTargetPhoneme1)
            f.component.selectContrast("u_vs_uu")
            assertEquals(0, f.state.selectedPairIndex)
            assertTrue(f.state.isTargetPhoneme1)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun invalidSelectionsAndChangesDuringRecordingOrScoringAreIgnored() = runTest {
        val f = Fixture(this)
        try {
            f.component.selectExamplePair(-1)
            f.component.selectExamplePair(100)
            f.component.selectContrast("unknown")
            assertEquals("i_vs_I", f.state.contrast.id)
            assertEquals(0, f.state.selectedPairIndex)
            f.component.selectExamplePair(2)
            f.component.toggleRecording()
            runCurrent()
            f.component.selectContrast("ae_vs_e")
            f.component.selectExamplePair(1)
            assertEquals("i_vs_I", f.state.contrast.id)
            assertEquals(2, f.state.selectedPairIndex)
            f.recorder.emit(f.samples)
            runCurrent()
            f.scorer.gate = CompletableDeferred()
            f.component.toggleRecording()
            runCurrent()
            f.component.selectContrast("ae_vs_e")
            f.component.selectExamplePair(1)
            assertEquals("i_vs_I", f.state.contrast.id)
            assertEquals(2, f.state.selectedPairIndex)
            f.scorer.gate!!.complete(Unit)
            runCurrent()
            assertEquals("seat", f.scorer.words.single())
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun retainedRouteRestoresRequestedContrastAfterManualSelection() = runTest {
        val f = Fixture(this)
        try {
            f.component.selectContrast("ae_vs_e")
            f.component.selectExamplePair(2)
            f.lifecycle.stop()
            f.component.preparePracticeTarget("ɪ", "i_vs_I")
            f.lifecycle.resume()
            runCurrent()
            assertEquals("i_vs_I", f.state.contrast.id)
            assertEquals(0, f.state.selectedPairIndex)
            assertFalse(f.state.isTargetPhoneme1)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun silenceClearsLiveFormantsInsteadOfLeavingAStaleComparison() = runTest {
        val f = Fixture(this)
        try {
            f.component.toggleRecording()
            runCurrent()
            f.recorder.emit(f.samples)
            runCurrent()
            assertTrue(f.state.liveF1 > 0f)
            f.recorder.emit(ShortArray(512))
            runCurrent()
            assertEquals(0f, f.state.liveF1)
            assertEquals(0f, f.state.liveF2)
            assertEquals(0f, f.state.liveF3)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun unmeasurableInputClearsLivePositionAndCannotBeScored() = runTest {
        val f = Fixture(this)
        val invalid = ShortArray(400) { if (it == 399) 10000 else 0 }
        try {
            f.component.toggleRecording()
            runCurrent()
            f.recorder.emit(f.samples)
            runCurrent()
            assertTrue(f.state.liveF1 > 0f)
            f.recorder.emit(invalid)
            runCurrent()
            assertEquals(0f, f.state.liveF1)
            assertEquals(0f, f.state.liveF2)
            f.component.toggleRecording()
            runCurrent()
            f.scorer.words.clear()
            f.record(invalid)
            assertEquals(0f, f.state.liveF1)
            assertEquals(0f, f.state.liveF2)
            assertNull(f.state.overallGopScore)
            assertTrue(f.scorer.words.isEmpty())
            assertTrue(f.state.error!!.contains("No clear vowel"))
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun stalledCaptureClearsLiveFormantsAfterFreshnessWindow() = runTest {
        val f = Fixture(this)
        try {
            f.component.toggleRecording()
            runCurrent()
            f.recorder.emit(f.samples)
            runCurrent()
            assertTrue(f.state.liveF1 > 0f)
            advanceTimeBy(751)
            runCurrent()
            assertEquals(0f, f.state.liveF1)
            assertEquals(0f, f.state.liveF2)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun examplesFollowBothTargetsAcrossEveryContrast() = runTest {
        val f = Fixture(this)
        try {
            for (contrast in com.minimalpairs.app.data.catalog.VowelContrastsCatalog.defaultContrasts) {
                f.component.selectContrast(contrast.id)
                for (first in listOf(true, false)) {
                    f.component.playPhonemeExample(first)
                    runCurrent()
                    assertEquals(if (first) contrast.phoneme1 else contrast.phoneme2, f.requestedPhonemes.last())
                    assertContentEquals(f.exampleSamples, f.player.samples.last())
                    assertFalse(f.state.isBusy)
                }
            }
            assertTrue(f.saved.isEmpty())
            assertTrue(f.scorer.words.isEmpty())
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun missingExampleShowsRecoverableErrorWithoutSyntheticFallback() = runTest {
        val f = Fixture(this)
        try {
            f.exampleAvailable = false
            f.component.playPhonemeExample(true)
            runCurrent()
            assertNotNull(f.state.error)
            assertTrue(f.player.samples.isEmpty())
            assertFalse(f.state.isBusy)
            f.exampleAvailable = true
            f.component.playPhonemeExample(true)
            runCurrent()
            assertNull(f.state.error)
            assertContentEquals(f.exampleSamples, f.player.samples.single())
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun everyListeningContrastSupportsSelectionAndConsonantWordPractice() = runTest {
        val f = Fixture(this)
        try {
            for (contrast in com.minimalpairs.app.data.catalog.ListeningContrastsCatalog.defaultContrasts) {
                f.component.selectContrast(contrast.id)
                assertEquals(contrast, f.state.contrast)
                for (first in listOf(true, false)) {
                    f.component.switchTargetPhoneme(first)
                    f.component.playPhonemeExample(first)
                    runCurrent()
                    if (contrast.vowelReference == null) {
                        assertEquals(if (first) contrast.pairs.first().word1 else contrast.pairs.first().word2,
                            f.requestedWords.last())
                        assertNotNull(com.minimalpairs.app.ui.reference.ConsonantPracticeReferences.forPhoneme(
                            if (first) contrast.phoneme1 else contrast.phoneme2))
                        // Unvoiced material must remain replayable without a vowel analysis gate.
                        val unvoiced = ShortArray(3200) { if (it % 2 == 0) 2000 else -2000 }
                        f.record(unvoiced)
                        assertTrue(f.state.hasRecording)
                        assertNull(f.state.error)
                        assertEquals(88f, f.state.overallGopScore)
                        assertEquals(0f, f.state.liveF1)
                        assertEquals(contrast.id, f.saved.last().contrastId)
                        assertEquals(if (first) contrast.phoneme1 else contrast.phoneme2, f.saved.last().targetPhoneme)
                        assertEquals(if (first) contrast.pairs.first().word1 else contrast.pairs.first().word2, f.scorer.words.last())
                        assertEquals(200, f.saved.last().durationMs)
                        f.component.playRecording()
                        runCurrent()
                        assertContentEquals(unvoiced, f.player.samples.last())
                    }
                }
            }
            assertEquals(26, f.scorer.words.size)
            assertEquals(26, f.saved.size)
            f.component.selectContrast("i_vs_I")
            assertFalse(f.state.hasRecording)
            assertNull(f.state.scoringDescription)
            f.record(f.samples)
            assertEquals(88f, f.state.overallGopScore)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun targetedPracticeCanReopenAConsonantPair() = runTest {
        val f = Fixture(this)
        try {
            f.component.preparePracticeTarget("v", "w_vs_v")
            runCurrent()
            assertEquals("w_vs_v", f.state.contrast.id)
            assertFalse(f.state.isTargetPhoneme1)
            f.exampleAvailable = false
            f.component.playPhonemeExample(false)
            runCurrent()
            assertNotNull(f.state.error)
            assertTrue(f.player.samples.isEmpty())
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    @Test
    fun consonantSilenceAndInvalidScoresAreNotSavedAndRecordingRemainsReplayable() = runTest {
        val f = Fixture(this)
        try {
            f.component.selectContrast("p_vs_b")
            f.record(ShortArray(3200))
            assertNotNull(f.state.error)
            assertTrue(f.scorer.words.isEmpty())
            assertTrue(f.saved.isEmpty())
            f.scorer.score = Float.NaN
            f.record(f.samples)
            assertNull(f.state.overallGopScore)
            assertTrue(f.saved.isEmpty())
            assertTrue(f.state.hasRecording)
            f.scorer.score = 82f
            f.scorer.method = "neural_ctc_phoneme"
            f.record(f.samples)
            assertEquals(82f, f.state.overallGopScore)
            assertTrue(f.state.scoringDescription!!.contains("Selected-phoneme model confidence"))
            assertEquals("p_vs_b", f.saved.single().contrastId)
            assertEquals("neural_ctc_phoneme", f.saved.single().scoringMethod)
        } finally { f.lifecycle.destroy(); runCurrent() }
    }

    private class Fixture(val scope: TestScope, targetPhoneme: String? = null) {
        val lifecycle = LifecycleRegistry().apply { resume() }
        val recorder = FakeRecorder()
        val player = FakePlayer()
        val scorer = FakeScorer()
        val samples = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 200)
        val saved = mutableListOf<SpeakingRecordEntity>()
        val exampleSamples = shortArrayOf(100, -200, 300)
        val requestedPhonemes = mutableListOf<String>()
        val requestedWords = mutableListOf<String>()
        var exampleAvailable = true
        val component = DefaultArticulatorLabComponent(DefaultComponentContext(lifecycle), null, scorer, {},
            recorder, player, StandardTestDispatcher(scope.testScheduler), StandardTestDispatcher(scope.testScheduler), onScored = saved::add, initialTargetPhoneme = targetPhoneme,
            loadPhonemePcm = { phoneme ->
                requestedPhonemes += phoneme
                if (exampleAvailable) exampleSamples else null
            }, loadWordPcm = { word ->
                requestedWords += word
                if (exampleAvailable) exampleSamples else null
            })
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
        var score = 88f
        var method = "unknown"
        var feedback: String? = null
        override suspend fun scoreAudio(audioSamples: ShortArray, targetWord: String, expectedIpa: String,
            contrastId: String?): PhonemeScoringResponse {
            words += targetWord
            phonemes += expectedIpa
            gate?.await()
            check(!fail)
            return PhonemeScoringResponse(overallScore = score, phonemes = emptyList(),
                scoringMethod = method, feedback = feedback)
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
