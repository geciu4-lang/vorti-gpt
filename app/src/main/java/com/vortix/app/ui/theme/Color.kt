package com.vortix.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Dark Frosted Glass palette, adapted from the supplied VORTIX design source. */
object VortixGlassColors {
    val Obsidian = Color(0xFF090B10)
    val GlassFill = Color(0xFF161A23).copy(alpha = 0.62f)
    val DockFill = Color(0xFF161A23).copy(alpha = 0.86f)
    val InnerFill = Color.White.copy(alpha = 0.045f)

    val Coral = Color(0xFFFF5A5F)
    val Emerald = Color(0xFF00D285)
    val EmeraldSoft = Color(0xFF6DF0BD)
    val Amber = Color(0xFFFFB454)

    val TextPrimary = Color(0xFFF5F7FA)
    val TextMuted = Color(0xFF9AA4B2)
    val SpecularBright = Color.White.copy(alpha = 0.45f)
    val SpecularDim = Color.White.copy(alpha = 0.04f)
}

val VortixBackground = VortixGlassColors.Obsidian
val VortixBackdropMid = Color(0xFF10141D)
val VortixSurface = Color(0xFF161A23)
val VortixSurfaceVariant = Color(0xFF20242D)
val VortixSurfaceElevated = Color(0xFF252A34)

// Frosted surfaces use translucent charcoal fills and a specular edge gradient.
val GlassSurfaceTop = Color(0x581E2638)
val GlassSurfaceBottom = Color(0x40121724)
val GlassBorderHighlight = VortixGlassColors.SpecularBright
val GlassBorderSubtle = VortixGlassColors.SpecularDim
val GlassPillActive = VortixGlassColors.Emerald.copy(alpha = 0.22f)

// Accent and status colors retain the app's public token names for existing screens.
val VortixPrimary = VortixGlassColors.Emerald
val VortixSecondary = Color(0xFF47C5FB)
val VortixTertiary = VortixGlassColors.Amber
val VortixError = VortixGlassColors.Coral
val VortixOnBackground = VortixGlassColors.TextPrimary
val VortixOnSurfaceVariant = VortixGlassColors.TextMuted
val VortixOutline = Color(0xFF303743)
