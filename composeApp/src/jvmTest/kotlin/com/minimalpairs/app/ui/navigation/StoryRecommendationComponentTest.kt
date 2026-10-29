package com.minimalpairs.app.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.hal.AudioRecorder
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.data.audio.StoryAudioSource
import com.minimalpairs.app.data.local.*
import com.minimalpairs.app.data.progress.*
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.domain.StoryContrastEvidence
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StoryRecommendationComponentTest {
    @Test
    fun lifetimeEvidenceIgnoresFiltersRetainsFailedReadsAndClearsAfterReset() = runTest {
        val store = Store(snapshot("ae_vs_e", "pen", "pan"))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = ProgressRepository(store, backgroundScope,
            calendar = flowOf(PracticeCalendar(LocalDate(2026, 10, 29), TimeZone.UTC)),
            nowEpochMs = { 1_793_232_000_000L }, refresh = flowOf(Unit), aggregationDispatcher = dispatcher)
        runCurrent()
        val evidence = repository.storyRecommendationEvidence.value
        assertEquals(mapOf("ae_vs_e" to StoryContrastEvidence(1, 1)), evidence)
        repository.setContrastFilter("i_vs_I")
        repository.setDateRange(ProgressDateRange.LAST_7_DAYS)
        runCurrent()
        assertEquals(0, repository.state.value.attempts)
        assertEquals(evidence, repository.storyRecommendationEvidence.value)
        assertEquals(1, store.observations)
        store.fail.value = true
        runCurrent()
        assertTrue(repository.state.value.canRetryLoad)
        assertEquals(evidence, repository.storyRecommendationEvidence.value)
        store.fail.value = false
        repository.retryLoading()
        runCurrent()
        assertEquals(evidence, repository.storyRecommendationEvidence.value)
        assertEquals(2, store.observations)
        repository.resetProgress()
        runCurrent()
        assertTrue(repository.storyRecommendationEvidence.value.isEmpty())
    }

    @Test
    fun recommendationUpdatesWithoutOverridingManualSelectionOrCountingExamplesTwice() = runTest {
        val store = Store(snapshot("ae_vs_e", "pen", "pan"))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = ProgressRepository(store, backgroundScope,
            calendar = flowOf(PracticeCalendar(LocalDate(2026, 10, 29), TimeZone.UTC)),
            refresh = flowOf(Unit), aggregationDispatcher = dispatcher)
        val lifecycle = LifecycleRegistry().apply { resume() }
        val tracker = PhonemeConfusionTracker()
        // An in-memory event is not added to the same event's persisted evidence.
        tracker.recordTrial("e", "æ", false)
        val trackerBefore = tracker.getAllConfusions()
        val component = DefaultMeaningTrapComponent(DefaultComponentContext(lifecycle), tracker, Scorer(), {},
            Recorder(), Player(), dispatcher, dispatcher, progressRepository = repository,
            storyAudioSource = StoryAudioSource { _, _ -> shortArrayOf(1, 2) })
        try {
            runCurrent()
            assertEquals("pen_vs_pan", component.state.value.recommendation?.storyId)
            assertTrue(component.state.value.recommendation!!.explanation.contains("1 of 1"))
            component.selectStory("sheep_vs_ship")
            runCurrent()
            for (correct in listOf(true, false)) {
                component.triggerSimulatedOutcome(correct)
                runCurrent()
                assertEquals("pen_vs_pan", component.state.value.recommendation?.storyId)
                assertEquals(mapOf("ae_vs_e" to StoryContrastEvidence(1, 1)), repository.storyRecommendationEvidence.value)
            }
            assertEquals(trackerBefore, tracker.getAllConfusions())
            store.snapshot.value = snapshot("ae_vs_uh", "cat", "cut")
            runCurrent()
            assertEquals("cat_vs_cut", component.state.value.recommendation?.storyId)
            assertEquals("sheep_vs_ship", component.state.value.activeStory.id)
            component.resetCurrentStory()
            runCurrent()
            assertEquals("cat_vs_cut", component.state.value.recommendation?.storyId)
            assertEquals("sheep_vs_ship", component.state.value.activeStory.id)
        } finally { lifecycle.destroy(); runCurrent() }
    }

    private fun snapshot(contrast: String, target: String, selected: String) = ProgressSnapshot(
        listOf(TrialRecordEntity(1, contrast, target, selected, false, 500, "FORCED_CHOICE", 1000)),
        emptyList(), emptyList()
    )

    private class Store(initial: ProgressSnapshot) : ProgressStore {
        val snapshot = MutableStateFlow(initial)
        val fail = MutableStateFlow(false)
        var observations = 0
        override fun observe(): Flow<ProgressSnapshot> {
            observations++
            return combine(snapshot, fail) { value, failed -> check(!failed); value }
        }
        override suspend fun record(trial: TrialRecordEntity, quality: Int) = error("Unused")
        override suspend fun recordSpeaking(record: SpeakingRecordEntity) = error("Unused")
        override suspend fun resetProgress() { snapshot.value = ProgressSnapshot(emptyList(), emptyList(), emptyList()) }
    }

    private class Player : AudioPlayer {
        override suspend fun play(samples: ShortArray, onComplete: () -> Unit) = onComplete()
        override suspend fun stop() {}
        override fun isPlaying() = false
    }

    private class Recorder : AudioRecorder {
        override val state = MutableStateFlow(AudioRecorderState.UNINITIALIZED)
        override suspend fun prepare() = false
        override suspend fun start(onBuffer: (ShortArray) -> Unit) = false
        override suspend fun pause() = false
        override suspend fun resume() = false
        override suspend fun stop() = true
        override suspend fun release() {}
    }

    private class Scorer : PhonemeScorerApi {
        override suspend fun scoreAudio(audioSamples: ShortArray, targetWord: String, expectedIpa: String, contrastId: String?) =
            error("No reply should be evaluated in this test")
    }
}
