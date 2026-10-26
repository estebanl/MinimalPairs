package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.ListeningContrast
import com.minimalpairs.app.ui.components.PracticeIcon
import com.minimalpairs.app.ui.navigation.DashboardComponent
import com.minimalpairs.app.ui.theme.PracticeTheme

@Composable
fun DashboardScreen(component: DashboardComponent, modifier: Modifier = Modifier) {
    val featured = component.contrasts.firstOrNull { it.id == "i_vs_I" } ?: component.contrasts.firstOrNull()
    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.small) {
                        PracticeIcon(PracticeIcon.Rhythm, modifier = Modifier.padding(7.dp).size(20.dp))
                    }
                    Text("minimal pairs", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(28.dp))
                Text("A LITTLE PRACTICE. A CLEARER VOICE.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("Hear the\ndifference.", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(10.dp))
                Text("Small sounds. New confidence.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            if (featured != null) {
                item { FeaturedContrast(featured, onClick = { component.onSelectContrast(featured.id) }) }
            }
            if (component.contrasts.size > 1) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Keep exploring", style = MaterialTheme.typography.titleMedium)
                    Text("Find your next sound", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(component.contrasts.filter { it.id != featured?.id }, key = { it.id }) { contrast ->
                    ContrastCard(contrast, onClick = { component.onSelectContrast(contrast.id) })
                }
            }
            item {
                Text(
                    if (featured == null) "Practice sounds will appear here when available." else "Go at your pace. Every listen counts.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FeaturedContrast(contrast: ListeningContrast, onClick: () -> Unit) {
    val pair = contrast.pairs.firstOrNull()
    val ink = PracticeTheme.colors.accentInk
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("START WITH A SOUND", style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.weight(1f))
                PracticeIcon(PracticeIcon.Listen)
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FeaturedWord(pair?.word1 ?: contrast.phoneme1, contrast.phoneme1, Modifier.weight(1f))
                FeaturedWord(pair?.word2 ?: contrast.phoneme2, contrast.phoneme2, Modifier.weight(1f))
            }
            Canvas(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp, vertical = 12.dp)) {
                val heights = listOf(.15f, .35f, .7f, 1f, .6f, .2f, .5f, .9f, .4f, .15f, .6f, 1f, .45f, .7f, .2f)
                val step = size.width / heights.size
                heights.forEachIndexed { index, fraction ->
                    val x = step * (index + .5f)
                    val half = size.height * fraction / 2f
                    drawLine(ink.copy(alpha = .6f), Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half), 3.dp.toPx(), StrokeCap.Round)
                }
            }
            Text("Two familiar words.\nOne small difference to listen for.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                Text("Start listening", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                PracticeIcon(PracticeIcon.Forward, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FeaturedWord(word: String, sound: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(word.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text("/$sound/", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ContrastCard(contrast: ListeningContrast, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pair = contrast.pairs.firstOrNull()
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pair?.let { "${it.word1.replaceFirstChar(Char::uppercaseChar)} / ${it.word2.replaceFirstChar(Char::uppercaseChar)}" } ?: "${contrast.phoneme1} / ${contrast.phoneme2}", style = MaterialTheme.typography.titleMedium)
                Text("/${contrast.phoneme1}/  &  /${contrast.phoneme2}/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${contrast.pairs.size} word pairs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PracticeIcon(PracticeIcon.Forward, modifier = Modifier.size(20.dp))
        }
    }
}
