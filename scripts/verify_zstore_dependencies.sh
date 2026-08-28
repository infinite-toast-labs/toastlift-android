#!/usr/bin/env bash
set -euo pipefail

report="$(mktemp "${TMPDIR:-/tmp}/toastlift-zstore-dependencies.XXXXXX")"
trap 'rm -f "$report"' EXIT

./gradlew --no-daemon --console=plain \
  :app:dependencies --configuration zstoreRuntimeClasspath > "$report"

for forbidden in \
  'com\.appreveal:appreveal:' \
  'org\.nanohttpd:' \
  'com\.google\.code\.gson:gson:' \
  'androidx\.compose\.ui:ui-tooling:' \
  'androidx\.compose\.ui:ui-test-manifest:' \
  'com\.google\.firebase:' \
  'com\.android\.billingclient:'; do
  if grep -Eq "$forbidden" "$report"; then
    echo "Forbidden Zstore runtime dependency matched: $forbidden" >&2
    exit 1
  fi
done

grep -Fq 'com.appreveal:appreveal-noop:' "$report" || {
  echo "Zstore must resolve the AppReveal no-op boundary." >&2
  exit 1
}

echo "Verified Zstore runtime dependencies: no internal server, analytics, or billing SDKs"
