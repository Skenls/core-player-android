package com.coreplayer.app.ui.util

import android.graphics.drawable.BitmapDrawable
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AdaptiveColorPalette(
    val primary: Color = Color(0xFF162334),
    val secondary: Color = Color(0xFF12344E)
)

private val paletteCache = LruCache<String, AdaptiveColorPalette>(40)

@Composable
fun rememberAdaptivePalette(coverUrl: String?): State<AdaptiveColorPalette> {
    val defaultPalette = remember { AdaptiveColorPalette() }
    val paletteState = remember(coverUrl) {
        mutableStateOf(coverUrl?.let { paletteCache.get(it) } ?: defaultPalette)
    }

    val context = LocalContext.current

    LaunchedEffect(coverUrl) {
        if (coverUrl.isNullOrBlank()) {
            paletteState.value = defaultPalette
            return@LaunchedEffect
        }

        val cached = paletteCache.get(coverUrl)
        if (cached != null) {
            paletteState.value = cached
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val loader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .size(128, 128)
                    .build()
                val result = loader.execute(request)
                val drawable = result.drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val dominant = palette.getDominantColor(0xFF162334.toInt())
                    val vibrant = palette.getVibrantColor(
                        palette.getDarkVibrantColor(palette.getMutedColor(0xFF12344E.toInt()))
                    )

                    val adaptive = AdaptiveColorPalette(
                        primary = Color(dominant),
                        secondary = Color(vibrant)
                    )
                    paletteCache.put(coverUrl, adaptive)
                    withContext(Dispatchers.Main) {
                        paletteState.value = adaptive
                    }
                }
            } catch (_: Exception) {
                // Keep default palette
            }
        }
    }

    return paletteState
}
