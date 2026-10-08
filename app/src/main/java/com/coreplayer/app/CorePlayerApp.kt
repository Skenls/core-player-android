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
            .build()
    }

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
            prefs.edit().putString("server_url", value).apply()
            initRepositories(value)
        }

    var audioCodec: String
        get() = prefs.getString("audio_codec", "auto") ?: "auto"
        set(value) {
            prefs.edit().putString("audio_codec", value).apply()
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
