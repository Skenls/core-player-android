package com.coreplayer.app.data.repository

import com.coreplayer.app.data.api.CorePlayerApi
import com.coreplayer.app.data.api.InMemoryCookieJar
import com.coreplayer.app.data.lyrics.LrcParser
import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.FavoriteToggleRequest
import com.coreplayer.app.data.model.FavoriteToggleResponse
import com.coreplayer.app.data.model.FavoritesIdsResponse
import com.coreplayer.app.data.model.LoginRequest
import com.coreplayer.app.data.model.LyricsResponse
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.model.UserDto
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepositoryTest {

    private lateinit var fakeApi: FakeCorePlayerApi
    private lateinit var cookieJar: InMemoryCookieJar
    private lateinit var authRepository: AuthRepository
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var lyricsRepository: LyricsRepository

    @Before
    fun setUp() {
        fakeApi = FakeCorePlayerApi()
        cookieJar = InMemoryCookieJar()
        authRepository = AuthRepository(fakeApi, cookieJar)
        libraryRepository = LibraryRepository(fakeApi)
        lyricsRepository = LyricsRepository(fakeApi, LrcParser())
    }

    @Test
    fun authRepository_login_setsCurrentUser() = runTest {
        assertNull(authRepository.currentUser.value)

        val user = authRepository.login("alice", "secret123")

        assertEquals("usr-alice", user.id)
        assertEquals("alice", user.username)
        assertEquals(user, authRepository.currentUser.value)
    }

    @Test
    fun authRepository_logout_clearsSessionAndCookieJar() = runTest {
        authRepository.login("alice", "secret123")
        cookieJar.saveFromResponse(
            "http://localhost/".toHttpUrl(),
            listOf(Cookie.Builder().domain("localhost").path("/").name("session_id").value("123").build())
        )
        assertTrue(cookieJar.hasSessionCookie())

        authRepository.logout()

        assertNull(authRepository.currentUser.value)
        assertFalse(cookieJar.hasSessionCookie())
    }

    @Test
    fun libraryRepository_fetchesArtistsAlbumsPlaylists() = runTest {
        val artists = libraryRepository.getArtists()
        assertEquals(1, artists.size)
        assertEquals("Radiohead", artists[0].name)

        val albums = libraryRepository.getAlbums()
        assertEquals(1, albums.size)
        assertEquals("OK Computer", albums[0].name)

        val playlists = libraryRepository.getPlaylists()
        assertEquals(1, playlists.size)
        assertEquals("Chill Vibes", playlists[0].name)
    }

    @Test
    fun libraryRepository_favorites() = runTest {
        val favIds = libraryRepository.getFavoriteIds()
        assertEquals(listOf("trk-1"), favIds.track_ids)

        val toggleRes = libraryRepository.toggleFavorite("trk-2", "track")
        assertEquals("trk-2", toggleRes.item_id)
        assertTrue(toggleRes.is_favorite)
    }

    @Test
    fun lyricsRepository_getLyrics_parsesLrcLines() = runTest {
        val lyrics = lyricsRepository.getLyrics("trk-1")

        assertEquals(2, lyrics.size)
        assertEquals(5000L, lyrics[0].timestampMs)
        assertEquals("Line one", lyrics[0].text)
        assertEquals(10000L, lyrics[1].timestampMs)
        assertEquals("Line two", lyrics[1].text)
    }

    private class FakeCorePlayerApi : CorePlayerApi {
        override suspend fun login(request: LoginRequest): UserDto {
            return UserDto(id = "usr-${request.username}", username = request.username, role = "user")
        }

        override suspend fun getCurrentUser(): UserDto {
            return UserDto(id = "usr-current", username = "current", role = "user")
        }

        override suspend fun logout(): ResponseBody {
            return "".toResponseBody()
        }

        override suspend fun getArtists(): List<ArtistDto> {
            return listOf(ArtistDto(id = "art-1", name = "Radiohead", albumCount = 9))
        }

        override suspend fun getAlbums(type: String?, size: Int?): List<AlbumDto> {
            return listOf(AlbumDto(id = "alb-1", name = "OK Computer", artist = "Radiohead", songCount = 12))
        }

        override suspend fun getPlaylists(): List<PlaylistDto> {
            return listOf(PlaylistDto(id = "pl-1", name = "Chill Vibes", songCount = 20))
        }

        override suspend fun getFavoriteIds(): FavoritesIdsResponse {
            return FavoritesIdsResponse(trackIdsList = listOf("trk-1"), albumIdsList = emptyList())
        }

        override suspend fun toggleFavorite(request: FavoriteToggleRequest): FavoriteToggleResponse {
            return FavoriteToggleResponse(itemId = request.id, itemType = request.type, isFavoriteBool = true)
        }

        override suspend fun getFavoriteTracks(): List<TrackDto> {
            return listOf(TrackDto(id = "trk-1", title = "Paranoid Android", artist = "Radiohead", duration = 383))
        }

        override suspend fun getLyrics(
            trackId: String,
            artist: String?,
            title: String?,
            album: String?,
            duration: Int?
        ): LyricsResponse {
            return LyricsResponse(
                trackId = trackId,
                lyricsText = "[00:05.00]Line one\n[00:10.00]Line two",
                synced = true
            )
        }
    }
}
