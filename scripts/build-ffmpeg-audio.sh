#!/usr/bin/env bash
# Builds the LGPL FFmpeg static libraries behind Media3's FFmpeg audio
# extension (:core:ffmpeg). Output lands in core/ffmpeg/src/main/jni/prebuilt/
# and is committed, so every build — including the local release build —
# ships the decoder without re-running this script.
#
# Scope: surround formats Android TV devices commonly cannot decode in
# hardware — AC-3, E-AC-3 (Atmos core), TrueHD/MLP, and DTS/DTS-HD core.
# Everything else stays on the platform MediaCodec decoders.
#
# Licensing: FFmpeg is configured --disable-gpl, so the audio decoders are
# LGPL-2.1+. The JNI glue is Media3's Apache-2.0 extension (see NOTICE in
# core/ffmpeg).
#
# Requirements: Android NDK 28.2.13676358 ($ANDROID_NDK_HOME or the SDK's
# ndk/ directory). Takes a few minutes on an M-series Mac.
set -euo pipefail

FFMPEG_REF="n7.1"
DECODERS=(ac3 eac3 truehd mlp dca)
ABIS=(armeabi-v7a arm64-v8a x86 x86_64)
API=26

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/core/ffmpeg/src/main/jni/prebuilt"
WORK="${WORK:-$ROOT/build/ffmpeg-audio}"
NDK="${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk/28.2.13676358}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/darwin-x86_64"
[ -d "$TOOLCHAIN" ] || { echo "NDK toolchain not found at $TOOLCHAIN" >&2; exit 1; }

mkdir -p "$WORK"
if [ ! -d "$WORK/ffmpeg" ]; then
  git clone --quiet --depth 1 --branch "$FFMPEG_REF" https://github.com/FFmpeg/FFmpeg.git "$WORK/ffmpeg"
fi

target() {
  case "$1" in
    armeabi-v7a) echo "arm armv7a-linux-androideabi" ;;
    arm64-v8a) echo "aarch64 aarch64-linux-android" ;;
    x86) echo "x86 i686-linux-android" ;;
    x86_64) echo "x86_64 x86_64-linux-android" ;;
  esac
}

decoder_flags=()
for decoder in "${DECODERS[@]}"; do decoder_flags+=("--enable-decoder=$decoder"); done

for abi in "${ABIS[@]}"; do
  read -r arch triple <<<"$(target "$abi")"
  extra=()
  # x86 builds serve the emulator only; skip hand-written asm (no nasm needed).
  case "$abi" in x86 | x86_64) extra+=(--disable-asm) ;; esac
  case "$abi" in armeabi-v7a) extra+=(--cpu=armv7-a --extra-cflags="-march=armv7-a -mfloat-abi=softfp") ;; esac
  prefix="$OUT/$abi"
  rm -rf "$prefix"
  make -C "$WORK/ffmpeg" distclean >/dev/null 2>&1 || true
  (cd "$WORK/ffmpeg" && ./configure \
    --enable-cross-compile --target-os=android --arch="$arch" \
    --cc="$TOOLCHAIN/bin/${triple}${API}-clang" \
    --cxx="$TOOLCHAIN/bin/${triple}${API}-clang++" \
    --nm="$TOOLCHAIN/bin/llvm-nm" --ar="$TOOLCHAIN/bin/llvm-ar" \
    --ranlib="$TOOLCHAIN/bin/llvm-ranlib" --strip="$TOOLCHAIN/bin/llvm-strip" \
    --prefix="$prefix" \
    --enable-static --disable-shared --disable-gpl --disable-debug \
    --disable-doc --disable-programs --disable-everything \
    --disable-avdevice --disable-avformat --disable-swscale --disable-postproc \
    --disable-avfilter --disable-symver --disable-network \
    --disable-v4l2-m2m --disable-vulkan --disable-mediacodec --disable-jni \
    --enable-swresample \
    "${decoder_flags[@]}" ${extra[@]+"${extra[@]}"} >/dev/null)
  make -C "$WORK/ffmpeg" -j"$(sysctl -n hw.ncpu)" install >/dev/null
  rm -rf "$prefix/share" "$prefix/lib/pkgconfig"
  # Headers are identical across ABIs except avconfig.h: keep one shared tree.
  mkdir -p "$prefix/avconfig/libavutil"
  mv "$prefix/include/libavutil/avconfig.h" "$prefix/avconfig/libavutil/"
  rm -rf "$OUT/include" && mv "$prefix/include" "$OUT/include"
  echo "built $abi: $(du -sh "$prefix" | cut -f1)"
done
