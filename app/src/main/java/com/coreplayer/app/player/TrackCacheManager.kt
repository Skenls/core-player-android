package com.coreplayer.app.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.coreplayer.app.data.model.TrackDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * Manages audio track caching using Media3's SimpleCache and CacheWriter.
 *
 * Implements a smart two-stage caching pipeline:
 * 1. Pre-caches current playing track completely in the active codec (eliminating seek latency).
 * 2. Once current track completes, pre-caches the next track in queue completely.
 * 3. On skipping forward, keeps the previous track cached until the next skip.
 * 4. Evicts cache on album switches, jumps to uncached tracks, or after an idle timeout.
 */
@OptIn(UnstableApi::class)
class TrackCacheManager private constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val cacheDir: File = File(context.cacheDir, "media_cache").apply { mkdirs() }
    val databaseProvider = StandaloneDatabaseProvider(context)
    val evictor = LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024) // 500 MB max

    val cache: SimpleCache by lazy {
        SimpleCache(cacheDir, evictor, databaseProvider)
    }

    val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)

    val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private var cachingJob: Job? = null
    private var idleTimeoutJob: Job? = null

    private var activeTrackId: String? = null
    private var activeAlbumId: String? = null
    private var previousTrackId: String? = null
    private var cachedUrls = mutableSetOf<String>()

    /**
     * Resolves the stream URI for a track with the given baseUrl and codec.
     */
    fun getStreamUrl(track: TrackDto, baseUrl: String, codec: String): String {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        return if (codec.isBlank() || codec == "auto" || codec == "raw") {
            "$cleanBaseUrl/api/stream/${track.id}"
        } else {
            "$cleanBaseUrl/api/stream/${track.id}?format=$codec"
        }
    }

    /**
     * Called when a track starts playing. Handles two-stage caching:
     * - Stage 1: Caches current track completely.
     * - Stage 2: Caches next track completely.
     */
    @Synchronized
    fun onTrackStarted(
        currentTrack: TrackDto,
        queue: List<TrackDto>,
        baseUrl: String,
        codec: String,
        nextTrack: TrackDto?
    ) {
        cancelIdleTimer()

        val trackAlbum = currentTrack.albumId ?: currentTrack.album

        // Check 1: Album switch -> clear all cache
        if (activeAlbumId != null && trackAlbum.isNotBlank() && activeAlbumId != trackAlbum) {
            Log.d(TAG, "Album changed from $activeAlbumId to $trackAlbum. Clearing track cache.")
            clearAllInternal()
        }
        activeAlbumId = trackAlbum.ifBlank { null }

        // Check 2: Jump to uncached track (neither active, previous, nor next)
        val isAdjacent = activeTrackId == null ||
            currentTrack.id == activeTrackId ||
            currentTrack.id == previousTrackId ||
            (nextTrack != null && currentTrack.id == nextTrack.id)

        if (!isAdjacent && activeTrackId != null) {
            Log.d(TAG, "Jumped to distant track ${currentTrack.id}. Clearing non-adjacent cache.")
            clearAllInternal()
        }

        // Track retention: Previous track remains cached until the next skip
        if (activeTrackId != null && activeTrackId != currentTrack.id) {
            // Evict older track (before previous)
            if (previousTrackId != null && previousTrackId != activeTrackId) {
                evictTrackById(previousTrackId!!, baseUrl, codec)
            }
            previousTrackId = activeTrackId
        }
        activeTrackId = currentTrack.id

        // Start two-stage background caching pipeline
        cachingJob?.cancel()
        cachingJob = scope.launch {
            try {
                // Stage 1: Pre-cache current track completely
                val currentUrl = getStreamUrl(currentTrack, baseUrl, codec)
                cacheTrack(currentUrl)

                // Stage 2: Pre-cache next track completely once current is cached
                if (isActive && nextTrack != null && nextTrack.id != currentTrack.id) {
                    val nextUrl = getStreamUrl(nextTrack, baseUrl, codec)
                    cacheTrack(nextUrl)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w(TAG, "Background pre-caching failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun cacheTrack(streamUrl: String) = withContext(Dispatchers.IO) {
        if (!isActive) return@withContext
        try {
            val uri = Uri.parse(streamUrl)
            val dataSpec = DataSpec(uri)
            val downloadingDataSource = cacheDataSourceFactory.createDataSourceForDownloading()
            val writer = CacheWriter(downloadingDataSource, dataSpec, null, null)

            Log.d(TAG, "Starting full pre-cache for: $streamUrl")
            writer.cache()
            cachedUrls.add(streamUrl)
            Log.d(TAG, "Pre-cache completed for: $streamUrl")
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Log.w(TAG, "Error caching $streamUrl: ${e.message}")
            }
        }
    }

    private fun evictTrackById(trackId: String, baseUrl: String, codec: String) {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val urlsToEvict = listOf(
            "$cleanBaseUrl/api/stream/$trackId",
            "$cleanBaseUrl/api/stream/$trackId?format=$codec",
            "$cleanBaseUrl/api/stream/$trackId?format=mp3",
            "$cleanBaseUrl/api/stream/$trackId?format=flac",
            "$cleanBaseUrl/api/stream/$trackId?format=aac",
            "$cleanBaseUrl/api/stream/$trackId?format=opus"
        )
        for (url in urlsToEvict) {
            try {
                cache.removeResource(url)
                cachedUrls.remove(url)
            } catch (_: Exception) {}
        }
        Log.d(TAG, "Evicted cached track: $trackId")
    }

    /**
     * Called when playback state changes.
     * Manages idle timeout: If paused/stopped for 10 minutes, clears cache.
     */
    @Synchronized
    fun onPlaybackState(isPlaying: Boolean) {
        if (isPlaying) {
            cancelIdleTimer()
        } else {
            startIdleTimer()
        }
    }

    private fun startIdleTimer() {
        idleTimeoutJob?.cancel()
        idleTimeoutJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (isActive) {
                Log.d(TAG, "Playback idle timeout expired (${IDLE_TIMEOUT_MS / 60000}m). Evicting cache.")
                clearAll()
            }
        }
    }

    private fun cancelIdleTimer() {
        idleTimeoutJob?.cancel()
        idleTimeoutJob = null
    }

    @Synchronized
    fun clearAll() {
        cachingJob?.cancel()
        cachingJob = null
        clearAllInternal()
    }

    private fun clearAllInternal() {
        scope.launch {
            try {
                for (key in cache.keys) {
                    try {
                        cache.removeResource(key)
                    } catch (_: Exception) {}
                }
                cachedUrls.clear()
                activeTrackId = null
                activeAlbumId = null
                previousTrackId = null
                Log.d(TAG, "Cleared all cached tracks.")
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing cache", e)
            }
        }
    }

    companion object {
        private const val TAG = "TrackCacheManager"
        private const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes

        @Volatile
        private var instance: TrackCacheManager? = null

        fun getInstance(context: Context, okHttpClient: OkHttpClient): TrackCacheManager {
            return instance ?: synchronized(this) {
                instance ?: TrackCacheManager(context.applicationContext, okHttpClient).also {
                    instance = it
                }
            }
        }
    }
}
