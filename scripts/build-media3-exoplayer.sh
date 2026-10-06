#!/usr/bin/env bash
# Builds Lamphaus's Media3 ExoPlayer module: upstream androidx/media at the
# pinned tag with the patches in third_party/media3/patches applied. The AAR
# and its POM land in the in-repo Maven folder third_party/maven and are
# committed, so every build — including the local release build — uses them
# without re-running this script. Gradle swaps androidx.media3:media3-exoplayer
# for this artifact everywhere (see the root build.gradle.kts).
#
# Patches: 0001 keeps buffered samples in off-heap memory when "Native memory
# buffer" is on (PLY-NET-01). Every other Media3 module stays stock.
#
# Licensing: Media3 and the patches are Apache-2.0 (see core/player/NOTICE.md).
#
# Requirements: JDK 17+, the Android SDK (sdk.dir or ANDROID_HOME) with NDK
# 28.2.13676358 and CMake 3.22.1. Takes a couple of minutes.
#
# When the Media3 version in gradle/libs.versions.toml changes, bump
# MEDIA3_TAG and FORK_VERSION here and in the root build.gradle.kts, rebuild,
# and re-run the player tests.
set -euo pipefail

MEDIA3_TAG="1.11.0"
FORK_VERSION="1.11.0-lamphaus.1"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK:-$ROOT/build/media3-fork}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
OUT="$ROOT/third_party/maven/com/lamphaus/media3/media3-exoplayer/$FORK_VERSION"

rm -rf "$WORK/media"
mkdir -p "$WORK"
git clone --quiet --depth 1 --branch "$MEDIA3_TAG" https://github.com/androidx/media.git "$WORK/media"
cd "$WORK/media"
for patch in "$ROOT"/third_party/media3/patches/*.patch; do
  git apply --whitespace=nowarn "$patch"
done
echo "sdk.dir=$SDK" > local.properties
./gradlew :lib-exoplayer:assembleRelease --max-workers=2 -q

mkdir -p "$OUT"
cp libraries/exoplayer/buildout/outputs/aar/lib-exoplayer-release.aar "$OUT/media3-exoplayer-$FORK_VERSION.aar"
# Upstream's POM with our coordinates: the first groupId and version are the
# module's own; its dependencies stay on stock Media3.
curl -fsSL "https://dl.google.com/android/maven2/androidx/media3/media3-exoplayer/$MEDIA3_TAG/media3-exoplayer-$MEDIA3_TAG.pom" |
  grep -v 'published-with-gradle-metadata' |
  perl -0pe "s|<groupId>androidx.media3</groupId>|<groupId>com.lamphaus.media3</groupId>|;
             s|<version>$MEDIA3_TAG</version>|<version>$FORK_VERSION</version>|;
             s|<name>Media3 ExoPlayer module</name>|<name>Media3 ExoPlayer module (Lamphaus patches)</name>|" \
  > "$OUT/media3-exoplayer-$FORK_VERSION.pom"

echo "Built $OUT"
shasum -a 256 "$OUT"/*
