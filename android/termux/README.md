# Building HNMods on Android

Horizon starts the native compiler through Termux's official `RUN_COMMAND` interface. The compiler does **not**
extract Termux binaries into Horizon's private app directory: Termux packages are tied to Termux's own prefix.

## One-time setup

1. Install the current Termux app from its [official GitHub releases](https://github.com/termux/termux-app/releases)
   or F-Droid. Do not use the obsolete Play Store build.
2. Open Termux once and let its initial setup finish.
3. In Termux, edit `~/.termux/termux.properties` and add:
   ```properties
   allow-external-apps=true
   ```
   This is an explicit security opt-in that allows Horizon to request commands in Termux.
4. In Android Settings, open Horizon's App info → Permissions → Additional permissions and grant
   **Run commands in Termux environment**.
5. In Termux, run `termux-setup-storage` once and grant the requested shared-storage permission.

## Compile from Horizon

1. In Horizon, choose **Compile HNMod (.hnmod) in Termux** and select one or more bundles.
2. On the first compile, Horizon asks Termux to update its package index and install:
   `rust clang git rsync binutils python cmake make pkg-config coreutils`.
   Later runs reuse the installed packages and the build cache.
3. The compiler checks the engine revision, hooks API and mixins revision, validates the bundles, applies
   `horizon-mixins`, builds `libhorizon_android_game.so`, and checks its Android shared-library dependencies.
4. The result is written under `Downloads/Horizon-builds/`. Return to Horizon and choose **Import compiled game library (.so)**.
   Horizon checks that it is an AArch64 ELF library and asks before activating it.

The current build script requires an ARM64 Android device and at least 10 GB free in Termux's home filesystem.
The first build can take a long time and consume several GB of temporary storage.

## Trust and safety

An `.hnmod` contains Rust source. Rust build scripts and procedural macros can execute code during compilation.
The local Termux build is **not a sandbox** and runs with Termux's app permissions. Only compile source from authors
you trust. The imported native library then runs inside the game process.

## Remote fallback

Starling's public workflow is tied to Starling's own source revision and curated build setup; it is not a remote
compiler endpoint for this Horizon fork. A compatible fallback needs a Horizon-specific build job and a safe way
to submit bundles and retrieve a verified result. That integration is tracked separately; this local path does not
silently upload mods to a third-party service.
