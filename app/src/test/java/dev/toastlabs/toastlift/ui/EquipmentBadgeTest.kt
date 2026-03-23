package dev.toastlabs.toastlift.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentBadgeTest {
    @Test
    fun usesHardcodedMappingsForExistingCatalogEquipment() {
        assertEquals("AW", equipmentBadgeLabel("Ab Wheel"))
        assertEquals("BB", equipmentBadgeLabel("Barbell"))
        assertEquals("TR", equipmentBadgeLabel("Suspension Trainer"))
        assertEquals("SN", equipmentBadgeLabel("Slant Board"))
    }

    @Test
    fun derivesSemanticBadgesForUnknownEquipment() {
        assertEquals("CB", equipmentBadgeLabel("Chain Belt"))
        assertEquals("BE", equipmentBadgeLabel("Bench"))
        assertEquals("WV", equipmentBadgeLabel("Weighted Vest Rack"))
    }

    @Test
    fun fallsBackToExerciseDefaultForBlankEquipment() {
        assertEquals("EX", equipmentBadgeLabel(null))
        assertEquals("EX", equipmentBadgeLabel(""))
        assertEquals("EX", equipmentBadgeLabel("   "))
    }
}
