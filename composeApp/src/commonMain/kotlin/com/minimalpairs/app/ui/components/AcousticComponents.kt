package com.minimalpairs.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.PhoneticBounds
import com.minimalpairs.app.core.model.PhoneticContrast
import com.minimalpairs.app.core.model.VisualIndicators
import com.minimalpairs.app.ui.theme.PracticeTheme

/**
 * 2D IPA Vowel Trapezoid Formant Plot (F1 vertical vs F2 horizontal, standard phonetic inversion).
 */
@Composable
fun VowelTrapezoidPlot(
    contrast: PhoneticContrast,
    liveF1: Float? = null,
    liveF2: Float? = null,
    modifier: Modifier = Modifier
        .fillMaxWidth(),
    targetPhoneme: String? = null
) {
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Where the sounds sit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(contrast.phoneme1 to colors.accentInk, contrast.phoneme2 to colors.warning)
                        .forEach { (phoneme, color) ->
                            val selected = phoneme == targetPhoneme
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp)))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (selected) "/$phoneme/ · Target" else "/$phoneme/",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(colors.success, RoundedCornerShape(5.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text("Your sound", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text("F1 · Low at top, high at bottom",
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val w = size.width
                val h = size.height

                // Formant Coordinate Mapping:
                // F1: 200 Hz (top) to 950 Hz (bottom)
                // F2: 2800 Hz (left) to 750 Hz (right)
                fun mapX(f2: Float): Float = ((2800f - f2) / (2800f - 750f)) * w
                fun mapY(f1: Float): Float = ((f1 - 200f) / (950f - 200f)) * h

                // Grid lines
                val gridColor = scheme.outlineVariant
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                listOf(300f, 500f, 700f).forEach { f1Grid ->
                    val y = mapY(f1Grid)
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f, pathEffect = dashEffect)
                }
                listOf(1000f, 1500f, 2000f, 2500f).forEach { f2Grid ->
                    val x = mapX(f2Grid)
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f, pathEffect = dashEffect)
                }

                // Region 1 (Phoneme 1)
                drawAcousticRegion(contrast.boundsPhoneme1, ::mapX, ::mapY, colors.accentInk,
                    selected = targetPhoneme == contrast.phoneme1)

                // Region 2 (Phoneme 2)
                drawAcousticRegion(contrast.boundsPhoneme2, ::mapX, ::mapY, colors.warning,
                    selected = targetPhoneme == contrast.phoneme2)

                // Live User Coordinate (Green / White glowing dot)
                if (liveF1 != null && liveF2 != null && liveF1 in 150f..1100f && liveF2 in 700f..3000f) {
                    val userX = mapX(liveF2).coerceIn(4f, w - 4f)
                    val userY = mapY(liveF1).coerceIn(4f, h - 4f)

                    drawCircle(colors.success.copy(alpha = 0.4f), radius = 14f, center = Offset(userX, userY))
                    drawCircle(colors.success, radius = 7f, center = Offset(userX, userY))
                    drawCircle(Color.White, radius = 3f, center = Offset(userX, userY))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Front (F2 High)", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text("Back (F2 Low)", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAcousticRegion(
    bounds: PhoneticBounds,
    mapX: (Float) -> Float,
    mapY: (Float) -> Float,
    color: Color,
    selected: Boolean
) {
    val x1 = mapX(bounds.f2MaxHz) // Note: F2 inverted
    val x2 = mapX(bounds.f2MinHz)
    val y1 = mapY(bounds.f1MinHz)
    val y2 = mapY(bounds.f1MaxHz)

    val left = minOf(x1, x2)
    val top = minOf(y1, y2)
    val width = kotlin.math.abs(x2 - x1)
    val height = kotlin.math.abs(y2 - y1)

    // Filled translucent region
    drawRoundRect(
        color = color.copy(alpha = if (selected) 0.4f else 0.15f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(12f, 12f)
    )
    // Border
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = if (selected) 3.dp.toPx() else 1.dp.toPx())
    )
}

/** Static phoneme-specific guidance; no calibrated camera measurements are implied. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ArticulatoryBiofeedbackCard(
    indicators: VisualIndicators,
    phoneme1: String,
    phoneme2: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = scheme.surface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mouth position reference", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            listOf(phoneme1, phoneme2).distinct().forEach { phoneme ->
                val reference = com.minimalpairs.app.ui.reference.MouthPositionReferences.forPhoneme(phoneme)
                Text("/$phoneme/", style = MaterialTheme.typography.labelLarge)
                Text(reference?.placementCue ?: "Explore this vowel with the sound map.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("These are teaching cues. The camera measures visible lip shape; it does not measure jaw distance or tongue position.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}
