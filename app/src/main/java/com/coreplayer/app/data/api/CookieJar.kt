package com.coreplayer.app.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Thread-safe implementation of [CookieJar] that stores cookies across requests and persists
 * non-expired cookies to [SharedPreferences] across application restarts.
 * Manages session cookies (e.g. `session_id`) required for Core Player FastAPI backend.
 */
class InMemoryCookieJar(
    private val prefs: SharedPreferences? = null
) : CookieJar {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val cookieStore = mutableListOf<Cookie>()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, SerializableCookie::class.java)
    private val adapter: JsonAdapter<List<SerializableCookie>> = moshi.adapter(listType)

    init {
        loadFromPrefs()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        for (newCookie in cookies) {
            // Remove existing cookie with the same name matching the URL
            cookieStore.removeAll { existing ->
                existing.name == newCookie.name && existing.matches(url)
            }
            if (newCookie.expiresAt > now) {
                cookieStore.add(newCookie)
            }
        }
        persistToPrefs()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val expired = cookieStore.filter { it.expiresAt <= now }
        if (expired.isNotEmpty()) {
            cookieStore.removeAll(expired)
            persistToPrefs()
        }
        return cookieStore.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookieStore.clear()
        prefs?.edit()?.remove(KEY_COOKIES)?.apply()
    }

    @Synchronized
    fun getCookies(): List<Cookie> = cookieStore.toList()

    @Synchronized
    fun hasSessionCookie(cookieName: String = "session_id"): Boolean {
        val now = System.currentTimeMillis()
        return cookieStore.any { it.name == cookieName && it.expiresAt > now }
    }

    private fun loadFromPrefs() {
        val preferences = prefs ?: return
        val json = preferences.getString(KEY_COOKIES, null) ?: return
        try {
            val list = adapter.fromJson(json) ?: return
            val now = System.currentTimeMillis()
            val valid = list.map { it.toCookie() }.filter { it.expiresAt > now }
            synchronized(this) {
                cookieStore.clear()
                cookieStore.addAll(valid)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore cookies from SharedPreferences", e)
        }
    }

    private fun persistToPrefs() {
        val preferences = prefs ?: return
        try {
            val now = System.currentTimeMillis()
            val validCookies = cookieStore.filter { it.expiresAt > now }
            val serializable = validCookies.map { SerializableCookie.fromCookie(it) }
            val json = adapter.toJson(serializable)
            preferences.edit().putString(KEY_COOKIES, json).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist cookies to SharedPreferences", e)
        }
    }

    companion object {
        private const val TAG = "CookieJar"
        const val PREFS_NAME = "core_player_cookies"
        const val KEY_COOKIES = "saved_cookies_json"
    }
}

/**
 * Serializable DTO representing an OkHttp [Cookie] for persistent storage.
 */
data class SerializableCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val hostOnly: Boolean = false
) {
    fun toCookie(): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
        if (hostOnly) {
            builder.hostOnlyDomain(domain)
        } else {
            builder.domain(domain)
        }
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }

    companion object {
        fun fromCookie(cookie: Cookie): SerializableCookie = SerializableCookie(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt,
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly
        )
    }
}
