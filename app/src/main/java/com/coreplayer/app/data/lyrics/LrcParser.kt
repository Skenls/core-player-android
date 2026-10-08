package com.coreplayer.app.data.lyrics

import com.coreplayer.app.data.model.LyricLine
import kotlin.math.roundToLong

class LrcParser {

    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{1,2}(?:\.\d{1,3})?)\]""")
    private val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

    /**
     * Parses standard LRC lyrics content into a sorted list of [LyricLine].
     *
     * Handles:
     * - Standard timestamps: `[mm:ss.xx]` and `[mm:ss.xxx]`
     * - Multiple timestamps on a single line
     * - Global offset tags: `[offset:+/-ms]`
     * - Metadata tags: `[ti:...]`, `[ar:...]`, `[al:...]`, etc. (ignored)
     * - Empty/blank lines and malformed lines
     */
    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        var offsetMs = 0L
        val lines = mutableListOf<LyricLine>()

        for (rawLine in lrcContent.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Check for offset tag
            val offsetMatch = offsetRegex.find(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                continue
            }

            // Check for timestamp tags
            val timestampMatches = timestampRegex.findAll(line).toList()
            if (timestampMatches.isNotEmpty()) {
                val cleanText = timestampRegex.replace(line, "").trim()
                for (match in timestampMatches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val secondsStr = match.groupValues[2]
                    val secondsDouble = secondsStr.toDoubleOrNull() ?: 0.0
                    val baseMs = (minutes * 60 * 1000) + (secondsDouble * 1000).roundToLong()
                    val totalMs = maxOf(0L, baseMs + offsetMs)
                    lines.add(LyricLine(timestampMs = totalMs, text = cleanText))
                }
            }
        }

        return lines.sortedBy { it.timestampMs }
    }
}
