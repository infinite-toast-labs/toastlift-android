package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogRepositoryNoteNormalizationTest {
    @Test
    fun normalizeExerciseNote_returnsNullForBlankInput() {
        assertNull(normalizeExerciseNote("   \n\t  "))
    }

    @Test
    fun normalizeExerciseNote_trimsOuterWhitespaceAndKeepsInnerFormatting() {
        assertEquals(
            "Brace core before unracking\nKeep elbows under wrists",
            normalizeExerciseNote("  Brace core before unracking\nKeep elbows under wrists  "),
        )
    }
}
