# BetaCraft Launcher for Android (work in progress)

This is a separate Android application project. The original v1 desktop build and its files are intentionally preserved.

## Build the APK

From this directory, using JDK 17 and Android SDK platform/build-tools 35:

```sh
gradle assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions also builds this Android project independently of the legacy desktop build.

## Current state

- Android manifest and installable Activity scaffold.
- Mobile UI with Launcher, Versions, Servers, Settings and Console sections.
- Persists the selected version locally.
- The original desktop Java source tree remains untouched.

## Work still required before this is a playable port

The v1 desktop launcher depends on Swing/AWT, desktop image APIs, and process/JVM launching assumptions that Android does not provide directly. The UI in this first milestone is a mobile adaptation, not yet a drop-in rendering of the original Swing windows.

- Port the original launcher views and their actions while matching the desktop visual style as closely as practical.
- Replace Swing/AWT dependencies and file/path assumptions with Android equivalents.
- Connect the original version metadata, authentication, server browser, downloads, instances, mods and configuration.
- Integrate an ARM64-compatible Java runtime and Minecraft renderer/input layer.
- Test game versions and fix compatibility issues before describing the APK as playable.

Do not remove or replace the original desktop build while the Android port is in progress.
