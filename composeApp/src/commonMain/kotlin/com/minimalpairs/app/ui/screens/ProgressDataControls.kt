package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.backup.ProgressBackupCodec
import com.minimalpairs.app.data.local.ProgressSnapshot
import com.minimalpairs.app.ui.backup.ProgressBackupFiles
import com.minimalpairs.app.ui.backup.rememberProgressBackupFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProgressDataControls(
    pendingResults: Int,
    exportSnapshot: suspend () -> ProgressSnapshot,
    importSnapshot: suspend (ProgressSnapshot) -> Unit,
    resetProgress: suspend () -> Unit,
    files: ProgressBackupFiles = rememberProgressBackupFiles()
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var resetConfirmation by remember { mutableStateOf(false) }
    var importConfirmation by remember { mutableStateOf<ProgressSnapshot?>(null) }
    fun report(text: String, error: Boolean = false) { message = text; failed = error; busy = false }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Manage progress", style = MaterialTheme.typography.titleLarge)
        Text("Export all saved practice results and review schedules to a JSON backup. Filters do not limit the backup. Audio recordings are not included.",
            style = MaterialTheme.typography.bodyMedium)
        Text("Move the file to another device and import it there. This is a manual backup, not automatic synchronization.",
            style = MaterialTheme.typography.bodySmall)
        Text("Backups support up to 100,000 saved records and 20 MB.", style = MaterialTheme.typography.bodySmall)
        if (pendingResults > 0) Text("Finish saving or retry pending results before exporting or importing.", style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy && pendingResults == 0, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
            busy = true; message = null
            scope.launch {
                try {
                    val snapshot = exportSnapshot()
                    val text = withContext(Dispatchers.Default) { ProgressBackupCodec.encode(snapshot) }
                    files.save(text) { result ->
                        result.fold(onSuccess = { report(if (it) "Backup exported." else "Export cancelled.") },
                            onFailure = { report("Couldn't save the backup. Choose another location and try again.", true) })
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    report(if (error is IllegalArgumentException) error.message ?: "The saved data cannot be exported."
                        else "Couldn't prepare the backup. Retry saving any pending results, then try again.", true)
                }
            }
        }) { Text("Export backup") }
        OutlinedButton(enabled = !busy && pendingResults == 0, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
            busy = true; message = null
            files.open { result ->
                result.fold(onSuccess = { text ->
                    if (text == null) report("Import cancelled.")
                    else scope.launch {
                        try {
                            importConfirmation = withContext(Dispatchers.Default) { ProgressBackupCodec.decode(text) }
                            busy = false
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            report("This file is not a supported Progress backup. No data was changed.", true)
                        }
                    }
                }, onFailure = { report("Couldn't read that backup. Choose a readable JSON file up to 20 MB.", true) })
            }
        }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) { Text("Import backup") }
        TextButton(enabled = !busy, onClick = { resetConfirmation = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Reset all progress") }
        if (busy) Text("Working…", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
    if (resetConfirmation) AlertDialog(
        onDismissRequest = { resetConfirmation = false },
        title = { Text("Reset all progress?") },
        text = { Text("This permanently removes listening answers, speaking scores, Stories and Rhythm results, review schedules, and pending saves from this device. Export a backup first if you want to keep them. Exercises and app settings stay available.") },
        confirmButton = { TextButton(onClick = {
            resetConfirmation = false; busy = true; message = null
            scope.launch {
                try { resetProgress(); report("All progress on this device was reset.") }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    report("Couldn't reset progress. Try again.", true)
                }
            }
        }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Reset progress") } },
        dismissButton = { TextButton(onClick = { resetConfirmation = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) { Text("Cancel") } }
    )
    importConfirmation?.let { snapshot ->
        AlertDialog(onDismissRequest = { importConfirmation = null },
            title = { Text("Import this backup?") },
            text = { Text("Merge ${snapshot.trials.size} listening answers, ${snapshot.speaking.size} speaking scores, and ${snapshot.modules.size} Stories/Rhythm results into this device. Identical results are kept once. Conflicting backups are rejected without changing your progress.") },
            confirmButton = { TextButton(onClick = {
                importConfirmation = null; busy = true; message = null
                scope.launch {
                    try { importSnapshot(snapshot); report("Backup imported. Existing results were kept.") }
                    catch (error: Exception) {
                        if (error is CancellationException) throw error
                        report("Couldn't import this backup. It may conflict with existing results, or storage may be unavailable. No backup records were imported.", true)
                    }
                }
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) { Text("Import results") } },
            dismissButton = { TextButton(onClick = { importConfirmation = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) { Text("Cancel") } }
        )
    }
}
