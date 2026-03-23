package com.fitlib.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentSelectionTest {
    @Test
    fun equipmentSelectionOptions_keepsItemsBeyondFirstThirtySix() {
        val options = listOf(
            "Ab Wheel",
            "Barbell",
            "Battle Ropes",
            "Bench (Decline)",
            "Bench (Flat)",
            "Bench (Incline)",
            "Bodyweight",
            "Bulgarian Bag",
            "Cable",
            "Climbing Rope",
            "Clubbell",
            "Dumbbell",
            "EZ Bar",
            "Gravity Boots",
            "Gymnastic Rings",
            "Heavy Sandbag",
            "Indian Club",
            "Kettlebell",
            "Landmine",
            "Macebell",
            "Machine",
            "Medicine Ball",
            "Miniband",
            "Parallette Bars",
            "Plyo Box",
            "Pull Up Bar",
            "Resistance Band",
            "Sandbag",
            "Slam Ball",
            "Slant Board",
            "Sled",
            "Sledge Hammer",
            "Sliders",
            "Stability Ball",
            "Superband",
            "Suspension Trainer",
            "Tire",
            "Trap Bar",
            "Wall Ball",
            "Weight Plate",
        )

        val visible = equipmentSelectionOptions(options)

        assertEquals(options.size, visible.size)
        assertTrue(visible.contains("Stability Ball"))
        assertTrue(visible.contains("Trap Bar"))
        assertTrue(visible.contains("Weight Plate"))
    }
}
