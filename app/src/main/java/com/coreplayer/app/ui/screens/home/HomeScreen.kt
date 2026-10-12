package com.coreplayer.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coreplayer.app.data.model.AlbumDto
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.ui.components.ArtistCard
import com.coreplayer.app.ui.components.FavoritesBanner
import com.coreplayer.app.ui.components.PlaylistCard
import com.coreplayer.app.ui.components.RecentTrackCard
import androidx.compose.ui.graphics.Color
import com.coreplayer.app.ui.util.resolveArtistImageUrl
import com.coreplayer.app.ui.util.resolveCoverUrl
import com.coreplayer.app.ui.theme.CoreLogoStyle
import com.coreplayer.app.ui.theme.CorePlayerTheme
import com.coreplayer.app.ui.theme.DarkBackground
import com.coreplayer.app.ui.theme.DarkSurfaceVariant
import com.coreplayer.app.ui.theme.SectionTitleStyle
import com.coreplayer.app.ui.theme.TextPrimary
import com.coreplayer.app.ui.theme.TextSecondary

import com.coreplayer.app.CorePlayerApp
import com.coreplayer.app.data.repository.AuthRepository
import com.coreplayer.app.ui.screens.settings.SettingsDialog
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    libraryRepository: LibraryRepository,
    playerController: PlayerControllerManager,
    modifier: Modifier = Modifier,
    authRepository: AuthRepository = CorePlayerApp.instance.authRepository,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(libraryRepository, playerController)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()
    var showSettingsDialog by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            authRepository.getCurrentUser()
        } catch (_: Exception) {}
    }

    if (showSettingsDialog) {
        SettingsDialog(
            username = currentUser?.username ?: "Пользователь",
            serverUrl = CorePlayerApp.instance.serverUrl,
            selectedCodec = CorePlayerApp.instance.audioCodec,
            onCodecSelected = { CorePlayerApp.instance.updateCodec(it) },
            selectedBgMode = CorePlayerApp.instance.playerBackgroundMode,
            onBgModeSelected = { CorePlayerApp.instance.playerBackgroundMode = it },
            isAdaptiveBubbleEnabled = CorePlayerApp.instance.adaptiveBubbleColor,
            onAdaptiveBubbleChanged = { CorePlayerApp.instance.adaptiveBubbleColor = it },
            isAmoledEnabled = CorePlayerApp.instance.amoledMode,
            onAmoledChanged = { CorePlayerApp.instance.amoledMode = it },
            isCrossfadeEnabled = CorePlayerApp.instance.crossfadeEnabled,
            onCrossfadeChanged = { CorePlayerApp.instance.crossfadeEnabled = it },
            onLogout = {
                showSettingsDialog = false
                authRepository.clearSession()
                onLogout()
                scope.launch {
                    try {
                        authRepository.logout()
                    } catch (_: Exception) {}
                }
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    var selectedArtistForDetail by remember { mutableStateOf<com.coreplayer.app.data.model.ArtistDto?>(null) }

    selectedArtistForDetail?.let { artist ->
        com.coreplayer.app.ui.screens.browse.ArtistDetailSheet(
            artist = artist,
            baseUrl = playerController.baseUrl,
            playerController = playerController,
            libraryRepository = libraryRepository,
            onDismiss = { selectedArtistForDetail = null }
        )
    }

    HomeScreenContent(
        uiState = uiState,
        baseUrl = playerController.baseUrl,
        onArtistClick = { selectedArtistForDetail = it },
        onTrackClick = { viewModel.playRecentTrack(it) },
        onFavoritesClick = { viewModel.playFavorites() },
        onFavoritesHeaderClick = onNavigateToFavorites,
        onPlaylistClick = { viewModel.playPlaylist(it) },
        onPlaylistPlayClick = { viewModel.playPlaylist(it) },
        onPlaylistsHeaderClick = onNavigateToPlaylists,
        onNavigateToSettings = { showSettingsDialog = true },
        onRetry = { viewModel.refresh() },
        modifier = modifier
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    baseUrl: String,
    onArtistClick: (ArtistDto) -> Unit,
    onTrackClick: (TrackDto) -> Unit = {},
    onFavoritesClick: () -> Unit,
    onFavoritesHeaderClick: () -> Unit,
    onPlaylistClick: (PlaylistDto) -> Unit,
    onPlaylistPlayClick: (PlaylistDto) -> Unit,
    onPlaylistsHeaderClick: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading && uiState.artists.isEmpty() && uiState.playlists.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Header with settings button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "core",
                        style = CoreLogoStyle
                    )
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .background(DarkSurfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(42.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Подключение к серверу...",
                            color = TextSecondary,
                            style = SectionTitleStyle
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = onNavigateToSettings,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Настройки", color = Color.White)
                        }
                    }
                }
            }
        } else if (uiState.error != null && uiState.artists.isEmpty() && uiState.playlists.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Header with settings button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "core",
                        style = CoreLogoStyle
                    )
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .background(DarkSurfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = uiState.error,
                            color = TextSecondary,
                            style = SectionTitleStyle,
                            modifier = Modifier.padding(bottom = 16.dp),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "Повторить", color = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = onNavigateToSettings,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Настройки сервера", color = Color.White)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                // 1. Header: bold "core" logo in top-left with settings/account button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "core",
                        style = CoreLogoStyle
                    )
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .background(DarkSurfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки сервера",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 2. "Недавние" quick-access grid (matching helpers/IMG_DB4D8C23A082-1.jpeg)
                if (uiState.recentTracks.isNotEmpty()) {
                    Text(
                        text = "Недавние",
                        style = SectionTitleStyle,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.foundation.layout.BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        val chunkSize = if (maxWidth > 650.dp) 4 else 2
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.recentTracks.chunked(chunkSize).forEach { rowTracks ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (track in rowTracks) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            RecentTrackCard(
                                                track = track,
                                                baseUrl = baseUrl,
                                                onClick = { onTrackClick(track) }
                                            )
                                        }
                                    }
                                    if (rowTracks.size < chunkSize) {
                                        for (i in 0 until (chunkSize - rowTracks.size)) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                } else if (uiState.artists.isNotEmpty()) {
                    Text(
                        text = "Недавние артисты",
                        style = SectionTitleStyle,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.artists, key = { it.id }) { artist ->
                            ArtistCard(
                                name = artist.name,
                                imageUrl = resolveArtistImageUrl(artist, baseUrl),
                                subtitle = "Артист",
                                onClick = { onArtistClick(artist) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // 3. "Избранное >" section header with FavoritesBanner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 24.dp),
                            onClick = onFavoritesHeaderClick
                        )
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Избранное",
                        style = SectionTitleStyle
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Favorites",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FavoritesBanner(
                    trackCount = uiState.favoritesCount,
                    imageUrl = uiState.favorites.firstOrNull()?.coverArt?.let { resolveCoverUrl(it, baseUrl) },
                    isFavorite = true,
                    onClick = onFavoritesClick,
                    onFavoriteClick = onFavoritesClick,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(28.dp))

                // 4. "Плейлисты >" section header with PlaylistCard horizontal row
                if (uiState.playlists.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 24.dp),
                                onClick = onPlaylistsHeaderClick
                            )
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Плейлисты",
                            style = SectionTitleStyle
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Playlists",
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            val resolvedArt = playlist.coverArt ?: uiState.playlistCovers[playlist.id]
                            PlaylistCard(
                                playlist = playlist,
                                coverUrl = resolveCoverUrl(resolvedArt, baseUrl),
                                onClick = { onPlaylistClick(playlist) },
                                onPlayClick = { onPlaylistPlayClick(playlist) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun HomeScreenMockupPreview() {
    val sampleArtists = listOf(
        ArtistDto(id = "1", name = "The Neighbourhood"),
        ArtistDto(id = "2", name = "Billie Eilish"),
        ArtistDto(id = "3", name = "Imagine Dragons"),
        ArtistDto(id = "4", name = "Arctic Monkeys")
    )
    val samplePlaylists = listOf(
        PlaylistDto(id = "p1", name = "Вечерний вайб", songCount = 57),
        PlaylistDto(id = "p2", name = "Chill vibes", songCount = 82),
        PlaylistDto(id = "p3", name = "Инди атмосфера", songCount = 64)
    )
    val state = HomeUiState(
        artists = sampleArtists,
        playlists = samplePlaylists,
        favorites = List(124) { TrackDto(id = "t$it", title = "Track $it") },
        favoritesCount = 124,
        isLoading = false,
        error = null
    )

    CorePlayerTheme {
        HomeScreenContent(
            uiState = state,
            baseUrl = "",
            onArtistClick = {},
            onFavoritesClick = {},
            onFavoritesHeaderClick = {},
            onPlaylistClick = {},
            onPlaylistPlayClick = {},
            onPlaylistsHeaderClick = {},
            onRetry = {}
        )
    }
}
