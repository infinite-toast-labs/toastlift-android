package dev.toastlabs.toastlift.data

import dev.toastlabs.toastlift.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ExerciseMetadataGeneratorTest {
    @Test
    fun liveSmokeGeminiExerciseMetadataGenerator_returnsRealMetadata() {
        assumeLiveAiSmokeTestsEnabled()
        assertTrue("GEMINI_API_KEY must be configured for live smoke tests.", BuildConfig.GEMINI_API_KEY.isNotBlank())
        assertTrue("GEMINI_PRIMARY_MODEL must be configured for live smoke tests.", BuildConfig.GEMINI_PRIMARY_MODEL.isNotBlank())

        val result = GeminiExerciseMetadataGenerator().generateWithReport(
            exerciseName = "Machine Chest Press",
            taxonomy = liveSmokeTaxonomy(),
            nearbyExercises = liveSmokeNearbyExercises(),
        )

        printLiveSmokeResult("Gemini", result)
        assertReasonableLiveSmokeMetadata(result.metadata)
    }

    @Test
    fun liveSmokeOpenCodeDeepSeekV4FlashExerciseMetadataGenerator_returnsRealMetadata() {
        assumeLiveAiSmokeTestsEnabled()
        assertTrue("OPENCODE_API_KEY must be configured for live smoke tests.", BuildConfig.OPENCODE_API_KEY.isNotBlank())

        val result = OpenCodeExerciseMetadataGenerator(
            model = "deepseek-v4-flash",
        ).generateWithReport(
            exerciseName = "Machine Chest Press",
            taxonomy = liveSmokeTaxonomy(),
            nearbyExercises = liveSmokeNearbyExercises(),
        )

        printLiveSmokeResult("OpenCode DeepSeek V4 Flash", result)
        assertReasonableLiveSmokeMetadata(result.metadata)
    }

    @Test
    fun liveSmokeOpenCodeGlm52ExerciseMetadataGenerator_returnsRealMetadata() {
        assumeLiveAiSmokeTestsEnabled()
        assertTrue("OPENCODE_API_KEY must be configured for live smoke tests.", BuildConfig.OPENCODE_API_KEY.isNotBlank())

        val result = OpenCodeExerciseMetadataGenerator(
            model = "glm-5.2",
        ).generateWithReport(
            exerciseName = "Machine Chest Press",
            taxonomy = liveSmokeTaxonomy(),
            nearbyExercises = liveSmokeNearbyExercises(),
        )

        printLiveSmokeResult("OpenCode GLM 5.2", result)
        assertReasonableLiveSmokeMetadata(result.metadata)
    }

    @Test
    fun liveSmokeOpenRouterGlm52ExerciseMetadataGenerator_returnsRealMetadata() {
        assumeLiveAiSmokeTestsEnabled()
        assertTrue("OPENROUTER_API_KEY must be configured for live smoke tests.", BuildConfig.OPENROUTER_API_KEY.isNotBlank())

        val result = OpenRouterExerciseMetadataGenerator(
            model = "z-ai/glm-5.2",
        ).generateWithReport(
            exerciseName = "Machine Chest Press",
            taxonomy = liveSmokeTaxonomy(),
            nearbyExercises = liveSmokeNearbyExercises(),
        )

        printLiveSmokeResult("OpenRouter GLM 5.2", result)
        assertReasonableLiveSmokeMetadata(result.metadata)
    }

    @Test
    fun selectedCustomExerciseAiProvider_mapsLegacyAliasesToOpenCode() {
        assertEquals(CustomExerciseAiProvider.OPENCODE, selectedCustomExerciseAiProvider("pi"))
        assertEquals(CustomExerciseAiProvider.OPENCODE, selectedCustomExerciseAiProvider("pi_sdk"))
        assertEquals(CustomExerciseAiProvider.OPENCODE, selectedCustomExerciseAiProvider("opencode"))
        assertEquals(CustomExerciseAiProvider.OPENROUTER, selectedCustomExerciseAiProvider("openrouter"))
        assertEquals(CustomExerciseAiProvider.GEMINI, selectedCustomExerciseAiProvider(""))
        assertEquals(CustomExerciseAiProvider.GEMINI, selectedCustomExerciseAiProvider("gemini"))
    }

    @Test
    fun customExerciseAiModelOptionForId_usesProviderQualifiedIds() {
        assertEquals("gemini_primary", customExerciseAiModelOptionForId("gemini_primary").id)
        assertEquals("opencode_deepseek_v4_flash", customExerciseAiModelOptionForId("opencode_deepseek_v4_flash").id)
        assertEquals("opencode_glm_5_2", customExerciseAiModelOptionForId("opencode_glm_5_2").id)
        assertEquals("openrouter_z_ai_glm_5_2", customExerciseAiModelOptionForId("openrouter_z_ai_glm_5_2").id)
        assertEquals("openrouter_deepseek_v4_pro", customExerciseAiModelOptionForId("openrouter_deepseek_v4_pro").id)
        assertEquals("openrouter_owl_alpha", customExerciseAiModelOptionForId("openrouter_owl_alpha").id)
        assertEquals("openrouter_openai_gpt_latest", customExerciseAiModelOptionForId("openrouter_openai_gpt_latest").id)
        assertEquals("openrouter_google_gemini_pro_latest", customExerciseAiModelOptionForId("openrouter_google_gemini_pro_latest").id)
        assertEquals("openrouter_anthropic_claude_opus_latest", customExerciseAiModelOptionForId("openrouter_anthropic_claude_opus_latest").id)
        assertEquals("gemini_primary", customExerciseAiModelOptionForId("glm-5.2").id)
        assertEquals("gemini_primary", customExerciseAiModelOptionForId("unknown").id)
    }

    @Test
    fun selectedCustomExerciseAiModelOption_mapsOpenCodeModelNamesToSpecificOptions() {
        assertEquals("gemini_primary", selectedCustomExerciseAiModelOption(rawProvider = "gemini").id)
        assertEquals(
            "opencode_deepseek_v4_flash",
            selectedCustomExerciseAiModelOption(rawProvider = "opencode", rawOpenCodeModel = "deepseek-v4-flash").id,
        )
        assertEquals(
            "opencode_glm_5_2",
            selectedCustomExerciseAiModelOption(rawProvider = "opencode", rawOpenCodeModel = "glm-5.2").id,
        )
        assertEquals(
            "opencode_glm_5_2",
            selectedCustomExerciseAiModelOption(rawProvider = "opencode", rawOpenCodeModel = "opencode/glm-5.2:high").id,
        )
        assertEquals(
            "openrouter_z_ai_glm_5_2",
            selectedCustomExerciseAiModelOption(rawProvider = "openrouter", rawOpenRouterModel = "z-ai/glm-5.2").id,
        )
        assertEquals(
            "openrouter_deepseek_v4_pro",
            selectedCustomExerciseAiModelOption(rawProvider = "openrouter", rawOpenRouterModel = "deepseek/deepseek-v4-pro").id,
        )
        assertEquals(
            "openrouter_owl_alpha",
            selectedCustomExerciseAiModelOption(rawProvider = "openrouter", rawOpenRouterModel = "openrouter/owl-alpha").id,
        )
        assertEquals(
            "openrouter_openai_gpt_latest",
            selectedCustomExerciseAiModelOption(rawProvider = "openrouter", rawOpenRouterModel = "~openai/gpt-latest").id,
        )
        assertEquals(
            "openrouter_google_gemini_pro_latest",
            selectedCustomExerciseAiModelOption(rawProvider = "openrouter", rawOpenRouterModel = "~google/gemini-pro-latest").id,
        )
        assertEquals(
            "openrouter_anthropic_claude_opus_latest",
            selectedCustomExerciseAiModelOption(rawProvider = "openrouter", rawOpenRouterModel = "~anthropic/claude-opus-latest").id,
        )
    }

    @Test
    fun createExerciseMetadataGeneratorForOption_setsProviderSpecificGenerationModel() {
        assertEquals(
            "opencode/deepseek-v4-flash (reasoning_effort=high)",
            createExerciseMetadataGeneratorForOption(customExerciseAiModelOptionForId("opencode_deepseek_v4_flash")).generationModel,
        )
        assertEquals(
            "opencode/glm-5.2 (reasoning_effort=high)",
            createExerciseMetadataGeneratorForOption(customExerciseAiModelOptionForId("opencode_glm_5_2")).generationModel,
        )
        assertEquals(
            "openrouter/z-ai/glm-5.2",
            createExerciseMetadataGeneratorForOption(customExerciseAiModelOptionForId("openrouter_z_ai_glm_5_2")).generationModel,
        )
        assertEquals(
            "openrouter/deepseek/deepseek-v4-pro",
            createExerciseMetadataGeneratorForOption(customExerciseAiModelOptionForId("openrouter_deepseek_v4_pro")).generationModel,
        )
        assertEquals(
            "openrouter/openrouter/owl-alpha",
            createExerciseMetadataGeneratorForOption(customExerciseAiModelOptionForId("openrouter_owl_alpha")).generationModel,
        )
        assertEquals(
            "openrouter/~openai/gpt-latest (reasoning.effort=high)",
            createExerciseMetadataGeneratorForOption(customExerciseAiModelOptionForId("openrouter_openai_gpt_latest")).generationModel,
        )
        assertEquals(
            "openrouter/~google/gemini-pro-latest (reasoning.effort=high)",
            createExerciseMetadataGeneratorForOption(customExerciseAiModelOptionForId("openrouter_google_gemini_pro_latest")).generationModel,
        )
        assertEquals(
            "openrouter/~anthropic/claude-opus-latest (reasoning.effort=high)",
            createExerciseMetadataGeneratorForOption(customExerciseAiModelOptionForId("openrouter_anthropic_claude_opus_latest")).generationModel,
        )
    }

    @Test
    fun parseCustomExerciseMetadataResponse_acceptsOpenAiStyleContentJson() {
        val metadata = parseCustomExerciseMetadataResponse(
            """
            ```json
            {
              "name": "Machine Chest Press",
              "difficultyLevel": "Beginner",
              "bodyRegion": "Upper Body",
              "targetMuscleGroup": "Chest",
              "primeMoverMuscle": "Pectoralis Major",
              "secondaryMuscle": "Anterior Deltoids",
              "tertiaryMuscle": "Triceps Brachii",
              "primaryEquipment": "Machine",
              "primaryItemCount": 1,
              "secondaryEquipment": "",
              "secondaryItemCount": 0,
              "posture": "Seated",
              "armUsage": "Double Arm",
              "armPattern": "Continuous",
              "grip": "Neutral",
              "loadPositionEnding": "Other",
              "legPattern": "Continuous",
              "footElevation": "No Elevation",
              "combinationType": "Single Exercise",
              "forceType": "Push",
              "mechanics": "Compound",
              "laterality": "Bilateral",
              "classification": "Bodybuilding",
              "movementPatterns": ["Horizontal Press", ""],
              "planesOfMotion": ["Transverse Plane"],
              "shortDemoLabel": "",
              "shortDemoUrl": "",
              "inDepthLabel": "",
              "inDepthUrl": "",
              "synonyms": ["Seated Machine Chest Press"]
            }
            ```
            """.trimIndent(),
        )

        assertEquals("Machine Chest Press", metadata.name)
        assertEquals("Machine", metadata.primaryEquipment)
        assertEquals(1, metadata.primaryItemCount)
        assertEquals(null, metadata.secondaryEquipment)
        assertEquals(listOf("Horizontal Press"), metadata.movementPatterns)
        assertEquals(listOf("Seated Machine Chest Press"), metadata.synonyms)
    }

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

    private fun liveSmokeTaxonomy(): CustomExerciseTaxonomy {
        return CustomExerciseTaxonomy(
            difficultyLevels = listOf("Beginner", "Intermediate", "Advanced"),
            bodyRegions = listOf("Upper Body", "Lower Body", "Full Body", "Core"),
            targetMuscles = listOf("Chest", "Back", "Shoulders", "Triceps", "Biceps", "Quadriceps", "Glutes"),
            primeMovers = listOf("Pectoralis Major", "Anterior Deltoids", "Triceps Brachii"),
            equipmentOptions = listOf("Machine", "Dumbbell", "Barbell", "Cable", "Bodyweight", ""),
            postures = listOf("Seated", "Standing", "Supine", "Prone"),
            armUsageOptions = listOf("Double Arm", "Single Arm", "Alternating"),
            armPatternOptions = listOf("Continuous", "Alternating"),
            gripOptions = listOf("Neutral", "Pronated", "Supinated"),
            loadPositionOptions = listOf("Other", "Front", "Chest"),
            legPatternOptions = listOf("Continuous", "Other"),
            footElevationOptions = listOf("No Elevation", "Bench"),
            combinationTypeOptions = listOf("Single Exercise", "Combination"),
            forceTypeOptions = listOf("Push", "Pull", "Static"),
            mechanicsOptions = listOf("Compound", "Isolation"),
            lateralityOptions = listOf("Bilateral", "Unilateral"),
            classificationOptions = listOf("Bodybuilding", "Powerlifting", "General Fitness"),
            movementPatternOptions = listOf("Horizontal Press", "Vertical Press", "Other"),
            planeOfMotionOptions = listOf("Transverse Plane", "Sagittal Plane", "Frontal Plane"),
        )
    }

    private fun liveSmokeNearbyExercises(): List<ExerciseSummary> {
        return listOf(
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
        )
    }

    private fun assertReasonableLiveSmokeMetadata(metadata: GeneratedCustomExerciseMetadata) {
        assertTrue(metadata.name.isNotBlank())
        assertTrue(metadata.bodyRegion?.isNotBlank() == true)
        assertTrue(metadata.targetMuscleGroup?.isNotBlank() == true)
        assertTrue(metadata.primaryEquipment?.isNotBlank() == true)
        assertTrue(metadata.movementPatterns.isNotEmpty())
        assertTrue(metadata.planesOfMotion.isNotEmpty())
    }

    private fun printLiveSmokeResult(label: String, result: GeneratedCustomExerciseResult) {
        println("$label live smoke metadata: ${result.metadata}")
        println("$label live smoke report:")
        println(result.report.clipboardText)
    }

    private fun assumeLiveAiSmokeTestsEnabled() {
        assumeTrue(
            "Set RUN_LIVE_AI_SMOKE_TESTS=true to run live provider smoke tests.",
            System.getenv("RUN_LIVE_AI_SMOKE_TESTS").equals("true", ignoreCase = true),
        )
    }
}
