package com.minimalpairs.app.data.progress

import androidx.room3.Room
import com.minimalpairs.app.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.test.*

class ProgressSnapshotTest {
    @Test
    fun concurrentWritesNeverExposeNewAnswerCountsWithOldMastery() = runBlocking {
        val directory = Files.createTempDirectory("progress-snapshot").toFile()
        val database = createDatabase(Room.databaseBuilder<AppDatabase>(name = directory.resolve("progress.db").absolutePath))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = RoomProgressStore(database)
            val repository = ProgressRepository(store, scope)
            val ready = CompletableDeferred<Unit>()
            val observed = async {
                withTimeout(10_000) {
                    repository.state.filter { !it.loading }.onEach { progress ->
                        assertNull(progress.error)
                        assertEquals(progress.attempts, progress.contrasts.singleOrNull()?.card?.repetitions ?: 0)
                        ready.complete(Unit)
                    }.first { it.attempts == 30 && it.speaking.size == 1 }
                }
            }
            ready.await()
            coroutineScope {
                (1..30).map { index -> async(Dispatchers.Default) {
                    store.record(TrialRecordEntity(
                        id = index.toLong(), contrastId = "i_vs_I", targetWord = "sheep", selectedWord = "sheep",
                        isCorrect = true, latencyMs = 1000, drillType = "FORCED_CHOICE", timestampEpochMs = index * 1000L
                    ), 5)
                } }.awaitAll()
            }
            store.recordSpeaking(SpeakingRecordEntity(-1, "i_vs_I", "sheep", "iː", 88f, 200, 31_000))
            val final = observed.await()
            assertEquals(30, final.attempts)
            assertEquals(100, final.accuracy)
            assertEquals(88f, final.speaking.single().score)
            assertEquals(88, final.speakingSummaries.single().recentAverage)
            assertEquals(final.speaking.single(), final.speakingSummaries.single().latest)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun summariesAreComputedOffTheUiDispatcher() = runBlocking {
        val ui = Executors.newSingleThreadExecutor { Thread(it, "progress-test-ui") }.asCoroutineDispatcher()
        val worker = Executors.newSingleThreadExecutor { Thread(it, "progress-test-summary") }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + ui)
        val threads = ConcurrentLinkedQueue<String>()
        val speakingThreads = ConcurrentLinkedQueue<String>()
        val recordings = object : AbstractList<SpeakingRecordEntity>() {
            override val size = 10
            override fun get(index: Int): SpeakingRecordEntity {
                speakingThreads.add(Thread.currentThread().name)
                return SpeakingRecordEntity(index.toLong(), "i_vs_I", "sheep", "iː",
                    if (index < 5) 40f else 80f, 200, index * 1000L)
            }
        }
        val store = object : ProgressStore {
            override fun observe() = flowOf(ProgressSnapshot(emptyList(), emptyList(), recordings))
            override suspend fun record(trial: TrialRecordEntity, quality: Int) = error("Not used")
            override suspend fun recordSpeaking(record: SpeakingRecordEntity) = error("Not used")
        }
        try {
            val repository = ProgressRepository(store, scope,
                nowEpochMs = { threads.add(Thread.currentThread().name); 1000L },
                aggregationDispatcher = worker)
            val loaded = withTimeout(5000) { repository.state.first { !it.loading } }
            assertEquals(40, loaded.speakingSummaries.single().changePoints)
            assertTrue(speakingThreads.isNotEmpty())
            assertTrue(speakingThreads.all { it.startsWith("progress-test-summary") }, speakingThreads.toString())
            assertTrue(threads.any { it.startsWith("progress-test-summary") }, threads.toString())
            assertTrue(threads.any { it.startsWith("progress-test-ui") }, threads.toString())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            worker.close()
            ui.close()
        }
    }
}
