package com.fitlib.app.data

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

private const val FAVORITE_SELECTION_BOOST = 28.0
private const val RECOMMEND_MORE_OFTEN_BOOST = 18.0
private const val RECOMMEND_LESS_OFTEN_PENALTY = -18.0
private const val MAX_RECOMMENDATION_DELTA_FOR_SCORING = 2.5
internal const val GYM_LOCATION_MODE_ID = 2L
private const val GYM_MACHINE_CABLE_TARGET_SHARE = 2.0 / 3.0
private val GYM_MACHINE_CABLE_EQUIPMENT = setOf("machine", "cable")

internal data class GymEquipmentBiasPlan(
    val desiredCount: Int,
    val targetPreferredCount: Int,
)

internal fun buildGymEquipmentBiasPlan(
    profile: UserProfile?,
    availableEquipment: Set<String>,
    desiredCount: Int,
): GymEquipmentBiasPlan? {
    if (profile == null) return null
    if (!profile.gymMachineCableBiasEnabled) return null
    if (profile.activeLocationModeId != GYM_LOCATION_MODE_ID) return null
    if (desiredCount <= 0) return null

    val normalizedEquipment = availableEquipment
        .mapTo(linkedSetOf()) { normalizedEquipmentName(it) }
        .filter { it.isNotBlank() }
    if (!normalizedEquipment.containsAll(GYM_MACHINE_CABLE_EQUIPMENT)) return null

    return GymEquipmentBiasPlan(
        desiredCount = desiredCount,
        targetPreferredCount = preferredEquipmentTargetCount(desiredCount),
    )
}

internal fun isMachineOrCableExercise(exercise: GeneratorCatalogExercise): Boolean = isMachineOrCableEquipment(exercise.equipment)

internal fun isMachineOrCableEquipment(equipment: String?): Boolean = normalizedEquipmentName(equipment) in GYM_MACHINE_CABLE_EQUIPMENT

internal fun biasProgramCandidatesTowardGymEquipment(
    candidates: List<GeneratorCatalogExercise>,
    biasPlan: GymEquipmentBiasPlan?,
): List<GeneratorCatalogExercise> {
    if (biasPlan == null || candidates.isEmpty()) return candidates

    val preferred = ArrayDeque(candidates.filter(::isMachineOrCableExercise))
    val fallback = ArrayDeque(candidates.filterNot(::isMachineOrCableExercise))
    if (preferred.isEmpty() || fallback.isEmpty()) return candidates

    val ordered = mutableListOf<GeneratorCatalogExercise>()
    val headLimit = min(candidates.size, biasPlan.desiredCount)
    var preferredPicked = 0

    while (ordered.size < headLimit) {
        val remainingSlots = headLimit - ordered.size
        val preferredNeeded = (biasPlan.targetPreferredCount - preferredPicked).coerceAtLeast(0)
        val takePreferred = when {
            preferred.isEmpty() -> false
            fallback.isEmpty() -> true
            preferredNeeded >= remainingSlots -> true
            preferredNeeded > 0 && ordered.size % 3 != 2 -> true
            else -> false
        }
        if (takePreferred) {
            ordered += preferred.removeFirst()
            preferredPicked++
        } else {
            ordered += fallback.removeFirst()
        }
    }

    while (preferred.isNotEmpty()) ordered += preferred.removeFirst()
    while (fallback.isNotEmpty()) ordered += fallback.removeFirst()
    return ordered
}

internal fun limitCandidatesForSelectionWindow(
    candidates: List<GeneratorCatalogExercise>,
    biasPlan: GymEquipmentBiasPlan?,
    maxCount: Int,
): List<GeneratorCatalogExercise> {
    if (maxCount <= 0) return emptyList()
    if (candidates.size <= maxCount) return candidates

    val ordered = if (biasPlan != null) {
        biasProgramCandidatesTowardGymEquipment(candidates, biasPlan)
    } else {
        candidates
    }
    return ordered.take(maxCount)
}

private fun normalizedEquipmentName(value: String?): String {
    return value
        ?.trim()
        ?.lowercase()
        .orEmpty()
}

private fun preferredEquipmentTargetCount(totalCount: Int): Int {
    if (totalCount <= 0) return 0
    return round(totalCount * GYM_MACHINE_CABLE_TARGET_SHARE)
        .toInt()
        .coerceIn(1, totalCount)
}

data class GeneratorCatalogExercise(
    val id: Long,
    val name: String,
    val difficulty: String,
    val bodyRegion: String,
    val targetMuscleGroup: String,
    val equipment: String,
    val secondaryEquipment: String?,
    val mechanics: String?,
    val favorite: Boolean,
    val preferenceScoreDelta: Double,
    val primeMover: String?,
    val secondaryMuscle: String?,
    val tertiaryMuscle: String?,
    val posture: String,
    val laterality: String,
    val classification: String,
    val movementPatterns: List<String>,
    val planesOfMotion: List<String>,
)

data class HistoricalExerciseSet(
    val completedAtUtc: Instant,
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
    val movementPatterns: List<String>,
    val planesOfMotion: List<String>,
)

enum class IntensityPrescriptionIntent {
    STANDARD,
    HEAVY,
    HIGH_REPS,
}

internal fun intensityPrescriptionIntentForFocusKey(focusKey: String?): IntensityPrescriptionIntent {
    return when (focusKey) {
        FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY,
        FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY,
        FORMULA_A_LOWER_STRENGTH_FOCUS_KEY,
        FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY,
        FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY,
        FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY,
        -> IntensityPrescriptionIntent.HEAVY

        FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY,
        FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY,
        FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY,
        FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY,
        FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY,
        FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY,
        -> IntensityPrescriptionIntent.HIGH_REPS

        else -> IntensityPrescriptionIntent.STANDARD
    }
}

data class GeneratorRestriction(
    val scope: String,
    val value: String,
    val severity: String,
    val notes: String?,
)

data class GeneratorPreferenceState(
    val exerciseId: Long,
    val favorite: Boolean,
    val hidden: Boolean,
    val banned: Boolean,
    val scoreDelta: Double,
    val notes: String?,
)

data class WorkoutGenerationRequest(
    val profile: UserProfile,
    val splitProgramName: String,
    val focus: String,
    val rawFocusKey: String? = null,
    val intensityIntent: IntensityPrescriptionIntent = IntensityPrescriptionIntent.STANDARD,
    val locationName: String,
    val availableEquipment: Set<String>,
    val candidates: List<GeneratorCatalogExercise>,
    val history: List<HistoricalExerciseSet>,
    val restrictions: List<GeneratorRestriction>,
    val preferences: Map<Long, GeneratorPreferenceState>,
    val previousExerciseIds: Set<Long>,
    val variationSeed: Long,
    val nowUtc: Instant,
    val config: GeneratorAlgorithmConfig = GeneratorAlgorithmConfig.default(),
    val programContext: ProgramSessionContext? = null,
)

data class GeneratedWorkoutResult(
    val sessionFormat: String,
    val estimatedMinutes: Int,
    val exercises: List<GeneratedWorkoutExercise>,
    val muscleInsights: List<WorkoutMuscleInsight>,
    val movementInsights: List<WorkoutMovementInsight>,
    val decisionSummary: List<String>,
)

data class GeneratedWorkoutExercise(
    val exerciseId: Long,
    val name: String,
    val bodyRegion: String,
    val targetMuscleGroup: String,
    val equipment: String,
    val setCount: Int,
    val repRange: String,
    val restSeconds: Int,
    val suggestedWeight: Double?,
    val overloadStrategy: String,
    val rationale: String,
    val decisionTrace: List<String>,
)

data class GeneratorAlgorithmConfig(
    val sessionFormatByStyle: Map<String, String>,
    val focusTargets: Map<String, List<String>>,
    val goalProfiles: Map<String, GoalProfile>,
    val baseWeeklyStimulus: Map<String, Double>,
    val experienceVolumeFactor: Map<String, Double>,
    val roleWeights: Map<String, Double>,
    val defaultUnknownEffortWeight: Double,
    val effortWeights: List<EffortWeight>,
    val repWeights: List<RepWeight>,
    val compoundStimulusBonus: Double,
    val unilateralTargetShare: Double,
    val lateralityNeedWeight: Double,
    val patternWeights: Map<String, Double>,
    val planeWeights: Map<String, Double>,
    val hardRestrictionKeywords: Set<String>,
    val mediumRestrictionKeywords: Set<String>,
    val loadIncreaseByGoal: Map<String, Double>,
    val loadRegressionFactor: Double,
    val reentryDays: Long,
    val lowReadinessFloor: Double,
    val transitionSeconds: Int,
    val setupSeconds: Int,
    val warmupSecondsPerPrimary: Int,
    val secondsPerRep: Double,
    val activeSecondsPerSet: Int,
    val imperialRounding: Map<String, Double>,
    val metricRounding: Map<String, Double>,
    val difficultyScores: Map<String, Int>,
) {
    companion object {
        fun default(): GeneratorAlgorithmConfig {
            val focusTargets = mapOf(
                "upper_body" to listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Abdominals", "Trapezius", "Forearms"),
                "lower_body" to listOf("Quadriceps", "Glutes", "Hamstrings", "Calves", "Adductors", "Abductors", "Abdominals"),
                "push_day" to listOf("Chest", "Shoulders", "Triceps", "Abdominals"),
                "pull_day" to listOf("Back", "Biceps", "Trapezius", "Forearms", "Abdominals"),
                "legs_day" to listOf("Quadriceps", "Glutes", "Hamstrings", "Calves", "Adductors", "Abductors"),
                "chest_day" to listOf("Chest", "Shoulders", "Triceps"),
                "back_day" to listOf("Back", "Biceps", "Trapezius", "Forearms"),
                "shoulders_arms_day" to listOf("Shoulders", "Biceps", "Triceps", "Forearms", "Abdominals"),
                FORMULA_B_GLUTES_HAMSTRINGS_DAY_FOCUS_KEY to listOf("Glutes", "Hamstrings", "Adductors", "Abductors"),
                FORMULA_B_UPPER_CHEST_DAY_FOCUS_KEY to listOf("Chest", "Shoulders", "Triceps", "Pectoralis Major", "Anterior Deltoids"),
                FORMULA_B_REAR_SIDE_DELTS_DAY_FOCUS_KEY to listOf("Shoulders", "Lateral Deltoids", "Posterior Deltoids", "Trapezius"),
                "full_body" to listOf("Chest", "Back", "Shoulders", "Quadriceps", "Glutes", "Hamstrings", "Abdominals"),
            )
            return GeneratorAlgorithmConfig(
                sessionFormatByStyle = mapOf(
                    "strength" to "Straight Sets",
                    "balanced" to "Straight Sets",
                    "superset" to "Straight Sets",
                    "circuit" to "Straight Sets",
                    "density" to "Straight Sets",
                    "hybrid" to "Straight Sets",
                ),
                focusTargets = focusTargets,
                goalProfiles = mapOf(
                    "Strength" to GoalProfile(
                        primaryCompoundRepRange = 4..6,
                        secondaryCompoundRepRange = 5..8,
                        accessoryRepRange = 8..12,
                        coreRepRange = 10..15,
                        primaryRestSeconds = 150,
                        secondaryRestSeconds = 120,
                        accessoryRestSeconds = 75,
                        coreRestSeconds = 60,
                    ),
                    "Hypertrophy" to GoalProfile(
                        primaryCompoundRepRange = 6..10,
                        secondaryCompoundRepRange = 8..12,
                        accessoryRepRange = 10..15,
                        coreRepRange = 12..16,
                        primaryRestSeconds = 105,
                        secondaryRestSeconds = 90,
                        accessoryRestSeconds = 60,
                        coreRestSeconds = 45,
                    ),
                    "Conditioning" to GoalProfile(
                        primaryCompoundRepRange = 10..15,
                        secondaryCompoundRepRange = 10..15,
                        accessoryRepRange = 12..20,
                        coreRepRange = 12..20,
                        primaryRestSeconds = 60,
                        secondaryRestSeconds = 45,
                        accessoryRestSeconds = 40,
                        coreRestSeconds = 30,
                    ),
                    "Fat Loss" to GoalProfile(
                        primaryCompoundRepRange = 8..12,
                        secondaryCompoundRepRange = 8..12,
                        accessoryRepRange = 10..15,
                        coreRepRange = 12..20,
                        primaryRestSeconds = 75,
                        secondaryRestSeconds = 60,
                        accessoryRestSeconds = 45,
                        coreRestSeconds = 30,
                    ),
                    "General Fitness" to GoalProfile(
                        primaryCompoundRepRange = 6..10,
                        secondaryCompoundRepRange = 8..12,
                        accessoryRepRange = 10..15,
                        coreRepRange = 10..15,
                        primaryRestSeconds = 90,
                        secondaryRestSeconds = 75,
                        accessoryRestSeconds = 60,
                        coreRestSeconds = 45,
                    ),
                ),
                baseWeeklyStimulus = mapOf(
                    "Strength" to 8.5,
                    "Hypertrophy" to 12.0,
                    "Conditioning" to 7.0,
                    "Fat Loss" to 9.0,
                    "General Fitness" to 9.5,
                ),
                experienceVolumeFactor = mapOf(
                    "Beginner" to 0.85,
                    "Intermediate" to 1.0,
                    "Advanced" to 1.12,
                ),
                roleWeights = mapOf(
                    "prime" to 1.0,
                    "secondary" to 0.55,
                    "tertiary" to 0.3,
                ),
                defaultUnknownEffortWeight = 0.85,
                effortWeights = listOf(
                    EffortWeight(rirMinInclusive = Int.MIN_VALUE, rirMaxInclusive = 2, weight = 1.0),
                    EffortWeight(rirMinInclusive = 3, rirMaxInclusive = 3, weight = 0.9),
                    EffortWeight(rirMinInclusive = 4, rirMaxInclusive = 4, weight = 0.72),
                    EffortWeight(rirMinInclusive = 5, rirMaxInclusive = Int.MAX_VALUE, weight = 0.45),
                ),
                repWeights = listOf(
                    RepWeight(reps = 1..2, weight = 0.65),
                    RepWeight(reps = 3..5, weight = 0.95),
                    RepWeight(reps = 6..12, weight = 1.0),
                    RepWeight(reps = 13..20, weight = 0.88),
                    RepWeight(reps = 21..100, weight = 0.75),
                ),
                compoundStimulusBonus = 1.08,
                unilateralTargetShare = 0.33,
                lateralityNeedWeight = 60.0,
                patternWeights = mapOf(
                    "Horizontal Push" to 1.0,
                    "Vertical Push" to 1.0,
                    "Horizontal Pull" to 1.0,
                    "Vertical Pull" to 1.0,
                    "Knee Dominant" to 1.0,
                    "Hip Hinge" to 1.0,
                    "Loaded Carry" to 0.8,
                    "Rotational" to 0.7,
                    "Anti-Rotational" to 0.8,
                    "Anti-Extension" to 0.8,
                    "Locomotion" to 0.6,
                ),
                planeWeights = mapOf(
                    "Sagittal Plane" to 1.0,
                    "Frontal Plane" to 1.1,
                    "Transverse Plane" to 1.1,
                ),
                hardRestrictionKeywords = setOf("hard", "block", "banned", "severe", "high"),
                mediumRestrictionKeywords = setOf("medium", "moderate"),
                loadIncreaseByGoal = mapOf(
                    "Strength" to 0.03,
                    "Hypertrophy" to 0.025,
                    "Conditioning" to 0.015,
                    "Fat Loss" to 0.015,
                    "General Fitness" to 0.02,
                ),
                loadRegressionFactor = 0.94,
                reentryDays = 21,
                lowReadinessFloor = 0.35,
                transitionSeconds = 45,
                setupSeconds = 30,
                warmupSecondsPerPrimary = 120,
                secondsPerRep = 3.8,
                activeSecondsPerSet = 22,
                imperialRounding = mapOf(
                    "Barbell" to 5.0,
                    "Dumbbell" to 5.0,
                    "Kettlebell" to 5.0,
                    "Cable" to 5.0,
                    "Machine" to 5.0,
                    "default" to 2.5,
                ),
                metricRounding = mapOf(
                    "Barbell" to 2.5,
                    "Dumbbell" to 2.0,
                    "Kettlebell" to 2.0,
                    "Cable" to 2.5,
                    "Machine" to 2.5,
                    "default" to 1.0,
                ),
                difficultyScores = mapOf(
                    "beginner" to 0,
                    "intermediate" to 1,
                    "advanced" to 2,
                ),
            )
        }
    }
}

data class GoalProfile(
    val primaryCompoundRepRange: IntRange,
    val secondaryCompoundRepRange: IntRange,
    val accessoryRepRange: IntRange,
    val coreRepRange: IntRange,
    val primaryRestSeconds: Int,
    val secondaryRestSeconds: Int,
    val accessoryRestSeconds: Int,
    val coreRestSeconds: Int,
)

data class EffortWeight(
    val rirMinInclusive: Int,
    val rirMaxInclusive: Int,
    val weight: Double,
)

data class RepWeight(
    val reps: IntRange,
    val weight: Double,
)

private data class MuscleState(
    val muscle: String,
    val weeklyStimulus: Double,
    val decayedFatigue: Double,
    val readinessScore: Double,
    val mev: Double,
    val mavLow: Double,
    val mavHigh: Double,
    val mrv: Double,
    val volumeStatus: GeneratorVolumeStatus,
    val priorityScore: Double,
)

private enum class GeneratorVolumeStatus {
    BELOW_MEV,
    WITHIN_MAV,
    ABOVE_MRV,
}

private data class MovementBalance(
    val patternExposure: Map<String, Double>,
    val patternNeed: Map<String, Double>,
    val planeExposure: Map<String, Double>,
    val planeNeed: Map<String, Double>,
    val unilateralShare: Double,
    val unilateralNeed: Double,
)

private data class ExercisePerformance(
    val exposureCount: Int,
    val stableTrainingMax: Double?,
    val recentBestWeight: Double?,
    val recentBestEstimated1Rm: Double?,
    val latestTargetRange: IntRange?,
    val latestAverageReps: Double?,
    val latestMinimumReps: Int?,
    val latestCompletedAllTargets: Boolean,
    val latestAtTopOfRange: Boolean,
    val latestEffortWasEasy: Boolean,
    val latestMissedTargets: Boolean,
    val inactivityDays: Long,
    val plateauState: PlateauState,
    val confidenceScore: Double,
)

private enum class PlateauState {
    NEW_EXERCISE,
    LEARNING,
    PROGRESSING,
    STALLED,
    REGRESSING,
}

private enum class SlotKind {
    PRIMARY,
    SECONDARY,
    SUPPORT,
    ACCESSORY,
    CORE,
}

internal fun targetExerciseCountForDuration(durationMinutes: Int): Int {
    val effectiveDurationMinutes = durationMinutes.coerceAtLeast(MIN_WORKOUT_DURATION_MINUTES)
    return max(4, ceil(effectiveDurationMinutes / 15.0).toInt() + 2)
}

private data class CandidateEvaluation(
    val exercise: GeneratorCatalogExercise,
    val slotKind: SlotKind,
    val selectionScore: Double,
    val performance: ExercisePerformance,
    val targetMuscleState: MuscleState,
    val movementNeed: Double,
    val restrictionNotes: List<String>,
)

private data class PrescribedExercise(
    val exercise: GeneratorCatalogExercise,
    val slotKind: SlotKind,
    val setCount: Int,
    val repRange: IntRange,
    val restSeconds: Int,
    val suggestedWeight: Double?,
    val overloadStrategy: String,
    val rationale: String,
    val decisionTrace: List<String>,
    val selectionScore: Double,
)

class WorkoutGenerationFacade(
    private val config: GeneratorAlgorithmConfig = GeneratorAlgorithmConfig.default(),
) {
    fun resolveAdaptiveFocus(
        profile: UserProfile,
        history: List<HistoricalExerciseSet>,
        nowUtc: Instant,
    ): String {
        val muscleIndex = buildMuscleIndex(
            history = history,
            profile = profile,
            focus = "full_body",
            nowUtc = nowUtc,
        )
        val scores = config.focusTargets
            .mapValues { (_, muscles) ->
                val priorities = muscles.map { muscle ->
                    val state = muscleIndex[muscle]
                    if (state == null) 0.25 else max(state.priorityScore, 0.05)
                }
                val mean = priorities.averageOrZero()
                val floor = priorities.minOrNull() ?: 0.0
                val spread = (priorities.maxOrNull() ?: 0.0) - floor
                val coherencePenalty = if (muscles.size > 5) 0.05 else 0.0
                mean + (floor * 0.35) - (spread * 0.5) - coherencePenalty
            }
        val bestSpecialized = scores
            .filterKeys { it != "full_body" }
            .maxByOrNull { it.value }
        return when {
            bestSpecialized != null -> bestSpecialized.key
            else -> scores.maxByOrNull { it.value }?.key ?: "full_body"
        }
    }

    fun generate(request: WorkoutGenerationRequest): GeneratedWorkoutResult {
        val targetDurationMinutes = request.programContext?.timeBudgetMinutes ?: request.profile.durationMinutes
        val desiredExerciseCount = targetExerciseCount(targetDurationMinutes)
        val gymEquipmentBiasPlan = buildGymEquipmentBiasPlan(
            profile = request.profile,
            availableEquipment = request.availableEquipment,
            desiredCount = desiredExerciseCount,
        )
        val focusTargets = config.focusTargets[request.focus].orEmpty()
        val muscleIndex = buildMuscleIndex(
            history = request.history,
            profile = request.profile,
            focus = request.focus,
            nowUtc = request.nowUtc,
        )
        val movementBalance = buildMovementBalance(request.history)
        val filteredCandidates = request.candidates
            .distinctBy { it.id }
            .mapNotNull { candidate -> applyConstraints(candidate, request.availableEquipment, request.restrictions, request.preferences[candidate.id]) }

        val selected = if (request.programContext != null && request.programContext.forcedExerciseIds.isNotEmpty()) {
            selectExercisesWithForced(
                request = request,
                focusTargets = focusTargets,
                candidates = filteredCandidates,
                muscleIndex = muscleIndex,
                movementBalance = movementBalance,
                forcedIds = request.programContext.forcedExerciseIds,
                gymEquipmentBiasPlan = gymEquipmentBiasPlan,
            )
        } else {
            selectExercises(
                request = request,
                focusTargets = focusTargets,
                candidates = filteredCandidates,
                muscleIndex = muscleIndex,
                movementBalance = movementBalance,
                gymEquipmentBiasPlan = gymEquipmentBiasPlan,
            )
        }

        val prescribed = selected.map { evaluation ->
            prescribeExercise(
                request = request,
                evaluation = evaluation,
                muscleState = evaluation.targetMuscleState,
            )
        }

        val timeFit = fitToTimeBudget(
            request = request,
            prescribed = prescribed,
            gymEquipmentBiasPlan = gymEquipmentBiasPlan,
        )
        val musclesForUi = muscleInsights(request.focus, focusTargets, muscleIndex)
        val movementsForUi = movementInsights(movementBalance)
        val topNeeds = musclesForUi.take(3).joinToString { it.muscle }
        val movementNeedSummary = movementsForUi.take(2).joinToString { it.label.lowercase() }
        val preferredEquipmentCount = timeFit.exercises.count { isMachineOrCableExercise(it.exercise) }

        return GeneratedWorkoutResult(
            sessionFormat = config.sessionFormatByStyle[request.profile.workoutStyle] ?: "Straight Sets",
            estimatedMinutes = max(timeFit.estimatedMinutes, min(targetDurationMinutes, 15)),
            exercises = timeFit.exercises.map { exercise ->
                GeneratedWorkoutExercise(
                    exerciseId = exercise.exercise.id,
                    name = exercise.exercise.name,
                    bodyRegion = exercise.exercise.bodyRegion,
                    targetMuscleGroup = exercise.exercise.targetMuscleGroup,
                    equipment = exercise.exercise.equipment,
                    setCount = exercise.setCount,
                    repRange = "${exercise.repRange.first}-${exercise.repRange.last}",
                    restSeconds = exercise.restSeconds,
                    suggestedWeight = exercise.suggestedWeight,
                    overloadStrategy = exercise.overloadStrategy,
                    rationale = exercise.rationale,
                    decisionTrace = exercise.decisionTrace,
                )
            },
            muscleInsights = musclesForUi,
            movementInsights = movementsForUi,
            decisionSummary = buildList {
                add("Focus is ${request.focus.replace('_', ' ')} based on your ${request.splitProgramName.lowercase()} split and current training state.")
                if (request.intensityIntent != IntensityPrescriptionIntent.STANDARD) {
                    add("Day intent is ${request.intensityIntent.name.lowercase().replace('_', ' ')}, which adjusted reps, rests, and accessory volume.")
                }
                if (topNeeds.isNotBlank()) add("Priority muscles: $topNeeds.")
                if (movementNeedSummary.isNotBlank()) add("Movement balance is nudging $movementNeedSummary.")
                if (gymEquipmentBiasPlan != null && timeFit.exercises.isNotEmpty()) {
                    add("Gym mode bias favored cable and machine work ($preferredEquipmentCount/${timeFit.exercises.size} exercises).")
                }
                add("The session was trimmed to ${max(timeFit.estimatedMinutes, 1)} minutes against a $targetDurationMinutes-minute cap.")
            },
        )
    }

    private fun buildMuscleIndex(
        history: List<HistoricalExerciseSet>,
        profile: UserProfile,
        focus: String,
        nowUtc: Instant,
    ): Map<String, MuscleState> {
        val muscles = linkedMapOf<String, MutableMuscleAccumulator>()
        history.forEach { set ->
            val stimulus = computeSetStimulus(set)
            if (stimulus <= 0.0) return@forEach
            val contributions = listOfNotNull(
                set.targetMuscleGroup?.takeIf { it.isNotBlank() }?.let { it to config.roleWeights.getValue("prime") },
                set.secondaryMuscle?.takeIf { it.isNotBlank() }?.let { it to config.roleWeights.getValue("secondary") },
                set.tertiaryMuscle?.takeIf { it.isNotBlank() }?.let { it to config.roleWeights.getValue("tertiary") },
            )
            contributions.forEach { (muscle, roleWeight) ->
                val accumulator = muscles.getOrPut(muscle) { MutableMuscleAccumulator(muscle) }
                val weightedStimulus = stimulus * roleWeight
                val ageDays = Duration.between(set.completedAtUtc, nowUtc).toHours().toDouble() / 24.0
                if (ageDays <= 7.0) accumulator.weeklyStimulus += weightedStimulus
                accumulator.decayedFatigue += weightedStimulus * decayFactor(ageDays, set.classification)
            }
        }

        val focusTargets = config.focusTargets[focus].orEmpty().toSet()
        val goalVolume = config.baseWeeklyStimulus[profile.goal] ?: config.baseWeeklyStimulus.getValue("General Fitness")
        val experienceFactor = config.experienceVolumeFactor[profile.experience] ?: 1.0
        val focusRelatedMuscles = (focusTargets + muscles.keys).ifEmpty { config.focusTargets.getValue("full_body").toSet() }

        return focusRelatedMuscles.associateWith { muscle ->
            val accumulator = muscles[muscle] ?: MutableMuscleAccumulator(muscle)
            val weeklyTarget = goalVolume * experienceFactor * muscleMultiplier(muscle)
            val mev = weeklyTarget * 0.6
            val mavLow = weeklyTarget * 0.9
            val mavHigh = weeklyTarget * 1.2
            val mrv = weeklyTarget * 1.45
            val readiness = clamp(1.0 - (accumulator.decayedFatigue / max(mrv, 1.0)), 0.05, 1.0)
            val volumeNeed = clamp((weeklyTarget - accumulator.weeklyStimulus) / max(weeklyTarget, 1.0), 0.0, 1.0)
            val splitGate = when {
                focus == "full_body" -> 0.85
                muscle in focusTargets -> 1.0
                else -> 0.35
            }
            val volumeStatus = when {
                accumulator.weeklyStimulus < mev -> GeneratorVolumeStatus.BELOW_MEV
                accumulator.weeklyStimulus > mrv -> GeneratorVolumeStatus.ABOVE_MRV
                else -> GeneratorVolumeStatus.WITHIN_MAV
            }
            MuscleState(
                muscle = muscle,
                weeklyStimulus = accumulator.weeklyStimulus,
                decayedFatigue = accumulator.decayedFatigue,
                readinessScore = readiness,
                mev = mev,
                mavLow = mavLow,
                mavHigh = mavHigh,
                mrv = mrv,
                volumeStatus = volumeStatus,
                priorityScore = volumeNeed * readiness * splitGate,
            )
        }
    }

    private fun buildMovementBalance(history: List<HistoricalExerciseSet>): MovementBalance {
        val patternExposure = linkedMapOf<String, Double>()
        val planeExposure = linkedMapOf<String, Double>()
        var unilateralExposure = 0.0
        var totalLateralityExposure = 0.0

        history.forEach { set ->
            val stimulus = computeSetStimulus(set)
            if (stimulus <= 0.0) return@forEach
            set.movementPatterns
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { pattern ->
                    patternExposure[pattern] = (patternExposure[pattern] ?: 0.0) + stimulus
                }
            set.planesOfMotion
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { plane ->
                    planeExposure[plane] = (planeExposure[plane] ?: 0.0) + stimulus
                }
            val laterality = normalizeValue(set.laterality)
            if (laterality.isNotBlank()) {
                totalLateralityExposure += stimulus
                if (laterality != "bilateral") {
                    unilateralExposure += stimulus
                }
            }
        }

        val maxPatternExposure = max(patternExposure.values.maxOrNull() ?: 0.0, 1.0)
        val maxPlaneExposure = max(planeExposure.values.maxOrNull() ?: 0.0, 1.0)
        val patternNeed = config.patternWeights.mapValues { (pattern, weight) ->
            val exposure = patternExposure[pattern] ?: 0.0
            clamp(((maxPatternExposure * weight) - exposure) / max(maxPatternExposure * weight, 1.0), 0.0, 1.0)
        }
        val planeNeed = config.planeWeights.mapValues { (plane, weight) ->
            val exposure = planeExposure[plane] ?: 0.0
            clamp(((maxPlaneExposure * weight) - exposure) / max(maxPlaneExposure * weight, 1.0), 0.0, 1.0)
        }
        val unilateralShare = if (totalLateralityExposure <= 0.0) 0.0 else unilateralExposure / totalLateralityExposure
        val unilateralNeed = clamp((config.unilateralTargetShare - unilateralShare) / max(config.unilateralTargetShare, 0.01), 0.0, 1.0)

        return MovementBalance(
            patternExposure = patternExposure,
            patternNeed = patternNeed,
            planeExposure = planeExposure,
            planeNeed = planeNeed,
            unilateralShare = unilateralShare,
            unilateralNeed = unilateralNeed,
        )
    }

    private fun selectExercises(
        request: WorkoutGenerationRequest,
        focusTargets: List<String>,
        candidates: List<ConstraintResult>,
        muscleIndex: Map<String, MuscleState>,
        movementBalance: MovementBalance,
        gymEquipmentBiasPlan: GymEquipmentBiasPlan?,
    ): List<CandidateEvaluation> {
        if (candidates.isEmpty()) return emptyList()
        val desiredCount = targetExerciseCount(
            request.programContext?.timeBudgetMinutes ?: request.profile.durationMinutes,
        )
        val selected = mutableListOf<CandidateEvaluation>()
        val remaining = candidates.toMutableList()

        while (selected.size < desiredCount && remaining.isNotEmpty()) {
            val slotKind = resolveSlotKind(selected.size, desiredCount)
            val ranked = remaining.map { result ->
                val candidate = result.exercise
                val performance = buildPerformanceProfile(candidate.id, request.history, request.nowUtc)
                val targetMuscleState = muscleIndex[candidate.targetMuscleGroup]
                    ?: MuscleState(
                        muscle = candidate.targetMuscleGroup,
                        weeklyStimulus = 0.0,
                        decayedFatigue = 0.0,
                        readinessScore = 1.0,
                        mev = 0.0,
                        mavLow = 0.0,
                        mavHigh = 0.0,
                        mrv = 1.0,
                        volumeStatus = GeneratorVolumeStatus.BELOW_MEV,
                        priorityScore = if (candidate.targetMuscleGroup in focusTargets) 0.85 else 0.4,
                    )
                val movementNeed = movementNeed(candidate, movementBalance)
                CandidateEvaluation(
                    exercise = candidate,
                    slotKind = slotKind,
                    selectionScore = scoreCandidate(
                        request = request,
                        candidate = candidate,
                        slotKind = slotKind,
                        focusTargets = focusTargets,
                        targetMuscleState = targetMuscleState,
                        movementBalance = movementBalance,
                        performance = performance,
                        selected = selected,
                        restrictionPenalty = result.softPenalty,
                        gymEquipmentBiasPlan = gymEquipmentBiasPlan,
                    ),
                    performance = performance,
                    targetMuscleState = targetMuscleState,
                    movementNeed = movementNeed,
                    restrictionNotes = result.notes,
                )
            }.sortedByDescending { it.selectionScore }

            val next = ranked.firstOrNull() ?: break
            selected += next
            remaining.removeAll { it.exercise.id == next.exercise.id }
        }

        return selected
    }

    private fun selectExercisesWithForced(
        request: WorkoutGenerationRequest,
        focusTargets: List<String>,
        candidates: List<ConstraintResult>,
        muscleIndex: Map<String, MuscleState>,
        movementBalance: MovementBalance,
        forcedIds: List<Long>,
        gymEquipmentBiasPlan: GymEquipmentBiasPlan?,
    ): List<CandidateEvaluation> {
        val desiredCount = targetExerciseCount(
            request.programContext?.timeBudgetMinutes ?: request.profile.durationMinutes,
        )
        val selected = mutableListOf<CandidateEvaluation>()
        val remaining = candidates.toMutableList()

        // First: inject forced exercises in order
        for (forcedId in forcedIds) {
            if (selected.size >= desiredCount) break
            val match = remaining.firstOrNull { it.exercise.id == forcedId } ?: continue
            val candidate = match.exercise
            val slotKind = resolveSlotKind(selected.size, desiredCount)
            val performance = buildPerformanceProfile(candidate.id, request.history, request.nowUtc)
            val targetMuscleState = muscleIndex[candidate.targetMuscleGroup]
                ?: MuscleState(
                    muscle = candidate.targetMuscleGroup,
                    weeklyStimulus = 0.0, decayedFatigue = 0.0, readinessScore = 1.0,
                    mev = 0.0, mavLow = 0.0, mavHigh = 0.0, mrv = 1.0,
                    volumeStatus = GeneratorVolumeStatus.BELOW_MEV, priorityScore = 0.85,
                )
            selected += CandidateEvaluation(
                exercise = candidate,
                slotKind = slotKind,
                selectionScore = 999.0 - selected.size, // High score to preserve order
                performance = performance,
                targetMuscleState = targetMuscleState,
                movementNeed = movementNeed(candidate, movementBalance),
                restrictionNotes = match.notes,
            )
            remaining.removeAll { it.exercise.id == forcedId }
        }

        // Then: fill remaining slots normally
        while (selected.size < desiredCount && remaining.isNotEmpty()) {
            val slotKind = resolveSlotKind(selected.size, desiredCount)
            val ranked = remaining.map { result ->
                val candidate = result.exercise
                val performance = buildPerformanceProfile(candidate.id, request.history, request.nowUtc)
                val targetMuscleState = muscleIndex[candidate.targetMuscleGroup]
                    ?: MuscleState(
                        muscle = candidate.targetMuscleGroup,
                        weeklyStimulus = 0.0, decayedFatigue = 0.0, readinessScore = 1.0,
                        mev = 0.0, mavLow = 0.0, mavHigh = 0.0, mrv = 1.0,
                        volumeStatus = GeneratorVolumeStatus.BELOW_MEV,
                        priorityScore = if (candidate.targetMuscleGroup in focusTargets) 0.85 else 0.4,
                    )
                CandidateEvaluation(
                    exercise = candidate, slotKind = slotKind,
                    selectionScore = scoreCandidate(
                        request = request,
                        candidate = candidate,
                        slotKind = slotKind,
                        focusTargets = focusTargets,
                        targetMuscleState = targetMuscleState,
                        movementBalance = movementBalance,
                        performance = performance,
                        selected = selected,
                        restrictionPenalty = result.softPenalty,
                        gymEquipmentBiasPlan = gymEquipmentBiasPlan,
                    ),
                    performance = performance, targetMuscleState = targetMuscleState,
                    movementNeed = movementNeed(candidate, movementBalance), restrictionNotes = result.notes,
                )
            }.sortedByDescending { it.selectionScore }
            val next = ranked.firstOrNull() ?: break
            selected += next
            remaining.removeAll { it.exercise.id == next.exercise.id }
        }
        return selected
    }

    private fun scoreCandidate(
        request: WorkoutGenerationRequest,
        candidate: GeneratorCatalogExercise,
        slotKind: SlotKind,
        focusTargets: List<String>,
        targetMuscleState: MuscleState,
        movementBalance: MovementBalance,
        performance: ExercisePerformance,
        selected: List<CandidateEvaluation>,
        restrictionPenalty: Double,
        gymEquipmentBiasPlan: GymEquipmentBiasPlan?,
    ): Double {
        val focusAlignment = when {
            candidate.targetMuscleGroup in focusTargets -> 210.0
            candidate.primeMover in focusTargets -> 135.0
            request.focus == "full_body" && candidate.bodyRegion == "Full Body" -> 120.0
            else -> 35.0
        }
        val bodyRegionBonus = when {
            request.focus == "upper_body" && candidate.bodyRegion == "Upper Body" -> 80.0
            request.focus == "lower_body" && candidate.bodyRegion == "Lower Body" -> 80.0
            request.focus in setOf(
                "push_day",
                "pull_day",
                "chest_day",
                "back_day",
                "shoulders_arms_day",
                FORMULA_B_UPPER_CHEST_DAY_FOCUS_KEY,
                FORMULA_B_REAR_SIDE_DELTS_DAY_FOCUS_KEY,
            ) && candidate.bodyRegion == "Upper Body" -> 75.0
            request.focus in setOf("legs_day", FORMULA_B_GLUTES_HAMSTRINGS_DAY_FOCUS_KEY) && candidate.bodyRegion == "Lower Body" -> 75.0
            candidate.bodyRegion == "Full Body" -> 45.0
            candidate.bodyRegion == "Core" -> 20.0
            else -> 0.0
        }
        val slotBonus = slotScore(slotKind, candidate)
        val musclePriority = targetMuscleState.priorityScore * 220.0
        val readinessPenalty = when {
            targetMuscleState.volumeStatus == GeneratorVolumeStatus.ABOVE_MRV -> 120.0
            targetMuscleState.readinessScore < config.lowReadinessFloor -> 70.0
            else -> 0.0
        }
        val movementBoost = movementNeed(candidate, movementBalance) * 95.0
        val continuityBoost = when {
            performance.exposureCount == 0 -> 10.0
            performance.inactivityDays in 7..20 -> 28.0
            performance.inactivityDays > 20 -> 14.0
            else -> 18.0
        }
        val plateauBoost = when (performance.plateauState) {
            PlateauState.NEW_EXERCISE -> 8.0
            PlateauState.LEARNING -> 18.0
            PlateauState.PROGRESSING -> 22.0
            PlateauState.STALLED -> -20.0
            PlateauState.REGRESSING -> -28.0
        }
        val proficiencyFit = difficultyScore(request.profile.experience, candidate.difficulty)
        val scaledPreferenceDelta = candidate.preferenceScoreDelta.coerceIn(
            -MAX_RECOMMENDATION_DELTA_FOR_SCORING,
            MAX_RECOMMENDATION_DELTA_FOR_SCORING,
        )
        val preferenceBoost = (if (candidate.favorite) FAVORITE_SELECTION_BOOST else 0.0) +
            when {
                scaledPreferenceDelta > 0.0 -> scaledPreferenceDelta * RECOMMEND_MORE_OFTEN_BOOST
                scaledPreferenceDelta < 0.0 -> scaledPreferenceDelta * abs(RECOMMEND_LESS_OFTEN_PENALTY)
                else -> 0.0
            }
        val repeatPenalty = buildRepeatPenalty(candidate.id, request.previousExerciseIds, request.history, request.nowUtc)
        val duplicatePenalty = selected.count { it.exercise.targetMuscleGroup == candidate.targetMuscleGroup } * 70.0
        val patternPenalty = selected.count { current ->
            current.exercise.movementPatterns.intersect(candidate.movementPatterns.toSet()).isNotEmpty()
        } * 18.0
        val gymEquipmentBias = gymEquipmentBiasScore(candidate, selected, gymEquipmentBiasPlan)
        val tieBreaker = (((candidate.id * 1103515245L) xor request.variationSeed) and 0xFF).toDouble() / 255.0

        return focusAlignment +
            bodyRegionBonus +
            slotBonus +
            musclePriority +
            movementBoost +
            continuityBoost +
            plateauBoost +
            proficiencyFit +
            preferenceBoost +
            gymEquipmentBias +
            tieBreaker -
            readinessPenalty -
            repeatPenalty -
            duplicatePenalty -
            patternPenalty -
            restrictionPenalty
    }

    private fun gymEquipmentBiasScore(
        candidate: GeneratorCatalogExercise,
        selected: List<CandidateEvaluation>,
        biasPlan: GymEquipmentBiasPlan?,
    ): Double {
        if (biasPlan == null) return 0.0

        val selectedPreferredCount = selected.count { isMachineOrCableExercise(it.exercise) }
        val preferredNeeded = (biasPlan.targetPreferredCount - selectedPreferredCount).coerceAtLeast(0)
        val remainingSlots = biasPlan.desiredCount - selected.size
        val preferredCadenceSlot = selected.size % 3 != 2

        return when {
            isMachineOrCableExercise(candidate) && preferredNeeded > 0 && remainingSlots <= preferredNeeded -> 82.0
            isMachineOrCableExercise(candidate) && preferredNeeded > 0 && preferredCadenceSlot -> 42.0
            isMachineOrCableExercise(candidate) && preferredNeeded > 0 -> 18.0
            isMachineOrCableExercise(candidate) -> 8.0
            preferredNeeded <= 0 -> 0.0
            remainingSlots <= preferredNeeded -> -120.0
            preferredCadenceSlot -> -36.0
            else -> -10.0
        }
    }

    private fun prescribeExercise(
        request: WorkoutGenerationRequest,
        evaluation: CandidateEvaluation,
        muscleState: MuscleState,
    ): PrescribedExercise {
        val goalProfile = config.goalProfiles[request.profile.goal] ?: config.goalProfiles.getValue("General Fitness")
        val progression = decideProgression(
            performance = evaluation.performance,
            muscleState = muscleState,
            goal = request.profile.goal,
            candidate = evaluation.exercise,
        )
        val repRange = adjustRepRangeForIntensity(
            repRange = resolveRepRange(goalProfile, evaluation.slotKind, evaluation.exercise),
            slotKind = evaluation.slotKind,
            intent = request.intensityIntent,
        )
        val restSeconds = adjustRestForIntensity(
            restSeconds = resolveRest(goalProfile, evaluation.slotKind, evaluation.exercise),
            slotKind = evaluation.slotKind,
            intent = request.intensityIntent,
        )
        val baseSets = when (evaluation.slotKind) {
            SlotKind.PRIMARY -> 4
            SlotKind.SECONDARY -> 3
            SlotKind.SUPPORT -> 3
            SlotKind.ACCESSORY -> 3
            SlotKind.CORE -> 2
        }
        val setAdjustment = when {
            progression == "ADD_SET" -> 1
            progression == "REGRESS_LOAD" && muscleState.readinessScore < 0.45 -> -1
            muscleState.volumeStatus == GeneratorVolumeStatus.ABOVE_MRV -> -1
            evaluation.performance.exposureCount == 0 && evaluation.slotKind == SlotKind.PRIMARY -> -1
            else -> 0
        }
        val intensitySetAdjustment = setAdjustmentForIntensity(
            slotKind = evaluation.slotKind,
            intent = request.intensityIntent,
        )
        val setCount = clampInt(baseSets + setAdjustment + intensitySetAdjustment, if (evaluation.slotKind == SlotKind.CORE) 2 else 2, 5)
        val rawSuggestedWeight = suggestWeight(
            request = request,
            candidate = evaluation.exercise,
            performance = evaluation.performance,
            progression = progression,
            repRange = repRange,
        )
        val suggestedWeight = if (rawSuggestedWeight != null && request.programContext != null) {
            roundToIncrement(
                value = rawSuggestedWeight * request.programContext.loadMultiplier,
                equipment = evaluation.exercise.equipment,
                units = request.profile.units,
            )
        } else {
            rawSuggestedWeight
        }
        val rationale = buildString {
            append("Targets ${evaluation.exercise.targetMuscleGroup.lowercase()} because readiness is ${formatPercent(muscleState.readinessScore)}")
            append(" and weekly stimulus is ${formatDecimal(muscleState.weeklyStimulus)}.")
            if (evaluation.movementNeed > 0.35) {
                append(" It also improves movement balance.")
            }
            when (request.intensityIntent) {
                IntensityPrescriptionIntent.HEAVY -> append(" The day is biased toward heavier loading with lower reps and longer rests.")
                IntensityPrescriptionIntent.HIGH_REPS -> append(" The day is biased toward higher reps with shorter rests and a little more accessory volume.")
                IntensityPrescriptionIntent.STANDARD -> Unit
            }
            append(" ${progression.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} is the overload axis.")
        }
        val decisionTrace = buildList {
            add("slot=${evaluation.slotKind.name.lowercase()}")
            add("priority=${formatDecimal(muscleState.priorityScore)}")
            add("readiness=${formatPercent(muscleState.readinessScore)}")
            add("progression=${progression.lowercase()}")
            val preferenceBias = RecommendationBias.fromScoreDelta(evaluation.exercise.preferenceScoreDelta)
            if (preferenceBias != RecommendationBias.Neutral) {
                val label = when (preferenceBias) {
                    RecommendationBias.MoreOften -> "more_often"
                    RecommendationBias.LessOften -> "less_often"
                    RecommendationBias.Neutral -> "neutral"
                }
                add("preference_bias=$label")
            }
            if (suggestedWeight != null) add("suggested_load=${formatDecimal(suggestedWeight)}")
            if (request.intensityIntent != IntensityPrescriptionIntent.STANDARD) {
                add("intensity=${request.intensityIntent.name.lowercase()}")
            }
        }

        return PrescribedExercise(
            exercise = evaluation.exercise,
            slotKind = evaluation.slotKind,
            setCount = setCount,
            repRange = repRange,
            restSeconds = restSeconds,
            suggestedWeight = suggestedWeight,
            overloadStrategy = progression,
            rationale = rationale,
            decisionTrace = decisionTrace,
            selectionScore = evaluation.selectionScore,
        )
    }

    private fun fitToTimeBudget(
        request: WorkoutGenerationRequest,
        prescribed: List<PrescribedExercise>,
        gymEquipmentBiasPlan: GymEquipmentBiasPlan?,
    ): TimeFitResult {
        if (prescribed.isEmpty()) {
            return TimeFitResult(emptyList(), 0)
        }

        val targetDurationMinutes = request.programContext?.timeBudgetMinutes ?: request.profile.durationMinutes
        val working = prescribed.toMutableList()
        while (working.isNotEmpty()) {
            val estimated = estimateMinutes(working)
            if (estimated <= targetDurationMinutes) {
                return TimeFitResult(working.toList(), estimated)
            }
            if (working.size == 1) {
                val last = working.first()
                if (last.setCount > 2) {
                    working[0] = last.copy(setCount = last.setCount - 1)
                    continue
                }
                return TimeFitResult(working.toList(), estimated)
            }

            val removable = working
                .withIndex()
                .sortedWith(
                    compareBy<IndexedValue<PrescribedExercise>>(
                        { removalBiasTier(it.value, working, gymEquipmentBiasPlan) },
                        { utilityTier(it.value.slotKind) },
                        { it.value.selectionScore },
                    ),
                )
                .firstOrNull()
                ?: break

            val target = removable.value
            when {
                target.slotKind in setOf(SlotKind.ACCESSORY, SlotKind.CORE) && target.setCount <= 2 -> working.removeAt(removable.index)
                target.slotKind in setOf(SlotKind.ACCESSORY, SlotKind.CORE, SlotKind.SUPPORT) && target.setCount > 2 -> {
                    working[removable.index] = target.copy(setCount = target.setCount - 1)
                }
                else -> working.removeAt(removable.index)
            }
        }

        return TimeFitResult(working.toList(), estimateMinutes(working))
    }

    private fun removalBiasTier(
        exercise: PrescribedExercise,
        working: List<PrescribedExercise>,
        biasPlan: GymEquipmentBiasPlan?,
    ): Int {
        if (biasPlan == null || !isMachineOrCableExercise(exercise.exercise)) return 0

        val currentPreferredCount = working.count { isMachineOrCableExercise(it.exercise) }
        val nextWorkoutSize = (working.size - 1).coerceAtLeast(0)
        if (nextWorkoutSize == 0) return 0

        val nextPreferredCount = currentPreferredCount - 1
        return if (nextPreferredCount < preferredEquipmentTargetCount(nextWorkoutSize)) 2 else 1
    }

    private fun muscleInsights(
        focus: String,
        focusTargets: List<String>,
        muscleIndex: Map<String, MuscleState>,
    ): List<WorkoutMuscleInsight> {
        val orderedTargets = when {
            focusTargets.isNotEmpty() -> focusTargets
            focus == "full_body" -> muscleIndex.keys.toList()
            else -> muscleIndex.keys.toList()
        }
        val direct = orderedTargets.mapNotNull { muscle ->
            muscleIndex[muscle]?.let { state ->
                WorkoutMuscleInsight(
                    muscle = state.muscle,
                    weeklyStimulus = state.weeklyStimulus,
                    readinessScore = state.readinessScore,
                    priorityScore = state.priorityScore,
                    volumeStatus = state.volumeStatus.name,
                )
            }
        }
        if (direct.isNotEmpty()) return direct
        return muscleIndex.values
            .sortedByDescending { it.priorityScore }
            .take(6)
            .map { state ->
                WorkoutMuscleInsight(
                    muscle = state.muscle,
                    weeklyStimulus = state.weeklyStimulus,
                    readinessScore = state.readinessScore,
                    priorityScore = state.priorityScore,
                    volumeStatus = state.volumeStatus.name,
                )
            }
    }

    private fun movementInsights(balance: MovementBalance): List<WorkoutMovementInsight> {
        val patternInsights = balance.patternNeed
            .filterValues { it > 0.2 }
            .map { (pattern, need) ->
                WorkoutMovementInsight(
                    kind = "pattern",
                    label = pattern,
                    currentExposure = balance.patternExposure[pattern] ?: 0.0,
                    needScore = need,
                )
            }
        val planeInsights = balance.planeNeed
            .filterValues { it > 0.15 }
            .map { (plane, need) ->
                WorkoutMovementInsight(
                    kind = "plane",
                    label = plane,
                    currentExposure = balance.planeExposure[plane] ?: 0.0,
                    needScore = need,
                )
            }
        val laterality = WorkoutMovementInsight(
            kind = "laterality",
            label = "Unilateral balance",
            currentExposure = balance.unilateralShare,
            needScore = balance.unilateralNeed,
        )
        val ranked = (patternInsights + planeInsights)
            .sortedByDescending { it.needScore }
            .take(5)
            .toMutableList()
        if (laterality.needScore > 0.1) {
            ranked += laterality
        }
        return ranked.sortedByDescending { it.needScore }
    }

    private fun applyConstraints(
        candidate: GeneratorCatalogExercise,
        availableEquipment: Set<String>,
        restrictions: List<GeneratorRestriction>,
        preference: GeneratorPreferenceState?,
    ): ConstraintResult? {
        if (preference?.hidden == true || preference?.banned == true) return null
        val normalizedEquipment = availableEquipment.mapTo(linkedSetOf()) { normalizeValue(it) }
        val candidateEquipment = listOfNotNull(candidate.equipment, candidate.secondaryEquipment)
            .map { normalizeValue(it) }
            .filter { it.isNotBlank() }
        if (candidateEquipment.any { it !in normalizedEquipment }) return null

        var softPenalty = 0.0
        val notes = mutableListOf<String>()
        restrictions.forEach { restriction ->
            val match = restrictionMatches(candidate, restriction)
            if (!match) return@forEach
            when (restrictionSeverity(restriction.severity)) {
                RestrictionLevel.HARD -> return null
                RestrictionLevel.MEDIUM -> {
                    softPenalty += 90.0
                    notes += "Soft restriction on ${restriction.scope}=${restriction.value}"
                }
                RestrictionLevel.LOW -> {
                    softPenalty += 40.0
                    notes += "Preference caution on ${restriction.scope}=${restriction.value}"
                }
            }
        }

        return ConstraintResult(candidate, softPenalty, notes)
    }

    private fun buildPerformanceProfile(
        exerciseId: Long,
        history: List<HistoricalExerciseSet>,
        nowUtc: Instant,
    ): ExercisePerformance {
        val sessions = history
            .filter { it.exerciseId == exerciseId && it.completed }
            .groupBy { it.completedAtUtc }
            .toList()
            .sortedByDescending { it.first }

        if (sessions.isEmpty()) {
            return ExercisePerformance(
                exposureCount = 0,
                stableTrainingMax = null,
                recentBestWeight = null,
                recentBestEstimated1Rm = null,
                latestTargetRange = null,
                latestAverageReps = null,
                latestMinimumReps = null,
                latestCompletedAllTargets = false,
                latestAtTopOfRange = false,
                latestEffortWasEasy = false,
                latestMissedTargets = false,
                inactivityDays = Long.MAX_VALUE,
                plateauState = PlateauState.NEW_EXERCISE,
                confidenceScore = 0.0,
            )
        }

        val latest = sessions.first()
        val latestRange = parseRepRange(latest.second.first().targetReps)
        val latestReps = latest.second.mapNotNull { it.actualReps }
        val latestMinReps = latestReps.minOrNull()
        val latestAverageReps = latestReps.takeIf { it.isNotEmpty() }?.average()
        val latestCompletedAllTargets = latestRange != null && latestMinReps != null && latestMinReps >= latestRange.first
        val latestAtTopOfRange = latestRange != null && latestMinReps != null && latestMinReps >= latestRange.last
        val latestEffortWasEasy = latest.second.first().lastSetRir?.let { it >= 3 } ?: latestAtTopOfRange
        val latestMissedTargets = latestRange != null && latestMinReps != null && latestMinReps < latestRange.first

        val e1rms = sessions.flatMap { (_, sets) ->
            sets.mapNotNull { set ->
                val reps = set.actualReps ?: return@mapNotNull null
                val weight = set.weight ?: return@mapNotNull null
                if (reps <= 0 || weight <= 0.0) return@mapNotNull null
                weight * (1.0 + reps / 30.0)
            }
        }
        val recentWeights = sessions.take(3).flatMap { it.second }.mapNotNull { it.weight }
        val stableTrainingMax = e1rms.takeIf { it.isNotEmpty() }?.let { values ->
            val recentAverage = values.take(6).average()
            recentAverage * 0.92
        }
        val recentBestEstimated1Rm = e1rms.maxOrNull()
        val recentBestWeight = recentWeights.maxOrNull()
        val trendSlope = trendSlope(sessions)
        val exposureCount = sessions.size
        val inactivityDays = Duration.between(latest.first, nowUtc).toDays()
        val plateauState = when {
            exposureCount < 2 -> PlateauState.NEW_EXERCISE
            exposureCount < 4 -> PlateauState.LEARNING
            trendSlope > 1.0 -> PlateauState.PROGRESSING
            trendSlope < -1.0 -> PlateauState.REGRESSING
            abs(trendSlope) < 0.35 -> PlateauState.STALLED
            else -> PlateauState.PROGRESSING
        }

        return ExercisePerformance(
            exposureCount = exposureCount,
            stableTrainingMax = stableTrainingMax,
            recentBestWeight = recentBestWeight,
            recentBestEstimated1Rm = recentBestEstimated1Rm,
            latestTargetRange = latestRange,
            latestAverageReps = latestAverageReps,
            latestMinimumReps = latestMinReps,
            latestCompletedAllTargets = latestCompletedAllTargets,
            latestAtTopOfRange = latestAtTopOfRange,
            latestEffortWasEasy = latestEffortWasEasy,
            latestMissedTargets = latestMissedTargets,
            inactivityDays = inactivityDays,
            plateauState = plateauState,
            confidenceScore = clamp(exposureCount / 5.0, 0.0, 1.0),
        )
    }

    private fun decideProgression(
        performance: ExercisePerformance,
        muscleState: MuscleState,
        goal: String,
        candidate: GeneratorCatalogExercise,
    ): String {
        if (performance.inactivityDays >= config.reentryDays) return "REGRESS_LOAD"
        if (muscleState.readinessScore < config.lowReadinessFloor) return "HOLD_STEADY"
        if (muscleState.volumeStatus == GeneratorVolumeStatus.ABOVE_MRV) return "HOLD_STEADY"
        if (performance.plateauState == PlateauState.STALLED && muscleState.weeklyStimulus < muscleState.mavHigh && muscleState.readinessScore > 0.65) {
            return "ADD_SET"
        }
        if (performance.latestMissedTargets) return "REGRESS_LOAD"
        if (candidate.equipment.equals("Bodyweight", ignoreCase = true) && performance.latestCompletedAllTargets && performance.latestEffortWasEasy) {
            return "ADD_REPS"
        }
        if (performance.latestCompletedAllTargets && performance.latestEffortWasEasy && performance.latestAtTopOfRange && performance.stableTrainingMax != null) {
            return "INCREASE_LOAD"
        }
        if (performance.latestCompletedAllTargets && performance.latestEffortWasEasy) return "ADD_REPS"
        if (goal == "Strength" && performance.plateauState == PlateauState.PROGRESSING && performance.stableTrainingMax != null) {
            return "INCREASE_LOAD"
        }
        return "HOLD_STEADY"
    }

    private fun resolveRepRange(
        goalProfile: GoalProfile,
        slotKind: SlotKind,
        candidate: GeneratorCatalogExercise,
    ): IntRange {
        val normalizedClassification = normalizeValue(candidate.classification)
        return when {
            candidate.bodyRegion == "Core" || normalizedClassification.contains("carry") -> goalProfile.coreRepRange
            slotKind == SlotKind.PRIMARY -> goalProfile.primaryCompoundRepRange
            slotKind == SlotKind.SECONDARY || normalizedClassification.contains("compound") -> goalProfile.secondaryCompoundRepRange
            else -> goalProfile.accessoryRepRange
        }
    }

    private fun resolveRest(
        goalProfile: GoalProfile,
        slotKind: SlotKind,
        candidate: GeneratorCatalogExercise,
    ): Int {
        val normalizedClassification = normalizeValue(candidate.classification)
        return when {
            candidate.bodyRegion == "Core" || normalizedClassification.contains("carry") -> goalProfile.coreRestSeconds
            slotKind == SlotKind.PRIMARY -> goalProfile.primaryRestSeconds
            slotKind == SlotKind.SECONDARY || normalizedClassification.contains("compound") -> goalProfile.secondaryRestSeconds
            else -> goalProfile.accessoryRestSeconds
        }
    }

    private fun adjustRepRangeForIntensity(
        repRange: IntRange,
        slotKind: SlotKind,
        intent: IntensityPrescriptionIntent,
    ): IntRange {
        val delta = when (intent) {
            IntensityPrescriptionIntent.STANDARD -> 0
            IntensityPrescriptionIntent.HEAVY -> when (slotKind) {
                SlotKind.CORE -> -1
                SlotKind.PRIMARY, SlotKind.SECONDARY, SlotKind.SUPPORT, SlotKind.ACCESSORY -> -2
            }
            IntensityPrescriptionIntent.HIGH_REPS -> when (slotKind) {
                SlotKind.CORE -> 2
                SlotKind.PRIMARY, SlotKind.SECONDARY -> 2
                SlotKind.SUPPORT, SlotKind.ACCESSORY -> 3
            }
        }
        if (delta == 0) return repRange

        val lower = (repRange.first + delta).coerceAtLeast(3)
        val upper = (repRange.last + delta).coerceAtLeast(lower)
        return lower..upper
    }

    private fun adjustRestForIntensity(
        restSeconds: Int,
        slotKind: SlotKind,
        intent: IntensityPrescriptionIntent,
    ): Int {
        return when (intent) {
            IntensityPrescriptionIntent.STANDARD -> restSeconds
            IntensityPrescriptionIntent.HEAVY -> {
                val multiplier = if (slotKind == SlotKind.PRIMARY || slotKind == SlotKind.SECONDARY) 1.2 else 1.15
                max(restSeconds + 10, round(restSeconds * multiplier).toInt())
            }
            IntensityPrescriptionIntent.HIGH_REPS -> {
                val multiplier = if (slotKind == SlotKind.PRIMARY || slotKind == SlotKind.SECONDARY) 0.85 else 0.9
                max(30, round(restSeconds * multiplier).toInt())
            }
        }
    }

    private fun setAdjustmentForIntensity(
        slotKind: SlotKind,
        intent: IntensityPrescriptionIntent,
    ): Int {
        return when (intent) {
            IntensityPrescriptionIntent.STANDARD -> 0
            IntensityPrescriptionIntent.HEAVY -> when (slotKind) {
                SlotKind.PRIMARY, SlotKind.SECONDARY -> 0
                SlotKind.SUPPORT, SlotKind.ACCESSORY, SlotKind.CORE -> -1
            }
            IntensityPrescriptionIntent.HIGH_REPS -> when (slotKind) {
                SlotKind.PRIMARY, SlotKind.SECONDARY -> 0
                SlotKind.SUPPORT, SlotKind.ACCESSORY, SlotKind.CORE -> 1
            }
        }
    }

    private fun suggestWeight(
        request: WorkoutGenerationRequest,
        candidate: GeneratorCatalogExercise,
        performance: ExercisePerformance,
        progression: String,
        repRange: IntRange,
    ): Double? {
        if (normalizeValue(candidate.equipment) == "bodyweight") return null
        val repTarget = (repRange.first + repRange.last) / 2.0
        val baseWeight = when {
            performance.stableTrainingMax != null -> performance.stableTrainingMax / (1.0 + (repTarget / 30.0))
            performance.recentBestWeight != null -> performance.recentBestWeight
            else -> null
        } ?: return null

        val adjusted = when (progression) {
            "INCREASE_LOAD" -> baseWeight * (1.0 + (config.loadIncreaseByGoal[request.profile.goal] ?: 0.02))
            "REGRESS_LOAD" -> baseWeight * config.loadRegressionFactor
            else -> baseWeight
        }
        return roundToIncrement(
            value = adjusted,
            equipment = candidate.equipment,
            units = request.profile.units,
        )
    }

    private fun movementNeed(candidate: GeneratorCatalogExercise, movementBalance: MovementBalance): Double {
        val patternNeed = candidate.movementPatterns
            .map { movementBalance.patternNeed[it] ?: 0.0 }
            .averageOrZero()
        val planeNeed = candidate.planesOfMotion
            .map { movementBalance.planeNeed[it] ?: 0.0 }
            .averageOrZero()
        val unilateralBoost = if (normalizeValue(candidate.laterality) != "bilateral") movementBalance.unilateralNeed else 0.0
        return (patternNeed * 0.55) + (planeNeed * 0.25) + (unilateralBoost * 0.20)
    }

    private fun slotScore(slotKind: SlotKind, candidate: GeneratorCatalogExercise): Double {
        val normalizedClassification = normalizeValue(candidate.classification)
        val unilateral = normalizeValue(candidate.laterality) != "bilateral"
        return when (slotKind) {
            SlotKind.PRIMARY -> if (normalizedClassification.contains("compound")) 90.0 else -40.0
            SlotKind.SECONDARY -> if (normalizedClassification.contains("compound")) 70.0 else 10.0
            SlotKind.SUPPORT -> if (unilateral || candidate.bodyRegion == "Full Body") 55.0 else 20.0
            SlotKind.ACCESSORY -> if (normalizedClassification.contains("isolation")) 55.0 else 20.0
            SlotKind.CORE -> if (candidate.bodyRegion == "Core" || candidate.targetMuscleGroup == "Abdominals") 75.0 else 0.0
        }
    }

    private fun difficultyScore(experience: String, candidateDifficulty: String): Double {
        val profileLevel = config.difficultyScores[normalizeValue(experience)] ?: 1
        val candidateLevel = config.difficultyScores[normalizeValue(candidateDifficulty)] ?: 1
        return when {
            candidateLevel == profileLevel -> 26.0
            candidateLevel < profileLevel -> 18.0
            candidateLevel == profileLevel + 1 -> 8.0
            else -> -35.0
        }
    }

    private fun buildRepeatPenalty(
        exerciseId: Long,
        previousExerciseIds: Set<Long>,
        history: List<HistoricalExerciseSet>,
        nowUtc: Instant,
    ): Double {
        var penalty = if (exerciseId in previousExerciseIds) 75.0 else 0.0
        val latestAt = history
            .filter { it.exerciseId == exerciseId }
            .maxOfOrNull { it.completedAtUtc }
        if (latestAt != null) {
            val daysAgo = Duration.between(latestAt, nowUtc).toDays()
            penalty += when {
                daysAgo <= 2 -> 80.0
                daysAgo <= 5 -> 45.0
                daysAgo <= 9 -> 20.0
                else -> 0.0
            }
        }
        return penalty
    }

    private fun computeSetStimulus(set: HistoricalExerciseSet): Double {
        val reps = set.actualReps ?: parseRepRange(set.targetReps)?.let { (it.first + it.last) / 2 }
        val repWeight = reps?.let { repStimulusWeight(it) } ?: 0.8
        val effortWeight = resolveEffortWeight(set.lastSetRir)
        val completionWeight = if (set.completed) 1.0 else 0.35
        val compoundBonus = if (normalizeValue(set.classification).contains("compound")) config.compoundStimulusBonus else 1.0
        return repWeight * effortWeight * completionWeight * compoundBonus
    }

    private fun resolveEffortWeight(rir: Int?): Double {
        if (rir == null) return config.defaultUnknownEffortWeight
        return config.effortWeights.firstOrNull { rir in it.rirMinInclusive..it.rirMaxInclusive }?.weight
            ?: config.defaultUnknownEffortWeight
    }

    private fun repStimulusWeight(reps: Int): Double {
        return config.repWeights.firstOrNull { reps in it.reps }?.weight ?: 0.75
    }

    private fun decayFactor(ageDays: Double, classification: String?): Double {
        val halfLife = if (normalizeValue(classification).contains("compound")) 2.5 else 1.8
        return 0.5.pow(ageDays / halfLife)
    }

    private fun restrictionMatches(
        candidate: GeneratorCatalogExercise,
        restriction: GeneratorRestriction,
    ): Boolean {
        val scope = normalizeValue(restriction.scope)
        val expected = normalizeValue(restriction.value)
        val attributes = when (scope) {
            "target_muscle", "target_muscle_group" -> listOf(candidate.targetMuscleGroup)
            "prime_mover", "prime_mover_muscle" -> listOfNotNull(candidate.primeMover)
            "secondary_muscle" -> listOfNotNull(candidate.secondaryMuscle)
            "tertiary_muscle" -> listOfNotNull(candidate.tertiaryMuscle)
            "movement_pattern", "movement_patterns" -> candidate.movementPatterns
            "plane", "plane_of_motion", "planes_of_motion" -> candidate.planesOfMotion
            "laterality" -> listOf(candidate.laterality)
            "body_region" -> listOf(candidate.bodyRegion)
            "classification", "primary_exercise_classification" -> listOf(candidate.classification)
            "mechanics" -> listOfNotNull(candidate.mechanics)
            "equipment", "primary_equipment" -> listOf(candidate.equipment)
            "secondary_equipment" -> listOfNotNull(candidate.secondaryEquipment)
            "posture" -> listOf(candidate.posture)
            "exercise_id" -> listOf(candidate.id.toString())
            "exercise_name", "name" -> listOf(candidate.name)
            else -> listOf(
                candidate.targetMuscleGroup,
                candidate.primeMover.orEmpty(),
                candidate.secondaryMuscle.orEmpty(),
                candidate.tertiaryMuscle.orEmpty(),
                candidate.bodyRegion,
                candidate.equipment,
                candidate.secondaryEquipment.orEmpty(),
                candidate.mechanics.orEmpty(),
                candidate.laterality,
                candidate.classification,
                candidate.posture,
                candidate.name,
            ) + candidate.movementPatterns + candidate.planesOfMotion
        }
        return attributes.any { normalizeValue(it) == expected }
    }

    private fun restrictionSeverity(severity: String): RestrictionLevel {
        val normalized = normalizeValue(severity)
        return when {
            normalized in config.hardRestrictionKeywords -> RestrictionLevel.HARD
            normalized in config.mediumRestrictionKeywords -> RestrictionLevel.MEDIUM
            else -> RestrictionLevel.LOW
        }
    }

    private fun targetExerciseCount(durationMinutes: Int): Int = targetExerciseCountForDuration(durationMinutes)

    private fun resolveSlotKind(index: Int, total: Int): SlotKind {
        return when {
            index == 0 -> SlotKind.PRIMARY
            index == 1 -> SlotKind.SECONDARY
            total >= 5 && index == total - 1 -> SlotKind.CORE
            total >= 6 && index == 2 -> SlotKind.SUPPORT
            else -> SlotKind.ACCESSORY
        }
    }

    private fun utilityTier(slotKind: SlotKind): Int {
        return when (slotKind) {
            SlotKind.PRIMARY -> 3
            SlotKind.SECONDARY -> 2
            SlotKind.SUPPORT -> 1
            SlotKind.ACCESSORY, SlotKind.CORE -> 0
        }
    }

    private fun estimateMinutes(exercises: List<PrescribedExercise>): Int {
        val totalSeconds = exercises.sumOf { exercise ->
            val repTarget = (exercise.repRange.first + exercise.repRange.last) / 2.0
            val workSeconds = round((repTarget * config.secondsPerRep) + config.activeSecondsPerSet).toInt()
            val warmupSeconds = if (exercise.slotKind in setOf(SlotKind.PRIMARY, SlotKind.SECONDARY)) config.warmupSecondsPerPrimary else 0
            warmupSeconds + config.setupSeconds + config.transitionSeconds + (exercise.setCount * (workSeconds + exercise.restSeconds))
        }
        return ceil(totalSeconds / 60.0).toInt()
    }

    private fun roundToIncrement(
        value: Double,
        equipment: String,
        units: String,
    ): Double {
        val increments = if (units.equals("metric", ignoreCase = true)) config.metricRounding else config.imperialRounding
        val increment = increments[equipment] ?: increments.getValue("default")
        return round(value / increment) * increment
    }

    private fun trendSlope(sessions: List<Pair<Instant, List<HistoricalExerciseSet>>>): Double {
        val scores = sessions.map { (_, sets) ->
            val e1rm = sets.mapNotNull { set ->
                val reps = set.actualReps ?: return@mapNotNull null
                val weight = set.weight ?: return@mapNotNull null
                if (reps <= 0 || weight <= 0.0) return@mapNotNull null
                weight * (1.0 + reps / 30.0)
            }.averageOrNull()
            e1rm ?: sets.mapNotNull { it.actualReps?.toDouble() }.averageOrNull() ?: 0.0
        }
        if (scores.size < 2) return 0.0
        val recent = scores.take(min(3, scores.size)).average()
        val older = scores.drop(min(3, scores.size)).takeIf { it.isNotEmpty() }?.average() ?: scores.last()
        return recent - older
    }

    private fun parseRepRange(raw: String): IntRange? {
        val digits = raw
            .split("-", "to", "–")
            .mapNotNull { token -> token.trim().toIntOrNull() }
        return when (digits.size) {
            0 -> null
            1 -> digits.first()..digits.first()
            else -> digits.first()..digits.last()
        }
    }

    private fun muscleMultiplier(muscle: String): Double {
        return when (muscle) {
            "Quadriceps", "Glutes", "Hamstrings", "Back", "Chest" -> 1.15
            "Shoulders", "Trapezius" -> 1.0
            "Calves", "Biceps", "Triceps", "Forearms", "Abdominals", "Adductors", "Abductors" -> 0.8
            else -> 0.95
        }
    }

    private fun normalizeValue(value: String?): String {
        return value
            ?.trim()
            ?.lowercase()
            ?.replace("_", " ")
            ?.replace("-", " ")
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
    }

    private fun clamp(value: Double, min: Double, max: Double): Double = when {
        value < min -> min
        value > max -> max
        else -> value
    }

    private fun clampInt(value: Int, min: Int, max: Int): Int = when {
        value < min -> min
        value > max -> max
        else -> value
    }

    private fun formatPercent(value: Double): String = "${round(value * 100).toInt()}%"

    private fun formatDecimal(value: Double): String = "%.1f".format(value)
}

private data class MutableMuscleAccumulator(
    val muscle: String,
    var weeklyStimulus: Double = 0.0,
    var decayedFatigue: Double = 0.0,
)

private data class ConstraintResult(
    val exercise: GeneratorCatalogExercise,
    val softPenalty: Double,
    val notes: List<String>,
)

private enum class RestrictionLevel {
    HARD,
    MEDIUM,
    LOW,
}

private data class TimeFitResult(
    val exercises: List<PrescribedExercise>,
    val estimatedMinutes: Int,
)

private fun Iterable<Double>.averageOrNull(): Double? {
    val values = toList()
    if (values.isEmpty()) return null
    return values.average()
}

private fun Iterable<Double>.averageOrZero(): Double {
    val values = toList()
    if (values.isEmpty()) return 0.0
    return values.average()
}
