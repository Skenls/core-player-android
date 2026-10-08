package com.coreplayer.app.data.api

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CookieJarTest {

    private lateinit var cookieJar: InMemoryCookieJar
    private val url = "http://localhost:8000/api/auth/login".toHttpUrl()

    @Before
    fun setUp() {
        cookieJar = InMemoryCookieJar()
    }

    @Test
    fun saveFromResponse_and_loadForRequest_preservesValidCookies() {
        val cookie = Cookie.Builder()
            .domain("localhost")
            .path("/")
            .name("session_id")
            .value("test-session-12345")
            .httpOnly()
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie))

        val loaded = cookieJar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("session_id", loaded[0].name)
        assertEquals("test-session-12345", loaded[0].value)
        assertTrue(cookieJar.hasSessionCookie("session_id"))
    }

    @Test
    fun loadForRequest_doesNotReturnCookiesForDifferentDomain() {
        val cookie = Cookie.Builder()
            .domain("example.com")
            .path("/")
            .name("session_id")
            .value("secret")
            .build()

        cookieJar.saveFromResponse("http://example.com/api/login".toHttpUrl(), listOf(cookie))

        val loaded = cookieJar.loadForRequest(url)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun saveFromResponse_replacesCookieWithSameName() {
        val cookie1 = Cookie.Builder()
            .domain("localhost")
            .path("/")
            .name("session_id")
            .value("initial-session")
            .build()

        val cookie2 = Cookie.Builder()
            .domain("localhost")
            .path("/")
            .name("session_id")
            .value("updated-session")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie1))
        cookieJar.saveFromResponse(url, listOf(cookie2))

        val loaded = cookieJar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("updated-session", loaded[0].value)
    }

    @Test
    fun clear_removesAllCookies() {
        val cookie = Cookie.Builder()
            .domain("localhost")
            .path("/")
            .name("session_id")
            .value("active-session")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie))
        assertTrue(cookieJar.hasSessionCookie())

        cookieJar.clear()

        val loaded = cookieJar.loadForRequest(url)
        assertTrue(loaded.isEmpty())
        assertFalse(cookieJar.hasSessionCookie())
    }
}
