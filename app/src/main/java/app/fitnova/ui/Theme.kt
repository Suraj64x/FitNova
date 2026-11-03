package app.fitnova.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitnova.ThemeMode

/** One accent plus three status colours. Flat surfaces and hairline borders instead of glow or shadow. */
object Tone {
    val accent = Color(0xFF4F7DF3)
    val good = Color(0xFF22A55A)
    val warn = Color(0xFFE59A0B)
    val bad = Color(0xFFE5484D)
}

private val Dark = darkColorScheme(
    primary = Color(0xFFEDEEF0),
    onPrimary = Color(0xFF0E0F11),
    primaryContainer = Color(0xFF23262B),
    onPrimaryContainer = Color(0xFFEDEEF0),
    secondary = Tone.accent,
    onSecondary = Color.White,
    background = Color(0xFF0E0F11),
    onBackground = Color(0xFFEDEEF0),
    surface = Color(0xFF0E0F11),
    onSurface = Color(0xFFEDEEF0),
    surfaceVariant = Color(0xFF1A1C20),
    onSurfaceVariant = Color(0xFF9BA1A8),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF131518),
    surfaceContainer = Color(0xFF16181C),
    surfaceContainerHigh = Color(0xFF1B1E22),
    surfaceContainerHighest = Color(0xFF212429),
    outline = Color(0xFF2B2F35),
    outlineVariant = Color(0xFF22252A),
    inverseSurface = Color(0xFFEDEEF0),
    inverseOnSurface = Color(0xFF121417),
    error = Tone.bad,
    onError = Color.White,
)

private val Light = lightColorScheme(
    primary = Color(0xFF121417),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9EAEC),
    onPrimaryContainer = Color(0xFF121417),
    secondary = Tone.accent,
    onSecondary = Color.White,
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF121417),
    surface = Color(0xFFF7F7F8),
    onSurface = Color(0xFF121417),
    surfaceVariant = Color(0xFFEEEFF1),
    onSurfaceVariant = Color(0xFF61676F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF2F3F5),
    surfaceContainerHighest = Color(0xFFEBECEF),
    outline = Color(0xFFDDDFE3),
    outlineVariant = Color(0xFFE8E9EC),
    inverseSurface = Color(0xFF1B1E22),
    inverseOnSurface = Color(0xFFF2F3F5),
    error = Tone.bad,
    onError = Color.White,
)

private val Numbers = "tnum"

/** Macro colours, used in nutrition rings and donuts. */
object Macro {
    val protein = Tone.accent
    val carbs = Color(0xFFE8A33D)
    val fat = Color(0xFF9B7BEA)
}

// System font on purpose: no font files to download or load on low-end phones.
// Sizes lean large for legibility at arm's length (dashboard numbers, workout counters).
private val Type = Typography(
    displayMedium = TextStyle(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp, fontFeatureSettings = Numbers),
    displaySmall = TextStyle(fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp, fontFeatureSettings = Numbers),
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FitNovaTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shape, content = content)
}
