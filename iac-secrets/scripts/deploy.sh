#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

secret_name="${TOASTLIFT_AWS_SECRET_NAME:-/toastlift/android/release-signing}"
npm ci
npx cdk bootstrap
npx cdk deploy --require-approval never -c "secretName=$secret_name"
