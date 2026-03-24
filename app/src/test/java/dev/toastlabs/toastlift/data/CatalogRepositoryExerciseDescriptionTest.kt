package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogRepositoryExerciseDescriptionTest {
    @Test
    fun normalizeExerciseDescription_trimsAndRejectsBlankValues() {
        assertEquals(
            "Step 1. Brace.\nStep 2. Lower slowly.",
            normalizeExerciseDescription("  Step 1. Brace.\nStep 2. Lower slowly.  "),
        )
        assertNull(normalizeExerciseDescription("   "))
        assertNull(normalizeExerciseDescription(null))
    }

    @Test
    fun resolveExerciseDescriptionColumn_prefersCommonDescriptionColumns() {
        assertEquals(
            "description",
            resolveExerciseDescriptionColumn(listOf("exercise_id", "name", "description", "instructions")),
        )
        assertEquals(
            "instructions",
            resolveExerciseDescriptionColumn(listOf("exercise_id", "name", "instructions")),
        )
        assertEquals(
            "exercise_description",
            resolveExerciseDescriptionColumn(listOf("exercise_id", "name", "exercise_description")),
        )
        assertNull(resolveExerciseDescriptionColumn(listOf("exercise_id", "name", "in_depth_url")))
    }
}
