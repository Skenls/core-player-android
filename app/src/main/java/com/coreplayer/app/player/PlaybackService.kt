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
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
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

    private var audioManager: android.media.AudioManager? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                CorePlayerApp.instance.playerController.pause()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try {
                    if (::activePlayer.isInitialized) activePlayer.volume = 0.2f
                } catch (_: Exception) {}
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                try {
                    if (::activePlayer.isInitialized) activePlayer.volume = 1.0f
                } catch (_: Exception) {}
            }
        }
    }

    private val becomingNoisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                CorePlayerApp.instance.playerController.pause()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this

        audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        val filter = android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, filter)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setMp3ExtractorFlags(
                Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS or
                Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING
            )
            .setAdtsExtractorFlags(
                AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
            )

        val playbackDataSourceFactory = TrackCacheManager.getInstance(this, CorePlayerApp.instance.okHttpClient).playbackDataSourceFactory
        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory).setDataSourceFactory(playbackDataSourceFactory)

        primaryPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(false)
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

        val coilBitmapLoader = CoilBitmapLoader(this, coil.Coil.imageLoader(this))
        val sessionBuilder = MediaSession.Builder(this, activePlayer)
            .setCallback(MediaSessionCallback())
            .setBitmapLoader(androidx.media3.session.CacheBitmapLoader(coilBitmapLoader))

        if (sessionActivityPendingIntent != null) {
            sessionBuilder.setSessionActivity(sessionActivityPendingIntent)
        }

        mediaSession = sessionBuilder.build()

        val notificationProvider = androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                setSmallIcon(com.coreplayer.app.R.mipmap.ic_launcher)
            }
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    fun seekTo(positionMs: Long) {
        try {
            if (::activePlayer.isInitialized) {
                activePlayer.seekTo(positionMs)
            }
        } catch (e: Exception) {
            android.util.Log.w("PlaybackService", "seekTo failed: ${e.message}")
        }
    }

    val currentPosition: Long
        get() = try { if (::activePlayer.isInitialized) activePlayer.currentPosition else 0L } catch (_: Exception) { 0L }

    val duration: Long
        get() = try { if (::activePlayer.isInitialized) activePlayer.duration else 0L } catch (_: Exception) { 0L }

    val isPlaying: Boolean
        get() = try { if (::activePlayer.isInitialized) activePlayer.isPlaying else false } catch (_: Exception) { false }


    /**
     * Crossfades playback from the currently active player to the incoming media item.
     * The outgoing track's volume ramps down while the incoming track's volume ramps up,
     * providing an overlapping crossfade without silence.
     */
    fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val am = audioManager ?: return true
        val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val playbackAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = (result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val am = audioManager ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    fun crossfadeTo(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        durationMs: Long = 3500L,
        onCompleted: (() -> Unit)? = null
    ) {
        requestAudioFocus()
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
            outgoing.clearMediaItems()
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
        try { unregisterReceiver(becomingNoisyReceiver) } catch (_: Exception) {}
        abandonAudioFocus()
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
