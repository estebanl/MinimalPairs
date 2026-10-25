package com.minimalpairs.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.ui.theme.LabAmber
import com.minimalpairs.app.ui.theme.LabCyan
import com.minimalpairs.app.ui.theme.LabGreen

/**
 * Renders an anatomical Sagittal Oral Cavity Cross-Section comparing
 * target vocal tract configuration (tongue height, backness, and lip opening)
 * with user's actual measured configuration.
 */
@Composable
fun SagittalVocalTractView(
    targetF1: Float,
    targetF2: Float,
    actualF1: Float? = null,
    actualF2: Float? = null,
    lipApertureRatio: Float = 0.15f,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(260.dp)
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Oral Cavity & Tongue Posture (Sagittal)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target", color = LabCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    if (actualF1 != null && actualF2 != null) {
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text("You", color = LabGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val w = size.width
                val h = size.height

                // Hard Palate & Upper Skull Roof (Static Anatomy)
                val palatePath = Path().apply {
                    moveTo(w * 0.15f, h * 0.28f) // Upper lip
                    cubicTo(
                        w * 0.25f, h * 0.15f, // Alveolar ridge
                        w * 0.55f, h * 0.12f, // Hard palate
                        w * 0.80f, h * 0.25f  // Velum (soft palate)
                    )
                    lineTo(w * 0.82f, h * 0.85f) // Pharyngeal posterior wall
                }
                drawPath(
                    path = palatePath,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    style = Stroke(width = 4f)
                )

                // Jaw & Teeth landmarks
                drawCircle(Color.LightGray.copy(alpha = 0.5f), radius = 5f, center = Offset(w * 0.25f, h * 0.32f)) // Upper incisor

                // Tongue Height is inversely proportional to F1 (Low F1 = High Tongue)
                // Tongue Backness is proportional to F2 (High F2 = Front Tongue)
                fun calculateTonguePeak(f1: Float, f2: Float): Offset {
                    // Normalize F1 (250Hz - 850Hz) -> y from h * 0.25 to h * 0.60
                    val normF1 = ((f1 - 250f) / 600f).coerceIn(0f, 1f)
                    val tongueY = h * 0.24f + normF1 * (h * 0.35f)

                    // Normalize F2 (800Hz - 2600Hz) -> x from h * 0.70 (back) to w * 0.35 (front)
                    val normF2 = ((2600f - f2) / 1800f).coerceIn(0f, 1f)
                    val tongueX = w * 0.35f + normF2 * (w * 0.35f)

                    return Offset(tongueX, tongueY)
                }

                // 1. Draw Target Tongue Silhouette (Cyan)
                val targetPeak = calculateTonguePeak(targetF1, targetF2)
                val targetTonguePath = Path().apply {
                    moveTo(w * 0.22f, h * 0.72f) // Lower teeth / mandible base
                    cubicTo(
                        w * 0.25f, h * 0.60f,
                        targetPeak.x - w * 0.12f, targetPeak.y + h * 0.05f,
                        targetPeak.x, targetPeak.y // Tongue body apex
                    )
                    cubicTo(
                        targetPeak.x + w * 0.12f, targetPeak.y + h * 0.08f,
                        w * 0.70f, h * 0.65f,
                        w * 0.75f, h * 0.85f // Tongue root / epiglottis
                    )
                }

                drawPath(
                    path = targetTonguePath,
                    color = LabCyan.copy(alpha = 0.35f),
                    style = Stroke(width = 8f)
                )
                drawPath(
                    path = targetTonguePath,
                    color = LabCyan,
                    style = Stroke(width = 3f)
                )
                drawCircle(LabCyan, radius = 6f, center = targetPeak)

                // 2. Draw Actual User Tongue Silhouette (Green / Amber)
                if (actualF1 != null && actualF2 != null && actualF1 > 150f && actualF2 > 700f) {
                    val userPeak = calculateTonguePeak(actualF1, actualF2)
                    val userTonguePath = Path().apply {
                        moveTo(w * 0.22f, h * 0.72f)
                        cubicTo(
                            w * 0.25f, h * 0.60f,
                            userPeak.x - w * 0.12f, userPeak.y + h * 0.05f,
                            userPeak.x, userPeak.y
                        )
                        cubicTo(
                            userPeak.x + w * 0.12f, userPeak.y + h * 0.08f,
                            w * 0.70f, h * 0.65f,
                            w * 0.75f, h * 0.85f
                        )
                    }

                    drawPath(
                        path = userTonguePath,
                        color = LabGreen.copy(alpha = 0.5f),
                        style = Stroke(width = 8f)
                    )
                    drawPath(
                        path = userTonguePath,
                        color = LabGreen,
                        style = Stroke(width = 3f)
                    )
                    drawCircle(LabGreen, radius = 6f, center = userPeak)

                    // Dashed alignment error vector from user peak to target peak
                    drawLine(
                        color = Color.White.copy(alpha = 0.7f),
                        start = userPeak,
                        end = targetPeak,
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                }

                // Lip Aperture Visualizer at mouth opening (front left)
                val lipOpeningH = (h * 0.12f * lipApertureRatio.coerceIn(0.1f, 0.6f)).coerceAtLeast(8f)
                drawLine(
                    color = LabAmber,
                    start = Offset(w * 0.14f, h * 0.32f),
                    end = Offset(w * 0.14f, h * 0.32f + lipOpeningH),
                    strokeWidth = 5f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Lips / Teeth (Front)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("Pharynx (Back)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}
