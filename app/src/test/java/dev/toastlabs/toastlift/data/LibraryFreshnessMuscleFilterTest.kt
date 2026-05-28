package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFreshnessMuscleFilterTest {
    @Test
    fun libraryFreshnessMuscleFilterClause_returnsNullWhenNoKnownMusclesAreSelected() {
        assertNull(libraryFreshnessMuscleFilterClause(emptySet()))
        assertNull(libraryFreshnessMuscleFilterClause(setOf("unknown")))
    }

    @Test
    fun libraryFreshnessMuscleFilterClause_matchesLowerBackAcrossAllExerciseMuscleColumns() {
        val clause = requireNotNull(libraryFreshnessMuscleFilterClause(setOf("erector_spinae")))

        assertTrue(clause.contains("e.target_muscle_group"))
        assertTrue(clause.contains("e.prime_mover_muscle"))
        assertTrue(clause.contains("e.secondary_muscle"))
        assertTrue(clause.contains("e.tertiary_muscle"))
        assertTrue(clause.contains("%erector%"))
        assertTrue(clause.contains("%lower back%"))
    }

    @Test
    fun libraryFiltersActiveCount_includesFreshnessMuscleSelection() {
        assertEquals(
            2,
            LibraryFilters(
                equipment = setOf("Cable"),
                freshnessMuscleKeys = setOf("erector_spinae"),
            ).activeCount(),
        )
    }
}
