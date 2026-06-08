package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryEquipmentLocationFilterTest {
    @Test
    fun libraryFiltersActiveCount_includesEquipmentLocationSelection() {
        assertEquals(
            1,
            LibraryFilters(equipmentLocation = LibraryEquipmentLocation.Home).activeCount(),
        )
    }

    @Test
    fun normalizeLibraryEquipmentLocationEquipment_trimsSortsAndDropsBlanks() {
        assertEquals(
            listOf("Bodyweight", "Dumbbell"),
            normalizeLibraryEquipmentLocationEquipment(listOf(" Dumbbell ", "", "Bodyweight", "Dumbbell")),
        )
    }

    @Test
    fun libraryEquipmentLocationFilterClause_rejectsEmptyLocationEquipment() {
        assertEquals("0 = 1", libraryEquipmentLocationFilterClause(emptySet()))
    }

    @Test
    fun libraryEquipmentLocationFilterClause_requiresAllExerciseEquipmentInsideLocationSet() {
        val clause = libraryEquipmentLocationFilterClause(setOf("Bodyweight", "Dumbbell"))

        assertTrue(clause.contains("NOT EXISTS"))
        assertTrue(clause.contains("location_eq.equipment_name NOT IN (?,?)"))
        assertTrue(clause.contains("COALESCE(e.primary_equipment, 'Bodyweight') IN (?,?)"))
        assertTrue(clause.contains("location_match_eq.equipment_name IN (?,?)"))
    }
}
