# BetaCraft v1 Android port (early scaffold)

This branch adds a separate native Android application module and a GitHub Actions workflow that produces a debug APK. The original desktop v1 project and its Gradle build files are left unchanged.

## Build

Open **Actions → Android APK (BetaCraft v1 port)** and run it with **Run workflow**, or push changes to this branch. Download the `betacraft-android-debug` artifact from the successful run.

## Important status

This is an initial Android UI/build scaffold, **not yet a working Minecraft launcher**. The existing v1 client is a Swing/AWT desktop application and uses desktop window classes, external Java process launching, filesystem assumptions, and desktop-oriented integrations. Those cannot be made Android-compatible simply by packaging the existing JAR as an APK.

The current app provides a native Android screen and a version selector to establish the APK build pipeline. The Play action explicitly reports that the game runtime is not integrated yet.

## Remaining port work

1. Replace desktop Swing/AWT UI with native Android screens.
2. Adapt launcher storage and instance/version management to Android app storage and Android's storage model.
3. Replace desktop `ProcessBuilder`/external-Java launch logic with an Android-compatible Java runtime integration.
4. Integrate a compatible rendering/input layer for legacy Minecraft versions and test each version separately.
5. Rework authentication, downloads, addons, logging, and lifecycle/background work for Android.
6. Add automated tests and verify the APK on real devices.

Do not treat the initial debug APK as evidence that Minecraft can already launch. It is only the first buildable step toward a proper port.
