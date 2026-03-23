package com.fitlib.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GeneratorRepositoryTest {
    private val facade = WorkoutGenerationFacade()
    private val now = Instant.parse("2026-03-17T12:00:00Z")

    @Test
    fun persistedGeneratedWorkoutFocusKey_keepsExplicitDayTwoSelection() {
        val persistedFocus = persistedGeneratedWorkoutFocusKey(
            requestedFocus = "lower_body_2",
            resolvedFocus = "lower_body_2",
            normalizedFocus = "lower_body",
        )

        assertEquals("lower_body_2", persistedFocus)
    }

    @Test
    fun persistedGeneratedWorkoutFocusKey_preservesResolvedSplitFocusWhenNoSelectionWasRequested() {
        val persistedFocus = persistedGeneratedWorkoutFocusKey(
            requestedFocus = null,
            resolvedFocus = FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY,
            normalizedFocus = "lower_body",
        )

        assertEquals(FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY, persistedFocus)
    }

    @Test
    fun generatedWorkoutFocusDisplayName_distinguishesSecondUpperAndLowerDays() {
        assertEquals("Upper Day 2", generatedWorkoutFocusDisplayName("upper_body_2"))
        assertEquals("Lower Day 2", generatedWorkoutFocusDisplayName("lower_body_2"))
    }

    @Test
    fun splitSequenceForName_returnsFormulaAWithoutRestDays() {
        assertEquals(
            listOf(
                FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY,
                FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY,
                FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY,
                FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY,
                FORMULA_A_LOWER_STRENGTH_FOCUS_KEY,
                FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY,
            ),
            splitSequenceForName(FORMULA_A_SPLIT_PROGRAM_NAME),
        )
    }

    @Test
    fun generatedWorkoutFocusDisplayName_formatsFormulaADays() {
        assertEquals(
            "UPPER-PUSH-S (chest/delts/tri HEAVY)",
            generatedWorkoutFocusDisplayName(FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY),
        )
        assertEquals(
            "LOWER-H (legs/abs HIGH REPS)",
            generatedWorkoutFocusDisplayName(FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY),
        )
        assertEquals(
            "UPPER-PULL-H (back/biceps HIGH REPS)",
            generatedWorkoutFocusDisplayName(FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY),
        )
    }

    @Test
    fun splitSequenceForName_returnsFormulaBWithoutRestDays() {
        assertEquals(
            listOf(
                FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY,
                FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY,
                FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY,
                FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY,
                FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY,
                FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY,
            ),
            splitSequenceForName(FORMULA_B_SPLIT_PROGRAM_NAME),
        )
    }

    @Test
    fun generatedWorkoutFocusDisplayName_formatsFormulaBDays() {
        assertEquals(
            "GLUTES+HAMSTRINGS-S (HEAVY)",
            generatedWorkoutFocusDisplayName(FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY),
        )
        assertEquals(
            "UPPER CHEST-H (HIGH REPS)",
            generatedWorkoutFocusDisplayName(FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY),
        )
        assertEquals(
            "REAR DELTS + SIDE DELTS-H (HIGH REPS)",
            generatedWorkoutFocusDisplayName(FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY),
        )
    }

    @Test
    fun intensityPrescriptionIntentForFocusKey_mapsFormulaVariantsAndLeavesClassicSplitsNeutral() {
        assertEquals(
            IntensityPrescriptionIntent.HEAVY,
            intensityPrescriptionIntentForFocusKey(FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY),
        )
        assertEquals(
            IntensityPrescriptionIntent.HIGH_REPS,
            intensityPrescriptionIntentForFocusKey(FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY),
        )
        assertEquals(
            IntensityPrescriptionIntent.STANDARD,
            intensityPrescriptionIntentForFocusKey("push_day"),
        )
        assertEquals(
            IntensityPrescriptionIntent.STANDARD,
            intensityPrescriptionIntentForFocusKey(null),
        )
    }

    @Test
    fun resolveAvailableEquipmentForGeneration_respectsDisabledBodyweight() {
        val availableEquipment = resolveAvailableEquipmentForGeneration(
            setOf("Dumbbell", " Sliders ", ""),
        )

        assertFalse(availableEquipment.contains("Bodyweight"))

        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(),
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Home",
                availableEquipment = availableEquipment,
                candidates = listOf(
                    candidate(
                        id = 101L,
                        name = "Bodyweight Wall Assisted Hip Airplane",
                        targetMuscle = "Glutes",
                        equipment = "Bodyweight",
                        movementPatterns = listOf("Hip Hinge"),
                    ),
                    candidate(
                        id = 102L,
                        name = "Dumbbell Romanian Deadlift",
                        targetMuscle = "Hamstrings",
                        equipment = "Dumbbell",
                        movementPatterns = listOf("Hip Hinge"),
                    ),
                ),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 23L,
                nowUtc = now,
            ),
        )

        assertFalse(result.exercises.any { it.exerciseId == 101L })
        assertTrue(result.exercises.any { it.exerciseId == 102L })
    }

    @Test
    fun generate_prefersExerciseWithStrongerLearnedPositiveBias() {
        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(durationMinutes = 20),
                splitProgramName = "Full Body",
                focus = "upper_body",
                locationName = "Gym",
                availableEquipment = setOf("Cable"),
                candidates = listOf(
                    candidate(
                        id = 201L,
                        name = "Cable Chest Press",
                        targetMuscle = "Chest",
                        equipment = "Cable",
                        movementPatterns = listOf("Horizontal Push"),
                        preferenceScoreDelta = 2.0,
                    ),
                    candidate(
                        id = 202L,
                        name = "Cable Chest Fly",
                        targetMuscle = "Chest",
                        equipment = "Cable",
                        movementPatterns = listOf("Horizontal Push"),
                        preferenceScoreDelta = 0.0,
                    ),
                    candidate(
                        id = 203L,
                        name = "Cable Row",
                        targetMuscle = "Back",
                        equipment = "Cable",
                        movementPatterns = listOf("Horizontal Pull"),
                        preferenceScoreDelta = 0.0,
                    ),
                ),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 77L,
                nowUtc = now,
            ),
        )

        assertTrue(result.exercises.isNotEmpty())
        assertEquals(201L, result.exercises.first().exerciseId)
    }

    @Test
    fun limitCandidatesForSelectionWindow_rebalancesRealisticLowerBodyCandidateSlice() {
        val orderedCandidates = buildList {
            add(
                candidate(
                    id = 1L,
                    name = "Ab Crunch Machine",
                    targetMuscle = "Abdominals",
                    equipment = "Machine",
                    movementPatterns = listOf("Anti-Extension"),
                ),
            )
            repeat(194) { index ->
                add(
                    candidate(
                        id = 10L + index,
                        name = "Barbell Lower Exercise ${index + 1}",
                        targetMuscle = when (index % 3) {
                            0 -> "Quadriceps"
                            1 -> "Hamstrings"
                            else -> "Glutes"
                        },
                        equipment = "Barbell",
                        movementPatterns = listOf(if (index % 2 == 0) "Knee Dominant" else "Hip Hinge"),
                    ),
                )
            }
            repeat(45) { index ->
                add(
                    candidate(
                        id = 500L + index,
                        name = "Bodyweight Lower Exercise ${index + 1}",
                        targetMuscle = if (index % 4 == 0) "Abdominals" else "Calves",
                        equipment = "Bodyweight",
                        movementPatterns = listOf(if (index % 4 == 0) "Anti-Extension" else "Plantar Flexion"),
                    ),
                )
            }
            repeat(20) { index ->
                add(
                    candidate(
                        id = 700L + index,
                        name = "Machine Lower Bias ${index + 1}",
                        targetMuscle = when (index % 4) {
                            0 -> "Quadriceps"
                            1 -> "Hamstrings"
                            2 -> "Glutes"
                            else -> "Calves"
                        },
                        equipment = "Machine",
                        movementPatterns = listOf(
                            when (index % 4) {
                                0 -> "Knee Dominant"
                                1, 2 -> "Hip Hinge"
                                else -> "Plantar Flexion"
                            },
                        ),
                    ),
                )
            }
            repeat(20) { index ->
                add(
                    candidate(
                        id = 900L + index,
                        name = "Cable Lower Bias ${index + 1}",
                        targetMuscle = when (index % 4) {
                            0 -> "Quadriceps"
                            1 -> "Hamstrings"
                            2 -> "Glutes"
                            else -> "Abdominals"
                        },
                        equipment = "Cable",
                        movementPatterns = listOf(
                            when (index % 4) {
                                0 -> "Knee Dominant"
                                1, 2 -> "Hip Hinge"
                                else -> "Anti-Rotational"
                            },
                        ),
                    ),
                )
            }
        }

        val biasPlan = buildGymEquipmentBiasPlan(
            profile = profile(durationMinutes = 75).copy(
                activeLocationModeId = GYM_LOCATION_MODE_ID,
                gymMachineCableBiasEnabled = true,
            ),
            availableEquipment = setOf("Machine", "Cable", "Barbell", "Bodyweight"),
            desiredCount = 7,
        )!!

        val unbiasedWindow = limitCandidatesForSelectionWindow(
            candidates = orderedCandidates,
            biasPlan = null,
            maxCount = 240,
        )
        val biasedWindow = limitCandidatesForSelectionWindow(
            candidates = orderedCandidates,
            biasPlan = biasPlan,
            maxCount = 240,
        )

        assertEquals(1, unbiasedWindow.count(::isMachineOrCableExercise))
        assertTrue(biasedWindow.take(7).count(::isMachineOrCableExercise) >= 5)
        assertTrue(biasedWindow.count(::isMachineOrCableExercise) >= 41)
    }

    private fun profile(durationMinutes: Int = 30): UserProfile {
        return UserProfile(
            goal = "General Fitness",
            experience = "Intermediate",
            durationMinutes = durationMinutes,
            weeklyFrequency = 4,
            splitProgramId = 1L,
            units = "imperial",
            activeLocationModeId = 1L,
            workoutStyle = "balanced",
            themePreference = ThemePreference.Dark,
        )
    }

    private fun candidate(
        id: Long,
        name: String,
        targetMuscle: String,
        equipment: String,
        movementPatterns: List<String>,
        preferenceScoreDelta: Double = 0.0,
    ): GeneratorCatalogExercise {
        return GeneratorCatalogExercise(
            id = id,
            name = name,
            difficulty = "Intermediate",
            bodyRegion = when (targetMuscle) {
                "Quadriceps", "Hamstrings", "Glutes", "Calves" -> "Lower Body"
                "Abdominals" -> "Core"
                else -> "Upper Body"
            },
            targetMuscleGroup = targetMuscle,
            equipment = equipment,
            secondaryEquipment = null,
            mechanics = "Compound",
            favorite = false,
            preferenceScoreDelta = preferenceScoreDelta,
            primeMover = targetMuscle,
            secondaryMuscle = null,
            tertiaryMuscle = null,
            posture = "Standing",
            laterality = "Bilateral",
            classification = "Compound",
            movementPatterns = movementPatterns,
            planesOfMotion = listOf("Sagittal Plane"),
        )
    }
}
