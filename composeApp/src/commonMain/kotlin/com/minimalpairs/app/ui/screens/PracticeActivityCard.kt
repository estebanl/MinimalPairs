package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.progress.PracticeActivity

@Composable
internal fun PracticeActivityCard(activity: PracticeActivity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Daily practice", style = MaterialTheme.typography.titleLarge)
            Text("${activity.currentStreak}-day streak", style = MaterialTheme.typography.headlineMedium)
            Text("Longest streak: ${activity.longestStreak} ${if (activity.longestStreak == 1) "day" else "days"}")
            Text(
                when {
                    activity.practicedToday -> "You practiced today. Every answer counts."
                    activity.currentStreak > 0 -> "Answer one listening question today to keep your streak going."
                    else -> "Answer one listening question to start a new streak."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Last 7 days · ${activity.activeDays} active", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activity.days.forEachIndexed { index, day ->
                    val active = day.answers > 0
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.widthIn(min = 40.dp).clearAndSetSemantics {
                            contentDescription = "${day.date}: ${day.answers} ${if (day.answers == 1) "answer" else "answers"}${if (index == 6) ", today" else ""}"
                        }
                    ) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (index == 6) "Today" else day.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                            Text(day.answers.toString(), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            Text("Answer counts by day, using your current time zone. One answer keeps a day active.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
