#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

release_secret_name="${TOASTLIFT_AWS_RELEASE_SECRET_NAME:-/toastlift/android/release-signing}"
staging_secret_name="${TOASTLIFT_AWS_STAGING_SECRET_NAME:-/toastlift/android/staging-signing}"
github_repository="${TOASTLIFT_GITHUB_REPOSITORY:-infinite-toast-labs/toastlift-android}"
npm ci
npx cdk bootstrap
npx cdk deploy --require-approval never \
  -c "releaseSecretName=$release_secret_name" \
  -c "stagingSecretName=$staging_secret_name" \
  -c "githubRepository=$github_repository"
