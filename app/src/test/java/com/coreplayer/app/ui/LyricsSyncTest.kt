package com.coreplayer.app.ui

import com.coreplayer.app.data.model.LyricLine
import com.coreplayer.app.ui.screens.nowplaying.findActiveLyricIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsSyncTest {

    private val sampleLyrics = listOf(
        LyricLine(timestampMs = 5000L, text = "First line at 5s"),
        LyricLine(timestampMs = 12000L, text = "Second line at 12s"),
        LyricLine(timestampMs = 25000L, text = "Third line at 25s"),
        LyricLine(timestampMs = 40000L, text = "Final line at 40s")
    )

    @Test
    fun `empty lyrics list returns -1`() {
        assertEquals(-1, findActiveLyricIndex(emptyList(), 10000L))
        assertEquals(-1, findActiveLyricIndex(emptyList(), 0L))
    }

    @Test
    fun `position before first lyric timestamp returns -1`() {
        assertEquals(-1, findActiveLyricIndex(sampleLyrics, 0L))
        assertEquals(-1, findActiveLyricIndex(sampleLyrics, 2500L))
        assertEquals(-1, findActiveLyricIndex(sampleLyrics, 4999L))
    }

    @Test
    fun `negative position returns -1`() {
        assertEquals(-1, findActiveLyricIndex(sampleLyrics, -1000L))
    }

    @Test
    fun `exact timestamp match for first line returns 0`() {
        assertEquals(0, findActiveLyricIndex(sampleLyrics, 5000L))
    }

    @Test
    fun `position between first and second lines returns 0`() {
        assertEquals(0, findActiveLyricIndex(sampleLyrics, 5001L))
        assertEquals(0, findActiveLyricIndex(sampleLyrics, 8000L))
        assertEquals(0, findActiveLyricIndex(sampleLyrics, 11999L))
    }

    @Test
    fun `exact timestamp match for intermediate lines returns correct index`() {
        assertEquals(1, findActiveLyricIndex(sampleLyrics, 12000L))
        assertEquals(2, findActiveLyricIndex(sampleLyrics, 25000L))
    }

    @Test
    fun `position between intermediate lines returns correct index`() {
        assertEquals(1, findActiveLyricIndex(sampleLyrics, 18000L))
        assertEquals(2, findActiveLyricIndex(sampleLyrics, 30000L))
        assertEquals(2, findActiveLyricIndex(sampleLyrics, 39999L))
    }

    @Test
    fun `exact timestamp match for final line returns last index`() {
        assertEquals(3, findActiveLyricIndex(sampleLyrics, 40000L))
    }

    @Test
    fun `position past the last line returns last index`() {
        assertEquals(3, findActiveLyricIndex(sampleLyrics, 45000L))
        assertEquals(3, findActiveLyricIndex(sampleLyrics, 300000L))
    }

    @Test
    fun `lyrics starting at 0ms is active immediately at 0ms`() {
        val lyricsWithZero = listOf(
            LyricLine(timestampMs = 0L, text = "Immediate intro"),
            LyricLine(timestampMs = 5000L, text = "Next line")
        )
        assertEquals(0, findActiveLyricIndex(lyricsWithZero, 0L))
        assertEquals(0, findActiveLyricIndex(lyricsWithZero, 1000L))
        assertEquals(1, findActiveLyricIndex(lyricsWithZero, 5000L))
    }

    @Test
    fun `single line lyrics behaves correctly`() {
        val singleLine = listOf(LyricLine(timestampMs = 3000L, text = "Only line"))
        assertEquals(-1, findActiveLyricIndex(singleLine, 1000L))
        assertEquals(0, findActiveLyricIndex(singleLine, 3000L))
        assertEquals(0, findActiveLyricIndex(singleLine, 10000L))
    }

    @Test
    fun `duplicate timestamps select the latter line`() {
        val duplicateTimestamps = listOf(
            LyricLine(timestampMs = 2000L, text = "Line A"),
            LyricLine(timestampMs = 2000L, text = "Line B"),
            LyricLine(timestampMs = 6000L, text = "Line C")
        )
        assertEquals(1, findActiveLyricIndex(duplicateTimestamps, 2000L))
        assertEquals(1, findActiveLyricIndex(duplicateTimestamps, 3000L))
        assertEquals(2, findActiveLyricIndex(duplicateTimestamps, 6000L))
    }
}
