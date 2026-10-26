package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.data.local.SpeakingRecordEntity
import kotlin.math.roundToInt

@Composable
internal fun SpeakingProgressCard(records: List<SpeakingRecordEntity>, nowEpochMs: Long, onPractice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Speaking progress", style = MaterialTheme.typography.titleLarge)
            Text("${records.size} scored ${if (records.size == 1) "recording" else "recordings"}", style = MaterialTheme.typography.titleMedium)
            Text("Pronunciation scores are estimates out of 100. They are separate from listening accuracy and do not affect listening streaks or reviews.", style = MaterialTheme.typography.bodySmall)
            if (records.isEmpty()) {
                Text("Complete a recording in Speak to start your speaking history.")
            }
            records.take(if (expanded) 20 else 5).forEach { record ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${record.targetWord} · /${record.targetPhoneme}/", style = MaterialTheme.typography.titleMedium)
                    Text("Score: ${record.score.roundToInt()}/100", style = MaterialTheme.typography.bodyLarge)
                    Text(answerAge(record.timestampEpochMs, nowEpochMs), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (records.size > 5) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show fewer recordings" else "Show latest ${minOf(records.size, 20)} recordings")
                }
            }
            val latestContrast = records.firstOrNull()?.contrastId
            if (latestContrast != null && VowelContrastsCatalog.getContrastById(latestContrast) != null) {
                Button(onClick = { onPractice(latestContrast) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Practice speaking again")
                }
            }
        }
    }
}
