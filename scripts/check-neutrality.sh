#!/usr/bin/env bash
set -euo pipefail

root="${1:-.}"
forbidden="$(printf '%s%s' 'stre' 'mio')"

if git -C "$root" grep -Iin "$forbidden" -- ':!scripts/check-neutrality.sh'; then
  echo "A prohibited third-party brand token was found."
  exit 1
fi

echo "Neutrality check passed."

