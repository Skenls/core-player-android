# Core Player: Fixes, Audio Engine, & Monochrome Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve all playback errors (streaming 200 crash, album/playlist playback by ID, search parameter mismatch), implement settings (account info, codec selection, logout), replace orange accents with monochrome white design matching the web player, add the mini-player progress ring, 2-column recent tracks grid, playlist first-track covers, and a 4-tab bottom navigation with a dedicated Browse screen.

**Architecture:** 
- Backend: Stream audio via Starlette `StreamingResponse` without setting `Content-Length` on status 200 to ensure chunked transfer without Uvicorn length mismatch crashes.
- Android Data Layer: Retrofit `CorePlayerApi` fixed with `@Query("query")` for search, plus album/playlist detail endpoints to fetch actual tracklists. SharedPreferences storage for user codec preference (`auto`, `mp3`, `opus`, `flac`).
- Android Audio Engine: ExoPlayer streams native audio by default (or user-chosen codec), properly plays tracks from albums/playlists with queue.
- Android Presentation (Compose):
  - Mini-player: capsule shape with circular progress ring around play button.
  - Full player: monochrome white aesthetic matching `NowPlayingView.tsx` with neat dot thumb scrubber.
  - Home: 2-column recent tracks grid matching `helpers/IMG_DB4D8C23A082-1.jpeg`.
  - Library: Favorites banner at top + Playlists below.
  - Bottom bar: Home, Search, Browse (Albums/Artists/Tracks), Library.
  - Settings: account info, codec selector, logout.

**Tech Stack:** Kotlin 2.0, Jetpack Compose Material 3, AndroidX Media3 (ExoPlayer + MediaSession), Retrofit 2 + Moshi, OkHttp 3, Coil Compose, FastAPI / Uvicorn.

## Global Constraints
- Android Min SDK: 26 (Android 8.0)
- Android Target SDK: 35 (Android 15)
- JVM Target: 17
- Package name: `com.coreplayer.app`
- Theme: Dark Obsidian background (`#0B0F17`), Dark Surface (`#131A26`), Monochrome White accents (strictly no orange, except the specific circular progress ring in mini-player)
- Backend working directory: `/Users/skenls/webplayer`
- Mobile working directory: `/Users/skenls/webplayer-mobile`

---

### Task 1: Fix Backend Streaming & API Endpoints

**Files:**
- Modify: `/Users/skenls/webplayer/backend/app/routers/stream.py:86-96`
- Modify: `/Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/data/api/CorePlayerApi.kt:45-75`
- Modify: `/Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/data/model/Models.kt:40-60`
- Modify: `/Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/data/repository/LibraryRepository.kt:15-50`
- Modify: `/Users/skenls/webplayer-mobile/app/src/test/java/com/coreplayer/app/data/repository/LibraryRepositoryTest.kt` (or create if needed)

**Interfaces:**
- Produces:
  - `CorePlayerApi.getAlbum(id: String): AlbumDetailResponse`
  - `CorePlayerApi.getPlaylist(id: String): PlaylistDetailResponse`
  - `CorePlayerApi.search(@Query("query") query: String): SearchResponse`
  - `LibraryRepository.getAlbumTracks(albumId: String): List<TrackDto>`
  - `LibraryRepository.getPlaylistTracks(playlistId: String): List<TrackDto>`
  - `LibraryRepository.search(query: String): List<TrackDto>`

- [ ] **Step 1: Fix backend `stream.py` to never set `Content-Length` on status 200**

In `/Users/skenls/webplayer/backend/app/routers/stream.py`, replace lines 87-95 with:
```python
    # Handle partial content (206)
    if navidrome_response.status_code == 206:
        if "content-range" in navidrome_response.headers:
            response_headers["Content-Range"] = navidrome_response.headers["content-range"]
        if "content-length" in navidrome_response.headers:
            response_headers["Content-Length"] = navidrome_response.headers["content-length"]
    # For status 200 (full audio), omit Content-Length so Starlette/Uvicorn uses chunked transfer
    # and never raises RuntimeError("Response content longer than Content-Length").
```

- [ ] **Step 2: Add Album & Playlist Detail models in `Models.kt`**

In `app/src/main/java/com/coreplayer/app/data/model/Models.kt`:
```kotlin
data class AlbumDetailResponse(
    @Json(name = "album") val album: AlbumWithSongsDto
)

data class AlbumWithSongsDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "artist") val artist: String? = null,
    @Json(name = "coverArt") val coverArt: String? = null,
    @Json(name = "song") val song: List<TrackDto> = emptyList()
)

data class PlaylistDetailResponse(
    @Json(name = "playlist") val playlist: PlaylistWithSongsDto
)

data class PlaylistWithSongsDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "songCount") val songCount: Int = 0,
    @Json(name = "entry") val entry: List<TrackDto> = emptyList()
)
```

- [ ] **Step 3: Update `CorePlayerApi.kt` endpoints**

In `app/src/main/java/com/coreplayer/app/data/api/CorePlayerApi.kt`:
```kotlin
    @GET("api/library/albums/{album_id}")
    suspend fun getAlbum(
        @Path("album_id") albumId: String
    ): AlbumDetailResponse

    @GET("api/library/playlists/{playlist_id}")
    suspend fun getPlaylist(
        @Path("playlist_id") playlistId: String
    ): PlaylistDetailResponse

    @GET("api/library/search")
    suspend fun search(
        @Query("query") query: String
    ): SearchResponse
```

- [ ] **Step 4: Add helper methods in `LibraryRepository.kt`**

In `app/src/main/java/com/coreplayer/app/data/repository/LibraryRepository.kt`:
```kotlin
    suspend fun getAlbumTracks(albumId: String): List<TrackDto> {
        return try {
            api.getAlbum(albumId).album.song
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch tracks for album $albumId", e)
            emptyList()
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<TrackDto> {
        return try {
            api.getPlaylist(playlistId).playlist.entry
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch tracks for playlist $playlistId", e)
            emptyList()
        }
    }

    suspend fun search(query: String): List<TrackDto> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return try {
            api.search(trimmed).tracks
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query: $trimmed", e)
            emptyList()
        }
    }
```

- [ ] **Step 5: Verify unit tests pass & commit**
Run: `./gradlew testDebugUnitTest`
Commit in `/Users/skenls/webplayer` and `/Users/skenls/webplayer-mobile`.

---

### Task 2: Settings Dialog: Account Info, Codec Preference & Logout

**Files:**
- Create: `app/src/main/java/com/coreplayer/app/ui/screens/settings/SettingsDialog.kt`
- Modify: `app/src/main/java/com/coreplayer/app/player/PlayerControllerManager.kt:120-145`
- Modify: `app/src/main/java/com/coreplayer/app/CorePlayerApp.kt:30-65`
- Modify: `app/src/main/java/com/coreplayer/app/ui/screens/home/HomeScreen.kt:70-110`
- Modify: `app/src/main/java/com/coreplayer/app/ui/navigation/AppNavigation.kt:140-190`

**Interfaces:**
- Consumes: `AuthRepository.getCurrentUser()`, `AuthRepository.logout()`, `CorePlayerApp.codecPreference`
- Produces: `SettingsDialog` composable with account view, codec selector radio group, and Logout action.

- [ ] **Step 1: Add Codec Preference in `CorePlayerApp.kt`**
Provide `getAudioCodec(): String` and `setAudioCodec(codec: String)` stored in `SharedPreferences` (values: `"auto"`, `"mp3"`, `"opus"`, `"flac"`).

- [ ] **Step 2: Update `PlayerControllerManager.kt` streaming URI**
In `toMediaItem(track: TrackDto, baseUrl: String)`:
Read the codec preference. If not `"auto"`:
`val streamUri = if (codec == "auto") "$cleanBaseUrl/api/stream/$id" else "$cleanBaseUrl/api/stream/$id?format=$codec"`

- [ ] **Step 3: Build `SettingsDialog.kt`**
Composable dialog featuring:
1. Header: "Настройки"
2. Account card:
   - User icon
   - Username (`user?.username ?: "Пользователь"`)
   - Server URL (`serverUrl`)
3. Codec picker:
   - "Формат аудио":
     - `Auto (Исходный)`
     - `MP3 (320 kbps)`
     - `Opus (160 kbps)`
     - `FLAC (Lossless)`
4. "Выйти из аккаунта" button:
   - Calls `onLogout()`.
   - Clears session cookies in `CookieJar`.
   - Clears saved credentials.
   - Closes dialog and navigates to `Screen.Login.route` clearing backstack.

- [ ] **Step 4: Integrate `SettingsDialog` into `HomeScreen.kt`**
Replace navigation to raw login with opening `SettingsDialog`.

- [ ] **Step 5: Verify unit tests pass & commit**
Run: `./gradlew testDebugUnitTest`
Commit: `feat(settings): add user profile view, audio codec selector, and logout`

---

### Task 3: Redesign Now Playing Sheet to Monochrome White & Sleek Dot Scrubber

**Files:**
- Modify: `app/src/main/java/com/coreplayer/app/ui/screens/nowplaying/NowPlayingBottomSheet.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/theme/Color.kt`

**Interfaces:**
- Produces: `NowPlayingBottomSheet` with white play controls, red/white heart toggle, and custom sleek progress bar with a clean white dot thumb.

- [ ] **Step 1: Replace Orange with Monochrome White Controls**
In `NowPlayingBottomSheet.kt`:
- Play/Pause Button: Large circular button (`size = 68.dp`, `background = Color.White`), icon is black (`tint = Color.Black`).
- Shuffle & Repeat: `Color.White` or `Color(0xFF60A5FA)` when active, `Color(0x66FFFFFF)` when inactive.
- Lyrics Toggle: `Color.White` when active, `Color(0x66FFFFFF)` when inactive.
- Heart Icon: Outline `Color(0x88FFFFFF)` when inactive, filled red `Color(0xFFEF4444)` when active.

- [ ] **Step 2: Replace standard Slider with sleek custom progress bar & dot thumb**
Implement custom progress bar:
- Track: Height `4.dp`, rounded corners, inactive track `Color(0x22FFFFFF)`, active track `Color.White`.
- Thumb: Sleek neat white circle dot (`size = 12.dp`, `Color.White`, subtle shadow).
- Touch target: Full `24.dp` vertical draggable area with smooth pointer input (`pointerInput { detectHorizontalDragGestures ... }`) or custom Slider thumb.

- [ ] **Step 3: Fix favorite toggle sync & feedback**
Do not blindly invert `isFavorite` on network error. Keep state synced, log errors properly.

- [ ] **Step 4: Verify unit tests pass & commit**
Run: `./gradlew testDebugUnitTest`
Commit: `feat(nowplaying): redesign to monochrome white aesthetic with sleek dot scrubber`

---

### Task 4: Mini-Player Circular Progress Ring Around Play Button

**Files:**
- Modify: `app/src/main/java/com/coreplayer/app/ui/components/FloatingMiniPlayer.kt`

**Interfaces:**
- Produces: `FloatingMiniPlayer` where the play button bubble has a circular progress ring filling up from 0% to 100% as the song plays.

- [ ] **Step 1: Implement Circular Progress Ring in `FloatingMiniPlayer.kt`**
Around the white Play/Pause circle (`size = 38.dp`):
Use a `Box(modifier = Modifier.size(48.dp))` with `Canvas`:
1. Inactive ring: `drawCircle` or `drawArc` with `color = Color(0x33FF5722)` (subtle dark orange/gray track), `style = Stroke(width = 3.dp)`.
2. Active ring: `drawArc` starting from `-90f` (12 o'clock), sweeping `360f * progress` clockwise with `color = Color(0xFFFF5722)` and `style = Stroke(width = 3.dp, cap = StrokeCap.Round)`.
3. Center: Solid white circle (`size = 36.dp`) containing the black play/pause icon.

- [ ] **Step 2: Animate Progress & Playback State**
Smooth progress interpolation with `animateFloatAsState`.
White heart icon outline with heart pop animation when clicked.

- [ ] **Step 3: Verify unit tests pass & commit**
Run: `./gradlew testDebugUnitTest`
Commit: `feat(miniplayer): add animated circular progress ring around play button`

---

### Task 5: Home Screen Quick-Access Recent Tracks Grid (helpers Reference)

**Files:**
- Create: `app/src/main/java/com/coreplayer/app/ui/components/RecentTrackCard.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/screens/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `HomeViewModel.recentTracks: StateFlow<List<TrackDto>>`
- Produces: `RecentTrackCard` 2-column quick-access grid on `HomeScreen` strictly matching `helpers/IMG_DB4D8C23A082-1.jpeg`.

- [ ] **Step 1: Create `RecentTrackCard.kt`**
Card specifications:
- Container: Squircle card (`height = 56.dp`, `shape = RoundedCornerShape(8.dp)`, `background = Color(0xFF1E222B)`).
- Left: Square album artwork (`size = 56.dp`, `shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)`).
- Right: Column with vertical centering:
  - Track Title (13sp, bold, `Color.White`, maxLines = 1, ellipsis).
  - Artist Name (11sp, `Color(0xFF8E95A5)`, maxLines = 1, ellipsis).
- Clickable with ripple.

- [ ] **Step 2: Update `HomeViewModel.kt` to load Recent Tracks**
Load user's recent or favorite tracks (4 to 6 items) into `recentTracks: List<TrackDto>`.

- [ ] **Step 3: Update `HomeScreen.kt` Layout**
Replace "Недавние артисты" horizontal scroll with:
- Section header "Недавние"
- 2-column grid (`LazyVerticalGrid(columns = GridCells.Fixed(2))` or 2-column Rows):
  Display up to 6 `RecentTrackCard` items.
  Tapping a card immediately calls `playerController.playTrack(track, recentTracks)`.

- [ ] **Step 4: Verify unit tests pass & commit**
Run: `./gradlew testDebugUnitTest`
Commit: `feat(home): replace recent artists with 2-column recent tracks grid`

---

### Task 6: Playlist First-Track Cover Art & Fix Album/Playlist Playback

**Files:**
- Modify: `app/src/main/java/com/coreplayer/app/ui/components/PlaylistCard.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/screens/library/LibraryScreen.kt`

**Interfaces:**
- Produces: Cover art loaded from playlist's first track rather than default gradient. Clicking an album or playlist fetches its songs and plays the first song with full queue.

- [ ] **Step 1: Update `PlaylistCard.kt` to accept Cover Art URL**
If `coverArtUrl` is present, display Coil `AsyncImage`. Fall back to gradient preset only if null.

- [ ] **Step 2: Load First-Track Cover Art in `HomeViewModel` and `LibraryScreen`**
For each playlist where `coverArt` is null, asynchronously fetch `libraryRepository.getPlaylistTracks(playlist.id)` and use `tracks.firstOrNull()?.coverArt ?: tracks.firstOrNull()?.id`.

- [ ] **Step 3: Fix Album and Playlist Playback**
When an album is clicked in `LibraryScreen` or `HomeScreen`:
```kotlin
scope.launch {
    val tracks = libraryRepository.getAlbumTracks(album.id)
    if (tracks.isNotEmpty()) {
        playerController.playTrack(tracks.first(), tracks)
    }
}
```
When a playlist is clicked:
```kotlin
scope.launch {
    val tracks = libraryRepository.getPlaylistTracks(playlist.id)
    if (tracks.isNotEmpty()) {
        playerController.playTrack(tracks.first(), tracks)
    }
}
```

- [ ] **Step 4: Verify unit tests pass & commit**
Run: `./gradlew testDebugUnitTest`
Commit: `fix(library): resolve playlist covers from first track and fix album tracklist playback`

---

### Task 7: Global Orange Purge & 4-Tab Bottom Bar with Dedicated Browse Screen

**Files:**
- Create: `app/src/main/java/com/coreplayer/app/ui/screens/browse/BrowseScreen.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/components/BottomNavBar.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/screens/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/screens/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/coreplayer/app/ui/navigation/AppNavigation.kt`

**Interfaces:**
- Produces:
  - `BrowseScreen` hosting 3 tabs: "Все треки", "Альбомы", "Артисты".
  - `LibraryScreen` reorganized to display: Favorites banner at top ("Мои треки") + Playlists below.
  - `BottomNavBar` with 4 items: `Home`, `Search`, `Browse`, `Library`.
  - Clean monochrome design across the entire application.

- [ ] **Step 1: Create `BrowseScreen.kt`**
Tabs:
1. "Треки" (List of favorite or all tracks)
2. "Альбомы" (Grid of `AlbumDto` with track playback)
3. "Артисты" (List/Grid of `ArtistDto`)
Tab indicators: clean white underline.

- [ ] **Step 2: Simplify `LibraryScreen.kt`**
Structure:
1. Title: "Медиатека"
2. Favorites Banner: "Мои треки" with heart icon.
3. Section title: "Плейлисты"
4. Grid/List of Playlists.
No orange accents.

- [ ] **Step 3: Update `BottomNavBar.kt` to 4 Tabs**
Tabs:
1. `Home` ("Главная")
2. `Search` ("Поиск")
3. `Browse` ("Все треки")
4. `Library` ("Медиатека")
Selected item tint: `Color.White`, unselected item tint: `Color(0xFF8E95A5)`.

- [ ] **Step 4: Update `SearchScreen.kt` Colors**
Replace any orange progress indicator or icons with `Color.White`.

- [ ] **Step 5: Verify unit tests pass & commit**
Run: `./gradlew testDebugUnitTest`
Commit: `feat(navigation): introduce 4-tab bar with Browse screen and monochrome theme`

---

### Task 8: End-to-End Verification & APK Build

**Files:**
- All modified files

- [ ] **Step 1: Run all unit tests**
Run: `./gradlew testDebugUnitTest`
Expected: 100% tests pass.

- [ ] **Step 2: Assemble Debug APK**
Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` and APK generated at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Update walkthrough.md and prepare report**
Document all completed features and verification results.
