# Core Player Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Создать нативное Android-приложение для Core Player на Kotlin и Jetpack Compose с Media3/ExoPlayer, взаимодействующее с FastAPI-бэкендом Core Player и повторяющее интерфейс из `helpers/`.

**Architecture:** MVVM Clean Architecture в рамках Android-модуля `app`. Фоновая аудиослужба на `Media3 MediaSessionService` обеспечивает непрерывный стриминг и системную шторку уведомлений. Сетевой слой на Retrofit/OkHttp поддерживает сессионные cookies. UI на Jetpack Compose реализует темную тему, скругленные карточки, плавающий капсульный мини-плеер и караоке-лирику.

**Tech Stack:** Kotlin 2.0+, Jetpack Compose, Material3, Navigation Compose, Media3 (ExoPlayer + Session), Retrofit2, Moshi, OkHttp3, Coil Compose.

**Spec:** [docs/superpowers/specs/2026-10-08-core-player-android-design.md](file:///Users/skenls/webplayer-mobile/docs/superpowers/specs/2026-10-08-core-player-android-design.md)

## Global Constraints

- Android Min SDK: 26 (Android 8.0)
- Android Target SDK: 35 (Android 15)
- JVM Target: 17
- Single-module project structure in repo root: `app/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`
- Background playback MUST use `MediaSessionService` with foreground notification
- Streaming endpoint: `${serverUrl}/api/stream/{trackId}?format=mp3`
- UI colors and layout must strictly follow the mockup in `helpers/ChatGPT Image 8 окт. 2026 г., 11_34_30.png`

---

### Task 1: Project Scaffolding & Gradle Configuration

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`

**Interfaces:**
- Consumes: None (root configuration)
- Produces: Gradle build system with Android plugin, Compose compiler, Media3, Retrofit, and OkHttp dependencies.

- [x] **Step 1: Create `settings.gradle.kts` with plugin management and repositories**
- [x] **Step 2: Create root `build.gradle.kts`**
- [x] **Step 3: Create `gradle/wrapper/gradle-wrapper.properties` and executable `gradlew`**
- [x] **Step 4: Create `app/build.gradle.kts` with all required dependencies**
- [x] **Step 5: Create `app/src/main/AndroidManifest.xml`**
- [x] **Step 6: Commit scaffolding**

```bash
git add settings.gradle.kts build.gradle.kts gradle/ app/
git commit -m "chore: scaffold Android project and Gradle build configuration"
```

---

### Task 2: Data Models, LRC Parser & API Network Layer

**Files:**
- Create: `app/src/main/java/com/coreplayer/app/data/model/Models.kt`
- Create: `app/src/main/java/com/coreplayer/app/data/lyrics/LrcParser.kt`
- Create: `app/src/test/java/com/coreplayer/app/data/lyrics/LrcParserTest.kt`
- Create: `app/src/main/java/com/coreplayer/app/data/api/CookieJar.kt`
- Create: `app/src/main/java/com/coreplayer/app/data/api/CorePlayerApi.kt`
- Create: `app/src/main/java/com/coreplayer/app/data/repository/AuthRepository.kt`
- Create: `app/src/main/java/com/coreplayer/app/data/repository/LibraryRepository.kt`
- Create: `app/src/main/java/com/coreplayer/app/data/repository/LyricsRepository.kt`

**Interfaces:**
- Consumes: Network connectivity & Core Player FastAPI backend contract
- Produces: `CorePlayerApi`, `LrcParser.parse(lrcContent: String): List<LyricLine>`, `AuthRepository`, `LibraryRepository`, `LyricsRepository`

- [x] **Step 1: Write unit tests for `LrcParser`**
- [x] **Step 2: Implement `LrcParser` and data models**
- [x] **Step 3: Run `LrcParserTest` to verify it passes**
- [x] **Step 4: Implement `InMemoryCookieJar` and `CorePlayerApi` interface**
- [x] **Step 5: Implement Repositories**
- [x] **Step 6: Commit data layer**

```bash
git add app/src/main/java/com/coreplayer/app/data/ app/src/test/java/com/coreplayer/app/data/
git commit -m "feat: add data models, LRC parser, Retrofit API and repositories"
```

---

### Task 3: Media3 Playback Service & Player Controller

**Files:**
- Create: `app/src/main/java/com/coreplayer/app/player/PlaybackService.kt`
- Create: `app/src/main/java/com/coreplayer/app/player/PlayerState.kt`
- Create: `app/src/main/java/com/coreplayer/app/player/PlayerControllerManager.kt`
- Create: `app/src/test/java/com/coreplayer/app/player/PlayerStateTest.kt`

**Interfaces:**
- Consumes: Android System MediaSession & ExoPlayer
- Produces: `PlayerControllerManager` exposing `StateFlow<PlayerState>`, `playTrack(track: TrackDto)`, `togglePlayPause()`, `seekTo(positionMs: Long)`, `skipNext()`, `skipPrevious()`

- [x] **Step 1: Write unit test for PlayerState transitions**
- [x] **Step 2: Implement `PlaybackService` extending `MediaSessionService`**
- [x] **Step 3: Implement `PlayerControllerManager`**
- [x] **Step 4: Run unit tests**
- [x] **Step 5: Commit audio service**

```bash
git add app/src/main/java/com/coreplayer/app/player/ app/src/test/java/com/coreplayer/app/player/
git commit -m "feat: implement Media3 PlaybackService and PlayerControllerManager"
```

---

### Task 4: Design System & Core UI Components (helpers Mockup)

**Files:**
- Create: `app/src/main/java/com/coreplayer/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/theme/Type.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/components/ArtistCard.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/components/PlaylistCard.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/components/FavoritesBanner.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/components/BottomNavBar.kt`

**Interfaces:**
- Consumes: Material 3, Coil Compose, Theme colors
- Produces: Reusable UI components matching `helpers/ChatGPT Image 8 окт. 2026 г., 11_34_30.png`

- [ ] **Step 1: Define Theme and Color palette**

Background `#0B0F17`, Surface `#131A26`, Play Accent `#FF5722`, Text White `#FFFFFF`, Subtitle `#8E95A5`.

- [ ] **Step 2: Implement `ArtistCard`**

Square card with rounded corners (16dp), artist image loaded via Coil, artist name, and subtitle "Артист".

- [ ] **Step 3: Implement `FavoritesBanner`**

Wide landscape banner card with rounded corners (20dp), background image, title "Мои треки", subtitle "Плейлист · N треков", and favorite heart icon on top right.

- [ ] **Step 4: Implement `PlaylistCard`**

Square playlist card with cover image, translucent play button overlay in bottom-left corner, title and subtitle "Плейлист · N треков".

- [ ] **Step 5: Implement `BottomNavBar`**

3-item navigation bar: Home, Search, Library with clean typography and matching icons.

- [ ] **Step 6: Commit design system and components**

```bash
git add app/src/main/java/com/coreplayer/app/ui/theme/ app/src/main/java/com/coreplayer/app/ui/components/
git commit -m "feat: implement dark glassmorphic design system and helper components"
```

---

### Task 5: Floating Mini-Player & Now Playing Sheet with Karaoke Lyrics

**Files:**
- Create: `app/src/main/java/com/coreplayer/app/ui/components/FloatingMiniPlayer.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/screens/nowplaying/SyncedLyricsView.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/screens/nowplaying/NowPlayingBottomSheet.kt`

**Interfaces:**
- Consumes: `PlayerState`, `PlayerControllerManager`, `LyricsRepository`
- Produces: Floating mini-player capsule and expandable full-screen player with synced LRC karaoke lyrics

- [ ] **Step 1: Implement `FloatingMiniPlayer`**

Pill-shaped floating card elevated above the bottom navigation bar:
- Orange circular Play/Pause button on the left
- Track title and artist name in the center
- Heart favorite toggle on the right
- Click opens `NowPlayingBottomSheet`

- [ ] **Step 2: Implement `SyncedLyricsView`**

Compose `LazyColumn` for karaoke lyrics:
- Automatic smooth scroll to current line based on `currentPositionMs`
- Highlight active line with bold white font and slight scale animation
- Inactive lines rendered with 40% opacity

- [ ] **Step 3: Implement `NowPlayingBottomSheet`**

Full-screen player modal:
- High-res album artwork with dynamic background blur
- Seek bar with elapsed time and total duration
- Playback controls: Shuffle, Previous, Play/Pause, Next, Repeat
- Toggle button for Lyrics view

- [ ] **Step 4: Commit mini-player and Now Playing screens**

```bash
git add app/src/main/java/com/coreplayer/app/ui/components/FloatingMiniPlayer.kt app/src/main/java/com/coreplayer/app/ui/screens/nowplaying/
git commit -m "feat: implement floating mini-player capsule and Now Playing with karaoke lyrics"
```

---

### Task 6: Home Screen, Navigation & App Integration

**Files:**
- Create: `app/src/main/java/com/coreplayer/app/ui/screens/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/screens/home/HomeScreen.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/screens/login/LoginScreen.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/screens/search/SearchScreen.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/screens/library/LibraryScreen.kt`
- Create: `app/src/main/java/com/coreplayer/app/ui/navigation/AppNavigation.kt`
- Create: `app/src/main/java/com/coreplayer/app/MainActivity.kt`
- Create: `app/src/main/java/com/coreplayer/app/CorePlayerApp.kt`

**Interfaces:**
- Consumes: All repositories, UI components, Navigation Compose
- Produces: Complete working Android application

- [ ] **Step 1: Implement `HomeViewModel`**

Fetch artists, favorite tracks, and playlists from `LibraryRepository`, expose `HomeUiState(artists, favorites, playlists, isLoading, error)`.

- [ ] **Step 2: Implement `HomeScreen` exactly matching mockup**

- Header: bold "core" logo
- "Недавние артисты": `LazyRow` of `ArtistCard`
- "Избранное >": `FavoritesBanner`
- "Плейлисты >": `LazyRow` of `PlaylistCard`

- [ ] **Step 3: Implement `LoginScreen`, `SearchScreen`, `LibraryScreen`**

Login screen for setting server URL and credentials; Search screen for searching library; Library screen for browsing albums and tracks.

- [ ] **Step 4: Implement `AppNavigation`, `MainActivity`, and `CorePlayerApp`**

Compose scaffold with bottom navigation bar and floating mini-player container.

- [ ] **Step 5: Run unit tests and assemble debug APK**

Run:
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
Verify APK generation at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 6: Commit complete application**

```bash
git add app/src/main/java/com/coreplayer/app/
git commit -m "feat: integrate HomeScreen, navigation graph, and MainActivity"
```
