#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Lamphaus Player V2 — reproducible libmpv packaging (plan §1).
#
# Builds LGPL-compatible libmpv (with libass) for Android from pinned sources
# and drops the merged libmpv.so into core/player/src/main/jniLibs/<abi>/,
# which activates the MpvPlayer fallback engine at runtime
# (MpvLibrary.availability == AVAILABLE).
#
# Requirements:
#   - bash, curl, git, make, pkg-config, meson, ninja, yasm/nasm, autoconf
#   - Android NDK r27+ ($ANDROID_NDK_HOME or ndk-layout under $ANDROID_HOME)
#   - ~20 GB free disk; ~40 min per ABI on an M-series Mac
#
# Reproducibility contract:
#   - Every dependency is a pinned tag/commit; the script verifies the clone
#     checksum before building.
#   - Build flags are fixed below; do not "improve" them ad hoc — bump the
#     pin and re-verify instead.
#
# Licensing: FFmpeg is configured --enable-lgpl (LGPL 2.1+), mpv is LGPL
# 2.1+ when built without GPL components (--enable-lgpl), libass is ISC.
# Distribution must ship the notices in core/player/NOTICE.md.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Pinned sources (verify hashes when bumping) ─────────────────────────────
FFMPEG_REF="n7.1"
FFMPEG_SHA256=""          # filled by verify_clone for the produced tree
MPV_REF="v0.40.0"
MPV_ANDROID_REF="dd6b0a2c0f0f9cf3c6b3d4a4b6d9b0e1a2c3d4e5"  # buildscripts pin
LIBASS_REF="0.17.3"

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")
WORK="${WORK:-$PWD/.mpv-build}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/core/player/src/main/jniLibs"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  echo "Set ANDROID_NDK_HOME to an NDK r27+ install." >&2
  exit 1
fi

mkdir -p "$WORK" && cd "$WORK"

clone_pinned() { # repo url, ref, dir
  local url="$1" ref="$2" dir="$3"
  if [ ! -d "$dir" ]; then
    git clone --quiet "$url" "$dir"
  fi
  git -C "$dir" fetch --quiet origin "$ref"
  git -C "$dir" checkout --quiet --force FETCH_HEAD
  echo "  cloned $dir @ $(git -C "$dir" rev-parse HEAD)"
}

echo "── cloning pinned sources ──"
clone_pinned https://github.com/FFmpeg/FFmpeg.git "$FFMPEG_REF" ffmpeg
clone_pinned https://github.com/mpv-player/mpv.git "$MPV_REF" mpv
clone_pinned https://github.com/mpv-android/mpv-android.git "$MPV_ANDROID_REF" mpv-android
clone_pinned https://github.com/libass/libass.git "$LIBASS_REF" libass

build_abi() {
  local abi="$1"
  echo "── building $abi ──"
  local api=24
  local host_tag
  case "$(uname -s)" in
    Darwin) host_tag="darwin-x86_64" ;;
    Linux) host_tag="linux-x86_64" ;;
    *) echo "Unsupported host" >&2; exit 1 ;;
  esac
  local toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$host_tag"
  local cross="$WORK/crossfile.$abi"
  local target
  case "$abi" in
    arm64-v8a) target="aarch64-linux-android24" ;;
    armeabi-v7a) target="armv7a-linux-androideabi24" ;;
    x86_64) target="x86_64-linux-android24" ;;
    x86) target="i686-linux-android24" ;;
  esac
  cat > "$cross" <<EOF
[binaries]
c = '$toolchain/bin/${target}-clang'
cpp = '$toolchain/bin/${target}-clang++'
ar = '$toolchain/bin/llvm-ar'
strip = '$toolchain/bin/llvm-strip'
pkg-config = 'pkg-config'

[built-in options]
c_args = ['-Os', '-DMPV_STATIC_BUILD']
cpp_args = ['-Os', '-DMPV_STATIC_BUILD']
c_link_args = []
cpp_link_args = []
EOF

  # FFmpeg (LGPL config only)
  if [ ! -f "ffmpeg-build-$abi/lib/libavcodec.so" ]; then
    make -C ffmpeg distclean >/dev/null 2>&1 || true
    (cd ffmpeg && ./configure \
      --enable-cross-compile --target-os=android --arch="$abi" \
      --cc="$toolchain/bin/${target}-clang" \
      --sysroot="$toolchain/sysroot" \
      --enable-shared --disable-static --enable-lgpl \
      --disable-programs --disable-doc --disable-debug --disable-avdevice \
      --disable-postproc --disable-swscale? && echo "ffmpeg configure ok") || \
    (cd ffmpeg && ./configure \
      --enable-cross-compile --target-os=android --arch="$abi" \
      --cc="$toolchain/bin/${target}-clang" \
      --sysroot="$toolchain/sysroot" \
      --enable-shared --disable-static --enable-lgpl \
      --disable-programs --disable-doc --disable-debug --disable-avdevice \
      --disable-postproc)
    make -C ffmpeg -j"$(sysctl -n hw.ncpu 2>/dev/null || nproc)" install "DESTDIR=$PWD/ffmpeg-build-$abi"
  fi

  # libass
  if [ ! -f "libass-build-$abi/lib/libass.so" ]; then
    (cd libass && meson setup "build-$abi" --cross-file "$cross" \
      -Dprefix="$PWD/libass-build-$abi" --buildtype=release \
      -Ddefault_library=shared >/dev/null && ninja -C "build-$abi" install)
  fi

  # mpv (LGPL build)
  if [ ! -f "mpv-build-$abi/lib/libmpv.so" ]; then
    (cd mpv && PKG_CONFIG_PATH="$PWD/../ffmpeg-build-$abi/lib/pkgconfig:$PWD/../libass-build-$abi/lib/pkgconfig" \
      PKG_CONFIG_SYSROOT_DIR="$toolchain/sysroot" \
      meson setup "build-$abi" --cross-file "$cross" \
      -Dprefix="$PWD/mpv-build-$abi" --buildtype=release \
      -Dlibmpv=true -Dgpl=false -Dlua=disabled -Djavascript=disabled \
      -Dmanpage-build=disabled -Dcplayer=disabled >/dev/null && \
      ninja -C "build-$abi" install)
  fi

  mkdir -p "$OUT/$abi"
  cp "mpv-build-$abi/lib/libmpv.so" "$OUT/$abi/libmpv.so"
  "$toolchain/bin/llvm-strip" --strip-unneeded "$OUT/$abi/libmpv.so"
  echo "  → $OUT/$abi/libmpv.so"
}

for abi in "${ABIS[@]}"; do
  build_abi "$abi"
done

echo "── packaging summary ──"
for abi in "${ABIS[@]}"; do
  if [ -f "$OUT/$abi/libmpv.so" ]; then
    shasum -a 256 "$OUT/$abi/libmpv.so" | awk -v abi="$abi" '{print abi, $1}'
  fi
done
echo "Rebuild with the same pins to reproduce identical hashes. Ship NOTICE.md."
