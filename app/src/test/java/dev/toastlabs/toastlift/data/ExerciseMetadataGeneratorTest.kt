package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMetadataGeneratorTest {
    @Test
    fun buildCustomExerciseMetadataPrompt_returnsSamplePromptSentToGemini() {
        val prompt = buildCustomExerciseMetadataPrompt(
            exerciseName = "Machine Chest Press",
            taxonomy = CustomExerciseTaxonomy(
                difficultyLevels = listOf("Beginner", "Intermediate"),
                bodyRegions = listOf("Upper Body", "Lower Body"),
                targetMuscles = listOf("Chest", "Back"),
                primeMovers = listOf("Pectoralis Major", "Anterior Deltoids", "Triceps Brachii"),
                equipmentOptions = listOf("Machine", "Dumbbell"),
                postures = listOf("Seated", "Standing"),
                armUsageOptions = listOf("Double Arm", "Single Arm"),
                armPatternOptions = listOf("Continuous", "Alternating"),
                gripOptions = listOf("Neutral", "Pronated"),
                loadPositionOptions = listOf("Other", "Front"),
                legPatternOptions = listOf("Continuous", "Other"),
                footElevationOptions = listOf("No Elevation", "Bench"),
                combinationTypeOptions = listOf("Single Exercise", "Combination"),
                forceTypeOptions = listOf("Push", "Pull"),
                mechanicsOptions = listOf("Compound", "Isolation"),
                lateralityOptions = listOf("Bilateral", "Unilateral"),
                classificationOptions = listOf("Bodybuilding", "Powerlifting"),
                movementPatternOptions = listOf("Horizontal Press", "Vertical Press"),
                planeOfMotionOptions = listOf("Transverse Plane", "Sagittal Plane"),
            ),
            nearbyExercises = listOf(
                ExerciseSummary(
                    id = 101L,
                    name = "Chest Press",
                    difficulty = "Beginner",
                    bodyRegion = "Upper Body",
                    targetMuscleGroup = "Chest",
                    equipment = "Machine",
                    secondaryEquipment = null,
                    mechanics = "Compound",
                    favorite = false,
                ),
            ),
        )

        assertEquals(
            """
            You are helping fill a local SQLite exercise catalog for a workout app.
            Given only an exercise name, infer the best structured metadata for the exercise.
            
            Exercise name:
            Machine Chest Press
            
            Nearby existing exercises:
            - Chest Press | bodyRegion=Upper Body | target=Chest | equipment=Machine
            
            Use existing canonical values when possible.
            Do not invent new values for these closed-set fields:
            - difficultyLevel
            - bodyRegion
            - targetMuscleGroup
            - primaryEquipment
            - secondaryEquipment
            - forceType
            - mechanics
            - laterality
            - armUsage
            - armPattern
            - legPattern
            - combinationType
            - classification
            - movementPatterns
            - planesOfMotion
            
            Closed-set options:
            difficultyLevel=[Beginner, Intermediate]
            bodyRegion=[Upper Body, Lower Body]
            targetMuscleGroup=[Chest, Back]
            primaryEquipment=[Machine, Dumbbell]
            secondaryEquipment=[Machine, Dumbbell, ]
            posture=[Seated, Standing]
            armUsage=[Double Arm, Single Arm]
            armPattern=[Continuous, Alternating]
            grip=[Neutral, Pronated]
            loadPositionEnding=[Other, Front]
            legPattern=[Continuous, Other]
            footElevation=[No Elevation, Bench]
            combinationType=[Single Exercise, Combination]
            forceType=[Push, Pull]
            mechanics=[Compound, Isolation]
            laterality=[Bilateral, Unilateral]
            classification=[Bodybuilding, Powerlifting]
            movementPatterns=[Horizontal Press, Vertical Press]
            planesOfMotion=[Transverse Plane, Sagittal Plane]
            primeMovers=[Pectoralis Major, Anterior Deltoids, Triceps Brachii]
            
            Return only JSON with this exact shape:
            {
              "name": "string",
              "difficultyLevel": "string",
              "bodyRegion": "string",
              "targetMuscleGroup": "string",
              "primeMoverMuscle": "string",
              "secondaryMuscle": "string",
              "tertiaryMuscle": "string",
              "primaryEquipment": "string",
              "primaryItemCount": 1,
              "secondaryEquipment": "string",
              "secondaryItemCount": 0,
              "posture": "string",
              "armUsage": "string",
              "armPattern": "string",
              "grip": "string",
              "loadPositionEnding": "string",
              "legPattern": "string",
              "footElevation": "string",
              "combinationType": "string",
              "forceType": "string",
              "mechanics": "string",
              "laterality": "string",
              "classification": "string",
              "movementPatterns": ["string"],
              "planesOfMotion": ["string"],
              "shortDemoLabel": "string",
              "shortDemoUrl": "string",
              "inDepthLabel": "string",
              "inDepthUrl": "string",
              "synonyms": ["string"]
            }
            
            Requirements:
            - Prefer generic equipment categories like "Machine" instead of brand-specific equipment.
            - Keep URLs blank unless you are reasonably confident.
            - Use 1-3 movement patterns.
            - Use 1-3 planes of motion.
            - If the exercise is a machine chest press, likely mechanics is Compound and forceType is Push.
            - Return empty strings instead of nulls for optional scalar fields.
            """.trimIndent(),
            prompt,
        )
    }

    @Test
    fun buildCustomExerciseMetadataPrompt_usesNoneWhenNoNearbyExercisesExist() {
        val prompt = buildCustomExerciseMetadataPrompt(
            exerciseName = "Cable Lateral Raise",
            taxonomy = CustomExerciseTaxonomy(),
            nearbyExercises = emptyList(),
        )

        assertTrue(prompt.contains("Nearby existing exercises:\nNone"))
    }
}
