package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.dsp.PitchFrame
import com.minimalpairs.app.core.dsp.PitchTracker
import com.minimalpairs.app.data.catalog.ProsodyCatalog
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntonationPatternsSpecTest {

    @Test
    fun testProsodyCatalogCompleteness() {
        val patterns = ProsodyCatalog.patterns
        assertEquals(3, patterns.size, "Must contain all 3 core sentence prosody patterns")

        for (pattern in patterns) {
            assertTrue(pattern.sentenceText.isNotBlank())
            assertTrue(pattern.referenceContour.isNotEmpty())
            assertTrue(pattern.syllables.isNotEmpty())
            assertTrue(pattern.weakForms.isNotEmpty())
            assertTrue(pattern.linkingFeatures.isNotEmpty())

            // Time coordinates of reference contour must be monotonic between 0.0 and 1.0
            var prevTime = -0.01f
            for (pt in pattern.referenceContour) {
                assertTrue(pt.first >= prevTime, "Reference contour time must be monotonic")
                assertTrue(pt.first in 0.0f..1.0f)
                assertTrue(pt.second in 80f..400f, "Reference F0 must be in human speech range")
                prevTime = pt.first
            }
        }
    }

    @Test
    fun testPitchTrackerDetectsFundamentalFrequency() {
        val sampleRate = 16000
        val targetFreq = 220f // A3
        val numSamples = sampleRate / 2 // 0.5s
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val phase = 2.0 * PI * targetFreq * i / sampleRate
            samples[i] = (sin(phase) * 15000.0).toInt().toShort()
        }

        val frames = PitchTracker.trackPitch(samples)
        val voiced = frames.filter { it.isVoiced }
        assertTrue(voiced.isNotEmpty(), "Synthesized tone must be detected as voiced")

        val avgPitch = voiced.map { it.f0Hz }.average().toFloat()
        assertEquals(targetFreq, avgPitch, 10f, "Measured pitch must match 220 Hz target within 10Hz")
    }

    @Test
    fun testProsodyEvaluationDistinguishesGoodMatchFromMonotone() {
        val pattern = ProsodyCatalog.getPatternById("ship_or_sheep_question")
        assertNotNull(pattern)

        // 1. Synthetic matching contour
        val matchingFrames = pattern.referenceContour.mapIndexed { idx, pt ->
            PitchFrame(timeSeconds = idx * 0.3f, f0Hz = pt.second, isVoiced = true)
        }
        val goodEval = PitchTracker.evaluateProsody(matchingFrames, pattern.referenceContour)
        assertTrue(goodEval.intonationScore >= 80f, "Matching contour must score high (got ${goodEval.intonationScore})")
        assertTrue(goodEval.overallScore >= 75f)

        // 2. Flat monotone contour (130 Hz flat)
        val flatFrames = pattern.referenceContour.mapIndexed { idx, _ ->
            PitchFrame(timeSeconds = idx * 0.3f, f0Hz = 130f, isVoiced = true)
        }
        val flatEval = PitchTracker.evaluateProsody(flatFrames, pattern.referenceContour)
        assertTrue(flatEval.intonationScore < goodEval.intonationScore, "Flat contour must score lower than matching contour")
    }
}
