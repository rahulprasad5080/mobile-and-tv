# Modular Android Video Player

A production-ready video player application built with Kotlin and Jetpack (Compose + Media3/ExoPlayer).

## Project Structure

- **`:core`**: Shared playback logic, `PlayerManager` (ExoPlayer wrapper), and data models.
- **`:mobileApp`**: Phone-optimized UI using Jetpack Compose with gesture controls.
- **`:tvApp`**: Android TV UI using Compose for TV with D-pad navigation support.

## Key Features

- **Media3 ExoPlayer**: High-performance playback with hardware acceleration.
- **Multi-Module Architecture**: Clean separation of UI and business logic.
- **Gesture Controls (Mobile)**:
  - Swipe Horizontal: Seek video.
  - Swipe Vertical (Left): Adjust brightness.
  - Swipe Vertical (Right): Adjust volume.
- **Android TV Support**:
  - D-pad / Remote navigation.
  - Focus-aware components.
  - Native Leanback launcher support.
- **Subtitle & Audio Support**:
  - Dynamic switching of audio tracks and subtitles.
  - Runtime customization of subtitle size and color.
- **Picture-in-Picture (PiP)**:
  - Supported on both Mobile and TV when leaving the app during playback.

## Build Instructions

1.  **Clone the repository**.
2.  **Open in Android Studio** (Hedgehog or newer recommended).
3.  **Sync Gradle**.
4.  **Run**:
    - Select `mobileApp` to run on a phone/emulator.
    - Select `tvApp` to run on an Android TV emulator/device.

## Keystore Setup

To generate a signed APK:
1. Go to `Build > Generate Signed Bundle / APK...`.
2. Select `APK`.
3. Create a new key store path or use an existing one.
4. Fill in the credentials.
5. Select the build variant (`release`) and click `Finish`.

## Extending Features

- **Adding a new format**: ExoPlayer supports MP4, MKV, AVI, and more out of the box. For custom formats, check `MediaItem.Builder().setMimeType()`.
- **Custom UI**: The UI is built entirely in Compose. Modify `VideoPlayerScreen.kt` (Mobile) or `TvPlayerScreen.kt` (TV) to change the overlay design.
- **Subtitles**: Add external subtitles by passing a list of `SubtitleTrack` to `VideoMediaItem`.

## Performance Optimization

- The project uses `StateFlow` to ensure UI updates only when necessary.
- `PlayerManager` handles the player lifecycle to prevent memory leaks.
- Hardware acceleration is enabled by default via Media3.
