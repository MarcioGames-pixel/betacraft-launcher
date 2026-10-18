# BetaCraft Launcher for Android (work in progress)

The Android app is being added alongside the original v1 desktop launcher. The existing Windows/desktop source and build are preserved.

## Build the APK

Use JDK 17 and Android SDK platform/build-tools 35:

```sh
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds the Android project independently; open the Android APK workflow and download the `betacraft-android-debug-apk` artifact after a successful run.

## Implemented in the current Android preview

- Native Android UI with Launcher, Versions, Servers, Settings and Console tabs.
- Saves the selected version and active profile.
- Create and remove named game profiles; each profile remembers its selected version.
- Save server names and addresses locally, copy an address to the clipboard, and remove saved servers.
- Console diagnostics for Android API, device ABI, profile count and saved server count.
- The original desktop Java source tree remains unchanged.

## Not yet a playable port

This is not yet equivalent to the full desktop launcher. The original v1 uses Swing/AWT, desktop image APIs, and a desktop process/JVM launch path that Android cannot run unchanged.

Still required:

- Replace remaining desktop-only dependencies and match the original UI and behavior more closely.
- Connect the original live version metadata/catalog, downloads, authentication, real server ping/join, mods/add-ons and configuration.
- Integrate an ARM64-compatible Java runtime, native libraries, LWJGL compatibility, GL4ES/OpenGL rendering, audio and touch/keyboard input.
- Implement and test the actual game launch bridge and legacy Minecraft versions on devices.

Version selection and server/profile storage in this preview do not download game files or start Minecraft. Do not describe this APK as playable until the runtime and launch flow are implemented and tested.
