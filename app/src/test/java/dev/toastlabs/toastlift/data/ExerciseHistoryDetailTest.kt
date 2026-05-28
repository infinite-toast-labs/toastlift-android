package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseHistoryDetailTest {
    @Test
    fun buildExerciseHistoryDetail_filtersToPrSessionsAndPreservesCounts() {
        val rows = listOf(
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 1, reps = 5, weight = 100.0),
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 2, reps = 5, weight = 100.0),
            row(completedAtUtc = "2026-03-03T12:00:00Z", workoutTitle = "Day 2", setNumber = 1, reps = 4, weight = 90.0),
            row(completedAtUtc = "2026-03-05T12:00:00Z", workoutTitle = "Day 3", setNumber = 1, reps = 5, weight = 105.0),
        )

        val detail = buildExerciseHistoryDetail(
            exerciseId = 42L,
            fallbackName = "Bench Press",
            rows = rows,
            prOnly = true,
        )

        assertTrue(detail.isPrOnlyFilterEnabled)
        assertEquals(3, detail.totalEntries)
        assertEquals(2, detail.prEntryCount)
        assertEquals(listOf("Day 3", "Day 1"), detail.entries.map { it.workoutTitle })
        assertTrue(detail.entries.all { it.hasPersonalRecord })
        assertTrue(detail.entries.first().workingSets.first().isWeightPr)
    }

    @Test
    fun buildExerciseHistoryDetail_dropsRowsWithoutLoggedRepsBeforeBuildingHistory() {
        val rows = listOf(
            row(
                completedAtUtc = "2026-03-01T12:00:00Z",
                workoutTitle = "Paused Session",
                setNumber = 1,
                reps = 5,
                weight = 100.0,
                isCompleted = false,
            ),
            row(
                completedAtUtc = "2026-03-03T12:00:00Z",
                workoutTitle = "Zero Rep Session",
                setNumber = 1,
                reps = 0,
                weight = 405.0,
            ),
        )

        val detail = buildExerciseHistoryDetail(
            exerciseId = 7L,
            fallbackName = "Deadlift",
            rows = rows,
            prOnly = true,
        )

        assertTrue(detail.isPrOnlyFilterEnabled)
        assertEquals(0, detail.totalEntries)
        assertEquals(0, detail.prEntryCount)
        assertTrue(detail.entries.isEmpty())
    }

    @Test
    fun buildExerciseHistoryDetail_keepsExerciseInstanceWhenOneRepIsLogged() {
        val rows = listOf(
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 1, reps = 0, weight = 315.0),
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 2, reps = 1, weight = 315.0),
        )

        val detail = buildExerciseHistoryDetail(
            exerciseId = 7L,
            fallbackName = "Deadlift",
            rows = rows,
            prOnly = false,
        )

        assertEquals(1, detail.totalEntries)
        assertEquals(1, detail.entries.single().workingSets.size)
        assertEquals(1, detail.entries.single().workingSets.single().reps)
    }

    @Test
    fun buildExerciseHistoryDetail_marksNonPrSessionsWhenFilterIsOff() {
        val rows = listOf(
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 1, reps = 6, weight = 90.0),
            row(completedAtUtc = "2026-03-03T12:00:00Z", workoutTitle = "Day 2", setNumber = 1, reps = 5, weight = 80.0),
        )

        val detail = buildExerciseHistoryDetail(
            exerciseId = 1L,
            fallbackName = "Row",
            rows = rows,
            prOnly = false,
        )

        assertFalse(detail.isPrOnlyFilterEnabled)
        assertEquals(2, detail.entries.size)
        assertFalse(detail.entries.first().hasPersonalRecord)
        assertTrue(detail.entries.last().hasPersonalRecord)
    }

    @Test
    fun buildExerciseHistoryDetail_showsLastLoggedSetFirstWithinEachEntry() {
        val rows = listOf(
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 1, reps = 8, weight = 100.0),
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 2, reps = 8, weight = 105.0),
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 3, reps = 7, weight = 95.0),
        )

        val detail = buildExerciseHistoryDetail(
            exerciseId = 2L,
            fallbackName = "Incline Press",
            rows = rows,
            prOnly = false,
        )

        val workingSets = detail.entries.single().workingSets
        assertEquals(listOf(3, 2, 1), workingSets.map { it.setNumber })
        assertFalse(workingSets.first().isWeightPr)
        assertTrue(workingSets[1].isWeightPr)
    }

    @Test
    fun buildExercisePerformanceStats_usesHighestIndividualWeightAndShowsThatSetsReps() {
        val rows = listOf(
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 1, reps = 25, weight = 20.0),
            row(completedAtUtc = "2026-03-03T12:00:00Z", workoutTitle = "Day 2", setNumber = 1, reps = 10, weight = 45.0),
            row(completedAtUtc = "2026-03-05T12:00:00Z", workoutTitle = "Day 3", setNumber = 1, reps = 8, weight = 40.0),
        )

        val stats = buildExercisePerformanceStats(rows)

        assertEquals(45.0, stats?.maxWeight ?: 0.0, 0.001)
        assertEquals(10, stats?.maxWeightReps)
    }

    @Test
    fun buildExercisePerformanceStats_averagesWeightedSetsFromLastFiveLoggedSessions() {
        val rows = listOf(
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 1, reps = 8, weight = 10.0),
            row(completedAtUtc = "2026-03-02T12:00:00Z", workoutTitle = "Day 2", setNumber = 1, reps = 8, weight = 20.0),
            row(completedAtUtc = "2026-03-03T12:00:00Z", workoutTitle = "Day 3", setNumber = 1, reps = 8, weight = 30.0),
            row(completedAtUtc = "2026-03-04T12:00:00Z", workoutTitle = "Day 4", setNumber = 1, reps = 8, weight = 40.0),
            row(completedAtUtc = "2026-03-05T12:00:00Z", workoutTitle = "Day 5", setNumber = 1, reps = 8, weight = 50.0),
            row(completedAtUtc = "2026-03-06T12:00:00Z", workoutTitle = "Day 6", setNumber = 1, reps = 8, weight = 60.0),
        )

        val stats = buildExercisePerformanceStats(rows)

        assertEquals(40.0, stats?.averageWeightLastFiveSessions ?: 0.0, 0.001)
    }

    @Test
    fun buildExercisePerformanceStats_usesLatestLoggedSessionForSetHints() {
        val rows = listOf(
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 1, reps = 10, weight = 35.0),
            row(completedAtUtc = "2026-03-01T12:00:00Z", workoutTitle = "Day 1", setNumber = 2, reps = 9, weight = 35.0),
            row(completedAtUtc = "2026-03-08T12:00:00Z", workoutTitle = "Day 2", setNumber = 1, reps = 8, weight = 40.0),
            row(completedAtUtc = "2026-03-08T12:00:00Z", workoutTitle = "Day 2", setNumber = 2, reps = 7, weight = 45.0),
        )

        val stats = buildExercisePerformanceStats(rows)

        assertEquals(2, stats?.previousSessionSetsBySetNumber?.size)
        assertEquals(40.0, stats?.previousSessionSetsBySetNumber?.get(1)?.weight ?: 0.0, 0.001)
        assertEquals(8, stats?.previousSessionSetsBySetNumber?.get(1)?.reps)
        assertEquals(1, stats?.previousSessionSetsBySetNumber?.get(1)?.repsInReserve)
        assertEquals(45.0, stats?.previousSessionSetsBySetNumber?.get(2)?.weight ?: 0.0, 0.001)
        assertEquals(7, stats?.previousSessionSetsBySetNumber?.get(2)?.reps)
        assertEquals(1, stats?.previousSessionSetsBySetNumber?.get(2)?.repsInReserve)
    }

    private fun row(
        completedAtUtc: String,
        workoutTitle: String,
        setNumber: Int,
        reps: Int?,
        weight: Double?,
        isCompleted: Boolean = true,
    ): ExerciseHistoryRow {
        return ExerciseHistoryRow(
            completedAtUtc = completedAtUtc,
            workoutTitle = workoutTitle,
            targetReps = "5-8",
            lastSetRepsInReserve = 1,
            lastSetRpe = 8.0,
            setNumber = setNumber,
            reps = reps,
            weight = weight,
            isCompleted = isCompleted,
        )
    }
}
