package com.fitlib.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutRepositoryReuseHelpersTest {
    @Test
    fun historyReuseRepRange_prefersExplicitTarget() {
        val repRange = historyReuseRepRange(
            listOf(
                WorkoutExerciseSetDraft(
                    setNumber = 1,
                    targetReps = "5-7",
                    reps = 8,
                ),
            ),
        )

        assertEquals("5-7", repRange)
    }

    @Test
    fun historyReuseRepRange_derivesFromPerformedRepsWhenTargetIsMissing() {
        val repRange = historyReuseRepRange(
            listOf(
                WorkoutExerciseSetDraft(setNumber = 1, targetReps = "", reps = 8),
                WorkoutExerciseSetDraft(setNumber = 2, targetReps = "", reps = 10),
            ),
        )

        assertEquals("8-10", repRange)
    }

    @Test
    fun historyReuseSuggestedWeight_usesLatestLoggedWeightBeforeRecommendation() {
        val weight = historyReuseSuggestedWeight(
            listOf(
                WorkoutExerciseSetDraft(setNumber = 1, targetReps = "8-10", recommendedWeight = 95.0),
                WorkoutExerciseSetDraft(setNumber = 2, targetReps = "8-10", weight = 105.0),
            ),
        )

        assertEquals(105.0, weight ?: 0.0, 0.0001)
        assertEquals(90, historyReuseRestSeconds("6-8"))
        assertEquals(5, historyReuseEstimatedMinutes(emptyList()))
    }

    @Test
    fun historyReuseSuggestedWeight_returnsNullWhenNoWeightsExist() {
        assertNull(
            historyReuseSuggestedWeight(
                listOf(
                    WorkoutExerciseSetDraft(setNumber = 1, targetReps = "8-10"),
                ),
            ),
        )
    }
}
