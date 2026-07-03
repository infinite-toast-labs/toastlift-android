# Research: MCP server for Android visual debugging / screen recreation

Goal: an MCP server for the ToastLift Android app used for **visual debugging** —
recreate any screen, interact with the app, screenshot screens scrolled to specific
text, open deep screens directly, capture a screen's full input state as JSON for
deterministic pixel-perfect recreation, all inside an explicit **ephemeral,
read-only** debug mode that leaves the user's real data untouched.

This file records what already exists (as of 2026-07-02) and where the gaps are.

---

## TL;DR

- **No off-the-shelf solution does everything.** The closest is
  **AppReveal** — a debug-only, in-app MCP server with Android/Compose support that
  already covers screenshots, scroll, tap, element/view-tree inspection, app-state
  snapshots, navigation stack, feature flags, deeplink navigation, and batch ops.
- AppReveal does **not** provide: an ephemeral RO debug session with state
  rollback, deterministic screen recreation from a captured fixture, or
  scroll-to-text. Those are app-specific and must be built on top.
- ADB-based MCP servers (DeepADB, adb-tui, mcp-server-adb, podium-mcp, glass) drive
  the app as a black box — good screenshots/taps on any app, but **no access to
  in-app state, navigation, or feature flags**, so they cannot do deterministic
  recreation or ephemeral mode.
- **Recommended path:** adopt AppReveal as the MCP transport + UI inspection layer,
  and add three app-specific extensions behind its provider hooks:
  1. Ephemeral RO debug session (snapshot user data → swap to in-memory source →
     block writes → restore on exit).
  2. Screen fixture capture/restore (each screen declares its inputs and how to
     rebuild itself).
  3. scroll-to-text helper.

---

## Candidates examined

### 1. AppReveal — `UnlikeOtherAI/AppReveal` ★32 (Swift core, Kotlin/Android module)
**Repo:** github.com/UnlikeOtherAI/AppReveal — clone @ main, docs `docs/android.md`,
`docs/tools.md`. Android module under `Android/appreveal/`.

Debug-only in-app MCP server (Streamable HTTP) for iOS/macOS/Android/Flutter/RN/Windows.
"Playwright for native apps." This is the strongest match.

**Relevant tools (Android/Compose supported):**
| Need | Tool | Notes |
|------|------|-------|
| Screenshot a screen | `screenshot` (PNG/JPEG, base64, optional `element_id`) | PixelCopy API 26+ |
| Screenshot scrolled to text Y | `scroll_to_element` + `screenshot` | **No native scroll-to-text** — only scroll-to-element by id. Text may need a loop. |
| Open a deep screen directly | `open_deeplink`, `select_tab`, `navigate_back`, `dismiss_modal` | Needs deeplinks wired in the app |
| List/inspect screen | `get_screen` (screenKey, title, nav depth, tab, modals) | `ScreenIdentifiable` on Activities/Fragments, else auto-derived |
| Inspect elements | `get_elements` (id, type, label, value, frame, safe-area, actions) | ids from `view.tag`/resource name/`contentDescription` |
| Full hierarchy | `get_view_tree` (depth-limited) | |
| App state as JSON | `get_state` — whatever the app registers via `StateProviding.snapshot()` | **This is the hook for fixture capture.** |
| Navigation state | `get_navigation_stack` (route, stack, modals) via `NavigationProviding` | |
| Feature flags | `get_feature_flags` via `FeatureFlagProviding` | e.g. "gym mode" flag |
| Network traffic | `get_network_calls` (auth redacted) | |
| Interact | `tap_element`, `tap_text`, `tap_point`, `type_text`, `clear_text`, `scroll` | |
| Batch (deterministic flows) | `batch` (sequential actions, `delay_ms`, `stop_on_error`) | |
| Env | `device_info`, `launch_context` | |
| WebView | `get_dom_tree`, `find_dom_text`, `web_click`, `web_evaluate`, … | N/A for ToastLift |

**Why it fits ToastLift:**
- `debugImplementation` + a `appreveal-noop` release artifact → zero code in
  release APK, no `BuildConfig.DEBUG` guards needed.
- Provider hooks (`registerStateProvider`, `registerNavigationProvider`,
  `registerFeatureFlagProvider`) are exactly where ephemeral-mode + fixture
  capture plug in.
- Emulator-friendly: `adb forward tcp:<port> tcp:<port>` then curl/SDK; or mDNS
  `_appreveal._tcp` discovery on LAN. Per-session token auth, loopback CORS.
- Compose-friendly: element inventory has a `ComposeElementInventory`; view-tree
  walk works on Compose via the underlying AndroidViews/semantics.

**Gaps for this feature set:**
- ❌ No ephemeral RO session / state rollback. AppReveal is debug-only at the
  *build* level, not at the *runtime data* level. Tapping/typing mutates the live
  app and the app still writes to its DB.
- ❌ No deterministic screen recreation. `get_state` is a one-way snapshot; there
  is no `restore_state`/`apply_fixture` tool and no scroll-position capture.
- ❌ No scroll-to-text (only scroll-to-element-by-id). Workaround: loop
  `scroll` + scan `get_elements`/view-tree for the text.
- ⚠️ "gym mode" as an *ephemeral* flag: `get_feature_flags` reads flags, but
  setting one for a debug session is not a built-in tool — needs a custom tool.

### 2. AndroClaudio — `Atul206/androidclaudio` ★2 (Kotlin, KSP)
Embedded MCP server in the **debug APK**. KSP generates type-safe registries from
`groups.json` so an agent can **call the app's public functions live** on the
emulator, with mock/live groups. `debugImplementation` → absent from release.
**Relevance:** This is the mechanism you'd want for "set gym mode ephemerally" and
"call `applyFixture(...)`" — it exposes arbitrary app functions to MCP. No
UI/visual surface (no screenshot/scroll/element tree), so it's complementary to
AppReveal, not a replacement.

### 3. FixThis — `beyondwin/FixThis` ★8 (Kotlin, Compose)
"Point at any Jetpack Compose UI element, annotate it, hand off source-pinned
prompts to AI." MCP-native, debug-only. Different angle — it's a **code-edit
hand-off** tool (annotate Compose nodes → prompt the coding agent with the source
location), not a control/state server. Not useful for screen recreation / RO mode,
but the Compose-semantics→source mapping idea is nice for audits.

### 4. DeepADB — `fullread/DeepADB` ★10 (TypeScript)
204 tools / 45 modules over ADB: ui dump, screenshot, input gestures,
screen-record, accessibility audit, regression/screenshot-diff, emulator mgmt,
network capture, snapshot module. Triple transport (stdio/HTTP-SSE/WS).
**Relevance:** Black-box ADB driver — works on any app with zero instrumentation,
great for screenshots + UI automator dumps + accessibility audits. But **no
in-app state, no navigation/feature-flag access, no ephemeral mode, no
deterministic recreation.** Good fallback for "screenshot screen X" when you
can't instrument, and for the pre-release accessibility/visual audit pass.

### 5. adb-tui — `alanisme/adb-tui` ★18 (Go)
Full-featured ADB TUI + MCP server. Same black-box category as DeepADB, smaller
tool surface. Not a fit for state recreation.

### 6. glass — `fixed-width/glass` ★2 (Rust)
"build → see → interact → debug" MCP loop over native GUI apps; has an Android
backend that drives an AVD over adb (`glass_screenshot`, `glass_click`,
`glass_diff`, `glass_wait_stable`, `glass_logs`). Text-only diff/wait tools save
vision tokens. Black-box, no in-app state. Interesting for cheap change-detection
between screenshots during audits, but not for recreation.

### 7. podium-mcp — `hoainho/podium-mcp` ★2 (TypeScript)
51 tools, one stdio endpoint for iOS+Android, Maestro flows, "oracle ladder"
assertions, WebView DOM, RN/Metro. ADB/uiautomator for Android. Black-box for
Android native; strongest for E2E-flow + assertion work, not state recreation.

### 8. Others (lower relevance)
`watabee/mcp-server-adb`, `TiagoDanin/Android-Debug-Bridge-MCP`,
`yava555/mcp-server-adb`, `amit-nayar/android-adb-skill`, `richard0913/adb-mcp`,
`dolaviber92/AdvancedSharpAdbClient-MCP` — all thin ADB wrappers (screenshot, tap,
shell, install). Black-box, no state. `Acendas/android-debugger` is a JDWP/JDI
Kotlin debugger — process-level debugging, not visual.

---

## Feature-by-feature mapping

| User requirement | Best existing coverage | Gap |
|---|---|---|
| Open app with "gym mode", ephemeral, restore after | AppReveal `get_feature_flags` (read) + AndroClaudio (call setter) | No bundled ephemeral-session + rollback. **Must build.** |
| Screenshot screen X scrolled so text Y is visible | AppReveal `open_deeplink` + `scroll_to_element` + `screenshot` | No scroll-to-text. **Build a small helper.** |
| Open a deep/hidden screen directly | AppReveal `open_deeplink` / `select_tab` / `batch` | App must wire deeplinks to every screen. |
| Capture all screen details as JSON for deterministic recreation | AppReveal `get_state` + `get_elements` + `get_view_tree` + `get_navigation_stack` | One-way snapshot; no scroll position, no `restore`. **Must build fixture capture/restore per screen.** |
| Explicit ephemeral RO debug mode (no DB/user-data side effects) | AppReveal is debug-build-only (no release code) | Runtime RO guard is **not** provided. **Must build a DebugSession that swaps the data layer to in-memory and blocks writes.** |
| Enumerate/validate all screens visually before release | AppReveal `get_screen` + `screenshot` + `batch`; DeepADB screenshot-diff/accessibility for cross-checks | Need a screen registry to enumerate. **Build a screen catalog tool.** |

---

## Recommendation for ToastLift

Use **AppReveal** as the MCP foundation (transport, auth, UI inspection,
screenshots, deeplink nav, batch, provider hooks). Add an app-specific module
`toastlift-debug-mcp` (debugImplementation only) that registers three custom
providers/tools on top of AppReveal:

1. **`DebugSession` (ephemeral RO mode)**
   - `debug_begin({ profile })` — snapshot current DB + prefs to memory/on-disk,
     swap the repository/data-source to an in-memory volatile implementation,
     set feature flags (e.g. `gym_mode=true`) from `profile`, enter RO guard
     (all write paths throw `DebugReadOnlyException`).
   - `debug_rollback()` / `debug_commit()` — discard or keep session changes.
   - `debug_end()` — restore the original data source + flags + nav state, leave
     the app as the user left it.
   - Exposed as MCP tools + via `FeatureFlagProviding` so `get_feature_flags`
     reflects the ephemeral profile.

2. **`ScreenFixture` (deterministic recreation)**
   - Each debuggable screen implements `ScreenFixtureProvider`:
     `capture(): ScreenFixture` (the inputs that produce it: exercise name, set
     count, completed set indices, prescription, scroll position, selected tab,
     nav stack, feature flags) and `apply(fixture)` (rebuild itself from those
     inputs, e.g. via a deeplink + a state-restore call).
   - MCP tools: `capture_screen_fixture`, `list_fixtures`, `restore_screen_fixture`,
     `save_fixture` / `load_fixture` (JSON on host). A fixture = the JSON the user
     asked for.
   - Combined with `debug_begin` for true determinism (fresh in-memory state each
     time) → pixel-perfect mirror on demand.

3. **`scroll_to_text` helper** — loop `scroll(direction=down)` + view-tree text
   scan until target text is on-screen, then return bounds; pair with `screenshot`
   for the "screenshot scrolled so text Y is visible" recipe.

4. **Screen catalog** — a `ScreenRegistry` (auto-built from `ScreenIdentifiable`
   keys) exposed as `list_screens` so an agent can enumerate every screen and run
   a fixture-screenshot audit per screen before release.

The companion **LLM skill** (`skills/android-visual-debug-mcp/SKILL.md`) drives
this surface: discover the server via `adb forward` / mDNS, run the ephemeral RO
session recipe, the screenshot-scrolled-to-text recipe, the deep-screen-open
recipe, and the capture/restore-fixture recipe. It is written against the
*intended* ToastLift MCP surface above but kept general so it works for any app
that implements the same tools.
