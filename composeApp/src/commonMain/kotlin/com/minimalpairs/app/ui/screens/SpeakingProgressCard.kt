package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.data.local.SpeakingRecordEntity
import com.minimalpairs.app.data.progress.SpeakingSoundSummary
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SpeakingProgressCard(
    records: List<SpeakingRecordEntity>,
    summaries: List<SpeakingSoundSummary>,
    nowEpochMs: Long,
    onStartSpeaking: () -> Unit,
    onPractice: (String, String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var allSounds by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Speaking progress", style = MaterialTheme.typography.titleLarge)
            Text("${records.size} scored ${if (records.size == 1) "recording" else "recordings"}", style = MaterialTheme.typography.titleMedium)
            Text("Pronunciation scores are estimates out of 100. They are separate from listening accuracy and do not affect listening streaks or reviews.", style = MaterialTheme.typography.bodySmall)
            if (records.isEmpty()) {
                Text("Complete a recording in Speak to start your speaking history.")
                Button(onClick = onStartSpeaking, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Start speaking")
                }
            }
            if (summaries.isNotEmpty()) {
                Text("By sound", style = MaterialTheme.typography.titleLarge)
                Text("Lowest recent averages first. Recording conditions can affect scores.", style = MaterialTheme.typography.bodySmall)
                summaries.take(if (allSounds) summaries.size else 3).forEach { sound ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("/${sound.phoneme}/ · ${sound.latest.targetWord}", style = MaterialTheme.typography.titleMedium)
                            Text("Recent average: ${sound.recentAverage}/100", style = MaterialTheme.typography.bodyLarge)
                            Text("Based on the latest ${sound.recentCount} ${if (sound.recentCount == 1) "recording" else "recordings"}.", style = MaterialTheme.typography.bodySmall)
                            Text("Latest: ${sound.latest.score.roundToInt()}/100 · Best: ${sound.bestScore}/100 · Attempts: ${sound.attempts}", style = MaterialTheme.typography.bodySmall)
                            val change = sound.changePoints
                            Text(when {
                                change == null -> "Record this sound ${10 - sound.recentCount - sound.previousCount} more ${if (10 - sound.recentCount - sound.previousCount == 1) "time" else "times"} to compare two sets of five."
                                change > 0 -> "Up $change points from the previous five recordings."
                                change < 0 -> "Down ${abs(change)} points from the previous five recordings."
                                else -> "Unchanged from the previous five recordings."
                            }, style = MaterialTheme.typography.bodyMedium)
                            VowelContrastsCatalog.getContrastById(sound.contrastId)?.takeIf { sound.phoneme == it.phoneme1 || sound.phoneme == it.phoneme2 }?.let {
                                Button(onClick = { onPractice(sound.contrastId, sound.phoneme) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text("Practice /${sound.phoneme}/ · ${sound.latest.targetWord}")
                                }
                            }
                        }
                    }
                }
                if (summaries.size > 3) {
                    TextButton(onClick = { allSounds = !allSounds }) {
                        Text(if (allSounds) "Show fewer sounds" else "Show all ${summaries.size} sounds")
                    }
                }
                Text("Recent recordings", style = MaterialTheme.typography.titleLarge)
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
                Button(onClick = { onPractice(latestContrast, records.first().targetPhoneme) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Practice speaking again")
                }
            }
        }
    }
}
