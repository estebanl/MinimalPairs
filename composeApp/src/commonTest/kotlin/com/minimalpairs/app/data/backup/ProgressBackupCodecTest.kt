package com.minimalpairs.app.data.backup

import com.minimalpairs.app.data.local.*
import kotlin.test.*

class ProgressBackupCodecTest {
    private val snapshot = ProgressSnapshot(
        listOf(TrialRecordEntity(-12, "i_vs_I", "sheep", "ship", false, 800, "FORCED_CHOICE", 1000)),
        listOf(Sm2CardEntity("i_vs_I", 0, 1, 2.3f, .47f, 1000)),
        listOf(SpeakingRecordEntity(-13, "i_vs_I", "ship", "ɪ", 78f, 500, 2000)),
        listOf(ModulePracticeRecordEntity(-14, "RHYTHM", "pattern", "Question?", null, 83f, true, 1000, 3000))
    )

    @Test
    fun roundTripPreservesAllSavedDataIncludingStableIdsAndUnicode() {
        val encoded = ProgressBackupCodec.encode(snapshot, 4000)
        assertEquals(snapshot, ProgressBackupCodec.decode(encoded))
        assertTrue(encoded.contains("minimal-pairs-progress"))
        assertFalse(encoded.contains("audio"))
    }

    @Test
    fun incompatibleMalformedAndUnknownDataAreRejected() {
        val encoded = ProgressBackupCodec.encode(snapshot, 4000)
        assertFails { ProgressBackupCodec.decode("not json") }
        assertFails { ProgressBackupCodec.decode(encoded.replace("\"version\":1", "\"version\":99")) }
        assertFails { ProgressBackupCodec.decode(encoded.replace("minimal-pairs-progress", "another-app")) }
        assertFails { ProgressBackupCodec.decode(encoded.replaceFirst("{", "{\"unexpected\":true,")) }
        assertFails { ProgressBackupCodec.decode(encoded.replace("78.0", "101.0")) }
    }

    @Test
    fun invalidScoresIdsDuplicatesAndDatesAreRejectedBeforeImport() {
        assertFails { ProgressBackupCodec.validate(snapshot.copy(trials = snapshot.trials + snapshot.trials)) }
        assertFails { ProgressBackupCodec.validate(snapshot.copy(speaking = listOf(snapshot.speaking.single().copy(id = 0)))) }
        assertFails { ProgressBackupCodec.validate(snapshot.copy(speaking = listOf(snapshot.speaking.single().copy(score = Float.NaN)))) }
        assertFails { ProgressBackupCodec.validate(snapshot.copy(trials = listOf(snapshot.trials.single().copy(timestampEpochMs = Long.MAX_VALUE)))) }
        assertFails { ProgressBackupCodec.validate(snapshot.copy(cards = listOf(snapshot.cards.single().copy(easeFactor = Float.POSITIVE_INFINITY)))) }
        assertFails { ProgressBackupCodec.validate(snapshot.copy(modules = listOf(snapshot.modules.single().copy(module = "UNKNOWN")))) }
    }

    @Test
    fun unknownCatalogTargetsArePreservedForOlderBackupsAndEmptyBackupsAreValid() {
        val old = snapshot.copy(speaking = listOf(snapshot.speaking.single().copy(contrastId = "retired-contrast")))
        assertEquals(old, ProgressBackupCodec.decode(ProgressBackupCodec.encode(old)))
        val empty = ProgressSnapshot(emptyList(), emptyList(), emptyList())
        assertEquals(empty, ProgressBackupCodec.decode(ProgressBackupCodec.encode(empty)))
    }
}
