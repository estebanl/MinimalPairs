package com.minimalpairs.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.minimalpairs.app.core.cv.*
import com.minimalpairs.app.core.hal.LipCameraPreview
import com.minimalpairs.app.ui.reference.MouthPositionReferences
import com.minimalpairs.app.ui.theme.PracticeTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

@Composable
fun LipCameraFeedbackCard(phoneme: String, exampleWord: String, modifier: Modifier = Modifier,
    controller: LipCameraController = remember { LipCameraController() },
    preview: @Composable (Modifier, (LipCameraFrame) -> Unit) -> Unit = { viewModifier, callback ->
        LipCameraPreview(viewModifier, callback)
    }) {
    val state by controller.state.collectAsState()
    val reference = MouthPositionReferences.forPhoneme(phoneme)
    val scheme = MaterialTheme.colorScheme
    val ink = PracticeTheme.colors.accentInk
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (controller.state.value.status.active) controller.stop(LipCameraStatus.PAUSED)
    }
    DisposableEffect(controller) { onDispose { controller.stop() } }
    // Do not leave an old measurement visible if the camera stops delivering frames.
    LaunchedEffect(state.session, state.frameIndex) {
        if (state.measurement != null) {
            delay(2000)
            controller.accept(state.session, LipCameraFrame(LipCameraStatus.NO_FACE))
        }
    }
    Surface(modifier.widthIn(max = 520.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = scheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mirror your mouth", style = MaterialTheme.typography.titleMedium)
            Text("$exampleWord /$phoneme/", style = MaterialTheme.typography.titleLarge)
            Text(reference?.placementCue ?: "Use the sound map to explore this vowel.", style = MaterialTheme.typography.bodyMedium)
            if (state.status.active) {
                key(state.session) {
                    val session = state.session
                    preview(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                        controller.accept(session, it)
                    }
                }
            }
            Text(when (state.status) {
                LipCameraStatus.OFF -> "Start the front camera to compare your lips with the reference."
                LipCameraStatus.STARTING -> "Opening camera…"
                LipCameraStatus.TRACKING -> "Live lip shape"
                LipCameraStatus.NO_FACE -> "Bring your face into view."
                LipCameraStatus.ADJUST_POSITION -> "Face the camera straight on, move a little closer, and keep your mouth visible."
                LipCameraStatus.PERMISSION_DENIED -> "Camera access is off. Allow it in app settings, then try again."
                LipCameraStatus.UNAVAILABLE -> "Live camera feedback is not available on this device. The reference and sound map are still available."
                LipCameraStatus.ERROR -> "Camera feedback stopped. Try starting the camera again."
                LipCameraStatus.PAUSED -> "Camera paused while the app was away. Start it again when you’re ready."
            }, style = MaterialTheme.typography.bodyMedium)
            state.measurement?.let { measurement ->
                Text("Lip opening: ${(measurement.openingRatio * 100).toInt()}% of mouth width",
                    style = MaterialTheme.typography.labelLarge)
                Canvas(Modifier.fillMaxWidth().height(100.dp).semantics {
                    contentDescription = "Live outline of your lips. Opening ${(measurement.openingRatio * 100).toInt()} percent of mouth width."
                }) {
                    fun drawContour(points: List<LipPoint>) {
                        val path = Path()
                        val scale = minOf(size.width * .65f, size.height * 1.2f)
                        points.forEachIndexed { index, p ->
                            val x = size.width / 2 + p.x * scale
                            val y = size.height / 2 + p.y * scale
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        drawPath(path, ink, style = Stroke(2.dp.toPx()))
                    }
                    drawContour(measurement.outerContour)
                    drawContour(measurement.innerContour)
                }
            }
            reference?.let {
                Text("Target lip position · /$phoneme/", style = MaterialTheme.typography.labelLarge)
                Image(painterResource(it.front), contentDescription = "Target lips. ${it.frontDescription}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).aspectRatio(1.5f).clip(RoundedCornerShape(12.dp)))
            }
            OutlinedButton(onClick = {
                if (state.status.active) controller.stop() else controller.start()
            }, colors = ButtonDefaults.outlinedButtonColors(contentColor = ink)) {
                Text(if (state.status.active) "Stop camera" else "Start camera")
            }
            Text("Frames are analyzed on this device and are not saved. Lip opening is an estimate, not a pronunciation score. Use the sound map for vowel feedback.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}
