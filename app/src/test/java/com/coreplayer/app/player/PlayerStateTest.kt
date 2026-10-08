package com.coreplayer.app.player

import android.net.Uri
import com.coreplayer.app.data.model.TrackDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlayerStateTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uriStr = firstArg<String>()
            val mockUri = mockk<Uri>()
            every { mockUri.toString() } returns uriStr
            mockUri
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun playerState_defaultValues() {
        val state = PlayerState()
        assertNull(state.currentTrack)
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(0L, state.durationMs)
        assertFalse(state.shuffleModeEnabled)
        assertEquals(RepeatMode.OFF, state.repeatMode)
        assertEquals(emptyList<TrackDto>(), state.queue)
        assertEquals(0f, state.progress, 0.001f)
        assertEquals("00:00", state.currentPositionFormatted)
        assertEquals("00:00", state.durationFormatted)
    }

    @Test
    fun repeatMode_cyclesCorrectly() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.cycle())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.cycle())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.cycle())
    }

    @Test
    fun playerState_progressComputation() {
        val zeroState = PlayerState(currentPositionMs = 0L, durationMs = 0L)
        assertEquals(0f, zeroState.progress, 0.001f)

        val halfState = PlayerState(currentPositionMs = 30_000L, durationMs = 60_000L)
        assertEquals(0.5f, halfState.progress, 0.001f)

        val overState = PlayerState(currentPositionMs = 70_000L, durationMs = 60_000L)
        assertEquals(1.0f, overState.progress, 0.001f)

        val negativeState = PlayerState(currentPositionMs = -100L, durationMs = 60_000L)
        assertEquals(0.0f, negativeState.progress, 0.001f)
    }

    @Test
    fun playerState_timeFormatting() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:05", formatTime(5_000L))
        assertEquals("01:05", formatTime(65_000L))
        assertEquals("10:30", formatTime(630_000L))
        assertEquals("01:01:01", formatTime(3_661_000L))

        val state = PlayerState(currentPositionMs = 65_000L, durationMs = 180_000L)
        assertEquals("01:05", state.currentPositionFormatted)
        assertEquals("03:00", state.durationFormatted)
    }

    @Test
    fun trackDto_toMediaItem_convertsFieldsCorrectly() {
        val track = TrackDto(
            id = "track-123",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            duration = 354,
            coverArt = "/api/stream/cover/queen-1"
        )
        val baseUrl = "http://192.168.1.50:8000"

        val mediaItem = track.toMediaItem(baseUrl)

        assertEquals("track-123", mediaItem.mediaId)
        val expectedStreamUri = "http://192.168.1.50:8000/api/stream/track-123"
        assertEquals(expectedStreamUri, mediaItem.localConfiguration?.uri?.toString())
        assertEquals("Bohemian Rhapsody", mediaItem.mediaMetadata.title?.toString())
        assertEquals("Queen", mediaItem.mediaMetadata.artist?.toString())
        assertEquals("A Night at the Opera", mediaItem.mediaMetadata.albumTitle?.toString())
        val expectedCoverUri = "http://192.168.1.50:8000/api/stream/cover/queen-1"
        assertEquals(expectedCoverUri, mediaItem.mediaMetadata.artworkUri?.toString())
    }

    @Test
    fun trackDto_toMediaItem_handlesFullCoverArtUrl() {
        val track = TrackDto(
            id = "track-456",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            duration = 230,
            coverArt = "http://cdn.example.com/cover.jpg"
        )
        val baseUrl = "http://localhost:8000"

        val mediaItem = track.toMediaItem(baseUrl)

        assertEquals("http://cdn.example.com/cover.jpg", mediaItem.mediaMetadata.artworkUri?.toString())
    }

    @Test
    fun trackDto_toMediaItem_handlesNullCoverArt() {
        val track = TrackDto(
            id = "track-789",
            title = "Solo Track",
            artist = "Artist",
            album = "Album",
            duration = 100,
            coverArt = null
        )
        val baseUrl = "http://localhost:8000"

        val mediaItem = track.toMediaItem(baseUrl)

        assertNull(mediaItem.mediaMetadata.artworkUri)
    }

    @Test
    fun queueNavigation_progressesSequentially() {
        val t1 = TrackDto(id = "1", title = "Song 1", artist = "Artist", album = "Album", duration = 120)
        val t2 = TrackDto(id = "2", title = "Song 2", artist = "Artist", album = "Album", duration = 180)
        val queue = listOf(t1, t2)

        val idx1 = queue.indexOfFirst { it.id == t1.id }
        assertEquals(0, idx1)
        val nextTrack = queue.getOrNull(idx1 + 1)
        assertEquals(t2, nextTrack)

        val idx2 = queue.indexOfFirst { it.id == t2.id }
        val endTrack = queue.getOrNull(idx2 + 1)
        assertNull(endTrack)
    }
}
