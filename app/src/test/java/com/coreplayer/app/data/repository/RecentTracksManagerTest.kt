package com.coreplayer.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.coreplayer.app.data.model.TrackDto
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentTracksManagerTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val memoryStorage = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        memoryStorage.clear()
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockPrefs.getString("recent_tracks_json", null) } answers {
            memoryStorage["recent_tracks_json"]
        }
        every { mockEditor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            memoryStorage[key] = value
            mockEditor
        }
        every { mockEditor.apply() } returns Unit
        every { mockPrefs.edit() } returns mockEditor
    }

    @Test
    fun addTrack_capsAtMaxRecentFourAndPushesToFront() {
        val manager = RecentTracksManager(mockPrefs)

        val track1 = TrackDto(id = "1", title = "Track 1", artist = "Artist 1")
        val track2 = TrackDto(id = "2", title = "Track 2", artist = "Artist 2")
        val track3 = TrackDto(id = "3", title = "Track 3", artist = "Artist 3")
        val track4 = TrackDto(id = "4", title = "Track 4", artist = "Artist 4")
        val track5 = TrackDto(id = "5", title = "Track 5", artist = "Artist 5")

        manager.addTrack(track1)
        manager.addTrack(track2)
        manager.addTrack(track3)
        manager.addTrack(track4)
        manager.addTrack(track5)

        val recent = manager.recentTracks.value
        assertEquals(4, recent.size)
        assertEquals("5", recent[0].id)
        assertEquals("4", recent[1].id)
        assertEquals("3", recent[2].id)
        assertEquals("2", recent[3].id)
    }

    @Test
    fun addTrack_existingTrack_movesToFrontWithoutDuplicates() {
        val manager = RecentTracksManager(mockPrefs)

        val track1 = TrackDto(id = "1", title = "Track 1", artist = "Artist 1")
        val track2 = TrackDto(id = "2", title = "Track 2", artist = "Artist 2")

        manager.addTrack(track1)
        manager.addTrack(track2)
        manager.addTrack(track1) // re-add track 1

        val recent = manager.recentTracks.value
        assertEquals(2, recent.size)
        assertEquals("1", recent[0].id)
        assertEquals("2", recent[1].id)
    }

    @Test
    fun clear_emptiesRecentTracks() {
        val manager = RecentTracksManager(mockPrefs)
        val track1 = TrackDto(id = "1", title = "Track 1", artist = "Artist 1")
        manager.addTrack(track1)
        assertEquals(1, manager.recentTracks.value.size)

        manager.clear()
        assertTrue(manager.recentTracks.value.isEmpty())
    }
}
