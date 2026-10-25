package com.minimalpairs.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.ui.theme.PracticeTheme

/** Small, shared vector set: consistent strokes and no platform-dependent emoji. */
enum class PracticeIcon { Listen, Speak, Progress, Stories, Rhythm, Back, Forward, Close, Check, Tips, Stop }

private val icons = PracticeIcon.entries.associateWith { icon ->
    ImageVector.Builder(icon.name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            when (icon) {
                PracticeIcon.Listen -> {
                    moveTo(4f, 14f); lineTo(4f, 11f); curveTo(4f, 1f, 20f, 1f, 20f, 11f); lineTo(20f, 14f)
                    moveTo(4f, 12f); lineTo(7f, 12f); lineTo(7f, 20f); lineTo(4f, 20f); close()
                    moveTo(17f, 12f); lineTo(20f, 12f); lineTo(20f, 20f); lineTo(17f, 20f); close()
                }
                PracticeIcon.Speak -> {
                    moveTo(9f, 6f); curveTo(9f, 2f, 15f, 2f, 15f, 6f); lineTo(15f, 12f)
                    curveTo(15f, 16f, 9f, 16f, 9f, 12f); close()
                    moveTo(6f, 11f); curveTo(6f, 21f, 18f, 21f, 18f, 11f)
                    moveTo(12f, 19f); lineTo(12f, 22f); moveTo(9f, 22f); lineTo(15f, 22f)
                }
                PracticeIcon.Progress -> {
                    moveTo(5f, 20f); lineTo(5f, 14f); moveTo(12f, 20f); lineTo(12f, 8f)
                    moveTo(19f, 20f); lineTo(19f, 3f)
                }
                PracticeIcon.Stories -> {
                    moveTo(4f, 4f); lineTo(18f, 4f); lineTo(18f, 15f); lineTo(9f, 15f)
                    lineTo(4f, 19f); close(); moveTo(8f, 8f); lineTo(14f, 8f)
                    moveTo(8f, 11f); lineTo(12f, 11f); moveTo(21f, 9f); lineTo(21f, 21f); lineTo(16f, 18f)
                }
                PracticeIcon.Rhythm -> {
                    moveTo(3f, 10f); lineTo(3f, 14f); moveTo(7f, 6f); lineTo(7f, 18f)
                    moveTo(12f, 3f); lineTo(12f, 21f); moveTo(17f, 7f); lineTo(17f, 17f)
                    moveTo(21f, 10f); lineTo(21f, 14f)
                }
                PracticeIcon.Back -> { moveTo(20f, 12f); lineTo(4f, 12f); moveTo(11f, 5f); lineTo(4f, 12f); lineTo(11f, 19f) }
                PracticeIcon.Forward -> { moveTo(4f, 12f); lineTo(20f, 12f); moveTo(13f, 5f); lineTo(20f, 12f); lineTo(13f, 19f) }
                PracticeIcon.Close -> { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(6f, 18f); lineTo(18f, 6f) }
                PracticeIcon.Check -> { moveTo(5f, 12f); lineTo(10f, 17f); lineTo(19f, 7f) }
                PracticeIcon.Tips -> {
                    moveTo(9f, 17f); curveTo(9f, 13f, 6f, 13f, 6f, 8f)
                    curveTo(6f, 1f, 18f, 1f, 18f, 8f); curveTo(18f, 13f, 15f, 13f, 15f, 17f)
                    close(); moveTo(10f, 21f); lineTo(14f, 21f)
                }
                PracticeIcon.Stop -> { moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close() }
            }
        }
    }.build()
}

@Composable
fun PracticeIcon(icon: PracticeIcon, contentDescription: String? = null, modifier: Modifier = Modifier.size(24.dp)) {
    Icon(imageVector = icons.getValue(icon), contentDescription = contentDescription, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    CenterAlignedTopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { PracticeIcon(PracticeIcon.Back, "Back") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
fun PracticeDetails(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            colors = ButtonDefaults.textButtonColors(contentColor = PracticeTheme.colors.accentInk)
        ) { Text(if (expanded) "− $title" else "+ $title") }
        AnimatedVisibility(expanded) { Column { content() } }
    }
}
