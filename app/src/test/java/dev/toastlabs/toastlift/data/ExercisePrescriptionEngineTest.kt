package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExercisePrescriptionEngineTest {
    private val engine = ExercisePrescriptionEngine()

    @Test
    fun prescribe_usesDirectHistoryFirst() {
        val exercise = workoutExercise(exerciseId = 1L, equipment = "Barbell", repRange = "6-8", suggestedWeight = 92.5)
        val detail = detail(exerciseId = 1L, equipment = "Barbell", targetMuscle = "Chest", classification = "Compound")

        val prescription = engine.prescribe(
            ExercisePrescriptionRequest(
                workoutExercise = exercise,
                exerciseDetail = detail,
                profile = profile(goal = "Strength"),
                history = listOf(
                    historySet(
                        exerciseId = 1L,
                        exerciseName = "Barbell Bench Press",
                        targetMuscle = "Chest",
                        weight = 90.0,
                        actualReps = 8,
                    ),
                ),
            ),
        )

        assertEquals(RecommendationSource.DIRECT_HISTORY, prescription.source)
        assertNotNull(prescription.recommendedWeight)
        assertTrue((prescription.recommendedWeight ?: 0.0) >= 90.0)
        assertEquals(6, prescription.recommendedRepCount)
    }

    @Test
    fun prescribe_fallsBackToSimilarHistoryForFirstTimeExercise() {
        val exercise = workoutExercise(exerciseId = 10L, equipment = "Dumbbell", repRange = "8-12")
        val detail = detail(exerciseId = 10L, equipment = "Dumbbell", targetMuscle = "Shoulders", classification = "Compound")

        val prescription = engine.prescribe(
            ExercisePrescriptionRequest(
                workoutExercise = exercise,
                exerciseDetail = detail,
                profile = profile(),
                history = listOf(
                    historySet(
                        exerciseId = 2L,
                        exerciseName = "Dumbbell Shoulder Press",
                        targetMuscle = "Shoulders",
                        weight = 35.0,
                        actualReps = 10,
                        movementPatterns = listOf("Vertical Push"),
                    ),
                ),
            ),
        )

        assertEquals(RecommendationSource.SIMILAR_EXERCISE_HISTORY, prescription.source)
        assertNotNull(prescription.recommendedWeight)
        assertTrue((prescription.recommendedWeight ?: 0.0) < 35.0)
        assertEquals(10, prescription.recommendedRepCount)
    }

    @Test
    fun prescribe_usesColdStartForZeroHistoryLoadedExercise() {
        val exercise = workoutExercise(exerciseId = 20L, equipment = "Machine", repRange = "10-15")
        val detail = detail(exerciseId = 20L, equipment = "Machine", targetMuscle = "Back", classification = "Compound")

        val prescription = engine.prescribe(
            ExercisePrescriptionRequest(
                workoutExercise = exercise,
                exerciseDetail = detail,
                profile = profile(goal = "General Fitness"),
                history = emptyList(),
            ),
        )

        assertEquals(RecommendationSource.COLD_START_HEURISTIC, prescription.source)
        assertNotNull(prescription.recommendedWeight)
        assertEquals(12, prescription.recommendedRepCount)
    }

    @Test
    fun prescribe_leavesWeightBlankForBodyweightMovement() {
        val exercise = workoutExercise(exerciseId = 30L, equipment = "Bodyweight", repRange = "10-15")
        val detail = detail(exerciseId = 30L, equipment = "Bodyweight", targetMuscle = "Abdominals", classification = "Isolation")

        val prescription = engine.prescribe(
            ExercisePrescriptionRequest(
                workoutExercise = exercise,
                exerciseDetail = detail,
                profile = profile(),
                history = emptyList(),
            ),
        )

        assertEquals(RecommendationSource.BODYWEIGHT, prescription.source)
        assertNull(prescription.recommendedWeight)
        assertEquals(12, prescription.recommendedRepCount)
    }

    private fun profile(goal: String = "Hypertrophy"): UserProfile {
        return UserProfile(
            goal = goal,
            experience = "Intermediate",
            durationMinutes = 45,
            weeklyFrequency = 4,
            splitProgramId = 1L,
            units = "imperial",
            activeLocationModeId = 2L,
            workoutStyle = "balanced",
            themePreference = ThemePreference.Dark,
        )
    }

    private fun workoutExercise(
        exerciseId: Long,
        equipment: String,
        repRange: String,
        suggestedWeight: Double? = null,
    ): WorkoutExercise {
        return WorkoutExercise(
            exerciseId = exerciseId,
            name = "Exercise $exerciseId",
            bodyRegion = if (equipment == "Bodyweight") "Core" else "Upper Body",
            targetMuscleGroup = if (equipment == "Bodyweight") "Abdominals" else "Chest",
            equipment = equipment,
            sets = 3,
            repRange = repRange,
            restSeconds = 90,
            rationale = "Test fixture",
            suggestedWeight = suggestedWeight,
            overloadStrategy = if (suggestedWeight != null) "INCREASE_LOAD" else "HOLD_STEADY",
        )
    }

    private fun detail(
        exerciseId: Long,
        equipment: String,
        targetMuscle: String,
        classification: String,
    ): ExerciseDetail {
        return ExerciseDetail(
            summary = ExerciseSummary(
                id = exerciseId,
                name = "Exercise $exerciseId",
                difficulty = "Intermediate",
                bodyRegion = if (targetMuscle == "Abdominals") "Core" else "Upper Body",
                targetMuscleGroup = targetMuscle,
                equipment = equipment,
                secondaryEquipment = null,
                mechanics = classification,
                favorite = false,
            ),
            notes = null,
            primeMover = targetMuscle,
            secondaryMuscle = null,
            tertiaryMuscle = null,
            posture = "Standing",
            laterality = "Bilateral",
            classification = classification,
            movementPatterns = if (targetMuscle == "Shoulders") listOf("Vertical Push") else listOf("Horizontal Push"),
            planesOfMotion = listOf("Sagittal Plane"),
            demoUrl = null,
            explanationUrl = null,
            synonyms = emptyList(),
        )
    }

    private fun historySet(
        exerciseId: Long,
        exerciseName: String,
        targetMuscle: String,
        weight: Double,
        actualReps: Int,
        movementPatterns: List<String> = listOf("Horizontal Push"),
    ): HistoricalExerciseSet {
        return HistoricalExerciseSet(
            completedAtUtc = Instant.parse("2026-03-16T12:00:00Z"),
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            targetReps = "8-12",
            actualReps = actualReps,
            weight = weight,
            completed = true,
            lastSetRir = 2,
            lastSetRpe = null,
            targetMuscleGroup = targetMuscle,
            primeMover = targetMuscle,
            secondaryMuscle = null,
            tertiaryMuscle = null,
            mechanics = "Compound",
            laterality = "Bilateral",
            classification = "Compound",
            movementPatterns = movementPatterns,
            planesOfMotion = listOf("Sagittal Plane"),
        )
    }
}
