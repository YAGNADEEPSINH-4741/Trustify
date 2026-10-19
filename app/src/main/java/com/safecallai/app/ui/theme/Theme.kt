package com.safecallai.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.safecallai.app.data.local.PreferencesManager

// ============================================================================
// QUIET PREMIUM — MATERIAL 3 COLOR SCHEMES
// ============================================================================

val DarkColorScheme = darkColorScheme(
    primary = DarkAccentMint,
    onPrimary = DarkBackground,
    primaryContainer = DarkAccentSubtleSurface,
    onPrimaryContainer = DarkAccentMint,
    secondary = DarkInfo,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = DarkInfo,
    tertiary = DarkWarning,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurfacePrimary,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceSecondary,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = DarkError,
    onError = Color.White,
    errorContainer = HighRiskRedContainer,
    onErrorContainer = DarkError
)

val LightColorScheme = lightColorScheme(
    primary = LightAccentMint,
    onPrimary = Color.White,
    primaryContainer = LightAccentSubtleSurface,
    onPrimaryContainer = LightAccentMint,
    secondary = LightInfo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF4FF),
    onSecondaryContainer = LightInfo,
    tertiary = LightWarning,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurfacePrimary,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceSecondary,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
    error = LightError,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBE9),
    onErrorContainer = LightError
)

@Composable
fun TrustifyTheme(
    themeMode: PreferencesManager.ThemeMode = PreferencesManager.ThemeMode.SYSTEM_DEFAULT,
    darkTheme: Boolean = when (themeMode) {
        PreferencesManager.ThemeMode.LIGHT -> false
        PreferencesManager.ThemeMode.DARK -> true
        PreferencesManager.ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrustifyTypography,
        content = content
    )
}

/**
 * Backward compatibility alias for SafeCallAITheme.
 */
@Composable
fun SafeCallAITheme(
    themeMode: PreferencesManager.ThemeMode = PreferencesManager.ThemeMode.SYSTEM_DEFAULT,
    darkTheme: Boolean = when (themeMode) {
        PreferencesManager.ThemeMode.LIGHT -> false
        PreferencesManager.ThemeMode.DARK -> true
        PreferencesManager.ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    TrustifyTheme(themeMode = themeMode, darkTheme = darkTheme, content = content)
}
