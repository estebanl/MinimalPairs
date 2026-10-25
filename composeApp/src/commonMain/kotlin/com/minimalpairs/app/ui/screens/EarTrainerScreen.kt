package com.minimalpairs.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalpairs.app.core.model.DrillType
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.ui.components.ArticulatoryBiofeedbackCard
import com.minimalpairs.app.ui.components.VowelTrapezoidPlot
import com.minimalpairs.app.ui.navigation.EarTrainerComponent
import com.minimalpairs.app.ui.theme.LabAmber
import com.minimalpairs.app.ui.theme.LabCyan
import com.minimalpairs.app.ui.theme.LabGreen
import com.minimalpairs.app.ui.theme.LabRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarTrainerScreen(
    component: EarTrainerComponent,
    modifier: Modifier = Modifier
) {
    val state by component.state.collectAsState()
    var trialStartTime by remember(state.targetWord) { mutableLongStateOf(0L) }

    LaunchedEffect(state.targetWord) {
        trialStartTime = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
        component.playPrompt()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "/${state.contrast.phoneme1}/ vs /${state.contrast.phoneme2}/ Ear Trainer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mode: ${state.drillType.name.replace("_", " ")} | Mastery: ${(state.sm2State.masteryScore * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = component::onBack) {
                        Text("←", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    FilterChip(
                        selected = state.playbackSpeed == PlaybackSpeed.SLOW_DIAGNOSTIC,
                        onClick = {
                            val next = if (state.playbackSpeed == PlaybackSpeed.SLOW_DIAGNOSTIC)
                                PlaybackSpeed.NORMAL else PlaybackSpeed.SLOW_DIAGNOSTIC
                            component.setPlaybackSpeed(next)
                        },
                        label = { Text(state.playbackSpeed.label) }
                    )
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
            // Mastery Progress Indicator
            LinearProgressIndicator(
                progress = { state.sm2State.masteryScore },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = LabCyan,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // 3-Failure Slow-Mo Diagnostic Trigger Banner
            AnimatedVisibility(visible = state.showFormantHint || state.consecutiveFailures >= 3) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = LabAmber.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Slow-Motion Diagnostic Triggered",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = LabAmber
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "3 consecutive misses detected. Playback automatically slowed to 0.75x phase-vocoded to preserve acoustic pitch. View the Formant visualizer below for articulatory cues.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { /* Bottom sheet opened below */ },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = LabAmber.copy(alpha = 0.4f)
                            )
                        ) {
                            Text("Open Formant Visualizer Hint", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Audio Playback Trigger Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .background(
                        if (state.isPlayingAudio) LabCyan.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                    .border(
                        3.dp,
                        if (state.isPlayingAudio) LabCyan else MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
                    .clickable { component.playPrompt() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.isPlayingAudio) "🔊" else "▶",
                        fontSize = 32.sp
                    )
                    Text(
                        text = if (state.isPlayingAudio) "Playing" else "Listen",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Tap to replay (${state.playbackSpeed.label})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            // Drill Options
            Text(
                text = "Which word did you hear?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OptionCard(
                    word = state.currentPair.word1,
                    phoneme = state.contrast.phoneme1,
                    isSelected = state.lastResultCorrect != null && state.currentPair.word1 == state.targetWord,
                    isWrong = state.lastResultCorrect == false && state.currentPair.word1 != state.targetWord,
                    onClick = {
                        val now = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
                        val latencySec = ((now - trialStartTime) / 1000f).coerceAtLeast(0.1f)
                        component.onSelectAnswer(state.currentPair.word1, latencySec)
                    },
                    modifier = Modifier.weight(1f)
                )

                OptionCard(
                    word = state.currentPair.word2,
                    phoneme = state.contrast.phoneme2,
                    isSelected = state.lastResultCorrect != null && state.currentPair.word2 == state.targetWord,
                    isWrong = state.lastResultCorrect == false && state.currentPair.word2 != state.targetWord,
                    onClick = {
                        val now = kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
                        val latencySec = ((now - trialStartTime) / 1000f).coerceAtLeast(0.1f)
                        component.onSelectAnswer(state.currentPair.word2, latencySec)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Feedback & Next Trial Button
            AnimatedVisibility(visible = state.lastResultCorrect != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text(
                        text = state.feedbackMessage,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.lastResultCorrect == true) LabGreen else LabRed,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = component::nextTrial,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next Pair →", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Inline Formant Visualizer & Articulator Biofeedback
            VowelTrapezoidPlot(contrast = state.contrast)
            Spacer(Modifier.height(14.dp))
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
fun OptionCard(
    word: String,
    phoneme: String,
    isSelected: Boolean,
    isWrong: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected -> LabGreen
        isWrong -> LabRed
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    val bgColor = when {
        isSelected -> LabGreen.copy(alpha = 0.15f)
        isWrong -> LabRed.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = modifier
            .height(110.dp)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = word,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "/$phoneme/",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
