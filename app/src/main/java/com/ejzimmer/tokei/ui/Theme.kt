package com.ejzimmer.tokei.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF1A1A2E)
val Surface = Color(0xFF26264A)
val SurfaceRaised = Color(0xFF23233F)
val Face = Color(0xFFF5F5F0)
val FaceDim = Color(0xFF9D9DB0)
val Accent = Color(0xFFFF6B35)
val Danger = Color(0xFFFF3B30)

private val TokeiColorScheme = darkColorScheme(
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceRaised,
    primary = Accent,
    onPrimary = Background,
    onBackground = Face,
    onSurface = Face,
    error = Danger,
)

// Deliberately not following the system light/dark switch -- always dark,
// matching the web version's fixed palette.
@Composable
fun TokeiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TokeiColorScheme, content = content)
}
