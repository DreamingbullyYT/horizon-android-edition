#!/usr/bin/env bash
# Fetch the PRoot runtime and its shared libraries from the official Termux package repository.
# The Termux bootstrap itself is downloaded and SHA-256 verified by Horizon on first use.
set -euo pipefail

ASSETS="app/src/main/assets/proot"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$ASSETS/lib"

REPO="https://packages-cf.termux.dev/apt/termux-main"
curl --retry 5 --retry-all-errors -fsSL "$REPO/dists/stable/main/binary-aarch64/Packages" -o "$WORK/Packages"

python3 - "$WORK/Packages" "$WORK/packages.tsv" <<'PY'
import sys
from pathlib import Path
wanted = {"proot", "libtalloc", "libandroid-shmem"}
records = Path(sys.argv[1]).read_text().split("\n\n")
found = {}
for record in records:
    fields = {}
    for line in record.splitlines():
        if ": " in line and not line.startswith(" "):
            key, value = line.split(": ", 1)
            fields[key] = value
    name = fields.get("Package")
    if name in wanted:
        found[name] = fields.get("Filename", "")
missing = wanted - found.keys()
if missing:
    raise SystemExit("Missing official Termux packages: " + ", ".join(sorted(missing)))
Path(sys.argv[2]).write_text("".join(f"{name}\t{found[name]}\n" for name in sorted(wanted)))
PY

while IFS=$'\t' read -r package path; do
  test -n "$path"
  curl --retry 5 --retry-all-errors -fsSL "$REPO/$path" -o "$WORK/$package.deb"
  mkdir -p "$WORK/$package"
  dpkg-deb -x "$WORK/$package.deb" "$WORK/$package"
done < "$WORK/packages.tsv"

PROOT_BIN="$(find "$WORK/proot" -type f -path '*/usr/bin/proot' -print -quit)"
PROOT_LOADER="$(find "$WORK/proot" -type f -path '*/usr/libexec/proot/loader' -print -quit)"
test -s "$PROOT_BIN"
test -s "$PROOT_LOADER"
install -m 0755 "$PROOT_BIN" "$ASSETS/proot"
install -m 0755 "$PROOT_LOADER" "$ASSETS/loader"

for package in libtalloc libandroid-shmem; do
  find "$WORK/$package" -type f -path '*/usr/lib/*' -name '*.so*' -print0 |
    while IFS= read -r -d '' library; do
      install -m 0644 "$library" "$ASSETS/lib/$(basename "$library")"
    done
done

test -n "$(find "$ASSETS/lib" -type f -name '*.so*' -print -quit)"
readelf -d "$ASSETS/proot" | grep NEEDED || true
echo "Prepared official Termux PRoot runtime:"
ls -lh "$ASSETS" "$ASSETS/lib"
