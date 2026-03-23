package com.fitlib.app.data

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject

open class ProgramRepository(private val database: FitLibDatabase?) {

    private fun db() = database!!.open()

    // ── TrainingProgram CRUD ──

    fun saveProgram(program: TrainingProgram) {
        val db = db()
        val cv = ContentValues().apply {
            put("id", program.id)
            put("title", program.title)
            put("goal", program.goal)
            put("primary_outcome_metric", program.primaryOutcomeMetric.name)
            put("program_archetype", program.programArchetype.name)
            put("periodization_model", program.periodizationModel.name)
            put("split_program_id", program.splitProgramId)
            put("total_weeks", program.totalWeeks)
            put("sessions_per_week", program.sessionsPerWeek)
            put("success_criteria_json", serializeSuccessCriteria(program.successCriteria))
            put("adaptation_policy_json", serializeAdaptationPolicy(program.adaptationPolicy))
            put("confidence_score", program.confidenceScore)
            put("last_reviewed_at", program.lastReviewedAt)
            put("created_at", program.createdAt)
            put("status", program.status.name)
        }
        db.insertWithOnConflict("training_programs", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadActiveProgram(): TrainingProgram? {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM training_programs WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY created_at DESC LIMIT 1",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            parseTrainingProgram(cursor)
        }
    }

    fun loadAllPrograms(): List<TrainingProgram> {
        val db = db()
        return db.rawQuery("SELECT * FROM training_programs ORDER BY created_at DESC", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(parseTrainingProgram(cursor))
                }
            }
        }
    }

    fun updateProgramStatus(programId: String, status: ProgramStatus) {
        val db = db()
        db.execSQL("UPDATE training_programs SET status = ? WHERE id = ?", arrayOf(status.name, programId))
    }

    open fun updateConfidenceScore(programId: String, score: Double) {
        val db = db()
        db.execSQL("UPDATE training_programs SET confidence_score = ? WHERE id = ?", arrayOf(score, programId))
    }

    // ── PlannedWeek CRUD ──

    fun savePlannedWeeks(weeks: List<PlannedWeek>) {
        val db = db()
        db.beginTransaction()
        try {
            weeks.forEach { week ->
                val cv = ContentValues().apply {
                    put("program_id", week.programId)
                    put("week_number", week.weekNumber)
                    put("week_type", week.weekType.name)
                    put("volume_multiplier", week.volumeMultiplier)
                    put("intensity_modifier", week.intensityModifier)
                }
                db.insertWithOnConflict("planned_weeks", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadWeeksForProgram(programId: String): List<PlannedWeek> {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM planned_weeks WHERE program_id = ? ORDER BY week_number",
            arrayOf(programId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PlannedWeek(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            programId = cursor.getString(cursor.getColumnIndexOrThrow("program_id")),
                            weekNumber = cursor.getInt(cursor.getColumnIndexOrThrow("week_number")),
                            weekType = WeekType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("week_type"))),
                            volumeMultiplier = cursor.getDouble(cursor.getColumnIndexOrThrow("volume_multiplier")),
                            intensityModifier = cursor.getDouble(cursor.getColumnIndexOrThrow("intensity_modifier")),
                        ),
                    )
                }
            }
        }
    }

    // ── PlannedSession CRUD ──

    fun savePlannedSessions(sessions: List<PlannedSession>) {
        val db = db()
        db.beginTransaction()
        try {
            sessions.forEach { session ->
                val cv = ContentValues().apply {
                    put("program_id", session.programId)
                    put("week_number", session.weekNumber)
                    put("day_index", session.dayIndex)
                    put("sequence_number", session.sequenceNumber)
                    put("focus_key", session.focusKey.name)
                    put("planned_sets", session.plannedSets)
                    if (session.timeBudgetMinutes != null) put("time_budget_minutes", session.timeBudgetMinutes) else putNull("time_budget_minutes")
                    put("status", session.status.name)
                    if (session.actualWorkoutId != null) put("actual_workout_id", session.actualWorkoutId) else putNull("actual_workout_id")
                    if (session.coachBrief != null) put("coach_brief", session.coachBrief) else putNull("coach_brief")
                }
                db.insert("planned_sessions", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    open fun loadSessionsForProgram(programId: String): List<PlannedSession> {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM planned_sessions WHERE program_id = ? ORDER BY sequence_number",
            arrayOf(programId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(parsePlannedSession(cursor))
                }
            }
        }
    }

    fun updateSessionStatus(sessionId: Long, status: SessionStatus, actualWorkoutId: Long? = null) {
        val db = db()
        if (actualWorkoutId != null) {
            db.execSQL(
                "UPDATE planned_sessions SET status = ?, actual_workout_id = ? WHERE id = ?",
                arrayOf(status.name, actualWorkoutId, sessionId),
            )
        } else {
            db.execSQL(
                "UPDATE planned_sessions SET status = ? WHERE id = ?",
                arrayOf(status.name, sessionId),
            )
        }
    }

    fun updateSessionCoachBrief(sessionId: Long, coachBrief: String) {
        val db = db()
        db.execSQL("UPDATE planned_sessions SET coach_brief = ? WHERE id = ?", arrayOf(coachBrief, sessionId))
    }

    fun updateSessionPlannedSets(sessionId: Long, plannedSets: Int) {
        val db = db()
        db.execSQL("UPDATE planned_sessions SET planned_sets = ? WHERE id = ?", arrayOf(plannedSets, sessionId))
    }

    fun updateUpcomingSessionTimeBudgets(
        programId: String,
        previousTimeBudgetMinutes: Int,
        newTimeBudgetMinutes: Int,
    ) {
        val db = db()
        db.execSQL(
            """
            UPDATE planned_sessions
            SET time_budget_minutes = ?
            WHERE program_id = ?
              AND status = 'UPCOMING'
              AND (time_budget_minutes IS NULL OR time_budget_minutes = ?)
            """.trimIndent(),
            arrayOf(newTimeBudgetMinutes, programId, previousTimeBudgetMinutes),
        )
    }

    fun insertReentrySession(
        session: PlannedSession,
        insertBeforeSequenceNumber: Int? = null,
    ): Long {
        val db = db()
        if (insertBeforeSequenceNumber != null) {
            db.execSQL(
                """
                UPDATE planned_sessions
                SET sequence_number = sequence_number + 1
                WHERE program_id = ? AND sequence_number >= ?
                """.trimIndent(),
                arrayOf(session.programId, insertBeforeSequenceNumber),
            )
        }
        val cv = ContentValues().apply {
            put("program_id", session.programId)
            put("week_number", session.weekNumber)
            put("day_index", session.dayIndex)
            put("sequence_number", insertBeforeSequenceNumber ?: session.sequenceNumber)
            put("focus_key", session.focusKey.name)
            put("planned_sets", session.plannedSets)
            if (session.timeBudgetMinutes != null) put("time_budget_minutes", session.timeBudgetMinutes) else putNull("time_budget_minutes")
            put("status", session.status.name)
            if (session.coachBrief != null) put("coach_brief", session.coachBrief) else putNull("coach_brief")
        }
        return db.insert("planned_sessions", null, cv)
    }

    open fun loadUpcomingSessions(programId: String, limit: Int): List<PlannedSession> {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM planned_sessions WHERE program_id = ? AND status = 'UPCOMING' ORDER BY sequence_number LIMIT ?",
            arrayOf(programId, limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(parsePlannedSession(cursor))
                }
            }
        }
    }

    fun currentPosition(programId: String): PlannedSession? {
        return loadUpcomingSessions(programId = programId, limit = 1).firstOrNull()
    }

    fun loadMostRecentSkippedSession(programId: String): PlannedSession? {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM planned_sessions WHERE program_id = ? AND status = 'SKIPPED' ORDER BY sequence_number DESC LIMIT 1",
            arrayOf(programId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else parsePlannedSession(cursor)
        }
    }

    fun maxSequenceNumber(programId: String): Int {
        val db = db()
        return db.rawQuery(
            "SELECT MAX(sequence_number) FROM planned_sessions WHERE program_id = ?",
            arrayOf(programId),
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0) else 0
        }
    }

    // ── PlannedSessionExercise CRUD ──

    fun saveSessionExercises(exercises: List<PlannedSessionExercise>) {
        val db = db()
        db.beginTransaction()
        try {
            exercises.forEach { ex ->
                val cv = ContentValues().apply {
                    put("planned_session_id", ex.plannedSessionId)
                    put("exercise_id", ex.exerciseId)
                    put("sort_order", ex.sortOrder)
                    put("execution_style", ex.executionStyle.name)
                }
                db.insert("planned_session_exercises", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadExercisesForSession(sessionId: Long): List<PlannedSessionExercise> {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM planned_session_exercises WHERE planned_session_id = ? ORDER BY sort_order",
            arrayOf(sessionId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PlannedSessionExercise(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            plannedSessionId = cursor.getLong(cursor.getColumnIndexOrThrow("planned_session_id")),
                            exerciseId = cursor.getLong(cursor.getColumnIndexOrThrow("exercise_id")),
                            sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
                            executionStyle = ExecutionStyle.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("execution_style"))),
                        ),
                    )
                }
            }
        }
    }

    fun updateExecutionStyle(id: Long, style: ExecutionStyle) {
        val db = db()
        db.execSQL("UPDATE planned_session_exercises SET execution_style = ? WHERE id = ?", arrayOf(style.name, id))
    }

    // ── ProgramExerciseSlot CRUD ──

    fun saveExerciseSlots(slots: List<ProgramExerciseSlot>) {
        val db = db()
        db.beginTransaction()
        try {
            slots.forEach { slot ->
                val cv = ContentValues().apply {
                    put("program_id", slot.programId)
                    put("exercise_id", slot.exerciseId)
                    put("role", slot.role.name)
                    put("baseline_weekly_set_target", slot.baselineWeeklySetTarget)
                    put("starting_sets", slot.progressionTrack.startingSets)
                    put("sets_per_week_increment", slot.progressionTrack.setsPerWeekIncrement)
                    put("load_progression_percent", slot.progressionTrack.loadProgressionPercent)
                    put("rep_range_shift", if (slot.progressionTrack.repRangeShift) 1 else 0)
                    if (slot.sfrScore != null) put("sfr_score", slot.sfrScore) else putNull("sfr_score")
                    if (slot.progressionTrack.evolutionTargetExerciseId != null) {
                        put("evolution_target_exercise_id", slot.progressionTrack.evolutionTargetExerciseId)
                    } else {
                        putNull("evolution_target_exercise_id")
                    }
                }
                db.insert("program_exercise_slots", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    open fun loadSlotsForProgram(programId: String): List<ProgramExerciseSlot> {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM program_exercise_slots WHERE program_id = ?",
            arrayOf(programId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ProgramExerciseSlot(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            programId = cursor.getString(cursor.getColumnIndexOrThrow("program_id")),
                            exerciseId = cursor.getLong(cursor.getColumnIndexOrThrow("exercise_id")),
                            role = ExerciseRole.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("role"))),
                            baselineWeeklySetTarget = cursor.getInt(cursor.getColumnIndexOrThrow("baseline_weekly_set_target")),
                            progressionTrack = ProgressionTrack(
                                startingSets = cursor.getInt(cursor.getColumnIndexOrThrow("starting_sets")),
                                setsPerWeekIncrement = cursor.getInt(cursor.getColumnIndexOrThrow("sets_per_week_increment")),
                                loadProgressionPercent = cursor.getDouble(cursor.getColumnIndexOrThrow("load_progression_percent")),
                                repRangeShift = cursor.getInt(cursor.getColumnIndexOrThrow("rep_range_shift")) == 1,
                                evolutionTargetExerciseId = if (cursor.isNull(cursor.getColumnIndexOrThrow("evolution_target_exercise_id"))) null
                                else cursor.getLong(cursor.getColumnIndexOrThrow("evolution_target_exercise_id")),
                            ),
                            sfrScore = if (cursor.isNull(cursor.getColumnIndexOrThrow("sfr_score"))) null
                            else cursor.getDouble(cursor.getColumnIndexOrThrow("sfr_score")),
                        ),
                    )
                }
            }
        }
    }

    fun updateSfrScore(slotId: Long, sfrScore: Double) {
        val db = db()
        db.execSQL("UPDATE program_exercise_slots SET sfr_score = ? WHERE id = ?", arrayOf(sfrScore, slotId))
    }

    // ── ProgramCheckpoint CRUD ──

    fun saveCheckpoints(checkpoints: List<ProgramCheckpoint>) {
        val db = db()
        db.beginTransaction()
        try {
            checkpoints.forEach { cp ->
                val cv = ContentValues().apply {
                    put("program_id", cp.programId)
                    put("week_number", cp.weekNumber)
                    put("checkpoint_type", cp.checkpointType.name)
                    put("status", cp.status.name)
                    if (cp.completedAt != null) put("completed_at", cp.completedAt) else putNull("completed_at")
                    if (cp.summary != null) put("summary", cp.summary) else putNull("summary")
                }
                db.insert("program_checkpoints", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadPendingCheckpoints(programId: String): List<ProgramCheckpoint> {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM program_checkpoints WHERE program_id = ? AND status = 'PENDING' ORDER BY week_number",
            arrayOf(programId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(parseProgramCheckpoint(cursor))
                }
            }
        }
    }

    open fun loadAllCheckpoints(programId: String): List<ProgramCheckpoint> {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM program_checkpoints WHERE program_id = ? ORDER BY week_number",
            arrayOf(programId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(parseProgramCheckpoint(cursor))
                }
            }
        }
    }

    open fun completeCheckpoint(checkpointId: Long, summary: String) {
        val db = db()
        db.execSQL(
            "UPDATE program_checkpoints SET status = 'COMPLETED', completed_at = ?, summary = ? WHERE id = ?",
            arrayOf(System.currentTimeMillis(), summary, checkpointId),
        )
    }

    // ── ProgramEvent CRUD ──

    open fun logEvent(event: ProgramEvent) {
        val db = db()
        val cv = ContentValues().apply {
            put("program_id", event.programId)
            put("event_type", event.eventType.name)
            put("payload_json", event.payloadJson)
            put("created_at", event.createdAt)
        }
        db.insert("program_events", null, cv)
    }

    fun loadEvents(programId: String): List<ProgramEvent> {
        val db = db()
        return db.rawQuery(
            "SELECT * FROM program_events WHERE program_id = ? ORDER BY created_at DESC",
            arrayOf(programId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ProgramEvent(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            programId = cursor.getString(cursor.getColumnIndexOrThrow("program_id")),
                            eventType = ProgramEventType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("event_type"))),
                            payloadJson = cursor.getString(cursor.getColumnIndexOrThrow("payload_json")),
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        ),
                    )
                }
            }
        }
    }

    // ── Convenience: count completed/skipped sessions ──

    open fun countSessionsByStatus(programId: String, status: SessionStatus): Int {
        val db = db()
        return db.rawQuery(
            "SELECT COUNT(*) FROM planned_sessions WHERE program_id = ? AND status = ?",
            arrayOf(programId, status.name),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun loadCompletedSessionIds(programId: String): List<Long> {
        val db = db()
        return db.rawQuery(
            "SELECT actual_workout_id FROM planned_sessions WHERE program_id = ? AND status = 'COMPLETED' AND actual_workout_id IS NOT NULL ORDER BY sequence_number",
            arrayOf(programId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getLong(0))
                }
            }
        }
    }

    fun updateWeekProgression(
        programId: String,
        weekNumber: Int,
        volumeMultiplier: Double? = null,
        intensityModifier: Double? = null,
    ) {
        val db = db()
        db.execSQL(
            """
            UPDATE planned_weeks
            SET
                volume_multiplier = COALESCE(?, volume_multiplier),
                intensity_modifier = COALESCE(?, intensity_modifier)
            WHERE program_id = ? AND week_number = ?
            """.trimIndent(),
            arrayOf(volumeMultiplier, intensityModifier, programId, weekNumber),
        )
    }

    fun updateSlotExercise(slotId: Long, exerciseId: Long) {
        val db = db()
        db.execSQL(
            "UPDATE program_exercise_slots SET exercise_id = ? WHERE id = ?",
            arrayOf(exerciseId, slotId),
        )
    }

    fun replaceUpcomingSessionExercise(
        programId: String,
        currentExerciseId: Long,
        replacementExerciseId: Long,
    ) {
        val db = db()
        db.execSQL(
            """
            UPDATE planned_session_exercises
            SET exercise_id = ?
            WHERE exercise_id = ?
              AND planned_session_id IN (
                  SELECT id
                  FROM planned_sessions
                  WHERE program_id = ? AND status = 'UPCOMING'
              )
            """.trimIndent(),
            arrayOf(replacementExerciseId, currentExerciseId, programId),
        )
    }

    // ── Helpers ──

    private fun parseTrainingProgram(cursor: android.database.Cursor): TrainingProgram {
        return TrainingProgram(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            goal = cursor.getString(cursor.getColumnIndexOrThrow("goal")),
            primaryOutcomeMetric = OutcomeMetric.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("primary_outcome_metric"))),
            programArchetype = ProgramArchetype.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("program_archetype"))),
            periodizationModel = PeriodizationModel.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("periodization_model"))),
            splitProgramId = cursor.getLong(cursor.getColumnIndexOrThrow("split_program_id")),
            totalWeeks = cursor.getInt(cursor.getColumnIndexOrThrow("total_weeks")),
            sessionsPerWeek = cursor.getInt(cursor.getColumnIndexOrThrow("sessions_per_week")),
            successCriteria = parseSuccessCriteria(cursor.getString(cursor.getColumnIndexOrThrow("success_criteria_json"))),
            adaptationPolicy = parseAdaptationPolicy(cursor.getString(cursor.getColumnIndexOrThrow("adaptation_policy_json"))),
            confidenceScore = cursor.getDouble(cursor.getColumnIndexOrThrow("confidence_score")),
            lastReviewedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("last_reviewed_at"))) null
            else cursor.getLong(cursor.getColumnIndexOrThrow("last_reviewed_at")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            status = ProgramStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
        )
    }

    private fun parsePlannedSession(cursor: android.database.Cursor): PlannedSession {
        return PlannedSession(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            programId = cursor.getString(cursor.getColumnIndexOrThrow("program_id")),
            weekNumber = cursor.getInt(cursor.getColumnIndexOrThrow("week_number")),
            dayIndex = cursor.getInt(cursor.getColumnIndexOrThrow("day_index")),
            sequenceNumber = cursor.getInt(cursor.getColumnIndexOrThrow("sequence_number")),
            focusKey = SessionFocus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("focus_key"))),
            plannedSets = cursor.getInt(cursor.getColumnIndexOrThrow("planned_sets")),
            timeBudgetMinutes = if (cursor.isNull(cursor.getColumnIndexOrThrow("time_budget_minutes"))) null
            else cursor.getInt(cursor.getColumnIndexOrThrow("time_budget_minutes")),
            status = SessionStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
            actualWorkoutId = if (cursor.isNull(cursor.getColumnIndexOrThrow("actual_workout_id"))) null
            else cursor.getLong(cursor.getColumnIndexOrThrow("actual_workout_id")),
            coachBrief = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("coach_brief")),
        )
    }

    private fun parseProgramCheckpoint(cursor: android.database.Cursor): ProgramCheckpoint {
        return ProgramCheckpoint(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            programId = cursor.getString(cursor.getColumnIndexOrThrow("program_id")),
            weekNumber = cursor.getInt(cursor.getColumnIndexOrThrow("week_number")),
            checkpointType = CheckpointType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("checkpoint_type"))),
            status = CheckpointStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
            completedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("completed_at"))) null
            else cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
            summary = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("summary")),
        )
    }

    companion object {
        fun serializeSuccessCriteria(sc: SuccessCriteria): String {
            val json = JSONObject()
            val lifts = JSONObject()
            sc.targetLifts.forEach { (id, outcome) ->
                lifts.put(id.toString(), JSONObject().apply {
                    put("metric", outcome.metric)
                    put("targetValue", outcome.targetValue)
                })
            }
            json.put("targetLifts", lifts)
            json.put("targetSessionCompletionRate", sc.targetSessionCompletionRate)
            return json.toString()
        }

        fun parseSuccessCriteria(raw: String): SuccessCriteria {
            return try {
                val json = JSONObject(raw)
                val liftsJson = json.optJSONObject("targetLifts") ?: JSONObject()
                val targetLifts = mutableMapOf<Long, TargetOutcome>()
                liftsJson.keys().forEach { key ->
                    val obj = liftsJson.getJSONObject(key)
                    targetLifts[key.toLong()] = TargetOutcome(
                        metric = obj.getString("metric"),
                        targetValue = obj.getDouble("targetValue"),
                    )
                }
                SuccessCriteria(
                    targetLifts = targetLifts,
                    targetSessionCompletionRate = json.optDouble("targetSessionCompletionRate", 0.8),
                )
            } catch (_: Exception) {
                SuccessCriteria(targetLifts = emptyMap(), targetSessionCompletionRate = 0.8)
            }
        }

        fun serializeAdaptationPolicy(ap: AdaptationPolicy): String {
            return JSONObject().apply {
                put("allowExerciseRepinning", ap.allowExerciseRepinning)
                put("maxWeeklySetDeltaPercent", ap.maxWeeklySetDeltaPercent)
                put("confidenceFloorForAutonomousChanges", ap.confidenceFloorForAutonomousChanges)
                put("triggerReviewAfterMissedSessions", ap.triggerReviewAfterMissedSessions)
            }.toString()
        }

        fun parseAdaptationPolicy(raw: String): AdaptationPolicy {
            return try {
                val json = JSONObject(raw)
                AdaptationPolicy(
                    allowExerciseRepinning = json.optBoolean("allowExerciseRepinning", false),
                    maxWeeklySetDeltaPercent = json.optDouble("maxWeeklySetDeltaPercent", 0.2),
                    confidenceFloorForAutonomousChanges = json.optDouble("confidenceFloorForAutonomousChanges", 0.4),
                    triggerReviewAfterMissedSessions = json.optInt("triggerReviewAfterMissedSessions", 2),
                )
            } catch (_: Exception) {
                AdaptationPolicy()
            }
        }
    }
}
