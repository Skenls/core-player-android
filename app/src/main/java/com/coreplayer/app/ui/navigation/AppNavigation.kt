package com.coreplayer.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coreplayer.app.CorePlayerApp
import com.coreplayer.app.data.repository.AuthRepository
import com.coreplayer.app.data.repository.LibraryRepository
import com.coreplayer.app.data.repository.LyricsRepository
import com.coreplayer.app.player.PlayerControllerManager
import com.coreplayer.app.ui.components.BottomNavBar
import com.coreplayer.app.ui.components.FloatingMiniPlayer
import com.coreplayer.app.ui.screens.home.HomeScreen
import com.coreplayer.app.ui.screens.library.LibraryScreen
import com.coreplayer.app.ui.screens.login.LoginScreen
import com.coreplayer.app.ui.screens.nowplaying.NowPlayingBottomSheet
import com.coreplayer.app.ui.screens.search.SearchScreen
import com.coreplayer.app.ui.theme.DarkBackground
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authRepository: AuthRepository = CorePlayerApp.instance.authRepository,
    libraryRepository: LibraryRepository = CorePlayerApp.instance.libraryRepository,
    lyricsRepository: LyricsRepository = CorePlayerApp.instance.lyricsRepository,
    playerController: PlayerControllerManager = CorePlayerApp.instance.playerController,
    startDestination: String = Screen.Home.route
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination
    val playerState by playerController.state.collectAsStateWithLifecycle()

    var showNowPlayingSheet by remember { mutableStateOf(false) }
    var isCurrentTrackFavorite by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                } catch (_: Exception) {
                    isCurrentTrackFavorite = !isCurrentTrackFavorite
                }
            }
        }
    }

    val isBottomBarVisible = currentRoute in listOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.Library.route
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        bottomBar = {
            if (isBottomBarVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Floating mini-player container above bottom navigation bar
                    if (playerState.currentTrack != null) {
                        FloatingMiniPlayer(
                            playerState = playerState,
                            isFavorite = isCurrentTrackFavorite,
                            onTogglePlayPause = { playerController.togglePlayPause() },
                            onToggleFavorite = toggleFavorite,
                            onClick = { showNowPlayingSheet = true }
                        )
                    }

                    BottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (route != currentRoute) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    authRepository = authRepository,
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    libraryRepository = libraryRepository,
                    playerController = playerController,
                    onNavigateToFavorites = {
                        navController.navigate(Screen.Library.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToPlaylists = {
                        navController.navigate(Screen.Library.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    libraryRepository = libraryRepository,
                    playerController = playerController
                )
            }

            composable(Screen.Library.route) {
                LibraryScreen(
                    libraryRepository = libraryRepository,
                    playerController = playerController
                )
            }
        }

        if (showNowPlayingSheet && playerState.currentTrack != null) {
            NowPlayingBottomSheet(
                playerController = playerController,
                lyricsRepository = lyricsRepository,
                isFavorite = isCurrentTrackFavorite,
                onToggleFavorite = toggleFavorite,
                onDismiss = { showNowPlayingSheet = false }
            )
        }
    }
}
