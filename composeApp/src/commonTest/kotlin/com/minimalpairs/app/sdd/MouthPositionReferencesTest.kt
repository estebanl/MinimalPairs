package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.ui.reference.MouthPositionReferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MouthPositionReferencesTest {
    @Test
    fun everySupportedSoundHasItsOwnPairOfViewsAndAccessibleGuidance() {
        val phonemes = VowelContrastsCatalog.defaultContrasts
            .flatMap { listOf(it.phoneme1, it.phoneme2) }.toSet()
        assertEquals(phonemes, MouthPositionReferences.byPhoneme.keys)
        val references = phonemes.map { phoneme ->
            assertNotNull(MouthPositionReferences.forPhoneme(phoneme), phoneme).also {
                assertTrue(it.placementCue.isNotBlank(), phoneme)
                assertTrue(it.frontDescription.isNotBlank(), phoneme)
                assertTrue(it.sideDescription.isNotBlank(), phoneme)
                assertNotEquals(it.front, it.side, phoneme)
            }
        }
        assertEquals(phonemes.size, references.map { it.assetKey }.toSet().size)
        assertEquals(phonemes.size * 2, references.flatMap { listOf(it.front, it.side) }.toSet().size)
    }

    @Test
    fun switchingContrastKeepsTheSameReferenceForSharedSounds() {
        val sheepShip = VowelContrastsCatalog.getContrastById("i_vs_I")!!
        val penPin = VowelContrastsCatalog.getContrastById("e_vs_I")!!
        val badBed = VowelContrastsCatalog.getContrastById("ae_vs_e")!!
        assertSame(
            MouthPositionReferences.forPhoneme(sheepShip.phoneme2),
            MouthPositionReferences.forPhoneme(penPin.phoneme2)
        )
        assertSame(
            MouthPositionReferences.forPhoneme(badBed.phoneme2),
            MouthPositionReferences.forPhoneme(penPin.phoneme1)
        )
        for (contrast in VowelContrastsCatalog.defaultContrasts) {
            assertNotEquals(
                MouthPositionReferences.forPhoneme(contrast.phoneme1),
                MouthPositionReferences.forPhoneme(contrast.phoneme2),
                contrast.id
            )
        }
    }

    @Test
    fun unsupportedSoundsDoNotDisplayAnUnrelatedPose() {
        assertNull(MouthPositionReferences.forPhoneme("ə"))
    }
}
