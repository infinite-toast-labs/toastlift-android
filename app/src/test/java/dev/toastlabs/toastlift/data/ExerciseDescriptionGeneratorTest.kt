package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseDescriptionGeneratorTest {
    @Test
    fun formatGeneratedExerciseDescription_usesSeparatedNumberedStepsAndCueBullets() {
        val formatted = formatGeneratedExerciseDescription(
            GeneratedExerciseDescriptionContent(
                summary = "Targets the chest with a controlled pressing path.",
                steps = listOf(
                    "Set the seat so the handles line up around mid-chest.",
                    "Brace your torso and press the handles forward until your arms extend.",
                    "Lower the handles back under control without letting the shoulders roll forward.",
                ),
                keyCues = listOf(
                    "Keep your wrists stacked over your elbows.",
                    "Let the chest lead the press instead of shrugging.",
                ),
            ),
        )

        assertEquals(
            """
            Targets the chest with a controlled pressing path.

            1. Set the seat so the handles line up around mid-chest.

            2. Brace your torso and press the handles forward until your arms extend.

            3. Lower the handles back under control without letting the shoulders roll forward.

            Key cues:
            - Keep your wrists stacked over your elbows.
            - Let the chest lead the press instead of shrugging.
            """.trimIndent(),
            formatted,
        )
    }

    @Test
    fun exerciseDetail_descriptionPrefersGeneratedDescriptionOverCanonical() {
        val detail = ExerciseDetail(
            summary = ExerciseSummary(
                id = 42L,
                name = "Machine Chest Press",
                difficulty = "Intermediate",
                bodyRegion = "Upper Body",
                targetMuscleGroup = "Chest",
                equipment = "Machine",
                secondaryEquipment = null,
                mechanics = "Compound",
                favorite = false,
            ),
            notes = null,
            primeMover = "Pectoralis Major",
            secondaryMuscle = null,
            tertiaryMuscle = null,
            posture = "Seated",
            laterality = "Bilateral",
            classification = "Bodybuilding",
            movementPatterns = listOf("Horizontal Press"),
            planesOfMotion = listOf("Transverse Plane"),
            demoUrl = null,
            explanationUrl = null,
            canonicalDescription = "Canonical description",
            generatedDescription = UserGeneratedExerciseDescription(
                description = "Generated description",
                generationModel = "gemini-test",
                generationPromptVersion = EXERCISE_DESCRIPTION_PROMPT_VERSION,
                createdAtUtc = "2026-03-24T00:00:00Z",
                updatedAtUtc = "2026-03-24T00:00:00Z",
            ),
            synonyms = emptyList(),
        )

        assertEquals("Generated description", detail.description)
    }
}
