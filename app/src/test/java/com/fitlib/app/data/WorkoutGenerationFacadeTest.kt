package com.fitlib.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WorkoutGenerationFacadeTest {
    private val facade = WorkoutGenerationFacade()
    private val now = Instant.parse("2026-03-17T12:00:00Z")

    @Test
    fun resolveAdaptiveFocusPrefersUndertrainedLowerBody() {
        val profile = profile(splitProgramId = 5)
        val history = listOf(
            historySet(
                exerciseId = 11L,
                completedAtUtc = "2026-03-16T12:00:00Z",
                targetMuscle = "Chest",
                movementPatterns = listOf("Horizontal Push"),
                actualReps = 10,
                weight = 70.0,
            ),
            historySet(
                exerciseId = 12L,
                completedAtUtc = "2026-03-15T12:00:00Z",
                targetMuscle = "Back",
                movementPatterns = listOf("Horizontal Pull"),
                actualReps = 10,
                weight = 65.0,
            ),
        )

        val focus = facade.resolveAdaptiveFocus(
            profile = profile,
            history = history,
            nowUtc = now,
        )

        assertTrue(focus != "full_body")
    }

    @Test
    fun generateRespectsHardMovementRestrictions() {
        val profile = profile(goal = "Strength", durationMinutes = 45)
        val restrictedPress = candidate(
            id = 1L,
            name = "Barbell Strict Press",
            targetMuscle = "Shoulders",
            equipment = "Barbell",
            classification = "Compound",
            movementPatterns = listOf("Vertical Push"),
        )
        val allowedBench = candidate(
            id = 2L,
            name = "Barbell Bench Press",
            targetMuscle = "Chest",
            equipment = "Barbell",
            classification = "Compound",
            movementPatterns = listOf("Horizontal Push"),
        )
        val allowedTriceps = candidate(
            id = 3L,
            name = "Cable Tricep Pressdown",
            targetMuscle = "Triceps",
            equipment = "Cable",
            classification = "Isolation",
            movementPatterns = listOf("Elbow Extension"),
        )

        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile,
                splitProgramName = "Push Pull Legs",
                focus = "push_day",
                locationName = "Gym",
                availableEquipment = setOf("Barbell", "Cable", "Bodyweight"),
                candidates = listOf(restrictedPress, allowedBench, allowedTriceps),
                history = emptyList(),
                restrictions = listOf(
                    GeneratorRestriction(
                        scope = "movement_pattern",
                        value = "Vertical Push",
                        severity = "hard",
                        notes = "Shoulder irritation",
                    ),
                ),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 7L,
                nowUtc = now,
            ),
        )

        assertFalse(result.exercises.any { it.exerciseId == restrictedPress.id })
        assertTrue(result.exercises.any { it.exerciseId == allowedBench.id })
    }

    @Test
    fun generateRejectsExerciseWhenSecondaryEquipmentIsUnavailable() {
        val profile = profile(goal = "Strength", durationMinutes = 30)
        val mixedEquipmentExercise = candidate(
            id = 21L,
            name = "Alternating Double Kettlebell Bottoms Up Cyclist Thruster",
            targetMuscle = "Quadriceps",
            equipment = "Kettlebell",
            secondaryEquipment = "Slant Board",
            movementPatterns = listOf("Knee Dominant", "Vertical Push"),
        )
        val allowedExercise = candidate(
            id = 22L,
            name = "Goblet Squat",
            targetMuscle = "Quadriceps",
            equipment = "Kettlebell",
            movementPatterns = listOf("Knee Dominant"),
        )

        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile,
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Kettlebell", "Bodyweight"),
                candidates = listOf(mixedEquipmentExercise, allowedExercise),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 17L,
                nowUtc = now,
            ),
        )

        assertFalse(result.exercises.any { it.exerciseId == mixedEquipmentExercise.id })
        assertTrue(result.exercises.any { it.exerciseId == allowedExercise.id })
    }

    @Test
    fun generateAllowsExerciseWhenAllRequiredEquipmentIsAvailable() {
        val profile = profile(goal = "Strength", durationMinutes = 20)
        val mixedEquipmentExercise = candidate(
            id = 31L,
            name = "Double Kettlebell Cyclist Thruster",
            targetMuscle = "Quadriceps",
            equipment = "Kettlebell",
            secondaryEquipment = "Slant Board",
            movementPatterns = listOf("Knee Dominant", "Vertical Push"),
        )

        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile,
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Kettlebell", "Slant Board", "Bodyweight"),
                candidates = listOf(mixedEquipmentExercise),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 19L,
                nowUtc = now,
            ),
        )

        assertTrue(result.exercises.any { it.exerciseId == mixedEquipmentExercise.id })
    }

    @Test
    fun generateProgressesSuccessfulLoadedLift() {
        val profile = profile(goal = "Strength", durationMinutes = 45, units = "metric")
        val bench = candidate(
            id = 8L,
            name = "Barbell Bench Press",
            targetMuscle = "Chest",
            equipment = "Barbell",
            classification = "Compound",
            movementPatterns = listOf("Horizontal Push"),
        )

        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile,
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Barbell", "Bodyweight"),
                candidates = listOf(bench),
                history = listOf(
                    historySet(
                        exerciseId = bench.id,
                        exerciseName = bench.name,
                        completedAtUtc = "2026-03-15T12:00:00Z",
                        targetMuscle = "Chest",
                        movementPatterns = listOf("Horizontal Push"),
                        actualReps = 8,
                        weight = 80.0,
                        targetReps = "6-8",
                        lastSetRir = 3,
                        equipmentClass = "Compound",
                    ),
                    historySet(
                        exerciseId = bench.id,
                        exerciseName = bench.name,
                        completedAtUtc = "2026-03-15T12:00:00Z",
                        targetMuscle = "Chest",
                        movementPatterns = listOf("Horizontal Push"),
                        actualReps = 8,
                        weight = 80.0,
                        targetReps = "6-8",
                        lastSetRir = 3,
                        equipmentClass = "Compound",
                    ),
                ),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 11L,
                nowUtc = now,
            ),
        )

        val exercise = result.exercises.single()
        assertEquals("INCREASE_LOAD", exercise.overloadStrategy)
        assertNotNull(exercise.suggestedWeight)
        assertTrue(exercise.suggestedWeight!! > 80.0)
    }

    @Test
    fun generateTrimsToShortTimeBudgetAndReturnsInsights() {
        val profile = profile(goal = "Strength", durationMinutes = 20)
        val candidates = listOf(
            candidate(1L, "Barbell Front Squat", "Quadriceps", "Barbell", movementPatterns = listOf("Knee Dominant")),
            candidate(2L, "Romanian Deadlift", "Hamstrings", "Barbell", movementPatterns = listOf("Hip Hinge")),
            candidate(3L, "Alternating Dumbbell Row", "Back", "Dumbbell", movementPatterns = listOf("Horizontal Pull"), laterality = "Unilateral"),
            candidate(4L, "Dumbbell Bench Press", "Chest", "Dumbbell", movementPatterns = listOf("Horizontal Push")),
            candidate(5L, "Cable Pallof Press", "Abdominals", "Cable", classification = "Isolation", movementPatterns = listOf("Anti-Rotational")),
            candidate(6L, "Farmer Carry", "Forearms", "Dumbbell", classification = "Carry", movementPatterns = listOf("Loaded Carry")),
        )

        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile,
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Barbell", "Dumbbell", "Cable", "Bodyweight"),
                candidates = candidates,
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 3L,
                nowUtc = now,
            ),
        )

        assertTrue(result.estimatedMinutes <= 20)
        assertTrue(result.exercises.size <= 4)
        assertTrue(result.muscleInsights.isNotEmpty())
        assertTrue(result.movementInsights.any { it.label == "Unilateral balance" })
    }

    @Test
    fun generateScalesBeyondSevenExercisesForLongProfileDuration() {
        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(goal = "General Fitness", durationMinutes = 300),
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight"),
                candidates = longDurationCandidates(),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 5L,
                nowUtc = now,
            ),
        )

        assertTrue(result.exercises.size > 7)
        assertTrue(result.estimatedMinutes > 56)
    }

    @Test
    fun generateUsesProgramTimeBudgetWhenSelectingExerciseCount() {
        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(goal = "General Fitness", durationMinutes = 30),
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight"),
                candidates = longDurationCandidates(),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 7L,
                nowUtc = now,
                programContext = ProgramSessionContext(
                    forcedExerciseIds = emptyList(),
                    volumeTarget = 24,
                    loadMultiplier = 1.0,
                    elasticityMode = false,
                    timeBudgetMinutes = 120,
                ),
            ),
        )

        assertTrue(result.exercises.size > 4)
        assertTrue(result.estimatedMinutes > 30)
    }

    @Test
    fun generateAppliesHeavyAndHighRepIntentWithoutChangingNeutralSplitDefaults() {
        val profile = profile(goal = "General Fitness", durationMinutes = 30, units = "metric")
        val bench = candidate(
            id = 601L,
            name = "Barbell Bench Press",
            targetMuscle = "Chest",
            equipment = "Barbell",
            classification = "Compound",
            movementPatterns = listOf("Horizontal Push"),
        )
        val history = listOf(
            historySet(
                exerciseId = bench.id,
                exerciseName = bench.name,
                completedAtUtc = "2026-03-15T12:00:00Z",
                targetMuscle = "Chest",
                movementPatterns = listOf("Horizontal Push"),
                actualReps = 8,
                weight = 80.0,
                targetReps = "8-10",
                lastSetRir = 3,
                equipmentClass = "Compound",
            ),
            historySet(
                exerciseId = bench.id,
                exerciseName = bench.name,
                completedAtUtc = "2026-03-12T12:00:00Z",
                targetMuscle = "Chest",
                movementPatterns = listOf("Horizontal Push"),
                actualReps = 8,
                weight = 80.0,
                targetReps = "8-10",
                lastSetRir = 3,
                equipmentClass = "Compound",
            ),
        )

        fun generateForIntent(
            splitProgramName: String,
            rawFocusKey: String,
            intensityIntent: IntensityPrescriptionIntent,
        ) = facade.generate(
            WorkoutGenerationRequest(
                profile = profile,
                splitProgramName = splitProgramName,
                focus = "push_day",
                rawFocusKey = rawFocusKey,
                intensityIntent = intensityIntent,
                locationName = "Gym",
                availableEquipment = setOf("Barbell", "Bodyweight"),
                candidates = listOf(bench),
                history = history,
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 211L,
                nowUtc = now,
            ),
        ).exercises.single()

        val heavy = generateForIntent(
            splitProgramName = FORMULA_A_SPLIT_PROGRAM_NAME,
            rawFocusKey = FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY,
            intensityIntent = IntensityPrescriptionIntent.HEAVY,
        )
        val neutral = generateForIntent(
            splitProgramName = "Push Pull Legs",
            rawFocusKey = "push_day",
            intensityIntent = IntensityPrescriptionIntent.STANDARD,
        )
        val highReps = generateForIntent(
            splitProgramName = FORMULA_A_SPLIT_PROGRAM_NAME,
            rawFocusKey = FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY,
            intensityIntent = IntensityPrescriptionIntent.HIGH_REPS,
        )

        assertTrue(repRangeStart(heavy.repRange) < repRangeStart(neutral.repRange))
        assertTrue(repRangeStart(highReps.repRange) > repRangeStart(neutral.repRange))
        assertTrue(heavy.restSeconds > neutral.restSeconds)
        assertTrue(highReps.restSeconds < neutral.restSeconds)
        assertNotNull(heavy.suggestedWeight)
        assertNotNull(neutral.suggestedWeight)
        assertNotNull(highReps.suggestedWeight)
        assertTrue(heavy.suggestedWeight!! >= neutral.suggestedWeight!!)
        assertTrue(neutral.suggestedWeight!! >= highReps.suggestedWeight!!)
        assertTrue(heavy.suggestedWeight!! > highReps.suggestedWeight!!)
    }

    @Test
    fun generatePrefersMoreOftenBiasOverNeutralPeer() {
        val favored = candidate(
            id = 41L,
            name = "Dumbbell Bench Press",
            targetMuscle = "Chest",
            equipment = "Dumbbell",
            movementPatterns = listOf("Horizontal Push"),
            preferenceScoreDelta = 1.0,
        )
        val neutral = candidate(
            id = 42L,
            name = "Machine Chest Press",
            targetMuscle = "Chest",
            equipment = "Machine",
            movementPatterns = listOf("Horizontal Push"),
        )

        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(durationMinutes = 15),
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Dumbbell", "Machine", "Bodyweight"),
                candidates = listOf(favored, neutral),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 99L,
                nowUtc = now,
            ),
        )

        assertEquals(favored.id, result.exercises.first().exerciseId)
        assertTrue(result.exercises.first().decisionTrace.any { it == "preference_bias=moreoften" || it == "preference_bias=more_often" })
    }

    @Test
    fun generateDeprioritizesLessOftenBiasButKeepsExerciseEligible() {
        val discouraged = candidate(
            id = 51L,
            name = "Dumbbell Bench Press",
            targetMuscle = "Chest",
            equipment = "Dumbbell",
            movementPatterns = listOf("Horizontal Push"),
            preferenceScoreDelta = -1.0,
        )
        val neutral = candidate(
            id = 52L,
            name = "Machine Chest Press",
            targetMuscle = "Chest",
            equipment = "Machine",
            movementPatterns = listOf("Horizontal Push"),
        )

        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(durationMinutes = 25),
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Dumbbell", "Machine", "Bodyweight"),
                candidates = listOf(discouraged, neutral),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 101L,
                nowUtc = now,
            ),
        )

        assertTrue(result.exercises.any { it.exerciseId == discouraged.id })
        assertEquals(neutral.id, result.exercises.first().exerciseId)
    }

    @Test
    fun generateBiasesTowardMachineAndCableInGymMode() {
        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(durationMinutes = 30),
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Machine", "Cable", "Dumbbell", "Barbell", "Bodyweight"),
                candidates = gymBiasCandidatePairs(),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 121L,
                nowUtc = now,
            ),
        )

        val preferredCount = result.exercises.count { isMachineOrCableEquipment(it.equipment) }
        assertTrue(preferredCount >= 3)
        assertTrue(result.decisionSummary.any { it.contains("Gym mode bias favored cable and machine work") })
    }

    @Test
    fun generateDoesNotApplyGymBiasWhenToggleIsOff() {
        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(durationMinutes = 30).copy(gymMachineCableBiasEnabled = false),
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Gym",
                availableEquipment = setOf("Machine", "Cable", "Dumbbell", "Barbell", "Bodyweight"),
                candidates = gymBiasCandidatePairs(),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 121L,
                nowUtc = now,
            ),
        )

        val preferredCount = result.exercises.count { isMachineOrCableEquipment(it.equipment) }
        assertTrue(preferredCount <= 1)
        assertTrue(result.decisionSummary.none { it.contains("Gym mode bias favored cable and machine work") })
    }

    @Test
    fun generateDoesNotApplyGymBiasOutsideGymMode() {
        val result = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(durationMinutes = 30).copy(activeLocationModeId = 1L),
                splitProgramName = "Full Body",
                focus = "full_body",
                locationName = "Home",
                availableEquipment = setOf("Machine", "Cable", "Dumbbell", "Barbell", "Bodyweight"),
                candidates = gymBiasCandidatePairs(),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 121L,
                nowUtc = now,
            ),
        )

        val preferredCount = result.exercises.count { isMachineOrCableEquipment(it.equipment) }
        assertTrue(preferredCount <= 1)
    }

    @Test
    fun generateGymBiasKeepsWorkoutFreshOnImmediateRegeneration() {
        val first = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(durationMinutes = 30),
                splitProgramName = "Upper/Lower",
                focus = "lower_body",
                locationName = "Gym",
                availableEquipment = setOf("Machine", "Cable", "Dumbbell", "Barbell", "Bodyweight"),
                candidates = lowerBodyGymBiasCandidates(),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = emptySet(),
                variationSeed = 301L,
                nowUtc = now,
            ),
        )

        val second = facade.generate(
            WorkoutGenerationRequest(
                profile = profile(durationMinutes = 30),
                splitProgramName = "Upper/Lower",
                focus = "lower_body",
                locationName = "Gym",
                availableEquipment = setOf("Machine", "Cable", "Dumbbell", "Barbell", "Bodyweight"),
                candidates = lowerBodyGymBiasCandidates(),
                history = emptyList(),
                restrictions = emptyList(),
                preferences = emptyMap(),
                previousExerciseIds = first.exercises.map { it.exerciseId }.toSet(),
                variationSeed = 302L,
                nowUtc = now,
            ),
        )

        assertTrue(first.exercises.map { it.exerciseId } != second.exercises.map { it.exerciseId })
        assertTrue(second.exercises.count { isMachineOrCableEquipment(it.equipment) } >= 3)
    }

    private fun profile(
        goal: String = "General Fitness",
        durationMinutes: Int = 45,
        units: String = "imperial",
        splitProgramId: Long = 1L,
    ): UserProfile {
        return UserProfile(
            goal = goal,
            experience = "Intermediate",
            durationMinutes = durationMinutes,
            weeklyFrequency = 4,
            splitProgramId = splitProgramId,
            units = units,
            activeLocationModeId = 2L,
            workoutStyle = "balanced",
            themePreference = ThemePreference.Dark,
        )
    }

    private fun candidate(
        id: Long,
        name: String,
        targetMuscle: String,
        equipment: String,
        secondaryEquipment: String? = null,
        classification: String = "Compound",
        movementPatterns: List<String> = emptyList(),
        laterality: String = "Bilateral",
        favorite: Boolean = false,
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
            secondaryEquipment = secondaryEquipment,
            mechanics = classification,
            favorite = favorite,
            preferenceScoreDelta = preferenceScoreDelta,
            primeMover = targetMuscle,
            secondaryMuscle = null,
            tertiaryMuscle = null,
            posture = "Standing",
            laterality = laterality,
            classification = classification,
            movementPatterns = movementPatterns,
            planesOfMotion = listOf("Sagittal Plane"),
        )
    }

    private fun historySet(
        exerciseId: Long,
        completedAtUtc: String,
        targetMuscle: String,
        movementPatterns: List<String>,
        actualReps: Int,
        weight: Double,
        exerciseName: String = "Logged Exercise",
        targetReps: String = "8-12",
        lastSetRir: Int = 2,
        equipmentClass: String = "Compound",
    ): HistoricalExerciseSet {
        return HistoricalExerciseSet(
            completedAtUtc = Instant.parse(completedAtUtc),
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            targetReps = targetReps,
            actualReps = actualReps,
            weight = weight,
            completed = true,
            lastSetRir = lastSetRir,
            lastSetRpe = null,
            targetMuscleGroup = targetMuscle,
            primeMover = targetMuscle,
            secondaryMuscle = null,
            tertiaryMuscle = null,
            mechanics = equipmentClass,
            laterality = "Bilateral",
            classification = equipmentClass,
            movementPatterns = movementPatterns,
            planesOfMotion = listOf("Sagittal Plane"),
        )
    }

    private fun gymBiasCandidatePairs(): List<GeneratorCatalogExercise> {
        return listOf(
            candidate(
                id = 201L,
                name = "Machine Chest Press",
                targetMuscle = "Chest",
                equipment = "Machine",
                movementPatterns = listOf("Horizontal Push"),
            ),
            candidate(
                id = 202L,
                name = "Dumbbell Bench Press",
                targetMuscle = "Chest",
                equipment = "Dumbbell",
                favorite = true,
                movementPatterns = listOf("Horizontal Push"),
            ),
            candidate(
                id = 203L,
                name = "Cable Row",
                targetMuscle = "Back",
                equipment = "Cable",
                movementPatterns = listOf("Horizontal Pull"),
            ),
            candidate(
                id = 204L,
                name = "Dumbbell Row",
                targetMuscle = "Back",
                equipment = "Dumbbell",
                favorite = true,
                movementPatterns = listOf("Horizontal Pull"),
            ),
            candidate(
                id = 205L,
                name = "Machine Leg Press",
                targetMuscle = "Quadriceps",
                equipment = "Machine",
                movementPatterns = listOf("Knee Dominant"),
            ),
            candidate(
                id = 206L,
                name = "Barbell Back Squat",
                targetMuscle = "Quadriceps",
                equipment = "Barbell",
                favorite = true,
                movementPatterns = listOf("Knee Dominant"),
            ),
            candidate(
                id = 207L,
                name = "Machine Leg Curl",
                targetMuscle = "Hamstrings",
                equipment = "Machine",
                classification = "Isolation",
                movementPatterns = listOf("Knee Flexion"),
            ),
            candidate(
                id = 208L,
                name = "Dumbbell Romanian Deadlift",
                targetMuscle = "Hamstrings",
                equipment = "Dumbbell",
                favorite = true,
                movementPatterns = listOf("Hip Hinge"),
            ),
        )
    }

    private fun lowerBodyGymBiasCandidates(): List<GeneratorCatalogExercise> {
        return listOf(
            candidate(301L, "Machine Leg Press", "Quadriceps", equipment = "Machine", movementPatterns = listOf("Knee Dominant")),
            candidate(302L, "Machine Hack Squat", "Quadriceps", equipment = "Machine", movementPatterns = listOf("Knee Dominant")),
            candidate(303L, "Cable Belt Squat", "Quadriceps", equipment = "Cable", movementPatterns = listOf("Knee Dominant")),
            candidate(304L, "Machine Leg Curl", "Hamstrings", equipment = "Machine", classification = "Isolation", movementPatterns = listOf("Knee Flexion")),
            candidate(305L, "Cable Romanian Deadlift", "Hamstrings", equipment = "Cable", movementPatterns = listOf("Hip Hinge")),
            candidate(306L, "Machine Hip Thrust", "Glutes", equipment = "Machine", movementPatterns = listOf("Hip Hinge")),
            candidate(307L, "Cable Pull Through", "Glutes", equipment = "Cable", movementPatterns = listOf("Hip Hinge")),
            candidate(308L, "Machine Calf Raise", "Calves", equipment = "Machine", classification = "Isolation", movementPatterns = listOf("Plantar Flexion")),
            candidate(309L, "Barbell Back Squat", "Quadriceps", equipment = "Barbell", favorite = true, movementPatterns = listOf("Knee Dominant")),
            candidate(310L, "Dumbbell Romanian Deadlift", "Hamstrings", equipment = "Dumbbell", favorite = true, movementPatterns = listOf("Hip Hinge")),
            candidate(311L, "Barbell Hip Thrust", "Glutes", equipment = "Barbell", favorite = true, movementPatterns = listOf("Hip Hinge")),
            candidate(312L, "Dumbbell Standing Calf Raise", "Calves", equipment = "Dumbbell", favorite = true, classification = "Isolation", movementPatterns = listOf("Plantar Flexion")),
        )
    }

    private fun longDurationCandidates(): List<GeneratorCatalogExercise> {
        val targets = listOf(
            "Chest" to listOf("Horizontal Push"),
            "Back" to listOf("Horizontal Pull"),
            "Shoulders" to listOf("Vertical Push"),
            "Quadriceps" to listOf("Knee Dominant"),
            "Hamstrings" to listOf("Hip Hinge"),
            "Glutes" to listOf("Hip Hinge"),
            "Abdominals" to listOf("Anti-Extension"),
            "Biceps" to listOf("Elbow Flexion"),
            "Triceps" to listOf("Elbow Extension"),
            "Calves" to listOf("Plantar Flexion"),
            "Forearms" to listOf("Loaded Carry"),
            "Trapezius" to listOf("Vertical Pull"),
        )
        val equipments = listOf("Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight")

        return (1L..30L).map { id ->
            val target = targets[((id - 1) % targets.size).toInt()]
            val equipment = equipments[((id - 1) % equipments.size).toInt()]
            candidate(
                id = 900L + id,
                name = "${equipment} ${target.first} Exercise $id",
                targetMuscle = target.first,
                equipment = equipment,
                classification = if (id % 4L == 0L) "Isolation" else "Compound",
                movementPatterns = target.second,
            )
        }
    }

    private fun repRangeStart(repRange: String): Int = repRange.substringBefore('-').trim().toInt()
}
