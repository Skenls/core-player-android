package com.coreplayer.app.player

import android.content.Context
import com.coreplayer.app.data.model.TrackDto
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrackCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private val mockClient = OkHttpClient()

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        val cacheDir = tempFolder.newFolder("test_cache")
        every { mockContext.cacheDir } returns cacheDir
        every { mockContext.applicationContext } returns mockContext
    }

    @Test
    fun getStreamUrl_formatsCorrectlyWithVariousCodecs() {
        val manager = TrackCacheManager.getInstance(mockContext, mockClient)
        val track = TrackDto(id = "trk-99", title = "Test Song")

        assertEquals(
            "http://example.com/api/stream/trk-99",
            manager.getStreamUrl(track, "http://example.com/", "auto")
        )

        assertEquals(
            "http://example.com/api/stream/trk-99",
            manager.getStreamUrl(track, "http://example.com", "raw")
        )

        assertEquals(
            "http://example.com/api/stream/trk-99?format=mp3",
            manager.getStreamUrl(track, "http://example.com", "mp3")
        )

        assertEquals(
            "https://server.local/api/stream/trk-99?format=flac",
            manager.getStreamUrl(track, "https://server.local/", "flac")
        )
    }

    @Test
    fun getInstance_returnsSameSingleton() {
        val manager1 = TrackCacheManager.getInstance(mockContext, mockClient)
        val manager2 = TrackCacheManager.getInstance(mockContext, mockClient)

        assertNotNull(manager1)
        assertEquals(manager1, manager2)
    }

    @Test
    fun cacheConfiguration_initializesDirectory() {
        val manager = TrackCacheManager.getInstance(mockContext, mockClient)
        assertNotNull(manager.cacheDir)
        assertNotNull(manager.upstreamFactory)
    }

    @Test
    fun audioStreamingInterceptor_addsBoundedRangeHeaders() {
        var interceptedRange: String? = null
        val testClient = OkHttpClient.Builder()
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
                    interceptedRange = newRange
                }
                okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .body(okhttp3.ResponseBody.create(null, ByteArray(0)))
                    .build()
            }
            .build()

        val req1 = okhttp3.Request.Builder().url("http://example.com/api/stream/trk-1?format=opus").build()
        testClient.newCall(req1).execute()
        assertEquals("bytes=0-2147483647", interceptedRange)

        val req2 = okhttp3.Request.Builder().url("http://example.com/api/stream/trk-1?format=mp3").header("Range", "bytes=1500000-").build()
        testClient.newCall(req2).execute()
        assertEquals("bytes=1500000-2147483647", interceptedRange)

        interceptedRange = null
        val req3 = okhttp3.Request.Builder().url("http://example.com/api/stream/cover/trk-1?size=512").build()
        testClient.newCall(req3).execute()
        assertEquals(null, interceptedRange)
    }
}

