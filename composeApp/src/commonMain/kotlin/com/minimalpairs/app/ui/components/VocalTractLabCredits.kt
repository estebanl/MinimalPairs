package com.minimalpairs.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.ui.theme.PracticeTheme
import kotlinx.coroutines.CancellationException
import minimalpairs.composeapp.generated.resources.Res

@Composable
internal fun VocalTractLabCredits() {
    var open by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = PracticeTheme.colors.accentInk)
    TextButton(colors = buttonColors, onClick = { open = true }) { Text("Model credits & license") }
    if (!open) return
    val license by produceState("Loading license…") {
        value = try {
            Res.readBytes("files/vtl/COPYING.txt").decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            "The bundled license could not be loaded. Use the source link to read the upstream license."
        }
    }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("VocalTractLab") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("VocalTractLab model geometry and JD3 speaker data. Copyright Peter Birkholz and contributors. GPL version 3 or later.")
                Text("The app includes the pinned upstream source, speaker data, exporter, and license with these meshes.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(colors = buttonColors, onClick = {
                    uriHandler.openUri("https://github.com/TUD-STKS/VocalTractLabBackend-dev/tree/df30392f18dc5e175b577c3ba734caaa65a3927f")
                }) { Text("View model source") }
                Text(license, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(colors = buttonColors, onClick = { open = false }) { Text("Close") } }
    )
}
