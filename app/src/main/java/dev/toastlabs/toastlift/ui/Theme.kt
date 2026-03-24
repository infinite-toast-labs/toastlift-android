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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

// ── Status Accent Colors ───────────────────────────────────────────────────────

@Immutable
data class StatusColors(
    val red: Color = Color(0xFFFF3D3D),
    val orange: Color = Color(0xFFFF7A1A),
    val yellow: Color = Color(0xFFFFC940),
    val green: Color = Color(0xFF3DFFA0),
    val blue: Color = Color(0xFF3D9FFF),
)

internal val LocalStatusColors = staticCompositionLocalOf { StatusColors() }
private val ToastLiftDarkStatusColors = StatusColors()
private val ToastLiftLightStatusColors = StatusColors(
    red = Color(0xFFBA1A1A),
    orange = Color(0xFFA75400),
    yellow = Color(0xFF7A5B00),
    green = Color(0xFF006B4C),
    blue = Color(0xFF005DB3),
)

// ── Dark Theme ─────────────────────────────────────────────────────────────────

internal val LocalToastLiftIsDarkTheme = staticCompositionLocalOf { false }

private val ToastLiftDarkColors = darkColorScheme(
    primary = Color(0xFFE8FF47),
    onPrimary = Color(0xFF0E0E0E),
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
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF161616),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF1F2328),
    onSurfaceVariant = Color(0xFFC2C7D0),
    outline = Color(0xFF5B616B),
    outlineVariant = Color(0xFF383E46),
    error = Color(0xFFFF3D3D),
    onError = Color(0xFF1A0505),
    errorContainer = Color(0xFF640E10),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ── Light Theme ────────────────────────────────────────────────────────────────

private val ToastLiftLightColors = lightColorScheme(
    primary = Color(0xFF5E7900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F5C0),
    onPrimaryContainer = Color(0xFF1A2200),
    secondary = Color(0xFF006B4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF8FF4D0),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF805600),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF281800),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE7EAF0),
    onSurfaceVariant = Color(0xFF47505A),
    outline = Color(0xFF717883),
    outlineVariant = Color(0xFFC4C8D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// ── Typography ─────────────────────────────────────────────────────────────────

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
        fontSize = 32.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.02.em,
    ),
    headlineLarge = TextStyle(
        fontFamily = SansFamily,
        fontSize = 24.sp,
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
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
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
        fontSize = 11.sp,
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
        fontSize = 8.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.18.em,
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
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
    CompositionLocalProvider(
        LocalToastLiftIsDarkTheme provides isDarkTheme,
        LocalStatusColors provides if (isDarkTheme) ToastLiftDarkStatusColors else ToastLiftLightStatusColors,
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
