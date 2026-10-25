package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.ui.components.PracticeIcon
import com.minimalpairs.app.ui.components.PracticeTopBar
import com.minimalpairs.app.ui.navigation.ConfusionMatrixComponent
import com.minimalpairs.app.ui.theme.PracticeTheme

@Composable
fun ConfusionMatrixScreen(
    component: ConfusionMatrixComponent,
    modifier: Modifier = Modifier,
    onPractice: () -> Unit = component::onBack
) {
    val colors = PracticeTheme.colors
    val scheme = MaterialTheme.colorScheme
    val confusion = component.confusionMap
    val weights = component.confidenceWeights
    Scaffold(topBar = { PracticeTopBar("Progress", onBack = component::onBack) }, modifier = modifier) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Every listen counts.", style = MaterialTheme.typography.headlineLarge)
            Text("See the sounds you recognize and the ones to practice next.", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            if (confusion.isEmpty()) {
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
            } else {
                confusion.forEach { (target, chosenMap) ->
                    Card(colors = CardDefaults.cardColors(containerColor = scheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Sound /$target/", style = MaterialTheme.typography.titleMedium)
                                val weight = weights[target] ?: .5f
                                Text("Confidence · ${(weight * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = if (weight >= .7f) colors.success else colors.warning)
                            }
                            chosenMap.forEach { (chosen, count) ->
                                Text(
                                    if (target == chosen) "✓ Heard /$chosen/ correctly · $count times" else "Mixed up with /$chosen/ · $count times",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (target == chosen) colors.success else colors.warning
                                )
                            }
                        }
                    }
                }
                Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Keep listening") }
            }
        }
    }
}
