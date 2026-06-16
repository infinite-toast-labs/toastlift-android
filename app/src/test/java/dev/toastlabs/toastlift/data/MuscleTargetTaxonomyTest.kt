package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleTargetTaxonomyTest {

    @Test
    fun knownCatalogMuscleTargetMappings_coverEveryKnownCatalogMuscleTerm() {
        val expected = mapOf(
            "abdominals" to null,
            "abductors" to "abductors",
            "adductor magnus" to "adductors",
            "adductors" to "adductors",
            "anconeus" to "triceps",
            "anterior deltoids" to "front_delts",
            "back" to "back",
            "biceps" to "biceps",
            "biceps brachii" to "biceps",
            "biceps femoris" to "hamstrings",
            "brachialis" to "biceps",
            "brachioradialis" to "forearms",
            "calves" to "calves",
            "cardio" to null,
            "chest" to "chest",
            "erector spinae" to null,
            "extensor digitorum longus" to null,
            "extensor hallucis longus" to null,
            "flexor carpi radialis" to "forearms",
            "forearms" to "forearms",
            "gastrocnemius" to "calves",
            "glutes" to "glutes",
            "gluteus maximus" to "glutes",
            "gluteus medius" to "glutes",
            "gluteus minimus" to "glutes",
            "hamstrings" to "hamstrings",
            "hip flexors" to null,
            "iliopsoas" to null,
            "infraspinatus" to null,
            "lateral deltoids" to "side_delts",
            "latissimus dorsi" to "lats",
            "levator scapulae" to "traps",
            "medial deltoids" to "side_delts",
            "obliques" to null,
            "pectoralis major" to "chest",
            "posterior deltoids" to "rear_delts",
            "quadriceps" to "quadriceps",
            "quadriceps femoris" to "quadriceps",
            "rectus abdominis" to null,
            "rectus femoris" to "quadriceps",
            "rhomboids" to "upper_back",
            "serratus anterior" to null,
            "shins" to null,
            "shoulders" to "shoulders",
            "soleus" to "calves",
            "subscapularis" to null,
            "supraspinatus" to null,
            "tensor fasciae latae" to "abductors",
            "teres major" to "lats",
            "teres minor" to null,
            "tibialis anterior" to null,
            "tibialis posterior" to null,
            "transverse abdominis" to null,
            "trapezius" to "traps",
            "triceps" to "triceps",
            "triceps brachii" to "triceps",
            "upper trapezius" to "traps",
            "vastus mediais" to "quadriceps",
        )
        val actual = knownCatalogMuscleTargetMappings()

        assertTrue(actual.keys.containsAll(expected.keys))
        expected.forEach { (muscleName, expectedKey) ->
            assertEquals("Unexpected mapping for $muscleName", expectedKey, actual.getValue(muscleName))
            assertEquals("Runtime classifier diverged for $muscleName", expectedKey, normalizeMuscleTargetSubcategoryKey(muscleName))
        }
        actual.values.filterNotNull().forEach { subcategoryKey ->
            assertTrue("$subcategoryKey must be a known muscle target subcategory", muscleTargetSubcategory(subcategoryKey) != null)
        }
        assertEquals("forearms", normalizeMuscleTargetSubcategoryKey("Loaded Brachioradialis"))
        assertEquals("forearms", normalizeMuscleTargetSubcategoryKey("Left Flexor Carpi Radialis"))
    }

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

    @Test
    fun resolveMuscleTargetContributions_doesNotMapTensorFasciaeLataeToLats() {
        val cases = listOf(
            "Hip Abductor Machine" to resolveMuscleTargetContributions(
                targetMuscleGroup = "Abductors",
                primeMover = "Gluteus Medius",
                secondaryMuscle = "Gluteus Minimus",
                tertiaryMuscle = "Tensor Fasciae Latae",
                movementPatterns = listOf("Hip Abduction"),
            ),
            "Cable Hip Abduction" to resolveMuscleTargetContributions(
                targetMuscleGroup = "Abductors",
                primeMover = "Gluteus Medius",
                secondaryMuscle = "Gluteus Minimus",
                tertiaryMuscle = "Tensor Fasciae Latae",
                movementPatterns = listOf("Hip Abduction"),
            ),
            "Side Plank Hip Abduction" to resolveMuscleTargetContributions(
                targetMuscleGroup = "Abductors",
                primeMover = "Gluteus Medius",
                secondaryMuscle = "Gluteus Minimus",
                tertiaryMuscle = "Tensor Fasciae Latae",
                movementPatterns = listOf("Hip Abduction", "Anti-Lateral Flexion"),
            ),
        )

        cases.forEach { (exerciseName, contributions) ->
            val keys = contributions.map { it.subcategoryKey }.toSet()

            assertTrue("$exerciseName should still target abductors", "abductors" in keys)
            assertTrue("$exerciseName should still target glutes", "glutes" in keys)
            assertFalse("$exerciseName should not target lats from Tensor Fasciae Latae", "lats" in keys)
            assertFalse("$exerciseName should not roll false lats into back", "back" in keys)
        }
    }

    @Test
    fun resolveMuscleTargetContributions_mapsBicepsFemorisToHamstringsNotArmBiceps() {
        val cases = listOf(
            "Thigh Adductor" to resolveMuscleTargetContributions(
                targetMuscleGroup = "Adductors",
                primeMover = "Adductor Magnus",
                secondaryMuscle = "Gluteus Maximus",
                tertiaryMuscle = "Biceps Femoris",
                movementPatterns = listOf("Hip Adduction"),
            ),
            "Bodyweight Glute Bridge" to resolveMuscleTargetContributions(
                targetMuscleGroup = "Glutes",
                primeMover = "Gluteus Maximus",
                secondaryMuscle = "Biceps Femoris",
                tertiaryMuscle = "Erector Spinae",
                movementPatterns = listOf("Hip Extension"),
            ),
            "Dumbbell Romanian Deadlift" to resolveMuscleTargetContributions(
                targetMuscleGroup = "Hamstrings",
                primeMover = "Biceps Femoris",
                secondaryMuscle = "Gluteus Maximus",
                tertiaryMuscle = "Erector Spinae",
                movementPatterns = listOf("Hip Hinge"),
            ),
            "Walking Lunge" to resolveMuscleTargetContributions(
                targetMuscleGroup = "Quadriceps",
                primeMover = "Quadriceps Femoris",
                secondaryMuscle = "Gluteus Maximus",
                tertiaryMuscle = "Biceps Femoris",
                movementPatterns = listOf("Knee Dominant"),
            ),
        )

        cases.forEach { (exerciseName, contributions) ->
            val keys = contributions.map { it.subcategoryKey }.toSet()

            assertTrue("$exerciseName should target hamstrings from Biceps Femoris", "hamstrings" in keys)
            assertFalse("$exerciseName should not target arm biceps from Biceps Femoris", "biceps" in keys)
        }
    }
}
