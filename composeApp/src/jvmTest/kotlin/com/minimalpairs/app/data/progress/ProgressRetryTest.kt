package com.minimalpairs.app.data.progress

import androidx.room3.Room
import com.minimalpairs.app.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import kotlin.test.*

class ProgressRetryTest {
    @Test
    fun failedAnswersStayQueuedInOrderAndRepeatedRetriesDoNotDuplicateThem() = runBlocking {
        withDatabase { database, scope ->
            val flaky = FlakyDao(database.progressDao()).apply { failBefore = true }
            val repository = ProgressRepository(flaky, scope)
            repository.record(trial("sheep"), 5)
            withTimeout(5000) { repository.state.first { it.canRetrySave && !it.isSaving } }
            repository.record(trial("ship"), 2)
            val failed = withTimeout(5000) { repository.state.first { it.pendingAnswers == 2 && !it.isSaving } }
            assertEquals(0, failed.attempts)
            assertNotNull(failed.error)
            flaky.failBefore = false
            repository.retrySaves()
            repository.retrySaves()
            val saved = withTimeout(5000) { repository.state.first { it.attempts == 2 && it.pendingAnswers == 0 && !it.isSaving } }
            assertFalse(saved.canRetrySave)
            assertNull(saved.error)
            assertEquals(2, database.progressDao().observeTrials().first().size)
            // Correct followed by incorrect must end with zero successful repetitions.
            assertEquals(0, database.progressDao().getCard("i_vs_I")!!.repetitions)
        }
    }

    @Test
    fun retryAfterCommitDoesNotApplyMasteryTwice() = runBlocking {
        withDatabase { database, scope ->
            val flaky = FlakyDao(database.progressDao()).apply { failAfter = true }
            val repository = ProgressRepository(flaky, scope)
            repository.record(trial("sheep"), 5)
            withTimeout(5000) { repository.state.first { it.canRetrySave && !it.isSaving } }
            assertEquals(1, database.progressDao().observeTrials().first().size)
            flaky.failAfter = false
            repository.retrySaves()
            withTimeout(5000) { repository.state.first { it.pendingAnswers == 0 && !it.isSaving } }
            assertEquals(1, database.progressDao().observeTrials().first().size)
            assertEquals(1, database.progressDao().getCard("i_vs_I")!!.repetitions)
        }
    }

    private suspend fun withDatabase(block: suspend (AppDatabase, CoroutineScope) -> Unit) {
        val directory = Files.createTempDirectory("progress-retry").toFile()
        val database = createDatabase(Room.databaseBuilder<AppDatabase>(name = directory.resolve("progress.db").absolutePath))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { block(database, scope) } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            directory.deleteRecursively()
        }
    }

    private fun trial(selected: String) = TrialRecordEntity(
        contrastId = "i_vs_I", targetWord = "sheep", selectedWord = selected, isCorrect = selected == "sheep",
        latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = 1000
    )

    private class FlakyDao(private val delegate: ProgressDao) : ProgressDao() {
        @Volatile var failBefore = false
        @Volatile var failAfter = false
        override fun observeTrials() = delegate.observeTrials()
        override fun observeCards() = delegate.observeCards()
        override suspend fun getCard(id: String) = delegate.getCard(id)
        override suspend fun getTrial(id: Long) = delegate.getTrial(id)
        override suspend fun insertTrial(trial: TrialRecordEntity) = delegate.insertTrial(trial)
        override suspend fun saveCard(card: Sm2CardEntity) = delegate.saveCard(card)
        override suspend fun record(trial: TrialRecordEntity, quality: Int) {
            if (failBefore) error("Simulated storage failure")
            delegate.record(trial, quality)
            if (failAfter) error("Simulated lost commit acknowledgement")
        }
    }
}
