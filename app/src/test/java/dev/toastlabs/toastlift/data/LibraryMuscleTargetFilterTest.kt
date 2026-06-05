package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMuscleTargetFilterTest {
    @Test
    fun libraryMuscleTargetFilterClause_returnsNullWithoutKnownTargets() {
        assertNull(libraryMuscleTargetFilterClause(emptySet(), emptySet()))
        assertNull(libraryMuscleTargetFilterClause(setOf("unknown"), setOf("also_unknown")))
    }

    @Test
    fun libraryMuscleTargetFilterClause_expandsBucketToSubcategoryTermsAcrossMuscleColumns() {
        val clause = requireNotNull(
            libraryMuscleTargetFilterClause(
                bucketKeys = setOf("legs"),
                subcategoryKeys = emptySet(),
            ),
        )

        assertTrue(clause.contains("e.target_muscle_group"))
        assertTrue(clause.contains("e.prime_mover_muscle"))
        assertTrue(clause.contains("e.secondary_muscle"))
        assertTrue(clause.contains("e.tertiary_muscle"))
        assertTrue(clause.contains("%quad%"))
        assertTrue(clause.contains("%hamstring%"))
        assertTrue(clause.contains("%glute%"))
    }

    @Test
    fun libraryMuscleTargetFilterClause_combinesBucketAndSubcategorySelections() {
        val clause = requireNotNull(
            libraryMuscleTargetFilterClause(
                bucketKeys = setOf("push"),
                subcategoryKeys = setOf("hamstrings"),
            ),
        )

        assertTrue(clause.contains("%chest%"))
        assertTrue(clause.contains("%tricep%"))
        assertTrue(clause.contains("%hamstring%"))
    }

    @Test
    fun librarySearchOrderBy_prioritizesMuscleTargetRoleBeforeHistoryWhenTargetFilterIsActive() {
        val orderBy = librarySearchOrderBy(
            LibraryFilters(muscleTargetSubcategoryKeys = setOf("chest")),
        )

        assertTrue(orderBy.startsWith("CASE"))
        assertTrue(orderBy.contains("lower(COALESCE(e.target_muscle_group, '')) = 'chest'"))
        assertTrue(orderBy.contains("e.prime_mover_muscle"))
        assertTrue(orderBy.indexOf("= 'chest'") < orderBy.indexOf("e.prime_mover_muscle"))
        assertTrue(orderBy.contains("COALESCE(logged_history.logged_session_count, 0) DESC"))
        assertTrue(orderBy.endsWith("COALESCE(p.is_favorite, 0) DESC, e.name ASC"))
    }

    @Test
    fun libraryFiltersActiveCount_includesMuscleTargetSelections() {
        assertEquals(
            3,
            LibraryFilters(
                equipment = setOf("Cable"),
                muscleTargetBucketKeys = setOf("push"),
                muscleTargetSubcategoryKeys = setOf("chest"),
            ).activeCount(),
        )
    }
}
