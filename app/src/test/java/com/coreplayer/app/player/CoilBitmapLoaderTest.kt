package com.coreplayer.app.player

import android.content.Context
import coil.ImageLoader
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoilBitmapLoaderTest {

    private val context = mockk<Context>(relaxed = true)
    private val imageLoader = mockk<ImageLoader>(relaxed = true)
    private val loader = CoilBitmapLoader(context, imageLoader)

    @Test
    fun supportsMimeType_correctlyIdentifiesImageTypes() {
        assertTrue(loader.supportsMimeType("image/jpeg"))
        assertTrue(loader.supportsMimeType("image/png"))
        assertTrue(loader.supportsMimeType("image/webp"))
        assertTrue(loader.supportsMimeType("image/gif"))
        assertFalse(loader.supportsMimeType("audio/mpeg"))
        assertFalse(loader.supportsMimeType("video/mp4"))
        assertFalse(loader.supportsMimeType("application/json"))
    }
}
