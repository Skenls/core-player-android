package com.coreplayer.app.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.coreplayer.app.data.model.TrackDto

enum class RepeatMode {
    OFF,
    ALL,
    ONE;

    fun cycle(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

fun formatTime(timeMs: Long): String {
    if (timeMs <= 0L) return "00:00"
    val totalSeconds = timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

data class PlayerState(
    val currentTrack: TrackDto? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<TrackDto> = emptyList()
) {
    val progress: Float
        get() = if (durationMs > 0L) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val currentPositionFormatted: String
        get() = formatTime(currentPositionMs)

    val durationFormatted: String
        get() = formatTime(durationMs)
}

fun TrackDto.toMediaItem(baseUrl: String, codec: String = "auto"): MediaItem {
    val cleanBaseUrl = baseUrl.trimEnd('/')
    val streamUri = if (codec.isBlank() || codec == "auto" || codec == "raw") {
        "$cleanBaseUrl/api/stream/$id"
    } else {
        "$cleanBaseUrl/api/stream/$id?format=$codec"
    }

    val effectiveCover = coverArt?.takeIf { it.isNotBlank() }
        ?: albumId?.takeIf { it.isNotBlank() }?.let { "al-$it" }
    val artworkUriString = com.coreplayer.app.ui.util.resolveCoverUrl(effectiveCover, baseUrl, size = 512)

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply {
            artworkUriString?.let { setArtworkUri(Uri.parse(it)) }
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUri)
        .setMediaMetadata(metadata)
        .build()
}
