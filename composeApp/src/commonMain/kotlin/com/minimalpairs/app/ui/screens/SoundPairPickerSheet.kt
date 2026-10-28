package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.ListeningContrast
import com.minimalpairs.app.ui.components.PracticeIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoundPairPickerSheet(
    contrasts: List<ListeningContrast>,
    selectedContrastId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = contrasts.indexOfFirst { it.id == selectedContrastId }.coerceAtLeast(0)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 560.dp,
        containerColor = scheme.surface
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose a sound pair", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    Text("Tap a pair to start practising.", style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = scheme.surfaceVariant)
                ) {
                    PracticeIcon(PracticeIcon.Close, contentDescription = "Close sound pair picker")
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(contrasts, key = { it.id }) { contrast ->
                    SoundPairCard(contrast, contrast.id == selectedContrastId, onSelect)
                }
            }
        }
    }
}

@Composable
private fun SoundPairCard(contrast: ListeningContrast, isSelected: Boolean, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pair = contrast.pairs.first()
    Surface(
        onClick = { onSelect(contrast.id) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp).semantics { selected = isSelected },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp,
            if (isSelected) scheme.onSurface else scheme.outlineVariant)
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 22.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("${pair.word1} / ${pair.word2}", style = MaterialTheme.typography.titleSmall)
                Text("/${contrast.phoneme1}/ · /${contrast.phoneme2}/",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (isSelected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 6.dp),
                    shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary
                ) {
                    PracticeIcon(PracticeIcon.Check, modifier = Modifier.padding(2.dp).size(14.dp))
                }
            }
        }
    }
}
