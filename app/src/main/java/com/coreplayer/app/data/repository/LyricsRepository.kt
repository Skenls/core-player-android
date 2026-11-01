package com.coreplayer.app.data.repository

import com.coreplayer.app.data.api.CorePlayerApi
import com.coreplayer.app.data.lyrics.LrcParser
import com.coreplayer.app.data.model.LyricLine
import com.coreplayer.app.data.model.LyricsResponse
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

class LyricsRepository(
    private val api: CorePlayerApi,
    private val parser: LrcParser = LrcParser()
) {

    private val cache = ConcurrentHashMap<String, List<LyricLine>>()

    fun getCachedLyrics(trackId: String): List<LyricLine>? {
        return cache[trackId]
    }

    fun clearCache() {
        cache.clear()
    }

    suspend fun getLyricsResponse(
        trackId: String,
        artist: String? = null,
        title: String? = null,
        album: String? = null,
        duration: Int? = null
    ): LyricsResponse {
        return api.getLyrics(
            trackId = trackId,
            artist = artist,
            title = title,
            album = album,
            duration = duration
        )
    }

    suspend fun getLyrics(
        trackId: String,
        artist: String? = null,
        title: String? = null,
        album: String? = null,
        duration: Int? = null
    ): List<LyricLine> {
        val cached = cache[trackId]
        if (!cached.isNullOrEmpty()) {
            return cached
        }
        val response = getLyricsResponse(
            trackId = trackId,
            artist = artist,
            title = title,
            album = album,
            duration = duration
        )
        val text = response.lyrics ?: return emptyList()
        val parsed = parser.parse(text)
        if (parsed.isNotEmpty()) {
            cache[trackId] = parsed
        }
        return parsed
    }

    /**
     * Retries requesting lyrics with exponential backoff if the server is still processing or
     * lyrics arrive with a delay (e.g. 404/network error or empty lyrics response).
     */
    suspend fun getLyricsWithRetry(
        trackId: String,
        artist: String? = null,
        title: String? = null,
        album: String? = null,
        duration: Int? = null,
        maxAttempts: Int = 5,
        initialDelayMs: Long = 2000L,
        backoffMultiplier: Double = 1.5,
        maxDelayMs: Long = 8000L
    ): List<LyricLine> {
        val cached = cache[trackId]
        if (!cached.isNullOrEmpty()) {
            return cached
        }

        var currentDelay = initialDelayMs
        var attempts = 0
        var lastException: Exception? = null

        while (attempts < maxAttempts) {
            attempts++
            try {
                val lines = getLyrics(
                    trackId = trackId,
                    artist = artist,
                    title = title,
                    album = album,
                    duration = duration
                )
                if (lines.isNotEmpty()) {
                    return lines
                }
            } catch (e: Exception) {
                lastException = e
            }

            if (attempts < maxAttempts) {
                delay(currentDelay)
                currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }

        if (lastException != null) {
            throw lastException
        }
        return emptyList()
    }
}
