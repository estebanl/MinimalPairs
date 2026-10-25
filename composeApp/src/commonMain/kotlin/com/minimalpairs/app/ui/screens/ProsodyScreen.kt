package com.minimalpairs.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalpairs.app.ui.components.PitchRibbonCanvas
import com.minimalpairs.app.ui.navigation.ProsodyComponent
import com.minimalpairs.app.ui.theme.LabAmber
import com.minimalpairs.app.ui.theme.LabCyan
import com.minimalpairs.app.ui.theme.LabGreen
import com.minimalpairs.app.ui.theme.LabRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProsodyScreen(
    component: ProsodyComponent,
    modifier: Modifier = Modifier
) {
    val state by component.state.collectAsState()
    val pattern = state.activePattern

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rhythm & Pitch Ribbon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Prosody & Connected Speech Lab",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = component::onBack) {
                        Text("←", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pattern Selector Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                items(component.availablePatterns) { itemPattern ->
                    FilterChip(
                        selected = itemPattern.id == pattern.id,
                        onClick = { component.selectPattern(itemPattern.id) },
                        label = { Text(itemPattern.sentenceText) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Intonation Target Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pattern.patternType.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = component::playReferenceAudio,
                            enabled = !state.isPlayingReference,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (state.isPlayingReference) "Playing..." else "🔊 Hear Melody", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "\"${pattern.sentenceText}\"",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pattern.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Continuous Pitch Ribbon Canvas
            PitchRibbonCanvas(
                pattern = pattern,
                userContour = state.evaluation?.userContour ?: emptyList()
            )

            Spacer(Modifier.height(16.dp))

            // Recording & Evaluation Control Bar
            val isRec = state.isRecording
            Button(
                onClick = component::toggleRecording,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRec) LabRed else LabCyan,
                    contentColor = if (isRec) Color.White else Color.Black
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    text = if (isRec) "■ Stop & Evaluate Prosody" else "🎤 Record Your Intonation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Fast Simulation Buttons (Test native vs monotone)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { component.triggerSimulatedProsody(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Test Native Melody (↗↘)", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { component.triggerSimulatedProsody(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Test Flat Monotone (→)", fontSize = 12.sp)
                }
            }

            // Prosody Evaluation Telemetry Card
            AnimatedVisibility(
                visible = state.evaluation != null,
                enter = fadeIn()
            ) {
                state.evaluation?.let { eval ->
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    2.dp,
                                    if (eval.overallScore >= 75f) LabGreen else LabAmber,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Prosody Telemetry",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (eval.overallScore >= 75f) LabGreen.copy(alpha = 0.2f) else LabAmber.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(
                                            text = "Overall: ${eval.overallScore.toInt()}%",
                                            color = if (eval.overallScore >= 75f) LabGreen else LabAmber,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    AcousticMetric(label = "Intonation Match", value = "${eval.intonationScore.toInt()}%")
                                    AcousticMetric(label = "Rhythm Contrast", value = "${eval.rhythmScore.toInt()}%")
                                    AcousticMetric(label = "Pitch Range", value = "${eval.pitchRangeHz.toInt()} Hz")
                                }

                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = eval.feedback,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Connected Speech & Weak Forms Reference Card
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Connected Speech Features",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    Text("Weak Forms in this sentence:", style = MaterialTheme.typography.labelMedium, color = LabAmber, fontWeight = FontWeight.Bold)
                    pattern.weakForms.forEach { wf ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("• \"${wf.word}\" -> /${wf.weakIpa}/: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(wf.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Linking & Liaison:", style = MaterialTheme.typography.labelMedium, color = LabCyan, fontWeight = FontWeight.Bold)
                    pattern.linkingFeatures.forEach { link ->
                        Text("• $link", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}
