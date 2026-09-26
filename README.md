# Music Player (Kotlin + Jetpack Compose)

A local music player that scans the device for audio files and plays them in the background.

## Features
- Scans all playable audio on the device via `MediaStore`
- Play / pause / next / previous / seek, with auto-advance to the next track
- Background playback through a foreground service, with working play/pause/next/previous buttons on the notification
- Respects audio focus (pauses for calls, ducks for other sounds)
- Handles the Android 13+ (`READ_MEDIA_AUDIO`) vs. older (`READ_EXTERNAL_STORAGE`) permission split
- Built on Media3 ExoPlayer, so it plays whatever formats the device itself supports (MP3, AAC, FLAC, OGG, WAV, etc.)
- Material You dynamic color on Android 12+, with a plain Material3 fallback below that

## What this can't do
This is source code, not a compiled app — turning it into an installable APK needs Android Studio and the Android SDK on a computer, which isn't available from this chat.

## Setup
1. In Android Studio: **New Project → Empty Activity (Compose)**, name it `MusicPlayer`, package `com.example.musicplayer`, language Kotlin, minimum SDK 26.
2. Copy every file from this project into the matching path in the new project, overwriting what's there:
   - `settings.gradle.kts`, `build.gradle.kts` (project root)
   - `app/build.gradle.kts`, `app/proguard-rules.pro`
   - `app/src/main/AndroidManifest.xml`
   - `app/src/main/java/com/example/musicplayer/*.kt`
   - `app/src/main/res/values/strings.xml`
3. Click **Sync Now**. This targets AGP 8.5.1, which needs Gradle 8.7+ — if Android Studio offers to upgrade the wrapper, accept it (a matching `gradle/wrapper/gradle-wrapper.properties` is included here too).
4. Run on a device or emulator that has some audio files on it, and grant the permission prompt.

## Building an APK yourself
Once it runs in Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The debug APK lands in `app/build/outputs/apk/debug/`.

## Notes
- `com.example.musicplayer` is a placeholder package/application ID — change it (in `app/build.gradle.kts` and the folder structure) before sharing the APK anywhere.
- No launcher icon is set, so it uses the system default — add your own under `res/mipmap-*` if you want one.
