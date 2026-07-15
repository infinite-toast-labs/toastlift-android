#!/usr/bin/env bash
set -euo pipefail

commit_sha="${1:-}"
if [[ ! "$commit_sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Promotion requires a full 40-character lowercase commit SHA." >&2
  exit 2
fi

git fetch --no-tags origin main
if ! git cat-file -e "$commit_sha^{commit}" 2>/dev/null; then
  echo "Promotion commit is not present in the fetched main history." >&2
  exit 1
fi
if ! git merge-base --is-ancestor "$commit_sha" origin/main; then
  echo "$commit_sha is not reachable from main; only immutable main commits can be promoted." >&2
  exit 1
fi

resolved="$(git rev-parse "$commit_sha^{commit}")"
if [[ "$resolved" != "$commit_sha" ]]; then
  echo "Promotion input must be a canonical full commit SHA, not an abbreviated reference." >&2
  exit 1
fi

echo "Validated main commit: $commit_sha"
