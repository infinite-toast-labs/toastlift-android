#!/usr/bin/env bash
set -euo pipefail

bundle="${1:?Usage: $0 <release.aab>}"
if [[ ! -f "$bundle" ]]; then
  echo "Release bundle not found at $bundle" >&2
  exit 1
fi

signature_entries="$(unzip -Z1 "$bundle")"
if ! grep -Eq '^META-INF/[^/]+\.(RSA|DSA|EC)$' <<< "$signature_entries"; then
  echo "Play bundle does not contain a JAR signature block." >&2
  echo "Configure all four TOASTLIFT_PLAY_UPLOAD_* signing values, then rebuild." >&2
  exit 1
fi

status=0
verification_output="$(jarsigner -verify -certs "$bundle" 2>&1)" || status=$?
if [[ $status -ne 0 \
  || "$verification_output" != *"jar verified."* \
  || "$verification_output" == *"jar is unsigned"* \
  || "$verification_output" == *"unsigned entries"* ]]; then
  echo "Play bundle is unsigned, partially unsigned, or has an invalid signature." >&2
  echo "Configure all four TOASTLIFT_PLAY_UPLOAD_* signing values, then rebuild." >&2
  exit 1
fi

echo "Signed Play bundle verified: $bundle"
