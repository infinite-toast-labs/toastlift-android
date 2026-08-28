# ToastLift Android Agent Notes

## Product and release handoff

Before changing the production product surface or preparing a Play release, read
[`docs/PRODUCTION_RELEASE_PLAYBOOK.md`](docs/PRODUCTION_RELEASE_PLAYBOOK.md).
It documents the debug/staging/release modes, feature-config discipline,
signing, visual QA, companion website, and Play Store procedure.

For the private Zstore path, also read
[`docs/ZSTORE_PUBLISHING.md`](docs/ZSTORE_PUBLISHING.md). Zstore candidates are
unsigned, secret-free, production-surface APKs; signing and promotion stay on the
trusted Zstore host.

## AppReveal MCP Visual Debugging

ToastLift's visual-debug MCP server is dynamic. It is not registered as a static
Codex MCP server. The server runs inside the debug APK through AppReveal, emits a
per-session URL/token to logcat, and is reached by the repo capture script through
`android-emulator-adb`.

Use the Make targets as the stable interface:

```bash
make mcp-screens-all
make mcp-screens-regular
make mcp-screens-sheets
make mcp-full-scroll-all
make mcp-full-scroll-screen SCREEN_KEY=sheet.exercise_history
```

Default to `make mcp-screens-all` when asked to audit or capture all screens. The
target builds and installs a fresh debug APK, launches the app, discovers the
AppReveal session URL/token, calls MCP tools, and saves artifacts under
`android-e2e/<timestamp>-...`.

For iterative visual fixes after a specific screenshot has been identified, use
the single-route full-scroll target instead of recapturing every screen. It
accepts both regular screen keys and bottom-sheet keys:

```bash
make mcp-full-scroll-screen SCREEN_KEY=<appreveal-key>
```

For explicit physical-phone capture, use the phone variants. Do not hardcode a
serial; set `DEVICE_SERIAL=<serial>` only when needed, otherwise the target picks
the first non-emulator ADB device:

```bash
make mcp-phone-screens-all
make mcp-phone-full-scroll-all
make mcp-phone-full-scroll-screen SCREEN_KEY=<appreveal-key>
```

Do not replace this with raw `adb screencap` when the request is specifically for
the MCP/AppReveal workflow.

After a run, check:

- `results.tsv` for non-`ok` rows.
- `debug_end.json` for `realStateUnchanged=true`.
- `contact-sheet.png` when present for a quick visual overview.

Treat `android-e2e/` as generated local output. It may include session JSON and
must not be force-added to git.

If the emulator bridge is missing, start or reconnect the host-managed emulator
from `../llm-sandbox`, for example:

```bash
cd ../llm-sandbox
make android-connect
```
