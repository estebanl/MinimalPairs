package com.minimalpairs.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberProgressBackupFiles(): ProgressBackupFiles {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var saveText by remember { mutableStateOf<String?>(null) }
    var saveResult by remember { mutableStateOf<((Result<Boolean>) -> Unit)?>(null) }
    var openResult by remember { mutableStateOf<((Result<String?>) -> Unit)?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val callback = saveResult
        val text = saveText
        saveResult = null
        saveText = null
        if (uri == null) callback?.invoke(Result.success(false))
        else scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                requireNotNull(text) { "Backup is no longer available. Export again." }
                requireNotNull(resolver.openOutputStream(uri, "wt")) { "Cannot open the destination." }
                    .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                true
            } }
            callback?.invoke(result)
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = openResult
        openResult = null
        if (uri == null) callback?.invoke(Result.success(null))
        else scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                requireNotNull(resolver.openInputStream(uri)) { "Cannot open the backup." }.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= MAX_BACKUP_BYTES) {
                        val count = stream.read(buffer, 0, minOf(buffer.size, MAX_BACKUP_BYTES + 1 - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    val bytes = output.toByteArray()
                    require(bytes.size <= MAX_BACKUP_BYTES) { "Backup is larger than 20 MB." }
                    bytes.toString(Charsets.UTF_8)
                }
            } }
            callback?.invoke(result)
        }
    }
    return remember(saveLauncher, openLauncher) {
        object : ProgressBackupFiles {
            override fun save(text: String, onResult: (Result<Boolean>) -> Unit) {
                saveText = text
                saveResult = onResult
                runCatching { saveLauncher.launch("minimal-pairs-progress.json") }.onFailure {
                    saveText = null; saveResult = null; onResult(Result.failure(it))
                }
            }
            override fun open(onResult: (Result<String?>) -> Unit) {
                openResult = onResult
                runCatching { openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    .onFailure { openResult = null; onResult(Result.failure(it)) }
            }
        }
    }
}
