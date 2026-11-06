# Horizon embedded HNMod runtime

Horizon uses the official Termux ARM64 bootstrap inside its own private app storage. It does not
install the separate Termux application, request RUN_COMMAND permission, or require a second
package repository.

## First HNMod compilation

1. The app downloads the pinned official Termux ARM64 bootstrap and verifies its SHA-256 digest.
2. It extracts the bootstrap into a private fake `/data/data/com.termux` tree.
3. PRoot mounts that tree at the paths expected by Termux packages.
4. The launcher configures the official Termux main repository and installs Rust/Cargo, Clang,
   Git, rsync, binutils, Python, CMake, Make, pkg-config and coreutils.
5. Shell, Rust and native compiler smoke tests must pass before the runtime is marked ready.
6. The existing Horizon HNMod validation/build/import pipeline runs inside this environment.

The APK build fetches PRoot and its small runtime libraries from the official Termux package
repository using `scripts/fetch-hnmod-runtime-assets.sh`. The larger bootstrap is downloaded only
when the user first compiles an HNMod, rather than inflating every APK.

## Pinned bootstrap

- Release: `bootstrap-2026.11.01-r1+apt.android-7`
- ABI: `aarch64`
- SHA-256: `5a1429b6c68ee3bdc17b5fd6e47ba42933ec924cd7283fe74992574e1d5badae`
- Package source: `https://packages-cf.termux.dev/apt/termux-main`

The package index is refreshed on first setup and packages are installed from the official Termux
repository. No additional Horizon package repository is created.

## Security

HNMod Rust build scripts and procedural macros execute code with Horizon's app permissions.
Compilation is not a sandbox. The existing explicit trust confirmation remains required.
The bootstrap and each package retain their upstream licenses; preserve the upstream Termux GPL-3.0
and package-specific notices when redistributing binaries.
