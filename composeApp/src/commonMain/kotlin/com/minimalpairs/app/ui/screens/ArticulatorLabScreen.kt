package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.hal.rememberMicrophonePermissionRequest
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.ui.components.LabIcon
import com.minimalpairs.app.ui.components.LabTarget
import com.minimalpairs.app.ui.components.LabModeTabs
import com.minimalpairs.app.ui.components.LabRecordingCredits
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
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.ui.reference.ConsonantPracticeReferences
import androidx.compose.foundation.layout.Box

@Composable
fun ArticulatorLabScreen(
    component: ArticulatorLabComponent,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
) {
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.3f
    val state by component.state.collectAsState()
    val vowel = state.contrast.vowelReference
    val isVowel = vowel != null
    val targetWord = if (state.isTargetPhoneme1) state.selectedPair.word1 else state.selectedPair.word2
    val consonantCue = ConsonantPracticeReferences.forPhoneme(if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2)
    val isRecording = state.recorderState == AudioRecorderState.RECORDING
    val showPlayback = state.hasRecording && !isRecording
    val canSelectTarget = !isRecording && !state.isBusy
    var soundPairPickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(canSelectTarget) {
        if (!canSelectTarget) soundPairPickerOpen = false
    }
    val targetPhoneme = if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2
    var visualMode by rememberSaveable { mutableStateOf(3) }
    val activeVisualMode = if (!isVowel && visualMode != 3) 1 else visualMode
    var permissionDenied by remember { mutableStateOf(false) }
    val requestMicrophone = rememberMicrophonePermissionRequest { granted ->
        permissionDenied = !granted
        if (granted) component.toggleRecording()
    }

    Scaffold(
        contentWindowInsets = contentWindowInsets,
        bottomBar = {
            Surface(color = scheme.background) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isRecording) {
                        Text("Recording · ${state.liveDurationMs / 1000}s / 30s",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }

                    if (state.isBusy) {
                        Text(
                            when {
                                state.isScoring -> "Reviewing your sound…"
                                state.isPlaying -> "Playing…"
                                else -> "Preparing microphone…"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    FlowRow(
                        modifier = Modifier.widthIn(max = 524.dp).fillMaxWidth(),
                        maxItemsInEachRow = if (largeText) 2 else 3,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { component.playPhonemeExample(state.isTargetPhoneme1) },
                            enabled = !state.isBusy && !isRecording,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, scheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onBackground),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .semantics { contentDescription = if (isVowel) "Hear /$targetPhoneme/ example" else "Hear $targetWord example" }
                        ) {
                            SpeakActionLabel("Example", largeText) {
                                PracticeIcon(PracticeIcon.Listen, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (showPlayback) {
                            Button(
                                onClick = component::playRecording,
                                enabled = !state.isBusy,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Play my recording" }
                            ) {
                                SpeakActionLabel("Play mine", largeText) {
                                    LabIcon(LabIcon.Play, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Button(
                            onClick = { if (isRecording) component.toggleRecording() else requestMicrophone() },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(14.dp),
                            border = if (showPlayback) BorderStroke(1.dp, scheme.outlineVariant) else null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    isRecording -> scheme.error
                                    showPlayback -> scheme.background
                                    else -> scheme.primary
                                },
                                contentColor = when {
                                    isRecording -> scheme.onError
                                    showPlayback -> scheme.onBackground
                                    else -> scheme.onPrimary
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                            modifier = Modifier.weight(if (showPlayback) 1f else 1.4f).heightIn(min = 48.dp)
                                .semantics { contentDescription = if (isRecording) "Stop recording" else "Record your sound" }
                        ) {
                            SpeakActionLabel(if (isRecording) "Stop" else "Record", largeText) {
                                PracticeIcon(if (isRecording) PracticeIcon.Stop else PracticeIcon.Speak, modifier = Modifier.size(18.dp))
                            }
                        }
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
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FlowRow(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp),
                    maxItemsInEachRow = if (largeText) 1 else 2,
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("MINIMAL PAIRS", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        Text("Speak", style = MaterialTheme.typography.headlineSmall)
                    }
                    Surface(onClick = { soundPairPickerOpen = true }, enabled = canSelectTarget,
                        modifier = Modifier.weight(1.15f).semantics { contentDescription = "Change sound pair" },
                        shape = RoundedCornerShape(24.dp), color = scheme.surface,
                        border = BorderStroke(1.dp, scheme.outlineVariant)) {
                        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${state.selectedPair.word1} / ${state.selectedPair.word2}",
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            LabIcon(LabIcon.Chevron, Modifier.size(16.dp))
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().selectableGroup().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabTarget(state.selectedPair.word1, state.contrast.phoneme1, state.isTargetPhoneme1,
                            canSelectTarget, { component.switchTargetPhoneme(true) }, Modifier.weight(1f))
                        LabTarget(state.selectedPair.word2, state.contrast.phoneme2, !state.isTargetPhoneme1,
                            canSelectTarget, { component.switchTargetPhoneme(false) }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                LabModeTabs(activeVisualMode, { visualMode = it }, vowelModes = isVowel)
                Spacer(Modifier.height(10.dp))
                if (activeVisualMode != 3) {
                    Text(if (isVowel) "Say /$targetPhoneme/ on its own."
                        else "Say “$targetWord” and focus on /$targetPhoneme/.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                }

                if (activeVisualMode == 0 && vowel != null) {
                    VowelTrapezoidPlot(
                        contrast = vowel,
                        targetPhoneme = targetPhoneme,
                        liveF1 = if (state.liveF1 > 0f) state.liveF1 else null,
                        liveF2 = if (state.liveF2 > 0f) state.liveF2 else null
                    )
                } else if (activeVisualMode == 3) {
                    LipCameraFeedbackCard(
                        practiceInstruction = if (isVowel) "Say /$targetPhoneme/ on its own." else "Say “$targetWord” and focus on /$targetPhoneme/.",
                        placementCue = consonantCue,
                        phoneme = if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2,
                        exampleWord = state.selectedPair.let {
                            if (state.isTargetPhoneme1) it.word1 else it.word2
                        },
                        footer = {
                            LabRecordingCredits(wordExamples = !isVowel)
                        },
                        measurements = { ArticulatorMeasurements(state, isRecording) }
                    )
                } else if (activeVisualMode == 2 && vowel != null) {
                    OralCavityReferenceCard(
                        phoneme = if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2,
                        exampleWord = state.selectedPair.let {
                            if (state.isTargetPhoneme1) it.word1 else it.word2
                        },
                        targetBounds = if (state.isTargetPhoneme1) vowel.boundsPhoneme1 else vowel.boundsPhoneme2,
                        observedF1 = state.liveF1.takeIf { it > 0f },
                        observedF2 = state.liveF2.takeIf { it > 0f },
                        isRecording = isRecording
                    )
                } else if (!isVowel) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MouthPositionReferenceCard(phoneme = targetPhoneme, exampleWord = targetWord)
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = scheme.surface)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(state.contrast.soundTip, style = MaterialTheme.typography.bodyMedium)
                                Text("Listen to the example, record the word, then play yours to compare.",
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("Record the whole word. The phoneme service aligns it and scores the selected sound.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    MouthPositionReferenceCard(
                        phoneme = if (state.isTargetPhoneme1) state.contrast.phoneme1 else state.contrast.phoneme2,
                        exampleWord = state.selectedPair.let {
                            if (state.isTargetPhoneme1) it.word1 else it.word2
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (activeVisualMode != 3) {
                    LabMeasurements {
                        ArticulatorMeasurements(state, isRecording)
                        LabRecordingCredits(wordExamples = !isVowel)
                    }
                }

                state.progressSaveError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = component::retryProgressSaves) { Text("Retry saving") }
                }
                state.feedback?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                state.overallGopScore?.let { score ->
                    Text("${if (isVowel) "Your sound score" else "Selected sound /$targetPhoneme/"} · ${score.toInt()}/100", style = MaterialTheme.typography.titleMedium, color = if (score >= 80f) colors.success else colors.warning)
                }
                state.scoringDescription?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (soundPairPickerOpen && canSelectTarget) {
        SoundPairPickerSheet(
            contrasts = ListeningContrastsCatalog.defaultContrasts,
            selectedContrastId = state.contrast.id,
            onSelect = { contrastId ->
                soundPairPickerOpen = false
                component.selectContrast(contrastId)
            },
            onDismiss = { soundPairPickerOpen = false }
        )
    }
}

@Composable
private fun SpeakActionLabel(label: String, stacked: Boolean, icon: @Composable () -> Unit) {
    if (stacked) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun ArticulatorMeasurements(state: ArticulatorLabUiState, isRecording: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val colors = PracticeTheme.colors
    val vowel = state.contrast.vowelReference
    if (vowel == null) {
        Text("Recording time · ${state.liveDurationMs} ms", style = MaterialTheme.typography.bodyMedium)
        Text("Compare the example with your recording. Vowel formants do not assess this consonant.",
            style = MaterialTheme.typography.bodySmall)
        return
    }
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
        indicators = vowel.visualIndicators,
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
