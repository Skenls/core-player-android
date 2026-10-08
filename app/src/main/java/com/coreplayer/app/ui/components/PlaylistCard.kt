package com.coreplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.util.getPlaylistGradient

/**
 * Playlist card strictly matching the "Плейлисты" section in helpers/ mockup.
 *
 * Displays a 22dp rounded cover image (or curated artistic gradient),
 * a translucent circular play button in the bottom-left corner of the cover,
 * title, and subtitle ("Плейлист · N треков").
 */
@Composable
fun PlaylistCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    cardSize: Dp = 140.dp,
    customGradient: Brush? = null,
    onClick: () -> Unit = {},
    onPlayClick: () -> Unit = onClick
) {
    val cornerShape = RoundedCornerShape(22.dp)
    val fallbackGradient = customGradient ?: remember(title) {
        getPlaylistGradient(PlaylistDto(id = title, name = title))
    }

    Column(
        modifier = modifier
            .width(cardSize)
            .clip(cornerShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        // Cover container with 22dp rounded corners and translucent play button
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(cornerShape)
                .background(fallbackGradient)
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Translucent circular play button in bottom-left corner matching mockup
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Воспроизвести $title",
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            color = Color(0xFF8E95A5),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Overload of [PlaylistCard] accepting numeric trackCount.
 */
@Composable
fun PlaylistCard(
    title: String,
    trackCount: Int,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    cardSize: Dp = 140.dp,
    customGradient: Brush? = null,
    onClick: () -> Unit = {},
    onPlayClick: () -> Unit = onClick
) {
    val subtitleText = "Плейлист · $trackCount ${getTrackWordEnding(trackCount)}"
    PlaylistCard(
        title = title,
        subtitle = subtitleText,
        coverUrl = coverUrl,
        modifier = modifier,
        cardSize = cardSize,
        customGradient = customGradient,
        onClick = onClick,
        onPlayClick = onPlayClick
    )
}

/**
 * Overload of [PlaylistCard] accepting [PlaylistDto].
 */
@Composable
fun PlaylistCard(
    playlist: PlaylistDto,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    cardSize: Dp = 140.dp,
    onClick: () -> Unit = {},
    onPlayClick: () -> Unit = onClick
) {
    val gradient = remember(playlist.name) { getPlaylistGradient(playlist) }
    PlaylistCard(
        title = playlist.name,
        trackCount = playlist.songCount,
        coverUrl = coverUrl,
        modifier = modifier,
        cardSize = cardSize,
        customGradient = gradient,
        onClick = onClick,
        onPlayClick = onPlayClick
    )
}

fun formatTracksCount(count: Int): String {
    return "Плейлист · $count ${getTrackWordEnding(count)}"
}

fun getTrackWordEnding(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..19 -> "треков"
        mod10 == 1 -> "трек"
        mod10 in 2..4 -> "трека"
        else -> "треков"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun PlaylistCardPreview() {
    CorePlayerTheme {
        PlaylistCard(
            title = "Вечерний вайб",
            trackCount = 57,
            coverUrl = null
        )
    }
}
