package com.coreplayer.app.data.repository

import com.coreplayer.app.data.api.CorePlayerApi
import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.FavoriteToggleRequest
import com.coreplayer.app.data.model.FavoriteToggleResponse
import com.coreplayer.app.data.model.FavoritesIdsResponse
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto

class LibraryRepository(
    private val api: CorePlayerApi
) {
    suspend fun getArtists(): List<ArtistDto> = api.getArtists()

    suspend fun getAlbums(type: String? = null, size: Int? = null): List<AlbumDto> =
        api.getAlbums(type = type, size = size)

    suspend fun getPlaylists(): List<PlaylistDto> = api.getPlaylists()

    suspend fun getFavoriteIds(): FavoritesIdsResponse = api.getFavoriteIds()

    suspend fun toggleFavorite(itemId: String, itemType: String = "track"): FavoriteToggleResponse =
        api.toggleFavorite(FavoriteToggleRequest(id = itemId, type = itemType))

    suspend fun getFavoriteTracks(): List<TrackDto> = api.getFavoriteTracks()
}
