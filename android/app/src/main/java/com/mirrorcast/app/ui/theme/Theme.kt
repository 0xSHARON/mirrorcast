package com.mirrorcast.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = ActiveGreen,
    background = RawLightBackground,
    surface = RawLightSurface,
    surfaceVariant = Color(0xFFF1F5F9),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = RawLightTextPrimary,
    onSurface = RawLightTextPrimary,
    onSurfaceVariant = RawLightTextSecondary,
    outline = RawLightCardBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = ActiveGreen,
    background = RawDarkBackground,
    surface = RawDarkSurface,
    surfaceVariant = RawDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = RawDarkTextPrimary,
    onSurface = RawDarkTextPrimary,
    onSurfaceVariant = RawDarkTextSecondary,
    outline = RawDarkCardBorder
)

@Composable
fun MirrorCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val mirrorCastColors = if (darkTheme) {
        MirrorCastColors(
            background = RawDarkBackground,
            surface = RawDarkSurface,
            cardBorder = RawDarkCardBorder,
            textPrimary = RawDarkTextPrimary,
            textSecondary = RawDarkTextSecondary,
            primary = PrimaryBlue,
            primaryContainer = RawDarkBlueLight,
            segmentBg = RawDarkSegmentBg,
            isDark = true
        )
    } else {
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = colorScheme.background.toArgb()
                it.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(it, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalMirrorCastColors provides mirrorCastColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

object AppTheme {
    val colors: MirrorCastColors
        @Composable
        get() = LocalMirrorCastColors.current
}
