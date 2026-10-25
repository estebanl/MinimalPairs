package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.ui.components.ArticulatoryBiofeedbackCard
import com.minimalpairs.app.ui.components.PracticeDetails
import com.minimalpairs.app.ui.components.PracticeTopBar
import com.minimalpairs.app.ui.components.MouthPositionReferenceCard
import com.minimalpairs.app.ui.components.VowelTrapezoidPlot
import com.minimalpairs.app.ui.navigation.ArticulatorLabComponent
import com.minimalpairs.app.ui.theme.PracticeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticulatorLabScreen(
    component: ArticulatorLabComponent,
    modifier: Modifier = Modifier
) {
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    val state by component.state.collectAsState()

    Scaffold(
        topBar = {
            PracticeTopBar("Speak", "Find a comfortable sound", onBack = component::onBack)
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
            Text("Shape your sound.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
            Text("Choose a sound, then try saying it aloud.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp))
            // Target Phoneme Selector
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.isTargetPhoneme1,
                    onClick = { component.switchTargetPhoneme(true) },
                    label = { Text("${state.contrast.pairs.first().word1} /${state.contrast.phoneme1}/") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = !state.isTargetPhoneme1,
                    onClick = { component.switchTargetPhoneme(false) },
                    label = { Text("${state.contrast.pairs.first().word2} /${state.contrast.phoneme2}/") }
                )
            }

            Spacer(Modifier.height(10.dp))

            var visualMode by remember { mutableStateOf(0) }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = visualMode == 0,
                    onClick = { visualMode = 0 },
                    label = { Text("Sound map") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = visualMode == 1,
                    onClick = { visualMode = 1 },
                    label = { Text("Mouth position") }
                )
            }

            Spacer(Modifier.height(14.dp))

            if (visualMode == 0) {
                VowelTrapezoidPlot(
                    contrast = state.contrast,
                    liveF1 = if (state.liveF1 > 0f) state.liveF1 else null,
                    liveF2 = if (state.liveF2 > 0f) state.liveF2 else null
                )
            } else {
                MouthPositionReferenceCard(
                    phoneme = if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2,
                    exampleWord = state.contrast.pairs.first().let {
                        if (state.isTargetPhoneme1) it.word1 else it.word2
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            // Microphone Capture Button (AudioRecorder HAL)
            val isRecording = state.recorderState == AudioRecorderState.RECORDING

            Button(
                onClick = component::toggleRecording,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) scheme.error else scheme.primary,
                    contentColor = if (isRecording) scheme.onError else scheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(
                    text = if (isRecording) "Stop and review" else "Record your sound",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            if (state.recorderState == AudioRecorderState.ERROR) {
                Text("Microphone unavailable. Check microphone access and try again.", style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
            state.overallGopScore?.let { score ->
                Text("Your sound score · ${score.toInt()}/100", style = MaterialTheme.typography.titleMedium, color = if (score >= 80f) colors.success else colors.warning)
            }

            PracticeDetails("Sound measurements and placement") {
                // Acoustic Readout & GOP Score Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = scheme.surface
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
                                text = "Sound measurements",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            state.overallGopScore?.let { score ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (score >= 80f) colors.success.copy(alpha = 0.2f) else colors.warning.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Text(
                                        text = "Score: ${score.toInt()}/100",
                                        color = if (score >= 80f) colors.success else colors.warning,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AcousticMetric(label = "Jaw height · F1", value = "${state.liveF1.toInt()} Hz")
                            AcousticMetric(label = "Tongue position · F2", value = "${state.liveF2.toInt()} Hz")
                            AcousticMetric(label = "Duration", value = "${state.liveDurationMs} ms")
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Test bench synthesized vowel trigger
                OutlinedButton(
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                    onClick = { component.testSynthesizedVowel(state.isTargetPhoneme1) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try a generated example of /${if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2}/")
                }

                Spacer(Modifier.height(16.dp))

                ArticulatoryBiofeedbackCard(
                    indicators = state.contrast.visualIndicators,
                    phoneme1 = state.contrast.phoneme1,
                    phoneme2 = state.contrast.phoneme2
                )

            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AcousticMetric(label: String, value: String) {
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colors.accentInk
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
    }
}
