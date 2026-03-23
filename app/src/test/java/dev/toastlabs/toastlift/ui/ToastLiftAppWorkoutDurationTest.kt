package dev.toastlabs.toastlift.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToastLiftAppWorkoutDurationTest {
    @Test
    fun workoutDurationValidationMessage_acceptsPresetAndCustomValuesInRange() {
        assertNull(workoutDurationValidationMessage("30"))
        assertNull(workoutDurationValidationMessage("52"))
        assertNull(workoutDurationValidationMessage("300"))
    }

    @Test
    fun workoutDurationValidationMessage_rejectsBlankAndOutOfRangeValues() {
        assertEquals("Enter a duration from 15 to 300 minutes.", workoutDurationValidationMessage(""))
        assertEquals("Enter a duration from 15 to 300 minutes.", workoutDurationValidationMessage("14"))
        assertEquals("Enter a duration from 15 to 300 minutes.", workoutDurationValidationMessage("301"))
    }

    @Test
    fun workoutDurationValidationMessage_rejectsNonNumericValues() {
        assertEquals("Enter whole minutes only.", workoutDurationValidationMessage("abc"))
    }
}
