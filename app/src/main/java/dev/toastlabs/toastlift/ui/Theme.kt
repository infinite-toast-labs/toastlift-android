package dev.toastlabs.toastlift.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.toastlabs.toastlift.R
import dev.toastlabs.toastlift.data.ThemePreference

// ── Font Families ──────────────────────────────────────────────────────────────

internal val DisplayFamily = FontFamily(
    Font(R.font.bebas_neue_regular, FontWeight.Normal),
)

internal val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

// Sans uses system default (matches Inter / Roboto on most devices)
internal val SansFamily = FontFamily.Default

// ── Spacing ────────────────────────────────────────────────────────────────────

@Immutable
object Spacing {
    val screenPadding: Dp = 20.dp
    val sectionGap: Dp = 24.dp
    val itemGap: Dp = 12.dp
    val cardPadding: Dp = 20.dp
    val heroPadding: Dp = 28.dp
    val minTouchTarget: Dp = 48.dp
    val bottomBarHeight: Dp = 72.dp
    val bottomBarMargin: Dp = 16.dp
}

internal val LocalSpacing = staticCompositionLocalOf { Spacing }

// ── Gradient Tokens ────────────────────────────────────────────────────────────

@Immutable
data class Gradients(
    val heroGradient: Brush,
    val sessionGradient: Brush,
)

internal val LocalGradients = staticCompositionLocalOf<Gradients> {
    error("Gradients not provided")
}

private val ToastLiftDarkGradients = Gradients(
    heroGradient = Brush.linearGradient(listOf(Color(0xFF0A0A0B), Color(0xFF1A1A0E))),
    sessionGradient = Brush.linearGradient(listOf(Color(0xFF1C1C1F), Color(0xFF0A0A0B))),
)

private val ToastLiftLightGradients = Gradients(
    heroGradient = Brush.linearGradient(listOf(Color(0xFFFAF8F5), Color(0xFFEFEAF6))),
    sessionGradient = Brush.linearGradient(listOf(Color(0xFFF2F0F5), Color(0xFFFAF8F5))),
)

// ── Status Accent Colors ───────────────────────────────────────────────────────
// Ember, Heat, Momentum, Surge, Amethyst

@Immutable
data class StatusColors(
    val ember: Color = Color(0xFFFF3D3D),
    val heat: Color = Color(0xFFFF7A1A),
    val momentum: Color = Color(0xFFFFC940),
    val surge: Color = Color(0xFF3DFFA0),
    val amethyst: Color = Color(0xFF3D9FFF),
)

internal val LocalStatusColors = staticCompositionLocalOf { StatusColors() }
private val ToastLiftDarkStatusColors = StatusColors()
private val ToastLiftLightStatusColors = StatusColors(
    ember = Color(0xFFBA3E47),
    heat = Color(0xFFA24F68),
    momentum = Color(0xFF695A91),
    surge = Color(0xFF4E5C9D),
    amethyst = Color(0xFF3E63A8),
)

// ── Shared UI Recipe Colors ───────────────────────────────────────────────────

@Immutable
data class UiColors(
    val surfaceHigh: Color = Color(0xFF1C1C1F),
    val surfaceMax: Color = Color(0xFF242428),
    val inkOnLightSurface: Color = Color(0xFF10131A),
    val chromeDivider: Color = Color(0xFF2A2A2E),
    val chromeBorder: Color = Color(0xFF2A2A2E),
    val inactiveNavigation: Color = Color(0xFF666666),
    val progressTrack: Color = Color(0xFF333333),
    val highlight: Color = Color(0xFFFFFFFF),
    val workoutDayFill: Color = Color(0xFF2A9D8F),
    val rirEasy: Color = Color(0xFFFFF1A6),
    val rirChallenge: Color = Color(0xFFFF4A6A),
    val rirHard: Color = Color(0xFFE61E53),
    val rirMax: Color = Color(0xFFB70F38),
    val rirOnEasy: Color = Color(0xFF29221A),
    val rirOnIntense: Color = Color(0xFFFFFFFF),
    val recommendationMoreOften: Color = Color(0xFF86E3B4),
    val recommendationLessOften: Color = Color(0xFFFFB38A),
    val systemBar: Color = Color.Transparent,
)

internal val LocalUiColors = staticCompositionLocalOf { UiColors() }
private val ToastLiftDarkUiColors = UiColors(
    surfaceHigh = Color(0xFF1C1C1F),
    surfaceMax = Color(0xFF242428),
)
private val ToastLiftLightUiColors = UiColors(
    surfaceHigh = Color(0xFFF2F0F5),
    surfaceMax = Color(0xFFE9E6EE),
    inkOnLightSurface = Color(0xFF18171D),
    chromeDivider = Color(0xFFCBC7D2),
    chromeBorder = Color(0xFFBBB5C5),
    inactiveNavigation = Color(0xFF716D7B),
    progressTrack = Color(0xFFDDD8E3),
    workoutDayFill = Color(0xFF4E5C9D),
    recommendationMoreOften = Color(0xFF4E5C9D),
    recommendationLessOften = Color(0xFFA24F68),
)

// ── Dark Theme ─────────────────────────────────────────────────────────────────

internal val LocalToastLiftIsDarkTheme = staticCompositionLocalOf { false }

private val ToastLiftDarkColors = darkColorScheme(
    primary = Color(0xFFE8FF47),
    onPrimary = Color(0xFF0A0A0B),
    primaryContainer = Color(0xFF1A1C0E),
    onPrimaryContainer = Color(0xFFE8FF47),
    secondary = Color(0xFF3DFFA0),
    onSecondary = Color(0xFF0A1A0F),
    secondaryContainer = Color(0xFF0F2418),
    onSecondaryContainer = Color(0xFF3DFFA0),
    tertiary = Color(0xFFFFC940),
    onTertiary = Color(0xFF1A1400),
    tertiaryContainer = Color(0xFF2A2008),
    onTertiaryContainer = Color(0xFFFFC940),
    background = Color(0xFF0A0A0B),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF141416),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF1C1C1F),
    onSurfaceVariant = Color(0xFF9A9AA0),
    outline = Color(0xFF2A2A2E),
    outlineVariant = Color(0xFF383E46),
    error = Color(0xFFFF3D3D),
    onError = Color(0xFF1A0505),
    errorContainer = Color(0xFF640E10),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ── Light Theme ────────────────────────────────────────────────────────────────

private val ToastLiftLightColors = lightColorScheme(
    primary = Color(0xFF4058A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E7FF),
    onPrimaryContainer = Color(0xFF141B4D),
    secondary = Color(0xFF7C4D63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1E1E8),
    onSecondaryContainer = Color(0xFF341423),
    tertiary = Color(0xFF695A91),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEAE6F4),
    onTertiaryContainer = Color(0xFF251D3E),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1C1A20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1A20),
    surfaceVariant = Color(0xFFF2F0F5),
    onSurfaceVariant = Color(0xFF625D6B),
    outline = Color(0xFFD8D3DE),
    outlineVariant = Color(0xFFC6C0CE),
    error = Color(0xFFB93434),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE1DD),
    onErrorContainer = Color(0xFF421012),
)

// ── Typography ─────────────────────────────────────────────────────────────────

internal val HeroTextStyle = TextStyle(
    fontFamily = DisplayFamily,
    fontSize = 64.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.02.em,
)

private val ToastLiftTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontSize = 56.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.02.em,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontSize = 40.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.02.em,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.02.em,
    ),
    headlineLarge = TextStyle(
        fontFamily = SansFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontFamily = SansFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = TextStyle(
        fontFamily = SansFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontFamily = SansFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = SansFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.em,
    ),
    bodyLarge = TextStyle(
        fontFamily = SansFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = SansFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.em,
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 9.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.em,
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 9.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.em,
    ),
)

// ── Theme Composable ───────────────────────────────────────────────────────────

@Composable
fun ToastLiftTheme(
    themePreference: ThemePreference,
    content: @Composable () -> Unit,
) {
    val isDarkTheme = when (themePreference) {
        ThemePreference.Dark -> true
        ThemePreference.Light -> false
        ThemePreference.System -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            val systemBarColor = if (isDarkTheme) ToastLiftDarkUiColors.systemBar else ToastLiftLightUiColors.systemBar
            window.statusBarColor = systemBarColor.toArgb()
            window.navigationBarColor = systemBarColor.toArgb()
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
    CompositionLocalProvider(
        LocalToastLiftIsDarkTheme provides isDarkTheme,
        LocalStatusColors provides if (isDarkTheme) ToastLiftDarkStatusColors else ToastLiftLightStatusColors,
        LocalUiColors provides if (isDarkTheme) ToastLiftDarkUiColors else ToastLiftLightUiColors,
        LocalSpacing provides Spacing,
        LocalGradients provides if (isDarkTheme) ToastLiftDarkGradients else ToastLiftLightGradients,
    ) {
        MaterialTheme(
            colorScheme = if (isDarkTheme) ToastLiftDarkColors else ToastLiftLightColors,
            typography = ToastLiftTypography,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
