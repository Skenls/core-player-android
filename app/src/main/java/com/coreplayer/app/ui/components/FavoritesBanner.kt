package com.coreplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.coreplayer.app.ui.theme.BannerSubtitleStyle
import com.coreplayer.app.ui.theme.BannerTitleStyle

/**
 * Pluralization helper for tracks count in Russian ("1 трек", "2 трека", "5 треков").
 */
fun formatTracksCount(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod100 in 11..19 -> "треков"
        mod10 == 1 -> "трек"
        mod10 in 2..4 -> "трека"
        else -> "треков"
    }
    return "Плейлист · $count $word"
}

/**
 * Wide landscape banner for the "Избранное" section matching the mockup.
 *
 * Displays a 20dp rounded landscape banner with dusk gradient/image,
 * favorite heart icon in top-right, and "Мои треки" with subtitle at bottom-left.
 */
@Composable
fun FavoritesBanner(
    modifier: Modifier = Modifier,
    title: String = "Мои треки",
    subtitle: String = "Плейлист · 124 трека",
    imageUrl: String? = null,
    isFavorite: Boolean = true,
    bannerHeight: Dp = 140.dp,
    onClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = onClick
) {
    val landscapeFallbackGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF282D46),
            Color(0xFF533B5F),
            Color(0xFF3B2B47),
            Color(0xFF131A26)
        )
    )

    val scrimGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0x33000000),
            Color(0x660B0F17),
            Color(0xCC0B0F17)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(landscapeFallbackGradient)
            .clickable(onClick = onClick)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dark scrim overlay for high readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimGradient)
        )

        // Heart icon in top-right corner
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // Title and subtitle at bottom-left corner
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp, end = 56.dp)
        ) {
            Text(
                text = title,
                style = BannerTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = BannerSubtitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Overload of [FavoritesBanner] accepting numeric trackCount.
 */
@Composable
fun FavoritesBanner(
    trackCount: Int,
    modifier: Modifier = Modifier,
    title: String = "Мои треки",
    imageUrl: String? = null,
    isFavorite: Boolean = true,
    bannerHeight: Dp = 140.dp,
    onClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = onClick
) {
    FavoritesBanner(
        modifier = modifier,
        title = title,
        subtitle = formatTracksCount(trackCount),
        imageUrl = imageUrl,
        isFavorite = isFavorite,
        bannerHeight = bannerHeight,
        onClick = onClick,
        onFavoriteClick = onFavoriteClick
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun FavoritesBannerPreview() {
    com.coreplayer.app.ui.theme.CorePlayerTheme {
        FavoritesBanner(
            title = "Мои треки",
            trackCount = 124,
            imageUrl = null,
            isFavorite = true
        )
    }
}

