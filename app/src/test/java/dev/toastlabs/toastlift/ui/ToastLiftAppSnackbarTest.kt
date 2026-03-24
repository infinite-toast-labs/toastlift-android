package dev.toastlabs.toastlift.ui

import androidx.compose.material3.SnackbarDuration
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ToastLiftAppSnackbarTest {
    @Test
    fun snackbarDurationFor_usesStandardDurationForProfileSaveConfirmation() {
        assertEquals(SnackbarDuration.Short, snackbarDurationFor(PROFILE_SAVED_MESSAGE))
    }

    @Test
    fun snackbarDurationFor_usesShortDurationForOtherMessages() {
        assertEquals(SnackbarDuration.Short, snackbarDurationFor("Template updated."))
    }

    @Test
    fun snackbarMessages_ignoresClearingNullValues() = runBlocking {
        val events = snackbarMessages(
            flowOf(PROFILE_SAVED_MESSAGE, null, "Template updated."),
        ).toList()

        assertEquals(listOf(PROFILE_SAVED_MESSAGE, "Template updated."), events)
    }
}
