#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: capture_playstore_screenshots.sh --device phone|tablet-7|tablet-10 [options]

Options:
  --device DEVICE       Required Play device class.
  --apk PATH            Staging APK. Default: app/build/outputs/apk/staging/app-staging.apk
  --out-root DIR        Versioned screenshot root. Default: play-store/listing/en-US/screenshots
  --work-root DIR       Ignored raw/log output. Default: artifacts/playstore
  --keep-emulator       Leave the dedicated emulator running after capture.
  -h, --help            Show this help.

Environment:
  ANDROID_SDK_ROOT              Android SDK root. Falls back to ANDROID_HOME,
                                local.properties, then ~/Library/Android/sdk.
  PLAYSTORE_KEEP_EMULATOR       1 keeps the emulator running; default 0.
  PLAYSTORE_SYSTEM_IMAGE        AVD package. Default:
                                system-images;android-36.1;google_apis_playstore;arm64-v8a
  PLAYSTORE_BOOT_TIMEOUT_SECONDS  Boot timeout. Default 240.
  PLAYSTORE_APP_STARTUP_WAIT_SECONDS  Settle time before AppReveal fixtures. Default 8.
  PLAYSTORE_SCREEN_SETTLE_SECONDS  Settle time after opening each route. Default 3.
EOF
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
device=""
apk="$repo_root/app/build/outputs/apk/staging/app-staging.apk"
out_root="$repo_root/play-store/listing/en-US/screenshots"
work_root="$repo_root/artifacts/playstore"
keep_emulator="${PLAYSTORE_KEEP_EMULATOR:-0}"
system_image="${PLAYSTORE_SYSTEM_IMAGE:-system-images;android-36.1;google_apis_playstore;arm64-v8a}"
boot_timeout_seconds="${PLAYSTORE_BOOT_TIMEOUT_SECONDS:-240}"
app_startup_wait_seconds="${PLAYSTORE_APP_STARTUP_WAIT_SECONDS:-8}"
screen_settle_seconds="${PLAYSTORE_SCREEN_SETTLE_SECONDS:-3}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      device="${2:-}"
      shift 2
      ;;
    --apk)
      apk="${2:-}"
      shift 2
      ;;
    --out-root)
      out_root="${2:-}"
      shift 2
      ;;
    --work-root)
      work_root="${2:-}"
      shift 2
      ;;
    --keep-emulator)
      keep_emulator=1
      shift
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

case "$keep_emulator" in
  0|1) ;;
  *)
    echo "PLAYSTORE_KEEP_EMULATOR must be 0 or 1." >&2
    exit 2
    ;;
esac

case "$device" in
  phone)
    avd_name="toastlift_play_phone_api_36_1_v1"
    hardware_profile="pixel_9_pro"
    emulator_port=5570
    display_size="1080x1920"
    display_density=420
    output_dir="$out_root/phone"
    ;;
  tablet-7)
    avd_name="toastlift_play_tablet_7_api_36_1_v1"
    hardware_profile="Nexus 7 2013"
    emulator_port=5572
    display_size="1080x1920"
    display_density=280
    output_dir="$out_root/tablet-7"
    ;;
  tablet-10)
    avd_name="toastlift_play_tablet_10_api_36_1_v1"
    hardware_profile="Nexus 10"
    emulator_port=5574
    display_size="1440x2560"
    display_density=240
    output_dir="$out_root/tablet-10"
    ;;
  *)
    echo "--device must be phone, tablet-7, or tablet-10." >&2
    usage >&2
    exit 2
    ;;
esac

resolve_android_sdk_root() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
    return
  fi
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    printf '%s\n' "$ANDROID_HOME"
    return
  fi
  if [[ -f "$repo_root/local.properties" ]]; then
    local sdk_dir
    sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$repo_root/local.properties" | tail -1)"
    if [[ -n "$sdk_dir" ]]; then
      printf '%s\n' "${sdk_dir//\\:/:}"
      return
    fi
  fi
  printf '%s\n' "$HOME/Library/Android/sdk"
}

android_sdk_root="$(resolve_android_sdk_root)"
adb="$android_sdk_root/platform-tools/adb"
emulator="$android_sdk_root/emulator/emulator"
avdmanager="$android_sdk_root/cmdline-tools/latest/bin/avdmanager"
serial="emulator-$emulator_port"

# The existing Makefile exports a remote ADB bridge for sandbox tasks. These
# host-native Play targets must always use the Mac's local ADB server instead.
unset ANDROID_ADB_HOST ANDROID_ADB_PORT ANDROID_ADB_SERIAL

for tool in "$adb" "$emulator" "$avdmanager" sips stat; do
  if [[ "$tool" == */* ]]; then
    if [[ ! -x "$tool" ]]; then
      echo "Required Android tool is missing or not executable: $tool" >&2
      exit 1
    fi
  elif ! command -v "$tool" >/dev/null 2>&1; then
    echo "Required macOS tool not found on PATH: $tool" >&2
    exit 1
  fi
done

if [[ ! -f "$apk" ]]; then
  echo "Staging APK not found: $apk" >&2
  echo "Run 'make playstore-build-stage' first." >&2
  exit 1
fi

image_dir="$(tr ';' '/' <<< "$system_image")"
if [[ ! -d "$android_sdk_root/$image_dir" ]]; then
  echo "Required Android system image is not installed: $system_image" >&2
  echo "Install it from Android Studio's SDK Manager and retry." >&2
  exit 1
fi

mkdir -p "$output_dir" "$work_root/raw" "$work_root/logs"
rm -f "$output_dir"/*.jpg "$output_dir/manifest.tsv"
emulator_log="$work_root/logs/$device-emulator.log"
raw_root="$work_root/raw/$device"
mkdir -p "$raw_root"

emulator_running=0
if "$adb" -s "$serial" get-state >/dev/null 2>&1; then
  running_avd="$($adb -s "$serial" emu avd name 2>/dev/null | tr -d '\r' | head -1)"
  if [[ "$running_avd" != "$avd_name" ]]; then
    echo "Port $emulator_port is already used by AVD '$running_avd'; expected '$avd_name'." >&2
    exit 1
  fi
  emulator_running=1
fi

cleanup() {
  local status=$?
  if [[ "$keep_emulator" == "0" ]]; then
    "$adb" -s "$serial" emu kill >/dev/null 2>&1 || true
  else
    echo "Keeping $avd_name running as $serial for iteration." >&2
  fi
  return "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ "$emulator_running" == "0" ]]; then
  printf 'no\n' | "$avdmanager" create avd \
    --force \
    --name "$avd_name" \
    --package "$system_image" \
    --device "$hardware_profile" >/dev/null

  nohup "$emulator" \
    -avd "$avd_name" \
    -port "$emulator_port" \
    -wipe-data \
    -no-snapshot-load \
    -no-snapshot-save \
    -no-boot-anim \
    -no-audio \
    -no-window \
    -gpu host \
    -camera-back none \
    -camera-front none \
    -timezone America/Chicago \
    </dev/null >"$emulator_log" 2>&1 &
fi

deadline=$((SECONDS + boot_timeout_seconds))
while (( SECONDS < deadline )); do
  if [[ "$($adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
    break
  fi
  sleep 2
done

if [[ "$($adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; then
  echo "Timed out waiting for $avd_name to boot as $serial." >&2
  echo "Emulator log: $emulator_log" >&2
  exit 1
fi

"$adb" -s "$serial" shell wm size "$display_size" >/dev/null
"$adb" -s "$serial" shell wm density "$display_density" >/dev/null
"$adb" -s "$serial" shell settings put global window_animation_scale 0
"$adb" -s "$serial" shell settings put global transition_animation_scale 0
"$adb" -s "$serial" shell settings put global animator_duration_scale 0
"$adb" -s "$serial" shell settings put system accelerometer_rotation 0
"$adb" -s "$serial" shell settings put system user_rotation 0
"$adb" -s "$serial" shell settings put system font_scale 1.0
"$adb" -s "$serial" shell settings put system system_locales en-US >/dev/null 2>&1 || true
"$adb" -s "$serial" shell settings put global stay_on_while_plugged_in 3
"$adb" -s "$serial" shell cmd uimode night no >/dev/null 2>&1 || true
"$adb" -s "$serial" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"$adb" -s "$serial" shell wm dismiss-keyguard >/dev/null 2>&1 || true

actual_size="$($adb -s "$serial" shell wm size | sed -n 's/^Override size: //p' | tr -d '\r')"
actual_density="$($adb -s "$serial" shell wm density | sed -n 's/^Override density: //p' | tr -d '\r')"
if [[ "$actual_size" != "$display_size" || "$actual_density" != "$display_density" ]]; then
  echo "Emulator display configuration did not apply: size=$actual_size density=$actual_density" >&2
  exit 1
fi

if ! install_output="$($adb -s "$serial" install -r -d -g --no-incremental "$apk" 2>&1)"; then
  echo "$install_output" >&2
  exit 1
fi
echo "$install_output"

routes=("home.today" "explore.library")
names=("01-todays-workout" "02-exercise-library")
generated_files=()

for index in "${!routes[@]}"; do
  route="${routes[$index]}"
  name="${names[$index]}"
  capture_log="$work_root/logs/$device-$name-capture.log"

  MCP_CONTACT_SHEET_MAX_IMAGES=0 "$repo_root/scripts/capture_appreveal_mcp_screens.sh" \
    --group regular \
    --screen-key "$route" \
    --adb "$adb" \
    --serial "$serial" \
    --app-id dev.toastlabs.toastlift.staging \
    --activity dev.toastlabs.toastlift.staging/dev.toastlabs.toastlift.MainActivity \
    --out-root "$raw_root" \
    --transport adb-forward \
    --startup-wait "$app_startup_wait_seconds" \
    --wait-seconds "$screen_settle_seconds" | tee "$capture_log"

  capture_dir="$(sed -n '1p' "$capture_log")"
  safe_route="${route//./_}"
  source_png="$capture_dir/$safe_route.png"
  if [[ ! -f "$source_png" ]]; then
    echo "AppReveal did not create the expected screenshot: $source_png" >&2
    exit 1
  fi
  if ! awk -F '\t' -v route="$route" '$1 == route && $2 == "ok" { found=1 } END { exit !found }' "$capture_dir/results.tsv"; then
    echo "AppReveal capture did not report success for $route." >&2
    exit 1
  fi
  if [[ "$(jq -r '.realStateUnchanged // false' "$capture_dir/debug_end.json")" != "true" ]]; then
    echo "AppReveal fixture capture changed real app state for $route." >&2
    exit 1
  fi

  target="$output_dir/$name.jpg"
  temp_target="$target.tmp.jpg"
  rm -f "$temp_target"
  sips -s format jpeg -s formatOptions 100 "$source_png" --out "$temp_target" >/dev/null
  mv "$temp_target" "$target"

  width="$(sips -g pixelWidth "$target" 2>/dev/null | awk '/pixelWidth/{print $2}')"
  height="$(sips -g pixelHeight "$target" 2>/dev/null | awk '/pixelHeight/{print $2}')"
  expected_width="${display_size%x*}"
  expected_height="${display_size#*x}"
  if [[ "$width" != "$expected_width" || "$height" != "$expected_height" ]]; then
    echo "Unexpected screenshot dimensions for $target: ${width}x${height}; expected $display_size." >&2
    exit 1
  fi
  bytes="$(stat -f %z "$target")"
  if (( bytes > 8 * 1024 * 1024 )); then
    echo "Screenshot exceeds Play's 8 MB limit: $target ($bytes bytes)" >&2
    exit 1
  fi
  generated_files+=("$target")
done

if [[ ${#generated_files[@]} -ne 2 ]]; then
  echo "Expected exactly two Play screenshots; generated ${#generated_files[@]}." >&2
  exit 1
fi

manifest="$output_dir/manifest.tsv"
{
  printf 'position\tdevice\tresolution\tfile\talt_text\n'
  printf '1\t%s\t%s\t%s\t%s\n' "$device" "$display_size" "$(basename "${generated_files[0]}")" "Today screen with a generated workout, training freshness, and weekly muscle targets."
  printf '2\t%s\t%s\t%s\t%s\n' "$device" "$display_size" "$(basename "${generated_files[1]}")" "Exercise library with filters, favorites, search, and training metadata."
} > "$manifest"

echo "Play Store screenshots ready:"
printf '  %s\n' "${generated_files[@]}"
echo "Manifest: $manifest"
