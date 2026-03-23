package com.fitlib.app.ui

import com.fitlib.app.data.CheckpointStatus
import com.fitlib.app.data.CheckpointType
import com.fitlib.app.data.OutcomeMetric
import com.fitlib.app.data.PeriodizationModel
import com.fitlib.app.data.PlannedSession
import com.fitlib.app.data.PlannedWeek
import com.fitlib.app.data.ProgramArchetype
import com.fitlib.app.data.ProgramCheckpoint
import com.fitlib.app.data.SessionFocus
import com.fitlib.app.data.SessionStatus
import com.fitlib.app.data.SuccessCriteria
import com.fitlib.app.data.AdaptationPolicy
import com.fitlib.app.data.TargetOutcome
import com.fitlib.app.data.TrainingProgram
import com.fitlib.app.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramProgressSummaryTest {

    @Test
    fun buildProgramProgressSummary_buildsBirdsEyeBlockProgressFromExistingState() {
        val summary = buildProgramProgressSummary(
            program = program(totalWeeks = 3),
            weeks = listOf(
                PlannedWeek(programId = "program-1", weekNumber = 1, weekType = WeekType.ACCUMULATION),
                PlannedWeek(programId = "program-1", weekNumber = 2, weekType = WeekType.INTENSIFICATION),
                PlannedWeek(programId = "program-1", weekNumber = 3, weekType = WeekType.DELOAD),
            ),
            sessions = listOf(
                session(id = 1L, weekNumber = 1, sequenceNumber = 1, status = SessionStatus.COMPLETED),
                session(id = 2L, weekNumber = 1, sequenceNumber = 2, status = SessionStatus.COMPLETED),
                session(id = 3L, weekNumber = 2, sequenceNumber = 3, status = SessionStatus.SKIPPED),
                session(id = 4L, weekNumber = 2, sequenceNumber = 4, status = SessionStatus.UPCOMING),
                session(id = 5L, weekNumber = 3, sequenceNumber = 5, status = SessionStatus.UPCOMING),
            ),
            checkpoints = listOf(
                checkpoint(weekNumber = 1, status = CheckpointStatus.COMPLETED),
                checkpoint(weekNumber = 2, status = CheckpointStatus.PENDING),
            ),
            nextSession = session(id = 4L, weekNumber = 2, sequenceNumber = 4, status = SessionStatus.UPCOMING),
        )

        assertEquals(2, summary.currentWeekNumber)
        assertEquals(3, summary.totalWeeks)
        assertEquals(2, summary.completedSessions)
        assertEquals(1, summary.skippedSessions)
        assertEquals(2, summary.remainingSessions)
        assertEquals(5, summary.totalSessions)
        assertEquals(1, summary.completedWeeks)
        assertEquals(1, summary.completedCheckpoints)
        assertEquals(2, summary.totalCheckpoints)

        val currentWeek = summary.weekSummaries[1]
        assertEquals("Intensification", currentWeek.weekTypeLabel)
        assertEquals("Current", currentWeek.statusLabel)
        assertTrue(currentWeek.isCurrentWeek)
        assertEquals(2, currentWeek.totalSessions)
        assertEquals(listOf(SessionStatus.SKIPPED, SessionStatus.UPCOMING), currentWeek.sessionStatuses)
        assertEquals(CheckpointStatus.PENDING, currentWeek.checkpointStatus)
    }

    @Test
    fun buildProgramProgressSummary_treatsMigratedSessionsAsClosedAndFallsBackToLastWeek() {
        val summary = buildProgramProgressSummary(
            program = program(totalWeeks = 2),
            weeks = listOf(
                PlannedWeek(programId = "program-1", weekNumber = 1, weekType = WeekType.ACCUMULATION),
                PlannedWeek(programId = "program-1", weekNumber = 2, weekType = WeekType.TEST),
            ),
            sessions = listOf(
                session(id = 1L, weekNumber = 1, sequenceNumber = 1, status = SessionStatus.COMPLETED),
                session(id = 2L, weekNumber = 1, sequenceNumber = 2, status = SessionStatus.SKIPPED),
                session(id = 3L, weekNumber = 2, sequenceNumber = 3, status = SessionStatus.MIGRATED),
                session(id = 4L, weekNumber = 2, sequenceNumber = 4, status = SessionStatus.COMPLETED),
            ),
            checkpoints = emptyList(),
            nextSession = null,
        )

        assertEquals(2, summary.currentWeekNumber)
        assertEquals(0, summary.remainingSessions)
        assertEquals(2, summary.completedWeeks)
        assertEquals(listOf("Done", "Done"), summary.weekSummaries.map { it.statusLabel })
    }

    private fun program(totalWeeks: Int) = TrainingProgram(
        id = "program-1",
        title = "Spring Block",
        goal = "Hypertrophy",
        primaryOutcomeMetric = OutcomeMetric.HYPERTROPHY,
        programArchetype = ProgramArchetype.LINEAR_RAMP,
        periodizationModel = PeriodizationModel.LINEAR,
        splitProgramId = 2L,
        totalWeeks = totalWeeks,
        sessionsPerWeek = 2,
        successCriteria = SuccessCriteria(
            targetLifts = mapOf(1L to TargetOutcome(metric = "5RM", targetValue = 100.0)),
            targetSessionCompletionRate = 0.8,
        ),
        adaptationPolicy = AdaptationPolicy(),
        createdAt = 0L,
    )

    private fun session(
        id: Long,
        weekNumber: Int,
        sequenceNumber: Int,
        status: SessionStatus,
    ) = PlannedSession(
        id = id,
        programId = "program-1",
        weekNumber = weekNumber,
        dayIndex = sequenceNumber - 1,
        sequenceNumber = sequenceNumber,
        focusKey = SessionFocus.FULL_BODY,
        plannedSets = 16,
        status = status,
    )

    private fun checkpoint(
        weekNumber: Int,
        status: CheckpointStatus,
    ) = ProgramCheckpoint(
        id = weekNumber.toLong(),
        programId = "program-1",
        weekNumber = weekNumber,
        checkpointType = CheckpointType.PROGRESS_REVIEW,
        status = status,
    )
}
