package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.progress.ContrastProgress
import com.minimalpairs.app.data.progress.ProgressState
import com.minimalpairs.app.data.progress.reviewDueAt
import com.minimalpairs.app.data.progress.reviewTiming
import com.minimalpairs.app.ui.components.PracticeIcon
import kotlin.math.roundToInt

@Composable
internal fun CompactProgressSection(
    title: String,
    summary: String = "",
    label: String = title,
    divider: Boolean = true,
    muted: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val contentState = rememberSaveableStateHolder()
    Column(Modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(vertical = 8.dp),
            shape = MaterialTheme.shapes.extraSmall,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = "${if (expanded) "Hide" else "Show"} ${title.lowercase()}"
                stateDescription = listOfNotNull(
                    if (expanded) "Expanded" else "Collapsed",
                    label.takeIf { it != title }, summary.takeIf { it.isNotEmpty() }
                ).joinToString(" · ")
            }
        ) {
            val labelStyle = if (muted) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
            val labelColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            if (LocalDensity.current.fontScale > 1.3f) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = labelStyle, color = labelColor)
                    if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(label, modifier = Modifier.weight(1f), style = labelStyle, color = labelColor)
                if (summary.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(summary, modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                }
            }
            Spacer(Modifier.width(12.dp))
            PracticeIcon(PracticeIcon.ChevronRight, modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f))
        }
        if (expanded) {
            contentState.SaveableStateProvider(title) {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun CompactListeningSummary(state: ProgressState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val streakDescription = "Current streak: ${state.activity.currentStreak} ${if (state.activity.currentStreak == 1) "day" else "days"}"
            if (LocalDensity.current.fontScale > 1.3f) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompactListeningMetric("${state.accuracy}%", "accuracy", "${state.accuracy}% accuracy", Modifier.widthIn(min = 72.dp))
                    CompactListeningMetric("${state.attempts}", "answers", "Answers: ${state.attempts}", Modifier.widthIn(min = 72.dp))
                    CompactListeningMetric("${state.activity.currentStreak}", "day streak", streakDescription, Modifier.widthIn(min = 72.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CompactListeningMetric("${state.accuracy}%", "accuracy", "${state.accuracy}% accuracy", Modifier.weight(1.3f))
                    VerticalDivider(Modifier.padding(horizontal = 10.dp).size(1.dp, 40.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    CompactListeningMetric("${state.attempts}", "answers", "Answers: ${state.attempts}", Modifier.weight(1f))
                    VerticalDivider(Modifier.padding(horizontal = 10.dp).size(1.dp, 40.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    CompactListeningMetric("${state.activity.currentStreak}", "day streak", streakDescription, Modifier.weight(1f))
                }
            }
            Text("${state.contrasts.size} ${if (state.contrasts.size == 1) "contrast" else "contrasts"} practiced · Listening", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.filters.isActive) {
                Text("Streak uses all listening practice.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompactListeningMetric(value: String, label: String, description: String, modifier: Modifier) {
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CompactContrastRow(
    contrastId: String,
    progress: ContrastProgress?,
    nowEpochMs: Long,
    onPractice: (String) -> Unit,
    review: Boolean = false,
    showDetails: Boolean = false
) {
    val pair = ListeningContrastsCatalog.getContrastById(contrastId)?.pairs?.firstOrNull() ?: return
    val label = "${pair.word1} / ${pair.word2}"
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(listOfNotNull(progress?.let { "${it.accuracy}% accuracy" }, if (review) "Review due" else null).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showDetails && progress != null) {
                Text("${progress.correct} of ${progress.attempts} correct", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                progress.card?.let { card ->
                    Text("Mastery estimate · ${(card.masteryScore * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                    Text(reviewTiming(reviewDueAt(card), nowEpochMs), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val action = if (review) "Review" else "Practice"
        TextButton(onClick = { onPractice(contrastId) },
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$action $label" }) {
            Text(action, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            PracticeIcon(PracticeIcon.Forward, modifier = Modifier.size(16.dp))
        }
    }
}
