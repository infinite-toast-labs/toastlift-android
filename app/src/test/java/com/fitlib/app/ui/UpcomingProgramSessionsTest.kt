package com.fitlib.app.ui

import com.fitlib.app.data.ExecutionStyle
import com.fitlib.app.data.PlannedSession
import com.fitlib.app.data.PlannedSessionExercise
import com.fitlib.app.data.SessionFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingProgramSessionsTest {

    @Test
    fun buildUpcomingProgramSessionSummaries_usesRealSessionAndExerciseState() {
        val sessions = listOf(
            session(id = 12L, weekNumber = 1, dayIndex = 1, sequenceNumber = 2, focus = SessionFocus.LOWER),
            session(id = 11L, weekNumber = 1, dayIndex = 0, sequenceNumber = 1, focus = SessionFocus.UPPER, timeBudgetMinutes = 45),
        )
        val exercisesBySessionId = mapOf(
            11L to listOf(
                plannedExercise(sessionId = 11L, exerciseId = 101L, sortOrder = 2),
                plannedExercise(sessionId = 11L, exerciseId = 77L, sortOrder = 0),
                plannedExercise(sessionId = 11L, exerciseId = 88L, sortOrder = 1),
                plannedExercise(sessionId = 11L, exerciseId = 88L, sortOrder = 3),
            ),
            12L to listOf(
                plannedExercise(sessionId = 12L, exerciseId = 202L, sortOrder = 0),
            ),
        )
        val exerciseNameById = mapOf(
            77L to "Bench Press",
            88L to "Chest Supported Row",
            101L to "Incline Dumbbell Press",
            202L to "Romanian Deadlift",
        )

        val summaries = buildUpcomingProgramSessionSummaries(
            sessions = sessions,
            exercisesBySessionId = exercisesBySessionId,
            exerciseNameById = exerciseNameById,
        )

        assertEquals(2, summaries.size)
        assertEquals(11L, summaries[0].sessionId)
        assertEquals("Upper Body", summaries[0].focusLabel)
        assertEquals(1, summaries[0].weekNumber)
        assertEquals(1, summaries[0].dayNumber)
        assertEquals(0, summaries[0].sequenceOffset)
        assertEquals(45, summaries[0].timeBudgetMinutes)
        assertEquals(
            listOf("Bench Press", "Chest Supported Row", "Incline Dumbbell Press"),
            summaries[0].exerciseNames,
        )
        assertEquals("Lower Body", summaries[1].focusLabel)
        assertEquals(listOf("Romanian Deadlift"), summaries[1].exerciseNames)
    }

    @Test
    fun buildUpcomingProgramSessionSummaries_filtersMissingExerciseNamesAndRespectsLimit() {
        val sessions = (1..5).map { index ->
            session(
                id = index.toLong(),
                weekNumber = 1,
                dayIndex = index - 1,
                sequenceNumber = index,
                focus = SessionFocus.FULL_BODY,
            )
        }
        val exercisesBySessionId = sessions.associate { session ->
            session.id to listOf(
                plannedExercise(sessionId = session.id, exerciseId = session.id * 10, sortOrder = 0),
                plannedExercise(sessionId = session.id, exerciseId = 999L, sortOrder = 1),
            )
        }
        val exerciseNameById = mapOf(
            10L to "Goblet Squat",
            20L to "Push-Up",
            30L to "Split Squat",
            40L to "Lat Pulldown",
        )

        val summaries = buildUpcomingProgramSessionSummaries(
            sessions = sessions,
            exercisesBySessionId = exercisesBySessionId,
            exerciseNameById = exerciseNameById,
        )

        assertEquals(4, summaries.size)
        assertEquals(listOf("Goblet Squat"), summaries[0].exerciseNames)
        assertEquals(listOf("Lat Pulldown"), summaries[3].exerciseNames)
        assertTrue(summaries.all { it.focusLabel == "Full Body" })
    }

    @Test
    fun buildUpcomingProgramSessionSummaries_preservesFormulaAFocusLabels() {
        val summaries = buildUpcomingProgramSessionSummaries(
            sessions = listOf(
                session(
                    id = 21L,
                    weekNumber = 1,
                    dayIndex = 0,
                    sequenceNumber = 1,
                    focus = SessionFocus.UPPER_PUSH_STRENGTH,
                ),
                session(
                    id = 22L,
                    weekNumber = 1,
                    dayIndex = 1,
                    sequenceNumber = 2,
                    focus = SessionFocus.LOWER_HIGH_REPS,
                ),
            ),
            exercisesBySessionId = emptyMap(),
            exerciseNameById = emptyMap(),
        )

        assertEquals("UPPER-PUSH-S (chest/delts/tri HEAVY)", summaries[0].focusLabel)
        assertEquals("LOWER-H (legs/abs HIGH REPS)", summaries[1].focusLabel)
    }

    @Test
    fun buildUpcomingProgramSessionSummaries_preservesFormulaBFocusLabels() {
        val summaries = buildUpcomingProgramSessionSummaries(
            sessions = listOf(
                session(
                    id = 31L,
                    weekNumber = 1,
                    dayIndex = 0,
                    sequenceNumber = 1,
                    focus = SessionFocus.GLUTES_HAMSTRINGS_STRENGTH,
                ),
                session(
                    id = 32L,
                    weekNumber = 1,
                    dayIndex = 1,
                    sequenceNumber = 2,
                    focus = SessionFocus.REAR_SIDE_DELTS_HIGH_REPS,
                ),
            ),
            exercisesBySessionId = emptyMap(),
            exerciseNameById = emptyMap(),
        )

        assertEquals("GLUTES+HAMSTRINGS-S (HEAVY)", summaries[0].focusLabel)
        assertEquals("REAR DELTS + SIDE DELTS-H (HIGH REPS)", summaries[1].focusLabel)
    }

    private fun session(
        id: Long,
        weekNumber: Int,
        dayIndex: Int,
        sequenceNumber: Int,
        focus: SessionFocus,
        timeBudgetMinutes: Int? = null,
    ): PlannedSession {
        return PlannedSession(
            id = id,
            programId = "program-1",
            weekNumber = weekNumber,
            dayIndex = dayIndex,
            sequenceNumber = sequenceNumber,
            focusKey = focus,
            plannedSets = 16,
            timeBudgetMinutes = timeBudgetMinutes,
        )
    }

    private fun plannedExercise(
        sessionId: Long,
        exerciseId: Long,
        sortOrder: Int,
    ): PlannedSessionExercise {
        return PlannedSessionExercise(
            plannedSessionId = sessionId,
            exerciseId = exerciseId,
            sortOrder = sortOrder,
            executionStyle = ExecutionStyle.NORMAL,
        )
    }
}
