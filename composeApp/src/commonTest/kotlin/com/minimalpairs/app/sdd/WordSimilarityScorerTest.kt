package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.dsp.WordSimilarityScorer
import kotlin.test.*

class WordSimilarityScorerTest {
    private val target = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 300)
    private val other = FormantSynthesizer.synthesizeVowel(750f, 1200f, durationMs = 300)

    @Test
    fun matchesReferenceAndRejectsOppositeReferenceWithoutDependingOnGainOrPadding() {
        assertEquals(100f, WordSimilarityScorer.score(target, target, other), .01f)
        assertEquals(0f, WordSimilarityScorer.score(other, target, other), .01f)
        val quieter = ShortArray(1600) + target.map { (it / 2).toShort() }.toShortArray() + ShortArray(1600)
        val quieterScore = WordSimilarityScorer.score(quieter, target, other)
        assertTrue(quieterScore > 85f, "Gain and silence changed score to $quieterScore")
    }

    @Test
    fun rejectsSilenceDcClicksAndOutOfBoundsDurationsButAcceptsUnvoicedEnergy() {
        for (input in listOf(ShortArray(0), ShortArray(3200), ShortArray(3200) { 2000 },
            ShortArray(100), ShortArray(480001), ShortArray(3200) { if (it == 1600) 20000 else 0 })) {
            assertFalse(WordSimilarityScorer.hasUsableAudio(input))
            assertFailsWith<IllegalArgumentException> { WordSimilarityScorer.score(input, target, other) }
        }
        assertTrue(WordSimilarityScorer.hasUsableAudio(ShortArray(3200) { if (it % 2 == 0) 2000 else -2000 }))
    }
}
