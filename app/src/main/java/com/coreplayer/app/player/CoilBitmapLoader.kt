package com.coreplayer.app.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BitmapLoader implementation for Media3 that delegates image loading to Coil.
 * This ensures that remote cover art loads with full authentication cookies (via OkHttp),
 * benefits from Coil's disk/memory caching, and produces software ARGB_8888 bitmaps
 * required by Android System UI media notifications (IPC over Binder).
 */
@OptIn(UnstableApi::class)
class CoilBitmapLoader(
    private val context: Context,
    private val imageLoader: ImageLoader
) : BitmapLoader {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun supportsMimeType(mimeType: String): Boolean {
        val lower = mimeType.lowercase()
        return lower.startsWith("image/") || lower.contains("jpeg") || lower.contains("png") || lower.contains("webp")
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalArgumentException("Failed to decode byte array into Bitmap"))
                }
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri.toString())
                    .allowHardware(false) // Crucial for NotificationCompat / System UI Binder transactions
                    .size(512, 512)
                    .build()

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                        drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: drawable.bitmap
                    } else {
                        val width = drawable.intrinsicWidth.coerceAtLeast(1)
                        val height = drawable.intrinsicHeight.coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, width, height)
                        drawable.draw(canvas)
                        bmp
                    }
                    future.set(bitmap)
                } else {
                    future.setException(IllegalStateException("Coil failed to load bitmap for $uri"))
                }
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }
}
