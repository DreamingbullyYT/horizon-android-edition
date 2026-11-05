# Horizon HNMod Core

Standalone, UI-independent Rust library for inspecting and planning builds from Horizon native mods.

## Current scope

- Reads .hnmod ZIP archives without extracting or executing native code.
- Validates manifest identity, exact source revisions, allowed payload paths, duplicate entries and archive size limits.
- Resolves multiple enabled mods against explicitly supplied engine candidates.
- Allows stable-hook mods to target a later engine only when its declared hook API level is high enough.
- Produces deterministic SHA-256 build keys independent of mod selection order.

There are no Android, JNI, UI, storage, network or process-execution dependencies.

## Conservative compatibility policy

A mod declaring hooks: N can use a candidate engine only when candidate.hooks_api >= N. A mod without hooks is pinned to its exact horizon commit. All selected mods must agree on the exact horizon-mixins revision in this initial version.

Git commit IDs are opaque identifiers. The resolver does not guess commit chronology by sorting hashes. An ancestry-aware resolver and complete dependency graph are planned next.

## Test

Run: cargo test --manifest-path hnmod-core/Cargo.toml

## Attribution

The archive format and compatibility concepts are based on Starling's MIT-licensed implementation. The original license is preserved in THIRD_PARTY_LICENSES/starling-MIT.txt.
