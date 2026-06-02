package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.ExerciseDetail
import dev.toastlabs.toastlift.data.MuscleTargetBucketKey
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionSet
import dev.toastlabs.toastlift.data.muscleTargetBucketLabel
import dev.toastlabs.toastlift.data.muscleTargetBuckets
import dev.toastlabs.toastlift.data.muscleTargetSubcategoriesForBucket
import dev.toastlabs.toastlift.data.muscleTargetSubcategoryLabel
import dev.toastlabs.toastlift.data.resolveMuscleTargetContributions
import kotlin.math.abs

internal enum class ActiveWorkoutMuscleTargetState {
    NotPlanned,
    Planned,
    InProgress,
    Covered,
    OverCovered,
}

internal enum class MuscleTargetWorkoutLens(val label: String) {
    UpperBalance("Upper balance"),
    LowerBalance("Leg balance"),
    FullBodyBalance("Full-body balance"),
}

internal data class ActiveWorkoutMuscleTargetSubcategoryRow(
    val key: String,
    val label: String,
    val bucketKey: String,
    val plannedWeightedSets: Double,
    val completedWeightedSets: Double,
    val remainingPlannedWeightedSets: Double,
    val progressFraction: Float,
    val state: ActiveWorkoutMuscleTargetState,
)

internal data class ActiveWorkoutMuscleTargetBucketRow(
    val key: String,
    val label: String,
    val plannedWeightedSets: Double,
    val completedWeightedSets: Double,
    val remainingPlannedWeightedSets: Double,
    val progressFraction: Float,
    val state: ActiveWorkoutMuscleTargetState,
    val subcategories: List<ActiveWorkoutMuscleTargetSubcategoryRow>,
)

internal data class ActiveWorkoutMuscleTargetCoverageSummary(
    val bucketRows: List<ActiveWorkoutMuscleTargetBucketRow>,
    val lens: MuscleTargetWorkoutLens,
    val balanceLabel: String,
    val cueLabel: String?,
    val actionTargetBucketKey: String?,
    val actionTargetSubcategoryKey: String?,
) {
    val plannedBucketCount: Int = bucketRows.count { it.plannedWeightedSets > 0.0 }
    val coveredBucketCount: Int = bucketRows.count { it.state == ActiveWorkoutMuscleTargetState.Covered || it.state == ActiveWorkoutMuscleTargetState.OverCovered }
}

internal enum class ActiveWorkoutMuscleTargetActionType {
    OpenExercise,
    OpenFilteredPicker,
}

internal data class ActiveWorkoutMuscleTargetAction(
    val type: ActiveWorkoutMuscleTargetActionType,
    val bucketKey: String,
    val subcategoryKey: String?,
    val targetLabel: String,
    val title: String,
    val body: String,
    val ctaLabel: String,
    val exerciseIndex: Int? = null,
    val exerciseId: Long? = null,
    val exerciseName: String? = null,
)

private data class ActiveWorkoutMuscleTargetAccumulator(
    var plannedWeightedSets: Double = 0.0,
    var completedWeightedSets: Double = 0.0,
)

private data class MatchingMuscleTargetExercise(
    val index: Int,
    val exercise: SessionExercise,
    val contributionWeight: Double,
    val completedSets: Int,
) {
    val totalSets: Int = exercise.sets.size
    val isStarted: Boolean = completedSets > 0
    val isComplete: Boolean = totalSets > 0 && exercise.sets.all(SessionSet::completed)
}

internal fun buildActiveWorkoutMuscleTargetCoverageSummary(
    session: ActiveSession,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
): ActiveWorkoutMuscleTargetCoverageSummary {
    val bucketAccumulators = muscleTargetBuckets()
        .associate { bucket -> bucket.key to ActiveWorkoutMuscleTargetAccumulator() }
        .toMutableMap()
    val subcategoryAccumulators = muscleTargetBuckets()
        .flatMap { bucket -> muscleTargetSubcategoriesForBucket(bucket.key) }
        .associate { subcategory -> subcategory.key to ActiveWorkoutMuscleTargetAccumulator() }
        .toMutableMap()

    session.exercises.forEach { exercise ->
        val plannedSetCount = exercise.sets.size
        val completedSetCount = exercise.sets.count(SessionSet::completed)
        val contributions = resolveMuscleTargetContributions(
            exercise = exercise,
            detail = exerciseDetailsById[exercise.exerciseId],
        )
        contributions.forEach { contribution ->
            bucketAccumulators[contribution.bucketKey]?.let { accumulator ->
                accumulator.plannedWeightedSets += plannedSetCount * contribution.weight
                accumulator.completedWeightedSets += completedSetCount * contribution.weight
            }
            subcategoryAccumulators[contribution.subcategoryKey]?.let { accumulator ->
                accumulator.plannedWeightedSets += plannedSetCount * contribution.weight
                accumulator.completedWeightedSets += completedSetCount * contribution.weight
            }
        }
    }

    val bucketRows = muscleTargetBuckets().map { bucket ->
        val accumulator = bucketAccumulators.getValue(bucket.key)
        val subcategoryRows = muscleTargetSubcategoriesForBucket(bucket.key).map { subcategory ->
            val subcategoryAccumulator = subcategoryAccumulators.getValue(subcategory.key)
            ActiveWorkoutMuscleTargetSubcategoryRow(
                key = subcategory.key,
                label = subcategory.label,
                bucketKey = bucket.key,
                plannedWeightedSets = subcategoryAccumulator.plannedWeightedSets,
                completedWeightedSets = subcategoryAccumulator.completedWeightedSets,
                remainingPlannedWeightedSets = (subcategoryAccumulator.plannedWeightedSets - subcategoryAccumulator.completedWeightedSets).coerceAtLeast(0.0),
                progressFraction = muscleTargetProgressFraction(subcategoryAccumulator),
                state = muscleTargetState(subcategoryAccumulator),
            )
        }
        ActiveWorkoutMuscleTargetBucketRow(
            key = bucket.key,
            label = "${bucket.label} Muscles",
            plannedWeightedSets = accumulator.plannedWeightedSets,
            completedWeightedSets = accumulator.completedWeightedSets,
            remainingPlannedWeightedSets = (accumulator.plannedWeightedSets - accumulator.completedWeightedSets).coerceAtLeast(0.0),
            progressFraction = muscleTargetProgressFraction(accumulator),
            state = muscleTargetState(accumulator),
            subcategories = subcategoryRows,
        )
    }

    val lens = inferMuscleTargetWorkoutLens(session, bucketRows)
    val balance = muscleTargetBalanceCue(lens, bucketRows)
    return ActiveWorkoutMuscleTargetCoverageSummary(
        bucketRows = bucketRows,
        lens = lens,
        balanceLabel = balance.balanceLabel,
        cueLabel = balance.cueLabel,
        actionTargetBucketKey = balance.actionTargetBucketKey,
        actionTargetSubcategoryKey = balance.actionTargetSubcategoryKey,
    )
}

internal fun buildActiveWorkoutMuscleTargetAction(
    session: ActiveSession,
    summary: ActiveWorkoutMuscleTargetCoverageSummary,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
): ActiveWorkoutMuscleTargetAction? {
    val targetBucket = summary.actionTargetBucketKey ?: return null
    val targetSubcategory = summary.actionTargetSubcategoryKey
    val targetLabel = if (targetSubcategory == null) {
        muscleTargetBucketLabel(targetBucket)
    } else {
        muscleTargetSubcategoryLabel(targetSubcategory)
    }
    val match = bestMatchingMuscleTargetExercise(
        session = session,
        exerciseDetailsById = exerciseDetailsById,
        bucketKey = targetBucket,
        subcategoryKey = targetSubcategory,
    )
    return if (match == null) {
        ActiveWorkoutMuscleTargetAction(
            type = ActiveWorkoutMuscleTargetActionType.OpenFilteredPicker,
            bucketKey = targetBucket,
            subcategoryKey = targetSubcategory,
            targetLabel = targetLabel,
            title = "Add $targetLabel work",
            body = "Filter the picker to find an exercise that helps this workout's balance.",
            ctaLabel = "Find $targetLabel",
        )
    } else {
        val body = when {
            match.isStarted && !match.isComplete -> "More sets here help close the current balance gap."
            match.isComplete -> "${match.exercise.name} already contributes here. Open it if you want extra work."
            else -> "${match.exercise.name} is already in this workout and matches the current target."
        }
        ActiveWorkoutMuscleTargetAction(
            type = ActiveWorkoutMuscleTargetActionType.OpenExercise,
            bucketKey = targetBucket,
            subcategoryKey = targetSubcategory,
            targetLabel = targetLabel,
            title = "Work $targetLabel next",
            body = body,
            ctaLabel = "Open ${match.exercise.name}",
            exerciseIndex = match.index,
            exerciseId = match.exercise.exerciseId,
            exerciseName = match.exercise.name,
        )
    }
}

private data class MuscleTargetBalanceCue(
    val balanceLabel: String,
    val cueLabel: String?,
    val actionTargetBucketKey: String?,
    val actionTargetSubcategoryKey: String?,
)

private fun muscleTargetBalanceCue(
    lens: MuscleTargetWorkoutLens,
    bucketRows: List<ActiveWorkoutMuscleTargetBucketRow>,
): MuscleTargetBalanceCue {
    return when (lens) {
        MuscleTargetWorkoutLens.UpperBalance -> upperMuscleTargetBalanceCue(bucketRows)
        MuscleTargetWorkoutLens.LowerBalance -> lowerMuscleTargetBalanceCue(bucketRows)
        MuscleTargetWorkoutLens.FullBodyBalance -> fullBodyMuscleTargetBalanceCue(bucketRows)
    }
}

private fun upperMuscleTargetBalanceCue(bucketRows: List<ActiveWorkoutMuscleTargetBucketRow>): MuscleTargetBalanceCue {
    val push = bucketRows.firstOrNull { it.key == MuscleTargetBucketKey.Push.storageKey }
    val pull = bucketRows.firstOrNull { it.key == MuscleTargetBucketKey.Pull.storageKey }
    if (push == null || pull == null) {
        return MuscleTargetBalanceCue("Upper balance unavailable", null, null, null)
    }
    val pushSets = push.completedWeightedSets.takeIf { it > 0.0 } ?: push.plannedWeightedSets
    val pullSets = pull.completedWeightedSets.takeIf { it > 0.0 } ?: pull.plannedWeightedSets
    val gap = abs(pushSets - pullSets)
    if (gap < MUSCLE_TARGET_BALANCE_GAP_THRESHOLD_SETS) {
        return MuscleTargetBalanceCue("Push/Pull balanced", "Push and pull are within 2 weighted sets.", null, null)
    }
    val laggingBucket = if (pushSets < pullSets) push else pull
    val leadingBucket = if (laggingBucket == push) pull else push
    return MuscleTargetBalanceCue(
        balanceLabel = "${laggingBucket.label.removeSuffix(" Muscles")} trailing",
        cueLabel = "${laggingBucket.label.removeSuffix(" Muscles")} trails ${leadingBucket.label.removeSuffix(" Muscles")} by ${muscleTargetSetCountLabel(gap)}.",
        actionTargetBucketKey = laggingBucket.key,
        actionTargetSubcategoryKey = laggingBucket.subcategories
            .filter { it.plannedWeightedSets > 0.0 || it.completedWeightedSets > 0.0 }
            .minByOrNull { it.completedWeightedSets }
            ?.key,
    )
}

private fun lowerMuscleTargetBalanceCue(bucketRows: List<ActiveWorkoutMuscleTargetBucketRow>): MuscleTargetBalanceCue {
    val legs = bucketRows.firstOrNull { it.key == MuscleTargetBucketKey.Legs.storageKey }
        ?: return MuscleTargetBalanceCue("Leg balance unavailable", null, null, null)
    val primaryRows = legs.subcategories
        .filter { it.key in PRIMARY_LEG_TARGET_KEYS }
        .filter { it.plannedWeightedSets > 0.0 || it.completedWeightedSets > 0.0 }
    if (primaryRows.size < 2) {
        return MuscleTargetBalanceCue("Leg targets starting", null, null, null)
    }
    val leading = primaryRows.maxByOrNull { it.completedWeightedSets.takeIf { sets -> sets > 0.0 } ?: it.plannedWeightedSets }
        ?: return MuscleTargetBalanceCue("Leg balance unavailable", null, null, null)
    val lagging = primaryRows.minByOrNull { it.completedWeightedSets.takeIf { sets -> sets > 0.0 } ?: it.plannedWeightedSets }
        ?: return MuscleTargetBalanceCue("Leg balance unavailable", null, null, null)
    val leadingSets = leading.completedWeightedSets.takeIf { it > 0.0 } ?: leading.plannedWeightedSets
    val laggingSets = lagging.completedWeightedSets.takeIf { it > 0.0 } ?: lagging.plannedWeightedSets
    val gap = (leadingSets - laggingSets).coerceAtLeast(0.0)
    if (gap < MUSCLE_TARGET_BALANCE_GAP_THRESHOLD_SETS) {
        return MuscleTargetBalanceCue("Leg categories balanced", "Quads, hamstrings, and glutes are within 2 weighted sets.", null, null)
    }
    return MuscleTargetBalanceCue(
        balanceLabel = "${lagging.label} trailing",
        cueLabel = "${lagging.label} trails ${leading.label} by ${muscleTargetSetCountLabel(gap)}.",
        actionTargetBucketKey = MuscleTargetBucketKey.Legs.storageKey,
        actionTargetSubcategoryKey = lagging.key,
    )
}

private fun fullBodyMuscleTargetBalanceCue(bucketRows: List<ActiveWorkoutMuscleTargetBucketRow>): MuscleTargetBalanceCue {
    val plannedRows = bucketRows.filter { it.plannedWeightedSets > 0.0 || it.completedWeightedSets > 0.0 }
    if (plannedRows.size < 2) {
        return MuscleTargetBalanceCue("Targets starting", null, null, null)
    }
    val leading = plannedRows.maxByOrNull { it.completedWeightedSets.takeIf { sets -> sets > 0.0 } ?: it.plannedWeightedSets }
        ?: return MuscleTargetBalanceCue("Targets starting", null, null, null)
    val lagging = plannedRows.minByOrNull { it.completedWeightedSets.takeIf { sets -> sets > 0.0 } ?: it.plannedWeightedSets }
        ?: return MuscleTargetBalanceCue("Targets starting", null, null, null)
    val leadingSets = leading.completedWeightedSets.takeIf { it > 0.0 } ?: leading.plannedWeightedSets
    val laggingSets = lagging.completedWeightedSets.takeIf { it > 0.0 } ?: lagging.plannedWeightedSets
    val gap = (leadingSets - laggingSets).coerceAtLeast(0.0)
    if (gap < MUSCLE_TARGET_BALANCE_GAP_THRESHOLD_SETS) {
        return MuscleTargetBalanceCue("Targets balanced", "Planned target buckets are within 2 weighted sets.", null, null)
    }
    return MuscleTargetBalanceCue(
        balanceLabel = "${lagging.label.removeSuffix(" Muscles")} trailing",
        cueLabel = "${lagging.label.removeSuffix(" Muscles")} trails ${leading.label.removeSuffix(" Muscles")} by ${muscleTargetSetCountLabel(gap)}.",
        actionTargetBucketKey = lagging.key,
        actionTargetSubcategoryKey = null,
    )
}

private fun inferMuscleTargetWorkoutLens(
    session: ActiveSession,
    bucketRows: List<ActiveWorkoutMuscleTargetBucketRow>,
): MuscleTargetWorkoutLens {
    val text = listOfNotNull(session.title, session.subtitle, session.focusKey)
        .joinToString(" ")
        .lowercase()
    return when {
        listOf("lower", "leg", "quad", "hamstring", "glute").any { it in text } -> MuscleTargetWorkoutLens.LowerBalance
        listOf("upper", "push", "pull", "chest", "back", "shoulder").any { it in text } -> MuscleTargetWorkoutLens.UpperBalance
        else -> {
            val plannedByKey = bucketRows.associate { row -> row.key to row.plannedWeightedSets }
            val legs = plannedByKey[MuscleTargetBucketKey.Legs.storageKey] ?: 0.0
            val upper = (plannedByKey[MuscleTargetBucketKey.Push.storageKey] ?: 0.0) +
                (plannedByKey[MuscleTargetBucketKey.Pull.storageKey] ?: 0.0)
            when {
                legs > upper * 0.8 -> MuscleTargetWorkoutLens.LowerBalance
                upper > 0.0 -> MuscleTargetWorkoutLens.UpperBalance
                else -> MuscleTargetWorkoutLens.FullBodyBalance
            }
        }
    }
}

private fun bestMatchingMuscleTargetExercise(
    session: ActiveSession,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
    bucketKey: String,
    subcategoryKey: String?,
): MatchingMuscleTargetExercise? {
    return session.exercises
        .withIndex()
        .mapNotNull { indexedExercise ->
            val exercise = indexedExercise.value
            val contribution = resolveMuscleTargetContributions(
                exercise = exercise,
                detail = exerciseDetailsById[exercise.exerciseId],
            )
                .filter { contribution ->
                    contribution.bucketKey == bucketKey &&
                        (subcategoryKey == null || contribution.subcategoryKey == subcategoryKey)
                }
                .maxByOrNull { it.weight }
                ?: return@mapNotNull null
            MatchingMuscleTargetExercise(
                index = indexedExercise.index,
                exercise = exercise,
                contributionWeight = contribution.weight,
                completedSets = exercise.sets.count(SessionSet::completed),
            )
        }
        .sortedWith(
            compareBy<MatchingMuscleTargetExercise> { it.actionRank() }
                .thenByDescending { it.contributionWeight }
                .thenBy { it.index },
        )
        .firstOrNull()
}

private fun MatchingMuscleTargetExercise.actionRank(): Int = when {
    isStarted && !isComplete -> 0
    !isStarted -> 1
    else -> 2
}

private fun muscleTargetState(accumulator: ActiveWorkoutMuscleTargetAccumulator): ActiveWorkoutMuscleTargetState {
    return when {
        accumulator.plannedWeightedSets <= 0.0 -> ActiveWorkoutMuscleTargetState.NotPlanned
        accumulator.completedWeightedSets <= 0.0 -> ActiveWorkoutMuscleTargetState.Planned
        accumulator.completedWeightedSets >= accumulator.plannedWeightedSets + MUSCLE_TARGET_OVERCOVERED_MARGIN_SETS -> ActiveWorkoutMuscleTargetState.OverCovered
        accumulator.completedWeightedSets >= accumulator.plannedWeightedSets -> ActiveWorkoutMuscleTargetState.Covered
        else -> ActiveWorkoutMuscleTargetState.InProgress
    }
}

private fun muscleTargetProgressFraction(accumulator: ActiveWorkoutMuscleTargetAccumulator): Float {
    return if (accumulator.plannedWeightedSets <= 0.0) {
        0f
    } else {
        (accumulator.completedWeightedSets / accumulator.plannedWeightedSets)
            .toFloat()
            .coerceIn(0f, 1f)
    }
}

private fun muscleTargetSetCountLabel(value: Double): String {
    val formatted = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
    return "$formatted weighted set${if (formatted == "1") "" else "s"}"
}

private val PRIMARY_LEG_TARGET_KEYS = setOf("quadriceps", "hamstrings", "glutes")
private const val MUSCLE_TARGET_BALANCE_GAP_THRESHOLD_SETS = 2.0
private const val MUSCLE_TARGET_OVERCOVERED_MARGIN_SETS = 1.0
