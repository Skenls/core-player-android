package com.coreplayer.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.coreplayer.app.data.model.TrackDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPlayer: Player
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uriStr = firstArg<String>()
            val uri = mockk<Uri>()
            every { uri.toString() } returns uriStr
            uri
        }

        mockContext = mockk(relaxed = true)
        mockPlayer = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createManager(): PlayerControllerManager {
        return PlayerControllerManager(
            context = mockContext,
            baseUrl = "http://localhost:8000",
            coroutineScope = testScope
        )
    }

    @Test
    fun initialState_isDefault() {
        val manager = createManager()
        val state = manager.state.value

        assertNull(state.currentTrack)
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(0L, state.durationMs)
        assertEquals(RepeatMode.OFF, state.repeatMode)
    }

    @Test
    fun attachPlayer_updatesStateFromPlayer() {
        val manager = createManager()
        every { mockPlayer.isPlaying } returns true
        every { mockPlayer.currentPosition } returns 45_000L
        every { mockPlayer.duration } returns 180_000L
        every { mockPlayer.shuffleModeEnabled } returns true
        every { mockPlayer.repeatMode } returns Player.REPEAT_MODE_ALL
        every { mockPlayer.playbackState } returns Player.STATE_READY

        manager.attachPlayer(mockPlayer)

        val state = manager.state.value
        assertTrue(state.isPlaying)
        assertFalse(state.isBuffering)
        assertEquals(45_000L, state.currentPositionMs)
        assertEquals(180_000L, state.durationMs)
        assertTrue(state.shuffleModeEnabled)
        assertEquals(RepeatMode.ALL, state.repeatMode)
    }

    @Test
    fun playTrack_setsMediaItemsAndPlays() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        val track = TrackDto(
            id = "trk-1",
            title = "Karma Police",
            artist = "Radiohead",
            album = "OK Computer",
            duration = 264
        )

        manager.playTrack(track)

        val state = manager.state.value
        assertEquals("trk-1", state.currentTrack?.id)
        assertTrue(state.isPlaying)
        assertTrue(state.isBuffering)
        assertEquals(listOf(track), state.queue)
        assertEquals(264_000L, state.durationMs)

        verify { mockPlayer.setMediaItems(any(), 0, 0L) }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun playTrack_withQueue_preservesQueueAndFindsCorrectIndex() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        val track1 = TrackDto(id = "trk-1", title = "Track 1", duration = 100)
        val track2 = TrackDto(id = "trk-2", title = "Track 2", duration = 200)
        val queue = listOf(track1, track2)

        manager.playTrack(track2, queue)

        val state = manager.state.value
        assertEquals("trk-2", state.currentTrack?.id)
        assertEquals(queue, state.queue)

        verify { mockPlayer.setMediaItems(match { it.size == 2 }, 1, 0L) }
    }

    @Test
    fun togglePlayPause_togglesPlayerPlayback() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        every { mockPlayer.isPlaying } returns false
        manager.togglePlayPause()
        verify { mockPlayer.play() }
        assertTrue(manager.state.value.isPlaying)

        every { mockPlayer.isPlaying } returns true
        manager.togglePlayPause()
        verify { mockPlayer.pause() }
        assertFalse(manager.state.value.isPlaying)
    }

    @Test
    fun seekTo_callsPlayerSeekAndUpdatesState() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        manager.seekTo(15_000L)

        verify { mockPlayer.seekTo(15_000L) }
        assertEquals(15_000L, manager.state.value.currentPositionMs)
    }

    @Test
    fun toggleShuffle_togglesPlayerShuffleMode() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        every { mockPlayer.shuffleModeEnabled } returns false
        manager.toggleShuffle()
        verify { mockPlayer.shuffleModeEnabled = true }
        assertTrue(manager.state.value.shuffleModeEnabled)

        every { mockPlayer.shuffleModeEnabled } returns true
        manager.toggleShuffle()
        verify { mockPlayer.shuffleModeEnabled = false }
        assertFalse(manager.state.value.shuffleModeEnabled)
    }

    @Test
    fun toggleRepeat_cyclesThroughRepeatModes() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        assertEquals(RepeatMode.OFF, manager.state.value.repeatMode)

        manager.toggleRepeat()
        assertEquals(RepeatMode.ALL, manager.state.value.repeatMode)
        verify { mockPlayer.repeatMode = Player.REPEAT_MODE_ALL }

        manager.toggleRepeat()
        assertEquals(RepeatMode.ONE, manager.state.value.repeatMode)
        verify { mockPlayer.repeatMode = Player.REPEAT_MODE_ONE }

        manager.toggleRepeat()
        assertEquals(RepeatMode.OFF, manager.state.value.repeatMode)
        verify { mockPlayer.repeatMode = Player.REPEAT_MODE_OFF }
    }

    @Test
    fun skipNext_callsSeekToNextMediaItem() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        every { mockPlayer.hasNextMediaItem() } returns true
        manager.skipNext()
        verify { mockPlayer.seekToNextMediaItem() }
    }

    @Test
    fun skipPrevious_fromMiddleOfTrack_seeksToStart() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        every { mockPlayer.currentPosition } returns 5_000L
        manager.skipPrevious()
        verify { mockPlayer.seekTo(0L) }
        assertEquals(0L, manager.state.value.currentPositionMs)
    }

    @Test
    fun skipPrevious_fromStartOfTrack_seeksToPrevious() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        every { mockPlayer.currentPosition } returns 1_000L
        every { mockPlayer.hasPreviousMediaItem() } returns true
        manager.skipPrevious()
        verify { mockPlayer.seekToPreviousMediaItem() }
    }

    @Test
    fun playerListener_eventsUpdateState() {
        val manager = createManager()
        val listenerSlot = slot<Player.Listener>()
        every { mockPlayer.addListener(capture(listenerSlot)) } returns Unit

        manager.attachPlayer(mockPlayer)
        val listener = listenerSlot.captured

        // isPlaying
        listener.onIsPlayingChanged(true)
        assertTrue(manager.state.value.isPlaying)

        // buffering state
        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertTrue(manager.state.value.isBuffering)

        listener.onPlaybackStateChanged(Player.STATE_READY)
        assertFalse(manager.state.value.isBuffering)

        // shuffle
        listener.onShuffleModeEnabledChanged(true)
        assertTrue(manager.state.value.shuffleModeEnabled)

        // repeat
        listener.onRepeatModeChanged(Player.REPEAT_MODE_ONE)
        assertEquals(RepeatMode.ONE, manager.state.value.repeatMode)
    }

    @Test
    fun pendingAction_executedWhenPlayerAttaches() {
        val manager = createManager()
        val track = TrackDto(id = "trk-deferred", title = "Deferred Song", duration = 120)

        // Play before player is attached
        manager.playTrack(track)
        assertEquals("trk-deferred", manager.state.value.currentTrack?.id)

        // Now attach player
        manager.attachPlayer(mockPlayer)

        verify { mockPlayer.setMediaItems(any(), 0, 0L) }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun release_cancelsScopeAndDetachesPlayer() {
        val isolatedScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined + kotlinx.coroutines.SupervisorJob())
        val manager = PlayerControllerManager(
            context = mockContext,
            baseUrl = "http://localhost:8000",
            coroutineScope = isolatedScope
        )
        manager.attachPlayer(mockPlayer)
        assertTrue(isolatedScope.isActive)
        assertNotNull(manager.player)

        manager.release()

        assertFalse(isolatedScope.isActive)
        assertNull(manager.player)
    }

    @Test
    fun applyCodec_repreparesPlayerAtCurrentPosition() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        val track = TrackDto(id = "trk-codec", title = "Codec Song", duration = 120)
        manager.playTrack(track)

        every { mockPlayer.currentPosition } returns 45_000L
        every { mockPlayer.isPlaying } returns true

        manager.applyCodec("flac")

        verify { mockPlayer.setMediaItems(any(), 0, 45_000L) }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun skipNext_atEndOfQueueWithoutRepeat_doesNotAdvance() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        val track1 = TrackDto(id = "trk-1", title = "Song 1")
        val track2 = TrackDto(id = "trk-2", title = "Song 2")
        val queue = listOf(track1, track2)

        manager.playTrack(track2, queue) // on last track

        every { mockPlayer.hasNextMediaItem() } returns false
        every { mockPlayer.repeatMode } returns Player.REPEAT_MODE_OFF
        every { mockPlayer.shuffleModeEnabled } returns false

        manager.skipNext()

        // Should NOT seek
        verify(exactly = 0) { mockPlayer.seekTo(any(), any()) }
    }

    @Test
    fun skipNext_atEndOfQueueWithRepeatAll_loopsToStart() {
        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        val track1 = TrackDto(id = "trk-1", title = "Song 1")
        val track2 = TrackDto(id = "trk-2", title = "Song 2")
        val queue = listOf(track1, track2)

        manager.playTrack(track2, queue) // on last track

        every { mockPlayer.hasNextMediaItem() } returns false
        every { mockPlayer.repeatMode } returns Player.REPEAT_MODE_ALL
        every { mockPlayer.shuffleModeEnabled } returns false

        manager.skipNext()

        verify { mockPlayer.seekTo(0, 0L) }
    }

    @Test
    fun playbackEnded_advancesToNextTrackInQueue() {
        val listenerSlot = slot<Player.Listener>()
        every { mockPlayer.addListener(capture(listenerSlot)) } returns Unit

        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        val track1 = TrackDto(id = "trk-1", title = "Song 1")
        val track2 = TrackDto(id = "trk-2", title = "Song 2")
        val queue = listOf(track1, track2)

        manager.playTrack(track1, queue)

        every { mockPlayer.hasNextMediaItem() } returns true
        every { mockPlayer.repeatMode } returns Player.REPEAT_MODE_OFF
        every { mockPlayer.shuffleModeEnabled } returns false

        // Simulate track ending
        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        verify { mockPlayer.seekToNextMediaItem() }
    }

    @Test
    fun playbackEnded_lastTrackWithRepeatOff_stopsPlayback() {
        val listenerSlot = slot<Player.Listener>()
        every { mockPlayer.addListener(capture(listenerSlot)) } returns Unit

        val manager = createManager()
        manager.attachPlayer(mockPlayer)

        val track1 = TrackDto(id = "trk-1", title = "Song 1")
        val queue = listOf(track1)

        manager.playTrack(track1, queue)

        every { mockPlayer.hasNextMediaItem() } returns false
        every { mockPlayer.repeatMode } returns Player.REPEAT_MODE_OFF
        every { mockPlayer.shuffleModeEnabled } returns false

        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertFalse(manager.state.value.isPlaying)
    }
}
