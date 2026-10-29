package com.minimalpairs.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.PhoneticBounds
import com.minimalpairs.app.ui.reference.articulationFrameKey
import com.minimalpairs.app.ui.reference.ArticulationClipMode
import com.minimalpairs.app.ui.reference.ArticulationReviewStatus
import com.minimalpairs.app.ui.reference.ReviewedArticulations
import minimalpairs.composeapp.generated.resources.Res
import androidx.compose.ui.platform.LocalUriHandler
import com.minimalpairs.app.ui.reference.ConsonantModelReferences
import com.minimalpairs.app.ui.reference.ConsonantPracticeReferences
import com.minimalpairs.app.ui.reference.MouthPositionReferences
import com.minimalpairs.app.ui.reference.OralCavityModel
import com.minimalpairs.app.ui.reference.toImageBitmap
import com.minimalpairs.app.ui.reference.OralRenderedFrame
import com.minimalpairs.app.ui.reference.OralRasterizer
import com.minimalpairs.app.ui.reference.VocalTractLabMeshes
import com.minimalpairs.app.ui.reference.OralTriangle
import com.minimalpairs.app.ui.reference.OralView
import com.minimalpairs.app.ui.theme.PracticeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

/** Interactive teaching model. Microphone data must not deform the reference anatomy. */
@Composable
fun OralCavityReferenceCard(
    phoneme: String,
    exampleWord: String,
    modifier: Modifier = Modifier,
    targetBounds: PhoneticBounds? = null,
    observedF1: Float? = null,
    observedF2: Float? = null,
    isRecording: Boolean = false
) {
    var articulationRetry by remember(phoneme) { mutableStateOf(0) }
    val articulationSpec = ReviewedArticulations.forPhoneme(phoneme)
    val articulation = rememberArticulationPlayback(phoneme, articulationSpec, articulationRetry) { Res.readBytes(it) }
    val playback = articulation.value
    val activeClip = playback.clip
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val accentInk = PracticeTheme.colors.accentInk
    val consonant = ConsonantModelReferences.forPhoneme(phoneme)
    var phaseIndex by remember(phoneme) { mutableStateOf(0) }
    val phase = consonant?.phases?.getOrNull(phaseIndex)
    val sourceShape = phase?.assetKey ?: VocalTractLabMeshes.sourceShapes[phoneme]
    val loadKey = "$phoneme:${phase?.assetKey.orEmpty()}"
    var failedPhoneme by remember { mutableStateOf<String?>(null) }
    var retry by remember(loadKey) { mutableStateOf(0) }
    val loadedMesh by produceState<Pair<String, List<OralTriangle>>?>(null, loadKey, retry) {
        value = null
        failedPhoneme = null
        try {
            val mesh = if (consonant != null) VocalTractLabMeshes.loadConsonant(phoneme, phaseIndex)
                else VocalTractLabMeshes.load(phoneme)
            value = mesh?.let { loadKey to it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failedPhoneme = loadKey
        }
    }
    val reference = MouthPositionReferences.forPhoneme(phoneme)
    var yaw by rememberSaveable { mutableStateOf(90f) }
    var pitch by rememberSaveable { mutableStateOf(0f) }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var cutaway by rememberSaveable { mutableStateOf(true) }
    fun reset() { yaw = 90f; pitch = 0f; zoom = 1f; cutaway = true }
    fun front() { yaw = 0f; pitch = 0f; cutaway = false }
    fun side() { yaw = 90f; pitch = 0f; cutaway = true }

    Surface(modifier.widthIn(max = 520.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = scheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Explore mouth placement", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() })
            Text("$exampleWord /$phoneme/", style = MaterialTheme.typography.titleLarge)
            targetBounds?.let { bounds ->
                AcousticPlacementComparisonCard(bounds, observedF1, observedF2, isRecording)
            }
            Text(when {
                activeClip != null -> "Illustrative anatomy · unvalidated"
                phoneme == "r" -> "Authored teaching examples"
                VocalTractLabMeshes.isDerived(phoneme) -> "Derived teaching pose"
                else -> "VocalTractLab reference"
            },
                style = MaterialTheme.typography.labelMedium, color = accentInk)
            if (activeClip == null && VocalTractLabMeshes.isDerived(phoneme)) {
                Text("${VocalTractLabMeshes.poseDescription(phoneme)} Approximate and unvalidated; not an exact English speaker pose.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (playback.failed) {
                Text(if (sourceShape != null) "Movement could not be loaded. Showing the static reference." else "The 3D movement could not be loaded.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { articulationRetry++ }) { Text("Retry movement") }
            }
            if (sourceShape == null && activeClip == null) {
                if (!playback.failed) Text(
                    if (articulationSpec?.reviewStatus == ArticulationReviewStatus.ACCEPTED && articulationSpec.phoneme == phoneme) "Loading 3D reference…"
                    else "This sound does not yet have a matching 3D reference.", style = MaterialTheme.typography.bodyMedium)
                val cue = ConsonantPracticeReferences.forPhoneme(phoneme)
                if (cue != null) Text(cue, style = MaterialTheme.typography.bodyMedium)
                else MouthPositionReferenceCard(phoneme, exampleWord)
                return@Column
            }
            if (consonant != null && activeClip == null) {
                Text("Consonant teaching snapshot · unreviewed", style = MaterialTheme.typography.labelMedium)
                Text(consonant.explanation, style = MaterialTheme.typography.bodySmall)
                Text(ConsonantPracticeReferences.forPhoneme(phoneme).orEmpty(), style = MaterialTheme.typography.bodyMedium)
                if (consonant.phases.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    consonant.phases.forEachIndexed { index, item ->
                        FilterChip(phaseIndex == index, onClick = { phaseIndex = index }, label = { Text(item.label) })
                    }
                }
                Text(phase!!.description, style = MaterialTheme.typography.bodySmall)
            }
            val animatedMesh = remember(activeClip) { activeClip?.sample(0) }
            val baseMesh = animatedMesh ?: loadedMesh?.takeIf { it.first == loadKey }?.second
            val frameKey = if (activeClip == null) loadKey else articulationFrameKey(phoneme, activeClip.spec.modelFamily, playback.generation, cutaway, yaw >= 0f)
            ArticulationPlaybackControls(playback) { articulation.value = it }
            if (baseMesh == null) {
                Text(if (failedPhoneme == loadKey) "The 3D reference could not be loaded." else "Loading 3D reference…")
                if (failedPhoneme == loadKey) TextButton(onClick = { retry++ }) { Text("Retry") }
                return@Column
            }
            reference?.let { Text(it.placementCue, style = MaterialTheme.typography.bodyMedium) }
            val authoredSections = activeClip?.spec?.clipMode == ArticulationClipMode.AUTHORED_SECTIONS
            val mesh = remember(baseMesh, cutaway, yaw >= 0f, authoredSections) {
                OralCavityModel.cutaway(baseMesh, cutaway, yaw >= 0f, authoredSections = authoredSections)
            }
            val view = OralView(yaw, pitch, zoom)
            // Retain the last completed camera view during a drag, but never a different
            // sound/phase or cutaway mesh. Cancellation must not clear the last good frame.
            val visibleFrame = if (activeClip == null) {
                val rendered by produceState<OralRenderedFrame<ImageBitmap>?>(null, frameKey, mesh, view) {
                    value = withContext(Dispatchers.Default) {
                        OralRenderedFrame(frameKey, mesh, view,
                            OralRasterizer.render(mesh, view, checkActive = { ensureActive() }).toImageBitmap { ensureActive() })
                    }
                }
                rendered?.forMesh(frameKey, mesh)
            } else {
                // Render sequentially with backpressure. Do not cancel every in-flight raster on
                // each animation tick, which can starve rendering on slower devices.
                val rendered by produceState<OralRenderedFrame<ImageBitmap>?>(null, frameKey, activeClip, view) {
                    snapshotFlow { articulation.value.elapsedMs }.collect { elapsed ->
                        value = withContext(Dispatchers.Default) {
                            val posed = OralCavityModel.cutaway(activeClip.sample(elapsed), cutaway, yaw >= 0f, authoredSections = authoredSections)
                            OralRenderedFrame(frameKey, posed, view,
                                OralRasterizer.render(posed, view, checkActive = { ensureActive() }).toImageBitmap { ensureActive() },
                                sampleElapsedMs = elapsed)
                        }
                    }
                }
                // Retain the latest completed sample only within this playback generation.
                rendered?.takeIf { it.selectionKey == frameKey }
            }
            val frame = visibleFrame?.image
            val shownView = visibleFrame?.view ?: view
            val sceneBackground = scheme.surfaceVariant
            val shadow = scheme.onSurface.copy(alpha = .08f)
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(12.dp))
                .semantics {
                    contentDescription = "3D mouth reference for /$phoneme/. " +
                        (if (activeClip != null) "Illustrative movement, not synchronized with audio. ${if (playback.playing) "Playing." else "Paused."}"
                         else phase?.let { "${it.label}. ${it.description}" } ?: "${reference?.sideDescription} ${reference?.frontDescription}")
                    stateDescription = "Rotation ${shownView.yaw.toInt()} degrees, tilt ${shownView.pitch.toInt()} degrees, zoom ${(shownView.zoom * 100).toInt()} percent. " +
                        (if (!cutaway) "Cutaway off." else if (authoredSections) "Cutaway on. Authored tissue sections shown." else "Cutaway on. Complete tongue surface retained.") +
                        when {
                            visibleFrame == null -> " Rendering."
                            visibleFrame.view != view -> " Updating view; showing the last completed rotation."
                            activeClip != null && visibleFrame.sampleElapsedMs != playback.elapsedMs -> " Updating movement; showing the last completed sample."
                            else -> " Rendered."
                        }
                    customActions = listOf(
                        CustomAccessibilityAction("Show front") { front(); true },
                        CustomAccessibilityAction("Show side cutaway") { side(); true },
                        CustomAccessibilityAction("Rotate left") { yaw = (yaw - 15f).coerceAtLeast(-100f); true },
                        CustomAccessibilityAction("Rotate right") { yaw = (yaw + 15f).coerceAtMost(100f); true },
                        CustomAccessibilityAction("Reset view") { reset(); true }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        yaw = (yaw + amount.x * .45f).coerceIn(-100f, 100f)
                        pitch = (pitch - amount.y * .35f).coerceIn(-30f, 40f)
                    }
                }) {
                drawRect(sceneBackground)
                drawOval(shadow, topLeft = Offset(size.width * .18f, size.height * .85f),
                    size = Size(size.width * .64f, size.height * .055f))
                frame?.let {
                    drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
            }

            Text("Drag to rotate and tilt.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(yaw == 0f && pitch == 0f && !cutaway, onClick = ::front, label = { Text("Front") })
                FilterChip(yaw == 90f && pitch == 0f && cutaway, onClick = ::side, label = { Text("Side") })
                FilterChip(cutaway, onClick = { cutaway = !cutaway }, label = { Text("Cutaway") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = accentInk), onClick = { zoom = (zoom - .15f).coerceAtLeast(.7f) }, enabled = zoom > .7f) { Text("Zoom out") }
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = accentInk), onClick = { zoom = (zoom + .15f).coerceAtMost(1.3f) }, enabled = zoom < 1.3f) { Text("Zoom in") }
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = accentInk), onClick = ::reset) { Text("Reset view") }
            }
            Text("Rose: tongue · Cream: teeth · Tan: tract walls · Burgundy: lips",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text("Representative speaker anatomy. English articulations vary; this is not a measurement of your mouth.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            VocalTractLabCredits()
            if (activeClip != null) {
                Text("BodyParts3D, © The Database Center for Life Science, CC BY 4.0. Artist-adapted geometry, surfaces and movement; not clinically validated.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { uriHandler.openUri("https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html") }) {
                    Text("BodyParts3D source and CC BY 4.0 license")
                }
            }
        }
    }
}
