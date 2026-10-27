package com.minimalpairs.app.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import com.minimalpairs.app.data.backup.ProgressBackupCodec
import com.minimalpairs.app.data.local.*
import com.minimalpairs.app.ui.backup.ProgressBackupFiles
import com.minimalpairs.app.ui.theme.MinimalPairsTheme
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ProgressDataControlsTest {
    @Test
    fun transfersUseFilesAndResetAndImportRequireConfirmation() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val snapshot = ProgressSnapshot(emptyList(), emptyList(),
                    listOf(SpeakingRecordEntity(-1, "i_vs_I", "ship", "ɪ", 80f, 500, 1000)))
                var exports = 0
                var resets = 0
                var imported: ProgressSnapshot? = null
                var exported: String? = null
                val pending = mutableStateOf(0)
                var importText = ProgressBackupCodec.encode(snapshot)
                val files = object : ProgressBackupFiles {
                    override fun save(text: String, onResult: (Result<Boolean>) -> Unit) { exported = text; onResult(Result.success(true)) }
                    override fun open(onResult: (Result<String?>) -> Unit) { onResult(Result.success(importText)) }
                }
                val scene = ImageComposeScene(width = 430, height = 1500) {
                    MinimalPairsTheme {
                        ProgressDataControls(pending.value, { exports++; snapshot }, { imported = it }, { resets++ }, files)
                    }
                }
                try {
                    var frame = 0L
                    fun settle() { repeat(40) { scene.render(++frame * 16_666_667L).close() } }
                    fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
                    fun nodes() = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
                    fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                    fun button(label: String) = nodes().first { it.text() == label && it.config.contains(SemanticsActions.OnClick) }
                    fun click(label: String) { button(label).config[SemanticsActions.OnClick].action!!.invoke(); settle() }
                    fun awaitText(label: String) {
                        repeat(100) { if (nodes().any { it.text() == label }) return; settle() }
                        fail("Missing text: $label")
                    }
                    settle()
                    click("Reset all progress")
                    assertEquals(0, resets)
                    click("Cancel")
                    assertEquals(0, resets)
                    click("Reset all progress")
                    click("Reset progress")
                    assertEquals(1, resets)
                    click("Export backup")
                    awaitText("Backup exported.")
                    assertEquals(1, exports)
                    assertEquals(snapshot, ProgressBackupCodec.decode(exported!!))
                    click("Import backup")
                    awaitText("Import this backup?")
                    assertNull(imported)
                    click("Cancel")
                    assertNull(imported)
                    click("Import backup")
                    awaitText("Import this backup?")
                    click("Import results")
                    assertEquals(snapshot, imported)
                    imported = null
                    importText = "invalid json"
                    click("Import backup")
                    awaitText("This file is not a supported Progress backup. No data was changed.")
                    assertNull(imported)
                    pending.value = 1
                    settle()
                    assertTrue(button("Export backup").config.contains(SemanticsProperties.Disabled))
                    assertTrue(button("Import backup").config.contains(SemanticsProperties.Disabled))
                } finally { scene.close() }
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
