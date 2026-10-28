package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.progress.ContrastProgress
import com.minimalpairs.app.data.progress.ReviewSchedule
import com.minimalpairs.app.data.progress.reviewTiming

@Composable
internal fun ReviewsDueCard(
    schedule: ReviewSchedule,
    nowEpochMs: Long,
    onReview: (String) -> Unit,
    contrasts: List<ContrastProgress> = emptyList()
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (schedule.due.isEmpty()) "No reviews due" else "Ready to review", style = MaterialTheme.typography.titleSmall)
            if (schedule.due.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall) {
                    Text("${schedule.due.size} due", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (schedule.due.isEmpty()) {
            schedule.next?.let { next ->
                val pair = ListeningContrastsCatalog.getContrastById(next.contrastId)?.pairs?.firstOrNull()
                if (pair != null) {
                    Text("Next: ${pair.word1} / ${pair.word2} · ${reviewTiming(next.dueEpochMs, nowEpochMs)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            schedule.due.take(if (expanded) schedule.due.size else 3).forEachIndexed { index, review ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CompactContrastRow(review.contrastId, contrasts.firstOrNull { it.contrastId == review.contrastId },
                    nowEpochMs, onReview, review = true)
            }
            if (schedule.due.size > 3) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                    Text(if (expanded) "Show fewer reviews" else "Show all ${schedule.due.size} reviews")
                }
            }
        }
    }
}
