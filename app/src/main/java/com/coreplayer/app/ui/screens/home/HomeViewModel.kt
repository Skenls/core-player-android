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
                _uiState.update {
                    it.copy(
                        artists = artists,
                        playlists = playlists,
                        favorites = favorites,
                        favoritesCount = favorites.size,
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

    fun playPlaylist(playlist: PlaylistDto) {
        val tracks = _uiState.value.favorites
        val track = TrackDto(
            id = playlist.id,
            title = playlist.name,
            artist = "Плейлист",
            coverArt = playlist.coverArt
        )
        val queue = if (tracks.isNotEmpty()) tracks else listOf(track)
        playerController.playTrack(track, queue)
    }

    fun playArtist(artist: ArtistDto) {
        val artistTracks = _uiState.value.favorites.filter {
            it.artist.equals(artist.name, ignoreCase = true) || it.artistId == artist.id
        }
        if (artistTracks.isNotEmpty()) {
            playerController.playTrack(artistTracks.first(), artistTracks)
        } else {
            val track = TrackDto(
                id = artist.id,
                title = artist.name,
                artist = artist.name,
                coverArt = artist.coverArt
            )
            playerController.playTrack(track, listOf(track))
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
            } catch (_: Exception) {
                // Silently handle error
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
