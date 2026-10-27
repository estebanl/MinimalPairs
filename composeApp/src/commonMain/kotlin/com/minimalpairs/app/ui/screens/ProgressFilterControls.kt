package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.progress.ProgressDateRange
import com.minimalpairs.app.data.progress.ProgressFilters

@Composable
internal fun ProgressFilterControls(
    filters: ProgressFilters,
    onDateRange: (ProgressDateRange) -> Unit,
    onContrast: (String?) -> Unit,
    onClear: () -> Unit
) {
    var choosingContrast by remember { mutableStateOf(false) }
    val selected = ListeningContrastsCatalog.getContrastById(filters.contrastId.orEmpty())?.pairs?.firstOrNull()
    val contrastLabel = selected?.let { "${it.word1} / ${it.word2}" } ?: "All contrasts"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Filter results", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressDateRange.entries.forEach { range ->
                FilterChip(selected = filters.dateRange == range, onClick = { onDateRange(range) },
                    label = { Text(range.label) })
            }
        }
        Box {
            TextButton(onClick = { choosingContrast = true }, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                Text("Contrast: $contrastLabel")
            }
            DropdownMenu(expanded = choosingContrast, onDismissRequest = { choosingContrast = false }) {
                DropdownMenuItem(text = { Text("All contrasts") }, onClick = {
                    choosingContrast = false
                    onContrast(null)
                })
                ListeningContrastsCatalog.defaultContrasts.forEach { contrast ->
                    val pair = contrast.pairs.first()
                    DropdownMenuItem(text = { Text("${pair.word1} / ${pair.word2}") }, onClick = {
                        choosingContrast = false
                        onContrast(contrast.id)
                    })
                }
            }
        }
        Text("Showing: ${filters.dateRange.label} · $contrastLabel", style = MaterialTheme.typography.bodyMedium)
        Text("Dates use your current time zone and include today. Streaks, daily activity, mastery estimates, and reviews use all listening practice.",
            style = MaterialTheme.typography.bodySmall)
        if (filters.isActive) {
            TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) { Text("Clear filters") }
        }
    }
}
