package com.minimalpairs.app.data.progress

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.serialization.json.*
import java.io.File
import com.minimalpairs.app.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import kotlin.test.*

class ProgressDurabilityTest {
    private fun trial(id: Long = -1) = TrialRecordEntity(id, "i_vs_I", "sheep", "sheep", true, 200, "FORCED_CHOICE", 1000)
    private fun database(path: String) = createDatabase(Room.databaseBuilder<AppDatabase>(name = path))

    @Test
    fun versionTwoMigrationPreservesSpeakingAndAddsDurableQueue() = runBlocking {
        val directory = Files.createTempDirectory("progress-v2-migration").toFile()
        val path = directory.resolve("progress.db").absolutePath
        val schema = Json.parseToJsonElement(File("schemas/com.minimalpairs.app.data.local.AppDatabase/2.json").readText())
            .jsonObject.getValue("database").jsonObject
        BundledSQLiteDriver().open(path).use { connection ->
            fun execute(sql: String) { connection.prepare(sql).use { it.step() } }
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject
                execute(table.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content))
            }
            schema.getValue("setupQueries").jsonArray.forEach { execute(it.jsonPrimitive.content) }
            execute("INSERT INTO speaking_records VALUES (-1, 'i_vs_I', 'ship', 'ɪ', 80, 200, 1000)")
            execute("PRAGMA user_version = 2")
        }
        val db = database(path)
        try {
            assertEquals(80f, db.progressDao().readSpeaking().single().score)
            val store = RoomProgressStore(db)
            assertTrue(store.stage(PendingProgressWrite(trial = trial(), quality = 5)))
            assertEquals(1, store.readPending().size)
            assertTrue(db.progressDao().readModules().isEmpty())
        } finally { db.close(); directory.deleteRecursively() }
    }

    @Test
    fun stagedResultsReplayAfterDatabaseAndRepositoryReopenExactlyOnce() = runBlocking {
        val directory = Files.createTempDirectory("durable-progress").toFile()
        val path = directory.resolve("progress.db").absolutePath
        var db = database(path)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val room = RoomProgressStore(db)
            val brokenCommit = object : ProgressStore by room {
                override suspend fun finish(write: PendingProgressWrite) = error("Simulated commit failure")
            }
            val repository = ProgressRepository(brokenCommit, scope)
            repository.record(trial(), 5)
            val pending = withTimeout(5000) { repository.state.first { it.canRetrySave && it.durablePendingAnswers == 1 && !it.isSaving } }
            assertTrue(pending.error!!.contains("will be retried when the app reopens"))
            assertEquals(1, db.progressDao().readPendingWrites().size)
            assertTrue(db.progressDao().readTrials().isEmpty())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            db = database(path)
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val reopened = ProgressRepository(RoomProgressStore(db), scope)
            withTimeout(5000) { reopened.state.first { it.attempts == 1 && it.pendingAnswers == 0 && !it.isSaving } }
            reopened.retrySaves()
            withTimeout(5000) { reopened.state.first { !it.isSaving } }
            assertEquals(listOf(trial()), db.progressDao().readTrials())
            assertEquals(1, db.progressDao().getCard("i_vs_I")!!.repetitions)
            assertTrue(db.progressDao().readPendingWrites().isEmpty())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun stagingFailureIsClearlyMemoryOnlyAndResetCannotReplayOldEntries() = runBlocking {
        val directory = Files.createTempDirectory("pending-reset").toFile()
        val db = database(directory.resolve("progress.db").absolutePath)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val room = RoomProgressStore(db)
            val failStaging = java.util.concurrent.atomic.AtomicBoolean(true)
            val store = object : ProgressStore by room {
                override suspend fun stage(write: PendingProgressWrite): Boolean {
                    check(!failStaging.get()) { "Simulated disk full" }
                    return room.stage(write)
                }
            }
            val repository = ProgressRepository(store, scope)
            repository.record(trial(), 5)
            val failed = withTimeout(5000) { repository.state.first { it.canRetrySave && !it.isSaving } }
            assertEquals(0, failed.durablePendingAnswers)
            assertTrue(failed.error!!.contains("Keep the app open"))
            assertFailsWith<IllegalStateException> { repository.exportSnapshot() }
            repository.resetProgress()
            failStaging.set(false)
            repository.retrySaves()
            withTimeout(5000) { repository.state.first { !it.isSaving && it.pendingAnswers == 0 } }
            assertTrue(db.progressDao().readTrials().isEmpty())
            assertTrue(db.progressDao().readPendingWrites().isEmpty())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin(); db.close(); directory.deleteRecursively()
        }
    }

    @Test
    fun resetErasesOldQueueButPreservesAResultAcceptedDuringReset() = runBlocking {
        val directory = Files.createTempDirectory("reset-race").toFile()
        val db = database(directory.resolve("progress.db").absolutePath)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val room = RoomProgressStore(db)
            val resetStarted = CompletableDeferred<Unit>()
            val allowReset = CompletableDeferred<Unit>()
            val failFinish = java.util.concurrent.atomic.AtomicBoolean(true)
            val store = object : ProgressStore by room {
                override suspend fun finish(write: PendingProgressWrite) {
                    check(!failFinish.get())
                    room.finish(write)
                }
                override suspend fun resetProgress() {
                    resetStarted.complete(Unit)
                    allowReset.await()
                    room.resetProgress()
                }
            }
            val repository = ProgressRepository(store, scope)
            repository.record(trial(-1), 5)
            withTimeout(5000) { repository.state.first { it.canRetrySave && !it.isSaving } }
            assertFailsWith<IllegalStateException> { repository.importSnapshot(ProgressSnapshot(emptyList(), emptyList(), emptyList())) }
            val resetting = async { repository.resetProgress() }
            resetStarted.await()
            repository.record(trial(-2), 5)
            failFinish.set(false)
            allowReset.complete(Unit)
            resetting.await()
            withTimeout(5000) { repository.state.first { it.attempts == 1 && it.pendingAnswers == 0 && !it.isSaving } }
            assertEquals(listOf(-2L), db.progressDao().readTrials().map { it.id })
            assertTrue(db.progressDao().readPendingWrites().isEmpty())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close(); directory.deleteRecursively() }
    }

    @Test
    fun atomicQueueCompletionSupportsSpeakingAndModules() = runBlocking {
        val directory = Files.createTempDirectory("pending-kinds").toFile()
        val db = database(directory.resolve("progress.db").absolutePath)
        try {
            val store = RoomProgressStore(db)
            val speaking = PendingProgressWrite(speaking = SpeakingRecordEntity(-1, "i_vs_I", "ship", "ɪ", 80f, 200, 1000))
            val module = PendingProgressWrite(module = ModulePracticeRecordEntity(-2, "RHYTHM", "pattern", "Pattern", null, 70f, true, 200, 1000))
            listOf(speaking, module).forEach { write ->
                assertTrue(store.stage(write))
                store.stage(write)
                assertEquals(write, store.readPending().last())
                store.finish(write)
                store.finish(write)
            }
            assertTrue(store.readPending().isEmpty())
            assertEquals(1, db.progressDao().readSpeaking().size)
            assertEquals(1, db.progressDao().readModules().size)
        } finally { db.close(); directory.deleteRecursively() }
    }
}
