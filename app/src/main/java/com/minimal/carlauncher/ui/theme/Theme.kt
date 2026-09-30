package com.minimal.carlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private fun createColorScheme(colors: CarColors, isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = colors.accentCyan,
        onPrimary = colors.bg,
        primaryContainer = colors.surfaceVariant,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.accentGreen,
        onSecondary = colors.bg,
        tertiary = colors.accentAmber,
        background = colors.bg,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.border
    )
} else {
    lightColorScheme(
        primary = colors.accentCyan,
        onPrimary = Color.White,
        primaryContainer = colors.surfaceVariant,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.accentGreen,
        onSecondary = Color.White,
        tertiary = colors.accentAmber,
        background = colors.bg,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.border
    )
}

@Composable
fun CarLauncherTheme(
    isDarkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (isDarkMode) DarkCarColors else LightCarColors
    val colorScheme = createColorScheme(colors, isDarkMode)

    CompositionLocalProvider(
        LocalCarColors provides colors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
