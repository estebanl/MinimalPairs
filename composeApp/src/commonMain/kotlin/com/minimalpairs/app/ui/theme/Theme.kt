package com.minimalpairs.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LabCyan = Color(0xFF00E5FF)
val LabCyanDark = Color(0xFF0097A7)
val LabAmber = Color(0xFFFFB300)
val LabAmberDark = Color(0xFFF57C00)
val LabGreen = Color(0xFF00E676)
val LabRed = Color(0xFFFF5252)

val DarkBackground = Color(0xFF101418)
val DarkSurface = Color(0xFF1B2026)
val DarkSurfaceVariant = Color(0xFF262C34)

val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEF2F6)

private val DarkColorScheme = darkColorScheme(
    primary = LabCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D40),
    secondary = LabAmber,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A3400),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1)
)

private val LightColorScheme = lightColorScheme(
    primary = LabCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    secondary = LabAmberDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF8E1),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = Color(0xFF1E293B),
    onSurface = Color(0xFF1E293B)
)

@Composable
fun MinimalPairsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
