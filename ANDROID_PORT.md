# BetaCraft v1 Android port (early development)

This branch keeps the original desktop launcher unchanged and adds a separate native Android app.

## Build

Open **Actions → Android APK (BetaCraft v1 port)** and choose **Run workflow**, or push changes to this branch. Download the `betacraft-android-debug` artifact from a successful run.

## Implemented

- Native Android launcher screen; no Swing/AWT UI in the Android module.
- Original vector pixel-cube launcher icon in the Android manifest.
- Local named instance manager with create/select/delete actions, independent per-instance game directories (`saves`, `mods`, `config`, `resourcepacks`, `screenshots`, and logs), persisted version assignments, and migration from earlier name-only profiles. Shared version JARs remain cached globally.
- Persistent preferred Java memory and GL4ES preference settings; these are stored locally but are not yet passed to a Minecraft process.
- Saves the selected version using Android preferences and synchronizes it with the active instance's assigned version.
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
- Adds a JNI/JLI bootstrap library using Android NDK/CMake, a **Test Java runtime** action, and a reusable entry point for launching a Java main class with an argument array. Adds an experimental selected-version launch path that builds a classpath from the version JAR and cached library JARs, uses the active instance game directory, creates an offline player UUID, and prepares the modern `net.minecraft.client.main.Main` arguments. The UI rejects applet-era launch methods rather than passing them incorrect arguments.
- Builds the pinned PojavLauncherTeam GL4ES compatibility library (`libGL.so`) for Android through CMake/FetchContent. GL4ES translates legacy desktop OpenGL calls to OpenGL ES; it is only one part of the rendering stack and does not provide an EGL game surface, LWJGL JNI binaries, or touch controls by itself.

## Important limitations

This is **not yet a working Minecraft launcher**. The experimental launch path is only an argument/JVM integration step for main-based versions; it blocks when the expected LWJGL native libraries are absent. It does not yet provide a compatible LWJGL 2 Android backend or connect touch input to Minecraft. Some historical metadata may contain URLs that no longer support HTTPS; those downloads will fail safely and need a compatibility decision.

Remaining work:
1. Validate metadata formats and test launch-method downloads against the live catalogue, including versions without a launch method.
2. Connect per-instance mods and assets to the launch process; the experimental launch plan now uses the active instance's game directory, but mod resolution and asset-index resolution remain incomplete.
3. Test the JNI/JLI bootstrap against both published ARM64 runtimes and device Android versions. The bootstrap is implemented, but its compatibility with each runtime still needs real-device validation.
4. Finish renderer integration around GL4ES: provide a compatible Android LWJGL 2 JNI/backend for old BetaCraft versions, bind its window/context lifecycle to the Android EGL surface, and connect keyboard, mouse and touch events. The existing EGL screen is still a smoke test, not the Minecraft render loop. The surface now captures Android touch, keyboard and mouse events into a bounded JNI queue and forwards size changes to the EGL test renderer. The queue is not yet drained by an LWJGL backend, and the EGL context is not yet handed to Minecraft. Test each supported version separately.
5. Port Microsoft/Mojang authentication, changelog/server pages, addon/modpack management, logging, and lifecycle/background work. The current settings/profile screens are only an initial UI foundation, not full parity with desktop BetaCraft.
6. Complete argument/authentication and asset resolution for the selected version, then run the JVM path end-to-end after the Android LWJGL/input backend is present. Build the APK in Actions and test on real Android devices.

A successful APK build only confirms the app compiles. It does not prove Minecraft can launch.
