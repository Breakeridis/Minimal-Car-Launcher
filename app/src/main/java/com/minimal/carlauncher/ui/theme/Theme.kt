package com.minimal.carlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = CarBg,
    primaryContainer = CarSurfaceVariant,
    onPrimaryContainer = TextPrimary,
    secondary = AccentGreen,
    onSecondary = CarBg,
    tertiary = AccentAmber,
    background = CarBg,
    onBackground = TextPrimary,
    surface = CarSurface,
    onSurface = TextPrimary,
    surfaceVariant = CarSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CarBorder
)

@Composable
fun CarLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
