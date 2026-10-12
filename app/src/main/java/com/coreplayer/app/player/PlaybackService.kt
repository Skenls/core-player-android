package com.coreplayer.app.player

import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.coreplayer.app.CorePlayerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    companion object {
        var instance: PlaybackService? = null
            internal set
    }

    private var mediaSession: MediaSession? = null

    private lateinit var primaryPlayer: ExoPlayer
    private lateinit var secondaryPlayer: ExoPlayer
    private lateinit var activePlayer: ExoPlayer
    private lateinit var inactivePlayer: ExoPlayer

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var crossfadeJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
        val cacheDataSourceFactory = TrackCacheManager.getInstance(this, CorePlayerApp.instance.okHttpClient).cacheDataSourceFactory
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory)

        primaryPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        secondaryPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(false)
            .build()

        activePlayer = primaryPlayer
        inactivePlayer = secondaryPlayer

        val sessionActivityPendingIntent = packageManager
            ?.getLaunchIntentForPackage(packageName)
            ?.let { sessionIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    sessionIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        val sessionBuilder = MediaSession.Builder(this, activePlayer)
            .setCallback(MediaSessionCallback())

        if (sessionActivityPendingIntent != null) {
            sessionBuilder.setSessionActivity(sessionActivityPendingIntent)
        }

        mediaSession = sessionBuilder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * Crossfades playback from the currently active player to the incoming media item.
     * The outgoing track's volume ramps down while the incoming track's volume ramps up,
     * providing an overlapping crossfade without silence.
     */
    fun crossfadeTo(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        durationMs: Long = 3500L,
        onCompleted: (() -> Unit)? = null
    ) {
        crossfadeJob?.cancel()

        val incoming = inactivePlayer
        val outgoing = activePlayer

        incoming.stop()
        incoming.clearMediaItems()
        val safeIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
        incoming.setMediaItems(mediaItems, safeIndex, 0L)
        incoming.repeatMode = outgoing.repeatMode
        incoming.shuffleModeEnabled = outgoing.shuffleModeEnabled
        incoming.prepare()
        incoming.volume = 0f
        incoming.play()

        activePlayer = incoming
        inactivePlayer = outgoing
        mediaSession?.setPlayer(activePlayer)

        crossfadeJob = serviceScope.launch {
            val totalSteps = (durationMs / 100L).coerceAtLeast(10L).toInt()
            val stepDelay = durationMs / totalSteps
            for (i in 1..totalSteps) {
                delay(stepDelay)
                val frac = i.toFloat() / totalSteps
                incoming.volume = frac
                outgoing.volume = (1f - frac).coerceAtLeast(0f)
            }
            outgoing.stop()
            outgoing.volume = 1f
            incoming.volume = 1f
            onCompleted?.invoke()
        }
    }

    fun crossfadeTo(
        nextMediaItem: MediaItem,
        durationMs: Long = 3500L,
        onCompleted: (() -> Unit)? = null
    ) {
        crossfadeTo(listOf(nextMediaItem), 0, durationMs, onCompleted)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        crossfadeJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        try { primaryPlayer.release() } catch (_: Exception) {}
        try { secondaryPlayer.release() } catch (_: Exception) {}
        instance = null
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands
            val playerCommands = connectionResult.availablePlayerCommands
                .buildUpon()
                .add(androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(androidx.media3.common.Player.COMMAND_SEEK_BACK)
                .add(androidx.media3.common.Player.COMMAND_SEEK_FORWARD)
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: android.content.Intent
        ): Boolean {
            val keyEvent = intent.getParcelableExtra<android.view.KeyEvent>(android.content.Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        CorePlayerApp.instance.playerController.skipNext()
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        CorePlayerApp.instance.playerController.skipPrevious()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }
    }
}
