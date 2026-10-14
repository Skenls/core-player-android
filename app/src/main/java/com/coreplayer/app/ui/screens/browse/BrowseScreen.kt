package com.coreplayer.app.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.ui.components.ArtistCard
import com.coreplayer.app.ui.components.PlaylistCard
import com.coreplayer.app.ui.util.resolveArtistImageUrl
import com.coreplayer.app.ui.screens.library.LibraryTrackItem
import com.coreplayer.app.ui.util.resolveCoverUrl
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.DarkBackground
import com.coreplayer.app.ui.theme.SectionTitleStyle
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary
import com.coreplayer.app.ui.util.responsiveGridColumns
import kotlinx.coroutines.launch

enum class BrowseTab(val title: String) {
    ALBUMS("Альбомы"),
    ARTISTS("Артисты"),
    TRACKS("Треки")
}

@Composable
fun BrowseScreen(
    libraryRepository: LibraryRepository,
    playerController: PlayerControllerManager,
    modifier: Modifier = Modifier,
    initialTab: Int = 0
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var albums by remember { mutableStateOf<List<AlbumDto>>(emptyList()) }
    var artists by remember { mutableStateOf<List<ArtistDto>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val favoriteTrackIds by libraryRepository.favoriteTrackIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            libraryRepository.refreshFavoriteIds()
            albums = libraryRepository.getAllAlbums()
            artists = libraryRepository.getArtists()
            // Quick initial populate
            val quickTracks = libraryRepository.getRandomTracks(100)
            if (quickTracks.isNotEmpty() && tracks.isEmpty()) {
                tracks = quickTracks
            }
            // Fetch all tracks across all albums (guarantees all album tracks are included)
            val allTracks = libraryRepository.getAllTracks()
            if (allTracks.isNotEmpty()) {
                tracks = allTracks
            } else if (tracks.isEmpty()) {
                tracks = libraryRepository.getFavoriteTracks()
            }
        } catch (e: Exception) {
            android.util.Log.e("BrowseScreen", "Failed to load browse data", e)
        } finally {
            isLoading = false
        }
    }

    var selectedAlbumForDetail by remember { mutableStateOf<AlbumDto?>(null) }
    var albumTracks by remember { mutableStateOf<List<TrackDto>>(emptyList()) }
    var isLoadingAlbumTracks by remember { mutableStateOf(false) }

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    selectedAlbumForDetail?.let { album ->
        AlbumDetailSheet(
            album = album,
            tracks = albumTracks,
            isLoading = isLoadingAlbumTracks,
            baseUrl = playerController.baseUrl,
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
            },
            bottomInset = navBarInset
        )
    }

    var selectedArtistForDetail by remember { mutableStateOf<ArtistDto?>(null) }

    selectedArtistForDetail?.let { artist ->
        ArtistDetailSheet(
            artist = artist,
            baseUrl = playerController.baseUrl,
            playerController = playerController,
            libraryRepository = libraryRepository,
            onDismiss = { selectedArtistForDetail = null },
            bottomInset = navBarInset
        )
    }

    BrowseScreenContent(
        selectedTabIndex = selectedTab,
        onTabSelected = { selectedTab = it },
        albums = albums,
        artists = artists,
        tracks = tracks,
        isLoading = isLoading,
        baseUrl = playerController.baseUrl,
        favoriteTrackIds = favoriteTrackIds,
        onToggleTrackFavorite = { trackId ->
            scope.launch {
                try {
                    libraryRepository.toggleFavorite(trackId, "track")
                } catch (e: Exception) {
                    android.util.Log.e("BrowseScreen", "Failed to toggle favorite for track $trackId", e)
                }
            }
        },
        onTrackClick = { track ->
            playerController.playTrack(track, tracks)
        },
        onAlbumClick = { album ->
            selectedAlbumForDetail = album
            isLoadingAlbumTracks = true
            scope.launch {
                try {
                    albumTracks = libraryRepository.getAlbumTracks(album.id)
                } catch (e: Exception) {
                    android.util.Log.e("BrowseScreen", "Failed to load album tracks", e)
                } finally {
                    isLoadingAlbumTracks = false
                }
            }
        },
        onArtistClick = { artist ->
            selectedArtistForDetail = artist
        },
        modifier = modifier
    )
}

@Composable
fun BrowseScreenContent(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    albums: List<AlbumDto>,
    artists: List<ArtistDto>,
    tracks: List<TrackDto>,
    isLoading: Boolean,
    baseUrl: String,
    favoriteTrackIds: Set<String> = emptySet(),
    onToggleTrackFavorite: (String) -> Unit = {},
    onTrackClick: (TrackDto) -> Unit,
    onAlbumClick: (AlbumDto) -> Unit,
    onArtistClick: (ArtistDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = BrowseTab.entries

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Text(
            text = "Все треки",
            style = SectionTitleStyle,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
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
            if (isLoading && albums.isEmpty() && artists.isEmpty() && tracks.isEmpty()) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                when (tabs[selectedTabIndex]) {
                    BrowseTab.ALBUMS -> {
                        if (albums.isEmpty()) {
                            Text(
                                text = "Нет альбомов",
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = responsiveGridColumns(minCardWidth = 150.dp),
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
                                        cardSize = 0.dp,
                                        showPlayButton = false,
                                        onClick = { onAlbumClick(album) }
                                    )
                                }
                            }
                        }
                    }

                    BrowseTab.ARTISTS -> {
                        if (artists.isEmpty()) {
                            Text(
                                text = "Нет артистов",
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = responsiveGridColumns(minCardWidth = 140.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(artists, key = { it.id }) { artist ->
                                    ArtistCard(
                                        name = artist.name,
                                        imageUrl = resolveArtistImageUrl(artist, baseUrl),
                                        subtitle = "Артист",
                                        cardSize = 0.dp,
                                        onClick = { onArtistClick(artist) }
                                    )
                                }
                            }
                        }
                    }

                    BrowseTab.TRACKS -> {
                        if (tracks.isEmpty()) {
                            Text(
                                text = "Нет треков",
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(tracks, key = { it.id }) { track ->
                                    val isTrackFavorite = favoriteTrackIds.contains(track.id)
                                    LibraryTrackItem(
                                        track = track,
                                        baseUrl = baseUrl,
                                        isFavorite = isTrackFavorite,
                                        onToggleFavorite = { onToggleTrackFavorite(track.id) },
                                        onClick = { onTrackClick(track) }
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

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun BrowseScreenPreview() {
    CorePlayerTheme {
        BrowseScreenContent(
            selectedTabIndex = 0,
            onTabSelected = {},
            albums = listOf(
                AlbumDto(id = "1", name = "OK Computer", artist = "Radiohead")
            ),
            artists = emptyList(),
            tracks = emptyList(),
            isLoading = false,
            baseUrl = "",
            onTrackClick = {},
            onAlbumClick = {},
            onArtistClick = {}
        )
    }
}
