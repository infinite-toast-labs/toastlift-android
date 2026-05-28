# Next Best App Improvements From The Last 5 Commits

Date: 2026-05-29

Method: repo-grounded `idea-wizard` pass. There is no `AGENTS.md`, no `ideas/` history, and `br list --json` reports that beads are not initialized, so this is a standalone brainstorm.

## Read Of The Last 5 Commits

Recent work is not random polish. It has a clear product direction:

- `4bf24fe` - muscle refresh status and last RIR ghost text.
  - Moved Training Freshness into the active workout details flow.
  - Added muscle refresh projection rows, due/overdue shields, active-session muscle filters, and last-session effort hints.
- `45f85a6` - fix unit for cardio.
  - Added `WorkUnits.kt`.
  - Made completed work-unit values count as logged work, even when reps are absent.
  - Started treating cardio and machine intervals as real training work instead of awkward strength-set impostors.
- `487bf7e` - improve active exercise picking.
  - Preserved history workout focus when reusing/generated add-exercise picks.
  - Kept set steppers synced while keyboard focus remains active.
  - Added configurable freshness bucket minimum.
  - Added per-exercise logged-session counts in picker metadata.
- `a5d3709` - workout session efficiencies.
  - Improved active-session flow, previous-session set hints, active workout persistence/notification robustness, and speed of logging.
- `397496f` - training freshness meters and exercise max/avg weight info on log set screen.
  - Built the Training Freshness system and put useful history stats directly where the user logs sets.

My read: the app is being pulled toward an in-workout copilot, not a passive tracker. The last five commits all tighten the loop between history, freshness, recommendation, and low-friction logging. The user likely wants the app to use the data it already shows to make the next training action obvious.

## Prediction Of What You Would Want

You probably do not want another dashboard as the next main move. You already added richer dashboards and details. You also probably do not want generic gamification, social features, or broad "AI coach" copy.

The next best improvement is to make the freshly exposed intelligence actionable during the workout:

## Core Recommendation: Live Freshness Gap Filler

Turn the active workout from "here is your muscle refresh projection" into "here is the smallest exact action that makes today's workout satisfy the important freshness gaps."

In practical terms:

- If an overdue muscle is already in today's workout, front the matching exercise in "next up" and show why.
- If a due/overdue muscle is partially covered, say the smallest remaining dose: "1 more triceps set refreshes triceps."
- If a due/overdue muscle is not covered, offer a one-tap finisher: "Add 6-min hamstrings finisher."
- When the qualifying set lands, give a tiny non-blocking confirmation: "Triceps refreshed."
- At workout close, the receipt should say which muscles/buckets were refreshed and which due items remain.

This is the highest-fit next move because it directly extends the latest commit. `ActiveWorkoutMuscleRefreshSummary` already knows expected weighted sets, completed weighted sets, state, family, and current freshness status. The missing layer is an action selector.

## The Top 5 Ideas

### 1. Live Freshness Gap Filler

This is the best idea.

What it adds:

- A compact "Freshness gaps" strip in the active workout surface.
- One prioritized recommendation at a time:
  - "Do Cable Row next - back is overdue."
  - "One more direct triceps set refreshes triceps."
  - "Add hamstring finisher - lower is still late."
- A one-tap action:
  - open the matching planned exercise
  - add one set to the current matching exercise
  - open picker filtered to the due muscle
  - generate a tiny finisher using the current location, equipment, time, and focus
- Completion micro-confirmations when a row crosses the refresh threshold.

Why this fits your taste:

- It is pragmatic and accretive.
- It builds on the exact code you just touched.
- It turns freshness from a status display into a decision reducer.
- It respects your recent obsession with active-session ergonomics.
- It is not vague coaching. It has a concrete logged-set threshold.

Implementation sketch:

- Add `ActiveWorkoutFreshnessAction`:
  - `OpenExercise(index, reason)`
  - `AddSet(exerciseIndex, reason)`
  - `OpenFilteredPicker(muscleKey, reason)`
  - `GenerateFinisher(muscleKey, family, reason)`
  - `None`
- Build it from:
  - `ActiveSession`
  - `ActiveWorkoutMuscleRefreshSummary`
  - `TrainingFreshnessSummary`
  - `exerciseDetailsById`
  - active profile equipment/location
- Priority order:
  - overdue and targeted but pending
  - overdue and not targeted
  - due soon and targeted but pending
  - due soon and not targeted
  - fresh but user has a saved smart target muscle
- Add a small active-session card or header chip. Avoid turning the log screen into a lecture.
- Reuse existing picker/generator paths instead of creating a new recommendation engine.

Tests:

- action builder opens a planned exercise when an overdue muscle is pending in-session
- action builder suggests add-set when one more weighted set crosses threshold
- action builder suggests filtered picker when overdue muscle is not targeted
- refreshed transition fires once per muscle per workout
- generated finisher preserves session focus/location and avoids duplicates

### 2. Previous Session Replay Logging

The app now shows previous set performance and last RIR hints. The next friction killer is to let the user replay or beat the last matching session in one tap.

What it adds:

- "Match last session" action on an exercise.
- "Beat last" action that applies the smallest sensible bump:
  - +5 lb where appropriate
  - +1 rep where weight should stay fixed
  - same reps/weight but lower RIR target when the prior session was too easy
- Ghost values for each set from the latest logged session, not just a text line.
- A diff row while logging:
  - "Set 2: +5 lb vs last"
  - "Same load, +1 rep"
  - "RIR 2 last time"

Why this fits:

- Recent commits keep moving historical detail into the active log screen.
- The current "Try max + 5" tag is useful but blunt.
- Lifters often want to beat last time, not inspect a history page.

Implementation sketch:

- Extend `ExercisePerformanceStats.previousSessionSetsBySetNumber` into a per-set replay model.
- Add pure helpers:
  - `buildPreviousSessionReplayPlan(...)`
  - `buildBeatLastPlan(...)`
  - `setDeltaLabel(current, previous)`
- Reuse `updateSessionValue` propagation carefully so applying a replay plan does not fight manual edits.

Tests:

- replay fills only incomplete sets
- beat-last chooses weight bump only when prior reps hit or exceeded target
- RIR hint appears only when previous RIR exists
- user-edited values are not overwritten by a later replay

### 3. Cardio And Work-Unit Parity

The cardio fix is foundational but not yet a full product experience. Work-unit exercises should become first-class in history, completion, freshness, and active workout feedback.

What it adds:

- Work-unit history detail:
  - treadmill pace, distance, incline, duration
  - rower split, distance, stroke rate
  - bike resistance, cadence, duration
- Work-unit PRs:
  - longest duration
  - fastest pace
  - farthest distance
  - highest average HR or calories when available
- Completion receipt support:
  - "20 min treadmill counted toward conditioning"
  - "2.1 mi, +0.3 mi vs last"
- Freshness contribution sanity:
  - cardio should count as training work, but not falsely refresh chest/triceps unless metadata says so.

Why this fits:

- You just made work-unit values count as logged signals.
- The next likely annoyance is that cardio logs count in some places but feel invisible in others.
- This is also a trust feature: if the app asks for these fields, it should use them.

Implementation sketch:

- Add `WorkUnitPerformanceStats`.
- Add formatters by unit key and value type.
- Extend history rows and share/export with work-unit summaries.
- Add focused tests for `encodeWorkUnitValues`, `decodeWorkUnitValues`, logged signal counting, and work-unit history presentation.

### 4. Explainable Smart Picker Ranking

The picker now has logged session counts, recommendation bias, focus preservation, and muscle filters. The next step is to make ranking explain itself.

What it adds:

- Picker reason chips:
  - "Due muscle"
  - "Logged 7x"
  - "Fresh pick"
  - "Bias more"
  - "Matches upper focus"
  - "No recent exposure"
- Sorting modes:
  - Best for today
  - Most familiar
  - Freshest variation
  - Due muscle first
- A tiny "why this" drawer for generated active-session suggestions.

Why this fits:

- The app already has the signals.
- Your recent changes suggest you care about confidence and trust in generated picks.
- This reduces the feeling that generated add-exercise suggestions are arbitrary.

Implementation sketch:

- Introduce `ExercisePickerReason`.
- Compute reasons next to the existing library query/facet model.
- Keep scoring deterministic and testable.
- Show at most 2 visible reason chips per item.

Tests:

- due/overdue freshness produces the highest-priority reason
- logged-session count reason formats correctly
- bias more/less reason matches repository bias state
- focus preservation reason appears for history-reused generated picks

### 5. Freshness-Aware Completion Receipt Bridge

Completion receipts are already in the project direction. The next useful slice is specifically tied to the new freshness and effort data.

What it adds:

- "Freshness updated" section:
  - refreshed muscles
  - due muscles still pending
  - next best split/focus
- "Effort captured" section:
  - last RIR by exercise
  - where the user trained close to failure
  - where the next prescription should stay conservative
- One next action:
  - "Next: lower refresh"
  - "Next: repeat pull and beat last set 2"
  - "Next: recovery-biased session"

Why this fits:

- It turns logging RIR and freshness into a closed loop.
- It avoids making the active workout UI too busy.
- It creates a satisfying endpoint that points to the next session.

Implementation sketch:

- Reuse `CompletionReceiptSupport.kt`.
- Add a compact `FreshnessReceiptSummary`.
- Persist enough fields to replay the summary later without recalculating against changed thresholds.

## Longlist: 30 Ideas Considered

1. Live Freshness Gap Filler for active workouts.
2. One-tap "match last session" for an exercise.
3. One-tap "beat last session" progression.
4. Work-unit/cardio history stats and PRs.
5. Explainable smart picker ranking.
6. Freshness-aware completion receipt bridge.
7. Active-session micro-confirmations when a muscle is refreshed.
8. "Next up" smart ordering based on due muscles, fatigue, and equipment.
9. Time-left-aware finisher suggestions.
10. Set-level delta labels versus previous session.
11. Exercise-specific RIR trend and prescription adjustment.
12. A "minimum effective refresh" mode for rushed sessions.
13. Freshness dashboard action buttons for each overdue row.
14. Muscle-targeted exercise picker launch from active details.
15. Work-unit interval templates for treadmill, rower, bike, and stairmaster.
16. Work-unit value steppers and input masks.
17. Automatic rest timer tuning based on RIR and exercise type.
18. "Do not repeat this today" guard for generated active-session exercises.
19. Exercise substitution reasons tied to friction and freshness.
20. Weekly freshness recap.
21. Freshness debt trend over time.
22. Stronger active workout notification with current exercise/rest state.
23. Voice-less, tap-only logging mode for sweaty hands.
24. Custom exercise muscle mapping repair flow.
25. PR detection for cardio/work-unit metrics.
26. "Save this successful session as template" after strong completion.
27. Smart target muscle rotation, not just one saved muscle target.
28. Overdue muscle mute/snooze for injury or soreness.
29. Better empty states for untracked muscles.
30. Developer debug panel for freshness contribution math during active workouts.

## Next Best 10 After The Top 5

1. Active-session micro-confirmations.
   - Add this as part of the Live Freshness Gap Filler if possible. It is tiny but makes the system feel alive.
2. Time-left-aware finishers.
   - Useful once the app can estimate remaining session time and due muscle gaps.
3. Work-unit input masks and steppers.
   - Duration, pace, and distance fields need better ergonomics than generic text entry.
4. Smart target muscle rotation.
   - The profile target muscle is useful, but the app can eventually rotate targets based on freshness/history.
5. Overdue muscle snooze.
   - Necessary for soreness, pain, travel, or deliberate specialization blocks.
6. Exercise substitution reasons.
   - Builds trust when the app says "swap this for that."
7. Freshness debt trend.
   - Good for History, but less urgent than action inside the workout.
8. Active notification with current rest/exercise state.
   - Nice ergonomics, but platform permission complexity makes it less attractive than in-app improvements.
9. Save successful session as template.
   - Good retention loop, especially after strong completion receipts.
10. Custom exercise muscle mapping repair.
   - Important once custom exercises become common; otherwise freshness may be silently wrong.

## First Implementation Slice I Would Actually Build

Build only the action-builder slice first.

Acceptance criteria:

- During an active workout, if at least one due/overdue muscle exists, the app can compute one best next action.
- If the best action maps to a planned untouched exercise, tapping it opens that exercise.
- If the best action maps to a due muscle not in the workout, tapping it opens the add-exercise picker filtered to that muscle.
- If the user completes enough sets to refresh the muscle, the action disappears or moves to the next gap.
- No educational copy appears mid-workout.

Files likely touched:

- `app/src/main/java/dev/toastlabs/toastlift/ui/ToastLiftApp.kt`
- `app/src/main/java/dev/toastlabs/toastlift/ui/ToastLiftViewModel.kt`
- `app/src/test/java/dev/toastlabs/toastlift/ui/ActiveSessionWorkoutDetailsTest.kt`
- `app/src/test/java/dev/toastlabs/toastlift/ui/ToastLiftViewModelTest.kt`

Pure functions to add first:

- `buildActiveWorkoutFreshnessAction(...)`
- `activeWorkoutFreshnessActionReason(...)`
- `activeWorkoutFreshnessActionCta(...)`

This keeps the first implementation narrow and testable before adding generated finishers.

## Risks And Guardrails

- Do not shame the user for not clearing every overdue muscle.
- Do not suggest extra work after a hard session if the user already completed the planned work.
- Do not let tiny secondary contributions pretend to satisfy a full split bucket.
- Do not make cardio refresh unrelated strength muscles.
- Keep the active log screen stable. This should be a compact action strip, not a new dashboard.
- Prefer deterministic ranking over vague "AI" phrasing.

## Bottom Line

The next best improvement is not more information. It is turning the information you just added into an exact next action.

Build the Live Freshness Gap Filler first. It is the cleanest continuation of the last five commits, it uses existing app primitives, and it matches the apparent product taste: concrete, data-faithful, in-the-flow, and useful while the user is actually training.
