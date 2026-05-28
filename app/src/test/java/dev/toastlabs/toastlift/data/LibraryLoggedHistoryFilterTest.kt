package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLoggedHistoryFilterTest {
    @Test
    fun loggedHistoryFilterClause_targetsCompletedSetsWithLoggedReps() {
        assertEquals(
            """
            EXISTS (
                SELECT 1
                FROM performed_exercises pe
                INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
                WHERE pe.exercise_id = e.exercise_id
                  AND ps.is_completed = 1
                  AND COALESCE(ps.actual_reps, 0) > 0
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
                  AND COALESCE(ps.actual_reps, 0) > 0
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

    @Test
    fun librarySearchOrderBy_usesOriginalOrderWhenNoFiltersAreActive() {
        assertEquals(
            "COALESCE(p.is_favorite, 0) DESC, e.name ASC",
            librarySearchOrderBy(LibraryFilters()),
        )
    }

    @Test
    fun librarySearchOrderBy_prioritizesLoggedSessionCountWhenAnyFilterIsActive() {
        assertEquals(
            "COALESCE(logged_history.logged_session_count, 0) DESC, COALESCE(p.is_favorite, 0) DESC, e.name ASC",
            librarySearchOrderBy(
                LibraryFilters(freshnessMuscleKeys = setOf("erector_spinae")),
            ),
        )
    }
}
