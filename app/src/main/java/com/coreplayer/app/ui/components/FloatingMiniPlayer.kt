package com.coreplayer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.coreplayer.app.ui.theme.TextPrimary

/**
 * Floating pill-shaped mini-player capsule strictly matching the mockup in `helpers/`.
 *
 * Sits elevated above the bottom navigation bar with:
 * - Stadium capsule shape (CircleShape)
 * - Twilight cyan-tinted glassmorphic background gradient
 * - Layered play button: vibrant orange badge + white inner circle + black play/pause icon
 * - White title + silver artist text
 * - Clean white outlined heart icon (or filled orange/red when favorited)
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
    val capsuleGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF161C26),
                Color(0xFF162334),
                Color(0xFF152A3F),
                Color(0xFF12344E)
            )
        )
    }

    val heartTint by animateColorAsState(
        targetValue = if (isFavorite) AccentOrange else Color.White,
        animationSpec = tween(250),
        label = "heartTint"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.85f)
            )
            .clip(CircleShape)
            .background(capsuleGradient)
            .border(
                border = BorderStroke(1.dp, Color(0x38FFFFFF)),
                shape = CircleShape
            )
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Layered Play/Pause button strictly matching the mockup
            // (Orange outer badge + solid white circular button + crisp black icon)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFF7043), Color(0xFFFF5722), Color(0xFFE64A19))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 24.dp),
                        role = Role.Button,
                        onClick = onTogglePlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                // White inner circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Воспроизведение",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(22.dp)
                            .then(if (!isPlaying) Modifier.padding(start = 2.dp) else Modifier)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Track title and artist name
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = trackTitle,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = artistName,
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right: Clean outlined white heart (or filled orange if favorited)
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Удалить из избранного" else "В избранное",
                    tint = heartTint,
                    modifier = Modifier.size(26.dp)
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
        artistName = track.artist.ifBlank { "Неизвестный исполнитель" },
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
private fun FloatingMiniPlayerMockupPreview() {
    CorePlayerTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
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
}
