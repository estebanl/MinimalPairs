package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.ui.components.ArticulatoryBiofeedbackCard
import com.minimalpairs.app.ui.components.PracticeIcon
import com.minimalpairs.app.ui.components.PracticeTopBar
import com.minimalpairs.app.ui.components.VowelTrapezoidPlot
import com.minimalpairs.app.ui.navigation.EarTrainerComponent
import com.minimalpairs.app.ui.theme.PracticeTheme
import kotlin.time.TimeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarTrainerScreen(component: EarTrainerComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    var trialStarted by remember(component) { mutableStateOf(TimeSource.Monotonic.markNow()) }
    var showTips by remember(component) { mutableStateOf(false) }
    val answered = state.lastResultCorrect != null
    val ink = PracticeTheme.colors.accentInk
    val success = PracticeTheme.colors.success
    val warning = PracticeTheme.colors.warning

    // nextTrial already starts playback, including when the same word is drawn again.
    LaunchedEffect(component) { component.playPrompt() }

    Scaffold(topBar = { PracticeTopBar("Listening practice", onBack = component::onBack) }, modifier = modifier) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("/${state.contrast.phoneme1}/  &  /${state.contrast.phoneme2}/", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Sound mastery · ${(state.sm2State.masteryScore * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { state.sm2State.masteryScore.coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp)
            )
            Spacer(Modifier.height(30.dp))
            Text("Which word\ndid you hear?", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("Listen closely. Then choose a word.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            FilledIconButton(
                onClick = component::playPrompt, enabled = !state.isPlayingAudio,
                shape = CircleShape, modifier = Modifier.size(96.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer, disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) { PracticeIcon(PracticeIcon.Listen, if (state.isPlayingAudio) "Playing" else "Replay sound", Modifier.size(34.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(if (state.isPlayingAudio) "Playing…" else "Tap to listen again", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(
                onClick = { component.setPlaybackSpeed(if (state.playbackSpeed == PlaybackSpeed.NORMAL) PlaybackSpeed.SLOW_DIAGNOSTIC else PlaybackSpeed.NORMAL) },
                colors = ButtonDefaults.textButtonColors(contentColor = ink)
            ) { Text(if (state.playbackSpeed == PlaybackSpeed.NORMAL) "Normal speed · 1×" else "Slower speed · 0.75×") }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(state.currentPair.word1 to state.contrast.phoneme1, state.currentPair.word2 to state.contrast.phoneme2).forEach { (word, sound) ->
                    OptionCard(
                        word = word, phoneme = sound,
                        isSelected = answered && word == state.targetWord,
                        isWrong = state.lastResultCorrect == false && word != state.targetWord,
                        enabled = !answered,
                        onClick = { component.onSelectAnswer(word, (trialStarted.elapsedNow().inWholeMilliseconds / 1000f).coerceAtLeast(.1f)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (answered) {
                val correct = state.lastResultCorrect == true
                Column(Modifier.fillMaxWidth().padding(top = 20.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PracticeIcon(if (correct) PracticeIcon.Check else PracticeIcon.Tips)
                        Text(if (correct) "You heard it." else "This one was “${state.targetWord}.”", style = MaterialTheme.typography.titleMedium, color = if (correct) success else warning, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (correct) "“${state.targetWord}” has the /${state.targetPhoneme}/ sound. Nicely noticed." else "Small differences take practice. Try the sound tips and listen again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { trialStarted = TimeSource.Monotonic.markNow(); component.nextTrial() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Next pair", Modifier.weight(1f), textAlign = TextAlign.Center)
                        PracticeIcon(PracticeIcon.Forward)
                    }
                }
            }
            if (state.showFormantHint) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Let's take it a little slower.", style = MaterialTheme.typography.titleSmall)
                        Text("Playback has slowed to help you hear the difference. Sound tips can help, too.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            TextButton(onClick = { showTips = true }, colors = ButtonDefaults.textButtonColors(contentColor = ink), modifier = Modifier.padding(top = 14.dp)) {
                PracticeIcon(PracticeIcon.Tips, modifier = Modifier.size(20.dp))
                Text("Sound tips", Modifier.padding(start = 8.dp))
            }
            if (!answered) Text("Take your time. You can listen more than once.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showTips) {
        ModalBottomSheet(
            onDismissRequest = { showTips = false; component.dismissFormantHint() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Feel the difference.", style = MaterialTheme.typography.headlineSmall)
                Text(soundTip(state.contrast.id), style = MaterialTheme.typography.bodyLarge)
                VowelTrapezoidPlot(state.contrast)
                ArticulatoryBiofeedbackCard(state.contrast.visualIndicators, state.contrast.phoneme1, state.contrast.phoneme2)
                Button(onClick = { showTips = false; component.dismissFormantHint() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Back to listening") }
            }
        }
    }
}

private fun soundTip(contrastId: String): String = when (contrastId) {
    "i_vs_I" -> "For “sheep,” lift your tongue toward the front and hold the sound a little longer. For “ship,” relax your tongue slightly lower and keep the sound short."
    "ae_vs_e" -> "Open your jaw a little more for “bad.” For “bed,” bring your jaw slightly closer and keep your tongue near the front."
    "a_vs_uh" -> "For “heart,” open your mouth and hold the vowel longer. The vowel in “hut” is shorter and more relaxed."
    "u_vs_uu" -> "Both sounds use rounded lips. For “fool,” round them more and hold the sound longer. For “full,” relax them and keep it short."
    "o_vs_or" -> "Keep your lips rounded. The vowel in “cot” is more open and shorter; the vowel in “caught” is usually held longer."
    "e_vs_I" -> "For “pen,” let your jaw open a little more. For “pin,” lift your tongue slightly higher. Both sounds stay near the front."
    else -> "Replay both sounds slowly. Notice how your tongue, lips, and jaw change position."
}

@Composable
fun OptionCard(word: String, phoneme: String, isSelected: Boolean, isWrong: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val resultColor = when { isSelected -> PracticeTheme.colors.success; isWrong -> PracticeTheme.colors.warning; else -> MaterialTheme.colorScheme.outlineVariant }
    val background = if (isSelected || isWrong) resultColor.copy(alpha = .1f) else MaterialTheme.colorScheme.surface
    Card(
        onClick = onClick, enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = background, disabledContainerColor = background, disabledContentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(if (isSelected || isWrong) 2.dp else 1.dp, resultColor),
        modifier = modifier.heightIn(min = 126.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(word.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text("/$phoneme/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isSelected || isWrong) Text(if (isSelected) "✓ Correct word" else "× Your choice", style = MaterialTheme.typography.labelSmall, color = resultColor, textAlign = TextAlign.Center)
        }
    }
}
