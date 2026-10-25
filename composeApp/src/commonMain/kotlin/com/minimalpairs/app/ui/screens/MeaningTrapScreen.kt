package com.minimalpairs.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.ui.navigation.MeaningTrapComponent
import com.minimalpairs.app.ui.theme.LabAmber
import com.minimalpairs.app.ui.theme.LabCyan
import com.minimalpairs.app.ui.theme.LabGreen
import com.minimalpairs.app.ui.theme.LabRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeaningTrapScreen(
    component: MeaningTrapComponent,
    modifier: Modifier = Modifier
) {
    val state by component.state.collectAsState()
    val story = state.activeStory

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("The Meaning Trap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Interactive Comic Dialogues: ${story.title}",
                            style = MaterialTheme.typography.bodySmall,
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

            Spacer(Modifier.height(8.dp))

            // Comic Panel 1: Setting & Prompt
            ComicPanelCard(
                panelTitle = "Panel 1: The Encounter",
                characterName = story.promptCharacter,
                speechBubbleText = story.promptDialogue,
                subCaption = story.premise
            )

            Spacer(Modifier.height(14.dp))

            // User Challenge Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your Turn to Speak:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = story.promptInstruction,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Target word: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = LabCyan.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "${story.intendedWord} /${story.intendedIpa}/",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = LabCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = " vs Trap: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = LabAmber.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "${story.trapWord} /${story.trapIpa}/",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = LabAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Speech Control Bar
            val isRec = state.isRecording
            Button(
                onClick = component::toggleRecording,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRec) LabRed else LabCyan,
                    contentColor = if (isRec) Color.White else Color.Black
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    text = if (isRec) "■ Stop & Evaluate Pronunciation" else "🎤 Record Spoken Response",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Test Simulation Buttons (for fast iteration and exploring both branches)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { component.triggerSimulatedOutcome(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Test Intended (/${story.intendedIpa}/)", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { component.triggerSimulatedOutcome(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Trigger Trap (/${story.trapIpa}/)", fontSize = 12.sp)
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
                            containerColor = if (isSuccess) LabGreen.copy(alpha = 0.15f) else LabRed.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                2.dp,
                                if (isSuccess) LabGreen else LabRed,
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
                                color = if (isSuccess) LabGreen else LabRed
                            )
                            Spacer(Modifier.height(8.dp))

                            // Large Comic Glyph
                            Text(
                                text = outcome.illustrationEmoji,
                                fontSize = 52.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(Modifier.height(8.dp))

                            // Character Speech Bubble
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 3.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "${story.promptCharacter}:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
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
                                text = outcome.outcomeDescription,
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
                                Text("💡", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Phonetic & Semantic Takeaway",
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
                                Text("Try This Scenario Again")
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
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = panelTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = characterName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = subCaption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // Speech Bubble
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
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
