package com.fitlib.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLoggedHistoryFilterTest {
    @Test
    fun loggedHistoryFilterClause_targetsCompletedPerformedSets() {
        assertEquals(
            """
            EXISTS (
                SELECT 1
                FROM performed_exercises pe
                INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
                WHERE pe.exercise_id = e.exercise_id
                  AND ps.is_completed = 1
            )
            """.trimIndent(),
            loggedHistoryFilterClause(),
        )
    }

    @Test
    fun loggedHistoryFilterClause_supportsCustomExerciseColumnAlias() {
        assertEquals(
            """
            EXISTS (
                SELECT 1
                FROM performed_exercises pe
                INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
                WHERE pe.exercise_id = exercise_summary.exercise_id
                  AND ps.is_completed = 1
            )
            """.trimIndent(),
            loggedHistoryFilterClause("exercise_summary.exercise_id"),
        )
    }

    @Test
    fun libraryFiltersActiveCount_includesLoggedHistorySelection() {
        assertEquals(
            3,
            LibraryFilters(
                equipment = setOf("Cable"),
                recommendationBiases = setOf(RecommendationBias.MoreOften),
                hasLoggedHistoryOnly = true,
            ).activeCount(),
        )
    }
}
