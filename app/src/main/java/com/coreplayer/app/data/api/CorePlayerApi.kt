package com.coreplayer.app.data.api

import com.coreplayer.app.data.model.AlbumDetailResponse
import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.FavoriteToggleRequest
import com.coreplayer.app.data.model.FavoriteToggleResponse
import com.coreplayer.app.data.model.FavoritesIdsResponse
import com.coreplayer.app.data.model.LoginRequest
import com.coreplayer.app.data.model.LyricsResponse
import com.coreplayer.app.data.model.PlaylistDetailResponse
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.SearchResponse
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.model.UserDto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

interface CorePlayerApi {

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): UserDto

    @GET("api/auth/me")
    suspend fun getCurrentUser(): UserDto

    @POST("api/auth/logout")
    suspend fun logout(): ResponseBody

    @GET("api/library/artists")
    suspend fun getArtists(): List<ArtistDto>

    @GET("api/library/albums")
    suspend fun getAlbums(
        @Query("type") type: String? = null,
        @Query("size") size: Int? = null
    ): List<AlbumDto>

    @GET("api/library/albums/{album_id}")
    suspend fun getAlbum(
        @Path("album_id") albumId: String
    ): AlbumDetailResponse

    @GET("api/library/playlists")
    suspend fun getPlaylists(): List<PlaylistDto>

    @POST("api/library/playlists")
    suspend fun createPlaylist(
        @Body body: com.coreplayer.app.data.model.CreatePlaylistRequest
    ): com.coreplayer.app.data.model.CreatePlaylistResponse

    @DELETE("api/library/playlists/{playlist_id}")
    suspend fun deletePlaylist(
        @Path("playlist_id") playlistId: String
    ): com.coreplayer.app.data.model.GenericMessageResponse

    @POST("api/library/playlists/{playlist_id}/tracks")
    suspend fun addTrackToPlaylist(
        @Path("playlist_id") playlistId: String,
        @Body body: com.coreplayer.app.data.model.AddTrackRequest
    ): com.coreplayer.app.data.model.GenericMessageResponse

    @DELETE("api/library/playlists/{playlist_id}/tracks/{track_id}")
    suspend fun removeTrackFromPlaylist(
        @Path("playlist_id") playlistId: String,
        @Path("track_id") trackId: String
    ): com.coreplayer.app.data.model.GenericMessageResponse

    @PUT("api/library/playlists/{playlist_id}/reorder")
    suspend fun reorderPlaylistTracks(
        @Path("playlist_id") playlistId: String,
        @Body body: com.coreplayer.app.data.model.ReorderPlaylistRequest
    ): com.coreplayer.app.data.model.GenericMessageResponse

    @GET("api/library/playlists/{playlist_id}")
    suspend fun getPlaylist(
        @Path("playlist_id") playlistId: String
    ): PlaylistDetailResponse

    @GET("api/library/favorites/ids")
    suspend fun getFavoriteIds(): FavoritesIdsResponse

    @POST("api/library/favorites/toggle")
    suspend fun toggleFavorite(
        @Body request: FavoriteToggleRequest
    ): FavoriteToggleResponse

    @GET("api/library/favorites/tracks")
    suspend fun getFavoriteTracks(): List<TrackDto>

    @GET("api/library/random")
    suspend fun getRandomTracks(
        @Query("size") size: Int = 100
    ): List<TrackDto>

    @GET("api/library/search")
    suspend fun search(
        @Query("query") query: String
    ): SearchResponse

    @GET("api/lyrics/{track_id}")
    suspend fun getLyrics(
        @Path("track_id") trackId: String,
        @Query("artist") artist: String? = null,
        @Query("title") title: String? = null,
        @Query("album") album: String? = null,
        @Query("duration") duration: Int? = null
    ): LyricsResponse

    companion object {
        fun createMoshi(): Moshi {
            return Moshi.Builder()
                .add(FlexibleListJsonAdapterFactory())
                .addLast(KotlinJsonAdapterFactory())
                .build()
        }

        fun create(
            baseUrl: String,
            cookieJar: InMemoryCookieJar = InMemoryCookieJar(),
            okHttpClient: OkHttpClient? = null
        ): CorePlayerApi {
            val client = okHttpClient ?: OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build()

            val moshi = createMoshi()
            val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            return Retrofit.Builder()
                .baseUrl(sanitizedUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(CorePlayerApi::class.java)
        }
    }
}

/**
 * Custom Moshi Adapter factory that handles both raw JSON arrays `[...]`
 * and FastAPI wrapped JSON objects `{"artists": [...]}` or `{"albums": [...]}`.
 */
class FlexibleListJsonAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        val rawType = Types.getRawType(type)
        if (rawType != List::class.java) return null

        val elementType = (type as? ParameterizedType)?.actualTypeArguments?.firstOrNull() ?: return null
        val elementRawType = Types.getRawType(elementType)

        val wrapperKeys = when (elementRawType) {
            ArtistDto::class.java -> listOf("artists", "artist")
            AlbumDto::class.java -> listOf("albums", "album")
            PlaylistDto::class.java -> listOf("playlists", "playlist")
            TrackDto::class.java -> listOf("tracks", "track", "songs", "song")
            else -> emptyList()
        }

        if (wrapperKeys.isEmpty()) return null

        val elementAdapter = moshi.adapter<Any>(elementType)
        return object : JsonAdapter<List<Any>>() {
            override fun fromJson(reader: JsonReader): List<Any> {
                if (reader.peek() == JsonReader.Token.NULL) {
                    reader.nextNull<Unit>()
                    return emptyList()
                }
                if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                    val list = mutableListOf<Any>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        elementAdapter.fromJson(reader)?.let { list.add(it) }
                    }
                    reader.endArray()
                    return list
                }
                if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                    var result: List<Any> = emptyList()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        if (wrapperKeys.contains(name) && reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            val list = mutableListOf<Any>()
                            reader.beginArray()
                            while (reader.hasNext()) {
                                elementAdapter.fromJson(reader)?.let { list.add(it) }
                            }
                            reader.endArray()
                            result = list
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                    return result
                }
                reader.skipValue()
                return emptyList()
            }

            override fun toJson(writer: JsonWriter, value: List<Any>?) {
                if (value == null) {
                    writer.nullValue()
                    return
                }
                writer.beginArray()
                for (item in value) {
                    elementAdapter.toJson(writer, item)
                }
                writer.endArray()
            }
        }
    }
}
