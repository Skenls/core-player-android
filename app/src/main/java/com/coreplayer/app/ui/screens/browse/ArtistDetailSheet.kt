package com.coreplayer.app.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.coreplayer.app.CorePlayerApp
import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.ui.components.FloatingMiniPlayer
import com.coreplayer.app.ui.components.PlaylistCard
import com.coreplayer.app.ui.screens.library.AddToPlaylistDialog
import com.coreplayer.app.ui.screens.nowplaying.NowPlayingBottomSheet
import com.coreplayer.app.ui.theme.DarkBackground
import com.coreplayer.app.ui.theme.DarkSurfaceVariant
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary
import com.coreplayer.app.ui.util.cleanArtistName
import com.coreplayer.app.ui.util.resolveArtistImageUrl
import com.coreplayer.app.ui.util.resolveCoverUrl
import kotlinx.coroutines.launch

/**
 * Screen showing an artist's details and their albums list in an adaptive grid.
 */
@Composable
fun ArtistDetailSheet(
    artist: ArtistDto,
    baseUrl: String,
    playerController: PlayerControllerManager,
    libraryRepository: LibraryRepository,
    onDismiss: () -> Unit
) {
    val cleanedArtistName = cleanArtistName(artist.name)
    val playerState by playerController.state.collectAsStateWithLifecycle()
    val isAdaptiveBubbleEnabled by CorePlayerApp.instance.adaptiveBubbleFlow.collectAsStateWithLifecycle()

    var albums by remember { mutableStateOf<List<AlbumDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedAlbumForDetail by remember { mutableStateOf<AlbumDto?>(null) }
    var albumTracks by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var isLoadingAlbumTracks by remember { mutableStateOf(false) }

    var showNowPlayingSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var playlistsForAdd by remember { mutableStateOf<List<PlaylistDto>>(emptyList()) }
    var isCurrentTrackFavorite by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(artist.name) {
        isLoading = true
        try {
            val allAlbums = libraryRepository.getAlbums()
            val filtered = allAlbums.filter { album ->
                val albumArtist = cleanArtistName(album.artist)
                albumArtist.equals(cleanedArtistName, ignoreCase = true) ||
                    albumArtist.contains(cleanedArtistName, ignoreCase = true) ||
                    cleanedArtistName.contains(albumArtist, ignoreCase = true)
            }
            if (filtered.isNotEmpty()) {
                albums = filtered
            } else {
                val searchRes = libraryRepository.searchFull(artist.name)
                albums = searchRes.albums
            }
        } catch (e: Exception) {
            android.util.Log.e("ArtistDetailSheet", "Failed to load artist albums", e)
        } finally {
            isLoading = false
        }
    }

    // Observe and sync favorite state for current playing track
    LaunchedEffect(playerState.currentTrack?.id) {
        val currentId = playerState.currentTrack?.id
        if (currentId != null) {
            try {
                val favIds = libraryRepository.getFavoriteIds()
                isCurrentTrackFavorite = favIds.track_ids.contains(currentId)
            } catch (_: Exception) {
                isCurrentTrackFavorite = playerState.currentTrack?.isFavorite ?: false
            }
        } else {
            isCurrentTrackFavorite = false
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
                    android.util.Log.e("ArtistDetailSheet", "Failed to toggle favorite", e)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val isWideLayout = maxWidth > 650.dp && maxWidth > maxHeight

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
                        text = "Исполнитель",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Artist Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = resolveArtistImageUrl(artist, baseUrl),
                            contentDescription = cleanedArtistName,
                            contentScale = ContentScale.Crop,
                            placeholder = rememberVectorPainter(Icons.Filled.Person),
                            error = rememberVectorPainter(Icons.Filled.Person),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cleanedArtistName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Альбомов: ${if (albums.isNotEmpty()) albums.size else artist.albumCount}",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Альбомы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (albums.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Альбомы не найдены",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(
                            bottom = if (playerState.currentTrack != null) 90.dp else 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(albums, key = { it.id }) { album ->
                            PlaylistCard(
                                title = album.name,
                                subtitle = album.artist ?: "",
                                coverUrl = resolveCoverUrl(album.coverArt, baseUrl),
                                cardSize = 0.dp,
                                showPlayButton = false,
                                onClick = {
                                    selectedAlbumForDetail = album
                                    isLoadingAlbumTracks = true
                                    scope.launch {
                                        try {
                                            albumTracks = libraryRepository.getAlbumTracks(album.id)
                                        } catch (e: Exception) {
                                            android.util.Log.e("ArtistDetailSheet", "Failed to load album tracks", e)
                                        } finally {
                                            isLoadingAlbumTracks = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Floating mini-player bubble in the artist view
            if (playerState.currentTrack != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
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

            // Sub-sheet for clicked album
            selectedAlbumForDetail?.let { album ->
                AlbumDetailSheet(
                    album = album,
                    tracks = albumTracks,
                    isLoading = isLoadingAlbumTracks,
                    baseUrl = baseUrl,
                    playerController = playerController,
                    libraryRepository = libraryRepository,
                    onDismiss = { selectedAlbumForDetail = null },
                    onPlayTrack = { track, list ->
                        playerController.playTrack(track, list)
                    },
                    onPlayAll = {
                        if (albumTracks.isNotEmpty()) {
                            playerController.playTrack(albumTracks.first(), albumTracks)
                        }
                    }
                )
            }

            if (showNowPlayingSheet && playerState.currentTrack != null) {
                NowPlayingBottomSheet(
                    playerController = playerController,
                    lyricsRepository = CorePlayerApp.instance.lyricsRepository,
                    isFavorite = isCurrentTrackFavorite,
                    onToggleFavorite = toggleFavorite,
                    onAddToPlaylist = { showAddToPlaylistDialog = true },
                    onDismiss = { showNowPlayingSheet = false }
                )
            }

            if (showAddToPlaylistDialog && playerState.currentTrack != null) {
                val curTrack = playerState.currentTrack!!
                AddToPlaylistDialog(
                    track = curTrack,
                    playlists = playlistsForAdd,
                    onDismiss = { showAddToPlaylistDialog = false },
                    onSelectPlaylist = { pl ->
                        scope.launch {
                            try {
                                libraryRepository.addTrackToPlaylist(pl.id, curTrack.id)
                                showAddToPlaylistDialog = false
                            } catch (e: Exception) {
                                android.util.Log.e("ArtistDetailSheet", "Failed to add track to playlist", e)
                            }
                        }
                    },
                    onCreatePlaylist = { name ->
                        scope.launch {
                            try {
                                libraryRepository.createPlaylist(name)
                                playlistsForAdd = libraryRepository.getPlaylists()
                            } catch (e: Exception) {
                                android.util.Log.e("ArtistDetailSheet", "Failed to create playlist", e)
                            }
                        }
                    }
                )
            }
        }
    }
}
