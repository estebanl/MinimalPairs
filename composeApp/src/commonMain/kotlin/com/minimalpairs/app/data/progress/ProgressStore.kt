package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.AppDatabase
import com.minimalpairs.app.data.local.ProgressSnapshot
import com.minimalpairs.app.data.local.SpeakingRecordEntity
import com.minimalpairs.app.data.local.TrialRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map

interface ProgressStore {
    fun observe(): Flow<ProgressSnapshot>
    suspend fun record(trial: TrialRecordEntity, quality: Int)
    suspend fun recordSpeaking(record: SpeakingRecordEntity)
}

class RoomProgressStore(private val database: AppDatabase) : ProgressStore {
    private val dao = database.progressDao()

    override fun observe(): Flow<ProgressSnapshot> = database.invalidationTracker
        .createFlow("trial_records", "sm2_cards", "speaking_records")
        .conflate()
        .map { dao.readSnapshot() }

    override suspend fun record(trial: TrialRecordEntity, quality: Int) = dao.record(trial, quality)
    override suspend fun recordSpeaking(record: SpeakingRecordEntity) = dao.recordSpeaking(record)
}
