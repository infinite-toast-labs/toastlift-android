# Personal Data Import/Export Plan

## Summary

The current JSON export is not sufficient for a full fresh-install restore. It is a useful machine-readable snapshot and includes most completed workout history, but it is not a full-fidelity backup format and there is no import flow today.

A second table-by-table audit found additional restore gaps beyond the first pass: incomplete child-row primary keys, missing raw JSON snapshot fields, incomplete active/abandoned session columns, adaptive program rows missing from the delete-all-personal-data path, and no custom-exercise conflict/remap policy for imports. Later goal-focused passes added import preflight, non-empty-database safety, atomic file writes, OS/remote-data scope, and restore-after-delete caveats.

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

## Fourth-Pass Findings

This pass focused on lower-level privacy/delete semantics, SQLite side effects, OS backup behavior, and scheduled/background state.

### No Additional Durable App Storage Found

Another storage sweep found:

- no SharedPreferences user-data storage
- no DataStore usage
- no WorkManager jobs
- no AlarmManager scheduled jobs

Notifications are immediate local notifications only. The active workout notification is already cancelled by the current delete path, and the rest timer is an in-memory coroutine/UI state rather than durable scheduled work.

### SQLite Deletes May Leave Recoverable Bytes

The delete-data plan currently says which rows to delete, but not how to deal with SQLite's physical storage behavior.

Plain `DELETE FROM` removes logical rows, but deleted content can remain in:

- free pages inside `toastlift.db`
- `toastlift.db-wal`
- `toastlift.db-shm`
- rollback journal files such as `toastlift.db-journal`, depending on SQLite journal mode/device state

If the feature is meant to be a strong local privacy wipe, the implementation should explicitly handle this.

Recommended plan:

- Run the logical delete transaction first.
- Checkpoint/truncate WAL after the transaction, for example via `PRAGMA wal_checkpoint(TRUNCATE)` if WAL is active.
- Run `VACUUM` or an equivalent database rebuild after deletion to purge freed pages.
- Consider `PRAGMA secure_delete = ON` before delete operations if acceptable for performance.
- Verify on-device that database sidecar files do not retain old user data after deletion.

Important implementation constraint:

- `VACUUM` cannot run inside an active transaction, so the delete flow needs a carefully staged transaction/checkpoint/vacuum sequence and clear error reporting if compaction fails.

### `sqlite_sequence` Can Leak Approximate Prior Use

The plan already notes `sqlite_sequence` for import collision handling. For delete-data correctness, it also matters as residual metadata.

After deleting user rows, `sqlite_sequence` can still contain high values for user-owned AUTOINCREMENT tables, which may reveal approximate prior counts for workouts, templates, sets, links, restrictions, program rows, and other personal activity.

Recommended plan:

- After deleting user-owned rows, reset `sqlite_sequence` entries for user-owned AUTOINCREMENT tables.
- Keep or restore only sequence state required by system-owned/bundled data.
- Add a post-delete assertion that user-owned sequence entries are absent or reset.

### Android Backup Rules Need Sidecar Review

The app's backup rules include:

- `toastlift.db`
- `toastlift.db-wal`
- `toastlift.db-shm`
- `custom_exercises_snapshot.json`

They do not explicitly include `toastlift.db-journal`. If the app ever uses rollback-journal mode or a journal file exists during backup, backup/restore consistency needs to be verified.

Recommended plan:

- Confirm the database journal mode used in production.
- Either include all relevant SQLite sidecar files or rely on Android's database-domain behavior only after testing.
- Add a backup/restore smoke test that exercises data written shortly before backup, including active workout and custom exercises.

### Manual JSON Exports Are Plain External Copies

The in-app export writes a user-selected JSON document. That exported file is outside the app's storage and cannot be deleted by the app's delete-personal-data action.

Recommended plan:

- Treat exported JSON as a plain-text personal data file.
- Make export UI copy clear that the file may contain workout history, profile settings, custom exercises, notes, and links.
- Make delete UI copy clear that previously exported files are not deleted by the app.
- If stronger privacy is required later, consider optional encrypted export with user-managed passphrase.

## Fifth-Pass Goal Audit Findings

This pass checked the plan against the actual user goals: survive a fresh install without losing long-term workout logs, avoid destructive import surprises, and make delete-personal-data honest about what it can and cannot remove.

### Import Needs A Dry-Run Preflight

The plan says import should validate required sections before mutating the database, but that needs to be an explicit dry-run phase.

Required behavior:

- Parse and validate the entire JSON backup before any delete, insert, snapshot rewrite, or UI state reset.
- Enforce a practical file-size/read limit and fail with a clear message for oversized files instead of trying to load unbounded JSON into memory.
- Build a complete import plan first: schema version, catalog compatibility result, exercise ID remap, location/split remap, row counts, date range, custom exercise count, template count, active workout presence, and active program presence.
- Reject unsupported future schema versions, malformed JSON, required-section omissions, broken parent/child references, duplicate primary keys inside a section, and unresolved bundled/custom exercise references.
- Show the preflight summary before import and make the destructive scope clear.
- Add tests proving failed preflight leaves the database and `custom_exercises_snapshot.json` unchanged.

### Replace Import Must Protect Existing Data

The practical first implementation should be a replace-style import, but replacement is dangerous if run against a device that already contains years of logs.

Required policy:

- On first launch with no `user_profile` and no user-owned rows, allow restore as the primary onboarding path.
- On an existing/non-empty install, never silently wipe current data.
- Either require the user to export a fresh local rescue backup first, or create a timestamped in-app rescue export before replacing data.
- Show counts for both current data and backup data before replacement.
- If rescue export creation fails, do not proceed with destructive replacement.
- Prefer applying the backup to an empty temporary database first, then swap/apply to the live database only after validation succeeds.

### Import Must Be All-Or-Nothing Across DB And Files

The database transaction is not enough by itself because custom exercise restore also depends on `custom_exercises_snapshot.json`.

Required behavior:

- Restore all database rows in one transaction.
- Regenerate `custom_exercises_snapshot.json` from restored custom exercise rows only after the database transaction succeeds.
- Write the snapshot atomically: write a temporary file, flush/sync if practical, then rename/replace.
- If snapshot regeneration fails, surface a partial-restore error and do not report success.
- Export should only report success after the Storage Access Framework stream is fully written and closed.

### Stable System Keys Are Needed Anywhere IDs Reference System Rows

The earlier location-mode finding covered profile and equipment inventory. The same issue applies to every exported row that stores `location_mode_id`, and to training split references.

Required change:

- Add `location_mode_key`/display name snapshots for completed workouts, active workout, abandoned workout, profile active location, and equipment inventory rows.
- Add `split_program_key` or stable split name snapshots for `user_profile.preferred_split_program_id` and `training_programs.split_program_id`.
- Import by stable key first, then numeric ID only for old exports.
- Preserve `planned_sessions.actual_workout_id` links by preserving completed workout IDs or remapping program session links after completed workout import.

### Program Export Needs Exact Raw Fields

The plan already says adaptive program data is missing, but the future schema should be explicit about lossless program fields.

Program export/import must preserve:

- `training_programs.success_criteria_json` and `adaptation_policy_json` as raw strings.
- integer timestamp fields such as `training_programs.created_at`, `last_reviewed_at`, `program_checkpoints.completed_at`, and `program_events.created_at` without converting them through ISO strings.
- `program_events.payload_json` as raw strings.
- `planned_sessions.status_updated_at_utc`, `actual_workout_id`, `coach_brief`, `completion_ratio`, `completion_credit`, and `completion_truth`.
- `program_exercise_slots.sfr_score` and `evolution_target_exercise_id`, with exercise remapping applied.

### Custom Exercise Import Must Restore Raw Rows

Custom exercise import should not run the normal draft save path or re-canonicalize values through the current UI taxonomy. The backup is the source of truth for the user's custom exercise rows.

Required behavior:

- Insert backed-up custom `exercises` and child taxonomy rows with their stored values and IDs, subject only to explicit conflict/remap policy.
- Preserve custom URLs, labels, synonyms, generated/manual prompt metadata, timestamps, and child-row sequence numbers exactly.
- Include custom `exercise_work_units` if any custom exercise ever has them.
- Regenerate the snapshot from restored rows after import instead of importing a possibly stale snapshot file verbatim.

### Remote Provider Data Is Out Of Local Delete Scope

The app sends some personal-context prompt data to Gemini-backed generators:

- daily coach prompt payload can include profile values, recent workout titles, recent exercise names, active program title/status, coach brief, and next exercise names.
- exercise description generation sends exercise detail/catalog context.
- custom exercise metadata generation sends the user-entered exercise name plus nearby catalog matches.

No persisted remote account or analytics SDK was found in the app code, but local delete cannot delete logs or retention held by third-party API providers.

Required plan/UI copy:

- Do not claim delete-personal-data removes data already sent to external AI/API providers.
- Privacy copy should classify Gemini generation as data leaving the device when those features are used.
- If legal/privacy requirements demand provider-side deletion, that needs a separate provider/data-retention process outside the local wipe.

### OS-Managed Notification Settings Are Not In The JSON Backup

The app creates Android notification channels for rest timer and active workout notifications. Users can modify channel behavior in Android system settings. Those OS-managed channel preferences are not stored in the app database, not included in the JSON export, and not cleared by the current app-level delete path.

Required plan:

- Treat notification channel preferences as OS-managed settings, not app JSON backup data.
- Do not promise a JSON import will restore Android notification channel sound/importance settings.
- Do not claim app-level delete resets notification channel settings unless the implementation explicitly deletes/recreates channels and the UX says so.

### OS Backup Can Reintroduce Data After Delete Or Before Import

Android backup is enabled for the database and snapshot file. That is good for device migration, but it complicates privacy and import semantics.

Required validation:

- Test fresh install with Android auto-restore enabled before manual JSON import. If data is auto-restored, the import flow must detect a non-empty database and use the existing-data replacement policy.
- Test delete followed by uninstall/reinstall/auto-restore. If old backup data can return, the delete UI must avoid saying cloud/device-transfer backups were erased.
- Consider a restore-after-delete product policy if stronger privacy is required, such as excluding backups, adding a local deletion marker, or documenting that local delete is device-local only.

### Fresh-Install Import Must Fit The Onboarding Flow

The expected UX is not just a Profile-screen import button. On a clean install the app currently routes to onboarding when `profile == null`.

Required behavior:

- On the first screen, offer import before forcing onboarding setup.
- A successful import that restores `user_profile` should skip onboarding and load the restored profile, equipment, history, active workout, templates, and program state.
- A failed import should leave the app in the same first-run/onboarding state with no partial restored rows.
- Profile-screen import should be available later, but must follow the non-empty database replacement/merge policy.

### Backup Schema Needs Migration Governance

The plan has a regression guard, but schema governance should be explicit because this app evolves by adding columns in `ToastLiftDatabase.ensureColumn()`.

Required process:

- Any new user-owned table or column must be classified as export-required, delete-required, import-derived, or system-owned in the same change.
- Backup schema versions should have migration readers for old JSON exports, or old exports should fail with a clear unsupported-version message.
- Unknown fields from newer exports should not be silently ignored if they affect lossless restore.

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
- Checkpoint/truncate database journals and compact the database after logical deletion if the feature needs a strong local wipe.
- Reset user-owned `sqlite_sequence` entries.
- Delete app-private user files after the database transaction succeeds.
- If file deletion fails, surface an error and do not claim full deletion.
- If database compaction/checkpoint fails, surface an error or use wording that does not overclaim physical erasure.
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
- Do not claim the app can remove data already written to user-selected external JSON exports or retained by third-party API providers.

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

If AI-backed generation remains enabled, privacy copy should also avoid claiming that local deletion removes data already sent to the external generation provider.

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
- user-owned `sqlite_sequence` entries are absent or reset
- `toastlift.db-wal`, `toastlift.db-shm`, and possible journal files do not retain deleted personal data after the wipe flow completes
- active workout notification and rest timer are cancelled
- UI state no longer references deleted active session, custom draft, export payload, or workout share
- delete/uninstall/reinstall with Android auto-restore does not contradict the user-facing delete wording

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
- Import should run a dry-run preflight first, then restore all database rows in a single transaction and either fully succeed or leave the existing database unchanged.
- On an existing/non-empty database, import must require an explicit replace/merge policy and should not wipe current data unless a rescue backup exists.

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

The program restore also needs exact preservation of raw policy/event JSON fields, integer timestamps, `planned_sessions.actual_workout_id` links to completed workouts, and exercise references inside planned exercises and slots.

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
- stable-key manifests for referenced system rows, including location modes, training split programs, and bundled exercises
- import mode expectations, such as `full_replace_backup`, so partial exports cannot be mistaken for full restores

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
- stable `location_mode_key` values wherever `location_mode_id` is exported
- stable split-program keys/names wherever `split_program_id` is exported
- raw program JSON/timestamp/link fields listed in the fifth-pass program findings

For restore reliability, prefer exporting raw persisted values in addition to friendly display names.

After exporting/importing custom exercises, ensure `custom_exercises_snapshot.json` can be regenerated from the restored rows.

### 3. Add Import Flow

Add a JSON document picker and import flow.

Import should:

- Validate top-level app name and supported `schema_version`.
- Validate required sections and all cross-section references in a dry-run preflight before mutating the database.
- Show a clear summary before import, such as workout count, date range, custom exercise count, template count, and active program presence.
- Detect whether the current install already has personal data and require the chosen replace/merge policy before continuing.
- Create or require a rescue export before destructive replacement of a non-empty install.
- Run inside a single transaction.
- Prefer applying the backup to an empty temporary database first for validation.
- Restore custom exercises before any rows that reference their exercise IDs.
- Resolve custom exercise ID/name conflicts before restoring dependent rows.
- Validate/remap all bundled exercise references before inserting dependent rows.
- Validate/remap all location-mode and split-program references by stable key before inserting dependent rows.
- Insert custom exercise backup rows directly instead of routing them through custom exercise draft validation/generation code.
- Restore parent rows before child rows.
- Preserve IDs where history/templates/program rows reference them.
- Regenerate `custom_exercises_snapshot.json` atomically after successful database restore.
- Rebuild derived/default catalog data only after user data is restored.
- Leave onboarding/current database state unchanged if JSON parsing, preflight, database restore, or snapshot rewrite fails.

### 4. Decide Conflict Policy

For a fresh install, the import can replace empty personal data.

For an existing install, define one explicit policy:

- Replace all current personal data with backup contents, or
- Merge backup into current data.

Replacement is simpler and safer for the first implementation. Merge requires duplicate detection for workouts, templates, custom exercises, preferences, and program state.

For the first shipped import, prefer replacement only on an empty/fresh install and replacement on non-empty installs only after a verified rescue export. Defer merge until duplicate detection and ID-remap behavior are fully specified.

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
- Dry-run failure fixtures for malformed JSON, unsupported schema versions, missing sections, duplicate IDs, unresolved exercise/location/split references, and oversized files.
- Existing-data replacement fixture proving a failed rescue export or failed import leaves current 10-year-style history untouched.
- First-launch import fixture proving restored profile skips onboarding and failed import leaves first-run state unchanged.
- Program linkage fixture proving `planned_sessions.actual_workout_id` still points at the restored completed workout.
- Atomic snapshot fixture proving `custom_exercises_snapshot.json` is absent/old on failed import and correct on success.
- Android backup/restore smoke tests for auto-restore before manual import and restore-after-delete wording assumptions.

The tests should fail whenever a new user-owned column is added but not covered by export/import.

## Implementation Phases And TODO

These phases are enough for the personal-data work:

- [x] Phase 1: make the existing `Export My Data (JSON)` action produce a comprehensive full-backup JSON.
- [ ] Phase 2: add JSON import/restore.
- [ ] Phase 3: make delete-personal-data complete and precise.

### Phase 1: Full JSON Export - Done In Current Workspace

Implementation status:

- Existing Profile `Export My Data (JSON)` button remains the entry point.
- Export remains a single JSON document.
- Backup schema is now versioned as `schema_version = 7`.
- Top-level `backup_kind = full_personal_data_backup` distinguishes this from partial/debug exports.
- Export includes a new `metadata` object with app version, database version, catalog metadata, stable system references, section counts, completed-workout date range, and referenced exercise manifest.
- `personal_data` now includes `experiment_assignments` and `programs`.
- `profile` now includes `user_id`, `active_location_mode_key`, `preferred_split_program_key`, and `dev_session_set_swipe_complete_enabled`.
- `equipment_inventory`, completed workouts, active workout, abandoned workout, and programs now carry stable location/split labels where useful for future import remapping.
- Completed workout export now preserves raw `ab_flags_snapshot_json` and `completion_receipt_snapshot_json` while keeping the parsed friendly `abFlags` and `completionReceipt` objects.
- Completed set export now includes `performed_set_id` and `completed_at_utc`.
- Template exercise export now includes `template_exercise_id`.
- Feedback signal export now includes `signal_id`.
- Active/abandoned workout export now includes metadata fields, pause fields, selected active exercise index, set primary keys, and set completion timestamps.
- Adaptive program export includes `training_programs`, `planned_weeks`, `planned_sessions`, `planned_session_exercises`, `program_exercise_slots`, `program_checkpoints`, and `program_events` as nested program JSON.
- Program export preserves raw program JSON fields and integer timestamps instead of converting them.
- Custom exercise export now includes synonym IDs and custom `exercise_work_units` rows.

Implementation notes:

- This phase intentionally does not add import behavior.
- This phase intentionally does not change delete-personal-data behavior.
- The generated custom exercise snapshot file is still not exported as a separate file; Phase 2 should regenerate `custom_exercises_snapshot.json` from restored custom exercise rows.
- The export is designed to be import-ready, but it is not a complete fresh-install recovery feature until Phase 2 exists and passes round-trip tests.
- Current verification for Phase 1: `./gradlew testDebugUnitTest` and `git diff --check` passed.

### Phase 2 TODO: JSON Import/Restore

- [ ] Add first-launch import entry point before onboarding setup is required.
- [ ] Add Profile import entry point for existing installs.
- [ ] Add document picker for JSON backup files.
- [ ] Parse and validate top-level `app`, `schema_version`, `backup_kind`, and required sections.
- [ ] Add dry-run preflight that validates all cross-section references before mutation.
- [ ] Enforce a practical file-size/read limit and clear error for oversized/malformed JSON.
- [ ] Build an import summary: backup date, workout count/date range, custom exercise count, template count, active workout presence, and active program presence.
- [ ] Detect whether the current install already has user data.
- [ ] For non-empty installs, require explicit replacement confirmation and a verified rescue export before destructive replacement.
- [ ] Restore into an empty/temp database first if practical, then apply/swap only after validation succeeds.
- [ ] Restore custom exercise rows directly from backup JSON rather than via draft-generation code.
- [ ] Resolve/remap custom exercise conflicts against newer bundled catalog rows.
- [ ] Validate/remap bundled exercise references using exercise ID plus slug/name manifest.
- [ ] Validate/remap `location_modes` by stable key.
- [ ] Validate/remap `training_split_programs` by stable key/name.
- [ ] Preserve primary keys for workouts, sets, templates, feedback signals, program rows, active/abandoned rows, and custom exercise rows.
- [ ] Restore parent rows before child rows.
- [ ] Preserve `planned_sessions.actual_workout_id` links to restored completed workouts.
- [ ] Reset/verify SQLite autoincrement sequences after import so future inserts do not collide.
- [ ] Regenerate `custom_exercises_snapshot.json` atomically after successful database restore.
- [ ] Leave the existing database/onboarding state unchanged on parse, preflight, restore, or snapshot-write failure.
- [ ] Add populated round-trip tests comparing exported/imported user-owned tables.
- [ ] Add failure tests for malformed JSON, unsupported schema versions, duplicate IDs, unresolved references, failed snapshot write, and failed rescue export.

### Phase 3 TODO: Delete Personal Data Correctness

- [ ] Move delete into one coordinated repository/service-level wipe path.
- [ ] Delete adaptive program tables in child-to-parent order.
- [ ] Keep bundled catalog/system tables intact.
- [ ] Delete custom exercise rows and their child rows.
- [ ] Delete `custom_exercises_snapshot.json`.
- [ ] Cancel active workout notification and rest timer state.
- [ ] Clear in-memory UI references to active sessions, drafts, pending exports, and shares.
- [ ] Reset user-owned `sqlite_sequence` entries.
- [ ] If strong local wipe is required, checkpoint/truncate WAL and compact/vacuum the database after logical delete.
- [ ] Handle compaction/file-delete failures without overclaiming deletion.
- [ ] Keep UI copy precise: delete removes app data stored on this device, not external JSON exports, OS-managed backups, or data already sent to external API providers.
- [ ] Add deletion fixture covering every user-owned table and file.
- [ ] Add restore-after-delete/Android backup smoke tests or adjust copy to avoid untested claims.

## Practical Risk Assessment

Pre-Phase-1 export was good enough for manual inspection and partial recovery of major workout logs.

With Phase 1 done, the export format is intended to contain all known user-generated data in one JSON file, but it is not yet proven as a fresh-install recovery path because import does not exist.

The app is still not good enough for:

- One-click fresh-install restore.
- Validated round-trip preservation of long-term history semantics.
- Validated restoration of adaptive program state.
- Validated recreation of an in-progress workout exactly.

Until import/export is upgraded and tested, preserving Android app data through matching signing keys is still the safest migration strategy.
