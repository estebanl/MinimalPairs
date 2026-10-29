package com.minimalpairs.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.ui.components.PracticeDetails
import com.minimalpairs.app.ui.components.PracticeIcon
import com.minimalpairs.app.ui.components.PracticeTopBar
import com.minimalpairs.app.ui.navigation.MeaningTrapComponent
import com.minimalpairs.app.ui.theme.PracticeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeaningTrapScreen(
    component: MeaningTrapComponent,
    modifier: Modifier = Modifier
) {
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    val state by component.state.collectAsState()
    val story = state.activeStory

    LaunchedEffect(component) {
        component.playPromptAudio()
    }

    Scaffold(
        topBar = {
            PracticeTopBar("Stories", onBack = component::onBack)
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
            // Story Selector Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                items(component.availableStories) { itemStory ->
                    FilterChip(
                        selected = itemStory.id == story.id,
                        onClick = { component.selectStory(itemStory.id) },
                        label = { Text("${itemStory.intendedWord} vs ${itemStory.trapWord}") }
                    )
                }
            }

            state.recommendation?.let { recommendation ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (recommendation.basedOnConfusions) "Recommended practice" else "Story to try",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(recommendation.explanation, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = { component.selectStory(recommendation.storyId) },
                            enabled = !state.isRecording && !state.isScoring
                        ) {
                            Text("Practise ${recommendation.contrastLabel}")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Comic Panel 1: Setting & Prompt
            ComicPanelCard(
                panelTitle = story.title,
                characterName = story.promptCharacter,
                speechBubbleText = story.promptDialogue,
                subCaption = story.premise,
                isPlayingAudio = state.isPlayingAudio,
                canPlayAudio = !state.isRecording && !state.isScoring,
                onListenClick = component::playPromptAudio,
                illustration = { StoryIllustration(story) }
            )

            Spacer(Modifier.height(14.dp))

            // User Challenge Banner
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Your turn",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentInk
                        )
                        OutlinedButton(
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                            onClick = component::playTargetPhraseAudio,
                            enabled = !state.isPlayingAudio && !state.isRecording && !state.isScoring,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (state.isPlayingAudio) "Playing..." else "Hear phrase", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = story.promptInstruction,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Target word: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.accentInk.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "${story.intendedWord} /${story.intendedIpa}/",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentInk,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Instead of: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.warning.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "${story.trapWord} /${story.trapIpa}/",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.warning,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            state.playbackError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    colors = CardDefaults.cardColors(containerColor = scheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Audio unavailable", style = MaterialTheme.typography.titleSmall)
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.feedbackText.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    colors = CardDefaults.cardColors(containerColor = if (state.feedbackIsError) scheme.errorContainer else scheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.isScoring) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                            Text(
                                if (state.isScoring) "Checking your reply" else if (state.isSimulated) "Example ending" else "Reply feedback",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text(state.feedbackText, style = MaterialTheme.typography.bodyMedium)
                        state.gopScore?.let { score ->
                            Text("Sound confidence · ${story.intendedWord}: ${score.toInt()}/100", style = MaterialTheme.typography.bodyMedium)
                            state.alternateScore?.let { alternate ->
                                Text("Sound confidence · ${story.trapWord}: ${alternate.toInt()}/100", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Experimental confidence between these two vowels within the phrase; not pronunciation accuracy.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Speech Control Bar
            val isRec = state.isRecording
            Button(
                onClick = component::toggleRecording,
                enabled = !state.isScoring,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRec) scheme.error else scheme.primary,
                    contentColor = if (isRec) scheme.onError else scheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(
                    text = if (state.isScoring) "Checking your reply…" else if (isRec) "Stop and review" else "Record your reply",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = component::playRecording,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                enabled = state.hasRecording && !state.isRecording && !state.isScoring && !state.isPlayingAudio,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("Replay your reply")
            }

            Spacer(Modifier.height(8.dp))

            PracticeDetails("Explore example endings") {
                    Text("These examples are simulated; record your voice for personal feedback.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                // Test Simulation Buttons (for fast iteration and exploring both branches)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                        onClick = { component.triggerSimulatedOutcome(true) },
                        enabled = !state.isRecording && !state.isScoring,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Intended sound (/${story.intendedIpa}/)", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                        onClick = { component.triggerSimulatedOutcome(false) },
                        enabled = !state.isRecording && !state.isScoring,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Other sound (/${story.trapIpa}/)", fontSize = 12.sp)
                    }
                }

            }

            // Comic Panel 2: Branching Narrative Consequence
            AnimatedVisibility(
                visible = state.branch != MeaningTrapBranch.PENDING,
                enter = fadeIn() + slideInVertically()
            ) {
                val isSuccess = state.branch == MeaningTrapBranch.INTENDED_SUCCESS
                val outcome = if (isSuccess) story.intendedOutcome else story.trapOutcome

                Column(modifier = Modifier.padding(top = 20.dp)) {
                    // Outcome Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccess) colors.success.copy(alpha = 0.15f) else scheme.error.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                2.dp,
                                if (isSuccess) colors.success else scheme.error,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            // Sound effect burst
                            Text(
                                text = outcome.soundEffect,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = if (isSuccess) colors.success else scheme.error
                            )
                            Spacer(Modifier.height(8.dp))

                            StoryIllustration(story, state.branch)

                            Spacer(Modifier.height(8.dp))

                            // Character Speech Bubble
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = scheme.surface,
                                tonalElevation = 0.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "${story.promptCharacter}:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        OutlinedButton(
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                                            onClick = component::playOutcomeAudio,
                                            enabled = !state.isPlayingAudio && !state.isRecording && !state.isScoring,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(if (state.isPlayingAudio) "Playing..." else "Listen", fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "\"${outcome.dialogue}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = if (state.isSimulated) outcome.outcomeDescription
                                    else if (isSuccess) "Your reply matched the intended ending."
                                    else "Your reply matched the alternate ending.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Pedagogical Semantic Takeaway Card
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PracticeIcon(PracticeIcon.Tips)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "What changed?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = story.semanticTakeaway,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = component::resetCurrentStory,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Try the story again")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun ComicPanelCard(
    panelTitle: String,
    characterName: String,
    speechBubbleText: String,
    subCaption: String,
    isPlayingAudio: Boolean = false,
    canPlayAudio: Boolean = true,
    onListenClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null
) {
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center
            ) {
                Column {
                    Text(
                        text = panelTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentInk
                    )
                    Text(
                        text = characterName,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (onListenClick != null) {
                    OutlinedButton(
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentInk),
                        onClick = onListenClick,
                        enabled = !isPlayingAudio && canPlayAudio,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isPlayingAudio) "Playing..." else "Listen", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = subCaption,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            illustration?.let {
                it()
                Spacer(Modifier.height(12.dp))
            }

            // Speech Bubble
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                color = scheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "\"$speechBubbleText\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}
