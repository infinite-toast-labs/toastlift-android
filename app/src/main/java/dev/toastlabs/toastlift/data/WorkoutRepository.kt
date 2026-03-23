package dev.toastlabs.toastlift.data

import java.time.Duration
import java.time.Instant

private const val MAX_LEARNED_RECOMMENDATION_DELTA = 3.0

internal data class ExerciseHistoryRow(
    val completedAtUtc: String,
    val workoutTitle: String,
    val targetReps: String,
    val lastSetRepsInReserve: Int?,
    val lastSetRpe: Double?,
    val setNumber: Int,
    val reps: Int?,
    val weight: Double?,
    val isCompleted: Boolean,
)

internal data class WeeklyMuscleTargetWorkoutRow(
    val completedAtUtc: String,
    val exerciseId: Long,
    val completedSetCount: Int,
)

internal fun buildExerciseHistoryDetail(
    exerciseId: Long,
    fallbackName: String,
    rows: List<ExerciseHistoryRow>,
    prOnly: Boolean,
): ExerciseHistoryDetail {
    if (rows.isEmpty()) {
        return ExerciseHistoryDetail(
            exerciseId = exerciseId,
            exerciseName = fallbackName,
            entries = emptyList(),
            isPrOnlyFilterEnabled = prOnly,
            totalEntries = 0,
            prEntryCount = 0,
        )
    }

    var maxReps = Double.NEGATIVE_INFINITY
    var maxWeight = Double.NEGATIVE_INFINITY
    var maxVolume = Double.NEGATIVE_INFINITY

    val allEntries = rows
        .groupBy { it.completedAtUtc to it.workoutTitle }
        .map { (header, sets) ->
            val workingSets = sets
                .filter { it.isCompleted }
                .sortedBy { it.setNumber }
                .map { row ->
                    val reps = row.reps
                    val weight = row.weight
                    val volume = if (reps != null && weight != null) reps * weight else null
                    val repPr = reps != null && reps.toDouble() > maxReps
                    val weightPr = weight != null && weight > maxWeight
                    val volumePr = volume != null && volume > maxVolume
                    if (reps != null && reps.toDouble() > maxReps) maxReps = reps.toDouble()
                    if (weight != null && weight > maxWeight) maxWeight = weight
                    if (volume != null && volume > maxVolume) maxVolume = volume
                    ExerciseHistorySet(
                        setNumber = row.setNumber,
                        reps = reps,
                        weight = weight,
                        isRepPr = repPr,
                        isWeightPr = weightPr,
                        isVolumePr = volumePr,
                    )
                }
            val displayWorkingSets = workingSets.asReversed()
            val estimatedOneRepMax = workingSets.mapNotNull { set ->
                val reps = set.reps ?: return@mapNotNull null
                val weight = set.weight ?: return@mapNotNull null
                weight * (1.0 + reps / 30.0)
            }.maxOrNull()
            val hasPersonalRecord = workingSets.any { it.isRepPr || it.isWeightPr || it.isVolumePr }
            ExerciseHistoryEntry(
                completedAtUtc = header.first,
                workoutTitle = header.second,
                targetReps = sets.firstOrNull()?.targetReps.orEmpty(),
                estimatedOneRepMax = estimatedOneRepMax,
                totalVolume = workingSets.sumOf { (it.reps ?: 0) * (it.weight ?: 0.0) },
                bestWeight = workingSets.maxOfOrNull { it.weight ?: 0.0 } ?: 0.0,
                lastSetRepsInReserve = sets.firstOrNull()?.lastSetRepsInReserve,
                lastSetRpe = sets.firstOrNull()?.lastSetRpe,
                workingSets = displayWorkingSets,
                hasPersonalRecord = hasPersonalRecord,
            )
        }
        .reversed()

    val prEntryCount = allEntries.count { it.hasPersonalRecord }
    val entries = if (prOnly) allEntries.filter { it.hasPersonalRecord } else allEntries

    return ExerciseHistoryDetail(
        exerciseId = exerciseId,
        exerciseName = fallbackName,
        entries = entries,
        isPrOnlyFilterEnabled = prOnly,
        totalEntries = allEntries.size,
        prEntryCount = prEntryCount,
    )
}

internal fun historyReuseRepRange(startingSets: List<WorkoutExerciseSetDraft>): String {
    val explicitTarget = startingSets.firstOrNull { it.targetReps.isNotBlank() }?.targetReps?.trim()
    if (!explicitTarget.isNullOrBlank()) return explicitTarget

    val performedReps = startingSets.mapNotNull(WorkoutExerciseSetDraft::reps)
    if (performedReps.isNotEmpty()) {
        val min = performedReps.minOrNull() ?: performedReps.first()
        val max = performedReps.maxOrNull() ?: performedReps.first()
        return if (min == max) min.toString() else "$min-$max"
    }

    val recommendedReps = startingSets.mapNotNull(WorkoutExerciseSetDraft::recommendedReps)
    if (recommendedReps.isNotEmpty()) {
        val min = recommendedReps.minOrNull() ?: recommendedReps.first()
        val max = recommendedReps.maxOrNull() ?: recommendedReps.first()
        return if (min == max) min.toString() else "$min-$max"
    }

    return "8-12"
}

internal fun historyReuseSuggestedWeight(startingSets: List<WorkoutExerciseSetDraft>): Double? {
    return startingSets.lastOrNull { it.weight != null }?.weight
        ?: startingSets.lastOrNull { it.recommendedWeight != null }?.recommendedWeight
}

internal fun historyReuseRestSeconds(repRange: String): Int {
    val lowerBound = repRange.substringBefore('-').trim().toIntOrNull()
    return when {
        lowerBound == null -> 75
        lowerBound <= 5 -> 120
        lowerBound <= 8 -> 90
        lowerBound <= 12 -> 75
        else -> 60
    }
}

internal fun historyReuseEstimatedMinutes(exercises: List<WorkoutExercise>): Int {
    return exercises.sumOf { exercise ->
        val setCount = exercise.startingSets.size.takeIf { it > 0 } ?: exercise.sets
        maxOf(1, setCount) * 2
    }.coerceAtLeast(5)
}

class WorkoutRepository(private val database: ToastLiftDatabase, private val catalogRepository: CatalogRepository) {
    fun saveTemplate(name: String, origin: String, exercises: List<WorkoutExercise>) {
        if (name.isBlank() || exercises.isEmpty()) return
        val db = database.open()
        val now = Instant.now().toString()
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT INTO workout_templates (name, origin_type, created_at_utc) VALUES (?, ?, ?)",
                arrayOf(name.trim(), origin, now),
            )
            val templateId = db.rawQuery("SELECT last_insert_rowid()", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
            exercises.forEachIndexed { index, exercise ->
                db.execSQL(
                    """
                    INSERT INTO workout_template_exercises (
                        template_id, sort_order, exercise_id, set_count, rep_range, rest_seconds, rationale
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        templateId,
                        index,
                        exercise.exerciseId,
                        exercise.sets,
                        exercise.repRange,
                        exercise.restSeconds,
                        exercise.rationale,
                    ),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadTemplates(): List<TemplateSummary> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT t.template_id, t.name, COUNT(e.template_exercise_id)
            FROM workout_templates t
            LEFT JOIN workout_template_exercises e ON e.template_id = t.template_id
            GROUP BY t.template_id, t.name
            ORDER BY t.created_at_utc DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(TemplateSummary(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)))
                }
            }
        }
    }

    fun loadTemplate(templateId: Long): WorkoutPlan? {
        val db = database.open()
        val header = db.rawQuery(
            "SELECT name, origin_type FROM workout_templates WHERE template_id = ?",
            arrayOf(templateId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getString(0) to cursor.getString(1)
        }
        val exerciseRows = db.rawQuery(
            """
            SELECT exercise_id, set_count, rep_range, rest_seconds, rationale
            FROM workout_template_exercises
            WHERE template_id = ?
            ORDER BY sort_order
            """.trimIndent(),
            arrayOf(templateId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Triple(
                            cursor.getLong(0),
                            cursor.getInt(1) to cursor.getString(2),
                            cursor.getInt(3) to cursor.getString(4),
                        ),
                    )
                }
            }
        }
        val summaries = catalogRepository.searchExercisesByIds(exerciseRows.map { it.first })
        val byId = summaries.associateBy { it.id }
        val workoutExercises = exerciseRows.mapNotNull { (exerciseId, setAndRep, restAndRationale) ->
            val summary = byId[exerciseId] ?: return@mapNotNull null
            WorkoutExercise(
                exerciseId = summary.id,
                name = summary.name,
                bodyRegion = summary.bodyRegion,
                targetMuscleGroup = summary.targetMuscleGroup,
                equipment = summary.equipment,
                sets = setAndRep.first,
                repRange = setAndRep.second,
                restSeconds = restAndRationale.first,
                rationale = restAndRationale.second,
            )
        }
        return WorkoutPlan(
            title = header.first,
            subtitle = "Saved ${header.second.replaceFirstChar { it.uppercase() }} template",
            locationModeId = 1L,
            estimatedMinutes = workoutExercises.sumOf { it.sets * 2 },
            origin = "template",
            exercises = workoutExercises,
        )
    }

    fun loadEditableTemplate(templateId: Long): EditableTemplate? {
        val db = database.open()
        val header = db.rawQuery(
            "SELECT name, origin_type FROM workout_templates WHERE template_id = ?",
            arrayOf(templateId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getString(0) to cursor.getString(1)
        }
        val exercises = loadTemplateExercises(templateId)
        return EditableTemplate(
            id = templateId,
            name = header.first,
            origin = header.second,
            exercises = exercises,
        )
    }

    fun renameTemplate(templateId: Long, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        database.open().execSQL(
            "UPDATE workout_templates SET name = ? WHERE template_id = ?",
            arrayOf(trimmedName, templateId),
        )
    }

    fun updateTemplate(templateId: Long, name: String, origin: String, exercises: List<WorkoutExercise>) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || exercises.isEmpty()) return
        val db = database.open()
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE workout_templates SET name = ?, origin_type = ? WHERE template_id = ?",
                arrayOf(trimmedName, origin, templateId),
            )
            db.execSQL(
                "DELETE FROM workout_template_exercises WHERE template_id = ?",
                arrayOf(templateId),
            )
            insertTemplateExercises(db, templateId, exercises)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteTemplate(templateId: Long) {
        database.open().execSQL(
            "DELETE FROM workout_templates WHERE template_id = ?",
            arrayOf(templateId),
        )
    }

    fun saveCompletedWorkout(
        session: ActiveSession,
        abFlags: CompletedWorkoutAbFlags? = null,
    ): Long? {
        val db = database.open()
        val completedAt = Instant.now()
        val durationSeconds = Duration.between(Instant.parse(session.startedAtUtc), completedAt).seconds.toInt().coerceAtLeast(60)
        if (session.exercises.isEmpty()) return null

        val workoutId: Long
        db.beginTransaction()
        try {
            db.execSQL(
                """
                INSERT INTO performed_workouts (
                    title, origin_type, location_mode_id, started_at_utc, completed_at_utc, actual_duration_seconds,
                    ab_flags_snapshot_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    session.title,
                    session.origin,
                    session.locationModeId,
                    session.startedAtUtc,
                    completedAt.toString(),
                    durationSeconds,
                    serializeCompletedWorkoutAbFlags(abFlags),
                ),
            )
            workoutId = db.rawQuery("SELECT last_insert_rowid()", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
            session.exercises.forEachIndexed { index, exercise ->
                db.execSQL(
                    """
                    INSERT INTO performed_exercises (
                        performed_workout_id, sort_order, exercise_id, exercise_name,
                        last_set_reps_in_reserve, last_set_rpe
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        workoutId,
                        index,
                        exercise.exerciseId,
                        exercise.name,
                        exercise.lastSetRepsInReserve,
                        exercise.lastSetRepsInReserve?.let { 10 - it.toDouble() },
                    ),
                )
                val performedExerciseId = db.rawQuery("SELECT last_insert_rowid()", null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(0)
                }
                exercise.sets.forEach { set ->
                    db.execSQL(
                        """
                        INSERT INTO performed_sets (
                            performed_exercise_id, set_number, target_reps, recommended_reps,
                            recommended_weight_value, actual_reps, weight_value, is_completed,
                            recommendation_source, recommendation_confidence
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf(
                            performedExerciseId,
                            set.setNumber,
                            set.targetReps,
                            set.recommendedReps,
                            set.recommendedWeight.toDoubleOrNull(),
                            set.reps.toIntOrNull(),
                            set.weight.toDoubleOrNull(),
                            if (set.completed) 1 else 0,
                            set.recommendationSource.name,
                            set.recommendationConfidence,
                        ),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return workoutId
    }

    fun recordGeneratedWorkoutFeedbackSignal(
        workout: WorkoutPlan,
        exercise: WorkoutExercise,
        signalType: WorkoutFeedbackSignalType,
    ) {
        recordWorkoutFeedbackSignal(
            workoutOrigin = workout.origin,
            workoutTitle = workout.title,
            workoutFocusKey = workout.focusKey,
            sessionStartedAtUtc = null,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
            signalType = signalType,
        )
    }

    fun recordActiveSessionFeedbackSignal(
        session: ActiveSession,
        exercise: SessionExercise,
        signalType: WorkoutFeedbackSignalType,
    ) {
        recordWorkoutFeedbackSignal(
            workoutOrigin = session.origin,
            workoutTitle = session.title,
            workoutFocusKey = session.focusKey,
            sessionStartedAtUtc = session.startedAtUtc,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
            signalType = signalType,
        )
    }

    fun recordSkippedExerciseFeedback(prompt: SkippedExerciseFeedbackPrompt, signalType: WorkoutFeedbackSignalType) {
        recordWorkoutFeedbackSignal(
            workoutOrigin = prompt.workoutOrigin,
            workoutTitle = prompt.workoutTitle,
            workoutFocusKey = prompt.workoutFocusKey,
            sessionStartedAtUtc = prompt.sessionStartedAtUtc,
            exerciseId = prompt.exerciseId,
            exerciseName = prompt.exerciseName,
            signalType = signalType,
        )
    }

    fun saveActiveSession(session: ActiveSession, selectedExerciseIndex: Int?) {
        val db = database.open()
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM active_workouts")
            db.execSQL(
                """
                INSERT INTO active_workouts (
                    active_workout_id, title, origin_type, location_mode_id, started_at_utc, focus_key,
                    subtitle, estimated_minutes, session_format, selected_exercise_index
                ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    session.title,
                    session.origin,
                    session.locationModeId,
                    session.startedAtUtc,
                    session.focusKey,
                    session.subtitle,
                    session.estimatedMinutes,
                    session.sessionFormat,
                    selectedExerciseIndex,
                ),
            )
            session.exercises.forEachIndexed { index, exercise ->
                db.execSQL(
                    """
                    INSERT INTO active_exercises (
                        active_workout_id, sort_order, exercise_id, exercise_name, body_region,
                        target_muscle_group, equipment, rest_seconds, notes, last_set_reps_in_reserve,
                        completion_sequence, fruit_icon
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        1,
                        index,
                        exercise.exerciseId,
                        exercise.name,
                        exercise.bodyRegion,
                        exercise.targetMuscleGroup,
                        exercise.equipment,
                        exercise.restSeconds,
                        exercise.notes,
                        exercise.lastSetRepsInReserve,
                        exercise.completionSequence,
                        exercise.fruitIcon,
                    ),
                )
                val activeExerciseId = db.rawQuery("SELECT last_insert_rowid()", null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(0)
                }
                exercise.sets.forEach { set ->
                    db.execSQL(
                        """
                        INSERT INTO active_sets (
                            active_exercise_id, set_stable_id, set_number, target_reps, recommended_reps,
                            recommended_weight_value, actual_reps, weight_value, is_completed,
                            recommendation_source, recommendation_confidence
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf(
                            activeExerciseId,
                            set.id,
                            set.setNumber,
                            set.targetReps,
                            set.recommendedReps,
                            set.recommendedWeight,
                            set.reps,
                            set.weight,
                            if (set.completed) 1 else 0,
                            set.recommendationSource.name,
                            set.recommendationConfidence,
                        ),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadActiveSession(): PersistedActiveSessionState? {
        val db = database.open()
        val header = db.rawQuery(
            """
            SELECT
                title,
                origin_type,
                location_mode_id,
                started_at_utc,
                focus_key,
                subtitle,
                estimated_minutes,
                session_format,
                selected_exercise_index
            FROM active_workouts
            WHERE active_workout_id = 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            ActiveSession(
                title = cursor.getString(0),
                origin = cursor.getString(1),
                locationModeId = cursor.getLong(2),
                startedAtUtc = cursor.getString(3),
                focusKey = cursor.getString(4),
                subtitle = cursor.getString(5).orEmpty(),
                estimatedMinutes = if (cursor.isNull(6)) null else cursor.getInt(6),
                sessionFormat = cursor.getStringOrNull(7),
                exercises = emptyList(),
            ) to if (cursor.isNull(8)) null else cursor.getInt(8)
        }
        val exercises = db.rawQuery(
            """
            SELECT active_exercise_id, exercise_id, exercise_name, body_region, target_muscle_group,
                   equipment, rest_seconds, notes, last_set_reps_in_reserve, completion_sequence,
                   fruit_icon
            FROM active_exercises
            WHERE active_workout_id = 1
            ORDER BY sort_order
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val activeExerciseId = cursor.getLong(0)
                    val sets = db.rawQuery(
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
                        FROM active_sets
                        WHERE active_exercise_id = ?
                        ORDER BY set_number
                        """.trimIndent(),
                        arrayOf(activeExerciseId.toString()),
                    ).use { setCursor ->
                        buildList {
                            while (setCursor.moveToNext()) {
                                add(
                                    SessionSet(
                                        id = setCursor.getLong(0),
                                        setNumber = setCursor.getInt(1),
                                        targetReps = setCursor.getString(2),
                                        recommendedReps = if (setCursor.isNull(3)) null else setCursor.getInt(3),
                                        recommendedWeight = setCursor.getString(4),
                                        reps = setCursor.getString(5),
                                        weight = setCursor.getString(6),
                                        completed = setCursor.getInt(7) == 1,
                                        recommendationSource = recommendationSourceFromStorage(setCursor.getStringOrNull(8)),
                                        recommendationConfidence = if (setCursor.isNull(9)) null else setCursor.getDouble(9),
                                    ),
                                )
                            }
                        }
                    }
                    add(
                        SessionExercise(
                            exerciseId = cursor.getLong(1),
                            name = cursor.getString(2),
                            bodyRegion = cursor.getString(3),
                            targetMuscleGroup = cursor.getString(4),
                            equipment = cursor.getString(5),
                            restSeconds = cursor.getInt(6),
                            completionSequence = if (cursor.isNull(9)) null else cursor.getInt(9),
                            lastSetRepsInReserve = if (cursor.isNull(8)) null else cursor.getInt(8),
                            notes = cursor.getString(7),
                            fruitIcon = cursor.getStringOrNull(10),
                            sets = sets,
                        ),
                    )
                }
            }
        }
        return PersistedActiveSessionState(
            session = header.first.copy(exercises = exercises),
            selectedExerciseIndex = header.second,
        )
    }

    fun clearActiveSession() {
        database.open().execSQL("DELETE FROM active_workouts")
    }

    fun saveAbandonedWorkout(session: ActiveSession) {
        val db = database.open()
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM abandoned_workouts")
            db.execSQL(
                """
                INSERT INTO abandoned_workouts (
                    abandoned_workout_id, title, origin_type, location_mode_id, started_at_utc, focus_key,
                    subtitle, estimated_minutes, session_format
                ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    session.title,
                    session.origin,
                    session.locationModeId,
                    session.startedAtUtc,
                    session.focusKey,
                    session.subtitle,
                    session.estimatedMinutes,
                    session.sessionFormat,
                ),
            )
            session.exercises.forEachIndexed { index, exercise ->
                db.execSQL(
                    """
                    INSERT INTO abandoned_exercises (
                        abandoned_workout_id, sort_order, exercise_id, exercise_name, body_region,
                        target_muscle_group, equipment, rest_seconds, notes, last_set_reps_in_reserve,
                        fruit_icon
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        1,
                        index,
                        exercise.exerciseId,
                        exercise.name,
                        exercise.bodyRegion,
                        exercise.targetMuscleGroup,
                        exercise.equipment,
                        exercise.restSeconds,
                        exercise.notes,
                        exercise.lastSetRepsInReserve,
                        exercise.fruitIcon,
                    ),
                )
                val abandonedExerciseId = db.rawQuery("SELECT last_insert_rowid()", null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(0)
                }
                exercise.sets.forEach { set ->
                    db.execSQL(
                        """
                        INSERT INTO abandoned_sets (
                            abandoned_exercise_id, set_stable_id, set_number, target_reps, recommended_reps,
                            recommended_weight_value, actual_reps, weight_value, is_completed,
                            recommendation_source, recommendation_confidence
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf(
                            abandonedExerciseId,
                            set.id,
                            set.setNumber,
                            set.targetReps,
                            set.recommendedReps,
                            set.recommendedWeight,
                            set.reps,
                            set.weight,
                            if (set.completed) 1 else 0,
                            set.recommendationSource.name,
                            set.recommendationConfidence,
                        ),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadAbandonedWorkout(): ActiveSession? {
        val db = database.open()
        val header = db.rawQuery(
            """
            SELECT
                title,
                origin_type,
                location_mode_id,
                started_at_utc,
                focus_key,
                subtitle,
                estimated_minutes,
                session_format
            FROM abandoned_workouts
            WHERE abandoned_workout_id = 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            listOf(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getLong(2).toString(),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getString(5).orEmpty(),
                if (cursor.isNull(6)) "" else cursor.getInt(6).toString(),
                cursor.getStringOrNull(7).orEmpty(),
            )
        }
        val exercises = db.rawQuery(
            """
            SELECT abandoned_exercise_id, exercise_id, exercise_name, body_region, target_muscle_group,
                   equipment, rest_seconds, notes, last_set_reps_in_reserve, fruit_icon
            FROM abandoned_exercises
            WHERE abandoned_workout_id = 1
            ORDER BY sort_order
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val abandonedExerciseId = cursor.getLong(0)
                    val sets = db.rawQuery(
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
                        FROM abandoned_sets
                        WHERE abandoned_exercise_id = ?
                        ORDER BY set_number
                        """.trimIndent(),
                        arrayOf(abandonedExerciseId.toString()),
                    ).use { setCursor ->
                        buildList {
                            while (setCursor.moveToNext()) {
                                add(
                                    SessionSet(
                                        id = setCursor.getLong(0),
                                        setNumber = setCursor.getInt(1),
                                        targetReps = setCursor.getString(2),
                                        recommendedReps = if (setCursor.isNull(3)) null else setCursor.getInt(3),
                                        recommendedWeight = setCursor.getString(4),
                                        reps = setCursor.getString(5),
                                        weight = setCursor.getString(6),
                                        completed = setCursor.getInt(7) == 1,
                                        recommendationSource = recommendationSourceFromStorage(setCursor.getStringOrNull(8)),
                                        recommendationConfidence = if (setCursor.isNull(9)) null else setCursor.getDouble(9),
                                    ),
                                )
                            }
                        }
                    }
                    add(
                        SessionExercise(
                            exerciseId = cursor.getLong(1),
                            name = cursor.getString(2),
                            bodyRegion = cursor.getString(3),
                            targetMuscleGroup = cursor.getString(4),
                            equipment = cursor.getString(5),
                            restSeconds = cursor.getInt(6),
                            lastSetRepsInReserve = if (cursor.isNull(8)) null else cursor.getInt(8),
                            notes = cursor.getString(7),
                            fruitIcon = cursor.getStringOrNull(9),
                            sets = sets,
                        ),
                    )
                }
            }
        }
        return ActiveSession(
            title = header[0],
            origin = header[1],
            locationModeId = header[2].toLong(),
            startedAtUtc = header[3],
            focusKey = header[4],
            subtitle = header[5],
            estimatedMinutes = header[6].toIntOrNull(),
            sessionFormat = header[7].ifBlank { null },
            exercises = exercises,
        )
    }

    fun clearAbandonedWorkout() {
        database.open().execSQL("DELETE FROM abandoned_workouts")
    }

    fun loadHistory(strengthScoreSummary: StrengthScoreSummary? = null): List<HistorySummary> {
        val db = database.open()
        val history = db.rawQuery(
            """
            SELECT
                pw.performed_workout_id,
                pw.title,
                pw.completed_at_utc,
                pw.actual_duration_seconds,
                COALESCE(SUM(
                    CASE
                        WHEN ps.is_completed = 1 THEN COALESCE(ps.actual_reps, 0) * COALESCE(ps.weight_value, 0)
                        ELSE 0
                    END
                ), 0),
                COUNT(DISTINCT pe.performed_exercise_id)
            FROM performed_workouts pw
            LEFT JOIN performed_exercises pe ON pe.performed_workout_id = pw.performed_workout_id
            LEFT JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            GROUP BY pw.performed_workout_id, pw.title, pw.completed_at_utc, pw.actual_duration_seconds
            ORDER BY completed_at_utc DESC
            LIMIT 20
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val workoutId = cursor.getLong(0)
                    add(
                        HistorySummary(
                            id = workoutId,
                            title = cursor.getString(1),
                            completedAtUtc = cursor.getString(2),
                            durationSeconds = cursor.getInt(3),
                            totalVolume = cursor.getDouble(4),
                            exerciseCount = cursor.getInt(5),
                            exerciseNames = loadExerciseNamesForWorkout(workoutId),
                        ),
                    )
                }
            }
        }
        return applyStrengthScores(
            history = history,
            strengthScoreSummary = strengthScoreSummary,
        )
    }

    internal fun loadWeeklyMuscleTargetRows(sinceUtcInclusive: String): List<WeeklyMuscleTargetWorkoutRow> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT
                pw.completed_at_utc,
                pe.exercise_id,
                COALESCE(SUM(CASE WHEN ps.is_completed = 1 THEN 1 ELSE 0 END), 0) AS completed_set_count
            FROM performed_workouts pw
            INNER JOIN performed_exercises pe ON pe.performed_workout_id = pw.performed_workout_id
            INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pw.completed_at_utc IS NOT NULL
              AND pw.completed_at_utc >= ?
            GROUP BY pw.completed_at_utc, pe.exercise_id
            HAVING completed_set_count > 0
            ORDER BY pw.completed_at_utc DESC
            """.trimIndent(),
            arrayOf(sinceUtcInclusive),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        WeeklyMuscleTargetWorkoutRow(
                            completedAtUtc = cursor.getString(0),
                            exerciseId = cursor.getLong(1),
                            completedSetCount = cursor.getInt(2),
                        ),
                    )
                }
            }
        }
    }

    fun loadStrengthScoreSummary(): StrengthScoreSummary? {
        return buildStrengthScoreSummary(loadStrengthScoreRows())
    }

    fun loadCompletedSetCountsForWorkouts(workoutIds: Collection<Long>): Map<Long, Int> {
        val distinctIds = workoutIds.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        val placeholders = distinctIds.joinToString(",") { "?" }
        val counts = database.open().rawQuery(
            """
            SELECT
                pw.performed_workout_id,
                COALESCE(SUM(CASE WHEN ps.is_completed = 1 THEN 1 ELSE 0 END), 0) AS completed_set_count
            FROM performed_workouts pw
            LEFT JOIN performed_exercises pe ON pe.performed_workout_id = pw.performed_workout_id
            LEFT JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pw.performed_workout_id IN ($placeholders)
            GROUP BY pw.performed_workout_id
            """.trimIndent(),
            distinctIds.map(Long::toString).toTypedArray(),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getLong(0), cursor.getInt(1))
                }
            }
        }

        return distinctIds.associateWith { workoutId -> counts[workoutId] ?: 0 }
    }

    fun loadCompletedSetCountsByWorkoutAndExercise(workoutIds: Collection<Long>): Map<Long, Map<Long, Int>> {
        val distinctIds = workoutIds.distinct()
        if (distinctIds.isEmpty()) return emptyMap()

        val placeholders = distinctIds.joinToString(",") { "?" }
        val groupedCounts = database.open().rawQuery(
            """
            SELECT
                pw.performed_workout_id,
                pe.exercise_id,
                COALESCE(SUM(CASE WHEN ps.is_completed = 1 THEN 1 ELSE 0 END), 0) AS completed_set_count
            FROM performed_workouts pw
            INNER JOIN performed_exercises pe ON pe.performed_workout_id = pw.performed_workout_id
            LEFT JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pw.performed_workout_id IN ($placeholders)
            GROUP BY pw.performed_workout_id, pe.exercise_id
            """.trimIndent(),
            distinctIds.map(Long::toString).toTypedArray(),
        ).use { cursor ->
            buildMap<Long, MutableMap<Long, Int>> {
                while (cursor.moveToNext()) {
                    val workoutId = cursor.getLong(0)
                    val exerciseId = cursor.getLong(1)
                    val completedSetCount = cursor.getInt(2)
                    val byExercise = getOrPut(workoutId) { linkedMapOf() }
                    byExercise[exerciseId] = completedSetCount
                }
            }
        }

        return distinctIds.associateWith { workoutId -> groupedCounts[workoutId].orEmpty() }
    }

    fun loadAbandonedWorkoutSummary(): AbandonedWorkoutSummary? {
        val session = loadAbandonedWorkout() ?: return null
        return AbandonedWorkoutSummary(
            title = session.title,
            startedAtUtc = session.startedAtUtc,
            exerciseCount = session.exercises.size,
            completedSetCount = session.exercises.sumOf { exercise -> exercise.sets.count { it.completed } },
        )
    }

    fun loadTopPerformedExercise(): String? {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT pe.exercise_name, COUNT(*) AS appearances
            FROM performed_exercises pe
            GROUP BY pe.exercise_id, pe.exercise_name
            ORDER BY appearances DESC, pe.exercise_name ASC
            LIMIT 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0)
        }
    }

    fun loadTopPerformedEquipment(): String? {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT COALESCE(e.primary_equipment, 'Bodyweight') AS equipment_name, COUNT(*) AS appearances
            FROM performed_exercises pe
            INNER JOIN exercises e ON e.exercise_id = pe.exercise_id
            GROUP BY equipment_name
            ORDER BY appearances DESC, equipment_name ASC
            LIMIT 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0)
        }
    }

    fun loadHistoryDetail(workoutId: Long): HistoryDetail? {
        val db = database.open()
        val header = db.rawQuery(
            """
            SELECT
                pw.title,
                pw.origin_type,
                pw.completed_at_utc,
                pw.actual_duration_seconds,
                COALESCE(SUM(
                    CASE
                        WHEN ps.is_completed = 1 THEN COALESCE(ps.actual_reps, 0) * COALESCE(ps.weight_value, 0)
                        ELSE 0
                    END
                ), 0),
                COUNT(DISTINCT pe.performed_exercise_id),
                pw.ab_flags_snapshot_json
            FROM performed_workouts pw
            LEFT JOIN performed_exercises pe ON pe.performed_workout_id = pw.performed_workout_id
            LEFT JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pw.performed_workout_id = ?
            GROUP BY pw.performed_workout_id, pw.title, pw.origin_type, pw.completed_at_utc, pw.actual_duration_seconds,
                     pw.ab_flags_snapshot_json
            """.trimIndent(),
            arrayOf(workoutId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            HistoryDetail(
                id = workoutId,
                title = cursor.getString(0),
                origin = cursor.getString(1),
                completedAtUtc = cursor.getString(2),
                durationSeconds = cursor.getInt(3),
                totalVolume = cursor.getDouble(4),
                exerciseCount = cursor.getInt(5),
                exercises = emptyList(),
                abFlags = deserializeCompletedWorkoutAbFlags(cursor.getStringOrNull(6)),
            )
        }

        val exercises = db.rawQuery(
            """
            SELECT
                pe.exercise_id,
                pe.exercise_name,
                COALESCE(MIN(ps.target_reps), ''),
                COALESCE(SUM(CASE WHEN ps.is_completed = 1 THEN 1 ELSE 0 END), 0),
                COUNT(ps.performed_set_id),
                COALESCE(SUM(
                    CASE
                        WHEN ps.is_completed = 1 THEN COALESCE(ps.actual_reps, 0) * COALESCE(ps.weight_value, 0)
                        ELSE 0
                    END
                ), 0),
                COALESCE(MAX(ps.weight_value), 0),
                COALESCE(MAX(ps.actual_reps), 0),
                pe.last_set_reps_in_reserve,
                pe.last_set_rpe
            FROM performed_exercises pe
            LEFT JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pe.performed_workout_id = ?
            GROUP BY pe.performed_exercise_id, pe.exercise_id, pe.exercise_name, pe.last_set_reps_in_reserve, pe.last_set_rpe
            ORDER BY pe.sort_order
            """.trimIndent(),
            arrayOf(workoutId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        HistoryExerciseDetail(
                            exerciseId = cursor.getLong(0),
                            name = cursor.getString(1),
                            targetReps = cursor.getString(2),
                            loggedSets = cursor.getInt(3),
                            totalSets = cursor.getInt(4),
                            totalVolume = cursor.getDouble(5),
                            bestWeight = cursor.getDouble(6),
                            bestReps = cursor.getInt(7),
                            lastSetRepsInReserve = if (cursor.isNull(8)) null else cursor.getInt(8),
                            lastSetRpe = if (cursor.isNull(9)) null else cursor.getDouble(9),
                        ),
                    )
                }
            }
        }

        return header.copy(exercises = exercises)
    }

    fun loadHistoryWorkoutShareDetail(workoutId: Long): HistoryWorkoutShareDetail? {
        val db = database.open()
        val header = db.rawQuery(
            """
            SELECT
                pw.title,
                pw.origin_type,
                pw.location_mode_id,
                pw.started_at_utc,
                pw.completed_at_utc,
                pw.actual_duration_seconds,
                COALESCE(SUM(
                    CASE
                        WHEN ps.is_completed = 1 THEN COALESCE(ps.actual_reps, 0) * COALESCE(ps.weight_value, 0)
                        ELSE 0
                    END
                ), 0),
                COUNT(DISTINCT pe.performed_exercise_id),
                pw.ab_flags_snapshot_json
            FROM performed_workouts pw
            LEFT JOIN performed_exercises pe ON pe.performed_workout_id = pw.performed_workout_id
            LEFT JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pw.performed_workout_id = ?
            GROUP BY
                pw.performed_workout_id,
                pw.title,
                pw.origin_type,
                pw.location_mode_id,
                pw.started_at_utc,
                pw.completed_at_utc,
                pw.actual_duration_seconds,
                pw.ab_flags_snapshot_json
            """.trimIndent(),
            arrayOf(workoutId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            HistoryWorkoutShareDetail(
                id = workoutId,
                title = cursor.getString(0),
                origin = cursor.getString(1),
                locationModeId = cursor.getLong(2),
                startedAtUtc = cursor.getString(3),
                completedAtUtc = cursor.getString(4),
                durationSeconds = cursor.getInt(5),
                totalVolume = cursor.getDouble(6),
                exerciseCount = cursor.getInt(7),
                exercises = emptyList(),
                abFlags = deserializeCompletedWorkoutAbFlags(cursor.getStringOrNull(8)),
            )
        }

        val exercises = db.rawQuery(
            """
            SELECT
                pe.performed_exercise_id,
                pe.exercise_id,
                pe.exercise_name,
                COALESCE(MIN(ps.target_reps), ''),
                COALESCE(SUM(CASE WHEN ps.is_completed = 1 THEN 1 ELSE 0 END), 0),
                COUNT(ps.performed_set_id),
                COALESCE(SUM(
                    CASE
                        WHEN ps.is_completed = 1 THEN COALESCE(ps.actual_reps, 0) * COALESCE(ps.weight_value, 0)
                        ELSE 0
                    END
                ), 0),
                COALESCE(MAX(ps.weight_value), 0),
                COALESCE(MAX(ps.actual_reps), 0),
                pe.last_set_reps_in_reserve,
                pe.last_set_rpe
            FROM performed_exercises pe
            LEFT JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pe.performed_workout_id = ?
            GROUP BY pe.performed_exercise_id, pe.exercise_id, pe.exercise_name, pe.last_set_reps_in_reserve, pe.last_set_rpe
            ORDER BY pe.sort_order
            """.trimIndent(),
            arrayOf(workoutId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val performedExerciseId = cursor.getLong(0)
                    add(
                        HistoryWorkoutShareExercise(
                            exerciseId = cursor.getLong(1),
                            name = cursor.getString(2),
                            targetReps = cursor.getString(3),
                            loggedSets = cursor.getInt(4),
                            totalSets = cursor.getInt(5),
                            totalVolume = cursor.getDouble(6),
                            bestWeight = cursor.getDouble(7),
                            bestReps = cursor.getInt(8),
                            lastSetRepsInReserve = if (cursor.isNull(9)) null else cursor.getInt(9),
                            lastSetRpe = if (cursor.isNull(10)) null else cursor.getDouble(10),
                            sets = loadHistoryWorkoutShareSets(db, performedExerciseId),
                        ),
                    )
                }
            }
        }

        return header.copy(exercises = exercises)
    }

    fun loadHistoryWorkoutForReuse(workoutId: Long): WorkoutPlan? {
        val db = database.open()
        val header = db.rawQuery(
            """
            SELECT title, location_mode_id
            FROM performed_workouts
            WHERE performed_workout_id = ?
            """.trimIndent(),
            arrayOf(workoutId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getString(0) to cursor.getLong(1)
        }

        val exercises = mutableListOf<WorkoutExercise>()
        var currentPerformedExerciseId: Long? = null
        var currentExerciseId = 0L
        var currentName = ""
        var currentBodyRegion = "Full Body"
        var currentTargetMuscleGroup = "Unknown"
        var currentEquipment = "Bodyweight"
        val currentSets = mutableListOf<WorkoutExerciseSetDraft>()

        fun flushCurrentExercise() {
            val performedExerciseId = currentPerformedExerciseId ?: return
            val startingSets = currentSets.toList()
            val repRange = historyReuseRepRange(startingSets)
            exercises += WorkoutExercise(
                exerciseId = currentExerciseId,
                name = currentName.ifBlank { "Exercise $performedExerciseId" },
                bodyRegion = currentBodyRegion,
                targetMuscleGroup = currentTargetMuscleGroup,
                equipment = currentEquipment,
                sets = startingSets.size.takeIf { it > 0 } ?: 1,
                repRange = repRange,
                restSeconds = historyReuseRestSeconds(repRange),
                rationale = "Reused from workout history.",
                suggestedWeight = historyReuseSuggestedWeight(startingSets),
                startingSets = startingSets,
            )
        }

        db.rawQuery(
            """
            SELECT
                pe.performed_exercise_id,
                pe.exercise_id,
                pe.exercise_name,
                COALESCE(e.body_region, 'Full Body'),
                COALESCE(e.target_muscle_group, 'Unknown'),
                COALESCE(e.primary_equipment, 'Bodyweight'),
                ps.set_number,
                COALESCE(ps.target_reps, ''),
                ps.recommended_reps,
                ps.recommended_weight_value,
                ps.actual_reps,
                ps.weight_value,
                ps.recommendation_source,
                ps.recommendation_confidence
            FROM performed_exercises pe
            LEFT JOIN exercises e ON e.exercise_id = pe.exercise_id
            LEFT JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pe.performed_workout_id = ?
            ORDER BY pe.sort_order, ps.set_number
            """.trimIndent(),
            arrayOf(workoutId.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val performedExerciseId = cursor.getLong(0)
                if (currentPerformedExerciseId != performedExerciseId) {
                    flushCurrentExercise()
                    currentPerformedExerciseId = performedExerciseId
                    currentExerciseId = cursor.getLong(1)
                    currentName = cursor.getString(2)
                    currentBodyRegion = cursor.getString(3)
                    currentTargetMuscleGroup = cursor.getString(4)
                    currentEquipment = cursor.getString(5)
                    currentSets.clear()
                }
                if (!cursor.isNull(6)) {
                    currentSets += WorkoutExerciseSetDraft(
                        setNumber = cursor.getInt(6),
                        targetReps = cursor.getString(7),
                        recommendedReps = if (cursor.isNull(8)) null else cursor.getInt(8),
                        recommendedWeight = if (cursor.isNull(9)) null else cursor.getDouble(9),
                        reps = if (cursor.isNull(10)) null else cursor.getInt(10),
                        weight = if (cursor.isNull(11)) null else cursor.getDouble(11),
                        recommendationSource = recommendationSourceFromStorage(cursor.getStringOrNull(12)),
                        recommendationConfidence = if (cursor.isNull(13)) null else cursor.getDouble(13),
                    )
                }
            }
        }
        flushCurrentExercise()

        return WorkoutPlan(
            title = header.first,
            subtitle = "History reuse",
            locationModeId = header.second,
            estimatedMinutes = historyReuseEstimatedMinutes(exercises),
            origin = "history",
            exercises = exercises,
        )
    }

    private fun loadHistoryWorkoutShareSets(
        db: android.database.sqlite.SQLiteDatabase,
        performedExerciseId: Long,
    ): List<HistoryWorkoutShareSet> {
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
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        HistoryWorkoutShareSet(
                            setNumber = cursor.getInt(0),
                            targetReps = cursor.getString(1),
                            recommendedReps = if (cursor.isNull(2)) null else cursor.getInt(2),
                            recommendedWeight = if (cursor.isNull(3)) null else cursor.getDouble(3),
                            actualReps = if (cursor.isNull(4)) null else cursor.getInt(4),
                            weight = if (cursor.isNull(5)) null else cursor.getDouble(5),
                            isCompleted = cursor.getInt(6) == 1,
                            recommendationSource = RecommendationSource.entries.firstOrNull {
                                it.name == cursor.getStringOrNull(7)
                            } ?: RecommendationSource.NONE,
                            recommendationConfidence = if (cursor.isNull(8)) null else cursor.getDouble(8),
                        ),
                    )
                }
            }
        }
    }

    fun deleteHistoryWorkout(workoutId: Long) {
        database.open().execSQL(
            "DELETE FROM performed_workouts WHERE performed_workout_id = ?",
            arrayOf(workoutId),
        )
    }

    private fun loadExerciseNamesForWorkout(workoutId: Long): List<String> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT exercise_name
            FROM performed_exercises
            WHERE performed_workout_id = ?
            ORDER BY sort_order
            """.trimIndent(),
            arrayOf(workoutId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun loadStrengthScoreRows(): List<StrengthScoreSetRow> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT
                pw.performed_workout_id,
                pw.title,
                pw.completed_at_utc,
                pe.exercise_id,
                ps.actual_reps,
                ps.weight_value,
                ps.is_completed
            FROM performed_workouts pw
            INNER JOIN performed_exercises pe ON pe.performed_workout_id = pw.performed_workout_id
            INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            ORDER BY pw.completed_at_utc ASC, pe.sort_order ASC, ps.set_number ASC
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        StrengthScoreSetRow(
                            workoutId = cursor.getLong(0),
                            workoutTitle = cursor.getString(1),
                            completedAtUtc = cursor.getString(2),
                            exerciseId = cursor.getLong(3),
                            reps = if (cursor.isNull(4)) null else cursor.getInt(4),
                            weight = if (cursor.isNull(5)) null else cursor.getDouble(5),
                            isCompleted = cursor.getInt(6) == 1,
                        ),
                    )
                }
            }
        }
    }

    fun loadExerciseHistory(exerciseId: Long, fallbackName: String, prOnly: Boolean = false): ExerciseHistoryDetail {
        val db = database.open()
        val rows = db.rawQuery(
            """
            SELECT
                pw.completed_at_utc,
                pw.title,
                COALESCE(ps.target_reps, ''),
                pe.last_set_reps_in_reserve,
                pe.last_set_rpe,
                ps.set_number,
                ps.actual_reps,
                ps.weight_value,
                ps.is_completed
            FROM performed_workouts pw
            INNER JOIN performed_exercises pe ON pe.performed_workout_id = pw.performed_workout_id
            INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pe.exercise_id = ?
            ORDER BY pw.completed_at_utc ASC, pe.sort_order ASC, ps.set_number ASC
            """.trimIndent(),
            arrayOf(exerciseId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ExerciseHistoryRow(
                            completedAtUtc = cursor.getString(0),
                            workoutTitle = cursor.getString(1),
                            targetReps = cursor.getString(2),
                            lastSetRepsInReserve = if (cursor.isNull(3)) null else cursor.getInt(3),
                            lastSetRpe = if (cursor.isNull(4)) null else cursor.getDouble(4),
                            setNumber = cursor.getInt(5),
                            reps = if (cursor.isNull(6)) null else cursor.getInt(6),
                            weight = if (cursor.isNull(7)) null else cursor.getDouble(7),
                            isCompleted = cursor.getInt(8) == 1,
                        ),
                    )
                }
            }
        }
        return buildExerciseHistoryDetail(
            exerciseId = exerciseId,
            fallbackName = fallbackName,
            rows = rows,
            prOnly = prOnly,
        )
    }

    private fun loadTemplateExercises(templateId: Long): List<WorkoutExercise> {
        val db = database.open()
        val exerciseRows = db.rawQuery(
            """
            SELECT exercise_id, set_count, rep_range, rest_seconds, rationale
            FROM workout_template_exercises
            WHERE template_id = ?
            ORDER BY sort_order
            """.trimIndent(),
            arrayOf(templateId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Triple(
                            cursor.getLong(0),
                            cursor.getInt(1) to cursor.getString(2),
                            cursor.getInt(3) to cursor.getString(4),
                        ),
                    )
                }
            }
        }
        val summaries = catalogRepository.searchExercisesByIds(exerciseRows.map { it.first })
        val byId = summaries.associateBy { it.id }
        return exerciseRows.mapNotNull { (exerciseId, setAndRep, restAndRationale) ->
            val summary = byId[exerciseId] ?: return@mapNotNull null
            WorkoutExercise(
                exerciseId = summary.id,
                name = summary.name,
                bodyRegion = summary.bodyRegion,
                targetMuscleGroup = summary.targetMuscleGroup,
                equipment = summary.equipment,
                sets = setAndRep.first,
                repRange = setAndRep.second,
                restSeconds = restAndRationale.first,
                rationale = restAndRationale.second,
            )
        }
    }

    private fun insertTemplateExercises(
        db: android.database.sqlite.SQLiteDatabase,
        templateId: Long,
        exercises: List<WorkoutExercise>,
    ) {
        exercises.forEachIndexed { index, exercise ->
            db.execSQL(
                """
                INSERT INTO workout_template_exercises (
                    template_id, sort_order, exercise_id, set_count, rep_range, rest_seconds, rationale
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    templateId,
                    index,
                    exercise.exerciseId,
                    exercise.sets,
                    exercise.repRange,
                    exercise.restSeconds,
                    exercise.rationale,
                ),
            )
        }
    }

    private fun recordWorkoutFeedbackSignal(
        workoutOrigin: String,
        workoutTitle: String,
        workoutFocusKey: String?,
        sessionStartedAtUtc: String?,
        exerciseId: Long,
        exerciseName: String,
        signalType: WorkoutFeedbackSignalType,
    ) {
        val db = database.open()
        val now = Instant.now().toString()
        db.beginTransaction()
        try {
            val currentPreferenceDelta = db.rawQuery(
                """
                SELECT preference_score_delta
                FROM exercise_preferences
                WHERE exercise_id = ?
                """.trimIndent(),
                arrayOf(exerciseId.toString()),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
            }
            val resultingPreferenceDelta = (currentPreferenceDelta + signalType.recommendationDelta)
                .coerceIn(-MAX_LEARNED_RECOMMENDATION_DELTA, MAX_LEARNED_RECOMMENDATION_DELTA)

            if (signalType.recommendationDelta != 0.0) {
                db.execSQL(
                    """
                    INSERT INTO exercise_preferences (
                        exercise_id,
                        is_favorite,
                        is_hidden,
                        is_banned,
                        preference_score_delta,
                        notes,
                        updated_at_utc
                    ) VALUES (?, 0, 0, 0, ?, NULL, ?)
                    ON CONFLICT(exercise_id) DO UPDATE SET
                        preference_score_delta = excluded.preference_score_delta,
                        updated_at_utc = excluded.updated_at_utc
                    """.trimIndent(),
                    arrayOf(exerciseId, resultingPreferenceDelta, now),
                )
            }

            db.execSQL(
                """
                INSERT INTO workout_feedback_signals (
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
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    signalType.storageValue,
                    workoutOrigin,
                    workoutTitle,
                    workoutFocusKey,
                    sessionStartedAtUtc,
                    exerciseId,
                    exerciseName,
                    signalType.recommendationDelta,
                    resultingPreferenceDelta,
                    now,
                ),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun recommendationSourceFromStorage(value: String?): RecommendationSource {
        return runCatching {
            RecommendationSource.valueOf(value.orEmpty())
        }.getOrDefault(RecommendationSource.NONE)
    }
}
