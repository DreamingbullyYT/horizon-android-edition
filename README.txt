Horizon Android 0.1.0-alpha (arm64-v8a)
========================================

Unofficial Android preview of the Horizon Rust Minecraft client. Includes GPL-3.0 touch-control components from Zalith Launcher 2. Not affiliated with Mojang, Microsoft, or Zalith.

Install
-------
Install Horizon-Android-0.1.0-alpha-arm64-debug.apk on an ARM64 Android device (Android 8 / API 26 or newer). This APK does not include Minecraft game files. Import only game files you are licensed to use.

Mods and shaders
----------------
The launcher imports Horizon .hmod packages from Mods. Java .jar mods and third-party loader ecosystems are not supported by this importer. Shaders accepts Horizon .hshader packs and compatible ZIP packs containing a shaders/ directory. The Shaders page lists/removes imported packs and opens the app-private shaderpacks folder through the Android file picker. The game may need to restart before changes take effect; select packs in Options > Horizon Settings > Shader Packs when available.

Controls
--------
The game screen has an editable touch HUD that emits keyboard and mouse input. Drag the free screen area to look around; menu swipes are routed to the game UI. The quick panel includes controls, mouse, gamepad, gestures, gyro, and system settings.

Verification and limits
-----------------------
The current source built successfully with Gradle assembleDebug and the APK installed/launched on a connected Android 16 / ARM64 phone. Import UI and dialogs are implemented, but package import was not manually exercised during this packaging pass. This is an alpha preview; Microsoft sign-in and multiplayer joining are not connected to the Android UI. The APK is debug-signed and cannot be used for seamless production updates.

Build from source
-----------------
See horizon-android-source.zip. Android Gradle Plugin 8.5.2 and Kotlin 2.0.21 are used.

Licensing
---------
The source ZIP contains the Android app, Rust bridge, Horizon source, and Zalith-derived controls. License notices are included here and in the source tree. The app is named Horizon and identified as an unofficial modified build as required by the Zalith GPL additional terms.

Application ID: dev.horizon.launcher
Version: 0.1.0-alpha (version code 1)
ABI: arm64-v8a
Signing: debug key
