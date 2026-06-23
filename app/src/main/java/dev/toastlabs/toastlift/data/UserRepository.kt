package dev.toastlabs.toastlift.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.toastlabs.toastlift.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal const val PERSONAL_DATA_EXPORT_SCHEMA_VERSION = 7
internal const val PERSONAL_DATA_EXPORT_KIND = "full_personal_data_backup"

class UserRepository(private val database: ToastLiftDatabase) {
    fun loadProfile(): UserProfile? {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT goal_primary, experience_level, default_duration_minutes, weekly_frequency_target,
                   preferred_split_program_id, units, active_location_mode_id, preferred_workout_style,
                   theme_preference, smart_picker_body_filter, smart_picker_target_muscle,
                   gym_machine_cable_bias_enabled, history_workout_ab_flags_visible,
                   dev_pick_next_exercise_enabled, dev_fruit_exercise_icons_enabled,
                   dev_exercise_detail_personal_note_visible, dev_exercise_detail_learned_preference_visible,
                   dev_rest_timer_sound_disabled, training_freshness_threshold_days,
                   training_freshness_min_bucket_exercises, dev_session_set_swipe_complete_enabled,
                   dev_in_session_bounties_enabled
            FROM user_profile
            WHERE user_id = 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val durationMinutes = normalizeWorkoutDurationMinutes(cursor.getInt(2))
            val weeklyFrequency = normalizeWeeklyFrequency(cursor.getInt(3))
            UserProfile(
                goal = cursor.getString(0),
                experience = cursor.getString(1),
                durationMinutes = durationMinutes,
                weeklyFrequency = weeklyFrequency,
                splitProgramId = cursor.getLong(4),
                units = cursor.getString(5),
                activeLocationModeId = cursor.getLong(6),
                workoutStyle = cursor.getString(7),
                themePreference = ThemePreference.fromStorageValue(cursor.getString(8)),
                smartPickerBodyFilter = SmartPickerMuscleBodyFilter.fromStorageValue(cursor.getString(9)),
                smartPickerTargetMuscle = cursor.getStringOrNull(10)?.trim()?.takeIf { it.isNotEmpty() },
                gymMachineCableBiasEnabled = cursor.getInt(11) == 1,
                historyWorkoutAbFlagsVisible = cursor.getInt(12) == 1,
                devPickNextExerciseEnabled = cursor.getInt(13) == 1,
                devFruitExerciseIconsEnabled = cursor.getInt(14) == 1,
                devExerciseDetailPersonalNoteVisible = cursor.getInt(15) == 1,
                devExerciseDetailLearnedPreferenceVisible = cursor.getInt(16) == 1,
                devRestTimerSoundDisabled = cursor.getInt(17) == 1,
                trainingFreshnessThresholdDays = normalizeTrainingFreshnessThresholdDays(cursor.getInt(18)),
                trainingFreshnessMinimumBucketExercises = normalizeTrainingFreshnessBucketExercises(cursor.getInt(19)),
                devSessionSetSwipeCompleteEnabled = cursor.getInt(20) == 1,
                devInSessionBountiesEnabled = cursor.getInt(21) == 1,
            )
        }
    }

    fun saveProfile(draft: OnboardingDraft, activeLocationModeId: Long) {
        val db = database.open()
        val now = Instant.now().toString()
        val durationMinutes = normalizeWorkoutDurationMinutes(draft.durationMinutes)
        val weeklyFrequency = normalizeWeeklyFrequency(draft.weeklyFrequency)
        db.execSQL(
            """
            INSERT INTO user_profile (
                user_id, goal_primary, experience_level, default_duration_minutes,
                weekly_frequency_target, preferred_split_program_id, units, active_location_mode_id,
                preferred_workout_style, smart_picker_body_filter, smart_picker_target_muscle,
                next_focus, created_at_utc, updated_at_utc
            ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'full_body', ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                goal_primary = excluded.goal_primary,
                experience_level = excluded.experience_level,
                default_duration_minutes = excluded.default_duration_minutes,
                weekly_frequency_target = excluded.weekly_frequency_target,
                preferred_split_program_id = excluded.preferred_split_program_id,
                units = excluded.units,
                active_location_mode_id = excluded.active_location_mode_id,
                preferred_workout_style = excluded.preferred_workout_style,
                smart_picker_body_filter = excluded.smart_picker_body_filter,
                smart_picker_target_muscle = excluded.smart_picker_target_muscle,
                updated_at_utc = excluded.updated_at_utc
            """.trimIndent(),
            arrayOf(
                draft.goal,
                draft.experience,
                durationMinutes,
                weeklyFrequency,
                draft.splitProgramId,
                draft.units,
                activeLocationModeId,
                draft.workoutStyle,
                draft.smartPickerBodyFilter.storageValue,
                draft.smartPickerTargetMuscle?.trim()?.takeIf { it.isNotEmpty() },
                now,
                now,
            ),
        )
    }

    fun loadSplitPrograms(): List<TrainingSplitProgram> {
        val db = database.open()
        return db.rawQuery(
            "SELECT split_program_id, name, description FROM training_split_programs ORDER BY split_program_id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        TrainingSplitProgram(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            description = cursor.getString(2),
                        ),
                    )
                }
            }
        }
    }

    fun loadLocationModes(): List<LocationMode> {
        val db = database.open()
        return db.rawQuery(
            "SELECT location_mode_id, name, display_name FROM location_modes ORDER BY location_mode_id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LocationMode(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            displayName = cursor.getString(2),
                        ),
                    )
                }
            }
        }
    }

    fun loadEquipmentForLocation(locationModeId: Long): Set<String> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT equipment_name
            FROM equipment_inventory
            WHERE location_mode_id = ? AND is_available = 1
            ORDER BY equipment_name
            """.trimIndent(),
            arrayOf(locationModeId.toString()),
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    fun toggleEquipment(locationModeId: Long, equipmentName: String) {
        val db = database.open()
        db.rawQuery(
            "SELECT is_available FROM equipment_inventory WHERE location_mode_id = ? AND equipment_name = ?",
            arrayOf(locationModeId.toString(), equipmentName),
        ).use { cursor ->
            val current = if (cursor.moveToFirst()) cursor.getInt(0) == 1 else false
            db.execSQL(
                """
                INSERT INTO equipment_inventory (location_mode_id, equipment_name, is_available)
                VALUES (?, ?, ?)
                ON CONFLICT(location_mode_id, equipment_name)
                DO UPDATE SET is_available = excluded.is_available
                """.trimIndent(),
                arrayOf(locationModeId, equipmentName, if (current) 0 else 1),
            )
        }
    }

    fun setActiveLocation(locationModeId: Long) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET active_location_mode_id = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(locationModeId, Instant.now().toString()),
        )
    }

    fun saveThemePreference(themePreference: ThemePreference) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET theme_preference = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(themePreference.storageValue, Instant.now().toString()),
        )
    }

    fun saveGymMachineCableBiasEnabled(enabled: Boolean) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET gym_machine_cable_bias_enabled = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(if (enabled) 1 else 0, Instant.now().toString()),
        )
    }

    fun saveHistoryWorkoutAbFlagsVisible(enabled: Boolean) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET history_workout_ab_flags_visible = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(if (enabled) 1 else 0, Instant.now().toString()),
        )
    }

    fun saveDevPickNextExerciseEnabled(enabled: Boolean) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET dev_pick_next_exercise_enabled = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(if (enabled) 1 else 0, Instant.now().toString()),
        )
    }

    fun saveDevFruitExerciseIconsEnabled(enabled: Boolean) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET dev_fruit_exercise_icons_enabled = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(if (enabled) 1 else 0, Instant.now().toString()),
        )
    }

    fun saveDevExerciseDetailPersonalNoteVisible(enabled: Boolean) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET dev_exercise_detail_personal_note_visible = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(if (enabled) 1 else 0, Instant.now().toString()),
        )
    }

    fun saveDevExerciseDetailLearnedPreferenceVisible(enabled: Boolean) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET dev_exercise_detail_learned_preference_visible = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(if (enabled) 1 else 0, Instant.now().toString()),
        )
    }

    fun saveDevRestTimerSoundDisabled(disabled: Boolean) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET dev_rest_timer_sound_disabled = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(if (disabled) 1 else 0, Instant.now().toString()),
        )
    }

    fun saveTrainingFreshnessThresholdDays(days: Int) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET training_freshness_threshold_days = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(normalizeTrainingFreshnessThresholdDays(days), Instant.now().toString()),
        )
    }

    fun saveTrainingFreshnessMinimumBucketExercises(exercises: Int) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET training_freshness_min_bucket_exercises = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(normalizeTrainingFreshnessBucketExercises(exercises), Instant.now().toString()),
        )
    }

    fun saveDevSessionSetSwipeCompleteEnabled(enabled: Boolean) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET dev_session_set_swipe_complete_enabled = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(if (enabled) 1 else 0, Instant.now().toString()),
        )
    }

    fun saveDevInSessionBountiesEnabled(enabled: Boolean) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET dev_in_session_bounties_enabled = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(if (enabled) 1 else 0, Instant.now().toString()),
        )
    }

    fun loadNextFocus(): String? {
        val db = database.open()
        return db.rawQuery(
            "SELECT next_focus FROM user_profile WHERE user_id = 1",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getString(0)
        }
    }

    fun saveNextFocus(nextFocus: String) {
        val db = database.open()
        db.execSQL(
            "UPDATE user_profile SET next_focus = ?, updated_at_utc = ? WHERE user_id = 1",
            arrayOf(nextFocus, Instant.now().toString()),
        )
    }

    fun exportPersonalDataJson(customExercises: JSONArray = JSONArray()): PersonalDataExportPayload {
        val exportedAtUtc = Instant.now().toString()
        val db = database.open()
        val personalData = JSONObject()
            .putNullable("profile", exportProfile(db))
            .put("equipment_inventory", exportEquipmentInventory(db))
            .put("experiment_assignments", exportExperimentAssignments(db))
            .put("earned_bounty_cards", exportEarnedBountyCards(db))
            .put("exercise_preferences", exportExercisePreferences(db))
            .put("exercise_generated_descriptions", exportExerciseGeneratedDescriptions(db))
            .put("exercise_user_video_links", exportExerciseUserVideoLinks(db))
            .put("movement_restrictions", exportMovementRestrictions(db))
            .put("custom_exercises", customExercises)
            .put("workout_templates", exportWorkoutTemplates(db))
            .put("workout_feedback_signals", exportWorkoutFeedbackSignals(db))
            .put("completed_workouts", exportCompletedWorkouts(db))
            .putNullable("active_workout", exportSession(db, workoutTable = "active", idColumn = "active_workout_id"))
            .putNullable("abandoned_workout", exportSession(db, workoutTable = "abandoned", idColumn = "abandoned_workout_id"))
            .put("programs", exportPrograms(db))
        val payload = JSONObject()
            .put("app", "ToastLift")
            .put("schema_version", PERSONAL_DATA_EXPORT_SCHEMA_VERSION)
            .put("backup_kind", PERSONAL_DATA_EXPORT_KIND)
            .put("exported_at_utc", exportedAtUtc)
            .put("format", "application/json")
            .put("metadata", exportBackupMetadata(db, personalData))
            .put("personal_data", personalData)
        val fileTimestamp = exportedAtUtc
            .replace(":", "-")
            .replace(".", "-")
        return PersonalDataExportPayload(
            fileName = "toastlift-personal-data-$fileTimestamp.json",
            contents = payload.toString(2),
        )
    }

    fun deleteAllPersonalData() {
        val db = database.open()
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM performed_workouts")
            db.execSQL("DELETE FROM earned_bounty_cards")
            db.execSQL("DELETE FROM active_workout_bounties")
            db.execSQL("DELETE FROM workout_templates")
            db.execSQL("DELETE FROM workout_feedback_signals")
            db.execSQL("DELETE FROM exercise_preferences")
            db.execSQL("DELETE FROM exercise_generated_descriptions")
            db.execSQL("DELETE FROM exercise_user_video_links")
            db.execSQL("DELETE FROM movement_restrictions")
            db.execSQL("DELETE FROM equipment_inventory")
            db.execSQL("DELETE FROM experiment_assignments")
            db.execSQL("DELETE FROM user_profile")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun exportProfile(db: SQLiteDatabase): JSONObject? {
        return db.rawQuery(
            """
            SELECT
                p.user_id,
                p.goal_primary,
                p.experience_level,
                p.default_duration_minutes,
                p.weekly_frequency_target,
                p.preferred_split_program_id,
                s.name,
                p.units,
                p.active_location_mode_id,
                l.name,
                l.display_name,
                p.preferred_workout_style,
                p.theme_preference,
                p.smart_picker_body_filter,
                p.smart_picker_target_muscle,
                p.gym_machine_cable_bias_enabled,
                p.history_workout_ab_flags_visible,
                p.dev_pick_next_exercise_enabled,
                p.dev_fruit_exercise_icons_enabled,
                p.dev_exercise_detail_personal_note_visible,
                p.dev_exercise_detail_learned_preference_visible,
                p.dev_rest_timer_sound_disabled,
                p.training_freshness_threshold_days,
                p.training_freshness_min_bucket_exercises,
                p.dev_session_set_swipe_complete_enabled,
                p.dev_in_session_bounties_enabled,
                p.next_focus,
                p.created_at_utc,
                p.updated_at_utc
            FROM user_profile p
            LEFT JOIN training_split_programs s ON s.split_program_id = p.preferred_split_program_id
            LEFT JOIN location_modes l ON l.location_mode_id = p.active_location_mode_id
            WHERE p.user_id = 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            JSONObject()
                .put("user_id", cursor.getLong(0))
                .put("goal_primary", cursor.getString(1))
                .put("experience_level", cursor.getString(2))
                .put("default_duration_minutes", cursor.getInt(3))
                .put("weekly_frequency_target", cursor.getInt(4))
                .put("preferred_split_program_id", cursor.getLong(5))
                .putNullable("preferred_split_program_key", cursor.getStringOrNull(6))
                .putNullable("preferred_split_program_name", cursor.getStringOrNull(6))
                .put("units", cursor.getString(7))
                .put("active_location_mode_id", cursor.getLong(8))
                .putNullable("active_location_mode_key", cursor.getStringOrNull(9))
                .putNullable("active_location_mode_name", cursor.getStringOrNull(10))
                .put("preferred_workout_style", cursor.getString(11))
                .put("theme_preference", cursor.getString(12))
                .put("smart_picker_body_filter", cursor.getString(13))
                .putNullable("smart_picker_target_muscle", cursor.getStringOrNull(14))
                .put("gym_machine_cable_bias_enabled", cursor.getInt(15) == 1)
                .put("history_workout_ab_flags_visible", cursor.getInt(16) == 1)
                .put("dev_pick_next_exercise_enabled", cursor.getInt(17) == 1)
                .put("dev_fruit_exercise_icons_enabled", cursor.getInt(18) == 1)
                .put("dev_exercise_detail_personal_note_visible", cursor.getInt(19) == 1)
                .put("dev_exercise_detail_learned_preference_visible", cursor.getInt(20) == 1)
                .put("dev_rest_timer_sound_disabled", cursor.getInt(21) == 1)
                .put("training_freshness_threshold_days", normalizeTrainingFreshnessThresholdDays(cursor.getInt(22)))
                .put("training_freshness_min_bucket_exercises", normalizeTrainingFreshnessBucketExercises(cursor.getInt(23)))
                .put("dev_session_set_swipe_complete_enabled", cursor.getInt(24) == 1)
                .put("dev_in_session_bounties_enabled", cursor.getInt(25) == 1)
                .put("next_focus", cursor.getString(26))
                .put("created_at_utc", cursor.getString(27))
                .put("updated_at_utc", cursor.getString(28))
        }
    }

    private fun exportEquipmentInventory(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT i.location_mode_id, l.name, l.display_name, i.equipment_name, i.is_available
            FROM equipment_inventory i
            LEFT JOIN location_modes l ON l.location_mode_id = i.location_mode_id
            ORDER BY i.location_mode_id, i.equipment_name
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("location_mode_id", cursor.getLong(0))
                            .putNullable("location_mode_key", cursor.getStringOrNull(1))
                            .putNullable("location_mode_name", cursor.getStringOrNull(2))
                            .put("equipment_name", cursor.getString(3))
                            .put("is_available", cursor.getInt(4) == 1),
                    )
                }
            }
        }
    }

    private fun exportExperimentAssignments(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT experiment_key, variant_key, assigned_at_utc
            FROM experiment_assignments
            ORDER BY experiment_key
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("experiment_key", cursor.getString(0))
                            .put("variant_key", cursor.getString(1))
                            .put("assigned_at_utc", cursor.getString(2)),
                    )
                }
            }
        }
    }

    private fun exportEarnedBountyCards(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT card_id, bounty_id, bounty_type, title, family, rarity, resolution_scope,
                   earned_at_utc, session_started_at_utc, workout_id, exercise_id, exercise_name,
                   proof_line, flavor_text, art_seed, source_set_number
            FROM earned_bounty_cards
            ORDER BY earned_at_utc DESC, card_id DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("card_id", cursor.getLong(0))
                            .put("bounty_id", cursor.getString(1))
                            .put("bounty_type", cursor.getString(2))
                            .put("title", cursor.getString(3))
                            .put("family", cursor.getString(4))
                            .put("rarity", cursor.getString(5))
                            .put("resolution_scope", cursor.getString(6))
                            .put("earned_at_utc", cursor.getString(7))
                            .put("session_started_at_utc", cursor.getString(8))
                            .putNullable("workout_id", if (cursor.isNull(9)) null else cursor.getLong(9))
                            .put("exercise_id", cursor.getLong(10))
                            .put("exercise_name", cursor.getString(11))
                            .put("proof_line", cursor.getString(12))
                            .put("flavor_text", cursor.getString(13))
                            .put("art_seed", cursor.getString(14))
                            .putNullable("source_set_number", if (cursor.isNull(15)) null else cursor.getInt(15))
                    )
                }
            }
        }
    }

    private fun exportExercisePreferences(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT
                p.exercise_id,
                e.name,
                p.is_favorite,
                p.is_hidden,
                p.is_banned,
                p.preference_score_delta,
                p.notes,
                p.updated_at_utc
            FROM exercise_preferences p
            LEFT JOIN exercises e ON e.exercise_id = p.exercise_id
            ORDER BY e.name, p.exercise_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("exercise_id", cursor.getLong(0))
                            .putNullable("exercise_name", cursor.getStringOrNull(1))
                            .put("is_favorite", cursor.getInt(2) == 1)
                            .put("is_hidden", cursor.getInt(3) == 1)
                            .put("is_banned", cursor.getInt(4) == 1)
                            .put("preference_score_delta", cursor.getDouble(5))
                            .putNullable("notes", cursor.getStringOrNull(6))
                            .put("updated_at_utc", cursor.getString(7)),
                    )
                }
            }
        }
    }

    private fun exportExerciseGeneratedDescriptions(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT
                d.exercise_id,
                e.name,
                d.description,
                d.generation_model,
                d.generation_prompt_version,
                d.created_at_utc,
                d.updated_at_utc
            FROM exercise_generated_descriptions d
            LEFT JOIN exercises e ON e.exercise_id = d.exercise_id
            ORDER BY e.name, d.exercise_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("exercise_id", cursor.getLong(0))
                            .putNullable("exercise_name", cursor.getStringOrNull(1))
                            .put("description", cursor.getString(2))
                            .putNullable("generation_model", cursor.getStringOrNull(3))
                            .putNullable("generation_prompt_version", cursor.getStringOrNull(4))
                            .put("created_at_utc", cursor.getString(5))
                            .put("updated_at_utc", cursor.getString(6)),
                    )
                }
            }
        }
    }

    private fun exportExerciseUserVideoLinks(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT
                l.user_video_link_id,
                l.exercise_id,
                e.name,
                l.label,
                l.url,
                l.created_at_utc,
                l.updated_at_utc
            FROM exercise_user_video_links l
            LEFT JOIN exercises e ON e.exercise_id = l.exercise_id
            ORDER BY e.name, l.user_video_link_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("user_video_link_id", cursor.getLong(0))
                            .put("exercise_id", cursor.getLong(1))
                            .putNullable("exercise_name", cursor.getStringOrNull(2))
                            .put("label", cursor.getString(3))
                            .put("url", cursor.getString(4))
                            .put("created_at_utc", cursor.getString(5))
                            .put("updated_at_utc", cursor.getString(6)),
                    )
                }
            }
        }
    }

    private fun exportMovementRestrictions(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT restriction_id, restriction_scope, restriction_value, severity, notes
            FROM movement_restrictions
            ORDER BY restriction_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("restriction_id", cursor.getLong(0))
                            .put("restriction_scope", cursor.getString(1))
                            .put("restriction_value", cursor.getString(2))
                            .put("severity", cursor.getString(3))
                            .putNullable("notes", cursor.getStringOrNull(4)),
                    )
                }
            }
        }
    }

    private fun exportWorkoutTemplates(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT template_id, name, origin_type, created_at_utc
            FROM workout_templates
            ORDER BY created_at_utc DESC, template_id DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val templateId = cursor.getLong(0)
                    put(
                        JSONObject()
                            .put("template_id", templateId)
                            .put("name", cursor.getString(1))
                            .put("origin_type", cursor.getString(2))
                            .put("created_at_utc", cursor.getString(3))
                            .put("exercises", exportTemplateExercises(db, templateId)),
                    )
                }
            }
        }
    }

    private fun exportWorkoutFeedbackSignals(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT
                signal_id,
                signal_type,
                workout_origin_type,
                workout_title,
                workout_focus_key,
                session_started_at_utc,
                exercise_id,
                exercise_name,
                signal_value,
                resulting_preference_score_delta,
                created_at_utc
            FROM workout_feedback_signals
            ORDER BY created_at_utc DESC, signal_id DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("signal_id", cursor.getLong(0))
                            .put("signal_type", cursor.getString(1))
                            .put("workout_origin_type", cursor.getString(2))
                            .put("workout_title", cursor.getString(3))
                            .putNullable("workout_focus_key", cursor.getStringOrNull(4))
                            .putNullable("session_started_at_utc", cursor.getStringOrNull(5))
                            .put("exercise_id", cursor.getLong(6))
                            .put("exercise_name", cursor.getString(7))
                            .put("signal_value", cursor.getDouble(8))
                            .put("resulting_preference_score_delta", cursor.getDouble(9))
                            .put("created_at_utc", cursor.getString(10)),
                    )
                }
            }
        }
    }

    private fun exportTemplateExercises(db: SQLiteDatabase, templateId: Long): JSONArray {
        return db.rawQuery(
            """
            SELECT
                te.template_exercise_id,
                te.sort_order,
                te.exercise_id,
                e.name,
                te.set_count,
                te.rep_range,
                te.rest_seconds,
                te.rationale
            FROM workout_template_exercises te
            LEFT JOIN exercises e ON e.exercise_id = te.exercise_id
            WHERE te.template_id = ?
            ORDER BY te.sort_order
            """.trimIndent(),
            arrayOf(templateId.toString()),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("template_exercise_id", cursor.getLong(0))
                            .put("sort_order", cursor.getInt(1))
                            .put("exercise_id", cursor.getLong(2))
                            .putNullable("exercise_name", cursor.getStringOrNull(3))
                            .put("set_count", cursor.getInt(4))
                            .put("rep_range", cursor.getString(5))
                            .put("rest_seconds", cursor.getInt(6))
                            .put("rationale", cursor.getString(7)),
                    )
                }
            }
        }
    }

    private fun exportCompletedWorkouts(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT
                performed_workouts.performed_workout_id,
                performed_workouts.title,
                performed_workouts.origin_type,
                performed_workouts.location_mode_id,
                l.name,
                l.display_name,
                performed_workouts.focus_key,
                performed_workouts.started_at_utc,
                performed_workouts.completed_at_utc,
                performed_workouts.actual_duration_seconds,
                performed_workouts.ab_flags_snapshot_json,
                performed_workouts.completion_receipt_snapshot_json
            FROM performed_workouts
            LEFT JOIN location_modes l ON l.location_mode_id = performed_workouts.location_mode_id
            ORDER BY performed_workouts.completed_at_utc DESC, performed_workouts.performed_workout_id DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val workoutId = cursor.getLong(0)
                    put(
                        JSONObject()
                            .put("performed_workout_id", workoutId)
                            .put("title", cursor.getString(1))
                            .put("origin_type", cursor.getString(2))
                            .put("location_mode_id", cursor.getLong(3))
                            .putNullable("location_mode_key", cursor.getStringOrNull(4))
                            .putNullable("location_mode_name", cursor.getStringOrNull(5))
                            .putNullable("focus_key", cursor.getStringOrNull(6))
                            .put("started_at_utc", cursor.getString(7))
                            .put("completed_at_utc", cursor.getString(8))
                            .put("actual_duration_seconds", cursor.getInt(9))
                            .putNullable("ab_flags_snapshot_json", cursor.getStringOrNull(10))
                            .putNullable("completion_receipt_snapshot_json", cursor.getStringOrNull(11))
                            .putNullable("abFlags", exportCompletedWorkoutAbFlags(cursor.getStringOrNull(10)))
                            .putNullable("completionReceipt", exportCompletionReceiptSnapshot(cursor.getStringOrNull(11)))
                            .put("exercises", exportPerformedExercises(db, workoutId)),
                    )
                }
            }
        }
    }

    private fun exportPerformedExercises(db: SQLiteDatabase, workoutId: Long): JSONArray {
        return db.rawQuery(
            """
            SELECT
                performed_exercise_id,
                sort_order,
                exercise_id,
                exercise_name,
                last_set_reps_in_reserve,
                last_set_rpe
            FROM performed_exercises
            WHERE performed_workout_id = ?
            ORDER BY sort_order, performed_exercise_id
            """.trimIndent(),
            arrayOf(workoutId.toString()),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val performedExerciseId = cursor.getLong(0)
                    put(
                        JSONObject()
                            .put("performed_exercise_id", performedExerciseId)
                            .put("sort_order", cursor.getInt(1))
                            .put("exercise_id", cursor.getLong(2))
                            .put("exercise_name", cursor.getString(3))
                            .putNullable("last_set_reps_in_reserve", cursor.optionalInt(4))
                            .putNullable("last_set_rpe", cursor.optionalDouble(5))
                            .put("sets", exportPerformedSets(db, performedExerciseId)),
                    )
                }
            }
        }
    }

    private fun exportPerformedSets(db: SQLiteDatabase, performedExerciseId: Long): JSONArray {
        return db.rawQuery(
            """
            SELECT
                performed_set_id,
                set_number,
                target_reps,
                recommended_reps,
                recommended_weight_value,
                actual_reps,
                weight_value,
                is_completed,
                recommendation_source,
                recommendation_confidence,
                completed_at_utc,
                work_unit_values_json
            FROM performed_sets
            WHERE performed_exercise_id = ?
            ORDER BY set_number, performed_set_id
            """.trimIndent(),
            arrayOf(performedExerciseId.toString()),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("performed_set_id", cursor.getLong(0))
                            .put("set_number", cursor.getInt(1))
                            .put("target_reps", cursor.getString(2))
                            .putNullable("recommended_reps", cursor.optionalInt(3))
                            .putNullable("recommended_weight_value", cursor.optionalDouble(4))
                            .putNullable("actual_reps", cursor.optionalInt(5))
                            .putNullable("weight_value", cursor.optionalDouble(6))
                            .put("is_completed", cursor.getInt(7) == 1)
                            .putNullable("recommendation_source", cursor.getStringOrNull(8))
                            .putNullable("recommendation_confidence", cursor.optionalDouble(9))
                            .putNullable("completed_at_utc", cursor.getStringOrNull(10))
                            .putNullable("work_unit_values_json", cursor.getStringOrNull(11)),
                    )
                }
            }
        }
    }

    private fun exportCompletedWorkoutAbFlags(payload: String?): JSONObject? {
        val abFlags = deserializeCompletedWorkoutAbFlags(payload) ?: return null
        return JSONObject().apply {
            abFlags.completionFeedbackFlag?.let { completionFeedbackFlag ->
                put(
                    "completionFeedbackFlag",
                    JSONObject()
                        .put("experimentKey", completionFeedbackFlag.experimentKey)
                        .put("flagName", completionFeedbackFlag.flagName)
                        .put("flagDescription", completionFeedbackFlag.flagDescription)
                        .put("variantKey", completionFeedbackFlag.variantKey)
                        .put("variantName", completionFeedbackFlag.variantName)
                        .put("enabledStatus", completionFeedbackFlag.enabledStatus),
                )
            }
            abFlags.receiptExperienceFlag?.let { receiptExperienceFlag ->
                put(
                    "receiptExperienceFlag",
                    JSONObject()
                        .put("experimentKey", receiptExperienceFlag.experimentKey)
                        .put("flagName", receiptExperienceFlag.flagName)
                        .put("flagDescription", receiptExperienceFlag.flagDescription)
                        .put("variantKey", receiptExperienceFlag.variantKey)
                        .put("variantName", receiptExperienceFlag.variantName)
                        .put("enabledStatus", receiptExperienceFlag.enabledStatus),
                )
            }
        }.takeIf { it.length() > 0 }
    }

    private fun exportCompletionReceiptSnapshot(payload: String?): JSONObject? {
        return payload?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun exportSession(db: SQLiteDatabase, workoutTable: String, idColumn: String): JSONObject? {
        val selectedExerciseColumn = if (workoutTable == "active") {
            "selected_exercise_index"
        } else {
            "NULL AS selected_exercise_index"
        }
        val sessionHeader = db.rawQuery(
            """
            SELECT
                $idColumn,
                title,
                origin_type,
                ${workoutTable}_workouts.location_mode_id,
                l.name,
                l.display_name,
                started_at_utc,
                focus_key,
                subtitle,
                estimated_minutes,
                session_format,
                is_paused,
                paused_at_utc,
                accumulated_paused_seconds,
                $selectedExerciseColumn
            FROM ${workoutTable}_workouts
            LEFT JOIN location_modes l ON l.location_mode_id = ${workoutTable}_workouts.location_mode_id
            WHERE $idColumn = 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            JSONObject()
                .put(idColumn, cursor.getLong(0))
                .put("title", cursor.getString(1))
                .put("origin_type", cursor.getString(2))
                .put("location_mode_id", cursor.getLong(3))
                .putNullable("location_mode_key", cursor.getStringOrNull(4))
                .putNullable("location_mode_name", cursor.getStringOrNull(5))
                .put("started_at_utc", cursor.getString(6))
                .putNullable("focus_key", cursor.getStringOrNull(7))
                .put("subtitle", cursor.getString(8))
                .putNullable("estimated_minutes", cursor.optionalInt(9))
                .putNullable("session_format", cursor.getStringOrNull(10))
                .put("is_paused", cursor.getInt(11) == 1)
                .putNullable("paused_at_utc", cursor.getStringOrNull(12))
                .put("accumulated_paused_seconds", cursor.getInt(13))
                .putNullable("selected_exercise_index", cursor.optionalInt(14))
        }
        return sessionHeader.put("exercises", exportSessionExercises(db, workoutTable))
    }

    private fun exportSessionExercises(db: SQLiteDatabase, workoutTable: String): JSONArray {
        val exerciseTable = "${workoutTable}_exercises"
        val exerciseIdColumn = "${workoutTable}_exercise_id"
        val completionSequenceColumn = if (workoutTable == "active") {
            "completion_sequence"
        } else {
            "NULL AS completion_sequence"
        }
        return db.rawQuery(
            """
            SELECT
                $exerciseIdColumn,
                sort_order,
                exercise_id,
                exercise_name,
                body_region,
                target_muscle_group,
                equipment,
                rest_seconds,
                notes,
                last_set_reps_in_reserve,
                fruit_icon,
                $completionSequenceColumn
            FROM $exerciseTable
            WHERE ${workoutTable}_workout_id = 1
            ORDER BY sort_order, $exerciseIdColumn
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val sessionExerciseId = cursor.getLong(0)
                    put(
                        JSONObject()
                            .put(exerciseIdColumn, sessionExerciseId)
                            .put("sort_order", cursor.getInt(1))
                            .put("exercise_id", cursor.getLong(2))
                            .put("exercise_name", cursor.getString(3))
                            .put("body_region", cursor.getString(4))
                            .put("target_muscle_group", cursor.getString(5))
                            .put("equipment", cursor.getString(6))
                            .put("rest_seconds", cursor.getInt(7))
                            .put("notes", cursor.getString(8))
                            .putNullable("last_set_reps_in_reserve", cursor.optionalInt(9))
                            .putNullable("fruit_icon", cursor.getStringOrNull(10))
                            .putNullable("completion_sequence", cursor.optionalInt(11))
                            .put("sets", exportSessionSets(db, workoutTable, sessionExerciseId)),
                    )
                }
            }
        }
    }

    private fun exportSessionSets(db: SQLiteDatabase, workoutTable: String, sessionExerciseId: Long): JSONArray {
        val setTable = "${workoutTable}_sets"
        val exerciseIdColumn = "${workoutTable}_exercise_id"
        val setIdColumn = "${workoutTable}_set_id"
        return db.rawQuery(
            """
            SELECT
                $setIdColumn,
                set_stable_id,
                set_number,
                target_reps,
                recommended_reps,
                recommended_weight_value,
                actual_reps,
                weight_value,
                is_completed,
                recommendation_source,
                recommendation_confidence,
                completed_at_utc,
                work_unit_values_json
            FROM $setTable
            WHERE $exerciseIdColumn = ?
            ORDER BY set_number, $setIdColumn
            """.trimIndent(),
            arrayOf(sessionExerciseId.toString()),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put(setIdColumn, cursor.getLong(0))
                            .put("set_stable_id", cursor.getLong(1))
                            .put("set_number", cursor.getInt(2))
                            .put("target_reps", cursor.getString(3))
                            .putNullable("recommended_reps", cursor.optionalInt(4))
                            .put("recommended_weight_value", cursor.getString(5))
                            .put("actual_reps", cursor.getString(6))
                            .put("weight_value", cursor.getString(7))
                            .put("is_completed", cursor.getInt(8) == 1)
                            .putNullable("recommendation_source", cursor.getStringOrNull(9))
                            .putNullable("recommendation_confidence", cursor.optionalDouble(10))
                            .putNullable("completed_at_utc", cursor.getStringOrNull(11))
                            .putNullable("work_unit_values_json", cursor.getStringOrNull(12)),
                    )
                }
            }
        }
    }

    private fun exportBackupMetadata(db: SQLiteDatabase, personalData: JSONObject): JSONObject {
        return JSONObject()
            .put("app_version_name", BuildConfig.VERSION_NAME)
            .put("app_version_code", BuildConfig.VERSION_CODE)
            .put("database_version", db.version)
            .put("catalog_metadata", exportCatalogMetadata(db))
            .put("system_references", exportSystemReferences(db))
            .put("section_counts", exportSectionCounts(personalData))
            .putNullable("completed_workout_date_range", exportCompletedWorkoutDateRange(db))
            .put("referenced_exercises", exportReferencedExercises(db))
    }

    private fun exportCatalogMetadata(db: SQLiteDatabase): JSONObject {
        return db.rawQuery(
            """
            SELECT metadata_key, metadata_value
            FROM import_metadata
            ORDER BY metadata_key
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONObject().apply {
                while (cursor.moveToNext()) {
                    put(cursor.getString(0), cursor.getString(1))
                }
            }
        }
    }

    private fun exportSystemReferences(db: SQLiteDatabase): JSONObject {
        return JSONObject()
            .put("location_modes", exportLocationModes(db))
            .put("training_split_programs", exportTrainingSplitPrograms(db))
    }

    private fun exportLocationModes(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT location_mode_id, name, display_name, is_default
            FROM location_modes
            ORDER BY location_mode_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("location_mode_id", cursor.getLong(0))
                            .put("location_mode_key", cursor.getString(1))
                            .put("display_name", cursor.getString(2))
                            .put("is_default", cursor.getInt(3) == 1),
                    )
                }
            }
        }
    }

    private fun exportTrainingSplitPrograms(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT split_program_id, name, description
            FROM training_split_programs
            ORDER BY split_program_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("split_program_id", cursor.getLong(0))
                            .put("split_program_key", cursor.getString(1))
                            .put("name", cursor.getString(1))
                            .put("description", cursor.getString(2)),
                    )
                }
            }
        }
    }

    private fun exportSectionCounts(personalData: JSONObject): JSONObject {
        return JSONObject().apply {
            val keys = personalData.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = personalData.opt(key)
                val count = when (value) {
                    is JSONArray -> value.length()
                    is JSONObject -> 1
                    JSONObject.NULL, null -> 0
                    else -> 1
                }
                put(key, count)
            }
        }
    }

    private fun exportCompletedWorkoutDateRange(db: SQLiteDatabase): JSONObject? {
        return db.rawQuery(
            """
            SELECT COUNT(*), MIN(completed_at_utc), MAX(completed_at_utc)
            FROM performed_workouts
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getLong(0) == 0L) return@use null
            JSONObject()
                .put("count", cursor.getLong(0))
                .put("first_completed_at_utc", cursor.getString(1))
                .put("last_completed_at_utc", cursor.getString(2))
        }
    }

    private fun exportReferencedExercises(db: SQLiteDatabase): JSONArray {
        val referencedIds = collectReferencedExerciseIds(db)
        if (referencedIds.isEmpty()) return JSONArray()

        val foundIds = mutableSetOf<Long>()
        val rows = mutableListOf<JSONObject>()
        db.rawQuery(
            """
            SELECT
                exercise_id,
                slug,
                name,
                source_row,
                COALESCE(is_post_install_llm_generated, 0)
            FROM exercises
            ORDER BY exercise_id
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val exerciseId = cursor.getLong(0)
                if (exerciseId !in referencedIds) continue
                foundIds += exerciseId
                rows += JSONObject()
                    .put("exercise_id", exerciseId)
                    .put("slug", cursor.getString(1))
                    .put("name", cursor.getString(2))
                    .put("source_row", cursor.getLong(3))
                    .put("is_custom", cursor.getInt(4) == 1)
            }
        }
        (referencedIds - foundIds).sorted().forEach { missingId ->
            rows += JSONObject()
                .put("exercise_id", missingId)
                .put("missing_from_catalog", true)
        }
        return JSONArray().apply {
            rows.sortedBy { it.getLong("exercise_id") }.forEach(::put)
        }
    }

    private fun collectReferencedExerciseIds(db: SQLiteDatabase): Set<Long> {
        val ids = linkedSetOf<Long>()
        listOf(
            "SELECT exercise_id FROM exercises WHERE COALESCE(is_post_install_llm_generated, 0) = 1",
            "SELECT exercise_id FROM exercise_preferences",
            "SELECT exercise_id FROM exercise_generated_descriptions",
            "SELECT exercise_id FROM exercise_user_video_links",
            "SELECT exercise_id FROM workout_template_exercises",
            "SELECT exercise_id FROM workout_feedback_signals",
            "SELECT exercise_id FROM performed_exercises",
            "SELECT exercise_id FROM active_exercises",
            "SELECT exercise_id FROM abandoned_exercises",
            "SELECT exercise_id FROM planned_session_exercises",
            "SELECT exercise_id FROM program_exercise_slots",
            "SELECT evolution_target_exercise_id FROM program_exercise_slots WHERE evolution_target_exercise_id IS NOT NULL",
        ).forEach { query ->
            collectExerciseIdsFromQuery(db, query, ids)
        }
        db.rawQuery("SELECT success_criteria_json FROM training_programs", null).use { cursor ->
            while (cursor.moveToNext()) {
                val targetLifts = runCatching {
                    JSONObject(cursor.getString(0)).optJSONObject("targetLifts")
                }.getOrNull()
                if (targetLifts == null) continue
                val keys = targetLifts.keys()
                while (keys.hasNext()) {
                    keys.next().toLongOrNull()?.let(ids::add)
                }
            }
        }
        return ids
    }

    private fun collectExerciseIdsFromQuery(
        db: SQLiteDatabase,
        query: String,
        ids: MutableSet<Long>,
    ) {
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                if (!cursor.isNull(0)) ids += cursor.getLong(0)
            }
        }
    }

    private fun exportPrograms(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT
                p.id,
                p.title,
                p.goal,
                p.primary_outcome_metric,
                p.program_archetype,
                p.periodization_model,
                p.split_program_id,
                s.name,
                p.total_weeks,
                p.sessions_per_week,
                p.success_criteria_json,
                p.adaptation_policy_json,
                p.confidence_score,
                p.last_reviewed_at,
                p.created_at,
                p.status
            FROM training_programs p
            LEFT JOIN training_split_programs s ON s.split_program_id = p.split_program_id
            ORDER BY p.created_at DESC, p.id
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val programId = cursor.getString(0)
                    put(
                        JSONObject()
                            .put("id", programId)
                            .put("title", cursor.getString(1))
                            .put("goal", cursor.getString(2))
                            .put("primary_outcome_metric", cursor.getString(3))
                            .put("program_archetype", cursor.getString(4))
                            .put("periodization_model", cursor.getString(5))
                            .put("split_program_id", cursor.getLong(6))
                            .putNullable("split_program_key", cursor.getStringOrNull(7))
                            .putNullable("split_program_name", cursor.getStringOrNull(7))
                            .put("total_weeks", cursor.getInt(8))
                            .put("sessions_per_week", cursor.getInt(9))
                            .put("success_criteria_json", cursor.getString(10))
                            .put("adaptation_policy_json", cursor.getString(11))
                            .put("confidence_score", cursor.getDouble(12))
                            .putNullable("last_reviewed_at", cursor.optionalLong(13))
                            .put("created_at", cursor.getLong(14))
                            .put("status", cursor.getString(15))
                            .put("planned_weeks", exportPlannedWeeks(db, programId))
                            .put("planned_sessions", exportPlannedSessions(db, programId))
                            .put("exercise_slots", exportProgramExerciseSlots(db, programId))
                            .put("checkpoints", exportProgramCheckpoints(db, programId))
                            .put("events", exportProgramEvents(db, programId)),
                    )
                }
            }
        }
    }

    private fun exportPlannedWeeks(db: SQLiteDatabase, programId: String): JSONArray {
        return db.rawQuery(
            """
            SELECT id, program_id, week_number, week_type, volume_multiplier, intensity_modifier
            FROM planned_weeks
            WHERE program_id = ?
            ORDER BY week_number, id
            """.trimIndent(),
            arrayOf(programId),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("id", cursor.getLong(0))
                            .put("program_id", cursor.getString(1))
                            .put("week_number", cursor.getInt(2))
                            .put("week_type", cursor.getString(3))
                            .put("volume_multiplier", cursor.getDouble(4))
                            .put("intensity_modifier", cursor.getDouble(5)),
                    )
                }
            }
        }
    }

    private fun exportPlannedSessions(db: SQLiteDatabase, programId: String): JSONArray {
        return db.rawQuery(
            """
            SELECT
                ps.id,
                ps.program_id,
                ps.week_number,
                ps.day_index,
                ps.sequence_number,
                ps.focus_key,
                ps.planned_sets,
                ps.time_budget_minutes,
                ps.status,
                ps.actual_workout_id,
                pw.title,
                pw.completed_at_utc,
                ps.status_updated_at_utc,
                ps.coach_brief,
                ps.completion_ratio,
                ps.completion_credit,
                ps.completion_truth
            FROM planned_sessions ps
            LEFT JOIN performed_workouts pw ON pw.performed_workout_id = ps.actual_workout_id
            WHERE ps.program_id = ?
            ORDER BY ps.sequence_number, ps.id
            """.trimIndent(),
            arrayOf(programId),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val sessionId = cursor.getLong(0)
                    put(
                        JSONObject()
                            .put("id", sessionId)
                            .put("program_id", cursor.getString(1))
                            .put("week_number", cursor.getInt(2))
                            .put("day_index", cursor.getInt(3))
                            .put("sequence_number", cursor.getInt(4))
                            .put("focus_key", cursor.getString(5))
                            .put("planned_sets", cursor.getInt(6))
                            .putNullable("time_budget_minutes", cursor.optionalInt(7))
                            .put("status", cursor.getString(8))
                            .putNullable("actual_workout_id", cursor.optionalLong(9))
                            .putNullable("actual_workout_title", cursor.getStringOrNull(10))
                            .putNullable("actual_workout_completed_at_utc", cursor.getStringOrNull(11))
                            .putNullable("status_updated_at_utc", cursor.getStringOrNull(12))
                            .putNullable("coach_brief", cursor.getStringOrNull(13))
                            .putNullable("completion_ratio", cursor.optionalDouble(14))
                            .putNullable("completion_credit", cursor.optionalDouble(15))
                            .putNullable("completion_truth", cursor.getStringOrNull(16))
                            .put("exercises", exportPlannedSessionExercises(db, sessionId)),
                    )
                }
            }
        }
    }

    private fun exportPlannedSessionExercises(db: SQLiteDatabase, sessionId: Long): JSONArray {
        return db.rawQuery(
            """
            SELECT
                pse.id,
                pse.planned_session_id,
                pse.exercise_id,
                e.slug,
                e.name,
                pse.sort_order,
                pse.execution_style
            FROM planned_session_exercises pse
            LEFT JOIN exercises e ON e.exercise_id = pse.exercise_id
            WHERE pse.planned_session_id = ?
            ORDER BY pse.sort_order, pse.id
            """.trimIndent(),
            arrayOf(sessionId.toString()),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("id", cursor.getLong(0))
                            .put("planned_session_id", cursor.getLong(1))
                            .put("exercise_id", cursor.getLong(2))
                            .putNullable("exercise_slug", cursor.getStringOrNull(3))
                            .putNullable("exercise_name", cursor.getStringOrNull(4))
                            .put("sort_order", cursor.getInt(5))
                            .put("execution_style", cursor.getString(6)),
                    )
                }
            }
        }
    }

    private fun exportProgramExerciseSlots(db: SQLiteDatabase, programId: String): JSONArray {
        return db.rawQuery(
            """
            SELECT
                slot.id,
                slot.program_id,
                slot.exercise_id,
                e.slug,
                e.name,
                slot.role,
                slot.baseline_weekly_set_target,
                slot.starting_sets,
                slot.sets_per_week_increment,
                slot.load_progression_percent,
                slot.rep_range_shift,
                slot.sfr_score,
                slot.evolution_target_exercise_id,
                target.slug,
                target.name
            FROM program_exercise_slots slot
            LEFT JOIN exercises e ON e.exercise_id = slot.exercise_id
            LEFT JOIN exercises target ON target.exercise_id = slot.evolution_target_exercise_id
            WHERE slot.program_id = ?
            ORDER BY slot.id
            """.trimIndent(),
            arrayOf(programId),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("id", cursor.getLong(0))
                            .put("program_id", cursor.getString(1))
                            .put("exercise_id", cursor.getLong(2))
                            .putNullable("exercise_slug", cursor.getStringOrNull(3))
                            .putNullable("exercise_name", cursor.getStringOrNull(4))
                            .put("role", cursor.getString(5))
                            .put("baseline_weekly_set_target", cursor.getInt(6))
                            .put("starting_sets", cursor.getInt(7))
                            .put("sets_per_week_increment", cursor.getInt(8))
                            .put("load_progression_percent", cursor.getDouble(9))
                            .put("rep_range_shift", cursor.getInt(10) == 1)
                            .putNullable("sfr_score", cursor.optionalDouble(11))
                            .putNullable("evolution_target_exercise_id", cursor.optionalLong(12))
                            .putNullable("evolution_target_exercise_slug", cursor.getStringOrNull(13))
                            .putNullable("evolution_target_exercise_name", cursor.getStringOrNull(14)),
                    )
                }
            }
        }
    }

    private fun exportProgramCheckpoints(db: SQLiteDatabase, programId: String): JSONArray {
        return db.rawQuery(
            """
            SELECT id, program_id, week_number, checkpoint_type, status, completed_at, summary
            FROM program_checkpoints
            WHERE program_id = ?
            ORDER BY week_number, id
            """.trimIndent(),
            arrayOf(programId),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("id", cursor.getLong(0))
                            .put("program_id", cursor.getString(1))
                            .put("week_number", cursor.getInt(2))
                            .put("checkpoint_type", cursor.getString(3))
                            .put("status", cursor.getString(4))
                            .putNullable("completed_at", cursor.optionalLong(5))
                            .putNullable("summary", cursor.getStringOrNull(6)),
                    )
                }
            }
        }
    }

    private fun exportProgramEvents(db: SQLiteDatabase, programId: String): JSONArray {
        return db.rawQuery(
            """
            SELECT id, program_id, event_type, payload_json, created_at
            FROM program_events
            WHERE program_id = ?
            ORDER BY created_at DESC, id DESC
            """.trimIndent(),
            arrayOf(programId),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("id", cursor.getLong(0))
                            .put("program_id", cursor.getString(1))
                            .put("event_type", cursor.getString(2))
                            .put("payload_json", cursor.getString(3))
                            .put("created_at", cursor.getLong(4)),
                    )
                }
            }
        }
    }

    private fun Cursor.optionalLong(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private fun Cursor.optionalInt(index: Int): Int? = if (isNull(index)) null else getInt(index)

    private fun Cursor.optionalDouble(index: Int): Double? = if (isNull(index)) null else getDouble(index)

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        put(key, value ?: JSONObject.NULL)
        return this
    }
}
