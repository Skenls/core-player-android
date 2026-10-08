package com.coreplayer.app.ui.screens.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.player.formatTime
import com.coreplayer.app.ui.components.FavoritesBanner
import com.coreplayer.app.ui.components.PlaylistCard
import com.coreplayer.app.ui.theme.AccentOrange
import com.coreplayer.app.ui.theme.CardTitleStyle
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.DarkBackground
import com.coreplayer.app.ui.theme.DarkSurfaceVariant
import com.coreplayer.app.ui.theme.SectionTitleStyle
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary
import com.coreplayer.app.ui.util.resolveCoverUrl
import kotlinx.coroutines.launch

enum class LibraryTab(val title: String) {
    FAVORITES("Мои треки"),
    PLAYLISTS("Плейлисты")
}

@Composable
fun LibraryScreen(
    libraryRepository: LibraryRepository,
    playerController: PlayerControllerManager,
    modifier: Modifier = Modifier,
    initialTab: Int = 0
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var favorites by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<PlaylistDto>>(emptyList()) }
    var playlistCovers by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }

    // Playlist detail & CRUD states
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedPlaylistForDetail by remember { mutableStateOf<PlaylistDto?>(null) }
    var playlistDetailTracks by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var isPlaylistDetailLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val refreshPlaylists: suspend () -> Unit = {
        try {
            val fetchedPlaylists = libraryRepository.getPlaylists()
            playlists = fetchedPlaylists
            val covers = mutableMapOf<String, String?>()
            for (pl in fetchedPlaylists) {
                if (!pl.coverArt.isNullOrBlank()) {
                    covers[pl.id] = pl.coverArt
                } else {
                    val tracks = libraryRepository.getPlaylistTracks(pl.id)
                    covers[pl.id] = tracks.firstOrNull()?.coverArt
                }
            }
            playlistCovers = covers
        } catch (e: Exception) {
            android.util.Log.e("LibraryScreen", "Failed to load playlists", e)
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            favorites = libraryRepository.getFavoriteTracks()
            refreshPlaylists()
        } catch (e: Exception) {
            android.util.Log.e("LibraryScreen", "Failed to load library data", e)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        libraryRepository.favoritesChanged.collect {
            try {
                favorites = libraryRepository.getFavoriteTracks()
            } catch (e: Exception) {
                android.util.Log.e("LibraryScreen", "Failed to refresh favorites", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        libraryRepository.playlistsChanged.collect {
            refreshPlaylists()
        }
    }

    // Load detail tracks when a playlist is opened
    LaunchedEffect(selectedPlaylistForDetail?.id) {
        val pl = selectedPlaylistForDetail
        if (pl != null) {
            isPlaylistDetailLoading = true
            try {
                playlistDetailTracks = libraryRepository.getPlaylistTracks(pl.id)
            } catch (e: Exception) {
                playlistDetailTracks = emptyList()
            } finally {
                isPlaylistDetailLoading = false
            }
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                showCreatePlaylistDialog = false
                scope.launch {
                    try {
                        libraryRepository.createPlaylist(name)
                        refreshPlaylists()
                    } catch (e: Exception) {
                        android.util.Log.e("LibraryScreen", "Failed to create playlist", e)
                    }
                }
            }
        )
    }

    // Playlist Details Sheet
    selectedPlaylistForDetail?.let { pl ->
        PlaylistDetailSheet(
            playlist = pl,
            tracks = playlistDetailTracks,
            isLoading = isPlaylistDetailLoading,
            baseUrl = playerController.baseUrl,
            onDismiss = { selectedPlaylistForDetail = null },
            onPlayTrack = { track, trackList ->
                playerController.playTrack(track, trackList)
            },
            onPlayAll = {
                if (playlistDetailTracks.isNotEmpty()) {
                    playerController.playTrack(playlistDetailTracks.first(), playlistDetailTracks)
                }
            },
            onRemoveTrack = { track ->
                playlistDetailTracks = playlistDetailTracks.filter { it.id != track.id }
                scope.launch {
                    try {
                        libraryRepository.removeTrackFromPlaylist(pl.id, track.id)
                        refreshPlaylists()
                    } catch (e: Exception) {
                        android.util.Log.e("LibraryScreen", "Failed to remove track from playlist", e)
                    }
                }
            },
            onMoveTrackUp = { index ->
                if (index > 0) {
                    val updated = playlistDetailTracks.toMutableList()
                    val item = updated.removeAt(index)
                    updated.add(index - 1, item)
                    playlistDetailTracks = updated
                    scope.launch {
                        try {
                            libraryRepository.reorderPlaylistTracks(pl.id, updated.map { it.id })
                        } catch (e: Exception) {
                            android.util.Log.e("LibraryScreen", "Failed to reorder playlist", e)
                        }
                    }
                }
            },
            onMoveTrackDown = { index ->
                if (index < playlistDetailTracks.size - 1) {
                    val updated = playlistDetailTracks.toMutableList()
                    val item = updated.removeAt(index)
                    updated.add(index + 1, item)
                    playlistDetailTracks = updated
                    scope.launch {
                        try {
                            libraryRepository.reorderPlaylistTracks(pl.id, updated.map { it.id })
                        } catch (e: Exception) {
                            android.util.Log.e("LibraryScreen", "Failed to reorder playlist", e)
                        }
                    }
                }
            },
            onDeletePlaylist = {
                selectedPlaylistForDetail = null
                scope.launch {
                    try {
                        libraryRepository.deletePlaylist(pl.id)
                        refreshPlaylists()
                    } catch (e: Exception) {
                        android.util.Log.e("LibraryScreen", "Failed to delete playlist", e)
                    }
                }
            }
        )
    }

    LibraryScreenContent(
        selectedTabIndex = selectedTab,
        onTabSelected = { selectedTab = it },
        favorites = favorites,
        playlists = playlists,
        playlistCovers = playlistCovers,
        isLoading = isLoading,
        baseUrl = playerController.baseUrl,
        onTrackClick = { track ->
            playerController.playTrack(track, favorites)
        },
        onCreatePlaylistClick = {
            showCreatePlaylistDialog = true
        },
        onPlaylistClick = { playlist ->
            selectedPlaylistForDetail = playlist
        },
        modifier = modifier
    )
}

@Composable
fun LibraryScreenContent(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    favorites: List<TrackDto>,
    playlists: List<PlaylistDto>,
    playlistCovers: Map<String, String?> = emptyMap(),
    isLoading: Boolean,
    baseUrl: String,
    onTrackClick: (TrackDto) -> Unit,
    onCreatePlaylistClick: () -> Unit = {},
    onPlaylistClick: (PlaylistDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = LibraryTab.entries

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Медиатека",
                style = SectionTitleStyle
            )

            if (tabs[selectedTabIndex] == LibraryTab.PLAYLISTS) {
                IconButton(onClick = onCreatePlaylistClick) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Создать плейлист", tint = TextPrimary)
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = DarkBackground,
            contentColor = TextPrimary,
            edgePadding = 20.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Color.White
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedTabIndex == index
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = tab.title,
                            color = if (selected) TextPrimary else TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (isLoading && favorites.isEmpty() && playlists.isEmpty()) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                when (tabs[selectedTabIndex]) {
                    LibraryTab.FAVORITES -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                FavoritesBanner(
                                    trackCount = favorites.size,
                                    imageUrl = favorites.firstOrNull()?.coverArt?.let { resolveCoverUrl(it, baseUrl) },
                                    isFavorite = true,
                                    onClick = {
                                        if (favorites.isNotEmpty()) {
                                            onTrackClick(favorites.first())
                                        }
                                    },
                                    onFavoriteClick = {
                                        if (favorites.isNotEmpty()) {
                                            onTrackClick(favorites.first())
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            if (favorites.isEmpty() && !isLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Нет избранных треков",
                                            color = TextSecondary
                                        )
                                    }
                                }
                            } else {
                                items(favorites, key = { it.id }) { track ->
                                    LibraryTrackItem(
                                        track = track,
                                        baseUrl = baseUrl,
                                        onClick = { onTrackClick(track) }
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.PLAYLISTS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header action row for Playlists
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${playlists.size} плейлистов",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )

                                Button(
                                    onClick = onCreatePlaylistClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Новый плейлист",
                                        color = Color.Black,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (playlists.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Нет плейлистов",
                                        color = TextSecondary
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(playlists, key = { it.id }) { playlist ->
                                        val resolvedArt = playlist.coverArt ?: playlistCovers[playlist.id]
                                        PlaylistCard(
                                            playlist = playlist,
                                            coverUrl = resolveCoverUrl(resolvedArt, baseUrl),
                                            cardSize = 160.dp,
                                            onClick = { onPlaylistClick(playlist) },
                                            onPlayClick = { onPlaylistClick(playlist) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryTrackItem(
    track: TrackDto,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = resolveCoverUrl(track.coverArt, baseUrl)
            if (coverUrl.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = track.title,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Filled.MusicNote),
                    error = rememberVectorPainter(Icons.Filled.MusicNote),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = CardTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = track.artist.ifBlank { "Неизвестный исполнитель" },
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        if (track.duration > 0) {
            Text(
                text = formatTime(track.duration * 1000L),
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun LibraryScreenPreview() {
    CorePlayerTheme {
        LibraryScreenContent(
            selectedTabIndex = 0,
            onTabSelected = {},
            favorites = listOf(
                TrackDto(id = "1", title = "Pretty Girls", artist = "Kai Angel", duration = 184)
            ),
            playlists = emptyList(),
            playlistCovers = emptyMap(),
            isLoading = false,
            baseUrl = "",
            onTrackClick = {},
            onPlaylistClick = {}
        )
    }
}
