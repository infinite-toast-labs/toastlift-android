package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutFeedbackSignalTypeTest {
    @Test
    fun manualAddSignals_promoteExerciseRecommendations() {
        assertEquals(1.0, WorkoutFeedbackSignalType.GENERATED_PLAN_MANUAL_ADD.recommendationDelta, 0.0)
        assertEquals(1.0, WorkoutFeedbackSignalType.ACTIVE_SESSION_MANUAL_ADD.recommendationDelta, 0.0)
    }

    @Test
    fun removeSignals_doNotDemoteExerciseRecommendations() {
        assertEquals(0.0, WorkoutFeedbackSignalType.GENERATED_PLAN_REMOVE.recommendationDelta, 0.0)
        assertEquals(0.0, WorkoutFeedbackSignalType.ACTIVE_SESSION_REMOVE.recommendationDelta, 0.0)
    }
}
