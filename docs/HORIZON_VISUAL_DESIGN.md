# Horizon Android Edition — Visual Design System

## Direction
Premium dark gaming launcher. Keep Horizon identity, but use a modern editorial dashboard with restrained neon-cyan accents, deep charcoal surfaces, large rounded cards, compact metadata, and clear hierarchy.

## Navigation
Bottom navigation: Home, Library, Mods, Servers, Settings. Profile/skin management is reachable from the player card and profile avatar.

## Home
- Greeting and player profile header.
- Featured Minecraft installation card with selected version, install state, and a prominent Play action.
- Player/skin showcase card; the final implementation must use the actual selected Minecraft skin texture and a real 3D model renderer, not a static illustration.
- Quick actions for versions, mods, servers, and file management.
- Runtime/build status presented as quiet metadata, not the main focus.

## Skin and profile
- 3D player preview with orbit/rotate interaction.
- Skin upload/import, classic/slim model selection, reset, and skin apply.
- Profile name and account state. Never fake an authenticated account or skin.

## Library
- Installed versions and instances, launch configuration, per-instance settings.

## Mods
- Separate HMod, HNMod, and HShaders sections; import, enable/disable, compatibility and build status.

## Servers
- Saved server list, add/edit/remove, ping/status only when measured.

## Settings
- Graphics backend, render distance, FPS, memory, input/control editor, downloads, storage, updates, diagnostics.

## Implementation constraints
- Native Kotlin + Jetpack Compose; no WebView.
- Existing GameSetup, Updater, EmbeddedToolchain and native bridge remain the source of truth.
- Do not display actions as working until they are wired to real operations.
- Keep engine and launcher-core source untouched unless an Android-only adapter is insufficient.
- Support narrow phone layouts, landscape gameplay, accessibility contrast, and low-memory devices.
