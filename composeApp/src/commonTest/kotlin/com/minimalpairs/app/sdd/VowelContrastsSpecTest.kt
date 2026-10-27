package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VowelContrastsSpecTest {

    @Test
    fun testVowelContrastsCatalogParsesAllSevenContrasts() {
        val contrasts = VowelContrastsCatalog.defaultContrasts
        assertEquals(7, contrasts.size, "Must contain all 7 target minimal pair contrasts")

        val ids = contrasts.map { it.id }.toSet()
        assertTrue(ids.contains("i_vs_I"))
        assertTrue(ids.contains("ae_vs_e"))
        assertTrue(ids.contains("a_vs_uh"))
        assertTrue(ids.contains("u_vs_uu"))
        assertTrue(ids.contains("o_vs_or"))
        assertTrue(ids.contains("e_vs_I"))
        assertTrue(ids.contains("ae_vs_uh"))
    }

    @Test
    fun testEachContrastHasAtLeastFourPairsAndValidAcousticBounds() {
        val contrasts = VowelContrastsCatalog.defaultContrasts
        for (contrast in contrasts) {
            assertTrue(contrast.pairs.size >= 4, "${contrast.id} must have >= 4 word pairs")

            // Validate Acoustic Formant Bounds F1 and F2
            val b1 = contrast.boundsPhoneme1
            val b2 = contrast.boundsPhoneme2

            assertTrue(b1.f1MinHz < b1.f1MaxHz, "${contrast.id} p1 F1 min must be < max")
            assertTrue(b1.f2MinHz < b1.f2MaxHz, "${contrast.id} p1 F2 min must be < max")
            assertTrue(b2.f1MinHz < b2.f1MaxHz, "${contrast.id} p2 F1 min must be < max")
            assertTrue(b2.f2MinHz < b2.f2MaxHz, "${contrast.id} p2 F2 min must be < max")

            // Visual indicators
            assertTrue(contrast.visualIndicators.lipApertureRatio in 0.05f..0.8f)
            assertTrue(contrast.visualIndicators.jawDropMinMm < contrast.visualIndicators.jawDropMaxMm)
        }
    }

    @Test
    fun catAndCutReuseEstablishedVowelBounds() {
        val contrast = assertNotNull(VowelContrastsCatalog.getContrastById("ae_vs_uh"))
        assertEquals("æ", contrast.phoneme1)
        assertEquals("ʌ", contrast.phoneme2)
        assertEquals("cat", contrast.pairs.first().word1)
        assertEquals("cut", contrast.pairs.first().word2)
        assertEquals(VowelContrastsCatalog.getContrastById("ae_vs_e")!!.boundsPhoneme1,
            contrast.boundsPhoneme1)
        assertEquals(VowelContrastsCatalog.getContrastById("a_vs_uh")!!.boundsPhoneme2,
            contrast.boundsPhoneme2)
    }

    @Test
    fun testSheepVsShipContrastSpecification() {
        val contrast = VowelContrastsCatalog.getContrastById("i_vs_I")
        assertNotNull(contrast)
        assertEquals("iː", contrast.phoneme1)
        assertEquals("ɪ", contrast.phoneme2)

        val pairWords = contrast.pairs.map { it.word1 to it.word2 }
        assertTrue(pairWords.contains("sheep" to "ship"))
        assertTrue(pairWords.contains("feet" to "fit"))
        assertTrue(pairWords.contains("seat" to "sit"))

        // /iː/ has higher F2 than /ɪ/
        assertTrue(contrast.boundsPhoneme1.f2MinHz >= contrast.boundsPhoneme2.f2MinHz)
    }
}
