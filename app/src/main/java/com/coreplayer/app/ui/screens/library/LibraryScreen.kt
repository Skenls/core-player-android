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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.player.formatTime
import com.coreplayer.app.ui.components.PlaylistCard
import com.coreplayer.app.ui.components.formatTracksCount
import com.coreplayer.app.ui.screens.nowplaying.resolveCoverUrl
import com.coreplayer.app.ui.theme.AccentOrange
import com.coreplayer.app.ui.theme.CardTitleStyle
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.DarkBackground
import com.coreplayer.app.ui.theme.DarkSurfaceVariant
import com.coreplayer.app.ui.theme.SectionTitleStyle
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary

enum class LibraryTab(val title: String) {
    FAVORITES("Избранное"),
    PLAYLISTS("Плейлисты"),
    ALBUMS("Альбомы")
}

@Composable
fun LibraryScreen(
    libraryRepository: LibraryRepository,
    playerController: PlayerControllerManager,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var favorites by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<PlaylistDto>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            favorites = libraryRepository.getFavoriteTracks()
            playlists = libraryRepository.getPlaylists()
            albums = libraryRepository.getAlbums()
        } catch (_: Exception) {
            // Silently handle
        } finally {
            isLoading = false
        }
    }

    LibraryScreenContent(
        selectedTabIndex = selectedTab,
        onTabSelected = { selectedTab = it },
        favorites = favorites,
        playlists = playlists,
        albums = albums,
        isLoading = isLoading,
        baseUrl = playerController.baseUrl,
        onTrackClick = { track ->
            playerController.playTrack(track, favorites)
        },
        onPlaylistClick = { playlist ->
            val track = TrackDto(id = playlist.id, title = playlist.name, artist = "Плейлист", coverArt = playlist.coverArt)
            val queue = if (favorites.isNotEmpty()) favorites else listOf(track)
            playerController.playTrack(track, queue)
        },
        onAlbumClick = { album ->
            val track = TrackDto(id = album.id, title = album.name, artist = album.artist ?: "", coverArt = album.coverArt)
            val queue = if (favorites.isNotEmpty()) favorites else listOf(track)
            playerController.playTrack(track, queue)
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
    albums: List<AlbumDto>,
    isLoading: Boolean,
    baseUrl: String,
    onTrackClick: (TrackDto) -> Unit,
    onPlaylistClick: (PlaylistDto) -> Unit,
    onAlbumClick: (AlbumDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = LibraryTab.values()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        Text(
            text = "Медиатека",
            style = SectionTitleStyle,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = DarkBackground,
            contentColor = TextPrimary,
            edgePadding = 20.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = AccentOrange
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
            if (isLoading && favorites.isEmpty() && playlists.isEmpty() && albums.isEmpty()) {
                CircularProgressIndicator(
                    color = AccentOrange,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                when (tabs[selectedTabIndex]) {
                    LibraryTab.FAVORITES -> {
                        if (favorites.isEmpty()) {
                            Text(
                                text = "Нет избранных треков",
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
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
                        if (playlists.isEmpty()) {
                            Text(
                                text = "Нет плейлистов",
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(playlists, key = { it.id }) { playlist ->
                                    PlaylistCard(
                                        title = playlist.name,
                                        subtitle = formatTracksCount(playlist.songCount),
                                        coverUrl = resolveCoverUrl(playlist.coverArt, baseUrl),
                                        cardSize = 160.dp,
                                        onClick = { onPlaylistClick(playlist) },
                                        onPlayClick = { onPlaylistClick(playlist) }
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.ALBUMS -> {
                        if (albums.isEmpty()) {
                            Text(
                                text = "Нет альбомов",
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(albums, key = { it.id }) { album ->
                                    PlaylistCard(
                                        title = album.name,
                                        subtitle = album.artist ?: "",
                                        coverUrl = resolveCoverUrl(album.coverArt, baseUrl),
                                        cardSize = 160.dp,
                                        onClick = { onAlbumClick(album) },
                                        onPlayClick = { onAlbumClick(album) }
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

        Spacer(modifier = Modifier.width(8.dp))

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
            albums = emptyList(),
            isLoading = false,
            baseUrl = "",
            onTrackClick = {},
            onPlaylistClick = {},
            onAlbumClick = {}
        )
    }
}
