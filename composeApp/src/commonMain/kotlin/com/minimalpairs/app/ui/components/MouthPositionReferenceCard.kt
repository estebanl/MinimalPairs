package com.minimalpairs.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.ui.reference.MouthPositionReferences
import com.minimalpairs.app.ui.reference.Mouth3dState
import com.minimalpairs.app.ui.reference.MouthView
import com.minimalpairs.app.ui.reference.mouthPose
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** A teaching reference only: recording never changes the illustrated pose. */
@Composable
fun MouthPositionReferenceCard(
    phoneme: String,
    exampleWord: String,
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    isPlayingReference: Boolean = false,
    canPlayReference: Boolean = true,
    onPlayReference: (() -> Unit)? = null
) {
    val reference = MouthPositionReferences.forPhoneme(phoneme)
    val scheme = MaterialTheme.colorScheme
    val pose = mouthPose(phoneme, exampleWord)
    val supports3d = mouth3dAvailable() && pose != null
    var use3d by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(MouthView.front) }
    var opacity by remember { mutableStateOf(.5f) }

    Surface(
        modifier = modifier.widthIn(max = 480.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Mouth position",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("$exampleWord /$phoneme/", style = MaterialTheme.typography.titleLarge)
                Surface(shape = RoundedCornerShape(8.dp), color = scheme.primaryContainer) {
                    Text(
                        "Reference pose",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer
                    )
                }
            }
            if (reference == null) {
                Text("A mouth-position reference is not available for this sound.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(reference.placementCue, style = MaterialTheme.typography.bodyMedium)
                if (supports3d) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !use3d, onClick = { use3d = false }, label = { Text("Illustration") })
                        FilterChip(selected = use3d, onClick = { failed = false; use3d = true }, label = { Text("3D prototype") })
                    }
                }
                if (supports3d && use3d && !failed) {
                    Text("Educational prototype · awaiting phonetic review.", style = MaterialTheme.typography.labelMedium)
                    Text("A reference vowel pose. It does not show your tongue or move in time with the word.", style = MaterialTheme.typography.bodySmall)
                    Text("${reference.frontDescription} ${reference.sideDescription}", style = MaterialTheme.typography.bodySmall)
                    Mouth3dViewport(
                        state = Mouth3dState(pose, view, opacity),
                        modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp)),
                        onError = { failed = true }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = view == MouthView.front, onClick = { view = MouthView.front }, label = { Text("Front") })
                        FilterChip(selected = view == MouthView.side, onClick = { view = MouthView.side }, label = { Text("Side") })
                        OutlinedButton(onClick = { view = MouthView.front; opacity = .5f }) { Text("Reset view") }
                    }
                    Text("Tissue opacity · ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(value = opacity, onValueChange = { opacity = it }, modifier = Modifier.semantics { contentDescription = "Tissue opacity" })
                    if (onPlayReference != null) {
                        OutlinedButton(enabled = canPlayReference && !isRecording, onClick = onPlayReference) {
                            Text(if (isPlayingReference) "Playing $exampleWord…" else "Listen to $exampleWord")
                        }
                    }
                } else {
                    if (failed && use3d && supports3d) Text("3D unavailable. Showing the illustration.", style = MaterialTheme.typography.bodySmall)
                    ReferenceView("Front view", reference.front, reference.frontDescription)
                    ReferenceView("Side view", reference.side, reference.sideDescription)
                }
            }
        }
    }
}

@Composable
private fun ReferenceView(label: String, resource: DrawableResource, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Image(
            painter = painterResource(resource),
            contentDescription = "$label. $description",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(12.dp))
                // The opaque artwork has a white studio backdrop in both app themes.
                .background(Color.White)
        )
    }
}
