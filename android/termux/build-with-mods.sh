#!/usr/bin/env bash
# Builds the Horizon game library (libhorizon_android_game.so) ON THE PHONE, in Termux, with native
# mods (.hnmod) applied the way the PC launcher does it: horizon-mixins rewrites the game's source,
# then cargo builds it. Then import the result in the app: Settings > Installed builds > Import local build.
#
#   bash android/termux/build-with-mods.sh [options] mod1.hnmod [mod2.hnmod | folder-of-hnmod ...]
#
#   --name NAME      folder name of the result (default: mods-<date>)
#   --jobs N         cargo jobs (default: by free RAM, about 1 per 2 GB)
#   --profile P      fast (default) | release (slower, a bit faster game; much more RAM)
#   --force          build even when a mod pins another Horizon commit than ours
#   --no-mods        build the plain game (checks that the toolchain works)
#
# First run: needs `pkg install rust clang git unzip rsync binutils` and 10+ GB free. The first build
# compiles every crate once (long, keep the phone charging, the script holds a wake lock);
# later builds with other mods only redo what the mods touch.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/../.." && pwd)
WORK=${HORIZON_BUILD_DIR:-$HOME/.horizon-build}
NAME=""; JOBS=""; PROFILE=fast; FORCE=0; PLAIN=0; INPUTS=()
while [ $# -gt 0 ]; do
  case $1 in
    --name) NAME=$2; shift 2;;
    --jobs) JOBS=$2; shift 2;;
    --profile) PROFILE=$2; shift 2;;
    --force) FORCE=1; shift;;
    --no-mods) PLAIN=1; shift;;
    -h|--help) sed -n 2,16p "$0"; exit 0;;
    *) INPUTS+=("$1"); shift;;
  esac
done
case $PROFILE in fast|release) ;; *) echo "profile must be fast or release" >&2; exit 2;; esac
[ "$PLAIN" = 1 ] || [ ${#INPUTS[@]} -gt 0 ] || { echo "Give at least one .hnmod (or --no-mods)." >&2; exit 2; }
NAME=${NAME:-mods-$(date +%Y%m%d-%H%M)}

say() { printf '\n== %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# ---- 0. tools ---------------------------------------------------------------------------------
for t in cargo rustc clang git unzip rsync sha256sum readelf; do
  command -v "$t" >/dev/null || die "missing '$t'. Run: pkg install rust clang git unzip rsync binutils"
done
case $(uname -m) in aarch64|arm64) ;; *) die "this script is for a 64-bit ARM phone (aarch64), not $(uname -m)";; esac
FREE_GB=$(df -k "$HOME" | awk 'NR==2{printf "%d", $4/1048576}')
[ "$FREE_GB" -ge 10 ] || die "only ${FREE_GB} GB free; the build needs about 10 GB."
if [ -z "$JOBS" ]; then
  MEM_GB=$(awk '/MemAvailable/{printf "%d", $2/1048576}' /proc/meminfo)
  JOBS=$(( MEM_GB / 2 )); [ "$JOBS" -ge 1 ] || JOBS=1
  CORES=$(nproc); [ "$JOBS" -le "$CORES" ] || JOBS=$CORES
fi
echo "cargo jobs: $JOBS (free RAM $(awk '/MemAvailable/{printf "%.1f", $2/1048576}' /proc/meminfo) GB)"
command -v termux-wake-lock >/dev/null && { termux-wake-lock; trap 'termux-wake-unlock || true' EXIT; }

# ---- 1. the engine: upstream at our pin + our Android patch -------------------------------------
PIN=$(tr -d '[:space:]' < "$REPO/android/UPSTREAM_PIN")
PATCH_SUM=$(sha256sum "$REPO/android/android.patch" | cut -c1-12)
SRC="$WORK/src/$PIN-$PATCH_SUM"
if [ ! -f "$SRC/.horizon-revision" ]; then
  say "Engine $PIN + Android patch (once)"
  rm -rf "$SRC.partial"; mkdir -p "$WORK/src"
  git clone -q --filter=blob:none https://github.com/horizon-rust/horizon.git "$SRC.partial"
  git -C "$SRC.partial" checkout -q "$PIN"
  git -C "$SRC.partial" config core.fileMode false
  git -C "$SRC.partial" apply --whitespace=nowarn "$REPO/android/android.patch" \
    || die "the Android patch does not apply to $PIN"
  printf '%s' "$PIN" > "$SRC.partial/.horizon-revision"
  rm -rf "$SRC.partial/.git"
  # horizon-mixins refuses symlinks (the PC launcher's source archive leaves them out too).
  find "$SRC.partial" -type l -delete
  mv "$SRC.partial" "$SRC"
fi

# ---- 2. the mods: unpack .hnmod bundles into a folder of bundles ---------------------------------
MODS="$WORK/mods"; IDS=()
rm -rf "$MODS" "$WORK/unpack"; mkdir -p "$MODS" "$WORK/unpack"
if [ "$PLAIN" = 0 ]; then
  say "Mods"
  FILES=()
  for i in "${INPUTS[@]}"; do
    if [ -d "$i" ]; then while IFS= read -r f; do FILES+=("$f"); done < <(find "$i" -maxdepth 1 -name '*.hnmod' | sort)
    else FILES+=("$i"); fi
  done
  [ ${#FILES[@]} -gt 0 ] || die "no .hnmod found in the given paths"
  n=0
  for f in "${FILES[@]}"; do
    [ -f "$f" ] || die "not a file: $f"
    n=$((n+1)); d="$WORK/unpack/$n"; mkdir -p "$d"
    unzip -q -o "$f" -d "$d" || die "$f is not a .hnmod (zip)"
    json="$d/horizon-native.json"
    [ -f "$json" ] || die "$f has no horizon-native.json"
    id=$(sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$json" | head -1)
    [ -n "$id" ] || die "$f: no mod id"
    if [ ! -d "$d/mixins/$id" ]; then
      echo "skipped $id: it has no mixin source (it only uses trait injections, which this script does not build yet)"
      continue
    fi
    cp -r "$d/mixins/$id" "$MODS/$id"
    toml="$MODS/$id/horizon-mixin.toml"
    pinned=$(sed -n 's/^horizon_revision[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$toml" | head -1)
    hooks=$(sed -n 's/.*"hooks"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$json" | head -1)
    if [ -n "$pinned" ] && [ "$pinned" != "$PIN" ]; then
      if [ -n "$hooks" ]; then
        # A mod on the hook points names a version, not a commit: any later Horizon builds it.
        sed -i "s/^\(horizon_revision[[:space:]]*=[[:space:]]*\)\"[^\"]*\"/\1\"$PIN\"/" "$toml"
      elif [ "$FORCE" = 1 ]; then
        echo "warning: $id was made for ${pinned:0:9}, building on ${PIN:0:9} (--force)"
        sed -i "s/^\(horizon_revision[[:space:]]*=[[:space:]]*\)\"[^\"]*\"/\1\"$PIN\"/" "$toml"
      else
        die "$id was made for Horizon ${pinned:0:9}, this app is on ${PIN:0:9}. Use --force to try anyway."
      fi
    fi
    IDS+=("$id"); echo "mod: $id"
  done
  [ ${#IDS[@]} -gt 0 ] || die "none of the given mods can be built by this script"
fi

# ---- 3. horizon-mixins (the same tool and commit the PC launcher uses) --------------------------
STAGE="$WORK/stage"
if [ ${#IDS[@]} -gt 0 ]; then
  REV=$(sed -n 's/^pub const MIXINS_REV: &str = "\([0-9a-f]*\)".*/\1/p' "$SRC/launcher/src/native_mods.rs" | head -1)
  [ -n "$REV" ] || die "could not read MIXINS_REV from the engine"
  TOOL="$WORK/tools/$REV"
  if [ ! -x "$TOOL/bin/horizon-mixins" ]; then
    say "Building horizon-mixins $REV (once)"
    cargo install --locked --git https://github.com/horizon-rust/horizon-mixins --rev "$REV" \
      horizon-mixins-cli --root "$TOOL" || die "horizon-mixins failed to build"
  fi
  say "Applying the mixins"
  rm -rf "$WORK/applied"
  "$TOOL/bin/horizon-mixins" apply --workspace "$SRC" --mods "$MODS" --out "$WORK/applied" \
    || die "horizon-mixins refused the mods (see the message above)"
  mkdir -p "$STAGE"
  # Only changed files are written, so cargo (which goes by file times) redoes only what the mods touch.
  rsync -rc --delete --exclude target "$WORK/applied/" "$STAGE/"
else
  mkdir -p "$STAGE"; rsync -rc --delete --exclude target "$SRC/" "$STAGE/"
fi

# ---- 4. compile ----------------------------------------------------------------------------------
say "Compiling (profile $PROFILE). The first time this takes long; keep the phone charging."
export CARGO_TARGET_DIR="$WORK/target"
export CARGO_BUILD_JOBS="$JOBS"
export CARGO_TERM_PROGRESS_WHEN=always CARGO_TERM_PROGRESS_WIDTH=100
nice -n 10 cargo build --locked --manifest-path "$STAGE/Cargo.toml" \
  -p horizon-android-game --profile "$PROFILE" 2>&1 | tee "$WORK/build.log" \
  || die "the build failed; the end of $WORK/build.log says why"
LIB="$CARGO_TARGET_DIR/$PROFILE/libhorizon_android_game.so"
[ -f "$LIB" ] || die "no library produced at $LIB"

# ---- 5. make sure the app can load it ------------------------------------------------------------
say "Checking the library"
command -v patchelf >/dev/null && patchelf --remove-rpath "$LIB" || true
SYSTEM='libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libvulkan.so|libEGL.so|libGLESv2.so|libGLESv3.so|libz.so|libOpenSLES.so|libaaudio.so|libjnigraphics.so|libnativewindow.so|libstdc\+\+.so|libc\+\+_shared.so'
BAD=$(readelf -d "$LIB" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p' | grep -Ev "^($SYSTEM)$" || true)
if [ -n "$BAD" ]; then
  echo "The library needs libraries the app does not have:"; echo "$BAD"
  die "libraries from Termux (not Android) are linked in. Send this list to the developer."
fi
if readelf -d "$LIB" | grep -q 'libc++_shared.so'; then
  echo "note: needs libc++_shared.so; the app must ship it (it does when built with the NDK's)." >&2
fi

OUTROOT="$HOME/storage/downloads"; [ -d "$OUTROOT" ] || OUTROOT="$HOME"
OUT="$OUTROOT/Horizon-builds/$NAME"; mkdir -p "$OUT"
cp "$LIB" "$OUT/libhorizon_android_game.so"
SUM=$(sha256sum "$OUT/libhorizon_android_game.so" | cut -d' ' -f1)
{
  printf '{\n  "name": "%s",\n  "upstream": "%s",\n  "android_patch": "%s",\n  "profile": "%s",\n' "$NAME" "$PIN" "$PATCH_SUM" "$PROFILE"
  printf '  "mods": [%s],\n  "sha256": "%s",\n  "built": "%s"\n}\n' \
    "$(printf '"%s",' "${IDS[@]:-}" | sed 's/,$//; s/^"",*$//')" "$SUM" "$(date -u +%FT%TZ)"
} > "$OUT/build.json"
say "Done: $OUT"
echo "sha256 ${SUM:0:16}…   mods: ${IDS[*]:-none}"
echo "In the app: Settings > Installed builds > Import local build > pick libhorizon_android_game.so"
[ "$OUTROOT" = "$HOME" ] && echo "(run termux-setup-storage once to get it into Downloads)"
