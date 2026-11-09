# rustMC — Functional and Technical Specification

> Status: design draft. Planning only; this document does not claim that these features are implemented.
> Repository: DreamingbullyYT/horizon-android-edition
> Working branch: feature/material-you-cleanup

## 1. Vision and principles

rustMC is an Android-first, offline-first Minecraft launcher. It should provide dependable launching and data management while keeping Android-specific code isolated from the Horizon engine/core wherever practical.

Principles:
- Reuse working upstream/core functionality; do not rewrite it without a demonstrated need.
- Preserve the existing JNI/native input bridge contract unless a separately reviewed change requires otherwise.
- Use stable interfaces between UI, launcher services, and engine.
- Treat downloaded packages and archives as untrusted until validated.
- Make long operations observable, cancellable jobs.
- Protect worlds and configuration from updates, restores, and cleanup operations.
- Do not claim a feature works until it passes its acceptance tests.

## 2. Confirmed decisions

| Area | Decision |
|---|---|
| Updates | Both independently updated engine/components and launcher APK |
| Engine | One globally active engine version shared by all instances |
| Recovery | Keep previous known-good engine and support rollback |
| Compatibility | Force: allow update despite instance compatibility warnings; warn and require confirmation before launching an incompatible instance |
| Activation tests | All mandatory pre-activation tests must pass |
| Offline | Strict: local features must not require network access |
| HNMod queue | Maximum 3 queued jobs per user |
| HNMod engine | Newest compatible engine; stop if none is compatible |
| HNMod cache | Persistent |
| HNMod installation | Ask before installing compiled output |
| HNMod partial failure | Keep successful outputs and report failed items |
| UI | Horizon identity, Material You / Material 3, hybrid navigation |
| Status | Planning only |

Important interpretation of force: it overrides per-instance compatibility warnings only. It does not bypass package signature, checksum, ABI, or mandatory test failures. A package that fails a mandatory test cannot become active; it may be retained for diagnostics.

## 3. Architecture

Logical layers:
1. Android UI — screens, navigation, accessibility, Material 3.
2. Launcher API/application services — commands, results, jobs, events.
3. Launcher Core — instances, storage, downloads, backups, accounts, diagnostics, update orchestration.
4. Engine Manager — manifests, validation, compatibility, activation, rollback.
5. Mod and Resource Services — HMod, HNMod, HShaders, resource packs and metadata.
6. Runtime Manager — Java runtimes and compiler/toolchain packages.
7. Native/Horizon boundary — existing JNI and native engine integration.

UI must not directly manipulate engine internals or duplicate storage logic. Android-specific code should remain isolated. Preserve existing native control, look, menu-touch, and IME bridge semantics. Prefer adapters over invasive engine changes.

## 4. Navigation and modules

Primary destinations: Home, Instances, Worlds, Mods, Resources, Servers, Accounts, Controls, Files, Settings, Diagnostics.

### Home
Selected instance, Minecraft version, launch action, session state, active engine, update status, last played, relevant warnings, and shortcuts.

### Instances
Create, rename, clone, import, export, and delete instances. Each instance owns its Minecraft data, worlds, mods, shaders, resource packs, and settings. Show version, Java selection, storage use, and engine compatibility. Deleting an instance must not delete shared engine/runtime packages. Prefer recoverable Trash before permanent deletion.

### Worlds
List worlds per instance; show name, version when known, size, and modification time. Import, export, rename, delete, back up, and restore. Warn before overwrite and offer restore under a new name. Normally save and close the game before snapshotting; do not claim safe live backup without a proven snapshot mechanism.

### Mods
Support HMod, HNMod, and HShaders as distinct formats/types. Import local files and optionally browse a remote catalog. Install, uninstall, enable, and disable without deleting disabled files. Show engine/Minecraft compatibility, dependencies, conflicts, and errors where reliable metadata exists. Do not claim dependency/conflict detection where the format has no trustworthy metadata.

### Resources
Manage resource packs, shader packs, and other supported resources. Import/export, enable/disable, and reorder only where supported by the engine. Validate archives and report unsupported resources.

### Servers
Save, add, edit, and delete server entries. Show address, port, optional name, and last connection. Ping/status only where protocol and network permit. Quick connect and connection history. Offline mode does not imply online multiplayer.

### Accounts
Support only authentication flows provided by the engine/game ecosystem. Support offline profiles only where permitted. Protect secrets using Android secure storage/Keystore where appropriate. Never put passwords or refresh tokens in ordinary backups, manifests, or logs. Cache skins for offline viewing; do not promise offline Microsoft authentication.

### Controls
Full touch-control editor: create, position, resize, configure, save, and switch profiles. Include keyboard/input mappings. Preserve existing working control modules and native bridge behavior. Validate changes in a real game session.

### Files
Browse launcher/game-owned files and user-selected locations. Use Android Storage Access Framework (SAF) where required. Provide safe operations, clear path context, and confirmations. Local file operations must work offline.

### Settings
Graphics, resolution/scaling, memory, audio, input, Java, appearance, storage, update, and backup settings. Expose only implemented and supported options; explain restart requirements.

### Diagnostics
Live logs, crash history, export with secrets redacted, missing/corrupt-file checks, and safe repair actions. Show probable causes with evidence and uncertainty, not certainty. Include app, engine, game, Java, ABI, renderer, and session identifiers where available.

## 5. Global Engine Manager

One globally active engine is shared by all instances. Instances retain separate Minecraft data and declare compatibility requirements; they do not bundle separate engine copies.

Logical package slots: active, previous known-good, downloaded packages, staged package awaiting validation, manifests, and update history. Physical paths must respect Android storage and native library loading constraints. Never overwrite native libraries while loaded; use a controlled process restart.

### Update/activation flow
1. Resolve trusted update manifest.
2. Download into staging.
3. Verify signature, checksums, package identity, ABI, and required files.
4. Run the complete mandatory pre-activation test suite.
5. Check compatibility against every known instance.
6. Show incompatibilities and affected instances.
7. If all mandatory tests pass, ask for activation confirmation and restart if required.
8. Switch active version atomically or through a recoverable manifest/pointer.
9. Retain previous known-good version.
10. Monitor startup/health checks; rollback and record diagnostics if activation fails.

### Force compatibility policy
- Instance incompatibility alone does not block installation/activation after mandatory tests pass.
- Before launching an incompatible instance, show version/reason and require explicit confirmation.
- Never silently migrate or rewrite a world.
- Recommend a backup before incompatible launch.
- Record override decisions in diagnostics.
- Mandatory test failure always blocks activation.

### Rollback
Retain at least the previous known-good package. Rollback must not modify worlds, mods, or instance settings. Support automatic recovery after failed health checks and manual rollback. If rollback fails, preserve both packages and diagnostics.

## 6. Update system

### Component updater
Independently version engine packages, Java runtimes, HNMod toolchains, and safely separable resources. Each package manifest must identify package/version, supported ABI/platform, size, checksum, signing identity, dependencies, and compatibility metadata.

### APK updater
Use a trusted release/manifest source, with GitHub as the intended distribution channel. Download APK, verify signing identity and checksum, then invoke Android's supported package installation flow. Preserve app data during normal in-place upgrades. Require OS/user confirmation where required. Do not bypass Android installer, signing, or downgrade protections. Signing-key changes require a deliberate migration/reinstall plan.

Interrupted downloads remain inactive. Failed component updates retain the previous active version. APK installation failure must leave the installed app unchanged.

## 7. Strict offline behavior

Must work offline:
- Launch already installed profiles that support local/offline authentication.
- Manage local instances, worlds, mods, resources, controls, and files.
- Create/restore local backups.
- View cached skins and local logs.
- Use installed engine and Java packages.
- Browse saved server entries.

Requires connectivity:
- New downloads and remote catalogs.
- New Microsoft authentication/token refresh when required.
- Online multiplayer.
- Cloud HNMod compilation.
- Remote update checks and remote services.

No network check may block a local operation when all required data is present. Clearly distinguish offline profiles from authenticated online play.

## 8. Storage and manifests

Suggested logical layout (actual paths must respect Android storage rules):

~~~text
rustMC/
  launcher/       settings, metadata, logs, cache
  engine/         active, previous, packages, manifests
  instances/      per-instance game data and instance.json
  runtimes/       Java and toolchains
  backups/        world and full backups
  updates/        downloads, staging, history
~~~

Each instance manifest should contain stable ID, display name, Minecraft version, Java selection, enabled mod/resource state, storage references, timestamps, and compatibility metadata. Never store credentials in it.

Use stable IDs rather than names. Validate archive paths against traversal. Use temporary files and atomic replacement for critical metadata. Separate user data from regenerable cache. Handle revoked SAF permissions and unavailable storage gracefully.

## 9. Backup and restore

World backup contains the selected world and metadata identifying version/source instance.

Full backup contains the explicitly selected scope: instance data, worlds, mods, shaders, resource packs, configuration, and relevant launcher metadata. Shared engine/runtime packages may be referenced or optionally included; UI must state which. Authentication secrets are excluded by default.

Archive manifest: format version, creation time, source instance/world ID, Minecraft version, included components, file sizes, and per-file checksums.

Restore flow:
1. Inspect without overwriting.
2. Validate format, paths, checksums, and free space.
3. Check Minecraft, engine, Java, and mod compatibility.
4. Present conflicts and overwrite risk.
5. Offer restore as a new instance/world by default.
6. Apply through staging and rollback-safe operations.
7. Verify restored data and report skipped/incompatible items.

## 10. Java Runtime Manager

Detect installed runtimes and architectures. Download only from trusted configured sources and verify packages. Allow per-instance runtime selection while sharing installed runtime packages. Check Minecraft/runtime compatibility using maintained metadata. Keep runtime selection independent of global engine version. Report missing runtime, wrong architecture, and unsupported version.

## 11. Graphics API Manager

Enumerate renderer backends actually built into/supported by the engine. Check Android API level, GPU, driver, ABI, and backend requirements. Show Vulkan 1.1 or OpenGL ES 2.0–3.0 only when the complete stack supports it. A selector cannot add unsupported engine/driver functionality. Preserve a known-good setting and recovery path after renderer failure. Record renderer/device details in diagnostics.

## 12. Android lifecycle and session safety

Handle pause/resume, focus loss, audio focus, surface destruction/recreation, screen lock, calls, and process recreation. Suspend/release renderer/audio only through safe engine hooks. Recreate graphics surfaces/contexts when required. Prevent stuck input after focus changes. Request world save before orderly shutdown where supported.

Android may kill the process under memory pressure; exact in-memory session restoration cannot be guaranteed. Prioritize durable world saves and recovery on next launch. Do not claim the game can remain alive indefinitely in the background.

## 13. HNMod Cloud Compiler

User selects .hnmod file(s); launcher resolves engine/build configuration and creates a unique job ID. Maximum three queued jobs per user. Select newest compatible engine; stop if none is compatible. Persistent cache key must include source content, engine/toolchain version, and build configuration.

Launcher is the primary place to view job status, progress, and logs. Discord bot may mirror status/notifications but must not be the only interface. Model states such as queued, preparing, compiling, packaging, succeeded, partially succeeded, failed, and cancelled. Persist useful job history locally.

Validate output before offering installation and ask before installing. If some inputs fail, preserve successful outputs and report failed items. Do not expose credentials/private source data in logs.

## 14. Launcher API, jobs, and events

Expose documented, versioned operation families:
- instances: list/create/clone/rename/delete/launch
- worlds: list/backup/restore/import/export/delete/rename
- mods: list/import/install/uninstall/enable/disable
- resources: list/import/enable/disable/reorder
- engine: checkUpdate/install/test/activate/rollback/status
- runtimes: list/install/select
- backups: create/list/inspect/restore
- diagnostics: logs/history/export/verify
- compiler: submit/status/cancel/results/install

Long-running operations return job IDs. Events include progress, stage, human-readable message, warning/error code, and terminal result. UI must handle cancellation, retries, process recreation, and stale job state.

## 15. Security and privacy

Verify package signatures/checksums and source identity. Reject malformed archives and path traversal. Protect credentials and redact logs/exports. Require confirmation for destructive operations and compatibility overrides. Minimize permissions and explain storage access. Telemetry is disabled by default; any future telemetry requires disclosure and opt-in. Treat downloaded manifests, mods, and server metadata as untrusted input.

## 16. Diagnostics and recovery

Correlate logs by launch/session/job ID. Capture app/engine/game/runtime/renderer versions and device ABI. Detect missing files and common integrity failures. Offer repair only from trusted sources and when affected data is understood. Crash analysis is probabilistic and must show evidence/uncertainty. Export bundles must redact secrets.

## 17. Acceptance test plan

A release is not complete merely because the APK compiles or installs.

### Build and installation
- Clean supported Android build variants succeed.
- APK installs and upgrades without losing app data.
- Signing identity remains consistent.
- Fresh install initializes metadata/directories.

### Launch and gameplay
- Install a supported Minecraft version.
- Launch to main menu without crash.
- Enter new and existing worlds.
- Verify touch controls, keyboard/input, look, menu interaction, audio, and rendering.
- Save, exit, relaunch, and verify world progress.

### Data management
- Two instances remain isolated.
- Clone/import/export preserve expected data.
- World backup/restore round-trip succeeds.
- Overwrite protection and Trash/recovery work.
- File operations handle denied/revoked SAF access.

### Mods and resources
- Install, enable, disable, and uninstall representative supported examples.
- Disabled mods remain on disk.
- Compatibility/dependency/conflict warnings match available metadata.
- Invalid archives are rejected safely.
- Verify in gameplay, not just metadata parsing.

### Engine and updates
- Valid engine passes all mandatory tests.
- Invalid signature/checksum/ABI is rejected.
- Mandatory test failure blocks activation.
- Force path warns affected instances and requires confirmation before incompatible launch.
- Failed activation triggers rollback.
- Previous engine remains usable.
- Interrupted downloads never become active.
- APK updater uses Android installation flow and preserves data after successful upgrade.

### Offline
- Enable airplane mode and reopen/reboot launcher.
- Launch an installed offline-compatible instance.
- Manage local files, mods, worlds, controls, and backups.
- Confirm local actions do not wait on remote services.
- Online-only features explain connectivity requirements.

### Android lifecycle and stability
- Pause/resume, focus changes, screen lock/unlock, audio focus interruption, surface recreation, and low-memory/process-kill recovery.
- Confirm saved world data survives orderly exit and recoverable interruptions.

### HNMod compiler
- Submit valid and invalid samples.
- Verify queue limit and engine selection.
- Track progress in launcher without Discord.
- Verify partial success.
- Validate output and require install consent.

### Diagnostics and security
- Crash history and export.
- Secret redaction.
- Corrupt/missing file detection.
- Archive path traversal rejection.
- Update rollback and recovery.

Release gates:
- Build gate: build and static checks pass.
- Integration gate: core, JNI, runtime, storage, and update tests pass.
- Device gate: real-device gameplay, controls, lifecycle, and stability pass.
- Feature gate: a feature is called implemented only after its acceptance tests pass.

## 18. Proposed delivery phases (planning only)

1. Foundation audit: map current repository and identify existing implementations before designing replacements.
2. Core contracts: API, storage model, manifests, jobs, events.
3. Reliability foundation: storage safety, diagnostics, lifecycle, recovery.
4. Engine/update foundation: manifests, validation, activation, rollback, APK update.
5. Game management: instances, worlds, backups, Java, resources, servers.
6. Mods/compiler: HMod/HNMod/HShaders and cloud job integration.
7. UI completion: Material 3 screens connected to real core operations.
8. Acceptance/release: automated tests and physical-device validation.

Reorder phases after repository audit. Reuse existing working modules rather than replacing them merely to match this document.

## 19. Open questions for later review

- Exact supported Minecraft versions and upstream engine/nightly compatibility policy.
- Exact HMod/HNMod/HShaders format versions and authoritative compatibility metadata.
- Trusted signing-key and key-rotation process for component packages.
- Whether full backups include shared engine/runtime packages or reference them.
- Backup scheduling defaults and retention limits.
- Trash retention duration and storage-pressure behavior.
- Renderer backends actually available in selected engine build.
- Supported Android minimum/target SDK and device ABI matrix.
- Cloud compiler protocol, authentication, upload limits, retention, and privacy policy.
- Account/authentication modes supported by upstream engine.

---

**Document status:** Draft for review. No feature is considered implemented until repository inspection, implementation, automated tests, and device acceptance tests demonstrate it.
