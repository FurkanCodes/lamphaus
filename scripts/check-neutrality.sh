#!/usr/bin/env bash
set -euo pipefail

root="${1:-.}"
forbidden="$(printf '%s%s' 'stre' 'mio')"

matches="$(git -C "$root" grep -Iin "$forbidden" -- ':!scripts/check-neutrality.sh' || true)"
unexpected="$(
  while IFS= read -r line; do
    if [[ "$line" == app/src/main/AndroidManifest.xml:* ]] &&
      [[ "$line" == *"android:scheme=\"$forbidden\""* ]]; then
      continue
    fi
    printf '%s\n' "$line"
  done <<< "$matches"
)"

if [[ -n "$unexpected" ]]; then
  printf '%s\n' "$unexpected"
  echo "A prohibited third-party brand token was found."
  exit 1
fi

echo "Neutrality check passed."
