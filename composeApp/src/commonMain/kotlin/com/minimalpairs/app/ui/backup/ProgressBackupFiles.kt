package com.minimalpairs.app.ui.backup

import androidx.compose.runtime.Composable

/** Platform document pickers keep the destination under the learner's control. */
interface ProgressBackupFiles {
    fun save(text: String, onResult: (Result<Boolean>) -> Unit)
    fun open(onResult: (Result<String?>) -> Unit)
}

const val MAX_BACKUP_BYTES = com.minimalpairs.app.data.backup.MAX_BACKUP_BYTES

@Composable
expect fun rememberProgressBackupFiles(): ProgressBackupFiles
