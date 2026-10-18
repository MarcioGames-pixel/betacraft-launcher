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
- Downloads to temporary files, rejects empty/corrupt JARs, verifies SHA-1 when metadata supplies it, and then moves the result into the versions directory.
- Adds a separate Android action to prepare the version's BetaCraft launch-method JAR from `launch-method-link`, checking HTTPS, descriptor fields, SHA-1, JAR integrity and size limits before installing it under `launcher/launch-methods`.
- Includes the installed launch-method artifact in the preflight report so a downloaded game JAR alone is not mistaken for a complete launch setup.
- Uses HTTPS only; known BetaCraft HTTP links are upgraded to HTTPS. Other HTTP links and insecure redirects are rejected.
- Stores files in the app-private directory shown in the UI.
- Adds a runtime preflight button that validates the selected JAR, reads its declared `launch-method`, reports the Android ABI, estimates the Java major version needed by the selected Minecraft version, and checks the runtime's `release` metadata plus expected native bridge components.
- Adds Android ARM64 OpenJDK 8 and 17 runtime installers. Java 8 is resolved from the latest successful public Android `jre8-pojav` CI artifact (not the similarly named iOS archive); Java 17 uses the Android-specific Pojav build. The installer validates Java release metadata and confirms that `libjvm.so` and `libjli.so` are ELF64 AArch64 libraries before activating the runtime. Downloads use HTTPS with redirect-host validation; XZ/TAR extraction uses staging storage, path-traversal checks and size limits before activation.
- Adds a JNI/JLI bootstrap library using Android NDK/CMake and a **Test Java runtime** action that calls OpenJDK's `JLI_Launch` entry point with `-version`. This tests the JVM bootstrap only; it does not claim Minecraft itself launches.
- Builds the pinned PojavLauncherTeam GL4ES compatibility library (`libGL.so`) for Android through CMake/FetchContent. GL4ES translates legacy desktop OpenGL calls to OpenGL ES; it is only one part of the rendering stack and does not provide an EGL game surface, LWJGL JNI binaries, or touch controls by itself.

## Important limitations

This is **not yet a working Minecraft launcher**. The app does not yet claim to launch Minecraft until the renderer and touch-input bridge are ready. Some historical metadata may contain URLs that no longer support HTTPS; those downloads will fail safely and need a compatibility decision.

Remaining work:
1. Validate metadata formats and test launch-method downloads against the live catalogue, including versions without a launch method.
2. Add version/profile and instance management.
3. Test the JNI/JLI bootstrap against both published ARM64 runtimes and device Android versions. The bootstrap is implemented, but its compatibility with each runtime still needs real-device validation.
4. Finish renderer integration around the now-built GL4ES library: create/manage an EGL surface, load compatible LWJGL JNI binaries, connect game lifecycle and touch input, then test each version separately. Java 17 is not a substitute for Java 8 on older versions.
5. Port authentication, changelog/server pages, addons, logging, and lifecycle/background work.
6. Integrate a complete Minecraft launch-argument builder and connect it to the JVM bootstrap after renderer/input support is available. Build the APK in Actions and test on real Android devices.

A successful APK build only confirms the app compiles. It does not prove Minecraft can launch.
