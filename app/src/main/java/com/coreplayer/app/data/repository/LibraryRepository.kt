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

class LibraryRepository(
    private val api: CorePlayerApi
) {
    suspend fun getArtists(): List<ArtistDto> = api.getArtists()

    suspend fun getAlbums(type: String? = null, size: Int? = null): List<AlbumDto> =
        api.getAlbums(type = type, size = size)

    suspend fun getAlbumTracks(albumId: String): List<TrackDto> {
        return try {
            api.getAlbum(albumId).album.song
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch tracks for album $albumId", e)
            emptyList()
        }
    }

    suspend fun getPlaylists(): List<PlaylistDto> = api.getPlaylists()

    suspend fun getPlaylistTracks(playlistId: String): List<TrackDto> {
        return try {
            api.getPlaylist(playlistId).playlist.entry
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch tracks for playlist $playlistId", e)
            emptyList()
        }
    }

    suspend fun getFavoriteIds(): FavoritesIdsResponse = api.getFavoriteIds()

    suspend fun toggleFavorite(itemId: String, itemType: String = "track"): FavoriteToggleResponse =
        api.toggleFavorite(FavoriteToggleRequest(id = itemId, type = itemType))

    suspend fun getFavoriteTracks(): List<TrackDto> = api.getFavoriteTracks()

    suspend fun search(query: String): List<TrackDto> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return try {
            api.search(trimmed).tracks
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
