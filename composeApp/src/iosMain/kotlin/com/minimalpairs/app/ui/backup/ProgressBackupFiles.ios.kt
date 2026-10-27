@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.minimalpairs.app.ui.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject

@Composable
actual fun rememberProgressBackupFiles(): ProgressBackupFiles {
    val controller = LocalUIViewController.current
    return remember(controller) { IosProgressBackupFiles(controller) }
}

private class IosProgressBackupFiles(private val controller: UIViewController) : ProgressBackupFiles {
    // UIDocumentPicker retains its delegate weakly.
    private var activeDelegate: BackupPickerDelegate? = null

    override fun save(text: String, onResult: (Result<Boolean>) -> Unit) {
        runCatching {
            val url = requireNotNull(NSFileManager.defaultManager.temporaryDirectory
                .URLByAppendingPathComponent("minimal-pairs-progress-${NSUUID().UUIDString}.json"))
            val bytes = text.encodeToByteArray()
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            check(data.writeToURL(url, atomically = true)) { "Cannot prepare the backup file." }
            val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
            activeDelegate = BackupPickerDelegate { selected ->
                NSFileManager.defaultManager.removeItemAtURL(url, error = null)
                activeDelegate = null
                onResult(Result.success(selected != null))
            }
            picker.delegate = activeDelegate
            controller.presentViewController(picker, animated = true, completion = null)
        }.onFailure { activeDelegate = null; onResult(Result.failure(it)) }
    }

    override fun open(onResult: (Result<String?>) -> Unit) {
        runCatching {
            val picker = UIDocumentPickerViewController(documentTypes = listOf("public.json", "public.text"), inMode = UIDocumentPickerMode.UIDocumentPickerModeImport)
            picker.allowsMultipleSelection = false
            activeDelegate = BackupPickerDelegate { selected ->
                activeDelegate = null
                onResult(runCatching {
                    if (selected == null) null else {
                        val accessed = selected.startAccessingSecurityScopedResource()
                        try {
                            val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(requireNotNull(selected.path), error = null)
                            val length = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue
                            require(length != null && length <= MAX_BACKUP_BYTES) { "Backup is larger than 20 MB or cannot be read." }
                            val data = requireNotNull(NSData.create(contentsOfURL = selected)) { "Cannot read the backup." }
                            require(data.length <= MAX_BACKUP_BYTES.toULong()) { "Backup is larger than 20 MB." }
                            if (data.length == 0uL) "" else requireNotNull(data.bytes).readBytes(data.length.toInt()).decodeToString(throwOnInvalidSequence = true)
                        } finally { if (accessed) selected.stopAccessingSecurityScopedResource() }
                    }
                })
            }
            picker.delegate = activeDelegate
            controller.presentViewController(picker, animated = true, completion = null)
        }.onFailure { activeDelegate = null; onResult(Result.failure(it)) }
    }
}

private class BackupPickerDelegate(private val finished: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        finished(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { finished(null) }
}
