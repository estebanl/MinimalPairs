package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.SpeakingRecordEntity
import kotlin.math.roundToInt

data class SpeakingSoundSummary(
    val contrastId: String,
    val phoneme: String,
    val latest: SpeakingRecordEntity,
    val attempts: Int,
    val bestScore: Int,
    val recentCount: Int,
    val recentAverage: Int,
    val previousCount: Int,
    val previousAverage: Int?
) {
    val changePoints: Int? get() = if (recentCount == 5 && previousCount == 5)
        recentAverage - previousAverage!! else null
}

/** Compare the same sound within the same contrast; never pool different speaking targets. */
fun summarizeSpeaking(records: List<SpeakingRecordEntity>): List<SpeakingSoundSummary> = records
    .filter { it.score.isFinite() && it.score in 0f..100f }
    .groupBy { it.contrastId to it.targetPhoneme }
    .map { (target, attempts) ->
        val ordered = attempts.sortedWith(compareByDescending<SpeakingRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id })
        val recent = ordered.take(5)
        val previous = ordered.drop(5).take(5)
        SpeakingSoundSummary(
            contrastId = target.first, phoneme = target.second, latest = ordered.first(), attempts = attempts.size,
            bestScore = attempts.maxOf { it.score }.roundToInt(),
            recentCount = recent.size, recentAverage = recent.map { it.score.toDouble() }.average().roundToInt(),
            previousCount = previous.size,
            previousAverage = previous.takeIf { it.isNotEmpty() }?.map { it.score.toDouble() }?.average()?.roundToInt()
        )
    }.sortedWith(compareBy<SpeakingSoundSummary> { it.recentAverage }.thenBy { it.contrastId }.thenBy { it.phoneme })
