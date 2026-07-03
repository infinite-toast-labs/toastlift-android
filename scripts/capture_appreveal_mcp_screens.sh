#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: capture_appreveal_mcp_screens.sh --group regular|sheets|all [options]

Options:
  --adb PATH             ADB wrapper to use. Default: android-emulator-adb
  --serial SERIAL        Device serial. Default: emulator-5560
  --app-id APP_ID        Android app id. Default: dev.toastlabs.toastlift
  --activity COMPONENT   Launch component. Default: dev.toastlabs.toastlift/.MainActivity
  --out-root DIR         Artifact root. Default: android-e2e
  --wait-seconds N       Delay after opening each screen. Default: 2
EOF
}

group=""
adb_bin="${EMULATOR_ADB:-android-emulator-adb}"
serial="${ADB_SERIAL:-emulator-5560}"
app_id="${APP_ID:-dev.toastlabs.toastlift}"
activity="${MAIN_ACTIVITY:-dev.toastlabs.toastlift/.MainActivity}"
out_root="${MCP_SCREEN_OUTPUT_ROOT:-android-e2e}"
wait_seconds="${MCP_SCREEN_WAIT_SECONDS:-2}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --group)
      group="${2:-}"
      shift 2
      ;;
    --adb)
      adb_bin="${2:-}"
      shift 2
      ;;
    --serial)
      serial="${2:-}"
      shift 2
      ;;
    --app-id)
      app_id="${2:-}"
      shift 2
      ;;
    --activity)
      activity="${2:-}"
      shift 2
      ;;
    --out-root)
      out_root="${2:-}"
      shift 2
      ;;
    --wait-seconds)
      wait_seconds="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

case "$group" in
  regular|sheets|all) ;;
  *)
    echo "--group must be regular, sheets, or all." >&2
    usage >&2
    exit 2
    ;;
esac

for tool in jq base64; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Required tool not found on PATH: $tool" >&2
    exit 1
  fi
done

if ! "$adb_bin" -s "$serial" get-state >/dev/null 2>&1; then
  echo "ADB device unavailable: expected $serial through $adb_bin." >&2
  exit 1
fi

timestamp="$(TZ=America/Chicago date +%F_%H-%M-%S_CST)"
case "$group" in
  regular) slug="regular-screens-mcp" ;;
  sheets) slug="bottom-sheets-mcp" ;;
  all) slug="all-screens-and-sheets-mcp" ;;
esac
out_dir="$out_root/${timestamp}-${slug}"
mkdir -p "$out_dir"

"$adb_bin" -s "$serial" logcat -c
"$adb_bin" -s "$serial" shell am force-stop "$app_id" >/dev/null
"$adb_bin" -s "$serial" shell am start -n "$activity" >/dev/null

session_url=""
for _ in {1..30}; do
  session_url="$("$adb_bin" -s "$serial" logcat -d | sed -n 's/.*AppReveal: Session URL: //p' | tail -1)"
  if [[ -n "$session_url" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "$session_url" ]]; then
  echo "Timed out waiting for AppReveal Session URL in logcat." >&2
  exit 1
fi

port="$(sed -E 's#.*127\.0\.0\.1:([0-9]+).*#\1#' <<< "$session_url")"
token="$(sed -E 's#.*appreveal_session_token=([^&[:space:]]+).*#\1#' <<< "$session_url")"
if [[ -z "$port" || -z "$token" || "$port" == "$session_url" || "$token" == "$session_url" ]]; then
  echo "Could not parse AppReveal port/token from: $session_url" >&2
  exit 1
fi

request_id=0
mcp_call() {
  local tool_name="$1"
  local args
  if [[ $# -ge 2 ]]; then
    args="$2"
  else
    args="{}"
  fi
  request_id=$((request_id + 1))
  local request
  request="$(jq -cn \
    --arg id "$request_id" \
    --arg name "$tool_name" \
    --argjson arguments "$args" \
    '{jsonrpc:"2.0", id:($id|tonumber), method:"tools/call", params:{name:$name, arguments:$arguments}}')"
  local content_length
  content_length="$(printf '%s' "$request" | wc -c)"
  printf 'POST /?appreveal_session_token=%s HTTP/1.1\r\nHost: 127.0.0.1:%s\r\nAuthorization: Bearer %s\r\nContent-Type: application/json\r\nAccept: application/json\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s' \
    "$token" "$port" "$token" "$content_length" "$request" |
    "$adb_bin" -s "$serial" shell "toybox nc -w 15 -W 15 127.0.0.1 $port" |
    awk 'BEGIN{body=0} /^\r?$/{body=1; next} body{print}'
}

decode_tool_json() {
  jq -e '.result.content[0].text | fromjson'
}

begin_rpc="$(mcp_call debug_begin '{}')"
printf '%s\n' "$begin_rpc" > "$out_dir/debug_begin.rpc.json"
printf '%s\n' "$begin_rpc" | decode_tool_json > "$out_dir/debug_begin.json"
debug_session_id="$(jq -r '.debugSessionId' "$out_dir/debug_begin.json")"
printf '%s\n' "$debug_session_id" > "$out_dir/debug_session_id.txt"

list_rpc="$(mcp_call list_screens '{}')"
printf '%s\n' "$list_rpc" > "$out_dir/list_screens.rpc.json"
printf '%s\n' "$list_rpc" | decode_tool_json > "$out_dir/list_screens.json"

case "$group" in
  regular)
    jq_filter='.screens[] | .key | select(startswith("sheet.") | not)'
    ;;
  sheets)
    jq_filter='.screens[] | .key | select(startswith("sheet."))'
    ;;
  all)
    jq_filter='.screens[] | .key'
    ;;
esac
mapfile -t routes < <(jq -r "$jq_filter" "$out_dir/list_screens.json")

if [[ ${#routes[@]} -eq 0 ]]; then
  echo "No AppReveal routes matched group: $group" >&2
  exit 1
fi

printf 'route\tstatus\timage\n' > "$out_dir/results.tsv"

finish_debug_session() {
  local end_args end_rpc
  end_args="$(jq -cn --arg debug_session_id "$debug_session_id" '{debug_session_id:$debug_session_id}')"
  end_rpc="$(mcp_call debug_end "$end_args" || true)"
  printf '%s\n' "$end_rpc" > "$out_dir/debug_end.rpc.json"
  printf '%s\n' "$end_rpc" | decode_tool_json > "$out_dir/debug_end.json" 2>/dev/null || true
}
trap finish_debug_session EXIT

for route in "${routes[@]}"; do
  safe_route="${route//./_}"
  open_args="$(jq -cn \
    --arg screen_key "$route" \
    --arg debug_session_id "$debug_session_id" \
    '{screen_key:$screen_key, debug_session_id:$debug_session_id, params:{set_count:"4", completed_sets:"1", exercise_index:"0"}}')"
  open_rpc="$(mcp_call open_screen "$open_args" || true)"
  printf '%s\n' "$open_rpc" > "$out_dir/$safe_route.open.rpc.json"

  if ! printf '%s\n' "$open_rpc" | decode_tool_json > "$out_dir/$safe_route.open.json" 2>"$out_dir/$safe_route.open.decode.err"; then
    printf '%s\topen_failed\t\n' "$route" >> "$out_dir/results.tsv"
    continue
  fi

  if [[ "$(jq -r '.opened' "$out_dir/$safe_route.open.json")" != "true" ]]; then
    printf '%s\topen_false\t\n' "$route" >> "$out_dir/results.tsv"
    continue
  fi

  sleep "$wait_seconds"
  screenshot_rpc="$(mcp_call screenshot '{}' || true)"
  printf '%s\n' "$screenshot_rpc" > "$out_dir/$safe_route.screenshot.rpc.json"
  if ! printf '%s\n' "$screenshot_rpc" | decode_tool_json > "$out_dir/$safe_route.screenshot.json" 2>"$out_dir/$safe_route.screenshot.decode.err"; then
    printf '%s\tscreenshot_failed\t\n' "$route" >> "$out_dir/results.tsv"
    continue
  fi

  jq -r '.image' "$out_dir/$safe_route.screenshot.json" | base64 -d > "$out_dir/$safe_route.png"
  printf '%s\tok\t%s.png\n' "$route" "$safe_route" >> "$out_dir/results.tsv"
done

if command -v montage >/dev/null 2>&1 && compgen -G "$out_dir/*.png" >/dev/null; then
  montage "$out_dir"/*.png \
    -thumbnail 240x520 \
    -tile 4x \
    -geometry +8+24 \
    -background '#101010' \
    -fill white \
    -font DejaVu-Sans \
    -pointsize 18 \
    -set label '%t' \
    "$out_dir/contact-sheet.png" || true
fi

trap - EXIT
finish_debug_session

echo "$out_dir"
cat "$out_dir/results.tsv"
if [[ -f "$out_dir/debug_end.json" ]]; then
  echo "realStateUnchanged=$(jq -r '.realStateUnchanged // empty' "$out_dir/debug_end.json")"
fi
