package com.coreplayer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.player.PlayerState
import com.coreplayer.app.ui.theme.AccentOrange
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.DarkSurface
import com.coreplayer.app.ui.theme.SubtleDivider
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary

/**
 * Floating pill-shaped mini-player capsule matching the mockup in `helpers/`.
 *
 * Sits elevated above the bottom navigation bar with:
 * - Semi-transparent dark glass background (#131A26 with subtle border & shadow)
 * - Rounded capsule shape (~32dp corner radius)
 * - Circular play/pause button with orange accent (`AccentOrange` #FF5722) on the left
 * - Track title (bold 15sp) and artist (13sp) in the center
 * - Heart favorite toggle on the right
 * - Card click opens full Now Playing player
 */
@Composable
fun FloatingMiniPlayer(
    trackTitle: String,
    artistName: String,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val capsuleShape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = 10.dp,
                shape = capsuleShape,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.7f)
            )
            .clip(capsuleShape)
            .background(DarkSurface.copy(alpha = 0.94f))
            .border(
                border = BorderStroke(1.dp, SubtleDivider.copy(alpha = 0.85f)),
                shape = capsuleShape
            )
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Circular Play/Pause button with orange accent
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentOrange)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 22.dp),
                        role = Role.Button,
                        onClick = onTogglePlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Track title and artist name
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = trackTitle,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = artistName,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Heart favorite toggle
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) AccentOrange else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Overload of [FloatingMiniPlayer] accepting a [TrackDto].
 */
@Composable
fun FloatingMiniPlayer(
    track: TrackDto,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingMiniPlayer(
        trackTitle = track.title,
        artistName = track.artist.ifBlank { "Unknown Artist" },
        isPlaying = isPlaying,
        isFavorite = isFavorite,
        onTogglePlayPause = onTogglePlayPause,
        onToggleFavorite = onToggleFavorite,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Overload of [FloatingMiniPlayer] accepting [PlayerState].
 * Renders nothing if [PlayerState.currentTrack] is null.
 */
@Composable
fun FloatingMiniPlayer(
    playerState: PlayerState,
    isFavorite: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack ?: return
    FloatingMiniPlayer(
        track = track,
        isPlaying = playerState.isPlaying,
        isFavorite = isFavorite,
        onTogglePlayPause = onTogglePlayPause,
        onToggleFavorite = onToggleFavorite,
        onClick = onClick,
        modifier = modifier
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun FloatingMiniPlayerPreview() {
    CorePlayerTheme {
        FloatingMiniPlayer(
            trackTitle = "Pretty Girls",
            artistName = "Kai Angel",
            isPlaying = false,
            isFavorite = false,
            onTogglePlayPause = {},
            onToggleFavorite = {},
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun FloatingMiniPlayerPlayingPreview() {
    CorePlayerTheme {
        FloatingMiniPlayer(
            trackTitle = "Pretty Girls",
            artistName = "Kai Angel",
            isPlaying = true,
            isFavorite = true,
            onTogglePlayPause = {},
            onToggleFavorite = {},
            onClick = {}
        )
    }
}
