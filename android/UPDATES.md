# Updates (same scheme as the PC launcher)

Horizon on PC ships one build, the **Nightly**: the GitHub release `nightly`, titled
`Nightly <date> · <commit>`, with older ones kept as `nightly-<date>`. Every release carries
`SHA256SUMS` and `SHA256SUMS.sig` (Ed25519, message `horizon release nightly\n` + the sums), and
launchers install nothing that no built-in key signed (`horizon-release-sign`).

The Android edition publishes exactly that, from its own repository, built from the newest upstream
commit that still takes our patch (`upstream-compatible.yml`, every 6 hours):

| Release | Contents |
|---|---|
| `nightly` | `libhorizon_core.so`, `libhorizon_android_game.so`, `engine-data.zip`, `Horizon-Android.apk`, `android-manifest.json`, `SHA256SUMS`, `SHA256SUMS.sig` |
| `nightly-<date>-<commit>` | the previous builds (the last 6 are kept) |

`android-manifest.json` is signed with everything else: `abi_level`, `build`, `version`
(`nightly-<date>-<commit>`), `upstream` (full commit) and the file names.

## In the app

* Checks at start and every 30 minutes (Settings → Update automatically).
* A build is downloaded in the background by the Rust core (`core/src/update.rs`): signature first,
  then each file against its signed SHA-256. Anything that fails leaves the active build untouched.
* Several builds stay installed (3). Settings → Installed builds switches between them, so a bad
  Nightly is one tap from a rollback; a rollback by hand is not undone by the next check.
* A build that does not live 20 seconds is marked bad and the previous one is used automatically.
* "Install app update" takes the APK from the same signed release and hands it to Android's installer.
* Only the runtime (libraries + engine data) changes without a new APK, and only while `abi_level`
  matches the app's (`android/ABI_LEVEL` = `Updater.ABI_LEVEL`). Bump both when the Kotlin <-> Rust
  JNI contract changes: older apps then offer the APK update instead.

## Keys

`android/release-keys.txt` lists the public keys the app trusts. The private key is the repository
secret `ANDROID_RELEASE_SIGNING_KEY` (base64 PKCS#8). Without the secret CI builds but publishes
nothing. To rotate: add the new public key to `release-keys.txt` (keep the old one for one
release), set the new secret, then remove the old key.
