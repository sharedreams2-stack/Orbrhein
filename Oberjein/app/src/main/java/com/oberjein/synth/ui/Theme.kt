package com.oberjein.synth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Black chassis, walnut end cheeks, and colored legend stripes like a late-70s polysynth panel.
val Chassis = Color(0xFF121314)
val Panel = Color(0xFF1C1D20)
val Walnut = Color(0xFF4A3120)
val WalnutDark = Color(0xFF2E1E13)
val Cream = Color(0xFFEDE6D6)
val Dim = Color(0xFF9B9585)
val Track = Color(0xFF34363B)
val Ivory = Color(0xFFF2ECDD)
val Ebony = Color(0xFF16120F)

val Amber = Color(0xFFE8A33D)
val Ember = Color(0xFFE0603A)
val Teal = Color(0xFF4FB5A5)
val Iris = Color(0xFF9A96F0)

/** Accent color of the section a control is in. */
val LocalTint = compositionLocalOf { Amber }

@Composable
fun OberjeinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber, background = Chassis, surface = Panel, onSurface = Cream, onBackground = Cream
        ),
        content = content
    )
}
