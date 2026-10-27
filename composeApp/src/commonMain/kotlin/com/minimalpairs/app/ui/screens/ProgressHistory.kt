package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.progress.AccuracySample
import com.minimalpairs.app.data.progress.AccuracyTrend
import com.minimalpairs.app.data.local.TrialRecordEntity
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.coroutines.delay

@Composable
internal fun AccuracyTrendCard(trend: AccuracyTrend, filtered: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recent accuracy", style = MaterialTheme.typography.titleLarge)
            AccuracySampleRow("Latest ${trend.recent.attempts} ${if (trend.recent.attempts == 1) "answer" else "answers"}", trend.recent)
            if (trend.previous.attempts > 0) {
                AccuracySampleRow("Previous ${trend.previous.attempts} ${if (trend.previous.attempts == 1) "answer" else "answers"}", trend.previous)
            }
            val change = trend.changePoints
            Text(
                when {
                    change == null -> "Answer ${40 - trend.recent.attempts - trend.previous.attempts} more to compare two sets of 20 answers."
                    change > 0 -> "Up $change percentage points from the previous 20 answers."
                    change < 0 -> "Down ${abs(change)} percentage points from the previous 20 answers."
                    else -> "Unchanged from the previous 20 answers."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                if (filtered) "Uses answers matching the selected filters. Practicing different sounds can affect the comparison."
                else "Includes all listening contrasts. Practicing different sounds can affect the comparison.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AccuracySampleRow(label: String, sample: AccuracySample) {
    Text("$label · ${sample.accuracy ?: 0}% (${sample.correct}/${sample.attempts})", style = MaterialTheme.typography.bodyMedium)
    LinearProgressIndicator(
        progress = { if (sample.attempts == 0) 0f else sample.correct.toFloat() / sample.attempts },
        modifier = Modifier.fillMaxWidth(),
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    )
}

@Composable
internal fun PracticeHistory(trials: List<TrialRecordEntity>, historyKey: String = "") {
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(60_000)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Practice history", style = MaterialTheme.typography.titleLarge)
            Text("${trials.size} listening ${if (trials.size == 1) "answer" else "answers"} · newest first.", style = MaterialTheme.typography.bodySmall)
            HistoryPages(trials, historyKey, "answers",
                if (trials.size <= 20) "Show all ${trials.size} answers" else "Browse all ${trials.size} answers") { trial ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (trial.isCorrect) "Correct · ${trial.targetWord}" else "Heard ${trial.targetWord} · chose ${trial.selectedWord}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(answerAge(trial.timestampEpochMs, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun answerAge(timestamp: Long, now: Long): String {
    val minutes = (now - timestamp).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes == 0L -> "Just now"
        minutes < 60 -> "$minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
        hours < 24 -> "$hours ${if (hours == 1L) "hour" else "hours"} ago"
        else -> "$days ${if (days == 1L) "day" else "days"} ago"
    }
}
