#!/usr/bin/env bash
set -euo pipefail

profile="${1:-}"
case "$profile" in
  staging)
    secret_name="/toastlift/android/staging-signing"
    prefix="TOASTLIFT_STAGING"
    expected_kind="toastlift-android-staging-signing"
    ;;
  play-upload)
    # The physical Secrets Manager name is retained during the production-key
    # migration. It stores the Play upload key, never the Play app-signing key.
    secret_name="/toastlift/android/release-signing"
    prefix="TOASTLIFT_PLAY_UPLOAD"
    expected_kind="toastlift-android-release-signing"
    ;;
  *)
    echo "Usage: $0 staging|play-upload" >&2
    exit 2
    ;;
esac

secret_directory="$(mktemp -d "${RUNNER_TEMP:?}/toastlift-signing.XXXXXX")"
chmod 700 "$secret_directory"
secret_file="$secret_directory/secret.json"

success=false
cleanup_on_exit() {
  if [[ "$success" == true ]]; then
    return
  fi
  rm -f "$secret_file" "$secret_directory/keystore.jks"
  rmdir "$secret_directory" 2>/dev/null || true
}
trap cleanup_on_exit EXIT

aws secretsmanager get-secret-value \
  --secret-id "$secret_name" \
  --query SecretString \
  --output text > "$secret_file"

for field in schemaVersion kind state keyStoreBase64 storePassword keyAlias keyPassword; do
  if ! value="$(jq -er ".$field" "$secret_file")"; then
    echo "Signing secret is missing a usable $field field." >&2
    exit 1
  fi
  if [[ "$field" == keyStoreBase64 || "$field" == storePassword || "$field" == keyAlias || "$field" == keyPassword ]]; then
    echo "::add-mask::$value"
  fi
done

if [[ "$(jq -r '.schemaVersion' "$secret_file")" != "1" \
  || "$(jq -r '.kind' "$secret_file")" != "$expected_kind" \
  || "$(jq -r '.state' "$secret_file")" != "backed-up" ]]; then
  echo "Signing secret is not a backed-up $profile signing secret." >&2
  exit 1
fi

key_store_base64="$(jq -r '.keyStoreBase64' "$secret_file")"
store_password="$(jq -r '.storePassword' "$secret_file")"
key_alias="$(jq -r '.keyAlias' "$secret_file")"
key_password="$(jq -r '.keyPassword' "$secret_file")"
keystore="$secret_directory/keystore.jks"
printf '%s' "$key_store_base64" | base64 --decode > "$keystore"
chmod 600 "$keystore"
rm -f "$secret_file"

{
  echo "${prefix}_STORE_FILE=$keystore"
  echo "${prefix}_STORE_PASSWORD=$store_password"
  echo "${prefix}_KEY_ALIAS=$key_alias"
  echo "${prefix}_KEY_PASSWORD=$key_password"
  echo "TOASTLIFT_SIGNING_DIRECTORY=$secret_directory"
} >> "$GITHUB_ENV"
success=true
