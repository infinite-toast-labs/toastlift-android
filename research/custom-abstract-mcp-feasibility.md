# Feasibility: Custom Abstract In-App MCP Server for Android Visual Debugging

Status: proposal / feasibility. Author: agent. Date: 2026-07-02.

## 1. Question

Build a **custom, abstract, from-scratch in-app MCP server** library (Android/Kotlin,
debug-only) that:

- Works **out of the box** for generic visual-debug operations that don't need app
  knowledge: open a specific screen, screenshot, scroll, tap, list elements, read
  the view/a11y tree, batch actions, device info.
- **Requires app developers to implement** the app-specific operations that *do*
  need app knowledge: capture/restore a screen's full input state as a JSON
  fixture for deterministic pixel-perfect recreation, and the ephemeral
  read-only debug session (snapshot user data → block writes → restore on exit).

Is this feasible? How should the abstraction be shaped? What are the risks?

## 2. TL;DR verdict

**Feasible and a good fit.** Recommended. ~1–2 weeks to a usable v0.1 for ToastLift,
~3–4 weeks to a clean reusable library. The hard parts are not the MCP plumbing —
that's well-understood (JSON-RPC over HTTP, tool registration, NanoHTTPD-style
server). The hard parts are (a) defining the **right seams** so generic code can
navigate/screenshot while app code owns state, and (b) the **RO ephemeral session**,
which is inherently app-specific because it depends on the data layer's shape.

Default posture:

- Build ToastLift-first in v0.1, but keep the API shaped like a future AAR.
- Treat visual equivalence under tolerance as the default deterministic target;
  byte-identical screenshots are an opt-in strict mode.
- Keep fixtures host-side at first; do not add on-device fixture persistence until
  the audit workflow proves it needs it.
- Enforce read-only debug sessions at the database/session boundary first, with
  repository write guards and lint as secondary defense.
- Omit `commit` from v0.1; add an explicit unsafe debug-write mode later only if
  the workflow proves it needs one.

Trade-off vs. adopting AppReveal: a custom server lets us ship **exactly** the
abstraction we want (generic OOTB + app-implemented fixtures + RO sessions) without
forking an external project, at the cost of owning the MCP transport, Compose
a11y inspection, and screenshot/scroll primitives ourselves. For an app that is
already fully custom (no Room, no Hilt, hand-rolled nav — see §5), the
integration tax of an external library is not obviously lower than building the
narrow surface we need.

## 3. Requirements recap (from the feature prompt)

| # | Requirement | Generic or app-specific? |
|---|---|---|
| R1 | Open app with "gym mode"; ephemeral; restore user state after | **App-specific** (feature flag + data layer) |
| R2 | Screenshot screen X scrolled so text Y visible | Mostly **generic** (scroll + text scan), needs screen reachability |
| R3 | Open a specific deep/hidden screen directly | **App-specific** (screen addressing), generic transport |
| R4 | Capture all screen details as JSON; recreate deterministically from fresh start | **App-specific** (per-screen inputs) |
| R5 | Explicit ephemeral RO debug mode; no DB/user-data side effects | **App-specific** (data layer), generic scaffold |
| R6 | Enumerate + visually validate all screens before release | **App-specific** (screen registry), generic runner |

The split is clean: **generic transport + UI primitives** vs. **app-specific state,
navigation, and fixtures**. That's exactly the seam an abstract library should
expose.

## 4. Proposed architecture

A debug-only Android library `debug-mcp` (publishable as an AAR, `debugImplementation`
only; a `debug-mcp-noop` artifact provides empty release stubs) with two layers:

```
┌─────────────────────────────────────────────────────────────┐
│  MCP Client (Claude / agent / curl)                         │
│   ↕ JSON-RPC 2.0 over Streamable HTTP, per-session token    │
├─────────────────────────────────────────────────────────────┤
│  debug-mcp  (library, debug builds only)                    │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Core (generic, works OOTB)                           │  │
│  │  • MCPServer (HTTP, session token, /health)          │  │
│  │  • Tool registry + JSON schema + batch                │  │
│  │  • Screenshot (PixelCopy / drawToBitmap)              │  │
│  │  • Element inventory (Compose semantics + View tree)  │  │
│  │  • Scroll / tap / type / swipe primitives             │  │
│  │  • scroll_to_text helper (loop + tree scan)           │  │
│  │  • device_info / launch_context                       │  │
│  │  • deeplink launch (am start / Intent) — generic      │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Abstraction seams (interfaces the app implements)    │  │
│  │  • ScreenRegistry   → list_screens, screen addressing │  │
│  │  • Navigator         → open(screenKey, params)        │  │
│  │  • StateProvider     → get_state                      │  │
│  │  • FeatureFlags      → get/set ephemeral flags        │  │
│  │  • DebugSessionHost  → begin/rollback/end (RO)        │  │
│  │  • ScreenFixtureProvider → capture()/apply(fixture)   │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              ▲ app implements seams
┌─────────────────────────────────────────────────────────────┐
│  App (debug build)                                          │
│   DebugMcp.start(application) {                             │
│     register(ScreenRegistry)                                │
│     register(Navigator)                                     │
│     register(DebugSessionHost)                              │
│     screens { fixture<LogExerciseScreen>() }                │
│   }                                                         │
└─────────────────────────────────────────────────────────────┘
```

### 4.1 Generic tools (work with zero app integration)

| Tool | Source | Notes |
|------|--------|-------|
| `initialize` / `tools/list` / `tools/call` | MCP standard | JSON-RPC |
| `screenshot` | PixelCopy API 26+ / `View.drawToBitmap` | base64 PNG/JPEG, optional element crop |
| `get_elements` | Compose `SemanticsNode` tree + View tree | id from testTag/resource/contentDescription |
| `get_view_tree` | same | depth-limited |
| `scroll` / `scroll_to_element` / `scroll_to_text` | scrollable-finder + loop | `scroll_to_text` loops scroll + scans tree text |
| `tap_element` / `tap_text` / `tap_point` | `View.performClick` / coords | |
| `type_text` / `clear_text` | focus + `InputConnection` | |
| `swipe` / `press_back` / `press_home` | `UiAutomation` / `InputManager` | |
| `open_deeplink` | `Intent(ACTION_VIEW, Uri)` | generic; app still owns deeplink routes |
| `device_info` / `launch_context` | `Context`/`Build`/`Display` | |
| `batch` | sequential + `delay_ms` + `stop_on_error` | |
| `list_screens` / `get_screen` | **degrades gracefully**: if no `ScreenRegistry`, report current activity + visible semantics/root class where available | OOTB gives orientation context; app registration makes screen addressing precise |

So an app that does **nothing** but call `DebugMcp.start(this)` still gets
screenshots, element listing, scroll-to-text, tap, deeplink, batch, and a
best-effort current-screen description. It should **not** promise reliable
enumeration of hidden or state-driven screens without app bindings. That satisfies
R2 and part of the generic half of R3/R6 with zero app code; precise screen
coverage still needs `ScreenRegistry`.

### 4.2 App-implemented seams (interfaces)

```kotlin
// Screen addressing — R3, R6
interface ScreenRegistry {
    fun all(): List<ScreenDescriptor>          // screenKey, title, deeplink?, fixtureTag?
    fun resolve(screenKey: String): ScreenDescriptor?
}

interface Navigator {
    fun open(screenKey: String, params: Map<String, String>): Boolean
    fun current(): ScreenRef                   // for get_screen
    fun back(); fun dismissModal()
}

// State — R4 input
interface StateProvider { fun snapshot(): Map<String, Any?> }

// Ephemeral flags — R1
interface FeatureFlags {
    fun all(): Map<String, Any?>
    fun setEphemeral(flags: Map<String, Any?>)  // only inside DebugSession
}

// RO ephemeral session — R1, R5
interface DebugSessionHost {
    fun begin(profile: DebugProfile): String    // returns sessionId
    fun rollback(sessionId: String)
    fun end(sessionId: String)
}

// Deterministic recreation — R4
interface ScreenFixtureProvider {
    val screenKey: String
    fun capture(): ScreenFixture
    fun apply(fixture: ScreenFixture)           // only inside DebugSession
}
```

`ScreenFixture` is a generic envelope:

```kotlin
@Serializable
data class ScreenFixture(
    val fixtureSchemaVersion: Int,
    val screenKey: String,
    val navStack: List<String> = emptyList(),
    val inputs: JsonObject,                     // app-defined per screen
    val featureFlags: Map<String, JsonElement> = emptyMap(),
    val scrollHint: ScrollHint? = null,         // captured best-effort by core
    val deviceProfile: DeviceProfile,           // density, fontScale, locale, theme, orientation
    val capturedAt: String,
    val appVersion: String,
    val appVersionCode: Long,
)
```

The **core captures the generic fields** (navStack via `Navigator.current()` +
history, scrollHint via the active scrollable's offset, featureFlags via
`FeatureFlags.all()`); the **app fills `inputs`** in `capture()` and consumes it
in `apply()`. This keeps the app's per-screen work to just "what fields define
this screen and how do I rebuild it," which is irreducibly app-specific.

### 4.3 The ephemeral RO session (the crux)

`DebugSessionHost` is the most app-specific seam because RO behavior depends on
how the app persists data. The library provides a **default implementation
template**, a **database/session isolation pattern**, and a **RO guard**, but the
app must wire them into its persistence layer.

Default template the library ships:

```kotlin
class DefaultDebugSessionHost(
    private val snapshotter: () -> UserStateSnapshot,   // app: copy DB file + prefs
    private val restorer: (UserStateSnapshot) -> Unit,  // app: restore them
    private val dataSourceSwap: (DebugDataMode) -> Unit, // app: real <-> disposable copy
) : DebugSessionHost {
    private val sessions = mutableMapOf<String, UserStateSnapshot>()
    private val roGuard = DebugRoGuard                  // process/session-scoped flag

    override fun begin(profile: DebugProfile): String {
        val snap = snapshotter()
        val id = UUID.randomUUID().toString()
        sessions[id] = snap
        dataSourceSwap(DebugDataMode.DisposableCopy)     // writes go to copied state
        roGuard.enterReadOnlySession(id)                 // protected writes throw
        profile.featureFlags.forEach { flags.setEphemeral(...) }
        return id
    }
    override fun end(id: String) {
        roGuard.exitReadOnlySession(id)
        dataSourceSwap(DebugDataMode.Real)              // swap back to real
        restorer(sessions.remove(id)!!)                 // restore user state byte-for-byte
    }
    // rollback = end (discard disposable copy); commit is intentionally absent in v0.1
}
```

`DebugRoGuard` should **not** be thread-local for ToastLift. The app launches
repository work through coroutines and `Dispatchers.IO`, so a thread-local flag can
miss writes that hop threads. Use a process/session-scoped guard or coroutine
context element, and prefer database-level isolation first: open a disposable copy
of `toastlift.db` for the debug session while keeping the real DB closed and
restorable.

The library **cannot** enforce RO by itself — it can only provide the flag,
database-isolation template, and annotation/lint checks. Enforcement is the app's
job; the library makes it cheap and consistent. The v0.1 acceptance test should
hash/copy the real DB before and after a full debug session and fail if it changes.

## 5. ToastLift-specific fit (why custom is cheap here)

ToastLift's stack is unusually amenable to this:

- **No Room** — `ToastLiftDatabase` wraps a raw `SQLiteDatabase` with a single
  `open()` and a known db file (`toastlift.db`). Snapshot = close/checkpoint,
  copy the file (+ any DataStore/prefs files), open a disposable copy for the
  debug session, then restore/reopen the real file. This needs a small explicit
  close/reopen/debug-path API on `ToastLiftDatabase`; it should not rely only on
  repository-level checks.
- **No Hilt/Dagger** — manual `AppContainer` DI. Swapping real repositories for
  a disposable debug database path = one explicit mode in `AppContainer` /
  `ToastLiftDatabase`. The library's `dataSourceSwap` hook maps onto this
  directly.
- **No Jetpack NavHost routes** — navigation is state-driven inside the
  `ToastLiftApp` composable. This means `Navigator.open(screenKey, params)` is
  app-implemented as "set the state that produces that screen," and
  `ScreenRegistry.all()` is a hand-maintained list. Slightly more app code than a
  NavHost app, but fully controllable — deep/hidden screens are trivial to expose
  (no graph constraints).
- **Compose** — element inventory + scroll-to-text map cleanly onto the
  `SemanticsNode` tree; no legacy View-only edge cases to worry about.

Net: the app-specific implementation for ToastLift is likely on the order of
~250–400 LOC once database isolation, screen registry, and the first fixture
provider are included. Still not a multi-week effort, but more than a tiny binding
object if RO behavior is enforced rigorously.

## 6. Effort & timeline

| Phase | Scope | Est. |
|-------|-------|------|
| 1 | Core MCP server (HTTP, session token, tool registry, JSON schema, batch) | 2–3 d |
| 2 | Generic UI primitives: screenshot, Compose element/view-tree inventory, tap/type, scroll + scroll_to_text, deeplink, device_info | 3–4 d |
| 3 | Abstraction seams + default templates (best-effort current screen, DefaultDebugSessionHost, DebugRoGuard + lint) | 2 d |
| 4 | ToastLift bindings: ScreenRegistry, Navigator, FeatureFlags(gym_mode), DebugSessionHost (disposable DB copy), 1–2 ScreenFixtureProviders (log-exercise screen first) | 3–4 d |
| 5 | LLM skill + recipes wired to the real tool names; deterministic-recreation screenshot diff test | 1–2 d |
| 6 | Hardening: release-noop artifact, auth/CORS, port-from-logcat discovery, error model, docs | 2–3 d |

**~2 weeks to a working v0.1 on ToastLift**, ~3–4 weeks to a clean reusable AAR
with docs + sample app. Assumes one developer, Kotlin/Compose fluent.

Suggested first milestone before committing to the whole server: prove four
things on one ToastLift screen — `screenshot`, `get_elements`, `open_screen`, and
real-DB hash unchanged after a debug session. That spike validates the riskiest
assumptions before spending time on reusable packaging.

## 7. Risks & mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| **RO guard not enforced** — a write path misses the `DebugRoGuard` check and mutates user data during a "RO" session | **High** (defeats R5, can corrupt user data) | Default to disposable database copy + restore real DB on exit; make `DebugRoGuard` process/session-scoped, not thread-local; add lint/annotation pass (`@WritesData` must check guard); integration test asserts the real DB file hash is identical afterward. |
| **Snapshot/restore of raw SQLite file** — copying `toastlift.db` while a write is in flight gives a corrupt snapshot | Medium | `begin()` quiesces writes (app provides a "drain" hook) before file copy; close/checkpoint the DB before copy; include WAL/SHM sidecars if WAL is enabled; verify restore with a schema+row-count check. |
| **Coroutine/thread hops bypass debug context** — repository writes run on `Dispatchers.IO` outside the thread that began the debug session | High | Avoid thread-local-only guards; use process/session-scoped state or a coroutine context element, and enforce isolation at the database boundary. |
| **Compose semantics not exposing ids** — element inventory returns derived ids, `scroll_to_element`/`tap_element` become flaky | Medium | Encourage `testTag` in debug builds; library falls back to text/label matching; document id priority. |
| **Deterministic recreation isn't actually pixel-perfect** — clock/time/random/UUIDs leak into UI (e.g. "today" dates, animation timestamps) | Medium | `DebugProfile` includes a frozen clock + seeded RNG the app reads during sessions; fixture captures and replays the clock. Library provides a `DebugClock` seam. |
| **Fixture drift across app versions/devices** — a fixture captured on one version, density, font scale, locale, or theme replays differently later | Medium | Add `fixtureSchemaVersion`, app version/code, density, fontScale, locale, orientation, and theme to the fixture envelope; fail clearly on incompatible fixtures unless explicitly forced. |
| **Scroll position not faithfully captured/restored** — `ScrollState.maxValue` differs across density/font-size | Low–Med | Capture `scroll_offset / max_value` ratio + the inputs that determine `maxValue`; restore by re-scrolling to the ratio. Accept "close" not byte-exact for scroll; gate pixel-diff with a tolerance. |
| **MCP transport bugs** (NanoHTTPD quirks, chunked streaming, base64 size) | Low | Use a battle-tested embedded HTTP lib (NanoHTTPD or Ktor); cap screenshot size; support `format:jpeg` for large screens. |
| **Security** — debug server reachable on LAN, leaks state | Low (debug-only) | Loopback CORS only; per-session token; release-noop artifact; `GET /health` only unauth endpoint. Same posture as AppReveal. |
| **Scope creep into a general framework** | Medium (time) | Keep v0.* ToastLift-shaped; extract the reusable AAR only after v0.1 proves the seams. Don't generalize prematurely. |
| **Compose-tree inspection across custom layouts** | Low | `SemanticsNode` tree is the stable public API; custom layouts still produce semantics. Worst case, fall back to `get_view_tree` of the AndroidView host. |

## 8. Build-vs-adopt decision

| Option | Pros | Cons |
|--------|------|------|
| **A. Adopt AppReveal + extend** | Mature transport, Compose inventory, scroll/tap, multi-platform; less code to write/maintain | Must learn their provider model; fixture capture/restore + RO session still app-built; external dep in a vibe-coded solo project; abstraction is *theirs*, not ours |
| **B. Custom abstract server (this proposal)** | Exact abstraction we want; generic OOTB surface is small enough to build in days; zero external dep; fits ToastLift's no-Room/no-Hilt stack perfectly; reusable AAR later | We own MCP transport + Compose inspection + scroll/screenshot primitives; reinvents ~60% of AppReveal |
| **C. Hybrid** — fork/graft AppReveal's Android module, strip to the seams we want, add fixture+RO | Reuses their proven primitives; still custom seams | Fork maintenance; licensing (check AppReveal license); more reading than writing |

**Recommendation: B.** The generic surface we actually need (screenshot, element
inventory, scroll-to-text, tap, deeplink, batch, device info) is ~5 days of work
on top of NanoHTTPD/Ktor and the Compose `SemanticsNode` API. The savings from
adopting AppReveal are real but mostly in areas (WebView DOM, iOS, desktop,
mDNS) we don't need. For a single-app project that wants a *specific* abstraction
(generic OOTB + app-implemented fixtures + RO sessions), the integration tax of
an external library is not obviously lower than building the narrow surface.

If multi-platform or shipping the library to other apps later becomes a goal,
revisit C (graft AppReveal's primitives) to avoid re-deriving Compose inspection.

## 9. What "works out of the box" means concretely

An app that adds **only**:

```kotlin
class MyApp : Application() {
  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) DebugMcp.start(this)   // no bindings
  }
}
```

gets these tools functional immediately:
`screenshot`, `get_elements`, `get_view_tree`, `scroll`, `scroll_to_element`,
`scroll_to_text`, `tap_element`, `tap_text`, `tap_point`, `type_text`,
`clear_text`, `swipe`, `press_back`, `press_home`, `open_deeplink`,
`device_info`, `launch_context`, `batch`, and a best-effort current-screen report
based on the active activity/window and visible semantics.

These tools return **graceful "not registered" errors** until the app provides
the matching seam: `get_state`, `get_feature_flags`, `set_feature_flags`,
`debug_begin/end`, `capture_screen_fixture`, `restore_screen_fixture`,
`list_fixtures`, and a precise `list_screens`.

So the OOTB experience covers R2 and generic visual inspection. R3/R6 are only
partially covered until the app provides `ScreenRegistry`/`Navigator`, and R1/R4/R5
light up only after the app implements the state/session seams — which is the
intended design.

## 10. Open questions for the user

1. **RO enforcement strictness:** lint-only warning, or hard build failure on any
   `@WritesData` function that doesn't check `DebugRoGuard`?
   **Default:** hard fail in debug builds, plus runtime database isolation. For
   ToastLift, do not rely on a thread-local guard.
2. **Determinism scope:** is "pixel-perfect" literally byte-identical PNGs, or
   visually-identical under a tolerance? Literal byte-identity forces a frozen
   clock + seeded RNG + no animations-during-capture, which is invasive.
   **Default:** visual tolerance; byte-identity as an opt-in strict mode.
3. **Fixture storage:** keep fixtures as JSON files on host only, or also
   round-trip them through the server (`save_fixture`/`load_fixture` with on-device
   storage)?
   **Default:** host-only first; on-device later if the audit flow needs it.
4. **Multi-app reuse horizon:** is this ToastLift-only, or do you want a
   publishable AAR from day one? (Affects how much we generalize the seams in v0.1.)
   **Default:** ToastLift-first, library-shaped; extract only after v0.1 proves the
   seams.
5. **Unsafe write-debug mode:** should the server ever support `commit` back to
   real user state?
   **Default:** no `commit` in v0.1. Add it later as a separate, explicit unsafe
   mode if needed.

## 11. Recommendation

Proceed with **Option B (custom abstract server)** scoped to ToastLift for v0.1,
designed around the §4 seams so it extracts cleanly into a reusable AAR later.
First run a narrow spike on the **exercise-logging screen** that proves
`screenshot`, `get_elements`, `open_screen`, fixture replay, and unchanged real DB
hash after session exit. Then build Phase 1–4 around the proven seams. Wire the
existing `skills/android-visual-debug-mcp/SKILL.md` recipes to the real tool names
once Phase 4 lands.
