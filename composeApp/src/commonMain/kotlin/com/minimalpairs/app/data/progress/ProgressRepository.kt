package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.local.*
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
    val error: String? = null,
    val pendingAnswers: Int = 0,
    val isSaving: Boolean = false,
    val canRetrySave: Boolean = false,
    val canRetryLoad: Boolean = false,
    val isRefreshing: Boolean = false,
    val contrasts: List<ContrastProgress> = emptyList(),
    val confusion: Map<String, Map<String, Int>> = emptyMap(),
    val trend: AccuracyTrend = AccuracyTrend(AccuracySample(0, 0), AccuracySample(0, 0)),
    val recentTrials: List<TrialRecordEntity> = emptyList(),
    val activity: PracticeActivity = PracticeActivity(),
    val reviews: ReviewSchedule = ReviewSchedule(),
    val speaking: List<SpeakingRecordEntity> = emptyList(),
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
        loading = false, contrasts = contrasts, confusion = confusion,
        trend = AccuracyTrend(sample(newestFirst.take(20)), sample(newestFirst.drop(20).take(20))),
        recentTrials = newestFirst.take(20),
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
    private data class PendingAnswer(val trial: TrialRecordEntity? = null, val quality: Int = 0, val speaking: SpeakingRecordEntity? = null)
    private data class SaveStatus(val pending: Int, val saving: Boolean, val failed: Boolean)
    private val pending = MutableStateFlow<List<PendingAnswer>>(emptyList())
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)
    private val saveMutex = Mutex()
    private val saveStatus = combine(pending, saving, saveFailed) { answers, active, failed ->
        SaveStatus(answers.size, active, failed)
    }
    private data class LoadedProgress(val progress: ProgressState, val cards: List<Sm2CardEntity>)
    private val reload = MutableStateFlow(0)
    private var lastLoaded = LoadedProgress(ProgressState(), emptyList())
    private val progress = reload.flatMapLatest { attempt ->
        flow {
            emit(lastLoaded.copy(progress = lastLoaded.progress.copy(isRefreshing = true, canRetryLoad = attempt > 0)))
            // Construct the database flows here so synchronous open errors can also be retried.
            emitAll(combine(store.observe(), calendar) { snapshot, day ->
                LoadedProgress(
                    summarizeProgress(snapshot.trials, snapshot.cards, day, nowEpochMs()).copy(
                        speaking = snapshot.speaking,
                        speakingSummaries = summarizeSpeaking(snapshot.speaking)
                    ),
                    snapshot.cards
                )
            }.flowOn(aggregationDispatcher).onEach { lastLoaded = it })
        }.catch {
            val message = if (lastLoaded.progress.loading) "Couldn't load progress. Try again."
            else "Couldn't refresh progress. Showing the last loaded totals. Retry to get the latest progress."
            emit(lastLoaded.copy(progress = lastLoaded.progress.copy(
                loading = false, isRefreshing = false, canRetryLoad = true, error = message
            )))
        }
    }
    val state: StateFlow<ProgressState> = combine(progress, refresh, saveStatus) { loaded, _, saves ->
        // Read the wall clock on every data/status update, not just on the periodic tick.
        val now = nowEpochMs()
        val progress = loaded.progress
        val saveMessage = if (saves.failed && saves.pending > 0)
            "${saves.pending} ${if (saves.pending == 1) "practice result hasn't" else "practice results haven't"} been saved. Free up device storage, then retry. Unsaved results are kept only while this app stays open."
        else null
        progress.copy(
            reviews = reviewSchedule(loaded.cards, now),
            nowEpochMs = now,
            error = listOfNotNull(progress.error, saveMessage).joinToString("\n").ifEmpty { null },
            pendingAnswers = saves.pending,
            isSaving = saves.saving,
            canRetrySave = saves.failed && saves.pending > 0
        )
    }.stateIn(scope, SharingStarted.Eagerly, ProgressState())

    fun record(trial: TrialRecordEntity, quality: Int) {
        // Negative IDs leave SQLite's positive auto-generated ID sequence untouched.
        val identified = if (trial.id == 0L) trial.copy(id = Random.nextLong(Long.MIN_VALUE, 0)) else trial
        pending.update { it + PendingAnswer(identified, quality) }
        drainPending()
    }

    fun recordSpeaking(record: SpeakingRecordEntity) {
        require(record.score.isFinite() && record.score in 0f..100f && record.durationMs > 0)
        val identified = if (record.id == 0L) record.copy(id = Random.nextLong(Long.MIN_VALUE, 0)) else record
        pending.update { it + PendingAnswer(speaking = identified) }
        drainPending()
    }

    fun retrySaves() = drainPending()

    fun retryLoading() { reload.update { it + 1 } }

    private fun drainPending() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Finish accepted writes even when the exercise or root is leaving.
            withContext(NonCancellable) {
                saveMutex.withLock {
                    if (pending.value.isEmpty()) return@withLock
                    saving.value = true
                    try {
                        while (true) {
                            val answer = pending.value.firstOrNull() ?: break
                            try {
                                if (answer.speaking != null) store.recordSpeaking(answer.speaking)
                                else store.record(answer.trial!!, answer.quality)
                            } catch (_: Exception) {
                                saveFailed.value = true
                                return@withLock
                            }
                            pending.update { it.drop(1) }
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
