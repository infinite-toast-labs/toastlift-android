# ToastLift internal tester rubric

Use this rubric for every release candidate before a production rollout. It is
designed for internal testers who may not know the implementation details. Test
results are evidence for a release decision, not a substitute for automated
tests, signed-bundle verification, or Play Console review.

## Which build to test

| Build | How to identify it | What the result proves |
| --- | --- | --- |
| Debug | Package `dev.toastlabs.toastlift.debug`; installed with `make install-device-debug` | Exploratory testing of the full development surface. Debug-only AI, programs, templates, custom exercises, and Exercise Family Tree findings do not describe the v1 Play product. |
| Staging | Package `dev.toastlabs.toastlift.staging`; installed with `make install-device-stage` or from the staging artifact | Primary functional and visual gate. It uses the production feature configuration while retaining AppReveal and localhost network access for testing. |
| Play internal testing | Package `dev.toastlabs.toastlift`; installed from Google Play's internal-testing link | Final delivery gate. It proves Play installation/update behavior, production signing/delivery, and the actual production surface. This is the only tester build that can confirm AppReveal is absent. |

Run the complete scored rubric on **staging** first. After it passes, repeat all
items marked **Play gate** on a Play-delivered internal-testing build. Do not use
staging's debug-only `INTERNET` permission to infer production network behavior.

## Test record

Copy this block into the test report and complete every field:

```text
Release/version:
Commit or Play release name:
Build: debug | staging | Play internal testing
Install source:
Tester:
Date and time:
Device manufacturer/model:
Android version/API level:
Screen size or form factor:
Fresh install or update:
Network state:
Overall result: PASS | FAIL | BLOCKED
Score: ___ / ___ applicable points = ___%
Issues filed:
Notes/evidence location:
```

Use disposable workout data for export/delete testing. Never include personal
health details, secrets, signing material, or private contact information in a
test report, screenshot, screen recording, or log.

## Severity and release decision

| Severity | Definition | Examples | Release effect |
| --- | --- | --- | --- |
| S0 — critical | Security/privacy violation, unrecoverable data loss, or a crash loop preventing use. | Production unexpectedly exposes a networked/debug feature; deleting one item destroys unrelated data; app cannot launch after update. | Immediate release blocker. |
| S1 — high | A required v1 journey cannot be completed, or the production contract is wrong. | Cannot generate/start/finish a workout; completed workout is missing from history; a production-disabled feature is reachable. | Release blocker. |
| S2 — medium | A journey is usable only with a workaround or produces materially confusing results. | Filters return the wrong exercises; freshness or weekly targets fail to update until restart; major clipping on a supported screen. | Fix before rollout unless the release owner documents an explicit exception. |
| S3 — low | Cosmetic or low-impact usability defect with no loss of function. | Minor spacing, copy, animation, or non-blocking accessibility issue. | May be deferred with an issue. |

The candidate passes only when all of these are true:

- No open S0 or S1 findings.
- Every hard gate in the rubric passes.
- The normalized score is at least 90%.
- Every S2/S3 deduction has a reproducible issue or an existing issue link.
- Staging passes before the Play internal-testing build is evaluated.
- The Play gate passes on at least one physical phone installed from Google Play.

For each scored item, award full points for **Pass**, half points for a usable
journey with an S2/S3 defect, and zero for **Fail**. Mark **N/A** only when the
release owner approves it; remove approved N/A points from the denominator.
A high score never overrides a hard-gate or S0/S1 failure.

## Scored rubric

| Area | Points | Test and expected result | Hard gate | Result/evidence |
| --- | ---: | --- | :---: | --- |
| Install and launch | 4 | Install the intended build, launch from the system launcher, and confirm the app reaches a usable first screen without a crash, blank screen, or development warning. | Yes | |
| First-run experience | 4 | Complete or dismiss onboarding as offered. Deny notification permission once; the app must remain usable and must not repeatedly trap the tester in the prompt. Relaunch and confirm the choice is respected. | Yes | |
| Generate a workout | 8 | Choose a duration, focus, and available equipment, then generate a session. The workout should contain plausible exercises and fit the chosen constraints. | Yes | |
| Adjust a generated workout | 4 | Review and make at least one supported adjustment before starting. The visible session must reflect the change without duplication or lost exercises. | No | |
| Start and log a workout | 8 | Start the generated session, log multiple sets across at least two exercises, and verify edits to reps/weight/completion remain visible while navigating within the workout. | Yes | |
| Resume and finish | 8 | Background and reopen the app during the workout, resume successfully, then finish it. The app must not lose logged sets or create duplicate completed workouts. | Yes | |
| History and details | 7 | Open workout history and the completed workout. Date, exercises, sets, and completion state must match what was logged. Relaunch once and confirm the record persists. | Yes | |
| Training freshness | 4 | Confirm freshness is visible and responds plausibly to the completed training. Record an issue for missing, contradictory, or obviously stale output. | No | |
| Weekly muscle targets | 4 | Confirm weekly targets are visible and the completed session contributes plausibly to the relevant muscles without obvious duplication. | No | |
| Token feedback | 3 | Confirm the completed-workout token feedback appears and does not increment repeatedly for the same completion. | No | |
| Exercise library | 5 | Search for an exercise, apply at least one filter, open an exercise detail, and return without losing the intended navigation state. | No | |
| Favorites and metadata | 5 | Favorite/unfavorite an exercise and inspect experience level, movement type, or history metadata. State and labels must remain consistent after navigation. | No | |
| Export data | 7 | With disposable workout data present, use the on-device export control. The share/save flow must open, produce a non-empty artifact, and avoid exposing secrets or unrelated device data. | Yes | |
| Delete data | 8 | After preserving any needed export, use the in-app delete control and confirm it clearly describes scope. Delete the disposable data, relaunch, and verify the intended data is gone while the app remains usable. | Yes | |
| Production feature surface | 6 | On staging and the Play build, verify AI, programs/plans, saved templates, manual workout builder, custom exercises, Exercise Family Tree, advanced history dashboards, bounty cards, and developer settings are not reachable through navigation, menus, or obvious deep paths. | Yes | |
| Privacy and support links | 4 | Open the public privacy policy from the in-app Privacy section and verify it loads at `https://www.toastlabs.dev/toastlift/privacy/`. Confirm the wording matches local storage, export/delete, no accounts, no ads, and no cloud sync. | Yes | |
| Play delivery | 4 | **Play gate:** install from the internal-testing link, launch the Play-delivered app, confirm package/version, and exercise generate → log → finish once. If testing an update, confirm existing disposable history survives. | Yes | |
| Production-only behavior | 3 | **Play gate:** confirm there is no AppReveal/debug UI or development tooling. Report any unexpected network/debug behavior as S0; do not use traffic interception that violates tester or device policy. | Yes | |
| Visual quality and accessibility | 4 | Check portrait layout, keyboard/input behavior, system back, readable contrast, touch targets, text scaling, and screen-reader labels on the main journey. No primary action or required text may be clipped or unreachable. | No | |
| **Total** | **100** | | | |

## Required device coverage

For the first production release, collect at least:

1. One current Android physical phone for the complete staging rubric.
2. One physical phone for the Play internal-testing gate; this may be the same
   device after uninstalling or clearly distinguishing the package.
3. One additional supported configuration chosen for risk: Android API 26 or
   another older supported version, a small screen, large text, or a tablet.

If an available device cannot satisfy the intended coverage, mark the report
**BLOCKED** and state the missing configuration rather than guessing.

## How to report a finding

Create one issue per independently fixable defect. Use this format:

```text
Title: [S0|S1|S2|S3] Short user-visible problem

Build/version and commit:
Install source:
Device and Android version:
Fresh install or update:
Preconditions:
Steps to reproduce:
1.
2.
3.

Expected:
Actual:
Frequency: always | intermittent (___ / ___ attempts)
Workaround:
Evidence: screenshot/video/log link
Rubric row:
```

Attach the minimum evidence needed to reproduce the problem. For a crash or
freeze, include the relevant sanitized log excerpt when available. For visual
issues, include one screenshot with the affected area visible. For data or
privacy issues, describe the observation without attaching exported personal
data.

## Tester sign-off

```text
[ ] I tested the build and install source recorded above.
[ ] I completed every applicable hard gate.
[ ] I filed or linked every score deduction.
[ ] I removed personal data and secrets from evidence.
[ ] I did not treat debug-only features as the production product.
[ ] For Play sign-off, I installed the build from Google Play internal testing.

Tester name:
Result: PASS | FAIL | BLOCKED
Signature/initials:
Date:
```
