#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="$repository_root/scripts/verify_play_bundle_signature.sh"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/toastlift-play-verifier.XXXXXX")"

cleanup() {
  find "$temporary_directory" -type f -delete 2>/dev/null || true
  find "$temporary_directory" -depth -type d -exec rmdir {} \; 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p "$temporary_directory/payload"
printf 'ToastLift verifier fixture\n' > "$temporary_directory/payload/base.txt"

unsigned_bundle="$temporary_directory/unsigned.aab"
signed_bundle="$temporary_directory/signed.aab"
partially_unsigned_bundle="$temporary_directory/partially-unsigned.aab"
keystore="$temporary_directory/test-signing.p12"
password=toastlift-verifier-fixture

(
  cd "$temporary_directory/payload"
  zip -q "$unsigned_bundle" base.txt
)
cp "$unsigned_bundle" "$signed_bundle"

keytool -genkeypair \
  -alias verifier-fixture \
  -keystore "$keystore" \
  -storetype PKCS12 \
  -storepass "$password" \
  -keypass "$password" \
  -dname 'CN=ToastLift Release Verifier Test' \
  -keyalg RSA \
  -validity 1 >/dev/null 2>&1

jarsigner \
  -keystore "$keystore" \
  -storepass "$password" \
  -keypass "$password" \
  "$signed_bundle" \
  verifier-fixture >/dev/null 2>&1

"$verifier" "$signed_bundle" >/dev/null

if "$verifier" "$unsigned_bundle" >/dev/null 2>&1; then
  echo "Unsigned bundle unexpectedly passed verification." >&2
  exit 1
fi

cp "$signed_bundle" "$partially_unsigned_bundle"
printf 'This entry was added after signing.\n' > "$temporary_directory/payload/unsigned.txt"
(
  cd "$temporary_directory/payload"
  zip -q "$partially_unsigned_bundle" unsigned.txt
)

if "$verifier" "$partially_unsigned_bundle" >/dev/null 2>&1; then
  echo "Partially unsigned bundle unexpectedly passed verification." >&2
  exit 1
fi

echo "Play bundle signature verifier tests passed."
