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

    fun skipNext() {
        val p = player ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val p = player ?: return
        if (p.currentPosition > 3000L) {
            p.seekTo(0L)
            _state.update { it.copy(currentPositionMs = 0L) }
        } else if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
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
