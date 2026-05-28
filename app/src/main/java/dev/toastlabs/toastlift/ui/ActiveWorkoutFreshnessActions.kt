package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.ExerciseDetail
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionSet
import kotlin.math.ceil

internal enum class ActiveWorkoutFreshnessActionType {
    OpenExercise,
    OpenFilteredPicker,
}

internal data class ActiveWorkoutFreshnessAction(
    val type: ActiveWorkoutFreshnessActionType,
    val muscleKey: String,
    val muscleLabel: String,
    val family: TrainingFreshnessFamily,
    val freshnessStatus: TrainingFreshnessStatus,
    val title: String,
    val body: String,
    val ctaLabel: String,
    val exerciseIndex: Int? = null,
    val exerciseId: Long? = null,
    val exerciseName: String? = null,
    val completedWeightedSets: Double = 0.0,
    val expectedWeightedSets: Double = 0.0,
    val remainingWeightedSets: Double = 0.0,
)

private data class ActiveWorkoutFreshnessActionCandidate(
    val action: ActiveWorkoutFreshnessAction,
    val statusRank: Int,
    val actionRank: Int,
    val remainingWeightedSets: Double,
)

private data class MatchingFreshnessExercise(
    val index: Int,
    val exercise: SessionExercise,
    val contributionWeight: Double,
    val completedSets: Int,
) {
    val totalSets: Int = exercise.sets.size
    val isStarted: Boolean = completedSets > 0
    val isComplete: Boolean = totalSets > 0 && exercise.sets.all(SessionSet::completed)
}

internal fun buildActiveWorkoutFreshnessAction(
    session: ActiveSession,
    summary: ActiveWorkoutMuscleRefreshSummary,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
): ActiveWorkoutFreshnessAction? {
    return summary.rows
        .asSequence()
        .filter { row -> row.freshnessStatus.isActionableFreshnessStatus() }
        .filter { row -> row.state != ActiveWorkoutMuscleRefreshState.Refreshed }
        .map { row ->
            when (row.state) {
                ActiveWorkoutMuscleRefreshState.Pending -> {
                    val match = bestMatchingFreshnessExercise(
                        session = session,
                        exerciseDetailsById = exerciseDetailsById,
                        muscleKey = row.key,
                    )
                    if (match == null) {
                        pickerCandidate(row)
                    } else {
                        exerciseCandidate(row, match)
                    }
                }
                ActiveWorkoutMuscleRefreshState.NotTargeted -> pickerCandidate(row)
                ActiveWorkoutMuscleRefreshState.Refreshed -> null
            }
        }
        .filterNotNull()
        .sortedWith(
            compareBy<ActiveWorkoutFreshnessActionCandidate> { it.statusRank }
                .thenBy { it.actionRank }
                .thenBy { it.remainingWeightedSets }
                .thenBy { it.action.exerciseIndex ?: Int.MAX_VALUE },
        )
        .firstOrNull()
        ?.action
}

private fun exerciseCandidate(
    row: ActiveWorkoutMuscleRefreshRow,
    match: MatchingFreshnessExercise,
): ActiveWorkoutFreshnessActionCandidate {
    val statusWord = row.freshnessStatus.actionStatusWord()
    val title = "${row.label} ${row.labelVerb()} $statusWord"
    val remaining = (TRAINING_FRESHNESS_MUSCLE_RESET_WEIGHTED_SETS - row.completedWeightedSets)
        .coerceAtLeast(0.0)
    val body = when {
        match.isStarted && !match.isComplete && remaining <= match.contributionWeight -> {
            "One more set here gets ${row.label.lowercase()} across today's refresh line."
        }
        match.isStarted && !match.isComplete -> {
            val setCount = ceil(remaining / match.contributionWeight).toInt().coerceAtLeast(1)
            "$setCount more focused set${if (setCount == 1) "" else "s"} here keep ${row.label.lowercase()} moving."
        }
        match.isComplete -> {
            "${match.exercise.name} already contributes here. Add work only if the session still feels good."
        }
        else -> {
            "${match.exercise.name} is already in this workout. Start it before fatigue stacks up."
        }
    }
    val cta = when {
        match.isStarted && !match.isComplete -> "Continue ${match.exercise.name}"
        match.isComplete -> "Open ${match.exercise.name}"
        else -> "Open ${match.exercise.name}"
    }
    return ActiveWorkoutFreshnessActionCandidate(
        action = ActiveWorkoutFreshnessAction(
            type = ActiveWorkoutFreshnessActionType.OpenExercise,
            muscleKey = row.key,
            muscleLabel = row.label,
            family = row.family,
            freshnessStatus = row.freshnessStatus,
            title = title,
            body = body,
            ctaLabel = cta,
            exerciseIndex = match.index,
            exerciseId = match.exercise.exerciseId,
            exerciseName = match.exercise.name,
            completedWeightedSets = row.completedWeightedSets,
            expectedWeightedSets = row.expectedWeightedSets,
            remainingWeightedSets = remaining,
        ),
        statusRank = row.freshnessStatus.actionStatusRank(),
        actionRank = match.actionRank(),
        remainingWeightedSets = remaining,
    )
}

private fun pickerCandidate(row: ActiveWorkoutMuscleRefreshRow): ActiveWorkoutFreshnessActionCandidate {
    val statusWord = row.freshnessStatus.actionStatusWord()
    val title = "${row.label} ${row.labelVerb()} $statusWord"
    return ActiveWorkoutFreshnessActionCandidate(
        action = ActiveWorkoutFreshnessAction(
            type = ActiveWorkoutFreshnessActionType.OpenFilteredPicker,
            muscleKey = row.key,
            muscleLabel = row.label,
            family = row.family,
            freshnessStatus = row.freshnessStatus,
            title = title,
            body = "Today's plan does not cover ${row.label.lowercase()}. Filter the picker to add a quick match.",
            ctaLabel = "Find ${row.label} Exercise",
            completedWeightedSets = row.completedWeightedSets,
            expectedWeightedSets = row.expectedWeightedSets,
            remainingWeightedSets = TRAINING_FRESHNESS_MUSCLE_RESET_WEIGHTED_SETS,
        ),
        statusRank = row.freshnessStatus.actionStatusRank(),
        actionRank = 30,
        remainingWeightedSets = TRAINING_FRESHNESS_MUSCLE_RESET_WEIGHTED_SETS,
    )
}

private fun bestMatchingFreshnessExercise(
    session: ActiveSession,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
    muscleKey: String,
): MatchingFreshnessExercise? {
    return session.exercises
        .withIndex()
        .mapNotNull { indexedExercise ->
            val exercise = indexedExercise.value
            val contribution = resolveTrainingFreshnessContributions(
                exercise = exercise,
                detail = exerciseDetailsById[exercise.exerciseId],
            )
                .filter { it.key == muscleKey }
                .maxByOrNull { it.weight }
                ?: return@mapNotNull null
            MatchingFreshnessExercise(
                index = indexedExercise.index,
                exercise = exercise,
                contributionWeight = contribution.weight,
                completedSets = exercise.sets.count(SessionSet::completed),
            )
        }
        .sortedWith(
            compareBy<MatchingFreshnessExercise> { it.actionRank() }
                .thenByDescending { it.contributionWeight }
                .thenBy { it.index },
        )
        .firstOrNull()
}

private fun MatchingFreshnessExercise.actionRank(): Int = when {
    isStarted && !isComplete -> 0
    !isStarted -> 1
    else -> 2
}

private fun TrainingFreshnessStatus.isActionableFreshnessStatus(): Boolean =
    this == TrainingFreshnessStatus.Overdue || this == TrainingFreshnessStatus.DueSoon

private fun TrainingFreshnessStatus.actionStatusRank(): Int = when (this) {
    TrainingFreshnessStatus.Overdue -> 0
    TrainingFreshnessStatus.DueSoon -> 1
    TrainingFreshnessStatus.Fresh,
    TrainingFreshnessStatus.Untracked,
    -> 2
}

private fun TrainingFreshnessStatus.actionStatusWord(): String = when (this) {
    TrainingFreshnessStatus.Overdue -> "overdue"
    TrainingFreshnessStatus.DueSoon -> "due soon"
    TrainingFreshnessStatus.Fresh -> "fresh"
    TrainingFreshnessStatus.Untracked -> "untracked"
}

private fun ActiveWorkoutMuscleRefreshRow.labelVerb(): String {
    return if (label in pluralFreshnessMuscleLabels) "are" else "is"
}

private val pluralFreshnessMuscleLabels = setOf(
    "Shoulders",
    "Triceps",
    "Biceps",
    "Forearms",
    "Quadriceps",
    "Hamstrings",
    "Glutes",
    "Calves",
    "Adductors",
    "Abductors",
)
