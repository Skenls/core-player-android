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
        assertTrue(libraryRepository.favoriteTrackIds.value.contains("trk-1"))

        val toggleRes = libraryRepository.toggleFavorite("trk-2", "track")
        assertEquals("trk-2", toggleRes.item_id)
        assertTrue(toggleRes.is_favorite)
        assertTrue(libraryRepository.favoriteTrackIds.value.contains("trk-2"))
    }

    @Test
    fun libraryRepository_getAllTracks_combinesAlbumAndRandomTracks() = runTest {
        val tracks = libraryRepository.getAllTracks()
        assertTrue(tracks.any { it.id == "trk-album-1" })
        assertTrue(tracks.any { it.id == "trk-rnd-1" })
    }

    @Test
    fun libraryRepository_getAllAlbums_fetchesAndCachesAlbums() = runTest {
        val albums = libraryRepository.getAllAlbums()
        assertEquals(1, albums.size)
        assertEquals("alb-1", albums[0].id)
        assertEquals("OK Computer", albums[0].name)
    }

    @Test
    fun libraryRepository_getArtistAlbums_fetchesArtistAlbums() = runTest {
        val albums = libraryRepository.getArtistAlbums("art-1")
        assertEquals(1, albums.size)
        assertEquals("alb-1", albums[0].id)
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

    @Test
    fun libraryRepository_search_callsApiSearch() = runTest {
        val results = libraryRepository.search("Karma")
        assertEquals(1, results.size)
        assertEquals("Karma Police", results[0].title)
    }

    @Test
    fun libraryRepository_search_returnsEmptyForBlank() = runTest {
        val results = libraryRepository.search("   ")
        assertTrue(results.isEmpty())
    }

    @Test
    fun libraryRepository_getRandomTracks_returnsTracks() = runTest {
        val tracks = libraryRepository.getRandomTracks(10)
        assertEquals(1, tracks.size)
        assertEquals("Lucky", tracks[0].title)
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

        override suspend fun getArtist(artistId: String): com.coreplayer.app.data.model.ArtistDetailResponse {
            return com.coreplayer.app.data.model.ArtistDetailResponse(
                artist = com.coreplayer.app.data.model.ArtistWithAlbumsDto(
                    id = artistId,
                    name = "Radiohead",
                    album = listOf(AlbumDto(id = "alb-1", name = "OK Computer", artist = "Radiohead", songCount = 12))
                )
            )
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

        override suspend fun getRandomTracks(size: Int): List<TrackDto> {
            return listOf(TrackDto(id = "trk-rnd-1", title = "Lucky", artist = "Radiohead", duration = 259))
        }

        override suspend fun search(query: String): com.coreplayer.app.data.model.SearchResponse {
            val track = TrackDto(id = "trk-search-1", title = "Karma Police", artist = "Radiohead", duration = 260)
            return com.coreplayer.app.data.model.SearchResponse(
                tracksList = listOf(track)
            )
        }

        override suspend fun getAlbum(albumId: String): com.coreplayer.app.data.model.AlbumDetailResponse {
            val song = TrackDto(id = "trk-album-1", title = "Airbag", artist = "Radiohead", albumId = albumId)
            return com.coreplayer.app.data.model.AlbumDetailResponse(
                album = com.coreplayer.app.data.model.AlbumWithSongsDto(
                    id = albumId,
                    name = "OK Computer",
                    song = listOf(song)
                )
            )
        }

        override suspend fun getPlaylist(playlistId: String): com.coreplayer.app.data.model.PlaylistDetailResponse {
            val song = TrackDto(id = "trk-pl-1", title = "Karma Police", artist = "Radiohead")
            return com.coreplayer.app.data.model.PlaylistDetailResponse(
                playlist = com.coreplayer.app.data.model.PlaylistWithSongsDto(
                    id = playlistId,
                    name = "Chill Vibes",
                    entry = listOf(song)
                )
            )
        }

        override suspend fun createPlaylist(body: com.coreplayer.app.data.model.CreatePlaylistRequest): com.coreplayer.app.data.model.CreatePlaylistResponse {
            return com.coreplayer.app.data.model.CreatePlaylistResponse(id = "pl-new", name = body.name, songCount = 0)
        }

        override suspend fun deletePlaylist(playlistId: String): com.coreplayer.app.data.model.GenericMessageResponse {
            return com.coreplayer.app.data.model.GenericMessageResponse(message = "Deleted")
        }

        override suspend fun addTrackToPlaylist(playlistId: String, body: com.coreplayer.app.data.model.AddTrackRequest): com.coreplayer.app.data.model.GenericMessageResponse {
            return com.coreplayer.app.data.model.GenericMessageResponse(message = "Added")
        }

        override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): com.coreplayer.app.data.model.GenericMessageResponse {
            return com.coreplayer.app.data.model.GenericMessageResponse(message = "Removed")
        }

        override suspend fun reorderPlaylistTracks(playlistId: String, body: com.coreplayer.app.data.model.ReorderPlaylistRequest): com.coreplayer.app.data.model.GenericMessageResponse {
            return com.coreplayer.app.data.model.GenericMessageResponse(message = "Reordered")
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

    @Test
    fun libraryRepository_getAlbumTracks_returnsTracks() = runTest {
        val tracks = libraryRepository.getAlbumTracks("alb-1")
        assertEquals(1, tracks.size)
        assertEquals("Airbag", tracks.first().title)
    }

    @Test
    fun libraryRepository_getPlaylistTracks_returnsTracks() = runTest {
        val tracks = libraryRepository.getPlaylistTracks("pl-1")
        assertEquals(1, tracks.size)
        assertEquals("Karma Police", tracks.first().title)
    }

    @Test
    fun libraryRepository_playlistCrudAndReorder_succeeds() = runTest {
        val created = libraryRepository.createPlaylist("My New Playlist")
        assertEquals("pl-new", created.id)
        assertEquals("My New Playlist", created.name)

        libraryRepository.addTrackToPlaylist("pl-new", "trk-1")
        libraryRepository.reorderPlaylistTracks("pl-new", listOf("trk-2", "trk-1"))
        libraryRepository.removeTrackFromPlaylist("pl-new", "trk-1")
        libraryRepository.deletePlaylist("pl-new")
    }
}
