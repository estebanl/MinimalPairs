package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.local.*
import com.minimalpairs.app.domain.StoryContrastEvidence
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ContrastProgress(
    val contrastId: String,
    val attempts: Int,
    val correct: Int,
    val card: Sm2CardEntity?,
    val confusion: Map<String, Map<String, Int>> = emptyMap()
) {
    val accuracy: Int get() = (correct * 100f / attempts).roundToInt()
}

/** Equal-sized, non-overlapping samples keep recent accuracy comparisons interpretable. */
data class AccuracySample(val attempts: Int, val correct: Int) {
    val accuracy: Int? get() = if (attempts == 0) null else (correct * 100f / attempts).roundToInt()
}

data class AccuracyTrend(
    val recent: AccuracySample,
    val previous: AccuracySample
) {
    val changePoints: Int? get() = if (recent.attempts == 20 && previous.attempts == 20)
        recent.accuracy!! - previous.accuracy!! else null
}

data class ProgressState(
    val loading: Boolean = true,
    val hasLoadedProgress: Boolean = false,
    val error: String? = null,
    val pendingAnswers: Int = 0,
    val durablePendingAnswers: Int = 0,
    val isSaving: Boolean = false,
    val canRetrySave: Boolean = false,
    val canRetryLoad: Boolean = false,
    val isRefreshing: Boolean = false,
    val contrasts: List<ContrastProgress> = emptyList(),
    val confusion: Map<String, Map<String, Int>> = emptyMap(),
    val trend: AccuracyTrend = AccuracyTrend(AccuracySample(0, 0), AccuracySample(0, 0)),
    val recentTrials: List<TrialRecordEntity> = emptyList(),
    val listeningHistory: List<TrialRecordEntity> = emptyList(),
    val filters: ProgressFilters = ProgressFilters(),
    val activity: PracticeActivity = PracticeActivity(),
    val reviews: ReviewSchedule = ReviewSchedule(),
    val speaking: List<SpeakingRecordEntity> = emptyList(),
    val modules: List<ModulePracticeRecordEntity> = emptyList(),
    val moduleSummaries: List<ModuleTargetSummary> = emptyList(),
    val speakingSummaries: List<SpeakingSoundSummary> = emptyList(),
    val nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()
) {
    val attempts: Int get() = contrasts.sumOf { it.attempts }
    val accuracy: Int get() = if (attempts == 0) 0 else
        (contrasts.sumOf { it.correct } * 100f / attempts).roundToInt()
}

fun summarizeProgress(trials: List<TrialRecordEntity>, cards: List<Sm2CardEntity>,
    calendar: PracticeCalendar = PracticeCalendar.current(),
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()
): ProgressState {
    val confusion = summarizeConfusions(trials)
    val byId = cards.associateBy { it.contrastId }
    val contrasts = trials.groupBy { it.contrastId }.map { (id, attempts) ->
        ContrastProgress(id, attempts.size, attempts.count { it.isCorrect }, byId[id], summarizeConfusions(attempts))
    }.sortedWith(compareBy<ContrastProgress> { it.correct.toDouble() / it.attempts }.thenBy { it.contrastId })
    val newestFirst = trials.sortedWith(compareByDescending<TrialRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id })
    fun sample(records: List<TrialRecordEntity>) = AccuracySample(records.size, records.count { it.isCorrect })
    return ProgressState(
        loading = false, hasLoadedProgress = true, contrasts = contrasts, confusion = confusion,
        trend = AccuracyTrend(sample(newestFirst.take(20)), sample(newestFirst.drop(20).take(20))),
        recentTrials = newestFirst.take(20),
        listeningHistory = newestFirst,
        activity = summarizeActivity(trials, calendar),
        reviews = reviewSchedule(cards, nowEpochMs),
        nowEpochMs = nowEpochMs
    )
}

private fun summarizeConfusions(trials: List<TrialRecordEntity>): Map<String, Map<String, Int>> {
    val confusion = mutableMapOf<String, MutableMap<String, Int>>()
    trials.forEach { trial ->
        val contrast = ListeningContrastsCatalog.getContrastById(trial.contrastId) ?: return@forEach
        val pair = contrast.pairs.firstOrNull { it.word1 == trial.targetWord || it.word2 == trial.targetWord }
            ?: return@forEach
        val target = if (trial.targetWord == pair.word1) contrast.phoneme1 else contrast.phoneme2
        val chosen = if (trial.isCorrect) target else if (target == contrast.phoneme1) contrast.phoneme2 else contrast.phoneme1
        val row = confusion.getOrPut(target) { mutableMapOf() }
        row[chosen] = (row[chosen] ?: 0) + 1
    }
    return confusion
}

private fun practiceCalendarChanges(): Flow<PracticeCalendar> = flow {
    // Refresh after local midnight or a time-zone change even without another answer.
    while (true) {
        emit(PracticeCalendar.current())
        delay(60_000)
    }
}.distinctUntilChanged()

private fun reviewRefreshes(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(60_000)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepository(
    private val store: ProgressStore,
    private val scope: CoroutineScope,
    calendar: Flow<PracticeCalendar> = practiceCalendarChanges(),
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    refresh: Flow<Unit> = reviewRefreshes(),
    aggregationDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private data class PendingEntry(val write: PendingProgressWrite, val durable: Boolean = false)
    private data class SaveStatus(val pending: Int, val durable: Int, val saving: Boolean, val failed: Boolean, val recoveryFailed: Boolean)
    private val pending = MutableStateFlow<List<PendingEntry>>(emptyList())
    private val recoveryFailed = MutableStateFlow(false)
    private var recoveredPending = false
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)
    private val saveMutex = Mutex()
    private val saveStatus = combine(pending, saving, saveFailed, recoveryFailed) { answers, active, failed, recoveryError ->
        SaveStatus(answers.size, answers.count { it.durable }, active, failed, recoveryError)
    }
    private data class LoadedSnapshot(
        val snapshot: ProgressSnapshot? = null,
        val isRefreshing: Boolean = false,
        val canRetryLoad: Boolean = false,
        val error: String? = null
    )
    private data class LoadedProgress(val progress: ProgressState, val cards: List<Sm2CardEntity>)
    private val filters = MutableStateFlow(ProgressFilters())
    private val reload = MutableStateFlow(0)
    private var lastSnapshot: ProgressSnapshot? = null
    private val snapshots = reload.flatMapLatest { attempt ->
        flow {
            emit(LoadedSnapshot(lastSnapshot, isRefreshing = true, canRetryLoad = attempt > 0))
            // Keep the raw snapshot so filter changes also work while reads are unavailable.
            emitAll(store.observe().map { snapshot ->
                lastSnapshot = snapshot
                LoadedSnapshot(snapshot)
            })
        }.catch {
            val message = if (lastSnapshot == null) "Couldn't load progress. Try again."
            else "Couldn't refresh progress. Showing the last loaded totals. Retry to get the latest progress."
            emit(LoadedSnapshot(lastSnapshot, canRetryLoad = true, error = message))
        }
    }.flowOn(aggregationDispatcher).shareIn(scope, SharingStarted.Eagerly, replay = 1)
    /** Lifetime saved evidence shares the existing database observer and ignores presentation filters. */
    val storyRecommendationEvidence: StateFlow<Map<String, StoryContrastEvidence>> = snapshots
        .map { it.snapshot }
        .distinctUntilChanged { old, new -> old?.trials === new?.trials && old?.modules === new?.modules }
        .map { snapshot -> snapshot?.let(::storyPracticeEvidence).orEmpty() }
        .distinctUntilChanged()
        .flowOn(aggregationDispatcher)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())
    private var lastAggregated: LoadedProgress? = null
    private val aggregator = ProgressAggregator()
    private val progress = combine(snapshots, calendar, filters) { loaded, day, selected ->
        try {
            val summary = loaded.snapshot?.let { aggregator.summarize(it, day, selected, nowEpochMs()) }
                ?: ProgressState(loading = !loaded.canRetryLoad, filters = selected)
            LoadedProgress(summary.copy(
                isRefreshing = loaded.isRefreshing,
                canRetryLoad = loaded.canRetryLoad,
                error = loaded.error
            ), loaded.snapshot?.cards.orEmpty()).also {
                if (loaded.snapshot != null) lastAggregated = it
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val previous = lastAggregated ?: LoadedProgress(ProgressState(filters = selected), emptyList())
            val message = if (!previous.progress.hasLoadedProgress) "Couldn't load progress. Try again."
                else "Couldn't refresh progress. Showing the last loaded totals. Retry to get the latest progress."
            // Preserve the previous filters too: old totals must not be labeled with a new selection.
            previous.copy(progress = previous.progress.copy(
                loading = false, isRefreshing = false, canRetryLoad = true, error = message
            ))
        }
    }.flowOn(aggregationDispatcher)
    val state: StateFlow<ProgressState> = combine(progress, refresh, saveStatus) { loaded, _, saves ->
        // Read the wall clock on every data/status update, not just on the periodic tick.
        val now = nowEpochMs()
        val progress = loaded.progress
        val saveMessage = when {
            saves.recoveryFailed -> "Couldn't restore pending practice results. Retry saving to recover them."
            saves.failed && saves.pending > 0 -> {
                val durability = if (saves.durable == saves.pending) "These results are stored on this device and will be retried when the app reopens."
                    else "${saves.pending - saves.durable} pending results are not yet stored safely. Keep the app open and retry."
                "${saves.pending} ${if (saves.pending == 1) "practice result hasn't" else "practice results haven't"} been added to progress. Free up device storage, then retry. $durability"
            }
            else -> null
        }
        progress.copy(
            reviews = reviewSchedule(loaded.cards, now),
            nowEpochMs = now,
            error = listOfNotNull(progress.error, saveMessage).joinToString("\n").ifEmpty { null },
            pendingAnswers = saves.pending,
            durablePendingAnswers = saves.durable,
            isSaving = saves.saving,
            canRetrySave = saves.recoveryFailed || (saves.failed && saves.pending > 0)
        )
    }.stateIn(scope, SharingStarted.Eagerly, ProgressState())

    fun record(trial: TrialRecordEntity, quality: Int) {
        // Negative IDs leave SQLite's positive auto-generated ID sequence untouched.
        val identified = if (trial.id == 0L) trial.copy(id = Random.nextLong(Long.MIN_VALUE, 0)) else trial
        enqueue(PendingProgressWrite(trial = identified, quality = quality))
        drainPending()
    }

    fun recordSpeaking(record: SpeakingRecordEntity) {
        require(record.score.isFinite() && record.score in 0f..100f && record.durationMs > 0)
        val identified = if (record.id == 0L) record.copy(id = Random.nextLong(Long.MIN_VALUE, 0)) else record
        enqueue(PendingProgressWrite(speaking = identified))
        drainPending()
    }

    init { drainPending() }

    fun recordModule(record: ModulePracticeRecordEntity) {
        require(record.module in setOf("STORIES", "RHYTHM") && record.score.isFinite() && record.score in 0f..100f && record.durationMs > 0)
        val identified = if (record.id == 0L) record.copy(id = Random.nextLong(Long.MIN_VALUE, 0)) else record
        enqueue(PendingProgressWrite(module = identified))
        drainPending()
    }

    private fun enqueue(write: PendingProgressWrite) {
        pending.update { entries ->
            val existing = entries.firstOrNull { it.write.key == write.key }
            check(existing == null || existing.write == write) { "Pending result ID collision" }
            if (existing == null) entries + PendingEntry(write) else entries
        }
    }

    suspend fun exportSnapshot(): ProgressSnapshot = saveMutex.withLock {
        check(recoveredPending && !recoveryFailed.value && pending.value.isEmpty()) { "Save pending results before exporting progress" }
        store.exportSnapshot()
    }

    suspend fun importSnapshot(snapshot: ProgressSnapshot) = saveMutex.withLock {
        check(recoveredPending && !recoveryFailed.value && pending.value.isEmpty()) { "Save pending results before importing progress" }
        com.minimalpairs.app.data.backup.ProgressBackupCodec.validate(snapshot)
        store.importSnapshot(snapshot)
    }

    suspend fun resetProgress() = saveMutex.withLock {
        // Clearing the durable queue and visible data is one database transaction.
        // Holding this lock prevents an old in-memory write from replaying afterward.
        val erasedKeys = pending.value.map { it.write.key }.toSet()
        store.resetProgress()
        pending.update { entries -> entries.filterNot { it.write.key in erasedKeys } }
        recoveredPending = true
        recoveryFailed.value = false
        saveFailed.value = false
        lastSnapshot = null
        lastAggregated = null
        reload.update { it + 1 }
    }

    fun retrySaves() = drainPending()

    fun retryLoading() { reload.update { it + 1 } }

    fun setDateRange(range: ProgressDateRange) { filters.update { it.copy(dateRange = range) } }

    fun setContrastFilter(contrastId: String?) {
        require(contrastId == null || ListeningContrastsCatalog.getContrastById(contrastId) != null)
        filters.update { it.copy(contrastId = contrastId) }
    }

    fun clearFilters() { filters.value = ProgressFilters() }

    private fun drainPending() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Finish accepted writes even when the exercise or root is leaving.
            withContext(NonCancellable) {
                saveMutex.withLock {
                    saving.value = true
                    try {
                        if (!recoveredPending) {
                            try {
                                val restored = store.readPending().map { PendingEntry(it, durable = true) }
                                pending.update { current ->
                                    val restoredByKey = restored.associateBy { it.write.key }
                                    current.forEach { entry ->
                                        val saved = restoredByKey[entry.write.key]
                                        check(saved == null || saved.write == entry.write) { "Pending result ID collision" }
                                    }
                                    restored + current.filterNot { it.write.key in restoredByKey }
                                }
                                recoveredPending = true
                                recoveryFailed.value = false
                            } catch (_: Exception) {
                                recoveryFailed.value = true
                                return@withLock
                            }
                        }
                        // Stage every accepted result before processing, so a failed first
                        // result cannot leave later results unnecessarily memory-only.
                        for (entry in pending.value) {
                            if (!entry.durable) {
                                try {
                                    val durable = store.stage(entry.write)
                                    if (durable) pending.update { entries -> entries.map {
                                        if (it.write.key == entry.write.key) it.copy(durable = true) else it
                                    } }
                                } catch (_: Exception) {
                                    saveFailed.value = true
                                    return@withLock
                                }
                            }
                        }
                        while (true) {
                            val entry = pending.value.firstOrNull() ?: break
                            try {
                                // Entries may arrive while staging/finishing another entry.
                                if (!entry.durable) {
                                    val durable = store.stage(entry.write)
                                    if (durable) pending.update { entries -> entries.map {
                                        if (it.write.key == entry.write.key) it.copy(durable = true) else it
                                    } }
                                }
                                store.finish(entry.write)
                            } catch (_: Exception) {
                                saveFailed.value = true
                                return@withLock
                            }
                            pending.update { entries -> entries.filterNot { it.write.key == entry.write.key } }
                        }
                        saveFailed.value = false
                    } finally {
                        saving.value = false
                    }
                }
            }
        }
    }
}
