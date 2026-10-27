package com.minimalpairs.app.sdd

import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SustainedVowelScorerTest {
    @Test
    fun selectedIpaUsesTheSameBoundsAsItsNamedWordForBothTargetsInEveryContrast() {
        val scorer = PhonemeScorerClient()
        for (contrast in VowelContrastsCatalog.defaultContrasts) {
            for (first in listOf(true, false)) {
                val ipa = if (first) contrast.phoneme1 else contrast.phoneme2
                val word = if (first) contrast.pairs.first().word1 else contrast.pairs.first().word2
                val bounds = if (first) contrast.boundsPhoneme1 else contrast.boundsPhoneme2
                val samples = FormantSynthesizer.synthesizeVowel(
                    (bounds.f1MinHz + bounds.f1MaxHz) / 2,
                    (bounds.f2MinHz + bounds.f2MaxHz) / 2, durationMs = 160
                )
                val named = scorer.evaluateLocalAcousticGop(samples, word, ipa, contrast.id)
                val sustained = scorer.evaluateLocalAcousticGop(samples, "vowel", ipa, contrast.id)
                assertEquals(ipa, sustained.phonemes.single().phoneme, contrast.id)
                assertEquals(named.overallScore, sustained.overallScore, "${contrast.id}: $ipa bounds")
            }
        }
    }

    @Test
    fun sameRecordingIsScoredAgainstDistinctSelectedTargets() {
        val scorer = PhonemeScorerClient()
        val samples = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 160)
        val sheep = scorer.evaluateLocalAcousticGop(samples, "vowel", "iː", "i_vs_I")
        val ship = scorer.evaluateLocalAcousticGop(samples, "vowel", "ɪ", "i_vs_I")
        assertNotEquals(sheep.overallScore, ship.overallScore)
    }
    @Test
    fun fullWordTranscriptsKeepWordLookupAndUnknownWordsDoNotSelectTheSecondSound() {
        val scorer = PhonemeScorerClient()
        val samples = FormantSynthesizer.synthesizeVowel(310f, 2350f, durationMs = 160)
        val sheep = scorer.evaluateLocalAcousticGop(samples, "SHEEP", "ʃ iː p", "i_vs_I")
        val sustained = scorer.evaluateLocalAcousticGop(samples, "vowel", "iː", "i_vs_I")
        assertEquals(sheep, sustained)
        val unknown = scorer.evaluateLocalAcousticGop(samples, "unknown", "?", "i_vs_I")
        assertEquals("?", unknown.phonemes.single().phoneme)
        assertEquals(75f, unknown.overallScore)
        val explicit = scorer.evaluateLocalAcousticGop(samples, "ship", "iː", "i_vs_I")
        assertEquals(sustained, explicit, "An exact selected IPA is authoritative")
    }

}
