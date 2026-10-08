package com.coreplayer.app.data.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * In-memory thread-safe implementation of [CookieJar] that stores cookies across requests.
 * Manages session cookies (e.g. `session_id`) required for Core Player FastAPI backend.
 */
class InMemoryCookieJar : CookieJar {

    private val cookieStore = mutableListOf<Cookie>()

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
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        // Purge expired cookies
        cookieStore.removeAll { it.expiresAt <= now }
        return cookieStore.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookieStore.clear()
    }

    @Synchronized
    fun getCookies(): List<Cookie> = cookieStore.toList()

    @Synchronized
    fun hasSessionCookie(cookieName: String = "session_id"): Boolean {
        val now = System.currentTimeMillis()
        return cookieStore.any { it.name == cookieName && it.expiresAt > now }
    }
}
