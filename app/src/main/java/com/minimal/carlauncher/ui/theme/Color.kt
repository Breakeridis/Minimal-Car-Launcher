package com.minimal.carlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CarColors(
    val bg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentCyan: Color,
    val accentAmber: Color,
    val accentGreen: Color,
    val accentZLink: Color,
    val accentBlue: Color,
    val accentRed: Color,
    val isDark: Boolean
)

// Luxury automotive obsidian backdrop with deep velvet undertone (Night Mode)
val DarkCarColors = CarColors(
    bg = Color(0xFF0A0E17),
    surface = Color(0xFF131926),
    surfaceVariant = Color(0xFF1C2436),
    border = Color(0xFF263248),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    accentCyan = Color(0xFF00D2EE),
    accentAmber = Color(0xFFF59E0B),
    accentGreen = Color(0xFF10B981),
    accentZLink = Color(0xFF10B981),
    accentBlue = Color(0xFF38BDF8),
    accentRed = Color(0xFFF43F5E),
    isDark = true
)

// Bright, high-contrast, non-boring automotive daylight palette (Day Mode)
val LightCarColors = CarColors(
    bg = Color(0xFFF1F5F9),             // Crisp ice-pearl ambient backdrop
    surface = Color(0xFFFFFFFF),        // Brilliant white porcelain instrument cards
    surfaceVariant = Color(0xFFE2E8F0), // Slate 200 elevated chips, search fields, containers
    border = Color(0xFFCBD5E1),         // Slate 300 razor-sharp structural borders
    textPrimary = Color(0xFF0F172A),    // Slate 900 maximum optical contrast in direct sunlight
    textSecondary = Color(0xFF475569),  // Slate 600 secondary information
    textMuted = Color(0xFF64748B),      // Slate 500 tertiary labels
    accentCyan = Color(0xFF0284C7),     // Vivid electric azure / sky 600 (rich contrast on white)
    accentAmber = Color(0xFFD97706),    // Rich warm amber-gold 600
    accentGreen = Color(0xFF059669),    // Deep emerald racing green 600
    accentZLink = Color(0xFF059669),    // Crisp CarPlay / Android Auto green
    accentBlue = Color(0xFF2563EB),     // Striking cobalt sapphire for navigation
    accentRed = Color(0xFFE11D48),      // Crimson ruby for recordings and warnings
    isDark = false
)

val LocalCarColors = staticCompositionLocalOf { DarkCarColors }

// Dynamic Composable Color Accessors:
// Seamlessly delegates to LocalCarColors.current so all existing composables adapt dynamically
val CarBg: Color
    @Composable
    get() = LocalCarColors.current.bg

val CarSurface: Color
    @Composable
    get() = LocalCarColors.current.surface

val CarSurfaceVariant: Color
    @Composable
    get() = LocalCarColors.current.surfaceVariant

val CarBorder: Color
    @Composable
    get() = LocalCarColors.current.border

val AccentCyan: Color
    @Composable
    get() = LocalCarColors.current.accentCyan

val AccentAmber: Color
    @Composable
    get() = LocalCarColors.current.accentAmber

val AccentGreen: Color
    @Composable
    get() = LocalCarColors.current.accentGreen

val AccentZLink: Color
    @Composable
    get() = LocalCarColors.current.accentZLink

val AccentBlue: Color
    @Composable
    get() = LocalCarColors.current.accentBlue

val AccentRed: Color
    @Composable
    get() = LocalCarColors.current.accentRed

val TextPrimary: Color
    @Composable
    get() = LocalCarColors.current.textPrimary

val TextSecondary: Color
    @Composable
    get() = LocalCarColors.current.textSecondary

val TextMuted: Color
    @Composable
    get() = LocalCarColors.current.textMuted
