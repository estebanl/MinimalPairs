package com.minimalpairs.app.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/** Scores from real completed Stories/Rhythm recordings, never generated examples. */
@Serializable
@Entity(tableName = "module_practice_records")
data class ModulePracticeRecordEntity(
    @PrimaryKey val id: Long,
    val module: String,
    val targetId: String,
    val targetTitle: String,
    val contrastId: String?,
    val score: Float,
    val succeeded: Boolean,
    val durationMs: Int,
    val timestampEpochMs: Long
)
