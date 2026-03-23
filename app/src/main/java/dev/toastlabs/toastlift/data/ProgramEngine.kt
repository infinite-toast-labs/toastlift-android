package dev.toastlabs.toastlift.data

import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class FocusExercisePool(
    val primarySlots: List<ProgramExerciseSlot>,
    val accessorySlots: List<ProgramExerciseSlot>,
)

internal data class ProgramExerciseSelection(
    val slots: List<ProgramExerciseSlot>,
    val slotsByFocus: Map<String, FocusExercisePool>,
)

internal fun normalizeProgramFocusKey(focusKey: String): String = when (focusKey) {
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
    else -> focusKey
}

internal fun focusArchetypeForSequence(
    sequenceNumber: Int,
    focusSequence: List<String>,
): String {
    if (focusSequence.isEmpty()) return "full_body"
    val index = ((sequenceNumber - 1) % focusSequence.size + focusSequence.size) % focusSequence.size
    return focusSequence[index]
}

internal fun buildProgramExerciseSelection(
    programId: String,
    goal: String,
    focusSequence: List<String>,
    candidatesByFocus: Map<String, List<GeneratorCatalogExercise>>,
    sfrLookup: (Long) -> Double?,
    profile: UserProfile? = null,
    availableEquipment: Set<String> = emptySet(),
): ProgramExerciseSelection {
    val slots = mutableListOf<ProgramExerciseSlot>()
    val slotsByFocus = linkedMapOf<String, FocusExercisePool>()
    val usedExerciseIds = mutableSetOf<Long>()

    val uniqueFocuses = focusSequence.distinct()

    val loadProgressionPercent = when (goal) {
        "Strength" -> 0.03
        "Hypertrophy" -> 0.025
        "Conditioning", "Fat Loss" -> 0.015
        else -> 0.02
    }

    for (focus in uniqueFocuses) {
        val candidates = candidatesByFocus[focus]
            .orEmpty()
            .distinctBy { it.id }
        if (candidates.isEmpty()) continue

        val primaryCount = if (candidates.size >= 6) 3 else 2
        val accessoryCandidateCount = (candidates.size - primaryCount).coerceAtLeast(0)
        val accessoryTarget = when {
            accessoryCandidateCount >= 4 -> 4
            accessoryCandidateCount == 3 -> 3
            accessoryCandidateCount == 2 -> 2
            accessoryCandidateCount == 1 -> 1
            else -> 0
        }
        val orderedCandidates = biasProgramCandidatesTowardGymEquipment(
            candidates = candidates,
            biasPlan = buildGymEquipmentBiasPlan(
                profile = profile,
                availableEquipment = availableEquipment,
                desiredCount = primaryCount + accessoryTarget,
            ),
        )
        val primaries = selectExercisesForFocus(
            candidates = orderedCandidates,
            count = primaryCount,
            globallyUsedExerciseIds = usedExerciseIds,
        )
        val primaryIds = primaries.mapTo(linkedSetOf()) { it.id }

        val accessories = selectExercisesForFocus(
            candidates = orderedCandidates,
            count = accessoryTarget,
            globallyUsedExerciseIds = usedExerciseIds + primaryIds,
            locallyExcludedExerciseIds = primaryIds,
        )

        usedExerciseIds += primaryIds
        usedExerciseIds += accessories.map { it.id }

        val primarySlots = primaries.map { ex ->
            ProgramExerciseSlot(
                programId = programId,
                exerciseId = ex.id,
                role = ExerciseRole.PRIMARY,
                baselineWeeklySetTarget = if (goal == "Strength") 8 else 12,
                progressionTrack = ProgressionTrack(
                    startingSets = 3,
                    setsPerWeekIncrement = 1,
                    loadProgressionPercent = loadProgressionPercent,
                ),
                sfrScore = sfrLookup(ex.id),
            )
        }

        val accessorySlots = accessories.map { ex ->
            ProgramExerciseSlot(
                programId = programId,
                exerciseId = ex.id,
                role = ExerciseRole.ACCESSORY,
                baselineWeeklySetTarget = if (goal == "Strength") 6 else 9,
                progressionTrack = ProgressionTrack(
                    startingSets = 3,
                    setsPerWeekIncrement = 0,
                    loadProgressionPercent = loadProgressionPercent * 0.5,
                ),
                sfrScore = sfrLookup(ex.id),
            )
        }

        slots += primarySlots
        slots += accessorySlots
        slotsByFocus[focus] = FocusExercisePool(
            primarySlots = primarySlots,
            accessorySlots = accessorySlots,
        )
    }

    return ProgramExerciseSelection(slots = slots, slotsByFocus = slotsByFocus)
}

internal fun assignProgramExercisesToSessions(
    sessions: List<PlannedSession>,
    focusSequence: List<String>,
    slotsByFocus: Map<String, FocusExercisePool>,
): Map<Int, List<PlannedSessionExercise>> {
    val accessoryOffsets = mutableMapOf<String, Int>()

    return sessions.associate { session ->
        val rawFocusKey = focusArchetypeForSequence(session.sequenceNumber, focusSequence)
        val normalizedFocusKey = normalizeProgramFocusKey(SessionFocus.toFocusKey(session.focusKey))
        val focusPool = slotsByFocus[rawFocusKey]
            ?: slotsByFocus[normalizedFocusKey]
            ?: slotsByFocus["full_body"]
            ?: FocusExercisePool(emptyList(), emptyList())
        val assignments = mutableListOf<PlannedSessionExercise>()
        var sortOrder = 0

        focusPool.primarySlots.forEach { slot ->
            assignments += PlannedSessionExercise(
                plannedSessionId = 0,
                exerciseId = slot.exerciseId,
                sortOrder = sortOrder++,
            )
        }

        if (focusPool.accessorySlots.isNotEmpty()) {
            val accessoryCount = min(2, focusPool.accessorySlots.size)
            val startIndex = accessoryOffsets[rawFocusKey] ?: 0
            repeat(accessoryCount) { offset ->
                val slot = focusPool.accessorySlots[(startIndex + offset) % focusPool.accessorySlots.size]
                assignments += PlannedSessionExercise(
                    plannedSessionId = 0,
                    exerciseId = slot.exerciseId,
                    sortOrder = sortOrder++,
                )
            }
            accessoryOffsets[rawFocusKey] = if (focusPool.accessorySlots.size <= accessoryCount) {
                0
            } else {
                (startIndex + accessoryCount) % focusPool.accessorySlots.size
            }
        }

        session.sequenceNumber to assignments
    }
}

private fun selectExercisesForFocus(
    candidates: List<GeneratorCatalogExercise>,
    count: Int,
    globallyUsedExerciseIds: Set<Long>,
    locallyExcludedExerciseIds: Set<Long> = emptySet(),
): List<GeneratorCatalogExercise> {
    if (count <= 0) return emptyList()

    val selected = mutableListOf<GeneratorCatalogExercise>()
    val takenExerciseIds = locallyExcludedExerciseIds.toMutableSet()

    fun collect(preferFreshAcrossProgram: Boolean) {
        for (candidate in candidates) {
            if (selected.size >= count) return
            if (candidate.id in takenExerciseIds) continue
            if (preferFreshAcrossProgram && candidate.id in globallyUsedExerciseIds) continue

            selected += candidate
            takenExerciseIds += candidate.id
        }
    }

    collect(preferFreshAcrossProgram = true)
    collect(preferFreshAcrossProgram = false)
    return selected
}

private fun targetAccessorySlotCount(
    candidates: List<GeneratorCatalogExercise>,
    primaryExerciseIds: Set<Long>,
): Int {
    val accessoryCandidates = candidates.count { it.id !in primaryExerciseIds }
    return when {
        accessoryCandidates >= 4 -> 4
        accessoryCandidates == 3 -> 3
        accessoryCandidates == 2 -> 2
        accessoryCandidates == 1 -> 1
        else -> 0
    }
}

class ProgramEngine(
    private val programRepository: ProgramRepository,
    private val generatorRepository: GeneratorRepository,
    private val userRepository: UserRepository,
    private val toastLiftDatabase: ToastLiftDatabase,
) {
    fun generatePlan(
        profile: UserProfile,
        goal: String,
        durationWeeks: Int,
        sessionsPerWeek: Int,
        splitProgramId: Long,
        availableEquipment: Set<String>,
        sessionTimeMinutes: Int,
        equipmentStability: Boolean,
        recentConsistencyPercent: Double,
    ): TrainingProgram {
        val programId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 1. Infer archetype
        val archetype = inferArchetype(
            goal = goal,
            durationWeeks = durationWeeks,
            sessionTimeMinutes = sessionTimeMinutes,
            equipmentStability = equipmentStability,
            recentConsistencyPercent = recentConsistencyPercent,
        )

        // 2. Infer periodization model
        val periodization = inferPeriodization(goal, profile.experience, durationWeeks)

        // 3. Determine actual duration
        val actualWeeks = if (durationWeeks == 0) 6 else durationWeeks

        // 4. Infer outcome metric
        val outcomeMetric = when (goal) {
            "Strength" -> OutcomeMetric.STRENGTH
            "Hypertrophy" -> OutcomeMetric.HYPERTROPHY
            "Conditioning", "Fat Loss" -> OutcomeMetric.WORK_CAPACITY
            else -> OutcomeMetric.CONSISTENCY
        }

        // 5. Build success criteria from history
        val history = generatorRepository.loadHistoricalSetsForRecommendations()
        val successCriteria = buildSuccessCriteria(history, goal)

        // 6. Build adaptation policy
        val adaptationPolicy = AdaptationPolicy(
            allowExerciseRepinning = false,
            maxWeeklySetDeltaPercent = 0.2,
            confidenceFloorForAutonomousChanges = 0.4,
            triggerReviewAfterMissedSessions = 2,
        )

        val program = TrainingProgram(
            id = programId,
            title = "$goal Block 1",
            goal = goal,
            primaryOutcomeMetric = outcomeMetric,
            programArchetype = archetype,
            periodizationModel = periodization,
            splitProgramId = splitProgramId,
            totalWeeks = actualWeeks,
            sessionsPerWeek = sessionsPerWeek,
            successCriteria = successCriteria,
            adaptationPolicy = adaptationPolicy,
            confidenceScore = 1.0,
            createdAt = now,
            status = ProgramStatus.ACTIVE,
        )

        // 7. Build planned weeks
        val weeks = buildPlannedWeeks(programId, actualWeeks, periodization)

        // 8. Build planned sessions
        val splitPrograms = userRepository.loadSplitPrograms()
        val splitName = splitPrograms.firstOrNull { it.id == splitProgramId }?.name ?: "Full Body"
        val focusSequence = generatorRepository.splitSequence(splitName)
        val sessions = buildPlannedSessions(programId, weeks, sessionsPerWeek, focusSequence, sessionTimeMinutes)

        // 9. Select exercises for the program
        val exerciseSelection = selectProgramExercises(
            programId = programId,
            profile = profile,
            goal = goal,
            focusSequence = focusSequence,
            availableEquipment = availableEquipment,
            history = history,
        )

        // 10. Assign exercises to sessions
        val sessionExercises = assignProgramExercisesToSessions(
            sessions = sessions,
            focusSequence = focusSequence,
            slotsByFocus = exerciseSelection.slotsByFocus,
        )

        // 11. Build checkpoints
        val checkpoints = buildCheckpoints(programId, actualWeeks)

        // 12. Save everything
        programRepository.saveProgram(program)
        programRepository.savePlannedWeeks(weeks)
        programRepository.savePlannedSessions(sessions)

        // Reload sessions to get auto-generated IDs
        val savedSessions = programRepository.loadSessionsForProgram(programId)
        val finalSessionExercises = remapSessionExercises(sessionExercises, savedSessions)
        programRepository.saveSessionExercises(finalSessionExercises)
        programRepository.saveExerciseSlots(exerciseSelection.slots)
        programRepository.saveCheckpoints(checkpoints)

        // 13. Log PLAN_CREATED event
        programRepository.logEvent(
            ProgramEvent(
                programId = programId,
                eventType = ProgramEventType.PLAN_CREATED,
                payloadJson = """{"archetype":"${archetype.name}","periodization":"${periodization.name}","weeks":$actualWeeks,"sessionsPerWeek":$sessionsPerWeek}""",
                createdAt = now,
            ),
        )

        return program
    }

    fun realizeSession(
        plannedSession: PlannedSession,
        readinessContext: ReadinessContext,
        history: List<HistoricalExerciseSet>,
        profile: UserProfile,
        availableEquipment: Set<String>,
    ): WorkoutPlan {
        val program = programRepository.loadActiveProgram() ?: error("No active program")
        val weeks = programRepository.loadWeeksForProgram(program.id)
        val currentWeek = weeks.firstOrNull { it.weekNumber == plannedSession.weekNumber }
            ?: weeks.firstOrNull()
            ?: PlannedWeek(programId = program.id, weekNumber = 1, weekType = WeekType.ACCUMULATION)

        val completedSessions = programRepository.loadSessionsForProgram(program.id)
            .filter { it.status == SessionStatus.COMPLETED }
        val sessionExercises = programRepository.loadExercisesForSession(plannedSession.id)
        val slots = programRepository.loadSlotsForProgram(program.id)
        val equipmentCompatibleExercises = filterExercisesByEquipment(sessionExercises, availableEquipment)
        val equipmentLimited = equipmentCompatibleExercises.size < sessionExercises.size

        // Select branch
        val branch = selectBranch(
            readinessContext = readinessContext,
            sessionTimeBudget = plannedSession.timeBudgetMinutes,
            fullSessionMinutes = profile.durationMinutes,
            equipmentLimited = equipmentLimited,
        )

        // Log branch selection
        programRepository.logEvent(
            ProgramEvent(
                programId = program.id,
                eventType = ProgramEventType.BRANCH_SELECTED,
                payloadJson = """{"sessionId":${plannedSession.id},"branch":"${branch.branchType.name}"}""",
                createdAt = System.currentTimeMillis(),
            ),
        )

        // Calculate volume and load adjustments
        val volumeMultiplier = currentWeek.volumeMultiplier
        val intensityModifier = currentWeek.intensityModifier
        val branchVolumeAdjust = when (branch.branchType) {
            BranchType.LOW_READINESS -> 0.8
            BranchType.COMPRESSED_TIME -> 0.7
            else -> 1.0
        }
        val branchLoadAdjust = when (branch.branchType) {
            BranchType.LOW_READINESS -> 0.9
            else -> 1.0
        }

        // Adjust based on recent RPE trends
        val rpeAdjustment = computeRpeAdjustment(completedSessions, history)

        val effectiveVolumeMultiplier = volumeMultiplier * branchVolumeAdjust
        val effectiveLoadMultiplier = intensityModifier * branchLoadAdjust * rpeAdjustment

        // Build forced exercise IDs from session exercises (PRIMARY first)
        val primarySlotExerciseIds = slots
            .filter { it.role == ExerciseRole.PRIMARY }
            .map { it.exerciseId }
        val forcedExerciseIds = equipmentCompatibleExercises
            .sortedBy { ex ->
                if (ex.exerciseId in primarySlotExerciseIds) 0 else 1
            }
            .map { it.exerciseId }

        // Determine time budget
        val timeBudget = readinessContext.timeBudgetMinutes ?: plannedSession.timeBudgetMinutes ?: profile.durationMinutes
        val elasticityMode = timeBudget < (profile.durationMinutes * 0.7)

        // Build ProgramSessionContext
        val programContext = ProgramSessionContext(
            forcedExerciseIds = forcedExerciseIds,
            volumeTarget = (plannedSession.plannedSets * effectiveVolumeMultiplier).roundToInt(),
            loadMultiplier = effectiveLoadMultiplier,
            elasticityMode = elasticityMode,
            timeBudgetMinutes = timeBudget,
        )

        // Generate workout using existing facade
        val splitPrograms = userRepository.loadSplitPrograms()
        val splitName = splitPrograms.firstOrNull { it.id == program.splitProgramId }?.name ?: "Full Body"
        val focusKey = SessionFocus.toFocusKey(plannedSession.focusKey)
        val locationModes = userRepository.loadLocationModes()

        val workout = generatorRepository.generateWorkout(
            profile = profile,
            splitProgram = splitPrograms.firstOrNull { it.id == program.splitProgramId } ?: splitPrograms.first(),
            locationModes = locationModes,
            requestedFocus = focusKey,
            programContext = programContext,
        )

        // Time-elastic execution: mark supersets on accessory exercises when compressed
        val finalWorkout = if (elasticityMode) {
            applyElasticityMutations(workout, slots)
        } else {
            workout
        }

        // Generate coach brief
        val coachBrief = generateCoachBrief(
            program = program,
            plannedSession = plannedSession,
            currentWeek = currentWeek,
            completedSessions = completedSessions,
            branch = branch,
        )
        programRepository.updateSessionCoachBrief(plannedSession.id, coachBrief)

        return finalWorkout.copy(
            title = "${program.title} — Week ${plannedSession.weekNumber}",
            subtitle = "${splitName} • ${focusDisplayName(focusKey)} • ${coachBrief.take(80)}",
        )
    }

    // ── Private helpers ──

    private fun inferArchetype(
        goal: String,
        durationWeeks: Int,
        sessionTimeMinutes: Int,
        equipmentStability: Boolean,
        recentConsistencyPercent: Double,
    ): ProgramArchetype = when {
        recentConsistencyPercent < 0.6 && durationWeeks <= 4 -> ProgramArchetype.COMEBACK
        sessionTimeMinutes <= 35 -> ProgramArchetype.CONSTRAINED_TIME
        !equipmentStability -> ProgramArchetype.TRAVEL_PROOF
        goal == "Strength" -> ProgramArchetype.UNDULATING_WAVE
        else -> ProgramArchetype.LINEAR_RAMP
    }

    private fun inferPeriodization(goal: String, experience: String, durationWeeks: Int): PeriodizationModel = when {
        durationWeeks == 0 -> PeriodizationModel.AUTO_REGULATED
        goal == "Strength" && experience != "Beginner" -> PeriodizationModel.BLOCK
        goal == "Strength" -> PeriodizationModel.LINEAR
        goal == "Hypertrophy" && experience == "Advanced" -> PeriodizationModel.UNDULATING
        else -> PeriodizationModel.LINEAR
    }

    private fun buildSuccessCriteria(
        history: List<HistoricalExerciseSet>,
        goal: String,
    ): SuccessCriteria {
        // Find top exercises from history and set targets at 110% of current best
        val topExercises = history
            .filter { it.completed && it.weight != null && it.weight > 0.0 }
            .groupBy { it.exerciseId }
            .mapValues { (_, sets) -> sets.maxOf { it.weight ?: 0.0 } }
            .entries
            .sortedByDescending { it.value }
            .take(3)

        val targetLifts = topExercises.associate { (exerciseId, bestWeight) ->
            exerciseId to TargetOutcome(
                metric = if (goal == "Strength") "5RM" else "10RM",
                targetValue = bestWeight * 1.1,
            )
        }

        return SuccessCriteria(
            targetLifts = targetLifts,
            targetSessionCompletionRate = 0.8,
        )
    }

    private fun buildPlannedWeeks(
        programId: String,
        totalWeeks: Int,
        periodization: PeriodizationModel,
    ): List<PlannedWeek> {
        return (1..totalWeeks).map { weekNum ->
            val (weekType, volumeMul, intensityMod) = when (periodization) {
                PeriodizationModel.LINEAR -> when {
                    weekNum == totalWeeks -> Triple(WeekType.DELOAD, 0.6, 0.85)
                    weekNum >= totalWeeks - 1 -> Triple(WeekType.INTENSIFICATION, 1.0 + (weekNum - 1) * 0.1, 1.1)
                    else -> Triple(WeekType.ACCUMULATION, 1.0 + (weekNum - 1) * 0.1, 1.0 + (weekNum - 1) * 0.025)
                }
                PeriodizationModel.BLOCK -> when {
                    weekNum == totalWeeks -> Triple(WeekType.DELOAD, 0.6, 0.85)
                    weekNum > totalWeeks * 2 / 3 -> Triple(WeekType.INTENSIFICATION, 0.85, 1.15 + (weekNum - totalWeeks * 2 / 3) * 0.05)
                    else -> Triple(WeekType.ACCUMULATION, 1.0 + (weekNum - 1) * 0.08, 1.0)
                }
                PeriodizationModel.UNDULATING -> when {
                    weekNum == totalWeeks -> Triple(WeekType.DELOAD, 0.6, 0.85)
                    weekNum % 3 == 0 -> Triple(WeekType.INTENSIFICATION, 0.9, 1.1)
                    else -> Triple(WeekType.ACCUMULATION, 1.0 + (weekNum - 1) * 0.05, 1.0)
                }
                PeriodizationModel.AUTO_REGULATED -> when {
                    weekNum == totalWeeks -> Triple(WeekType.DELOAD, 0.6, 0.85)
                    else -> Triple(WeekType.ACCUMULATION, 1.0 + (weekNum - 1) * 0.08, 1.0 + (weekNum - 1) * 0.02)
                }
            }
            PlannedWeek(
                programId = programId,
                weekNumber = weekNum,
                weekType = weekType,
                volumeMultiplier = volumeMul,
                intensityModifier = intensityMod,
            )
        }
    }

    private fun buildPlannedSessions(
        programId: String,
        weeks: List<PlannedWeek>,
        sessionsPerWeek: Int,
        focusSequence: List<String>,
        sessionTimeMinutes: Int,
    ): List<PlannedSession> {
        val sessions = mutableListOf<PlannedSession>()
        var sequenceNumber = 1
        var focusIndex = 0
        for (week in weeks) {
            for (dayIndex in 0 until sessionsPerWeek) {
                val focusKey = focusSequence[focusIndex % focusSequence.size]
                val baseSets = when {
                    sessionsPerWeek <= 3 -> 20
                    sessionsPerWeek == 4 -> 16
                    else -> 14
                }
                val plannedSets = (baseSets * week.volumeMultiplier).roundToInt()
                sessions += PlannedSession(
                    programId = programId,
                    weekNumber = week.weekNumber,
                    dayIndex = dayIndex,
                    sequenceNumber = sequenceNumber++,
                    focusKey = SessionFocus.fromFocusKey(focusKey),
                    plannedSets = plannedSets,
                    timeBudgetMinutes = sessionTimeMinutes,
                )
                focusIndex++
            }
        }
        return sessions
    }

    private fun selectProgramExercises(
        programId: String,
        profile: UserProfile,
        goal: String,
        focusSequence: List<String>,
        availableEquipment: Set<String>,
        history: List<HistoricalExerciseSet>,
    ): ProgramExerciseSelection {
        val candidatesByFocus = focusSequence
            .distinct()
            .associateWith { focus ->
                queryCandidatesForFocus(
                    focus = normalizeProgramFocusKey(focus),
                    availableEquipment = availableEquipment,
                    profile = profile,
                )
            }

        return buildProgramExerciseSelection(
            programId = programId,
            goal = goal,
            focusSequence = focusSequence,
            candidatesByFocus = candidatesByFocus,
            sfrLookup = { exerciseId -> lookupSfrFromHistory(exerciseId, history) },
            profile = profile,
            availableEquipment = availableEquipment,
        )
    }

    private fun queryCandidatesForFocus(
        focus: String,
        availableEquipment: Set<String>,
        profile: UserProfile,
    ): List<GeneratorCatalogExercise> {
        val db = database()
        if (availableEquipment.isEmpty()) return emptyList()

        val placeholders = availableEquipment.joinToString(",") { "?" }
        val args = (availableEquipment + availableEquipment).toTypedArray()
        val bodyRegionFilter = when (focus) {
            "upper_body" -> "e.body_region IN ('Upper Body', 'Full Body', 'Core')"
            "lower_body", "legs_day", FORMULA_B_GLUTES_HAMSTRINGS_DAY_FOCUS_KEY -> "e.body_region IN ('Lower Body', 'Full Body', 'Core')"
            "push_day", "pull_day", "chest_day", "back_day", "shoulders_arms_day",
            FORMULA_B_UPPER_CHEST_DAY_FOCUS_KEY, FORMULA_B_REAR_SIDE_DELTS_DAY_FOCUS_KEY,
            -> "e.body_region IN ('Upper Body', 'Full Body', 'Core')"
            else -> "e.body_region IN ('Full Body', 'Upper Body', 'Lower Body', 'Core')"
        }
        val focusFilter = focusMuscleFilter(focus)

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
                (SELECT group_concat(mp.movement_pattern, '|') FROM exercise_movement_patterns mp WHERE mp.exercise_id = e.exercise_id),
                (SELECT group_concat(po.plane_of_motion, '|') FROM exercise_planes_of_motion po WHERE po.exercise_id = e.exercise_id)
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            WHERE $bodyRegionFilter
              $focusFilter
              AND COALESCE(NULLIF(trim(COALESCE(e.primary_equipment, 'Bodyweight')), ''), 'Bodyweight') IN ($placeholders)
              AND (e.secondary_equipment IS NULL OR trim(e.secondary_equipment) = '' OR e.secondary_equipment IN ($placeholders))
              AND COALESCE(p.is_hidden, 0) = 0
              AND COALESCE(p.is_banned, 0) = 0
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
                            movementPatterns = cursor.getStringOrNull(16)?.split("|")?.filter { it.isNotBlank() }.orEmpty(),
                            planesOfMotion = cursor.getStringOrNull(17)?.split("|")?.filter { it.isNotBlank() }.orEmpty(),
                        ),
                    )
                }
            }
            limitCandidatesForSelectionWindow(
                candidates = candidates,
                biasPlan = buildGymEquipmentBiasPlan(
                    profile = profile,
                    availableEquipment = availableEquipment,
                    desiredCount = 7,
                ),
                maxCount = 120,
            )
        }
    }

    private fun focusMuscleFilter(focus: String): String {
        val muscles = when (focus) {
            "push_day", "chest_day", "shoulders_arms_day" -> listOf("chest", "shoulders", "triceps", "front delts", "side delts", "upper chest")
            "pull_day", "back_day" -> listOf("back", "upper back", "lats", "biceps", "rear delts", "traps")
            "lower_body", "legs_day" -> listOf("quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors", "abdominals")
            FORMULA_B_GLUTES_HAMSTRINGS_DAY_FOCUS_KEY -> listOf(
                "glutes", "hamstrings", "gluteus maximus", "biceps femoris", "semitendinosus", "semimembranosus",
                "adductors", "abductors",
            )
            FORMULA_B_UPPER_CHEST_DAY_FOCUS_KEY -> listOf(
                "chest", "upper chest", "pectoralis major", "front delts", "anterior deltoids", "triceps", "triceps brachii",
            )
            FORMULA_B_REAR_SIDE_DELTS_DAY_FOCUS_KEY -> listOf(
                "shoulders", "rear delts", "side delts", "posterior deltoids", "lateral deltoids", "traps", "trapezius",
            )
            else -> emptyList()
        }
        if (muscles.isEmpty()) return ""

        val quoted = muscles.joinToString(",") { "'${it.lowercase()}'" }
        return """
            AND (
                lower(COALESCE(e.target_muscle_group, '')) IN ($quoted)
                OR lower(COALESCE(e.prime_mover_muscle, '')) IN ($quoted)
                OR lower(COALESCE(e.secondary_muscle, '')) IN ($quoted)
            )
        """.trimIndent()
    }

    private fun database() = toastLiftDatabase.open()

    private fun remapSessionExercises(
        exercisesBySequence: Map<Int, List<PlannedSessionExercise>>,
        savedSessions: List<PlannedSession>,
    ): List<PlannedSessionExercise> {
        val sequenceToSessionId = savedSessions.associate { it.sequenceNumber to it.id }

        return exercisesBySequence.entries
            .sortedBy { it.key }
            .flatMap { (sequenceNumber, exercises) ->
                val sessionId = sequenceToSessionId[sequenceNumber] ?: return@flatMap emptyList()
                exercises.map { it.copy(plannedSessionId = sessionId) }
            }
    }

    private fun buildCheckpoints(
        programId: String,
        totalWeeks: Int,
    ): List<ProgramCheckpoint> {
        val checkpoints = mutableListOf<ProgramCheckpoint>()
        when {
            totalWeeks <= 4 -> {
                checkpoints += ProgramCheckpoint(programId = programId, weekNumber = 2, checkpointType = CheckpointType.PROGRESS_REVIEW)
                checkpoints += ProgramCheckpoint(programId = programId, weekNumber = totalWeeks, checkpointType = CheckpointType.BLOCK_WRAP)
            }
            totalWeeks <= 6 -> {
                checkpoints += ProgramCheckpoint(programId = programId, weekNumber = 2, checkpointType = CheckpointType.PROGRESS_REVIEW)
                checkpoints += ProgramCheckpoint(programId = programId, weekNumber = 4, checkpointType = CheckpointType.PROGRESS_REVIEW)
                checkpoints += ProgramCheckpoint(programId = programId, weekNumber = totalWeeks, checkpointType = CheckpointType.BLOCK_WRAP)
            }
            else -> {
                for (week in 2..totalWeeks step 2) {
                    checkpoints += ProgramCheckpoint(
                        programId = programId,
                        weekNumber = week,
                        checkpointType = if (week == totalWeeks) CheckpointType.BLOCK_WRAP else CheckpointType.PROGRESS_REVIEW,
                    )
                }
            }
        }
        return checkpoints
    }

    private fun selectBranch(
        readinessContext: ReadinessContext,
        sessionTimeBudget: Int?,
        fullSessionMinutes: Int,
        equipmentLimited: Boolean,
    ): SessionBranch {
        val effectiveTimeBudget = readinessContext.timeBudgetMinutes ?: sessionTimeBudget ?: fullSessionMinutes

        return when {
            readinessContext.energyLevel <= 2 -> SessionBranch(
                branchType = BranchType.LOW_READINESS,
                deltaInstructions = "Reduced volume by 20%, load by 10%. Focus on movement quality.",
            )
            equipmentLimited -> SessionBranch(
                branchType = BranchType.EQUIPMENT_LIMITED,
                deltaInstructions = "Adjusted around currently available equipment while preserving the session focus.",
            )
            effectiveTimeBudget < (fullSessionMinutes * 0.7) -> SessionBranch(
                branchType = BranchType.COMPRESSED_TIME,
                deltaInstructions = "Superset accessories, drop finishers. Primary lifts preserved.",
            )
            else -> SessionBranch(
                branchType = BranchType.NORMAL,
                deltaInstructions = "Full session as planned.",
            )
        }
    }

    private fun computeRpeAdjustment(
        completedSessions: List<PlannedSession>,
        history: List<HistoricalExerciseSet>,
    ): Double {
        if (completedSessions.isEmpty()) return 1.0

        // Check if recent RPE is consistently high
        val recentSets = history
            .filter { it.lastSetRpe != null }
            .take(20)

        if (recentSets.isEmpty()) return 1.0
        val averageRpe = recentSets.mapNotNull { it.lastSetRpe }.average()

        return when {
            averageRpe >= 9.0 -> 0.95  // High fatigue, back off slightly
            averageRpe <= 6.5 -> 1.025 // Easy, can push slightly
            else -> 1.0
        }
    }

    private fun generateCoachBrief(
        program: TrainingProgram,
        plannedSession: PlannedSession,
        currentWeek: PlannedWeek,
        completedSessions: List<PlannedSession>,
        branch: SessionBranch,
    ): String {
        val weekContext = when (currentWeek.weekType) {
            WeekType.ACCUMULATION -> "Building volume this week."
            WeekType.INTENSIFICATION -> "Pushing intensity this week — heavier loads, focused effort."
            WeekType.DELOAD -> "Recovery week — reduced volume and load to let adaptation happen."
            WeekType.TEST -> "Testing week — time to see how far you've come."
        }
        val progressNote = if (completedSessions.isNotEmpty()) {
            val completed = completedSessions.size
            val total = programRepository.loadSessionsForProgram(program.id).size.coerceAtLeast(completed)
            "You've completed $completed of $total planned sessions so far."
        } else {
            "This is your first session — establishing baselines."
        }
        val branchNote = when (branch.branchType) {
            BranchType.LOW_READINESS -> "Adapted for lower energy today."
            BranchType.COMPRESSED_TIME -> "Compressed for your available time."
            BranchType.EQUIPMENT_LIMITED -> "Adjusted for available equipment."
            BranchType.NORMAL -> ""
        }
        val focusName = focusDisplayName(SessionFocus.toFocusKey(plannedSession.focusKey))

        return buildString {
            append("Week ${plannedSession.weekNumber}, Session ${plannedSession.dayIndex + 1}: $focusName. ")
            append(weekContext)
            append(" ")
            append(progressNote)
            if (branchNote.isNotBlank()) {
                append(" ")
                append(branchNote)
            }
        }
    }

    private fun lookupSfrFromHistory(exerciseId: Long, history: List<HistoricalExerciseSet>): Double? {
        // SFR scores require 3+ sessions; return null if insufficient data
        val sessions = history
            .filter { it.exerciseId == exerciseId && it.completed }
            .groupBy { it.completedAtUtc }
        if (sessions.size < 3) return null
        return null // Will be populated by user feedback over time
    }

    private fun filterExercisesByEquipment(
        sessionExercises: List<PlannedSessionExercise>,
        availableEquipment: Set<String>,
    ): List<PlannedSessionExercise> {
        if (sessionExercises.isEmpty()) return emptyList()
        val normalizedEquipment = availableEquipment.mapTo(linkedSetOf()) { it.trim().lowercase() }
        val exerciseIds = sessionExercises.map { it.exerciseId }.distinct()
        val placeholders = exerciseIds.joinToString(",") { "?" }
        val compatibility = database().rawQuery(
            """
            SELECT
                exercise_id,
                lower(COALESCE(NULLIF(trim(COALESCE(primary_equipment, 'Bodyweight')), ''), 'bodyweight')),
                lower(COALESCE(NULLIF(trim(secondary_equipment), ''), ''))
            FROM exercises
            WHERE exercise_id IN ($placeholders)
            """.trimIndent(),
            exerciseIds.map(Long::toString).toTypedArray(),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getLong(0),
                        cursor.getString(1) to cursor.getStringOrNull(2),
                    )
                }
            }
        }

        return sessionExercises.filter { exercise ->
            val (primaryEquipment, secondaryEquipment) = compatibility[exercise.exerciseId] ?: return@filter false
            primaryEquipment in normalizedEquipment &&
                (secondaryEquipment.isNullOrBlank() || secondaryEquipment in normalizedEquipment)
        }
    }

    // ── Time-elastic execution ──

    private fun applyElasticityMutations(
        workout: WorkoutPlan,
        slots: List<ProgramExerciseSlot>,
    ): WorkoutPlan {
        val primaryExerciseIds = slots.filter { it.role == ExerciseRole.PRIMARY }.map { it.exerciseId }.toSet()
        // Keep all PRIMARY exercises at full volume; mark accessory pairs as supersets
        val exercises = workout.exercises.toMutableList()

        // Pair accessories for supersets using antagonist pairing
        val accessories = exercises.withIndex()
            .filter { (_, ex) -> ex.exerciseId !in primaryExerciseIds }
            .toMutableList()

        // Simple antagonist pairing: pair consecutive accessories
        val paired = mutableSetOf<Int>()
        for (i in accessories.indices step 2) {
            if (i + 1 < accessories.size) {
                val (idx1, ex1) = accessories[i]
                val (idx2, ex2) = accessories[i + 1]
                if (areAntagonists(ex1, ex2)) {
                    exercises[idx1] = ex1.copy(rationale = ex1.rationale + " (Superset)")
                    exercises[idx2] = ex2.copy(rationale = ex2.rationale + " (Superset)")
                    paired += idx1
                    paired += idx2
                }
            }
        }

        // Drop finisher-like exercises (last accessory) if still over budget
        val finisherCandidates = accessories.filter { it.index !in paired }
        val trimmed = if (finisherCandidates.isNotEmpty() && exercises.size > 4) {
            exercises.toMutableList().also { it.removeAt(finisherCandidates.last().index) }
        } else {
            exercises
        }

        return workout.copy(exercises = trimmed)
    }

    private fun areAntagonists(a: WorkoutExercise, b: WorkoutExercise): Boolean {
        val pairs = antagonistPairs
        val aGroup = a.targetMuscleGroup.lowercase()
        val bGroup = b.targetMuscleGroup.lowercase()
        return pairs[aGroup]?.contains(bGroup) == true || pairs[bGroup]?.contains(aGroup) == true
    }

    private val antagonistPairs = mapOf(
        "chest" to setOf("back", "upper back", "lats"),
        "back" to setOf("chest"),
        "upper back" to setOf("chest"),
        "lats" to setOf("chest", "shoulders"),
        "biceps" to setOf("triceps"),
        "triceps" to setOf("biceps"),
        "quadriceps" to setOf("hamstrings", "glutes"),
        "hamstrings" to setOf("quadriceps"),
        "glutes" to setOf("quadriceps"),
        "shoulders" to setOf("lats", "upper back"),
    )

    private fun focusDisplayName(focus: String): String = when (focus) {
        "upper_body" -> "Upper Body"
        "lower_body" -> "Lower Body"
        "push_day" -> "Push"
        "pull_day" -> "Pull"
        "legs_day" -> "Legs"
        "chest_day" -> "Chest"
        "back_day" -> "Back"
        "shoulders_arms_day" -> "Shoulders + Arms"
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

/**
 * Context passed to WorkoutGenerationFacade when generating a session within a program.
 */
data class ProgramSessionContext(
    val forcedExerciseIds: List<Long>,
    val volumeTarget: Int,
    val loadMultiplier: Double,
    val elasticityMode: Boolean,
    val timeBudgetMinutes: Int?,
)
