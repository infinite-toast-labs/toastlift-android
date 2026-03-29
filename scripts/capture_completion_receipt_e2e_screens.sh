#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="${PACKAGE_NAME:-dev.toastlabs.toastlift}"
ACTIVITY_NAME="${ACTIVITY_NAME:-.MainActivity}"
SURFACE_EXTRA_KEY="${SURFACE_EXTRA_KEY:-dev.toastlabs.toastlift.extra.DEBUG_SURFACE}"
SCENARIO_EXTRA_KEY="${SCENARIO_EXTRA_KEY:-dev.toastlabs.toastlift.extra.DEBUG_RECEIPT_SCENARIO}"
THEME_EXTRA_KEY="${THEME_EXTRA_KEY:-dev.toastlabs.toastlift.extra.DEBUG_THEME}"
OUTPUT_ROOT="${OUTPUT_ROOT:-android-e2e}"
GRADLEW_PATH="${GRADLEW_PATH:-./gradlew}"
BUILD_COMMAND="${BUILD_COMMAND:-assembleDebug}"
APK_PATH="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
SCREEN_THEME="${SCREEN_THEME:-}"
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-15}"

ANDROID_ADB_HOST="${ANDROID_ADB_HOST:-host.docker.internal}"
ANDROID_ADB_PORT="${ANDROID_ADB_PORT:-5037}"

SCENARIOS=(
  "completion_receipt|program_clean_comparison|receipt-program-clean-comparison|comparison=present"
  "completion_receipt|program_partial_friction|receipt-program-partial-friction|comparison=absent"
  "completion_receipt|generated_clean_comparison|receipt-generated-clean-comparison|comparison=present"
  "completion_receipt|template_replay_comparison|receipt-template-replay-comparison|comparison=present"
  "history_receipt_replay|history_replay|receipt-history-replay|replay=true"
  "today_receipt_recap|today_recap_single|today-recap-single|today_count=1"
  "today_receipt_recap|today_recap_multi|today-recap-multi|today_count=2"
  "completion_receipt|no_comparison_fallback|receipt-no-comparison-fallback|comparison=absent"
  "completion_receipt|program_learning_card|receipt-program-learning-card|comparison=absent"
)

if command -v android-emulator-adb >/dev/null 2>&1; then
  ADB=(android-emulator-adb)
else
  ADB=(adb -H "$ANDROID_ADB_HOST" -P "$ANDROID_ADB_PORT")
fi

if ! "${ADB[@]}" devices -l | grep -q 'emulator-5560[[:space:]].*device'; then
  echo "External emulator bridge is unavailable: expected emulator-5560 as device." >&2
  exit 1
fi

if [[ ! -x "$GRADLEW_PATH" ]]; then
  echo "Missing executable Gradle wrapper at ${GRADLEW_PATH}." >&2
  exit 1
fi

"$GRADLEW_PATH" --no-daemon "$BUILD_COMMAND" --console=plain

if [[ ! -f "$APK_PATH" ]]; then
  echo "Expected APK at ${APK_PATH} after build." >&2
  exit 1
fi

"${ADB[@]}" install -r "$APK_PATH" >/dev/null

timestamp="$(TZ=America/Regina date +%Y-%m-%d_%H-%M-%S_CST)"
output_dir="${OUTPUT_ROOT}/${timestamp}"
mkdir -p "$output_dir"

git_rev="$(git rev-parse --short HEAD 2>/dev/null || printf 'unknown')"
manifest_path="${output_dir}/manifest.json"

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '%s' "$value"
}

append_manifest_item() {
  local first_item="$1"
  local surface="$2"
  local scenario="$3"
  local file="$4"
  local saw_ready="$5"
  local assertions="$6"
  if [[ "$first_item" != "true" ]]; then
    printf ',\n' >> "$manifest_path"
  fi
  cat >> "$manifest_path" <<EOF
    {
      "surface": "$(json_escape "$surface")",
      "scenario": "$(json_escape "$scenario")",
      "file": "$(json_escape "$file")",
      "saw_ready": ${saw_ready},
      "assertions": "$(json_escape "$assertions")"
    }
EOF
}

wait_for_ready() {
  local surface="$1"
  local scenario="$2"
  local ready_line="TOASTLIFT_E2E_READY surface=${surface} scenario=${scenario}"
  local deadline=$((SECONDS + READY_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    local logs
    logs="$("${ADB[@]}" logcat -d -s ToastLiftE2E:D '*:S' || true)"
    if grep -Fq "$ready_line" <<<"$logs"; then
      printf '%s' "$logs"
      return 0
    fi
    sleep 1
  done
  return 1
}

assert_log_contains() {
  local logs="$1"
  local expected="$2"
  if ! grep -Fq "TOASTLIFT_E2E_ASSERT ${expected}" <<<"$logs"; then
    echo "Missing expected assertion '${expected}'." >&2
    return 1
  fi
}

cat > "$manifest_path" <<EOF
{
  "generated_at_cst": "$(json_escape "$timestamp")",
  "git_rev": "$(json_escape "$git_rev")",
  "theme": "$(json_escape "${SCREEN_THEME:-default}")",
  "captures": [
EOF

first_manifest_item=true

for item in "${SCENARIOS[@]}"; do
  IFS='|' read -r surface scenario base_name expected_assert <<<"$item"
  "${ADB[@]}" logcat -c

  start_args=(
    shell am start -S
    -n "${PACKAGE_NAME}/${ACTIVITY_NAME}"
    --es "${SURFACE_EXTRA_KEY}" "${surface}"
    --es "${SCENARIO_EXTRA_KEY}" "${scenario}"
  )
  if [[ -n "$SCREEN_THEME" ]]; then
    start_args+=(--es "${THEME_EXTRA_KEY}" "${SCREEN_THEME}")
  fi

  "${ADB[@]}" "${start_args[@]}" >/dev/null

  logs=""
  if ! logs="$(wait_for_ready "$surface" "$scenario")"; then
    echo "Timed out waiting for ready marker for ${surface}/${scenario}." >&2
    exit 1
  fi

  assert_log_contains "$logs" "$expected_assert"

  if [[ -n "$SCREEN_THEME" ]]; then
    filename="${base_name}-${SCREEN_THEME}.png"
  else
    filename="${base_name}.png"
  fi
  "${ADB[@]}" exec-out screencap -p > "${output_dir}/${filename}"

  append_manifest_item "$first_manifest_item" "$surface" "$scenario" "$filename" true "$expected_assert"
  first_manifest_item=false
done

cat >> "$manifest_path" <<EOF

  ]
}
EOF

printf '%s\n' "$output_dir"
