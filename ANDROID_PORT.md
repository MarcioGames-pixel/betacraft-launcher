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
- Builds the pinned PojavLauncherTeam GL4ES compatibility library (`libGL.so`) for Android through CMake/FetchContent.
- Adds a pinned Android port of LWJGL 2 using the MojoLauncher `lwjgl2-glfw` fork. The APK workflow builds its `lwjgl.jar` and ARM64 `liblwjgl64.so`, and packages the fork's Android `libglfw.so` and `libjawt.so`. The game JVM classpath places the Android LWJGL JAR before the selected version's desktop libraries so its GLFW-backed `Display` implementation can override the desktop implementation. The upstream LWJGL license is included in the APK assets.

## BetaCraft legacy-launch research

The upstream BetaCraft v1 source confirms that its legacy support is more than finding a class name:

- Its wrapper creates an AWT/Swing game window, loads the game JARs with a dedicated class loader, constructs the version-specific client, and provides an AppletStub with parameters such as username, session ID, paid/offline state, server/port, and UUID.
- It uses distinct launch wrappers for RubyDung/Pre-Classic, Classic 0.0.12a, Classic 0.0.15a, and general Classic/Indev/Infdev clients. The generic wrapper also handles addons and version-specific startup/shutdown behavior.
- Pre-Classic has an extra bundled PreClassic.jar adapter and default map dimensions; it is not equivalent to passing a modern Main class arguments.
- BetaCraft's public v1 project describes support from Pre-Classic through 1.5.2, including early Infdev/Indev, skins/sounds where supported, fixes for historical versions, and custom server compatibility. Those are desktop launcher features and do not automatically carry over to Android.

This Android branch now classifies a repackaged MinecraftApplet as a generic classic launch method if no more-specific known signature matches. That improves recognition only. It intentionally continues to refuse launching these JARs until an Android-native game host and the rendering/input bridge replace the desktop AWT/Swing window. Do not mark applet versions as playable based on detection tests alone.
## Important limitations

This remains an experimental Android port and is not yet verified as a working Minecraft launcher. The build now includes a real LWJGL 2 GLFW backend instead of only checking for absent libraries, but the fork's window creation and event callbacks must still be verified on a physical Android device. Legacy applet launch methods (including Classic, Indev and Infdev), custom launch-method adapters, complete game argument/library/assets resolution, and in-game touch mapping are not complete. The version catalogue can list all BetaCraft versions, but that does not mean every historical version can launch yet. Some historical metadata may contain URLs that no longer support HTTPS; those downloads will fail safely and need a compatibility decision.

Remaining work:
1. Validate metadata formats and test launch-method downloads against the live catalogue, including versions without a launch method.
2. Connect per-instance mods and assets to the launch process; the experimental launch plan now uses the active instance's game directory, but mod resolution and asset-index resolution remain incomplete.
3. Test the JNI/JLI bootstrap against both published ARM64 runtimes and device Android versions. The bootstrap is implemented, but its compatibility with each runtime still needs real-device validation.
4. Validate the pinned LWJGL 2 GLFW backend on real Android hardware. The Android native GLFW window must successfully attach to the launcher's game surface and present Minecraft frames through GL4ES. The current EGL screen remains a separate smoke test. Next, connect touch, keyboard and mouse events to the backend's GLFW event buffers and test each supported version separately.
5. Port Microsoft/Mojang authentication, changelog/server pages, addon/modpack management, logging, and lifecycle/background work. The current settings/profile screens are only an initial UI foundation, not full parity with desktop BetaCraft.
6. Complete argument/authentication and asset resolution for the selected version, then run the JVM path end-to-end after the Android LWJGL/input backend is present. Build the APK in Actions and test on real Android devices.

A successful APK build only confirms the app compiles. It does not prove Minecraft can launch.


### Legacy applet adapter groundwork

`AndroidAppletLaunchAdapter` now scans the selected JAR for a `MinecraftApplet.class` in any package and builds a validated launch descriptor with the discovered class name, JAR URL, game directory, dimensions, and baseline offline applet parameters (`username`, `sessionid`, `haspaid`, `standalone`, `demo`, `fullscreen`, `version`). Its unit test uses a nonstandard package (`com.example.old.MinecraftApplet`) to prevent regression to hard-coded package paths.

This is intentionally a planning adapter, not a claim of playable applet support. It does not instantiate the applet or call `init()`/`start()`: the separate JLI JVM still needs a JVM-side bootstrap JAR, and AWT applet components need a compatible Android rendering/container strategy. The launcher must continue refusing to launch applet-only versions until those pieces are wired together.

### LWJGL input adapter groundwork

`AndroidLwjglInputAdapter` now contains tested Android-key-to-GLFW mappings, normalized touch-to-cursor/left-click conversion, and a reflective adapter for the Pojav-style `org.lwjgl.glfw.CallbackBridge` API. This is an adapter component only: the compatibility classes are not bundled into this APK and the separate JVM launched through JLI cannot see the Android app's class loader. Therefore this does **not** yet connect controls to Minecraft. The next required work is to bundle/build the matching LWJGL compatibility layer and run its input pump inside the launched game JVM, then replace the EGL smoke test with that backend's real render surface.
