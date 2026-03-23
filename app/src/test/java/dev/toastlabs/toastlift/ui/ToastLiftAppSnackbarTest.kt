package dev.toastlabs.toastlift.ui

import androidx.compose.material3.SnackbarDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class ToastLiftAppSnackbarTest {
    @Test
    fun snackbarDurationFor_keepsProfileSaveConfirmationVisibleLonger() {
        assertEquals(SnackbarDuration.Long, snackbarDurationFor(PROFILE_SAVED_MESSAGE))
    }

    @Test
    fun snackbarDurationFor_usesShortDurationForOtherMessages() {
        assertEquals(SnackbarDuration.Short, snackbarDurationFor("Template updated."))
    }
}
