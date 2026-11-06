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

The former launcher integration invoked a separately installed Termux app through RUN_COMMAND.
That path has been removed. Horizon is moving to an app-owned ARM64 bootstrap under its private
`files/usr` prefix, with a Horizon-specific package build and an Android linker-exec bridge.

The old `android/termux/build-with-mods.sh` script is retained as a **developer reference** for the
HNMod validation, engine sync, mixins application and native game build sequence. It is not the
supported launcher path and must not be presented to users as requiring Termux.

The current embedded integration has:
- a pinned custom-prefix bootstrap build workflow;
- an atomic, checksum-verified archive extractor with symlink/path validation;
- a process runner using the Termux linker-exec preload;
- bootstrap second-stage and compiler smoke-test gates before the runtime-ready marker.

The bootstrap build and Android process bridge still require successful CI and real-device validation.
The launcher does not yet claim that local HNMod compilation is available.

## Test

Run: cargo test --manifest-path hnmod-core/Cargo.toml

## Attribution

The archive format and compatibility concepts are based on Starling's MIT-licensed implementation. The original license is preserved in THIRD_PARTY_LICENSES/starling-MIT.txt.
