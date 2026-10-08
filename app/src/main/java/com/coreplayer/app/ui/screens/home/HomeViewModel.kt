package com.coreplayer.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.player.PlayerControllerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val artists: List<ArtistDto> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
    val favorites: List<TrackDto> = emptyList(),
    val favoritesCount: Int = favorites.size,
    val recentTracks: List<TrackDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    private val playerController: PlayerControllerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val artists = libraryRepository.getArtists()
                val playlists = libraryRepository.getPlaylists()
                val favorites = libraryRepository.getFavoriteTracks()
                val recent = favorites.take(6)
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Failed to load home data"
                    )
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
        val artistTracks = _uiState.value.favorites.filter {
            it.artist.equals(artist.name, ignoreCase = true) || it.artistId == artist.id
        }
        if (artistTracks.isNotEmpty()) {
            playerController.playTrack(artistTracks.first(), artistTracks)
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
        private val playerController: PlayerControllerManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(libraryRepository, playerController) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
