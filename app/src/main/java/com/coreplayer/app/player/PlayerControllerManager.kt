package com.coreplayer.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.coreplayer.app.data.model.TrackDto
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

open class PlayerControllerManager(
    private val context: Context,
    var baseUrl: String = "",
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    internal var player: Player? = null
    private var currentQueue: List<TrackDto> = emptyList()
    private var positionJob: Job? = null
    private var pendingAction: (() -> Unit)? = null
    private var isCrossfading = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val track = currentQueue.find { it.id == mediaItem?.mediaId }
            val trackDurationMs = (track?.duration?.toLong()?.times(1000) ?: 0L).coerceAtLeast(0L)
            _state.update {
                it.copy(
                    currentTrack = track ?: it.currentTrack,
                    currentPositionMs = 0L,
                    durationMs = if (trackDurationMs > 0L) trackDurationMs else it.durationMs
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuffering = playbackState == Player.STATE_BUFFERING
            val p = player
            val duration = if (p != null && p.duration > 0L) p.duration else _state.value.durationMs
            _state.update {
                it.copy(
                    isBuffering = isBuffering,
                    durationMs = duration
                )
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mapped = when (repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
            _state.update { it.copy(repeatMode = mapped) }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.w(TAG, "Playback error: ${error.errorCodeName}, attempting auto-reconnect", error)
            coroutineScope.launch {
                delay(2000L)
                val p = player ?: return@launch
                val cur = _state.value.currentTrack
                if (cur != null) {
                    val pos = _state.value.currentPositionMs
                    p.prepare()
                    p.seekTo(pos)
                    p.play()
                }
            }
        }
    }

    init {
        connect()
    }

    fun connect() {
        if (player != null || controllerFuture != null) return
        try {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        val mediaController = future.get()
                        attachPlayer(mediaController)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "MediaController connection failed or cancelled", e)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Context or service unavailable for MediaController", e)
        }
    }

    internal fun attachPlayer(newPlayer: Player) {
        player?.removeListener(playerListener)
        player = newPlayer
        newPlayer.addListener(playerListener)

        if (pendingAction != null) {
            val action = pendingAction
            pendingAction = null
            action?.invoke()
        } else {
            updateStateFromPlayer()
        }

        startPositionUpdates()
    }

    private fun updateStateFromPlayer() {
        val p = player ?: return
        val currentMediaItem = p.currentMediaItem
        val track = currentQueue.find { it.id == currentMediaItem?.mediaId }
            ?: _state.value.currentTrack

        val repeat = when (p.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }

        val isBuffering = p.playbackState == Player.STATE_BUFFERING
        val duration = if (p.duration > 0L) {
            p.duration
        } else {
            (track?.duration?.toLong()?.times(1000) ?: 0L).coerceAtLeast(0L)
        }
        val position = p.currentPosition.coerceAtLeast(0L)

        _state.update { current ->
            current.copy(
                currentTrack = track,
                isPlaying = p.isPlaying,
                isBuffering = isBuffering,
                currentPositionMs = position,
                durationMs = duration,
                shuffleModeEnabled = p.shuffleModeEnabled,
                repeatMode = repeat,
                queue = currentQueue
            )
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = coroutineScope.launch {
            while (isActive) {
                val p = player
                if (p != null && p.isPlaying) {
                    val pos = p.currentPosition.coerceAtLeast(0L)
                    val dur = if (p.duration > 0L) p.duration else _state.value.durationMs
                    _state.update { it.copy(currentPositionMs = pos, durationMs = dur) }

                    val isCrossfade = try { com.coreplayer.app.CorePlayerApp.instance.crossfadeEnabled } catch (_: Exception) { true }
                    if (isCrossfade && dur > 12000L && pos >= dur - 4000L && !isCrossfading) {
                        val next = getNextTrackInQueue()
                        val svc = PlaybackService.instance
                        if (next != null && svc != null) {
                            isCrossfading = true
                            val codec = try { com.coreplayer.app.CorePlayerApp.instance.audioCodec } catch (_: Exception) { "auto" }
                            val mediaItem = next.toMediaItem(baseUrl, codec)
                            _state.update {
                                it.copy(
                                    currentTrack = next,
                                    currentPositionMs = 0L,
                                    durationMs = (next.duration * 1000L).coerceAtLeast(0L),
                                    isPlaying = true
                                )
                            }
                            svc.crossfadeTo(mediaItem, 3500L) {
                                isCrossfading = false
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun playTrack(track: TrackDto, queue: List<TrackDto> = emptyList()) {
        val effectiveQueue = if (queue.isEmpty()) {
            listOf(track)
        } else if (queue.none { it.id == track.id }) {
            listOf(track) + queue
        } else {
            queue
        }
        currentQueue = effectiveQueue

        _state.update {
            it.copy(
                currentTrack = track,
                queue = effectiveQueue,
                currentPositionMs = 0L,
                durationMs = (track.duration * 1000L).coerceAtLeast(0L),
                isPlaying = true,
                isBuffering = true
            )
        }

        val action: () -> Unit = {
            val startIndex = currentQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val codec = try { com.coreplayer.app.CorePlayerApp.instance.audioCodec } catch (e: Exception) { "auto" }
            val mediaItems = currentQueue.map { it.toMediaItem(baseUrl, codec) }

            player?.run {
                setMediaItems(mediaItems, startIndex, 0L)
                prepare()
                play()
            }
        }

        if (player != null) {
            action()
        } else {
            pendingAction = action
        }
    }

    /**
     * Applies a new audio codec immediately to currently playing queue.
     * Re-prepares current track at current playback position seamlessly.
     */
    fun applyCodec(newCodec: String) {
        val p = player ?: return
        val currentTrack = _state.value.currentTrack ?: return
        val currentPos = p.currentPosition.coerceAtLeast(0L)
        val isPlaying = p.isPlaying

        val startIndex = currentQueue.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)
        val mediaItems = currentQueue.map { it.toMediaItem(baseUrl, newCodec) }

        p.setMediaItems(mediaItems, startIndex, currentPos)
        p.prepare()
        if (isPlaying) {
            p.play()
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            _state.update { it.copy(isPlaying = false) }
        } else {
            p.play()
            _state.update { it.copy(isPlaying = true) }
        }
    }

    fun play() {
        player?.play()
        _state.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        player?.pause()
        _state.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        val validPos = positionMs.coerceAtLeast(0L)
        player?.seekTo(validPos)
        _state.update { it.copy(currentPositionMs = validPos) }
    }

    fun getNextTrackInQueue(): TrackDto? {
        val queue = currentQueue
        if (queue.isEmpty()) return null
        val currentTrack = _state.value.currentTrack
        val currentIndex = if (currentTrack != null) {
            queue.indexOfFirst { it.id == currentTrack.id }
        } else {
            player?.currentMediaItemIndex ?: -1
        }.let { if (it >= 0) it else 0 }

        val isShuffle = player?.shuffleModeEnabled ?: _state.value.shuffleModeEnabled
        val isRepeatAll = (player?.repeatMode == Player.REPEAT_MODE_ALL) || _state.value.repeatMode == RepeatMode.ALL

        if (isShuffle && queue.size > 1) {
            val candidates = queue.indices.filter { it != currentIndex }
            val nextIndex = candidates.randomOrNull() ?: 0
            return queue.getOrNull(nextIndex)
        }

        if (currentIndex in 0 until queue.size - 1) {
            return queue[currentIndex + 1]
        }

        if (isRepeatAll && queue.isNotEmpty()) {
            return queue[0]
        }

        return null
    }

    fun getPreviousTrackInQueue(): TrackDto? {
        val queue = currentQueue
        if (queue.isEmpty()) return null
        val currentTrack = _state.value.currentTrack
        val currentIndex = if (currentTrack != null) {
            queue.indexOfFirst { it.id == currentTrack.id }
        } else {
            player?.currentMediaItemIndex ?: -1
        }.let { if (it >= 0) it else 0 }

        if (currentIndex > 0) {
            return queue[currentIndex - 1]
        }

        val isRepeatAll = (player?.repeatMode == Player.REPEAT_MODE_ALL) || _state.value.repeatMode == RepeatMode.ALL
        if (isRepeatAll && queue.isNotEmpty()) {
            return queue.last()
        }

        return null
    }

    fun skipNext() {
        val isCrossfade = try { com.coreplayer.app.CorePlayerApp.instance.crossfadeEnabled } catch (_: Exception) { true }
        val next = getNextTrackInQueue()
        val svc = PlaybackService.instance
        if (isCrossfade && next != null && svc != null) {
            val codec = try { com.coreplayer.app.CorePlayerApp.instance.audioCodec } catch (_: Exception) { "auto" }
            val mediaItem = next.toMediaItem(baseUrl, codec)
            _state.update {
                it.copy(
                    currentTrack = next,
                    currentPositionMs = 0L,
                    durationMs = (next.duration * 1000L).coerceAtLeast(0L),
                    isPlaying = true
                )
            }
            svc.crossfadeTo(mediaItem, 1000L) {
                isCrossfading = false
            }
            return
        }

        val p = player ?: return
        val queue = currentQueue

        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
            return
        }

        if (queue.isEmpty()) return

        val currentTrack = _state.value.currentTrack
        val currentIndex = if (currentTrack != null) {
            queue.indexOfFirst { it.id == currentTrack.id }
        } else {
            p.currentMediaItemIndex
        }.let { if (it >= 0) it else p.currentMediaItemIndex }

        val isShuffle = p.shuffleModeEnabled || _state.value.shuffleModeEnabled
        val isRepeatAll = p.repeatMode == Player.REPEAT_MODE_ALL || _state.value.repeatMode == RepeatMode.ALL

        // If shuffle is enabled and queue has multiple tracks:
        if (isShuffle && queue.size > 1) {
            val candidateIndices = queue.indices.filter { it != currentIndex }
            val nextIndex = candidateIndices.randomOrNull() ?: 0
            val nextTrack = queue.getOrNull(nextIndex)
            if (nextTrack != null) {
                _state.update {
                    it.copy(
                        currentTrack = nextTrack,
                        currentPositionMs = 0L,
                        durationMs = (nextTrack.duration * 1000L).coerceAtLeast(0L),
                        isPlaying = true,
                        isBuffering = true
                    )
                }
            }
            p.seekTo(nextIndex, 0L)
            p.play()
            return
        }

        // If not at the end of the queue: advance to next item
        if (currentIndex in 0 until queue.size - 1) {
            val nextIndex = currentIndex + 1
            val nextTrack = queue[nextIndex]
            _state.update {
                it.copy(
                    currentTrack = nextTrack,
                    currentPositionMs = 0L,
                    durationMs = (nextTrack.duration * 1000L).coerceAtLeast(0L),
                    isPlaying = true,
                    isBuffering = true
                )
            }
            p.seekTo(nextIndex, 0L)
            p.play()
            return
        }

        // If at the last track of current queue:
        if (isRepeatAll && queue.isNotEmpty()) {
            val firstTrack = queue[0]
            _state.update {
                it.copy(
                    currentTrack = firstTrack,
                    currentPositionMs = 0L,
                    durationMs = (firstTrack.duration * 1000L).coerceAtLeast(0L),
                    isPlaying = true,
                    isBuffering = true
                )
            }
            p.seekTo(0, 0L)
            p.play()
            return
        }
    }

    fun skipPrevious() {
        val p = player ?: return
        if (p.currentPosition > 3000L) {
            p.seekTo(0L)
            _state.update { it.copy(currentPositionMs = 0L) }
            return
        }

        val isCrossfade = try { com.coreplayer.app.CorePlayerApp.instance.crossfadeEnabled } catch (_: Exception) { true }
        val prev = getPreviousTrackInQueue()
        val svc = PlaybackService.instance
        if (isCrossfade && prev != null && svc != null) {
            val codec = try { com.coreplayer.app.CorePlayerApp.instance.audioCodec } catch (_: Exception) { "auto" }
            val mediaItem = prev.toMediaItem(baseUrl, codec)
            _state.update {
                it.copy(
                    currentTrack = prev,
                    currentPositionMs = 0L,
                    durationMs = (prev.duration * 1000L).coerceAtLeast(0L),
                    isPlaying = true
                )
            }
            svc.crossfadeTo(mediaItem, 1000L) {
                isCrossfading = false
            }
            return
        }

        if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
            return
        }

        val queue = currentQueue
        val currentTrack = _state.value.currentTrack
        val currentIndex = if (currentTrack != null) {
            queue.indexOfFirst { it.id == currentTrack.id }
        } else {
            p.currentMediaItemIndex
        }.let { if (it >= 0) it else p.currentMediaItemIndex }

        if (currentIndex > 0 && queue.isNotEmpty()) {
            val prevIndex = currentIndex - 1
            val prevTrack = queue[prevIndex]
            _state.update {
                it.copy(
                    currentTrack = prevTrack,
                    currentPositionMs = 0L,
                    durationMs = (prevTrack.duration * 1000L).coerceAtLeast(0L),
                    isPlaying = true,
                    isBuffering = true
                )
            }
            p.seekTo(prevIndex, 0L)
            p.play()
        } else {
            p.seekTo(0L)
            _state.update { it.copy(currentPositionMs = 0L) }
        }
    }

    fun toggleShuffle() {
        val p = player ?: return
        val newShuffle = !p.shuffleModeEnabled
        p.shuffleModeEnabled = newShuffle
        _state.update { it.copy(shuffleModeEnabled = newShuffle) }
    }

    fun toggleRepeat() {
        val p = player ?: return
        val nextMode = _state.value.repeatMode.cycle()
        val mediaRepeat = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        p.repeatMode = mediaRepeat
        _state.update { it.copy(repeatMode = nextMode) }
    }

    fun release() {
        positionJob?.cancel()
        coroutineScope.cancel()
        player?.removeListener(playerListener)
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
            controllerFuture = null
        }
        player = null
    }

    companion object {
        private const val TAG = "PlayerControllerManager"
    }
}
