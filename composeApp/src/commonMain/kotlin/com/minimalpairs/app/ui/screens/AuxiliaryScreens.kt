package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.progress.ProgressDateRange
import com.minimalpairs.app.data.progress.ProgressState
import com.minimalpairs.app.ui.navigation.ConfusionMatrixComponent
import kotlin.math.roundToInt

@Composable
fun ConfusionMatrixScreen(
    component: ConfusionMatrixComponent,
    modifier: Modifier = Modifier,
    onPractice: () -> Unit = component::onBack
) {
    val scheme = MaterialTheme.colorScheme
    val state by component.state.collectAsState()
    Scaffold(modifier = modifier) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Every practice counts.", style = MaterialTheme.typography.headlineSmall)
                Text("Your listening and speaking, at a glance.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            ProgressFilterControls(state.filters, component::setDateRange, component::setContrastFilter, component::clearFilters)
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
                TextButton(onClick = component::clearFilters,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.secondary),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear filters") }
            } else if (state.attempts == 0 && (state.speaking.isNotEmpty() || state.modules.isNotEmpty()) && state.error == null) {
                Text("No listening answers yet.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Start listening") }
            } else if (state.attempts == 0 && state.error == null && state.pendingAnswers == 0) {
                Card(colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your progress starts here", style = MaterialTheme.typography.titleMedium)
                        Text("Try a listening exercise. Your sound patterns will appear as you practice.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Start listening") }
                    }
                }
            } else if (state.attempts > 0) {
                CompactListeningSummary(state)
            }
            if (!state.loading && (state.attempts > 0 || state.hasLoadedProgress && state.filters.isActive)) {
                if (state.filters.isActive) {
                    Text("All listening practice", style = MaterialTheme.typography.labelMedium)
                    if (state.attempts == 0) {
                        Text("Current streak: ${state.activity.currentStreak} ${if (state.activity.currentStreak == 1) "day" else "days"}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                ReviewsDueCard(state.reviews, state.nowEpochMs, component::practiceContrast, state.contrasts)
                if (state.reviews.due.isEmpty() && state.attempts > 0) {
                    CompactPracticeTargets(state, component::practiceContrast)
                }
            }
            // A failed read is not evidence of an empty speaking history.
            // Retain any cached results while the shared error offers a retry.
            Column(Modifier.fillMaxWidth()) {
                if (!state.loading && (!state.canRetryLoad || state.speaking.isNotEmpty())) {
                    val latest = state.speaking.firstOrNull()
                    val count = "${state.speaking.size} scored ${if (state.speaking.size == 1) "recording" else "recordings"}"
                    CompactProgressSection("Speaking progress", summary = count + (latest?.let { " · Latest ${it.score.roundToInt()}/100" } ?: ""), label = "Speaking") {
                        if (state.filters.isActive && state.speaking.isEmpty()) {
                            Text("No speaking recordings match these filters.", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            SpeakingProgressCard(state.speaking, state.speakingSummaries, state.nowEpochMs,
                                component::startSpeaking, component::practiceSpeaking, historyKey = state.filters.historyKey)
                        }
                    }
                }
                if (state.hasLoadedProgress) {
                    CompactProgressSection("Stories & Rhythm", summary = "${state.modules.size} scored attempts") {
                        ModuleProgressSections(state, component)
                    }
                }
                if (state.attempts > 0 || state.hasLoadedProgress && state.filters.isActive) {
                    CompactProgressSection("More listening details") {
                        if (state.reviews.due.isNotEmpty() && state.attempts > 0) {
                            CompactPracticeTargets(state, component::practiceContrast, showDetails = true)
                        } else if (state.attempts > 0) {
                            ProgressDetailSection("Contrast details", "Answer counts and mastery estimates for each contrast.") {
                                CompactPracticeTargets(state, component::practiceContrast, showDetails = true)
                            }
                        }
                        if (state.attempts > 0) {
                            Text("Accuracy is the share of listening answers you got right. Mastery estimates use answer speed and repeated success to plan reviews; they are separate from accuracy.",
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        ProgressDetailSection("Daily activity", "All listening practice: your last seven days and longest streak.") {
                            PracticeActivityCard(state.activity)
                        }
                        if (state.attempts > 0) {
                            ProgressDetailSection("Accuracy trends", "Compare your latest answers with earlier practice.") {
                                AccuracyTrendCard(state.trend, state.filters.isActive)
                            }
                            ProgressDetailSection("Sound patterns", "See which sounds you recognize and mix up.") {
                                SoundConfusionMatrix(state.contrasts, component::practiceContrast)
                            }
                            ProgressDetailSection("Practice history", "Review your latest listening answers.") {
                                PracticeHistory(state.listeningHistory, historyKey = state.filters.historyKey)
                            }
                            Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Keep listening") }
                        }
                    }
                }
                CompactProgressSection("Manage progress", muted = true) {
                    ProgressDataControls(state.pendingAnswers, component::exportProgress, component::importProgress, component::resetProgress)
                }
            }
        }
    }
}

@Composable
private fun CompactPracticeTargets(state: ProgressState, onPractice: (String) -> Unit, showDetails: Boolean = false) {
    var showAllContrasts by rememberSaveable { mutableStateOf(false) }
    val practiced = state.contrasts.filter { ListeningContrastsCatalog.getContrastById(it.contrastId) != null }
    Column(Modifier.fillMaxWidth()) {
        Text("Practice next", style = MaterialTheme.typography.titleSmall)
        practiced.take(if (showAllContrasts) practiced.size else 3).forEachIndexed { index, progress ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CompactContrastRow(progress.contrastId, progress, state.nowEpochMs, onPractice, showDetails = showDetails)
        }
        if (practiced.size > 3) {
            TextButton(onClick = { showAllContrasts = !showAllContrasts }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                Text(if (showAllContrasts) "Show fewer contrasts" else "Show all ${practiced.size} contrasts")
            }
        }
    }
}

@Composable
private fun ModuleProgressSections(state: ProgressState, component: ConfusionMatrixComponent) {
    listOf("STORIES" to "Stories", "RHYTHM" to "Rhythm").forEach { (module, title) ->
        val records = state.modules.filter { it.module == module }
        ProgressDetailSection("$title progress", "${records.size} scored attempts · separate from listening.") {
            if (module == "RHYTHM") {
                Text("Uses the selected date range. Contrast filters do not apply to Rhythm.", style = MaterialTheme.typography.bodySmall)
            }
            if (records.isEmpty() && (state.filters.dateRange != ProgressDateRange.ALL_TIME || module == "STORIES" && state.filters.contrastId != null)) {
                Text("No $title attempts match these filters.")
            } else {
                ModuleProgressCard(title, records, state.moduleSummaries.filter { it.latest.module == module }, state.nowEpochMs,
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
