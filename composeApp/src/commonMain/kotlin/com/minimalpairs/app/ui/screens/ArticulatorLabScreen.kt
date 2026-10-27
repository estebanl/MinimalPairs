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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalpairs.app.core.hal.rememberMicrophonePermissionRequest
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.ui.components.ArticulatoryBiofeedbackCard
import com.minimalpairs.app.ui.components.PracticeDetails
import com.minimalpairs.app.ui.components.PracticeTopBar
import com.minimalpairs.app.ui.components.LipCameraFeedbackCard
import com.minimalpairs.app.ui.components.OralCavityReferenceCard
import com.minimalpairs.app.ui.components.MouthPositionReferenceCard
import com.minimalpairs.app.ui.components.VowelTrapezoidPlot
import com.minimalpairs.app.ui.navigation.ArticulatorLabComponent
import com.minimalpairs.app.ui.theme.PracticeTheme
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticulatorLabScreen(
    component: ArticulatorLabComponent,
    modifier: Modifier = Modifier
) {
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    val state by component.state.collectAsState()
    val isRecording = state.recorderState == AudioRecorderState.RECORDING
    val canSelectTarget = !isRecording && !state.isBusy
    var permissionDenied by remember { mutableStateOf(false) }
    val requestMicrophone = rememberMicrophonePermissionRequest { granted ->
        permissionDenied = !granted
        if (granted) component.toggleRecording()
    }

    Scaffold(
        topBar = {
            PracticeTopBar("Speak", "Find a comfortable sound", onBack = component::onBack)
        },
        bottomBar = {
            Surface(color = scheme.background, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isRecording) {
                        Text("Recording · ${state.liveDurationMs / 1000}s / 30s",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }

                    Button(
                        onClick = { if (isRecording) component.toggleRecording() else requestMicrophone() },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) scheme.error else scheme.primary,
                            contentColor = if (isRecording) scheme.onError else scheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) {
                        Text(
                            text = when {
                                state.isScoring -> "Reviewing your sound…"
                                state.isPlaying -> "Playing…"
                                state.isBusy -> "Preparing microphone…"
                                isRecording -> "Stop and review"
                                else -> "Record your sound"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    if (permissionDenied) {
                        Text("Allow microphone access in app settings, then try again.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.error)
                    }
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
                    }
                    if (state.recorderState == AudioRecorderState.ERROR && state.error == null) {
                        Text("Microphone unavailable. Check microphone access and try again.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.error)
                    }
                }
            }
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
            Text("Choose a sound, then practise its vowel on its own.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp))
            var contrastMenuOpen by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { contrastMenuOpen = true }, enabled = canSelectTarget,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Contrast: /${state.contrast.phoneme1}/ and /${state.contrast.phoneme2}/")
                }
                DropdownMenu(expanded = contrastMenuOpen && canSelectTarget,
                    onDismissRequest = { contrastMenuOpen = false }) {
                    VowelContrastsCatalog.defaultContrasts.forEach { contrast ->
                        DropdownMenuItem(
                            text = { Text("/${contrast.phoneme1}/ and /${contrast.phoneme2}/ · ${contrast.pairs.first().word1} / ${contrast.pairs.first().word2}") },
                            onClick = {
                                contrastMenuOpen = false
                                component.selectContrast(contrast.id)
                            }
                        )
                    }
                }
            }
            Text("Example words", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.contrast.pairs.forEachIndexed { index, pair ->
                    FilterChip(selected = state.selectedPairIndex == index,
                        onClick = { component.selectExamplePair(index) }, enabled = canSelectTarget,
                        label = { Text("${pair.word1} / ${pair.word2}") })
                }
            }
            // Target Phoneme Selector
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.isTargetPhoneme1,
                    onClick = { component.switchTargetPhoneme(true) },
                    enabled = canSelectTarget,
                    label = { Text("${state.selectedPair.word1} /${state.contrast.phoneme1}/") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = !state.isTargetPhoneme1,
                    onClick = { component.switchTargetPhoneme(false) },
                    enabled = canSelectTarget,
                    label = { Text("${state.selectedPair.word2} /${state.contrast.phoneme2}/") }
                )
            }

            Text("Say /${if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2}/ on its own, using “${if (state.isTargetPhoneme1) state.selectedPair.word1 else state.selectedPair.word2}” as a reference.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            Spacer(Modifier.height(10.dp))

            var visualMode by rememberSaveable { mutableStateOf(0) }

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
                FilterChip(
                    selected = visualMode == 2,
                    onClick = { visualMode = 2 },
                    label = { Text("3D model") }
                )
                FilterChip(
                    selected = visualMode == 3,
                    onClick = { visualMode = 3 },
                    label = { Text("Camera") }
                )
            }

            Spacer(Modifier.height(14.dp))

            if (visualMode == 0) {
                VowelTrapezoidPlot(
                    contrast = state.contrast,
                    liveF1 = if (state.liveF1 > 0f) state.liveF1 else null,
                    liveF2 = if (state.liveF2 > 0f) state.liveF2 else null
                )
            } else if (visualMode == 3) {
                LipCameraFeedbackCard(
                    phoneme = if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2,
                    exampleWord = state.selectedPair.let {
                        if (state.isTargetPhoneme1) it.word1 else it.word2
                    }
                )
            } else if (visualMode == 2) {
                OralCavityReferenceCard(
                    phoneme = if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2,
                    exampleWord = state.selectedPair.let {
                        if (state.isTargetPhoneme1) it.word1 else it.word2
                    },
                    targetBounds = if (state.isTargetPhoneme1) state.contrast.boundsPhoneme1 else state.contrast.boundsPhoneme2,
                    observedF1 = state.liveF1.takeIf { it > 0f },
                    observedF2 = state.liveF2.takeIf { it > 0f },
                    isRecording = isRecording
                )
            } else {
                MouthPositionReferenceCard(
                    phoneme = if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2,
                    exampleWord = state.selectedPair.let {
                        if (state.isTargetPhoneme1) it.word1 else it.word2
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            state.progressSaveError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                Button(onClick = component::retryProgressSaves) { Text("Retry saving") }
            }
            state.feedback?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.hasRecording) {
                OutlinedButton(onClick = component::playRecording, enabled = !state.isBusy && !isRecording) {
                    Text("Listen to your recording")
                }
            }
            state.overallGopScore?.let { score ->
                Text("Your sound score · ${score.toInt()}/100", style = MaterialTheme.typography.titleMedium, color = if (score >= 80f) colors.success else colors.warning)
            }
            state.scoringDescription?.let { description ->
                Text(description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
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
                            AcousticMetric(label = "F3", value = "${state.liveF3.toInt()} Hz")
                            AcousticMetric(label = if (isRecording) "Recording time" else "Vowel duration", value = "${state.liveDurationMs} ms")
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Test bench synthesized vowel trigger
                OutlinedButton(
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                    onClick = { component.testSynthesizedVowel(state.isTargetPhoneme1) },
                    enabled = !state.isBusy && !isRecording,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Listen to a generated example of /${if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2}/")
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
