package com.minimalpairs.app.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sm2_cards")
data class Sm2CardEntity(
    @PrimaryKey val contrastId: String,
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val masteryScore: Float,
    val lastReviewedEpochMs: Long
)

@Entity(tableName = "confusion_entries")
data class ConfusionMatrixEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetPhoneme: String,
    val chosenPhoneme: String,
    val count: Int
)

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

@Database(
    entities = [Sm2CardEntity::class, ConfusionMatrixEntryEntity::class, TrialRecordEntity::class],
    version = 1
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sm2Dao(): Sm2Dao
    abstract fun confusionDao(): ConfusionDao
    abstract fun trialDao(): TrialDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>

