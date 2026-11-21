package com.vortix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VortixDarkColorScheme = darkColorScheme(
    primary = VortixGlassColors.Emerald,
    onPrimary = VortixGlassColors.Obsidian,
    secondary = VortixGlassColors.Amber,
    onSecondary = VortixGlassColors.Obsidian,
    tertiary = VortixGlassColors.Coral,
    onTertiary = VortixGlassColors.Obsidian,
    error = VortixGlassColors.Coral,
    background = VortixGlassColors.Obsidian,
    onBackground = VortixGlassColors.TextPrimary,
    surface = VortixSurface,
    onSurface = VortixGlassColors.TextPrimary,
    surfaceVariant = VortixSurfaceVariant,
    onSurfaceVariant = VortixGlassColors.TextMuted,
    outline = VortixOutline
)

@Composable
fun VortixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VortixDarkColorScheme,
        typography = VortixTypography,
        content = content
    )
}
