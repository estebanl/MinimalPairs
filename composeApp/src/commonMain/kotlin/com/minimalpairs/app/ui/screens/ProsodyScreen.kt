package com.minimalpairs.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalpairs.app.ui.components.PitchRibbonCanvas
import com.minimalpairs.app.ui.components.PracticeDetails
import com.minimalpairs.app.ui.components.PracticeTopBar
import com.minimalpairs.app.ui.navigation.ProsodyComponent
import com.minimalpairs.app.ui.theme.PracticeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProsodyScreen(
    component: ProsodyComponent,
    modifier: Modifier = Modifier
) {
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    val state by component.state.collectAsState()
    val pattern = state.activePattern

    Scaffold(
        topBar = {
            PracticeTopBar("Rhythm", "Find the shape of a sentence", onBack = component::onBack)
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
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
                    containerColor = scheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = pattern.patternType.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentInk
                        )
                        OutlinedButton(
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                            onClick = component::playReferenceAudio,
                            enabled = !state.isPlayingReference,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (state.isPlayingReference) "Playing..." else "Listen", fontSize = 12.sp)
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
                        color = scheme.onSurfaceVariant
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
                    containerColor = if (isRec) scheme.error else scheme.primary,
                    contentColor = if (isRec) scheme.onError else scheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(
                    text = if (isRec) "Stop and review" else "Record your voice",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            state.error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium) }

            PracticeDetails("Explore example rhythms") {
                    Text("These examples are simulated; record your voice for personal feedback.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                // Fast Simulation Buttons (Test native vs monotone)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                        onClick = { component.triggerSimulatedProsody(true) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Varied pitch", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                        onClick = { component.triggerSimulatedProsody(false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Flat pitch", fontSize = 12.sp)
                    }
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
                                containerColor = scheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    2.dp,
                                    if (eval.overallScore >= 75f) colors.success else colors.warning,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Your rhythm feedback",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (eval.overallScore >= 75f) colors.success.copy(alpha = 0.2f) else colors.warning.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(
                                            text = "Overall: ${eval.overallScore.toInt()}%",
                                            color = if (eval.overallScore >= 75f) colors.success else colors.warning,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        text = "Make it flow",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    Text("Sounds to soften", style = MaterialTheme.typography.labelMedium, color = colors.warning, fontWeight = FontWeight.Bold)
                    pattern.weakForms.forEach { wf ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("• \"${wf.word}\" -> /${wf.weakIpa}/: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(wf.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Words to connect", style = MaterialTheme.typography.labelMedium, color = colors.accentInk, fontWeight = FontWeight.Bold)
                    pattern.linkingFeatures.forEach { link ->
                        Text("• $link", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}
