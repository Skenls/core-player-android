package com.coreplayer.app.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
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
import kotlinx.coroutines.job
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
 * 5. Uses a dedicated download data source and cancels in-flight downloads cleanly to prevent
 *    thread contention and server 502 errors.
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

    /**
     * Playback data source factory for ExoPlayer:
     * Reads directly from SimpleCache when cached; falls back to upstream OkHttp when not.
     * Disables write sink (null) so ExoPlayer playback never conflicts with background CacheWriter.
     */
    val playbackDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Backward-compatible alias for playback data source.
     */
    val cacheDataSourceFactory: CacheDataSource.Factory
        get() = playbackDataSourceFactory

    /**
     * Dedicated data source factory for background CacheWriter pre-caching.
     */
    val downloadDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private var cachingJob: Job? = null
    private var idleTimeoutJob: Job? = null
    @Volatile
    private var currentWriter: CacheWriter? = null

    @Volatile
    private var activeTrackId: String? = null
    @Volatile
    private var activeCodec: String? = null
    @Volatile
    private var activeNextTrackId: String? = null
    @Volatile
    private var activeAlbumId: String? = null
    @Volatile
    private var previousTrackId: String? = null
    private val cachedUrls = mutableSetOf<String>()

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

        // De-duplicate: If already caching this track with this codec, do not cancel & restart!
        if (currentTrack.id == activeTrackId &&
            codec == activeCodec &&
            nextTrack?.id == activeNextTrackId &&
            cachingJob?.isActive == true
        ) {
            return
        }

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
        activeCodec = codec
        activeNextTrackId = nextTrack?.id

        // Cancel existing writer and job immediately
        cachingJob?.cancel()
        try {
            currentWriter?.cancel()
        } catch (_: Exception) {}
        currentWriter = null

        // Start two-stage background caching pipeline
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

        // Check if already fully cached in SimpleCache
        val isAlreadyCached = try {
            val meta = cache.getContentMetadata(streamUrl)
            val contentLength = ContentMetadata.getContentLength(meta)
            contentLength > 0 && cache.isCached(streamUrl, 0, contentLength)
        } catch (_: Exception) { false }

        if (isAlreadyCached) {
            Log.d(TAG, "Track already fully cached: $streamUrl")
            cachedUrls.add(streamUrl)
            return@withContext
        }

        var writer: CacheWriter? = null
        try {
            val uri = Uri.parse(streamUrl)
            val dataSpec = DataSpec(uri)
            val downloadingDataSource = downloadDataSourceFactory.createDataSourceForDownloading()
            // High-throughput 256 KB buffer for fast streaming
            val tempBuffer = ByteArray(256 * 1024)
            writer = CacheWriter(downloadingDataSource, dataSpec, tempBuffer, null)
            currentWriter = writer

            // Attach cancellation listener so writer terminates immediately when coroutine cancels
            val job = coroutineContext.job
            val handle = job.invokeOnCompletion { cause ->
                if (cause != null) {
                    try {
                        writer.cancel()
                    } catch (_: Exception) {}
                }
            }

            Log.d(TAG, "Starting full pre-cache for: $streamUrl")
            writer.cache()
            val totalBytes = cache.getCachedBytes(streamUrl, 0L, -1L)
            if (totalBytes > 0) {
                try {
                    val mutations = ContentMetadataMutations()
                    ContentMetadataMutations.setContentLength(mutations, totalBytes)
                    cache.applyContentMetadataMutations(streamUrl, mutations)
                    Log.d(TAG, "Applied ContentLength $totalBytes to cache metadata for: $streamUrl")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to apply ContentLength to cache metadata: ${e.message}")
                }
            }
            cachedUrls.add(streamUrl)
            Log.d(TAG, "Pre-cache completed for: $streamUrl")
            handle.dispose()
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException && !e.javaClass.name.contains("Interrupted")) {
                Log.w(TAG, "Error caching $streamUrl: ${e.message}")
            }
        } finally {
            if (currentWriter === writer) {
                currentWriter = null
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
                Log.d(TAG, "Idle timeout reached. Clearing track cache.")
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
        try {
            currentWriter?.cancel()
        } catch (_: Exception) {}
        currentWriter = null
        clearAllInternal()
    }

    private fun clearAllInternal() {
        try {
            currentWriter?.cancel()
        } catch (_: Exception) {}
        currentWriter = null
        scope.launch {
            try {
                for (key in cache.keys) {
                    try {
                        cache.removeResource(key)
                    } catch (_: Exception) {}
                }
                cachedUrls.clear()
                activeTrackId = null
                activeCodec = null
                activeNextTrackId = null
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
