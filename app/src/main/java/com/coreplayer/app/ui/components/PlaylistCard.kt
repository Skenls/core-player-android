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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.coreplayer.app.ui.theme.CardSubtitleStyle
import com.coreplayer.app.ui.theme.CardTitleStyle
import com.coreplayer.app.ui.theme.DarkSurfaceVariant
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary
import com.coreplayer.app.ui.theme.TranslucentPlayButton

/**
 * Playlist card matching the "Плейлисты" section in the mockup.
 *
 * Displays a 16dp rounded square cover image, a translucent circular play button
 * in the bottom-left corner of the cover, title, and subtitle ("Плейлист · N треков").
 */
@Composable
fun PlaylistCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    cardSize: Dp = 140.dp,
    onClick: () -> Unit = {},
    onPlayClick: () -> Unit = onClick
) {
    Column(
        modifier = modifier
            .width(cardSize)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        // Cover container with 16dp rounded corners and overlay play button
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceVariant)
        ) {
            if (coverUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Album,
                        contentDescription = title,
                        tint = TextSecondary,
                        modifier = Modifier.size(52.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Filled.Album),
                    error = rememberVectorPainter(Icons.Filled.Album),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Translucent circular play button in bottom-left corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(TranslucentPlayButton)
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play $title",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = CardTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            style = CardSubtitleStyle,
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
    onClick: () -> Unit = {},
    onPlayClick: () -> Unit = onClick
) {
    PlaylistCard(
        title = title,
        subtitle = formatTracksCount(trackCount),
        coverUrl = coverUrl,
        modifier = modifier,
        cardSize = cardSize,
        onClick = onClick,
        onPlayClick = onPlayClick
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun PlaylistCardPreview() {
    com.coreplayer.app.ui.theme.CorePlayerTheme {
        PlaylistCard(
            title = "Вечерний вайб",
            trackCount = 57,
            coverUrl = null
        )
    }
}

