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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.util.FavoritesSunsetMountainGradient

/**
 * Landscape banner for the "Избранное / Мои треки" section in helpers/ mockup.
 *
 * Displays a 24dp rounded landscape card with sunset mountain gradient,
 * "Мои треки" title, tracks count, and an outlined white heart icon in the top-right corner.
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
    val cardShape = RoundedCornerShape(24.dp)
    val softOverlayGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0x10000000),
            Color(0x280B0F17),
            Color(0x990B0F17)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .clip(cardShape)
            .background(FavoritesSunsetMountainGradient)
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

        // Soft gradient overlay for text legibility without washing out colors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(softOverlayGradient)
        )

        // Heart icon in top-right corner (clean white outlined heart matching mockup)
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.FavoriteBorder,
                contentDescription = "Избранное",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // Title and subtitle at bottom-left corner
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 18.dp, end = 60.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFFD1D5DB),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Overload helper formatting track count dynamically.
 */
@Composable
fun FavoritesBanner(
    trackCount: Int,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = true,
    onClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = onClick
) {
    val subtitleText = "Плейлист · $trackCount ${getTrackWordEnding(trackCount)}"
    FavoritesBanner(
        modifier = modifier,
        title = "Мои треки",
        subtitle = subtitleText,
        imageUrl = imageUrl,
        isFavorite = isFavorite,
        onClick = onClick,
        onFavoriteClick = onFavoriteClick
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun FavoritesBannerPreview() {
    CorePlayerTheme {
        FavoritesBanner(
            trackCount = 124,
            imageUrl = null,
            modifier = Modifier.padding(16.dp)
        )
    }
}
