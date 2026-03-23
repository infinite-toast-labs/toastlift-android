package com.fitlib.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReviewEngineAdditionalTest {

    private val baseCriteria = SuccessCriteria(
        targetLifts = mapOf(
            1L to TargetOutcome(metric = "5RM", targetValue = 100.0),
            2L to TargetOutcome(metric = "5RM", targetValue = 60.0),
        ),
        targetSessionCompletionRate = 0.8,
    )

    private val basePolicy = AdaptationPolicy(
        allowExerciseRepinning = false,
        maxWeeklySetDeltaPercent = 0.2,
        confidenceFloorForAutonomousChanges = 0.4,
        triggerReviewAfterMissedSessions = 2,
    )

    private fun program(
        confidenceScore: Double = 1.0,
        successCriteria: SuccessCriteria = baseCriteria,
        adaptationPolicy: AdaptationPolicy = basePolicy,
    ) = TrainingProgram(
        id = "test-program",
        title = "Test Block",
        goal = "Strength",
        primaryOutcomeMetric = OutcomeMetric.STRENGTH,
        programArchetype = ProgramArchetype.LINEAR_RAMP,
        periodizationModel = PeriodizationModel.LINEAR,
        splitProgramId = 1L,
        totalWeeks = 4,
        sessionsPerWeek = 4,
        successCriteria = successCriteria,
        adaptationPolicy = adaptationPolicy,
        confidenceScore = confidenceScore,
        createdAt = System.currentTimeMillis(),
    )

    private fun checkpoint(weekNumber: Int = 2) = ProgramCheckpoint(
        id = 1L,
        programId = "test-program",
        weekNumber = weekNumber,
        checkpointType = CheckpointType.PROGRESS_REVIEW,
    )

    private fun session(status: SessionStatus = SessionStatus.COMPLETED) = PlannedSession(
        id = 1L,
        programId = "test-program",
        weekNumber = 1,
        dayIndex = 0,
        sequenceNumber = 1,
        focusKey = SessionFocus.FULL_BODY,
        plannedSets = 16,
        status = status,
    )

    private fun historySet(
        exerciseId: Long,
        weight: Double,
        rpe: Double? = null,
        completedAtUtc: String = "2026-03-15T12:00:00Z",
        exerciseName: String = "Test Exercise",
    ) = HistoricalExerciseSet(
        completedAtUtc = Instant.parse(completedAtUtc),
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        targetReps = "5",
        actualReps = 5,
        weight = weight,
        completed = true,
        lastSetRir = 2,
        lastSetRpe = rpe,
        targetMuscleGroup = "Chest",
        primeMover = "Chest",
        secondaryMuscle = null,
        tertiaryMuscle = null,
        mechanics = "Compound",
        laterality = "Bilateral",
        classification = "Compound",
        movementPatterns = listOf("Horizontal Push"),
        planesOfMotion = listOf("Sagittal Plane"),
    )

    private fun fakeRepo(
        completedCount: Int = 0,
        skippedCount: Int = 0,
        previousCheckpoints: List<ProgramCheckpoint> = emptyList(),
        slots: List<ProgramExerciseSlot> = emptyList(),
    ): ProgramRepository {
        return object : ProgramRepository(null) {
            override fun loadSessionsForProgram(programId: String) = emptyList<PlannedSession>()
            override fun countSessionsByStatus(programId: String, status: SessionStatus) = when (status) {
                SessionStatus.COMPLETED -> completedCount
                SessionStatus.SKIPPED -> skippedCount
                SessionStatus.UPCOMING -> 0
                SessionStatus.MIGRATED -> 0
                SessionStatus.IN_PROGRESS -> 0
            }
            override fun loadAllCheckpoints(programId: String) = previousCheckpoints
            override fun loadSlotsForProgram(programId: String) = slots
            override fun logEvent(event: ProgramEvent) {}
            override fun completeCheckpoint(checkpointId: Long, summary: String) {}
            override fun updateConfidenceScore(programId: String, score: Double) {}
        }
    }

    // ── 1. On-track progress returns CONTINUE ──

    @Test
    fun onTrackProgressReturnsContinue() {
        val repo = fakeRepo(completedCount = 6, skippedCount = 1)
        val engine = ReviewEngine(repo)
        // Weights between 95% and 100% of target -> ON_TRACK (not AHEAD, not FLAT)
        val history = listOf(
            historySet(exerciseId = 1L, weight = 96.0),
            historySet(exerciseId = 2L, weight = 58.0),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(),
            completedSessions = (1..6).map { session() },
            performedWorkoutIds = (1L..6L).toList(),
            history = history,
        )
        assertEquals(CheckpointAction.CONTINUE, result.action)
    }

    // ── 2. High adherence + flat progress across 2+ checkpoints -> PIVOT_EXERCISES ──

    @Test
    fun highAdherenceFlatProgressAcrossMultipleCheckpointsTriggersPivot() {
        val repo = fakeRepo(
            completedCount = 8,
            skippedCount = 0,
            previousCheckpoints = listOf(
                ProgramCheckpoint(
                    id = 10L, programId = "test-program", weekNumber = 2,
                    checkpointType = CheckpointType.PROGRESS_REVIEW,
                    status = CheckpointStatus.COMPLETED,
                ),
                ProgramCheckpoint(
                    id = 11L, programId = "test-program", weekNumber = 4,
                    checkpointType = CheckpointType.PROGRESS_REVIEW,
                    status = CheckpointStatus.COMPLETED,
                ),
            ),
        )
        val engine = ReviewEngine(repo)
        // Both lifts well below 95% of target -> FLAT
        val history = listOf(
            historySet(exerciseId = 1L, weight = 80.0),
            historySet(exerciseId = 2L, weight = 45.0),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(weekNumber = 6),
            completedSessions = (1..8).map { session() },
            performedWorkoutIds = (1L..8L).toList(),
            history = history,
        )
        assertEquals(CheckpointAction.PIVOT_EXERCISES, result.action)
    }

    // ── 3. Confidence doesn't go below 0.0 ──

    @Test
    fun confidenceDoesNotGoBelowZero() {
        // Start at 0.1 confidence, 5 skipped sessions -> 0.1 - 0.5 = -0.4 -> clamped to 0.0
        val repo = fakeRepo(completedCount = 3, skippedCount = 5)
        val engine = ReviewEngine(repo)
        val result = engine.runCheckpoint(
            program = program(confidenceScore = 0.1),
            checkpoint = checkpoint(),
            completedSessions = (1..3).map { session() },
            performedWorkoutIds = (1L..3L).toList(),
            history = emptyList(),
        )
        assertEquals(0.0, result.newConfidenceScore, 0.001)
    }

    // ── 4. Confidence doesn't go above 1.0 ──

    @Test
    fun confidenceDoesNotGoAboveOne() {
        // Start at 1.0, no skips, no fatigue, no flat progress -> stays 1.0
        val repo = fakeRepo(completedCount = 8, skippedCount = 0)
        val engine = ReviewEngine(repo)
        val history = listOf(
            historySet(exerciseId = 1L, weight = 110.0),
            historySet(exerciseId = 2L, weight = 65.0),
        )
        val result = engine.runCheckpoint(
            program = program(confidenceScore = 1.0),
            checkpoint = checkpoint(),
            completedSessions = (1..8).map { session() },
            performedWorkoutIds = (1L..8L).toList(),
            history = history,
        )
        assertTrue(result.newConfidenceScore <= 1.0)
        assertEquals(1.0, result.newConfidenceScore, 0.001)
    }

    // ── 5. RPE fatigue with only 1 muscle group does NOT trigger deload ──

    @Test
    fun rpeFatigueWithOnlyOneGroupDoesNotTriggerDeload() {
        val repo = fakeRepo(completedCount = 6, skippedCount = 0)
        val engine = ReviewEngine(repo)
        // Only exerciseId=1L shows RPE fatigue (early=7.0, late=9.0, delta=2.0 >= 1.5)
        // exerciseId=2L has stable RPE (no fatigue)
        val history = listOf(
            // Exercise 1: rising RPE (fatigue)
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.0, completedAtUtc = "2026-03-10T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.0, completedAtUtc = "2026-03-12T12:00:00Z"),
            // Exercise 2: stable RPE (no fatigue)
            historySet(exerciseId = 2L, weight = 60.0, rpe = 7.0, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 7.0, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 7.0, completedAtUtc = "2026-03-10T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 7.0, completedAtUtc = "2026-03-12T12:00:00Z"),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(),
            completedSessions = (1..6).map { session() },
            performedWorkoutIds = (1L..6L).toList(),
            history = history,
        )
        // Only 1 group fatigued, need 2+ -> should NOT be TRIGGER_DELOAD
        assertTrue(
            "Expected action other than TRIGGER_DELOAD but got ${result.action}",
            result.action != CheckpointAction.TRIGGER_DELOAD,
        )
    }

    // ── 6. Exercise evolution suggestions when slots have evolutionTargetExerciseId and RPE is low ──

    @Test
    fun exerciseEvolutionSuggestedWhenRpeIsLowAndEvolutionTargetExists() {
        val slots = listOf(
            ProgramExerciseSlot(
                id = 100L,
                programId = "test-program",
                exerciseId = 1L,
                role = ExerciseRole.PRIMARY,
                baselineWeeklySetTarget = 12,
                progressionTrack = ProgressionTrack(
                    startingSets = 3,
                    evolutionTargetExerciseId = 99L,
                ),
            ),
        )
        val repo = fakeRepo(completedCount = 6, skippedCount = 1, slots = slots)
        val engine = ReviewEngine(repo)
        // Two sessions with low RPE (<= 6.5) at distinct timestamps
        val history = listOf(
            historySet(
                exerciseId = 1L, weight = 80.0, rpe = 6.0,
                completedAtUtc = "2026-03-10T12:00:00Z", exerciseName = "Goblet Squat",
            ),
            historySet(
                exerciseId = 1L, weight = 80.0, rpe = 6.5,
                completedAtUtc = "2026-03-12T12:00:00Z", exerciseName = "Goblet Squat",
            ),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(),
            completedSessions = (1..6).map { session() },
            performedWorkoutIds = (1L..6L).toList(),
            history = history,
        )
        assertTrue(
            "Expected at least one evolution suggestion but got none",
            result.exerciseEvolutionSuggestions.isNotEmpty(),
        )
        val suggestion = result.exerciseEvolutionSuggestions.first()
        assertEquals(100L, suggestion.slotId)
        assertEquals(1L, suggestion.currentExerciseId)
        assertEquals(99L, suggestion.suggestedExerciseId)
        assertEquals("Goblet Squat", suggestion.currentExerciseName)
    }

    @Test
    fun noEvolutionSuggestionWhenSlotHasNoEvolutionTarget() {
        val slots = listOf(
            ProgramExerciseSlot(
                id = 100L,
                programId = "test-program",
                exerciseId = 1L,
                role = ExerciseRole.PRIMARY,
                baselineWeeklySetTarget = 12,
                progressionTrack = ProgressionTrack(
                    startingSets = 3,
                    evolutionTargetExerciseId = null,
                ),
            ),
        )
        val repo = fakeRepo(completedCount = 6, skippedCount = 1, slots = slots)
        val engine = ReviewEngine(repo)
        val history = listOf(
            historySet(exerciseId = 1L, weight = 80.0, rpe = 5.0, completedAtUtc = "2026-03-10T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 80.0, rpe = 5.0, completedAtUtc = "2026-03-12T12:00:00Z"),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(),
            completedSessions = (1..6).map { session() },
            performedWorkoutIds = (1L..6L).toList(),
            history = history,
        )
        assertTrue(
            "Expected no evolution suggestions but got ${result.exerciseEvolutionSuggestions.size}",
            result.exerciseEvolutionSuggestions.isEmpty(),
        )
    }

    @Test
    fun noEvolutionSuggestionWhenRpeIsTooHigh() {
        val slots = listOf(
            ProgramExerciseSlot(
                id = 100L,
                programId = "test-program",
                exerciseId = 1L,
                role = ExerciseRole.PRIMARY,
                baselineWeeklySetTarget = 12,
                progressionTrack = ProgressionTrack(
                    startingSets = 3,
                    evolutionTargetExerciseId = 99L,
                ),
            ),
        )
        val repo = fakeRepo(completedCount = 6, skippedCount = 1, slots = slots)
        val engine = ReviewEngine(repo)
        // RPE above 6.5 threshold
        val history = listOf(
            historySet(exerciseId = 1L, weight = 80.0, rpe = 8.0, completedAtUtc = "2026-03-10T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 80.0, rpe = 8.5, completedAtUtc = "2026-03-12T12:00:00Z"),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(),
            completedSessions = (1..6).map { session() },
            performedWorkoutIds = (1L..6L).toList(),
            history = history,
        )
        assertTrue(
            "Expected no evolution suggestions when RPE is high but got ${result.exerciseEvolutionSuggestions.size}",
            result.exerciseEvolutionSuggestions.isEmpty(),
        )
    }
}
