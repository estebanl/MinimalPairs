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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.PhoneticBounds
import com.minimalpairs.app.ui.reference.ConsonantModelReferences
import com.minimalpairs.app.ui.reference.ConsonantPracticeReferences
import com.minimalpairs.app.ui.reference.MouthPositionReferences
import com.minimalpairs.app.ui.reference.OralCavityModel
import com.minimalpairs.app.ui.reference.OralPart
import com.minimalpairs.app.ui.reference.OralSection
import com.minimalpairs.app.ui.reference.OralPoint
import com.minimalpairs.app.ui.reference.VocalTractLabMeshes
import com.minimalpairs.app.ui.reference.OralTriangle
import com.minimalpairs.app.ui.reference.OralView
import com.minimalpairs.app.ui.theme.PracticeTheme
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.min

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
    var sectionFill by rememberSaveable { mutableStateOf(true) }
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
                phoneme == "r" -> "Authored teaching examples"
                VocalTractLabMeshes.isDerived(phoneme) -> "Derived teaching pose"
                else -> "VocalTractLab reference"
            },
                style = MaterialTheme.typography.labelMedium, color = accentInk)
            if (VocalTractLabMeshes.isDerived(phoneme)) {
                Text("${VocalTractLabMeshes.poseDescription(phoneme)} Approximate and unvalidated; not an exact English speaker pose.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (sourceShape == null) {
                Text("This sound does not yet have a matching 3D reference.", style = MaterialTheme.typography.bodyMedium)
                val cue = ConsonantPracticeReferences.forPhoneme(phoneme)
                if (cue != null) Text(cue, style = MaterialTheme.typography.bodyMedium)
                else MouthPositionReferenceCard(phoneme, exampleWord)
                return@Column
            }
            if (consonant != null) {
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
            val baseMesh = loadedMesh?.takeIf { it.first == loadKey }?.second
            if (baseMesh == null) {
                Text(if (failedPhoneme == loadKey) "The 3D reference could not be loaded." else "Loading 3D reference…")
                if (failedPhoneme == loadKey) TextButton(onClick = { retry++ }) { Text("Retry") }
                return@Column
            }
            reference?.let { Text(it.placementCue, style = MaterialTheme.typography.bodyMedium) }
            val mesh = remember(baseMesh, cutaway, sectionFill, yaw >= 0f) {
                OralCavityModel.cutaway(baseMesh, cutaway, yaw >= 0f) +
                    if (cutaway && sectionFill) OralSection.tongueFill(baseMesh, yaw >= 0f) else emptyList()
            }
            val view = OralView(yaw, pitch, zoom)
            val faces = remember(mesh, view) {
                mesh.map { triangle ->
                    triangle.copy(a = view.rotate(triangle.a), b = view.rotate(triangle.b), c = view.rotate(triangle.c),
                        na = view.rotate(triangle.na), nb = view.rotate(triangle.nb), nc = view.rotate(triangle.nc))
                }.sortedBy { (it.a.z + it.b.z + it.c.z) / 3f }
            }
            val colors = mapOf(
                OralPart.TONGUE to Color(0xFFC97982), OralPart.PALATE to Color(0xFFD4ACA0),
                OralPart.TEETH to Color(0xFFF2E7D4), OralPart.LIPS to Color(0xFFA85C6A),
                OralPart.TISSUE to Color(0xFFCBA69B)
            )
            val sceneBackground = scheme.surfaceVariant
            val shadow = scheme.onSurface.copy(alpha = .08f)
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(12.dp))
                .semantics {
                    contentDescription = "3D mouth reference for /$phoneme/. " +
                        (phase?.let { "${it.label}. ${it.description}" } ?: "${reference?.sideDescription} ${reference?.frontDescription}")
                    stateDescription = "Rotation ${yaw.toInt()} degrees, tilt ${pitch.toInt()} degrees, zoom ${(zoom * 100).toInt()} percent. " +
                        if (cutaway) "Cutaway on. ${if (sectionFill) "Illustrative tongue section filled." else "Surface only."}" else "Cutaway off."
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
                val scale = min(size.width, size.height) / 3.4f
                fun screen(point: OralPoint): Offset {
                    val p = view.project(point)
                    return Offset(size.width / 2f + p.x * scale, size.height * .48f - p.y * scale)
                }
                val positions = ArrayList<Offset>(faces.size * 3)
                val vertexColors = ArrayList<Color>(faces.size * 3)
                for (face in faces) {
                    val base = colors.getValue(face.part)
                    for ((point, normal) in listOf(face.a to face.na, face.b to face.nb, face.c to face.nc)) {
                        // Broad diffuse light: avoid bright rims on both sides of
                        // a surface. Section faces stay matte and readable.
                        val light = if (face.isSection) .95f else
                            (.76f + .24f * (normal.x * -.3f + normal.y * .6f + normal.z * .74f)).coerceIn(.58f, 1f)
                        positions += screen(point)
                        vertexColors += Color(base.red * light, base.green * light, base.blue * light)
                    }
                }
                drawContext.canvas.drawVertices(
                    Vertices(VertexMode.Triangles, positions, List(positions.size) { Offset.Zero }, vertexColors, positions.indices.toList()),
                    BlendMode.Modulate, Paint().apply { color = Color.White }
                )
                // Emphasize the intersection with the midsagittal cut plane.
                if (cutaway) for (face in mesh) {
                    val edge = listOf(face.a, face.b, face.c).filter { abs(it.x) < .00001f }
                    if (edge.size == 2) drawLine(colors.getValue(face.part).copy(alpha = .9f),
                        screen(view.rotate(edge[0])), screen(view.rotate(edge[1])), strokeWidth = 1.2f)
                }
            }
            Text("Drag to rotate and tilt.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(yaw == 0f && pitch == 0f && !cutaway, onClick = ::front, label = { Text("Front") })
                FilterChip(yaw == 90f && pitch == 0f && cutaway, onClick = ::side, label = { Text("Side") })
                FilterChip(cutaway, onClick = { cutaway = !cutaway }, label = { Text("Cutaway") })
                if (cutaway) FilterChip(sectionFill, onClick = { sectionFill = !sectionFill }, label = { Text("Tongue section") })
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
            if (cutaway && sectionFill) Text("The filled tongue section joins source surface contours for clarity. It does not show measured internal anatomy.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            VocalTractLabCredits()
        }
    }
}
