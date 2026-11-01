# Horizon Android port — current status

This archive contains the Android launcher preview, its JNI Rust bridge, and the embedded Horizon source tree. The original uploaded archives were left untouched; this is a working-copy continuation.

## What is currently usable

- Jetpack Compose launcher shell with Home, Mods, Servers, Versions, and Settings screens.
- The Rust launcher core is called through the small JNI/JSON boundary in `core/`.
- App-private launcher storage is initialized under `filesDir/launcher`.
- Offline player names and the selected player can be managed through the native core.
- Instances, saved servers, mods metadata, installed versions, release metadata, telemetry consent, and launcher settings are exposed by the bridge.
- Settings changes for auto-install, pruning, opening animation, AI opt-out, and accent colour are saved through the Rust core.
- In-game controls now use Zalith Launcher 2's GPL-3.0 `LayerController` and `InputMap` modules. A Zalith joystick and keyboard/mouse buttons are rendered in a Compose subwindow above the NativeActivity surface; button outputs and relative look are bridged into the Rust game loop.
- The in-game quick panel now has Controls, Mouse, Gamepad, Gestures, Gyro, and System tabs. It exposes HUD/opacity, camera sensitivity and inversion, tap and long-press mouse actions, gyro look, FPS number/chart and process-memory overlays, controller button/axis mappings and deadzone/camera sensitivity, media volume, brightness, keep-awake, maximum refresh-rate request, and close-game action. The Zalith editor can select, move, resize, add, and delete button/joystick controls, then saves a compatible JSON layout at `filesDir/launcher/touch-layout.json`.
- Unoccupied touches steer the camera during gameplay and are routed through the existing Winit touch path in menus, including drag scrolling. The launcher Play button starts the custom `HorizonGameActivity` subclass of NativeActivity and passes the selected offline account name into the shared Rust client process.
- Gradle builds the Rust JNI core and game `cdylib` for `arm64-v8a`; archived prebuilt libraries are not used as build inputs.
- On Android the client selects OpenGL ES. Vulkan on the tested Adreno 740 failed to link Horizon's sky pipeline; GLES created the renderer and presented a frame.
- The Rust JNI libraries use 16 KB ELF load alignment, and APK packaging extracts native libraries at install time for compatibility with newer Android page sizes.

## Port-specific corrections in this continuation

- Reconciled `SettingsScreen` with the arguments supplied by `HorizonApp`; the previous source had a function/call-site mismatch.
- Restored the saved accent centrally in `HorizonApp` and removed the duplicate effect from the Settings screen.
- Wired Play to the in-process NativeActivity game runtime. Join remains gated because multiplayer integration is not connected to Android.
- Made the release list clearly show that Android game assets are unavailable and suppresses Install when a release has no matching Android asset.
- Removed the misleading Microsoft sign-in buttons. The Rust core can report whether a client ID is configured, but the Android app does not yet start/poll the device-code flow.
- Corrected the Servers warning to refer to joining, not hosting; saved server lists can still be maintained.
- Expanded the in-app preview notes to describe the actual mobile blockers and next implementation sequence.

## What still blocks gameplay

The APK does not package Minecraft game files. On the connected ARM64 phone, ADB testing reached the Horizon 26.3 world and title screens, rendered the Zalith HUD, and confirmed the quick panel's Controls, Mouse, Gamepad, Gestures, Gyro, and System tabs open without an Android exception. Menu drag scrolling is bridged to native Winit wheel events. The current editor changes layout geometry and adds/deletes controls; per-button style/binding dialogs, context-layer editing, macros, and layout import/export UI remain future work. Mod and shader import paths are implemented but were not manually exercised during this packaging pass. Gamepad remapping/axes and gyro motion need physical hardware validation; no physical gamepad was connected during this check.

Other mobile gaps:

- Microsoft device-code sign-in is not connected to the Compose UI.
- The current Android account store is not backed by Android Keystore; do not use it for persistent Microsoft refresh tokens.
- Downloads should move to an Android foreground service with progress, cancellation, and process-death recovery before large installs are exposed as normal mobile UX.
- Zalith's virtual cursor modes, input-method controls, custom resolution rules, and game-log viewer are not implemented: Horizon's Android runtime does not yet expose equivalent renderer/window or IME/log controls.
- The full desktop GPUI launcher UI is not reused; this is a native Compose adaptation over the shared Rust backend.

## Recommended next milestones

1. Add Android Keystore-backed credential storage and only then wire Microsoft device-code sign-in.
2. Add a foreground download service with resumable downloads, progress events, cancellation, and integrity verification.
3. Validate joystick movement, action buttons, gamepad remaps, gyro aim, settings persistence, and audio output on-device; extend the editor with style dialogs, custom layers, and layout import/export.
4. Add Android-specific resolution scaling, virtual mouse modes, input-method integration, audio focus, logs, and safe pause/resume; connect Android multiplayer joining.
5. Add ARM64 Android CI that builds both Rust libraries and the APK from source.

## Build and verification notes

The project uses Android Gradle Plugin 8.5.2 and Kotlin 2.0.21. On 2026-11-01, `assembleDebug` built successfully from source for ARM64. ADB installed and launched the app on Android 16 / API 36 (OnePlus CPH2449). Checks showed the Rust game running in the saved world, the Zalith HUD and quick settings panel, and all six settings tabs. The gamepad tab correctly reports no physical controller when none is attached. The menu scroll bridge is present in the Rust Winit event path; physical gamepad mappings, gyro aim, and saved setting values have not yet been exhaustively exercised. Audio output also remains unconfirmed on this device.

The device was tested through normal ADB app install; its `su` command is unavailable to the ADB shell, and root access was not needed for these checks. The included prebuilt APK and `.so` files are archival artifacts and are excluded from the new build source set.
