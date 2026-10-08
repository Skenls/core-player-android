package com.coreplayer.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.player.PlayerState
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.util.cleanArtistName
import com.coreplayer.app.ui.util.rememberAdaptivePalette
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating pill-shaped mini-player capsule strictly matching the mockup in `helpers/`.
 *
 * Sits elevated above the bottom navigation bar with:
 * - Stadium capsule shape (CircleShape)
 * - Twilight cyan-tinted glassmorphic background gradient
 * - Circular progress ring around the white play button filling with playback
 * - White title + silver artist text
 * - Clean white outlined heart icon (or filled red when favorited)
 * - Swipe gestures: horizontal swipe to switch tracks with slide animation;
 *   vertical swipe up to expand player with zoom/elevation animation.
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
    modifier: Modifier = Modifier,
    onSkipNext: (() -> Unit)? = null,
    onSkipPrevious: (() -> Unit)? = null,
    progress: Float = 0f,
    coverArtUrl: String? = null,
    isAdaptiveColorEnabled: Boolean = true
) {
    val palette by rememberAdaptivePalette(if (isAdaptiveColorEnabled) coverArtUrl else null)
    val startColor by animateColorAsState(targetValue = palette.primary, animationSpec = tween(500), label = "palStart")
    val endColor by animateColorAsState(targetValue = palette.secondary, animationSpec = tween(500), label = "palEnd")

    val capsuleGradient = remember(startColor, endColor, isAdaptiveColorEnabled) {
        if (isAdaptiveColorEnabled) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF10141D),
                    startColor,
                    endColor
                )
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF161C26),
                    Color(0xFF162334),
                    Color(0xFF152A3F),
                    Color(0xFF12344E)
                )
            )
        }
    }

    val heartTint by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFEF4444) else Color.White,
        animationSpec = tween(250),
        label = "heartTint"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(150),
        label = "miniPlayerProgress"
    )

    val coroutineScope = rememberCoroutineScope()
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val animScale = remember { Animatable(1f) }

    var dragDirection by remember { mutableStateOf<String?>(null) } // "H" or "V"
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .graphicsLayer {
                translationX = animOffsetX.value
                translationY = animOffsetY.value
                scaleX = animScale.value
                scaleY = animScale.value
            }
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
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragDirection = null
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        if (dragDirection == "V" && totalDragY < -20f) {
                            // Swipe up: expand sheet and smoothly return mini player offset
                            onClick()
                            coroutineScope.launch {
                                animOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                animScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        } else if (dragDirection == "H") {
                            val threshold = 40f
                            if (totalDragX < -threshold && onSkipNext != null) {
                                onSkipNext()
                            } else if (totalDragX > threshold && onSkipPrevious != null) {
                                onSkipPrevious()
                            }
                            coroutineScope.launch {
                                animOffsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        } else {
                            // Settle back to resting position smoothly
                            coroutineScope.launch {
                                animOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                animOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                animScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                            }
                        }
                        dragDirection = null
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                            animOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                            animScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                            dragDirection = null
                            totalDragX = 0f
                            totalDragY = 0f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (dragDirection == null) {
                            if (abs(totalDragX) > 12f && abs(totalDragX) > abs(totalDragY)) {
                                dragDirection = "H"
                            } else if (totalDragY < -12f && abs(totalDragY) > abs(totalDragX)) {
                                dragDirection = "V"
                            }
                        }

                        if (dragDirection != null) {
                            change.consume()
                        }

                        if (dragDirection == "H") {
                            coroutineScope.launch {
                                animOffsetX.snapTo(totalDragX.coerceIn(-130f, 130f))
                            }
                        } else if (dragDirection == "V" && totalDragY < 0f) {
                            coroutineScope.launch {
                                animOffsetY.snapTo(totalDragY.coerceAtLeast(-45f))
                                val scaleVal = 1f + (-totalDragY / 400f).coerceIn(0f, 0.04f)
                                animScale.snapTo(scaleVal)
                            }
                        }
                    }
                )
            }
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
            // Left: Play/Pause button surrounded by an orange circular progress ring
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 24.dp),
                        role = Role.Button,
                        onClick = onTogglePlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Circular Progress Ring
                Canvas(modifier = Modifier.size(46.dp)) {
                    val strokeWidthPx = 3.dp.toPx()
                    val arcRadius = (size.minDimension - strokeWidthPx) / 2f

                    // Subtle background track
                    drawCircle(
                        color = Color(0x33FF5722),
                        radius = arcRadius,
                        style = Stroke(width = strokeWidthPx)
                    )

                    // Filling orange progress arc
                    if (animatedProgress > 0f) {
                        drawArc(
                            color = Color(0xFFFF5722),
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(
                                width = strokeWidthPx,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                // Solid white circular inner button with crisp black icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Воспроизведение",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(20.dp)
                            .then(if (!isPlaying) Modifier.padding(start = 2.dp) else Modifier)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Track title and artist name with smooth crossfade
            Crossfade(
                targetState = Pair(trackTitle, artistName),
                animationSpec = tween(200),
                modifier = Modifier.weight(1f),
                label = "miniPlayerTextCrossfade"
            ) { (title, artist) ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
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
                        text = artist,
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
    modifier: Modifier = Modifier,
    onSkipNext: (() -> Unit)? = null,
    onSkipPrevious: (() -> Unit)? = null,
    progress: Float = 0f,
    baseUrl: String = "",
    isAdaptiveColorEnabled: Boolean = true
) {
    val coverUrl = com.coreplayer.app.ui.util.resolveCoverUrl(track.coverArt, baseUrl)
    val cleanedArtist = cleanArtistName(track.artist).ifBlank { "Неизвестный исполнитель" }
    FloatingMiniPlayer(
        trackTitle = track.title,
        artistName = cleanedArtist,
        isPlaying = isPlaying,
        isFavorite = isFavorite,
        onTogglePlayPause = onTogglePlayPause,
        onToggleFavorite = onToggleFavorite,
        onClick = onClick,
        modifier = modifier,
        onSkipNext = onSkipNext,
        onSkipPrevious = onSkipPrevious,
        progress = progress,
        coverArtUrl = coverUrl,
        isAdaptiveColorEnabled = isAdaptiveColorEnabled
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
    modifier: Modifier = Modifier,
    onSkipNext: (() -> Unit)? = null,
    onSkipPrevious: (() -> Unit)? = null,
    baseUrl: String = "",
    isAdaptiveColorEnabled: Boolean = true
) {
    val track = playerState.currentTrack ?: return
    FloatingMiniPlayer(
        track = track,
        isPlaying = playerState.isPlaying,
        isFavorite = isFavorite,
        progress = playerState.progress,
        onTogglePlayPause = onTogglePlayPause,
        onToggleFavorite = onToggleFavorite,
        onClick = onClick,
        modifier = modifier,
        onSkipNext = onSkipNext,
        onSkipPrevious = onSkipPrevious,
        baseUrl = baseUrl,
        isAdaptiveColorEnabled = isAdaptiveColorEnabled
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
