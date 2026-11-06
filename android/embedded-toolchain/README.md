# Horizon embedded Android toolchain

## Goal

HNMod compilation is intended to run inside Horizon's own application sandbox. Users should install
only Horizon: no separate Termux APK, no RUN_COMMAND permission, no Termux settings, and no shared
Downloads staging directory.

## Current repository state

- The external Termux RUN_COMMAND integration has been removed from the Android manifest and
  compiler entry point.
- The launcher now has a Horizon-owned runtime status/preflight entry point and fails closed when
  the runtime payload is absent.
- The Android app remains ARM64-only, matching the current native game runtime.
- **Compilation is not yet enabled in this commit.** The embedded runtime marker is not a compiler;
  a real signed toolchain payload and executable bridge must be built and tested before marking the
  runtime ready.

## Why this is staged

A stock Termux bootstrap cannot simply be unpacked under Horizon's private directory. Termux
packages are built around Termux's own package prefix and app data path. Horizon needs a package
feed built for its own prefix (or a deliberately relocatable toolchain). Android 10+ also restricts
execution of writable app-private files; the native execution bridge must address the W^X policy
rather than relying on chmod.

The planned implementation is split into verifiable milestones:

1. **Bootstrap proof:** package an ARM64 minimal shell/runtime in the APK, extract atomically into
   `files/horizon-toolchain/usr`, verify a pinned SHA-256 manifest, and run an in-app smoke test.
2. **Executable bridge:** implement and test the Android-compatible linker/exec mechanism on API 29+
   devices. Never mark the runtime ready unless the smoke test passes.
3. **Toolchain feed:** publish Horizon-prefix-compatible Rust, LLVM/Clang, Git, Python and build
   dependencies. Pin package versions and verify signatures/checksums.
4. **HNMod adapter:** connect the existing validation/mixins/build script to the private runtime,
   preserve build logs in app storage, and import only verified AArch64 ELF outputs.
5. **Remote fallback:** provide a separate isolated GitHub Actions build path. Do not embed a personal
   GitHub token or expose repository secrets to mod build scripts.

## Security

HNMod build scripts and Rust procedural macros execute code. Local builds are not sandboxed, so
show a trust warning and require explicit confirmation. A remote build runner must be ephemeral,
must not receive repository secrets, and must validate the resulting ELF and metadata before
publishing it.

## Storage and lifecycle

The full Rust/Clang environment is large. Install it on first use, keep Cargo caches between builds,
show storage requirements, support repair/update, and allow users to remove the toolchain without
removing Minecraft data. Do not install multi-gigabyte packages as part of the base APK.
