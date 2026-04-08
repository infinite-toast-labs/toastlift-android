package dev.toastlabs.toastlift.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.toastlabs.toastlift.BuildConfig
import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.AbandonedWorkoutSummary
import dev.toastlabs.toastlift.data.AdherenceCurrencyTrend
import dev.toastlabs.toastlift.data.AppContainer
import dev.toastlabs.toastlift.data.CheckpointAction
import dev.toastlabs.toastlift.data.CheckpointResult
import dev.toastlabs.toastlift.data.CompletionReceiptAccountingSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptAchievementSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptBridgeSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptEvidenceSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptExperimentSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptExperienceVariant
import dev.toastlabs.toastlift.data.CompletionReceiptHeroSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptLearningSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptMeaningKind
import dev.toastlabs.toastlift.data.CompletionReceiptMeaningRowSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptMeaningSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptSplitProgressSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptStatSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptStatsRailSnapshot
import dev.toastlabs.toastlift.data.CustomExerciseDraft
import dev.toastlabs.toastlift.data.DailyCoachMessage
import dev.toastlabs.toastlift.data.EquipmentConflictItem
import dev.toastlabs.toastlift.data.AdherenceSessionSignal
import dev.toastlabs.toastlift.data.ExerciseDetail
import dev.toastlabs.toastlift.data.ExerciseHistoryDetail
import dev.toastlabs.toastlift.data.ExerciseSummary
import dev.toastlabs.toastlift.data.ExerciseVideoLinks
import dev.toastlabs.toastlift.data.FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_LOWER_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY
import dev.toastlabs.toastlift.data.FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY
import dev.toastlabs.toastlift.data.HistoricalExerciseSet
import dev.toastlabs.toastlift.data.HistoryShareFormat
import dev.toastlabs.toastlift.data.HistoryReuseMode
import dev.toastlabs.toastlift.data.HistorySummary
import dev.toastlabs.toastlift.data.HistoryDetail
import dev.toastlabs.toastlift.data.LibraryFacets
import dev.toastlabs.toastlift.data.LibraryFilters
import dev.toastlabs.toastlift.data.LocationMode
import dev.toastlabs.toastlift.data.normalizeExerciseNote
import dev.toastlabs.toastlift.data.normalizeExerciseVideoLinkLabel
import dev.toastlabs.toastlift.data.normalizeExerciseVideoLinkUrl
import dev.toastlabs.toastlift.data.normalizeWeeklyFrequency
import dev.toastlabs.toastlift.data.normalizeWorkoutDurationMinutes
import dev.toastlabs.toastlift.data.OnboardingDraft
import dev.toastlabs.toastlift.data.PersonalDataExportPayload
import dev.toastlabs.toastlift.data.PendingWorkoutShare
import dev.toastlabs.toastlift.data.PlannedSession
import dev.toastlabs.toastlift.data.PlannedSessionExercise
import dev.toastlabs.toastlift.data.ProgramCheckpoint
import dev.toastlabs.toastlift.data.ProgramCompletionTruth
import dev.toastlabs.toastlift.data.ProgramOverview
import dev.toastlabs.toastlift.data.ProgramSetupDraft
import dev.toastlabs.toastlift.data.ProgramStatus
import dev.toastlabs.toastlift.data.ReadinessContext
import dev.toastlabs.toastlift.data.RecommendationBias
import dev.toastlabs.toastlift.data.pause
import dev.toastlabs.toastlift.data.resume
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionFocus
import dev.toastlabs.toastlift.data.SessionSet
import dev.toastlabs.toastlift.data.SessionStatus
import dev.toastlabs.toastlift.data.SkippedExerciseFeedbackPrompt
import dev.toastlabs.toastlift.data.SmartPickerMuscleTargetOption
import dev.toastlabs.toastlift.data.SfrDebriefExercise
import dev.toastlabs.toastlift.data.SfrTag
import dev.toastlabs.toastlift.data.StrengthScoreSummary
import dev.toastlabs.toastlift.data.TemplateSummary
import dev.toastlabs.toastlift.data.TodayCompletionFeedbackVariant
import dev.toastlabs.toastlift.data.ThemePreference
import dev.toastlabs.toastlift.data.TrainingProgram
import dev.toastlabs.toastlift.data.TrainingSplitProgram
import dev.toastlabs.toastlift.data.UserProfile
import dev.toastlabs.toastlift.data.WeeklyPromiseSnapshot
import dev.toastlabs.toastlift.data.WeeklyMuscleTargetWorkoutRow
import dev.toastlabs.toastlift.data.WorkoutExercise
import dev.toastlabs.toastlift.data.WorkoutFeedbackSignalType
import dev.toastlabs.toastlift.data.WorkoutMovementInsight
import dev.toastlabs.toastlift.data.WorkoutMuscleInsight
import dev.toastlabs.toastlift.data.WorkoutPlan
import dev.toastlabs.toastlift.data.WorkoutExerciseSetDraft
import dev.toastlabs.toastlift.data.buildCompletionReceiptAbFlags
import dev.toastlabs.toastlift.data.buildTodayCompletionFeedbackAbFlags
import dev.toastlabs.toastlift.data.buildAdherenceCurrencySnapshot
import dev.toastlabs.toastlift.data.buildGlobalAdherenceCurrencyTrend
import dev.toastlabs.toastlift.data.toPendingWorkoutShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

enum class MainTab(val label: String) {
    Today("Today"),
    Generate("Generate"),
    Library("Library"),
    History("History"),
    Profile("Profile"),
}

enum class CustomExerciseDestination {
    ActiveSession,
    ManualBuilder,
    GeneratedWorkout,
    TodayTemplate,
}

enum class ActiveSessionAddExerciseMode {
    Choice,
    Manual,
    Generated,
}

internal data class ActiveSessionGeneratedExerciseState(
    val exercise: WorkoutExercise? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val seenExerciseIds: Set<Long> = emptySet(),
)

private const val UPCOMING_PROGRAM_SESSIONS_LIMIT = 4
internal const val PROFILE_SAVED_MESSAGE = "Profile saved."
private const val ANALYTICS_ROLE_WEIGHT_PRIME = 1.0
private const val ANALYTICS_ROLE_WEIGHT_SECONDARY = 0.55
private const val ANALYTICS_ROLE_WEIGHT_TERTIARY = 0.3
private const val ANALYTICS_UNILATERAL_TARGET_SHARE = 0.33

private val ANALYTICS_BASE_WEEKLY_STIMULUS = mapOf(
    "Strength" to 8.5,
    "Hypertrophy" to 12.0,
    "Conditioning" to 7.0,
    "Fat Loss" to 9.0,
    "General Fitness" to 9.5,
)

private val ANALYTICS_EXPERIENCE_VOLUME_FACTOR = mapOf(
    "Beginner" to 0.85,
    "Intermediate" to 1.0,
    "Advanced" to 1.12,
)

private val ANALYTICS_PATTERN_WEIGHTS = linkedMapOf(
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
)

private val ANALYTICS_PLANE_WEIGHTS = linkedMapOf(
    "Sagittal Plane" to 1.0,
    "Frontal Plane" to 1.1,
    "Transverse Plane" to 1.1,
)

private data class AnalyticsMuscleAccumulator(
    var weeklyStimulus: Double = 0.0,
    var decayedFatigue: Double = 0.0,
)

private data class ProjectedContributionAccumulator(
    var exposure: Double = 0.0,
    val exerciseIds: MutableSet<Long> = linkedSetOf(),
)

private data class HistoricalAnalytics(
    val muscles: List<WorkoutMuscleInsight>,
    val movements: List<WorkoutMovementInsight>,
)

private data class ProjectedAnalytics(
    val muscles: List<ProjectedMuscleInsight>,
    val movements: List<ProjectedMovementInsight>,
)

data class UpcomingProgramSessionSummary(
    val sessionId: Long,
    val weekNumber: Int,
    val dayNumber: Int,
    val sequenceOffset: Int,
    val focusLabel: String,
    val plannedSets: Int,
    val timeBudgetMinutes: Int?,
    val exerciseNames: List<String>,
)

data class ProgramWeekProgressSummary(
    val weekNumber: Int,
    val weekTypeLabel: String?,
    val statusLabel: String,
    val isCurrentWeek: Boolean,
    val completedSessions: Int,
    val skippedSessions: Int,
    val totalSessions: Int,
    val sessionStatuses: List<SessionStatus>,
    val checkpointStatus: dev.toastlabs.toastlift.data.CheckpointStatus?,
)

data class ProgramProgressSummary(
    val currentWeekNumber: Int,
    val totalWeeks: Int,
    val completedSessions: Int,
    val skippedSessions: Int,
    val remainingSessions: Int,
    val totalSessions: Int,
    val completedWeeks: Int,
    val completedCheckpoints: Int,
    val totalCheckpoints: Int,
    val weekSummaries: List<ProgramWeekProgressSummary>,
)

data class WeeklyMuscleTargetRange(
    val start: LocalDate,
    val end: LocalDate,
)

data class WeeklyMuscleTargetMuscleSummary(
    val label: String,
    val completedSets: Double,
    val targetSets: Double,
)

data class WeeklyMuscleTargetGroupSummary(
    val key: String,
    val label: String,
    val completedSets: Double,
    val targetSets: Double,
    val muscleSummaries: List<WeeklyMuscleTargetMuscleSummary>,
)

data class WeeklyMuscleTargetHistorySummary(
    val weekNumber: Int,
    val completionRatio: Double,
    val range: WeeklyMuscleTargetRange,
)

data class WeeklyMuscleTargetSummary(
    val weekNumber: Int,
    val overallCompletionRatio: Double,
    val completedSets: Double,
    val targetSets: Double,
    val range: WeeklyMuscleTargetRange,
    val groupSummaries: List<WeeklyMuscleTargetGroupSummary>,
    val history: List<WeeklyMuscleTargetHistorySummary>,
)

data class ProjectedMuscleInsight(
    val muscle: String,
    val contribution: Double,
    val share: Double,
    val exerciseCount: Int,
)

data class ProjectedMovementInsight(
    val kind: String,
    val label: String,
    val exposure: Double,
    val share: Double,
    val exerciseCount: Int,
)

internal data class AppUiState(
    val isLoading: Boolean = true,
    val selectedTab: MainTab = MainTab.Today,
    val themePreference: ThemePreference = ThemePreference.Dark,
    val onboardingDraft: OnboardingDraft = OnboardingDraft(),
    val profile: UserProfile? = null,
    val smartPickerTargetOptions: List<SmartPickerMuscleTargetOption> = emptyList(),
    val splitPrograms: List<TrainingSplitProgram> = emptyList(),
    val locationModes: List<LocationMode> = emptyList(),
    val equipmentOptions: List<String> = emptyList(),
    val equipmentByLocation: Map<Long, Set<String>> = emptyMap(),
    val recommendationBiasByExerciseId: Map<Long, RecommendationBias> = emptyMap(),
    val libraryQuery: String = "",
    val librarySearchVisible: Boolean = false,
    val libraryFilters: LibraryFilters = LibraryFilters(),
    val libraryFacets: LibraryFacets = LibraryFacets(),
    val libraryResults: List<ExerciseSummary> = emptyList(),
    val selectedExerciseDetail: ExerciseDetail? = null,
    val generatingExerciseDescriptionId: Long? = null,
    val selectedExerciseHistory: ExerciseHistoryDetail? = null,
    val selectedExerciseVideos: ExerciseVideoLinks? = null,
    val generatedWorkout: WorkoutPlan? = null,
    val manualWorkoutName: String = "Custom Workout",
    val manualWorkoutItems: List<WorkoutExercise> = emptyList(),
    val editingTemplateId: Long? = null,
    val editingTemplateOrigin: String? = null,
    val todayEditingTemplateId: Long? = null,
    val todayEditingTemplateOrigin: String? = null,
    val todayEditingTemplateName: String = "",
    val todayEditingTemplateItems: List<WorkoutExercise> = emptyList(),
    val templates: List<TemplateSummary> = emptyList(),
    val history: List<HistorySummary> = emptyList(),
    val historyTopExercise: String? = null,
    val historyTopEquipment: String? = null,
    val historyStrengthScore: StrengthScoreSummary? = null,
    val historicalMuscleInsights: List<WorkoutMuscleInsight> = emptyList(),
    val historicalMovementInsights: List<WorkoutMovementInsight> = emptyList(),
    val projectedMuscleInsights: List<ProjectedMuscleInsight> = emptyList(),
    val projectedMovementInsights: List<ProjectedMovementInsight> = emptyList(),
    val abandonedWorkout: AbandonedWorkoutSummary? = null,
    val selectedHistoryDetail: HistoryDetail? = null,
    val completionReceipt: CompletionReceiptUiState? = null,
    val activeSession: ActiveSession? = null,
    val activeSessionExerciseIndex: Int? = null,
    val activeSessionAddExerciseVisible: Boolean = false,
    val activeSessionAddExerciseMode: ActiveSessionAddExerciseMode = ActiveSessionAddExerciseMode.Choice,
    val activeSessionGeneratedExercise: ActiveSessionGeneratedExerciseState = ActiveSessionGeneratedExerciseState(),
    val skippedExerciseFeedbackPrompt: SkippedExerciseFeedbackPrompt? = null,
    val customExerciseDraft: CustomExerciseDraft? = null,
    val customExerciseDestination: CustomExerciseDestination? = null,
    val pendingAddExercisePickerSelection: ExerciseSummary? = null,
    val pendingPersonalDataExport: PersonalDataExportPayload? = null,
    val pendingWorkoutShare: PendingWorkoutShare? = null,
    val message: String? = null,
    val dailyCoachMessage: DailyCoachMessage? = null,
    val todayCompletionFeedbackVariant: TodayCompletionFeedbackVariant = TodayCompletionFeedbackVariant.DONE_TODAY_BADGE,
    val todayWorkoutCompletion: TodayWorkoutCompletionState = TodayWorkoutCompletionState(
        isCompletedToday = false,
        progressFraction = 0f,
    ),
    val todayReceiptRecap: TodayReceiptRecapState? = null,
    val debugReceiptLaunch: CompletionReceiptDebugLaunch? = null,
    // ── Adaptive Program Engine state ──
    val activeProgram: TrainingProgram? = null,
    val programOverview: ProgramOverview? = null,
    val tokenBalanceTrend: AdherenceCurrencyTrend? = null,
    val programProgress: ProgramProgressSummary? = null,
    val weeklyMuscleTargets: WeeklyMuscleTargetSummary? = null,
    val nextPlannedSession: PlannedSession? = null,
    val recoverableSkippedSession: PlannedSession? = null,
    val nextSessionExercises: List<PlannedSessionExercise> = emptyList(),
    val upcomingProgramSessions: List<UpcomingProgramSessionSummary> = emptyList(),
    val pendingCheckpoint: ProgramCheckpoint? = null,
    val showProgramSetup: Boolean = false,
    val showCheckpointReview: Boolean = false,
    val showSfrDebrief: Boolean = false,
    val showProgramWrapUp: Boolean = false,
    val programSetupDraft: ProgramSetupDraft = ProgramSetupDraft(),
    val checkpointResult: CheckpointResult? = null,
    val sfrDebriefExercises: List<SfrDebriefExercise> = emptyList(),
    val equipmentConflicts: List<EquipmentConflictItem> = emptyList(),
    val programReadiness: ReadinessContext = ReadinessContext(),
)

internal data class WorkoutGenerationRequestContext(
    val previousExerciseIds: Set<Long> = emptySet(),
    val requestedFocus: String? = null,
)

internal fun workoutGenerationRequestContext(currentWorkout: WorkoutPlan?): WorkoutGenerationRequestContext {
    return WorkoutGenerationRequestContext(
        previousExerciseIds = currentWorkout?.exercises?.map { it.exerciseId }?.toSet().orEmpty(),
        requestedFocus = currentWorkout?.focusKey,
    )
}

internal fun workoutGenerationRequestContext(session: ActiveSession?): WorkoutGenerationRequestContext {
    return WorkoutGenerationRequestContext(
        previousExerciseIds = session?.exercises?.map(SessionExercise::exerciseId)?.toSet().orEmpty(),
        requestedFocus = session?.focusKey,
    )
}

internal fun generatedActiveSessionExerciseExclusionIds(
    session: ActiveSession?,
    generatedState: ActiveSessionGeneratedExerciseState,
): Set<Long> {
    return session?.exercises?.map(SessionExercise::exerciseId)?.toSet().orEmpty() + generatedState.seenExerciseIds
}

internal fun generatedAdditionalSessionExercise(
    workout: WorkoutPlan,
    excludedExerciseIds: Set<Long>,
): WorkoutExercise? {
    return workout.exercises.firstOrNull { it.exerciseId !in excludedExerciseIds }
}

internal fun syncProgramSetupDraftWithProfileDuration(
    currentDraft: ProgramSetupDraft,
    profile: UserProfile?,
): ProgramSetupDraft {
    val profileDurationMinutes = profile?.durationMinutes ?: return currentDraft
    val normalizedProfileDurationMinutes = normalizeWorkoutDurationMinutes(profileDurationMinutes)
    val defaultSessionTimeMinutes = ProgramSetupDraft().sessionTimeMinutes
    return if (currentDraft.sessionTimeMinutes == defaultSessionTimeMinutes) {
        currentDraft.copy(sessionTimeMinutes = normalizedProfileDurationMinutes)
    } else {
        currentDraft
    }
}

internal fun activeSessionSelectionAfterExerciseRemoval(
    selectedExerciseIndex: Int?,
    removedExerciseIndex: Int,
    remainingExerciseCount: Int,
): Int? {
    val adjustedSelection = when {
        selectedExerciseIndex == null -> null
        selectedExerciseIndex == removedExerciseIndex -> null
        selectedExerciseIndex > removedExerciseIndex -> selectedExerciseIndex - 1
        else -> selectedExerciseIndex
    }
    return adjustedSelection?.takeIf { it in 0 until remainingExerciseCount }
}

private fun SessionExercise.isFullyCompletedInActiveWorkout(): Boolean {
    return sets.isNotEmpty() && sets.all(SessionSet::completed)
}

private fun SessionExercise.hasLoggedSetInActiveWorkout(): Boolean {
    return sets.any(SessionSet::completed)
}

private fun SessionExercise.isNotStartedInActiveWorkout(): Boolean {
    return sets.none(SessionSet::completed)
}

internal fun orderedSessionExercises(session: ActiveSession): List<IndexedValue<SessionExercise>> {
    return session.exercises
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<SessionExercise>> { it.value.activitySequence ?: Int.MIN_VALUE }
                .thenByDescending { it.value.hasLoggedSetInActiveWorkout() }
                .thenByDescending { it.value.isFullyCompletedInActiveWorkout() }
                .thenByDescending { it.value.completionSequence ?: Int.MIN_VALUE }
                .thenBy { it.index },
        )
}

private fun nextSessionExerciseActivitySequence(exercises: List<SessionExercise>): Int {
    return (exercises.mapNotNull(SessionExercise::activitySequence).maxOrNull() ?: 0) + 1
}

private fun nextSessionExerciseCompletionSequence(exercises: List<SessionExercise>): Int {
    return (exercises.mapNotNull(SessionExercise::completionSequence).maxOrNull() ?: 0) + 1
}

internal fun pickNextSessionExerciseIndex(
    session: ActiveSession,
    random: Random = Random.Default,
): Int? {
    return pickNextSessionExerciseIndex(
        session = session,
        smartTargetMuscle = null,
        exerciseDetailsById = emptyMap(),
        random = random,
    )
}

internal fun pickNextSessionExerciseIndex(
    session: ActiveSession,
    smartTargetMuscle: String?,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
    random: Random = Random.Default,
): Int? {
    val unstartedExercises = session.exercises
        .withIndex()
        .filter { (_, exercise) -> exercise.isNotStartedInActiveWorkout() }
    if (unstartedExercises.isEmpty()) return null
    val normalizedTarget = normalizeMuscleToken(smartTargetMuscle)
    if (normalizedTarget.isBlank()) return unstartedExercises.random(random).index

    val rankedMatches = unstartedExercises
        .map { indexedExercise ->
            indexedExercise to smartPickExerciseScore(
                exercise = indexedExercise.value,
                detail = exerciseDetailsById[indexedExercise.value.exerciseId],
                normalizedTargetMuscle = normalizedTarget,
            )
        }
        .filter { (_, score) -> score > 0.0 }
        .sortedWith(
            compareByDescending<Pair<IndexedValue<SessionExercise>, Double>> { it.second }
                .thenBy { it.first.index },
        )

    return rankedMatches.firstOrNull()?.first?.index ?: unstartedExercises.random(random).index
}

internal fun reconcileSessionExerciseCompletionState(
    exercises: List<SessionExercise>,
    exerciseIndex: Int,
    updatedExercise: SessionExercise,
    promoteForLoggedSet: Boolean = false,
): SessionExercise {
    val previousExercise = exercises.getOrNull(exerciseIndex)
    val activityAdjustedExercise = when {
        promoteForLoggedSet && updatedExercise.hasLoggedSetInActiveWorkout() -> updatedExercise.copy(
            activitySequence = nextSessionExerciseActivitySequence(exercises),
        )
        !updatedExercise.hasLoggedSetInActiveWorkout() && updatedExercise.activitySequence != null -> updatedExercise.copy(activitySequence = null)
        else -> updatedExercise
    }
    val wasCompleted = previousExercise?.isFullyCompletedInActiveWorkout() == true
    val isCompleted = activityAdjustedExercise.isFullyCompletedInActiveWorkout()
    return when {
        isCompleted && !wasCompleted -> activityAdjustedExercise.copy(
            completionSequence = nextSessionExerciseCompletionSequence(exercises),
        )
        isCompleted -> activityAdjustedExercise
        activityAdjustedExercise.completionSequence != null -> activityAdjustedExercise.copy(completionSequence = null)
        else -> activityAdjustedExercise
    }
}

internal fun reorderActiveSessionSets(
    sets: List<SessionSet>,
    prioritizedCompletedSetId: Long? = null,
): List<SessionSet> {
    val prioritizedCompletedSet = prioritizedCompletedSetId?.let { setId ->
        sets.firstOrNull { it.id == setId && it.completed }
    }
    val orderedSets = buildList {
        addAll(
            sets.filter { set ->
                set.completed && set.id != prioritizedCompletedSetId
            },
        )
        prioritizedCompletedSet?.let { add(it) }
        addAll(sets.filterNot(SessionSet::completed))
    }
    return orderedSets.mapIndexed { index, set ->
        set.copy(setNumber = index + 1)
    }
}

internal fun logNextSessionSetInActiveSession(
    session: ActiveSession,
    exerciseIndex: Int,
    loggedAt: Instant = Instant.now(),
): ActiveSession {
    val resumedSession = session.resume(loggedAt)
    val updatedExercises = resumedSession.exercises.toMutableList()
    val exercise = updatedExercises[exerciseIndex]
    val updatedSets = exercise.sets.toMutableList()
    val nextIndex = updatedSets.indexOfFirst { !it.completed }
    if (nextIndex == -1) return resumedSession
    val nextSet = updatedSets[nextIndex]
    updatedSets[nextIndex] = nextSet.copy(
        completed = true,
        reps = nextSet.resolvedRepsForLogging(),
        weight = nextSet.resolvedWeightForLogging(),
    )
    val reorderedSets = reorderActiveSessionSets(
        sets = updatedSets,
        prioritizedCompletedSetId = nextSet.id,
    )
    updatedExercises[exerciseIndex] = reconcileSessionExerciseCompletionState(
        exercises = resumedSession.exercises,
        exerciseIndex = exerciseIndex,
        updatedExercise = exercise.copy(sets = reorderedSets),
        promoteForLoggedSet = true,
    )
    return resumedSession.copy(exercises = updatedExercises)
}

internal fun logAllSessionSetsInActiveSession(
    session: ActiveSession,
    exerciseIndex: Int,
    loggedAt: Instant = Instant.now(),
): ActiveSession {
    val resumedSession = session.resume(loggedAt)
    val updatedExercises = resumedSession.exercises.toMutableList()
    val exercise = updatedExercises[exerciseIndex]
    val updatedSets = exercise.sets.map { set ->
        set.copy(
            completed = true,
            reps = set.resolvedRepsForLogging(),
            weight = set.resolvedWeightForLogging(),
        )
    }
    updatedExercises[exerciseIndex] = reconcileSessionExerciseCompletionState(
        exercises = resumedSession.exercises,
        exerciseIndex = exerciseIndex,
        updatedExercise = exercise.copy(sets = updatedSets),
        promoteForLoggedSet = updatedSets.any(SessionSet::completed),
    )
    return resumedSession.copy(exercises = updatedExercises)
}

private fun sanitizeActiveSessionCompletionState(session: ActiveSession): ActiveSession {
    val fruitIconSession = ensureSessionFruitIcons(session)
    val normalizedExercises = fruitIconSession.exercises.map { exercise ->
        when {
            !exercise.hasLoggedSetInActiveWorkout() && exercise.activitySequence != null && exercise.completionSequence != null ->
                exercise.copy(activitySequence = null, completionSequence = null)
            !exercise.hasLoggedSetInActiveWorkout() && exercise.activitySequence != null ->
                exercise.copy(activitySequence = null)
            !exercise.isFullyCompletedInActiveWorkout() && exercise.completionSequence != null ->
                exercise.copy(completionSequence = null)
            else -> exercise
        }
    }
    return if (normalizedExercises == fruitIconSession.exercises) {
        fruitIconSession
    } else {
        fruitIconSession.copy(exercises = normalizedExercises)
    }
}

private fun buildHistoricalAnalytics(
    history: List<HistoricalExerciseSet>,
    profile: UserProfile?,
    nowUtc: Instant,
): HistoricalAnalytics {
    if (history.isEmpty()) {
        return HistoricalAnalytics(
            muscles = emptyList(),
            movements = emptyList(),
        )
    }

    val muscles = linkedMapOf<String, AnalyticsMuscleAccumulator>()
    val patternExposure = linkedMapOf<String, Double>()
    val planeExposure = linkedMapOf<String, Double>()
    var unilateralExposure = 0.0
    var totalLateralityExposure = 0.0

    history.forEach { set ->
        val stimulus = historicalStimulus(set)
        if (stimulus <= 0.0) return@forEach
        historicalMuscleContributions(set).forEach { (muscle, roleWeight) ->
            val accumulator = muscles.getOrPut(muscle) { AnalyticsMuscleAccumulator() }
            val weightedStimulus = stimulus * roleWeight
            val ageDays = Duration.between(set.completedAtUtc, nowUtc).toHours().toDouble() / 24.0
            if (ageDays <= 7.0) accumulator.weeklyStimulus += weightedStimulus
            accumulator.decayedFatigue += weightedStimulus * historicalDecayFactor(ageDays, set.classification)
        }
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
        val laterality = normalizeAnalyticsValue(set.laterality)
        if (laterality.isNotBlank()) {
            totalLateralityExposure += stimulus
            if (laterality != "bilateral") {
                unilateralExposure += stimulus
            }
        }
    }

    val goalVolume = ANALYTICS_BASE_WEEKLY_STIMULUS[profile?.goal] ?: ANALYTICS_BASE_WEEKLY_STIMULUS.getValue("General Fitness")
    val experienceFactor = ANALYTICS_EXPERIENCE_VOLUME_FACTOR[profile?.experience] ?: 1.0
    val muscleInsights = muscles.entries
        .map { (muscle, accumulator) ->
            val weeklyTarget = goalVolume * experienceFactor * analyticsMuscleMultiplier(muscle)
            val mev = weeklyTarget * 0.6
            val mrv = weeklyTarget * 1.45
            val readiness = analyticsClamp(1.0 - (accumulator.decayedFatigue / max(mrv, 1.0)), 0.05, 1.0)
            val volumeNeed = analyticsClamp((weeklyTarget - accumulator.weeklyStimulus) / max(weeklyTarget, 1.0), 0.0, 1.0)
            val volumeStatus = when {
                accumulator.weeklyStimulus < mev -> "BELOW_MEV"
                accumulator.weeklyStimulus > mrv -> "ABOVE_MRV"
                else -> "WITHIN_MAV"
            }
            WorkoutMuscleInsight(
                muscle = muscle,
                weeklyStimulus = accumulator.weeklyStimulus,
                readinessScore = readiness,
                priorityScore = volumeNeed * readiness,
                volumeStatus = volumeStatus,
            )
        }
        .sortedWith(
            compareByDescending<WorkoutMuscleInsight> { it.priorityScore }
                .thenByDescending { it.weeklyStimulus },
        )

    val maxPatternExposure = max(patternExposure.values.maxOrNull() ?: 0.0, 1.0)
    val maxPlaneExposure = max(planeExposure.values.maxOrNull() ?: 0.0, 1.0)
    val patternInsights = ANALYTICS_PATTERN_WEIGHTS.map { (pattern, weight) ->
        val exposure = patternExposure[pattern] ?: 0.0
        WorkoutMovementInsight(
            kind = "pattern",
            label = pattern,
            currentExposure = exposure,
            needScore = analyticsClamp(((maxPatternExposure * weight) - exposure) / max(maxPatternExposure * weight, 1.0), 0.0, 1.0),
        )
    }
    val planeInsights = ANALYTICS_PLANE_WEIGHTS.map { (plane, weight) ->
        val exposure = planeExposure[plane] ?: 0.0
        WorkoutMovementInsight(
            kind = "plane",
            label = plane,
            currentExposure = exposure,
            needScore = analyticsClamp(((maxPlaneExposure * weight) - exposure) / max(maxPlaneExposure * weight, 1.0), 0.0, 1.0),
        )
    }
    val unilateralShare = if (totalLateralityExposure <= 0.0) 0.0 else unilateralExposure / totalLateralityExposure
    val lateralityInsight = WorkoutMovementInsight(
        kind = "laterality",
        label = "Unilateral balance",
        currentExposure = unilateralShare,
        needScore = analyticsClamp(
            (ANALYTICS_UNILATERAL_TARGET_SHARE - unilateralShare) / max(ANALYTICS_UNILATERAL_TARGET_SHARE, 0.01),
            0.0,
            1.0,
        ),
    )

    return HistoricalAnalytics(
        muscles = muscleInsights,
        movements = (patternInsights + planeInsights + lateralityInsight)
            .sortedWith(
                compareByDescending<WorkoutMovementInsight> { it.needScore }
                    .thenByDescending { it.currentExposure },
            ),
    )
}

private fun buildProjectedAnalytics(
    workout: WorkoutPlan,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
): ProjectedAnalytics {
    if (workout.exercises.isEmpty()) {
        return ProjectedAnalytics(
            muscles = emptyList(),
            movements = emptyList(),
        )
    }

    val muscleExposure = linkedMapOf<String, ProjectedContributionAccumulator>()
    val patternExposure = linkedMapOf<String, ProjectedContributionAccumulator>()
    val planeExposure = linkedMapOf<String, ProjectedContributionAccumulator>()
    var unilateralExposure = 0.0
    var totalLateralityExposure = 0.0

    workout.exercises.forEach { exercise ->
        val detail = exerciseDetailsById[exercise.exerciseId]
        projectedMuscleContributions(exercise, detail).forEach { (muscle, weight) ->
            val accumulator = muscleExposure.getOrPut(muscle) { ProjectedContributionAccumulator() }
            accumulator.exposure += exercise.sets * weight
            accumulator.exerciseIds += exercise.exerciseId
        }
        detail?.movementPatterns
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { pattern ->
                val accumulator = patternExposure.getOrPut(pattern) { ProjectedContributionAccumulator() }
                accumulator.exposure += exercise.sets.toDouble()
                accumulator.exerciseIds += exercise.exerciseId
            }
        detail?.planesOfMotion
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { plane ->
                val accumulator = planeExposure.getOrPut(plane) { ProjectedContributionAccumulator() }
                accumulator.exposure += exercise.sets.toDouble()
                accumulator.exerciseIds += exercise.exerciseId
            }
        val laterality = normalizeAnalyticsValue(detail?.laterality)
        if (laterality.isNotBlank()) {
            totalLateralityExposure += exercise.sets
            if (laterality != "bilateral") {
                unilateralExposure += exercise.sets
            }
        }
    }

    val totalMuscleExposure = muscleExposure.values.sumOf { it.exposure }.takeIf { it > 0.0 } ?: 1.0
    val projectedMuscles = muscleExposure.entries
        .map { (muscle, accumulator) ->
            ProjectedMuscleInsight(
                muscle = muscle,
                contribution = accumulator.exposure,
                share = accumulator.exposure / totalMuscleExposure,
                exerciseCount = accumulator.exerciseIds.size,
            )
        }
        .sortedWith(
            compareByDescending<ProjectedMuscleInsight> { it.contribution }
                .thenByDescending { it.exerciseCount },
        )

    val projectedMovements = buildList {
        val totalPatternExposure = patternExposure.values.sumOf { it.exposure }.takeIf { it > 0.0 } ?: 1.0
        patternExposure.entries
            .sortedByDescending { it.value.exposure }
            .forEach { (pattern, accumulator) ->
                add(
                    ProjectedMovementInsight(
                        kind = "pattern",
                        label = pattern,
                        exposure = accumulator.exposure,
                        share = accumulator.exposure / totalPatternExposure,
                        exerciseCount = accumulator.exerciseIds.size,
                    ),
                )
            }

        val totalPlaneExposure = planeExposure.values.sumOf { it.exposure }.takeIf { it > 0.0 } ?: 1.0
        planeExposure.entries
            .sortedByDescending { it.value.exposure }
            .forEach { (plane, accumulator) ->
                add(
                    ProjectedMovementInsight(
                        kind = "plane",
                        label = plane,
                        exposure = accumulator.exposure,
                        share = accumulator.exposure / totalPlaneExposure,
                        exerciseCount = accumulator.exerciseIds.size,
                    ),
                )
            }

        if (totalLateralityExposure > 0.0) {
            add(
                ProjectedMovementInsight(
                    kind = "laterality",
                    label = "Unilateral balance",
                    exposure = unilateralExposure / totalLateralityExposure,
                    share = unilateralExposure / totalLateralityExposure,
                    exerciseCount = workout.exercises.count {
                        normalizeAnalyticsValue(exerciseDetailsById[it.exerciseId]?.laterality).isNotBlank()
                    },
                ),
            )
        }
    }

    return ProjectedAnalytics(
        muscles = projectedMuscles,
        movements = projectedMovements,
    )
}

private fun projectedMuscleContributions(
    exercise: WorkoutExercise,
    detail: ExerciseDetail?,
): List<Pair<String, Double>> {
    return buildList {
        add(exercise.targetMuscleGroup to ANALYTICS_ROLE_WEIGHT_PRIME)
        detail?.primeMover
            ?.takeIf { !it.equals(exercise.targetMuscleGroup, ignoreCase = true) }
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it to ANALYTICS_ROLE_WEIGHT_PRIME) }
        detail?.secondaryMuscle
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it to ANALYTICS_ROLE_WEIGHT_SECONDARY) }
        detail?.tertiaryMuscle
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it to ANALYTICS_ROLE_WEIGHT_TERTIARY) }
    }.distinctBy { it.first.lowercase() }
}

private fun historicalMuscleContributions(set: HistoricalExerciseSet): List<Pair<String, Double>> {
    return buildList {
        set.targetMuscleGroup?.takeIf { it.isNotBlank() }?.let { add(it to ANALYTICS_ROLE_WEIGHT_PRIME) }
        set.secondaryMuscle?.takeIf { it.isNotBlank() }?.let { add(it to ANALYTICS_ROLE_WEIGHT_SECONDARY) }
        set.tertiaryMuscle?.takeIf { it.isNotBlank() }?.let { add(it to ANALYTICS_ROLE_WEIGHT_TERTIARY) }
    }
}

private fun historicalStimulus(set: HistoricalExerciseSet): Double {
    val reps = set.actualReps ?: parseRepRangeMidpoint(set.targetReps)
    val repWeight = reps?.let(::historicalRepStimulusWeight) ?: 0.8
    val effortWeight = historicalEffortWeight(set.lastSetRir)
    val completionWeight = if (set.completed) 1.0 else 0.35
    val compoundBonus = if (normalizeAnalyticsValue(set.classification).contains("compound")) 1.08 else 1.0
    return repWeight * effortWeight * completionWeight * compoundBonus
}

private fun historicalEffortWeight(rir: Int?): Double {
    return when {
        rir == null -> 0.85
        rir <= 2 -> 1.0
        rir == 3 -> 0.9
        rir == 4 -> 0.72
        else -> 0.45
    }
}

private fun historicalRepStimulusWeight(reps: Int): Double {
    return when (reps) {
        in 1..2 -> 0.65
        in 3..5 -> 0.95
        in 6..12 -> 1.0
        in 13..20 -> 0.88
        else -> 0.75
    }
}

private fun historicalDecayFactor(ageDays: Double, classification: String?): Double {
    val halfLife = if (normalizeAnalyticsValue(classification).contains("compound")) 2.5 else 1.8
    return 0.5.pow(ageDays / halfLife)
}

private fun parseRepRangeMidpoint(raw: String): Int? {
    val digits = raw
        .split("-", "to", "–")
        .mapNotNull { token -> token.trim().toIntOrNull() }
    return when (digits.size) {
        0 -> null
        1 -> digits.first()
        else -> (digits.first() + digits.last()) / 2
    }
}

private fun analyticsMuscleMultiplier(muscle: String): Double {
    return when (muscle) {
        "Quadriceps", "Glutes", "Hamstrings", "Back", "Chest" -> 1.15
        "Shoulders", "Trapezius" -> 1.0
        "Calves", "Biceps", "Triceps", "Forearms", "Abdominals", "Adductors", "Abductors" -> 0.8
        else -> 0.95
    }
}

private fun analyticsClamp(value: Double, minValue: Double, maxValue: Double): Double {
    return when {
        value < minValue -> minValue
        value > maxValue -> maxValue
        else -> value
    }
}

private fun normalizeAnalyticsValue(value: String?): String {
    return value
        ?.trim()
        ?.lowercase()
        ?.replace("_", " ")
        ?.replace("-", " ")
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()
}

internal fun canFinishActiveSession(exerciseCount: Int): Boolean = exerciseCount > 0

internal fun historyReusePlanTitle(originalTitle: String, mode: HistoryReuseMode): String = when (mode) {
    HistoryReuseMode.ExactCopy -> "$originalTitle Replay"
    HistoryReuseMode.RefreshPrescription -> "$originalTitle Refreshed"
}

internal fun historyReusePlanSubtitle(mode: HistoryReuseMode): String = when (mode) {
    HistoryReuseMode.ExactCopy -> "History reuse • Exact copy • Edit before starting"
    HistoryReuseMode.RefreshPrescription -> "History reuse • Refreshed prescription • Edit before starting"
}

internal fun historyReusePlanOrigin(mode: HistoryReuseMode): String = when (mode) {
    HistoryReuseMode.ExactCopy -> "history_reuse_exact"
    HistoryReuseMode.RefreshPrescription -> "history_reuse_refreshed"
}

internal fun historyReuseConfirmationMessage(workoutTitle: String, mode: HistoryReuseMode): String = when (mode) {
    HistoryReuseMode.ExactCopy -> "$workoutTitle added to My Plan as an exact copy."
    HistoryReuseMode.RefreshPrescription -> "$workoutTitle added to My Plan with refreshed prescriptions."
}

internal fun sessionSetFromHistoryReuseDraft(
    draft: WorkoutExerciseSetDraft,
    fallbackTargetReps: String,
): SessionSet {
    val targetReps = draft.targetReps.ifBlank { fallbackTargetReps }
    val recommendedReps = draft.recommendedReps ?: draft.reps
    return SessionSet(
        setNumber = draft.setNumber,
        targetReps = targetReps,
        recommendedReps = recommendedReps,
        recommendedWeight = dev.toastlabs.toastlift.data.formatRecommendedWeight(draft.recommendedWeight ?: draft.weight),
        reps = draft.reps?.toString()
            ?: recommendedReps?.toString()
            ?: targetReps.substringBefore('-').trim().ifBlank { targetReps.trim() },
        weight = dev.toastlabs.toastlift.data.formatRecommendedWeight(draft.weight ?: draft.recommendedWeight),
        recommendationSource = draft.recommendationSource,
        recommendationConfidence = draft.recommendationConfidence,
    )
}

internal fun firstSkippedExerciseFeedbackPrompt(session: ActiveSession): SkippedExerciseFeedbackPrompt? {
    val skippedExercise = session.exercises.firstOrNull { exercise -> exercise.sets.none(SessionSet::completed) } ?: return null
    return SkippedExerciseFeedbackPrompt(
        exerciseId = skippedExercise.exerciseId,
        exerciseName = skippedExercise.name,
        workoutTitle = session.title,
        workoutOrigin = session.origin,
        workoutFocusKey = session.focusKey,
        sessionStartedAtUtc = session.startedAtUtc,
    )
}

internal fun redistributedSetsForSkippedSession(plannedSets: Int): Int {
    return (plannedSets * 0.15).toInt().coerceIn(1, 2)
}

internal fun unskipSessionMenuLabel(session: PlannedSession): String {
    return "Unskip Week ${session.weekNumber} Day ${session.dayIndex + 1}"
}

internal fun programFocusLabel(focus: SessionFocus): String = when (SessionFocus.toFocusKey(focus)) {
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

internal fun isClosedProgramSession(status: SessionStatus): Boolean = when (status) {
    SessionStatus.COMPLETED,
    SessionStatus.SKIPPED,
    SessionStatus.MIGRATED,
    -> true
    SessionStatus.UPCOMING,
    SessionStatus.IN_PROGRESS,
    -> false
}

internal fun weekTypeLabel(weekType: dev.toastlabs.toastlift.data.WeekType?): String? = when (weekType) {
    dev.toastlabs.toastlift.data.WeekType.ACCUMULATION -> "Accumulation"
    dev.toastlabs.toastlift.data.WeekType.INTENSIFICATION -> "Intensification"
    dev.toastlabs.toastlift.data.WeekType.DELOAD -> "Deload"
    dev.toastlabs.toastlift.data.WeekType.TEST -> "Test"
    null -> null
}

internal fun buildUpcomingProgramSessionSummaries(
    sessions: List<PlannedSession>,
    exercisesBySessionId: Map<Long, List<PlannedSessionExercise>>,
    exerciseNameById: Map<Long, String>,
    limit: Int = UPCOMING_PROGRAM_SESSIONS_LIMIT,
): List<UpcomingProgramSessionSummary> {
    return sessions
        .sortedBy { it.sequenceNumber }
        .take(limit)
        .mapIndexed { index, session ->
            val exerciseNames = exercisesBySessionId[session.id]
                .orEmpty()
                .sortedBy { it.sortOrder }
                .mapNotNull { exerciseNameById[it.exerciseId] }
                .distinct()
            UpcomingProgramSessionSummary(
                sessionId = session.id,
                weekNumber = session.weekNumber,
                dayNumber = session.dayIndex + 1,
                sequenceOffset = index,
                focusLabel = programFocusLabel(session.focusKey),
                plannedSets = session.plannedSets,
                timeBudgetMinutes = session.timeBudgetMinutes,
                exerciseNames = exerciseNames,
            )
        }
}

internal fun buildProgramProgressSummary(
    program: TrainingProgram,
    weeks: List<dev.toastlabs.toastlift.data.PlannedWeek>,
    sessions: List<PlannedSession>,
    checkpoints: List<ProgramCheckpoint>,
    nextSession: PlannedSession?,
): ProgramProgressSummary {
    val orderedSessions = sessions.sortedBy { it.sequenceNumber }
    val highestWeekNumber = maxOf(
        program.totalWeeks,
        orderedSessions.maxOfOrNull { it.weekNumber } ?: 0,
        weeks.maxOfOrNull { it.weekNumber } ?: 0,
    ).coerceAtLeast(1)
    val sessionsByWeek = orderedSessions.groupBy { it.weekNumber }
    val weeksByNumber = weeks.associateBy { it.weekNumber }
    val checkpointsByWeek = checkpoints.groupBy { it.weekNumber }
    val currentWeekNumber = nextSession?.weekNumber
        ?: orderedSessions.firstOrNull { !isClosedProgramSession(it.status) }?.weekNumber
        ?: orderedSessions.maxOfOrNull { it.weekNumber }
        ?: highestWeekNumber

    val weekSummaries = (1..highestWeekNumber).map { weekNumber ->
        val weekSessions = sessionsByWeek[weekNumber].orEmpty()
        val checkpointStatus = checkpointsByWeek[weekNumber]
            ?.map { it.status }
            ?.let { statuses ->
                when {
                    statuses.all { it == dev.toastlabs.toastlift.data.CheckpointStatus.COMPLETED } -> dev.toastlabs.toastlift.data.CheckpointStatus.COMPLETED
                    statuses.any { it == dev.toastlabs.toastlift.data.CheckpointStatus.PENDING } -> dev.toastlabs.toastlift.data.CheckpointStatus.PENDING
                    else -> dev.toastlabs.toastlift.data.CheckpointStatus.SKIPPED
                }
            }
        val completedSessions = weekSessions.count { it.status == SessionStatus.COMPLETED }
        val skippedSessions = weekSessions.count { it.status == SessionStatus.SKIPPED }
        ProgramWeekProgressSummary(
            weekNumber = weekNumber,
            weekTypeLabel = weekTypeLabel(weeksByNumber[weekNumber]?.weekType),
            statusLabel = when {
                weekSessions.isEmpty() && weekNumber == currentWeekNumber -> "Current"
                weekSessions.isEmpty() && weekNumber < currentWeekNumber -> "Closed"
                weekSessions.isEmpty() -> "Queued"
                weekSessions.all { isClosedProgramSession(it.status) } -> "Done"
                weekNumber == currentWeekNumber -> "Current"
                weekNumber < currentWeekNumber -> "Closed"
                else -> "Queued"
            },
            isCurrentWeek = weekNumber == currentWeekNumber,
            completedSessions = completedSessions,
            skippedSessions = skippedSessions,
            totalSessions = weekSessions.size,
            sessionStatuses = weekSessions.map { it.status },
            checkpointStatus = checkpointStatus,
        )
    }

    return ProgramProgressSummary(
        currentWeekNumber = currentWeekNumber,
        totalWeeks = highestWeekNumber,
        completedSessions = orderedSessions.count { it.status == SessionStatus.COMPLETED },
        skippedSessions = orderedSessions.count { it.status == SessionStatus.SKIPPED },
        remainingSessions = orderedSessions.count { !isClosedProgramSession(it.status) },
        totalSessions = orderedSessions.size,
        completedWeeks = weekSummaries.count { it.totalSessions > 0 && it.sessionStatuses.all(::isClosedProgramSession) },
        completedCheckpoints = checkpoints.count { it.status == dev.toastlabs.toastlift.data.CheckpointStatus.COMPLETED },
        totalCheckpoints = checkpoints.size,
        weekSummaries = weekSummaries,
    )
}

private data class MuscleContribution(
    val bucketKey: String,
    val muscleKey: String,
    val weight: Double,
)

private data class WeeklyMuscleTargetAccumulator(
    val key: String,
    val label: String,
    val muscles: LinkedHashMap<String, MuscleAccumulator>,
)

private data class MuscleAccumulator(
    val key: String,
    val label: String,
    var targetSets: Double = 0.0,
    var completedSets: Double = 0.0,
)

private enum class PplWeeklyMuscleBucket(
    val key: String,
    val label: String,
    val focus: SessionFocus,
    val muscleOrder: List<Pair<String, String>>,
) {
    Push(
        key = "push",
        label = "Push Muscles",
        focus = SessionFocus.PUSH,
        muscleOrder = listOf(
            "chest" to "Chest",
            "shoulders" to "Shoulders",
            "triceps" to "Triceps",
        ),
    ),
    Pull(
        key = "pull",
        label = "Pull Muscles",
        focus = SessionFocus.PULL,
        muscleOrder = listOf(
            "back" to "Back",
            "biceps" to "Biceps",
        ),
    ),
    Legs(
        key = "legs",
        label = "Leg Muscles",
        focus = SessionFocus.LEGS,
        muscleOrder = listOf(
            "quadriceps" to "Quadriceps",
            "hamstrings" to "Hamstrings",
            "glutes" to "Glutes",
        ),
    ),
}

private val weeklyMuscleTargetDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val weeklyMuscleTargetBaseByGoal = mapOf(
    "Strength" to 8.5,
    "Hypertrophy" to 12.0,
    "Conditioning" to 7.0,
    "Fat Loss" to 9.0,
    "General Fitness" to 9.5,
)
private val weeklyMuscleTargetExperienceFactorByLevel = mapOf(
    "Beginner" to 0.85,
    "Intermediate" to 1.0,
    "Advanced" to 1.12,
)

internal fun buildWeeklyMuscleTargetSummary(
    profile: UserProfile,
    rows: List<WeeklyMuscleTargetWorkoutRow>,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
    now: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    historyLimit: Int = 4,
): WeeklyMuscleTargetSummary {
    val currentWeekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val weekStarts = (historyLimit - 1 downTo 0).map { offset ->
        currentWeekStart.minusWeeks(offset.toLong())
    }
    val rowsByWeekStart = rows.groupBy { row ->
        runCatching {
            Instant.parse(row.completedAtUtc)
                .atZone(zoneId)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        }.getOrElse { currentWeekStart }
    }
    val weekSummaries = weekStarts.mapIndexed { index, weekStart ->
        buildWeeklyMuscleTargetWeekSummary(
            profile = profile,
            weekIndex = index + 1,
            weekStart = weekStart,
            rows = rowsByWeekStart[weekStart].orEmpty(),
            exerciseDetailsById = exerciseDetailsById,
        )
    }

    val currentWeek = weekSummaries.last()
    val history = weekSummaries
        .map {
            WeeklyMuscleTargetHistorySummary(
                weekNumber = it.weekNumber,
                completionRatio = it.overallCompletionRatio,
                range = it.range,
            )
        }

    return currentWeek.copy(history = history)
}

private fun buildWeeklyMuscleTargetWeekSummary(
    profile: UserProfile,
    weekIndex: Int,
    weekStart: LocalDate,
    rows: List<WeeklyMuscleTargetWorkoutRow>,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
): WeeklyMuscleTargetSummary {
    val accumulators = PplWeeklyMuscleBucket.entries.associate { bucket ->
        bucket.key to WeeklyMuscleTargetAccumulator(
            key = bucket.key,
            label = bucket.label,
            muscles = LinkedHashMap<String, MuscleAccumulator>().apply {
                bucket.muscleOrder.forEach { (muscleKey, label) ->
                    put(
                        muscleKey,
                        MuscleAccumulator(
                            key = muscleKey,
                            label = label,
                            targetSets = weeklyTargetSetsForMuscle(profile = profile, muscleKey = muscleKey),
                        ),
                    )
                }
            },
        )
    }

    rows.forEach rowLoop@{ row ->
        if (row.completedSetCount <= 0) return@rowLoop
        val contributions = resolveMuscleContributions(detail = exerciseDetailsById[row.exerciseId])
        applyMuscleContributions(
            accumulators = accumulators,
            contributions = contributions,
            setCount = row.completedSetCount.toDouble(),
        )
    }

    val groupSummaries = PplWeeklyMuscleBucket.entries.mapNotNull { bucket ->
        val group = accumulators.getValue(bucket.key)
        val muscles = group.muscles.values
            .filter { it.targetSets > 0.0 || it.completedSets > 0.0 }
            .map {
                WeeklyMuscleTargetMuscleSummary(
                    label = it.label,
                    completedSets = it.completedSets,
                    targetSets = it.targetSets,
                )
            }
        val targetSets = muscles.sumOf { it.targetSets }
        val completedSets = muscles.sumOf { it.completedSets }
        if (targetSets <= 0.0 && completedSets <= 0.0) {
            null
        } else {
            WeeklyMuscleTargetGroupSummary(
                key = group.key,
                label = group.label,
                completedSets = completedSets,
                targetSets = targetSets,
                muscleSummaries = muscles,
            )
        }
    }

    val targetSets = groupSummaries.sumOf { it.targetSets }
    val completedSets = groupSummaries.sumOf { it.completedSets }
    return WeeklyMuscleTargetSummary(
        weekNumber = weekIndex,
        overallCompletionRatio = if (targetSets > 0.0) completedSets / targetSets else 0.0,
        completedSets = completedSets,
        targetSets = targetSets,
        range = WeeklyMuscleTargetRange(
            start = weekStart,
            end = weekStart.plusDays(6),
        ),
        groupSummaries = groupSummaries,
        history = emptyList(),
    )
}

private fun applyMuscleContributions(
    accumulators: Map<String, WeeklyMuscleTargetAccumulator>,
    contributions: List<MuscleContribution>,
    setCount: Double,
) {
    contributions.forEach { contribution ->
        val group = accumulators[contribution.bucketKey] ?: return@forEach
        val accumulator = group.muscles[contribution.muscleKey] ?: return@forEach
        accumulator.completedSets += setCount * contribution.weight
    }
}

private fun weeklyTargetSetsForMuscle(profile: UserProfile, muscleKey: String): Double {
    val base = weeklyMuscleTargetBaseByGoal[profile.goal] ?: weeklyMuscleTargetBaseByGoal.getValue("General Fitness")
    val experienceFactor = weeklyMuscleTargetExperienceFactorByLevel[profile.experience] ?: 1.0
    val multiplier = when (muscleKey) {
        "chest", "back", "quadriceps", "hamstrings", "glutes" -> 1.15
        "shoulders" -> 1.0
        "biceps", "triceps" -> 0.8
        else -> 0.95
    }
    return base * experienceFactor * multiplier
}

internal fun weeklyMuscleTargetRangeLabel(range: WeeklyMuscleTargetRange): String {
    return "${range.start.format(weeklyMuscleTargetDateFormatter)} - ${range.end.format(weeklyMuscleTargetDateFormatter)}"
}

private fun resolveMuscleContributions(
    detail: ExerciseDetail?,
): List<MuscleContribution> {
    val contributions = linkedMapOf<Pair<String, String>, Double>()

    fun addContribution(muscleKey: String?, weight: Double) {
        val resolvedKey = muscleKey
            ?.let(::mapMuscleToSlot)
            ?: return
        val key = resolvedKey.bucket.key to resolvedKey.key
        contributions[key] = maxOf(contributions[key] ?: 0.0, weight)
    }

    addContribution(detail?.summary?.targetMuscleGroup, 1.0)
    addContribution(detail?.primeMover, 1.0)
    addContribution(detail?.secondaryMuscle, 0.5)
    addContribution(detail?.tertiaryMuscle, 0.5)

    if (contributions.isNotEmpty()) {
        return contributions.entries.map { (key, weight) ->
            MuscleContribution(
                bucketKey = key.first,
                muscleKey = key.second,
                weight = weight,
            )
        }
    }

    val patterns = detail?.movementPatterns.orEmpty().map(::normalizeMuscleToken)
    return fallbackContributionsFromMovementPatterns(patterns)
}

private fun fallbackContributionsFromMovementPatterns(
    patterns: List<String>,
): List<MuscleContribution> = when {
    patterns.any { "overhead" in it || "vertical press" in it } -> listOf(
        MuscleContribution(bucketKey = "push", muscleKey = "shoulders", weight = 1.0),
        MuscleContribution(bucketKey = "push", muscleKey = "triceps", weight = 0.5),
    )
    patterns.any { "fly" in it || "horizontal push" in it || "push up" in it || "bench" in it } -> listOf(
        MuscleContribution(bucketKey = "push", muscleKey = "chest", weight = 1.0),
        MuscleContribution(bucketKey = "push", muscleKey = "triceps", weight = 0.5),
    )
    patterns.any { "curl" in it } -> listOf(
        MuscleContribution(bucketKey = "pull", muscleKey = "biceps", weight = 1.0),
    )
    patterns.any { "pull" in it || "row" in it } -> listOf(
        MuscleContribution(bucketKey = "pull", muscleKey = "back", weight = 1.0),
        MuscleContribution(bucketKey = "pull", muscleKey = "biceps", weight = 0.5),
    )
    patterns.any { "hinge" in it || "deadlift" in it || "good morning" in it } -> listOf(
        MuscleContribution(bucketKey = "legs", muscleKey = "hamstrings", weight = 1.0),
        MuscleContribution(bucketKey = "legs", muscleKey = "glutes", weight = 0.5),
    )
    patterns.any { "bridge" in it || "thrust" in it || "abduction" in it } -> listOf(
        MuscleContribution(bucketKey = "legs", muscleKey = "glutes", weight = 1.0),
        MuscleContribution(bucketKey = "legs", muscleKey = "hamstrings", weight = 0.5),
    )
    patterns.any { "squat" in it || "lunge" in it || "step up" in it } -> listOf(
        MuscleContribution(bucketKey = "legs", muscleKey = "quadriceps", weight = 1.0),
        MuscleContribution(bucketKey = "legs", muscleKey = "glutes", weight = 0.5),
    )
    else -> emptyList()
}

private data class WeeklyMuscleSlot(
    val key: String,
    val bucket: PplWeeklyMuscleBucket,
)

private fun mapMuscleToSlot(muscleName: String): WeeklyMuscleSlot? {
    val normalized = normalizeMuscleToken(muscleName)
    if (normalized.isBlank()) return null

    return when {
        normalized.contains("chest") || normalized.contains("pec") -> WeeklyMuscleSlot("chest", PplWeeklyMuscleBucket.Push)
        normalized.contains("shoulder") || normalized.contains("delt") -> WeeklyMuscleSlot("shoulders", PplWeeklyMuscleBucket.Push)
        normalized.contains("tricep") -> WeeklyMuscleSlot("triceps", PplWeeklyMuscleBucket.Push)
        normalized.contains("bicep") || normalized.contains("brachialis") || normalized.contains("brachioradialis") ->
            WeeklyMuscleSlot("biceps", PplWeeklyMuscleBucket.Pull)
        normalized.contains("back") || normalized.contains("lat") || normalized.contains("trap") ||
            normalized.contains("rear delt") || normalized.contains("rhomboid") ->
            WeeklyMuscleSlot("back", PplWeeklyMuscleBucket.Pull)
        normalized.contains("quad") || normalized.contains("vastus") || normalized.contains("rectus femoris") ->
            WeeklyMuscleSlot("quadriceps", PplWeeklyMuscleBucket.Legs)
        normalized.contains("hamstring") || normalized.contains("biceps femoris") ||
            normalized.contains("semitendinosus") || normalized.contains("semimembranosus") ->
            WeeklyMuscleSlot("hamstrings", PplWeeklyMuscleBucket.Legs)
        normalized.contains("glute") -> WeeklyMuscleSlot("glutes", PplWeeklyMuscleBucket.Legs)
        else -> null
    }
}

private fun normalizeMuscleToken(value: String?): String {
    return value
        .orEmpty()
        .trim()
        .lowercase()
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")
}

private fun muscleFamilyKey(value: String?): String? {
    val normalized = normalizeMuscleToken(value)
    if (normalized.isBlank()) return null
    return when {
        normalized.contains("lat") -> "lats"
        normalized.contains("rhomboid") -> "rhomboids"
        normalized.contains("rear delt") || normalized.contains("posterior delt") -> "rear_delts"
        normalized.contains("front delt") || normalized.contains("anterior delt") -> "front_delts"
        normalized.contains("side delt") || normalized.contains("lateral delt") -> "side_delts"
        normalized.contains("delt") || normalized.contains("shoulder") -> "shoulders"
        normalized.contains("trap") -> "traps"
        normalized.contains("bicep") || normalized.contains("brachialis") || normalized.contains("brachioradialis") -> "biceps"
        normalized.contains("tricep") -> "triceps"
        normalized.contains("pec") || normalized.contains("chest") -> "chest"
        normalized.contains("glute") -> "glutes"
        normalized.contains("hamstring") || normalized.contains("biceps femoris") || normalized.contains("semitendinosus") ||
            normalized.contains("semimembranosus") -> "hamstrings"
        normalized.contains("quad") || normalized.contains("vastus") || normalized.contains("rectus femoris") -> "quadriceps"
        normalized.contains("calf") || normalized.contains("gastrocnemius") || normalized.contains("soleus") -> "calves"
        normalized.contains("adductor") -> "adductors"
        normalized.contains("abductor") -> "abductors"
        normalized.contains("abdom") || normalized.contains("oblique") || normalized.contains("transverse abdominis") -> "core"
        normalized.contains("erector") || normalized.contains("lower back") -> "erector_spinae"
        normalized.contains("forearm") -> "forearms"
        else -> null
    }
}

private fun muscleMatchScore(targetMuscle: String, candidateMuscle: String?): Double {
    val normalizedCandidate = normalizeMuscleToken(candidateMuscle)
    if (normalizedCandidate.isBlank()) return 0.0
    if (normalizedCandidate == targetMuscle) return 1.0

    val targetFamily = muscleFamilyKey(targetMuscle)
    val candidateFamily = muscleFamilyKey(normalizedCandidate)
    if (targetFamily != null && targetFamily == candidateFamily) return 0.72

    return if (
        normalizedCandidate.contains(targetMuscle) ||
        targetMuscle.contains(normalizedCandidate)
    ) {
        0.4
    } else {
        0.0
    }
}

internal fun smartPickExerciseScore(
    exercise: SessionExercise,
    detail: ExerciseDetail?,
    normalizedTargetMuscle: String,
): Double {
    val roleScore = listOf(
        muscleMatchScore(normalizedTargetMuscle, detail?.primeMover) * 4.0,
        muscleMatchScore(normalizedTargetMuscle, exercise.targetMuscleGroup) * 3.1,
        muscleMatchScore(normalizedTargetMuscle, detail?.secondaryMuscle) * 1.9,
        muscleMatchScore(normalizedTargetMuscle, detail?.tertiaryMuscle) * 1.2,
    ).sum()
    if (roleScore <= 0.0) return 0.0

    val compoundBonus = when (exercise.equipment.lowercase()) {
        "barbell", "machine", "cable", "smith machine", "lever machine" -> 0.18
        else -> 0.0
    }
    return roleScore + compoundBonus
}

class ToastLiftViewModel(private val container: AppContainer) : ViewModel() {
    internal var uiState by mutableStateOf(AppUiState())
        private set

    private var customExerciseNameLookupJob: Job? = null
    private val exercisePrescriptionEngine = dev.toastlabs.toastlift.data.ExercisePrescriptionEngine()

    init {
        refreshAll()
        viewModelScope.launch(Dispatchers.IO) {
            val restoredCustomExercises = container.customExerciseRepository.restoreSnapshotIfNeeded()
            container.catalogRepository.ensureSynonymsSeeded()
            if (restoredCustomExercises) {
                refreshAll()
            }
            loadProgramState()
            refreshDailyCoachMessage(force = true)
        }
    }

    private fun loadExerciseDetailsById(exerciseIds: Collection<Long>): Map<Long, ExerciseDetail> {
        return exerciseIds
            .distinct()
            .mapNotNull { exerciseId ->
                container.catalogRepository.getExerciseDetail(exerciseId)?.let { detail -> exerciseId to detail }
            }
            .toMap()
    }

    private fun projectedAnalyticsFor(workout: WorkoutPlan?): ProjectedAnalytics {
        if (workout == null) {
            return ProjectedAnalytics(
                muscles = emptyList(),
                movements = emptyList(),
            )
        }
        val exerciseDetailsById = loadExerciseDetailsById(workout.exercises.map { it.exerciseId })
        return buildProjectedAnalytics(
            workout = workout,
            exerciseDetailsById = exerciseDetailsById,
        )
    }

    fun onAppOpened() {
        refreshDailyCoachMessage()
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val splitPrograms = container.userRepository.loadSplitPrograms()
            val locationModes = container.userRepository.loadLocationModes()
            val equipmentOptions = container.catalogRepository.loadEquipmentOptions()
            val equipmentByLocation = locationModes.associate { mode ->
                mode.id to container.userRepository.loadEquipmentForLocation(mode.id)
            }
            val recommendationBiasByExerciseId = container.catalogRepository.loadRecommendationBiases()
            val profile = container.userRepository.loadProfile()
            val smartPickerTargetOptions = container.catalogRepository.loadSmartPickerTargetOptions()
            val onboardingDraft = profile?.let {
                OnboardingDraft(
                    goal = it.goal,
                    experience = it.experience,
                    durationMinutes = it.durationMinutes,
                    weeklyFrequency = it.weeklyFrequency,
                    splitProgramId = it.splitProgramId,
                    units = it.units,
                    workoutStyle = it.workoutStyle,
                    smartPickerBodyFilter = it.smartPickerBodyFilter,
                    smartPickerTargetMuscle = it.smartPickerTargetMuscle,
                )
            } ?: OnboardingDraft()
            val programSetupDraft = syncProgramSetupDraftWithProfileDuration(
                currentDraft = uiState.programSetupDraft,
                profile = profile,
            )

            val query = uiState.libraryQuery
            val filters = uiState.libraryFilters
            val selectedHistoryDetail = uiState.selectedHistoryDetail
            val libraryPayload = container.catalogRepository.loadLibraryPayload(query, filters)
            val templates = container.workoutRepository.loadTemplates()
            val historyStrengthScore = container.workoutRepository.loadStrengthScoreSummary()
            val history = container.workoutRepository.loadHistory(historyStrengthScore)
            val recommendationHistory = container.generatorRepository.loadHistoricalSetsForRecommendations()
            val historyTopExercise = container.workoutRepository.loadTopPerformedExercise()
            val historyTopEquipment = container.workoutRepository.loadTopPerformedEquipment()
            val abandonedWorkout = container.workoutRepository.loadAbandonedWorkoutSummary()
            val historicalAnalytics = buildHistoricalAnalytics(
                history = recommendationHistory,
                profile = profile,
                nowUtc = Instant.now(),
            )
            val projectedAnalytics = projectedAnalyticsFor(uiState.generatedWorkout)
            val weeklyMuscleTargets = profile?.let { activeProfile ->
                val now = LocalDate.now()
                val zoneId = ZoneId.systemDefault()
                val historyStart = now
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                    .minusWeeks(3)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toString()
                val weeklyTargetRows = container.workoutRepository.loadWeeklyMuscleTargetRows(historyStart)
                val exerciseDetailsById = weeklyTargetRows
                    .map { it.exerciseId }
                    .distinct()
                    .mapNotNull { exerciseId ->
                        container.catalogRepository.getExerciseDetail(exerciseId)?.let { detail -> exerciseId to detail }
                    }
                    .toMap()
                buildWeeklyMuscleTargetSummary(
                    profile = activeProfile,
                    rows = weeklyTargetRows,
                    exerciseDetailsById = exerciseDetailsById,
                    now = now,
                    zoneId = zoneId,
                )
            }
            val tokenBalanceTrend = buildTokenBalanceTrend()
            val restoredActiveSession = uiState.activeSession?.let {
                null
            } ?: container.workoutRepository.loadActiveSession()?.let { persisted ->
                persisted.copy(session = sanitizeActiveSessionCompletionState(persisted.session))
            }
            val todayCompletionFeedbackVariant = if (profile != null) {
                container.experimentRepository.loadTodayCompletionFeedbackVariant()
            } else {
                TodayCompletionFeedbackVariant.DONE_TODAY_BADGE
            }
            val todayWorkoutCompletion = buildTodayWorkoutCompletionState(history = history)
            val todayReceiptRecap = uiState.debugReceiptLaunch
                ?.takeIf { it.surface.equals("today_receipt_recap", ignoreCase = true) }
                ?.let { buildDebugReceiptFixture(it).recap }
                ?: buildTodayReceiptRecapState(history = history)
            uiState = uiState.copy(
                isLoading = false,
                themePreference = profile?.themePreference ?: ThemePreference.Dark,
                onboardingDraft = onboardingDraft,
                profile = profile,
                smartPickerTargetOptions = smartPickerTargetOptions,
                splitPrograms = splitPrograms,
                locationModes = locationModes,
                equipmentOptions = equipmentOptions,
                equipmentByLocation = equipmentByLocation,
                recommendationBiasByExerciseId = recommendationBiasByExerciseId,
                libraryResults = libraryPayload.results,
                libraryFacets = libraryPayload.facets,
                templates = templates,
                history = history,
                historyTopExercise = historyTopExercise,
                historyTopEquipment = historyTopEquipment,
                historyStrengthScore = historyStrengthScore,
                historicalMuscleInsights = historicalAnalytics.muscles,
                historicalMovementInsights = historicalAnalytics.movements,
                projectedMuscleInsights = projectedAnalytics.muscles,
                projectedMovementInsights = projectedAnalytics.movements,
                tokenBalanceTrend = tokenBalanceTrend,
                weeklyMuscleTargets = weeklyMuscleTargets,
                abandonedWorkout = abandonedWorkout,
                todayCompletionFeedbackVariant = todayCompletionFeedbackVariant,
                todayWorkoutCompletion = todayWorkoutCompletion,
                todayReceiptRecap = todayReceiptRecap,
                activeSession = uiState.activeSession ?: restoredActiveSession?.session,
                activeSessionExerciseIndex = uiState.activeSessionExerciseIndex ?: restoredActiveSession?.selectedExerciseIndex,
                selectedHistoryDetail = if (selectedHistoryDetail != null) {
                    container.workoutRepository.loadHistoryDetail(selectedHistoryDetail.id)
                } else {
                    null
                },
                selectedExerciseHistory = uiState.selectedExerciseHistory?.let {
                    container.workoutRepository.loadExerciseHistory(
                        exerciseId = it.exerciseId,
                        fallbackName = it.exerciseName,
                        prOnly = it.isPrOnlyFilterEnabled,
                    )
                },
                programSetupDraft = programSetupDraft,
                debugReceiptLaunch = uiState.debugReceiptLaunch,
                message = null,
            )
        }
    }

    fun selectTab(tab: MainTab) {
        uiState = uiState.copy(selectedTab = tab, message = null)
    }

    private fun refreshDailyCoachMessage(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            if (!force && uiState.dailyCoachMessage?.generatedForDate == today) {
                return@launch
            }
            val coachMessage = container.dailyCoachService.generateForToday()
            uiState = uiState.copy(dailyCoachMessage = coachMessage)
        }
    }

    fun setThemePreference(themePreference: ThemePreference) {
        uiState = uiState.copy(
            themePreference = themePreference,
            profile = uiState.profile?.copy(themePreference = themePreference),
            message = null,
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.profile != null) {
                container.userRepository.saveThemePreference(themePreference)
            }
        }
    }

    fun updateOnboardingDraft(transform: (OnboardingDraft) -> OnboardingDraft) {
        val updatedDraft = transform(uiState.onboardingDraft)
        uiState = uiState.copy(
            onboardingDraft = updatedDraft.copy(
                durationMinutes = normalizeWorkoutDurationMinutes(updatedDraft.durationMinutes),
                weeklyFrequency = normalizeWeeklyFrequency(updatedDraft.weeklyFrequency),
            ),
        )
    }

    fun setGymMachineCableBiasEnabled(enabled: Boolean) {
        val profile = uiState.profile ?: return
        uiState = uiState.copy(
            profile = profile.copy(gymMachineCableBiasEnabled = enabled),
            message = if (enabled) {
                "Gym machine/cable bias enabled."
            } else {
                "Gym machine/cable bias disabled."
            },
        )
        viewModelScope.launch(Dispatchers.IO) {
            container.userRepository.saveGymMachineCableBiasEnabled(enabled)
        }
    }

    fun setHistoryWorkoutAbFlagsVisible(enabled: Boolean) {
        val profile = uiState.profile ?: return
        uiState = uiState.copy(
            profile = profile.copy(historyWorkoutAbFlagsVisible = enabled),
            message = if (enabled) {
                "History A/B flag details enabled."
            } else {
                "History A/B flag details hidden."
            },
        )
        viewModelScope.launch(Dispatchers.IO) {
            container.userRepository.saveHistoryWorkoutAbFlagsVisible(enabled)
        }
    }

    fun setDevPickNextExerciseEnabled(enabled: Boolean) {
        val profile = uiState.profile ?: return
        uiState = uiState.copy(
            profile = profile.copy(devPickNextExerciseEnabled = enabled),
            message = if (enabled) {
                "Pick Next Exercise dev toggle enabled."
            } else {
                "Pick Next Exercise dev toggle disabled."
            },
        )
        viewModelScope.launch(Dispatchers.IO) {
            container.userRepository.saveDevPickNextExerciseEnabled(enabled)
        }
    }

    fun setDevFruitExerciseIconsEnabled(enabled: Boolean) {
        val profile = uiState.profile ?: return
        uiState = uiState.copy(
            profile = profile.copy(devFruitExerciseIconsEnabled = enabled),
            message = if (enabled) {
                "Fruit icon workout badges enabled."
            } else {
                "Fruit icon workout badges disabled."
            },
        )
        viewModelScope.launch(Dispatchers.IO) {
            container.userRepository.saveDevFruitExerciseIconsEnabled(enabled)
        }
    }

    fun setDevExerciseDetailPersonalNoteVisible(enabled: Boolean) {
        val profile = uiState.profile ?: return
        uiState = uiState.copy(
            profile = profile.copy(devExerciseDetailPersonalNoteVisible = enabled),
            message = if (enabled) {
                "Exercise detail personal note card visible."
            } else {
                "Exercise detail personal note card hidden."
            },
        )
        viewModelScope.launch(Dispatchers.IO) {
            container.userRepository.saveDevExerciseDetailPersonalNoteVisible(enabled)
        }
    }

    fun setDevExerciseDetailLearnedPreferenceVisible(enabled: Boolean) {
        val profile = uiState.profile ?: return
        uiState = uiState.copy(
            profile = profile.copy(devExerciseDetailLearnedPreferenceVisible = enabled),
            message = if (enabled) {
                "Exercise detail learned preference card visible."
            } else {
                "Exercise detail learned preference card hidden."
            },
        )
        viewModelScope.launch(Dispatchers.IO) {
            container.userRepository.saveDevExerciseDetailLearnedPreferenceVisible(enabled)
        }
    }

    fun toggleEquipment(locationModeId: Long, equipment: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val before = container.userRepository.loadEquipmentForLocation(locationModeId)
            container.userRepository.toggleEquipment(locationModeId, equipment)
            val after = container.userRepository.loadEquipmentForLocation(locationModeId)
            val wasRemoved = equipment in before && equipment !in after

            val conflicts = if (wasRemoved && uiState.activeProgram != null) {
                val program = uiState.activeProgram!!
                val slots = container.programRepository.loadSlotsForProgram(program.id)
                slots.mapNotNull { slot ->
                    val detail = container.catalogRepository.exerciseById(slot.exerciseId) ?: return@mapNotNull null
                    val needsEquipment = detail.equipment.equals(equipment, ignoreCase = true) ||
                        detail.secondaryEquipment?.equals(equipment, ignoreCase = true) == true
                    if (needsEquipment) {
                        EquipmentConflictItem(
                            exerciseId = slot.exerciseId,
                            exerciseName = detail.name,
                            role = slot.role,
                            missingEquipment = equipment,
                        )
                    } else null
                }
            } else emptyList()

            uiState = uiState.copy(
                equipmentByLocation = uiState.equipmentByLocation + (locationModeId to after),
                equipmentConflicts = conflicts,
                message = if (conflicts.isNotEmpty()) {
                    "${conflicts.size} program exercise(s) need ${equipment.lowercase()}."
                } else {
                    "Updated ${equipment.lowercase()} for ${locationName(locationModeId)}."
                },
            )
        }
    }

    fun saveProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val activeLocationId = uiState.profile?.activeLocationModeId ?: 1L
            val previousProfile = container.userRepository.loadProfile()
            container.userRepository.saveProfile(uiState.onboardingDraft, activeLocationId)
            val updatedProfile = container.userRepository.loadProfile()
            val activeProgram = container.programRepository.loadActiveProgram()
            if (
                activeProgram != null &&
                previousProfile != null &&
                updatedProfile != null &&
                previousProfile.durationMinutes != updatedProfile.durationMinutes
            ) {
                container.programRepository.updateUpcomingSessionTimeBudgets(
                    programId = activeProgram.id,
                    previousTimeBudgetMinutes = previousProfile.durationMinutes,
                    newTimeBudgetMinutes = updatedProfile.durationMinutes,
                )
            }
            refreshAll()
            refreshDailyCoachMessage(force = true)
            uiState = uiState.copy(message = PROFILE_SAVED_MESSAGE)
        }
    }

    fun setActiveLocationMode(locationModeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            container.userRepository.setActiveLocation(locationModeId)
            val profile = container.userRepository.loadProfile()
            uiState = uiState.copy(
                profile = profile,
                message = "Active mode set to ${locationName(locationModeId)}.",
            )
        }
    }

    fun deleteAllPersonalData() {
        viewModelScope.launch(Dispatchers.IO) {
            container.userRepository.deleteAllPersonalData()
            container.customExerciseRepository.deleteAllCustomExercisesAndSnapshot()
            container.workoutRepository.clearActiveSession()
            container.workoutRepository.clearAbandonedWorkout()
            val splitPrograms = container.userRepository.loadSplitPrograms()
            val locationModes = container.userRepository.loadLocationModes()
            val equipmentOptions = container.catalogRepository.loadEquipmentOptions()
            val smartPickerTargetOptions = container.catalogRepository.loadSmartPickerTargetOptions()
            val libraryPayload = container.catalogRepository.loadLibraryPayload("", LibraryFilters())
            uiState = AppUiState(
                isLoading = false,
                themePreference = ThemePreference.Dark,
                smartPickerTargetOptions = smartPickerTargetOptions,
                splitPrograms = splitPrograms,
                locationModes = locationModes,
                equipmentOptions = equipmentOptions,
                libraryResults = libraryPayload.results,
                libraryFacets = libraryPayload.facets,
                activeSession = null,
                activeSessionExerciseIndex = null,
                activeSessionAddExerciseVisible = false,
                customExerciseDraft = null,
                pendingPersonalDataExport = null,
                pendingWorkoutShare = null,
                message = "All personal data deleted from this device.",
            )
        }
    }

    fun preparePersonalDataExport() {
        viewModelScope.launch(Dispatchers.IO) {
            val payload = container.userRepository.exportPersonalDataJson(
                customExercises = container.customExerciseRepository.exportCustomExercisesJson(),
            )
            uiState = uiState.copy(
                pendingPersonalDataExport = payload,
                message = null,
            )
        }
    }

    fun cancelPendingPersonalDataExport() {
        uiState = uiState.copy(pendingPersonalDataExport = null)
    }

    fun completePendingPersonalDataExport() {
        uiState = uiState.copy(
            pendingPersonalDataExport = null,
            message = "Personal data exported as JSON.",
        )
    }

    fun failPendingPersonalDataExport() {
        uiState = uiState.copy(
            pendingPersonalDataExport = null,
            message = "Could not export personal data.",
        )
    }

    fun prepareHistoryWorkoutShare(workoutId: Long, format: HistoryShareFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            val detail = container.workoutRepository.loadHistoryWorkoutShareDetail(workoutId)
            if (detail == null) {
                uiState = uiState.copy(message = "Could not load workout to share.")
                return@launch
            }
            uiState = uiState.copy(
                pendingWorkoutShare = detail.toPendingWorkoutShare(format),
                message = null,
            )
        }
    }

    fun completePendingWorkoutShare() {
        uiState = uiState.copy(pendingWorkoutShare = null)
    }

    fun failPendingWorkoutShare() {
        uiState = uiState.copy(
            pendingWorkoutShare = null,
            message = "Could not open the share sheet.",
        )
    }

    fun updateLibraryQuery(query: String) {
        uiState = uiState.copy(libraryQuery = query)
        refreshLibrary()
    }

    fun toggleLibrarySearch() {
        val shouldShow = !uiState.librarySearchVisible
        if (shouldShow) {
            uiState = uiState.copy(librarySearchVisible = true)
            return
        }

        if (uiState.libraryQuery.isBlank()) {
            uiState = uiState.copy(librarySearchVisible = false)
            return
        }

        uiState = uiState.copy(
            librarySearchVisible = false,
            libraryQuery = "",
        )
        refreshLibrary()
    }

    fun toggleLibraryFavoritesOnly() {
        uiState = uiState.copy(
            libraryFilters = uiState.libraryFilters.copy(
                favoritesOnly = !uiState.libraryFilters.favoritesOnly,
            ),
        )
        refreshLibrary()
    }

    fun toggleLibraryEquipmentFilter(equipment: String) {
        val updated = uiState.libraryFilters.equipment.toMutableSet().apply {
            if (!add(equipment)) remove(equipment)
        }
        uiState = uiState.copy(libraryFilters = uiState.libraryFilters.copy(equipment = updated))
        refreshLibrary()
    }

    fun toggleLibraryTargetMuscleFilter(targetMuscle: String) {
        val updated = uiState.libraryFilters.targetMuscles.toMutableSet().apply {
            if (!add(targetMuscle)) remove(targetMuscle)
        }
        uiState = uiState.copy(libraryFilters = uiState.libraryFilters.copy(targetMuscles = updated))
        refreshLibrary()
    }

    fun toggleLibraryPrimeMoverFilter(primeMover: String) {
        val updated = uiState.libraryFilters.primeMovers.toMutableSet().apply {
            if (!add(primeMover)) remove(primeMover)
        }
        uiState = uiState.copy(libraryFilters = uiState.libraryFilters.copy(primeMovers = updated))
        refreshLibrary()
    }

    fun toggleLibraryRecommendationBiasFilter(bias: RecommendationBias) {
        if (bias == RecommendationBias.Neutral) return
        val updated = uiState.libraryFilters.recommendationBiases.toMutableSet().apply {
            if (!add(bias)) remove(bias)
        }
        uiState = uiState.copy(
            libraryFilters = uiState.libraryFilters.copy(recommendationBiases = updated),
        )
        refreshLibrary()
    }

    fun toggleLibraryLoggedHistoryFilter() {
        uiState = uiState.copy(
            libraryFilters = uiState.libraryFilters.copy(
                hasLoggedHistoryOnly = !uiState.libraryFilters.hasLoggedHistoryOnly,
            ),
        )
        refreshLibrary()
    }

    fun clearLibraryFilters() {
        uiState = uiState.copy(libraryFilters = LibraryFilters())
        refreshLibrary()
    }

    private fun refreshLibrary() {
        val query = uiState.libraryQuery
        val filters = uiState.libraryFilters
        viewModelScope.launch(Dispatchers.IO) {
            val payload = container.catalogRepository.loadLibraryPayload(
                query = query,
                filters = filters,
            )
            uiState = uiState.copy(
                libraryResults = payload.results,
                libraryFacets = payload.facets,
            )
        }
    }

    fun showExerciseDetail(exerciseId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            uiState = uiState.copy(selectedExerciseDetail = container.catalogRepository.getExerciseDetail(exerciseId))
        }
    }

    fun dismissExerciseDetail() {
        uiState = uiState.copy(
            selectedExerciseDetail = null,
            generatingExerciseDescriptionId = null,
        )
    }

    fun generateExerciseDescription(exerciseId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingDetail = uiState.selectedExerciseDetail
            val detail = if (existingDetail?.summary?.id == exerciseId) {
                existingDetail
            } else {
                container.catalogRepository.getExerciseDetail(exerciseId)
            } ?: run {
                uiState = uiState.copy(message = "Could not load the exercise description context.")
                return@launch
            }

            uiState = uiState.copy(generatingExerciseDescriptionId = exerciseId)
            runCatching {
                val generated = container.exerciseDescriptionService.generate(exerciseId)
                container.catalogRepository.saveGeneratedExerciseDescription(
                    exerciseId = exerciseId,
                    description = generated.description,
                    generationModel = generated.generationModel,
                    generationPromptVersion = generated.generationPromptVersion,
                )
                generated
            }.onSuccess {
                val refreshedDetail = container.catalogRepository.getExerciseDetail(exerciseId)
                uiState = uiState.copy(
                    selectedExerciseDetail = if (uiState.selectedExerciseDetail?.summary?.id == exerciseId) {
                        refreshedDetail
                    } else {
                        uiState.selectedExerciseDetail
                    },
                    generatingExerciseDescriptionId = null,
                    message = if (detail.generatedDescription == null) {
                        "Generated description for ${detail.summary.name}."
                    } else {
                        "Replaced generated description for ${detail.summary.name}."
                    },
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    generatingExerciseDescriptionId = null,
                    message = error.message ?: "Could not generate a description for ${detail.summary.name}.",
                )
            }
        }
    }

    fun openExerciseHistory(exerciseId: Long, exerciseName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val detail = container.workoutRepository.loadExerciseHistory(
                exerciseId = exerciseId,
                fallbackName = exerciseName,
                prOnly = false,
            )
            uiState = uiState.copy(selectedExerciseHistory = detail)
        }
    }

    fun setExerciseHistoryPrOnly(prOnly: Boolean) {
        val currentDetail = uiState.selectedExerciseHistory ?: return
        if (currentDetail.isPrOnlyFilterEnabled == prOnly) return
        viewModelScope.launch(Dispatchers.IO) {
            val detail = container.workoutRepository.loadExerciseHistory(
                exerciseId = currentDetail.exerciseId,
                fallbackName = currentDetail.exerciseName,
                prOnly = prOnly,
            )
            uiState = uiState.copy(selectedExerciseHistory = detail)
        }
    }

    fun closeExerciseHistory() {
        uiState = uiState.copy(selectedExerciseHistory = null)
    }

    fun openExerciseVideos(exerciseId: Long, exerciseName: String) {
        val query = "${exerciseName.trim()} exercise tutorial"
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        uiState = uiState.copy(
            selectedExerciseVideos = ExerciseVideoLinks(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                youtubeAppUri = "vnd.youtube://results?search_query=$encoded",
                youtubeWebUrl = "https://www.youtube.com/results?search_query=$encoded",
                tiktokAppUri = "snssdk1233://search/result?keyword=$encoded",
                tiktokWebUrl = "https://www.tiktok.com/search?q=$encoded",
            ),
        )
    }

    fun closeExerciseVideos() {
        uiState = uiState.copy(selectedExerciseVideos = null)
    }

    fun toggleFavorite(exercise: ExerciseSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            container.catalogRepository.toggleFavorite(exercise.id, !exercise.favorite)
            refreshAll()
            if (uiState.selectedExerciseDetail?.summary?.id == exercise.id) {
                uiState = uiState.copy(
                    selectedExerciseDetail = container.catalogRepository.getExerciseDetail(exercise.id),
                )
            }
        }
    }

    fun saveExerciseNote(exercise: ExerciseSummary, noteInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedNote = normalizeExerciseNote(noteInput)
            container.catalogRepository.setExerciseNote(exercise.id, normalizedNote)
            if (uiState.selectedExerciseDetail?.summary?.id == exercise.id) {
                uiState = uiState.copy(
                    selectedExerciseDetail = container.catalogRepository.getExerciseDetail(exercise.id),
                )
            }
            uiState = uiState.copy(
                message = if (normalizedNote == null) {
                    "${exercise.name} note cleared."
                } else {
                    "Saved note for ${exercise.name}."
                },
            )
        }
    }

    fun saveExerciseVideoLink(
        exercise: ExerciseSummary,
        linkId: Long?,
        labelInput: String,
        urlInput: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedLabel = normalizeExerciseVideoLinkLabel(labelInput)
            val normalizedUrl = normalizeExerciseVideoLinkUrl(urlInput)
            if (normalizedLabel == null || normalizedUrl == null) {
                uiState = uiState.copy(message = "Enter a label and a valid YouTube or TikTok URL.")
                return@launch
            }
            container.catalogRepository.saveExerciseVideoLink(
                exerciseId = exercise.id,
                linkId = linkId,
                labelInput = normalizedLabel,
                urlInput = normalizedUrl,
            )
            if (uiState.selectedExerciseDetail?.summary?.id == exercise.id) {
                uiState = uiState.copy(
                    selectedExerciseDetail = container.catalogRepository.getExerciseDetail(exercise.id),
                )
            }
            uiState = uiState.copy(
                message = if (linkId == null) {
                    "Saved video link for ${exercise.name}."
                } else {
                    "Updated video link for ${exercise.name}."
                },
            )
        }
    }

    fun deleteExerciseVideoLink(exercise: ExerciseSummary, linkId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            container.catalogRepository.deleteExerciseVideoLink(exercise.id, linkId)
            if (uiState.selectedExerciseDetail?.summary?.id == exercise.id) {
                uiState = uiState.copy(
                    selectedExerciseDetail = container.catalogRepository.getExerciseDetail(exercise.id),
                )
            }
            uiState = uiState.copy(message = "Removed video link for ${exercise.name}.")
        }
    }

    fun setRecommendationBias(exerciseId: Long, selectedBias: RecommendationBias, currentBias: RecommendationBias) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextBias = if (selectedBias == currentBias) RecommendationBias.Neutral else selectedBias
            container.catalogRepository.setRecommendationBias(exerciseId, nextBias)
            refreshLibrary()
            refreshRecommendationBiasState(exerciseId)
        }
    }

    fun resetRecommendationPreferenceScore(exercise: ExerciseSummary) {
        if (exercise.preferenceScoreDelta == 0.0) return
        viewModelScope.launch(Dispatchers.IO) {
            container.catalogRepository.resetRecommendationPreferenceScore(exercise.id)
            refreshLibrary()
            refreshRecommendationBiasState(exercise.id)
            if (uiState.selectedExerciseDetail?.summary?.id == exercise.id) {
                uiState = uiState.copy(
                    selectedExerciseDetail = container.catalogRepository.getExerciseDetail(exercise.id),
                )
            }
            uiState = uiState.copy(message = "${exercise.name} recommendation score reset.")
        }
    }

    fun addExerciseToBuilder(exercise: ExerciseSummary) {
        val updated = uiState.manualWorkoutItems + exercise.toWorkoutExercise(
            goal = uiState.onboardingDraft.goal,
            rationale = "Added manually from the exercise library.",
        )
        uiState = uiState.copy(
            manualWorkoutItems = updated,
            selectedTab = MainTab.Generate,
            message = "${exercise.name} added to the manual builder.",
        )
    }

    fun addExercisesToBuilder(exercises: List<ExerciseSummary>) {
        if (exercises.isEmpty()) return
        val existingIds = uiState.manualWorkoutItems.map { it.exerciseId }.toSet()
        val additions = exercises
            .distinctBy { it.id }
            .filterNot { it.id in existingIds }
            .map {
                it.toWorkoutExercise(
                    goal = uiState.onboardingDraft.goal,
                    rationale = "Added manually from the exercise library.",
                )
            }
        if (additions.isEmpty()) {
            uiState = uiState.copy(message = "Those exercises are already in the manual builder.")
            return
        }
        uiState = uiState.copy(
            manualWorkoutItems = uiState.manualWorkoutItems + additions,
            selectedTab = MainTab.Generate,
            message = "Added ${additions.size} exercise${if (additions.size == 1) "" else "s"} to the manual builder.",
        )
    }

    fun removeBuilderExercise(exerciseId: Long) {
        uiState = uiState.copy(
            manualWorkoutItems = uiState.manualWorkoutItems.filterNot { it.exerciseId == exerciseId },
        )
    }

    fun addExerciseToGeneratedWorkout(exercise: ExerciseSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            val workout = uiState.generatedWorkout
            if (workout == null) {
                uiState = uiState.copy(message = "Generate a workout before adding to My Plan.")
                return@launch
            }
            if (workout.exercises.any { it.exerciseId == exercise.id }) {
                uiState = uiState.copy(message = "${exercise.name} is already in My Plan.")
                return@launch
            }
            val updatedExercises = workout.exercises + exercise.toWorkoutExercise(
                goal = uiState.onboardingDraft.goal,
                rationale = "Added to My Plan from the exercise library.",
            )
            val updatedWorkout = workout.withExercises(updatedExercises, uiState.profile?.durationMinutes)
            val projectedAnalytics = projectedAnalyticsFor(updatedWorkout)
            uiState = uiState.copy(
                generatedWorkout = updatedWorkout,
                projectedMuscleInsights = projectedAnalytics.muscles,
                projectedMovementInsights = projectedAnalytics.movements,
                selectedTab = MainTab.Generate,
                message = "${exercise.name} added to My Plan.",
            )
            if (workout.origin == "generated") {
                container.workoutRepository.recordGeneratedWorkoutFeedbackSignal(
                    workout = workout,
                    exercise = updatedExercises.last(),
                    signalType = WorkoutFeedbackSignalType.GENERATED_PLAN_MANUAL_ADD,
                )
                refreshRecommendationBiasState(exercise.id)
            }
        }
    }

    fun addExercisesToGeneratedWorkout(exercises: List<ExerciseSummary>) {
        if (exercises.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val workout = uiState.generatedWorkout
            if (workout == null) {
                uiState = uiState.copy(message = "Generate a workout before adding to My Plan.")
                return@launch
            }
            val existingIds = workout.exercises.map { it.exerciseId }.toSet()
            val additions = exercises
                .distinctBy { it.id }
                .filterNot { it.id in existingIds }
                .map {
                    it.toWorkoutExercise(
                        goal = uiState.onboardingDraft.goal,
                        rationale = "Added to My Plan from the exercise library.",
                    )
                }
            if (additions.isEmpty()) {
                uiState = uiState.copy(message = "Those exercises are already in My Plan.")
                return@launch
            }
            val updatedWorkout = workout.withExercises(
                exercises = workout.exercises + additions,
                fallbackMinutes = uiState.profile?.durationMinutes,
            )
            val projectedAnalytics = projectedAnalyticsFor(updatedWorkout)
            uiState = uiState.copy(
                generatedWorkout = updatedWorkout,
                projectedMuscleInsights = projectedAnalytics.muscles,
                projectedMovementInsights = projectedAnalytics.movements,
                selectedTab = MainTab.Generate,
                message = "Added ${additions.size} exercise${if (additions.size == 1) "" else "s"} to My Plan.",
            )
            if (workout.origin == "generated") {
                additions.forEach { addedExercise ->
                    container.workoutRepository.recordGeneratedWorkoutFeedbackSignal(
                        workout = workout,
                        exercise = addedExercise,
                        signalType = WorkoutFeedbackSignalType.GENERATED_PLAN_MANUAL_ADD,
                    )
                }
                refreshRecommendationBiasState()
            }
        }
    }

    fun removeGeneratedExercise(exerciseId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val workout = uiState.generatedWorkout ?: return@launch
            val removedExercise = workout.exercises.firstOrNull { it.exerciseId == exerciseId } ?: return@launch
            val updatedWorkout = workout.withExercises(
                exercises = workout.exercises.filterNot { it.exerciseId == exerciseId },
                fallbackMinutes = uiState.profile?.durationMinutes,
            )
            val projectedAnalytics = projectedAnalyticsFor(updatedWorkout)
            uiState = uiState.copy(
                generatedWorkout = updatedWorkout,
                projectedMuscleInsights = projectedAnalytics.muscles,
                projectedMovementInsights = projectedAnalytics.movements,
                message = "${removedExercise.name} removed from My Plan.",
            )
            if (workout.origin == "generated") {
                container.workoutRepository.recordGeneratedWorkoutFeedbackSignal(
                    workout = workout,
                    exercise = removedExercise,
                    signalType = WorkoutFeedbackSignalType.GENERATED_PLAN_REMOVE,
                )
                refreshRecommendationBiasState(exerciseId)
            }
        }
    }

    fun updateManualWorkoutName(value: String) {
        uiState = uiState.copy(manualWorkoutName = value)
    }

    fun saveManualTemplate() {
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.manualWorkoutItems.isEmpty()) {
                uiState = uiState.copy(message = "Add exercises before saving a template.")
                return@launch
            }
            val editingTemplateId = uiState.editingTemplateId
            if (editingTemplateId != null) {
                container.workoutRepository.updateTemplate(
                    templateId = editingTemplateId,
                    name = uiState.manualWorkoutName,
                    origin = uiState.editingTemplateOrigin ?: "manual",
                    exercises = uiState.manualWorkoutItems,
                )
            } else {
                container.workoutRepository.saveTemplate(
                    name = uiState.manualWorkoutName,
                    origin = "manual",
                    exercises = uiState.manualWorkoutItems,
                )
            }
            refreshAll()
            uiState = uiState.copy(
                message = if (editingTemplateId != null) "Template updated." else "Manual template saved.",
            )
        }
    }

    fun saveGeneratedTemplate(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val workout = uiState.generatedWorkout ?: return@launch
            container.workoutRepository.saveTemplate(
                name = name,
                origin = "generated",
                exercises = workout.exercises,
            )
            refreshAll()
            uiState = uiState.copy(message = "Generated workout saved as a template.")
        }
    }

    fun generateWorkout() {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = container.userRepository.loadProfile()
            if (profile == null) {
                uiState = uiState.copy(message = "Finish onboarding before generating.")
                return@launch
            }
            val splitProgram = uiState.splitPrograms.firstOrNull { it.id == profile.splitProgramId } ?: uiState.splitPrograms.first()
            val requestContext = workoutGenerationRequestContext(uiState.generatedWorkout)
            val workout = container.generatorRepository.generateWorkout(
                profile = profile,
                splitProgram = splitProgram,
                locationModes = uiState.locationModes,
                previousExerciseIds = requestContext.previousExerciseIds,
                variationSeed = System.currentTimeMillis(),
                requestedFocus = requestContext.requestedFocus,
            )
            val projectedAnalytics = projectedAnalyticsFor(workout)
            uiState = uiState.copy(
                generatedWorkout = workout,
                projectedMuscleInsights = projectedAnalytics.muscles,
                projectedMovementInsights = projectedAnalytics.movements,
                selectedTab = MainTab.Generate,
                message = "Generated a ${workout.title.lowercase()} workout.",
            )
        }
    }

    fun swapGeneratedWorkoutToFocus(targetFocus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentWorkout = uiState.generatedWorkout ?: return@launch
            val profile = container.userRepository.loadProfile() ?: return@launch
            val splitProgram = uiState.splitPrograms.firstOrNull { it.id == profile.splitProgramId } ?: return@launch
            if (currentWorkout.focusKey == targetFocus) return@launch
            val workout = container.generatorRepository.generateWorkout(
                profile = profile,
                splitProgram = splitProgram,
                locationModes = uiState.locationModes,
                previousExerciseIds = currentWorkout.exercises.map { it.exerciseId }.toSet(),
                variationSeed = System.currentTimeMillis(),
                requestedFocus = targetFocus,
            )
            val projectedAnalytics = projectedAnalyticsFor(workout)
            uiState = uiState.copy(
                generatedWorkout = workout,
                projectedMuscleInsights = projectedAnalytics.muscles,
                projectedMovementInsights = projectedAnalytics.movements,
                selectedTab = MainTab.Generate,
                message = "Swapped to ${workout.title.lowercase()}.",
            )
        }
    }

    fun startGeneratedWorkout() {
        val workout = uiState.generatedWorkout ?: return
        if (workout.exercises.isEmpty()) {
            uiState = uiState.copy(message = "Add at least one exercise to My Plan first.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val session = buildActiveSession(workout)
            container.workoutRepository.clearAbandonedWorkout()
            container.workoutRepository.saveActiveSession(session, null)
            refreshAll()
            uiState = uiState.copy(
                activeSession = session,
                activeSessionExerciseIndex = null,
                activeSessionAddExerciseVisible = false,
                skippedExerciseFeedbackPrompt = null,
                customExerciseDraft = null,
                message = null,
            )
        }
    }

    fun startManualWorkout() {
        if (uiState.manualWorkoutItems.isEmpty()) {
            uiState = uiState.copy(message = "Add exercises to the builder first.")
            return
        }
        val profile = uiState.profile ?: return
        val workout = WorkoutPlan(
            title = uiState.manualWorkoutName.ifBlank { "Manual Workout" },
            subtitle = "Manual builder • ${profile.durationMinutes} min",
            locationModeId = profile.activeLocationModeId,
            estimatedMinutes = profile.durationMinutes,
            origin = "manual",
            exercises = uiState.manualWorkoutItems,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val session = buildActiveSession(workout)
            container.workoutRepository.clearAbandonedWorkout()
            container.workoutRepository.saveActiveSession(session, null)
            refreshAll()
            uiState = uiState.copy(
                activeSession = session,
                activeSessionExerciseIndex = null,
                activeSessionAddExerciseVisible = false,
                skippedExerciseFeedbackPrompt = null,
                customExerciseDraft = null,
                message = null,
            )
        }
    }

    fun startTemplate(templateId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val workout = container.workoutRepository.loadTemplate(templateId)
            if (workout == null) {
                uiState = uiState.copy(message = "Could not load that template.")
                return@launch
            }
            container.workoutRepository.clearAbandonedWorkout()
            val session = buildActiveSession(workout)
            container.workoutRepository.saveActiveSession(session, null)
            refreshAll()
            uiState = uiState.copy(
                activeSession = session,
                activeSessionExerciseIndex = null,
                activeSessionAddExerciseVisible = false,
                skippedExerciseFeedbackPrompt = null,
                customExerciseDraft = null,
                message = null,
            )
        }
    }

    fun editTemplate(templateId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val template = container.workoutRepository.loadEditableTemplate(templateId)
            if (template == null) {
                uiState = uiState.copy(message = "Could not load that template for editing.")
                return@launch
            }
            uiState = uiState.copy(
                todayEditingTemplateId = template.id,
                todayEditingTemplateOrigin = template.origin,
                todayEditingTemplateName = template.name,
                todayEditingTemplateItems = template.exercises,
                message = "Editing ${template.name}.",
            )
        }
    }

    fun closeTodayTemplateEditor() {
        uiState = uiState.copy(
            todayEditingTemplateId = null,
            todayEditingTemplateOrigin = null,
            todayEditingTemplateName = "",
            todayEditingTemplateItems = emptyList(),
            message = null,
        )
    }

    fun updateTodayTemplateName(value: String) {
        uiState = uiState.copy(todayEditingTemplateName = value)
    }

    fun removeTodayTemplateExercise(exerciseId: Long) {
        uiState = uiState.copy(
            todayEditingTemplateItems = uiState.todayEditingTemplateItems.filterNot { it.exerciseId == exerciseId },
        )
    }

    fun addExercisesToTodayTemplate(exercises: List<ExerciseSummary>) {
        if (exercises.isEmpty()) return
        val existingIds = uiState.todayEditingTemplateItems.map { it.exerciseId }.toSet()
        val additions = exercises
            .distinctBy { it.id }
            .filterNot { it.id in existingIds }
            .map {
                it.toWorkoutExercise(
                    goal = uiState.onboardingDraft.goal,
                    rationale = "Added manually from the exercise library.",
                )
            }
        if (additions.isEmpty()) {
            uiState = uiState.copy(message = "Those exercises are already in this template.")
            return
        }
        uiState = uiState.copy(
            todayEditingTemplateItems = uiState.todayEditingTemplateItems + additions,
            message = "Added ${additions.size} exercise${if (additions.size == 1) "" else "s"} to the template.",
        )
    }

    fun saveTodayTemplate() {
        viewModelScope.launch(Dispatchers.IO) {
            val editingTemplateId = uiState.todayEditingTemplateId
            if (editingTemplateId == null) return@launch
            if (uiState.todayEditingTemplateItems.isEmpty()) {
                uiState = uiState.copy(message = "Add exercises before updating this template.")
                return@launch
            }
            container.workoutRepository.updateTemplate(
                templateId = editingTemplateId,
                name = uiState.todayEditingTemplateName,
                origin = uiState.todayEditingTemplateOrigin ?: "manual",
                exercises = uiState.todayEditingTemplateItems,
            )
            refreshAll()
            uiState = uiState.copy(
                todayEditingTemplateId = null,
                todayEditingTemplateOrigin = null,
                todayEditingTemplateName = "",
                todayEditingTemplateItems = emptyList(),
                message = "Template updated.",
            )
        }
    }

    fun startTodayTemplateWorkout() {
        uiState.todayEditingTemplateId ?: return
        val profile = uiState.profile ?: return
        if (uiState.todayEditingTemplateItems.isEmpty()) {
            uiState = uiState.copy(message = "Add exercises before starting this template.")
            return
        }
        val workout = WorkoutPlan(
            title = uiState.todayEditingTemplateName.ifBlank { "Template Workout" },
            subtitle = "Saved template • ${profile.durationMinutes} min",
            locationModeId = profile.activeLocationModeId,
            estimatedMinutes = profile.durationMinutes,
            origin = uiState.todayEditingTemplateOrigin ?: "manual",
            exercises = uiState.todayEditingTemplateItems,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val session = buildActiveSession(workout)
            container.workoutRepository.clearAbandonedWorkout()
            container.workoutRepository.saveActiveSession(session, null)
            refreshAll()
            uiState = uiState.copy(
                todayEditingTemplateId = null,
                todayEditingTemplateOrigin = null,
                todayEditingTemplateName = "",
                todayEditingTemplateItems = emptyList(),
                activeSession = session,
                activeSessionExerciseIndex = null,
                activeSessionAddExerciseVisible = false,
                skippedExerciseFeedbackPrompt = null,
                customExerciseDraft = null,
                message = "Starting ${workout.title}.",
            )
        }
    }

    fun renameTemplate(templateId: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                uiState = uiState.copy(message = "Template name cannot be blank.")
                return@launch
            }
            container.workoutRepository.renameTemplate(templateId, trimmedName)
            if (uiState.editingTemplateId == templateId) {
                uiState = uiState.copy(manualWorkoutName = trimmedName)
            }
            if (uiState.todayEditingTemplateId == templateId) {
                uiState = uiState.copy(todayEditingTemplateName = trimmedName)
            }
            refreshAll()
            uiState = uiState.copy(message = "Template renamed.")
        }
    }

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            container.workoutRepository.deleteTemplate(templateId)
            val clearBuilderEdit = uiState.editingTemplateId == templateId
            val clearTodayEdit = uiState.todayEditingTemplateId == templateId
            refreshAll()
            uiState = uiState.copy(
                editingTemplateId = if (clearBuilderEdit) null else uiState.editingTemplateId,
                editingTemplateOrigin = if (clearBuilderEdit) null else uiState.editingTemplateOrigin,
                manualWorkoutName = if (clearBuilderEdit) "Custom Workout" else uiState.manualWorkoutName,
                manualWorkoutItems = if (clearBuilderEdit) emptyList() else uiState.manualWorkoutItems,
                todayEditingTemplateId = if (clearTodayEdit) null else uiState.todayEditingTemplateId,
                todayEditingTemplateOrigin = if (clearTodayEdit) null else uiState.todayEditingTemplateOrigin,
                todayEditingTemplateName = if (clearTodayEdit) "" else uiState.todayEditingTemplateName,
                todayEditingTemplateItems = if (clearTodayEdit) emptyList() else uiState.todayEditingTemplateItems,
                message = "Template deleted.",
            )
        }
    }

    fun openSessionExercise(exerciseIndex: Int) {
        uiState = uiState.copy(activeSessionExerciseIndex = exerciseIndex)
        persistActiveSessionState(selectedExerciseIndex = exerciseIndex)
    }

    fun toggleSessionPause() {
        val session = uiState.activeSession ?: return
        val updatedSession = if (session.isPaused) {
            session.resume()
        } else {
            session.pause()
        }
        uiState = uiState.copy(activeSession = updatedSession)
        persistActiveSessionState(session = updatedSession)
    }

    fun closeSessionExercise() {
        uiState = uiState.copy(activeSessionExerciseIndex = null)
        persistActiveSessionState(selectedExerciseIndex = null)
    }

    fun pickNextSessionExercise() {
        val session = uiState.activeSession ?: return
        val smartTargetMuscle = uiState.profile?.smartPickerTargetMuscle
        viewModelScope.launch(Dispatchers.IO) {
            val untouchedExerciseIds = session.exercises
                .filter(SessionExercise::isNotStartedInActiveWorkout)
                .map(SessionExercise::exerciseId)
            val exerciseDetailsById = loadExerciseDetailsById(untouchedExerciseIds)
            val pickedExerciseIndex = pickNextSessionExerciseIndex(
                session = session,
                smartTargetMuscle = smartTargetMuscle,
                exerciseDetailsById = exerciseDetailsById,
            ) ?: run {
                uiState = uiState.copy(message = "No untouched exercises left. Pick an exercise already in progress.")
                return@launch
            }
            val pickedExercise = session.exercises[pickedExerciseIndex]
            uiState = uiState.copy(
                activeSessionExerciseIndex = pickedExerciseIndex,
                message = "Next up: ${pickedExercise.name}.",
            )
            persistActiveSessionState(selectedExerciseIndex = pickedExerciseIndex)
        }
    }

    fun updateSessionValue(exerciseIndex: Int, setIndex: Int, value: String, isWeight: Boolean) {
        val session = uiState.activeSession ?: return
        val updatedExercises = session.exercises.toMutableList()
        val exercise = updatedExercises[exerciseIndex]
        val updatedSets = exercise.sets.toMutableList()
        val targetSet = updatedSets[setIndex]
        val propagationSource = if (isWeight) targetSet.weight else targetSet.reps
        updatedSets[setIndex] = if (isWeight) {
            targetSet.copy(weight = value)
        } else {
            targetSet.copy(reps = value)
        }
        for (index in (setIndex + 1) until updatedSets.size) {
            val set = updatedSets[index]
            if (set.completed) continue
            updatedSets[index] = if (isWeight) {
                if (set.weight == propagationSource) {
                    set.copy(weight = value)
                } else {
                    set
                }
            } else {
                if (set.reps == propagationSource) {
                    set.copy(reps = value)
                } else {
                    set
                }
            }
        }
        updatedExercises[exerciseIndex] = exercise.copy(sets = updatedSets)
        val updatedSession = session.copy(exercises = updatedExercises)
        uiState = uiState.copy(activeSession = updatedSession)
        persistActiveSessionState(session = updatedSession)
    }

    fun toggleSessionSetComplete(exerciseIndex: Int, setIndex: Int) {
        val session = uiState.activeSession ?: return
        val updatedExercises = session.exercises.toMutableList()
        val exercise = updatedExercises[exerciseIndex]
        val updatedSets = exercise.sets.toMutableList()
        val targetSet = updatedSets[setIndex]
        updatedSets[setIndex] = targetSet.copy(completed = !targetSet.completed)
        val prioritizedSetId = updatedSets[setIndex].id.takeIf { updatedSets[setIndex].completed }
        val reorderedSets = reorderActiveSessionSets(
            sets = updatedSets,
            prioritizedCompletedSetId = prioritizedSetId,
        )
        updatedExercises[exerciseIndex] = exercise.copy(
            sets = reorderedSets,
            lastSetRepsInReserve = exercise.lastSetRepsInReserve.takeIf { reorderedSets.all { it.completed } },
        )
        updatedExercises[exerciseIndex] = reconcileSessionExerciseCompletionState(
            exercises = session.exercises,
            exerciseIndex = exerciseIndex,
            updatedExercise = updatedExercises[exerciseIndex],
            promoteForLoggedSet = prioritizedSetId != null,
        )
        val updatedSession = session.copy(exercises = updatedExercises)
        uiState = uiState.copy(activeSession = updatedSession)
        persistActiveSessionState(session = updatedSession)
    }

    fun addSessionSet(exerciseIndex: Int) {
        val session = uiState.activeSession ?: return
        val updatedExercises = session.exercises.toMutableList()
        val exercise = updatedExercises[exerciseIndex]
        val newSetNumber = exercise.sets.size + 1
        val template = exercise.sets.lastOrNull()
        val updatedSets = exercise.sets + SessionSet(
            setNumber = newSetNumber,
            targetReps = template?.targetReps ?: "8-12",
            recommendedReps = template?.recommendedReps,
            recommendedWeight = template?.recommendedWeight.orEmpty(),
            weight = template?.weight.orEmpty(),
            reps = template?.reps.orEmpty(),
            recommendationSource = template?.recommendationSource ?: dev.toastlabs.toastlift.data.RecommendationSource.NONE,
            recommendationConfidence = template?.recommendationConfidence,
            completed = false,
        )
        updatedExercises[exerciseIndex] = exercise.copy(
            sets = updatedSets,
            lastSetRepsInReserve = null,
        )
        updatedExercises[exerciseIndex] = reconcileSessionExerciseCompletionState(
            exercises = session.exercises,
            exerciseIndex = exerciseIndex,
            updatedExercise = updatedExercises[exerciseIndex],
        )
        val updatedSession = session.copy(exercises = updatedExercises)
        uiState = uiState.copy(activeSession = updatedSession)
        persistActiveSessionState(session = updatedSession)
    }

    fun deleteSessionSet(exerciseIndex: Int, setIndex: Int) {
        val session = uiState.activeSession ?: return
        val updatedExercises = session.exercises.toMutableList()
        val exercise = updatedExercises[exerciseIndex]
        val updatedSets = exercise.sets
            .filterIndexed { index, _ -> index != setIndex }
            .mapIndexed { index, set -> set.copy(setNumber = index + 1) }
        updatedExercises[exerciseIndex] = exercise.copy(
            sets = updatedSets,
            lastSetRepsInReserve = exercise.lastSetRepsInReserve.takeIf { updatedSets.all { it.completed } },
        )
        updatedExercises[exerciseIndex] = reconcileSessionExerciseCompletionState(
            exercises = session.exercises,
            exerciseIndex = exerciseIndex,
            updatedExercise = updatedExercises[exerciseIndex],
        )
        val updatedSession = session.copy(exercises = updatedExercises)
        uiState = uiState.copy(activeSession = updatedSession)
        persistActiveSessionState(session = updatedSession)
    }

    fun removeSessionExercise(exerciseIndex: Int) {
        val session = uiState.activeSession ?: return
        val removedExercise = session.exercises.getOrNull(exerciseIndex) ?: return
        val updatedExercises = session.exercises.filterIndexed { index, _ -> index != exerciseIndex }
        val updatedSelection = activeSessionSelectionAfterExerciseRemoval(
            selectedExerciseIndex = uiState.activeSessionExerciseIndex,
            removedExerciseIndex = exerciseIndex,
            remainingExerciseCount = updatedExercises.size,
        )
        val updatedSession = session.copy(exercises = updatedExercises)
        uiState = uiState.copy(
            activeSession = updatedSession,
            activeSessionExerciseIndex = updatedSelection,
            message = if (updatedExercises.isEmpty()) {
                "${removedExercise.name} deleted. Add another exercise or abandon the workout."
            } else {
                "${removedExercise.name} deleted from this workout."
            },
        )
        persistActiveSessionState(session = updatedSession, selectedExerciseIndex = updatedSelection)
        if (session.origin == "generated") {
            viewModelScope.launch(Dispatchers.IO) {
                container.workoutRepository.recordActiveSessionFeedbackSignal(
                    session = session,
                    exercise = removedExercise,
                    signalType = WorkoutFeedbackSignalType.ACTIVE_SESSION_REMOVE,
                )
                refreshRecommendationBiasState(removedExercise.exerciseId)
            }
        }
    }

    fun logNextSessionSet(exerciseIndex: Int) {
        val session = uiState.activeSession ?: return
        val updatedSession = logNextSessionSetInActiveSession(session, exerciseIndex)
        uiState = uiState.copy(activeSession = updatedSession)
        persistActiveSessionState(session = updatedSession)
    }

    fun logAllSessionSets(exerciseIndex: Int) {
        val session = uiState.activeSession ?: return
        val updatedSession = logAllSessionSetsInActiveSession(session, exerciseIndex)
        uiState = uiState.copy(activeSession = updatedSession)
        persistActiveSessionState(session = updatedSession)
    }

    fun updateSessionExerciseRepsInReserve(exerciseIndex: Int, repsInReserve: Int) {
        val session = uiState.activeSession ?: return
        val updatedExercises = session.exercises.toMutableList()
        val exercise = updatedExercises[exerciseIndex]
        updatedExercises[exerciseIndex] = exercise.copy(lastSetRepsInReserve = repsInReserve)
        val updatedSession = session.copy(exercises = updatedExercises)
        uiState = uiState.copy(activeSession = updatedSession)
        persistActiveSessionState(session = updatedSession)
    }

    fun finishSessionExercise(exerciseIndex: Int) {
        val session = uiState.activeSession ?: return
        val exercise = session.exercises.getOrNull(exerciseIndex) ?: return
        if (exercise.sets.isEmpty() || exercise.sets.any { !it.completed } || exercise.lastSetRepsInReserve == null) return
        uiState = uiState.copy(activeSessionExerciseIndex = null)
        persistActiveSessionState(selectedExerciseIndex = null)
    }

    fun openCustomExerciseFlow() {
        openCustomExerciseFlow(
            destination = CustomExerciseDestination.ActiveSession,
            seedName = uiState.libraryQuery,
        )
    }

    fun openCustomExerciseForBuilder() {
        openCustomExerciseFlow(
            destination = CustomExerciseDestination.ManualBuilder,
            seedName = uiState.libraryQuery,
        )
    }

    fun openCustomExerciseForGeneratedWorkout() {
        openCustomExerciseFlow(
            destination = CustomExerciseDestination.GeneratedWorkout,
            seedName = uiState.libraryQuery,
        )
    }

    fun openCustomExerciseForTodayTemplate() {
        openCustomExerciseFlow(
            destination = CustomExerciseDestination.TodayTemplate,
            seedName = uiState.libraryQuery,
        )
    }

    fun openActiveSessionAddExercise() {
        uiState = uiState.copy(
            activeSessionAddExerciseVisible = true,
            activeSessionAddExerciseMode = ActiveSessionAddExerciseMode.Choice,
            activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(),
            customExerciseDraft = null,
            customExerciseDestination = null,
            pendingAddExercisePickerSelection = null,
            librarySearchVisible = false,
            libraryQuery = "",
            libraryFilters = LibraryFilters(),
            message = null,
        )
        refreshLibrary()
    }

    fun closeActiveSessionAddExercise() {
        customExerciseNameLookupJob?.cancel()
        uiState = uiState.copy(
            activeSessionAddExerciseVisible = false,
            activeSessionAddExerciseMode = ActiveSessionAddExerciseMode.Choice,
            activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(),
            customExerciseDraft = null,
            customExerciseDestination = null,
            pendingAddExercisePickerSelection = null,
            librarySearchVisible = false,
            libraryQuery = "",
            libraryFilters = LibraryFilters(),
        )
        refreshLibrary()
    }

    fun openManualActiveSessionExercisePicker() {
        uiState = uiState.copy(
            activeSessionAddExerciseMode = ActiveSessionAddExerciseMode.Manual,
            activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(),
            customExerciseDraft = null,
            customExerciseDestination = null,
            pendingAddExercisePickerSelection = null,
            librarySearchVisible = false,
            libraryQuery = "",
            libraryFilters = LibraryFilters(),
            message = null,
        )
        refreshLibrary()
    }

    fun openGeneratedActiveSessionExercisePicker() {
        uiState = uiState.copy(
            activeSessionAddExerciseMode = ActiveSessionAddExerciseMode.Generated,
            activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(isLoading = true),
            customExerciseDraft = null,
            customExerciseDestination = null,
            pendingAddExercisePickerSelection = null,
            message = null,
        )
        generateActiveSessionExerciseSuggestion()
    }

    fun pickAgainGeneratedActiveSessionExercise() {
        val current = uiState.activeSessionGeneratedExercise
        if (current.isLoading) return
        uiState = uiState.copy(
            activeSessionAddExerciseMode = ActiveSessionAddExerciseMode.Generated,
            activeSessionGeneratedExercise = current.copy(
                exercise = null,
                isLoading = true,
                errorMessage = null,
            ),
            message = null,
        )
        generateActiveSessionExerciseSuggestion()
    }

    private fun openCustomExerciseFlow(
        destination: CustomExerciseDestination,
        seedName: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val draft = container.customExerciseRepository.newDraft(seedName)
            uiState = uiState.copy(
                customExerciseDraft = draft,
                customExerciseDestination = destination,
                pendingAddExercisePickerSelection = null,
                message = null,
            )
        }
    }

    fun closeCustomExerciseFlow() {
        customExerciseNameLookupJob?.cancel()
        uiState = uiState.copy(
            customExerciseDraft = null,
            customExerciseDestination = null,
        )
    }

    fun clearPendingAddExercisePickerSelection() {
        uiState = uiState.copy(pendingAddExercisePickerSelection = null)
    }

    fun updateCustomExerciseDraft(draft: CustomExerciseDraft) {
        uiState = uiState.copy(customExerciseDraft = draft.copy(errorMessage = null))
    }

    fun updateCustomExerciseName(name: String) {
        val current = uiState.customExerciseDraft ?: return
        val trimmed = name.trimStart()
        uiState = uiState.copy(
            customExerciseDraft = current.copy(
                name = trimmed,
                errorMessage = null,
            ),
        )
        customExerciseNameLookupJob?.cancel()
        customExerciseNameLookupJob = viewModelScope.launch(Dispatchers.IO) {
            delay(120)
            val matches = container.customExerciseRepository.loadExistingMatches(trimmed)
            val latest = uiState.customExerciseDraft ?: return@launch
            if (latest.name != trimmed) return@launch
            uiState = uiState.copy(
                customExerciseDraft = latest.copy(
                    existingMatches = matches,
                    errorMessage = null,
                ),
            )
        }
    }

    fun generateCustomExerciseDetails() {
        val current = uiState.customExerciseDraft ?: return
        if (current.name.isBlank()) {
            uiState = uiState.copy(customExerciseDraft = current.copy(errorMessage = "Enter an exercise name first."))
            return
        }
        uiState = uiState.copy(customExerciseDraft = current.copy(isGenerating = true, errorMessage = null))
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.customExerciseRepository.generateDetails(current)
            }.onSuccess { updated ->
                uiState = uiState.copy(customExerciseDraft = updated.copy(isGenerating = false))
            }.onFailure { error ->
                uiState = uiState.copy(
                    customExerciseDraft = current.copy(
                        isGenerating = false,
                        errorMessage = error.message ?: "Could not generate exercise details.",
                    ),
                )
            }
        }
    }

    fun addExistingExerciseToActiveSession(exercise: ExerciseSummary) {
        appendExerciseToActiveSession(exercise)
        uiState = uiState.copy(
            activeSessionAddExerciseVisible = false,
            librarySearchVisible = false,
            libraryQuery = "",
            libraryFilters = LibraryFilters(),
            customExerciseDraft = null,
            message = "${exercise.name} added to the workout.",
        )
        refreshLibrary()
    }

    fun addExercisesToActiveSession(exercises: List<ExerciseSummary>) {
        if (exercises.isEmpty()) return
        val additions = exercises.distinctBy { it.id }
        appendExercisesToActiveSession(additions)
        uiState = uiState.copy(
            activeSessionAddExerciseVisible = false,
            activeSessionAddExerciseMode = ActiveSessionAddExerciseMode.Choice,
            activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(),
            librarySearchVisible = false,
            libraryQuery = "",
            libraryFilters = LibraryFilters(),
            customExerciseDraft = null,
            message = "Added ${additions.size} exercise${if (additions.size == 1) "" else "s"} to the workout.",
        )
        refreshLibrary()
    }

    fun addGeneratedExerciseToActiveSession() {
        val generatedExercise = uiState.activeSessionGeneratedExercise.exercise ?: return
        appendWorkoutExercisesToActiveSession(listOf(generatedExercise))
        uiState = uiState.copy(
            activeSessionAddExerciseVisible = false,
            activeSessionAddExerciseMode = ActiveSessionAddExerciseMode.Choice,
            activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(),
            librarySearchVisible = false,
            libraryQuery = "",
            libraryFilters = LibraryFilters(),
            customExerciseDraft = null,
            customExerciseDestination = null,
            pendingAddExercisePickerSelection = null,
            message = "${generatedExercise.name} added to the workout.",
        )
        refreshLibrary()
    }

    fun saveCustomExercise() {
        val current = uiState.customExerciseDraft ?: return
        uiState = uiState.copy(customExerciseDraft = current.copy(isSaving = true, errorMessage = null))
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.customExerciseRepository.saveCustomExercise(current)
            }.onSuccess { saved ->
                queueExerciseForPickerSelection(saved)
            }.onFailure { error ->
                uiState = uiState.copy(
                    customExerciseDraft = current.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Could not save custom exercise.",
                    ),
                )
            }
        }
    }

    fun completeSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = uiState.activeSession ?: return@launch
            if (!canFinishActiveSession(session.exercises.size)) {
                uiState = uiState.copy(message = "Add at least one exercise before finishing the workout.")
                return@launch
            }
            val skippedExercisePrompt = firstSkippedExerciseFeedbackPrompt(session)
            val splitName = uiState.profile
                ?.let { profile -> uiState.splitPrograms.firstOrNull { it.id == profile.splitProgramId }?.name }
            val profile = uiState.profile
            val activeProgram = uiState.activeProgram
            val plannedSession = uiState.nextPlannedSession
            val beforeHistory = uiState.history
            val beforeStrengthScore = uiState.historyStrengthScore
            val beforeWeeklyTargets = uiState.weeklyMuscleTargets
            val beforeProgramSessions = activeProgram?.let { container.programRepository.loadSessionsForProgram(it.id) }.orEmpty()
            val beforeTokenTrend = buildTokenBalanceTrend()
            val beforeWeekCredit = if (plannedSession != null) {
                weekCreditForProgramSessions(beforeProgramSessions, plannedSession.weekNumber)
            } else {
                null
            }
            val receiptVariant = container.experimentRepository.loadCompletionReceiptVariant()
            val workoutId = container.workoutRepository.saveCompletedWorkout(
                session = session,
                abFlags = buildCompletionReceiptAbFlags(
                    variant = receiptVariant,
                    legacyTodayVariant = uiState.todayCompletionFeedbackVariant,
                ),
            )
            container.workoutRepository.clearActiveSession()
            if (session.focusKey != null && splitName != null) {
                container.userRepository.saveNextFocus(
                    container.generatorRepository.nextFocusAfter(splitName, session.focusKey),
                )
            }
            if (activeProgram != null && plannedSession != null && workoutId != null) {
                val accounting = computeProgramCompletionAccounting(session, plannedSession)
                container.programRepository.updateSessionCompletionAccounting(
                    sessionId = plannedSession.id,
                    actualWorkoutId = workoutId,
                    completionRatio = accounting.completionRatio,
                    completionCredit = accounting.completionCredit,
                    completionTruth = accounting.truth ?: ProgramCompletionTruth.MINIMUM_DOSE,
                )
                container.programRepository.logEvent(
                    dev.toastlabs.toastlift.data.ProgramEvent(
                        programId = activeProgram.id,
                        eventType = dev.toastlabs.toastlift.data.ProgramEventType.SESSION_REALIZED,
                        payloadJson = """{"sessionId":${plannedSession.id},"workoutId":$workoutId}""",
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                completeProgramIfFinished(activeProgram.id)
            }
            val currentDetail = workoutId?.let { container.workoutRepository.loadHistoryDetail(it) }
            val afterStrengthScore = container.workoutRepository.loadStrengthScoreSummary()
            val afterHistory = container.workoutRepository.loadHistory(afterStrengthScore)
            val todayReceiptRecap = buildTodayReceiptRecapState(afterHistory)
            val afterWeeklyTargets = profile?.let { loadWeeklyMuscleTargetSummary(it) }
            val afterProgramSessions = activeProgram?.let { container.programRepository.loadSessionsForProgram(it.id) }.orEmpty()
            val afterTokenTrend = buildTokenBalanceTrend()
            val afterNextPlannedSession = activeProgram?.let { container.programRepository.currentPosition(it.id) }
            val afterWeekCredit = if (plannedSession != null) {
                weekCreditForProgramSessions(afterProgramSessions, plannedSession.weekNumber)
            } else {
                null
            }
            val referenceCandidate = workoutId?.let {
                selectReceiptReferenceCandidate(
                    session = session,
                    candidates = container.workoutRepository.loadRecentReceiptReferenceCandidates(it),
                )
            }
            val referenceDetail = referenceCandidate?.let { candidate ->
                container.workoutRepository.loadHistoryDetail(candidate.workoutId)
            }
            val programExerciseIds = activeProgram?.let { loadProgramExerciseIds(it.id) }.orEmpty()
            val accounting = if (activeProgram != null && plannedSession != null) {
                computeProgramCompletionAccounting(session, plannedSession)
            } else {
                null
            }
            val receiptSnapshot = if (workoutId != null && currentDetail != null) {
                val exerciseHistories = session.exercises
                    .distinctBy(SessionExercise::exerciseId)
                    .map { exercise ->
                        container.workoutRepository.loadExerciseHistory(
                            exerciseId = exercise.exerciseId,
                            fallbackName = exercise.name,
                        )
                    }
                buildCompletionReceiptSnapshot(
                    workoutId = workoutId,
                    completedAtUtc = currentDetail.completedAtUtc,
                    session = session,
                    durationSeconds = currentDetail.durationSeconds,
                    totalVolume = currentDetail.totalVolume.takeIf { it > 0.0 },
                    profile = profile,
                    activeProgram = activeProgram,
                    plannedSession = plannedSession,
                    beforeHistory = beforeHistory,
                    afterHistory = afterHistory,
                    beforeStrengthScore = beforeStrengthScore,
                    afterStrengthScore = afterStrengthScore,
                    beforeWeeklyTargets = beforeWeeklyTargets,
                    afterWeeklyTargets = afterWeeklyTargets,
                    beforeTokenTrend = beforeTokenTrend,
                    afterTokenTrend = afterTokenTrend,
                    afterNextPlannedSession = afterNextPlannedSession,
                    beforeWeekCredit = beforeWeekCredit,
                    afterWeekCredit = afterWeekCredit,
                    accounting = accounting,
                    comparison = buildComparisonSnapshot(
                        session = session,
                        durationSeconds = currentDetail.durationSeconds,
                        candidate = referenceCandidate,
                        referenceDetail = referenceDetail,
                    ),
                    exerciseHistories = exerciseHistories,
                    programExerciseIds = programExerciseIds,
                    skippedExercisePrompt = skippedExercisePrompt,
                    receiptVariant = receiptVariant,
                )
            } else {
                null
            }
            if (workoutId != null && receiptSnapshot != null) {
                container.workoutRepository.updateCompletionReceiptSnapshot(workoutId, receiptSnapshot)
            }

            uiState = uiState.copy(
                activeSession = null,
                activeSessionExerciseIndex = null,
                activeSessionAddExerciseVisible = false,
                skippedExerciseFeedbackPrompt = null,
                customExerciseDraft = null,
                generatedWorkout = null,
                projectedMuscleInsights = emptyList(),
                projectedMovementInsights = emptyList(),
                showSfrDebrief = false,
                sfrDebriefExercises = emptyList(),
                history = afterHistory,
                historyStrengthScore = afterStrengthScore,
                todayReceiptRecap = todayReceiptRecap,
                completionReceipt = receiptSnapshot?.let { CompletionReceiptUiState(snapshot = it) },
                message = "${session.title} logged.",
            )
            refreshAll()
            loadProgramState()
        }
    }

    fun dismissCompletionReceipt() {
        uiState = uiState.copy(completionReceipt = null)
    }

    fun openHistoryReceipt(workoutId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val detail = container.workoutRepository.loadHistoryDetail(workoutId)
            val snapshot = detail?.completionReceipt
            if (detail == null || snapshot == null) {
                uiState = uiState.copy(message = "No saved receipt for this workout.")
                return@launch
            }
            uiState = uiState.copy(
                selectedHistoryDetail = null,
                completionReceipt = CompletionReceiptUiState(
                    snapshot = snapshot,
                    isReplay = true,
                ),
            )
        }
    }

    fun shareVisibleCompletionReceipt() {
        val receipt = uiState.completionReceipt ?: return
        prepareHistoryWorkoutShare(receipt.snapshot.workoutId, HistoryShareFormat.FormattedText)
    }

    fun tagCompletionReceiptProgramFeel(exerciseId: Long, tag: SfrTag) {
        val receipt = uiState.completionReceipt ?: return
        if (receipt.isReplay) return
        submitSfrFeedback(exerciseId, tag)
        updateVisibleCompletionReceiptSnapshot { snapshot ->
            val updatedLearning = snapshot.learning?.copy(
                programFeelRows = snapshot.learning.programFeelRows.map { row ->
                    if (row.exerciseId == exerciseId) {
                        row.copy(selectedTag = tag)
                    } else {
                        row
                    }
                },
            )
            snapshot.copy(learning = updatedLearning)
        }
    }

    fun tagCompletionReceiptFriction(
        reason: dev.toastlabs.toastlift.data.CompletionFrictionReason,
        biasAwaySelected: Boolean = false,
    ) {
        val receipt = uiState.completionReceipt ?: return
        if (receipt.isReplay) return
        val frictionPrompt = receipt.snapshot.learning?.frictionPrompt ?: return
        val prompt = skippedExerciseFeedbackPromptFromReceipt(receipt.snapshot) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val signalType = if (biasAwaySelected || reason == dev.toastlabs.toastlift.data.CompletionFrictionReason.WRONG_EXERCISE) {
                WorkoutFeedbackSignalType.SKIPPED_EXERCISE_DISLIKED
            } else {
                WorkoutFeedbackSignalType.SKIPPED_EXERCISE_DISMISSED
            }
            container.workoutRepository.recordSkippedExerciseFeedback(prompt, signalType)
            if (signalType == WorkoutFeedbackSignalType.SKIPPED_EXERCISE_DISLIKED) {
                refreshRecommendationBiasState(prompt.exerciseId)
            }
            updateVisibleCompletionReceiptSnapshot { snapshot ->
                snapshot.copy(
                    learning = snapshot.learning?.copy(
                        frictionPrompt = frictionPrompt.copy(
                            selectedReason = reason,
                            biasAwaySelected = biasAwaySelected,
                        ),
                    ),
                )
            }
        }
    }

    private fun updateVisibleCompletionReceiptSnapshot(
        transform: (CompletionReceiptSnapshot) -> CompletionReceiptSnapshot,
    ) {
        val receipt = uiState.completionReceipt ?: return
        if (receipt.isReplay) return
        val updatedSnapshot = transform(receipt.snapshot)
        uiState = uiState.copy(
            completionReceipt = receipt.copy(snapshot = updatedSnapshot),
            history = uiState.history.map { entry ->
                if (entry.id == updatedSnapshot.workoutId) {
                    entry.copy(completionReceipt = updatedSnapshot)
                } else {
                    entry
                }
            },
            todayReceiptRecap = buildTodayReceiptRecapState(
                history = uiState.history.map { entry ->
                    if (entry.id == updatedSnapshot.workoutId) {
                        entry.copy(completionReceipt = updatedSnapshot)
                    } else {
                        entry
                    }
                },
            ),
        )
        viewModelScope.launch(Dispatchers.IO) {
            container.workoutRepository.updateCompletionReceiptSnapshot(updatedSnapshot.workoutId, updatedSnapshot)
        }
    }

    private fun buildCompletionReceiptSnapshot(
        workoutId: Long,
        completedAtUtc: String,
        session: ActiveSession,
        durationSeconds: Int,
        totalVolume: Double?,
        profile: UserProfile?,
        activeProgram: TrainingProgram?,
        plannedSession: PlannedSession?,
        beforeHistory: List<HistorySummary>,
        afterHistory: List<HistorySummary>,
        beforeStrengthScore: StrengthScoreSummary?,
        afterStrengthScore: StrengthScoreSummary?,
        beforeWeeklyTargets: WeeklyMuscleTargetSummary?,
        afterWeeklyTargets: WeeklyMuscleTargetSummary?,
        beforeTokenTrend: AdherenceCurrencyTrend?,
        afterTokenTrend: AdherenceCurrencyTrend?,
        afterNextPlannedSession: PlannedSession?,
        beforeWeekCredit: Double?,
        afterWeekCredit: Double?,
        accounting: CompletionReceiptAccountingSnapshot?,
        comparison: dev.toastlabs.toastlift.data.CompletionReceiptComparisonSnapshot?,
        exerciseHistories: List<ExerciseHistoryDetail>,
        programExerciseIds: Set<Long>,
        skippedExercisePrompt: SkippedExerciseFeedbackPrompt?,
        receiptVariant: CompletionReceiptExperienceVariant,
    ): CompletionReceiptSnapshot {
        val completedSets = computeCompletedSetCount(session)
        val plannedSets = plannedSession?.plannedSets ?: computePlannedSetCount(session)
        val completedExercises = computeCompletedExerciseCount(session)
        val totalExercises = session.exercises.size
        val receiptAccounting = (accounting ?: computeSessionCompletionAccounting(session)).copy(
            tokenDelta = completionReceiptTokenDelta(
                beforeTokenTrend = beforeTokenTrend,
                afterTokenTrend = afterTokenTrend,
            ),
        )
        val outcomeTier = determineOutcomeTier(session, receiptAccounting)
        val achievements = buildReceiptAchievementSnapshot(
            session = session,
            workoutTitle = session.title,
            completedAtUtc = completedAtUtc,
            exerciseHistories = exerciseHistories,
        )
        val splitProgress = buildReceiptSplitProgressSnapshot(
            beforeWeeklyTargets = beforeWeeklyTargets,
            afterWeeklyTargets = afterWeeklyTargets,
        )
        val statsRail = buildReceiptStatsRailSnapshot(
            session = session,
            durationSeconds = durationSeconds,
            volume = totalVolume,
        )
        val evidence = CompletionReceiptEvidenceSnapshot(
            completedSets = completedSets,
            plannedSets = plannedSets,
            completedExercises = completedExercises,
            totalExercises = totalExercises,
            durationSeconds = durationSeconds,
            volume = totalVolume,
            highlights = buildEvidenceHighlights(session, comparison),
        )
        val learning = buildDefaultLearningSnapshot(
            session = session,
            programExerciseIds = programExerciseIds,
            skippedPrompt = skippedExercisePrompt,
        )
        val meaning = if (activeProgram != null && plannedSession != null) {
            buildProgramReceiptMeaningSnapshot(
                plannedSession = plannedSession,
                accounting = receiptAccounting,
                comparison = comparison,
                beforeWeekCredit = beforeWeekCredit,
                afterWeekCredit = afterWeekCredit,
                beforeWeeklyTargets = beforeWeeklyTargets,
                afterWeeklyTargets = afterWeeklyTargets,
                beforeTokenTrend = beforeTokenTrend,
                afterTokenTrend = afterTokenTrend,
            )
        } else {
            buildNonProgramReceiptMeaningSnapshot(
                profile = profile,
                beforeHistory = beforeHistory,
                afterHistory = afterHistory,
                accounting = receiptAccounting,
                beforeTokenTrend = beforeTokenTrend,
                afterTokenTrend = afterTokenTrend,
                beforeStrengthScore = beforeStrengthScore,
                afterStrengthScore = afterStrengthScore,
                comparison = comparison,
            )
        }
        return CompletionReceiptSnapshot(
            workoutId = workoutId,
            createdAtUtc = Instant.now().toString(),
            origin = session.origin,
            focusKey = session.focusKey,
            experiment = CompletionReceiptExperimentSnapshot(
                experimentKey = dev.toastlabs.toastlift.data.COMPLETION_RECEIPT_EXPERIMENT_KEY,
                variantKey = receiptVariant.storageKey,
                variantName = dev.toastlabs.toastlift.data.completionReceiptVariantName(receiptVariant),
            ),
            accounting = receiptAccounting,
            comparison = comparison,
            achievements = achievements,
            splitProgress = splitProgress,
            statsRail = statsRail,
            hero = CompletionReceiptHeroSnapshot(
                title = session.title,
                subtitle = "${formatMinutesCompact(durationSeconds)} • $completedSets/$plannedSets sets • ${receiptOriginLabel(session.origin)}",
                outcomeTier = outcomeTier,
                accentKey = session.title,
            ),
            evidence = evidence,
            meaning = meaning,
            bridge = buildReceiptBridgeSnapshot(
                session = session,
                activeProgram = activeProgram,
                nextPlannedSession = afterNextPlannedSession,
            ),
            learning = learning,
        )
    }

    private fun buildProgramReceiptMeaningSnapshot(
        plannedSession: PlannedSession,
        accounting: CompletionReceiptAccountingSnapshot?,
        comparison: dev.toastlabs.toastlift.data.CompletionReceiptComparisonSnapshot?,
        beforeWeekCredit: Double?,
        afterWeekCredit: Double?,
        beforeWeeklyTargets: WeeklyMuscleTargetSummary?,
        afterWeeklyTargets: WeeklyMuscleTargetSummary?,
        beforeTokenTrend: AdherenceCurrencyTrend?,
        afterTokenTrend: AdherenceCurrencyTrend?,
    ): CompletionReceiptMeaningSnapshot {
        val rows = mutableListOf<CompletionReceiptMeaningRowSnapshot>()
        val totalSessionsThisWeek = uiState.programProgress?.weekSummaries
            ?.firstOrNull { it.weekNumber == plannedSession.weekNumber }
            ?.totalSessions
            ?.coerceAtLeast(1)
            ?: 1
        if (beforeWeekCredit != null && afterWeekCredit != null) {
            rows += CompletionReceiptMeaningRowSnapshot(
                kind = CompletionReceiptMeaningKind.PROGRAM_CREDIT,
                label = "Week ${plannedSession.weekNumber} credit",
                value = "${"%.1f".format(beforeWeekCredit)} -> ${"%.1f".format(afterWeekCredit)} of ${"%.1f".format(totalSessionsThisWeek.toDouble())}",
                supportingText = programTruthSupportingText(accounting?.truth),
            )
        }
        if (beforeTokenTrend != null && afterTokenTrend != null) {
            rows += CompletionReceiptMeaningRowSnapshot(
                kind = CompletionReceiptMeaningKind.TOKEN_BALANCE,
                label = "Token balance",
                value = "${beforeTokenTrend.snapshot.displayValue} -> ${afterTokenTrend.snapshot.displayValue}",
                supportingText = afterTokenTrend.snapshot.statusLabel,
            )
        }
        if (beforeWeeklyTargets != null && afterWeeklyTargets != null) {
            rows += CompletionReceiptMeaningRowSnapshot(
                kind = CompletionReceiptMeaningKind.WEEKLY_TARGET,
                label = "Weekly target coverage",
                value = "${percentLabel(beforeWeeklyTargets.overallCompletionRatio)} -> ${percentLabel(afterWeeklyTargets.overallCompletionRatio)}",
            )
        }
        val summaryLine = when (accounting?.truth) {
            ProgramCompletionTruth.ON_PLAN -> "This session moved week ${plannedSession.weekNumber} forward."
            ProgramCompletionTruth.PARTIAL_CREDIT -> "This session partially advanced week ${plannedSession.weekNumber}."
            ProgramCompletionTruth.MINIMUM_DOSE -> "This session kept continuity without overstating program progress."
            null -> comparison?.headline ?: "This session counted."
        }
        return CompletionReceiptMeaningSnapshot(
            summaryLine = comparison?.headline ?: summaryLine,
            rows = rows.take(3),
        )
    }

    private fun buildNonProgramReceiptMeaningSnapshot(
        profile: UserProfile?,
        beforeHistory: List<HistorySummary>,
        afterHistory: List<HistorySummary>,
        accounting: CompletionReceiptAccountingSnapshot?,
        beforeTokenTrend: AdherenceCurrencyTrend?,
        afterTokenTrend: AdherenceCurrencyTrend?,
        beforeStrengthScore: StrengthScoreSummary?,
        afterStrengthScore: StrengthScoreSummary?,
        comparison: dev.toastlabs.toastlift.data.CompletionReceiptComparisonSnapshot?,
    ): CompletionReceiptMeaningSnapshot {
        val weeklyTarget = profile?.weeklyFrequency ?: 3
        val beforePromise = buildWeeklyPromiseSnapshot(beforeHistory, weeklyTarget)
        val afterPromise = buildWeeklyPromiseSnapshot(afterHistory, weeklyTarget)
        val rows = mutableListOf<CompletionReceiptMeaningRowSnapshot>()
        rows += CompletionReceiptMeaningRowSnapshot(
            kind = CompletionReceiptMeaningKind.WEEKLY_PROMISE,
            label = "Weekly promise",
            value = "${beforePromise.completedSessions}/${beforePromise.targetSessions} -> ${afterPromise.completedSessions}/${afterPromise.targetSessions}",
            supportingText = "Current local week",
        )
        if (afterTokenTrend != null) {
            rows += CompletionReceiptMeaningRowSnapshot(
                kind = CompletionReceiptMeaningKind.TOKEN_BALANCE,
                label = "Token balance",
                value = "${beforeTokenTrend?.snapshot?.displayValue ?: "0"} -> ${afterTokenTrend.snapshot.displayValue}",
                supportingText = afterTokenTrend.snapshot.statusLabel,
            )
        }
        if (beforeStrengthScore != null && afterStrengthScore != null) {
            rows += CompletionReceiptMeaningRowSnapshot(
                kind = CompletionReceiptMeaningKind.STRENGTH_SCORE,
                label = "Strength score",
                value = "${beforeStrengthScore.currentScore} -> ${afterStrengthScore.currentScore}",
                supportingText = signedIntLabel(afterStrengthScore.deltaFromPrevious),
            )
        }
        rows += CompletionReceiptMeaningRowSnapshot(
            kind = CompletionReceiptMeaningKind.MILESTONE,
            label = "Lifetime workouts",
            value = "${beforeHistory.size} -> ${afterHistory.size}",
        )
        val summaryLine = comparison?.headline ?: when {
            accounting?.tokenDelta != null && accounting.tokenDelta > 0 ->
                "This banked ${signedIntLabel(accounting.tokenDelta)} TL and counted toward your weekly training target."
            else -> "This counted toward your weekly training target."
        }
        return CompletionReceiptMeaningSnapshot(
            summaryLine = summaryLine,
            rows = rows.take(3),
        )
    }

    private fun buildReceiptBridgeSnapshot(
        session: ActiveSession,
        activeProgram: TrainingProgram?,
        nextPlannedSession: PlannedSession?,
    ): CompletionReceiptBridgeSnapshot? {
        val suggestedNextLabel = when {
            activeProgram != null && nextPlannedSession != null ->
                "Next: Week ${nextPlannedSession.weekNumber} Day ${nextPlannedSession.dayIndex + 1} • ${programFocusLabel(nextPlannedSession.focusKey)}"
            session.origin.contains("template", ignoreCase = true) ->
                "Next: repeat this template in 2-3 days"
            session.focusKey != null ->
                "Next: rotate to a different focus in the next 2-3 days"
            else -> "Next: keep the next workout small and easy to start"
        }
        val fallbackLabel = "If that slips: 20-min minimum dose"
        return CompletionReceiptBridgeSnapshot(
            suggestedNextLabel = suggestedNextLabel,
            fallbackLabel = fallbackLabel,
        )
    }

    private fun loadProgramExerciseIds(programId: String): Set<Long> {
        return container.programRepository.loadSlotsForProgram(programId)
            .map { it.exerciseId }
            .toSet()
    }

    private fun buildTokenBalanceTrend(): AdherenceCurrencyTrend? {
        return buildGlobalAdherenceCurrencyTrend(
            completedWorkouts = container.workoutRepository.loadCompletedWorkoutAdherenceSignals(),
            skippedSessions = container.programRepository.loadSessionsByStatus(SessionStatus.SKIPPED),
        )
    }

    private fun weekCreditForProgramSessions(
        sessions: List<PlannedSession>,
        weekNumber: Int,
    ): Double {
        return sessions
            .filter { it.weekNumber == weekNumber }
            .sumOf { programSession ->
                programSession.completionCredit ?: if (programSession.status == SessionStatus.COMPLETED) 1.0 else 0.0
            }
    }

    private fun loadWeeklyMuscleTargetSummary(profile: UserProfile): WeeklyMuscleTargetSummary? {
        val now = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val historyStart = now
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .minusWeeks(3)
            .atStartOfDay(zoneId)
            .toInstant()
            .toString()
        val weeklyTargetRows = container.workoutRepository.loadWeeklyMuscleTargetRows(historyStart)
        val exerciseDetailsById = weeklyTargetRows
            .map(WeeklyMuscleTargetWorkoutRow::exerciseId)
            .distinct()
            .mapNotNull { exerciseId ->
                container.catalogRepository.getExerciseDetail(exerciseId)?.let { detail -> exerciseId to detail }
            }
            .toMap()
        return buildWeeklyMuscleTargetSummary(
            profile = profile,
            rows = weeklyTargetRows,
            exerciseDetailsById = exerciseDetailsById,
            now = now,
            zoneId = zoneId,
        )
    }

    private fun skippedExerciseFeedbackPromptFromReceipt(
        snapshot: CompletionReceiptSnapshot,
    ): SkippedExerciseFeedbackPrompt? {
        val frictionPrompt = snapshot.learning?.frictionPrompt ?: return null
        val skippedExerciseId = frictionPrompt.skippedExerciseId ?: return null
        val skippedExerciseName = frictionPrompt.skippedExerciseName ?: return null
        return SkippedExerciseFeedbackPrompt(
            exerciseId = skippedExerciseId,
            exerciseName = skippedExerciseName,
            workoutTitle = snapshot.hero.title,
            workoutOrigin = snapshot.origin,
            workoutFocusKey = snapshot.focusKey,
            sessionStartedAtUtc = snapshot.createdAtUtc,
        )
    }

    private fun receiptOriginLabel(origin: String): String {
        return when {
            origin.contains("program", ignoreCase = true) -> "Program day"
            origin.contains("template", ignoreCase = true) -> "Template"
            origin.contains("history_reuse", ignoreCase = true) -> "History replay"
            origin.contains("manual", ignoreCase = true) -> "Manual workout"
            else -> origin.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    private fun programTruthSupportingText(truth: ProgramCompletionTruth?): String = when (truth) {
        ProgramCompletionTruth.ON_PLAN -> "On-plan session credit."
        ProgramCompletionTruth.PARTIAL_CREDIT -> "Progress banked without overstating compliance."
        ProgramCompletionTruth.MINIMUM_DOSE -> "Continuity preserved."
        null -> ""
    }

    private fun percentLabel(ratio: Double): String {
        return "${(ratio * 100).roundToInt()}%"
    }

    private fun signedIntLabel(value: Int): String {
        return when {
            value > 0 -> "+$value"
            value < 0 -> value.toString()
            else -> "Flat"
        }
    }

    fun applyDebugReceiptLaunch(launch: CompletionReceiptDebugLaunch) {
        if (!BuildConfig.DEBUG) return
        val fixture = buildDebugReceiptFixture(launch)
        uiState = uiState.copy(
            completionReceipt = fixture.receipt,
            todayReceiptRecap = fixture.recap,
            debugReceiptLaunch = launch,
            selectedHistoryDetail = null,
            activeSession = null,
            activeSessionExerciseIndex = null,
            skippedExerciseFeedbackPrompt = null,
            showSfrDebrief = false,
            sfrDebriefExercises = emptyList(),
            message = null,
        )
    }

    private data class DebugReceiptFixture(
        val receipt: CompletionReceiptUiState? = null,
        val recap: TodayReceiptRecapState? = null,
    )

    private fun buildDebugReceiptFixture(launch: CompletionReceiptDebugLaunch): DebugReceiptFixture {
        val scenario = launch.scenario
        return when (launch.surface.lowercase()) {
            "completion_receipt" -> {
                val snapshot = when (scenario) {
                    "program_clean_comparison" -> debugCompletionReceiptSnapshot(
                        title = "Upper Push",
                        subtitle = "46 min • 21/24 sets • Program day",
                        outcomeTier = dev.toastlabs.toastlift.data.SessionOutcomeTier.SOLID_SESSION,
                        tokenDelta = 3,
                        summaryLine = "More work closed than last time.",
                        meaningRows = listOf(
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.PROGRAM_CREDIT,
                                label = "Week 2 credit",
                                value = "2.0 -> 2.9 of 4.0",
                                supportingText = "On-plan session credit.",
                            ),
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.TOKEN_BALANCE,
                                label = "Token balance",
                                value = "+4 -> +7",
                                supportingText = "Steady",
                            ),
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.WEEKLY_TARGET,
                                label = "Weekly target coverage",
                                value = "62% -> 81%",
                            ),
                        ),
                        comparison = dev.toastlabs.toastlift.data.CompletionReceiptComparisonSnapshot(
                            reference = dev.toastlabs.toastlift.data.CompletionReceiptReferenceSnapshot(
                                workoutId = 41L,
                                type = dev.toastlabs.toastlift.data.CompletionReceiptReferenceType.SAME_PROGRAM_FOCUS,
                                label = "Upper Push",
                                completedAtUtc = "2026-03-22T18:30:00Z",
                            ),
                            headline = "More work closed than last time.",
                            rows = listOf(
                                dev.toastlabs.toastlift.data.CompletionReceiptComparisonRowSnapshot(
                                    kind = dev.toastlabs.toastlift.data.CompletionReceiptComparisonKind.COMPLETED_SETS,
                                    label = "Completed sets",
                                    currentValue = "21",
                                    previousValue = "18",
                                    deltaLabel = "+3 sets",
                                ),
                                dev.toastlabs.toastlift.data.CompletionReceiptComparisonRowSnapshot(
                                    kind = dev.toastlabs.toastlift.data.CompletionReceiptComparisonKind.TOP_SET,
                                    label = "Bench Press",
                                    currentValue = "185 x 6",
                                    previousValue = "180 x 6",
                                    deltaLabel = "+5 lb",
                                ),
                            ),
                        ),
                        bridge = CompletionReceiptBridgeSnapshot(
                            suggestedNextLabel = "Next: Week 2 Day 4 • Pull",
                            fallbackLabel = "If that slips: 20-min minimum dose",
                        ),
                    )

                    "program_partial_friction" -> debugCompletionReceiptSnapshot(
                        title = "Lower Body",
                        subtitle = "39 min • 12/24 sets • Program day",
                        outcomeTier = dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL,
                        tokenDelta = 1,
                        summaryLine = "This session partially advanced week 2.",
                        meaningRows = listOf(
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.PROGRAM_CREDIT,
                                label = "Week 2 credit",
                                value = "1.8 -> 2.3 of 4.0",
                                supportingText = "Progress banked without overstating compliance.",
                            ),
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.TOKEN_BALANCE,
                                label = "Token balance",
                                value = "+1 -> +2",
                                supportingText = "Steady",
                            ),
                        ),
                        comparison = null,
                        bridge = CompletionReceiptBridgeSnapshot(
                            suggestedNextLabel = "Next: return with a shorter lower session",
                            fallbackLabel = "If that slips: 20-min minimum dose",
                        ),
                        learning = CompletionReceiptLearningSnapshot(
                            programFeelRows = listOf(
                                dev.toastlabs.toastlift.data.CompletionReceiptProgramFeelRowSnapshot(
                                    exerciseId = 10L,
                                    exerciseName = "Leg Press",
                                    selectedTag = null,
                                ),
                            ),
                            frictionPrompt = dev.toastlabs.toastlift.data.CompletionReceiptFrictionPromptSnapshot(
                                skippedExerciseId = 22L,
                                skippedExerciseName = "Bulgarian Split Squat",
                                selectedReason = dev.toastlabs.toastlift.data.CompletionFrictionReason.TOO_LONG,
                                biasAwaySelected = true,
                            ),
                        ),
                    )

                    "generated_clean_comparison" -> debugCompletionReceiptSnapshot(
                        title = "Upper Focus Generator",
                        subtitle = "42 min • 18/18 sets • Generated workout",
                        outcomeTier = dev.toastlabs.toastlift.data.SessionOutcomeTier.CLOSED_CLEAN,
                        summaryLine = "This counted toward your weekly training target.",
                        meaningRows = listOf(
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.WEEKLY_PROMISE,
                                label = "Weekly promise",
                                value = "1/3 -> 2/3",
                                supportingText = "Current local week",
                            ),
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.MILESTONE,
                                label = "Lifetime workouts",
                                value = "19 -> 20",
                            ),
                        ),
                        comparison = dev.toastlabs.toastlift.data.CompletionReceiptComparisonSnapshot(
                            reference = dev.toastlabs.toastlift.data.CompletionReceiptReferenceSnapshot(
                                workoutId = 32L,
                                type = dev.toastlabs.toastlift.data.CompletionReceiptReferenceType.SAME_GENERATED_FOCUS,
                                label = "Upper Focus Generator",
                                completedAtUtc = "2026-03-19T17:00:00Z",
                            ),
                            headline = "Matched the work in less time.",
                            rows = listOf(
                                dev.toastlabs.toastlift.data.CompletionReceiptComparisonRowSnapshot(
                                    kind = dev.toastlabs.toastlift.data.CompletionReceiptComparisonKind.DURATION,
                                    label = "Duration",
                                    currentValue = "42 min",
                                    previousValue = "48 min",
                                    deltaLabel = "-6 mins",
                                ),
                            ),
                        ),
                        bridge = CompletionReceiptBridgeSnapshot(
                            suggestedNextLabel = "Next: rotate to a different focus in the next 2-3 days",
                            fallbackLabel = "If that slips: 20-min minimum dose",
                        ),
                    )

                    "template_replay_comparison", "history_replay" -> debugCompletionReceiptSnapshot(
                        title = "Posterior Chain Replay",
                        subtitle = "44 min • 16/18 sets • History replay",
                        outcomeTier = dev.toastlabs.toastlift.data.SessionOutcomeTier.SOLID_SESSION,
                        summaryLine = "A close match to the last comparable session.",
                        meaningRows = listOf(
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.MILESTONE,
                                label = "Lifetime workouts",
                                value = "24 -> 25",
                            ),
                        ),
                        comparison = dev.toastlabs.toastlift.data.CompletionReceiptComparisonSnapshot(
                            reference = dev.toastlabs.toastlift.data.CompletionReceiptReferenceSnapshot(
                                workoutId = 27L,
                                type = dev.toastlabs.toastlift.data.CompletionReceiptReferenceType.HISTORY_REPLAY_SOURCE,
                                label = "Posterior Chain Replay",
                                completedAtUtc = "2026-03-10T17:00:00Z",
                            ),
                            headline = "A close match to the last comparable session.",
                            rows = listOf(
                                dev.toastlabs.toastlift.data.CompletionReceiptComparisonRowSnapshot(
                                    kind = dev.toastlabs.toastlift.data.CompletionReceiptComparisonKind.COMPLETED_SETS,
                                    label = "Completed sets",
                                    currentValue = "16",
                                    previousValue = "15",
                                    deltaLabel = "+1 set",
                                ),
                            ),
                        ),
                        bridge = CompletionReceiptBridgeSnapshot(
                            suggestedNextLabel = "Next: repeat this template in 2-3 days",
                            fallbackLabel = "If that slips: 20-min minimum dose",
                        ),
                    )

                    "no_comparison_fallback" -> debugCompletionReceiptSnapshot(
                        title = "Travel Circuit",
                        subtitle = "28 min • 10/12 sets • Manual workout",
                        outcomeTier = dev.toastlabs.toastlift.data.SessionOutcomeTier.SOLID_SESSION,
                        summaryLine = "This counted toward your weekly training target.",
                        meaningRows = listOf(
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.WEEKLY_PROMISE,
                                label = "Weekly promise",
                                value = "0/3 -> 1/3",
                                supportingText = "Current local week",
                            ),
                        ),
                        comparison = null,
                        bridge = CompletionReceiptBridgeSnapshot(
                            suggestedNextLabel = "Next: keep the next workout small and easy to start",
                            fallbackLabel = "If that slips: 20-min minimum dose",
                        ),
                    )

                    "program_learning_card" -> debugCompletionReceiptSnapshot(
                        title = "Upper Pull",
                        subtitle = "41 min • 18/20 sets • Program day",
                        outcomeTier = dev.toastlabs.toastlift.data.SessionOutcomeTier.SOLID_SESSION,
                        tokenDelta = 2,
                        summaryLine = "This session moved week 3 forward.",
                        meaningRows = listOf(
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.PROGRAM_CREDIT,
                                label = "Week 3 credit",
                                value = "0.9 -> 1.8 of 4.0",
                                supportingText = "On-plan session credit.",
                            ),
                        ),
                        comparison = null,
                        bridge = CompletionReceiptBridgeSnapshot(
                            suggestedNextLabel = "Next: Week 3 Day 2 • Lower",
                            fallbackLabel = "If that slips: 20-min minimum dose",
                        ),
                        learning = CompletionReceiptLearningSnapshot(
                            programFeelRows = listOf(
                                dev.toastlabs.toastlift.data.CompletionReceiptProgramFeelRowSnapshot(
                                    exerciseId = 31L,
                                    exerciseName = "Chest-Supported Row",
                                    selectedTag = dev.toastlabs.toastlift.data.SfrTag.GREAT_STIMULUS,
                                ),
                                dev.toastlabs.toastlift.data.CompletionReceiptProgramFeelRowSnapshot(
                                    exerciseId = 32L,
                                    exerciseName = "Lat Pulldown",
                                    selectedTag = null,
                                ),
                            ),
                        ),
                    )

                    else -> debugCompletionReceiptSnapshot(
                        title = "Workout Logged",
                        subtitle = "36 min • 14/16 sets • Receipt debug",
                        outcomeTier = dev.toastlabs.toastlift.data.SessionOutcomeTier.SOLID_SESSION,
                        summaryLine = "Debug receipt fixture.",
                        meaningRows = listOf(
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.MILESTONE,
                                label = "Fixture",
                                value = scenario,
                            ),
                        ),
                        comparison = null,
                        bridge = CompletionReceiptBridgeSnapshot(
                            suggestedNextLabel = "Next: debug fixture",
                            fallbackLabel = "If that slips: 20-min minimum dose",
                        ),
                    )
                }
                DebugReceiptFixture(
                    receipt = CompletionReceiptUiState(
                        snapshot = if (scenario == "history_replay") snapshot.copy(origin = "history_reuse_exact") else snapshot,
                        isReplay = scenario == "history_replay",
                        debugSurface = launch.surface,
                        debugScenario = scenario,
                    ),
                    recap = null,
                )
            }

            "today_receipt_recap" -> {
                val recap = when (scenario) {
                    "today_recap_multi" -> TodayReceiptRecapState(
                        todayWorkoutIds = listOf(81L, 82L),
                        latestWorkoutId = 82L,
                        latestWorkoutTitle = "Lower Body",
                        latestEvidenceLine = "Completed sets: +3 sets",
                        latestMeaningLine = "This session partially advanced week 2.",
                        workoutCountToday = 2,
                        debugSurface = launch.surface,
                        debugScenario = scenario,
                    )

                    else -> TodayReceiptRecapState(
                        todayWorkoutIds = listOf(80L),
                        latestWorkoutId = 80L,
                        latestWorkoutTitle = "Upper Push",
                        latestEvidenceLine = "Bench Press: +5 lb",
                        latestMeaningLine = "This session moved week 2 forward.",
                        workoutCountToday = 1,
                        debugSurface = launch.surface,
                        debugScenario = scenario,
                    )
                }
                DebugReceiptFixture(recap = recap)
            }

            "history_receipt_replay" -> DebugReceiptFixture(
                    receipt = CompletionReceiptUiState(
                        snapshot = debugCompletionReceiptSnapshot(
                        title = "Posterior Chain Replay",
                        subtitle = "44 min • 16/18 sets • History replay",
                        outcomeTier = dev.toastlabs.toastlift.data.SessionOutcomeTier.SOLID_SESSION,
                        summaryLine = "A close match to the last comparable session.",
                        meaningRows = listOf(
                            CompletionReceiptMeaningRowSnapshot(
                                kind = CompletionReceiptMeaningKind.MILESTONE,
                                label = "Lifetime workouts",
                                value = "24 -> 25",
                            ),
                        ),
                        comparison = null,
                        bridge = CompletionReceiptBridgeSnapshot(
                            suggestedNextLabel = "Next: repeat this template in 2-3 days",
                            fallbackLabel = "If that slips: 20-min minimum dose",
                        ),
                        ),
                        isReplay = true,
                        debugSurface = launch.surface,
                        debugScenario = scenario,
                    ),
                )

            else -> DebugReceiptFixture()
        }
    }

    private fun debugCompletionReceiptSnapshot(
        title: String,
        subtitle: String,
        outcomeTier: dev.toastlabs.toastlift.data.SessionOutcomeTier,
        summaryLine: String,
        meaningRows: List<CompletionReceiptMeaningRowSnapshot>,
        comparison: dev.toastlabs.toastlift.data.CompletionReceiptComparisonSnapshot?,
        bridge: CompletionReceiptBridgeSnapshot,
        learning: CompletionReceiptLearningSnapshot? = null,
        achievements: CompletionReceiptAchievementSnapshot? = null,
        splitProgress: CompletionReceiptSplitProgressSnapshot? = null,
        statsRail: CompletionReceiptStatsRailSnapshot? = null,
        tokenDelta: Int? = null,
    ): CompletionReceiptSnapshot {
        val debugDurationSeconds = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) {
            39 * 60
        } else {
            46 * 60
        }
        val debugVolume = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) {
            5400.0
        } else {
            9400.0
        }
        return CompletionReceiptSnapshot(
            workoutId = 999L,
            createdAtUtc = "2026-03-28T12:00:00Z",
            origin = "debug",
            focusKey = "upper_push_strength",
            experiment = CompletionReceiptExperimentSnapshot(
                experimentKey = dev.toastlabs.toastlift.data.COMPLETION_RECEIPT_EXPERIMENT_KEY,
                variantKey = CompletionReceiptExperienceVariant.MULTI_LAYER_RECEIPT.storageKey,
                variantName = dev.toastlabs.toastlift.data.completionReceiptVariantName(
                    CompletionReceiptExperienceVariant.MULTI_LAYER_RECEIPT,
                ),
            ),
            accounting = CompletionReceiptAccountingSnapshot(
                completionRatio = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 0.5 else 0.92,
                completionCredit = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 0.5 else 1.0,
                truth = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) {
                    ProgramCompletionTruth.PARTIAL_CREDIT
                } else {
                    ProgramCompletionTruth.ON_PLAN
                },
                tokenDelta = tokenDelta,
            ),
            comparison = comparison,
            achievements = achievements ?: if (comparison != null) {
                CompletionReceiptAchievementSnapshot(
                    title = "2 PRs Broken",
                    prCount = 2,
                    chips = listOf("Bench 185 lb PR", "Chest-Supported Row Rep PR"),
                    supportingText = "New bests from this workout are now saved to history.",
                )
            } else {
                CompletionReceiptAchievementSnapshot(
                    title = "Best Set Today",
                    fallbackLabel = "Bench Press",
                    fallbackValue = "185 x 6",
                    supportingText = "No new PRs, but this was the strongest set you logged today.",
                )
            },
            splitProgress = splitProgress ?: CompletionReceiptSplitProgressSnapshot(
                overallBeforeCompletedSets = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 11.0 else 18.5,
                overallAfterCompletedSets = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 14.0 else 24.5,
                overallTargetSets = 30.0,
                groupRows = listOf(
                    dev.toastlabs.toastlift.data.CompletionReceiptSplitProgressRowSnapshot(
                        key = "push",
                        label = "Push Muscles",
                        beforeCompletedSets = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 4.0 else 6.5,
                        afterCompletedSets = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 5.5 else 10.5,
                        targetSets = 10.0,
                    ),
                    dev.toastlabs.toastlift.data.CompletionReceiptSplitProgressRowSnapshot(
                        key = "pull",
                        label = "Pull Muscles",
                        beforeCompletedSets = 6.0,
                        afterCompletedSets = 8.0,
                        targetSets = 10.0,
                    ),
                    dev.toastlabs.toastlift.data.CompletionReceiptSplitProgressRowSnapshot(
                        key = "legs",
                        label = "Legs Muscles",
                        beforeCompletedSets = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 1.0 else 6.0,
                        afterCompletedSets = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 3.0 else 6.0,
                        targetSets = 10.0,
                    ),
                ),
            ),
            statsRail = statsRail ?: CompletionReceiptStatsRailSnapshot(
                items = listOf(
                    CompletionReceiptStatSnapshot(
                        label = "Volume",
                        value = formatVolumeShort(debugVolume),
                        supportingText = "Completed load",
                    ),
                    CompletionReceiptStatSnapshot(
                        label = "Time",
                        value = formatMinutesCompact(debugDurationSeconds),
                        supportingText = "Elapsed",
                    ),
                    CompletionReceiptStatSnapshot(
                        label = "Sets",
                        value = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) "12" else "21",
                        supportingText = "Completed",
                    ),
                    CompletionReceiptStatSnapshot(
                        label = "Closed",
                        value = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) "3/6" else "5/6",
                        supportingText = "Exercises",
                    ),
                ),
            ),
            hero = CompletionReceiptHeroSnapshot(
                title = title,
                subtitle = subtitle,
                outcomeTier = outcomeTier,
                accentKey = title,
            ),
            evidence = CompletionReceiptEvidenceSnapshot(
                completedSets = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 12 else 21,
                plannedSets = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 24 else 24,
                completedExercises = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) 3 else 5,
                totalExercises = 6,
                durationSeconds = debugDurationSeconds,
                volume = debugVolume,
                highlights = listOfNotNull(
                    dev.toastlabs.toastlift.data.CompletionReceiptHighlightSnapshot(
                        kind = dev.toastlabs.toastlift.data.CompletionReceiptHighlightKind.LOAD_GAIN,
                        label = "Bench Press",
                        deltaLabel = "+5 lb",
                        detail = "Compared to last time",
                    ),
                    dev.toastlabs.toastlift.data.CompletionReceiptHighlightSnapshot(
                        kind = dev.toastlabs.toastlift.data.CompletionReceiptHighlightKind.FIRST_COMPLETION,
                        label = "Exercises closed",
                        deltaLabel = if (outcomeTier == dev.toastlabs.toastlift.data.SessionOutcomeTier.MEANINGFUL_PARTIAL) "3/6" else "5/6",
                        detail = "Most useful view of finished work",
                    ),
                ),
            ),
            meaning = CompletionReceiptMeaningSnapshot(
                summaryLine = summaryLine,
                rows = meaningRows,
            ),
            bridge = bridge,
            learning = learning,
        )
    }

    fun markSkippedExerciseAsDisliked() {
        val prompt = uiState.skippedExerciseFeedbackPrompt ?: return
        uiState = uiState.copy(
            skippedExerciseFeedbackPrompt = null,
            message = "${prompt.exerciseName} will show up less often next time.",
        )
        viewModelScope.launch(Dispatchers.IO) {
            container.workoutRepository.recordSkippedExerciseFeedback(
                prompt = prompt,
                signalType = WorkoutFeedbackSignalType.SKIPPED_EXERCISE_DISLIKED,
            )
            refreshRecommendationBiasState(prompt.exerciseId)
        }
    }

    fun dismissSkippedExerciseFeedbackPrompt() {
        val prompt = uiState.skippedExerciseFeedbackPrompt ?: return
        uiState = uiState.copy(skippedExerciseFeedbackPrompt = null)
        viewModelScope.launch(Dispatchers.IO) {
            container.workoutRepository.recordSkippedExerciseFeedback(
                prompt = prompt,
                signalType = WorkoutFeedbackSignalType.SKIPPED_EXERCISE_DISMISSED,
            )
        }
    }

    fun cancelSession() {
        val session = uiState.activeSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            container.workoutRepository.saveAbandonedWorkout(session)
            container.workoutRepository.clearActiveSession()
            refreshAll()
            uiState = uiState.copy(
                activeSession = null,
                activeSessionExerciseIndex = null,
                activeSessionAddExerciseVisible = false,
                skippedExerciseFeedbackPrompt = null,
                customExerciseDraft = null,
                message = "Workout abandoned. You can restore the latest abandoned workout from Today.",
            )
        }
    }

    fun restoreAbandonedWorkout() {
        viewModelScope.launch(Dispatchers.IO) {
            val abandoned = container.workoutRepository.loadAbandonedWorkout() ?: return@launch
            val restoredSession = ensureSessionFruitIcons(abandoned)
            container.workoutRepository.clearAbandonedWorkout()
            container.workoutRepository.saveActiveSession(restoredSession, null)
            refreshAll()
            uiState = uiState.copy(
                activeSession = restoredSession,
                activeSessionExerciseIndex = null,
                activeSessionAddExerciseVisible = false,
                skippedExerciseFeedbackPrompt = null,
                customExerciseDraft = null,
                message = "Restored ${restoredSession.title}.",
            )
        }
    }

    // ── Adaptive Program Engine methods ──

    fun loadProgramState() {
        viewModelScope.launch(Dispatchers.IO) {
            val program = container.programRepository.loadActiveProgram()
            if (program == null) {
                uiState = uiState.copy(
                    activeProgram = null,
                    programOverview = null,
                    programProgress = null,
                    nextPlannedSession = null,
                    recoverableSkippedSession = null,
                    nextSessionExercises = emptyList(),
                    upcomingProgramSessions = emptyList(),
                    pendingCheckpoint = null,
                )
                return@launch
            }

            val allWeeks = container.programRepository.loadWeeksForProgram(program.id)
            val allSessions = container.programRepository.loadSessionsForProgram(program.id)
            val allCheckpoints = container.programRepository.loadAllCheckpoints(program.id)
            val completedSetCountsByWorkoutId = container.workoutRepository.loadCompletedSetCountsForWorkouts(
                allSessions.mapNotNull { it.actualWorkoutId },
            )
            val completedWorkoutTimestampsById = container.workoutRepository.loadCompletedWorkoutTimestamps(
                allSessions.mapNotNull { it.actualWorkoutId },
            )
            val adherenceSignals = allSessions.map { session ->
                AdherenceSessionSignal(
                    sequenceNumber = session.sequenceNumber,
                    status = session.status,
                    plannedSets = session.plannedSets,
                    completedSetCount = session.actualWorkoutId?.let { completedSetCountsByWorkoutId[it] } ?: 0,
                    occurredAtUtc = session.statusUpdatedAtUtc
                        ?: session.actualWorkoutId?.let { completedWorkoutTimestampsById[it] },
                )
            }
            val adherenceSnapshot = buildAdherenceCurrencySnapshot(
                adherenceSignals,
            )
            val upcomingSessions = allSessions
                .asSequence()
                .filter { it.status == SessionStatus.UPCOMING }
                .sortedBy { it.sequenceNumber }
                .take(UPCOMING_PROGRAM_SESSIONS_LIMIT)
                .toList()
            val nextSession = upcomingSessions.firstOrNull()
            val recoverableSkippedSession = container.programRepository.loadMostRecentSkippedSession(program.id)
            val allSessionExerciseIds = allSessions.associate { session ->
                session.id to container.programRepository.loadExercisesForSession(session.id)
            }
            val exercisesBySessionId = upcomingSessions.associate { session ->
                session.id to allSessionExerciseIds[session.id].orEmpty()
            }
            val exerciseNameById = container.catalogRepository.searchExercisesByIds(
                exercisesBySessionId.values
                    .flatten()
                    .map { it.exerciseId }
                    .distinct(),
            ).associate { it.id to it.name }
            val exercises = nextSession?.let { exercisesBySessionId[it.id] }.orEmpty()
            val pendingCheckpoints = allCheckpoints.filter { it.status == dev.toastlabs.toastlift.data.CheckpointStatus.PENDING }
            val nextCheckpoint = pendingCheckpoints.firstOrNull { it.weekNumber <= (nextSession?.weekNumber ?: Int.MAX_VALUE) }
            val progressSummary = buildProgramProgressSummary(
                program = program,
                weeks = allWeeks,
                sessions = allSessions,
                checkpoints = allCheckpoints,
                nextSession = nextSession,
            )

            val confidenceLabel = when {
                program.confidenceScore >= 0.7 -> "Stable"
                program.confidenceScore >= 0.4 -> "Adjusting"
                else -> "Needs Review"
            }
            val overview = ProgramOverview(
                title = program.title,
                weekNumber = progressSummary.currentWeekNumber,
                totalWeeks = progressSummary.totalWeeks,
                confidenceLabel = confidenceLabel,
                adherenceSnapshot = adherenceSnapshot,
                nextSessionFocus = nextSession?.let { focusDisplayName(SessionFocus.toFocusKey(it.focusKey)) },
                coachBrief = nextSession?.coachBrief ?: if (program.status == ProgramStatus.PAUSED) {
                    "Program paused. Resume when you're ready for the next session."
                } else {
                    null
                },
            )

            uiState = uiState.copy(
                activeProgram = program,
                programOverview = overview,
                programProgress = progressSummary,
                nextPlannedSession = nextSession,
                recoverableSkippedSession = recoverableSkippedSession,
                nextSessionExercises = exercises,
                upcomingProgramSessions = buildUpcomingProgramSessionSummaries(
                    sessions = upcomingSessions,
                    exercisesBySessionId = exercisesBySessionId,
                    exerciseNameById = exerciseNameById,
                ),
                pendingCheckpoint = nextCheckpoint,
            )
        }
    }

    fun showProgramSetup() {
        uiState = uiState.copy(
            showProgramSetup = true,
            programSetupDraft = syncProgramSetupDraftWithProfileDuration(
                currentDraft = uiState.programSetupDraft,
                profile = uiState.profile,
            ),
        )
    }

    fun dismissProgramSetup() {
        uiState = uiState.copy(showProgramSetup = false)
    }

    fun updateProgramSetupDraft(transform: (ProgramSetupDraft) -> ProgramSetupDraft) {
        val updatedDraft = transform(uiState.programSetupDraft)
        uiState = uiState.copy(
            programSetupDraft = updatedDraft.copy(
                sessionsPerWeek = normalizeWeeklyFrequency(updatedDraft.sessionsPerWeek),
            ),
        )
    }

    fun startProgram(draft: ProgramSetupDraft) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = container.userRepository.loadProfile()
            if (profile == null) {
                uiState = uiState.copy(message = "Complete your profile first.")
                return@launch
            }
            val sessionsPerWeek = normalizeWeeklyFrequency(draft.sessionsPerWeek)

            val locationModeId = profile.activeLocationModeId
            val availableEquipment = dev.toastlabs.toastlift.data.resolveAvailableEquipmentForGeneration(
                container.userRepository.loadEquipmentForLocation(locationModeId),
            )

            // Infer recent consistency from history
            val history = container.workoutRepository.loadHistory()
            val recentConsistency = if (history.size >= 4) {
                val recentWeeks = 4
                val recentCount = history.take(recentWeeks * sessionsPerWeek).size.toDouble()
                (recentCount / (recentWeeks * sessionsPerWeek)).coerceIn(0.0, 1.0)
            } else {
                0.5
            }

            val program = container.programEngine.generatePlan(
                profile = profile,
                goal = draft.goal,
                durationWeeks = draft.durationWeeks,
                sessionsPerWeek = sessionsPerWeek,
                splitProgramId = draft.splitProgramId,
                availableEquipment = availableEquipment,
                sessionTimeMinutes = draft.sessionTimeMinutes,
                equipmentStability = draft.equipmentStability,
                recentConsistencyPercent = recentConsistency,
            )

            uiState = uiState.copy(
                showProgramSetup = false,
                message = "Started ${program.title}.",
            )
            loadProgramState()
        }
    }

    fun realizeNextSession(readinessContext: ReadinessContext) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = uiState.nextPlannedSession ?: return@launch
            val profile = container.userRepository.loadProfile() ?: return@launch
            val locationModeId = profile.activeLocationModeId
            val availableEquipment = dev.toastlabs.toastlift.data.resolveAvailableEquipmentForGeneration(
                container.userRepository.loadEquipmentForLocation(locationModeId),
            )
            val history = container.generatorRepository.loadHistoricalSetsForRecommendations()

            val workout = container.programEngine.realizeSession(
                plannedSession = session,
                readinessContext = readinessContext,
                history = history,
                profile = profile,
                availableEquipment = availableEquipment,
            )

            // Start the workout immediately using the existing active session flow
            val activeSession = buildActiveSession(workout)
            container.workoutRepository.clearAbandonedWorkout()
            container.workoutRepository.saveActiveSession(activeSession, null)

            uiState = uiState.copy(
                activeSession = activeSession,
                activeSessionExerciseIndex = null,
                activeSessionAddExerciseVisible = false,
                customExerciseDraft = null,
                message = null,
            )
        }
    }

    fun skipPlannedSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val program = uiState.activeProgram ?: return@launch
            container.programRepository.updateSessionStatus(sessionId, SessionStatus.SKIPPED, null)

            // Check if volume can be migrated to next session
            val nextSession = container.programRepository.currentPosition(program.id)
            if (nextSession != null) {
                val skippedSession = uiState.nextPlannedSession
                if (skippedSession != null) {
                    val extraSets = redistributedSetsForSkippedSession(skippedSession.plannedSets)
                    container.programRepository.updateSessionPlannedSets(
                        nextSession.id,
                        nextSession.plannedSets + extraSets,
                    )
                }
            }

            // Check for program inactivity (would be based on dates in production)
            val skippedCount = container.programRepository.countSessionsByStatus(program.id, SessionStatus.SKIPPED)
            if (skippedCount >= program.adaptationPolicy.triggerReviewAfterMissedSessions) {
                uiState = uiState.copy(message = "Session skipped. Consider reviewing your program.")
            } else {
                uiState = uiState.copy(message = "Session skipped. Volume redistributed to next session.")
            }
            completeProgramIfFinished(program.id)
            refreshAll()
            loadProgramState()
        }
    }

    fun unskipMostRecentSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val skippedSession = uiState.recoverableSkippedSession ?: return@launch
            val currentUpcomingSession = uiState.nextPlannedSession

            if (
                currentUpcomingSession != null &&
                currentUpcomingSession.sequenceNumber > skippedSession.sequenceNumber
            ) {
                val redistributedSets = redistributedSetsForSkippedSession(skippedSession.plannedSets)
                container.programRepository.updateSessionPlannedSets(
                    currentUpcomingSession.id,
                    (currentUpcomingSession.plannedSets - redistributedSets).coerceAtLeast(1),
                )
            }

            container.programRepository.updateSessionStatus(skippedSession.id, SessionStatus.UPCOMING, null)
            uiState = uiState.copy(message = "Skipped session restored.")
            refreshAll()
            loadProgramState()
        }
    }

    fun pauseProgram() {
        viewModelScope.launch(Dispatchers.IO) {
            val program = uiState.activeProgram ?: return@launch
            if (program.status != ProgramStatus.ACTIVE) return@launch

            container.programRepository.updateProgramStatus(program.id, ProgramStatus.PAUSED)
            uiState = uiState.copy(message = "Program paused.")
            loadProgramState()
        }
    }

    fun resumeProgram() {
        viewModelScope.launch(Dispatchers.IO) {
            val program = uiState.activeProgram ?: return@launch
            if (program.status != ProgramStatus.PAUSED) return@launch

            container.programRepository.updateProgramStatus(program.id, ProgramStatus.ACTIVE)

            // Insert re-entry session at reduced volume
            val nextSession = container.programRepository.currentPosition(program.id)
            val insertBeforeSequence = nextSession?.sequenceNumber
            val fallbackSequence = insertBeforeSequence ?: (container.programRepository.maxSequenceNumber(program.id) + 1)
            val reentrySession = PlannedSession(
                programId = program.id,
                weekNumber = nextSession?.weekNumber ?: program.totalWeeks,
                dayIndex = nextSession?.dayIndex ?: 0,
                sequenceNumber = fallbackSequence,
                focusKey = nextSession?.focusKey ?: SessionFocus.FULL_BODY,
                plannedSets = ((nextSession?.plannedSets ?: 16) * 0.65).roundToInt().coerceAtLeast(6),
                timeBudgetMinutes = nextSession?.timeBudgetMinutes,
                coachBrief = "Welcome back! This session eases you in with reduced volume and load.",
            )
            val reentrySessionId = container.programRepository.insertReentrySession(reentrySession, insertBeforeSequence)
            if (nextSession != null && reentrySessionId > 0) {
                val templateExercises = container.programRepository.loadExercisesForSession(nextSession.id)
                val reentryExercises = templateExercises.map { exercise ->
                    PlannedSessionExercise(
                        plannedSessionId = reentrySessionId,
                        exerciseId = exercise.exerciseId,
                        sortOrder = exercise.sortOrder,
                        executionStyle = exercise.executionStyle,
                    )
                }
                container.programRepository.saveSessionExercises(reentryExercises)
            }

            uiState = uiState.copy(message = "Program resumed. Starting with a lighter session.")
            loadProgramState()
        }
    }

    fun endProgram(programId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.programRepository.updateProgramStatus(programId, ProgramStatus.COMPLETED)
            uiState = uiState.copy(
                showProgramWrapUp = true,
                message = "Program completed.",
            )
            loadProgramState()
        }
    }

    fun dismissProgramWrapUp() {
        uiState = uiState.copy(showProgramWrapUp = false)
    }

    fun runPendingCheckpoint() {
        viewModelScope.launch(Dispatchers.IO) {
            val program = uiState.activeProgram ?: return@launch
            val checkpoint = uiState.pendingCheckpoint ?: return@launch
            val completedSessions = container.programRepository.loadSessionsForProgram(program.id)
                .filter { it.status == SessionStatus.COMPLETED }
            val performedWorkoutIds = container.programRepository.loadCompletedSessionIds(program.id)
            val history = container.generatorRepository.loadHistoricalSetsForRecommendations()

            val result = container.reviewEngine.runCheckpoint(
                program = program,
                checkpoint = checkpoint,
                completedSessions = completedSessions,
                performedWorkoutIds = performedWorkoutIds,
                history = history,
            )
            applyCheckpointAction(program, checkpoint, result)

            uiState = uiState.copy(
                showCheckpointReview = true,
                checkpointResult = result,
            )
            loadProgramState()
        }
    }

    fun dismissCheckpointReview() {
        val programId = uiState.activeProgram?.id
        uiState = uiState.copy(
            showCheckpointReview = false,
            checkpointResult = null,
        )
        if (programId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                completeProgramIfFinished(programId)
                loadProgramState()
            }
        }
    }

    fun submitSfrFeedback(exerciseId: Long, tag: SfrTag) {
        viewModelScope.launch(Dispatchers.IO) {
            val program = uiState.activeProgram ?: return@launch
            val slots = container.programRepository.loadSlotsForProgram(program.id)
            val slot = slots.firstOrNull { it.exerciseId == exerciseId } ?: return@launch

            val currentScore = slot.sfrScore ?: 0.0
            val delta = when (tag) {
                SfrTag.GREAT_STIMULUS -> 1.0
                SfrTag.JOINT_DISCOMFORT -> -2.0
                SfrTag.NO_OPINION -> 0.0
            }
            // Running average: (old * sessions + delta) / (sessions + 1)
            // Simplified: adjust by a fraction
            val newScore = (currentScore + delta * 0.2).coerceIn(-1.0, 1.0)
            container.programRepository.updateSfrScore(slot.id, newScore)

            container.programRepository.logEvent(
                dev.toastlabs.toastlift.data.ProgramEvent(
                    programId = program.id,
                    eventType = dev.toastlabs.toastlift.data.ProgramEventType.SFR_UPDATED,
                    payloadJson = """{"exerciseId":$exerciseId,"tag":"${tag.name}","newScore":$newScore}""",
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun dismissSfrDebrief() {
        uiState = uiState.copy(
            showSfrDebrief = false,
            sfrDebriefExercises = emptyList(),
        )
    }

    fun acceptEvolutionSuggestion(slotId: Long, newExerciseId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val program = uiState.activeProgram ?: return@launch
            val slot = container.programRepository.loadSlotsForProgram(program.id)
                .firstOrNull { it.id == slotId }
                ?: return@launch

            container.programRepository.updateSlotExercise(slotId, newExerciseId)
            container.programRepository.replaceUpcomingSessionExercise(
                programId = program.id,
                currentExerciseId = slot.exerciseId,
                replacementExerciseId = newExerciseId,
            )
            container.programRepository.logEvent(
                dev.toastlabs.toastlift.data.ProgramEvent(
                    programId = program.id,
                    eventType = dev.toastlabs.toastlift.data.ProgramEventType.EXERCISE_EVOLVED,
                    payloadJson = """{"slotId":$slotId,"newExerciseId":$newExerciseId,"reason":"evolution_accepted"}""",
                    createdAt = System.currentTimeMillis(),
                ),
            )
            uiState = uiState.copy(message = "Exercise evolution accepted.")
            loadProgramState()
        }
    }

    private fun applyCheckpointAction(
        program: TrainingProgram,
        checkpoint: ProgramCheckpoint,
        result: CheckpointResult,
    ) {
        val sessions = container.programRepository.loadSessionsForProgram(program.id)
        val upcomingSessions = sessions.filter { it.status == SessionStatus.UPCOMING }
        val nextUpcomingWeek = upcomingSessions.minByOrNull { it.sequenceNumber }?.weekNumber ?: return

        when (result.action) {
            CheckpointAction.CONTINUE -> Unit
            CheckpointAction.INTENSIFY -> {
                val week = container.programRepository.loadWeeksForProgram(program.id)
                    .firstOrNull { it.weekNumber == nextUpcomingWeek }
                    ?: return
                container.programRepository.updateWeekProgression(
                    programId = program.id,
                    weekNumber = nextUpcomingWeek,
                    intensityModifier = (week.intensityModifier + 0.05).coerceAtMost(1.3),
                )
            }
            CheckpointAction.TRIGGER_DELOAD -> {
                val week = container.programRepository.loadWeeksForProgram(program.id)
                    .firstOrNull { it.weekNumber == nextUpcomingWeek }
                    ?: return
                container.programRepository.updateWeekProgression(
                    programId = program.id,
                    weekNumber = nextUpcomingWeek,
                    volumeMultiplier = minOf(week.volumeMultiplier, 0.7),
                    intensityModifier = minOf(week.intensityModifier, 0.9),
                )
                upcomingSessions
                    .filter { it.weekNumber == nextUpcomingWeek }
                    .forEach { session ->
                        container.programRepository.updateSessionPlannedSets(
                            session.id,
                            (session.plannedSets * 0.7).roundToInt().coerceAtLeast(8),
                        )
                    }
            }
            CheckpointAction.REDUCE_TO_MAINTAIN -> {
                upcomingSessions.forEach { session ->
                    container.programRepository.updateSessionPlannedSets(
                        session.id,
                        (session.plannedSets * 0.6).roundToInt().coerceAtLeast(6),
                    )
                }
            }
            CheckpointAction.PIVOT_EXERCISES -> {
                result.exerciseEvolutionSuggestions.firstOrNull()?.let { suggestion ->
                    container.programRepository.logEvent(
                        dev.toastlabs.toastlift.data.ProgramEvent(
                            programId = program.id,
                            eventType = dev.toastlabs.toastlift.data.ProgramEventType.PLAN_PIVOTED,
                            payloadJson = """{"checkpointId":${checkpoint.id},"slotId":${suggestion.slotId},"suggestedExerciseId":${suggestion.suggestedExerciseId}}""",
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            CheckpointAction.EXTEND_BLOCK -> Unit
        }
    }

    private fun completeProgramIfFinished(programId: String) {
        val hasUpcoming = container.programRepository.currentPosition(programId) != null
        val hasPendingCheckpoint = container.programRepository.loadPendingCheckpoints(programId).isNotEmpty()
        if (hasUpcoming || hasPendingCheckpoint) return

        container.programRepository.updateProgramStatus(programId, ProgramStatus.COMPLETED)
        uiState = uiState.copy(
            showProgramWrapUp = true,
            message = "Program completed.",
        )
    }

    fun updateReadiness(transform: (ReadinessContext) -> ReadinessContext) {
        uiState = uiState.copy(programReadiness = transform(uiState.programReadiness))
    }

    private fun focusDisplayName(focus: String): String = programFocusLabel(SessionFocus.fromFocusKey(focus))

    fun openHistoryWorkout(workoutId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val detail = container.workoutRepository.loadHistoryDetail(workoutId) ?: return@launch
            uiState = uiState.copy(selectedHistoryDetail = detail)
        }
    }

    fun closeHistoryWorkout() {
        uiState = uiState.copy(selectedHistoryDetail = null)
    }

    fun deleteHistoryWorkout(workoutId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            container.workoutRepository.deleteHistoryWorkout(workoutId)
            refreshAll()
            uiState = uiState.copy(
                selectedHistoryDetail = uiState.selectedHistoryDetail?.takeUnless { it.id == workoutId },
                message = "Workout deleted from history.",
            )
        }
    }

    fun reuseHistoryWorkout(workoutId: Long, mode: HistoryReuseMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val reusableWorkout = container.workoutRepository.loadHistoryWorkoutForReuse(workoutId)
            if (reusableWorkout == null) {
                uiState = uiState.copy(message = "Could not reuse that workout.")
                return@launch
            }
            val profile = container.userRepository.loadProfile()
            val history = container.generatorRepository.loadHistoricalSetsForRecommendations()
            val locationModeId = profile?.activeLocationModeId ?: reusableWorkout.locationModeId
            val exercises = when (mode) {
                HistoryReuseMode.ExactCopy -> reusableWorkout.exercises
                HistoryReuseMode.RefreshPrescription -> reusableWorkout.exercises.map { exercise ->
                    refreshWorkoutExerciseForMyPlan(
                        exercise = exercise,
                        profile = profile,
                        history = history,
                    )
                }
            }
            val workoutTitle = historyReusePlanTitle(reusableWorkout.title, mode)
            val generatedWorkout = reusableWorkout.copy(
                title = workoutTitle,
                subtitle = historyReusePlanSubtitle(mode),
                locationModeId = locationModeId,
                origin = historyReusePlanOrigin(mode),
                exercises = exercises,
            )
            val projectedAnalytics = projectedAnalyticsFor(generatedWorkout)
            uiState = uiState.copy(
                generatedWorkout = generatedWorkout,
                projectedMuscleInsights = projectedAnalytics.muscles,
                projectedMovementInsights = projectedAnalytics.movements,
                selectedHistoryDetail = null,
                selectedTab = MainTab.Generate,
                message = historyReuseConfirmationMessage(workoutTitle, mode),
            )
        }
    }

    fun dismissMessage() {
        uiState = uiState.copy(message = null)
    }

    private fun persistActiveSessionState(
        session: ActiveSession? = uiState.activeSession,
        selectedExerciseIndex: Int? = uiState.activeSessionExerciseIndex,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (session == null) {
                container.workoutRepository.clearActiveSession()
            } else {
                container.workoutRepository.saveActiveSession(session, selectedExerciseIndex)
            }
        }
    }

    private fun generateActiveSessionExerciseSuggestion() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = uiState.activeSession
            if (session == null) {
                uiState = uiState.copy(
                    activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(
                        errorMessage = "Start a workout before asking for a generated exercise.",
                    ),
                )
                return@launch
            }
            val profile = container.userRepository.loadProfile()
            if (profile == null) {
                uiState = uiState.copy(
                    activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(
                        errorMessage = "Finish onboarding before generating an exercise.",
                    ),
                )
                return@launch
            }
            val splitProgram = uiState.splitPrograms.firstOrNull { it.id == profile.splitProgramId }
                ?: uiState.splitPrograms.firstOrNull()
            if (splitProgram == null) {
                uiState = uiState.copy(
                    activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(
                        errorMessage = "Could not load your split program.",
                    ),
                )
                return@launch
            }
            val requestContext = workoutGenerationRequestContext(session)
            val excludedExerciseIds = generatedActiveSessionExerciseExclusionIds(
                session = session,
                generatedState = uiState.activeSessionGeneratedExercise,
            )
            val generatedWorkout = runCatching {
                container.generatorRepository.generateWorkout(
                    profile = profile.copy(activeLocationModeId = session.locationModeId),
                    splitProgram = splitProgram,
                    locationModes = uiState.locationModes,
                    previousExerciseIds = excludedExerciseIds,
                    variationSeed = System.currentTimeMillis(),
                    requestedFocus = requestContext.requestedFocus,
                )
            }.getOrElse { error ->
                uiState = uiState.copy(
                    activeSessionGeneratedExercise = ActiveSessionGeneratedExerciseState(
                        errorMessage = error.message ?: "Could not generate an exercise right now.",
                    ),
                )
                return@launch
            }
            val suggestedExercise = generatedAdditionalSessionExercise(
                workout = generatedWorkout,
                excludedExerciseIds = excludedExerciseIds,
            )
            val currentGeneratedState = uiState.activeSessionGeneratedExercise
            uiState = uiState.copy(
                activeSessionGeneratedExercise = if (suggestedExercise != null) {
                    currentGeneratedState.copy(
                        exercise = suggestedExercise,
                        isLoading = false,
                        errorMessage = null,
                        seenExerciseIds = currentGeneratedState.seenExerciseIds + suggestedExercise.exerciseId,
                    )
                } else {
                    currentGeneratedState.copy(
                        exercise = null,
                        isLoading = false,
                        errorMessage = "No matching exercise was generated for this workout.",
                    )
                },
            )
        }
    }

    private fun appendExerciseToActiveSession(exercise: ExerciseSummary) {
        appendExercisesToActiveSession(listOf(exercise))
    }

    fun useExistingExerciseFromCustomFlow(exercise: ExerciseSummary) {
        queueExerciseForPickerSelection(exercise)
    }

    private fun queueExerciseForPickerSelection(exercise: ExerciseSummary) {
        uiState = uiState.copy(
            customExerciseDraft = null,
            customExerciseDestination = null,
            pendingAddExercisePickerSelection = exercise,
            message = "${exercise.name} ready to add.",
        )
        refreshLibrary()
    }

    private fun appendExercisesToActiveSession(exercises: List<ExerciseSummary>) {
        if (exercises.isEmpty()) return
        val goal = uiState.profile?.goal ?: uiState.onboardingDraft.goal
        appendWorkoutExercisesToActiveSession(
            exercises = exercises.map { exercise ->
                exercise.toWorkoutExercise(
                    goal = goal,
                    rationale = if (exercise.id >= 9_000_000_000_000_000L) {
                        "Custom exercise created during the active workout."
                    } else {
                        "Added manually from the exercise library."
                    },
                )
            },
        )
    }

    private fun appendWorkoutExercisesToActiveSession(exercises: List<WorkoutExercise>) {
        if (exercises.isEmpty()) return
        val session = uiState.activeSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val profile = uiState.profile
            val history = container.generatorRepository.loadHistoricalSetsForRecommendations()
            val additions = exercises.map { exercise -> buildSessionExercise(exercise, profile, history) }
            val updatedSession = ensureSessionFruitIcons(
                session.copy(exercises = session.exercises + additions),
            )
            uiState = uiState.copy(activeSession = updatedSession, activeSessionExerciseIndex = null)
            persistActiveSessionState(session = updatedSession, selectedExerciseIndex = null)
            if (session.origin == "generated") {
                additions.forEach { addedExercise ->
                    container.workoutRepository.recordActiveSessionFeedbackSignal(
                        session = session,
                        exercise = addedExercise,
                        signalType = WorkoutFeedbackSignalType.ACTIVE_SESSION_MANUAL_ADD,
                    )
                }
                refreshRecommendationBiasState()
            }
        }
    }

    private fun locationName(locationModeId: Long): String =
        uiState.locationModes.firstOrNull { it.id == locationModeId }?.displayName ?: "location"

    private fun ExerciseSummary.toWorkoutExercise(
        goal: String,
        rationale: String,
    ): WorkoutExercise {
        val repRange = when (goal) {
            "Strength" -> "4-6"
            "Hypertrophy" -> "8-12"
            "Conditioning" -> "12-20"
            else -> "6-10"
        }
        return WorkoutExercise(
            exerciseId = id,
            name = name,
            bodyRegion = bodyRegion,
            targetMuscleGroup = targetMuscleGroup,
            equipment = equipment,
            sets = 3,
            repRange = repRange,
            restSeconds = 75,
            rationale = rationale,
        )
    }

    private fun WorkoutPlan.withExercises(
        exercises: List<WorkoutExercise>,
        fallbackMinutes: Int?,
    ): WorkoutPlan {
        val updatedMinutes = when {
            exercises.isEmpty() -> 0
            this.exercises.isEmpty() -> fallbackMinutes ?: estimatedMinutes
            else -> ((estimatedMinutes.toDouble() / this.exercises.size) * exercises.size)
                .roundToInt()
                .coerceAtLeast(5)
        }
        return copy(
            exercises = exercises,
            estimatedMinutes = updatedMinutes,
        )
    }

    private fun buildActiveSession(workout: WorkoutPlan): ActiveSession {
        val profile = container.userRepository.loadProfile()
        val history = container.generatorRepository.loadHistoricalSetsForRecommendations()
        return ensureSessionFruitIcons(
            ActiveSession(
                title = workout.title,
                origin = workout.origin,
                locationModeId = workout.locationModeId,
                startedAtUtc = Instant.now().toString(),
                focusKey = workout.focusKey,
                subtitle = workout.subtitle,
                estimatedMinutes = workout.estimatedMinutes,
                sessionFormat = workout.sessionFormat,
                exercises = workout.exercises.map { exercise -> buildSessionExercise(exercise, profile, history) },
            ),
        )
    }

    private fun buildSessionExercise(
        exercise: WorkoutExercise,
        profile: UserProfile?,
        history: List<dev.toastlabs.toastlift.data.HistoricalExerciseSet>,
    ): SessionExercise {
        if (exercise.startingSets.isNotEmpty()) {
            return SessionExercise(
                exerciseId = exercise.exerciseId,
                name = exercise.name,
                bodyRegion = exercise.bodyRegion,
                targetMuscleGroup = exercise.targetMuscleGroup,
                equipment = exercise.equipment,
                restSeconds = exercise.restSeconds,
                sets = exercise.startingSets
                    .sortedBy(WorkoutExerciseSetDraft::setNumber)
                    .map { draft -> sessionSetFromHistoryReuseDraft(draft, exercise.repRange) },
            )
        }
        val prescription = prescribeExercise(
            exercise = exercise,
            profile = profile,
            history = history,
        )
        return SessionExercise(
            exerciseId = exercise.exerciseId,
            name = exercise.name,
            bodyRegion = exercise.bodyRegion,
            targetMuscleGroup = exercise.targetMuscleGroup,
            equipment = exercise.equipment,
            restSeconds = exercise.restSeconds,
            sets = (1..exercise.sets).map { setNo ->
                val initialWeight = dev.toastlabs.toastlift.data.formatRecommendedWeight(prescription.recommendedWeight)
                SessionSet(
                    setNumber = setNo,
                    targetReps = prescription.repRange,
                    recommendedReps = prescription.recommendedRepCount,
                    recommendedWeight = initialWeight,
                    reps = initialSessionRepsValue(
                        targetReps = prescription.repRange,
                        recommendedReps = prescription.recommendedRepCount,
                    ),
                    weight = initialWeight,
                    recommendationSource = prescription.source,
                    recommendationConfidence = prescription.confidence,
                )
            },
        )
    }

    private fun refreshWorkoutExerciseForMyPlan(
        exercise: WorkoutExercise,
        profile: UserProfile?,
        history: List<dev.toastlabs.toastlift.data.HistoricalExerciseSet>,
    ): WorkoutExercise {
        val baseExercise = exercise.copy(startingSets = emptyList())
        val prescription = prescribeExercise(
            exercise = baseExercise,
            profile = profile,
            history = history,
        )
        return baseExercise.copy(
            sets = prescription.setCount,
            repRange = prescription.repRange,
            suggestedWeight = prescription.recommendedWeight,
            rationale = "Refreshed from workout history using the current prescription path.",
        )
    }

    private fun prescribeExercise(
        exercise: WorkoutExercise,
        profile: UserProfile?,
        history: List<dev.toastlabs.toastlift.data.HistoricalExerciseSet>,
    ): dev.toastlabs.toastlift.data.ExercisePrescription {
        val detail = container.catalogRepository.getExerciseDetail(exercise.exerciseId)
        return exercisePrescriptionEngine.prescribe(
            dev.toastlabs.toastlift.data.ExercisePrescriptionRequest(
                workoutExercise = exercise,
                exerciseDetail = detail,
                profile = profile,
                history = history,
            ),
        )
    }

    private fun initialSessionRepsValue(targetReps: String, recommendedReps: Int?): String {
        return recommendedReps?.toString()
            ?: targetReps.substringBefore('-').trim().ifBlank { targetReps.trim() }
    }

    private fun refreshRecommendationBiasState(exerciseId: Long? = null) {
        val refreshedBiases = container.catalogRepository.loadRecommendationBiases()
        val selectedDetail = uiState.selectedExerciseDetail
        uiState = uiState.copy(
            recommendationBiasByExerciseId = refreshedBiases,
            selectedExerciseDetail = when {
                selectedDetail == null -> null
                exerciseId == null || selectedDetail.summary.id == exerciseId -> {
                    container.catalogRepository.getExerciseDetail(selectedDetail.summary.id)
                }
                else -> selectedDetail
            },
        )
    }
}

class ToastLiftViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ToastLiftViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ToastLiftViewModel(container) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
