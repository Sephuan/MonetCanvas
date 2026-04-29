# MonetCanvas

<div align="center">

A **Material You** wallpaper manager for static & live wallpapers

[![GitHub release](https://img.shields.io/github/v/release/Sephuan/MonetCanvas)](https://github.com/Sephuan/MonetCanvas/releases)
[![License](https://img.shields.io/github/license/Sephuan/MonetCanvas)](LICENSE)

<p align="center">
  <a href="README.md">🇨🇳 中文</a> ·
  <a href="README-en.md">🇬🇧 English</a>
</p>

</div>

---

## Introduction

MonetCanvas is an Android wallpaper management app built on **Material You** (Monet) design principles. It supports importing, previewing, and setting both static wallpapers and **video live wallpapers**, and can intelligently extract colors from wallpapers to drive the system's Monet theme engine — harmonizing your entire device interface with your wallpaper.

## Features at a Glance

| | Feature | Description |
|---|---|---|
| 🖼️ | **Wallpaper Management** | Import, preview, set static/live wallpapers with grid/list view switching |
| 🎬 | **Live Wallpaper** | Set video files as live wallpapers, smooth background playback |
| 🎨 | **Monet Color Extraction** | Smart extraction of primary/secondary/tertiary colors with customizable rules |
| 🎭 | **Image Adjustment** | Fill mode, zoom, canvas background, flip, brightness/contrast/saturation |
| 💾 | **Storage & Backup** | Secure private storage, backup/sync with migration support |
| 🌙 | **Dark Mode** | Follow system / light / dark — three modes |
| 🎨 | **App Theme** | Follow system / extraction rules / manual color pick — three sources |
| 🌐 | **Multi-language** | 简体中文 / English |
| 🔤 | **Font Scale** | Adjustable text size throughout the app |

## Features

### 🖼️ Wallpaper Management
- **Import** images (jpg/png/webp) and videos (mp4/gif/webm) as wallpapers
- Grid / list layout with **4 density levels** (small/medium/large/list)
- **Favorites** — long-press to favorite with haptic feedback
- Quick filter by type: **All / Static / Live** with counts
- Set as **home screen**, **lock screen**, or **both** with one tap
- **Fullscreen preview** mode for immersive viewing
- **Delete** wallpaper with confirmation dialog
- Thumbnails loaded via **Coil** with **crossfade animation**

### 🎬 Live Wallpaper
- Set **video files** as live wallpapers via Android's Live Wallpaper mechanism
- Background playback powered by **ExoPlayer (Media3)**, smooth and looped
- `LiveWallpaperService` maintains its own player, continues running after app exits
- **Permission detection & guidance** — shows dialog to navigate to system settings when service permission is missing
- **Fullscreen preview** to watch live effects in real-time
- Complete workflow: preview → tap set → system picker → confirmation → automatic apply
- Live wallpaper cards marked with **PlayCircle icon** for clear identification

### 🎨 Monet Color Extraction (Core Feature)

MonetCanvas's color engine is built on **AndroidX Palette**, extracting 3 colors (primary / secondary / tertiary) to drive the system's Material You theme.

**Static Wallpaper Extraction:**
- **Adaptive downsampling** (max 800px) preserves color characteristics while boosting performance
- Secondary downsampling to **720px edge limit** before Palette analysis
- **6 tone preferences**:
  - `Auto` — uses Palette's dominant swatch
  - `Vibrant` — prefers vibrant color variants
  - `Muted` — prefers muted color variants
  - `Dominant` — uses the highest population color
  - `Dark Preferred` — prefers dark color variants
  - `Light Preferred` — prefers light color variants
- **Color region** selection: full frame / center / top half / bottom half
- **Manual override** color support

**Live Wallpaper Extraction:**
- Uses `MediaMetadataRetriever` for video frame extraction
- **4 frame position strategies**:
  - `First Frame` — near video start (~250ms)
  - `Middle Frame` — video midpoint
  - `Last Frame` — near video end
  - `Random Frame` — random position between 10%~90%
- **Multi-candidate fallback** — automatically tries alternative timestamps when the preferred frame fails to decode
- Prioritizes sync frames (keyframes) first, falls back to exact frames

**Color Extraction UX:**
- Real-time **loading animation** during extraction
- Extracted colors displayed as **color circles** with **fade-in + expand animation**
- Rule configuration via **ModalBottomSheet** with segmented buttons and filter chips
- Rules auto-save and trigger **re-analysis**
- Live wallpaper rules include additional frame position options + extraction hints

### 🎭 Image Adjustment

Rich image editing features, all adjustments apply in real-time:

- **Fill mode** (3 options via FilterChip):
  - `Cover` — scale to fill, draggable + zoomable
  - `Fit` — scale to fit with letterboxing, draggable + zoomable
  - `Stretch` — non-uniform stretch to fill screen
- **Zoom**: 0.2x ~ 3.0x slider with percentage display, auto-hidden in stretch mode
- **Canvas background color**: **45+ preset colors** (solids, dark tones, blues, greens, reds/oranges/pinks, purples, warm/browns, yellows/cyans) with selection highlight and ✓ icon
- **Mirror**: horizontal flip / vertical flip (Switch toggles)
- **Color adjustment** (-100 ~ +100 sliders, three independent channels):
  - Brightness
  - Contrast
  - Saturation
- **One-tap reset** (visible only when adjustments are present, with expand/collapse animation)

### 💾 Storage & Backup
- Wallpaper files securely stored in **app-private directory**, immune to gallery deletions or external file changes
- Configurable **backup directory** for file sharing and sync
- **One-tap backup** of all wallpapers
- **Sync**: auto-import new wallpapers from backup + clean up invalid database records
- **Migration support** when switching backup directories

### 🌙 Dark Mode
- Three modes: **Follow system** / **Always light** / **Always dark**
- Seamless integration with Material You colors
- System follow based on `isSystemInDarkTheme()`

### 🎨 App Theme
- Three theme color sources:
  - **Follow system** — use system Monet colors
  - **Extraction rules** — extract from last wallpaper's color rules
  - **Manual pick** — choose from 10 preset Material You style colors
- Status bar / navigation bar color adaptation via **Accompanist SystemUIController**

### 🌐 Multi-language
- **简体中文** and **English**
- Language switch takes effect immediately (with restart prompt)
- Auto-matches system language

### 🔤 Font Scale
- Adjustable text size throughout the entire app
- Multiple levels to suit different visual needs

## System Compatibility

| Item | Details |
|------|---------|
| **Minimum** | Android 8.0 (API 26) — covers the vast majority of devices |
| **Target SDK** | Android 15 (API 35) |
| **Compile SDK** | Android 15 (API 35) |
| **Architecture** | MVVM + Hilt DI |
| **Database** | Room (with KSP) |
| **Preferences** | DataStore Preferences |
| **Image Loading** | Coil (with video frame support) |
| **Video Playback** | ExoPlayer (Media3) |
| **Color Engine** | AndroidX Palette |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Navigation** | Navigation Compose (with animated transitions) |
| **Background Tasks** | WorkManager (Hilt integration) |
| **Status Bar** | Accompanist SystemUIController |

## Tech Stack

- **MVVM Architecture**: ViewModel + Repository pattern, UI driven by StateFlow / Flow
- **Dependency Injection**: Hilt (with Hilt Navigation Compose, Hilt WorkManager)
- **Async Processing**: Kotlin Coroutines (Dispatchers.IO for color extraction, file operations)
- **Persistence**: Room database + DataStore Preferences
- **Background Tasks**: WorkManager (Hilt integration)
- **Image Loading**: Coil (Compose native + Video extension)
- **Video Playback**: ExoPlayer (Media3)
- **Color Engine**: Custom ColorEngine built on AndroidX Palette, supporting static and live wallpapers
- **System Wallpaper**: WallpaperManager API + WallpaperService (Live Wallpaper)
- **UI Framework**: Jetpack Compose + Material 3
- **Navigation Animation**: Navigation Compose AnimatedContentTransitionScope with slide + fade + scale combinations

## UX Design Highlights

- **🎬 Navigation Animation**: Page transitions use **slide + fade** combinations; preview → fullscreen uses **scale expand** effect
- **📱 Predictive Back**: Uses system-native Navigation Compose back gesture, **does not intercept** PredictiveBackHandler — letting Android's native predictive back animation (API 33+) work naturally
- **📐 Edge-to-Edge**: `enableEdgeToEdge()` for immersive full-screen experience
- **👆 Draggable Panel**: Preview bottom panel supports **gesture drag** to expand/collapse
- **🎯 Entry Animation**: Wallpaper grid features **staggered fade-in + spring slide-up** animation (AnimatedVisibility)
- **❤️ Haptic Feedback**: Long-press favorites triggers **vibration haptics** (HapticFeedback)
- **🔄 Live Preview**: All image adjustments take effect **instantly**, WYSIWYG
- **✨ Color Animation**: Extracted color circles **fade-in + expand** (AnimatedVisibility)
- **📋 Return Banner**: Top banner shows live wallpaper set result (success/failure)
- **🎨 Translucent Panel**: Preview bottom panel uses translucent background to reduce occlusion
- **📏 Adaptive UI**: Wallpaper grid supports 4 density levels for different screen sizes

## Download

[![Release](https://img.shields.io/github/v/release/Sephuan/MonetCanvas)](https://github.com/Sephuan/MonetCanvas/releases/latest)

Get the latest APK from the [Releases](https://github.com/Sephuan/MonetCanvas/releases) page.

## Build

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17+
- Android SDK 35

### Local Build

```shell
# Clone the repository
git clone https://github.com/Sephuan/MonetCanvas.git
cd MonetCanvas

# Debug build
./gradlew assembleDebug

# Release build (requires signing configuration)
./gradlew assembleRelease
```

Release builds need a keystore configured — see `keystore.properties` template.

## Acknowledgements

Thanks to [hhad8](https://github.com/hhad8) for suggestions and assistance.

## License

This project is open-sourced under the MIT License. See [LICENSE](LICENSE) for details.

## Known Issues

### 🐞 Live Wallpaper Color Extraction May Be Incorrect

**Symptom:** After setting a live wallpaper, the system Monet theme colors may not match the wallpaper — incorrect color extraction.

**Workaround:** Re-enter the wallpaper's preview page and set it again. This triggers a fresh color extraction and the colors will be correct.

**Root Cause:** Global caches in the live wallpaper service may cause cross-wallpaper color contamination, and there's a race condition from dual writes of the seed color. Currently being fixed.
