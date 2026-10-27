package com.minimalpairs.app.data.backup

import com.minimalpairs.app.data.local.ProgressSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

/** Versioned, portable data only: never microphone audio or account credentials. */
object ProgressBackupCodec {
    private const val FORMAT = "minimal-pairs-progress"
    private const val VERSION = 1
    private const val MAX_RECORDS = 100_000
    private const val LAST_SUPPORTED_TIMESTAMP = 253402300799999L // 9999-12-31 UTC
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }

    @Serializable
    private data class Backup(
        val format: String,
        val version: Int,
        val exportedAtEpochMs: Long,
        val progress: ProgressSnapshot
    )

    fun encode(snapshot: ProgressSnapshot, nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): String {
        validate(snapshot)
        val text = json.encodeToString(Backup(FORMAT, VERSION, nowEpochMs, snapshot))
        require(text.encodeToByteArray().size <= MAX_BACKUP_BYTES) { "Backup is larger than 20 MB." }
        return text
    }

    fun decode(text: String): ProgressSnapshot {
        require(text.length <= MAX_BACKUP_BYTES && text.encodeToByteArray().size <= MAX_BACKUP_BYTES) { "Backup is larger than 20 MB." }
        val backup = json.decodeFromString<Backup>(text)
        require(backup.format == FORMAT) { "This is not a Minimal Pairs progress backup." }
        require(backup.version == VERSION) { "This backup version is not supported." }
        validate(backup.progress)
        return backup.progress
    }

    fun validate(snapshot: ProgressSnapshot) {
        val count = snapshot.trials.size.toLong() + snapshot.cards.size + snapshot.speaking.size + snapshot.modules.size
        require(count <= MAX_RECORDS) { "Backup contains too many records." }
        require(snapshot.trials.map { it.id }.distinct().size == snapshot.trials.size) { "Duplicate listening result IDs." }
        require(snapshot.speaking.map { it.id }.distinct().size == snapshot.speaking.size) { "Duplicate speaking result IDs." }
        require(snapshot.modules.map { it.id }.distinct().size == snapshot.modules.size) { "Duplicate Stories/Rhythm result IDs." }
        require(snapshot.cards.map { it.contrastId }.distinct().size == snapshot.cards.size) { "Duplicate review schedules." }
        snapshot.trials.forEach {
            require(it.id != 0L && it.latencyMs >= 0) { "Invalid listening result." }
            text(it.contrastId); text(it.targetWord); text(it.selectedWord); text(it.drillType); timestamp(it.timestampEpochMs)
            require(it.isCorrect == (it.targetWord == it.selectedWord)) { "Listening correctness does not match the selected word." }
        }
        snapshot.cards.forEach {
            text(it.contrastId); timestamp(it.lastReviewedEpochMs)
            require(it.repetitions >= 0 && it.intervalDays >= 0 && it.easeFactor.isFinite() && it.easeFactor > 0 &&
                it.masteryScore.isFinite() && it.masteryScore in 0f..1f) { "Invalid review schedule." }
        }
        snapshot.speaking.forEach {
            require(it.id != 0L && it.durationMs > 0) { "Invalid speaking result." }
            text(it.contrastId); text(it.targetWord); text(it.targetPhoneme); score(it.score); timestamp(it.timestampEpochMs)
        }
        snapshot.modules.forEach {
            require(it.id != 0L && it.durationMs > 0 && it.module in setOf("STORIES", "RHYTHM")) { "Invalid Stories/Rhythm result." }
            text(it.targetId); text(it.targetTitle); it.contrastId?.let(::text); score(it.score); timestamp(it.timestampEpochMs)
            require(it.module != "RHYTHM" || it.contrastId == null) { "Rhythm results cannot have a listening contrast." }
        }
    }

    private fun text(value: String) { require(value.isNotBlank() && value.length <= 4096) { "Invalid result label." } }
    private fun timestamp(value: Long) { require(value in 0..LAST_SUPPORTED_TIMESTAMP) { "Invalid practice date." } }
    private fun score(value: Float) { require(value.isFinite() && value in 0f..100f) { "Invalid pronunciation score." } }
}
