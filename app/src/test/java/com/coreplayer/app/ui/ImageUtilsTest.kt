package com.coreplayer.app.ui

import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.ui.util.getPlaylistGradient
import com.coreplayer.app.ui.util.resolveArtistImageUrl
import com.coreplayer.app.ui.util.resolveCoverUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun resolveCoverUrl_formatsSubsonicIdProperly() {
        val resolved = resolveCoverUrl("5iXB2X2NPDYv93u0NU3Gv2", baseUrl = "http://127.0.0.1:8000", size = 300)
        assertEquals("http://127.0.0.1:8000/api/stream/cover/5iXB2X2NPDYv93u0NU3Gv2?size=300", resolved)
    }

    @Test
    fun resolveCoverUrl_preservesFullHttpUrl() {
        val url = "https://images.example.com/cover.jpg"
        assertEquals(url, resolveCoverUrl(url, baseUrl = "http://127.0.0.1:8000"))
    }

    @Test
    fun resolveCoverUrl_prependsBaseUrlToApiPath() {
        val apiPath = "/api/stream/cover/custom-id?size=512"
        val resolved = resolveCoverUrl(apiPath, baseUrl = "http://localhost:8000/")
        assertEquals("http://localhost:8000/api/stream/cover/custom-id?size=512", resolved)
    }

    @Test
    fun resolveCoverUrl_returnsNullOnBlank() {
        assertNull(resolveCoverUrl(null, baseUrl = "http://localhost:8000"))
        assertNull(resolveCoverUrl("", baseUrl = "http://localhost:8000"))
        assertNull(resolveCoverUrl("   ", baseUrl = "http://localhost:8000"))
    }

    @Test
    fun resolveArtistImageUrl_fallsBackToArtistId() {
        val artist = ArtistDto(id = "ar-99", name = "madk1d", coverArt = null)
        val resolved = resolveArtistImageUrl(artist, baseUrl = "http://localhost:8000")
        assertEquals("http://localhost:8000/api/stream/cover/ar-99?size=300", resolved)
    }

    @Test
    fun getPlaylistGradient_returnsValidGradient() {
        val pl1 = PlaylistDto(id = "p1", name = "Вечерний вайб")
        val pl2 = PlaylistDto(id = "p2", name = "Chill vibes")
        val pl3 = PlaylistDto(id = "p3", name = "Инди атмосфера")

        assertNotNull(getPlaylistGradient(pl1))
        assertNotNull(getPlaylistGradient(pl2))
        assertNotNull(getPlaylistGradient(pl3))
    }
}
