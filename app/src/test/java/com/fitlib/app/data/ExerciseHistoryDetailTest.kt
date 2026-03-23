package com.fitlib.app.data

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
    fun buildExerciseHistoryDetail_returnsEmptyPrViewWhenNoCompletedSetsContainPrs() {
        val rows = listOf(
            row(
                completedAtUtc = "2026-03-01T12:00:00Z",
                workoutTitle = "Paused Session",
                setNumber = 1,
                reps = 5,
                weight = 100.0,
                isCompleted = false,
            ),
        )

        val detail = buildExerciseHistoryDetail(
            exerciseId = 7L,
            fallbackName = "Deadlift",
            rows = rows,
            prOnly = true,
        )

        assertTrue(detail.isPrOnlyFilterEnabled)
        assertEquals(1, detail.totalEntries)
        assertEquals(0, detail.prEntryCount)
        assertTrue(detail.entries.isEmpty())
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
