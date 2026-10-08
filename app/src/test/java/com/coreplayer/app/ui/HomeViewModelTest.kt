package com.coreplayer.app.ui

import com.coreplayer.app.data.model.ArtistDto
import com.coreplayer.app.data.model.FavoriteToggleResponse
import com.coreplayer.app.data.model.PlaylistDto
import com.coreplayer.app.data.model.TrackDto
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.ui.screens.home.HomeViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var playerController: PlayerControllerManager

    private val sampleArtists = listOf(
        ArtistDto(id = "art-1", name = "Radiohead", albumCount = 9),
        ArtistDto(id = "art-2", name = "The Neighbourhood", albumCount = 4)
    )

    private val samplePlaylists = listOf(
        PlaylistDto(id = "pl-1", name = "Вечерний вайб", songCount = 57),
        PlaylistDto(id = "pl-2", name = "Chill vibes", songCount = 82)
    )

    private val sampleFavorites = listOf(
        TrackDto(id = "trk-1", title = "Karma Police", artist = "Radiohead", duration = 264),
        TrackDto(id = "trk-2", title = "Sweater Weather", artist = "The Neighbourhood", duration = 240)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        libraryRepository = mockk(relaxed = true)
        playerController = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadHomeData_success_populatesState() = runTest(testDispatcher) {
        coEvery { libraryRepository.getArtists() } returns sampleArtists
        coEvery { libraryRepository.getPlaylists() } returns samplePlaylists
        coEvery { libraryRepository.getFavoriteTracks() } returns sampleFavorites

        val viewModel = HomeViewModel(libraryRepository, playerController)

        // Initially loading
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(sampleArtists, state.artists)
        assertEquals(samplePlaylists, state.playlists)
        assertEquals(sampleFavorites, state.favorites)
        assertEquals(2, state.favoritesCount)
    }

    @Test
    fun loadHomeData_failure_setsError() = runTest(testDispatcher) {
        coEvery { libraryRepository.getArtists() } throws RuntimeException("Network connection failed")

        val viewModel = HomeViewModel(libraryRepository, playerController)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertEquals("Network connection failed", state.error)
    }

    @Test
    fun playFavorites_playsFirstTrackWithQueue() = runTest(testDispatcher) {
        coEvery { libraryRepository.getArtists() } returns sampleArtists
        coEvery { libraryRepository.getPlaylists() } returns samplePlaylists
        coEvery { libraryRepository.getFavoriteTracks() } returns sampleFavorites

        val viewModel = HomeViewModel(libraryRepository, playerController)
        advanceUntilIdle()

        viewModel.playFavorites()

        verify { playerController.playTrack(sampleFavorites[0], sampleFavorites) }
    }

    @Test
    fun playFavorites_empty_doesNotPlay() = runTest(testDispatcher) {
        coEvery { libraryRepository.getArtists() } returns sampleArtists
        coEvery { libraryRepository.getPlaylists() } returns samplePlaylists
        coEvery { libraryRepository.getFavoriteTracks() } returns emptyList()

        val viewModel = HomeViewModel(libraryRepository, playerController)
        advanceUntilIdle()

        viewModel.playFavorites()

        verify(exactly = 0) { playerController.playTrack(any(), any()) }
    }

    @Test
    fun playPlaylist_playsTrack() = runTest(testDispatcher) {
        coEvery { libraryRepository.getArtists() } returns sampleArtists
        coEvery { libraryRepository.getPlaylists() } returns samplePlaylists
        coEvery { libraryRepository.getFavoriteTracks() } returns sampleFavorites

        val viewModel = HomeViewModel(libraryRepository, playerController)
        advanceUntilIdle()

        val playlist = samplePlaylists[0]
        viewModel.playPlaylist(playlist)

        verify {
            playerController.playTrack(
                match { it.id == playlist.id && it.title == playlist.name },
                sampleFavorites
            )
        }
    }

    @Test
    fun playArtist_withMatchingFavoriteTrack_playsArtistTracks() = runTest(testDispatcher) {
        coEvery { libraryRepository.getArtists() } returns sampleArtists
        coEvery { libraryRepository.getPlaylists() } returns samplePlaylists
        coEvery { libraryRepository.getFavoriteTracks() } returns sampleFavorites

        val viewModel = HomeViewModel(libraryRepository, playerController)
        advanceUntilIdle()

        val artist = sampleArtists[0] // Radiohead
        viewModel.playArtist(artist)

        verify {
            playerController.playTrack(
                sampleFavorites[0],
                listOf(sampleFavorites[0])
            )
        }
    }

    @Test
    fun playArtist_withoutMatchingFavoriteTrack_synthesizesAndPlays() = runTest(testDispatcher) {
        coEvery { libraryRepository.getArtists() } returns sampleArtists
        coEvery { libraryRepository.getPlaylists() } returns samplePlaylists
        coEvery { libraryRepository.getFavoriteTracks() } returns sampleFavorites

        val viewModel = HomeViewModel(libraryRepository, playerController)
        advanceUntilIdle()

        val unknownArtist = ArtistDto(id = "art-99", name = "Unknown Band")
        viewModel.playArtist(unknownArtist)

        verify {
            playerController.playTrack(
                match { it.id == "art-99" && it.title == "Unknown Band" },
                match { it.size == 1 && it[0].id == "art-99" }
            )
        }
    }

    @Test
    fun toggleFavorite_callsRepositoryAndUpdatesFavorites() = runTest(testDispatcher) {
        coEvery { libraryRepository.getArtists() } returns sampleArtists
        coEvery { libraryRepository.getPlaylists() } returns samplePlaylists
        coEvery { libraryRepository.getFavoriteTracks() } returns sampleFavorites andThen emptyList()
        coEvery { libraryRepository.toggleFavorite("trk-1") } returns FavoriteToggleResponse(itemId = "trk-1", isFavoriteBool = false)

        val viewModel = HomeViewModel(libraryRepository, playerController)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.favoritesCount)

        viewModel.toggleFavorite("trk-1")
        advanceUntilIdle()

        coVerify { libraryRepository.toggleFavorite("trk-1") }
        assertEquals(0, viewModel.uiState.value.favoritesCount)
    }

    @Test
    fun refresh_reloadsHomeData() = runTest(testDispatcher) {
        coEvery { libraryRepository.getArtists() } returns sampleArtists
        coEvery { libraryRepository.getPlaylists() } returns samplePlaylists
        coEvery { libraryRepository.getFavoriteTracks() } returns sampleFavorites

        val viewModel = HomeViewModel(libraryRepository, playerController)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(atLeast = 2) { libraryRepository.getArtists() }
        coVerify(atLeast = 2) { libraryRepository.getPlaylists() }
        coVerify(atLeast = 2) { libraryRepository.getFavoriteTracks() }
    }
}
