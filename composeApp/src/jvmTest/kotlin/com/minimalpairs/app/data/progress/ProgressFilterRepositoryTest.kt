package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.*

class ProgressFilterRepositoryTest {
    @Test
    fun failedAggregationRetainsMatchingFilterLabelsAndRecovers() = runBlocking {
        val failSummary = java.util.concurrent.atomic.AtomicBoolean(false)
        val records = object : AbstractList<TrialRecordEntity>() {
            override val size = 1
            override fun get(index: Int): TrialRecordEntity {
                check(!failSummary.get()) { "Simulated aggregation failure" }
                return TrialRecordEntity(1, "i_vs_I", "sheep", "sheep", true, 200, "FORCED_CHOICE", 1000)
            }
        }
        val store = object : ProgressStore {
            override fun observe() = flowOf(ProgressSnapshot(records, emptyList(), emptyList()))
            override suspend fun record(trial: TrialRecordEntity, quality: Int) = error("Unused")
            override suspend fun recordSpeaking(record: SpeakingRecordEntity) = error("Unused")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = ProgressRepository(store, scope,
                calendar = flowOf(PracticeCalendar(LocalDate(1970, 1, 1), TimeZone.UTC)), nowEpochMs = { 1000 }, refresh = flowOf(Unit))
            withTimeout(5000) { repository.state.first { it.attempts == 1 } }
            failSummary.set(true)
            repository.setContrastFilter("p_vs_b")
            val stale = withTimeout(5000) { repository.state.first { it.canRetryLoad } }
            assertEquals(1, stale.attempts)
            assertNull(stale.filters.contrastId)
            assertNotNull(stale.error)
            failSummary.set(false)
            repository.retryLoading()
            val recovered = withTimeout(5000) { repository.state.first { it.filters.contrastId == "p_vs_b" && !it.canRetryLoad } }
            assertEquals(0, recovered.attempts)
            assertNull(recovered.error)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test
    fun changingFiltersUsesCachedSnapshotAfterReadFailureAndDoesNotReopenStore() = runBlocking {
        val failed = MutableStateFlow(false)
        var observations = 0
        val trials = listOf("i_vs_I", "p_vs_b").mapIndexed { index, id ->
            TrialRecordEntity(index.toLong(), id, "sheep", "sheep", true, 200, "FORCED_CHOICE", 1000)
        }
        val store = object : ProgressStore {
            override fun observe(): Flow<ProgressSnapshot> {
                observations++
                return failed.map { check(!it); ProgressSnapshot(trials, emptyList(), emptyList()) }
            }
            override suspend fun record(trial: TrialRecordEntity, quality: Int) = error("Unused")
            override suspend fun recordSpeaking(record: SpeakingRecordEntity) = error("Unused")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = ProgressRepository(store, scope,
                calendar = flowOf(PracticeCalendar(LocalDate(1970, 1, 1), TimeZone.UTC)), nowEpochMs = { 1000 }, refresh = flowOf(Unit))
            withTimeout(5000) { repository.state.first { it.attempts == 2 } }
            repository.setContrastFilter("i_vs_I")
            withTimeout(5000) { repository.state.first { it.attempts == 1 } }
            assertEquals(1, observations)
            failed.value = true
            withTimeout(5000) { repository.state.first { it.canRetryLoad } }
            repository.setContrastFilter("p_vs_b")
            val stale = withTimeout(5000) { repository.state.first { it.filters.contrastId == "p_vs_b" } }
            assertEquals("p_vs_b", stale.contrasts.single().contrastId)
            assertTrue(stale.canRetryLoad)
            assertNotNull(stale.error)
            repository.clearFilters()
            val cleared = withTimeout(5000) { repository.state.first { it.attempts == 2 } }
            assertFalse(cleared.filters.isActive)
            assertTrue(cleared.canRetryLoad)
            assertEquals(1, observations)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
