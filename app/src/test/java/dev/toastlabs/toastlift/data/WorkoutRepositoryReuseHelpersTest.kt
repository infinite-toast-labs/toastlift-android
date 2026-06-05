package dev.toastlabs.toastlift.data

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
                WorkoutExerciseSetDraft(setNumber = 2, targetReps = "", reps = 0),
                WorkoutExerciseSetDraft(setNumber = 3, targetReps = "", reps = 10),
            ),
        )

        assertEquals("8-10", repRange)
    }

    @Test
    fun historyReuseSuggestedWeight_usesLatestLoggedWeightBeforeRecommendation() {
        val weight = historyReuseSuggestedWeight(
            listOf(
                WorkoutExerciseSetDraft(setNumber = 1, targetReps = "8-10", recommendedWeight = 95.0),
                WorkoutExerciseSetDraft(setNumber = 2, targetReps = "8-10", reps = 0, weight = 405.0),
                WorkoutExerciseSetDraft(setNumber = 3, targetReps = "8-10", reps = 8, weight = 105.0),
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

    @Test
    fun historyReuseWorkoutPlan_preservesCompletedWorkoutFocus() {
        val workout = historyReuseWorkoutPlan(
            header = HistoryReuseWorkoutHeader(
                title = "Upper Day",
                locationModeId = 2L,
                focusKey = "upper_body",
            ),
            exercises = emptyList(),
        )

        assertEquals("upper_body", workout.focusKey)
    }

    @Test
    fun distinctTemplateExercises_keepsFirstExerciseForDuplicateIds() {
        val exercises = distinctTemplateExercises(
            listOf(
                workoutExercise(101L, "Bench Press"),
                workoutExercise(202L, "Cable Row"),
                workoutExercise(101L, "Duplicate Bench Press"),
            ),
        )

        assertEquals(listOf(101L, 202L), exercises.map(WorkoutExercise::exerciseId))
        assertEquals("Bench Press", exercises.first().name)
    }

    @Test
    fun appendDistinctTemplateExercise_skipsExistingExerciseId() {
        val existing = listOf(workoutExercise(101L, "Bench Press"))

        assertEquals(existing, appendDistinctTemplateExercise(existing, workoutExercise(101L, "Bench Press")))
        assertEquals(
            listOf(101L, 202L),
            appendDistinctTemplateExercise(existing, workoutExercise(202L, "Cable Row")).map(WorkoutExercise::exerciseId),
        )
    }

    private fun workoutExercise(id: Long, name: String): WorkoutExercise {
        return WorkoutExercise(
            exerciseId = id,
            name = name,
            bodyRegion = "Upper Body",
            targetMuscleGroup = "Chest",
            equipment = "Barbell",
            sets = 3,
            repRange = "8-12",
            restSeconds = 75,
            rationale = "Template test",
        )
    }
}
