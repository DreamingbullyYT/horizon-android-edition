# Repository Audit — rustMC / Horizon Android Edition

> Audit date: 2026-11-09
> Audited ref: feature/material-you-cleanup
> Base: main
> Scope: static repository/tree and source inspection against RUSTMC_SPEC.md.
> This is an audit report, not an implementation commit. No source files were deleted or rewritten during this audit.

## 1. Executive summary

The repository already contains substantial launcher and runtime functionality. rustMC should extend and connect these systems, not replace them wholesale.

Existing foundations:
- Rust JNI bridge and Horizon launcher/core integration.
- Multiple Minecraft instance profiles (create/select/delete).
- Native game runtime and Android-specific engine overlay.
- Signed runtime/component update pipeline with staged downloads and automatic startup rollback.
- APK update checking and Android PackageInstaller handoff code.
- Local embedded Termux-compatible PRoot/Rust/Clang toolchain for HNMod builds.
- HMod content-package import, HShader import, resource/catalog browsing.
- Microsoft/offline account flows and skin preview.
- Zalith touch-control renderer/editor integration, including keyboard/input-related modules.
- Whole-game Minecraft resource backup/restore.

The current UI and data operations are materially narrower than the rustMC specification. Several spec sections describe future functionality, not existing functionality.

## 2. Repository inventory

At the audited branch head:
- 505 tracked tree entries.
- 176 Kotlin source files.
- 6 Java source files.
- 35 Rust source files.
- 4 GitHub Actions workflow files.
- Five included Zalith Android library modules: InputMap, LayerController, Guide, ColorPicker, ControlEditor.
- The upstream horizon/ checkout is created by android/sync.sh during CI and is ignored locally.

The current branch is nine commits ahead of main and zero commits behind it (including this audit and safe bytecode cleanup). The branch contains the cleanup/UI work, RUSTMC_SPEC.md and REPO_AUDIT.md; it has not been merged.

## 3. Feature-by-feature status

Status meanings: Present = concrete code and UI path exist; Partial = narrower implementation exists; Missing = no corresponding complete implementation was found in inspected paths; Needs runtime verification = source exists but behavior cannot be established from static inspection alone; Not applicable / reconsider = proposed feature may not fit the native Rust game architecture.

| rustMC area | Current repository evidence | Status |
|---|---|---|
| Launcher UI / Material 3 | Compose + Material 3; Android 12+ dynamic wallpaper color scheme; custom Horizon palette on older Android | Partial |
| Home | Home dashboard, selected instance, launch/setup status and shortcuts | Present, limited |
| Instances | Rust core list/create/select/delete; UI lists and selects profiles | Partial |
| Per-instance isolation | Native core exposes instance IDs/home/version; needs data-level tests across worlds/mods/config | Needs runtime verification |
| Clone/import/export/rename instances | No corresponding UI/API operations found in inspected Core/Native surface | Missing |
| Worlds | World list with name, mode, version, size and last-played metadata | Partial |
| World rename/delete/import/export/individual backup | No world-level mutation/backup API found in inspected Core/Native surface | Missing |
| HMod | Imports .hmod content packages with registry capability; rejects WASM/code packages in this build; imported mod is enabled by default | Partial |
| Mod enable/disable | Rust helper writes horizon-mods.txt, but inspected JNI/UI surface exposes import/remove rather than a toggle operation | Missing UI/API path |
| HNMod local compiler | Embedded Termux bootstrap, PRoot, Cargo/Rust/Clang installation, local build adapter, logs, output import | Present, device verification required |
| HNMod Cloud Compiler | No cloud job submission/status service found in inspected code | Missing |
| HShaders | .hshader validation and shader ZIP import/removal | Partial |
| Resource packs/catalog | Rust browse/catalog service and Discover UI exist; no dedicated Resources destination | Partial |
| Servers | List/add/remove saved servers | Partial |
| Server edit/ping/history | No matching operations in inspected Native/Core/UI surface | Missing |
| Accounts | Microsoft auth bridge, offline profiles, account selection/removal, keychain status | Present, auth configuration needs validation |
| Skin preview | AccountSkinPreview is used by Accounts UI | Present |
| Controls | Touch settings, separate touch editor activity, Zalith overlay/control renderer, layout profiles and keyboard-related source | Present, real-device validation required |
| Files | SAF folder import and file pickers for selected imports | Partial; no general-purpose explorer |
| Settings | Core-backed settings, appearance preferences, renderer selector, telemetry preferences | Partial |
| Diagnostics | Last game log/logcat display and HNMod live log | Partial |
| Crash history/export/redaction/repair | No complete crash-history and diagnostic-bundle workflow found | Missing |
| Java Runtime Manager | No Java runtime manager found | Reconsider: gameplay runtime is native Rust; Java may not be required |
| Android lifecycle | Separate :game process, lifecycle callbacks, foreground keep-alive service, session markers | Partial; cannot guarantee survival after OS process kill |
| Full backup | MinecraftBackup archives installed game version files and game-folder user files | Partial |
| World-only backup | No individual world backup operation | Missing |
| Graphics API | Auto/Vulkan/OpenGL selector; UI explicitly describes OpenGL as GLES 3.0+ and says GLES 2.0 is not the backend | Partial |
| Hardware/driver preflight | No full GPU/driver/backend capability probe found in inspected UI | Missing |
| Component/engine updater | Signed release manifest/checksum verification, ABI check, staged runtime package, active-version pointer, retention and startup rollback | Present, but no complete mandatory pre-activation test suite |
| APK updater | Check/download/PackageInstaller code exists; monitor currently announces a newer APK | Partial; download/install path is not wired to a clear user action in inspected UI |
| Strict offline | Offline profiles and local file/backup flows exist; not all local operations have been tested with network disabled | Needs runtime verification |

## 4. Existing architecture worth preserving

### 4.1 Native core and engine boundary
- core/src/lib.rs exposes JNI operations for instances, accounts, settings, versions, worlds, servers, mods, launch and telemetry.
- app/src/main/java/dev/horizon/launcher/core/Native.kt mirrors that native API.
- GameInput.kt provides the game input bridge, including native control, look, menu-touch and IME pathways.
- Android-specific engine code is isolated under android/overlay, then applied by the upstream sync/build process.
- Keep this boundary stable. Avoid moving Android UI concerns into the upstream game engine.

### 4.2 Zalith control system
The Gradle project includes all five Zalith modules. ControlEditor depends on LayerController, InputMap, Guide and ColorPicker; LayerController also depends on InputMap. The game Activity uses LayerController to draw the control overlay. The full editor is launched from the Controls UI.

These modules are not safe cleanup targets. Removing a module because its code is under third_party or because the app has a separate UI would break transitive dependencies or control behavior.

### 4.3 HNMod compiler
The local compiler is a real, integrated subsystem:
- First-use private Termux-compatible bootstrap.
- PRoot runtime and library dependency checks.
- Rust, Clang, Git, rsync and build dependencies installed inside app-private storage.
- Build script and HNMod core materialized from app assets.
- Build logs and output validation.
- Native game library imported as a local runtime version.

The current adapter passes --jobs 3 and --allow-version-mismatch to the build script after a user confirmation dialog. This is a local compiler workflow, not the cloud compiler described in rustMC. The build script has its own engine/mod compatibility resolver and fallback behavior; its exact policy should be reconciled with the rustMC decisions before changes.

### 4.4 Engine/component updater
The current updater already has:
- Signed GitHub Nightly metadata and Ed25519 verification in the Rust update module.
- SHA-256 validation for downloaded files.
- Staging directories and ABI-level checks.
- Versioned runtime directories and an active-version marker.
- Retention of several runtime versions.
- Startup guards and automatic rollback when the launcher/game process fails to survive its startup window.
- Protection for a runtime currently used by the separate game process.

This is a strong foundation for the global-engine decision. It is not yet equivalent to the proposed mandatory pre-activation test suite: current activation is based on package checks and ABI compatibility, followed by startup health/rollback.

### 4.5 APK updater
AppUpdater has code to check an APK offer, download the signed APK and submit it to Android's PackageInstaller. The current monitor checks for an offer and shows a status message. In the inspected UI path, a user-facing action that calls both download and install was not found. Treat APK updating as partially connected until this is wired and tested.

## 5. Important gaps and risks

### High priority — backup restore safety
MinecraftBackup.restore stages archive extraction and includes a Zip Slip path guard, which is good. Before this backup is treated as a reliable recovery system, address/test:
- No clear archive entry-count or total-uncompressed-size limit in the inspected restore loop.
- Existing installed version directories are deleted before the staged directory is moved into place; a failed rename can lose the previous copy.
- Userdata restore uses overwrite semantics.
- The previous Downloads backup is deleted before a replacement export has completed.
- Backup schema is currently minimal and does not describe a full launcher/instance backup.
- Restore is global game-resource restore, not world-level restore.

Do not remove or redesign this code before adding failure-injection and round-trip tests.

### High priority — update activation policy
The user's selected policy requires all mandatory tests before activation. Current runtime updater verifies signatures, checksums, ABI and required files, then activates and uses a startup guard for rollback. A dedicated mandatory test stage is not present in the inspected code. Design this before claiming the all policy is implemented.

### High priority — HNMod policy mismatch
The UI confirmation explicitly authorizes attempting a mixed-version mod set, and the adapter passes --allow-version-mismatch. The build script resolves engine/mixin compatibility and can fall back to a stable Android pin. Reconcile this with the confirmed rustMC policy (newest compatible engine; stop if none) and keep the local compiler behavior explicit. Do not silently carry the flag into a future cloud service.

### High priority — mod enable/disable
The Rust core has a helper that can edit enabled-mod state, but the inspected JNI/UI surface does not expose an enable/disable command. Import currently enables the mod; remove deletes it. Add a proper operation only after defining semantics for content mods and native mods separately.

### Medium priority — diagnostics/privacy
The last-game log viewer includes raw game log and optional logcat output. The inspected path copies raw text and does not demonstrate a redaction/export bundle. Add redaction before sharing diagnostics externally; do not treat the current log viewer as a complete diagnostics manager.

### Medium priority — Android lifecycle
A foreground service and a separate process help keep the game alive, but Android can still terminate it. The code itself acknowledges that a restarted service cannot restore a destroyed native session. Validate save durability and surface/audio recovery on actual devices; do not promise exact session restoration.

### Medium priority — renderer selection
The current UI has Auto, Vulkan and OpenGL. Its own explanatory text says OpenGL means GLES 3.0+ and not GLES 2.0. There is no evidence of an automatic GPU/driver preflight. Do not add GLES 2.0 to the selector unless the engine backend and supported hardware path are actually implemented.

### Medium priority — Java Runtime Manager
This project builds and runs a native Rust Horizon game library. The current Android build tasks compile Rust for Android; the embedded HNMod toolchain installs Rust/Clang, not a Java game runtime. Before implementing a Java Runtime Manager, confirm a real Java-dependent gameplay or mod requirement. It may be an unnecessary feature for this engine.

## 6. Cleanup candidates — do not delete blindly

| Path/item | Finding | Recommendation |
|---|---|---|
| android/__pycache__/*.pyc | Two tracked generated Python bytecode files were removed during this audit; .gitignore now excludes __pycache__ and *.py[cod] | Done on the audit branch |
| app/horizon-debug.jks | Tracked fixed debug keystore; Gradle explicitly uses it for stable debug APK signing | Do not delete casually. It is not a production signing key, but it is publicly exposed and must never be used for production releases. Changing it can break in-place updates of debug APKs signed with the existing key |
| app/src/main/assets/hnmod/android/** and app/src/main/assets/hnmod/hnmod-core/** | Intentional asset mirror of root build sources; Android workflow verifies selected files/directories with diff/cmp | Keep; generated compiler workspace is populated from these assets |
| android/android.patch, android/android-next.patch, overlay scripts and android/overlay/** | Required source overlay for syncing/building the upstream engine | Keep |
| app/third_party/zalith/** | Gradle modules and live control editor dependencies | Keep |
| app/src/main/java/dev/horizon/launcher/game/zalith/ZalithMenu.kt | Possible legacy Compose menu helper; no usage was identified in the primary game Activity during this pass, but a repository-wide symbol/reference proof is still required | Candidate for a focused reference audit, not yet approved for deletion |
| app/src/main/java/dev/horizon/launcher/ui/SkinPreview.kt | Used by Accounts UI through AccountSkinPreview | Keep |
| app/src/main/java/dev/horizon/launcher/game/controls/** | Used by the control integration and layout persistence | Keep |

The root .gitignore now ignores Python __pycache__/bytecode so generated files do not return.

## 7. CI and verification snapshot

At the time of this audit:
- HNMod core tests for the audited branch commit completed successfully.
- Android build for the audited branch commit was still running at the last status poll; do not report it as passed until the workflow concludes.
- The previous Android build on the parent cleanup commit completed successfully.
- The repository's Android workflow builds APK variants; the tree contains no Kotlin src/test or src/androidTest suite. Rust HNMod core tests exist, and the plugin-loader has Rust tests.

CI links:
- [Current Android build](https://github.com/DreamingbullyYT/horizon-android-edition/actions/runs/42643291098)
- [Current HNMod core tests](https://github.com/DreamingbullyYT/horizon-android-edition/actions/runs/42643291082)
- [Previous successful Android build](https://github.com/DreamingbullyYT/horizon-android-edition/actions/runs/42635173227)

## 8. Recommended next sequence

1. Safe cleanup completed: removed the two tracked Python bytecode files and added ignore rules. Do not touch mirrored compiler assets or Zalith modules.
2. Add tests before changing backup code: restore failure injection, archive limits, overwrite protection, and export replacement safety.
3. Expose mod enable/disable correctly: separate content-mod toggles from native HNMod runtime selection.
4. Wire APK updater UI: show version/build details, progress, signature result, and launch Android installer only after explicit confirmation.
5. Add engine pre-activation tests: mandatory test set, per-instance compatibility report, activation confirmation, and rollback.
6. Implement missing data operations: instance clone/rename/import/export and world backup/rename/delete/import/export.
7. Complete diagnostics and offline verification: redaction, export, crash history, and airplane-mode device tests.
8. Only then expand navigation/UI to the full rustMC destination list and connect every screen to real operations.

## 9. Audit conclusion

Do not perform a broad delete pass. The apparent duplication in the HNMod asset tree and the Zalith modules is intentional and build-critical. The first clearly safe cleanup target is tracked Python bytecode plus missing ignore rules. Most remaining work is feature completion and integration rather than removal of large unused modules.

This audit is based on static inspection. A final dead-code deletion decision still requires repository-wide reference analysis, Gradle dependency analysis, successful CI, and device regression testing.
