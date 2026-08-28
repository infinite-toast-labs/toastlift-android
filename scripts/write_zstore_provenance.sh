#!/usr/bin/env bash
set -euo pipefail

artifact="${1:?Usage: $0 <apk> <source-commit> <appreveal-commit> <output-directory>}"
source_commit="${2:?}"
appreveal_commit="${3:?}"
output_directory="${4:?}"

[[ "$source_commit" =~ ^[0-9a-f]{40}$ ]] || { echo "Source commit must be a full lowercase SHA." >&2; exit 1; }
[[ "$appreveal_commit" =~ ^[0-9a-f]{40}$ ]] || { echo "AppReveal commit must be a full lowercase SHA." >&2; exit 1; }
scripts/verify_zstore_candidate.sh "$artifact"

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
build_tools_version="${ANDROID_BUILD_TOOLS:-36.0.0}"
badging="$($sdk_root/build-tools/$build_tools_version/aapt2 dump badging "$artifact")"
package_line="$(printf '%s\n' "$badging" | sed -n 's/^package: //p' | head -n 1)"
package_name="$(printf '%s\n' "$package_line" | sed -n "s/^name='\([^']*\)'.*/\1/p")"
version_code="$(printf '%s\n' "$package_line" | sed -n "s/^name='[^']*' versionCode='\([^']*\)'.*/\1/p")"
version_name="$(printf '%s\n' "$package_line" | sed -n "s/^name='[^']*' versionCode='[^']*' versionName='\([^']*\)'.*/\1/p")"
if command -v sha256sum >/dev/null 2>&1; then
  artifact_sha256="$(sha256sum "$artifact" | awk '{print $1}')"
else
  artifact_sha256="$(shasum -a 256 "$artifact" | awk '{print $1}')"
fi
artifact_size="$(wc -c < "$artifact" | tr -d '[:space:]')"

mkdir -p "$output_directory"
provenance_file="$output_directory/toastlift-zstore-provenance.json"
checksum_file="$output_directory/$(basename "$artifact").sha256"
printf '%s  %s\n' "$artifact_sha256" "$(basename "$artifact")" > "$checksum_file"

jq -n \
  --argjson schemaVersion 1 \
  --arg sourceRepository "infinite-toast-labs/toastlift-android" \
  --arg sourceCommit "$source_commit" \
  --arg appRevealRepository "infinite-toast-labs/appreveal-toastlift" \
  --arg appRevealCommit "$appreveal_commit" \
  --arg artifact "$(basename "$artifact")" \
  --arg sha256 "$artifact_sha256" \
  --argjson sizeBytes "$artifact_size" \
  --arg packageName "$package_name" \
  --argjson versionCode "$version_code" \
  --arg versionName "$version_name" \
  --arg builtAtUtc "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '{
    schemaVersion: $schemaVersion,
    source: {repository: $sourceRepository, commit: $sourceCommit},
    dependency: {appReveal: {repository: $appRevealRepository, commit: $appRevealCommit}},
    artifact: {
      file: $artifact,
      sha256: $sha256,
      sizeBytes: $sizeBytes,
      packageName: $packageName,
      versionCode: $versionCode,
      versionName: $versionName,
      buildType: "zstore",
      debuggable: true,
      signed: false
    },
    builtAtUtc: $builtAtUtc
  }' > "$provenance_file"

printf 'checksum_file=%s\nprovenance_file=%s\n' "$checksum_file" "$provenance_file"
