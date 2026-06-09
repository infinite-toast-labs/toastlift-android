package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.LibraryEquipmentLocation
import dev.toastlabs.toastlift.data.LibraryFilters
import dev.toastlabs.toastlift.data.LocationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryEquipmentLocationFilterTest {
    @Test
    fun libraryFiltersWithEquipmentLocation_appliesHomeEquipmentFromProfileSelections() {
        val filters = libraryFiltersWithEquipmentLocation(
            filters = LibraryFilters(equipmentLocation = LibraryEquipmentLocation.Home),
            locationModes = listOf(
                LocationMode(id = 1L, name = "home", displayName = "Home"),
                LocationMode(id = 2L, name = "gym", displayName = "Gym"),
            ),
            equipmentByLocation = mapOf(
                1L to setOf("Bodyweight", "Dumbbell"),
                2L to setOf("Cable", "Machine"),
            ),
        )

        assertEquals(LibraryEquipmentLocation.Home, filters.equipmentLocation)
        assertEquals(setOf("Bodyweight", "Dumbbell"), filters.equipmentLocationEquipment)
    }

    @Test
    fun libraryFiltersWithEquipmentLocation_clearsDerivedEquipmentWhenNoLocationIsSelected() {
        val filters = libraryFiltersWithEquipmentLocation(
            filters = LibraryFilters(equipmentLocationEquipment = setOf("Cable")),
            locationModes = emptyList(),
            equipmentByLocation = emptyMap(),
        )

        assertTrue(filters.equipmentLocationEquipment.isEmpty())
    }

    @Test
    fun libraryFiltersWithActiveEquipmentLocation_appliesActiveGymLocationToMuscleTargetShortcut() {
        val filters = libraryFiltersWithActiveEquipmentLocation(
            filters = muscleTargetLibraryFilters(bucketKey = null, subcategoryKey = "Chest"),
            activeLocationModeId = 2L,
            locationModes = listOf(
                LocationMode(id = 1L, name = "home", displayName = "Home"),
                LocationMode(id = 2L, name = "gym", displayName = "Gym"),
            ),
        )

        assertEquals(setOf("chest"), filters.muscleTargetSubcategoryKeys)
        assertEquals(LibraryEquipmentLocation.Gym, filters.equipmentLocation)
        assertTrue(filters.equipmentLocationEquipment.isEmpty())
    }

    @Test
    fun libraryFiltersWithActiveEquipmentLocation_appliesActiveHomeLocationToFreshnessShortcut() {
        val filters = libraryFiltersWithActiveEquipmentLocation(
            filters = activeSessionFreshnessLibraryFilters(
                muscleKey = "abductors",
                muscleLabel = "Abductors",
            ),
            activeLocationModeId = 1L,
            locationModes = listOf(
                LocationMode(id = 1L, name = "home", displayName = "Home"),
                LocationMode(id = 2L, name = "gym", displayName = "Gym"),
            ),
        )

        assertEquals(setOf("abductors"), filters.freshnessMuscleKeys)
        assertEquals(LibraryEquipmentLocation.Home, filters.equipmentLocation)
        assertTrue(filters.equipmentLocationEquipment.isEmpty())
    }

    @Test
    fun libraryFiltersWithActiveEquipmentLocation_leavesFiltersUnchangedWhenNoActiveLocationMatches() {
        val base = LibraryFilters()
        val filters = libraryFiltersWithActiveEquipmentLocation(
            filters = base,
            activeLocationModeId = 99L,
            locationModes = listOf(
                LocationMode(id = 1L, name = "home", displayName = "Home"),
            ),
        )

        assertEquals(base, filters)
    }
}
