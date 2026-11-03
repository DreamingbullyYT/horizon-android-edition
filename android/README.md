# How horizon/ is made

`horizon/` is NOT stored in this repo any more. CI builds it with `android/sync.sh`:

1. clone the original https://github.com/horizon-rust/horizon at the commit in `android/UPSTREAM_PIN`
2. apply `android/android.patch` (all our Android changes, ~30 files)

`.github/workflows/upstream-compatible.yml` builds the newest upstream Nightlies every 6 hours and moves the
pin to the first one that builds. How the app follows them: see `UPDATES.md`.

Local use: `bash android/sync.sh` (needs git), then build as usual.
To change Horizon code: edit inside `horizon/` after syncing, then regenerate the patch:
`git -C horizon init` is not needed; use `git diff` against the pinned commit and save it to android/android.patch.
