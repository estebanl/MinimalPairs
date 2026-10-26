package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt

data class ContrastProgress(
    val contrastId: String,
    val attempts: Int,
    val correct: Int,
    val card: Sm2CardEntity?
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
    val contrasts: List<ContrastProgress> = emptyList(),
    val confusion: Map<String, Map<String, Int>> = emptyMap(),
    val trend: AccuracyTrend = AccuracyTrend(AccuracySample(0, 0), AccuracySample(0, 0)),
    val recentTrials: List<TrialRecordEntity> = emptyList()
) {
    val attempts: Int get() = contrasts.sumOf { it.attempts }
    val accuracy: Int get() = if (attempts == 0) 0 else
        (contrasts.sumOf { it.correct } * 100f / attempts).roundToInt()
}

fun summarizeProgress(trials: List<TrialRecordEntity>, cards: List<Sm2CardEntity>): ProgressState {
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
    val byId = cards.associateBy { it.contrastId }
    val contrasts = trials.groupBy { it.contrastId }.map { (id, attempts) ->
        ContrastProgress(id, attempts.size, attempts.count { it.isCorrect }, byId[id])
    }.sortedWith(compareBy<ContrastProgress> { it.correct.toDouble() / it.attempts }.thenBy { it.contrastId })
    val newestFirst = trials.sortedWith(compareByDescending<TrialRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id })
    fun sample(records: List<TrialRecordEntity>) = AccuracySample(records.size, records.count { it.isCorrect })
    return ProgressState(
        loading = false, contrasts = contrasts, confusion = confusion,
        trend = AccuracyTrend(sample(newestFirst.take(20)), sample(newestFirst.drop(20).take(20))),
        recentTrials = newestFirst.take(20)
    )
}

class ProgressRepository(private val dao: ProgressDao, private val scope: CoroutineScope) {
    private val saveError = MutableStateFlow<String?>(null)
    val state: StateFlow<ProgressState> = combine(dao.observeTrials(), dao.observeCards(), saveError) { trials, cards, error ->
        summarizeProgress(trials, cards).copy(error = error)
    }.catch { emit(ProgressState(loading = false, error = "Couldn't load progress. Reopen the app to try again.")) }
        .stateIn(scope, SharingStarted.Eagerly, ProgressState())

    fun record(trial: TrialRecordEntity, quality: Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Finish an accepted answer even if the user leaves the exercise.
            withContext(NonCancellable) {
                try {
                    dao.record(trial, quality)
                    saveError.value = null
                } catch (_: Exception) {
                    saveError.value = "Couldn't save your latest answer. Check your device storage before continuing."
                }
            }
        }
    }
}
