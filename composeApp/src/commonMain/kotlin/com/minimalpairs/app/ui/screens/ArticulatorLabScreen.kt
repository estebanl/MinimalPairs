package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalpairs.app.core.hal.rememberMicrophonePermissionRequest
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.ui.components.LabIcon
import com.minimalpairs.app.ui.components.LabTarget
import com.minimalpairs.app.ui.components.LabModeTabs
import com.minimalpairs.app.ui.components.LabPlayback
import com.minimalpairs.app.ui.components.LabMeasurements
import com.minimalpairs.app.ui.components.PracticeIcon
import com.minimalpairs.app.ui.navigation.ArticulatorLabUiState
import com.minimalpairs.app.ui.components.ArticulatoryBiofeedbackCard
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
    modifier: Modifier = Modifier,
    topBarWindowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    val state by component.state.collectAsState()
    val isRecording = state.recorderState == AudioRecorderState.RECORDING
    val canSelectTarget = !isRecording && !state.isBusy
    var visualMode by rememberSaveable { mutableStateOf(3) }
    val targetPhoneme = if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2
    var permissionDenied by remember { mutableStateOf(false) }
    val requestMicrophone = rememberMicrophonePermissionRequest { granted ->
        permissionDenied = !granted
        if (granted) component.toggleRecording()
    }

    Scaffold(
        topBar = {
            // A custom top bar must handle the safe area that Material top bars apply internally.
            Box(Modifier.fillMaxWidth().windowInsetsPadding(topBarWindowInsets), contentAlignment = Alignment.Center) {
                Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = component::onBack) { PracticeIcon(PracticeIcon.Back, "Back") }
                    Column(Modifier.padding(start = 4.dp)) {
                        Text("Speak", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            fontSize = 34.sp, lineHeight = 38.sp)
                        Text(if (visualMode == 3) "Camera practice" else "Shape your sound",
                            fontSize = 15.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = scheme.background) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
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
                        modifier = Modifier.widthIn(max = 524.dp).fillMaxWidth().heightIn(min = 56.dp)
                    ) {
                        PracticeIcon(if (isRecording) PracticeIcon.Stop else PracticeIcon.Speak, modifier = Modifier.size(25.dp))
                        Spacer(Modifier.width(12.dp))
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
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var contrastMenuOpen by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(22.dp), color = scheme.background,
                        border = BorderStroke(1.dp, scheme.outlineVariant)) {
                        Column(Modifier.padding(5.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                LabTarget(state.selectedPair.word1, state.contrast.phoneme1, state.isTargetPhoneme1,
                                    canSelectTarget, { component.switchTargetPhoneme(true) }, Modifier.weight(1f))
                                LabTarget(state.selectedPair.word2, state.contrast.phoneme2, !state.isTargetPhoneme1,
                                    canSelectTarget, { component.switchTargetPhoneme(false) }, Modifier.weight(1f))
                                if (!largeText) {
                                    IconButton(onClick = { contrastMenuOpen = true }, enabled = canSelectTarget,
                                        modifier = Modifier.semantics { contentDescription = "Change sound" }) {
                                        LabIcon(LabIcon.Chevron)
                                    }
                                }
                            }
                            if (largeText) {
                                androidx.compose.material3.TextButton(onClick = { contrastMenuOpen = true },
                                    enabled = canSelectTarget, colors = ButtonDefaults.textButtonColors(contentColor = scheme.onBackground),
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text("Change sound")
                                    LabIcon(LabIcon.Chevron)
                                }
                            }
                        }
                    }
                    DropdownMenu(expanded = contrastMenuOpen && canSelectTarget,
                        onDismissRequest = { contrastMenuOpen = false }) {
                        Text("Example words", modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelLarge)
                        state.contrast.pairs.forEachIndexed { index, pair ->
                            DropdownMenuItem(text = { Text("${pair.word1} / ${pair.word2}") },
                                onClick = {
                                    contrastMenuOpen = false
                                    component.selectExamplePair(index)
                                })
                        }
                        Text("Contrasts", modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelLarge)
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
                Spacer(Modifier.height(12.dp))
                LabModeTabs(visualMode) { visualMode = it }
                Spacer(Modifier.height(12.dp))

                if (visualMode != 3) {
                    Text("Say /${if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2}/ on its own.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                }
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
                        },
                        footer = {
                            LabPlayback(targetPhoneme, !state.isBusy && !isRecording) {
                                component.playPhonemeExample(state.isTargetPhoneme1)
                            }
                        },
                        measurements = { ArticulatorMeasurements(state, isRecording) }
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

                if (visualMode != 3) {
                    LabPlayback(targetPhoneme, !state.isBusy && !isRecording) {
                        component.playPhonemeExample(state.isTargetPhoneme1)
                    }
                    Spacer(Modifier.height(12.dp))
                    LabMeasurements { ArticulatorMeasurements(state, isRecording) }
                }

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

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ArticulatorMeasurements(state: ArticulatorLabUiState, isRecording: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val colors = PracticeTheme.colors
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

    ArticulatoryBiofeedbackCard(
        indicators = state.contrast.visualIndicators,
        phoneme1 = state.contrast.phoneme1,
        phoneme2 = state.contrast.phoneme2
    )
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
