package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.HistorySummary
import dev.toastlabs.toastlift.data.UserProfile
import dev.toastlabs.toastlift.data.WorkoutExercise
import dev.toastlabs.toastlift.data.WorkoutPlan
import dev.toastlabs.toastlift.data.generatedWorkoutFocusDisplayName
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

internal const val FRESHNESS_REENTRY_ORIGIN = "reentry"

private const val REENTRY_GAP_DAYS = 4L
private const val MAINTENANCE_GAP_DAYS = 2L
private const val REENTRY_LONG_GAP_DAYS = 7L

internal enum class FreshnessReEntryMode {
    MaintenanceSave,
    ReEntry,
}

internal data class FreshnessReEntryState(
    val mode: FreshnessReEntryMode,
    val gapDays: Long,
    val locationLabel: String,
    val focusKey: String,
    val suggestedDurationMinutes: Int,
    val headline: String,
    val supportingText: String,
    val ctaLabel: String,
    val targetLabels: List<String>,
)

internal fun buildFreshnessReEntryState(
    profile: UserProfile?,
    history: List<HistorySummary>,
    trainingFreshness: TrainingFreshnessSummary?,
    locationLabel: String,
    nowUtc: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): FreshnessReEntryState? {
    profile ?: return null
    trainingFreshness ?: return null

    val lastWorkoutDate = history
        .mapNotNull { summary ->
            runCatching { Instant.parse(summary.workoutOccurredAtUtc).atZone(zoneId).toLocalDate() }.getOrNull()
        }
        .maxOrNull()
        ?: return null
    val today = nowUtc.atZone(zoneId).toLocalDate()
    val gapDays = ChronoUnit.DAYS.between(lastWorkoutDate, today).coerceAtLeast(0)
    if (gapDays == 0L) return null

    val primaryRows = trainingFreshness.bucketRows.filter { it.family != TrainingFreshnessFamily.Core }
    val overduePrimaryRows = primaryRows.filter { it.status == TrainingFreshnessStatus.Overdue }
    val dueSoonPrimaryRows = primaryRows.filter { it.status == TrainingFreshnessStatus.DueSoon }
    val mode = when {
        gapDays >= REENTRY_GAP_DAYS -> FreshnessReEntryMode.ReEntry
        overduePrimaryRows.size >= 2 -> FreshnessReEntryMode.ReEntry
        gapDays >= 3L && overduePrimaryRows.isNotEmpty() -> FreshnessReEntryMode.ReEntry
        gapDays >= MAINTENANCE_GAP_DAYS -> FreshnessReEntryMode.MaintenanceSave
        dueSoonPrimaryRows.isNotEmpty() || overduePrimaryRows.isNotEmpty() -> FreshnessReEntryMode.MaintenanceSave
        else -> return null
    }

    val targetMuscles = trainingFreshness.muscleRows
        .filter { row ->
            row.status == TrainingFreshnessStatus.Overdue ||
                row.status == TrainingFreshnessStatus.DueSoon
        }
        .sortedWith(
            compareBy<TrainingFreshnessMuscleRow> { row ->
                when (row.status) {
                    TrainingFreshnessStatus.Overdue -> 0
                    TrainingFreshnessStatus.DueSoon -> 1
                    TrainingFreshnessStatus.Fresh -> 2
                    TrainingFreshnessStatus.Untracked -> 3
                }
            }
                .thenByDescending { it.hoursSinceStimulus ?: -1L }
                .thenBy { it.label },
        )
    val targetLabels = targetMuscles
        .map { it.label }
        .ifEmpty { (overduePrimaryRows + dueSoonPrimaryRows).map { it.label } }
        .distinct()
        .take(3)
    val focusKey = freshnessReEntryFocusKey(
        mode = mode,
        gapDays = gapDays,
        bucketRows = overduePrimaryRows.ifEmpty { dueSoonPrimaryRows },
        muscleRows = targetMuscles,
    )
    val durationMinutes = freshnessReEntryDurationMinutes(profile.durationMinutes, mode)
    val targetSummary = targetLabels.joinToNaturalLanguage().ifBlank {
        generatedWorkoutFocusDisplayName(focusKey).lowercase()
    }
    val effectiveLocationLabel = locationLabel.ifBlank { "current location" }
    val headline = when (mode) {
        FreshnessReEntryMode.ReEntry -> "$gapDays days since your last session."
        FreshnessReEntryMode.MaintenanceSave -> "Keep this gap small."
    }
    val supportingText = when (mode) {
        FreshnessReEntryMode.ReEntry ->
            "Restart with $targetSummary at $effectiveLocationLabel. No catch-up volume."
        FreshnessReEntryMode.MaintenanceSave ->
            "$durationMinutes minutes at $effectiveLocationLabel keeps the next session easy to start."
    }
    return FreshnessReEntryState(
        mode = mode,
        gapDays = gapDays,
        locationLabel = effectiveLocationLabel,
        focusKey = focusKey,
        suggestedDurationMinutes = durationMinutes,
        headline = headline,
        supportingText = supportingText,
        ctaLabel = when (mode) {
            FreshnessReEntryMode.ReEntry -> "Start Re-Entry Workout"
            FreshnessReEntryMode.MaintenanceSave -> "Start Maintenance Session"
        },
        targetLabels = targetLabels,
    )
}

internal fun shapeFreshnessReEntryWorkout(
    workout: WorkoutPlan,
    state: FreshnessReEntryState,
): WorkoutPlan {
    val maxExercises = when {
        state.mode == FreshnessReEntryMode.MaintenanceSave -> 2
        state.suggestedDurationMinutes >= 18 && state.focusKey == "full_body" -> 4
        state.suggestedDurationMinutes >= 15 -> 3
        else -> 2
    }
    val scoredExercises = workout.exercises
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<WorkoutExercise>> { indexed ->
                reEntryExerciseScore(indexed.value, state.targetLabels)
            }.thenBy { it.index },
        )
        .take(maxExercises)
        .sortedBy { it.index }
        .mapIndexed { index, indexed ->
            indexed.value.withReEntryVolume(
                exerciseIndex = index,
                mode = state.mode,
                durationMinutes = state.suggestedDurationMinutes,
            )
        }
    val focusLabel = generatedWorkoutFocusDisplayName(state.focusKey)
    return workout.copy(
        title = "${state.locationLabel} ${if (state.mode == FreshnessReEntryMode.ReEntry) "Re-Entry" else "Maintenance"}",
        subtitle = "$focusLabel - ${state.suggestedDurationMinutes} min - ${state.locationLabel}",
        estimatedMinutes = state.suggestedDurationMinutes,
        origin = FRESHNESS_REENTRY_ORIGIN,
        focusKey = state.focusKey,
        exercises = scoredExercises,
        sessionFormat = if (state.mode == FreshnessReEntryMode.ReEntry) {
            "Freshness Re-Entry"
        } else {
            "Momentum Maintenance"
        },
        decisionSummary = listOf(
            "Re-entry mode used ${state.locationLabel} equipment and trimmed volume so the session is easy to start.",
            "Freshness targets: ${state.targetLabels.joinToNaturalLanguage().ifBlank { focusLabel }}.",
        ) + workout.decisionSummary,
    )
}

internal fun currentReturnStreak(history: List<HistorySummary>): Int {
    return history
        .mapNotNull { summary ->
            runCatching { Instant.parse(summary.workoutOccurredAtUtc) }.getOrNull()?.let { occurredAt ->
                occurredAt to summary
            }
        }
        .sortedByDescending { (occurredAt, _) -> occurredAt }
        .map { (_, summary) -> summary }
        .takeWhile { it.origin == FRESHNESS_REENTRY_ORIGIN }
        .size
}

private fun freshnessReEntryFocusKey(
    mode: FreshnessReEntryMode,
    gapDays: Long,
    bucketRows: List<TrainingFreshnessBucketRow>,
    muscleRows: List<TrainingFreshnessMuscleRow>,
): String {
    val families = (bucketRows.map { it.family } + muscleRows.take(4).map { it.family })
        .filter { it != TrainingFreshnessFamily.Core }
        .toSet()
    return when {
        mode == FreshnessReEntryMode.ReEntry &&
            (gapDays >= REENTRY_LONG_GAP_DAYS || families.containsAll(setOf(TrainingFreshnessFamily.Upper, TrainingFreshnessFamily.Lower))) ->
            "full_body"
        TrainingFreshnessFamily.Lower in families -> "lower_body"
        TrainingFreshnessFamily.Upper in families -> "upper_body"
        else -> "full_body"
    }
}

private fun freshnessReEntryDurationMinutes(
    profileDurationMinutes: Int,
    mode: FreshnessReEntryMode,
): Int {
    val target = when (mode) {
        FreshnessReEntryMode.ReEntry -> (profileDurationMinutes * 0.45).roundToInt().coerceIn(12, 20)
        FreshnessReEntryMode.MaintenanceSave -> (profileDurationMinutes * 0.35).roundToInt().coerceIn(8, 12)
    }
    return target.coerceAtMost(profileDurationMinutes).coerceAtLeast(8)
}

private fun reEntryExerciseScore(exercise: WorkoutExercise, targetLabels: List<String>): Int {
    val haystack = listOf(exercise.name, exercise.targetMuscleGroup, exercise.bodyRegion)
        .joinToString(" ")
        .lowercase()
    return targetLabels.count { label -> label.lowercase() in haystack }
}

private fun WorkoutExercise.withReEntryVolume(
    exerciseIndex: Int,
    mode: FreshnessReEntryMode,
    durationMinutes: Int,
): WorkoutExercise {
    val setCap = when {
        mode == FreshnessReEntryMode.MaintenanceSave -> 2
        exerciseIndex == 0 && durationMinutes >= 16 -> 3
        else -> 2
    }
    val cappedStartingSets = startingSets.take(setCap)
    val cappedSetCount = when {
        cappedStartingSets.isNotEmpty() -> cappedStartingSets.size
        else -> sets.coerceAtMost(setCap).coerceAtLeast(1)
    }
    return copy(
        sets = cappedSetCount,
        overloadStrategy = "HOLD_STEADY",
        rationale = "$rationale Re-entry mode keeps this dose deliberately winnable.",
        startingSets = cappedStartingSets,
    )
}

private fun List<String>.joinToNaturalLanguage(): String {
    val clean = map { it.trim() }.filter { it.isNotBlank() }.distinct()
    return when (clean.size) {
        0 -> ""
        1 -> clean.single()
        2 -> "${clean[0]} and ${clean[1]}"
        else -> clean.dropLast(1).joinToString(", ") + ", and " + clean.last()
    }
}
