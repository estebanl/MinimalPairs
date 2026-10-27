package com.minimalpairs.app.data.progress

import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import kotlin.math.roundToInt

data class ModuleTargetSummary(
    val latest: ModulePracticeRecordEntity,
    val attempts: Int,
    val bestScore: Int,
    val recentAverage: Int,
    val recentCount: Int
)

fun summarizeModules(records: List<ModulePracticeRecordEntity>): List<ModuleTargetSummary> = records
    .filter { it.score.isFinite() && it.score in 0f..100f && it.durationMs > 0 }
    .groupBy { it.module to it.targetId }
    .map { (_, attempts) ->
        val ordered = attempts.sortedWith(compareByDescending<ModulePracticeRecordEntity> { it.timestampEpochMs }.thenByDescending { it.id })
        val recent = ordered.take(5)
        ModuleTargetSummary(ordered.first(), attempts.size, attempts.maxOf { it.score }.roundToInt(),
            recent.map { it.score.toDouble() }.average().roundToInt(), recent.size)
    }.sortedWith(compareBy<ModuleTargetSummary> { it.recentAverage }.thenBy { it.latest.targetId })
