package com.coreplayer.app.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.coreplayer.app.CorePlayerApp
import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.LyricLine
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.player.formatTime
import com.coreplayer.app.ui.components.FloatingMiniPlayer
import com.coreplayer.app.ui.screens.library.AddToPlaylistDialog
import com.coreplayer.app.ui.screens.nowplaying.NowPlayingBottomSheet
import com.coreplayer.app.ui.theme.AccentOrange
import com.coreplayer.app.ui.theme.DarkBackground
import com.coreplayer.app.ui.theme.DarkSurfaceVariant
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary
import com.coreplayer.app.ui.util.cleanArtistName
import com.coreplayer.app.ui.util.resolveCoverUrl
import kotlinx.coroutines.launch

/**
 * Fullscreen or modal album details showing tracklist and playback controls.
 */
@Composable
fun AlbumDetailSheet(
    album: AlbumDto,
    tracks: List<TrackDto>,
    isLoading: Boolean,
    baseUrl: String,
    playerController: PlayerControllerManager,
    libraryRepository: LibraryRepository,
    onDismiss: () -> Unit,
    onPlayTrack: (TrackDto, List<TrackDto>) -> Unit,
    onPlayAll: () -> Unit
) {
    val cleanedArtist = cleanArtistName(album.artist)
    val playerState by playerController.state.collectAsStateWithLifecycle()
    val isAdaptiveBubbleEnabled by CorePlayerApp.instance.adaptiveBubbleFlow.collectAsStateWithLifecycle()

    var showNowPlayingSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var playlistsForAdd by remember { mutableStateOf<List<PlaylistDto>>(emptyList()) }
    var isCurrentTrackFavorite by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Observe and sync favorite state for current playing track
    LaunchedEffect(playerState.currentTrack?.id) {
        val currentId = playerState.currentTrack?.id
        if (currentId != null) {
            try {
                val favIds = libraryRepository.getFavoriteIds()
                isCurrentTrackFavorite = favIds.track_ids.contains(currentId)
            } catch (e: Exception) {
                isCurrentTrackFavorite = playerState.currentTrack?.isFavorite ?: false
            }
        } else {
            isCurrentTrackFavorite = false
        }
    }

    LaunchedEffect(Unit) {
        libraryRepository.favoritesChanged.collect {
            val currentId = playerState.currentTrack?.id
            if (currentId != null) {
                try {
                    val favIds = libraryRepository.getFavoriteIds()
                    isCurrentTrackFavorite = favIds.track_ids.contains(currentId)
                } catch (_: Exception) {}
            }
        }
    }

    val toggleFavorite: () -> Unit = {
        val currentId = playerState.currentTrack?.id
        if (currentId != null) {
            scope.launch {
                try {
                    val res = libraryRepository.toggleFavorite(currentId, "track")
                    isCurrentTrackFavorite = res.is_favorite
                } catch (e: Exception) {
                    android.util.Log.e("AlbumDetailSheet", "Failed to toggle favorite", e)
                }
            }
        }
    }

    LaunchedEffect(showAddToPlaylistDialog) {
        if (showAddToPlaylistDialog) {
            try {
                playlistsForAdd = libraryRepository.getPlaylists()
            } catch (e: Exception) {
                android.util.Log.e("AlbumDetailSheet", "Failed to load playlists", e)
            }
        }
    }

    val currentTrack = playerState.currentTrack
    val isTrackFromThisAlbum = currentTrack != null && (
        (currentTrack.albumId != null && currentTrack.albumId == album.id) ||
        tracks.any { it.id == currentTrack.id } ||
        (album.name.isNotBlank() && currentTrack.album.equals(album.name, ignoreCase = true))
    )
    val isPlayingThisAlbum = isTrackFromThisAlbum && playerState.isPlaying

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Top header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Закрыть",
                            tint = TextPrimary
                        )
                    }

                    Text(
                        text = "Альбом",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Album Header Card (Cover + Title + Artist)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = resolveCoverUrl(album.coverArt, baseUrl),
                            contentDescription = album.name,
                            contentScale = ContentScale.Crop,
                            placeholder = rememberVectorPainter(Icons.Filled.MusicNote),
                            error = rememberVectorPainter(Icons.Filled.MusicNote),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cleanedArtist.ifBlank { "Неизвестный исполнитель" },
                            fontSize = 14.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val metaText = buildString {
                            if (album.songCount > 0) {
                                append("${album.songCount} треков")
                            } else if (tracks.isNotEmpty()) {
                                append("${tracks.size} треков")
                            }
                            if (album.year != null && album.year > 0) {
                                if (length > 0) append(" · ")
                                append("${album.year}")
                            }
                        }
                        if (metaText.isNotEmpty()) {
                            Text(
                                text = metaText,
                                fontSize = 12.sp,
                                color = TextSecondary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Play / Pause button (changes dynamically if playing this album)
                if (tracks.isNotEmpty()) {
                    Button(
                        onClick = {
                            if (isPlayingThisAlbum) {
                                playerController.togglePlayPause()
                            } else if (isTrackFromThisAlbum) {
                                playerController.togglePlayPause()
                            } else {
                                onPlayAll()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isPlayingThisAlbum) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPlayingThisAlbum) "Приостановить" else "Слушать",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Tracklist content
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (tracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "В альбоме нет треков",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = if (playerState.currentTrack != null) 100.dp else 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(tracks, key = { index, t -> "${t.id}_$index" }) { index, track ->
                            val isThisTrackActive = currentTrack?.id == track.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (isThisTrackActive) {
                                            playerController.togglePlayPause()
                                        } else {
                                            onPlayTrack(track, tracks)
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isThisTrackActive) AccentOrange else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isThisTrackActive) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.width(28.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = if (isThisTrackActive) AccentOrange else TextPrimary,
                                        fontSize = 14.5.sp,
                                        fontWeight = if (isThisTrackActive) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val trackArtist = cleanArtistName(track.artist)
                                    if (trackArtist.isNotBlank() && trackArtist != cleanedArtist) {
                                        Text(
                                            text = trackArtist,
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = formatTime(track.duration * 1000L),
                                    color = if (isThisTrackActive) AccentOrange else TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Floating mini-player bubble in the album view
            if (playerState.currentTrack != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    FloatingMiniPlayer(
                        playerState = playerState,
                        isFavorite = isCurrentTrackFavorite,
                        onTogglePlayPause = { playerController.togglePlayPause() },
                        onToggleFavorite = toggleFavorite,
                        onSkipNext = { playerController.skipNext() },
                        onSkipPrevious = { playerController.skipPrevious() },
                        onClick = { showNowPlayingSheet = true },
                        baseUrl = baseUrl,
                        isAdaptiveColorEnabled = isAdaptiveBubbleEnabled
                    )
                }
            }

            if (showNowPlayingSheet && currentTrack != null) {
                NowPlayingBottomSheet(
                    playerController = playerController,
                    lyricsRepository = CorePlayerApp.instance.lyricsRepository,
                    isFavorite = isCurrentTrackFavorite,
                    onToggleFavorite = toggleFavorite,
                    onAddToPlaylist = { showAddToPlaylistDialog = true },
                    onDismiss = { showNowPlayingSheet = false }
                )
            }

            if (showAddToPlaylistDialog && currentTrack != null) {
                AddToPlaylistDialog(
                    track = currentTrack,
                    playlists = playlistsForAdd,
                    onDismiss = { showAddToPlaylistDialog = false },
                    onSelectPlaylist = { pl ->
                        scope.launch {
                            try {
                                libraryRepository.addTrackToPlaylist(pl.id, currentTrack.id)
                                showAddToPlaylistDialog = false
                            } catch (e: Exception) {
                                android.util.Log.e("AlbumDetailSheet", "Failed to add track to playlist", e)
                            }
                        }
                    },
                    onCreatePlaylist = { name ->
                        scope.launch {
                            try {
                                libraryRepository.createPlaylist(name)
                                playlistsForAdd = libraryRepository.getPlaylists()
                            } catch (e: Exception) {
                                android.util.Log.e("AlbumDetailSheet", "Failed to create playlist", e)
                            }
                        }
                    }
                )
            }
        }
    }
}
