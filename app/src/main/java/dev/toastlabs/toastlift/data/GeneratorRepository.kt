package dev.toastlabs.toastlift.data

import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max

class GeneratorRepository(
    private val database: ToastLiftDatabase,
    private val userRepository: UserRepository,
) {
    private val generationFacade = WorkoutGenerationFacade()

    fun generateWorkout(
        profile: UserProfile,
        splitProgram: TrainingSplitProgram,
        locationModes: List<LocationMode>,
        previousExerciseIds: Set<Long> = emptySet(),
        variationSeed: Long = System.currentTimeMillis(),
        requestedFocus: String? = null,
        programContext: ProgramSessionContext? = null,
    ): WorkoutPlan {
        val locationModeId = profile.activeLocationModeId
        val locationName = locationModes.firstOrNull { it.id == locationModeId }?.displayName ?: "Ready"
        val availableEquipment = resolveAvailableEquipmentForGeneration(
            userRepository.loadEquipmentForLocation(locationModeId),
        )
        val targetDurationMinutes = programContext?.timeBudgetMinutes ?: profile.durationMinutes
        val desiredExerciseCount = fallbackExerciseCount(targetDurationMinutes)
        val gymEquipmentBiasPlan = buildGymEquipmentBiasPlan(
            profile = profile,
            availableEquipment = availableEquipment,
            desiredCount = desiredExerciseCount,
        )
        val history = loadHistoricalSets()
        val focus = when {
            requestedFocus != null -> requestedFocus
            splitProgram.name == "Adaptive" -> generationFacade.resolveAdaptiveFocus(
                profile = profile,
                history = history,
                nowUtc = Instant.now(),
            )
            else -> determineFocus(splitProgram, profile.goal, null)
        }
        val normalizedFocus = normalizeFocus(focus)
        val persistedFocus = persistedGeneratedWorkoutFocusKey(
            requestedFocus = requestedFocus,
            resolvedFocus = focus,
            normalizedFocus = normalizedFocus,
        )
        val filteredCandidates = queryCandidateExercises(
            focus = normalizedFocus,
            availableEquipment = availableEquipment,
            gymEquipmentBiasPlan = gymEquipmentBiasPlan,
        )
        val generated = generationFacade.generate(
            WorkoutGenerationRequest(
                profile = profile,
                splitProgramName = splitProgram.name,
                focus = normalizedFocus,
                rawFocusKey = persistedFocus,
                intensityIntent = intensityPrescriptionIntentForFocusKey(persistedFocus),
                locationName = locationName,
                availableEquipment = availableEquipment,
                candidates = filteredCandidates,
                history = history,
                restrictions = loadRestrictions(),
                preferences = loadPreferenceStates(),
                previousExerciseIds = previousExerciseIds,
                variationSeed = variationSeed,
                nowUtc = Instant.now(),
                programContext = programContext,
            ),
        )

        if (generated.exercises.isEmpty()) {
            val fallbackPool = if (filteredCandidates.isNotEmpty()) {
                filteredCandidates
            } else {
                queryCandidateExercises(
                    focus = "full_body",
                    availableEquipment = availableEquipment,
                    gymEquipmentBiasPlan = gymEquipmentBiasPlan,
                )
            }
            val fallbackExercises = fallbackPool
                .take(fallbackExerciseCount(profile.durationMinutes))
                .mapIndexed { index, exercise ->
                    val workUnitDefaults = defaultWorkUnitValues(exercise.workUnits).takeIf { exercise.workUnits.isNotEmpty() }
                    val startingSets = workUnitDefaults?.let { values ->
                        listOf(
                            WorkoutExerciseSetDraft(
                                setNumber = 1,
                                targetReps = "",
                                workUnitValues = values,
                                recommendationSource = RecommendationSource.GENERATED_PLAN,
                                recommendationConfidence = 0.5,
                            ),
                        )
                    }.orEmpty()
                    WorkoutExercise(
                        exerciseId = exercise.id,
                        name = exercise.name,
                        bodyRegion = exercise.bodyRegion,
                        targetMuscleGroup = exercise.targetMuscleGroup,
                        equipment = exercise.equipment,
                        sets = startingSets.size.takeIf { it > 0 } ?: if (index < 2) 4 else 3,
                        repRange = workUnitTargetLabel(exercise.workUnits, workUnitDefaults.orEmpty())
                            ?: defaultRepRange(profile.goal),
                        restSeconds = if (startingSets.isNotEmpty()) 0 else defaultRestSeconds(profile.goal),
                        rationale = "Fallback selection because the filtered candidate pool was empty.",
                        startingSets = startingSets,
                    )
                }
            return WorkoutPlan(
                title = "$locationName ${generatedWorkoutFocusDisplayName(persistedFocus)}",
                subtitle = "${splitProgram.name} • ${profile.goal} • ${profile.durationMinutes} min",
                locationModeId = locationModeId,
                estimatedMinutes = max(profile.durationMinutes, fallbackExercises.sumOf(::fallbackEstimatedMinutes)),
                origin = "generated",
                focusKey = persistedFocus,
                exercises = fallbackExercises,
            )
        }

        val workoutExercises = generated.exercises.map { exercise ->
            val startingSets = exercise.workUnitValuesBySet.mapIndexed { index, values ->
                WorkoutExerciseSetDraft(
                    setNumber = index + 1,
                    targetReps = "",
                    workUnitValues = values,
                    recommendationSource = RecommendationSource.GENERATED_PLAN,
                    recommendationConfidence = 0.82,
                )
            }
            WorkoutExercise(
                exerciseId = exercise.exerciseId,
                name = exercise.name,
                bodyRegion = exercise.bodyRegion,
                targetMuscleGroup = exercise.targetMuscleGroup,
                equipment = exercise.equipment,
                sets = startingSets.size.takeIf { it > 0 } ?: exercise.setCount,
                repRange = exercise.repRange,
                restSeconds = exercise.restSeconds,
                rationale = exercise.rationale,
                suggestedWeight = exercise.suggestedWeight,
                overloadStrategy = exercise.overloadStrategy,
                decisionTrace = exercise.decisionTrace,
                startingSets = startingSets,
            )
        }

        return WorkoutPlan(
            title = "$locationName ${generatedWorkoutFocusDisplayName(persistedFocus)}",
            subtitle = "${splitProgram.name} • ${profile.goal} • ${generated.estimatedMinutes} min • ${generated.sessionFormat}",
            locationModeId = locationModeId,
            estimatedMinutes = generated.estimatedMinutes,
            origin = "generated",
            focusKey = persistedFocus,
            exercises = workoutExercises,
            sessionFormat = generated.sessionFormat,
            muscleInsights = generated.muscleInsights,
            movementInsights = generated.movementInsights,
            decisionSummary = generated.decisionSummary,
        )
    }

    fun splitSequence(splitProgramName: String): List<String> {
        return splitSequenceForName(splitProgramName)
    }

    fun loadHistoricalSetsForRecommendations(): List<HistoricalExerciseSet> = loadHistoricalSets()

    fun nextFocusAfter(splitProgramName: String, currentFocus: String): String {
        val sequence = splitSequence(splitProgramName)
        val index = sequence.indexOf(currentFocus)
        return if (index == -1) sequence.first() else sequence[(index + 1) % sequence.size]
    }

    private fun determineFocus(splitProgram: TrainingSplitProgram, goal: String, requestedFocus: String?): String {
        if (requestedFocus != null) return requestedFocus
        val storedNextFocus = userRepository.loadNextFocus()
        val sequence = splitSequence(splitProgram.name)
        return when (splitProgram.name) {
            "Upper/Lower",
            "Push Pull Legs",
            "Body Part Split",
            FORMULA_A_SPLIT_PROGRAM_NAME,
            FORMULA_B_SPLIT_PROGRAM_NAME,
            -> storedNextFocus?.takeIf { it in sequence } ?: sequence.first()
            else -> if (goal == "Conditioning") "full_body" else "full_body"
        }
    }

    private fun normalizeFocus(focus: String): String {
        return when (focus) {
            "upper_body_2" -> "upper_body"
            "lower_body_2" -> "lower_body"
            FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY,
            FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY,
            -> "push_day"
            FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY,
            FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY,
            -> "pull_day"
            FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY,
            FORMULA_A_LOWER_STRENGTH_FOCUS_KEY,
            -> "lower_body"
            FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY,
            FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY,
            -> FORMULA_B_GLUTES_HAMSTRINGS_DAY_FOCUS_KEY
            FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY,
            FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY,
            -> FORMULA_B_UPPER_CHEST_DAY_FOCUS_KEY
            FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY,
            FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY,
            -> FORMULA_B_REAR_SIDE_DELTS_DAY_FOCUS_KEY
            else -> focus
        }
    }

    private fun queryCandidateExercises(
        focus: String,
        availableEquipment: Set<String>,
        gymEquipmentBiasPlan: GymEquipmentBiasPlan?,
    ): List<GeneratorCatalogExercise> {
        if (availableEquipment.isEmpty()) return emptyList()
        val db = database.open()
        val args = (availableEquipment + availableEquipment + availableEquipment).toTypedArray()
        val placeholders = availableEquipment.joinToString(",") { "?" }
        val bodyRegionFilter = when (focus) {
            "upper_body" -> "e.body_region IN ('Upper Body', 'Full Body', 'Core')"
            "lower_body" -> "e.body_region IN ('Lower Body', 'Full Body', 'Core')"
            "push_day", "pull_day", "chest_day", "back_day", "shoulders_arms_day",
            FORMULA_B_UPPER_CHEST_DAY_FOCUS_KEY, FORMULA_B_REAR_SIDE_DELTS_DAY_FOCUS_KEY,
            -> "e.body_region IN ('Upper Body', 'Full Body', 'Core')"
            "legs_day", FORMULA_B_GLUTES_HAMSTRINGS_DAY_FOCUS_KEY -> "e.body_region IN ('Lower Body', 'Full Body', 'Core')"
            else -> "e.body_region IN ('Full Body', 'Upper Body', 'Lower Body', 'Core')"
        }
        val focusFilter = when (focus) {
            FORMULA_B_GLUTES_HAMSTRINGS_DAY_FOCUS_KEY -> """
                AND (
                    lower(COALESCE(e.target_muscle_group, '')) IN ('glutes', 'hamstrings', 'abductors', 'adductors')
                    OR lower(COALESCE(e.prime_mover_muscle, '')) IN ('gluteus maximus', 'hamstrings', 'biceps femoris', 'semitendinosus', 'semimembranosus', 'glutes')
                    OR lower(COALESCE(e.secondary_muscle, '')) IN ('gluteus maximus', 'hamstrings', 'biceps femoris', 'semitendinosus', 'semimembranosus', 'glutes')
                    OR lower(COALESCE(e.tertiary_muscle, '')) IN ('gluteus maximus', 'hamstrings', 'biceps femoris', 'semitendinosus', 'semimembranosus', 'glutes')
                )
            """.trimIndent()
            FORMULA_B_UPPER_CHEST_DAY_FOCUS_KEY -> """
                AND (
                    lower(COALESCE(e.target_muscle_group, '')) IN ('chest', 'triceps', 'shoulders')
                    OR lower(COALESCE(e.prime_mover_muscle, '')) IN ('pectoralis major', 'anterior deltoids', 'triceps brachii')
                    OR lower(COALESCE(e.secondary_muscle, '')) IN ('pectoralis major', 'anterior deltoids', 'triceps brachii')
                    OR lower(COALESCE(e.tertiary_muscle, '')) IN ('pectoralis major', 'anterior deltoids', 'triceps brachii')
                )
            """.trimIndent()
            FORMULA_B_REAR_SIDE_DELTS_DAY_FOCUS_KEY -> """
                AND (
                    lower(COALESCE(e.target_muscle_group, '')) = 'shoulders'
                    OR lower(COALESCE(e.prime_mover_muscle, '')) IN ('posterior deltoids', 'lateral deltoids')
                    OR lower(COALESCE(e.secondary_muscle, '')) IN ('posterior deltoids', 'lateral deltoids')
                    OR lower(COALESCE(e.tertiary_muscle, '')) IN ('posterior deltoids', 'lateral deltoids')
                )
            """.trimIndent()
            else -> ""
        }

        return db.rawQuery(
            """
            SELECT
                e.exercise_id,
                e.name,
                e.difficulty_level,
                e.body_region,
                e.target_muscle_group,
                COALESCE(e.primary_equipment, 'Bodyweight'),
                e.secondary_equipment,
                e.mechanics,
                COALESCE(p.is_favorite, 0),
                COALESCE(p.preference_score_delta, 0),
                e.prime_mover_muscle,
                e.secondary_muscle,
                e.tertiary_muscle,
                e.posture,
                e.laterality,
                e.primary_exercise_classification,
                (
                    SELECT group_concat(mp.movement_pattern, '|')
                    FROM exercise_movement_patterns mp
                    WHERE mp.exercise_id = e.exercise_id
                ),
                (
                    SELECT group_concat(po.plane_of_motion, '|')
                    FROM exercise_planes_of_motion po
                    WHERE po.exercise_id = e.exercise_id
                )
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            WHERE $bodyRegionFilter
              $focusFilter
              AND COALESCE(NULLIF(trim(COALESCE(e.primary_equipment, 'Bodyweight')), ''), 'Bodyweight') IN ($placeholders)
              AND (
                    e.secondary_equipment IS NULL
                    OR trim(e.secondary_equipment) = ''
                    OR e.secondary_equipment IN ($placeholders)
              )
              AND NOT EXISTS (
                    SELECT 1
                    FROM exercise_equipment eq
                    WHERE eq.exercise_id = e.exercise_id
                      AND eq.equipment_name IS NOT NULL
                      AND trim(eq.equipment_name) != ''
                      AND eq.equipment_name NOT IN ($placeholders)
              )
            ORDER BY COALESCE(p.is_favorite, 0) DESC, COALESCE(p.preference_score_delta, 0) DESC, e.name ASC
            """.trimIndent(),
            args,
        ).use { cursor ->
            val candidates = buildList {
                while (cursor.moveToNext()) {
                    add(
                        GeneratorCatalogExercise(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            difficulty = cursor.getString(2),
                            bodyRegion = cursor.getString(3),
                            targetMuscleGroup = cursor.getString(4),
                            equipment = cursor.getString(5),
                            secondaryEquipment = cursor.getStringOrNull(6),
                            mechanics = cursor.getStringOrNull(7),
                            favorite = cursor.getInt(8) == 1,
                            preferenceScoreDelta = cursor.getDouble(9),
                            primeMover = cursor.getStringOrNull(10),
                            secondaryMuscle = cursor.getStringOrNull(11),
                            tertiaryMuscle = cursor.getStringOrNull(12),
                            posture = cursor.getString(13),
                            laterality = cursor.getString(14),
                            classification = cursor.getString(15),
                            movementPatterns = cursor.getStringOrNull(16).splitPipe(),
                            planesOfMotion = cursor.getStringOrNull(17).splitPipe(),
                        ),
                    )
                }
            }
            limitCandidatesForSelectionWindow(
                candidates = candidates,
                biasPlan = gymEquipmentBiasPlan,
                maxCount = 240,
            ).withCatalogWorkUnits()
        }
    }

    private fun List<GeneratorCatalogExercise>.withCatalogWorkUnits(): List<GeneratorCatalogExercise> {
        if (isEmpty()) return this
        val workUnitsByExerciseId = loadWorkUnitsByExerciseIds(map(GeneratorCatalogExercise::id))
        if (workUnitsByExerciseId.isEmpty()) return this
        return map { exercise ->
            exercise.copy(workUnits = workUnitsByExerciseId[exercise.id].orEmpty())
        }
    }

    private fun loadWorkUnitsByExerciseIds(exerciseIds: Collection<Long>): Map<Long, List<WorkUnitDefinition>> {
        val distinctIds = exerciseIds.distinct()
        if (distinctIds.isEmpty()) return emptyMap()
        val db = database.open()
        val placeholders = distinctIds.joinToString(",") { "?" }
        return db.rawQuery(
            """
            SELECT
                exercise_id,
                unit_key,
                display_label,
                value_type,
                unit_label,
                default_value,
                min_value,
                max_value,
                step_value,
                is_primary,
                is_required,
                tracks_effort
            FROM exercise_work_units
            WHERE exercise_id IN ($placeholders)
            ORDER BY exercise_id, sequence_no
            """.trimIndent(),
            distinctIds.map(Long::toString).toTypedArray(),
        ).use { cursor ->
            val grouped = linkedMapOf<Long, MutableList<WorkUnitDefinition>>()
            while (cursor.moveToNext()) {
                grouped.getOrPut(cursor.getLong(0)) { mutableListOf() } += WorkUnitDefinition(
                    key = cursor.getString(1),
                    label = cursor.getString(2),
                    valueType = cursor.getString(3),
                    unitLabel = cursor.getStringOrNull(4),
                    defaultValue = cursor.getStringOrNull(5),
                    minValue = if (cursor.isNull(6)) null else cursor.getDouble(6),
                    maxValue = if (cursor.isNull(7)) null else cursor.getDouble(7),
                    stepValue = if (cursor.isNull(8)) null else cursor.getDouble(8),
                    isPrimary = cursor.getInt(9) == 1,
                    isRequired = cursor.getInt(10) == 1,
                    tracksEffort = cursor.getInt(11) == 1,
                )
            }
            grouped
        }
    }

    private fun workUnitTargetLabel(
        workUnits: List<WorkUnitDefinition>,
        values: Map<String, String>,
    ): String? {
        val primary = workUnits.firstOrNull { it.isPrimary } ?: workUnits.firstOrNull() ?: return null
        val value = values[primary.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val unit = primary.unitLabel?.trim()?.takeIf { it.isNotEmpty() }
        return listOfNotNull(value, unit).joinToString(" ")
    }

    private fun fallbackEstimatedMinutes(exercise: WorkoutExercise): Int {
        val workUnitMinutes = exercise.startingSets
            .mapNotNull { draft -> draft.workUnitValues["duration_min"]?.toDoubleOrNull() }
            .sum()
        return workUnitMinutes
            .takeIf { it > 0.0 }
            ?.let { ceil(it).toInt() }
            ?: (exercise.sets * 3)
    }

    private fun loadHistoricalSets(): List<HistoricalExerciseSet> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT
                pe.performed_exercise_id,
                pw.started_at_utc,
                pe.exercise_id,
                pe.exercise_name,
                COALESCE(ps.target_reps, ''),
                ps.actual_reps,
                ps.weight_value,
                ps.is_completed,
                pe.last_set_reps_in_reserve,
                pe.last_set_rpe,
                e.target_muscle_group,
                e.prime_mover_muscle,
                e.secondary_muscle,
                e.tertiary_muscle,
                e.mechanics,
                e.laterality,
                e.primary_exercise_classification,
                COALESCE(e.primary_equipment, ''),
                ps.set_number,
                ps.work_unit_values_json,
                (
                    SELECT group_concat(mp.movement_pattern, '|')
                    FROM exercise_movement_patterns mp
                    WHERE mp.exercise_id = pe.exercise_id
                ),
                (
                    SELECT group_concat(po.plane_of_motion, '|')
                    FROM exercise_planes_of_motion po
                    WHERE po.exercise_id = pe.exercise_id
                ),
                pw.completed_at_utc
            FROM performed_workouts pw
            INNER JOIN performed_exercises pe ON pe.performed_workout_id = pw.performed_workout_id
            INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            LEFT JOIN exercises e ON e.exercise_id = pe.exercise_id
            WHERE ${loggedRepSignalClause()}
            ORDER BY pw.started_at_utc DESC, pe.sort_order ASC, ps.set_number ASC
            """.trimIndent(),
            null,
        ).use { cursor ->
            data class HistoryRow(
                val performedExerciseId: Long,
                val workoutOccurredAtUtc: Instant,
                val exerciseId: Long,
                val exerciseName: String,
                val targetReps: String,
                val actualReps: Int?,
                val weight: Double?,
                val completed: Boolean,
                val lastSetRir: Int?,
                val lastSetRpe: Double?,
                val targetMuscleGroup: String?,
                val primeMover: String?,
                val secondaryMuscle: String?,
                val tertiaryMuscle: String?,
                val mechanics: String?,
                val laterality: String?,
                val classification: String?,
                val equipment: String?,
                val setNumber: Int,
                val workUnitValues: Map<String, String>,
                val movementPatterns: List<String>,
                val planesOfMotion: List<String>,
            )

            val rows = buildList {
                while (cursor.moveToNext()) {
                    val workoutOccurredAt = parseWorkoutOccurrenceAtUtc(
                        startedAtUtc = cursor.getString(1),
                        completedAtUtc = cursor.getString(22),
                    ) ?: continue
                    add(
                        HistoryRow(
                            performedExerciseId = cursor.getLong(0),
                            workoutOccurredAtUtc = workoutOccurredAt,
                            exerciseId = cursor.getLong(2),
                            exerciseName = cursor.getString(3),
                            targetReps = cursor.getString(4),
                            actualReps = if (cursor.isNull(5)) null else cursor.getInt(5),
                            weight = if (cursor.isNull(6)) null else cursor.getDouble(6),
                            completed = cursor.getInt(7) == 1,
                            lastSetRir = if (cursor.isNull(8)) null else cursor.getInt(8),
                            lastSetRpe = if (cursor.isNull(9)) null else cursor.getDouble(9),
                            targetMuscleGroup = cursor.getStringOrNull(10),
                            primeMover = cursor.getStringOrNull(11),
                            secondaryMuscle = cursor.getStringOrNull(12),
                            tertiaryMuscle = cursor.getStringOrNull(13),
                            mechanics = cursor.getStringOrNull(14),
                            laterality = cursor.getStringOrNull(15),
                            classification = cursor.getStringOrNull(16),
                            equipment = cursor.getStringOrNull(17),
                            setNumber = cursor.getInt(18),
                            workUnitValues = decodeWorkUnitValues(cursor.getStringOrNull(19)),
                            movementPatterns = cursor.getStringOrNull(20).splitPipe(),
                            planesOfMotion = cursor.getStringOrNull(21).splitPipe(),
                        ),
                    )
                }
            }.sortedByDescending(HistoryRow::workoutOccurredAtUtc)
            val maxLoggedSetByExerciseSession = rows
                .groupBy { row -> row.performedExerciseId }
                .mapValues { (_, groupedRows) -> groupedRows.maxOf { it.setNumber } }

            rows.map { row ->
                HistoricalExerciseSet(
                    completedAtUtc = row.workoutOccurredAtUtc,
                    exerciseId = row.exerciseId,
                    exerciseName = row.exerciseName,
                    targetReps = row.targetReps,
                    actualReps = row.actualReps,
                    weight = row.weight,
                    completed = row.completed,
                    lastSetRir = row.lastSetRir,
                    lastSetRpe = row.lastSetRpe,
                    targetMuscleGroup = row.targetMuscleGroup,
                    primeMover = row.primeMover,
                    secondaryMuscle = row.secondaryMuscle,
                    tertiaryMuscle = row.tertiaryMuscle,
                    mechanics = row.mechanics,
                    laterality = row.laterality,
                    classification = row.classification,
                    movementPatterns = row.movementPatterns,
                    planesOfMotion = row.planesOfMotion,
                    equipment = row.equipment,
                    setNumber = row.setNumber,
                    effortAppliesToSet = row.setNumber == maxLoggedSetByExerciseSession[row.performedExerciseId],
                    workUnitValues = row.workUnitValues,
                )
            }
        }
    }

    private fun loadPreferenceStates(): Map<Long, GeneratorPreferenceState> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT
                exercise_id,
                is_favorite,
                is_hidden,
                is_banned,
                preference_score_delta,
                notes
            FROM exercise_preferences
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val exerciseId = cursor.getLong(0)
                    put(
                        exerciseId,
                        GeneratorPreferenceState(
                            exerciseId = exerciseId,
                            favorite = cursor.getInt(1) == 1,
                            hidden = cursor.getInt(2) == 1,
                            banned = cursor.getInt(3) == 1,
                            scoreDelta = cursor.getDouble(4),
                            notes = cursor.getStringOrNull(5),
                        ),
                    )
                }
            }
        }
    }

    private fun loadRestrictions(): List<GeneratorRestriction> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT restriction_scope, restriction_value, severity, notes
            FROM movement_restrictions
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GeneratorRestriction(
                            scope = cursor.getString(0),
                            value = cursor.getString(1),
                            severity = cursor.getString(2),
                            notes = cursor.getStringOrNull(3),
                        ),
                    )
                }
            }
        }
    }

    private fun fallbackExerciseCount(durationMinutes: Int): Int {
        return targetExerciseCountForDuration(durationMinutes)
    }

    private fun defaultRepRange(goal: String): String {
        return when (goal) {
            "Strength" -> "4-6"
            "Hypertrophy" -> "8-12"
            "Conditioning" -> "12-20"
            "Fat Loss" -> "10-15"
            else -> "6-10"
        }
    }

    private fun defaultRestSeconds(goal: String): Int {
        return when (goal) {
            "Strength" -> 120
            "Conditioning" -> 45
            "Fat Loss" -> 60
            else -> 75
        }
    }
}

internal fun persistedGeneratedWorkoutFocusKey(
    requestedFocus: String?,
    resolvedFocus: String,
    normalizedFocus: String,
): String = requestedFocus ?: resolvedFocus.ifBlank { normalizedFocus }

internal fun splitSequenceForName(splitProgramName: String): List<String> {
    return when (splitProgramName) {
        "Upper/Lower" -> listOf("upper_body", "lower_body", "upper_body_2", "lower_body_2", "full_body")
        "Push Pull Legs" -> listOf("push_day", "pull_day", "legs_day")
        "Body Part Split" -> listOf("chest_day", "back_day", "shoulders_arms_day", "lower_body")
        FORMULA_A_SPLIT_PROGRAM_NAME -> listOf(
            FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY,
            FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY,
            FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY,
            FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY,
            FORMULA_A_LOWER_STRENGTH_FOCUS_KEY,
            FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY,
        )
        FORMULA_B_SPLIT_PROGRAM_NAME -> listOf(
            FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY,
            FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY,
            FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY,
            FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY,
            FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY,
            FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY,
        )
        else -> listOf("full_body")
    }
}

internal fun generatedWorkoutFocusDisplayName(focus: String): String {
    return when (focus) {
        "upper_body" -> "Upper Day"
        "upper_body_2" -> "Upper Day 2"
        "lower_body" -> "Lower Day"
        "lower_body_2" -> "Lower Day 2"
        "push_day" -> "Push Day"
        "pull_day" -> "Pull Day"
        "legs_day" -> "Leg Day"
        "chest_day" -> "Chest Day"
        "back_day" -> "Back Day"
        "shoulders_arms_day" -> "Shoulders + Arms Day"
        FORMULA_B_GLUTES_HAMSTRINGS_DAY_FOCUS_KEY -> "Glutes + Hamstrings Day"
        FORMULA_B_UPPER_CHEST_DAY_FOCUS_KEY -> "Upper Chest Day"
        FORMULA_B_REAR_SIDE_DELTS_DAY_FOCUS_KEY -> "Rear + Side Delts Day"
        FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY -> "UPPER-PUSH-S (chest/delts/tri HEAVY)"
        FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY -> "LOWER-H (legs/abs HIGH REPS)"
        FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY -> "UPPER-PULL-S (back/biceps HEAVY)"
        FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY -> "UPPER-PUSH-H (chest/delts/tri HIGH REPS)"
        FORMULA_A_LOWER_STRENGTH_FOCUS_KEY -> "LOWER-S (legs/abs HEAVY)"
        FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY -> "UPPER-PULL-H (back/biceps HIGH REPS)"
        FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY -> "GLUTES+HAMSTRINGS-S (HEAVY)"
        FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY -> "UPPER CHEST-H (HIGH REPS)"
        FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY -> "REAR DELTS + SIDE DELTS-S (HEAVY)"
        FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY -> "GLUTES+HAMSTRINGS-H (HIGH REPS)"
        FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY -> "UPPER CHEST-S (HEAVY)"
        FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY -> "REAR DELTS + SIDE DELTS-H (HIGH REPS)"
        else -> "Full Body"
    }
}

internal fun resolveAvailableEquipmentForGeneration(configuredEquipment: Set<String>): Set<String> {
    return configuredEquipment
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toCollection(linkedSetOf())
}

private fun String?.splitPipe(): List<String> {
    return this
        ?.split("|")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
}
