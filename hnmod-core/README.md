# Horizon HNMod Core

Standalone, UI-independent Rust library for inspecting and planning builds from Horizon native mods.

## Current scope

- Reads and validates official Horizon `.hnmod` source bundles without extracting or executing native code.
- Enforces Horizon's manifest ID rules, exact source revisions, allowed payload paths, duplicate-entry checks and archive size limits.
- Resolves multiple enabled mods against the exact engine revision, hook API level and pinned horizon-mixins revision.
- Allows stable-hook mods to target a later engine only when its declared hook API level is high enough.
- Extracts only validated `mixins/<id>/` source into a fresh destination, with create-new semantics.
- Produces deterministic SHA-256 build keys independent of mod selection order.
- Provides the `hnmod` CLI used by the Termux compiler: `inspect`, `resolve` and `extract`.

There are no Android, JNI, UI, storage, network or process-execution dependencies.

## Conservative compatibility policy

A mod declaring hooks: N can use a candidate engine only when candidate.hooks_api >= N. A mod without hooks is pinned to its exact horizon commit. All selected mods must agree on the exact horizon-mixins revision in this initial version.

Git commit IDs are opaque identifiers. The resolver does not guess commit chronology by sorting hashes. An ancestry-aware resolver and complete dependency graph are planned next.

## Android compiler integration

Horizon now packages this crate and the Android build script as app assets. The launcher materializes
them in private storage, validates selected archives, runs the build script inside Horizon's own
Termux-derived ARM64 runtime, then imports the resulting AArch64 game library automatically.

The former external Termux RUN_COMMAND integration is removed. Users do not install a separate
Termux app, grant its permissions, or configure shared storage. The script path
`android/termux/build-with-mods.sh` is invoked by Horizon's embedded process runner.

The runtime installer and Android linker-exec bridge are gated by checksum, bootstrap second-stage
and compiler smoke tests. Real-device validation is still required before claiming the full local
compiler flow is production-ready.

## Test

Run: cargo test --manifest-path hnmod-core/Cargo.toml

## Attribution

The archive format and compatibility concepts are based on Starling's MIT-licensed implementation. The original license is preserved in THIRD_PARTY_LICENSES/starling-MIT.txt.
