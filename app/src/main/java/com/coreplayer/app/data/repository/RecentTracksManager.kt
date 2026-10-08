package com.coreplayer.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.coreplayer.app.data.api.CorePlayerApi
import com.coreplayer.app.data.model.TrackDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecentTracksManager(
    private val prefs: SharedPreferences,
    moshi: Moshi = CorePlayerApi.createMoshi()
) {
    constructor(context: Context, moshi: Moshi = CorePlayerApi.createMoshi()) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        moshi
    )

    private val listType = Types.newParameterizedType(List::class.java, TrackDto::class.java)
    private val adapter = moshi.adapter<List<TrackDto>>(listType)

    private val _recentTracks = MutableStateFlow(loadRecent())
    val recentTracks: StateFlow<List<TrackDto>> = _recentTracks.asStateFlow()

    fun loadRecent(): List<TrackDto> {
        val json = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            (adapter.fromJson(json) ?: emptyList()).take(MAX_RECENT)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addTrack(track: TrackDto) {
        val current = _recentTracks.value.toMutableList()
        current.removeAll { it.id == track.id }
        current.add(0, track)
        val trimmed = current.take(MAX_RECENT)
        saveRecent(trimmed)
    }

    fun clear() {
        saveRecent(emptyList())
    }

    private fun saveRecent(list: List<TrackDto>) {
        _recentTracks.value = list
        val json = adapter.toJson(list)
        prefs.edit().putString(KEY_RECENT, json).apply()
    }

    companion object {
        private const val PREFS_NAME = "core_player_recent_tracks"
        private const val KEY_RECENT = "recent_tracks_json"
        const val MAX_RECENT = 4
    }
}
