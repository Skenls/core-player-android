package com.coreplayer.app.data.repository

import com.coreplayer.app.data.api.CorePlayerApi
import com.coreplayer.app.data.lyrics.LrcParser
import com.coreplayer.app.data.model.LyricLine
import com.coreplayer.app.data.model.LyricsResponse

class LyricsRepository(
    private val api: CorePlayerApi,
    private val parser: LrcParser = LrcParser()
) {

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
        val response = getLyricsResponse(
            trackId = trackId,
            artist = artist,
            title = title,
            album = album,
            duration = duration
        )
        val text = response.lyrics ?: return emptyList()
        return parser.parse(text)
    }
}
