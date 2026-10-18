# BetaCraft Android v1 port

This is a separate Android application module. It does not replace or alter the original desktop Gradle build.

## Build

Open the `android-port` directory in Android Studio, or run:

```sh
gradle --version
gradle -p android-port :app:assembleDebug
```

The GitHub Actions workflow builds a debug APK on pushes to `android-v1-port`.

## Implemented

- Native Android UI styled around BetaCraft's dark/brown/gold desktop identity.
- Official Minecraft version manifest download with a local cache.
- Version picker for numeric official releases up to 1.5.2.
- Private app directories for versions, metadata, instances, addons, resources, logs and assets.
- Storage/diagnostics screen.

## Not implemented yet

The Play action deliberately does not claim to launch Minecraft. The Android Java runtime and game-process backend are not connected yet. The official Mojang manifest also does not contain every pre-Classic, Classic, Indev, Infdev, Alpha or Beta build supported by BetaCraft. The next major step is a tested Android runtime adapter (for example, an explicitly integrated compatible runtime backend), followed by version-specific library/native extraction, launch arguments, input/rendering and audio validation.

The Android port is experimental and is not yet feature-equivalent to the desktop launcher.
