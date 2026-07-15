#!/usr/bin/env bash
set -euo pipefail

directory="${TOASTLIFT_SIGNING_DIRECTORY:-}"
if [[ -z "$directory" ]]; then
  exit 0
fi
case "$directory" in
  "${RUNNER_TEMP:?}"/toastlift-signing.*) ;;
  *)
    echo "Refusing to remove signing material outside RUNNER_TEMP." >&2
    exit 1
    ;;
esac

find "$directory" -type f -exec shred --remove --zero {} + 2>/dev/null || find "$directory" -type f -delete
rmdir "$directory" 2>/dev/null || true
