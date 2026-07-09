# Active Workout Overview AppReveal Replay

This workflow recreates the active workout overview screenshot on the emulator
from a physical-phone AppReveal fixture. Use it when reviewing or iterating on
the regression where completed, in-progress, and upcoming exercises are hard to
distinguish.

## Fresh Session Prompt

Use this prompt in a fresh Codex session:

```text
We are in /home/gem/workspace/itl/toastlift-android.

Goal: continue UI work on the active workout overview regression using
emulator-only AppReveal replay screenshots.

Important artifact:
examples/active-workout-overview-replay/phone.fixture.json

This fixture was captured from the physical phone and contains the active
workout state to replay. Use it to restore the workout overview on the emulator,
then screenshot the emulator for review. Do not use the physical phone unless I
explicitly ask.

Existing known-good replay screenshot for reference:
examples/active-workout-overview-replay/emulator-workout-overview-known-good.png

The repo has AppReveal support for:
- screen key: active.workout_overview
- tools: debug_begin, restore_screen_fixture, open_screen, screenshot, debug_end

First create a fresh baseline emulator screenshot from the fixture and critique
the visual state. The issue is that completed vs in-progress exercises are hard
to distinguish. Note the progress ring around the exercise badge, but judge
whether it is visually strong enough.

Then iteratively make UI fixes in emulator only:
- edit code
- rebuild/install
- replay the same fixture
- capture a new screenshot
- compare against prior screenshot
- repeat until the overview clearly communicates completed, in-progress, and
  upcoming exercises

Keep generated android-e2e artifacts out of git. Do not force-add them.

Before final response, report:
- latest screenshot path
- what changed
- what was verified
- any remaining visual concerns
```

## Fresh-Session Pitfalls

These came from the `fresh-ui-test` run and are worth keeping explicit:

- Do not inspect the fixture with `.data`; the fixture shape is `screenKey`,
  `featureFlags`, and `inputs`.
- Do not start by searching an `Android/` directory in this repo. If AppReveal
  source is needed, it is usually under `../appreveal-toastlift/Android/...`.
- Do not use `tr ... | head -c 5` while `set -euo pipefail` is active; `head`
  can close the pipe normally and make the command fail before capture starts.
- Do not use `curl` through `android-emulator-adb forward`; the host wrapper may
  not expose forwarding. Use in-device `toybox nc`.
- When posting raw HTTP to AppReveal, compute `Content-Length` with
  `wc -c < file | tr -d '[:space:]'`. Padded `wc` output can produce
  `{"error":"Empty body"}`.
- For large fixture payloads, write the JSON request to a file and `cat` it into
  the HTTP body. Avoid passing the whole fixture through nested shell strings.

## Baseline Replay

From the repo root, build and install the debug APK on the emulator:

```bash
./gradlew --no-daemon --console=plain assembleDebug
android-emulator-adb -s emulator-5560 install -r -d -g --no-incremental app/build/outputs/apk/debug/app-debug.apk
```

Then run this replay script from the repo root:

```bash
set -euo pipefail

adb_bin=android-emulator-adb
serial=emulator-5560
app_id=dev.toastlabs.toastlift
activity=dev.toastlabs.toastlift/.MainActivity
fixture_path=examples/active-workout-overview-replay/phone.fixture.json
slug="$(printf '%s' "$(date -u +%Y%m%d%H%M%S%N)" | sha1sum | cut -c1-5)"
out_dir="android-e2e/$(date -u +%Y%m%d-%H%M%S)-active-workout-overview-replay-${slug}"
mkdir -p "$out_dir"

"$adb_bin" -s "$serial" logcat -c
"$adb_bin" -s "$serial" shell am force-stop "$app_id" >/dev/null
"$adb_bin" -s "$serial" shell am start -n "$activity" >/dev/null

session_url=""
for _ in $(seq 1 30); do
  session_url="$("$adb_bin" -s "$serial" logcat -d | sed -n 's/.*AppReveal: Session URL: //p' | tail -1)"
  if [ -n "$session_url" ]; then
    break
  fi
  sleep 1
done
if [ -z "$session_url" ]; then
  "$adb_bin" -s "$serial" logcat -d > "$out_dir/logcat.txt"
  echo "Timed out waiting for AppReveal Session URL. See $out_dir/logcat.txt" >&2
  exit 1
fi

port="$(sed -E 's#.*127\.0\.0\.1:([0-9]+).*#\1#' <<< "$session_url")"
token="$(sed -E 's#.*appreveal_session_token=([^&[:space:]]+).*#\1#' <<< "$session_url")"
printf '%s\n' "$session_url" > "$out_dir/session_url.txt"

mcp_post_file() {
  local request_file="$1"
  local content_length
  content_length="$(wc -c < "$request_file" | tr -d '[:space:]')"
  {
    printf 'POST /?appreveal_session_token=%s HTTP/1.1\r\n' "$token"
    printf 'Host: 127.0.0.1:%s\r\n' "$port"
    printf 'Authorization: Bearer %s\r\n' "$token"
    printf 'Content-Type: application/json\r\n'
    printf 'Accept: application/json\r\n'
    printf 'Content-Length: %s\r\n' "$content_length"
    printf 'Connection: close\r\n\r\n'
    cat "$request_file"
  } | "$adb_bin" -s "$serial" shell "toybox nc -w 20 -W 20 127.0.0.1 $port" |
    awk 'BEGIN{body=0} /^\r?$/{body=1; next} body{print}'
}

decode_tool_json() {
  jq -e '.result.content[0].text | fromjson'
}

jq -cn \
  '{jsonrpc:"2.0", id:1, method:"tools/call", params:{name:"debug_begin", arguments:{}}}' \
  > "$out_dir/debug_begin.request.json"
mcp_post_file "$out_dir/debug_begin.request.json" > "$out_dir/debug_begin.rpc.json"
decode_tool_json < "$out_dir/debug_begin.rpc.json" > "$out_dir/debug_begin.json"
debug_session_id="$(jq -r '.debugSessionId // .id' "$out_dir/debug_begin.json")"
printf '%s\n' "$debug_session_id" > "$out_dir/debug_session_id.txt"

finish_debug_session() {
  if [ -n "${debug_session_id:-}" ]; then
    jq -cn \
      --arg sid "$debug_session_id" \
      '{jsonrpc:"2.0", id:99, method:"tools/call", params:{name:"debug_end", arguments:{debug_session_id:$sid}}}' \
      > "$out_dir/debug_end.request.json"
    mcp_post_file "$out_dir/debug_end.request.json" > "$out_dir/debug_end.rpc.json" || true
    decode_tool_json < "$out_dir/debug_end.rpc.json" > "$out_dir/debug_end.json" 2>/dev/null || true
  fi
}
trap finish_debug_session EXIT

jq -cn \
  --arg sid "$debug_session_id" \
  --slurpfile fixture "$fixture_path" \
  '{jsonrpc:"2.0", id:2, method:"tools/call", params:{name:"restore_screen_fixture", arguments:{debug_session_id:$sid, fixture:$fixture[0]}}}' \
  > "$out_dir/active_workout_overview.restore_screen_fixture.request.json"
mcp_post_file "$out_dir/active_workout_overview.restore_screen_fixture.request.json" \
  > "$out_dir/active_workout_overview.restore_screen_fixture.rpc.json"
decode_tool_json < "$out_dir/active_workout_overview.restore_screen_fixture.rpc.json" \
  > "$out_dir/active_workout_overview.restore_screen_fixture.json"

sleep 2

jq -cn \
  --arg sid "$debug_session_id" \
  '{jsonrpc:"2.0", id:3, method:"tools/call", params:{name:"open_screen", arguments:{screen_key:"active.workout_overview", debug_session_id:$sid, params:{}}}}' \
  > "$out_dir/active_workout_overview.open.request.json"
mcp_post_file "$out_dir/active_workout_overview.open.request.json" \
  > "$out_dir/active_workout_overview.open.rpc.json"
decode_tool_json < "$out_dir/active_workout_overview.open.rpc.json" \
  > "$out_dir/active_workout_overview.open.json"

sleep 2

jq -cn \
  '{jsonrpc:"2.0", id:4, method:"tools/call", params:{name:"screenshot", arguments:{}}}' \
  > "$out_dir/active_workout_overview.screenshot.request.json"
mcp_post_file "$out_dir/active_workout_overview.screenshot.request.json" \
  > "$out_dir/active_workout_overview.screenshot.rpc.json"
decode_tool_json < "$out_dir/active_workout_overview.screenshot.rpc.json" \
  > "$out_dir/active_workout_overview.screenshot.json"
jq -r '.image' "$out_dir/active_workout_overview.screenshot.json" |
  base64 -d > "$out_dir/emulator-workout-overview.png"

printf 'route\tstatus\timage\nactive.workout_overview\tok\temulator-workout-overview.png\n' \
  > "$out_dir/results.tsv"
trap - EXIT
finish_debug_session

echo "$out_dir"
cat "$out_dir/results.tsv"
echo "realStateUnchanged=$(jq -r '.realStateUnchanged // empty' "$out_dir/debug_end.json")"
file "$out_dir/emulator-workout-overview.png"
```

If `toybox nc` prints `bad argument count`, remove `-W 20` from the `toybox nc`
invocation and rerun.

## Expected Output

Check the generated run before using the screenshot for review:

- `results.tsv` should contain `active.workout_overview ok`.
- `debug_end.json` should have `realStateUnchanged=true`.
- `emulator-workout-overview.png` should be a non-empty PNG, typically
  `1280 x 2856`.
- Save replay outputs under `android-e2e/<timestamp>-active-workout-overview-replay-<5-char-slug>/`.

## Iteration Loop

After the first screenshot is captured:

1. Review the overview screenshot against the known-good reference.
2. Make the smallest UI change that improves state clarity.
3. Rebuild and install the debug APK.
4. Replay the same fixture with the script above.
5. Compare the new screenshot against the prior run.
6. Repeat until completed, in-progress, and upcoming exercises are visually
   distinct.

Generated `android-e2e/` artifacts are local output. Do not force-add them to
git.
