package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.ui.reference.ConsonantModelReferences
import com.minimalpairs.app.ui.reference.ConsonantPracticeReferences
import kotlin.test.*

class ConsonantModelReferencesTest {
    @Test fun everyConsonantHasAnExplicitCoverageDecision() {
        val sounds = ListeningContrastsCatalog.defaultContrasts.filter { it.vowelReference == null }
            .flatMap { listOf(it.phoneme1, it.phoneme2) }.toSet()
        assertEquals(19, sounds.size)
        assertEquals(sounds, ConsonantModelReferences.byPhoneme.keys)
        assertNull(ConsonantModelReferences.forPhoneme("unknown"))
        ConsonantModelReferences.byPhoneme.forEach { (sound, reference) ->
            assertNotNull(ConsonantPracticeReferences.forPhoneme(sound))
            assertTrue(reference.explanation.contains("unreviewed", ignoreCase = true))
            assertTrue(reference.phases.all { it.assetKey.isNotBlank() && it.label.isNotBlank() && it.description.isNotBlank() })
        }
    }
    @Test fun rhoticVariantsAreAlternativesRatherThanCompulsoryOrTimedPhases() {
        val reference = ConsonantModelReferences.forPhoneme("r")!!
        assertEquals(listOf("Bunched", "Tip-up"), reference.phases.map { it.label })
        assertEquals(2, reference.phases.map { it.assetKey }.toSet().size)
        assertTrue(reference.explanation.contains("Neither tongue shape is obligatory"))
        assertTrue(reference.explanation.contains("not matched to the word audio"))
    }
    @Test fun affricatesExposeClosureAndReleaseInsteadOfPretendingOnePoseIsTheSound() {
        for (sound in listOf("tʃ", "dʒ")) {
            val phases = ConsonantModelReferences.forPhoneme(sound)!!.phases
            assertEquals(listOf("Affricate closure", "Fricative release"), phases.map { it.label })
            assertEquals(2, phases.map { it.assetKey }.toSet().size)
            assertEquals(ConsonantModelReferences.forPhoneme("ʃ")!!.phases.single().assetKey, phases[1].assetKey)
        }
    }
}
