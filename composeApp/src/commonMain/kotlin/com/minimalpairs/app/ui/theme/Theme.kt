package com.minimalpairs.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PracticeYellow = Color(0xFFFAFA33)
private val Ink = Color(0xFF202B28)

@Immutable
data class PracticeColors(
    val accentInk: Color,
    val success: Color,
    val warning: Color
)

private val LightPracticeColors = PracticeColors(
    accentInk = Color(0xFF62620A),
    success = Color(0xFF216443),
    warning = Color(0xFF8C4E2A)
)
private val DarkPracticeColors = PracticeColors(
    accentInk = PracticeYellow,
    success = Color(0xFFA1DBB4),
    warning = Color(0xFFF0B58C)
)
private val LocalPracticeColors = staticCompositionLocalOf { LightPracticeColors }

object PracticeTheme {
    val colors: PracticeColors
        @Composable get() = LocalPracticeColors.current
}

// Yellow is a filled-control color. Use accentInk/secondary for text on light surfaces.
private val LightColors = lightColorScheme(
    primary = PracticeYellow,
    onPrimary = Ink,
    primaryContainer = Color(0xFFF5F5D9),
    onPrimaryContainer = Ink,
    secondary = LightPracticeColors.accentInk,
    onSecondary = Color.White,
    secondaryContainer = PracticeYellow,
    onSecondaryContainer = Ink,
    tertiary = LightPracticeColors.success,
    background = Color(0xFFF7F6F2),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFEEE7),
    onSurfaceVariant = Color(0xFF596961),
    surfaceContainer = Color(0xFFF7F6F2),
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFEFEEE7),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF74796D),
    outlineVariant = Color(0xFFDCE1DA),
    error = Color(0xFF9C3F32),
    onError = Color.White,
    errorContainer = Color(0xFFF9E9E5),
    onErrorContainer = Color(0xFF6F281F)
)
private val DarkColors = darkColorScheme(
    primary = PracticeYellow,
    onPrimary = Ink,
    primaryContainer = Color(0xFF343421),
    onPrimaryContainer = Color(0xFFEFF4EF),
    secondary = PracticeYellow,
    onSecondary = Ink,
    secondaryContainer = PracticeYellow,
    onSecondaryContainer = Ink,
    tertiary = DarkPracticeColors.success,
    background = Color(0xFF181E1C),
    onBackground = Color(0xFFEFF4EF),
    surface = Color(0xFF242C28),
    onSurface = Color(0xFFEFF4EF),
    surfaceVariant = Color(0xFF303A33),
    onSurfaceVariant = Color(0xFFB1BEB6),
    surfaceContainer = Color(0xFF242C28),
    surfaceContainerLow = Color(0xFF242C28),
    surfaceContainerHigh = Color(0xFF303A33),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF8D9B91),
    outlineVariant = Color(0xFF3F4C44),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF571C13),
    errorContainer = Color(0xFF4D2721),
    onErrorContainer = Color(0xFFFFDAD2)
)

private val PracticeTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun MinimalPairsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPracticeColors provides if (darkTheme) DarkPracticeColors else LightPracticeColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PracticeTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp)
            ),
            content = content
        )
    }
}
