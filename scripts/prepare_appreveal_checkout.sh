#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
source "$repository_root/ci/android-ci.env"

configured_android_directory="${APPREVEAL_COMPOSITE_BUILD:-../appreveal-toastlift/Android}"
if [[ "$configured_android_directory" == /* ]]; then
  android_directory="$configured_android_directory"
else
  android_directory="$repository_root/$configured_android_directory"
fi
checkout_directory="$(dirname "$android_directory")"

if [[ -e "$checkout_directory" && ! -d "$checkout_directory/.git" ]]; then
  echo "Refusing to replace non-Git path: $checkout_directory" >&2
  exit 1
fi

if [[ ! -d "$checkout_directory/.git" ]]; then
  git clone "https://github.com/$APPREVEAL_REPOSITORY.git" "$checkout_directory"
fi

if [[ -n "$(git -C "$checkout_directory" status --short)" ]]; then
  echo "Refusing to change dirty AppReveal checkout: $checkout_directory" >&2
  exit 1
fi

git -C "$checkout_directory" fetch --no-tags origin "$APPREVEAL_COMMIT"
git -C "$checkout_directory" checkout --detach "$APPREVEAL_COMMIT"

if [[ ! -d "$android_directory" ]]; then
  echo "Pinned AppReveal checkout has no Android directory at $android_directory" >&2
  exit 1
fi

echo "AppReveal ready at $android_directory ($APPREVEAL_COMMIT)."
