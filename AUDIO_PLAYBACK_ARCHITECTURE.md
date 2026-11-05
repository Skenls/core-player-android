# Core Player Audio Playback Architecture & Uninterrupted Streaming Engine

This document provides a comprehensive technical overview of the audio playback pipeline in Core Player Android. It details the complete end-to-end chain from server audio delivery to hardware output, alongside the mechanisms designed to guarantee uninterrupted, low-latency playback across network fluctuations and app lifecycle events.

---

## 1. High-Level Architecture Overview

The audio playback subsystem is built on **AndroidX Media3 (ExoPlayer)** and organized into four core layers:

```mermaid
flowchart TD
    subgraph Server ["Server & Backend Proxy"]
        N[Navidrome Server / Transcoder] -->|HTTP Stream / Range 206| B[FastAPI Proxy / Backend]
        B -->|Audio Stream + Headers| NET[Network Layer]
    end

    subgraph Data ["Data & Caching Layer"]
        NET --> OK[OkHttpClient + Custom OkHttpDataSource]
        OK --> CM[TrackCacheManager]
        CM -->|Read Cache| SC[(Media3 SimpleCache + Disk Cache)]
        CM -->|Cache Miss| UP[Upstream OkHttp Source]
        SC --> DS[CacheDataSource]
        UP --> DS
    end

    subgraph Service ["Playback Engine (Foreground Service)"]
        DS --> MSF[DefaultMediaSourceFactory]
        MSF --> EF[DefaultExtractorsFactory]
        EF --> EP1[Primary ExoPlayer]
        EF --> EP2[Secondary ExoPlayer]
        EP1 <--> XF[Crossfade Controller]
        EP2 <--> XF
        PS[PlaybackService (MediaSessionService)] --> EP1
        PS --> EP2
        MS[MediaSessionCompat / Media3 MediaSession] --> PS
    end

    subgraph Controller ["UI & State Management"]
        MC[MediaController] <--> MS
        PCM[PlayerControllerManager] <--> MC
        PCM --> UI[Jetpack Compose UI (NowPlaying, MiniPlayer, Lyrics)]
    end
```

---

## 2. End-to-End Playback Flow: From Server to Hardware

### Step 1: Request Generation & Server-Side Transcoding
1. **URI Resolution**:
   When a track is prepared (`TrackDto.toMediaItem` in [`PlayerState.kt`](file:///Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/player/PlayerState.kt)), the app resolves the streaming endpoint:
   - **Raw / Original**: `/api/stream/{id}`
   - **Transcoded (Opus, MP3, FLAC, AAC)**: `/api/stream/{id}?format={codec}&estimateContentLength=true`
2. **Backend Proxy & Header Conditioning**:
   - The backend proxies requests to Navidrome's Subsonic API (`/rest/stream.view`).
   - By supplying `estimateContentLength=true`, the transcoding daemon pre-calculates the estimated audio length, returning `Content-Length` and `Accept-Ranges: bytes`.
   - The backend forwards `Content-Length` on both `200 OK` (initial play) and `206 Partial Content` (range seek).
3. **MIME Type Signaling**:
   - URLs without file extensions can cause container sniffing delays. `PlayerState.kt` explicitly attaches container MIME types (e.g., `MimeTypes.AUDIO_OGG` for Opus, `MimeTypes.AUDIO_MPEG` for MP3) directly to the `MediaItem`.

### Step 2: Caching & Upstream Ingestion
- Media requests pass through `TrackCacheManager`'s `playbackDataSourceFactory`:
  - **Cache Hit**: Audio bytes are read directly from local flash storage (`SimpleCache`) without touching the cellular or Wi-Fi network.
  - **Cache Miss**: `OkHttpDataSource` performs HTTP Range queries via `OkHttpClient`, leveraging HTTP/1.1 and HTTP/2 connection reuse and connection pooling.
  - **Write Isolation**: To prevent thread contention between ExoPlayer reading audio for playback and background pre-fetching, `playbackDataSourceFactory` sets `setCacheWriteDataSinkFactory(null)`. Write operations are strictly managed by dedicated background download workers.

### Step 3: Demuxing & Extraction
1. `DefaultExtractorsFactory` passes the incoming byte stream to the appropriate extractor:
   - **Opus / Ogg**: `OggExtractor` -> `StreamReader` / `OpusReader`. Because `Content-Length` is known from headers, `DefaultOggSeeker` is instantiated, enabling full bisection seeking across Ogg pages.
   - **MP3**: `Mp3Extractor` with `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS` and `FLAG_ENABLE_INDEX_SEEKING`.
   - **FLAC**: `FlacExtractor` using stream info metadata.
2. The demuxer produces packetized, timestamped audio samples and sends them to the playback pipeline.

### Step 4: Decoding & Audio Sink Rendering
1. **Audio Track Initialization**:
   ExoPlayer's `MediaCodecAudioRenderer` routes compressed samples to Android's native hardware decoder (`MediaCodec`) or software decoders.
2. **Audio Track / Hardware Output**:
   Decoded PCM frames are fed to `DefaultAudioSink`, configured with:
   - `AudioAttributes`: `USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`.
   - System audio focus auto-management (`handleAudioFocus = true`).
   - Noise handling (`setHandleAudioBecomingNoisy(true)`) to automatically pause playback on headphone disconnection.
3. PCM frames pass via Android Audio HAL to ALSA/TinyALSA and the hardware DAC/DSP.

---

## 3. Mechanisms for Uninterrupted & Continuous Playback

Core Player incorporates multiple architectural mechanisms to ensure playback never stutters, drops, or terminates unexpectedly.

### 1. Foreground Service & Lifecycle Survivability
- **`PlaybackService` (`MediaSessionService`)**:
  Playback runs inside an Android `ForegroundService` tied to a high-priority media notification (`DefaultMediaNotificationProvider`).
  - **Process Priority**: Holding an active foreground service prevents the Android low-memory killer (OOM killer) from killing the process while running in the background or with the screen off.
  - **Wake Lock Management**: ExoPlayer automatically holds a partial wake lock while playing, preventing the CPU from sleeping mid-stream.
  - **Headless MediaSession**: `MediaSession` allows Android Auto, Bluetooth accessories, smartwatch controls, and lock-screen widgets to control playback without keeping UI activities in memory.

### 2. Intelligent Two-Stage Pre-Caching (`TrackCacheManager`)
To eliminate buffering delays between tracks and prevent mobile network dropouts:
- **Two-Stage Pipeline**:
  1. **Stage 1 (Current Track)**: When a track starts, a background `CacheWriter` immediately pre-fetches the remaining unbuffered segments of the track into disk cache (`media_cache`, up to 500 MB LRU).
  2. **Stage 2 (Next Track)**: Once the active track is fully cached, the worker automatically transitions to pre-fetching the upcoming track in the queue. When the user skips forward or the song ends, playback begins instantaneously from local disk.
- **Cache Eviction Strategy**:
  - `LeastRecentlyUsedCacheEvictor` automatically trims old audio files once the 500 MB limit is reached.
  - On queue switches or long idle periods, non-active cache jobs are cleanly canceled to conserve CPU cycles and network bandwidth.

### 3. Dual-Player Crossfade Engine (Zero-Silence Transitions)
Standard single-player implementations experience audio gaps when loading and preparing the next track. Core Player eliminates gaps using a dual-player architecture:
- **Primary & Secondary ExoPlayer Instances**:
  [`PlaybackService.kt`](file:///Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/player/PlaybackService.kt) manages two distinct `ExoPlayer` instances (`primaryPlayer` and `secondaryPlayer`), swapping their roles (`activePlayer` vs `inactivePlayer`).
- **Overlapping Crossfade**:
  - ~4 seconds before track completion, the next track is initialized and started on the inactive player at volume `0.0`.
  - A coroutine performs linear interpolation over 3500ms, ramping the outgoing track down (`1.0` -> `0.0`) while ramping the incoming track up (`0.0` -> `1.0`).
  - Once complete, the outgoing player stops, and `MediaSession` binds seamlessly to the new active player.

### 4. Seamless Codec Switching (`applyCodec`)
When changing audio format mid-stream (e.g. switching from Raw FLAC to Opus):
- [`PlayerControllerManager.kt`](file:///Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/player/PlayerControllerManager.kt) captures the highest active playback position across `player.currentPosition`, `PlaybackService.instance?.currentPosition`, and the state flow:
  ```kotlin
  val servicePos = PlaybackService.instance?.currentPosition ?: 0L
  val currentPos = maxOf(p.currentPosition, servicePos, _state.value.currentPositionMs).coerceAtLeast(0L)
  ```
- The new queue with modified media URIs is passed directly into `setMediaItems(mediaItems, startIndex, currentPos)`, resuming playback at the exact same millisecond without resetting to `0:00`.

### 5. Resilient Range-Based Seeking
- For transcoded codecs (such as Opus), Navidrome's on-the-fly transcoder is instructed via `estimateContentLength=true` to compute target stream metrics.
- Seeking requests issue HTTP byte-range queries (`Range: bytes=X-Y`), allowing instant jumps to any point in the track without needing to buffer preceding audio packets.

---

## 4. Key Components Summary

| Component | File Path | Core Function |
|---|---|---|
| `PlaybackService` | [`PlaybackService.kt`](file:///Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/player/PlaybackService.kt) | Manages Dual ExoPlayers, MediaSession, notification provider, and crossfade execution. |
| `TrackCacheManager` | [`TrackCacheManager.kt`](file:///Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/player/TrackCacheManager.kt) | Coordinates `SimpleCache`, background `CacheWriter`, cache dataSource routing, and prefetching. |
| `PlayerControllerManager` | [`PlayerControllerManager.kt`](file:///Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/player/PlayerControllerManager.kt) | Binds UI state (`PlayerState`), controls playback commands, and handles seamless codec changes. |
| `PlayerState` | [`PlayerState.kt`](file:///Users/skenls/webplayer-mobile/app/src/main/java/com/coreplayer/app/player/PlayerState.kt) | MediaItem factory, stream URI formatting with `estimateContentLength`, and MIME type assignment. |
