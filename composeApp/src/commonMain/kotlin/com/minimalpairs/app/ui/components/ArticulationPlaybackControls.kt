package com.minimalpairs.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.minimalpairs.app.ui.reference.ArticulationPlaybackState

/** Opt-in controls; the reference card does not activate these until reviewed assets exist. */
@Composable
fun ArticulationPlaybackControls(state: ArticulationPlaybackState, onStateChange: (ArticulationPlaybackState) -> Unit) {
    if (state.clip == null || state.clip.durationMs == 0) return
    Column {
        Text("Illustrative movement · not synchronized with audio")
        Row {
            TextButton(onClick = { onStateChange(if (state.playing) state.pause() else state.play()) }) {
                Text(if (state.playing) "Pause movement" else "Play movement")
            }
            TextButton(onClick = { onStateChange(state.reset()) }) { Text("Reset movement") }
        }
    }
}
