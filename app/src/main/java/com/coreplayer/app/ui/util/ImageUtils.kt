package com.coreplayer.app.ui.util

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.coreplayer.app.CorePlayerApp
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.PlaylistDto
import kotlin.math.abs

/**
 * Centralized utility for resolving artwork and cover URLs for Core Player / Subsonic / Navidrome.
 */
fun resolveCoverUrl(raw: String?, baseUrl: String = "", size: Int = 300): String? {
    if (raw.isNullOrBlank()) return null
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw

    val serverUrl = try {
        CorePlayerApp.instance.serverUrl
    } catch (_: Exception) {
        ""
    }
    val cleanBase = (if (baseUrl.isNotBlank()) baseUrl else serverUrl).trimEnd('/')
    if (cleanBase.isEmpty()) return null

    if (raw.startsWith("/api/stream/cover/")) {
        return "$cleanBase$raw"
    }
    if (raw.startsWith("/api/")) {
        return "$cleanBase$raw"
    }

    val cleanId = raw.trimStart('/')
    return "$cleanBase/api/stream/cover/$cleanId?size=$size"
}

/**
 * Resolves artist image URL with fallback to artist.id.
 */
fun resolveArtistImageUrl(artist: ArtistDto, baseUrl: String = "", size: Int = 300): String? {
    val artKey = artist.effectiveCoverArt.ifBlank { artist.id }
    return resolveCoverUrl(artKey, baseUrl, size)
}

/**
 * Sunset mountain gradient matching the "Избранное / Мои треки" banner in helpers/ mockup.
 */
val FavoritesSunsetMountainGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF28153A),
        Color(0xFF4C2253),
        Color(0xFF673266),
        Color(0xFF293D5C),
        Color(0xFF101826)
    )
)

/**
 * Curated gradient palettes matching the reference playlists in helpers/ mockup.
 */
fun getPlaylistGradient(playlist: PlaylistDto): Brush {
    val nameLower = playlist.name.lowercase()
    return when {
        nameLower.contains("вечер") || nameLower.contains("вайб") || nameLower.contains("sunset") -> {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF4A154B),
                    Color(0xFF8B2658),
                    Color(0xFFD64545),
                    Color(0xFFFF7A45)
                )
            )
        }
        nameLower.contains("chill") || nameLower.contains("вайб") || nameLower.contains("blue") || nameLower.contains("вода") -> {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF09203F),
                    Color(0xFF164773),
                    Color(0xFF1A6B9B),
                    Color(0xFF389FD6)
                )
            )
        }
        nameLower.contains("инди") || nameLower.contains("атмосфер") || nameLower.contains("зелен") || nameLower.contains("forest") -> {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0F382C),
                    Color(0xFF1A5A46),
                    Color(0xFF287C62),
                    Color(0xFF4EA685)
                )
            )
        }
        else -> {
            val palettes = listOf(
                listOf(Color(0xFF2B1055), Color(0xFF46197E), Color(0xFF7597DE)),
                listOf(Color(0xFF1F1C2C), Color(0xFF4B384C), Color(0xFF928DAB)),
                listOf(Color(0xFF0B486B), Color(0xFF1B6B7C), Color(0xFF79BD9A)),
                listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)),
                listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF3A6073))
            )
            val index = abs((playlist.id.ifBlank { playlist.name }).hashCode()) % palettes.size
            Brush.linearGradient(colors = palettes[index])
        }
    }
}
