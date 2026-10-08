package com.coreplayer.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalAmoledMode = staticCompositionLocalOf { false }

val CorePlayerColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SubtleDivider
)

val AmoledColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF101010),
    onPrimaryContainer = TextPrimary,
    background = Color(0xFF000000),
    onBackground = TextPrimary,
    surface = Color(0xFF000000),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF101010),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF222222)
)

val CorePlayerShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CorePlayerTheme(
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (amoled) AmoledColorScheme else CorePlayerColorScheme
    CompositionLocalProvider(LocalAmoledMode provides amoled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CorePlayerTypography,
            shapes = CorePlayerShapes,
            content = content
        )
    }
}
