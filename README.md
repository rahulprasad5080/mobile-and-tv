# Android Video Player

A Kotlin/Jetpack Compose video player that ships as one APK for both phones/tablets and Android TV.

## Project Structure

- `:app`: The single Android application module. It contains mobile UI under `com.mplayer.videoplayer.mobile` and TV UI under `com.mplayer.videoplayer.tv`.
- `:core`: Shared playback, repository, and model logic used by both mobile and TV experiences.

## Platform Support

- Mobile devices launch the existing mobile Compose UI.
- Android TV devices launch the adapted TV Compose UI from the same APK.
- The manifest includes both standard launcher and Leanback launcher support.
- TV screens include D-pad focus handling, larger controls, and remote-friendly playback actions.

## Build

Open the project in Android Studio, sync Gradle, then run the `app` module.

Command line:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated from the single `:app` module.
