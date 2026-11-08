package com.amar.gallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val line: Color,
    val isDark: Boolean
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        bg = DarkBg,
        surface = DarkSurface,
        surface2 = DarkSurface2,
        text = DarkText,
        muted = DarkMuted,
        accent = DarkAccent,
        onAccent = DarkOnAccent,
        line = DarkLine,
        isDark = true
    )
}

@Composable
fun AmarTheme(
    themePreference: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    val appColors = if (isDark) {
        AppColors(
            bg = DarkBg,
            surface = DarkSurface,
            surface2 = DarkSurface2,
            text = DarkText,
            muted = DarkMuted,
            accent = DarkAccent,
            onAccent = DarkOnAccent,
            line = DarkLine,
            isDark = true
        )
    } else {
        AppColors(
            bg = LightBg,
            surface = LightSurface,
            surface2 = LightSurface2,
            text = LightText,
            muted = LightMuted,
            accent = LightAccent,
            onAccent = LightOnAccent,
            line = LightLine,
            isDark = false
        )
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            background = DarkBg,
            surface = DarkSurface,
            surfaceVariant = DarkSurface2,
            primary = DarkAccent,
            onPrimary = DarkOnAccent,
            onBackground = DarkText,
            onSurface = DarkText,
            outline = DarkLine
        )
    } else {
        lightColorScheme(
            background = LightBg,
            surface = LightSurface,
            surfaceVariant = LightSurface2,
            primary = LightAccent,
            onPrimary = LightOnAccent,
            onBackground = LightText,
            onSurface = LightText,
            outline = LightLine
        )
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

