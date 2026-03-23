package com.fitlib.app.ui

import androidx.compose.material3.SnackbarDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FitLibAppSnackbarTest {
    @Test
    fun snackbarDurationFor_keepsProfileSaveConfirmationVisibleLonger() {
        assertEquals(SnackbarDuration.Long, snackbarDurationFor(PROFILE_SAVED_MESSAGE))
    }

    @Test
    fun snackbarDurationFor_usesShortDurationForOtherMessages() {
        assertEquals(SnackbarDuration.Short, snackbarDurationFor("Template updated."))
    }
}
