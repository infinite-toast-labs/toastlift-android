package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.AdherenceCurrencyTrend
import dev.toastlabs.toastlift.data.CompletionReceiptAccountingSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptComparisonKind
import dev.toastlabs.toastlift.data.CompletionReceiptComparisonRowSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptComparisonSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptAchievementSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptFrictionPromptSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptHighlightKind
import dev.toastlabs.toastlift.data.CompletionReceiptHighlightSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptLearningSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptProgramFeelRowSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptReferenceSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptReferenceType
import dev.toastlabs.toastlift.data.CompletionReceiptSplitProgressRowSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptSplitProgressSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptStatSnapshot
import dev.toastlabs.toastlift.data.CompletionReceiptStatsRailSnapshot
import dev.toastlabs.toastlift.data.ExerciseHistoryDetail
import dev.toastlabs.toastlift.data.HistoryDetail
import dev.toastlabs.toastlift.data.HistorySummary
import dev.toastlabs.toastlift.data.PlannedSession
import dev.toastlabs.toastlift.data.ProgramCompletionTruth
import dev.toastlabs.toastlift.data.ReceiptReferenceCandidate
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionOutcomeTier
import dev.toastlabs.toastlift.data.SessionSet
import dev.toastlabs.toastlift.data.SkippedExerciseFeedbackPrompt
import dev.toastlabs.toastlift.data.WeeklyPromiseSnapshot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal data class CompletionReceiptUiState(
    val snapshot: dev.toastlabs.toastlift.data.CompletionReceiptSnapshot,
    val isReplay: Boolean = false,
    val debugSurface: String? = null,
    val debugScenario: String? = null,
)

data class CompletionReceiptDebugLaunch(
    val surface: String,
    val scenario: String,
)

internal data class TodayReceiptRecapState(
    val todayWorkoutIds: List<Long>,
    val latestWorkoutId: Long?,
    val latestWorkoutTitle: String?,
    val latestEvidenceLine: String?,
    val latestMeaningLine: String?,
    val workoutCountToday: Int,
    val debugSurface: String? = null,
    val debugScenario: String? = null,
)

internal data class ReceiptTopSet(
    val exerciseId: Long,
    val exerciseName: String,
    val weight: Double,
    val reps: Int,
)

internal fun computeCompletedSetCount(session: ActiveSession): Int {
    return session.exercises.sumOf { exercise -> exercise.sets.count(SessionSet::completed) }
}

internal fun computePlannedSetCount(session: ActiveSession): Int {
    return session.exercises.sumOf { it.sets.size }
}

internal fun computeCompletedExerciseCount(session: ActiveSession): Int {
    return session.exercises.count { exercise -> exercise.sets.any(SessionSet::completed) }
}

internal fun computeCompletedRepCount(session: ActiveSession): Int {
    return session.exercises.sumOf { exercise ->
        exercise.sets.sumOf { set ->
            if (!set.completed) {
                0
            } else {
                set.reps.toIntOrNull() ?: 0
            }
        }
    }
}

internal fun computeSessionVolume(session: ActiveSession): Double {
    return session.exercises.sumOf { exercise ->
        exercise.sets.sumOf { set ->
            if (!set.completed) {
                0.0
            } else {
                val reps = set.reps.toIntOrNull()
                val weight = set.weight.toDoubleOrNull()
                if (reps != null && weight != null) reps * weight else 0.0
            }
        }
    }
}

internal fun computeSessionCompletionAccounting(
    session: ActiveSession,
): CompletionReceiptAccountingSnapshot {
    val completedSets = computeCompletedSetCount(session)
    val plannedSets = computePlannedSetCount(session)
    val ratio = if (plannedSets <= 0) 1.0 else completedSets.toDouble() / plannedSets.toDouble()
    return CompletionReceiptAccountingSnapshot(
        completionRatio = ratio.coerceAtLeast(0.0),
        completionCredit = ratio.coerceIn(0.0, 1.0),
    )
}

internal fun computeProgramCompletionAccounting(
    session: ActiveSession,
    plannedSession: PlannedSession?,
): CompletionReceiptAccountingSnapshot {
    val baseAccounting = if (plannedSession?.plannedSets?.takeIf { it > 0 } != null) {
        val completedSets = computeCompletedSetCount(session)
        val plannedSets = plannedSession.plannedSets
        val ratio = if (plannedSets <= 0) 1.0 else completedSets.toDouble() / plannedSets.toDouble()
        CompletionReceiptAccountingSnapshot(
            completionRatio = ratio.coerceAtLeast(0.0),
            completionCredit = ratio.coerceIn(0.0, 1.0),
        )
    } else {
        computeSessionCompletionAccounting(session)
    }
    val truth = when {
        baseAccounting.completionRatio >= 0.85 -> ProgramCompletionTruth.ON_PLAN
        baseAccounting.completionRatio >= 0.40 -> ProgramCompletionTruth.PARTIAL_CREDIT
        else -> ProgramCompletionTruth.MINIMUM_DOSE
    }
    return baseAccounting.copy(
        truth = truth,
    )
}

internal fun completionReceiptTokenDelta(
    beforeTokenTrend: AdherenceCurrencyTrend?,
    afterTokenTrend: AdherenceCurrencyTrend?,
): Int? {
    if (afterTokenTrend == null) return null
    return afterTokenTrend.snapshot.balance - (beforeTokenTrend?.snapshot?.balance ?: 0)
}

internal fun determineOutcomeTier(
    session: ActiveSession,
    accounting: CompletionReceiptAccountingSnapshot?,
): SessionOutcomeTier {
    val completedSets = computeCompletedSetCount(session)
    val plannedSets = computePlannedSetCount(session)
    val ratio = accounting?.completionRatio ?: if (plannedSets == 0) 1.0 else completedSets.toDouble() / plannedSets.toDouble()
    return when {
        plannedSets > 0 && completedSets >= plannedSets -> SessionOutcomeTier.CLOSED_CLEAN
        ratio >= 0.60 -> SessionOutcomeTier.SOLID_SESSION
        completedSets > 0 -> SessionOutcomeTier.MEANINGFUL_PARTIAL
        else -> SessionOutcomeTier.SHOWED_UP
    }
}

internal fun outcomeTierLabel(tier: SessionOutcomeTier): String = when (tier) {
    SessionOutcomeTier.CLOSED_CLEAN -> "Closed Clean"
    SessionOutcomeTier.SOLID_SESSION -> "Solid Session"
    SessionOutcomeTier.MEANINGFUL_PARTIAL -> "Meaningful Partial"
    SessionOutcomeTier.SHOWED_UP -> "Showed Up"
}

internal fun buildDefaultLearningSnapshot(
    session: ActiveSession,
    programExerciseIds: Set<Long>,
    skippedPrompt: SkippedExerciseFeedbackPrompt?,
): CompletionReceiptLearningSnapshot? {
    val feelRows = session.exercises
        .filter { it.exerciseId in programExerciseIds }
        .distinctBy(SessionExercise::exerciseId)
        .map { exercise ->
            CompletionReceiptProgramFeelRowSnapshot(
                exerciseId = exercise.exerciseId,
                exerciseName = exercise.name,
            )
        }
    val frictionPrompt = skippedPrompt?.let {
        CompletionReceiptFrictionPromptSnapshot(
            skippedExerciseId = it.exerciseId,
            skippedExerciseName = it.exerciseName,
        )
    }
    if (feelRows.isEmpty() && frictionPrompt == null) return null
    return CompletionReceiptLearningSnapshot(
        programFeelRows = feelRows,
        frictionPrompt = frictionPrompt,
    )
}

internal fun buildReceiptAchievementSnapshot(
    session: ActiveSession,
    workoutTitle: String,
    completedAtUtc: String,
    exerciseHistories: List<ExerciseHistoryDetail>,
): CompletionReceiptAchievementSnapshot {
    val chips = buildList {
        exerciseHistories.forEach { history ->
            val currentEntry = history.entries.firstOrNull { entry ->
                entry.workoutTitle == workoutTitle && entry.completedAtUtc == completedAtUtc
            } ?: history.entries.firstOrNull()
            if (currentEntry == null) return@forEach

            if (currentEntry.workingSets.any { it.isWeightPr } && currentEntry.bestWeight > 0.0) {
                add("${history.exerciseName} ${trimWeight(currentEntry.bestWeight)} lb PR")
            }
            if (currentEntry.workingSets.any { it.isRepPr }) {
                add("${history.exerciseName} Rep PR")
            }
            if (currentEntry.workingSets.any { it.isVolumePr }) {
                add("${history.exerciseName} Volume PR")
            }
        }
    }

    if (chips.isNotEmpty()) {
        return CompletionReceiptAchievementSnapshot(
            title = if (chips.size == 1) "1 PR Broken" else "${chips.size} PRs Broken",
            prCount = chips.size,
            chips = chips,
            supportingText = "New bests from this workout are now saved to history.",
        )
    }

    val topSet = topSetForSession(session)
    return CompletionReceiptAchievementSnapshot(
        title = "Best Set Today",
        prCount = 0,
        chips = emptyList(),
        fallbackLabel = topSet?.exerciseName ?: "Exercises closed",
        fallbackValue = topSet?.let { "${trimWeight(it.weight)} x ${it.reps}" }
            ?: "${computeCompletedExerciseCount(session)}/${session.exercises.size}",
        supportingText = if (topSet != null) {
            "No new PRs, but this was the strongest set you logged today."
        } else {
            "No new PRs this time, but the workout still closed useful work."
        },
    )
}

internal fun buildReceiptSplitProgressSnapshot(
    beforeWeeklyTargets: WeeklyMuscleTargetSummary?,
    afterWeeklyTargets: WeeklyMuscleTargetSummary?,
): CompletionReceiptSplitProgressSnapshot? {
    if (beforeWeeklyTargets == null || afterWeeklyTargets == null) return null

    val beforeGroups = beforeWeeklyTargets.groupSummaries.associateBy { it.key }
    val afterGroups = afterWeeklyTargets.groupSummaries.associateBy { it.key }
    val rows = listOf("push", "pull", "legs").mapNotNull { key ->
        val beforeGroup = beforeGroups[key]
        val afterGroup = afterGroups[key]
        val targetSets = afterGroup?.targetSets ?: beforeGroup?.targetSets ?: 0.0
        val beforeCompletedSets = beforeGroup?.completedSets ?: 0.0
        val afterCompletedSets = afterGroup?.completedSets ?: 0.0
        if (targetSets <= 0.0 && beforeCompletedSets <= 0.0 && afterCompletedSets <= 0.0) {
            null
        } else {
            CompletionReceiptSplitProgressRowSnapshot(
                key = key,
                label = afterGroup?.label ?: beforeGroup?.label ?: key.replaceFirstChar { it.uppercase() },
                beforeCompletedSets = beforeCompletedSets,
                afterCompletedSets = afterCompletedSets,
                targetSets = targetSets,
            )
        }
    }

    if (rows.isEmpty()) return null

    return CompletionReceiptSplitProgressSnapshot(
        overallBeforeCompletedSets = beforeWeeklyTargets.completedSets,
        overallAfterCompletedSets = afterWeeklyTargets.completedSets,
        overallTargetSets = afterWeeklyTargets.targetSets.takeIf { it > 0.0 } ?: beforeWeeklyTargets.targetSets,
        groupRows = rows,
    )
}

internal fun buildReceiptStatsRailSnapshot(
    session: ActiveSession,
    durationSeconds: Int,
    volume: Double?,
): CompletionReceiptStatsRailSnapshot {
    val volumeOrRepsStat = if ((volume ?: 0.0) > 0.0) {
        CompletionReceiptStatSnapshot(
            label = "Volume",
            value = formatVolumeShort(volume ?: 0.0),
            supportingText = "Completed load",
        )
    } else {
        CompletionReceiptStatSnapshot(
            label = "Reps",
            value = computeCompletedRepCount(session).toString(),
            supportingText = "Completed work",
        )
    }

    return CompletionReceiptStatsRailSnapshot(
        items = listOf(
            volumeOrRepsStat,
            CompletionReceiptStatSnapshot(
                label = "Time",
                value = formatMinutesCompact(durationSeconds),
                supportingText = "Elapsed",
            ),
            CompletionReceiptStatSnapshot(
                label = "Sets",
                value = computeCompletedSetCount(session).toString(),
                supportingText = "Completed",
            ),
            CompletionReceiptStatSnapshot(
                label = "Closed",
                value = "${computeCompletedExerciseCount(session)}/${session.exercises.size}",
                supportingText = "Exercises",
            ),
        ),
    )
}

internal fun buildEvidenceHighlights(
    session: ActiveSession,
    comparison: CompletionReceiptComparisonSnapshot?,
): List<CompletionReceiptHighlightSnapshot> {
    val completedExercises = computeCompletedExerciseCount(session)
    val totalExercises = session.exercises.size
    val volume = computeSessionVolume(session)
    val highlights = mutableListOf<CompletionReceiptHighlightSnapshot>()

    comparison?.rows?.firstOrNull()?.let { row ->
        highlights += CompletionReceiptHighlightSnapshot(
            kind = CompletionReceiptHighlightKind.CONSISTENCY,
            label = row.label,
            deltaLabel = row.deltaLabel,
            detail = "Compared to last time",
        )
    }
    highlights += CompletionReceiptHighlightSnapshot(
        kind = CompletionReceiptHighlightKind.FIRST_COMPLETION,
        label = "Exercises closed",
        deltaLabel = "$completedExercises/$totalExercises",
        detail = if (completedExercises == totalExercises) "All exercises touched" else "Most useful view of finished work",
    )
    if (volume > 0.0) {
        highlights += CompletionReceiptHighlightSnapshot(
            kind = CompletionReceiptHighlightKind.CONSISTENCY,
            label = "Volume",
            deltaLabel = formatVolumeShort(volume),
            detail = "Completed workload",
        )
    }
    return highlights.take(3)
}

internal fun selectReceiptReferenceCandidate(
    session: ActiveSession,
    candidates: List<ReceiptReferenceCandidate>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): ReceiptReferenceCandidate? {
    val currentExerciseIds = session.exercises.map { it.exerciseId }.toSet()
    val maxAgeDays = 56L
    return candidates
        .mapNotNull { candidate ->
            val completedAt = runCatching { Instant.parse(candidate.completedAtUtc) }.getOrNull() ?: return@mapNotNull null
            val ageDays = Duration.between(completedAt, now).toDays().coerceAtLeast(0)
            val ageAllowed = ageDays <= maxAgeDays || session.origin.contains("history_reuse", ignoreCase = true)
            if (!ageAllowed) return@mapNotNull null

            val overlapCount = currentExerciseIds.intersect(candidate.exerciseIds.toSet()).size
            val overlapRatio = if (currentExerciseIds.isEmpty()) 0.0 else overlapCount.toDouble() / currentExerciseIds.size.toDouble()
            val focusMatch = session.focusKey != null && session.focusKey == candidate.focusKey
            val titleMatch = session.title.equals(candidate.title, ignoreCase = true)
            val originMatch = session.origin == candidate.origin

            val score = (
                (if (focusMatch) 100 else 0) +
                    (if (titleMatch) 45 else 0) +
                    (if (originMatch) 25 else 0) +
                    (overlapRatio * 50.0).toInt() -
                    ageDays.toInt().coerceAtMost(30)
                )
            if (score < 40) return@mapNotNull null
            score to candidate
        }
        .maxByOrNull { it.first }
        ?.second
}

internal fun buildComparisonSnapshot(
    session: ActiveSession,
    durationSeconds: Int,
    candidate: ReceiptReferenceCandidate?,
    referenceDetail: HistoryDetail?,
): CompletionReceiptComparisonSnapshot? {
    if (candidate == null || referenceDetail == null) return null

    val rows = mutableListOf<CompletionReceiptComparisonRowSnapshot>()
    val currentCompletedSets = computeCompletedSetCount(session)
    val setDelta = currentCompletedSets - candidate.completedSetCount
    rows += CompletionReceiptComparisonRowSnapshot(
        kind = CompletionReceiptComparisonKind.COMPLETED_SETS,
        label = "Completed sets",
        currentValue = currentCompletedSets.toString(),
        previousValue = candidate.completedSetCount.toString(),
        deltaLabel = signedDelta(setDelta, "set"),
    )

    val durationDeltaMinutes = ((durationSeconds - candidate.durationSeconds).toDouble() / 60.0).toInt()
    rows += CompletionReceiptComparisonRowSnapshot(
        kind = CompletionReceiptComparisonKind.DURATION,
        label = "Duration",
        currentValue = formatMinutesCompact(durationSeconds),
        previousValue = formatMinutesCompact(candidate.durationSeconds),
        deltaLabel = signedDelta(durationDeltaMinutes, "min"),
    )

    val currentTopSet = topSetForSession(session)
    val previousTopSet = currentTopSet?.let { top ->
        referenceDetail.exercises.firstOrNull { it.exerciseId == top.exerciseId }
    }
    if (currentTopSet != null && previousTopSet != null && previousTopSet.bestWeight > 0.0) {
        val weightDelta = currentTopSet.weight - previousTopSet.bestWeight
        if (weightDelta != 0.0) {
            rows += CompletionReceiptComparisonRowSnapshot(
                kind = CompletionReceiptComparisonKind.TOP_SET,
                label = currentTopSet.exerciseName,
                currentValue = "${trimWeight(currentTopSet.weight)} x ${currentTopSet.reps}",
                previousValue = "${trimWeight(previousTopSet.bestWeight)} x ${previousTopSet.bestReps}",
                deltaLabel = signedWeightDelta(weightDelta),
            )
        }
    }

    if (rows.isEmpty()) return null

    val referenceType = when {
        session.focusKey != null && session.focusKey == candidate.focusKey && session.origin.contains("program", ignoreCase = true) ->
            CompletionReceiptReferenceType.SAME_PROGRAM_FOCUS
        session.origin.contains("template", ignoreCase = true) -> CompletionReceiptReferenceType.SAME_TEMPLATE
        session.focusKey != null && session.focusKey == candidate.focusKey -> CompletionReceiptReferenceType.SAME_GENERATED_FOCUS
        session.origin.contains("history_reuse", ignoreCase = true) -> CompletionReceiptReferenceType.HISTORY_REPLAY_SOURCE
        else -> CompletionReceiptReferenceType.SAME_ORIGIN_FALLBACK
    }

    val headline = when {
        setDelta > 0 -> "More work closed than last time."
        setDelta < 0 -> "Less work closed than last time."
        durationDeltaMinutes < 0 -> "Matched the work in less time."
        durationDeltaMinutes > 0 -> "Similar work, slower pace."
        else -> "A close match to the last comparable session."
    }

    return CompletionReceiptComparisonSnapshot(
        reference = CompletionReceiptReferenceSnapshot(
            workoutId = candidate.workoutId,
            type = referenceType,
            label = candidate.title,
            completedAtUtc = candidate.completedAtUtc,
        ),
        headline = headline,
        rows = rows.take(3),
    )
}

internal fun buildWeeklyPromiseSnapshot(
    history: List<HistorySummary>,
    targetSessions: Int,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): WeeklyPromiseSnapshot {
    val today = now.atZone(zoneId).toLocalDate()
    val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() % 7L)
    val completedSessions = history.count { entry ->
        val completedDate = runCatching { Instant.parse(entry.completedAtUtc).atZone(zoneId).toLocalDate() }.getOrNull()
        completedDate != null && !completedDate.isBefore(startOfWeek) && !completedDate.isAfter(today)
    }
    return WeeklyPromiseSnapshot(
        weekStartLocal = startOfWeek.toString(),
        targetSessions = targetSessions,
        completedSessions = completedSessions,
    )
}

internal fun buildTodayReceiptRecapState(
    history: List<HistorySummary>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): TodayReceiptRecapState? {
    val today = now.atZone(zoneId).toLocalDate()
    val todayEntries = history.filter { entry ->
        val completedDate = runCatching { Instant.parse(entry.completedAtUtc).atZone(zoneId).toLocalDate() }.getOrNull()
        completedDate == today && entry.completionReceipt != null
    }
    if (todayEntries.isEmpty()) return null

    val latest = todayEntries.first()
    val receipt = latest.completionReceipt
    return TodayReceiptRecapState(
        todayWorkoutIds = todayEntries.map(HistorySummary::id),
        latestWorkoutId = latest.id,
        latestWorkoutTitle = latest.title,
        latestEvidenceLine = receipt?.evidence?.highlights?.firstOrNull()?.let { "${it.label}: ${it.deltaLabel}" },
        latestMeaningLine = receipt?.meaning?.summaryLine,
        workoutCountToday = todayEntries.size,
        debugSurface = null,
        debugScenario = null,
    )
}

internal fun topSetForSession(session: ActiveSession): ReceiptTopSet? {
    return session.exercises
        .flatMap { exercise ->
            exercise.sets.mapNotNull { set ->
                if (!set.completed) return@mapNotNull null
                val weight = set.weight.toDoubleOrNull() ?: return@mapNotNull null
                val reps = set.reps.toIntOrNull() ?: return@mapNotNull null
                ReceiptTopSet(
                    exerciseId = exercise.exerciseId,
                    exerciseName = exercise.name,
                    weight = weight,
                    reps = reps,
                )
            }
        }
        .maxWithOrNull(compareBy<ReceiptTopSet> { it.weight }.thenBy { it.reps })
}

private fun signedDelta(delta: Int, unit: String): String {
    val abs = kotlin.math.abs(delta)
    val suffix = if (abs == 1) unit else "${unit}s"
    return when {
        delta > 0 -> "+$abs $suffix"
        delta < 0 -> "-$abs $suffix"
        else -> "Matched"
    }
}

private fun signedWeightDelta(delta: Double): String {
    val abs = trimWeight(kotlin.math.abs(delta))
    return when {
        delta > 0.0 -> "+$abs lb"
        delta < 0.0 -> "-$abs lb"
        else -> "Matched"
    }
}

internal fun formatMinutesCompact(durationSeconds: Int): String {
    val minutes = (durationSeconds / 60.0).toInt().coerceAtLeast(1)
    return "$minutes min"
}

internal fun formatVolumeShort(volume: Double): String {
    return if (volume >= 1000.0) {
        "${(volume / 100.0).toInt() / 10.0}k lb"
    } else {
        "${volume.toInt()} lb"
    }
}

internal fun trimWeight(weight: Double): String {
    val longValue = weight.toLong()
    return if (weight == longValue.toDouble()) longValue.toString() else "%.1f".format(weight)
}
