package dev.toastlabs.toastlift.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class UserRepository(private val database: ToastLiftDatabase) {
    fun loadProfile(): UserProfile? {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT goal_primary, experience_level, default_duration_minutes, weekly_frequency_target,
                   preferred_split_program_id, units, active_location_mode_id, preferred_workout_style,
                   theme_preference, gym_machine_cable_bias_enabled, history_workout_ab_flags_visible,
                   dev_pick_next_exercise_enabled, dev_fruit_exercise_icons_enabled
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
                gymMachineCableBiasEnabled = cursor.getInt(9) == 1,
                historyWorkoutAbFlagsVisible = cursor.getInt(10) == 1,
                devPickNextExerciseEnabled = cursor.getInt(11) == 1,
                devFruitExerciseIconsEnabled = cursor.getInt(12) == 1,
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
                preferred_workout_style, next_focus, created_at_utc, updated_at_utc
            ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, 'full_body', ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                goal_primary = excluded.goal_primary,
                experience_level = excluded.experience_level,
                default_duration_minutes = excluded.default_duration_minutes,
                weekly_frequency_target = excluded.weekly_frequency_target,
                preferred_split_program_id = excluded.preferred_split_program_id,
                units = excluded.units,
                active_location_mode_id = excluded.active_location_mode_id,
                preferred_workout_style = excluded.preferred_workout_style,
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
        val payload = JSONObject()
            .put("app", "ToastLift")
            .put("schema_version", 5)
            .put("exported_at_utc", exportedAtUtc)
            .put("format", "application/json")
            .put(
                "personal_data",
                JSONObject()
                    .putNullable("profile", exportProfile(db))
                    .put("equipment_inventory", exportEquipmentInventory(db))
                    .put("exercise_preferences", exportExercisePreferences(db))
                    .put("movement_restrictions", exportMovementRestrictions(db))
                    .put("custom_exercises", customExercises)
                    .put("workout_templates", exportWorkoutTemplates(db))
                    .put("workout_feedback_signals", exportWorkoutFeedbackSignals(db))
                    .put("completed_workouts", exportCompletedWorkouts(db))
                    .putNullable("active_workout", exportSession(db, workoutTable = "active", idColumn = "active_workout_id"))
                    .putNullable("abandoned_workout", exportSession(db, workoutTable = "abandoned", idColumn = "abandoned_workout_id")),
            )
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
            db.execSQL("DELETE FROM workout_templates")
            db.execSQL("DELETE FROM workout_feedback_signals")
            db.execSQL("DELETE FROM exercise_preferences")
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
                p.goal_primary,
                p.experience_level,
                p.default_duration_minutes,
                p.weekly_frequency_target,
                p.preferred_split_program_id,
                s.name,
                p.units,
                p.active_location_mode_id,
                l.display_name,
                p.preferred_workout_style,
                p.theme_preference,
                p.gym_machine_cable_bias_enabled,
                p.history_workout_ab_flags_visible,
                p.dev_pick_next_exercise_enabled,
                p.dev_fruit_exercise_icons_enabled,
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
                .put("goal_primary", cursor.getString(0))
                .put("experience_level", cursor.getString(1))
                .put("default_duration_minutes", cursor.getInt(2))
                .put("weekly_frequency_target", cursor.getInt(3))
                .put("preferred_split_program_id", cursor.getLong(4))
                .putNullable("preferred_split_program_name", cursor.getStringOrNull(5))
                .put("units", cursor.getString(6))
                .put("active_location_mode_id", cursor.getLong(7))
                .putNullable("active_location_mode_name", cursor.getStringOrNull(8))
                .put("preferred_workout_style", cursor.getString(9))
                .put("theme_preference", cursor.getString(10))
                .put("gym_machine_cable_bias_enabled", cursor.getInt(11) == 1)
                .put("history_workout_ab_flags_visible", cursor.getInt(12) == 1)
                .put("dev_pick_next_exercise_enabled", cursor.getInt(13) == 1)
                .put("dev_fruit_exercise_icons_enabled", cursor.getInt(14) == 1)
                .put("next_focus", cursor.getString(15))
                .put("created_at_utc", cursor.getString(16))
                .put("updated_at_utc", cursor.getString(17))
        }
    }

    private fun exportEquipmentInventory(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT i.location_mode_id, l.display_name, i.equipment_name, i.is_available
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
                            .putNullable("location_mode_name", cursor.getStringOrNull(1))
                            .put("equipment_name", cursor.getString(2))
                            .put("is_available", cursor.getInt(3) == 1),
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
                            .put("signal_type", cursor.getString(0))
                            .put("workout_origin_type", cursor.getString(1))
                            .put("workout_title", cursor.getString(2))
                            .putNullable("workout_focus_key", cursor.getStringOrNull(3))
                            .putNullable("session_started_at_utc", cursor.getStringOrNull(4))
                            .put("exercise_id", cursor.getLong(5))
                            .put("exercise_name", cursor.getString(6))
                            .put("signal_value", cursor.getDouble(7))
                            .put("resulting_preference_score_delta", cursor.getDouble(8))
                            .put("created_at_utc", cursor.getString(9)),
                    )
                }
            }
        }
    }

    private fun exportTemplateExercises(db: SQLiteDatabase, templateId: Long): JSONArray {
        return db.rawQuery(
            """
            SELECT
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
                            .put("sort_order", cursor.getInt(0))
                            .put("exercise_id", cursor.getLong(1))
                            .putNullable("exercise_name", cursor.getStringOrNull(2))
                            .put("set_count", cursor.getInt(3))
                            .put("rep_range", cursor.getString(4))
                            .put("rest_seconds", cursor.getInt(5))
                            .put("rationale", cursor.getString(6)),
                    )
                }
            }
        }
    }

    private fun exportCompletedWorkouts(db: SQLiteDatabase): JSONArray {
        return db.rawQuery(
            """
            SELECT
                performed_workout_id,
                title,
                origin_type,
                location_mode_id,
                started_at_utc,
                completed_at_utc,
                actual_duration_seconds,
                ab_flags_snapshot_json
            FROM performed_workouts
            ORDER BY completed_at_utc DESC, performed_workout_id DESC
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
                            .put("started_at_utc", cursor.getString(4))
                            .put("completed_at_utc", cursor.getString(5))
                            .put("actual_duration_seconds", cursor.getInt(6))
                            .putNullable("abFlags", exportCompletedWorkoutAbFlags(cursor.getStringOrNull(7)))
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
                set_number,
                target_reps,
                recommended_reps,
                recommended_weight_value,
                actual_reps,
                weight_value,
                is_completed,
                recommendation_source,
                recommendation_confidence
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
                            .put("set_number", cursor.getInt(0))
                            .put("target_reps", cursor.getString(1))
                            .putNullable("recommended_reps", cursor.optionalInt(2))
                            .putNullable("recommended_weight_value", cursor.optionalDouble(3))
                            .putNullable("actual_reps", cursor.optionalInt(4))
                            .putNullable("weight_value", cursor.optionalDouble(5))
                            .put("is_completed", cursor.getInt(6) == 1)
                            .putNullable("recommendation_source", cursor.getStringOrNull(7))
                            .putNullable("recommendation_confidence", cursor.optionalDouble(8)),
                    )
                }
            }
        }
    }

    private fun exportCompletedWorkoutAbFlags(payload: String?): JSONObject? {
        val abFlags = deserializeCompletedWorkoutAbFlags(payload) ?: return null
        val completionFeedbackFlag = abFlags.completionFeedbackFlag ?: return null
        return JSONObject().put(
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

    private fun exportSession(db: SQLiteDatabase, workoutTable: String, idColumn: String): JSONObject? {
        val sessionHeader = db.rawQuery(
            """
            SELECT $idColumn, title, origin_type, location_mode_id, started_at_utc, focus_key
            FROM ${workoutTable}_workouts
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
                .put("started_at_utc", cursor.getString(4))
                .putNullable("focus_key", cursor.getStringOrNull(5))
        }
        return sessionHeader.put("exercises", exportSessionExercises(db, workoutTable))
    }

    private fun exportSessionExercises(db: SQLiteDatabase, workoutTable: String): JSONArray {
        val exerciseTable = "${workoutTable}_exercises"
        val exerciseIdColumn = "${workoutTable}_exercise_id"
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
                fruit_icon
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
                set_stable_id,
                set_number,
                target_reps,
                recommended_reps,
                recommended_weight_value,
                actual_reps,
                weight_value,
                is_completed,
                recommendation_source,
                recommendation_confidence
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
                            .put("set_stable_id", cursor.getLong(0))
                            .put("set_number", cursor.getInt(1))
                            .put("target_reps", cursor.getString(2))
                            .putNullable("recommended_reps", cursor.optionalInt(3))
                            .put("recommended_weight_value", cursor.getString(4))
                            .put("actual_reps", cursor.getString(5))
                            .put("weight_value", cursor.getString(6))
                            .put("is_completed", cursor.getInt(7) == 1)
                            .putNullable("recommendation_source", cursor.getStringOrNull(8))
                            .putNullable("recommendation_confidence", cursor.optionalDouble(9)),
                    )
                }
            }
        }
    }

    private fun Cursor.optionalInt(index: Int): Int? = if (isNull(index)) null else getInt(index)

    private fun Cursor.optionalDouble(index: Int): Double? = if (isNull(index)) null else getDouble(index)

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        put(key, value ?: JSONObject.NULL)
        return this
    }
}
