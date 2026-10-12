package com.coreplayer.app.ui.util

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure calculation function for grid columns based on available screen width.
 * Guarantees at least [minColumns] (default 2) for phones of all sizes,
 * and scales dynamically to 3..7+ columns on larger screens and tablets.
 */
fun calculateGridColumns(
    screenWidthDp: Int,
    minCardWidthDp: Int = 150,
    horizontalPaddingDp: Int = 40,
    spacingDp: Int = 16,
    minColumns: Int = 2
): Int {
    val availableWidth = (screenWidthDp - horizontalPaddingDp).coerceAtLeast(0)
    val calculated = (availableWidth + spacingDp) / (minCardWidthDp + spacingDp)
    return calculated.coerceAtLeast(minColumns)
}

/**
 * Composable returning [GridCells.Fixed] with adaptive column count.
 * Guarantees exactly 2 columns on phones, scaling to 3+ on tablets.
 */
@Composable
fun responsiveGridColumns(
    minCardWidth: Dp = 150.dp,
    horizontalPadding: Dp = 40.dp,
    spacing: Dp = 16.dp,
    minColumns: Int = 2
): GridCells {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val columns = calculateGridColumns(
        screenWidthDp = screenWidthDp,
        minCardWidthDp = minCardWidth.value.toInt(),
        horizontalPaddingDp = horizontalPadding.value.toInt(),
        spacingDp = spacing.value.toInt(),
        minColumns = minColumns
    )
    return GridCells.Fixed(columns)
}
