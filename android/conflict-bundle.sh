#!/usr/bin/env bash
# After a failed `android/sync.sh <ref>`: packs everything needed to merge by hand (or to hand to
# Claude) into conflict-bundle.zip. For every file that conflicts:
#   <path>.base      upstream at the pinned commit (what our patch was made against)
#   <path>.ours      the same file with our Android changes (what works today)
#   <path>.upstream  the newest upstream version
#   <path>.merged    git's attempt, with <<<<<<< markers where it could not decide
set -euo pipefail
cd "$(dirname "$0")/.."
dir=.horizon-sync
out=conflict-bundle
rm -rf "$out" "$out.zip"; mkdir -p "$out"
pin=$(tr -d '[:space:]' < android/UPSTREAM_PIN)
newest=$(git -C "$dir" rev-parse HEAD)
git -C "$dir" diff --name-only --diff-filter=U > "$out/CONFLICTS.txt"
while IFS= read -r f; do
  [ -n "$f" ] || continue
  mkdir -p "$out/$(dirname "$f")"
  git -C "$dir" show ":1:$f" > "$out/$f.base"     2>/dev/null || true
  git -C "$dir" show ":3:$f" > "$out/$f.ours"     2>/dev/null || true
  git -C "$dir" show "$newest:$f" > "$out/$f.upstream" 2>/dev/null || true
  # Upstream keeps game/mod.rs as game.rs (we split it into a folder): fall back to that name.
  if [ ! -s "$out/$f.upstream" ] && [ "${f%/mod.rs}" != "$f" ]; then
    git -C "$dir" show "$newest:${f%/mod.rs}.rs" > "$out/$f.upstream" 2>/dev/null || true
  fi
  cp "$dir/$f" "$out/$f.merged" 2>/dev/null || true
done < "$out/CONFLICTS.txt"
cp android/android.patch "$out/android.patch"
printf 'pinned upstream: %s\nnewest upstream: %s\n' "$pin" "$newest" > "$out/INFO.txt"
(cd "$out" && zip -qr ../"$out.zip" .)
echo "wrote $out.zip ($(wc -l < "$out/CONFLICTS.txt") conflicting files)"
