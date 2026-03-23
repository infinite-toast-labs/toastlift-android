package dev.toastlabs.toastlift.data

import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val STRENGTH_SCORE_SMOOTHING_FACTOR = 0.35

internal data class StrengthScoreSetRow(
    val workoutId: Long,
    val workoutTitle: String,
    val completedAtUtc: String,
    val exerciseId: Long,
    val reps: Int?,
    val weight: Double?,
    val isCompleted: Boolean,
)

data class StrengthScorePoint(
    val workoutId: Long,
    val workoutTitle: String,
    val completedAtUtc: String,
    val sessionScore: Int,
    val runningScore: Int,
)

data class StrengthScoreSummary(
    val currentScore: Int,
    val previousScore: Int?,
    val bestScore: Int,
    val totalTrackedWorkouts: Int,
    val deltaFromPrevious: Int,
    val timeline: List<StrengthScorePoint>,
)

internal fun buildStrengthScoreSummary(rows: List<StrengthScoreSetRow>): StrengthScoreSummary? {
    if (rows.isEmpty()) return null

    var runningScore: Double? = null
    val timeline = rows
        .groupBy { Triple(it.workoutId, it.workoutTitle, it.completedAtUtc) }
        .toList()
        .sortedBy { (_, workoutRows) -> workoutRows.first().completedAtUtc }
        .mapNotNull { (header, workoutRows) ->
            val sessionScore = calculateSessionStrengthScore(workoutRows)
            if (sessionScore <= 0) return@mapNotNull null

            runningScore = runningScore?.let { previous ->
                previous + ((sessionScore - previous) * STRENGTH_SCORE_SMOOTHING_FACTOR)
            } ?: sessionScore.toDouble()

            StrengthScorePoint(
                workoutId = header.first,
                workoutTitle = header.second,
                completedAtUtc = header.third,
                sessionScore = sessionScore,
                runningScore = runningScore!!.roundToInt(),
            )
        }

    if (timeline.isEmpty()) return null

    val current = timeline.last()
    val previous = timeline.getOrNull(timeline.lastIndex - 1)
    return StrengthScoreSummary(
        currentScore = current.runningScore,
        previousScore = previous?.runningScore,
        bestScore = timeline.maxOf { it.runningScore },
        totalTrackedWorkouts = timeline.size,
        deltaFromPrevious = previous?.let { current.runningScore - it.runningScore } ?: 0,
        timeline = timeline,
    )
}

internal fun applyStrengthScores(
    history: List<HistorySummary>,
    strengthScoreSummary: StrengthScoreSummary?,
): List<HistorySummary> {
    val scoresByWorkoutId = strengthScoreSummary
        ?.timeline
        ?.associate { it.workoutId to it.runningScore }
        .orEmpty()

    if (scoresByWorkoutId.isEmpty()) return history

    return history.map { entry ->
        entry.copy(strengthScore = scoresByWorkoutId[entry.id])
    }
}

private fun calculateSessionStrengthScore(rows: List<StrengthScoreSetRow>): Int {
    val completedRows = rows.filter { it.isCompleted }
    if (completedRows.isEmpty()) return 0

    val bestEstimatedMaxByExercise = completedRows
        .groupBy { it.exerciseId }
        .mapNotNull { (_, exerciseRows) ->
            exerciseRows.mapNotNull { row ->
                val reps = row.reps?.takeIf { it > 0 }
                val weight = row.weight?.takeIf { it > 0.0 }
                if (reps == null || weight == null) {
                    null
                } else {
                    estimatedStrengthContribution(weight = weight, reps = reps)
                }
            }.maxOrNull()
        }
    val weightedVolume = completedRows.sumOf { row ->
        val reps = row.reps?.takeIf { it > 0 } ?: return@sumOf 0.0
        val weight = row.weight?.takeIf { it > 0.0 } ?: return@sumOf 0.0
        reps * weight
    }
    val repOnlyWork = completedRows.sumOf { row ->
        if ((row.weight ?: 0.0) > 0.0) {
            0
        } else {
            row.reps?.coerceAtLeast(0) ?: 0
        }
    }

    // Blend estimated top-end strength with completed workload, then smooth it workout-to-workout
    // so lower-intensity days do not whipsaw the progress signal.
    val rawScore = (bestEstimatedMaxByExercise.sum() * 0.25) +
        (sqrt(weightedVolume) * 5.0) +
        (sqrt(repOnlyWork.toDouble()) * 10.0) +
        (completedRows.size * 2.0)
    return rawScore.roundToInt().coerceAtLeast(0)
}

private fun estimatedStrengthContribution(weight: Double, reps: Int): Double {
    val effectiveReps = reps.coerceIn(1, 12)
    return weight * (1.0 + (effectiveReps / 30.0))
}
