package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDurationValidationTest {
    @Test
    fun isValidWorkoutDurationMinutes_acceptsBoundsAndInteriorCustomValues() {
        assertTrue(isValidWorkoutDurationMinutes(MIN_WORKOUT_DURATION_MINUTES))
        assertTrue(isValidWorkoutDurationMinutes(52))
        assertTrue(isValidWorkoutDurationMinutes(MAX_WORKOUT_DURATION_MINUTES))
    }

    @Test
    fun isValidWorkoutDurationMinutes_rejectsOutOfRangeValues() {
        assertFalse(isValidWorkoutDurationMinutes(MIN_WORKOUT_DURATION_MINUTES - 1))
        assertFalse(isValidWorkoutDurationMinutes(MAX_WORKOUT_DURATION_MINUTES + 1))
    }

    @Test
    fun normalizeWorkoutDurationMinutes_clampsToSupportedRange() {
        assertEquals(MIN_WORKOUT_DURATION_MINUTES, normalizeWorkoutDurationMinutes(5))
        assertEquals(45, normalizeWorkoutDurationMinutes(45))
        assertEquals(MAX_WORKOUT_DURATION_MINUTES, normalizeWorkoutDurationMinutes(360))
    }
}
