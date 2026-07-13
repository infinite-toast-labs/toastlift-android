#!/usr/bin/env bash
set -euo pipefail

artifact="${1:?Usage: $0 <artifact> <build-kind> <commit> <appreveal-commit>}"
build_kind="${2:?}"
commit_sha="${3:?}"
appreveal_commit="${4:?}"

if [[ ! -f "$artifact" ]]; then
  echo "Artifact does not exist: $artifact" >&2
  exit 1
fi

version="$(tr -d '[:space:]' < version.txt)"
checksum_file="${artifact}.sha256"
sha256sum "$artifact" > "$checksum_file"
provenance_directory="${RUNNER_TEMP:?}/toastlift-provenance"
mkdir -p "$provenance_directory"
provenance_file="$provenance_directory/${build_kind}-provenance.json"
artifact_sha256="$(cut -d ' ' -f1 < "$checksum_file")"

jq -n \
  --arg schemaVersion "1" \
  --arg buildKind "$build_kind" \
  --arg commit "$commit_sha" \
  --arg version "$version" \
  --arg artifact "$(basename "$artifact")" \
  --arg sha256 "$artifact_sha256" \
  --arg apprevealCommit "$appreveal_commit" \
  --arg builtAtUtc "$(date --utc +%Y-%m-%dT%H:%M:%SZ)" \
  '{schemaVersion: $schemaVersion, buildKind: $buildKind, commit: $commit, version: $version, artifact: $artifact, sha256: $sha256, apprevealCommit: $apprevealCommit, builtAtUtc: $builtAtUtc}' \
  > "$provenance_file"

echo "checksum_file=$checksum_file" >> "$GITHUB_OUTPUT"
echo "provenance_file=$provenance_file" >> "$GITHUB_OUTPUT"
