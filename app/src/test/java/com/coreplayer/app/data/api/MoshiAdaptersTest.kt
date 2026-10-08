package com.coreplayer.app.data.api

import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.FavoriteToggleResponse
import com.coreplayer.app.data.model.FavoritesIdsResponse
import com.coreplayer.app.data.model.LyricsResponse
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.model.UserDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoshiAdaptersTest {

    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        moshi = CorePlayerApi.createMoshi()
    }

    @Test
    fun parseUserDto() {
        val json = """{"id":"usr-123","username":"tester","role":"admin"}"""
        val adapter = moshi.adapter(UserDto::class.java)
        val user = adapter.fromJson(json)

        assertEquals("usr-123", user?.id)
        assertEquals("tester", user?.username)
        assertEquals("admin", user?.role)
    }

    @Test
    fun parseArtistList_directArray() {
        val json = """[{"id":"art-1","name":"Artist One","coverArt":"cov-1","albumCount":2}]"""
        val type = Types.newParameterizedType(List::class.java, ArtistDto::class.java)
        val adapter = moshi.adapter<List<ArtistDto>>(type)
        val artists = adapter.fromJson(json)

        assertEquals(1, artists?.size)
        assertEquals("art-1", artists?.get(0)?.id)
        assertEquals("Artist One", artists?.get(0)?.name)
    }

    @Test
    fun parseArtistList_wrappedObjectFromFastApi() {
        val json = """{"artists":[{"id":"art-2","name":"Artist Two","albumCount":5}]}"""
        val type = Types.newParameterizedType(List::class.java, ArtistDto::class.java)
        val adapter = moshi.adapter<List<ArtistDto>>(type)
        val artists = adapter.fromJson(json)

        assertEquals(1, artists?.size)
        assertEquals("art-2", artists?.get(0)?.id)
        assertEquals("Artist Two", artists?.get(0)?.name)
        assertEquals(5, artists?.get(0)?.albumCount)
    }

    @Test
    fun parseAlbumList_wrappedObjectFromFastApi() {
        val json = """{"albums":[{"id":"alb-1","name":"Album One","artist":"Artist One","songCount":10,"year":2026}]}"""
        val type = Types.newParameterizedType(List::class.java, AlbumDto::class.java)
        val adapter = moshi.adapter<List<AlbumDto>>(type)
        val albums = adapter.fromJson(json)

        assertEquals(1, albums?.size)
        assertEquals("alb-1", albums?.get(0)?.id)
        assertEquals(10, albums?.get(0)?.songCount)
        assertEquals(2026, albums?.get(0)?.year)
    }

    @Test
    fun parsePlaylistList_wrappedObjectFromFastApi() {
        val json = """{"playlists":[{"id":"pl-1","name":"Favorites","songCount":25}]}"""
        val type = Types.newParameterizedType(List::class.java, PlaylistDto::class.java)
        val adapter = moshi.adapter<List<PlaylistDto>>(type)
        val playlists = adapter.fromJson(json)

        assertEquals(1, playlists?.size)
        assertEquals("pl-1", playlists?.get(0)?.id)
        assertEquals("Favorites", playlists?.get(0)?.name)
        assertEquals(25, playlists?.get(0)?.songCount)
    }

    @Test
    fun parseFavoritesIdsResponse_bothVariants() {
        val json1 = """{"track_ids":["t1","t2"],"album_ids":["a1"]}"""
        val res1 = moshi.adapter(FavoritesIdsResponse::class.java).fromJson(json1)
        assertEquals(listOf("t1", "t2"), res1?.track_ids)
        assertEquals(listOf("a1"), res1?.album_ids)

        val json2 = """{"tracks":["t3"],"albums":["a2","a3"]}"""
        val res2 = moshi.adapter(FavoritesIdsResponse::class.java).fromJson(json2)
        assertEquals(listOf("t3"), res2?.track_ids)
        assertEquals(listOf("a2", "a3"), res2?.album_ids)
    }

    @Test
    fun parseFavoriteToggleResponse_bothVariants() {
        val json1 = """{"item_id":"t1","item_type":"track","is_favorite":true}"""
        val res1 = moshi.adapter(FavoriteToggleResponse::class.java).fromJson(json1)
        assertEquals("t1", res1?.item_id)
        assertEquals("track", res1?.item_type)
        assertTrue(res1?.is_favorite == true)

        val json2 = """{"id":"a1","type":"album","starred":false}"""
        val res2 = moshi.adapter(FavoriteToggleResponse::class.java).fromJson(json2)
        assertEquals("a1", res2?.item_id)
        assertEquals("album", res2?.item_type)
        assertFalse(res2?.is_favorite == true)
    }

    @Test
    fun parseLyricsResponse_bothVariants() {
        val json1 = """{"track_id":"trk-1","lyrics":"[00:01.00]Song lyrics","synced":true}"""
        val res1 = moshi.adapter(LyricsResponse::class.java).fromJson(json1)
        assertEquals("trk-1", res1?.track_id)
        assertEquals("[00:01.00]Song lyrics", res1?.lyrics)
        assertTrue(res1?.synced == true)

        val json2 = """{"text":"Plain lyrics","synced":false}"""
        val res2 = moshi.adapter(LyricsResponse::class.java).fromJson(json2)
        assertEquals("Plain lyrics", res2?.lyrics)
        assertFalse(res2?.synced == true)
    }

    @Test
    fun parseTrackDto_starredFlag() {
        val json = """{"id":"t1","title":"Song Title","artist":"Artist","starred":true}"""
        val track = moshi.adapter(TrackDto::class.java).fromJson(json)
        assertEquals("t1", track?.id)
        assertTrue(track?.isFavorite == true)
    }
}
