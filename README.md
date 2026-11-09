# Horizon Android Edition

**A native Android edition of [Horizon](https://github.com/horizon-rust/horizon), bringing the Rust-based Minecraft: Java Edition client to ARM64 Android devices.**

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![ABI](https://img.shields.io/badge/ABI-arm64--v8a-blue)](#requirements)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-6750A4)](#launcher)
[![Core](https://img.shields.io/badge/Core-Rust-orange?logo=rust&logoColor=white)](#architecture)

Horizon Android Edition is an unofficial community Android port. It is not affiliated with Mojang Studios, Microsoft, or Zalith Launcher 2.

> **Minecraft files are not included.** You must provide game files that you are legally entitled to use.

## Project status

This repository is actively maintained as an Android adaptation of upstream Horizon. Android compatibility is checked against upstream changes before they are accepted; a newer upstream revision is not considered Android-compatible merely because it builds on desktop.

The project is a preview and still requires testing across more Android devices and GPUs.

## Launcher

The launcher is built with **Kotlin, Jetpack Compose and Material 3**. On Android 12 and newer it can use the device's wallpaper-derived Material You colors; older Android versions use the built-in Horizon light/dark palettes.

Current launcher sections include:

- **Home** — game readiness, selected instance, saved worlds and launch/setup actions.
- **Instances** — create, select and delete instances; manage installed engine versions; import folders and restore/export backups.
- **Mods** — import and remove Horizon mods and shader packs; compile HNMods with the embedded Android toolchain; view build logs and import compiled game libraries.
- **Discover** — browse the native add-on catalog when the upstream core provides catalog data.
- **Servers** — manage saved multiplayer server entries.
- **Accounts** — Microsoft/offline account management and the account skin preview.
- **Controls** — open the full touch-control editor and configure available touch preferences.
- **System** — engine/runtime information, settings, update diagnostics and supported Android rendering options.

The Android launcher is separate from Horizon's desktop GPUI interface. It uses the shared Rust backend through the Android JNI/JSON bridge instead of trying to load the desktop UI on Android.

## Game and touch controls

The game runtime is Horizon's Rust-based Android runtime, packaged for ARM64 (arm64-v8a).

The touch-control system incorporates licensed components from Zalith Launcher 2, including the control layout/editor and the Android-side layer renderer. Horizon keeps its Android input bridge as the route into the game; the launcher UI does not replace the engine's input semantics.

Available control features depend on the bundled Zalith components and the current Android integration. They include editable layouts, virtual keyboard/mouse controls, joystick and camera input, configurable widgets and gamepad-related mappings.

## Mods and HNMod compiler

The launcher supports Horizon-native content rather than treating Java mod-loader packages as native Horizon mods:

- .hmod — Horizon native mods.
- .hshader — Horizon shader packs.
- HNMod archives — compiled through the embedded Android toolchain, with build output streamed to the launcher.
- Compiled native game libraries — can be imported through the launcher.

Compatibility is determined by the engine and mod build metadata. A shader inventory, ping measurement or other feature is shown only when the native interface actually provides that information.

## Minecraft data

Android app storage is sandboxed, so the launcher uses Android's Storage Access Framework for user-selected folders and files.

- Import an existing Minecraft folder.
- Restore a ZIP backup.
- Export a backup of supported user data.
- Keep the game data inside the app's managed storage.

The project does not redistribute Minecraft assets or game files.

## Requirements

### Device

- Android 8.0 / API 26 or newer.
- ARM64 device (arm64-v8a).
- Free storage for the application, game resources, worlds and imported content.
- Minecraft Java Edition files that you are licensed to use.

The Android renderer currently uses the GLES path as the supported default. GPU behaviour and performance vary by device; Vulkan should not be assumed to work on every Android GPU.

### Build environment

The Android build uses:

- JDK 17.
- Android SDK (compile SDK 34).
- Android NDK 26.3.11579264.
- Kotlin 2.0.21 and Android Gradle Plugin 8.5.2.
- Rust toolchain for the JNI core, authentication bridge and Android game runtime.
- Gradle 8.7 in CI.

For the complete environment setup and local build procedure, see [ANDROID_BUILD.md](ANDROID_BUILD.md).

## Build

The CI Android workflow prepares the pinned upstream source and generated Android engine data before running the Gradle build. From a prepared checkout, the debug APK task is:

~~~bash
gradle assembleDebug --stacktrace --no-daemon
~~~

The APK is produced under:

~~~text
app/build/outputs/apk/debug/app-debug.apk
~~~

A successful desktop build alone is not sufficient validation. Changes affecting the Android port must pass the Android build and the relevant Rust/Android integration checks.

## Architecture

~~~text
┌────────────────────────────────────────────────────┐
│               Android launcher UI                  │
│            Kotlin · Compose · Material 3            │
├────────────────────────────────────────────────────┤
│          Android platform integrations              │
│       Storage · Auth · Backup · Game Activity       │
├────────────────────────────────────────────────────┤
│                JNI / JSON bridge                    │
├────────────────────────────────────────────────────┤
│                 Horizon Rust core                   │
│          Launcher · Client · Mods · Engine          │
├────────────────────────────────────────────────────┤
│             Android graphics and input              │
│                GLES · Winit · Touch                 │
└────────────────────────────────────────────────────┘
~~~

### Main repository areas

| Path | Responsibility |
| --- | --- |
| app/ | Android launcher, Compose UI, game activity and platform integrations |
| app/third_party/zalith/ | Vendored Zalith control/editor modules and their supporting modules |
| core/ | JNI bridge between Kotlin and the Horizon Rust code |
| android-auth/ | Microsoft device-code authentication bridge |
| android/ | Android patching, overlays, upstream preparation and build helpers |
| horizon/ | Prepared/pinned upstream Horizon source tree |
| hnmod-core/ | HNMod build and compatibility tooling |
| .github/workflows/ | Android builds, HNMod tests and upstream compatibility checks |

The horizon/ directory is synchronized from upstream. Android-specific changes should normally live in the Android patch/overlay and adapter layers, rather than becoming permanent edits to upstream engine code.

## Upstream compatibility

The repository contains automation to check newer upstream Horizon revisions, prepare the Android patch, regenerate required engine data and run Android build validation. Only a candidate that passes the Android compatibility gate should become the new pinned base.

See the workflows in .github/workflows/ and the Android build documentation for the exact process.

## Installation

1. Download an APK from [GitHub Actions artifacts](https://github.com/DreamingbullyYT/horizon-android-edition/actions) or the project's [Releases](https://github.com/DreamingbullyYT/horizon-android-edition/releases).
2. Install it on a supported ARM64 Android device.
3. Open Horizon Android Edition.
4. Import or install the Minecraft files you are licensed to use.
5. Select or create an instance and configure an account.
6. Launch the game and adjust the touch controls.

Preview/debug APKs may be debug-signed. They are intended for testing and should not be treated as a stable production update channel.

## Known limitations

- Android hardware, drivers and GPU capabilities differ; compatibility still needs broader device testing.
- Microsoft authentication requires the appropriate client configuration in builds.
- Some upstream or desktop-only features do not yet have Android-native implementations.
- Android download lifecycle, credential hardening and broader multiplayer validation remain ongoing work.
- A successful CI build does not replace real-device testing.

## Development principles

- **Upstream first:** reuse Horizon's existing behaviour whenever possible.
- **Android where necessary:** keep platform-specific code in Android adapters and services.
- **Preserve input semantics:** do not change the existing game input bridge for UI-only work.
- **No invented functionality:** only expose controls and status that are backed by real native operations.
- **Test before shipping:** validate the Android build and exercise important flows on a physical device.
- **Respect player data:** preserve worlds and user data during updates, imports and cleanup.

## Credits and licensing

- [Horizon](https://github.com/horizon-rust/horizon) — upstream Rust project.
- Zalith Launcher 2 — source of the licensed Android touch-control components included in this repository.

This repository contains components with different licenses. Review [HORIZON-LICENSE.txt](HORIZON-LICENSE.txt), [LICENSE-ZalithLauncher2-GPL-3.0.txt](LICENSE-ZalithLauncher2-GPL-3.0.txt), and the license notices accompanying third-party dependencies before redistributing modified source or builds.

## Disclaimer

Horizon Android Edition is an unofficial project. It is not affiliated with, endorsed by, or sponsored by Mojang Studios or Microsoft. Minecraft is a trademark of Mojang Synergies AB. The project does not include or redistribute Minecraft's proprietary code or assets.
