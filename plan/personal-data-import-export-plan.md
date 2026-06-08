# Personal Data Import/Export Plan

## Summary

The current JSON export is not sufficient for a full fresh-install restore. It is a useful machine-readable snapshot and includes most completed workout history, but it is not a full-fidelity backup format and there is no import flow today.

A second table-by-table audit found additional restore gaps beyond the first pass: incomplete child-row primary keys, missing raw JSON snapshot fields, incomplete active/abandoned session columns, adaptive program rows missing from the delete-all-personal-data path, and no custom-exercise conflict/remap policy for imports.

For a user with long-term workout history, do not rely on the existing export alone before uninstalling. The safest near-term path remains installing updates with the same signing key so Android preserves app data.

## Second-Pass Additional Findings

The second audit added these specific gaps to the findings:

- Raw `ab_flags_snapshot_json` and `completion_receipt_snapshot_json` are not preserved as raw database strings.
- Several child primary keys are missing from export: set IDs, template exercise IDs, and feedback signal IDs.
- `performed_sets.completed_at_utc` is missing from completed workout export.
- Active and abandoned workout export is still partial: pause state, selected exercise index, workout metadata, active exercise completion sequence, and set timestamps/IDs are not fully covered.
- Custom exercise import must regenerate `custom_exercises_snapshot.json` after restoring custom exercise rows.
- Import needs a conflict/remap policy if a restored custom exercise collides with a newer bundled catalog exercise.
- Delete-all-personal-data currently does not clear adaptive program tables.
- A persistence sweep found SQLite plus the custom exercise snapshot file; no SharedPreferences or DataStore user-data path was found.

Note: `plan/` is currently ignored by `.gitignore`, so this findings document exists in the workspace but will not be included in Git unless the ignore rule changes or the file is force-added.

## Third-Pass Meticulous Findings

The third audit enumerated the SQLite asset schema, runtime-created tables in `ToastLiftDatabase`, export code in `UserRepository`, custom exercise snapshot code, OS backup rules, and durable storage searches for SharedPreferences/DataStore/file writes.

### Storage Inventory

Persistent user/runtime storage found:

- `toastlift.db`
- `custom_exercises_snapshot.json`

No SharedPreferences or DataStore user-data path was found. `rememberSaveable` usages are Compose UI state and should be treated as transient, not backup data.

System/bundled storage that should not be treated as user data:

- bundled catalog tables for built-in exercises
- `training_split_programs`
- `location_modes`
- `import_metadata`
- `exercise_filter_summary` view
- generated/cache copy of the bundled catalog database

Mixed catalog tables that contain system rows plus user-created custom exercise rows:

- `exercises`
- `exercise_muscles`
- `exercise_equipment`
- `exercise_movement_patterns`
- `exercise_planes_of_motion`
- `exercise_synonyms`
- `exercise_work_units`

The current custom exercise export covers custom `exercises`, child taxonomy rows, and synonyms. It does not currently export custom `exercise_work_units`; today custom exercise creation does not appear to create work-unit definitions, but if that feature is added later, export/import must include custom work units too.

### New Third-Pass Gaps

#### Catalog Compatibility Is Not Explicit

Workout history, templates, active/abandoned sessions, program rows, preferences, descriptions, video links, and feedback signals all reference exercises by `exercise_id`.

The export does not include a catalog fingerprint/version, a referenced-exercises manifest, or exercise slugs for every referenced bundled exercise. That means an import into a newer app build has no robust way to prove that every referenced bundled `exercise_id` still means the same exercise.

Required change:

- Include catalog compatibility metadata, ideally from `import_metadata` plus an app/database version.
- Include a `referenced_exercises` section or add `exercise_slug`/`exercise_name` snapshots wherever exercise IDs are exported.
- On import, validate every referenced bundled exercise ID or remap by stable slug/name before inserting rows with foreign keys.

#### Existing Custom Exercise Canonicalization Is Incomplete

`ToastLiftDatabase.canonicalizeBundledCustomExercises()` remaps a custom exercise to a bundled exercise when their normalized names collide. It currently updates:

- `workout_template_exercises.exercise_id`
- `performed_exercises.exercise_id` and `exercise_name`
- `active_exercises.exercise_id` and `exercise_name`
- `abandoned_exercises.exercise_id` and `exercise_name`

It does not update every table that can reference an exercise:

- `planned_session_exercises.exercise_id`
- `program_exercise_slots.exercise_id`
- `program_exercise_slots.evolution_target_exercise_id`
- `exercise_preferences.exercise_id`
- `workout_feedback_signals.exercise_id`

Rows in these tables can become stale if the custom exercise is deleted. Also, user-owned rows with foreign keys such as `exercise_generated_descriptions` and `exercise_user_video_links` are deleted by cascade instead of being transferred to the bundled replacement.

Required change:

- Create one central exercise-ID remap routine and use it for catalog canonicalization and import conflict resolution.
- Remap every exercise reference table consistently.
- Decide whether user notes/preferences/generated descriptions/video links should transfer from a custom exercise to the bundled replacement when names collide.

#### Program Tables Need Explicit Delete/Import Ordering

Adaptive program tables reference each other but do not declare broad `ON DELETE CASCADE` coverage. A replace-style import must clear child tables before parent tables.

Safe clear order should be approximately:

- `program_events`
- `program_checkpoints`
- `program_exercise_slots`
- `planned_session_exercises`
- `planned_sessions`
- `planned_weeks`
- `training_programs`

The same ordering matters for any future delete-all-personal-data fix.

#### Location Mode Stable Keys Are Missing From Export

Profile and equipment export include `location_mode_id` and a display name, but not `location_modes.name` (`home`/`gym`).

IDs are stable today, but a robust import should not depend only on numeric IDs and mutable display names.

Required change:

- Export `location_mode_key` from `location_modes.name` for profile active location and each equipment inventory row.
- Import by stable key first, then fall back to ID for old exports.

#### Exact Table Restore Needs A Few More Low-Level Fields Documented

These are lower risk than workout history, but they should be either exported or explicitly treated as constants/non-user state:

- `user_profile.user_id` is not exported. It is always `1`, so this is low risk, but a table round-trip test should account for it.
- SQLite `sqlite_sequence` values are not exported. This is usually acceptable if imports preserve explicit IDs and then verify future inserts, but a replace-style import should ensure autoincrement sequences cannot collide.
- Active/abandoned set fields are stored as TEXT for reps and weight. An importer must preserve these as raw strings and not coerce them through numeric parsing.

## Delete Personal Data Correctness Plan

This section is an engineering plan for making the in-app delete-personal-data feature complete. It is not a legal compliance opinion. The implementation should avoid claiming GDPR compliance until legal/product review confirms the expected behavior, wording, backups, and support process.

### Target Behavior

The Profile delete action should remove all user-created and runtime-derived personal data stored by the app on the device, while keeping bundled/system catalog data needed for the app to launch.

After deletion:

- No workout history should remain.
- No active or abandoned workout should remain.
- No workout templates should remain.
- No adaptive training program state should remain.
- No exercise favorites, hidden/banned state, learned preference signals, personal notes, generated descriptions, or user video links should remain.
- No custom exercises or custom exercise snapshot file should remain.
- No profile/onboarding choices, equipment selections, experiment assignments, movement restrictions, or next-focus state should remain.
- No local active workout notification or rest timer should remain.
- App UI should return to a clean first-run/onboarding state or an explicitly empty state.

### Current Delete Path

Current entry point:

- `ToastLiftViewModel.deleteAllPersonalData()`

Current repository deletes:

- `performed_workouts`
- `workout_templates`
- `workout_feedback_signals`
- `exercise_preferences`
- `exercise_generated_descriptions`
- `exercise_user_video_links`
- `movement_restrictions`
- `equipment_inventory`
- `experiment_assignments`
- `user_profile`
- custom exercises where `is_post_install_llm_generated = 1`
- `custom_exercises_snapshot.json`
- `active_workouts`
- `abandoned_workouts`

The current path also cancels the rest timer and active workout notification.

### Current Gaps

The delete feature is incomplete because it does not delete adaptive program tables:

- `training_programs`
- `planned_weeks`
- `planned_sessions`
- `planned_session_exercises`
- `program_exercise_slots`
- `program_checkpoints`
- `program_events`

This means a user can delete personal data and still retain program state, planned sessions, skipped/completed session state, slot/SFR state, checkpoint summaries, and program event history.

The operation is also split across multiple repositories instead of one coordinated transaction. If one step fails after an earlier delete succeeds, the app can be left partially wiped.

### Data To Delete

User-owned SQLite tables:

- `user_profile`
- `equipment_inventory`
- `experiment_assignments`
- `movement_restrictions`
- `exercise_preferences`
- `exercise_generated_descriptions`
- `exercise_user_video_links`
- `workout_templates`
- `workout_feedback_signals`
- `performed_workouts`
- `active_workouts`
- `abandoned_workouts`
- `training_programs`
- `planned_weeks`
- `planned_sessions`
- `planned_session_exercises`
- `program_exercise_slots`
- `program_checkpoints`
- `program_events`

User-owned rows inside mixed catalog tables:

- `exercises` where `is_post_install_llm_generated = 1`
- child rows reached by cascade from those custom exercises:
  - `exercise_muscles`
  - `exercise_equipment`
  - `exercise_movement_patterns`
  - `exercise_planes_of_motion`
  - `exercise_synonyms`
  - `exercise_work_units`

User-owned app-private files:

- `custom_exercises_snapshot.json`

Transient local state to clear:

- active workout notification
- rest timer notification/state
- pending export/share state in UI memory
- active session UI state
- custom exercise draft UI state

System/bundled data to keep:

- bundled exercise catalog rows
- `training_split_programs`
- `location_modes`
- `import_metadata`
- `exercise_filter_summary`

### Delete Ordering

Use explicit child-to-parent ordering for tables without reliable broad cascade coverage.

Recommended delete order:

- cancel timers/notifications
- `program_events`
- `program_checkpoints`
- `program_exercise_slots`
- `planned_session_exercises`
- `planned_sessions`
- `planned_weeks`
- `training_programs`
- `active_workouts`
- `abandoned_workouts`
- `performed_workouts`
- `workout_templates`
- `workout_feedback_signals`
- `exercise_user_video_links`
- `exercise_generated_descriptions`
- `exercise_preferences`
- `movement_restrictions`
- `equipment_inventory`
- `experiment_assignments`
- `user_profile`
- custom exercise rows where `is_post_install_llm_generated = 1`
- delete `custom_exercises_snapshot.json`

After the transaction, reload system data and return the app to a first-run or clean empty state.

### Transaction Boundary

The delete operation should be a single coordinated delete path. Prefer one repository/service that owns the full wipe transaction rather than separate partial deletes across `UserRepository`, `WorkoutRepository`, `CustomExerciseRepository`, and `ProgramRepository`.

Required behavior:

- Start one database transaction.
- Delete all user-owned database rows.
- Commit only if all database deletes succeed.
- Delete app-private user files after the database transaction succeeds.
- If file deletion fails, surface an error and do not claim full deletion.
- Refresh UI only after persistent deletion succeeds.

SQLite foreign keys should remain enabled during deletion. If explicit child-first deletes are used, disabling foreign keys should not be necessary.

### Backup Caveat

The app has Android backup enabled and includes:

- `toastlift.db`
- `custom_exercises_snapshot.json`

The delete feature can remove local on-device app data. It should not claim to erase OS-managed cloud/device-transfer backups unless that behavior is explicitly designed, tested, and reviewed.

Plan options:

- Keep Android backup enabled, but make the UI wording precise: deletion removes data stored by the app on this device.
- Add product/legal review for whether backup behavior needs a stronger user-facing warning.
- Consider whether future privacy requirements need backup exclusion, backup invalidation, or a documented restore-after-delete policy.

### UI Copy Requirements

The confirmation dialog should say what will be deleted using user-facing categories:

- profile and setup choices
- equipment selections
- workout history
- active/abandoned workouts
- templates
- favorites, notes, learned preferences, and video links
- custom exercises
- adaptive program state

The dialog should not overclaim deletion from cloud backups or external exports. If the app continues to support manual JSON exports, the copy should make clear that files the user previously exported are outside the app's control.

### Verification Tests

Add a deletion fixture that creates all user-owned data, runs delete, and asserts that every user-owned table/file is empty or absent.

Minimum fixture:

- populated profile with non-default settings
- equipment inventory changes
- experiment assignments
- movement restrictions
- exercise preferences, generated descriptions, and user video links
- custom exercise plus child rows and snapshot file
- workout templates
- feedback signals
- completed workout history with exercises and sets
- active workout
- abandoned workout
- adaptive program with all child tables populated

Post-delete assertions:

- all user-owned tables listed above have zero rows
- no custom exercises remain
- bundled exercises remain
- `training_split_programs`, `location_modes`, and `import_metadata` remain
- `custom_exercises_snapshot.json` does not exist
- active workout notification and rest timer are cancelled
- UI state no longer references deleted active session, custom draft, export payload, or workout share

Add a regression guard that fails if a new user-owned table is added without being classified as delete-required, export-required, or system-owned.

## Current Export Coverage

The current export is built in `UserRepository.exportPersonalDataJson()` and contains:

- Profile settings.
- Home/Gym equipment inventory.
- Exercise preferences, favorites, hidden/banned state, preference score, and notes.
- Generated exercise descriptions.
- User exercise video links.
- Movement restrictions.
- Custom exercises from `CustomExerciseRepository.exportCustomExercisesJson()`.
- Workout templates and template exercises.
- Workout feedback signals.
- Completed workouts with exercises and sets.
- Active workout snapshot.
- Abandoned workout snapshot.

Custom exercises are exported with stable `exercise_id` values and child taxonomy rows, which is important because workout logs and templates reference exercise IDs.

Android OS backup is separate from this JSON export. The app's backup XML includes `toastlift.db` plus `custom_exercises_snapshot.json`, but the current in-app JSON export only writes the JSON payload. A JSON import implementation must not assume the app-private snapshot file exists after a fresh install.

## Major Gaps

### No Import Path

The app currently only exports JSON through Android's document creator. There is no import picker, parser, validator, or database restore code.

Expected future UX:

- On fresh launch, offer `Import existing data` alongside onboarding.
- From Profile, offer `Import data from JSON`.
- Import should run in a single transaction and either fully succeed or leave the existing database unchanged.

### Completed Workout History Is Not Fully Lossless

Completed workout headers, exercises, and sets are mostly exported, including reps, weights, recommendation fields, and work-unit JSON.

Missing fields for exact restore:

- `performed_sets.performed_set_id` is not exported.
- `performed_sets.completed_at_utc` exists in the database and is used by history/detail queries, but it is not exported.
- `performed_workouts.ab_flags_snapshot_json` and `completion_receipt_snapshot_json` are exported only as parsed friendly objects (`abFlags` and `completionReceipt`), not as the raw database strings. If an older or malformed legacy payload cannot be parsed, the current export would drop the original value.

This should be added before claiming the export can restore history exactly.

### Adaptive Program Data Is Missing

The export does not include adaptive program tables:

- `training_programs`
- `planned_weeks`
- `planned_sessions`
- `planned_session_exercises`
- `program_exercise_slots`
- `program_checkpoints`
- `program_events`

Without these, a restored install would lose the current program, planned sessions, skipped/completed session state, checkpoints, slot evolution, SFR state, and program event history.

### Experiment Assignments Are Missing

`experiment_assignments` is deleted by the personal-data deletion path, so it is treated as personal data. It is not currently exported.

Impact:

- Restored installs may get different experiment variants.
- Completed workouts preserve their A/B snapshots, but future UI behavior may change after restore.

### Profile Export Is Missing At Least One Current Setting

The schema includes `dev_session_set_swipe_complete_enabled`, but the profile export query does not include it.

Any import/export contract should be audited against the full `user_profile` schema whenever profile fields are added.

### Active And Abandoned Workout Snapshots Are Partial

Current active/abandoned exports include basic workout header, exercises, and sets, but omit several persisted fields:

- `subtitle`
- `estimated_minutes`
- `session_format`
- `selected_exercise_index` for active workout
- pause state fields: `is_paused`, `paused_at_utc`, `accumulated_paused_seconds`
- active exercise `completion_sequence`
- active/abandoned set primary keys: `active_set_id`, `abandoned_set_id`
- set `completed_at_utc`

If the requirement is "everything as it was before uninstall", these should be exported and imported.

### Some Child Table Primary Keys Are Not Exported

Several parent rows are exported with stable IDs, but some repeated child rows are not:

- `workout_template_exercises.template_exercise_id`
- `workout_feedback_signals.signal_id`
- `performed_sets.performed_set_id`
- `active_sets.active_set_id`
- `abandoned_sets.abandoned_set_id`

An importer could recreate these child rows with new IDs if no other table references them. For a full-fidelity backup and table round-trip tests, export and import the IDs anyway.

### Custom Exercise Restore Needs A Snapshot And Conflict Policy

Custom exercises are exported from database rows, not by embedding the app-private `custom_exercises_snapshot.json` file. That is acceptable if import restores the database rows and then rewrites the snapshot file from the restored custom exercises.

The importer also needs a policy for custom exercises whose `exercise_id`, slug, or normalized name conflicts with a bundled catalog exercise in a newer app build. If a custom exercise must be remapped or canonicalized to a bundled exercise, every reference in workouts, templates, active/abandoned sessions, preferences, descriptions, video links, and adaptive program rows must be remapped consistently.

### Delete-All Personal Data Does Not Clear Program Tables

The current UI deletion path clears profile, workout history, templates, preferences, custom exercises, active workout, and abandoned workout. It does not clear adaptive program tables:

- `training_programs`
- `planned_weeks`
- `planned_sessions`
- `planned_session_exercises`
- `program_exercise_slots`
- `program_checkpoints`
- `program_events`

This is not directly an export gap, but it is a personal-data coverage gap and should be fixed alongside import/export work. A replace-style import should also clear these tables before restoring.

## Required App Changes

### 1. Define A Full Backup Schema

Create a documented backup schema version that maps every user-owned table to JSON.

Recommended sections:

- `profile`
- `equipment_inventory`
- `experiment_assignments`
- `exercise_preferences`
- `exercise_generated_descriptions`
- `exercise_user_video_links`
- `movement_restrictions`
- `custom_exercises`
- `workout_templates`
- `workout_feedback_signals`
- `completed_workouts`
- `active_workout`
- `abandoned_workout`
- `programs`

The `programs` section should include all program child rows nested under each program or represented as normalized arrays with stable IDs.

Include export metadata that helps imports fail safely:

- exporter app version / database version
- catalog fingerprint/version
- backup schema version
- section row counts
- date range for completed workout history
- optional checksum/hash per section

### 2. Make Export Full-Fidelity

Add missing columns to the export:

- raw `ab_flags_snapshot_json`
- raw `completion_receipt_snapshot_json`
- `performed_sets.completed_at_utc`
- child-row primary keys where currently omitted
- active/abandoned workout metadata fields
- active/abandoned exercise `completion_sequence`
- active/abandoned set `completed_at_utc`
- profile `dev_session_set_swipe_complete_enabled`
- `experiment_assignments`
- adaptive program tables

For restore reliability, prefer exporting raw persisted values in addition to friendly display names.

After exporting/importing custom exercises, ensure `custom_exercises_snapshot.json` can be regenerated from the restored rows.

### 3. Add Import Flow

Add a JSON document picker and import flow.

Import should:

- Validate top-level app name and supported `schema_version`.
- Validate required sections before mutating the database.
- Show a clear summary before import, such as workout count, date range, custom exercise count, template count, and active program presence.
- Run inside a single transaction.
- Restore custom exercises before any rows that reference their exercise IDs.
- Resolve custom exercise ID/name conflicts before restoring dependent rows.
- Validate/remap all bundled exercise references before inserting dependent rows.
- Restore parent rows before child rows.
- Preserve IDs where history/templates/program rows reference them.
- Rebuild derived/default catalog data only after user data is restored.

### 4. Decide Conflict Policy

For a fresh install, the import can replace empty personal data.

For an existing install, define one explicit policy:

- Replace all current personal data with backup contents, or
- Merge backup into current data.

Replacement is simpler and safer for the first implementation. Merge requires duplicate detection for workouts, templates, custom exercises, preferences, and program state.

### 5. Add Round-Trip Tests

Add tests that create a populated database, export JSON, import into a fresh database, then compare user-owned tables.

Minimum test fixture should include:

- Profile with all settings changed from defaults.
- Home and Gym equipment changes.
- Favorites, hidden/banned exercises, notes, generated descriptions, video links.
- Custom exercise used by a workout and template.
- Completed workout with multiple exercises, completed/incomplete sets, set completion timestamps, RIR/RPE, recommendations, work-unit values, receipt snapshot.
- Active workout with pause state and selected exercise index.
- Abandoned workout.
- Workout template.
- Feedback signals.
- Active adaptive program with planned weeks/sessions/exercises, slots, checkpoints, and events.
- Experiment assignments.
- Raw A/B and completion receipt snapshot JSON values.
- Custom exercise collision/remap fixture if a bundled exercise with the same normalized name exists.
- Catalog compatibility/remap fixture where a referenced exercise ID must be validated against slug/name.
- Insert-after-import checks for autoincrement tables to ensure new rows do not collide with restored IDs.

The tests should fail whenever a new user-owned column is added but not covered by export/import.

## Practical Risk Assessment

Current export is good enough for manual inspection and partial recovery of major workout logs.

Current export is not good enough for:

- One-click fresh-install restore.
- Exact preservation of long-term history semantics.
- Preserving adaptive program state.
- Recreating an in-progress workout exactly.

Until import/export is upgraded and tested, preserving Android app data through matching signing keys is still the safest migration strategy.
