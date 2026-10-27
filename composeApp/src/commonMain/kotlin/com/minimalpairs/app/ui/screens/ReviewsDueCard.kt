package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.progress.ReviewSchedule
import com.minimalpairs.app.data.progress.reviewTiming

@Composable
internal fun ReviewsDueCard(schedule: ReviewSchedule, nowEpochMs: Long, onReview: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reviews due", style = MaterialTheme.typography.titleLarge)
            if (schedule.due.isEmpty()) {
                Text("No reviews due", style = MaterialTheme.typography.titleMedium)
                schedule.next?.let { next ->
                    val pair = ListeningContrastsCatalog.getContrastById(next.contrastId)?.pairs?.firstOrNull()
                    if (pair != null) {
                        Text("Next: ${pair.word1} / ${pair.word2} · ${reviewTiming(next.dueEpochMs, nowEpochMs)}", style = MaterialTheme.typography.bodyMedium)
                    }
                } ?: Text("Keep listening to build your review schedule.", style = MaterialTheme.typography.bodyMedium)
                Text("You can still practice any contrast below.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("${schedule.due.size} ${if (schedule.due.size == 1) "contrast is" else "contrasts are"} ready to review.", style = MaterialTheme.typography.bodyLarge)
                Text("Oldest due first. Each answer updates that contrast’s next review.", style = MaterialTheme.typography.bodySmall)
                schedule.due.take(if (expanded) schedule.due.size else 3).forEach { review ->
                    val pair = ListeningContrastsCatalog.getContrastById(review.contrastId)?.pairs?.firstOrNull()
                    if (pair != null) {
                        Button(onClick = { onReview(review.contrastId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Review ${pair.word1} / ${pair.word2}")
                        }
                    }
                }
                if (schedule.due.size > 3) {
                    TextButton(onClick = { expanded = !expanded },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                        Text(if (expanded) "Show fewer reviews" else "Show all ${schedule.due.size} reviews")
                    }
                }
            }
        }
    }
}
