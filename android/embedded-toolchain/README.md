# Horizon embedded Android toolchain

## Target architecture

Horizon owns the build environment in its private app sandbox. The target is:

- App ID: `dev.horizon.launcher`
- ARM64 ABI: `arm64-v8a`
- Runtime prefix: `/data/data/dev.horizon.launcher/files/usr`
- Android 10+ bootstrap format
- No separate Termux app, RUN_COMMAND permission, external terminal, or shared-storage setup

## Bootstrap build

The manual GitHub Actions workflow `.github/workflows/embedded-toolchain.yml` checks out a pinned
Termux package-build-system commit and builds packages from source with Horizon's application ID.
It does **not** install stock `com.termux` binary packages into Horizon's prefix.

The initial package set is Rust/Cargo, Clang/LLVM, Git, rsync, binutils, Python, CMake, Make,
pkg-config and coreutils. The workflow validates the archive contents, emits a SHA-256 checksum and
a manifest, and uploads an artifact. Publishing is opt-in and creates an immutable, versioned
GitHub Release.

Pinned package-builder revision: `2cce9f2663c75223f8418b9682a61bc690052097`.

## Runtime status

The launcher currently has the Horizon-owned compiler boundary and intentionally fails closed.
The generated bootstrap is **not yet installed or executed by the app**. The following work remains
before local compilation can be enabled:

1. The app now has a streaming, checksum-verified, atomic bootstrap extractor for `files/usr`.
   It rejects traversal, duplicate entries, unsafe symlink destinations and oversized archives.
2. Add the first-run downloader and pin the release manifest/checksum after the bootstrap workflow
   produces its first verified artifact.
3. Verify the archive's symlink map against the custom Horizon prefix and execute the bootstrap
   second stage with Horizon's app UID.
4. Integrate an Android 10+ executable bridge compatible with app-private writable storage and
   verify it on API 29+ devices. Merely extracting binaries and setting executable bits is not enough.
5. Run shell, Cargo, Clang and `readelf` smoke tests before marking the runtime ready.
6. Connect the existing HNMod validator, compatibility resolver, mixins application and game build
   script to this runtime. Retain Cargo cache and logs across runs.
7. Add artifact signature verification and rollback/repair support.

The toolchain is multi-gigabyte and should be downloaded on first compilation, not embedded in the
base APK. The app should display the download size, free-space requirement, progress and cancellation.

## Remote fallback

The remote build must be a separate Horizon-specific workflow. Never embed a personal GitHub token
in the APK, and never expose repository secrets to mod build scripts or procedural macros.

## Security and licensing

HNMod build scripts and Rust procedural macros execute code. A local build is not a sandbox; ask
for explicit trust confirmation before compilation. The build bootstrap is based on the Termux
package build system; preserve its GPL-3.0 and per-package license notices when redistributing it.
