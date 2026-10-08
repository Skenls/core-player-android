package com.coreplayer.app.data.model

import com.squareup.moshi.Json

data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

data class UserDto(
    @Json(name = "id") val id: String,
    @Json(name = "username") val username: String,
    @Json(name = "role") val role: String = "user"
)

data class TrackDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "artist") val artist: String = "",
    @Json(name = "album") val album: String = "",
    @Json(name = "duration") val duration: Int = 0,
    @Json(name = "coverArt") val coverArt: String? = null,
    @Json(name = "albumId") val albumId: String? = null,
    @Json(name = "artistId") val artistId: String? = null,
    @Json(name = "starred") val isStarred: Boolean? = null,
    @Json(name = "is_favorite") val isFav: Boolean? = null
) {
    val isFavorite: Boolean get() = isStarred == true || isFav == true
}

data class ArtistDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "coverArt") val coverArt: String? = null,
    @Json(name = "albumCount") val albumCount: Int = 0
)

data class AlbumDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "artist") val artist: String? = null,
    @Json(name = "coverArt") val coverArt: String? = null,
    @Json(name = "songCount") val songCount: Int = 0,
    @Json(name = "duration") val duration: Int = 0,
    @Json(name = "year") val year: Int? = null
)

data class PlaylistDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "songCount") val songCount: Int = 0,
    @Json(name = "coverArt") val coverArt: String? = null,
    @Json(name = "created") val created: String? = null
)

data class FavoritesIdsResponse(
    @Json(name = "track_ids") val trackIdsList: List<String>? = null,
    @Json(name = "tracks") val tracksList: List<String>? = null,
    @Json(name = "album_ids") val albumIdsList: List<String>? = null,
    @Json(name = "albums") val albumsList: List<String>? = null
) {
    val track_ids: List<String> get() = trackIdsList ?: tracksList ?: emptyList()
    val album_ids: List<String> get() = albumIdsList ?: albumsList ?: emptyList()
}

data class FavoriteToggleRequest(
    @Json(name = "id") val id: String,
    @Json(name = "type") val type: String = "track"
)

data class FavoriteToggleResponse(
    @Json(name = "item_id") val itemId: String? = null,
    @Json(name = "id") val id: String? = null,
    @Json(name = "item_type") val itemType: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "is_favorite") val isFavoriteBool: Boolean? = null,
    @Json(name = "starred") val starred: Boolean? = null
) {
    val item_id: String get() = itemId ?: id ?: ""
    val item_type: String get() = itemType ?: type ?: ""
    val is_favorite: Boolean get() = isFavoriteBool ?: starred ?: false
}

data class LyricsResponse(
    @Json(name = "track_id") val trackId: String? = null,
    @Json(name = "lyrics") val lyricsText: String? = null,
    @Json(name = "text") val text: String? = null,
    @Json(name = "synced") val synced: Boolean? = null
) {
    val track_id: String get() = trackId ?: ""
    val lyrics: String? get() = lyricsText ?: text
}

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class SearchResponse(
    @Json(name = "results") val results: SearchResultDto? = null,
    @Json(name = "tracks") val tracksList: List<TrackDto>? = null,
    @Json(name = "song") val songList: List<TrackDto>? = null,
    @Json(name = "albums") val albumsList: List<AlbumDto>? = null,
    @Json(name = "album") val albumList: List<AlbumDto>? = null,
    @Json(name = "artists") val artistsList: List<ArtistDto>? = null,
    @Json(name = "artist") val artistList: List<ArtistDto>? = null
) {
    val tracks: List<TrackDto>
        get() = results?.songs ?: tracksList ?: songList ?: emptyList()

    val albums: List<AlbumDto>
        get() = results?.albums ?: albumsList ?: albumList ?: emptyList()

    val artists: List<ArtistDto>
        get() = results?.artists ?: artistsList ?: artistList ?: emptyList()
}

data class SearchResultDto(
    @Json(name = "song") val songList: List<TrackDto>? = null,
    @Json(name = "songs") val songsList: List<TrackDto>? = null,
    @Json(name = "tracks") val tracksList: List<TrackDto>? = null,
    @Json(name = "album") val albumList: List<AlbumDto>? = null,
    @Json(name = "albums") val albumsList: List<AlbumDto>? = null,
    @Json(name = "artist") val artistList: List<ArtistDto>? = null,
    @Json(name = "artists") val artistsList: List<ArtistDto>? = null
) {
    val songs: List<TrackDto>
        get() = songList ?: songsList ?: tracksList ?: emptyList()

    val albums: List<AlbumDto>
        get() = albumList ?: albumsList ?: emptyList()

    val artists: List<ArtistDto>
        get() = artistList ?: artistsList ?: emptyList()
}
