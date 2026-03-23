package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.PlannedSession
import dev.toastlabs.toastlift.data.SessionFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayProgramActionsTest {

    @Test
    fun redistributedSetsForSkippedSession_enforcesExpectedBounds() {
        assertEquals(1, redistributedSetsForSkippedSession(plannedSets = 4))
        assertEquals(2, redistributedSetsForSkippedSession(plannedSets = 16))
        assertEquals(2, redistributedSetsForSkippedSession(plannedSets = 40))
    }

    @Test
    fun unskipSessionMenuLabel_usesWeekAndDay() {
        assertEquals(
            "Unskip Week 3 Day 2",
            unskipSessionMenuLabel(session(weekNumber = 3, dayIndex = 1)),
        )
    }

    @Test
    fun programActionConfirmationContent_buildsSkipDialogFromSession() {
        val content = programActionConfirmationContent(
            action = TodayProgramActionConfirmation.SkipSession,
            nextSession = session(weekNumber = 2, dayIndex = 0),
        )

        assertEquals("Skip Week 2 Day 1?", content.title)
        assertEquals("Skip session", content.confirmLabel)
        assertTrue(content.isDestructive)
        assertTrue(content.message.contains("shifts a small amount of volume"))
    }

    @Test
    fun programActionConfirmationContent_buildsPauseDialogAsNonDestructive() {
        val content = programActionConfirmationContent(
            action = TodayProgramActionConfirmation.PauseProgram,
            nextSession = null,
        )

        assertEquals("Pause program?", content.title)
        assertEquals("Pause program", content.confirmLabel)
        assertFalse(content.isDestructive)
        assertTrue(content.message.contains("lighter re-entry session"))
    }

    private fun session(
        weekNumber: Int = 1,
        dayIndex: Int = 0,
    ): PlannedSession {
        return PlannedSession(
            id = 7L,
            programId = "program-1",
            weekNumber = weekNumber,
            dayIndex = dayIndex,
            sequenceNumber = 2,
            focusKey = SessionFocus.FULL_BODY,
            plannedSets = 16,
        )
    }
}
