package dev.toastlabs.toastlift.data

import java.util.concurrent.atomic.AtomicLong

private val sessionSetIdGenerator = AtomicLong(1L)

fun nextSessionSetId(): Long = sessionSetIdGenerator.getAndIncrement()

internal const val RECOMMENDATION_BIAS_THRESHOLD = 0.5

enum class RecommendationBias(val scoreDelta: Double) {
    LessOften(-1.0),
    Neutral(0.0),
    MoreOften(1.0),
    ;

    val filterLabel: String
        get() = when (this) {
            MoreOften -> "Upgraded in preference"
            LessOften -> "Downgraded in preference"
            Neutral -> "Neutral"
        }

    companion object {
        val filterableEntries: List<RecommendationBias> = listOf(MoreOften, LessOften)

        fun fromScoreDelta(value: Double?): RecommendationBias {
            return when {
                value == null -> Neutral
                value <= -RECOMMENDATION_BIAS_THRESHOLD -> LessOften
                value >= RECOMMENDATION_BIAS_THRESHOLD -> MoreOften
                else -> Neutral
            }
        }
    }
}

enum class RecommendationSource {
    DIRECT_HISTORY,
    SIMILAR_EXERCISE_HISTORY,
    COLD_START_HEURISTIC,
    BODYWEIGHT,
    NONE,
}

enum class HistoryReuseMode {
    ExactCopy,
    RefreshPrescription,
}

enum class WorkoutFeedbackSignalType(
    val storageValue: String,
    val recommendationDelta: Double,
) {
    GENERATED_PLAN_MANUAL_ADD("generated_plan_manual_add", 1.0),
    GENERATED_PLAN_REMOVE("generated_plan_remove", -1.0),
    ACTIVE_SESSION_MANUAL_ADD("active_session_manual_add", 1.0),
    ACTIVE_SESSION_REMOVE("active_session_remove", -1.0),
    SKIPPED_EXERCISE_DISLIKED("skipped_exercise_disliked", -1.0),
    SKIPPED_EXERCISE_DISMISSED("skipped_exercise_dismissed", 0.0),
    ;
}

data class SkippedExerciseFeedbackPrompt(
    val exerciseId: Long,
    val exerciseName: String,
    val workoutTitle: String,
    val workoutOrigin: String,
    val workoutFocusKey: String?,
    val sessionStartedAtUtc: String,
)

data class ExerciseSummary(
    val id: Long,
    val name: String,
    val difficulty: String,
    val bodyRegion: String,
    val targetMuscleGroup: String,
    val equipment: String,
    val secondaryEquipment: String?,
    val mechanics: String?,
    val favorite: Boolean,
    val hidden: Boolean = false,
    val banned: Boolean = false,
    val preferenceScoreDelta: Double = 0.0,
    val recommendationBias: RecommendationBias = RecommendationBias.Neutral,
)

data class LibraryFilters(
    val equipment: Set<String> = emptySet(),
    val targetMuscles: Set<String> = emptySet(),
    val primeMovers: Set<String> = emptySet(),
    val recommendationBiases: Set<RecommendationBias> = emptySet(),
    val hasLoggedHistoryOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
) {
    fun activeCount(): Int =
        equipment.size +
            targetMuscles.size +
            primeMovers.size +
            recommendationBiases.size +
            if (hasLoggedHistoryOnly) 1 else 0 +
            if (favoritesOnly) 1 else 0
}

data class FilterOptionCount(
    val label: String,
    val count: Int,
)

data class RecommendationBiasFilterOptionCount(
    val bias: RecommendationBias,
    val count: Int,
)

data class LibraryFacets(
    val equipment: List<FilterOptionCount> = emptyList(),
    val targetMuscles: List<FilterOptionCount> = emptyList(),
    val primeMovers: List<FilterOptionCount> = emptyList(),
    val recommendationBiases: List<RecommendationBiasFilterOptionCount> = emptyList(),
    val loggedHistoryCount: Int = 0,
)

data class LibrarySearchPayload(
    val results: List<ExerciseSummary>,
    val facets: LibraryFacets,
)

data class ExerciseDetail(
    val summary: ExerciseSummary,
    val notes: String?,
    val primeMover: String?,
    val secondaryMuscle: String?,
    val tertiaryMuscle: String?,
    val posture: String,
    val laterality: String,
    val classification: String,
    val movementPatterns: List<String>,
    val planesOfMotion: List<String>,
    val demoUrl: String?,
    val explanationUrl: String?,
    val description: String? = null,
    val synonyms: List<String>,
    val defaultVideoLinks: List<ExerciseVideoLink> = emptyList(),
    val userVideoLinks: List<ExerciseVideoLink> = emptyList(),
)

data class TrainingSplitProgram(
    val id: Long,
    val name: String,
    val description: String,
)

data class LocationMode(
    val id: Long,
    val name: String,
    val displayName: String,
)

enum class ThemePreference(val storageValue: String) {
    Dark("dark"),
    Light("light"),
    System("system");

    companion object {
        fun fromStorageValue(value: String?): ThemePreference {
            return entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: Dark
        }
    }
}

enum class SmartPickerMuscleBodyFilter(val storageValue: String) {
    ALL("all"),
    UPPER("upper"),
    LOWER("lower"),
    CORE("core");

    companion object {
        fun fromStorageValue(value: String?): SmartPickerMuscleBodyFilter {
            return entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: ALL
        }
    }
}

data class SmartPickerMuscleTargetOption(
    val name: String,
    val exerciseCount: Int,
    val upperBodyExerciseCount: Int,
    val lowerBodyExerciseCount: Int,
    val coreExerciseCount: Int,
) {
    fun matches(filter: SmartPickerMuscleBodyFilter): Boolean {
        return when (filter) {
            SmartPickerMuscleBodyFilter.ALL -> true
            SmartPickerMuscleBodyFilter.UPPER ->
                upperBodyExerciseCount > 0 || (upperBodyExerciseCount == 0 && lowerBodyExerciseCount == 0 && coreExerciseCount == 0 && !looksLowerBody() && !looksCore())
            SmartPickerMuscleBodyFilter.LOWER ->
                lowerBodyExerciseCount > 0 || (upperBodyExerciseCount == 0 && lowerBodyExerciseCount == 0 && coreExerciseCount == 0 && looksLowerBody())
            SmartPickerMuscleBodyFilter.CORE ->
                coreExerciseCount > 0 || (upperBodyExerciseCount == 0 && lowerBodyExerciseCount == 0 && coreExerciseCount == 0 && looksCore())
        }
    }

    private fun looksLowerBody(): Boolean {
        val normalized = name.lowercase()
        return normalized.contains("quad") ||
            normalized.contains("glute") ||
            normalized.contains("hamstring") ||
            normalized.contains("calf") ||
            normalized.contains("adductor") ||
            normalized.contains("abductor") ||
            normalized.contains("femoris") ||
            normalized.contains("vastus")
    }

    private fun looksCore(): Boolean {
        val normalized = name.lowercase()
        return normalized.contains("abdom") ||
            normalized.contains("oblique") ||
            normalized.contains("transverse abdominis") ||
            normalized.contains("rectus abdominis") ||
            normalized.contains("serratus") ||
            normalized.contains("core")
    }
}

const val MIN_WORKOUT_DURATION_MINUTES = 15
const val MAX_WORKOUT_DURATION_MINUTES = 300
const val MIN_WEEKLY_FREQUENCY = 1
const val MAX_WEEKLY_FREQUENCY = 7
const val FORMULA_A_SPLIT_PROGRAM_ID = 6L
const val FORMULA_A_SPLIT_PROGRAM_NAME = "6-Day PPL Intensity"
const val FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY = "upper_push_strength"
const val FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY = "lower_high_reps"
const val FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY = "upper_pull_strength"
const val FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY = "upper_push_high_reps"
const val FORMULA_A_LOWER_STRENGTH_FOCUS_KEY = "lower_strength"
const val FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY = "upper_pull_high_reps"
const val FORMULA_B_SPLIT_PROGRAM_ID = 7L
const val FORMULA_B_SPLIT_PROGRAM_NAME = "6-Day Glutes/Chest/Delts Intensity"
const val FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY = "glutes_hamstrings_strength"
const val FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY = "upper_chest_high_reps"
const val FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY = "rear_side_delts_strength"
const val FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY = "glutes_hamstrings_high_reps"
const val FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY = "upper_chest_strength"
const val FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY = "rear_side_delts_high_reps"
const val FORMULA_B_GLUTES_HAMSTRINGS_DAY_FOCUS_KEY = "glutes_hamstrings_day"
const val FORMULA_B_UPPER_CHEST_DAY_FOCUS_KEY = "upper_chest_day"
const val FORMULA_B_REAR_SIDE_DELTS_DAY_FOCUS_KEY = "rear_side_delts_day"

fun isValidWorkoutDurationMinutes(durationMinutes: Int): Boolean {
    return durationMinutes in MIN_WORKOUT_DURATION_MINUTES..MAX_WORKOUT_DURATION_MINUTES
}

fun normalizeWorkoutDurationMinutes(durationMinutes: Int): Int {
    return durationMinutes.coerceIn(MIN_WORKOUT_DURATION_MINUTES, MAX_WORKOUT_DURATION_MINUTES)
}

fun isValidWeeklyFrequency(frequency: Int): Boolean {
    return frequency in MIN_WEEKLY_FREQUENCY..MAX_WEEKLY_FREQUENCY
}

fun normalizeWeeklyFrequency(frequency: Int): Int {
    return frequency.coerceIn(MIN_WEEKLY_FREQUENCY, MAX_WEEKLY_FREQUENCY)
}

data class UserProfile(
    val goal: String,
    val experience: String,
    val durationMinutes: Int,
    val weeklyFrequency: Int,
    val splitProgramId: Long,
    val units: String,
    val activeLocationModeId: Long,
    val workoutStyle: String,
    val themePreference: ThemePreference,
    val smartPickerBodyFilter: SmartPickerMuscleBodyFilter = SmartPickerMuscleBodyFilter.ALL,
    val smartPickerTargetMuscle: String? = null,
    val gymMachineCableBiasEnabled: Boolean = true,
    val historyWorkoutAbFlagsVisible: Boolean = false,
    val devPickNextExerciseEnabled: Boolean = false,
    val devFruitExerciseIconsEnabled: Boolean = false,
    val devExerciseDetailPersonalNoteVisible: Boolean = true,
    val devExerciseDetailLearnedPreferenceVisible: Boolean = true,
)

data class WorkoutExercise(
    val exerciseId: Long,
    val name: String,
    val bodyRegion: String,
    val targetMuscleGroup: String,
    val equipment: String,
    val sets: Int,
    val repRange: String,
    val restSeconds: Int,
    val rationale: String,
    val suggestedWeight: Double? = null,
    val overloadStrategy: String? = null,
    val decisionTrace: List<String> = emptyList(),
    val startingSets: List<WorkoutExerciseSetDraft> = emptyList(),
)

data class WorkoutExerciseSetDraft(
    val setNumber: Int,
    val targetReps: String,
    val recommendedReps: Int? = null,
    val recommendedWeight: Double? = null,
    val reps: Int? = null,
    val weight: Double? = null,
    val recommendationSource: RecommendationSource = RecommendationSource.NONE,
    val recommendationConfidence: Double? = null,
)

data class ExercisePrescription(
    val repRange: String,
    val recommendedRepCount: Int?,
    val recommendedWeight: Double?,
    val setCount: Int,
    val source: RecommendationSource,
    val confidence: Double,
    val rationale: List<String> = emptyList(),
)

data class WorkoutMuscleInsight(
    val muscle: String,
    val weeklyStimulus: Double,
    val readinessScore: Double,
    val priorityScore: Double,
    val volumeStatus: String,
)

data class WorkoutMovementInsight(
    val kind: String,
    val label: String,
    val currentExposure: Double,
    val needScore: Double,
)

data class WorkoutPlan(
    val title: String,
    val subtitle: String,
    val locationModeId: Long,
    val estimatedMinutes: Int,
    val origin: String,
    val focusKey: String? = null,
    val exercises: List<WorkoutExercise>,
    val sessionFormat: String? = null,
    val muscleInsights: List<WorkoutMuscleInsight> = emptyList(),
    val movementInsights: List<WorkoutMovementInsight> = emptyList(),
    val decisionSummary: List<String> = emptyList(),
)

data class TemplateSummary(
    val id: Long,
    val name: String,
    val exerciseCount: Int,
)

data class EditableTemplate(
    val id: Long,
    val name: String,
    val origin: String,
    val exercises: List<WorkoutExercise>,
)

data class HistorySummary(
    val id: Long,
    val title: String,
    val completedAtUtc: String,
    val durationSeconds: Int,
    val totalVolume: Double,
    val exerciseCount: Int,
    val exerciseNames: List<String>,
    val strengthScore: Int? = null,
)

data class HistoryExerciseDetail(
    val exerciseId: Long,
    val name: String,
    val targetReps: String,
    val loggedSets: Int,
    val totalSets: Int,
    val totalVolume: Double,
    val bestWeight: Double,
    val bestReps: Int,
    val lastSetRepsInReserve: Int?,
    val lastSetRpe: Double?,
)

data class WorkoutAbFlagSnapshot(
    val experimentKey: String,
    val flagName: String,
    val flagDescription: String,
    val variantKey: String,
    val variantName: String,
    val enabledStatus: String,
)

data class CompletedWorkoutAbFlags(
    val completionFeedbackFlag: WorkoutAbFlagSnapshot? = null,
)

data class HistoryDetail(
    val id: Long,
    val title: String,
    val origin: String,
    val completedAtUtc: String,
    val durationSeconds: Int,
    val totalVolume: Double,
    val exerciseCount: Int,
    val exercises: List<HistoryExerciseDetail>,
    val abFlags: CompletedWorkoutAbFlags? = null,
)

data class ExerciseHistorySet(
    val setNumber: Int,
    val reps: Int?,
    val weight: Double?,
    val isRepPr: Boolean,
    val isWeightPr: Boolean,
    val isVolumePr: Boolean,
)

data class ExerciseHistoryEntry(
    val completedAtUtc: String,
    val workoutTitle: String,
    val targetReps: String,
    val estimatedOneRepMax: Double?,
    val totalVolume: Double,
    val bestWeight: Double,
    val lastSetRepsInReserve: Int?,
    val lastSetRpe: Double?,
    val workingSets: List<ExerciseHistorySet>,
    val hasPersonalRecord: Boolean,
)

data class ExerciseHistoryDetail(
    val exerciseId: Long,
    val exerciseName: String,
    val entries: List<ExerciseHistoryEntry>,
    val isPrOnlyFilterEnabled: Boolean,
    val totalEntries: Int,
    val prEntryCount: Int,
)

data class ExerciseVideoLinks(
    val exerciseId: Long,
    val exerciseName: String,
    val youtubeAppUri: String,
    val youtubeWebUrl: String,
    val tiktokAppUri: String,
    val tiktokWebUrl: String,
)

data class ExerciseVideoLink(
    val id: Long? = null,
    val label: String,
    val url: String,
    val isReadOnly: Boolean = false,
)

data class CustomExerciseTaxonomy(
    val difficultyLevels: List<String> = emptyList(),
    val bodyRegions: List<String> = emptyList(),
    val targetMuscles: List<String> = emptyList(),
    val primeMovers: List<String> = emptyList(),
    val equipmentOptions: List<String> = emptyList(),
    val postures: List<String> = emptyList(),
    val armUsageOptions: List<String> = emptyList(),
    val armPatternOptions: List<String> = emptyList(),
    val gripOptions: List<String> = emptyList(),
    val loadPositionOptions: List<String> = emptyList(),
    val legPatternOptions: List<String> = emptyList(),
    val footElevationOptions: List<String> = emptyList(),
    val combinationTypeOptions: List<String> = emptyList(),
    val forceTypeOptions: List<String> = emptyList(),
    val mechanicsOptions: List<String> = emptyList(),
    val lateralityOptions: List<String> = emptyList(),
    val classificationOptions: List<String> = emptyList(),
    val movementPatternOptions: List<String> = emptyList(),
    val planeOfMotionOptions: List<String> = emptyList(),
)

data class CustomExerciseDraft(
    val name: String = "",
    val difficultyLevel: String = "",
    val bodyRegion: String = "",
    val targetMuscleGroup: String = "",
    val primeMoverMuscle: String = "",
    val secondaryMuscle: String = "",
    val tertiaryMuscle: String = "",
    val primaryEquipment: String = "",
    val primaryItemCount: String = "1",
    val secondaryEquipment: String = "",
    val secondaryItemCount: String = "",
    val posture: String = "",
    val armUsage: String = "",
    val armPattern: String = "",
    val grip: String = "",
    val loadPositionEnding: String = "",
    val legPattern: String = "",
    val footElevation: String = "",
    val combinationType: String = "",
    val forceType: String = "",
    val mechanics: String = "",
    val laterality: String = "",
    val classification: String = "",
    val movementPatternsInput: String = "",
    val planesOfMotionInput: String = "",
    val shortDemoLabel: String = "",
    val shortDemoUrl: String = "",
    val inDepthLabel: String = "",
    val inDepthUrl: String = "",
    val synonymsInput: String = "",
    val taxonomy: CustomExerciseTaxonomy = CustomExerciseTaxonomy(),
    val existingMatches: List<ExerciseSummary> = emptyList(),
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val generatedWithAi: Boolean = false,
    val errorMessage: String? = null,
)

data class SessionSet(
    val id: Long = nextSessionSetId(),
    val setNumber: Int,
    val targetReps: String,
    val recommendedReps: Int? = null,
    val recommendedWeight: String = "",
    val reps: String = "",
    val weight: String = "",
    val recommendationSource: RecommendationSource = RecommendationSource.NONE,
    val recommendationConfidence: Double? = null,
    val completed: Boolean = false,
) {
    fun displayedReps(): String = reps

    fun displayedWeight(): String = weight

    fun resolvedRepsForLogging(): String = reps

    fun resolvedWeightForLogging(): String = weight
}

data class SessionExercise(
    val exerciseId: Long,
    val name: String,
    val bodyRegion: String,
    val targetMuscleGroup: String,
    val equipment: String,
    val restSeconds: Int,
    val sets: List<SessionSet>,
    val activitySequence: Int? = null,
    val completionSequence: Int? = null,
    val lastSetRepsInReserve: Int? = null,
    val notes: String = "",
    val fruitIcon: String? = null,
)

data class ActiveSession(
    val title: String,
    val origin: String,
    val locationModeId: Long,
    val startedAtUtc: String,
    val focusKey: String? = null,
    val subtitle: String = "",
    val estimatedMinutes: Int? = null,
    val sessionFormat: String? = null,
    val exercises: List<SessionExercise>,
)

data class PersistedActiveSessionState(
    val session: ActiveSession,
    val selectedExerciseIndex: Int?,
)

data class AbandonedWorkoutSummary(
    val title: String,
    val startedAtUtc: String,
    val exerciseCount: Int,
    val completedSetCount: Int,
)

data class OnboardingDraft(
    val goal: String = "General Fitness",
    val experience: String = "Intermediate",
    val durationMinutes: Int = 45,
    val weeklyFrequency: Int = 4,
    val splitProgramId: Long = 1,
    val units: String = "imperial",
    val workoutStyle: String = "balanced",
    val smartPickerBodyFilter: SmartPickerMuscleBodyFilter = SmartPickerMuscleBodyFilter.ALL,
    val smartPickerTargetMuscle: String? = null,
)

// ── Adaptive Program Engine Models ──

enum class ProgramStatus { ACTIVE, PAUSED, COMPLETED, ABANDONED }
enum class OutcomeMetric { STRENGTH, HYPERTROPHY, CONSISTENCY, WORK_CAPACITY }

enum class ProgramArchetype {
    LINEAR_RAMP,
    UNDULATING_WAVE,
    COMEBACK,
    CONSTRAINED_TIME,
    TRAVEL_PROOF,
}

enum class PeriodizationModel { LINEAR, UNDULATING, BLOCK, AUTO_REGULATED }

data class SuccessCriteria(
    val targetLifts: Map<Long, TargetOutcome>,
    val targetSessionCompletionRate: Double,
)

data class TargetOutcome(
    val metric: String,
    val targetValue: Double,
)

data class AdaptationPolicy(
    val allowExerciseRepinning: Boolean = false,
    val maxWeeklySetDeltaPercent: Double = 0.2,
    val confidenceFloorForAutonomousChanges: Double = 0.4,
    val triggerReviewAfterMissedSessions: Int = 2,
)

data class TrainingProgram(
    val id: String,
    val title: String,
    val goal: String,
    val primaryOutcomeMetric: OutcomeMetric,
    val programArchetype: ProgramArchetype,
    val periodizationModel: PeriodizationModel,
    val splitProgramId: Long,
    val totalWeeks: Int,
    val sessionsPerWeek: Int,
    val successCriteria: SuccessCriteria,
    val adaptationPolicy: AdaptationPolicy,
    val confidenceScore: Double = 1.0,
    val lastReviewedAt: Long? = null,
    val createdAt: Long,
    val status: ProgramStatus = ProgramStatus.ACTIVE,
)

enum class WeekType { ACCUMULATION, INTENSIFICATION, DELOAD, TEST }

data class PlannedWeek(
    val id: Long = 0,
    val programId: String,
    val weekNumber: Int,
    val weekType: WeekType,
    val volumeMultiplier: Double = 1.0,
    val intensityModifier: Double = 1.0,
)

enum class SessionFocus {
    UPPER_PUSH,
    UPPER_PULL,
    UPPER,
    LOWER,
    FULL_BODY,
    PUSH,
    PULL,
    LEGS,
    UPPER_PUSH_STRENGTH,
    LOWER_HIGH_REPS,
    UPPER_PULL_STRENGTH,
    UPPER_PUSH_HIGH_REPS,
    LOWER_STRENGTH,
    UPPER_PULL_HIGH_REPS,
    GLUTES_HAMSTRINGS_STRENGTH,
    UPPER_CHEST_HIGH_REPS,
    REAR_SIDE_DELTS_STRENGTH,
    GLUTES_HAMSTRINGS_HIGH_REPS,
    UPPER_CHEST_STRENGTH,
    REAR_SIDE_DELTS_HIGH_REPS,
    ;

    companion object {
        fun fromFocusKey(key: String): SessionFocus = when (key) {
            "upper_body" -> UPPER
            "lower_body" -> LOWER
            "push_day" -> PUSH
            "pull_day" -> PULL
            "legs_day" -> LEGS
            "full_body" -> FULL_BODY
            "chest_day" -> UPPER_PUSH
            "back_day" -> UPPER_PULL
            "shoulders_arms_day" -> UPPER_PUSH
            FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY -> UPPER_PUSH_STRENGTH
            FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY -> LOWER_HIGH_REPS
            FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY -> UPPER_PULL_STRENGTH
            FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY -> UPPER_PUSH_HIGH_REPS
            FORMULA_A_LOWER_STRENGTH_FOCUS_KEY -> LOWER_STRENGTH
            FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY -> UPPER_PULL_HIGH_REPS
            FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY -> GLUTES_HAMSTRINGS_STRENGTH
            FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY -> UPPER_CHEST_HIGH_REPS
            FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY -> REAR_SIDE_DELTS_STRENGTH
            FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY -> GLUTES_HAMSTRINGS_HIGH_REPS
            FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY -> UPPER_CHEST_STRENGTH
            FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY -> REAR_SIDE_DELTS_HIGH_REPS
            else -> FULL_BODY
        }

        fun toFocusKey(focus: SessionFocus): String = when (focus) {
            UPPER -> "upper_body"
            LOWER -> "lower_body"
            PUSH -> "push_day"
            PULL -> "pull_day"
            LEGS -> "legs_day"
            FULL_BODY -> "full_body"
            UPPER_PUSH -> "push_day"
            UPPER_PULL -> "pull_day"
            UPPER_PUSH_STRENGTH -> FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY
            LOWER_HIGH_REPS -> FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY
            UPPER_PULL_STRENGTH -> FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY
            UPPER_PUSH_HIGH_REPS -> FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY
            LOWER_STRENGTH -> FORMULA_A_LOWER_STRENGTH_FOCUS_KEY
            UPPER_PULL_HIGH_REPS -> FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY
            GLUTES_HAMSTRINGS_STRENGTH -> FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY
            UPPER_CHEST_HIGH_REPS -> FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY
            REAR_SIDE_DELTS_STRENGTH -> FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY
            GLUTES_HAMSTRINGS_HIGH_REPS -> FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY
            UPPER_CHEST_STRENGTH -> FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY
            REAR_SIDE_DELTS_HIGH_REPS -> FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY
        }
    }
}

enum class SessionStatus { UPCOMING, IN_PROGRESS, COMPLETED, SKIPPED, MIGRATED }

data class PlannedSession(
    val id: Long = 0,
    val programId: String,
    val weekNumber: Int,
    val dayIndex: Int,
    val sequenceNumber: Int,
    val focusKey: SessionFocus,
    val plannedSets: Int,
    val timeBudgetMinutes: Int? = null,
    val status: SessionStatus = SessionStatus.UPCOMING,
    val actualWorkoutId: Long? = null,
    val coachBrief: String? = null,
)

enum class ExecutionStyle { NORMAL, SUPERSET, REST_PAUSE, DROP_SET }

data class PlannedSessionExercise(
    val id: Long = 0,
    val plannedSessionId: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val executionStyle: ExecutionStyle = ExecutionStyle.NORMAL,
)

enum class ExerciseRole { PRIMARY, ACCESSORY, FINISHER }

data class ProgressionTrack(
    val startingSets: Int,
    val setsPerWeekIncrement: Int = 0,
    val loadProgressionPercent: Double = 0.025,
    val repRangeShift: Boolean = false,
    val evolutionTargetExerciseId: Long? = null,
)

data class ProgramExerciseSlot(
    val id: Long = 0,
    val programId: String,
    val exerciseId: Long,
    val role: ExerciseRole,
    val baselineWeeklySetTarget: Int,
    val progressionTrack: ProgressionTrack,
    val sfrScore: Double? = null,
)

enum class CheckpointType { READINESS_REVIEW, PROGRESS_REVIEW, DELOAD_DECISION, BLOCK_WRAP }
enum class CheckpointStatus { PENDING, COMPLETED, SKIPPED }

data class ProgramCheckpoint(
    val id: Long = 0,
    val programId: String,
    val weekNumber: Int,
    val checkpointType: CheckpointType,
    val status: CheckpointStatus = CheckpointStatus.PENDING,
    val completedAt: Long? = null,
    val summary: String? = null,
)

enum class ProgramEventType {
    PLAN_CREATED, SESSION_REALIZED, BRANCH_SELECTED,
    REVIEW_COMPLETED, PLAN_PIVOTED, SFR_UPDATED, EXERCISE_EVOLVED,
}

data class ProgramEvent(
    val id: Long = 0,
    val programId: String,
    val eventType: ProgramEventType,
    val payloadJson: String,
    val createdAt: Long,
)

data class ReadinessContext(
    val energyLevel: Int = 3,
    val sorenessLevel: Int = 2,
    val timeBudgetMinutes: Int? = null,
)

enum class BranchType { NORMAL, LOW_READINESS, COMPRESSED_TIME, EQUIPMENT_LIMITED }

data class SessionBranch(
    val branchType: BranchType,
    val deltaInstructions: String,
)

data class ProgramPosition(
    val programId: String,
    val weekNumber: Int,
    val sessionSequenceNumber: Int,
)

data class ProgramOverview(
    val title: String,
    val weekNumber: Int,
    val totalWeeks: Int,
    val confidenceLabel: String,
    val adherenceSnapshot: AdherenceCurrencySnapshot? = null,
    val nextSessionFocus: String?,
    val coachBrief: String?,
)

enum class SfrTag { GREAT_STIMULUS, JOINT_DISCOMFORT, NO_OPINION }

data class SfrDebriefExercise(
    val exerciseId: Long,
    val exerciseName: String,
)

data class ProgramSetupDraft(
    val goal: String = "Hypertrophy",
    val durationWeeks: Int = 4,
    val sessionsPerWeek: Int = 4,
    val splitProgramId: Long = 2,
    val sessionTimeMinutes: Int = 60,
    val energyLevel: Int = 3,
    val equipmentStability: Boolean = true,
)

data class EquipmentConflictItem(
    val exerciseId: Long,
    val exerciseName: String,
    val role: ExerciseRole,
    val missingEquipment: String,
)
