# BetaCraft official feature audit — Android port

Audit date: 2026-10-18  
Target branch: android-v1-port

## Sources checked

- Official v1 feature list and platform scope: https://github.com/betacraftuk/betacraft-launcher/blob/v1/README.md
- Official download page and v1/v2 version scope: https://betacraft.uk/downloads
- Official v1 source tree: https://github.com/betacraftuk/betacraft-launcher/tree/v1/src/main/java
- Official release notes: https://github.com/betacraftuk/betacraft-launcher/releases
- Official v2 progress update: https://betacraft.uk/blog/post/betacraft-v2-update-2025-09-04

This is a feature-parity audit, not a claim that the Android port is complete. A visible screen, stored preference, downloaded native library, or successful APK build does not count as a working in-game feature by itself.

## Product scope: v1 versus v2

| Official product | Published scope | Implication |
|---|---|---|
| BetaCraft v1 (Java) | Pre-Classic through Minecraft 1.5.2; legacy fixes, accounts, mods, server list, addons, Discord RPC, JVM/Java/instance/resolution settings, console, BetaEvolutions and translations | The Java source in this repository is the v1 codebase. Android needs replacements for desktop AWT/Swing and platform-specific behavior. |
| BetaCraft v2 (C++ alpha) | Official download page says up to Minecraft 1.12.2; separate implementation, still alpha | Porting v1 alone does not reproduce v2. Versions 1.6–1.12.2 are a separate compatibility milestone, not proven Android support. |

## Feature-by-feature status

Status: **Partial** means groundwork/UI exists but end-to-end behavior is missing. **Missing** means no functioning implementation was identified in the Android module. **Platform replacement** means the desktop feature needs an Android-native equivalent.

| Official capability | Android port status | Evidence / remaining work |
|---|---|---|
| Browse the legacy version catalogue | Partial | Fetches and caches the BetaCraft version list. A listed version is not necessarily launchable. |
| Download version metadata and game JAR | Partial | Download, staging and integrity checks exist; test across historical metadata, failed requests, redirects and old URLs. |
| Version-specific launch methods | Partial | Installer and detector exist; desktop launch adapters still depend on AWT/Swing and are not Android-ready. |
| Launch modern Java main-class versions | Partial | JLI/JNI launch plan exists, but complete dependencies/assets, render loop and input integration remain incomplete. |
| Pre-Classic / RubyDung / Cave Game | Partial | Detection and launch-plan groundwork exist; no verified physical-device gameplay. |
| Classic 0.0.12a and 0.0.15a | Missing | Official wrappers use AWT Canvas/window and version-specific reflection; Android-native equivalents are not complete. |
| Classic / Indev / Infdev applets | Partial detection only | Package-independent applet detection and parameter planning exist. Android applet lifecycle host and rendering/container integration are missing; launch is deliberately blocked. |
| All currently available v1 versions through 1.5.2 | Not achieved | Catalogue visibility does not equal compatibility. Every launch-method family needs an Android adapter and device tests. |
| BetaCraft proxy for skins and sounds | Missing | No end-to-end proxy integration identified in Android code. |
| Online-mode multiplayer for pre-Beta 1.8 versions | Missing | Old-client authentication/session integration is not wired into the Android launch path. |
| Custom servers / c0.0.15a compatibility | Partial UI only | Server bookmarks are saved locally; the screen says joining and live status are unavailable until the game client is integrated. |
| Live server list, descriptions and player counts | Missing | No live service integration identified in the Android module. |
| Auto-download a server-required mod | Missing | No working repository resolver/install flow. |
| Mod repository | Missing | No functioning catalogue, download/install, compatibility check or safe modded-instance workflow. |
| Addons system | Missing | No Android addon browser/installer or compatible injection framework. |
| OfflineDATSave for Classic worlds | Missing | Needs Classic client hooks and Android save export/import. |
| Fullscreen addon | Platform replacement | Android uses activities/surfaces/window insets, not desktop JFrame fullscreen. Implement a suitable Android equivalent if needed. |
| Demo addon | Missing | No version-gated addon integration. |
| UnlicensedCopy addon | Missing | No version-gated addon integration. |
| QuitGame addon | Missing | No version-gated addon integration. |
| GameModeSwitch addon | Missing | No version-gated addon integration. |
| ClassicNotPaid addon | Missing | No version-gated addon integration. |
| Microsoft sign-in and account/profile handling | Missing | No functioning Microsoft/Xbox/Minecraft login flow is wired into Android UI or launch plan. Offline username is not account authentication. |
| Token refresh, licence checks and logout | Missing | Requires account flow and secure token storage; secrets must never be logged. |
| Discord Rich Presence | Missing / platform replacement | Desktop RPC is not integrated; needs supported Android equivalent or explicit omission. |
| Configurable JVM arguments | Partial | Memory preference is stored but settings explicitly say it is not passed to Minecraft. General JVM argument editing/validation is incomplete. |
| Java path/runtime selection | Partial | ARM64 Java 8/17 installers and preflight exist. Per-instance runtime selection and compatibility matrix are incomplete. |
| Per-instance game directory | Partial | Instance manager and separate folders exist; launch must consistently use them for assets, libraries, saves and mods. |
| Instance icon | Not verified | Full instance-specific icon picker/display parity not confirmed. |
| Starting resolution | Missing as a working game feature | EGL smoke test exists; resolution is not yet reliably passed to a functioning game renderer. |
| Console output with pause/copy/clear | Partial | Project has logging groundwork; full live console parity and reliable game stdout/stderr routing are not confirmed. |
| Launcher/client logs and error handling | Partial | Preflight diagnostics exist; end-to-end logging, exception reporting, secret redaction and failure tests remain. |
| BetaEvolutions | Missing | No Android-compatible integration identified. |
| Multiple languages and translation fallback | Missing | Main Android UI contains hard-coded English; no complete language selection/resource system. |
| News/changelog/update notifications | Missing | No complete news/changelog/update flow identified in Android UI. |
| Download validation and recovery | Partial | HTTPS, staging and validation exist; test retries, cancellation, failures and version updates. |
| Runtime chosen for game version | Partial | Java 8/17 heuristic exists; each version/native-library family needs compatibility testing. |
| Desktop fixes (macOS mouse, AMD clouds, Intel graphics, old Mojang-screen crash) | Platform-specific / missing | Evaluate Android relevance individually; do not blindly copy desktop-only flags. |
| LegacyFix for later versions | Missing | Separate official project; not integrated into this Android launch path. |
| Touch/keyboard/mouse mapping | Partial | Android input capture and bounded JNI queue exist, but events are not yet consumed by a working LWJGL backend. |
| Game audio | Not verified | No complete physical-device game audio test confirmed. |
| LWJGL 2 / GL4ES / native rendering | Partial | Native artifacts are built/packaged and EGL smoke test exists; actual Minecraft surface, frame presentation, callbacks and device compatibility are not validated end-to-end. |
| Custom desktop launch-method JARs | Partial download only | Descriptor/download/install validation exists; desktop adapter classes are not automatically Android-compatible. |
| Saves/screenshots/resource packs file access | Partial | Per-instance folders exist; Android export/import and real game output need testing. |
| Launcher self-update | Missing | No complete update/release-channel workflow identified. Android app updates should use a signed APK/release flow. |
| Activity lifecycle, interruptions and low-memory recovery | Partial | Native activities exist; suspend/resume, audio focus, rotation, low-memory recovery and long-running downloads need device tests. |

## Confirmed Android groundwork

- Native Android launcher UI, instance manager and app-private storage.
- Version catalogue fetch/cache and metadata/JAR downloads with integrity checks.
- Launch-method download/install checks and version-family detection.
- ARM64 Java 8/17 installers, runtime preflight and JNI/JLI bootstrap.
- GL4ES and an Android LWJGL 2 fork in build workflows.
- Experimental main-class launch plan and RubyDung/Cave Game classification.
- Package-independent applet detection and applet parameter-plan groundwork.
- EGL smoke test and Android input-capture queue.

These are building blocks, not proof of gameplay. Current preflight explicitly reports missing complete dependency/asset resolution and a compatible LWJGL Android render loop.

## Recommended implementation order

1. Make one supported version genuinely playable on a physical ARM64 Android device: runtime, classpath, libraries/assets, LWJGL, GL4ES, real Android surface, input/audio and clean shutdown.
2. Complete main-class launching and make memory, game directory, assets and native paths consistent.
3. Implement and test each historical launch family separately: RubyDung, Classic 0.0.12a, Classic 0.0.15a, general Classic, Indev, Infdev and other applet variants.
4. Add server joining and legacy authentication/proxy behavior after client networking/session handling is testable.
5. Add Microsoft account flow and secure token lifecycle.
6. Add mod repository, server status/required mods, addon framework and individual addon compatibility.
7. Add complete logs, translations, changelog/update UI, save export/import and lifecycle recovery.
8. Treat v2's 1.6–1.12.2 scope as a separate milestone; v1 parity does not cover it.
9. Maintain a device-tested matrix: version, launch method, Java, dependencies/assets, graphics, audio, input, multiplayer and shutdown.

## Acceptance criteria

Do not mark a feature complete just because the APK compiles or a screen exists. Require automated tests where possible, clean-install/upgrade tests, physical ARM64 device tests for runtime/render/input/audio, clear errors for unsupported versions, no secrets in logs, and no success state until the game actually starts.
