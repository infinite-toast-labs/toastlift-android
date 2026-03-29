package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.AdherenceSessionSignal
import dev.toastlabs.toastlift.data.ExerciseHistoryDetail
import dev.toastlabs.toastlift.data.ExerciseHistoryEntry
import dev.toastlabs.toastlift.data.ExerciseHistorySet
import dev.toastlabs.toastlift.data.SessionStatus
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionSet
import dev.toastlabs.toastlift.data.buildAdherenceCurrencyTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompletionReceiptSupportTest {

    @Test
    fun buildReceiptAchievementSnapshot_reportsPersonalRecordsFromCurrentWorkout() {
        val snapshot = buildReceiptAchievementSnapshot(
            session = session(
                exerciseName = "Bench Press",
                reps = "5",
                weight = "185",
            ),
            workoutTitle = "Upper Push",
            completedAtUtc = "2026-03-28T12:00:00Z",
            exerciseHistories = listOf(
                exerciseHistoryDetail(
                    exerciseName = "Bench Press",
                    workoutTitle = "Upper Push",
                    completedAtUtc = "2026-03-28T12:00:00Z",
                    bestWeight = 185.0,
                    workingSets = listOf(
                        ExerciseHistorySet(
                            setNumber = 1,
                            reps = 5,
                            weight = 185.0,
                            isRepPr = false,
                            isWeightPr = true,
                            isVolumePr = true,
                        ),
                    ),
                ),
            ),
        )

        assertEquals("2 PRs Broken", snapshot.title)
        assertEquals(2, snapshot.prCount)
        assertTrue(snapshot.chips.contains("Bench Press 185 lb PR"))
        assertTrue(snapshot.chips.contains("Bench Press Volume PR"))
    }

    @Test
    fun buildReceiptAchievementSnapshot_fallsBackToBestSetWhenNoPrsExist() {
        val snapshot = buildReceiptAchievementSnapshot(
            session = session(
                exerciseName = "Bench Press",
                reps = "3",
                weight = "225",
            ),
            workoutTitle = "Upper Push",
            completedAtUtc = "2026-03-28T12:00:00Z",
            exerciseHistories = listOf(
                exerciseHistoryDetail(
                    exerciseName = "Bench Press",
                    workoutTitle = "Upper Push",
                    completedAtUtc = "2026-03-28T12:00:00Z",
                    bestWeight = 225.0,
                    workingSets = listOf(
                        ExerciseHistorySet(
                            setNumber = 1,
                            reps = 3,
                            weight = 225.0,
                            isRepPr = false,
                            isWeightPr = false,
                            isVolumePr = false,
                        ),
                    ),
                ),
            ),
        )

        assertEquals("Best Set Today", snapshot.title)
        assertEquals("Bench Press", snapshot.fallbackLabel)
        assertEquals("225 x 3", snapshot.fallbackValue)
    }

    @Test
    fun buildReceiptSplitProgressSnapshot_usesBeforeAndAfterWeeklyTargetState() {
        val snapshot = buildReceiptSplitProgressSnapshot(
            beforeWeeklyTargets = weeklySummary(
                completedSets = 12.0,
                targetSets = 30.0,
                groups = listOf(
                    weeklyGroup("push", "Push Muscles", completedSets = 4.0, targetSets = 10.0),
                    weeklyGroup("pull", "Pull Muscles", completedSets = 5.0, targetSets = 10.0),
                    weeklyGroup("legs", "Legs Muscles", completedSets = 3.0, targetSets = 10.0),
                ),
            ),
            afterWeeklyTargets = weeklySummary(
                completedSets = 17.5,
                targetSets = 30.0,
                groups = listOf(
                    weeklyGroup("push", "Push Muscles", completedSets = 7.5, targetSets = 10.0),
                    weeklyGroup("pull", "Pull Muscles", completedSets = 6.0, targetSets = 10.0),
                    weeklyGroup("legs", "Legs Muscles", completedSets = 4.0, targetSets = 10.0),
                ),
            ),
        )

        requireNotNull(snapshot)
        assertEquals(12.0, snapshot.overallBeforeCompletedSets, 0.0001)
        assertEquals(17.5, snapshot.overallAfterCompletedSets, 0.0001)
        assertEquals(3, snapshot.groupRows.size)
        assertEquals(4.0, snapshot.groupRows.first { it.key == "push" }.beforeCompletedSets, 0.0001)
        assertEquals(7.5, snapshot.groupRows.first { it.key == "push" }.afterCompletedSets, 0.0001)
    }

    @Test
    fun buildReceiptStatsRailSnapshot_usesCompletedRepsWhenWeightedVolumeIsMissing() {
        val snapshot = buildReceiptStatsRailSnapshot(
            session = session(
                exerciseName = "Push-Up",
                reps = "12",
                weight = "",
            ),
            durationSeconds = 18 * 60,
            volume = null,
        )

        assertEquals("Reps", snapshot.items.first().label)
        assertEquals("12", snapshot.items.first().value)
        assertEquals("Time", snapshot.items[1].label)
        assertEquals("Closed", snapshot.items.last().label)
        assertEquals("1/1", snapshot.items.last().value)
    }

    @Test
    fun completionReceiptTokenDelta_reportsAppliedSessionReward() {
        val beforeTrend = buildAdherenceCurrencyTrend(
            signals = listOf(
                AdherenceSessionSignal(
                    sequenceNumber = 1,
                    status = SessionStatus.COMPLETED,
                    plannedSets = 12,
                    completedSetCount = 12,
                ),
            ),
        )
        val afterTrend = buildAdherenceCurrencyTrend(
            signals = listOf(
                AdherenceSessionSignal(
                    sequenceNumber = 1,
                    status = SessionStatus.COMPLETED,
                    plannedSets = 12,
                    completedSetCount = 12,
                ),
                AdherenceSessionSignal(
                    sequenceNumber = 2,
                    status = SessionStatus.COMPLETED,
                    plannedSets = 12,
                    completedSetCount = 12,
                ),
            ),
        )

        assertEquals(3, completionReceiptTokenDelta(beforeTrend, afterTrend))
        assertEquals(3, completionReceiptTokenDelta(null, beforeTrend))
        assertNull(completionReceiptTokenDelta(beforeTrend, null))
    }

    private fun session(
        exerciseName: String,
        reps: String,
        weight: String,
    ): ActiveSession {
        return ActiveSession(
            title = "Upper Push",
            origin = "generated",
            locationModeId = 1L,
            startedAtUtc = "2026-03-28T11:30:00Z",
            exercises = listOf(
                SessionExercise(
                    exerciseId = 11L,
                    name = exerciseName,
                    bodyRegion = "Upper",
                    targetMuscleGroup = "Chest",
                    equipment = "Barbell",
                    restSeconds = 120,
                    sets = listOf(
                        SessionSet(
                            setNumber = 1,
                            targetReps = "5",
                            reps = reps,
                            weight = weight,
                            completed = true,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun exerciseHistoryDetail(
        exerciseName: String,
        workoutTitle: String,
        completedAtUtc: String,
        bestWeight: Double,
        workingSets: List<ExerciseHistorySet>,
    ): ExerciseHistoryDetail {
        return ExerciseHistoryDetail(
            exerciseId = 11L,
            exerciseName = exerciseName,
            entries = listOf(
                ExerciseHistoryEntry(
                    completedAtUtc = completedAtUtc,
                    workoutTitle = workoutTitle,
                    targetReps = "5",
                    estimatedOneRepMax = null,
                    totalVolume = workingSets.sumOf { (it.reps ?: 0) * (it.weight ?: 0.0) },
                    bestWeight = bestWeight,
                    lastSetRepsInReserve = null,
                    lastSetRpe = null,
                    workingSets = workingSets,
                    hasPersonalRecord = workingSets.any { it.isRepPr || it.isWeightPr || it.isVolumePr },
                ),
            ),
            isPrOnlyFilterEnabled = false,
            totalEntries = 1,
            prEntryCount = if (workingSets.any { it.isRepPr || it.isWeightPr || it.isVolumePr }) 1 else 0,
        )
    }

    private fun weeklySummary(
        completedSets: Double,
        targetSets: Double,
        groups: List<WeeklyMuscleTargetGroupSummary>,
    ): WeeklyMuscleTargetSummary {
        return WeeklyMuscleTargetSummary(
            weekNumber = 1,
            overallCompletionRatio = if (targetSets > 0.0) completedSets / targetSets else 0.0,
            completedSets = completedSets,
            targetSets = targetSets,
            range = WeeklyMuscleTargetRange(
                start = LocalDate.of(2026, 3, 22),
                end = LocalDate.of(2026, 3, 28),
            ),
            groupSummaries = groups,
            history = emptyList(),
        )
    }

    private fun weeklyGroup(
        key: String,
        label: String,
        completedSets: Double,
        targetSets: Double,
    ): WeeklyMuscleTargetGroupSummary {
        return WeeklyMuscleTargetGroupSummary(
            key = key,
            label = label,
            completedSets = completedSets,
            targetSets = targetSets,
            muscleSummaries = emptyList(),
        )
    }
}
