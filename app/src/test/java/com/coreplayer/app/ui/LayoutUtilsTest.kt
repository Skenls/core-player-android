package com.coreplayer.app.ui

import com.coreplayer.app.ui.util.calculateGridColumns
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutUtilsTest {

    @Test
    fun calculateGridColumns_phoneScreens_returnTwoColumns() {
        // Small phones (e.g. Galaxy S10e, iPhone SE)
        assertEquals(2, calculateGridColumns(screenWidthDp = 320))
        assertEquals(2, calculateGridColumns(screenWidthDp = 360))
        assertEquals(2, calculateGridColumns(screenWidthDp = 375))
        assertEquals(2, calculateGridColumns(screenWidthDp = 390))
        assertEquals(2, calculateGridColumns(screenWidthDp = 412))
    }

    @Test
    fun calculateGridColumns_tabletsAndWideScreens_scaleUpwards() {
        // Foldable or small tablet (600dp)
        assertEquals(3, calculateGridColumns(screenWidthDp = 600))

        // Medium tablet portrait (800dp)
        assertEquals(4, calculateGridColumns(screenWidthDp = 800))

        // Large tablet portrait / landscape (1000dp)
        assertEquals(5, calculateGridColumns(screenWidthDp = 1000))

        // Tablet landscape / desktop (1280dp)
        assertEquals(7, calculateGridColumns(screenWidthDp = 1280))
    }
}
