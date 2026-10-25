package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.ui.components.ArticulatoryBiofeedbackCard
import com.minimalpairs.app.ui.components.VowelTrapezoidPlot
import com.minimalpairs.app.ui.navigation.ArticulatorLabComponent
import com.minimalpairs.app.ui.theme.LabAmber
import com.minimalpairs.app.ui.theme.LabCyan
import com.minimalpairs.app.ui.theme.LabGreen
import com.minimalpairs.app.ui.theme.LabRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticulatorLabScreen(
    component: ArticulatorLabComponent,
    modifier: Modifier = Modifier
) {
    val state by component.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Articulator Lab (Live HAL)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Target: /${if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2}/ | HAL State: ${state.recorderState.name}",
                            style = MaterialTheme.typography.labelSmall,
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
            // Target Phoneme Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = state.isTargetPhoneme1,
                    onClick = { component.switchTargetPhoneme(true) },
                    label = { Text("/${state.contrast.phoneme1}/ (${state.contrast.pairs.first().word1})") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = !state.isTargetPhoneme1,
                    onClick = { component.switchTargetPhoneme(false) },
                    label = { Text("/${state.contrast.phoneme2}/ (${state.contrast.pairs.first().word2})") }
                )
            }

            Spacer(Modifier.height(14.dp))

            // 2D Vowel Trapezoid with live coordinates
            VowelTrapezoidPlot(
                contrast = state.contrast,
                liveF1 = if (state.liveF1 > 0f) state.liveF1 else null,
                liveF2 = if (state.liveF2 > 0f) state.liveF2 else null
            )

            Spacer(Modifier.height(14.dp))

            // Acoustic Readout & GOP Score Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                            text = "LPC Formant Telemetry",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        state.overallGopScore?.let { score ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (score >= 80f) LabGreen.copy(alpha = 0.2f) else LabAmber.copy(alpha = 0.2f),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "GOP Score: ${score.toInt()}/100",
                                    color = if (score >= 80f) LabGreen else LabAmber,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        AcousticMetric(label = "F1 (Jaw/Height)", value = "${state.liveF1.toInt()} Hz")
                        AcousticMetric(label = "F2 (Tongue/Back)", value = "${state.liveF2.toInt()} Hz")
                        AcousticMetric(label = "Duration", value = "${state.liveDurationMs} ms")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Microphone Capture Button (AudioRecorder HAL)
            val isRecording = state.recorderState == AudioRecorderState.RECORDING

            Button(
                onClick = component::toggleRecording,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) LabRed else LabCyan,
                    contentColor = if (isRecording) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    text = if (isRecording) "■ Stop Recording & Score GOP" else "🎤 Start Live Mic Formant Capture",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // Test bench synthesized vowel trigger
            OutlinedButton(
                onClick = { component.testSynthesizedVowel(state.isTargetPhoneme1) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Synthesize & Test Target Vowel /${if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2}/")
            }

            Spacer(Modifier.height(16.dp))

            ArticulatoryBiofeedbackCard(
                indicators = state.contrast.visualIndicators,
                phoneme1 = state.contrast.phoneme1,
                phoneme2 = state.contrast.phoneme2
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AcousticMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
