package com.coreplayer.app.data.lyrics

import com.coreplayer.app.data.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LrcParserTest {

    private lateinit var parser: LrcParser

    @Before
    fun setUp() {
        parser = LrcParser()
    }

    @Test
    fun parse_standardTwoDigitTimestamps_returnsCorrectLines() {
        val lrc = """
            [00:12.34]First line
            [01:05.67]Second line
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(2, result.size)
        // 00:12.34 -> 12 * 1000 + 340 = 12340 ms
        assertEquals(LyricLine(12340L, "First line"), result[0])
        // 01:05.67 -> (1 * 60 + 5) * 1000 + 670 = 65670 ms
        assertEquals(LyricLine(65670L, "Second line"), result[1])
    }

    @Test
    fun parse_threeDigitMilliseconds_returnsExactMilliseconds() {
        val lrc = """
            [00:15.123]Line with three digit milliseconds
            [02:00.005]Line with five milliseconds
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(2, result.size)
        // 15.123s -> 15123 ms
        assertEquals(LyricLine(15123L, "Line with three digit milliseconds"), result[0])
        // 2m 0.005s -> 120005 ms
        assertEquals(LyricLine(120005L, "Line with five milliseconds"), result[1])
    }

    @Test
    fun parse_multipleTimestampsOnSingleLine_createsMultipleSortedLines() {
        val lrc = """
            [00:10.00][00:25.00]Repeated chorus
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(2, result.size)
        assertEquals(LyricLine(10000L, "Repeated chorus"), result[0])
        assertEquals(LyricLine(25000L, "Repeated chorus"), result[1])
    }

    @Test
    fun parse_outOfOrderLines_returnsSortedByTimestamp() {
        val lrc = """
            [02:00.00]Third line
            [00:10.00]First line
            [01:00.00]Second line
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(3, result.size)
        assertEquals(10000L, result[0].timestampMs)
        assertEquals("First line", result[0].text)
        assertEquals(60000L, result[1].timestampMs)
        assertEquals("Second line", result[1].text)
        assertEquals(120000L, result[2].timestampMs)
        assertEquals("Third line", result[2].text)
    }

    @Test
    fun parse_metadataTags_areIgnored() {
        val lrc = """
            [ti:Song Title]
            [ar:Artist Name]
            [al:Album Title]
            [by:Creator]
            [length:03:45]
            [00:05.00]Only real lyric line
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(1, result.size)
        assertEquals(LyricLine(5000L, "Only real lyric line"), result[0])
    }

    @Test
    fun parse_positiveOffsetTag_shiftsTimestampsUp() {
        val lrc = """
            [offset:500]
            [00:10.00]Line with positive offset
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(1, result.size)
        // 10000 + 500 = 10500 ms
        assertEquals(LyricLine(10500L, "Line with positive offset"), result[0])
    }

    @Test
    fun parse_negativeOffsetTag_shiftsTimestampsDown() {
        val lrc = """
            [offset:-500]
            [00:10.00]Line with negative offset
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(1, result.size)
        // 10000 - 500 = 9500 ms
        assertEquals(LyricLine(9500L, "Line with negative offset"), result[0])
    }

    @Test
    fun parse_negativeOffsetBelowZero_clampsToZero() {
        val lrc = """
            [offset:-2000]
            [00:01.00]Line clamped
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(1, result.size)
        assertEquals(LyricLine(0L, "Line clamped"), result[0])
    }

    @Test
    fun parse_emptyOrBlankLines_areHandledGracefully() {
        val lrc = """

            
            [00:01.00]Line one

            [00:02.00]Line two
            
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(2, result.size)
        assertEquals("Line one", result[0].text)
        assertEquals("Line two", result[1].text)
    }

    @Test
    fun parse_nullOrEmptyInput_returnsEmptyList() {
        assertTrue(parser.parse(null).isEmpty())
        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("   \n\t  ").isEmpty())
    }

    @Test
    fun parse_malformedLinesWithoutTimestamps_areIgnored() {
        val lrc = """
            This is an invalid line without brackets
            [invalid:tags]Not a timestamp
            [00:03.00]Valid line
            Another bad line
        """.trimIndent()

        val result = parser.parse(lrc)

        assertEquals(1, result.size)
        assertEquals(LyricLine(3000L, "Valid line"), result[0])
    }
}
