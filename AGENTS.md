# ToastLift Android Agent Notes

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
```

Default to `make mcp-screens-all` when asked to audit or capture all screens. The
target builds and installs a fresh debug APK, launches the app, discovers the
AppReveal session URL/token, calls MCP tools, and saves artifacts under
`android-e2e/<timestamp>-...`.

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
