package com.minimalpairs.app.data.progress

import androidx.room3.Room
import com.minimalpairs.app.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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

    @Test
    fun loadRetryKeepsLastTotalsAndReconnectsToLiveChanges() = runBlocking {
        withDatabase { database, scope ->
            val dao = database.progressDao()
            dao.record(trial("sheep"), 5)
            val flaky = FlakyDao(dao).apply { failReads.value = true }
            val repository = ProgressRepository(flaky, scope)
            val failed = withTimeout(5000) { repository.state.first { it.canRetryLoad } }
            assertFalse(failed.loading)
            assertNotNull(failed.error)
            flaky.failReads.value = false
            repository.retryLoading()
            withTimeout(5000) { repository.state.first { it.attempts == 1 && !it.canRetryLoad } }
            flaky.failReads.value = true
            val stale = withTimeout(5000) { repository.state.first { it.canRetryLoad } }
            assertEquals(1, stale.attempts)
            assertTrue(stale.error!!.contains("last loaded totals"))
            dao.record(trial("ship"), 2)
            flaky.failReads.value = false
            repository.retryLoading()
            repository.retryLoading()
            val recovered = withTimeout(5000) { repository.state.first { it.attempts == 2 && !it.canRetryLoad } }
            assertNull(recovered.error)
            dao.record(trial("sheep"), 5)
            withTimeout(5000) { repository.state.first { it.attempts == 3 } }
        }
    }

    @Test
    fun loadAndSaveFailuresRecoverIndependently() = runBlocking {
        withDatabase { database, scope ->
            val flaky = FlakyDao(database.progressDao()).apply {
                failReads.value = true
                failBefore = true
            }
            val repository = ProgressRepository(flaky, scope)
            repository.record(trial("sheep"), 5)
            withTimeout(5000) { repository.state.first { it.canRetryLoad && it.canRetrySave && !it.isSaving } }
            flaky.failReads.value = false
            repository.retryLoading()
            val reloaded = withTimeout(5000) { repository.state.first { !it.canRetryLoad && it.canRetrySave } }
            assertEquals(1, reloaded.pendingAnswers)
            assertEquals(0, reloaded.attempts)
            flaky.failBefore = false
            repository.retrySaves()
            val recovered = withTimeout(5000) { repository.state.first { it.attempts == 1 && it.pendingAnswers == 0 } }
            assertNull(recovered.error)
        }
    }

    @Test
    fun speakingSaveRetriesWithoutDuplicatingACommittedResult() = runBlocking {
        withDatabase { database, scope ->
            val flaky = FlakyDao(database.progressDao()).apply { failAfter = true }
            val repository = ProgressRepository(flaky, scope)
            repository.recordSpeaking(SpeakingRecordEntity(0, "i_vs_I", "sheep", "iː", 88f, 200, 1000))
            withTimeout(5000) { repository.state.first { it.canRetrySave && !it.isSaving } }
            flaky.failAfter = false
            repository.retrySaves()
            val recovered = withTimeout(5000) { repository.state.first { it.pendingAnswers == 0 && it.speaking.size == 1 } }
            assertEquals(0, recovered.attempts)
            assertEquals(1, database.progressDao().observeSpeaking().first().size)
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
        val failReads = MutableStateFlow(false)
        override fun observeTrials() = combine(delegate.observeTrials(), failReads) { trials, failed ->
            check(!failed) { "Simulated read failure" }
            trials
        }
        override fun observeCards() = delegate.observeCards()
        override fun observeSpeaking() = delegate.observeSpeaking()
        override suspend fun getSpeaking(id: Long) = delegate.getSpeaking(id)
        override suspend fun insertSpeaking(record: SpeakingRecordEntity) = delegate.insertSpeaking(record)
        override suspend fun getCard(id: String) = delegate.getCard(id)
        override suspend fun getTrial(id: Long) = delegate.getTrial(id)
        override suspend fun insertTrial(trial: TrialRecordEntity) = delegate.insertTrial(trial)
        override suspend fun saveCard(card: Sm2CardEntity) = delegate.saveCard(card)
        override suspend fun recordSpeaking(record: SpeakingRecordEntity) {
            if (failBefore) error("Simulated storage failure")
            delegate.recordSpeaking(record)
            if (failAfter) error("Simulated lost commit acknowledgement")
        }
        override suspend fun record(trial: TrialRecordEntity, quality: Int) {
            if (failBefore) error("Simulated storage failure")
            delegate.record(trial, quality)
            if (failAfter) error("Simulated lost commit acknowledgement")
        }
    }
}
