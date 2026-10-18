package com.mirrorcast.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Raw Color Definitions
val RawLightBackground = Color(0xFFF8FAFC)
val RawLightSurface = Color(0xFFFFFFFF)
val RawLightCardBorder = Color(0xFFE2E8F0)
val RawLightTextPrimary = Color(0xFF0F172A)
val RawLightTextSecondary = Color(0xFF64748B)
val RawLightBlueLight = Color(0xFFEFF6FF)
val RawLightSegmentBg = Color(0xFFE2E8F0)

// Raw Dark Definitions (Matching Windows receiver's deep sleek navy)
val RawDarkBackground = Color(0xFF0A1322)
val RawDarkSurface = Color(0xFF131D31)
val RawDarkCardBorder = Color(0xFF1E293B)
val RawDarkTextPrimary = Color(0xFFF8FAFC)
val RawDarkTextSecondary = Color(0xFF94A3B8)
val RawDarkBlueLight = Color(0xFF172554)
val RawDarkSegmentBg = Color(0xFF1E293B)

// Shared Branding
val PrimaryBlue = Color(0xFF1B6EF3)
val ActiveGreen = Color(0xFF16A34A)
val ActiveGreenLight = Color(0xFFDCFCE7)
val StopRed = Color(0xFFEF4444)
val StopRedLight = Color(0xFFFEE2E2)

// Dynamic Composable Theme-Aware Accessors
val LightBackground: Color
    @Composable
    get() = AppTheme.colors.background

val LightSurface: Color
    @Composable
    get() = AppTheme.colors.surface

val LightCardBorder: Color
    @Composable
    get() = AppTheme.colors.cardBorder

val TextSlatePrimary: Color
    @Composable
    get() = AppTheme.colors.textPrimary

val TextSlateSecondary: Color
    @Composable
    get() = AppTheme.colors.textSecondary

val PrimaryBlueLight: Color
    @Composable
    get() = AppTheme.colors.primaryContainer

val DarkBackground: Color
    @Composable
    get() = AppTheme.colors.background

val DarkSurface: Color
    @Composable
    get() = AppTheme.colors.surface

val DarkCardBorder: Color
    @Composable
    get() = AppTheme.colors.cardBorder

val AccentBlue = PrimaryBlue
val DangerRed = StopRed

val TextPrimary: Color
    @Composable
    get() = AppTheme.colors.textPrimary

val TextSecondary: Color
    @Composable
    get() = AppTheme.colors.textSecondary

data class MirrorCastColors(
    val background: Color,
    val surface: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val primaryContainer: Color,
    val segmentBg: Color,
    val isDark: Boolean
)

val LocalMirrorCastColors = staticCompositionLocalOf {
    MirrorCastColors(
        background = RawLightBackground,
        surface = RawLightSurface,
        cardBorder = RawLightCardBorder,
        textPrimary = RawLightTextPrimary,
        textSecondary = RawLightTextSecondary,
        primary = PrimaryBlue,
        primaryContainer = RawLightBlueLight,
        segmentBg = RawLightSegmentBg,
        isDark = false
    )
}
