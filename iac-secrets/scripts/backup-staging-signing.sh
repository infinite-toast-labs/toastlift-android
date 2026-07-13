#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

npm ci
TOASTLIFT_SIGNING_PROFILE=staging npx tsx scripts/backup-release-signing.ts
