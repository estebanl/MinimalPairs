package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.progress.reviewDueAt
import com.minimalpairs.app.data.progress.reviewTiming
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.ui.components.PracticeIcon
import com.minimalpairs.app.ui.components.PracticeTopBar
import com.minimalpairs.app.ui.navigation.ConfusionMatrixComponent

@Composable
fun ConfusionMatrixScreen(
    component: ConfusionMatrixComponent,
    modifier: Modifier = Modifier,
    onPractice: () -> Unit = component::onBack
) {
    val scheme = MaterialTheme.colorScheme
    val state by component.state.collectAsState()
    Scaffold(topBar = { PracticeTopBar("Progress", onBack = component::onBack) }, modifier = modifier) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Every listen counts.", style = MaterialTheme.typography.headlineLarge)
            Text("See the sounds you recognize and the ones to practice next.", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            Text("Listening practice", style = MaterialTheme.typography.labelLarge)
            state.error?.let {
                Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (state.canRetrySave) {
                Button(onClick = component::retrySaves, enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (state.isSaving) "Saving answers…" else "Retry saving")
                }
            } else if (state.pendingAnswers > 0) {
                Text("Saving answers…", style = MaterialTheme.typography.bodySmall)
            }
            if (state.loading) {
                Text("Loading your progress…", style = MaterialTheme.typography.bodyLarge)
            } else if (state.attempts == 0 && state.error == null && state.pendingAnswers == 0) {
                Card(colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PracticeIcon(PracticeIcon.Progress, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(18.dp))
                        Text("Your progress starts here", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text("Try a listening exercise. Your sound patterns will appear as you practice.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start listening") }
                    }
                }
            } else if (state.attempts > 0) {
                Card(colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${state.accuracy}% accuracy", style = MaterialTheme.typography.headlineMedium)
                        Text("Answers: ${state.attempts} · Contrasts practiced: ${state.contrasts.size}")
                        Text("Accuracy is the share of listening answers you got right.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                ReviewsDueCard(state.reviews, state.nowEpochMs, component::practiceContrast)
                PracticeActivityCard(state.activity)
                AccuracyTrendCard(state.trend)
                Text("Practice next", style = MaterialTheme.typography.titleLarge)
                Text("Your lowest-accuracy contrasts come first. Small samples can change quickly.", style = MaterialTheme.typography.bodyMedium)
                state.contrasts.forEach { progress ->
                    val contrast = ListeningContrastsCatalog.getContrastById(progress.contrastId)
                    if (contrast != null) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val pair = contrast.pairs.first()
                                Text("${pair.word1} / ${pair.word2}", style = MaterialTheme.typography.titleMedium)
                                Text("${progress.accuracy}% accuracy · ${progress.correct} of ${progress.attempts} correct")
                                progress.card?.let { card ->
                                    Text("Mastery estimate · ${(card.masteryScore * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                                    Text(reviewTiming(reviewDueAt(card), state.nowEpochMs), style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { component.practiceContrast(contrast.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text("Practice ${pair.word1} / ${pair.word2}")
                                }
                            }
                        }
                    }
                }
                Text("Mastery estimates use answer speed and repeated success to plan reviews. They are separate from accuracy.", style = MaterialTheme.typography.bodySmall)
                SoundConfusionMatrix(state.contrasts, component::practiceContrast)
                PracticeHistory(state.recentTrials)
                Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Keep listening") }
            }
        }
    }
}
