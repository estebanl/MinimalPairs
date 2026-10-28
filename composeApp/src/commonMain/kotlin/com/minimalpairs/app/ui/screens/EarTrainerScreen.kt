package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.ui.components.ArticulatoryBiofeedbackCard
import com.minimalpairs.app.ui.components.PracticeIcon
import com.minimalpairs.app.ui.components.PracticeTopBar
import com.minimalpairs.app.ui.components.VowelTrapezoidPlot
import com.minimalpairs.app.ui.navigation.EarTrainerComponent
import com.minimalpairs.app.ui.theme.PracticeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarTrainerScreen(component: EarTrainerComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    var showTips by remember(component) { mutableStateOf(false) }
    val answered = state.lastResultCorrect != null
    val ink = PracticeTheme.colors.accentInk
    val scrollState = rememberScrollState()
    val playFocus = remember { FocusRequester() }
    var pendingPlayFocus by remember(component) { mutableStateOf<Long?>(null) }
    val closeTips = {
        showTips = false
        component.dismissFormantHint()
        component.setAutoChangePaused(false)
    }

    LaunchedEffect(component, state.trialId) {
        if (state.trialId > 0) {
            scrollState.scrollTo(0)
            pendingPlayFocus = state.trialId
        }
    }
    LaunchedEffect(pendingPlayFocus, state.isPlayingAudio) {
        if (pendingPlayFocus != null && !state.isPlayingAudio) {
            withFrameNanos { }
            playFocus.requestFocus()
            pendingPlayFocus = null
        }
    }

    Scaffold(
        topBar = { PracticeTopBar("Listening practice", onBack = component::onBack) },
        bottomBar = {
            if (answered) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = component::nextTrial,
                            enabled = !state.isPlayingAudio,
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 56.dp).semantics {
                                if (state.autoChangeEnabled) {
                                    progressBarRangeInfo = ProgressBarRangeInfo(state.autoChangeProgress ?: 0f, 0f..1f)
                                    stateDescription = if (state.autoChangeProgress == null) "Auto change paused" else "Continues automatically"
                                }
                            }
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Next word", textAlign = TextAlign.Center)
                                if (state.autoChangeEnabled) {
                                    NextWordArrowRing(state.autoChangeProgress)
                                } else {
                                    PracticeIcon(PracticeIcon.Forward)
                                }
                            }
                        }
                        if (state.autoChangeEnabled) {
                            Text(
                                if (state.autoChangeProgress == null) "Auto change paused" else "Continues automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier.verticalScroll(scrollState).padding(horizontal = 24.dp, vertical = 20.dp)
                    .widthIn(max = 560.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Which word do you hear?",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        state.playingOptionWord != null -> "Listen to “${state.playingOptionWord}”."
                        state.isPlayingAudio -> "Listen to the word."
                        answered -> "Listen again, or try the next word."
                        state.hasPlayedPrompt -> "Choose the word you heard."
                        else -> "Play the word, then choose an answer."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 72.dp)) {
                        Button(
                            onClick = component::playPrompt,
                            enabled = !state.isPlayingAudio,
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    .compositeOver(MaterialTheme.colorScheme.surface)
                            ),
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(playFocus)
                        ) {
                            PracticeIcon(PracticeIcon.Listen, modifier = Modifier.size(28.dp))
                            Text(
                                when {
                                    state.isPlayingAudio && state.playingOptionWord == null -> "Playing…"
                                    state.playbackError != null && !answered -> "Try again"
                                    state.hasPlayedPrompt -> "Play again"
                                    else -> "Play word"
                                },
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)))
                        val autoColor = if (state.autoChangeEnabled) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f).compositeOver(MaterialTheme.colorScheme.primary)
                        } else MaterialTheme.colorScheme.primary
                        Surface(
                            color = autoColor,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.fillMaxHeight().widthIn(min = 76.dp)
                                .toggleable(
                                    value = state.autoChangeEnabled,
                                    role = Role.Switch,
                                    onValueChange = component::setAutoChangeEnabled
                                ).semantics {
                                    contentDescription = "Auto change"
                                    stateDescription = if (state.autoChangeEnabled) "Automatically play words and continue after each answer"
                                    else "Play words and continue manually"
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                            ) {
                                PracticeIcon(PracticeIcon.AutoChange, modifier = Modifier.size(20.dp))
                                Text(if (state.autoChangeEnabled) "Auto" else "Manual", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                state.progressSaveError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = component::retryProgressSaves, enabled = !state.isSavingProgress) {
                        Text(if (state.isSavingProgress) "Saving progress…" else "Retry saving")
                    }
                }
                state.playbackError?.let { message ->
                    Text(
                        message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.selectableGroup()
                ) {
                    listOf(PlaybackSpeed.NORMAL to "Normal", PlaybackSpeed.SLOW_DIAGNOSTIC to "Slow").forEach { (speed, label) ->
                        FilterChip(
                            selected = state.playbackSpeed == speed,
                            onClick = { component.setPlaybackSpeed(speed) },
                            enabled = !state.isPlayingAudio,
                            label = { Text(label) },
                            leadingIcon = if (state.playbackSpeed == speed) {
                                { PracticeIcon(PracticeIcon.Check, modifier = Modifier.size(18.dp)) }
                            } else null,
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                stateDescription = if (state.playbackSpeed == speed) "Selected playback speed" else "Playback speed"
                            }
                        )
                    }
                }
                if (state.showFormantHint && state.playbackSpeed == PlaybackSpeed.SLOW_DIAGNOSTIC) {
                    Text(
                        "Slow playback is on to help you hear the difference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Spacer(Modifier.height(24.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stackAnswers = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.5f
                    val option: @Composable (String, Modifier) -> Unit = { word, optionModifier ->
                        OptionCard(
                            word = word,
                            isCorrect = answered && word == state.targetWord,
                            isWrong = answered && word == state.selectedWord && word != state.targetWord,
                            enabled = state.hasPlayedPrompt && !state.isPlayingAudio && !answered,
                            onClick = { component.onSelectAnswer(word) },
                            showPlayback = answered,
                            playbackEnabled = !state.isPlayingAudio,
                            isPlaying = state.playingOptionWord == word,
                            onPlay = { component.playOption(word) },
                            modifier = optionModifier
                        )
                    }
                    if (stackAnswers) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            option(state.currentPair.word1, Modifier.fillMaxWidth())
                            option(state.currentPair.word2, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            option(state.currentPair.word1, Modifier.weight(1f))
                            option(state.currentPair.word2, Modifier.weight(1f))
                        }
                    }
                }
                if (answered) {
                    val correct = state.lastResultCorrect == true
                    val feedbackRequester = remember { BringIntoViewRequester() }
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        feedbackRequester.bringIntoView()
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 20.dp).bringIntoViewRequester(feedbackRequester).semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                        },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PracticeIcon(if (correct) PracticeIcon.Check else PracticeIcon.Tips)
                        Text(
                            if (correct) "Correct — you heard “${state.targetWord}”."
                            else "You heard “${state.targetWord}”. You chose “${state.selectedWord}”.",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (correct) PracticeTheme.colors.success else PracticeTheme.colors.warning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                TextButton(
                    onClick = {
                        component.setAutoChangePaused(true)
                        showTips = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ink),
                    modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp)
                ) {
                    PracticeIcon(PracticeIcon.Tips, modifier = Modifier.size(20.dp))
                    Text("Sound tips", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (showTips) {
        var showDetails by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = closeTips,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Sound tips", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(state.contrast.soundTip, style = MaterialTheme.typography.bodyLarge)
                TextButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        stateDescription = if (showDetails) "Expanded" else "Collapsed"
                    }
                ) { Text(if (showDetails) "Less detail" else "More details") }
                if (showDetails) {
                    Text("/${state.contrast.phoneme1}/ and /${state.contrast.phoneme2}/", style = MaterialTheme.typography.titleMedium)
                    Text("Sound mastery · ${(state.sm2State.masteryScore.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    state.contrast.vowelReference?.let { vowel ->
                        VowelTrapezoidPlot(vowel)
                        ArticulatoryBiofeedbackCard(vowel.visualIndicators, vowel.phoneme1, vowel.phoneme2)
                    }
                }
                Button(onClick = closeTips, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Back to listening") }
            }
        }
    }
}

@Composable
private fun NextWordArrowRing(progress: Float?) {
    val color = LocalContentColor.current
    Box(
        Modifier.size(32.dp).drawBehind {
            val strokeWidth = 2.5.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val inset = Offset(strokeWidth / 2, strokeWidth / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = color.copy(alpha = color.alpha * 0.15f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = inset, size = arcSize, style = Stroke(strokeWidth)
            )
            if (progress != null && progress > 0f) {
                drawArc(
                    color = color, startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                    topLeft = inset, size = arcSize,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
        },
        contentAlignment = Alignment.Center
    ) {
        PracticeIcon(PracticeIcon.Forward, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun OptionCard(
    word: String,
    isCorrect: Boolean,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    showPlayback: Boolean,
    playbackEnabled: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resultColor = when {
        isCorrect -> PracticeTheme.colors.success
        isWrong -> PracticeTheme.colors.warning
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val background = if (isCorrect || isWrong) resultColor.copy(alpha = .1f) else MaterialTheme.colorScheme.surface
    val colors = CardDefaults.cardColors(
        containerColor = background, disabledContainerColor = background,
        disabledContentColor = MaterialTheme.colorScheme.onSurface
    )
    val border = BorderStroke(if (isCorrect || isWrong) 2.dp else 1.dp, resultColor)
    val content: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(word.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (isCorrect || isWrong) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PracticeIcon(if (isCorrect) PracticeIcon.Check else PracticeIcon.Close, modifier = Modifier.size(18.dp))
                    Text(if (isCorrect) "Correct answer" else "Your choice", style = MaterialTheme.typography.labelLarge, color = resultColor)
                }
            }
            if (showPlayback) {
                TextButton(
                    onClick = onPlay,
                    enabled = playbackEnabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = PracticeTheme.colors.accentInk),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    PracticeIcon(PracticeIcon.Listen, modifier = Modifier.size(20.dp))
                    Text(
                        if (isPlaying) "Playing…" else "Play $word",
                        modifier = Modifier.padding(start = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    if (showPlayback) {
        Card(colors = colors, border = border, modifier = modifier.heightIn(min = 88.dp)) { content() }
    } else {
        Card(
            onClick = onClick, enabled = enabled,
            colors = colors, border = border, modifier = modifier.heightIn(min = 88.dp)
        ) { content() }
    }
}
