# BetaCraft v1 Android port (early development)

This branch keeps the original desktop launcher unchanged and adds a separate native Android app.

## Build

Open **Actions → Android APK (BetaCraft v1 port)** and choose **Run workflow**, or push changes to this branch. Download the `betacraft-android-debug` artifact from a successful run.

## Implemented

- Native Android launcher screen; no Swing/AWT UI in the Android module.
- Saves the selected version using Android preferences.
- Creates app-private directories for versions, metadata, instances, addons, language files, launch methods, assets, and logs.
- Fetches the BetaCraft version catalogue over HTTPS on a background thread and caches it for offline display.
- Downloads a selected version's `.info` metadata and the JAR URL declared in that metadata.
- Shows download progress when the server reports a content length.
- Downloads to temporary files, rejects empty JARs, verifies SHA-1 when metadata supplies it, and then moves the result into the versions directory.
- Uses HTTPS only; known BetaCraft HTTP links are upgraded to HTTPS. Other HTTP links and insecure redirects are rejected.
- Stores files in the app-private directory shown in the UI.
- Adds a runtime preflight button that validates the selected JAR, reads its declared `launch-method`, reports the Android ABI, estimates the Java major version needed by the selected Minecraft version, and checks the runtime's `release` metadata plus expected native bridge components.
- Adds an ARM64-only OpenJDK 17 runtime installer. It downloads the published Android JRE archive over HTTPS, validates redirect hosts, extracts XZ/TAR into staging storage with path traversal and size limits, checks for `libjvm.so` and Java 17 release metadata, then swaps the runtime into place.

## Important limitations

This is **not yet a working Minecraft launcher**. The Play button still reports the missing functionality rather than pretending to start a game. Some historical metadata may contain URLs that no longer support HTTPS; those downloads will fail safely and need a compatibility decision.

Remaining work:
1. Validate metadata formats and test downloads against the live catalogue.
2. Add version/profile and instance management.
3. Integrate and test a native JVM launch bridge. The installer only places the OpenJDK 17 ARM64 files in app-private storage; it does not yet load the JVM or execute Minecraft.
4. Integrate a compatible rendering/input bridge (LWJGL/OpenGL compatibility) for legacy Minecraft versions and test each version separately. Java 17 is not a substitute for Java 8 on older versions.
5. Port authentication, changelog/server pages, addons, logging, and lifecycle/background work.
6. Build the APK in Actions and fix any CI errors; then test on real Android devices.

A successful APK build only confirms the app compiles. It does not prove Minecraft can launch.
