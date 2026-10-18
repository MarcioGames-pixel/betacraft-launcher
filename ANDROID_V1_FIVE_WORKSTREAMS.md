# BetaCraft v1 Android port — five workstreams

This branch targets the Java BetaCraft v1 launcher and its historical clients only. It does not port BetaCraft v2.

## 1. Applet-era clients

- The detector recognizes official v1 launch-method signatures and repackaged `MinecraftApplet` classes.
- `AndroidAppletLaunchAdapter` prepares the applet class, code base, document base and standard parameters.
- AWT/Applet lifecycle and the actual applet renderer are **not implemented**. Classic, Indev and related applets must remain blocked rather than being launched as a normal `main` class.
- `AndroidV1Compatibility` and preflight now distinguish recognition from Android playability.

## 2. Libraries and assets

- Current download action fetches the selected version metadata and version JAR and validates the JAR/checksum when available.
- The launch plan assembles local JARs and the Android LWJGL compatibility JAR.
- Complete metadata-driven dependency resolution, per-artifact checksums/downloads, legacy sound/skin assets and asset-index resolution remain unfinished. A successful JAR download is not a complete installation.

## 3. Input and renderer

- The native EGL smoke-test surface queues hardware key, touch and mouse events.
- `AndroidLwjglInputAdapter.pumpPendingEvents` adds a bounded queue-drain API for use on a future game/render thread.
- A reusable transparent touch-control view supplies WASD, jump, hit, inventory and Escape controls. It is currently wired into the graphics diagnostic screen so its events can be inspected.
- The diagnostic queue is not yet consumed by a live Minecraft/LWJGL render loop. Audio and real-game rendering are not complete.

## 4. Launcher UI and icon

- The launcher UI is English and has version selection, download, runtime, preflight, instance, server, file and settings screens.
- The app manifest already points to `@drawable/betacraft_icon`.
- The virtual controls and truthful capability messages improve the Android experience, but the UI is not yet a pixel-identical recreation of the official desktop launcher. News, Microsoft sign-in, skins, mod repository, addon manager and live server data are not complete.

## 5. Build and device validation

- Unit tests cover launch-method detection, capability classification, launch arguments and Android-to-LWJGL input translation.
- GitHub Actions builds the Android app and runs unit tests; check the Actions page for the latest commit before treating it as verified.
- A CI APK build does not prove a Minecraft client is playable. Real-device testing is still required for Java 8 ARM64, RubyDung, a main-class release, GLES/GL4ES, input, audio, saves and memory pressure.

## Current honest status

The port has useful groundwork, not full v1 parity. RubyDung and main-class clients have launch-plan support, but they still depend on complete dependencies/assets and a functioning game renderer/input loop. Applet-era clients remain blocked until a real Android applet host exists. Do not label any legacy applet as playable merely because its class signature was detected.

## CI

- [GitHub Actions](https://github.com/MarcioGames-pixel/betacraft-launcher/actions)
- [Android compatibility registry](https://github.com/MarcioGames-pixel/betacraft-launcher/blob/android-v1-port/android-app/app/src/main/java/com/marciogames/betacraft/android/AndroidV1Compatibility.java)
- [Touch controls](https://github.com/MarcioGames-pixel/betacraft-launcher/blob/android-v1-port/android-app/src/main/java/com/marciogames/betacraft/android/AndroidTouchControls.java)
