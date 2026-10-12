package com.coreplayer.app.ui.screens.nowplaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.coreplayer.app.data.model.LyricLine
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LyricsRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.player.PlayerState
import com.coreplayer.app.player.RepeatMode
import com.coreplayer.app.player.formatTime
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.DarkBackground
import com.coreplayer.app.ui.theme.DarkSurfaceVariant
import com.coreplayer.app.ui.theme.SubtleDivider
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary
import com.coreplayer.app.ui.util.cleanArtistName

/**
 * Resolves the artwork URL against the server base URL using ImageUtils.
 */
fun resolveCoverUrl(rawUrl: String?, baseUrl: String = ""): String? {
    return com.coreplayer.app.ui.util.resolveCoverUrl(rawUrl, baseUrl, size = 600)
}

/**
 * Full-screen modal Now Playing bottom sheet with synchronized karaoke lyrics.
 *
 * Consumes:
 * - [PlayerState]: Playback status, current track, timing, shuffle/repeat modes
 * - Callbacks: Seek, play/pause, skip, shuffle, repeat, favorite, and dismiss
 * - Synced lyrics list and loading state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingBottomSheet(
    playerState: PlayerState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    lyrics: List<LyricLine> = emptyList(),
    isLyricsLoading: Boolean = false,
    lyricsError: String? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    baseUrl: String = ""
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = null,
        modifier = modifier.fillMaxSize()
    ) {
        NowPlayingContent(
            playerState = playerState,
            onTogglePlayPause = onTogglePlayPause,
            onSeekTo = onSeekTo,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            lyrics = lyrics,
            isLyricsLoading = isLyricsLoading,
            lyricsError = lyricsError,
            onAddToPlaylist = onAddToPlaylist,
            onDismiss = onDismiss,
            baseUrl = baseUrl
        )
    }
}

/**
 * High-level overload consuming [PlayerControllerManager] and [LyricsRepository].
 */
@Composable
fun NowPlayingBottomSheet(
    playerController: PlayerControllerManager,
    lyricsRepository: LyricsRepository? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerState by playerController.state.collectAsStateWithLifecycle()
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }

    val currentTrack = playerState.currentTrack

    // Automatically fetch synchronized lyrics when current track changes
    LaunchedEffect(currentTrack?.id) {
        if (currentTrack != null && lyricsRepository != null) {
            isLyricsLoading = true
            lyricsError = null
            try {
                lyrics = lyricsRepository.getLyrics(
                    trackId = currentTrack.id,
                    artist = currentTrack.artist.ifBlank { null },
                    title = currentTrack.title.ifBlank { null },
                    album = currentTrack.album.ifBlank { null },
                    duration = if (currentTrack.duration > 0) currentTrack.duration else null
                )
            } catch (e: Exception) {
                lyrics = emptyList()
                lyricsError = "Не удалось загрузить текст песни"
            } finally {
                isLyricsLoading = false
            }
        } else {
            lyrics = emptyList()
            isLyricsLoading = false
        }
    }

    NowPlayingBottomSheet(
        playerState = playerState,
        onTogglePlayPause = { playerController.togglePlayPause() },
        onSeekTo = { playerController.seekTo(it) },
        onSkipNext = { playerController.skipNext() },
        onSkipPrevious = { playerController.skipPrevious() },
        onToggleShuffle = { playerController.toggleShuffle() },
        onToggleRepeat = { playerController.toggleRepeat() },
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        lyrics = lyrics,
        isLyricsLoading = isLyricsLoading,
        lyricsError = lyricsError,
        onAddToPlaylist = onAddToPlaylist,
        onDismiss = onDismiss,
        baseUrl = playerController.baseUrl,
        modifier = modifier
    )
}

/**
 * Complete Now Playing screen content with blur background, artwork/lyrics switcher,
 * scrubbing slider, and playback controls.
 */
@Composable
fun NowPlayingContent(
    playerState: PlayerState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    lyrics: List<LyricLine> = emptyList(),
    isLyricsLoading: Boolean = false,
    lyricsError: String? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    baseUrl: String = ""
) {
    var showLyrics by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }

    val track = playerState.currentTrack
    val coverUrl = resolveCoverUrl(track?.coverArt, baseUrl)
    val durationMs = playerState.durationMs.coerceAtLeast(0L)
    val displayedPositionMs = if (isSeeking) seekPositionMs.toLong() else playerState.currentPositionMs
    val backgroundMode = try { com.coreplayer.app.CorePlayerApp.instance.playerBackgroundMode } catch (_: Exception) { "cover" }

    val contentBg = androidx.compose.material3.MaterialTheme.colorScheme.background
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(contentBg)
    ) {
        val parentMaxWidth = maxWidth
        val parentMaxHeight = maxHeight
        val isWideLayout = parentMaxWidth > 600.dp && parentMaxWidth > parentMaxHeight

        // Dynamic background blur layer from album artwork (respects backgroundMode setting)
        if (backgroundMode == "cover" && !coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
            )
            // Dark scrim overlay for high contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(contentBg.copy(alpha = 0.82f))
            )
        }

        if (isWideLayout) {
            // Wide / Tablet Landscape: Two-column layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Artwork or Lyrics
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = showLyrics,
                        animationSpec = tween(durationMillis = 300),
                        label = "ArtworkLyricsCrossfadeWide"
                    ) { lyricsMode ->
                        if (lyricsMode) {
                            SyncedLyricsView(
                                lyrics = lyrics,
                                currentPositionMs = displayedPositionMs,
                                onSeekTo = onSeekTo,
                                isLoading = isLyricsLoading,
                                errorMessage = lyricsError,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .sizeIn(maxWidth = parentMaxWidth * 0.42f, maxHeight = parentMaxHeight - 48.dp)
                                    .aspectRatio(1f)
                                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.8f))
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable { showLyrics = true },
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverUrl.isNullOrBlank()) {
                                    Icon(
                                        imageVector = Icons.Filled.Album,
                                        contentDescription = track?.title,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(96.dp)
                                    )
                                } else {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = track?.title,
                                        contentScale = ContentScale.Crop,
                                        placeholder = rememberVectorPainter(Icons.Filled.Album),
                                        error = rememberVectorPainter(Icons.Filled.Album),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Right Column: Controls, Info, Scrubber
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    NowPlayingTopBar(
                        track = track,
                        showLyrics = showLyrics,
                        onDismiss = onDismiss,
                        onAddToPlaylist = onAddToPlaylist,
                        onToggleLyrics = { showLyrics = !showLyrics }
                    )

                    NowPlayingTrackInfo(
                        track = track,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite
                    )

                    NowPlayingScrubber(
                        durationMs = durationMs,
                        displayedPositionMs = displayedPositionMs,
                        isSeeking = isSeeking,
                        seekPositionMs = seekPositionMs,
                        onSeekTo = onSeekTo,
                        onDragStart = { isSeeking = true; seekPositionMs = it },
                        onDragUpdate = { seekPositionMs = it },
                        onDragEnd = { isSeeking = false }
                    )

                    NowPlayingControlsRow(
                        playerState = playerState,
                        onToggleShuffle = onToggleShuffle,
                        onSkipPrevious = onSkipPrevious,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onToggleRepeat = onToggleRepeat
                    )
                }
            }
        } else {
            // Portrait / Standard Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                NowPlayingTopBar(
                    track = track,
                    showLyrics = showLyrics,
                    onDismiss = onDismiss,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleLyrics = { showLyrics = !showLyrics }
                )

                // Central Area: Artwork View OR Synced Karaoke Lyrics View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = showLyrics,
                        animationSpec = tween(durationMillis = 300),
                        label = "ArtworkLyricsCrossfade"
                    ) { lyricsMode ->
                        if (lyricsMode) {
                            SyncedLyricsView(
                                lyrics = lyrics,
                                currentPositionMs = displayedPositionMs,
                                onSeekTo = onSeekTo,
                                isLoading = isLyricsLoading,
                                errorMessage = lyricsError,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .aspectRatio(1f)
                                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.8f))
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable { showLyrics = true },
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverUrl.isNullOrBlank()) {
                                    Icon(
                                        imageVector = Icons.Filled.Album,
                                        contentDescription = track?.title,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(96.dp)
                                    )
                                } else {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = track?.title,
                                        contentScale = ContentScale.Crop,
                                        placeholder = rememberVectorPainter(Icons.Filled.Album),
                                        error = rememberVectorPainter(Icons.Filled.Album),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    NowPlayingTrackInfo(
                        track = track,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    NowPlayingScrubber(
                        durationMs = durationMs,
                        displayedPositionMs = displayedPositionMs,
                        isSeeking = isSeeking,
                        seekPositionMs = seekPositionMs,
                        onSeekTo = onSeekTo,
                        onDragStart = { isSeeking = true; seekPositionMs = it },
                        onDragUpdate = { seekPositionMs = it },
                        onDragEnd = { isSeeking = false }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    NowPlayingControlsRow(
                        playerState = playerState,
                        onToggleShuffle = onToggleShuffle,
                        onSkipPrevious = onSkipPrevious,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onToggleRepeat = onToggleRepeat
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    track: TrackDto?,
    showLyrics: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
    onToggleLyrics: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Collapse player",
                tint = TextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "ИГРАЕТ ИЗ",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            val sourceContext = if (!track?.album.isNullOrBlank()) {
                "Альбом «${track.album}»"
            } else {
                "Медиатека"
            }
            Text(
                text = sourceContext,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAdd,
                        contentDescription = "Добавить в плейлист",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            IconButton(onClick = onToggleLyrics) {
                Icon(
                    imageVector = if (showLyrics) Icons.Filled.Lyrics else Icons.Outlined.Lyrics,
                    contentDescription = "Toggle lyrics view",
                    tint = if (showLyrics) Color.White else TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingTrackInfo(
    track: TrackDto?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track?.title ?: "Нет активного трека",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cleanArtistName(track?.artist).ifBlank { "Неизвестный исполнитель" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) Color(0xFFEF4444) else TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NowPlayingScrubber(
    durationMs: Long,
    displayedPositionMs: Long,
    isSeeking: Boolean,
    seekPositionMs: Float,
    onSeekTo: (Long) -> Unit,
    onDragStart: (Float) -> Unit,
    onDragUpdate: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val targetMs = (fraction * durationMs).toLong()
                            onSeekTo(targetMs)
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (durationMs > 0) {
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                onDragStart(fraction * durationMs)
                            }
                        },
                        onDragEnd = {
                            onSeekTo(seekPositionMs.toLong())
                            onDragEnd()
                        },
                        onDragCancel = onDragEnd,
                        onHorizontalDrag = { _, dragAmount ->
                            if (durationMs > 0) {
                                val deltaFraction = dragAmount / size.width
                                val currentFraction = (seekPositionMs / durationMs).coerceIn(0f, 1f)
                                val newFraction = (currentFraction + deltaFraction).coerceIn(0f, 1f)
                                onDragUpdate(newFraction * durationMs)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val fraction = if (durationMs > 0) {
                (displayedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0x28FFFFFF))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White)
            )

            val thumbRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 5.dp.toPx() }
            val thumbOffsetPx = (widthPx * fraction - thumbRadiusPx).coerceIn(0f, widthPx - thumbRadiusPx * 2)

            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetPx.toInt(), 0) }
                    .align(Alignment.CenterStart)
                    .size(10.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(displayedPositionMs),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Text(
                text = formatTime(durationMs),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun NowPlayingControlsRow(
    playerState: PlayerState,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = if (playerState.shuffleModeEnabled) Color.White else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(
            onClick = onSkipPrevious,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = TextPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 36.dp),
                    role = Role.Button,
                    onClick = onTogglePlayPause
                ),
            contentAlignment = Alignment.Center
        ) {
            if (playerState.isBuffering) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        IconButton(
            onClick = onSkipNext,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = TextPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(onClick = onToggleRepeat) {
            val (repeatIcon, repeatTint) = when (playerState.repeatMode) {
                RepeatMode.OFF -> Icons.Filled.Repeat to TextSecondary
                RepeatMode.ALL -> Icons.Filled.Repeat to Color.White
                RepeatMode.ONE -> Icons.Filled.RepeatOne to Color.White
            }
            Icon(
                imageVector = repeatIcon,
                contentDescription = "Repeat: ${playerState.repeatMode}",
                tint = repeatTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun NowPlayingContentArtworkPreview() {
    val sampleTrack = TrackDto(
        id = "1",
        title = "Pretty Girls",
        artist = "Kai Angel",
        album = "ANGEL MAY CRY",
        duration = 184
    )
    val state = PlayerState(
        currentTrack = sampleTrack,
        isPlaying = true,
        currentPositionMs = 45000L,
        durationMs = 184000L
    )
    CorePlayerTheme {
        NowPlayingContent(
            playerState = state,
            onTogglePlayPause = {},
            onSeekTo = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onToggleShuffle = {},
            onToggleRepeat = {},
            isFavorite = true,
            onToggleFavorite = {},
            onDismiss = {}
        )
    }
}
