package com.minimalpairs.app.sdd

import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ListeningContrastsSpecTest {
    @Test
    fun everyDocumentSampleBelongsToItsSoundContrastWithAlignedWords() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "ship_or_sheep_minimal_pairs.md").isFile }
        val heading = Regex("^#{3,4} /([^/]+)/ vs\\. /([^/]+)/")
        val example = Regex("^\\* \\*\\*(.*?)\\*\\* /([^/]+)/ vs\\. \\*\\*(.*?)\\*\\* /([^/]+)/")
        var phonemes = emptySet<String>()
        var sampleCount = 0
        File(root, "ship_or_sheep_minimal_pairs.md").forEachLine { line ->
            heading.find(line)?.let { phonemes = setOf(it.groupValues[1], it.groupValues[2]) }
            example.find(line)?.let { match ->
                val (_, word1, ipa1, word2, ipa2) = match.groupValues
                val contrast = assertNotNull(ListeningContrastsCatalog.defaultContrasts.singleOrNull {
                    setOf(it.phoneme1, it.phoneme2) == phonemes
                }, "Missing contrast $phonemes")
                val pair = assertNotNull(contrast.pairs.singleOrNull {
                    setOf(it.word1.lowercase(), it.word2.lowercase()) == setOf(word1.lowercase(), word2.lowercase())
                }, "Missing sample $word1 / $word2 in ${contrast.id}")
                val sameOrder = pair.word1.equals(word1, ignoreCase = true)
                assertTrue((if (sameOrder) ipa1 else ipa2).contains(contrast.phoneme1), "$pair: first phoneme")
                assertTrue((if (sameOrder) ipa2 else ipa1).contains(contrast.phoneme2), "$pair: second phoneme")
                sampleCount++
            }
        }
        assertEquals(60, sampleCount, "All source samples must be parsed")
    }

    @Test
    fun listeningRetainsExistingExercisesWithoutDuplicateIdsOrPairs() {
        val contrasts = ListeningContrastsCatalog.defaultContrasts
        assertEquals(20, contrasts.size)
        assertEquals(76, contrasts.sumOf { it.pairs.size })
        assertEquals(contrasts.size, contrasts.map { it.id }.toSet().size)
        for (contrast in contrasts) {
            assertTrue(contrast.pairs.isNotEmpty(), contrast.id)
            assertTrue(contrast.soundTip.isNotBlank(), contrast.id)
            assertEquals(contrast.pairs.size, contrast.pairs.map {
                setOf(it.word1.lowercase(), it.word2.lowercase())
            }.toSet().size, contrast.id)
        }
        for (vowel in VowelContrastsCatalog.defaultContrasts) {
            val listening = assertNotNull(ListeningContrastsCatalog.getContrastById(vowel.id))
            assertEquals(vowel, listening.vowelReference)
            for (pair in vowel.pairs) {
                assertTrue(listening.pairs.any {
                    it.word1.equals(pair.word1, ignoreCase = true) && it.word2.equals(pair.word2, ignoreCase = true)
                }, "Existing sample $pair must remain available")
            }
        }
    }
}
