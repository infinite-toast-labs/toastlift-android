package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveSessionWorkoutDetailsTest {
    @Test
    fun activeSessionExpectedRepSummary_sumsRepRangesAcrossPlannedSets() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "4-6"),
                        SessionSet(setNumber = 2, targetReps = "4-6"),
                    ),
                ),
                exercise(
                    "Incline Dumbbell Press",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "8-10"),
                        SessionSet(setNumber = 2, targetReps = "8-10"),
                        SessionSet(setNumber = 3, targetReps = "8-10"),
                    ),
                ),
            ),
        )

        assertEquals("32-42 total reps", activeSessionExpectedRepSummary(session))
    }

    @Test
    fun activeSessionExpectedLoadVolume_usesRecommendedWeightsAndReps() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "4-6", recommendedReps = 5, recommendedWeight = "185"),
                        SessionSet(setNumber = 2, targetReps = "4-6", recommendedReps = 5, recommendedWeight = "185"),
                    ),
                ),
                exercise(
                    "Leg Curl",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "10-12", recommendedWeight = "90"),
                    ),
                ),
            ),
        )

        val expectedVolume = requireNotNull(activeSessionExpectedLoadVolume(session))
        assertEquals(2840.0, expectedVolume, 0.001)
    }

    @Test
    fun activeSessionExpectedLoadVolume_returnsNullWithoutWeightTargets() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Push-Up",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "12-15"),
                        SessionSet(setNumber = 2, targetReps = "12-15"),
                    ),
                ),
            ),
        )

        assertNull(activeSessionExpectedLoadVolume(session))
    }

    @Test
    fun activeSessionIntensityLabel_usesFocusKeyWhenHeavyDayIsKnown() {
        val session = session(
            focusKey = "lower_strength",
            exercises = listOf(
                exercise(
                    "Back Squat",
                    sets = listOf(SessionSet(setNumber = 1, targetReps = "8-10")),
                ),
            ),
        )

        assertEquals("Heavy", activeSessionIntensityLabel(session))
    }

    @Test
    fun activeSessionIntensityLabel_fallsBackToRepRangesForManualSessions() {
        val session = session(
            focusKey = null,
            exercises = listOf(
                exercise(
                    "Lateral Raise",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "15-20"),
                        SessionSet(setNumber = 2, targetReps = "15-20"),
                    ),
                ),
            ),
        )

        assertEquals("High reps", activeSessionIntensityLabel(session))
    }

    private fun session(
        focusKey: String? = "upper_body",
        exercises: List<SessionExercise>,
    ): ActiveSession {
        return ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-23T10:00:00Z",
            focusKey = focusKey,
            exercises = exercises,
        )
    }

    private fun exercise(
        name: String,
        sets: List<SessionSet>,
    ): SessionExercise {
        return SessionExercise(
            exerciseId = name.hashCode().toLong(),
            name = name,
            bodyRegion = "Upper Body",
            targetMuscleGroup = "Chest",
            equipment = "Barbell",
            restSeconds = 90,
            sets = sets,
        )
    }
}
