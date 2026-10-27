package com.minimalpairs.app.data.progress

import androidx.room3.Room
import com.minimalpairs.app.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Files
import kotlin.test.*

class ProgressDeltaTest {
    @Test
    fun appendReadsOnlyChangedRecordsAndRetainsUnchangedHistoryLists() = runBlocking {
        val directory = Files.createTempDirectory("progress-delta").toFile()
        val db = createDatabase(Room.databaseBuilder<AppDatabase>(name = directory.resolve("progress.db").absolutePath))
        try {
            val dao = db.progressDao()
            repeat(150) { index -> dao.record(TrialRecordEntity(-(index + 1L), "i_vs_I", "sheep", "sheep", true, 200, "FORCED_CHOICE", index * 1000L), 5) }
            val version = dao.readVersionedSnapshot()
            val recording = SpeakingRecordEntity(-1, "i_vs_I", "ship", "ɪ", 80f, 200, 200000)
            val initial = CompletableDeferred<ProgressSnapshot>()
            val later = async {
                withTimeout(5000) {
                    RoomProgressStore(db).observe().onEach { initial.complete(it) }.first { it.speaking.isNotEmpty() }
                }
            }
            val before = initial.await()
            dao.recordSpeaking(recording)
            val after = later.await()
            assertSame(before.trials, after.trials)
            assertEquals(150, after.trials.size)
            val delta = dao.readDelta(version.sequence)
            assertFalse(delta.reset)
            assertTrue(delta.trials.isEmpty())
            assertEquals(listOf(recording), delta.speaking)
            assertEquals(1, dao.changesAfter(version.sequence).size)
        } finally { db.close(); directory.deleteRecursively() }
    }

    @Test
    fun coalescedCardDeletionAndTrackedAppendDoesNotRetainRemovedReview() = runBlocking {
        val directory = Files.createTempDirectory("progress-delta-card-delete").toFile()
        val db = createDatabase(Room.databaseBuilder<AppDatabase>(name = directory.resolve("progress.db").absolutePath))
        try {
            val dao = db.progressDao()
            dao.record(TrialRecordEntity(-1, "i_vs_I", "sheep", "sheep", true, 200, "FORCED_CHOICE", 1000), 5)
            val before = dao.readVersionedSnapshot()
            assertEquals(1, before.snapshot.cards.size)
            // Read once after both writes, as a conflated invalidation observer does.
            dao.clearCards()
            dao.recordSpeaking(SpeakingRecordEntity(-2, "i_vs_I", "ship", "ɪ", 80f, 200, 2000))
            val delta = dao.readDelta(before.sequence)
            assertFalse(delta.reset)
            val after = applyProgressDelta(before.snapshot, delta)
            assertTrue(after.cards.isEmpty())
            assertEquals(1, after.speaking.size)
            assertSame(before.snapshot.trials, after.trials)
        } finally { db.close(); directory.deleteRecursively() }
    }

    @Test
    fun importsRejectConflictsAtomicallyAndResetDropsAllKindsAndQueue() = runBlocking {
        val directory = Files.createTempDirectory("progress-import").toFile()
        val db = createDatabase(Room.databaseBuilder<AppDatabase>(name = directory.resolve("progress.db").absolutePath))
        try {
            val dao = db.progressDao()
            val speaking = SpeakingRecordEntity(-1, "i_vs_I", "ship", "ɪ", 80f, 200, 1000)
            val module = ModulePracticeRecordEntity(-2, "RHYTHM", "pattern", "Pattern", null, 70f, true, 200, 1000)
            val first = ProgressSnapshot(emptyList(), emptyList(), listOf(speaking), listOf(module))
            dao.importSnapshot(first)
            dao.importSnapshot(first)
            assertEquals(first, dao.readSnapshot())
            val other = speaking.copy(id = -3)
            assertFailsWith<IllegalStateException> {
                dao.importSnapshot(first.copy(speaking = listOf(other, speaking.copy(score = 20f))))
            }
            assertEquals(first, dao.readSnapshot())
            val store = RoomProgressStore(db)
            store.stage(PendingProgressWrite(speaking = other))
            val sequence = dao.changeSequence()
            dao.resetProgress()
            val empty = dao.readSnapshot()
            assertTrue(empty.trials.isEmpty() && empty.speaking.isEmpty() && empty.modules.isEmpty() && empty.cards.isEmpty())
            assertTrue(store.readPending().isEmpty())
            assertTrue(dao.readDelta(sequence).reset)
        } finally { db.close(); directory.deleteRecursively() }
    }
}
