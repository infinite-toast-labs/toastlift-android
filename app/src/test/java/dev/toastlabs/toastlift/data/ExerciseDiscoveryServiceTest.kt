package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDiscoveryServiceTest {
    @Test
    fun buildExerciseDiscoveryPrompt_exposesRealSamplePromptPayload() {
        val context = discoveryContext(
            performedExercises = listOf(
                discoveryExercise(
                    id = 10L,
                    name = "Barbell Bench Press",
                    loggedSessionCount = 18,
                    targetMuscleGroup = "Chest",
                    equipment = "Barbell",
                    primeMover = "Pectoralis Major",
                    movementPatterns = listOf("Horizontal Push"),
                ),
            ),
            zeroSessionCandidates = listOf(
                discoveryExercise(
                    id = 42L,
                    name = "Cable Hip Abduction",
                    loggedSessionCount = 0,
                    targetMuscleGroup = "Abductors",
                    equipment = "Cable",
                    primeMover = "Gluteus Medius",
                    movementPatterns = listOf("Hip Abduction"),
                ),
            ),
        )

        val prompt = buildExerciseDiscoveryPrompt(context)

        assertTrue(prompt.contains("top 5 exercises"))
        assertTrue(prompt.contains("\"validCandidateIds\""))
        assertTrue(prompt.contains("42"))
        assertTrue(prompt.contains("\"performedExercises\""))
        assertTrue(prompt.contains("Barbell Bench Press"))
        assertTrue(prompt.contains("\"zeroSessionCandidates\""))
        assertTrue(prompt.contains("Cable Hip Abduction"))
        assertTrue(prompt.contains("Home equipment"))
        assertTrue(prompt.contains("Target: Abductors"))
    }

    @Test
    fun parseExerciseDiscoveryGeminiResponse_rejectsHallucinatedIdsAndDedupes() {
        val context = discoveryContext(
            zeroSessionCandidates = listOf(
                discoveryExercise(id = 1L, name = "Cable Hip Abduction"),
                discoveryExercise(id = 2L, name = "Side Lying Hip Raise"),
            ),
        )

        val picks = parseExerciseDiscoveryGeminiResponse(
            rawText = """
                {
                  "rankedPicks": [
                    {"rank": 1, "exerciseId": 999, "reason": "Invalid.", "whyNow": "Invalid.", "confidence": 0.8},
                    {"rank": 2, "exerciseId": 1, "reason": "Fits the abductor filter.", "whyNow": "It is new to the user.", "discoveryAngle": "new_to_you", "confidence": 0.91, "evidence": ["0 logged sessions"]},
                    {"rank": 3, "exerciseId": 1, "reason": "Duplicate.", "whyNow": "Duplicate.", "confidence": 0.7},
                    {"rank": 4, "exerciseId": 2, "reason": "Adds a different setup.", "whyNow": "Keeps the same muscle target.", "discoveryAngle": "movement_pattern_gap", "confidence": 1.5}
                  ]
                }
            """.trimIndent(),
            context = context,
        )

        assertEquals(listOf(1L, 2L), picks.map { it.exercise.id })
        assertEquals(listOf(1, 2), picks.map { it.rank })
        assertEquals("Fits the abductor filter.", picks.first().reason)
        assertEquals(1.0, picks.last().confidence, 0.0)
        assertEquals("movement_pattern_gap", picks.last().discoveryAngle)
    }

    @Test
    fun exerciseDiscoveryService_fallsBackToDeterministicTopFiveWhenRemoteFails() {
        val context = discoveryContext(
            performedExercises = listOf(
                discoveryExercise(id = 100L, name = "Back Squat", loggedSessionCount = 24, targetMuscleGroup = "Quadriceps", equipment = "Barbell"),
                discoveryExercise(id = 101L, name = "Leg Press", loggedSessionCount = 14, targetMuscleGroup = "Quadriceps", equipment = "Machine"),
            ),
            zeroSessionCandidates = (1L..6L).map { id ->
                discoveryExercise(
                    id = id,
                    name = "Discovery Exercise $id",
                    targetMuscleGroup = if (id % 2L == 0L) "Abductors" else "Glutes",
                    equipment = if (id % 2L == 0L) "Cable" else "Dumbbell",
                    primeMover = if (id % 2L == 0L) "Gluteus Medius" else "Gluteus Maximus",
                )
            },
        )
        val service = ExerciseDiscoveryService(
            remoteGenerator = FakeExerciseDiscoveryRemoteGenerator(error = IllegalStateException("offline")),
        )

        val result = service.generate(context)

        assertEquals(ExerciseDiscoverySource.DETERMINISTIC_FALLBACK, result.source)
        assertEquals(5, result.picks.size)
        assertEquals(listOf(1, 2, 3, 4, 5), result.picks.map { it.rank })
        assertTrue(result.picks.all { it.exercise.loggedSessionCount == 0 })
    }

    @Test
    fun exerciseDiscoveryService_backfillsValidGeminiPicksToFive() {
        val context = discoveryContext(
            zeroSessionCandidates = (1L..6L).map { id ->
                discoveryExercise(id = id, name = "Discovery Exercise $id")
            },
        )
        val service = ExerciseDiscoveryService(
            remoteGenerator = FakeExerciseDiscoveryRemoteGenerator(
                response = """
                    {
                      "rankedPicks": [
                        {"rank": 1, "exerciseId": 6, "reason": "Best novelty fit.", "whyNow": "It is unseen and filter compatible.", "confidence": 0.86},
                        {"rank": 2, "exerciseId": 999, "reason": "Hallucinated.", "whyNow": "No.", "confidence": 0.75},
                        {"rank": 3, "exerciseId": 5, "reason": "Second useful option.", "whyNow": "It adds variety.", "confidence": 0.81}
                      ]
                    }
                """.trimIndent(),
            ),
        )

        val result = service.generate(context)

        assertEquals(ExerciseDiscoverySource.GEMINI, result.source)
        assertEquals(5, result.picks.size)
        assertEquals(listOf(6L, 5L), result.picks.take(2).map { it.exercise.id })
        assertTrue(result.picks.none { it.exercise.id == 999L })
        assertEquals("gemini-test", result.model)
    }

    private class FakeExerciseDiscoveryRemoteGenerator(
        private val response: String? = null,
        private val error: RuntimeException? = null,
    ) : ExerciseDiscoveryRemoteGenerator {
        override val model: String = "gemini-test"

        override fun generate(prompt: String): String {
            error?.let { throw it }
            return response ?: error("Missing fake response.")
        }
    }

    private fun discoveryContext(
        query: String = "abductors",
        appliedFilterLabels: List<String> = listOf("Home equipment", "Target: Abductors"),
        performedExercises: List<ExerciseDiscoveryExercise> = emptyList(),
        zeroSessionCandidates: List<ExerciseDiscoveryExercise> = emptyList(),
        lowExposureCandidates: List<ExerciseDiscoveryExercise> = emptyList(),
    ): ExerciseDiscoveryContext {
        return ExerciseDiscoveryContext(
            query = query,
            filters = LibraryFilters(targetMuscles = setOf("Abductors"), equipmentLocation = LibraryEquipmentLocation.Home),
            appliedFilterLabels = appliedFilterLabels,
            performedExercises = performedExercises,
            zeroSessionCandidates = zeroSessionCandidates,
            lowExposureCandidates = lowExposureCandidates,
            totalPerformedExercises = performedExercises.size,
            totalMatchingExercises = zeroSessionCandidates.size + lowExposureCandidates.size,
            totalZeroSessionCandidates = zeroSessionCandidates.size,
        )
    }

    private fun discoveryExercise(
        id: Long,
        name: String,
        loggedSessionCount: Int = 0,
        targetMuscleGroup: String = "Abductors",
        equipment: String = "Cable",
        primeMover: String? = "Gluteus Medius",
        movementPatterns: List<String> = listOf("Hip Abduction"),
    ): ExerciseDiscoveryExercise {
        return ExerciseDiscoveryExercise(
            summary = ExerciseSummary(
                id = id,
                name = name,
                difficulty = "Intermediate",
                bodyRegion = "Lower Body",
                targetMuscleGroup = targetMuscleGroup,
                equipment = equipment,
                secondaryEquipment = null,
                mechanics = "Isolation",
                favorite = false,
                preferenceScoreDelta = 0.0,
                recommendationBias = RecommendationBias.Neutral,
                loggedSessionCount = loggedSessionCount,
            ),
            primeMover = primeMover,
            secondaryMuscle = null,
            tertiaryMuscle = null,
            posture = "Standing",
            laterality = "Bilateral",
            classification = "Accessory",
            movementPatterns = movementPatterns,
            planesOfMotion = listOf("Frontal"),
        )
    }
}
