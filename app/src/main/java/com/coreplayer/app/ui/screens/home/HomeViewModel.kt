package com.coreplayer.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.data.repository.RecentTracksManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val artists: List<ArtistDto> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
    val favorites: List<TrackDto> = emptyList(),
    val favoritesCount: Int = favorites.size,
    val recentTracks: List<TrackDto> = emptyList(),
    val playlistCovers: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    private val playerController: PlayerControllerManager,
    private val recentTracksManager: RecentTracksManager? = null
) : ViewModel() {

    private val recentManager: RecentTracksManager? = recentTracksManager ?: try {
        com.coreplayer.app.CorePlayerApp.instance.recentTracksManager
    } catch (_: Exception) {
        null
    }

    private val _uiState = MutableStateFlow(
        HomeUiState(
            recentTracks = recentManager?.loadRecent()?.take(RecentTracksManager.MAX_RECENT) ?: emptyList()
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var autoReconnectJob: Job? = null

    init {
        loadHomeData()
        observePlayerState()
        observeFavorites()
        observePlaylists()
        observeRecentTracks()
    }

    private fun observeRecentTracks() {
        val manager = recentManager ?: return
        viewModelScope.launch {
            manager.recentTracks.collect { tracks ->
                _uiState.update { it.copy(recentTracks = tracks.take(RecentTracksManager.MAX_RECENT)) }
            }
        }
    }

    private fun observePlayerState() {
        viewModelScope.launch {
            playerController.state
                .mapNotNull { it.currentTrack }
                .distinctUntilChangedBy { it.id }
                .collect { newTrack ->
                    recentManager?.addTrack(newTrack)
                    _uiState.update { state ->
                        val updated = (listOf(newTrack) + state.recentTracks.filter { it.id != newTrack.id })
                            .take(RecentTracksManager.MAX_RECENT)
                        state.copy(recentTracks = updated)
                    }
                }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            libraryRepository.favoritesChanged.collect {
                try {
                    val updatedFavorites = libraryRepository.getFavoriteTracks()
                    _uiState.update {
                        it.copy(
                            favorites = updatedFavorites,
                            favoritesCount = updatedFavorites.size
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Failed to refresh favorites", e)
                }
            }
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            libraryRepository.playlistsChanged.collect {
                try {
                    val updatedPlaylists = libraryRepository.getPlaylists()
                    _uiState.update { it.copy(playlists = updatedPlaylists) }
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Failed to refresh playlists", e)
                }
            }
        }
    }

    fun loadHomeData() {
        autoReconnectJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val artists = libraryRepository.getArtists()
                val playlists = libraryRepository.getPlaylists()
                val favorites = libraryRepository.getFavoriteTracks()
                val persistedRecent = recentManager?.loadRecent()?.take(RecentTracksManager.MAX_RECENT)
                val recent = if (!persistedRecent.isNullOrEmpty()) {
                    persistedRecent
                } else {
                    _uiState.value.recentTracks.take(RecentTracksManager.MAX_RECENT)
                }
                _uiState.update {
                    it.copy(
                        artists = artists,
                        playlists = playlists,
                        favorites = favorites,
                        favoritesCount = favorites.size,
                        recentTracks = recent,
                        isLoading = false,
                        error = null
                    )
                }

                // Asynchronously fetch first-track covers for playlists
                val covers = mutableMapOf<String, String>()
                for (pl in playlists) {
                    val tracks = libraryRepository.getPlaylistTracks(pl.id)
                    val first = tracks.firstOrNull()
                    val art = first?.coverArt ?: first?.id
                    if (!art.isNullOrBlank()) {
                        covers[pl.id] = art
                    }
                }
                if (covers.isNotEmpty()) {
                    _uiState.update { it.copy(playlistCovers = covers) }
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Ошибка подключения к серверу"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
                startAutoReconnect()
            }
        }
    }

    private fun startAutoReconnect() {
        autoReconnectJob?.cancel()
        autoReconnectJob = viewModelScope.launch {
            var backoffMs = 3000L
            var attempts = 0
            while (_uiState.value.error != null && attempts < 5) {
                delay(backoffMs)
                attempts++
                try {
                    val artists = libraryRepository.getArtists()
                    val playlists = libraryRepository.getPlaylists()
                    val favorites = libraryRepository.getFavoriteTracks()
                    val recent = recentManager?.loadRecent()?.take(RecentTracksManager.MAX_RECENT)
                        ?: _uiState.value.recentTracks.take(RecentTracksManager.MAX_RECENT)
                    _uiState.update {
                        it.copy(
                            artists = artists,
                            playlists = playlists,
                            favorites = favorites,
                            favoritesCount = favorites.size,
                            recentTracks = recent,
                            error = null,
                            isLoading = false
                        )
                    }
                    break
                } catch (e: Exception) {
                    backoffMs = (backoffMs * 1.5).toLong().coerceAtMost(15000L)
                }
            }
        }
    }

    fun refresh() {
        loadHomeData()
    }

    fun playFavorites() {
        val tracks = _uiState.value.favorites
        if (tracks.isNotEmpty()) {
            playerController.playTrack(tracks.first(), tracks)
        }
    }

    fun playRecentTrack(track: TrackDto) {
        val queue = _uiState.value.recentTracks.ifEmpty { listOf(track) }
        playerController.playTrack(track, queue)
    }

    fun playPlaylist(playlist: PlaylistDto) {
        viewModelScope.launch {
            val tracks = libraryRepository.getPlaylistTracks(playlist.id)
            if (tracks.isNotEmpty()) {
                playerController.playTrack(tracks.first(), tracks)
            }
        }
    }

    fun playArtist(artist: ArtistDto) {
        viewModelScope.launch {
            val artistTracks = _uiState.value.favorites.filter {
                it.artist.equals(artist.name, ignoreCase = true) || it.artistId == artist.id
            }
            if (artistTracks.isNotEmpty()) {
                playerController.playTrack(artistTracks.first(), artistTracks)
            } else {
                val searched = libraryRepository.search(artist.name)
                if (searched.isNotEmpty()) {
                    playerController.playTrack(searched.first(), searched)
                }
            }
        }
    }

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch {
            try {
                libraryRepository.toggleFavorite(trackId)
                val updatedFavorites = libraryRepository.getFavoriteTracks()
                _uiState.update {
                    it.copy(
                        favorites = updatedFavorites,
                        favoritesCount = updatedFavorites.size
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to toggle favorite for track $trackId", e)
                _uiState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to update favorite")
                }
            }
        }
    }

    class Factory(
        private val libraryRepository: LibraryRepository,
        private val playerController: PlayerControllerManager,
        private val recentTracksManager: RecentTracksManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(libraryRepository, playerController, recentTracksManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
