package com.minimalpairs.app.ui.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberProgressBackupFiles(): ProgressBackupFiles = remember {
    object : ProgressBackupFiles {
        override fun save(text: String, onResult: (Result<Boolean>) -> Unit) {
            SwingUtilities.invokeLater {
                onResult(runCatching {
                    val picker = JFileChooser().apply {
                        selectedFile = java.io.File("minimal-pairs-progress.json")
                        fileFilter = FileNameExtensionFilter("Progress backup (.json)", "json")
                    }
                    if (picker.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) false
                    else if (picker.selectedFile.exists() && JOptionPane.showConfirmDialog(null,
                            "Replace ${picker.selectedFile.name}?", "Replace backup", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) false
                    else { picker.selectedFile.writeText(text, Charsets.UTF_8); true }
                })
            }
        }
        override fun open(onResult: (Result<String?>) -> Unit) {
            SwingUtilities.invokeLater {
                onResult(runCatching {
                    val picker = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Progress backup (.json)", "json") }
                    if (picker.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) null
                    else picker.selectedFile.inputStream().use {
                        val bytes = it.readNBytes(MAX_BACKUP_BYTES + 1)
                        require(bytes.size <= MAX_BACKUP_BYTES) { "Backup is larger than 20 MB." }
                        bytes.toString(Charsets.UTF_8)
                    }
                })
            }
        }
    }
}
