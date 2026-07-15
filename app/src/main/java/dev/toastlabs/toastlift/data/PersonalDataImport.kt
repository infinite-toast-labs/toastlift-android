package dev.toastlabs.toastlift.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

data class PersonalDataImportSummary(val completedWorkouts: Int, val customExercises: Int)

/** Replaces local personal data with a ToastLift full-backup export. */
class PersonalDataImporter(
    private val database: ToastLiftDatabase,
    private val customExercises: CustomExerciseRepository,
) {
    fun import(contents: String): PersonalDataImportSummary {
        val root = try { JSONObject(contents) } catch (error: Exception) {
            throw IllegalArgumentException("This file is not valid JSON.", error)
        }
        require(root.optString("app") == "ToastLift") { "This is not a ToastLift backup." }
        require(root.optString("backup_kind") == PERSONAL_DATA_EXPORT_KIND) { "This file is not a full personal-data backup." }
        require(root.optInt("schema_version") in 1..PERSONAL_DATA_EXPORT_SCHEMA_VERSION) {
            "This backup was made by a newer version of ToastLift."
        }
        val personal = root.optJSONObject("personal_data")
            ?: throw IllegalArgumentException("This backup has no personal data.")
        require(!personal.isNull("profile")) { "This backup does not contain a profile." }
        val replacesUserConfirmedExerciseAliases = personal.has("user_confirmed_exercise_aliases")

        val db = database.open()
        db.beginTransaction()
        try {
            clearPersonalData(db, replacesUserConfirmedExerciseAliases)
            // Custom exercises must exist before workouts/templates that reference them.
            customExercises.replaceCustomExercisesFromExport(db, personal.array("custom_exercises"))
            importProfile(db, personal.getJSONObject("profile"))
            importEquipment(db, personal.array("equipment_inventory"))
            importSimpleRows(db, "experiment_assignments", personal.array("experiment_assignments"), listOf("experiment_key", "variant_key", "assigned_at_utc"))
            importSimpleRows(db, "movement_restrictions", personal.array("movement_restrictions"), listOf("restriction_id", "restriction_scope", "restriction_value", "severity", "notes"))
            importSimpleRows(db, "exercise_preferences", personal.array("exercise_preferences"), listOf("exercise_id", "is_favorite", "is_hidden", "is_banned", "preference_score_delta", "notes", "updated_at_utc"))
            importSimpleRows(db, "exercise_generated_descriptions", personal.array("exercise_generated_descriptions"), listOf("exercise_id", "description", "generation_model", "generation_prompt_version", "created_at_utc", "updated_at_utc"))
            importSimpleRows(db, "exercise_user_video_links", personal.array("exercise_user_video_links"), listOf("user_video_link_id", "exercise_id", "label", "url", "created_at_utc", "updated_at_utc"))
            importUserConfirmedExerciseAliases(db, personal.array("user_confirmed_exercise_aliases"))
            importTemplates(db, personal.array("workout_templates"))
            importFeedback(db, personal.array("workout_feedback_signals"))
            importCompletedWorkouts(db, personal.array("completed_workouts"))
            importSession(db, personal.optJSONObject("active_workout"), "active")
            importSession(db, personal.optJSONObject("abandoned_workout"), "abandoned")
            importPrograms(db, personal.array("programs"))
            importSimpleRows(db, "earned_bounty_cards", personal.array("earned_bounty_cards"), listOf("card_id", "bounty_id", "bounty_type", "title", "family", "rarity", "resolution_scope", "earned_at_utc", "session_started_at_utc", "workout_id", "exercise_id", "exercise_name", "proof_line", "flavor_text", "art_seed", "source_set_number"))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        customExercises.refreshSnapshot()
        return PersonalDataImportSummary(personal.array("completed_workouts").length(), personal.array("custom_exercises").length())
    }

    private fun clearPersonalData(db: SQLiteDatabase, clearUserConfirmedExerciseAliases: Boolean) {
        if (clearUserConfirmedExerciseAliases) {
            db.execSQL(
                "DELETE FROM exercise_synonyms WHERE source = ?",
                arrayOf(USER_CONFIRMED_EXERCISE_SYNONYM_SOURCE),
            )
        }
        listOf("earned_bounty_cards", "active_workout_bounties", "active_workouts", "abandoned_workouts", "performed_workouts", "workout_templates", "workout_feedback_signals", "exercise_preferences", "exercise_generated_descriptions", "exercise_user_video_links", "movement_restrictions", "equipment_inventory", "experiment_assignments", "training_programs", "user_profile").forEach { db.execSQL("DELETE FROM $it") }
    }

    private fun importProfile(db: SQLiteDatabase, row: JSONObject) = insert(db, "user_profile", listOf(
        "user_id", "goal_primary", "experience_level", "default_duration_minutes", "weekly_frequency_target", "preferred_split_program_id", "units", "active_location_mode_id", "preferred_workout_style", "theme_preference", "smart_picker_body_filter", "smart_picker_target_muscle", "gym_machine_cable_bias_enabled", "history_workout_ab_flags_visible", "dev_pick_next_exercise_enabled", "dev_fruit_exercise_icons_enabled", "dev_exercise_detail_personal_note_visible", "dev_exercise_detail_learned_preference_visible", "dev_rest_timer_sound_disabled", "training_freshness_threshold_days", "training_freshness_min_bucket_exercises", "dev_session_set_swipe_complete_enabled", "dev_in_session_bounties_enabled", "custom_exercise_ai_model_id", "next_focus", "created_at_utc", "updated_at_utc"), row)

    private fun importEquipment(db: SQLiteDatabase, rows: JSONArray) = importSimpleRows(db, "equipment_inventory", rows, listOf("location_mode_id", "equipment_name", "is_available"))

    private fun importUserConfirmedExerciseAliases(db: SQLiteDatabase, rows: JSONArray) = rows.forEachObject { row ->
        require(row.optString("source", USER_CONFIRMED_EXERCISE_SYNONYM_SOURCE) == USER_CONFIRMED_EXERCISE_SYNONYM_SOURCE) {
            "This backup contains an unsupported exercise alias source."
        }
        val synonymName = normalizeExerciseSynonym(row.getString("synonym_name"))
            ?: throw IllegalArgumentException("This backup contains a blank exercise alias.")
        val normalizedKey = normalizedExerciseSynonymKey(synonymName)
        require(normalizedKey.isNotBlank()) { "This backup contains an invalid exercise alias." }
        require(row.optString("synonym_name_normalized", normalizedKey) == normalizedKey) {
            "This backup contains an invalid normalized exercise alias."
        }
        val values = ContentValues().apply {
            put("exercise_id", row.getLong("exercise_id"))
            put("synonym_name", synonymName)
            put("synonym_name_normalized", normalizedKey)
            put("synonym_type", "custom")
            put("source", USER_CONFIRMED_EXERCISE_SYNONYM_SOURCE)
            put("confidence_score", 1.0)
            put("created_at_utc", row.getString("created_at_utc"))
        }
        db.insertWithOnConflict(
            "exercise_synonyms",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun importTemplates(db: SQLiteDatabase, rows: JSONArray) = rows.forEachObject { row ->
        insert(db, "workout_templates", listOf("template_id", "name", "origin_type", "created_at_utc"), row)
        row.array("exercises").forEachObject { child ->
            val childWithTemplateId = JSONObject(child.toString()).put("template_id", row.getLong("template_id"))
            insert(
                db,
                "workout_template_exercises",
                listOf("template_exercise_id", "template_id", "sort_order", "exercise_id", "set_count", "rep_range", "rest_seconds", "rationale"),
                childWithTemplateId,
            )
        }
    }

    private fun importFeedback(db: SQLiteDatabase, rows: JSONArray) = importSimpleRows(db, "workout_feedback_signals", rows, listOf("signal_id", "signal_type", "workout_origin_type", "workout_title", "workout_focus_key", "session_started_at_utc", "exercise_id", "exercise_name", "signal_value", "resulting_preference_score_delta", "created_at_utc"))

    private fun importCompletedWorkouts(db: SQLiteDatabase, rows: JSONArray) = rows.forEachObject { workout ->
        insert(db, "performed_workouts", listOf("performed_workout_id", "title", "origin_type", "location_mode_id", "focus_key", "started_at_utc", "completed_at_utc", "actual_duration_seconds", "ab_flags_snapshot_json", "completion_receipt_snapshot_json"), workout)
        workout.array("exercises").forEachObject { exercise ->
            val exerciseWithWorkoutId = JSONObject(exercise.toString())
                .put("performed_workout_id", workout.getLong("performed_workout_id"))
            insert(
                db,
                "performed_exercises",
                listOf("performed_exercise_id", "performed_workout_id", "sort_order", "exercise_id", "exercise_name", "last_set_reps_in_reserve", "last_set_rpe"),
                exerciseWithWorkoutId,
            )
            exercise.array("sets").forEachObject { set ->
                val setWithExerciseId = JSONObject(set.toString())
                    .put("performed_exercise_id", exercise.getLong("performed_exercise_id"))
                insert(
                    db,
                    "performed_sets",
                    listOf("performed_set_id", "performed_exercise_id", "set_number", "target_reps", "recommended_reps", "recommended_weight_value", "actual_reps", "weight_value", "is_completed", "recommendation_source", "recommendation_confidence", "completed_at_utc", "work_unit_values_json"),
                    setWithExerciseId,
                )
            }
        }
    }

    private fun importSession(db: SQLiteDatabase, session: JSONObject?, prefix: String) {
        if (session == null) return
        val id = "${prefix}_workout_id"
        insert(db, "${prefix}_workouts", listOf(id, "title", "origin_type", "location_mode_id", "started_at_utc", "focus_key", "subtitle", "estimated_minutes", "session_format", "is_paused", "paused_at_utc", "accumulated_paused_seconds", "selected_exercise_index").filter { prefix == "active" || it != "selected_exercise_index" }, session)
        session.array("exercises").forEachObject { exercise ->
            val exId = "${prefix}_exercise_id"
            val exerciseWithParent = JSONObject(exercise.toString()).put("${prefix}_workout_id", 1)
            insert(db, "${prefix}_exercises", listOf(exId, "${prefix}_workout_id", "sort_order", "exercise_id", "exercise_name", "body_region", "target_muscle_group", "equipment", "rest_seconds", "notes", "last_set_reps_in_reserve", "fruit_icon").filter { prefix == "active" || it != "last_set_reps_in_reserve" }, exerciseWithParent)
            exercise.array("sets").forEachObject { set ->
                val setWithExerciseId = JSONObject(set.toString()).put(exId, exercise.getLong(exId))
                insert(
                    db,
                    "${prefix}_sets",
                    listOf("${prefix}_set_id", exId, "set_stable_id", "set_number", "target_reps", "recommended_reps", "recommended_weight_value", "actual_reps", "weight_value", "is_completed", "recommendation_source", "recommendation_confidence", "completed_at_utc", "work_unit_values_json"),
                    setWithExerciseId,
                )
            }
        }
    }

    private fun importPrograms(db: SQLiteDatabase, rows: JSONArray) = rows.forEachObject { program ->
        insert(db, "training_programs", listOf("id", "title", "goal", "primary_outcome_metric", "program_archetype", "periodization_model", "split_program_id", "total_weeks", "sessions_per_week", "success_criteria_json", "adaptation_policy_json", "confidence_score", "last_reviewed_at", "created_at", "status"), program)
        importSimpleRows(db, "planned_weeks", program.array("planned_weeks"), listOf("id", "program_id", "week_number", "week_type", "volume_multiplier", "intensity_modifier"))
        program.array("planned_sessions").forEachObject { session ->
            insert(db, "planned_sessions", listOf("id", "program_id", "week_number", "day_index", "sequence_number", "focus_key", "planned_sets", "time_budget_minutes", "status", "actual_workout_id", "status_updated_at_utc", "coach_brief", "completion_ratio", "completion_credit", "completion_truth"), session)
            importSimpleRows(db, "planned_session_exercises", session.array("exercises"), listOf("id", "planned_session_id", "exercise_id", "sort_order", "execution_style"))
        }
        importSimpleRows(db, "program_exercise_slots", program.array("exercise_slots"), listOf("id", "program_id", "exercise_id", "role", "baseline_weekly_set_target", "starting_sets", "sets_per_week_increment", "load_progression_percent", "rep_range_shift", "sfr_score", "evolution_target_exercise_id"))
        importSimpleRows(db, "program_checkpoints", program.array("checkpoints"), listOf("id", "program_id", "week_number", "checkpoint_type", "status", "completed_at", "summary"))
        importSimpleRows(db, "program_events", program.array("events"), listOf("id", "program_id", "event_type", "payload_json", "created_at"))
    }

    private fun importSimpleRows(db: SQLiteDatabase, table: String, rows: JSONArray, columns: List<String>) = rows.forEachObject { insert(db, table, columns, it) }
    private fun insert(db: SQLiteDatabase, table: String, columns: List<String>, row: JSONObject) {
        val values = ContentValues()
        columns.forEach { column -> if (row.has(column) && !row.isNull(column)) values.putAny(column, row.get(column)) }
        db.insertOrThrow(table, null, values)
    }
    private fun ContentValues.putAny(key: String, value: Any) = when (value) { is Boolean -> put(key, if (value) 1 else 0); is Int -> put(key, value); is Long -> put(key, value); is Double -> put(key, value); is Number -> put(key, value.toDouble()); else -> put(key, value.toString()) }
    private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) { for (index in 0 until length()) block(getJSONObject(index)) }
}
