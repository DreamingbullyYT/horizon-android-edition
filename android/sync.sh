#!/usr/bin/env bash
# Rebuilds ./horizon from the original https://github.com/horizon-rust/horizon plus our Android
# changes: android/android.patch (small edits to upstream files) and android/overlay/ (files upstream does
# not have, copied in as they are). Usage: android/sync.sh [upstream-ref]   (default: android/UPSTREAM_PIN)
# Exits non-zero when the patch no longer applies cleanly (an upstream change collided with ours).
set -euo pipefail
cd "$(dirname "$0")/.."
ref=${1:-$(tr -d '[:space:]' < android/UPSTREAM_PIN)}
rm -rf .horizon-sync
git clone -q --filter=blob:none https://github.com/horizon-rust/horizon.git .horizon-sync
pin=$(tr -d '[:space:]' < android/UPSTREAM_PIN)
if [ "$ref" != "$pin" ]; then
  # 3-way merging needs the files the patch was made against: check them out once so git fetches them.
  git -C .horizon-sync checkout -q "$pin"
fi
git -C .horizon-sync checkout -q "$ref"
git -C .horizon-sync config core.fileMode false
if ! git -C .horizon-sync apply --whitespace=nowarn "$PWD/android/android.patch"; then
  echo "::error::Android patch conflicts with upstream $ref" >&2
  git -C .horizon-sync diff --name-only --diff-filter=U >&2 || true
  exit 1
fi
cp -a android/overlay/. .horizon-sync/
git -C .horizon-sync rev-parse HEAD > android/.upstream-built
rm -rf horizon .horizon-sync/.git
mv .horizon-sync horizon
echo "horizon/ = upstream $ref + Android patch + overlay"
