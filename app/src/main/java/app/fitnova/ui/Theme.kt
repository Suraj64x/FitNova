package app.fitnova.ui

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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitnova.R
import app.fitnova.ThemeMode
import app.fitnova.domain.Category

// "Nova Indigo": cool graphite neutrals plus one indigo brand. Every pair is WCAG-checked (research/design.md).
// Flat by design: surfaceTint = surface, so tonal elevation adds no tint.

private val Light = lightColorScheme(
    primary = Color(0xFF534EF5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3E8FF),
    onPrimaryContainer = Color(0xFF2C2693),
    inversePrimary = Color(0xFFA4B1FF),
    secondary = Color(0xFF484D5B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7E9F0),
    onSecondaryContainer = Color(0xFF15171D),
    tertiary = Color(0xFFC63D00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE5DC),
    onTertiaryContainer = Color(0xFF7E2401),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF15171D),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF15171D),
    surfaceVariant = Color(0xFFEDEEF3),
    onSurfaceVariant = Color(0xFF595C65),
    surfaceTint = Color(0xFFF5F6F8),
    inverseSurface = Color(0xFF212329),
    inverseOnSurface = Color(0xFFF2F3F6),
    error = Color(0xFFC7042C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE6E5),
    onErrorContainer = Color(0xFF861118),
    outline = Color(0xFF82848C),
    outlineVariant = Color(0xFFE0E2E6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDEDFE4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE5E7EC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7E8BFF),
    onPrimary = Color(0xFF121243),
    primaryContainer = Color(0xFF282C6C),
    onPrimaryContainer = Color(0xFFD5DCFF),
    inversePrimary = Color(0xFF534EF5),
    secondary = Color(0xFFB9BDCB),
    onSecondary = Color(0xFF191A1F),
    secondaryContainer = Color(0xFF25272E),
    onSecondaryContainer = Color(0xFFEFF0F3),
    tertiary = Color(0xFFF28058),
    onTertiary = Color(0xFF3C1304),
    tertiaryContainer = Color(0xFF572412),
    onTertiaryContainer = Color(0xFFFDD4C6),
    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFEFF0F3),
    surface = Color(0xFF0B0C0F),
    onSurface = Color(0xFFEFF0F3),
    surfaceVariant = Color(0xFF202127),
    onSurfaceVariant = Color(0xFFA7AAB4),
    surfaceTint = Color(0xFF0B0C0F),
    inverseSurface = Color(0xFFE6E8EB),
    inverseOnSurface = Color(0xFF191A1F),
    error = Color(0xFFF96F70),
    onError = Color(0xFF3D090D),
    errorContainer = Color(0xFF581B1D),
    onErrorContainer = Color(0xFFFED2CF),
    outline = Color(0xFF71747D),
    outlineVariant = Color(0xFF292B30),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2B2C32),
    surfaceDim = Color(0xFF0B0C0F),
    surfaceContainerLowest = Color(0xFF070709),
    surfaceContainerLow = Color(0xFF121316),
    surfaceContainer = Color(0xFF16171B),
    surfaceContainerHigh = Color(0xFF1C1E22),
    surfaceContainerHighest = Color(0xFF24262B),
)

/**
 * Theme-aware status and data colours. Data colours never colour text: use them for marks, 6-8 dp dots
 * and 14 % tints behind onSurface text. good/warn/bad are text-grade inks (>= 4.5:1, also on their own tint).
 */
@Immutable
data class NovaColors(
    val good: Color, val warn: Color, val bad: Color,
    val points: Color, val minutes: Color, val water: Color, val sleep: Color,
    val protein: Color, val carbs: Color, val fat: Color,
    val upper: Color, val lower: Color, val core: Color, val cardio: Color,
    val track: Color, // neutral track for every arc and meter, and empty bars
)

val LightNova = NovaColors(
    good = Color(0xFF00773B), warn = Color(0xFF8F5A00), bad = Color(0xFFC7042C),
    points = Color(0xFF534EF5), minutes = Color(0xFFE75700), water = Color(0xFF0093BF), sleep = Color(0xFF955BE3),
    protein = Color(0xFFCB2865), carbs = Color(0xFFB88000), fat = Color(0xFF955BE3),
    upper = Color(0xFF955BE3), lower = Color(0xFFB88000), core = Color(0xFF0093BF), cardio = Color(0xFFCB2865),
    track = Color(0xFFF2F3F5),
)

val DarkNova = NovaColors(
    good = Color(0xFF4BD27C), warn = Color(0xFFF9B13E), bad = Color(0xFFF96F70),
    points = Color(0xFF6368FF), minutes = Color(0xFFE76838), water = Color(0xFF00A7B4), sleep = Color(0xFFA86FF9),
    protein = Color(0xFFD12C80), carbs = Color(0xFFC48800), fat = Color(0xFFA86FF9),
    upper = Color(0xFFA86FF9), lower = Color(0xFFC48800), core = Color(0xFF00A7B4), cardio = Color(0xFFD12C80),
    track = Color(0xFF27282C),
)

val LocalNova = staticCompositionLocalOf { LightNova }

/** True while the dark scheme is active. The theme is picked in-app, so use this instead of drawable-night. */
val LocalDark = staticCompositionLocalOf { false }

object Nova {
    /** Read once before Canvas/DrawScope lambdas: `val c = Nova.colors`. */
    val colors: NovaColors
        @Composable @ReadOnlyComposable get() = LocalNova.current

    /** Fixed brand hex for launcher, splash and share images. In UI use colorScheme.primary. */
    val Brand = Color(0xFF534EF5)

    // Camera overlay sits on live video, so it is theme-independent. Text on any Live fill = OnLive.
    val LiveGood = Color(0xFF2FD47A)
    val LiveWarn = Color(0xFFFFC53D)
    val LiveIdle = Color(0xFFA3A9B3)
    val OnLive = Color(0xFF0B0D10)
    val Bone = Color(0xE6FFFFFF)

    // Medals: marks and tints only; text on them stays onSurface.
    val Gold = Color(0xFFB88C19)
    val Silver = Color(0xFF7C8691)
    val Bronze = Color(0xFFB26D3A)
}

/** Category colour: 💪 violet, 🦵 gold, 🧘 cyan, 🏃 raspberry. */
fun NovaColors.of(category: Category): Color = when (category) {
    Category.UPPER -> upper
    Category.LOWER -> lower
    Category.CORE -> core
    Category.CARDIO -> cardio
}

/** 1.4 accent and status colours, fixed to the light values. Not theme-aware. */
@Deprecated("Use MaterialTheme.colorScheme.primary or Nova.colors, which follow the theme.")
object Tone {
    val accent = Color(0xFF534EF5)
    val good = Color(0xFF00773B)
    val warn = Color(0xFF8F5A00)
    val bad = Color(0xFFC7042C)
}

/** 1.4 macro colours, fixed to the light values. Not theme-aware. */
@Deprecated("Use Nova.colors.protein/carbs/fat, which follow the theme.")
object Macro {
    val protein = Color(0xFFCB2865)
    val carbs = Color(0xFFB88000)
    val fat = Color(0xFF955BE3)
}

// The variable font's default instance is ExtraLight, so every Font pins its weight axis explicitly.
private fun manrope(weight: FontWeight) =
    Font(R.font.manrope, weight, variationSettings = FontVariation.Settings(weight, FontStyle.Normal))

val Manrope = FontFamily(
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, tnum: Boolean = false) = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tnum) "tnum" else null,
)

// tnum on numeric roles only, so live counters and timers never jitter as digits change.
private val Type = Typography(
    displayLarge = style(96, 96, FontWeight.ExtraBold, -3.0, tnum = true),
    displayMedium = style(64, 68, FontWeight.ExtraBold, -2.0, tnum = true),
    displaySmall = style(48, 52, FontWeight.ExtraBold, -1.4, tnum = true),
    headlineLarge = style(36, 42, FontWeight.Bold, -0.9, tnum = true),
    headlineMedium = style(30, 36, FontWeight.Bold, -0.6),
    headlineSmall = style(24, 30, FontWeight.Bold, -0.3, tnum = true),
    titleLarge = style(20, 26, FontWeight.Bold, -0.2),
    titleMedium = style(17, 22, FontWeight.SemiBold, -0.1),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Medium),
    bodyMedium = style(15, 22, FontWeight.Medium),
    bodySmall = style(13, 18, FontWeight.Medium, 0.1),
    labelLarge = style(15, 20, FontWeight.SemiBold, 0.1, tnum = true),
    labelMedium = style(13, 16, FontWeight.SemiBold, 0.2, tnum = true),
    labelSmall = style(12, 16, FontWeight.SemiBold, 0.3),
)

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Workout mode (Session, Rest, Summary, alarm Ring) always passes dark = true. */
@Composable
fun FitNovaTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNova provides if (dark) DarkNova else LightNova, LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shape, content = content)
    }
}
