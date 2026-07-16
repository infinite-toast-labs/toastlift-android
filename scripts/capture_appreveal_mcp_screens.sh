#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: capture_appreveal_mcp_screens.sh --group regular|sheets|all [options]

Options:
  --adb PATH             ADB wrapper to use. Default: android-emulator-adb
  --serial SERIAL        Device serial. Default: emulator-5560
  --app-id APP_ID        Android app id. Default: dev.toastlabs.toastlift.debug
  --activity COMPONENT   Launch component. Default: dev.toastlabs.toastlift.debug/dev.toastlabs.toastlift.MainActivity
  --out-root DIR         Artifact root. Default: android-e2e
  --startup-wait N       Delay after AppReveal starts before debug_begin. Default: 0
  --wait-seconds N       Delay after opening each screen. Default: 2
  --capture-mode MODE    Capture mode: single or full. Default: single
  --screen-key KEY       Capture only one registered AppReveal screen/sheet key
  --transport MODE       MCP transport: device-nc or adb-forward. Default: device-nc
  --scroll-max-pages N   Maximum scroll pages after the first capture. Default: 120
  --scroll-settle-ms N   Delay after each scroll before capture. Default: 40
EOF
}

group=""
adb_bin="${EMULATOR_ADB:-android-emulator-adb}"
serial="${ADB_SERIAL:-emulator-5560}"
app_id="${APP_ID:-dev.toastlabs.toastlift.debug}"
activity="${MAIN_ACTIVITY:-dev.toastlabs.toastlift.debug/dev.toastlabs.toastlift.MainActivity}"
out_root="${MCP_SCREEN_OUTPUT_ROOT:-android-e2e}"
startup_wait_seconds="${MCP_SCREEN_STARTUP_WAIT_SECONDS:-0}"
wait_seconds="${MCP_SCREEN_WAIT_SECONDS:-2}"
capture_mode="${MCP_CAPTURE_MODE:-single}"
screen_key="${MCP_SCREEN_KEY:-}"
scroll_max_pages="${MCP_SCROLL_MAX_PAGES:-120}"
scroll_settle_ms="${MCP_SCROLL_SETTLE_MS:-40}"
contact_sheet_max_images="${MCP_CONTACT_SHEET_MAX_IMAGES:-40}"
transport="${MCP_TRANSPORT:-device-nc}"

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
    --startup-wait)
      startup_wait_seconds="${2:-}"
      shift 2
      ;;
    --wait-seconds)
      wait_seconds="${2:-}"
      shift 2
      ;;
    --capture-mode)
      capture_mode="${2:-}"
      shift 2
      ;;
    --screen-key)
      screen_key="${2:-}"
      shift 2
      ;;
    --transport)
      transport="${2:-}"
      shift 2
      ;;
    --scroll-max-pages)
      scroll_max_pages="${2:-}"
      shift 2
      ;;
    --scroll-settle-ms)
      scroll_settle_ms="${2:-}"
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

case "$capture_mode" in
  single|full) ;;
  *)
    echo "--capture-mode must be single or full." >&2
    usage >&2
    exit 2
    ;;
esac

case "$transport" in
  device-nc|adb-forward) ;;
  *)
    echo "--transport must be device-nc or adb-forward." >&2
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
if [[ "$transport" == "adb-forward" ]] && ! command -v curl >/dev/null 2>&1; then
  echo "Required tool not found on PATH: curl" >&2
  exit 1
fi

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
if [[ "$capture_mode" == "full" ]]; then
  slug="full-scroll-$slug"
fi
if [[ -n "$screen_key" ]]; then
  safe_screen_slug="$(sed -E 's/[^A-Za-z0-9._-]+/-/g; s/[.]+/_/g' <<< "$screen_key")"
  slug="$slug-$safe_screen_slug"
fi
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

if [[ "$startup_wait_seconds" != "0" ]]; then
  sleep "$startup_wait_seconds"
fi

request_id=0
forward_port=""
cleanup_forward() {
  if [[ -n "$forward_port" ]]; then
    "$adb_bin" -s "$serial" forward --remove "tcp:$forward_port" >/dev/null 2>&1 || true
    forward_port=""
  fi
}
if [[ "$transport" == "adb-forward" ]]; then
  forward_port="$($adb_bin -s "$serial" forward tcp:0 "tcp:$port")"
  trap cleanup_forward EXIT
fi

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
  if [[ "$transport" == "adb-forward" ]]; then
    curl --fail --silent --show-error --max-time 30 \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json" \
      --data "$request" \
      "http://127.0.0.1:$forward_port/?appreveal_session_token=$token"
  else
    local content_length
    content_length="$(printf '%s' "$request" | wc -c)"
    printf 'POST /?appreveal_session_token=%s HTTP/1.1\r\nHost: 127.0.0.1:%s\r\nAuthorization: Bearer %s\r\nContent-Type: application/json\r\nAccept: application/json\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s' \
      "$token" "$port" "$token" "$content_length" "$request" |
      "$adb_bin" -s "$serial" shell "toybox nc -w 15 -W 15 127.0.0.1 $port" |
      awk 'BEGIN{body=0} /^\r?$/{body=1; next} body{print}'
  fi
}

epoch_ms() {
  local value
  value="$(date +%s%3N)"
  if [[ "$value" =~ ^[0-9]+$ ]]; then
    printf '%s\n' "$value"
  else
    printf '%s000\n' "$(date +%s)"
  fi
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
routes=()
while IFS= read -r route; do
  routes+=("$route")
done < <(jq -r "$jq_filter" "$out_dir/list_screens.json")

if [[ -n "$screen_key" ]]; then
  if ! jq -e --arg screen_key "$screen_key" '.screens[] | select(.key == $screen_key)' "$out_dir/list_screens.json" >/dev/null; then
    echo "No registered AppReveal screen matched --screen-key: $screen_key" >&2
    echo "Known keys:" >&2
    jq -r '.screens[] | .key' "$out_dir/list_screens.json" >&2
    exit 1
  fi
  if ! printf '%s\n' "${routes[@]}" | grep -Fxq "$screen_key"; then
    echo "Registered AppReveal screen does not match --group $group: $screen_key" >&2
    exit 1
  fi
  routes=("$screen_key")
fi

if [[ ${#routes[@]} -eq 0 ]]; then
  echo "No AppReveal routes matched group: $group" >&2
  exit 1
fi

printf 'route\tstatus\tpage_count\timages\tstop_reason\thost_total_ms\tapp_total_ms\tapp_capture_ms\tapp_scroll_ms\tapp_settle_ms\n' > "$out_dir/results.tsv"

finish_debug_session() {
  local end_args end_rpc
  end_args="$(jq -cn --arg debug_session_id "$debug_session_id" '{debug_session_id:$debug_session_id}')"
  end_rpc="$(mcp_call debug_end "$end_args" || true)"
  printf '%s\n' "$end_rpc" > "$out_dir/debug_end.rpc.json"
  printf '%s\n' "$end_rpc" | decode_tool_json > "$out_dir/debug_end.json" 2>/dev/null || true
}
finish_capture() {
  finish_debug_session
  cleanup_forward
}
trap finish_capture EXIT

for route in "${routes[@]}"; do
  safe_route="${route//./_}"
  open_args="$(jq -cn \
    --arg screen_key "$route" \
    --arg debug_session_id "$debug_session_id" \
    '{screen_key:$screen_key, debug_session_id:$debug_session_id, params:{set_count:"4", completed_sets:"1", exercise_index:"0"}}')"
  open_rpc="$(mcp_call open_screen "$open_args" || true)"
  printf '%s\n' "$open_rpc" > "$out_dir/$safe_route.open.rpc.json"

  if ! printf '%s\n' "$open_rpc" | decode_tool_json > "$out_dir/$safe_route.open.json" 2>"$out_dir/$safe_route.open.decode.err"; then
    printf '%s\topen_failed\t0\t\t\t\t\t\t\t\n' "$route" >> "$out_dir/results.tsv"
    continue
  fi

  if [[ "$(jq -r '.opened' "$out_dir/$safe_route.open.json")" != "true" ]]; then
    printf '%s\topen_false\t0\t\t\t\t\t\t\t\n' "$route" >> "$out_dir/results.tsv"
    continue
  fi

  sleep "$wait_seconds"
  if [[ "$capture_mode" == "single" ]]; then
    capture_started_ms="$(epoch_ms)"
    screenshot_rpc="$(mcp_call screenshot '{}' || true)"
    host_total_ms=$(( $(epoch_ms) - capture_started_ms ))
    printf '%s\n' "$screenshot_rpc" > "$out_dir/$safe_route.screenshot.rpc.json"
    if ! printf '%s\n' "$screenshot_rpc" | decode_tool_json > "$out_dir/$safe_route.screenshot.json" 2>"$out_dir/$safe_route.screenshot.decode.err"; then
      printf '%s\tscreenshot_failed\t0\t\t\t%s\t\t\t\t\n' "$route" "$host_total_ms" >> "$out_dir/results.tsv"
      continue
    fi

    image_name="$safe_route.png"
    jq -r '.image' "$out_dir/$safe_route.screenshot.json" | base64 -d > "$out_dir/$image_name"
    printf '%s\tok\t1\t%s\tsingle_screenshot\t%s\t\t\t\t\n' \
      "$route" \
      "$image_name" \
      "$host_total_ms" >> "$out_dir/results.tsv"
    continue
  fi

  capture_args="$(jq -cn \
    --argjson max_pages "$scroll_max_pages" \
    --argjson settle_ms "$scroll_settle_ms" \
    '{direction:"down", format:"png", max_pages:$max_pages, settle_ms:$settle_ms}')"
  capture_started_ms="$(epoch_ms)"
  capture_rpc="$(mcp_call capture_scrollable_region "$capture_args" || true)"
  host_total_ms=$(( $(epoch_ms) - capture_started_ms ))
  printf '%s\n' "$capture_rpc" > "$out_dir/$safe_route.capture_scrollable_region.rpc.json"
  if ! printf '%s\n' "$capture_rpc" | decode_tool_json > "$out_dir/$safe_route.capture_scrollable_region.json" 2>"$out_dir/$safe_route.capture_scrollable_region.decode.err"; then
    printf '%s\tcapture_failed\t0\t\t\t%s\t\t\t\t\n' "$route" "$host_total_ms" >> "$out_dir/results.tsv"
    continue
  fi

  page_count="$(jq -r '.pageCount // (.pages | length)' "$out_dir/$safe_route.capture_scrollable_region.json")"
  if [[ "$page_count" == "0" ]]; then
    printf '%s\tcapture_empty\t0\t\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$route" \
      "$(jq -r '.stopReason // empty' "$out_dir/$safe_route.capture_scrollable_region.json")" \
      "$host_total_ms" \
      "$(jq -r '.timing.totalMs // empty' "$out_dir/$safe_route.capture_scrollable_region.json")" \
      "$(jq -r '.timing.captureMs // empty' "$out_dir/$safe_route.capture_scrollable_region.json")" \
      "$(jq -r '.timing.scrollMs // empty' "$out_dir/$safe_route.capture_scrollable_region.json")" \
      "$(jq -r '.timing.settleMs // empty' "$out_dir/$safe_route.capture_scrollable_region.json")" >> "$out_dir/results.tsv"
    continue
  fi

  image_names=()
  for ((page_index=0; page_index<page_count; page_index++)); do
    page_number=$((page_index + 1))
    image_name="${safe_route}__${page_number}-of-${page_count}.png"
    jq -r ".pages[$page_index].image" "$out_dir/$safe_route.capture_scrollable_region.json" | base64 -d > "$out_dir/$image_name"
    image_names+=("$image_name")
  done
  images="$(IFS=,; echo "${image_names[*]}")"
  stop_reason="$(jq -r '.stopReason // empty' "$out_dir/$safe_route.capture_scrollable_region.json")"
  status="ok"
  if [[ "$stop_reason" == "max_pages" ]]; then
    status="max_pages"
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$route" \
    "$status" \
    "$page_count" \
    "$images" \
    "$stop_reason" \
    "$host_total_ms" \
    "$(jq -r '.timing.totalMs // empty' "$out_dir/$safe_route.capture_scrollable_region.json")" \
    "$(jq -r '.timing.captureMs // empty' "$out_dir/$safe_route.capture_scrollable_region.json")" \
    "$(jq -r '.timing.scrollMs // empty' "$out_dir/$safe_route.capture_scrollable_region.json")" \
    "$(jq -r '.timing.settleMs // empty' "$out_dir/$safe_route.capture_scrollable_region.json")" >> "$out_dir/results.tsv"
done

if (( contact_sheet_max_images > 0 )) && command -v montage >/dev/null 2>&1 && compgen -G "$out_dir/*.png" >/dev/null; then
  png_files=()
  while IFS= read -r png_file; do
    png_files+=("$png_file")
  done < <(find "$out_dir" -maxdepth 1 -type f -name '*.png' | sort)
  if [[ ${#png_files[@]} -le "$contact_sheet_max_images" ]]; then
    montage "${png_files[@]}" \
      -thumbnail 240x520 \
      -tile 4x \
      -geometry +8+24 \
      -background '#101010' \
      -fill white \
      -font DejaVu-Sans \
      -pointsize 18 \
      -set label '%t' \
      "$out_dir/contact-sheet.png" || true
  else
    echo "Skipping contact-sheet.png: ${#png_files[@]} screenshots exceed the montage safety limit of $contact_sheet_max_images." >&2
  fi
fi

trap - EXIT
finish_capture

echo "$out_dir"
cat "$out_dir/results.tsv"
if [[ -f "$out_dir/debug_end.json" ]]; then
  echo "realStateUnchanged=$(jq -r '.realStateUnchanged // empty' "$out_dir/debug_end.json")"
fi
