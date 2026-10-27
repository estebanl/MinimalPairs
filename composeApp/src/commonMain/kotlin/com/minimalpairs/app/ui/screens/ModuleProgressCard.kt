package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.local.ModulePracticeRecordEntity
import com.minimalpairs.app.data.progress.ModuleTargetSummary
import com.minimalpairs.app.data.catalog.MeaningTrapCatalog
import com.minimalpairs.app.data.catalog.ProsodyCatalog
import kotlin.math.roundToInt

@Composable
internal fun ModuleProgressCard(
    title: String,
    records: List<ModulePracticeRecordEntity>,
    summaries: List<ModuleTargetSummary>,
    nowEpochMs: Long,
    historyKey: String = "",
    onPractice: (String) -> Unit
) {
    var allTargets by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$title progress", style = MaterialTheme.typography.titleLarge)
            Text("${records.size} scored ${if (records.size == 1) "attempt" else "attempts"}")
            Text("Scores are estimates out of 100. They do not change listening accuracy, streaks, or reviews. Demonstrations are not saved.",
                style = MaterialTheme.typography.bodySmall)
            if (records.isEmpty()) {
                Text("Complete a voice recording in $title to build this history.")
            }
            summaries.take(if (allTargets) summaries.size else 3).forEach { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(summary.latest.targetTitle, style = MaterialTheme.typography.titleMedium)
                    Text("Attempts: ${summary.attempts} · Latest: ${summary.latest.score.roundToInt()}/100 · Best: ${summary.bestScore}/100")
                    Text("Recent average: ${summary.recentAverage}/100 · latest ${summary.recentCount} attempts", style = MaterialTheme.typography.bodySmall)
                    val available = when (summary.latest.module) {
                        "STORIES" -> MeaningTrapCatalog.stories.any { it.id == summary.latest.targetId }
                        "RHYTHM" -> ProsodyCatalog.patterns.any { it.id == summary.latest.targetId }
                        else -> false
                    }
                    if (available) {
                        Button(onClick = { onPractice(summary.latest.targetId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Practice ${summary.latest.targetTitle}")
                        }
                    }
                }
            }
            if (summaries.size > 3) {
                TextButton(onClick = { allTargets = !allTargets }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                    Text(if (allTargets) "Show fewer activities" else "Show all ${summaries.size} activities")
                }
            }
            if (records.isNotEmpty()) Text("Attempt history", style = MaterialTheme.typography.titleMedium)
            HistoryPages(records, historyKey, "attempts", "Browse all ${records.size} attempts") { record ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.targetTitle, style = MaterialTheme.typography.titleSmall)
                    Text("Score: ${record.score.roundToInt()}/100")
                    if (record.module == "STORIES") Text(if (record.succeeded) "Intended outcome" else "Meaning trap", style = MaterialTheme.typography.bodySmall)
                    Text(answerAge(record.timestampEpochMs, nowEpochMs), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
