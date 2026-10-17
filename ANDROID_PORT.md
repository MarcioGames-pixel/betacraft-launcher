# BetaCraft v1 Android port (early development)

This branch keeps the original desktop launcher unchanged and adds a separate native Android app.

## Build

Open **Actions → Android APK (BetaCraft v1 port)** and choose **Run workflow**, or push changes to this branch. Download the `betacraft-android-debug` artifact from a successful run.

## Implemented in this stage

- Native Android launcher screen; no Swing/AWT UI is used in the Android module.
- Saves the selected version using Android preferences.
- Creates app-private directories for versions, version metadata, instances, addons, language files, launch methods, assets, and logs.
- Tries to fetch the BetaCraft version catalogue over HTTPS on a background thread.
- Caches the catalogue locally so the list can still be shown when offline.
- Keeps a small fallback list if neither the network nor a cache is available.
- Shows the app-private data directory on screen.

## Not implemented yet

This is **not yet a working Minecraft launcher**. The current Play button reports the missing functionality rather than pretending to start a game.

Remaining work:
1. Download and validate version metadata and game JARs; verify checksums where supplied.
2. Implement version/profile and instance management.
3. Integrate an Android-compatible Java runtime and launch pipeline instead of desktop `ProcessBuilder` assumptions.
4. Integrate a compatible rendering/input layer for legacy Minecraft versions and test each version separately.
5. Port authentication, changelog/server pages, addons, logging, and lifecycle/background work.
6. Build the APK in Actions and fix any CI errors; then test on real Android devices.

A successful APK build only confirms the app compiles. It does not prove Minecraft can launch.
