package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class PendingProgressWrite(
    val trial: TrialRecordEntity? = null,
    val quality: Int = 0,
    val speaking: SpeakingRecordEntity? = null,
    val module: ModulePracticeRecordEntity? = null
) {
    val key: String get() = when {
        trial != null -> "LISTENING:${trial.id}"
        speaking != null -> "SPEAKING:${speaking.id}"
        module != null -> "MODULE:${module.id}"
        else -> error("Empty progress write")
    }
}

interface ProgressStore {
    fun observe(): Flow<ProgressSnapshot>
    suspend fun record(trial: TrialRecordEntity, quality: Int)
    suspend fun recordSpeaking(record: SpeakingRecordEntity)
    suspend fun recordModule(record: ModulePracticeRecordEntity) { error("Module progress unsupported") }
    suspend fun readPending(): List<PendingProgressWrite> = emptyList()
    /** False means a non-durable implementation; never claim restart safety for this result. */
    suspend fun stage(write: PendingProgressWrite): Boolean = false
    suspend fun finish(write: PendingProgressWrite) {
        when {
            write.trial != null -> record(write.trial, write.quality)
            write.speaking != null -> recordSpeaking(write.speaking)
            write.module != null -> recordModule(write.module)
        }
    }
    suspend fun exportSnapshot(): ProgressSnapshot = error("Export unsupported")
    suspend fun importSnapshot(snapshot: ProgressSnapshot) { error("Import unsupported") }
    suspend fun resetProgress() { error("Reset unsupported") }
}

class RoomProgressStore(private val database: AppDatabase) : ProgressStore {
    private val dao = database.progressDao()
    private val json = Json

    override fun observe(): Flow<ProgressSnapshot> = flow {
        var current: VersionedProgressSnapshot? = null
        database.invalidationTracker.createFlow("trial_records", "sm2_cards", "speaking_records", "module_practice_records")
            .conflate().collect {
                val previous = current
                val delta = previous?.let { dao.readDelta(it.sequence) }
                current = if (previous == null || delta == null || delta.reset || delta.sequence == previous.sequence ||
                    !delta.matchesCounts(previous.snapshot)) {
                    // Initial load, imports/reset, or a write made through a legacy DAO.
                    dao.readVersionedSnapshot()
                } else {
                    VersionedProgressSnapshot(delta.sequence, applyProgressDelta(previous.snapshot, delta))
                }
                emit(current!!.snapshot)
            }
    }

    override suspend fun record(trial: TrialRecordEntity, quality: Int) = dao.record(trial, quality)
    override suspend fun recordSpeaking(record: SpeakingRecordEntity) = dao.recordSpeaking(record)
    override suspend fun recordModule(record: ModulePracticeRecordEntity) = dao.recordModule(record)
    override suspend fun readPending(): List<PendingProgressWrite> = dao.readPendingWrites().map { json.decodeFromString(it.payload) }
    override suspend fun stage(write: PendingProgressWrite): Boolean {
        dao.stageWrite(PendingProgressWriteEntity(operationKey = write.key, payload = json.encodeToString(write)))
        return true
    }
    override suspend fun finish(write: PendingProgressWrite) {
        when {
            write.trial != null -> dao.finishListening(write.trial, write.quality, write.key)
            write.speaking != null -> dao.finishSpeaking(write.speaking, write.key)
            write.module != null -> dao.finishModule(write.module, write.key)
        }
    }
    override suspend fun exportSnapshot(): ProgressSnapshot = dao.readSnapshot()
    override suspend fun importSnapshot(snapshot: ProgressSnapshot) = dao.importSnapshot(snapshot)
    override suspend fun resetProgress() = dao.resetProgress()
}

/** Cards are a complete small snapshot, so removals must replace rather than merge. */
internal fun applyProgressDelta(old: ProgressSnapshot, delta: ProgressDelta): ProgressSnapshot = ProgressSnapshot(
    merge(old.trials, delta.trials, { it.id }, compareByDescending<TrialRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id }),
    if (old.cards == delta.cards) old.cards else delta.cards,
    merge(old.speaking, delta.speaking, { it.id }, compareByDescending<SpeakingRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id }),
    merge(old.modules, delta.modules, { it.id }, compareByDescending<ModulePracticeRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id })
)

/** Preserve unchanged lists by identity; insert a small delta without sorting the whole history. */
private fun <T, K> merge(old: List<T>, updates: List<T>, id: (T) -> K, comparator: Comparator<T>): List<T> {
    if (updates.isEmpty()) return old
    val replaced = updates.map(id).toSet()
    val retained = old.filterNot { id(it) in replaced }
    val ordered = updates.sortedWith(comparator)
    val result = ArrayList<T>(retained.size + ordered.size)
    var left = 0
    var right = 0
    while (left < retained.size && right < ordered.size) {
        if (comparator.compare(retained[left], ordered[right]) <= 0) result.add(retained[left++])
        else result.add(ordered[right++])
    }
    while (left < retained.size) result.add(retained[left++])
    while (right < ordered.size) result.add(ordered[right++])
    return result
}

private fun ProgressDelta.matchesCounts(old: ProgressSnapshot): Boolean {
    fun <T> expected(old: List<T>, added: List<T>, id: (T) -> Long): Int {
        if (added.isEmpty()) return old.size
        val ids = old.map(id).toSet()
        return old.size + added.count { id(it) !in ids }
    }
    return trialCount == expected(old.trials, trials) { it.id } &&
        speakingCount == expected(old.speaking, speaking) { it.id } &&
        moduleCount == expected(old.modules, modules) { it.id }
}
