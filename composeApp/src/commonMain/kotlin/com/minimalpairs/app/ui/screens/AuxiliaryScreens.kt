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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    var showAllContrasts by rememberSaveable { mutableStateOf(false) }
    val practicedContrasts = state.contrasts.filter { ListeningContrastsCatalog.getContrastById(it.contrastId) != null }
    Scaffold(topBar = { PracticeTopBar("Progress", onBack = component::onBack) }, modifier = modifier) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Every practice counts.", style = MaterialTheme.typography.headlineLarge)
            Text("Review your listening patterns and speaking scores.", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            ProgressFilterControls(state.filters, component::setDateRange, component::setContrastFilter, component::clearFilters)
            Text("Listening practice", style = MaterialTheme.typography.labelLarge)
            state.error?.let {
                Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (state.canRetryLoad) {
                Button(onClick = component::retryLoading, enabled = !state.isRefreshing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (state.isRefreshing) "Refreshing progress…" else "Retry loading")
                }
            }
            if (state.canRetrySave) {
                Button(onClick = component::retrySaves, enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (state.isSaving) "Saving progress…" else "Retry saving")
                }
            } else if (state.pendingAnswers > 0) {
                Text("Saving progress…", style = MaterialTheme.typography.bodySmall)
            }
            if (state.loading) {
                Text("Loading your progress…", style = MaterialTheme.typography.bodyLarge)
            } else if (state.attempts == 0 && state.filters.isActive && state.error == null) {
                Text("No listening answers match these filters.", style = MaterialTheme.typography.bodyLarge)
            } else if (state.attempts == 0 && (state.speaking.isNotEmpty() || state.modules.isNotEmpty()) && state.error == null) {
                Text("No listening answers yet.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start listening") }
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
                        if (!state.filters.isActive) Text("Current streak: ${state.activity.currentStreak} ${if (state.activity.currentStreak == 1) "day" else "days"}", style = MaterialTheme.typography.bodyMedium)
                        Text("Accuracy is the share of listening answers you got right.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!state.filters.isActive) ReviewsDueCard(state.reviews, state.nowEpochMs, component::practiceContrast)
                Text("Practice next", style = MaterialTheme.typography.titleLarge)
                Text("Your lowest-accuracy contrasts come first. Small samples can change quickly.", style = MaterialTheme.typography.bodyMedium)
                practicedContrasts.take(if (showAllContrasts) practicedContrasts.size else 3).forEach { progress ->
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
                if (practicedContrasts.size > 3) {
                    TextButton(onClick = { showAllContrasts = !showAllContrasts }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                        Text(if (showAllContrasts) "Show fewer contrasts" else "Show all ${practicedContrasts.size} contrasts")
                    }
                }
                Text("Mastery estimates use answer speed and repeated success to plan reviews. They are separate from accuracy.", style = MaterialTheme.typography.bodySmall)
                if (!state.filters.isActive) {
                    ProgressDetailSection("Daily activity", "Your last seven days and longest streak.") {
                        PracticeActivityCard(state.activity)
                    }
                }
                ProgressDetailSection("Accuracy trends", "Compare your latest answers with earlier practice.") {
                    AccuracyTrendCard(state.trend, state.filters.isActive)
                }
                ProgressDetailSection("Sound patterns", "See which sounds you recognize and mix up.") {
                    SoundConfusionMatrix(state.contrasts, component::practiceContrast)
                }
                ProgressDetailSection("Practice history", "Review your latest listening answers.") {
                    PracticeHistory(state.listeningHistory, historyKey = state.filters.historyKey)
                }
                Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Keep listening") }
            }
            if (state.hasLoadedProgress && state.filters.isActive) {
                Text("All listening practice", style = MaterialTheme.typography.titleMedium)
                Text("Current streak: ${state.activity.currentStreak} ${if (state.activity.currentStreak == 1) "day" else "days"}")
                ReviewsDueCard(state.reviews, state.nowEpochMs, component::practiceContrast)
                ProgressDetailSection("Daily activity", "All listening practice: your last seven days and longest streak.") {
                    PracticeActivityCard(state.activity)
                }
            }
            // A failed read is not evidence of an empty speaking history.
            // Retain any cached results while the shared error offers a retry.
            if (!state.loading && (!state.canRetryLoad || state.speaking.isNotEmpty())) {
                ProgressDetailSection("Speaking progress", "${state.speaking.size} scored ${if (state.speaking.size == 1) "recording" else "recordings"} · separate from listening accuracy.") {
                    if (state.filters.isActive && state.speaking.isEmpty()) {
                        Text("No speaking recordings match these filters.", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        SpeakingProgressCard(state.speaking, state.speakingSummaries, state.nowEpochMs,
                            component::startSpeaking, component::practiceSpeaking, historyKey = state.filters.historyKey)
                    }
                }
            }
            if (state.hasLoadedProgress) {
                listOf("STORIES" to "Stories", "RHYTHM" to "Rhythm").forEach { (module, title) ->
                    val records = state.modules.filter { it.module == module }
                    ProgressDetailSection("$title progress", "${records.size} scored attempts · separate from listening.") {
                        if (module == "RHYTHM") {
                            Text("Uses the selected date range. Contrast filters do not apply to Rhythm.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (records.isEmpty() && (state.filters.dateRange != com.minimalpairs.app.data.progress.ProgressDateRange.ALL_TIME ||
                                module == "STORIES" && state.filters.contrastId != null)) {
                            Text("No $title attempts match these filters.")
                        } else {
                            ModuleProgressCard(title, records,
                                state.moduleSummaries.filter { it.latest.module == module }, state.nowEpochMs,
                                historyKey = if (module == "RHYTHM") state.filters.dateRange.name else state.filters.historyKey,
                                onPractice = if (module == "STORIES") component::practiceStory else component::practiceRhythm)
                            if (records.isEmpty()) {
                                Button(onClick = { if (module == "STORIES") component.practiceStory("") else component.practiceRhythm("") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Start $title") }
                            }
                        }
                    }
                }
            }
            ProgressDetailSection("Manage progress", "Export, import, or reset saved practice.") {
                ProgressDataControls(state.pendingAnswers, component::exportProgress, component::importProgress, component::resetProgress)
            }
        }
    }
}
