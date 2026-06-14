package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleTargetTaxonomyTest {

    @Test
    fun resolveMuscleTargetContributions_rollsAnteriorDeltsIntoShoulders() {
        val contributions = resolveMuscleTargetContributions(
            targetMuscleGroup = "Shoulders",
            primeMover = "Anterior Deltoids",
            secondaryMuscle = "Triceps Brachii",
            tertiaryMuscle = null,
            movementPatterns = emptyList(),
        ).associateBy { it.subcategoryKey }

        assertEquals(1.0, contributions.getValue("shoulders").weight, 0.001)
        assertEquals(1.0, contributions.getValue("front_delts").weight, 0.001)
        assertEquals(0.5, contributions.getValue("triceps").weight, 0.001)
    }

    @Test
    fun resolveMuscleTargetContributions_rollsBackChildrenIntoBack() {
        val contributions = resolveMuscleTargetContributions(
            targetMuscleGroup = "Back",
            primeMover = "Latissimus Dorsi",
            secondaryMuscle = "Rhomboids",
            tertiaryMuscle = "Biceps Brachii",
            movementPatterns = emptyList(),
        ).associateBy { it.subcategoryKey }

        assertEquals(1.0, contributions.getValue("back").weight, 0.001)
        assertEquals(1.0, contributions.getValue("lats").weight, 0.001)
        assertEquals(0.5, contributions.getValue("upper_back").weight, 0.001)
        assertEquals(0.5, contributions.getValue("biceps").weight, 0.001)
    }

    @Test
    fun resolveMuscleTargetContributions_addsBackRollupWithoutDirectBackLabel() {
        val contributions = resolveMuscleTargetContributions(
            targetMuscleGroup = "Shoulders",
            primeMover = "Posterior Deltoids",
            secondaryMuscle = null,
            tertiaryMuscle = null,
            movementPatterns = emptyList(),
        ).associateBy { it.subcategoryKey }

        assertEquals(1.0, contributions.getValue("back").weight, 0.001)
        assertEquals(1.0, contributions.getValue("rear_delts").weight, 0.001)
        assertTrue(contributions.containsKey("shoulders"))
    }
}
