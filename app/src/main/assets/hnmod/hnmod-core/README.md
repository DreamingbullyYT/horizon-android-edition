# Horizon HNMod Core

Standalone, UI-independent Rust library for inspecting and planning builds from Horizon native mods.

## Current scope

- Reads the PC launcher's `.hnmod` ZIP layout: `horizon-native.json` plus
  `mixins/<id>/horizon-mixin.toml`.
- Accepts PC bundle metadata fields without requiring Android-only `schema`,
  `game`, or duplicated version fields. The mod version comes from
  `[mod].version`; the Horizon pin may come from the native manifest or
  `[compatibility].horizon_revision`.
- Keeps ZIP traversal, duplicate-entry, symlink/special-file, entry-count and
  expanded-size protections while accepting complete safe payload trees (API
  crates, helper sources, data and other files) under the mod's own folder.
- Resolves stable hook API requirements and reports pinned-revision differences.
  The Termux script selects Horizon and horizon-mixins commits by their actual
  Git commit dates, matching the PC launcher's newest-revision policy.
- Extracts only validated `mixins/<id>/` payloads and computes deterministic
  source digests.

The Android build runs the upstream `horizon-mixins` CLI against a staged
workspace, then builds the Android ARM64 game library. PC binary outputs are
never copied to Android: the source bundle format is shared, while Cargo
recompiles for Android.

**Compatibility boundary:** the launcher-side API-crate/dependency staging and
full PC build-record/cache workflow are not yet ported. Bundles declaring
`[api]` or API-to-API `[dependencies]` still need that staging layer before
they can be considered fully supported. A source mod that uses Windows-only
APIs or unsupported Android dependencies can also fail the ARM64 build.
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
