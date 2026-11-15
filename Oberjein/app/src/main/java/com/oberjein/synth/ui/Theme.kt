package com.oberjein.synth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val Chassis = Color(0xFF101113)
val Panel = Color(0xFF1A1C1F)
val Walnut = Color(0xFF70462D)
val WalnutDark = Color(0xFF321F16)
val ColorWood = Color(0xFF553522)
val Cream = Color(0xFFE9E6DF)
val Dim = Color(0xFF92969B)
val Track = Color(0xFF34383D)
val Ivory = Color(0xFFF2EFE6)
val Ebony = Color(0xFF151619)
val ElectricBlue = Color(0xFF249BFF)
val LedRed = Color(0xFFFF343B)
val Amber = Color(0xFFE8A33D)
val Ember = Color(0xFFE0603A)
val Teal = Color(0xFF4FB5A5)
val Iris = Color(0xFF9A96F0)
val LocalTint = compositionLocalOf { ElectricBlue }

@Composable
fun OberjeinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = ElectricBlue, background = Chassis,
        surface = Panel, onSurface = Cream, onBackground = Cream), content = content)
}
