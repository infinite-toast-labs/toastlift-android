package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramEngineVarietyTest {

    @Test
    fun buildProgramExerciseSelection_keepsUpperDayArchetypesDistinct() {
        val upperCandidates = (1L..18L).map { id ->
            candidate(
                id = id,
                name = "Upper Exercise $id",
                targetMuscle = when {
                    id % 3L == 0L -> "Back"
                    id % 2L == 0L -> "Shoulders"
                    else -> "Chest"
                },
            )
        }
        val lowerCandidates = (101L..118L).map { id ->
            candidate(
                id = id,
                name = "Lower Exercise $id",
                targetMuscle = if (id % 2L == 0L) "Quadriceps" else "Hamstrings",
                bodyRegion = "Lower Body",
            )
        }

        val selection = buildProgramExerciseSelection(
            programId = "program-under-test",
            goal = "Strength",
            focusSequence = listOf("upper_body", "lower_body", "upper_body_2", "lower_body_2"),
            candidatesByFocus = mapOf(
                "upper_body" to upperCandidates,
                "upper_body_2" to upperCandidates,
                "lower_body" to lowerCandidates,
                "lower_body_2" to lowerCandidates,
            ),
            sfrLookup = { null },
        )

        val upperDay1Ids = selection.slotsByFocus.getValue("upper_body").allExerciseIds()
        val upperDay2Ids = selection.slotsByFocus.getValue("upper_body_2").allExerciseIds()
        val lowerDay1Ids = selection.slotsByFocus.getValue("lower_body").allExerciseIds()
        val lowerDay2Ids = selection.slotsByFocus.getValue("lower_body_2").allExerciseIds()

        assertTrue(upperDay1Ids.intersect(upperDay2Ids).isEmpty())
        assertTrue(lowerDay1Ids.intersect(lowerDay2Ids).isEmpty())
    }

    @Test
    fun assignProgramExercisesToSessions_rotatesTwoAccessorySetsPerArchetype() {
        val sessions = (1..4).map { sequenceNumber ->
            plannedSession(
                sequenceNumber = sequenceNumber,
                focus = SessionFocus.UPPER,
            )
        }
        val focusPool = FocusExercisePool(
            primarySlots = listOf(
                primarySlot(1L),
                primarySlot(2L),
            ),
            accessorySlots = listOf(
                accessorySlot(11L),
                accessorySlot(12L),
                accessorySlot(13L),
                accessorySlot(14L),
            ),
        )

        val assignments = assignProgramExercisesToSessions(
            sessions = sessions,
            focusSequence = listOf("upper_body"),
            slotsByFocus = mapOf("upper_body" to focusPool),
        )

        val variationPairs = assignments.values
            .map { it.takeLast(2).map(PlannedSessionExercise::exerciseId) }
            .distinct()

        assertEquals(2, variationPairs.size)
        assertEquals(listOf(11L, 12L), variationPairs[0])
        assertEquals(listOf(13L, 14L), variationPairs[1])
    }

    @Test
    fun assignProgramExercisesToSessions_tracksOffsetsPerRawArchetype() {
        val sessions = (1..4).map { sequenceNumber ->
            plannedSession(
                sequenceNumber = sequenceNumber,
                focus = SessionFocus.UPPER,
            )
        }
        val upperDay1Pool = FocusExercisePool(
            primarySlots = listOf(primarySlot(1L)),
            accessorySlots = listOf(accessorySlot(11L), accessorySlot(12L), accessorySlot(13L), accessorySlot(14L)),
        )
        val upperDay2Pool = FocusExercisePool(
            primarySlots = listOf(primarySlot(21L)),
            accessorySlots = listOf(accessorySlot(31L), accessorySlot(32L), accessorySlot(33L), accessorySlot(34L)),
        )

        val assignments = assignProgramExercisesToSessions(
            sessions = sessions,
            focusSequence = listOf("upper_body", "upper_body_2"),
            slotsByFocus = mapOf(
                "upper_body" to upperDay1Pool,
                "upper_body_2" to upperDay2Pool,
            ),
        )

        val session1Ids = assignments.getValue(1).map(PlannedSessionExercise::exerciseId)
        val session2Ids = assignments.getValue(2).map(PlannedSessionExercise::exerciseId)
        val session3Ids = assignments.getValue(3).map(PlannedSessionExercise::exerciseId)
        val session4Ids = assignments.getValue(4).map(PlannedSessionExercise::exerciseId)

        assertTrue(1L in session1Ids)
        assertTrue(21L in session2Ids)
        assertTrue(1L in session3Ids)
        assertTrue(21L in session4Ids)

        assertNotEquals(session1Ids.takeLast(2), session3Ids.takeLast(2))
        assertNotEquals(session2Ids.takeLast(2), session4Ids.takeLast(2))
    }

    @Test
    fun buildProgramExerciseSelection_biasesTowardMachineAndCableInGymMode() {
        val candidates = listOf(
            candidate(1L, "Machine Chest Press", "Chest", equipment = "Machine"),
            candidate(2L, "Cable Chest Fly", "Chest", equipment = "Cable"),
            candidate(3L, "Machine Shoulder Press", "Shoulders", equipment = "Machine"),
            candidate(4L, "Cable Lateral Raise", "Shoulders", equipment = "Cable"),
            candidate(5L, "Machine Row", "Back", equipment = "Machine"),
            candidate(6L, "Cable Pulldown", "Back", equipment = "Cable"),
            candidate(7L, "Dumbbell Bench Press", "Chest", equipment = "Dumbbell"),
            candidate(8L, "Dumbbell Shoulder Press", "Shoulders", equipment = "Dumbbell"),
            candidate(9L, "Barbell Row", "Back", equipment = "Barbell"),
        )

        val selection = buildProgramExerciseSelection(
            programId = "program-under-test",
            goal = "Hypertrophy",
            focusSequence = listOf("upper_body"),
            candidatesByFocus = mapOf("upper_body" to candidates),
            sfrLookup = { null },
            profile = UserProfile(
                goal = "Hypertrophy",
                experience = "Intermediate",
                durationMinutes = 45,
                weeklyFrequency = 4,
                splitProgramId = 1L,
                units = "imperial",
                activeLocationModeId = GYM_LOCATION_MODE_ID,
                workoutStyle = "balanced",
                themePreference = ThemePreference.Dark,
            ),
            availableEquipment = setOf("Machine", "Cable", "Dumbbell", "Barbell", "Bodyweight"),
        )

        val preferredCount = selection.slotsByFocus
            .getValue("upper_body")
            .allExerciseIds()
            .count { exerciseId ->
                candidates.first { it.id == exerciseId }.equipment in setOf("Machine", "Cable")
            }

        assertTrue(preferredCount >= 5)
    }

    @Test
    fun focusArchetypeForSequence_wrapsFormulaSequenceForSevenSessionWeeks() {
        val formulaASequence = splitSequenceForName(FORMULA_A_SPLIT_PROGRAM_NAME)

        assertEquals(FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY, focusArchetypeForSequence(1, formulaASequence))
        assertEquals(FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY, focusArchetypeForSequence(6, formulaASequence))
        assertEquals(FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY, focusArchetypeForSequence(7, formulaASequence))
        assertEquals(FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY, focusArchetypeForSequence(8, formulaASequence))
    }

    private fun FocusExercisePool.allExerciseIds(): Set<Long> {
        return (primarySlots + accessorySlots).mapTo(linkedSetOf()) { it.exerciseId }
    }

    private fun plannedSession(
        sequenceNumber: Int,
        focus: SessionFocus,
    ) = PlannedSession(
        id = sequenceNumber.toLong(),
        programId = "program-under-test",
        weekNumber = 1,
        dayIndex = sequenceNumber - 1,
        sequenceNumber = sequenceNumber,
        focusKey = focus,
        plannedSets = 16,
    )

    private fun primarySlot(exerciseId: Long) = ProgramExerciseSlot(
        programId = "program-under-test",
        exerciseId = exerciseId,
        role = ExerciseRole.PRIMARY,
        baselineWeeklySetTarget = 8,
        progressionTrack = ProgressionTrack(startingSets = 3, setsPerWeekIncrement = 1),
        sfrScore = null,
    )

    private fun accessorySlot(exerciseId: Long) = ProgramExerciseSlot(
        programId = "program-under-test",
        exerciseId = exerciseId,
        role = ExerciseRole.ACCESSORY,
        baselineWeeklySetTarget = 6,
        progressionTrack = ProgressionTrack(startingSets = 3),
        sfrScore = null,
    )

    private fun candidate(
        id: Long,
        name: String,
        targetMuscle: String,
        bodyRegion: String = "Upper Body",
        equipment: String = "Dumbbell",
    ) = GeneratorCatalogExercise(
        id = id,
        name = name,
        difficulty = "Intermediate",
        bodyRegion = bodyRegion,
        targetMuscleGroup = targetMuscle,
        equipment = equipment,
        secondaryEquipment = null,
        mechanics = "Compound",
        favorite = false,
        preferenceScoreDelta = 0.0,
        primeMover = targetMuscle,
        secondaryMuscle = null,
        tertiaryMuscle = null,
        posture = "Standing",
        laterality = "Bilateral",
        classification = "Compound",
        movementPatterns = listOf("Horizontal Push"),
        planesOfMotion = listOf("Sagittal Plane"),
    )
}
