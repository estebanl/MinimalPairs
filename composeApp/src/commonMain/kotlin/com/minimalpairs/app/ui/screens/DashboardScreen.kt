package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.ListeningContrast
import com.minimalpairs.app.ui.components.PracticeIcon
import com.minimalpairs.app.ui.navigation.DashboardComponent

@Composable
fun DashboardScreen(component: DashboardComponent, modifier: Modifier = Modifier) {
    val contrasts = component.contrasts
    var lastOpenedContrastId by rememberSaveable { mutableStateOf<String?>(null) }
    val highlightedContrastId = lastOpenedContrastId ?: contrasts.firstOrNull()?.id

    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        PracticeIcon(PracticeIcon.Rhythm, modifier = Modifier.padding(5.dp).size(18.dp))
                    }
                    Text("minimal pairs", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Listen",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() }
                    )
                    Text(
                        "${contrasts.size} ${if (contrasts.size == 1) "contrast" else "contrasts"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pick a pair. Hear the difference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }
            itemsIndexed(contrasts, key = { _, contrast -> contrast.id }) { index, contrast ->
                ContrastRow(
                    contrast = contrast,
                    index = index,
                    isHighlighted = contrast.id == highlightedContrastId,
                    onClick = {
                        lastOpenedContrastId = contrast.id
                        component.onSelectContrast(contrast.id)
                    }
                )
                if (index < contrasts.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (contrasts.isEmpty()) {
                item {
                    Text(
                        "Practice sounds will appear here when available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ContrastRow(contrast: ListeningContrast, index: Int, isHighlighted: Boolean, onClick: () -> Unit) {
    val pair = contrast.pairs.firstOrNull()
    val title = pair?.let {
        "${it.word1.replaceFirstChar(Char::uppercaseChar)} / ${it.word2.replaceFirstChar(Char::uppercaseChar)}"
    } ?: "${contrast.phoneme1} / ${contrast.phoneme2}"

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Practice $title", onClick = onClick)
            .semantics { selected = isHighlighted }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.widthIn(min = 26.dp)
                .background(
                    color = if (isHighlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.extraSmall
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                color = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                "/${contrast.phoneme1}/ · /${contrast.phoneme2}/  ${contrast.pairs.size} ${if (contrast.pairs.size == 1) "pair" else "pairs"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PracticeIcon(PracticeIcon.ChevronRight, modifier = Modifier.size(18.dp))
    }
}
