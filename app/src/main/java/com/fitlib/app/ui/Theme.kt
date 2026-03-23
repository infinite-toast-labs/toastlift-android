package com.fitlib.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.fitlib.app.data.ThemePreference

internal val LocalFitLibIsDarkTheme = staticCompositionLocalOf { false }

private val FitLibDarkColors = darkColorScheme(
    primary = Color(0xFFFF5A7D),
    onPrimary = Color(0xFFFFF8FA),
    primaryContainer = Color(0xFF442A35),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFF7DE7D4),
    onSecondary = Color(0xFF072A27),
    secondaryContainer = Color(0xFF173836),
    onSecondaryContainer = Color(0xFFB8FFF1),
    tertiary = Color(0xFFFFC857),
    onTertiary = Color(0xFF302100),
    tertiaryContainer = Color(0xFF4A3920),
    onTertiaryContainer = Color(0xFFFFE7B2),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFF2F3F7),
    surface = Color(0xFF181C24),
    onSurface = Color(0xFFF2F3F7),
    surfaceVariant = Color(0xFF2B313D),
    onSurfaceVariant = Color(0xFFB7C0CF),
    outline = Color(0xFF6A7280),
    error = Color(0xFFFFB4B9),
    onError = Color(0xFF3F030D),
)

private val FitLibLightColors = lightColorScheme(
    primary = Color(0xFFC52C57),
    onPrimary = Color(0xFFFFFBFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F0018),
    secondary = Color(0xFF006B60),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF8FF4E1),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF805600),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF281800),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF171B24),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF171B24),
    surfaceVariant = Color(0xFFE0E5EF),
    onSurfaceVariant = Color(0xFF434A58),
    outline = Color(0xFF747B89),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun FitLibTheme(
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
    CompositionLocalProvider(LocalFitLibIsDarkTheme provides isDarkTheme) {
        MaterialTheme(
            colorScheme = if (isDarkTheme) FitLibDarkColors else FitLibLightColors,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
