# How horizon/ is made

`horizon/` is NOT stored in this repo any more. CI builds it with `android/sync.sh`:

1. clone the original https://github.com/horizon-rust/horizon at the commit in `android/UPSTREAM_PIN`
2. apply `android/android.patch` (all our Android changes, ~30 files)

`.github/workflows/upstream-check.yml` tries the newest upstream every day. A clean apply opens a PR
that moves the pin; a conflict opens an issue and the build stays on the pinned commit.

Local use: `bash android/sync.sh` (needs git), then build as usual.
To change Horizon code: edit inside `horizon/` after syncing, then regenerate the patch:
`git -C horizon init` is not needed; use `git diff` against the pinned commit and save it to android/android.patch.
