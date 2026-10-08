package com.coreplayer.app.ui

import com.coreplayer.app.ui.util.cleanArtistName
import com.coreplayer.app.ui.util.isFio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistUtilsTest {

    @Test
    fun testIsFioIdentification() {
        assertTrue(isFio("Серков Марк Робертович"))
        assertTrue(isFio("Ицков Дмитрий Олегович"))
        assertTrue(isFio("Марк Робертович"))
        assertTrue(isFio("Иванова Анна Сергеевна"))

        assertFalse(isFio("madk1d"))
        assertFalse(isFio("Kai Angel"))
        assertFalse(isFio("9mice"))
        assertFalse(isFio("erikkaspi"))
        assertFalse(isFio("Pixies"))
        assertFalse(isFio("Sematary"))
    }

    @Test
    fun testCleanArtistNameWithBullet() {
        val raw = "madk1d • Серков Марк Робертович"
        assertEquals("madk1d", cleanArtistName(raw))
    }

    @Test
    fun testCleanArtistNameWithComma() {
        val raw = "erikkaspi, elmora, Ицков Дмитрий Олегович"
        assertEquals("erikkaspi, elmora", cleanArtistName(raw))
    }

    @Test
    fun testCleanArtistNameSoleFio() {
        assertEquals("", cleanArtistName("Серков Марк Робертович"))
        assertEquals("", cleanArtistName("Ицков Дмитрий Олегович"))
    }

    @Test
    fun testCleanArtistNameStandardStageNames() {
        assertEquals("Kai Angel, 9mice", cleanArtistName("Kai Angel, 9mice"))
        assertEquals("fallen777angel", cleanArtistName("fallen777angel"))
        assertEquals("Pixies", cleanArtistName("Pixies"))
    }
}
