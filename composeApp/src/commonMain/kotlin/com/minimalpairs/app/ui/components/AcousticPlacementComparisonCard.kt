package com.minimalpairs.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.PhoneticBounds
import com.minimalpairs.app.ui.reference.AcousticPlacementComparison
import com.minimalpairs.app.ui.reference.OralPoint
import com.minimalpairs.app.ui.reference.OralView

@Composable
fun AcousticPlacementComparisonCard(
    bounds: PhoneticBounds,
    observedF1: Float?,
    observedF2: Float?,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val observation = AcousticPlacementComparison.observation(observedF1, observedF2)
    val validBounds = AcousticPlacementComparison.validBounds(bounds)
    val description = when {
        !validBounds -> "No acoustic target is available for this sound."
        observation == null -> if (isRecording) "No clear vowel measurement. Say the vowel to compare." else "Record a vowel to compare with the target."
        else -> "${if (isRecording) "Live" else "Recorded"} F1 ${observation.f1.toInt()} Hz, F2 ${observation.f2.toInt()} Hz. " +
            AcousticPlacementComparison.guidance(observation, bounds)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Target and your sound", style = MaterialTheme.typography.titleSmall)
        Text("Acoustic placement estimate", style = MaterialTheme.typography.labelMedium)
        if (validBounds) {
            Canvas(Modifier.fillMaxWidth().height(170.dp).semantics {
                contentDescription = "Schematic formant plane. Outlined area: target range. Filled dot: your sound when available. $description"
            }) {
                val view = OralView(yaw = 65f, pitch = 10f)
                fun screen(point: OralPoint): Offset {
                    val p = view.project(view.rotate(point))
                    return Offset(size.width / 2 + p.x * size.width * .38f, size.height / 2 - p.y * size.height * .38f)
                }
                fun point(f1: Float, f2: Float) = screen(AcousticPlacementComparison.point(f1, f2))
                val corners = listOf(point(bounds.f1MinHz, bounds.f2MinHz), point(bounds.f1MinHz, bounds.f2MaxHz),
                    point(bounds.f1MaxHz, bounds.f2MaxHz), point(bounds.f1MaxHz, bounds.f2MinHz))
                val area = Path().apply {
                    moveTo(corners.first().x, corners.first().y)
                    corners.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawLine(scheme.outline, screen(OralPoint(0f, -1f, -1f)), screen(OralPoint(0f, 1f, -1f)), 1.dp.toPx())
                drawLine(scheme.outline, screen(OralPoint(0f, -1f, -1f)), screen(OralPoint(0f, -1f, 1f)), 1.dp.toPx())
                drawPath(area, scheme.primary.copy(alpha = .15f))
                drawPath(area, scheme.primary, style = Stroke(2.dp.toPx()))
                observation?.let {
                    val center = point((bounds.f1MinHz + bounds.f1MaxHz) / 2f, (bounds.f2MinHz + bounds.f2MaxHz) / 2f)
                    val observed = screen(it.point)
                    drawLine(scheme.onSurfaceVariant, center, observed, 1.dp.toPx())
                    drawCircle(scheme.tertiary, 6.dp.toPx(), observed)
                    drawCircle(scheme.onSurface, 6.dp.toPx(), observed, style = Stroke(1.dp.toPx()))
                }
            }
            Text("Up: lower F1 / higher tongue tendency. Right: higher F2 / more forward tendency.",
                style = MaterialTheme.typography.bodySmall)
            Text("Outline: target · Dot: your sound", style = MaterialTheme.typography.labelSmall)
        }
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Text("Sound suggests placement; it cannot reveal your tongue shape. The 3D mouth remains a teaching reference.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}
