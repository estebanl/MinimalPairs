package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Only compose one page, while retaining access to every saved result. */
@Composable
internal fun <T> HistoryPages(
    records: List<T>,
    historyKey: String,
    noun: String,
    showMoreLabel: String,
    row: @Composable (T) -> Unit
) {
    var savedKey by rememberSaveable { mutableStateOf(historyKey) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    // Save the key too: filters may change while the parent section is collapsed.
    val sameSelection = savedKey == historyKey
    val isExpanded = expanded && sameSelection
    val lastPage = ((records.size - 1).coerceAtLeast(0)) / 20
    val currentPage = if (isExpanded) page.coerceIn(0, lastPage) else 0
    LaunchedEffect(historyKey, records.size) {
        if (savedKey != historyKey) {
            savedKey = historyKey
            expanded = false
            page = 0
        } else {
            page = page.coerceIn(0, lastPage)
        }
    }
    val start = currentPage * 20
    val end = minOf(records.size, start + if (isExpanded) 20 else 5)
    if (records.isNotEmpty()) {
        Text("${noun.replaceFirstChar { it.uppercase() }} ${start + 1}–$end of ${records.size}",
            style = MaterialTheme.typography.bodySmall)
        records.subList(start, end).forEach { row(it) }
    }
    if (isExpanded && records.size > 20) {
        FlowRow {
            TextButton(onClick = { page = currentPage - 1 }, enabled = currentPage > 0,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                Text("Newer $noun")
            }
            TextButton(onClick = { page = currentPage + 1 }, enabled = currentPage < lastPage,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                Text("Older $noun")
            }
        }
    }
    if (records.size > 5) {
        TextButton(onClick = { expanded = !isExpanded; page = 0 },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
            Text(if (isExpanded) "Show fewer $noun" else showMoreLabel)
        }
    }
}
