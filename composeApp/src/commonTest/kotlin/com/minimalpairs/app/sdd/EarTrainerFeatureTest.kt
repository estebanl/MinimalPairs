package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.model.DrillType
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.domain.srs.DrillProgressionManager
import com.minimalpairs.app.domain.srs.ModifiedSm2Engine
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import com.minimalpairs.app.domain.srs.Sm2State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Executable acceptance test suite verifying specs/features/ear_trainer.feature
 */
class EarTrainerFeatureTest {

    @Test
    fun testScenarioSuccessfulIdentificationIncreasesConfidenceWeight() {
        // Background: Given the user has selected vowel contrast "/iː/ vs /ɪ/"
        val contrast = VowelContrastsCatalog.getContrastById("i_vs_I")!!
        val tracker = PhonemeConfusionTracker()
        val progression = DrillProgressionManager()

        assertEquals(PlaybackSpeed.NORMAL, progression.playbackSpeed)
        val initialWeight = tracker.getConfidenceWeight("ɪ")

        // Given an audio prompt plays the target word "ship"
        val targetWord = "ship"
        val targetPhoneme = "ɪ"

        // When the user selects option "ship" within 3.0 seconds
        val selectedOption = "ship"
        val latencySeconds = 2.1f
        val isCorrect = selectedOption == targetWord

        // Record trial in confusion tracker
        tracker.recordTrial(targetPhoneme, targetPhoneme, isCorrect)

        // Calculate SM-2 quality grade
        val q = ModifiedSm2Engine.calculateQuality(isCorrect, latencySeconds, progression.consecutiveFailures)
        val nextSm2 = ModifiedSm2Engine.review(Sm2State(), q)
        val feedback = progression.onTrialCompleted(isCorrect)

        // Then the system records a successful discrimination trial
        assertTrue(feedback.isCorrect)
        assertEquals(0, feedback.consecutiveFailures)

        // And the confidence weight for phoneme "/ɪ/" in SM-2 is increased
        val updatedWeight = tracker.getConfidenceWeight("ɪ")
        assertTrue(updatedWeight > initialWeight, "Confidence weight must increase upon successful discrimination")
        assertTrue(q >= 4, "Quality score for <= 3.0s correct answer must be >= 4")
        assertTrue(nextSm2.repetitions >= 1)
    }

    @Test
    fun testScenarioSlowMotionDiagnosticPlaybackTrigger() {
        // Background: Given contrast "/iː/ vs /ɪ/" and normal 1.0x playback
        val progression = DrillProgressionManager()
        assertEquals(PlaybackSpeed.NORMAL, progression.playbackSpeed)
        assertEquals(false, progression.showFormantVisualizerHint)

        // Given the user has failed the target contrast 3 consecutive times
        progression.onTrialCompleted(isCorrect = false)
        assertEquals(1, progression.consecutiveFailures)
        assertEquals(PlaybackSpeed.NORMAL, progression.playbackSpeed)

        progression.onTrialCompleted(isCorrect = false)
        assertEquals(2, progression.consecutiveFailures)
        assertEquals(PlaybackSpeed.NORMAL, progression.playbackSpeed)

        val feedback3 = progression.onTrialCompleted(isCorrect = false)
        assertEquals(3, progression.consecutiveFailures)

        // Then the audio playback speed automatically adjusts to "0.75x" phase-vocoded
        assertEquals(PlaybackSpeed.SLOW_DIAGNOSTIC, feedback3.speed)
        assertEquals(PlaybackSpeed.SLOW_DIAGNOSTIC, progression.playbackSpeed)

        // And a visual hint pointing to formant visualizers is rendered
        assertTrue(feedback3.showFormantHint)
        assertTrue(progression.showFormantVisualizerHint)
    }

    @Test
    fun testScenarioAdaptiveDrillProgressionFromForcedChoiceToAbx() {
        val progression = DrillProgressionManager()

        // Initial mastery <= 0.4 -> Forced Choice
        assertEquals(DrillType.FORCED_CHOICE, progression.resolveDrillType(0.2f))

        // Intermediate mastery 0.4..0.7 -> ABX
        assertEquals(DrillType.ABX, progression.resolveDrillType(0.55f))

        // Given user achieves mastery score above 0.7
        val highMastery = 0.78f
        // Then drill progresses to Masking Noise / Advanced testing
        assertEquals(DrillType.MASKING_NOISE, progression.resolveDrillType(highMastery))
    }
}
