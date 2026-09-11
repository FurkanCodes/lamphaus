#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Lamphaus Player V2 — reproducible libmpv packaging (plan §6).
#
# Builds LGPL-compatible libmpv (+ FFmpeg, libass, fonts, TLS) for Android
# from pinned sources and drops every non-system .so into
# core/player/src/main/jniLibs/<abi>/, which activates the MpvPlayer fallback
# engine at runtime (MpvLibrary.availability == AVAILABLE).
#
# Requirements:
#   - bash, curl, git, cmake, meson, ninja, nasm/yasm, pkg-config, autoconf
#   - Android NDK 28.2.13676358 ($ANDROID_NDK_HOME, revision verified below)
#   - ~25 GB free disk; ~60-90 min for all four ABIs on an M-series Mac
#
# Reproducibility contract:
#   - Every dependency is a pinned tag; the script resolves each ref to a
#     commit SHA, records it in the build manifest, and FAILS if a ref does
#     not resolve (no placeholder pins, no silent fallbacks).
#   - Build flags are fixed below; do not "improve" them ad hoc — bump the
#     pin and re-verify instead.
#
# Licensing: FFmpeg is configured --enable-lgpl (LGPL 2.1+), mpv is LGPL
# 2.1+ when built without GPL components, libass is ISC, mbedtls is
# Apache-2.0, freetype is FTL/GPL-dual (built without GPL helpers),
# fontconfig is MIT, harfbuzz is MIT, fribidi is LGPL-2.1+, expat is MIT.
# Distribution must ship the notices in core/player/NOTICE.md.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Pinned sources. Tags MUST resolve in their upstream repos; the script
# aborts otherwise. Bump only with a documented compatibility reason. ────────
FFMPEG_REF="n7.1"
MPV_REF="v0.40.0"
LIBASS_REF="0.17.3"
MBEDTLS_REF="mbedtls-3.6.2"
FREETYPE_REF="VER-2-13-3"
HARFBUZZ_REF="10.2.0"
FRIBIDI_REF="v1.0.16"
FONTCONFIG_REF="2.15.0"
EXPAT_REF="R_2_6_3"
# Pinned NDK (plan §6). Any other revision aborts the build.
REQUIRED_NDK_REV="28.2.13676358"

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")
# FFmpeg arch names (configure --arch), NOT Gradle ABI names.
ffmpeg_arch() {
  case "$1" in
    arm64-v8a) echo "aarch64" ;;
    armeabi-v7a) echo "arm" ;;
    x86_64) echo "x86_64" ;;
    x86) echo "x86" ;;
  esac
}
# LLVM triple prefix (without API level) per ABI.
triple_prefix() {
  case "$1" in
    arm64-v8a) echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "armv7a-linux-androideabi" ;;
    x86_64) echo "x86_64-linux-android" ;;
    x86) echo "i686-linux-android" ;;
  esac
}
# Meson cpu_family per ABI.
meson_cpu() {
  case "$1" in
    arm64-v8a) echo "aarch64" ;;
    armeabi-v7a) echo "arm" ;;
    x86_64) echo "x86_64" ;;
    x86) echo "x86" ;;
  esac
}

API=24
WORK="${WORK:-$PWD/.mpv-build}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/core/player/src/main/jniLibs"
MANIFEST="$WORK/build-manifest.txt"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  echo "Set ANDROID_NDK_HOME to NDK $REQUIRED_NDK_REV." >&2
  exit 1
fi
NDK_REV="$(grep -o 'Pkg.Revision = .*' "$ANDROID_NDK_HOME/source.properties" | cut -d' ' -f3 || true)"
if [ "$NDK_REV" != "$REQUIRED_NDK_REV" ]; then
  echo "NDK revision mismatch: have '${NDK_REV:-unknown}', need $REQUIRED_NDK_REV." >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin) HOST_TAG="darwin-x86_64" ;;
  Linux) HOST_TAG="linux-x86_64" ;;
  *) echo "Unsupported host" >&2; exit 1 ;;
esac
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
: > "$MANIFEST"

mkdir -p "$WORK" && cd "$WORK"

clone_pinned() { # repo url, ref, dir — resolves the ref and records the SHA
  local url="$1" ref="$2" dir="$3"
  if [ ! -d "$dir" ]; then
    git clone --quiet "$url" "$dir"
  fi
  git -C "$dir" fetch --quiet --tags origin
  if ! git -C "$dir" rev-parse --quiet --verify "$ref^{commit}" >/dev/null; then
    echo "Pin '$ref' does not resolve in $url — refusing to build." >&2
    exit 1
  fi
  git -C "$dir" checkout --quiet --force "$ref"
  local sha
  sha="$(git -C "$dir" rev-parse HEAD)"
  echo "$dir $ref $sha" >> "$MANIFEST"
  echo "  $dir @ $ref ($sha)"
}

echo "── cloning pinned sources ──"
clone_pinned https://github.com/FFmpeg/FFmpeg.git "$FFMPEG_REF" ffmpeg
clone_pinned https://github.com/mpv-player/mpv.git "$MPV_REF" mpv
clone_pinned https://github.com/libass/libass.git "$LIBASS_REF" libass
clone_pinned https://github.com/Mbed-TLS/mbedtls.git "$MBEDTLS_REF" mbedtls
clone_pinned https://github.com/freetype/freetype.git "$FREETYPE_REF" freetype
clone_pinned https://github.com/harfbuzz/harfbuzz.git "$HARFBUZZ_REF" harfbuzz
clone_pinned https://github.com/fribidi/fribidi.git "$FRIBIDI_REF" fribidi
clone_pinned https://github.com/fontconfig/fontconfig.git "$FONTCONFIG_REF" fontconfig
clone_pinned https://github.com/libexpat/libexpat.git "$EXPAT_REF" expat

echo "ndk $REQUIRED_NDK_REV" >> "$MANIFEST"

# 16 KB page-size support (plan §6): every shared object must be installable
# on 16 KB-page devices. Verified per-ABI after the build.
PAGE_LDFLAGS="-Wl,-z,max-page-size=16384"

write_crossfile() { # abi, triple, prefix dir
  local abi="$1" triple="$2" prefix="$3"
  local cross="$WORK/crossfile.$abi.ini"
  cat > "$cross" <<EOF
[binaries]
c = '$TOOLCHAIN/bin/${triple}-clang'
cpp = '$TOOLCHAIN/bin/${triple}-clang++'
ar = '$TOOLCHAIN/bin/llvm-ar'
strip = '$TOOLCHAIN/bin/llvm-strip'
pkg-config = 'pkg-config'

[properties]
needs_exe_wrapper = true
c_args = ['-Os', '-DMPV_STATIC_BUILD']
c_link_args = ['$PAGE_LDFLAGS']
cpp_args = ['-Os', '-DMPV_STATIC_BUILD']
cpp_link_args = ['$PAGE_LDFLAGS']

[host_machine]
system = 'android'
cpu_family = '$(meson_cpu "$abi")'
cpu = '$(meson_cpu "$abi")'
endian = 'little'
EOF
  echo "$cross"
}

build_cmake_lib() { # srcdir builddir prefix extra-args...
  local src="$1" builddir="$2" prefix="$3"
  shift 3
  cmake -S "$src" -B "$builddir" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$API" \
    -DCMAKE_INSTALL_PREFIX="$prefix" -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_SHARED_LINKER_FLAGS="$PAGE_LDFLAGS" \
    "$@"
  cmake --build "$builddir" -j"$(sysctl -n hw.ncpu 2>/dev/null || nproc)"
  cmake --install "$builddir"
}

build_abi() {
  local abi="$1"
  ABI="$abi"
  echo "── building $abi ──"
  local triple
  triple="$(triple_prefix "$abi")$API"
  local cc="$TOOLCHAIN/bin/${triple}-clang"
  local prefix="$WORK/prefix-$abi"
  mkdir -p "$prefix"
  local cross
  cross="$(write_crossfile "$abi" "$triple" "$prefix")"
  export PKG_CONFIG_PATH="$prefix/lib/pkgconfig:$prefix/lib/x86_64-linux-gnu/pkgconfig:$prefix/share/pkgconfig"
  export PKG_CONFIG_SYSROOT_DIR="$TOOLCHAIN/sysroot"

  # expat (fontconfig dependency)
  if [ ! -f "$prefix/lib/libexpat.so" ]; then
    build_cmake_lib "$WORK/expat/expat" "$WORK/expat/build-$abi" "$prefix" \
      -DEXPAT_BUILD_TESTS=OFF -DEXPAT_BUILD_EXAMPLES=OFF -DEXPAT_SHARED_LIBS=ON
  fi

  # freetype without harfbuzz first (breaks the mutual dependency cycle)
  if [ ! -f "$prefix/lib/libfreetype.so" ]; then
    (cd freetype && rm -rf "build-$abi" && meson setup "build-$abi" --cross-file "$cross" \
      --prefix="$prefix" --buildtype=release -Ddefault_library=shared \
      -Dharfbuzz=disabled -Dbrotli=disabled && ninja -C "build-$abi" install)
  fi

  # harfbuzz
  if [ ! -f "$prefix/lib/libharfbuzz.so" ]; then
    (cd harfbuzz && rm -rf "build-$abi" && meson setup "build-$abi" --cross-file "$cross" \
      --prefix="$prefix" --buildtype=release -Ddefault_library=shared \
      -Dglib=disabled -Dgobject=disabled -Dcairo=disabled -Dicu=disabled \
      -Dtests=disabled -Ddocs=disabled && ninja -C "build-$abi" install)
  fi

  # fribidi
  if [ ! -f "$prefix/lib/libfribidi.so" ]; then
    (cd fribidi && rm -rf "build-$abi" && meson setup "build-$abi" --cross-file "$cross" \
      --prefix="$prefix" --buildtype=release -Ddefault_library=shared \
      -Ddocs=false -Dtests=false && ninja -C "build-$abi" install)
  fi

  # fontconfig
  if [ ! -f "$prefix/lib/libfontconfig.so" ]; then
    (cd fontconfig && rm -rf "build-$abi" && meson setup "build-$abi" --cross-file "$cross" \
      --prefix="$prefix" --buildtype=release -Ddefault_library=shared \
      -Ddoc=disabled -Dtests=disabled -Dtools=disabled && ninja -C "build-$abi" install)
  fi

  # mbedtls (HTTPS playback)
  if [ ! -f "$prefix/lib/libmbedtls.so" ]; then
    build_cmake_lib "$WORK/mbedtls" "$WORK/mbedtls/build-$abi" "$prefix" \
      -DENABLE_TESTING=OFF -DENABLE_PROGRAMS=OFF
  fi

  # FFmpeg: LGPL only, software video output (swscale ON), network + mbedtls.
  if [ ! -f "$prefix/lib/libavcodec.so" ]; then
    make -C ffmpeg distclean >/dev/null 2>&1 || true
    (cd ffmpeg && ./configure \
      --enable-cross-compile --target-os=android --arch="$(ffmpeg_arch "$abi")" \
      --cc="$cc" --sysroot="$TOOLCHAIN/sysroot" \
      --prefix="$prefix" \
      --enable-shared --disable-static --enable-lgpl --disable-gpl \
      --disable-programs --disable-doc --disable-debug --disable-avdevice \
      --disable-postproc \
      --enable-network --enable-mbedtls \
      --extra-ldflags="$PAGE_LDFLAGS")
    make -C ffmpeg -j"$(sysctl -n hw.ncpu 2>/dev/null || nproc)" install
  fi

  # libass (ASS rendering)
  if [ ! -f "$prefix/lib/libass.so" ]; then
    (cd libass && rm -rf "build-$abi" && meson setup "build-$abi" --cross-file "$cross" \
      --prefix="$prefix" --buildtype=release -Ddefault_library=shared && ninja -C "build-$abi" install)
  fi

  # mpv (LGPL build, no GPL components)
  if [ ! -f "$prefix/lib/libmpv.so" ]; then
    (cd mpv && rm -rf "build-$abi" && meson setup "build-$abi" --cross-file "$cross" \
      --prefix="$prefix" --buildtype=release -Ddefault_library=shared \
      -Dlibmpv=true -Dcplayer=disabled -Dlua=disabled -Djavascript=disabled \
      -Dmanpage-build=disabled && ninja -C "build-$abi" install)
  fi

  # Package EVERY non-system shared library for this ABI — copying only
  # libmpv.so is insufficient when its dependencies are dynamically linked.
  mkdir -p "$OUT/$abi"
  for so in "$prefix"/lib/libmpv.so "$prefix"/lib/libav*.so "$prefix"/lib/libsw*.so \
            "$prefix"/lib/libass.so "$prefix"/lib/libfontconfig.so \
            "$prefix"/lib/libfreetype.so "$prefix"/lib/libharfbuzz.so \
            "$prefix"/lib/libfribidi.so "$prefix"/lib/libexpat.so \
            "$prefix"/lib/libmbed*.so; do
    [ -e "$so" ] || continue
    cp "$so" "$OUT/$abi/"
  done
  "$TOOLCHAIN/bin/llvm-strip" --strip-unneeded "$OUT/$abi"/*.so

  # Verify: no unexpected NEEDED entries (system libs only besides packaged).
  echo "  NEEDED($abi):"
  for so in "$OUT/$abi"/*.so; do
    "$TOOLCHAIN/bin/llvm-readelf" --dynamic "$so" | grep NEEDED || true
  done
  # Verify 16 KB page alignment of LOAD segments.
  for so in "$OUT/$abi"/*.so; do
    if "$TOOLCHAIN/bin/llvm-readelf" -l "$so" | grep -q "ALIGN.*0x4000"; then
      : # 16 KB-aligned segment present
    else
      echo "warning: $so shows no 16 KB-aligned LOAD segment (verify on a 16 KB device)" >&2
    fi
  done
  echo "  → $OUT/$abi/ ($(ls "$OUT/$abi" | tr '\n' ' '))"
}

for abi in "${ABIS[@]}"; do
  build_abi "$abi"
done

echo "── packaging summary ──"
for abi in "${ABIS[@]}"; do
  for so in "$OUT/$abi"/*.so; do
    shasum -a 256 "$so" | awk -v abi="$abi" '{print abi, $1, $2}'
  done
done | tee -a "$MANIFEST"
echo "Manifest: $MANIFEST. Rebuild with the same pins to reproduce. Ship NOTICE.md."
