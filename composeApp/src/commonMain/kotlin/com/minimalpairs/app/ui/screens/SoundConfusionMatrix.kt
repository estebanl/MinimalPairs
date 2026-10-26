package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.progress.ContrastProgress
import kotlin.math.roundToInt

@Composable
internal fun SoundConfusionMatrix(contrasts: List<ContrastProgress>, onPractice: (String) -> Unit) {
    val available = contrasts.filter { ListeningContrastsCatalog.getContrastById(it.contrastId) != null }
    if (available.isEmpty()) return
    var selectedId by rememberSaveable { mutableStateOf(available.first().contrastId) }
    val selected = available.firstOrNull { it.contrastId == selectedId } ?: available.first()
    // Keep the displayed contrast stable when accuracy updates reorder the list.
    // If it disappears, retain the fallback rather than jumping back if it returns.
    LaunchedEffect(selected.contrastId) { selectedId = selected.contrastId }
    val contrast = ListeningContrastsCatalog.getContrastById(selected.contrastId) ?: return
    val sounds = listOf(contrast.phoneme1, contrast.phoneme2)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sound patterns", style = MaterialTheme.typography.titleLarge)
            Text("Choose a contrast to see what you heard and what you selected.", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { progress ->
                    val pair = ListeningContrastsCatalog.getContrastById(progress.contrastId)!!.pairs.first()
                    FilterChip(
                        selected = progress.contrastId == selected.contrastId,
                        onClick = { selectedId = progress.contrastId },
                        label = { Text("${pair.word1} / ${pair.word2}") }
                    )
                }
            }
            Text("Rows: sound played. Columns: sound chosen.", style = MaterialTheme.typography.bodySmall)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < (280 * LocalDensity.current.fontScale).dp
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!stacked) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Heard ↓\nChose →", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                            sounds.forEach { sound ->
                                Text("/$sound/", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    sounds.forEach { target ->
                        val row = selected.confusion[target].orEmpty()
                        val total = row.values.sum()
                        if (stacked) {
                            Text("Heard /$target/ · $total ${if (total == 1) "answer" else "answers"}", style = MaterialTheme.typography.titleSmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!stacked) {
                                Column(Modifier.weight(1f)) {
                                    Text("/$target/", style = MaterialTheme.typography.titleMedium)
                                    Text("$total ${if (total == 1) "answer" else "answers"}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            sounds.forEach { chosen ->
                                ConfusionCell(target, chosen, row[chosen] ?: 0, total, stacked, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Text("Matching sounds on the diagonal are correct answers. Other cells show mix-ups. Percentages use each row’s answers; — means that sound has not been tested yet.", style = MaterialTheme.typography.bodySmall)
            Text("Small samples can change quickly. Counts include only this contrast.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onPractice(selected.contrastId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Practice these sounds")
            }
        }
    }
}

@Composable
private fun ConfusionCell(target: String, chosen: String, count: Int, total: Int, showChosen: Boolean, modifier: Modifier) {
    val percent = if (total == 0) null else (count * 100f / total).roundToInt()
    val correct = target == chosen
    val background = when {
        count == 0 -> MaterialTheme.colorScheme.surface
        correct -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    Surface(
        color = background,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.heightIn(min = 82.dp).clearAndSetSemantics {
            contentDescription = if (percent == null) "Heard /$target/, chose /$chosen/: not practiced"
            else "Heard /$target/, chose /$chosen/: $count of $total answers, $percent percent, ${if (correct) "correct" else "mixed up"}"
        }
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (showChosen) {
                Text("Chose", style = MaterialTheme.typography.bodySmall)
                Text("/$chosen/", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Text(percent?.let { "$it%" } ?: "—", style = MaterialTheme.typography.titleMedium)
            Text(if (percent == null) "No data" else "$count ${if (count == 1) "time" else "times"}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
