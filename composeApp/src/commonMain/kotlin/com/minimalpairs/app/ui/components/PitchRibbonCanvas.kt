package com.minimalpairs.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.dsp.PitchFrame
import com.minimalpairs.app.core.model.ProsodyPattern
import com.minimalpairs.app.ui.theme.PracticeTheme

/**
 * Interactive Pitch Ribbon Canvas: Plots continuous target native pitch contour
 * (reference ribbon) against user's measured pitch points (amber/green).
 */
@Composable
fun PitchRibbonCanvas(
    pattern: ProsodyPattern,
    userContour: List<PitchFrame> = emptyList(),
    modifier: Modifier = Modifier
        .fillMaxWidth()
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
                    text = "Follow the pitch",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reference", color = colors.accentInk, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    if (userContour.any { it.isVoiced }) {
                        Spacer(Modifier.width(10.dp))
                        Text("Your voice", color = colors.success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val w = size.width
                val h = size.height

                // Pitch range: 100 Hz (bottom) to 320 Hz (top)
                val minF0 = 100f
                val maxF0 = 320f
                fun mapX(normT: Float): Float = normT.coerceIn(0f, 1f) * (w - 24f) + 12f
                fun mapY(f0: Float): Float = h - ((f0.coerceIn(minF0, maxF0) - minF0) / (maxF0 - minF0)) * (h - 20f) - 10f

                // Grid lines at 150Hz, 200Hz, 250Hz
                val gridColor = scheme.outlineVariant
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                listOf(150f, 200f, 250f).forEach { gridHz ->
                    val y = mapY(gridHz)
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f, pathEffect = dash)
                }

                // 1. Draw Native Reference Ribbon (reference)
                if (pattern.referenceContour.isNotEmpty()) {
                    val targetPath = Path()
                    val first = pattern.referenceContour.first()
                    targetPath.moveTo(mapX(first.first), mapY(first.second))

                    for (i in 1 until pattern.referenceContour.size) {
                        val pt = pattern.referenceContour[i]
                        val prev = pattern.referenceContour[i - 1]
                        val midX = (mapX(prev.first) + mapX(pt.first)) / 2f
                        targetPath.cubicTo(
                            midX, mapY(prev.second),
                            midX, mapY(pt.second),
                            mapX(pt.first), mapY(pt.second)
                        )
                    }

                    // Wide translucent glow
                    drawPath(
                        path = targetPath,
                        color = colors.accentInk.copy(alpha = 0.3f),
                        style = Stroke(width = 12f)
                    )
                    // Sharp center ribbon
                    drawPath(
                        path = targetPath,
                        color = colors.accentInk,
                        style = Stroke(width = 4f)
                    )

                    // Target key points
                    for (pt in pattern.referenceContour) {
                        drawCircle(colors.accentInk, radius = 5f, center = Offset(mapX(pt.first), mapY(pt.second)))
                    }
                }

                // 2. Draw User Measured Pitch Contour (measured)
                val voicedFrames = userContour.filter { it.isVoiced && it.f0Hz in minF0..maxF0 }
                if (voicedFrames.size >= 2) {
                    val userStartTime = userContour.first().timeSeconds
                    val userDuration = (userContour.last().timeSeconds - userStartTime).coerceAtLeast(0.1f)

                    val userPath = Path()
                    var pathStarted = false

                    for (frame in voicedFrames) {
                        val normT = (frame.timeSeconds - userStartTime) / userDuration
                        val x = mapX(normT)
                        val y = mapY(frame.f0Hz)

                        if (!pathStarted) {
                            userPath.moveTo(x, y)
                            pathStarted = true
                        } else {
                            userPath.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = userPath,
                        color = colors.success,
                        style = Stroke(width = 4f)
                    )
                    for (frame in voicedFrames) {
                        val normT = (frame.timeSeconds - userStartTime) / userDuration
                        drawCircle(colors.success, radius = 3f, center = Offset(mapX(normT), mapY(frame.f0Hz)))
                    }
                }
            }

            // Syllables along timeline
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pattern.syllables.forEach { syl ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = syl.text,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (syl.isStressed) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (syl.isStressed) colors.accentInk else if (syl.isWeakForm) scheme.onSurfaceVariant else scheme.onSurface
                        )
                        Text(
                            text = "/${syl.ipa}/",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (syl.isWeakForm) colors.warning else scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
