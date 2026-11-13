package com.openclaude.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = AmberDeep,
    onPrimaryContainer = Ink,
    secondary = Mist,
    onSecondary = Ink,
    background = Ink,
    onBackground = Color(0xFFEDF1F7),
    surface = InkSoft,
    onSurface = Color(0xFFEDF1F7),
    surfaceVariant = InkRaised,
    onSurfaceVariant = Mist,
    error = Danger,
    outline = Color(0xFF2B3240)
)

private val LightScheme = lightColorScheme(
    primary = AmberDeep,
    onPrimary = Color.White,
    background = Color(0xFFF6F7FA),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    secondary = MistDim,
    error = Danger
)

@Composable
fun OpenClaudeTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // The app is designed dark first; light is offered for outdoor readability.
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
