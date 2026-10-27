package com.minimalpairs.app.data.local

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import kotlinx.serialization.Serializable
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Update
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Serializable
@Entity(tableName = "sm2_cards")
data class Sm2CardEntity(
    @PrimaryKey val contrastId: String,
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val masteryScore: Float,
    val lastReviewedEpochMs: Long
)

@Serializable
@Entity(tableName = "confusion_entries")
data class ConfusionMatrixEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetPhoneme: String,
    val chosenPhoneme: String,
    val count: Int
)

@Serializable
@Entity(tableName = "trial_records")
data class TrialRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contrastId: String,
    val targetWord: String,
    val selectedWord: String,
    val isCorrect: Boolean,
    val latencyMs: Long,
    val drillType: String,
    val timestampEpochMs: Long
)

@Serializable
@Entity(tableName = "speaking_records")
data class SpeakingRecordEntity(
    @PrimaryKey val id: Long,
    val contrastId: String,
    val targetWord: String,
    val targetPhoneme: String,
    val score: Float,
    val durationMs: Int,
    val timestampEpochMs: Long
)

@Dao
interface Sm2Dao {
    @Query("SELECT * FROM sm2_cards WHERE contrastId = :contrastId")
    suspend fun getCard(contrastId: String): Sm2CardEntity?

    @Query("SELECT * FROM sm2_cards")
    fun getAllCards(): Flow<List<Sm2CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(card: Sm2CardEntity)
}

@Dao
interface ConfusionDao {
    @Query("SELECT * FROM confusion_entries WHERE targetPhoneme = :target AND chosenPhoneme = :chosen")
    suspend fun getEntry(target: String, chosen: String): ConfusionMatrixEntryEntity?

    @Query("SELECT * FROM confusion_entries")
    fun getAllEntries(): Flow<List<ConfusionMatrixEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: ConfusionMatrixEntryEntity)
}

@Dao
interface TrialDao {
    @Insert
    suspend fun insertTrial(record: TrialRecordEntity)

    @Query("SELECT * FROM trial_records ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun getRecentTrials(limit: Int = 100): Flow<List<TrialRecordEntity>>

    @Query("SELECT COUNT(*) FROM trial_records WHERE isCorrect = 1")
    suspend fun getCorrectTrialCount(): Int

    @Query("SELECT COUNT(*) FROM trial_records")
    suspend fun getTotalTrialCount(): Int
}

@Serializable
data class ProgressSnapshot(
    val trials: List<TrialRecordEntity>,
    val cards: List<Sm2CardEntity>,
    val speaking: List<SpeakingRecordEntity>,
    val modules: List<ModulePracticeRecordEntity> = emptyList()
)

@Entity(tableName = "pending_progress_writes", indices = [Index(value = ["operationKey"], unique = true)])
data class PendingProgressWriteEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val operationKey: String,
    val payload: String
)

@Entity(tableName = "progress_changes")
data class ProgressChangeEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val kind: String,
    val recordId: Long
)

data class ProgressDelta(
    val sequence: Long,
    val reset: Boolean,
    val trials: List<TrialRecordEntity>,
    val cards: List<Sm2CardEntity>,
    val speaking: List<SpeakingRecordEntity>,
    val modules: List<ModulePracticeRecordEntity>,
    val trialCount: Int = 0,
    val speakingCount: Int = 0,
    val moduleCount: Int = 0
)

data class VersionedProgressSnapshot(val sequence: Long, val snapshot: ProgressSnapshot)

@Dao
abstract class ProgressDao {
    @Query("SELECT * FROM trial_records ORDER BY timestampEpochMs DESC, id DESC")
    abstract suspend fun readTrials(): List<TrialRecordEntity>

    @Query("SELECT * FROM sm2_cards ORDER BY contrastId")
    abstract suspend fun readCards(): List<Sm2CardEntity>

    @Query("SELECT * FROM speaking_records ORDER BY timestampEpochMs DESC, id DESC")
    abstract suspend fun readSpeaking(): List<SpeakingRecordEntity>

    @Transaction
    open suspend fun readSnapshot(): ProgressSnapshot = ProgressSnapshot(readTrials(), readCards(), readSpeaking(), readModules())

    @Query("SELECT * FROM speaking_records ORDER BY timestampEpochMs DESC, id DESC")
    abstract fun observeSpeaking(): Flow<List<SpeakingRecordEntity>>

    @Query("SELECT * FROM speaking_records WHERE id = :id")
    abstract suspend fun getSpeaking(id: Long): SpeakingRecordEntity?

    @Insert
    abstract suspend fun insertSpeaking(record: SpeakingRecordEntity)

    @Transaction
    open suspend fun recordSpeaking(record: SpeakingRecordEntity) {
        getSpeaking(record.id)?.let {
            check(it == record) { "Recording ID already belongs to another result" }
            return
        }
        insertSpeaking(record)
        logChange(ProgressChangeEntity(kind = "SPEAKING", recordId = record.id))
    }

    @Query("SELECT * FROM trial_records ORDER BY timestampEpochMs DESC")
    abstract fun observeTrials(): Flow<List<TrialRecordEntity>>

    @Query("SELECT * FROM sm2_cards")
    abstract fun observeCards(): Flow<List<Sm2CardEntity>>

    @Query("SELECT * FROM sm2_cards WHERE contrastId = :id")
    abstract suspend fun getCard(id: String): Sm2CardEntity?

    @Query("SELECT * FROM trial_records WHERE id = :id")
    abstract suspend fun getTrial(id: Long): TrialRecordEntity?

    @Insert
    abstract suspend fun insertTrial(trial: TrialRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveCard(card: Sm2CardEntity)

    @Transaction
    open suspend fun record(trial: TrialRecordEntity, quality: Int) {
        // Retried writes retain their ID, including a retry after an ambiguous commit result.
        if (trial.id != 0L) {
            getTrial(trial.id)?.let { existing ->
                check(existing == trial) { "Answer ID already belongs to another answer" }
                return
            }
        }
        val previous = getCard(trial.contrastId)
        val state = previous?.let {
            com.minimalpairs.app.domain.srs.Sm2State(it.repetitions, it.intervalDays, it.easeFactor, it.masteryScore)
        } ?: com.minimalpairs.app.domain.srs.Sm2State()
        val next = com.minimalpairs.app.domain.srs.ModifiedSm2Engine.review(state, quality)
        val insertedId = insertTrial(trial)
        logChange(ProgressChangeEntity(kind = "LISTENING", recordId = if (trial.id == 0L) insertedId else trial.id))
        saveCard(Sm2CardEntity(trial.contrastId, next.repetitions, next.intervalDays,
            next.easeFactor, next.masteryScore, trial.timestampEpochMs))
    }

    @Query("SELECT * FROM module_practice_records ORDER BY timestampEpochMs DESC, id DESC")
    abstract suspend fun readModules(): List<ModulePracticeRecordEntity>

    @Query("SELECT * FROM module_practice_records WHERE id = :id")
    abstract suspend fun getModule(id: Long): ModulePracticeRecordEntity?

    @Insert
    abstract suspend fun insertModule(record: ModulePracticeRecordEntity)

    @Transaction
    open suspend fun recordModule(record: ModulePracticeRecordEntity) {
        getModule(record.id)?.let { check(it == record) { "Module result ID already belongs to another result" }; return }
        insertModule(record)
        logChange(ProgressChangeEntity(kind = "MODULE", recordId = record.id))
    }

    @Insert
    abstract suspend fun logChange(change: ProgressChangeEntity)

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM progress_changes")
    abstract suspend fun changeSequence(): Long

    @Query("SELECT * FROM progress_changes WHERE sequence > :after ORDER BY sequence LIMIT 1001")
    abstract suspend fun changesAfter(after: Long): List<ProgressChangeEntity>

    @Transaction
    open suspend fun readVersionedSnapshot(): VersionedProgressSnapshot = VersionedProgressSnapshot(changeSequence(), readSnapshot())

    @Query("SELECT COUNT(*) FROM trial_records") abstract suspend fun trialCount(): Int
    @Query("SELECT COUNT(*) FROM speaking_records") abstract suspend fun speakingCount(): Int
    @Query("SELECT COUNT(*) FROM module_practice_records") abstract suspend fun moduleCount(): Int

    @Transaction
    open suspend fun readDelta(after: Long): ProgressDelta {
        val sequence = changeSequence()
        val changes = changesAfter(after)
        // Large imports and external/legacy DAO writes fall back to one consistent snapshot.
        val reset = sequence < after || changes.size > 1000 || changes.any { it.kind == "RESET" }
        if (reset) return ProgressDelta(sequence, true, emptyList(), emptyList(), emptyList(), emptyList())
        val trials = changes.filter { it.kind == "LISTENING" }.mapNotNull { getTrial(it.recordId) }.distinctBy { it.id }
        val speaking = changes.filter { it.kind == "SPEAKING" }.mapNotNull { getSpeaking(it.recordId) }.distinctBy { it.id }
        val modules = changes.filter { it.kind == "MODULE" }.mapNotNull { getModule(it.recordId) }.distinctBy { it.id }
        val cards = readCards()
        return ProgressDelta(sequence, false, trials, cards, speaking, modules, trialCount(), speakingCount(), moduleCount())
    }

    @Query("SELECT * FROM pending_progress_writes ORDER BY sequence")
    abstract suspend fun readPendingWrites(): List<PendingProgressWriteEntity>

    @Query("SELECT * FROM pending_progress_writes WHERE operationKey = :key")
    abstract suspend fun getPendingWrite(key: String): PendingProgressWriteEntity?

    @Insert
    abstract suspend fun insertPendingWrite(write: PendingProgressWriteEntity)

    @Transaction
    open suspend fun stageWrite(write: PendingProgressWriteEntity) {
        getPendingWrite(write.operationKey)?.let { check(it.payload == write.payload) { "Pending result ID collision" }; return }
        insertPendingWrite(write)
    }

    @Query("DELETE FROM pending_progress_writes WHERE operationKey = :key")
    abstract suspend fun deletePendingWrite(key: String)

    @Transaction
    open suspend fun finishListening(trial: TrialRecordEntity, quality: Int, key: String) {
        record(trial, quality)
        deletePendingWrite(key)
    }

    @Transaction
    open suspend fun finishSpeaking(record: SpeakingRecordEntity, key: String) {
        recordSpeaking(record)
        deletePendingWrite(key)
    }

    @Transaction
    open suspend fun finishModule(record: ModulePracticeRecordEntity, key: String) {
        recordModule(record)
        deletePendingWrite(key)
    }

    @Query("DELETE FROM trial_records") abstract suspend fun clearTrials()
    @Query("DELETE FROM speaking_records") abstract suspend fun clearSpeaking()
    @Query("DELETE FROM module_practice_records") abstract suspend fun clearModules()
    @Query("DELETE FROM sm2_cards") abstract suspend fun clearCards()
    @Query("DELETE FROM confusion_entries") abstract suspend fun clearConfusions()
    @Query("DELETE FROM pending_progress_writes") abstract suspend fun clearPendingWrites()
    @Query("DELETE FROM progress_changes") abstract suspend fun clearChanges()

    @Transaction
    open suspend fun resetProgress() {
        clearTrials(); clearSpeaking(); clearModules(); clearCards(); clearConfusions(); clearPendingWrites(); clearChanges()
        logChange(ProgressChangeEntity(kind = "RESET", recordId = 0))
    }

    @Transaction
    open suspend fun importSnapshot(snapshot: ProgressSnapshot) {
        // Preflight every conflict before any writes; rollback also protects against later failures.
        check(snapshot.trials.all { it.id != 0L } && snapshot.speaking.all { it.id != 0L } && snapshot.modules.all { it.id != 0L }) { "Backup result IDs must be stable" }
        check(snapshot.trials.groupBy { it.id }.values.all { it.distinct().size == 1 }) { "Conflicting listening IDs in backup" }
        check(snapshot.speaking.groupBy { it.id }.values.all { it.distinct().size == 1 }) { "Conflicting speaking IDs in backup" }
        check(snapshot.modules.groupBy { it.id }.values.all { it.distinct().size == 1 }) { "Conflicting module IDs in backup" }
        check(snapshot.cards.groupBy { it.contrastId }.values.all { it.distinct().size == 1 }) { "Conflicting review schedules in backup" }
        snapshot.trials.forEach { incoming -> getTrial(incoming.id)?.let { check(it == incoming) { "Conflicting listening result" } } }
        snapshot.speaking.forEach { incoming -> getSpeaking(incoming.id)?.let { check(it == incoming) { "Conflicting speaking result" } } }
        snapshot.modules.forEach { incoming -> getModule(incoming.id)?.let { check(it == incoming) { "Conflicting module result" } } }
        snapshot.cards.forEach { incoming -> getCard(incoming.contrastId)?.let {
            check(it.lastReviewedEpochMs != incoming.lastReviewedEpochMs || it == incoming) { "Conflicting review schedule" }
        } }
        snapshot.trials.forEach { if (getTrial(it.id) == null) insertTrial(it) }
        snapshot.speaking.forEach { if (getSpeaking(it.id) == null) insertSpeaking(it) }
        snapshot.modules.forEach { if (getModule(it.id) == null) insertModule(it) }
        snapshot.cards.forEach { incoming ->
            val previous = getCard(incoming.contrastId)
            if (previous == null || incoming.lastReviewedEpochMs > previous.lastReviewedEpochMs) saveCard(incoming)
        }
        logChange(ProgressChangeEntity(kind = "RESET", recordId = 0))
    }

}

@Database(
    entities = [Sm2CardEntity::class, ConfusionMatrixEntryEntity::class, TrialRecordEntity::class, SpeakingRecordEntity::class, ModulePracticeRecordEntity::class, PendingProgressWriteEntity::class, ProgressChangeEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)]
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sm2Dao(): Sm2Dao
    abstract fun confusionDao(): ConfusionDao
    abstract fun trialDao(): TrialDao
    abstract fun progressDao(): ProgressDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>

