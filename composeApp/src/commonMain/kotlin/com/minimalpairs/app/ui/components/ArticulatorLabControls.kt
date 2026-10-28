package com.minimalpairs.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import minimalpairs.composeapp.generated.resources.Res
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Screen-specific line icons; their geometry and stroke weight match the mirror concept. */
enum class LabIcon { Sound, Mouth, Model, Camera, Chevron, Play, Selected, Unselected }
private val labIcons = LabIcon.entries.associateWith { icon ->
    ImageVector.Builder(icon.name, 28.dp, 28.dp, 28f, 28f).apply {
        path(fill = if (icon == LabIcon.Play || icon == LabIcon.Selected) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            when (icon) {
                LabIcon.Sound -> { moveTo(2f, 22f); curveTo(7f, 22f, 6f, 7f, 10f, 7f); curveTo(14f, 7f, 12f, 20f, 17f, 20f); curveTo(21f, 20f, 20f, 5f, 23f, 5f); curveTo(25f, 5f, 25f, 10f, 26f, 12f) }
                LabIcon.Mouth -> { moveTo(2f, 14f); curveTo(8f, 4f, 9f, 5f, 14f, 8f); curveTo(19f, 5f, 20f, 4f, 26f, 14f); curveTo(20f, 25f, 8f, 25f, 2f, 14f); close(); moveTo(2f, 14f); curveTo(9f, 16f, 19f, 16f, 26f, 14f) }
                LabIcon.Model -> { moveTo(14f, 2f); lineTo(25f, 8f); lineTo(25f, 21f); lineTo(14f, 27f); lineTo(3f, 21f); lineTo(3f, 8f); close(); moveTo(3f, 8f); lineTo(14f, 14f); lineTo(25f, 8f); moveTo(14f, 14f); lineTo(14f, 27f) }
                LabIcon.Camera -> { moveTo(3f, 7f); lineTo(8f, 7f); lineTo(10f, 3f); lineTo(18f, 3f); lineTo(20f, 7f); lineTo(25f, 7f); lineTo(25f, 24f); lineTo(3f, 24f); close(); moveTo(19f, 15f); curveTo(19f, 22f, 9f, 22f, 9f, 15f); curveTo(9f, 8f, 19f, 8f, 19f, 15f); close() }
                LabIcon.Chevron -> { moveTo(7f, 11f); lineTo(14f, 18f); lineTo(21f, 11f) }
                LabIcon.Play -> { moveTo(9f, 5f); lineTo(23f, 14f); lineTo(9f, 23f); close() }
                LabIcon.Selected, LabIcon.Unselected -> { moveTo(24f, 14f); curveTo(24f, 27f, 4f, 27f, 4f, 14f); curveTo(4f, 1f, 24f, 1f, 24f, 14f); close() }
            }
        }
    }.build()
}

@Composable
fun LabIcon(icon: LabIcon, modifier: Modifier = Modifier.size(28.dp)) {
    Box(contentAlignment = Alignment.Center) {
        Icon(labIcons.getValue(icon), contentDescription = null, modifier = modifier)
        if (icon == LabIcon.Selected) Surface(Modifier.size(6.dp), color = MaterialTheme.colorScheme.background, shape = CircleShape) {}
    }
}

@Composable
fun LabTarget(word: String, phoneme: String, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.3f
    Surface(modifier = modifier.alpha(if (enabled) 1f else .55f), shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primary else scheme.surfaceVariant,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface) {
        Row(Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$word /$phoneme/" }
            .heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (largeText) {
                Column(Modifier.weight(1f)) {
                    Text("/$phoneme/", fontFamily = FontFamily.Serif, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    Text(word, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("/$phoneme/", fontFamily = FontFamily.Serif, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                Text(word, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                LabIcon(if (selected) LabIcon.Selected else LabIcon.Unselected, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun LabModeTabs(selected: Int, onSelect: (Int) -> Unit, vowelModes: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val labels = listOf("Map", "Mouth", "3D", "Camera")
    val descriptions = listOf("Sound map", "Mouth position", "3D model", "Camera")
    val icons = listOf(LabIcon.Sound, LabIcon.Mouth, LabIcon.Model, LabIcon.Camera)
    val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (if (vowelModes) labels.indices.toList() else listOf(1, 3)).chunked(columns).forEach { indices ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                indices.forEach { index ->
                    Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(12.dp),
                        color = if (selected == index) scheme.onBackground else scheme.background,
                        contentColor = if (selected == index) scheme.background else scheme.onBackground) {
                        Row(Modifier.fillMaxHeight().selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) })
                            .semantics { contentDescription = descriptions[index] }
                            .heightIn(min = 48.dp).padding(horizontal = 2.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                            LabIcon(icons[index], Modifier.size(16.dp))
                            Text(labels[index], fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LabRecordingCredits(wordExamples: Boolean = false) {
    var showCredits by remember { mutableStateOf(false) }
    var credits by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(showCredits, wordExamples) {
        if (showCredits) credits = if (wordExamples) "Word examples generated with ElevenLabs. These recordings are also used in Listen.\n\nhttps://elevenlabs.io/"
        else Res.readBytes("files/audio/phonemes/CREDITS.txt").decodeToString()
    }
    if (showCredits) {
        AlertDialog(
            onDismissRequest = { showCredits = false },
            title = { Text("Recording credits") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(credits, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { uriHandler.openUri("https://elevenlabs.io/") }) {
                        Text("About ElevenLabs")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCredits = false }) { Text("Close") } }
        )
    }
    TextButton(onClick = { showCredits = true },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
        Text("Recording credits", fontSize = 12.sp)
    }
}

@Composable
fun LabMeasurements(content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = scheme.outlineVariant)
        Surface(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), color = scheme.background) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 2.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (LocalDensity.current.fontScale <= 1.3f) {
                    PracticeIcon(PracticeIcon.Progress, modifier = Modifier.size(18.dp))
                }
                Text("Sound measurements", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                LabIcon(LabIcon.Chevron, Modifier.size(16.dp))
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        AnimatedVisibility(expanded) { Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
    }
}
