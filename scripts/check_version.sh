#!/usr/bin/env bash
set -euo pipefail

version_file="${1:-version.txt}"

if [[ ! -f "$version_file" ]]; then
  echo "Missing version source: $version_file" >&2
  exit 1
fi

version="$(tr -d '[:space:]' < "$version_file")"
if [[ ! "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Expected a SemVer core version in $version_file (for example 1.2.3); found: $version" >&2
  exit 1
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
patch="${BASH_REMATCH[3]}"
version_code=$((major * 1000000 + minor * 1000 + patch))

if (( version_code < 1 || version_code > 2147483647 )); then
  echo "Android versionCode derived from $version is out of range: $version_code" >&2
  exit 1
fi

printf 'versionName=%s\nversionCode=%s\n' "$version" "$version_code"
