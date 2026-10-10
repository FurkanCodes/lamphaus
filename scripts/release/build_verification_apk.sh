#!/usr/bin/env bash
# Builds the APK that proves ownership of the production signing key to the
# Android Developer Console (developer verification).
#
# The console gives a snippet; it lives, git-ignored, in
# app/src/main/assets/adi-registration.properties. This script signs a release
# build with the production key exactly as `lamphaus_release.py prepare` does
# (key location from release/signing.properties, password from macOS
# Keychain), checks the signer and the snippet, and leaves the APK in
# dist/verification/. Upload it in the console only: it is not a release and
# is never published (REL-05, REL-06).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PROPERTIES="release/signing.properties"
SNIPPET="app/src/main/assets/adi-registration.properties"
[[ -f "$PROPERTIES" ]] || { echo "Missing $PROPERTIES (see release/signing.properties.example)"; exit 1; }
[[ -s "$SNIPPET" ]] || { echo "Missing $SNIPPET: paste the console's snippet into it first"; exit 1; }

property() { sed -n "s/^$1=//p" "$PROPERTIES" | head -1; }
STORE="$(property production.storeFile)"
STORE="${STORE/#\~/$HOME}"
ALIAS="$(property production.keyAlias)"
SERVICE="$(property production.keychainService)"
ACCOUNT="$(property production.keychainAccount)"
EXPECTED="$(property production.signerFingerprint | tr '[:lower:]' '[:upper:]')"
[[ -f "$STORE" && -n "$ALIAS" && -n "$SERVICE" && -n "$ACCOUNT" && -n "$EXPECTED" ]] || {
  echo "Production signing settings are incomplete in $PROPERTIES"; exit 1;
}

PASSWORD="$(security find-generic-password -s "$SERVICE" -a "$ACCOUNT" -w)"
rm -f app/build/outputs/apk/release/*.apk
LAMPHAUS_RELEASE_STORE_FILE="$STORE" \
LAMPHAUS_RELEASE_KEY_ALIAS="$ALIAS" \
LAMPHAUS_RELEASE_STORE_PASSWORD="$PASSWORD" \
LAMPHAUS_RELEASE_KEY_PASSWORD="$PASSWORD" \
  ./gradlew :app:assembleRelease --max-workers=2
unset PASSWORD
./gradlew --stop >/dev/null

APK="app/build/outputs/apk/release/app-release.apk"
APKSIGNER="$(command -v apksigner || ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
ACTUAL="$("$APKSIGNER" verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr -d ':' | tr '[:lower:]' '[:upper:]')"
[[ "$ACTUAL" == "${EXPECTED//:/}" ]] || { echo "Signer mismatch: got $ACTUAL"; exit 1; }
# Compare the packaged snippet itself; piping a listing into `grep -q` fails under pipefail.
[[ "$(unzip -p "$APK" assets/adi-registration.properties)" == "$(cat "$SNIPPET")" ]] || { echo "Snippet missing from the APK"; exit 1; }

mkdir -p dist/verification
cp "$APK" dist/verification/lamphaus-developer-verification.apk
echo "Ready to upload: dist/verification/lamphaus-developer-verification.apk"
