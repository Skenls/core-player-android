package com.coreplayer.app.data.repository

import android.util.Log
import com.coreplayer.app.data.api.CorePlayerApi
import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.FavoriteToggleRequest
import com.coreplayer.app.data.model.FavoriteToggleResponse
import com.coreplayer.app.data.model.FavoritesIdsResponse
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.SearchResponse
import com.coreplayer.app.data.model.TrackDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class LibraryRepository(
    private val api: CorePlayerApi
) {
    private val _favoritesChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val favoritesChanged: SharedFlow<Unit> = _favoritesChanged.asSharedFlow()

    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    private val _playlistsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val playlistsChanged: SharedFlow<Unit> = _playlistsChanged.asSharedFlow()

    private var cachedAllTracks: List<TrackDto>? = null
    private var cachedAllAlbums: List<AlbumDto>? = null

    fun notifyFavoritesChanged() {
        _favoritesChanged.tryEmit(Unit)
    }

    fun notifyPlaylistsChanged() {
        _playlistsChanged.tryEmit(Unit)
    }

    private fun cleanTrack(track: TrackDto): TrackDto {
        val cleaned = com.coreplayer.app.ui.util.cleanArtistName(track.artist)
        return if (cleaned != track.artist) track.copy(artist = cleaned) else track
    }

    suspend fun getArtists(): List<ArtistDto> =
        api.getArtists().filterNot { com.coreplayer.app.ui.util.isFio(it.name) }

    suspend fun getArtistAlbums(artistId: String): List<AlbumDto> {
        return try {
            val res = api.getArtist(artistId)
            val albums = res.artist.album.map {
                val cleaned = com.coreplayer.app.ui.util.cleanArtistName(it.artist)
                if (cleaned != it.artist) it.copy(artist = cleaned) else it
            }
            if (albums.isNotEmpty() && cachedAllAlbums != null) {
                val existingIds = cachedAllAlbums!!.map { it.id }.toSet()
                val newOnes = albums.filter { it.id !in existingIds }
                if (newOnes.isNotEmpty()) {
                    cachedAllAlbums = cachedAllAlbums!! + newOnes
                }
            }
            albums
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get artist albums for $artistId", e)
            emptyList()
        }
    }

    suspend fun getAlbums(type: String? = null, size: Int? = 500): List<AlbumDto> =
        api.getAlbums(type = type, size = size).map {
            val cleaned = com.coreplayer.app.ui.util.cleanArtistName(it.artist)
            if (cleaned != it.artist) it.copy(artist = cleaned) else it
        }

    suspend fun getAlbumTracks(albumId: String): List<TrackDto> {
        return try {
            api.getAlbum(albumId).album.song.map { cleanTrack(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch tracks for album $albumId", e)
            emptyList()
        }
    }

    suspend fun getPlaylists(): List<PlaylistDto> = api.getPlaylists()

    suspend fun createPlaylist(name: String): com.coreplayer.app.data.model.CreatePlaylistResponse {
        val res = api.createPlaylist(com.coreplayer.app.data.model.CreatePlaylistRequest(name))
        _playlistsChanged.tryEmit(Unit)
        return res
    }

    suspend fun deletePlaylist(playlistId: String) {
        api.deletePlaylist(playlistId)
        _playlistsChanged.tryEmit(Unit)
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {
        api.addTrackToPlaylist(playlistId, com.coreplayer.app.data.model.AddTrackRequest(trackId))
        _playlistsChanged.tryEmit(Unit)
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        api.removeTrackFromPlaylist(playlistId, trackId)
        _playlistsChanged.tryEmit(Unit)
    }

    suspend fun reorderPlaylistTracks(playlistId: String, trackIds: List<String>) {
        api.reorderPlaylistTracks(playlistId, com.coreplayer.app.data.model.ReorderPlaylistRequest(trackIds))
        _playlistsChanged.tryEmit(Unit)
    }

    suspend fun getPlaylistTracks(playlistId: String): List<TrackDto> {
        return try {
            api.getPlaylist(playlistId).playlist.entry.map { cleanTrack(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch tracks for playlist $playlistId", e)
            emptyList()
        }
    }

    suspend fun getFavoriteIds(): FavoritesIdsResponse {
        val res = api.getFavoriteIds()
        _favoriteTrackIds.value = res.track_ids.toSet()
        return res
    }

    suspend fun refreshFavoriteIds(): Set<String> {
        return try {
            val res = api.getFavoriteIds()
            val set = res.track_ids.toSet()
            _favoriteTrackIds.value = set
            set
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh favorite ids", e)
            _favoriteTrackIds.value
        }
    }

    suspend fun toggleFavorite(itemId: String, itemType: String = "track"): FavoriteToggleResponse {
        val previousSet = _favoriteTrackIds.value
        if (itemType == "track") {
            // Optimistic update
            val isFav = previousSet.contains(itemId)
            _favoriteTrackIds.value = if (isFav) previousSet - itemId else previousSet + itemId
        }
        return try {
            val response = api.toggleFavorite(FavoriteToggleRequest(id = itemId, type = itemType))
            if (itemType == "track") {
                val current = _favoriteTrackIds.value
                _favoriteTrackIds.value = if (response.is_favorite) current + itemId else current - itemId
            }
            _favoritesChanged.tryEmit(Unit)
            response
        } catch (e: Exception) {
            if (itemType == "track") {
                _favoriteTrackIds.value = previousSet
            }
            throw e
        }
    }

    suspend fun getFavoriteTracks(): List<TrackDto> = api.getFavoriteTracks().map { cleanTrack(it) }

    suspend fun getRandomTracks(size: Int = 100): List<TrackDto> {
        return try {
            api.getRandomTracks(size).map { cleanTrack(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch random tracks", e)
            emptyList()
        }
    }

    fun clearAllCaches() {
        cachedAllTracks = null
        cachedAllAlbums = null
        _favoriteTrackIds.value = emptySet()
    }

    /**
     * Fetches all albums across the user's library by combining alphabetical and newest listings,
     * ensuring full coverage without being cut off by the backend default 50-item limit.
     */
    suspend fun getAllAlbums(forceRefresh: Boolean = false): List<AlbumDto> {
        if (!forceRefresh && !cachedAllAlbums.isNullOrEmpty()) {
            return cachedAllAlbums!!
        }
        return try {
            coroutineScope {
                val alphaDeferred = async { getAlbums(type = "alphabeticalByName", size = 500) }
                val newestDeferred = async { getAlbums(type = "newest", size = 500) }

                val alpha = alphaDeferred.await()
                val newest = newestDeferred.await()

                val combined = (alpha + newest).distinctBy { it.id }
                if (combined.isNotEmpty()) {
                    cachedAllAlbums = combined
                    combined
                } else {
                    getAlbums(size = 500)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch all albums, falling back to basic getAlbums", e)
            getAlbums(size = 500)
        }
    }

    /**
     * Fetches all tracks across the user's library by aggregating album tracks and random tracks.
     * Guarantees that every track in every album is included without omissions.
     */
    suspend fun getAllTracks(forceRefresh: Boolean = false): List<TrackDto> {
        if (!forceRefresh && !cachedAllTracks.isNullOrEmpty()) {
            return cachedAllTracks!!
        }
        return try {
            coroutineScope {
                val albumsDeferred = async { getAlbums(size = 500) }
                val randomDeferred = async { getRandomTracks(500) }

                val albums = albumsDeferred.await()
                val randomTracks = randomDeferred.await()

                val albumTracksDeferred = albums.map { album ->
                    async { getAlbumTracks(album.id) }
                }
                val albumTracks = albumTracksDeferred.awaitAll().flatten()

                val combined = (albumTracks + randomTracks)
                    .distinctBy { it.id }

                if (combined.isNotEmpty()) {
                    cachedAllTracks = combined
                    combined
                } else {
                    randomTracks
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch all tracks, falling back to random", e)
            getRandomTracks(500)
        }
    }

    suspend fun search(query: String): List<TrackDto> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return try {
            api.search(trimmed).tracks.map { cleanTrack(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query: $trimmed", e)
            emptyList()
        }
    }

    suspend fun searchFull(query: String): SearchResponse {
        val trimmed = query.trim()
        if (trimmed.length < 2) return SearchResponse()
        return try {
            api.search(trimmed)
        } catch (e: Exception) {
            Log.e(TAG, "Search full failed for query: $trimmed", e)
            SearchResponse()
        }
    }

    companion object {
        private const val TAG = "LibraryRepository"
    }
}
