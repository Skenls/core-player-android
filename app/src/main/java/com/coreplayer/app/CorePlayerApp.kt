package com.coreplayer.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.coreplayer.app.data.api.CorePlayerApi
import com.coreplayer.app.data.api.InMemoryCookieJar
import com.coreplayer.app.data.repository.AuthRepository
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.data.repository.LyricsRepository
import com.coreplayer.app.data.repository.SearchHistoryManager
import com.coreplayer.app.player.PlayerControllerManager
import okhttp3.OkHttpClient

class CorePlayerApp : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: CorePlayerApp
            internal set
    }

    private val prefs by lazy {
        getSharedPreferences("core_player_prefs", Context.MODE_PRIVATE)
    }

    val cookieJar by lazy { InMemoryCookieJar(this) }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                if (path.contains("/api/stream/") && !path.contains("/cover/")) {
                    val range = request.header("Range")
                    val newRange = when {
                        range == null -> "bytes=0-2147483647"
                        range == "bytes=0-" -> "bytes=0-2147483647"
                        range.endsWith("-") -> "${range}2147483647"
                        else -> range
                    }
                    if (newRange != range) {
                        chain.proceed(request.newBuilder().header("Range", newRange).build())
                    } else {
                        chain.proceed(request)
                    }
                } else {
                    chain.proceed(request)
                }
            }
            .build()
    }

    val searchHistoryManager by lazy { SearchHistoryManager(this) }
    val recentTracksManager by lazy { com.coreplayer.app.data.repository.RecentTracksManager(this) }

    lateinit var playerController: PlayerControllerManager
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var libraryRepository: LibraryRepository
        private set

    lateinit var lyricsRepository: LyricsRepository
        private set

    var serverUrl: String
        get() = prefs.getString("server_url", "http://localhost:8000") ?: "http://localhost:8000"
        set(value) {
            val oldUrl = prefs.getString("server_url", "") ?: ""
            prefs.edit().putString("server_url", value).apply()
            initRepositories(value)
            if (oldUrl.isNotBlank() && oldUrl.trimEnd('/') != value.trimEnd('/')) {
                recentTracksManager.clear()
            }
        }

    var audioCodec: String
        get() = prefs.getString("audio_codec", "auto") ?: "auto"
        set(value) {
            prefs.edit().putString("audio_codec", value).apply()
        }

    var playerBackgroundMode: String
        get() = prefs.getString("player_bg_mode", "cover") ?: "cover"
        set(value) {
            prefs.edit().putString("player_bg_mode", value).apply()
        }

    val adaptiveBubbleFlow by lazy {
        kotlinx.coroutines.flow.MutableStateFlow(adaptiveBubbleColor)
    }

    var adaptiveBubbleColor: Boolean
        get() = prefs.getBoolean("adaptive_bubble_color", true)
        set(value) {
            prefs.edit().putBoolean("adaptive_bubble_color", value).apply()
            adaptiveBubbleFlow.value = value
        }

    val amoledFlow by lazy {
        kotlinx.coroutines.flow.MutableStateFlow(amoledMode)
    }

    var amoledMode: Boolean
        get() = prefs.getBoolean("amoled_mode", false)
        set(value) {
            prefs.edit().putBoolean("amoled_mode", value).apply()
            amoledFlow.value = value
        }

    val crossfadeFlow by lazy {
        kotlinx.coroutines.flow.MutableStateFlow(crossfadeEnabled)
    }

    var crossfadeEnabled: Boolean
        get() = prefs.getBoolean("crossfade_enabled", true)
        set(value) {
            prefs.edit().putBoolean("crossfade_enabled", value).apply()
            crossfadeFlow.value = value
        }

    fun updateCodec(newCodec: String) {
        audioCodec = newCodec
        if (::playerController.isInitialized) {
            playerController.applyCodec(newCodec)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        playerController = PlayerControllerManager(this, baseUrl = serverUrl)
        initRepositories(serverUrl)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
    }

    fun initRepositories(url: String) {
        val api = CorePlayerApi.create(baseUrl = url, cookieJar = cookieJar, okHttpClient = okHttpClient)
        authRepository = AuthRepository(api, cookieJar)
        libraryRepository = LibraryRepository(api)
        lyricsRepository = LyricsRepository(api)
        if (::playerController.isInitialized) {
            playerController.baseUrl = url
        }
    }
}
