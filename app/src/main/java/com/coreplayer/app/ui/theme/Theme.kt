package com.coreplayer.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

val CorePlayerColorScheme = darkColorScheme(
    primary = AccentOrange,
    onPrimary = TextPrimary,
    primaryContainer = AccentOrangeVibrant,
    onPrimaryContainer = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SubtleDivider
)

val CorePlayerShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CorePlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CorePlayerColorScheme,
        typography = CorePlayerTypography,
        shapes = CorePlayerShapes,
        content = content
    )
}
