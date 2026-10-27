package com.minimalpairs.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
    practiceInstruction: String = "Say /$phoneme/ on its own.",
    placementCue: String? = null,
    footer: @Composable () -> Unit = {},
    measurements: @Composable () -> Unit = {},
    controller: LipCameraController = remember { LipCameraController() },
    preview: @Composable (Modifier, (LipCameraFrame) -> Unit) -> Unit = { viewModifier, callback ->
        LipCameraPreview(viewModifier, callback)
    }) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val state by controller.state.collectAsState()
    // A reference belongs to one sound and one camera session; never compare another vowel/session.
    var comparisonReference by remember(phoneme, state.session) { mutableStateOf<LipMeasurement?>(null) }
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().then(if (largeText && !state.status.active) Modifier.heightIn(min = 360.dp) else Modifier.aspectRatio(1.17f)).clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceVariant)) {
            if (state.status.active) {
                key(state.session) {
                    val session = state.session
                    preview(Modifier.fillMaxSize()) { controller.accept(session, it) }
                }
            } else {
                Column(Modifier.align(Alignment.Center).padding(horizontal = 24.dp, vertical = 36.dp)
                    .then(if (largeText) Modifier.padding(top = 90.dp) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!largeText) {
                        LabIcon(LabIcon.Camera, Modifier.size(42.dp))
                        Text("Your practice mirror", modifier = Modifier.fillMaxWidth(), fontFamily = FontFamily.Serif,
                            fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    Text("Start the camera to see your lips here.", modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Surface(modifier = if (largeText) Modifier.weight(1f) else Modifier, shape = RoundedCornerShape(12.dp), color = Color(0xB3202B28), contentColor = Color.White) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Surface(Modifier.size(8.dp), shape = RoundedCornerShape(50),
                            color = if (state.status.active) Color(0xFF85D782) else Color(0xFFCDD2CC)) {}
                        Text(if (state.status.active) "Camera on" else "Camera off", fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                if (!largeText) Spacer(Modifier.weight(1f))
                Surface(modifier = if (largeText) Modifier.weight(1f) else Modifier,
                    onClick = { if (state.status.active) controller.stop() else controller.start() },
                    shape = RoundedCornerShape(12.dp), color = Color(0xB3202B28), contentColor = Color.White) {
                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(if (state.status.active) "Turn off" else "Start camera", fontSize = 12.sp)
                    }
                }
            }
            if (state.status.active) {
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Lip alignment guide, not a measurement" }) {
                    drawOval(Color.White.copy(alpha = .85f),
                        topLeft = Offset(size.width * .31f, size.height * .30f),
                        size = Size(size.width * .38f, size.height * .20f),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
                }
                Surface(Modifier.align(Alignment.BottomStart).padding(10.dp), shape = RoundedCornerShape(10.dp),
                    color = Color(0x99303030), contentColor = Color.White) {
                    Text("Preview · alignment guide", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp)
                }
            }
        }
        if (state.status !in listOf(LipCameraStatus.OFF, LipCameraStatus.TRACKING)) {
            Text(when (state.status) {
                LipCameraStatus.STARTING -> "Opening camera…"
                LipCameraStatus.NO_FACE -> "Bring your face into view."
                LipCameraStatus.ADJUST_POSITION -> "Face the camera straight on, move a little closer, and keep your mouth visible."
                LipCameraStatus.PERMISSION_DENIED -> "Camera access is off. Allow it in app settings, then try again."
                LipCameraStatus.UNAVAILABLE -> "Live camera feedback is not available on this device. You can still use the practice guidance and audio examples."
                LipCameraStatus.ERROR -> "Camera feedback stopped. Try starting the camera again."
                LipCameraStatus.PAUSED -> "Camera paused while the app was away. Start it again when you’re ready."
                else -> ""
            }, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Match the lip shape", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    fontSize = 24.sp, lineHeight = 27.sp)
                Text(practiceInstruction, fontSize = 17.sp, lineHeight = 22.sp)
            }
            reference?.let {
                Surface(Modifier.width(112.dp), shape = RoundedCornerShape(18.dp),
                    color = scheme.surfaceVariant.copy(alpha = .5f), border = BorderStroke(1.dp, scheme.outlineVariant)) {
                    Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Image(painterResource(it.front), contentDescription = "Target lips. ${it.frontDescription}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)))
                        Text("Reference", fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
        footer()
        LabMeasurements {
            Text("$exampleWord /$phoneme/", style = MaterialTheme.typography.titleMedium)
            Text(placementCue ?: reference?.placementCue ?: "Use the sound map to explore this vowel.",
                style = MaterialTheme.typography.bodyMedium)
            measurements()
            state.measurement?.let { measurement ->
                Text("Lip opening: ${(measurement.openingRatio * 100).toInt()}% of mouth width",
                    style = MaterialTheme.typography.labelLarge)
                Text("Lip contour circularity: ${(measurement.roundingIndex * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge)
                Text("A higher circularity means a rounder outline in this view; it does not measure lip protrusion.",
                    style = MaterialTheme.typography.bodySmall)
                comparisonReference?.let { saved ->
                    val comparison = measurement.compareWith(saved)
                    fun change(value: Float): String {
                        val points = (value * 100).toInt()
                        return if (points > 0) "+$points" else "$points"
                    }
                    Text("Compared with your saved pose: opening ${change(comparison.openingChange)} percentage points; circularity ${change(comparison.roundingChange)} percentage points.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { comparisonReference = measurement }) {
                    Text(if (comparisonReference == null) "Use this pose for comparison" else "Replace comparison pose")
                }
                if (comparisonReference != null) {
                    Text("Your saved pose is a personal practice reference, not a verified pronunciation target.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { comparisonReference = null }) { Text("Clear comparison pose") }
                }
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
            state.performance?.let { performance ->
                PracticeDetails("Tracking details") {
                    Text("Processed frames: ${performance.sampleCount}", style = MaterialTheme.typography.bodySmall)
                    performance.framesPerSecond?.let { fps ->
                        Text("Analysis rate: ${(fps * 10).toInt() / 10.0} frames/s", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Analysis time: ${performance.meanInferenceMs.toInt()} ms average · ${performance.maxInferenceMs.toInt()} ms maximum",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Recent processed frames, including frames with no face. Camera capture and screen refresh may run at different rates. A 30 frames/s target is not a measured guarantee.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            PracticeDetails("Camera privacy and limitations") {
                Text("Jaw distance and tongue position are unavailable from this camera view. Compare the target illustration and placement cue; use the sound map for acoustic feedback.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text("Frames are analyzed on this device and are not saved. Lip measurements and the temporary comparison pose are not pronunciation scores.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
