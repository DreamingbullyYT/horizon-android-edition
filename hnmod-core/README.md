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

## Android Termux compiler

The compiler script uses this crate as its only archive-validation and extraction path:

```sh
bash android/termux/build-with-mods.sh mod1.hnmod mod2.hnmod
```

It rejects mismatched Horizon/mixins revisions and insufficient hook API levels before calling
`horizon-mixins`. The build is local to Termux; native Rust build scripts and procedural macros
execute with the Termux user's permissions. This is **not sandboxed**, so compile only mods whose
source you trust. It does not yet provide Starling's remote isolated build service or launcher UI.

The CLI can also be used directly:

```sh
cargo run --manifest-path hnmod-core/Cargo.toml --bin hnmod -- inspect example.hnmod
cargo run --manifest-path hnmod-core/Cargo.toml --bin hnmod -- resolve <horizon-revision> <hooks-api> <mixins-revision> example.hnmod
cargo run --manifest-path hnmod-core/Cargo.toml --bin hnmod -- extract example.hnmod /tmp/example-mod
```

## Test

Run: cargo test --manifest-path hnmod-core/Cargo.toml

## Attribution

The archive format and compatibility concepts are based on Starling's MIT-licensed implementation. The original license is preserved in THIRD_PARTY_LICENSES/starling-MIT.txt.
