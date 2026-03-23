package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * End-to-end lifecycle test for the ReviewEngine checkpoint flow.
 * Simulates a program going through multiple checkpoints with changing conditions.
 */
class ProgramLifecycleTest {

    private val programId = "lifecycle-program"

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
        id = programId,
        title = "Lifecycle Test Block",
        goal = "Strength",
        primaryOutcomeMetric = OutcomeMetric.STRENGTH,
        programArchetype = ProgramArchetype.LINEAR_RAMP,
        periodizationModel = PeriodizationModel.LINEAR,
        splitProgramId = 1L,
        totalWeeks = 8,
        sessionsPerWeek = 4,
        successCriteria = successCriteria,
        adaptationPolicy = adaptationPolicy,
        confidenceScore = confidenceScore,
        createdAt = System.currentTimeMillis(),
    )

    private fun checkpoint(weekNumber: Int, id: Long = weekNumber.toLong()) = ProgramCheckpoint(
        id = id,
        programId = programId,
        weekNumber = weekNumber,
        checkpointType = CheckpointType.PROGRESS_REVIEW,
    )

    private fun sessions(count: Int): List<PlannedSession> = (1..count).map { i ->
        PlannedSession(
            id = i.toLong(),
            programId = programId,
            weekNumber = (i - 1) / 4 + 1,
            dayIndex = (i - 1) % 4,
            sequenceNumber = i,
            focusKey = SessionFocus.FULL_BODY,
            plannedSets = 16,
            status = SessionStatus.COMPLETED,
        )
    }

    private fun historySet(
        exerciseId: Long,
        weight: Double,
        rpe: Double? = null,
        completedAtUtc: String = "2026-03-15T12:00:00Z",
        exerciseName: String = "Exercise $exerciseId",
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
        targetMuscleGroup = if (exerciseId == 1L) "Chest" else "Back",
        primeMover = if (exerciseId == 1L) "Chest" else "Back",
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
        upcomingCount: Int = 0,
        previousCheckpoints: List<ProgramCheckpoint> = emptyList(),
        slots: List<ProgramExerciseSlot> = emptyList(),
    ): ProgramRepository {
        return object : ProgramRepository(null) {
            override fun loadSessionsForProgram(programId: String) = emptyList<PlannedSession>()
            override fun countSessionsByStatus(programId: String, status: SessionStatus) = when (status) {
                SessionStatus.COMPLETED -> completedCount
                SessionStatus.SKIPPED -> skippedCount
                SessionStatus.UPCOMING -> upcomingCount
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

    // ── Checkpoint 1 (week 2): good adherence + progress ahead -> INTENSIFY ──

    @Test
    fun firstCheckpointGoodAdherenceProgressAheadReturnsIntensify() {
        val repo = fakeRepo(completedCount = 7, skippedCount = 1)
        val engine = ReviewEngine(repo)
        // Both lifts at or above target -> AHEAD
        val history = listOf(
            historySet(exerciseId = 1L, weight = 105.0),
            historySet(exerciseId = 2L, weight = 62.0),
        )
        val result = engine.runCheckpoint(
            program = program(confidenceScore = 1.0),
            checkpoint = checkpoint(weekNumber = 2),
            completedSessions = sessions(7),
            performedWorkoutIds = (1L..7L).toList(),
            history = history,
        )
        assertEquals(CheckpointAction.INTENSIFY, result.action)
        // confidence = 1.0 - (1 * 0.1) = 0.9 (one skipped session)
        assertEquals(0.9, result.newConfidenceScore, 0.01)
        assertFalse(result.pivotRequired)
    }

    // ── Checkpoint 2 (week 4): good adherence but RPE climbing across 2+ groups -> TRIGGER_DELOAD ──

    @Test
    fun secondCheckpointRpeClimbingTwoGroupsTriggersDeload() {
        val repo = fakeRepo(completedCount = 14, skippedCount = 2)
        val engine = ReviewEngine(repo)
        // Two exercises with RPE fatigue: early sessions low RPE, late sessions high RPE
        val history = listOf(
            // Exercise 1 (Chest): early RPE ~7.0, late RPE ~9.0 -> delta = 2.0 >= 1.5
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.0, completedAtUtc = "2026-03-08T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.5, completedAtUtc = "2026-03-10T12:00:00Z"),
            // Exercise 2 (Back): early RPE ~6.5, late RPE ~8.5 -> delta = 2.0 >= 1.5
            historySet(exerciseId = 2L, weight = 60.0, rpe = 6.5, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 6.5, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 8.5, completedAtUtc = "2026-03-08T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 9.0, completedAtUtc = "2026-03-10T12:00:00Z"),
        )
        val result = engine.runCheckpoint(
            program = program(confidenceScore = 0.9),
            checkpoint = checkpoint(weekNumber = 4),
            completedSessions = sessions(14),
            performedWorkoutIds = (1L..14L).toList(),
            history = history,
        )
        assertEquals(CheckpointAction.TRIGGER_DELOAD, result.action)
        // confidence = 0.9 - (2 * 0.1) - 0.2 (rpeFatigue) = 0.5
        assertEquals(0.5, result.newConfidenceScore, 0.01)
        assertTrue(result.summaryText.contains("fatigue", ignoreCase = true))
    }

    // ── Checkpoint 3 (week 6): low adherence -> REDUCE_TO_MAINTAIN with pivot required ──

    @Test
    fun thirdCheckpointLowAdherenceReturnsReduceToMaintainWithPivot() {
        val repo = fakeRepo(completedCount = 4, skippedCount = 8)
        val engine = ReviewEngine(repo)
        val result = engine.runCheckpoint(
            program = program(confidenceScore = 0.5),
            checkpoint = checkpoint(weekNumber = 6),
            completedSessions = sessions(4),
            performedWorkoutIds = (1L..4L).toList(),
            history = emptyList(),
        )
        // adherence = 4/(4+8) = 0.33 < 0.6 -> REDUCE_TO_MAINTAIN
        assertEquals(CheckpointAction.REDUCE_TO_MAINTAIN, result.action)
        // confidence = 0.5 - (8 * 0.1) = -0.3 -> clamped to 0.0
        assertEquals(0.0, result.newConfidenceScore, 0.01)
        // 0.0 < 0.4 (confidenceFloor) -> pivot required
        assertTrue(result.pivotRequired)
        assertTrue(result.summaryText.contains("maintenance", ignoreCase = true))
    }

    // ── Full lifecycle: confidence degrades across three checkpoints ──

    @Test
    fun confidenceDegradesThroughLifecycle() {
        // Phase 1: starts at 1.0, 1 skip -> 0.9
        val repo1 = fakeRepo(completedCount = 7, skippedCount = 1)
        val engine1 = ReviewEngine(repo1)
        val result1 = engine1.runCheckpoint(
            program = program(confidenceScore = 1.0),
            checkpoint = checkpoint(weekNumber = 2),
            completedSessions = sessions(7),
            performedWorkoutIds = (1L..7L).toList(),
            history = listOf(
                historySet(exerciseId = 1L, weight = 105.0),
                historySet(exerciseId = 2L, weight = 62.0),
            ),
        )
        assertEquals(0.9, result1.newConfidenceScore, 0.01)

        // Phase 2: carry forward 0.9, 2 skips + RPE fatigue -> 0.9 - 0.2 - 0.2 = 0.5
        val repo2 = fakeRepo(completedCount = 14, skippedCount = 2)
        val engine2 = ReviewEngine(repo2)
        val fatigueHistory = listOf(
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.0, completedAtUtc = "2026-03-08T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.0, completedAtUtc = "2026-03-10T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 6.5, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 6.5, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 8.5, completedAtUtc = "2026-03-08T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 8.5, completedAtUtc = "2026-03-10T12:00:00Z"),
        )
        val result2 = engine2.runCheckpoint(
            program = program(confidenceScore = result1.newConfidenceScore),
            checkpoint = checkpoint(weekNumber = 4),
            completedSessions = sessions(14),
            performedWorkoutIds = (1L..14L).toList(),
            history = fatigueHistory,
        )
        assertEquals(0.5, result2.newConfidenceScore, 0.01)

        // Phase 3: carry forward 0.5, 6 skips -> 0.5 - 0.6 = -0.1 -> clamped to 0.0
        val repo3 = fakeRepo(completedCount = 4, skippedCount = 6)
        val engine3 = ReviewEngine(repo3)
        val result3 = engine3.runCheckpoint(
            program = program(confidenceScore = result2.newConfidenceScore),
            checkpoint = checkpoint(weekNumber = 6),
            completedSessions = sessions(4),
            performedWorkoutIds = (1L..4L).toList(),
            history = emptyList(),
        )
        assertEquals(0.0, result3.newConfidenceScore, 0.01)
        assertTrue(result3.pivotRequired)
    }

    // ── Verify action priority: low adherence takes precedence over RPE fatigue ──

    @Test
    fun lowAdherenceTakesPrecedenceOverRpeFatigue() {
        val repo = fakeRepo(completedCount = 3, skippedCount = 7)
        val engine = ReviewEngine(repo)
        // Provide RPE fatigue history that would normally trigger deload
        val history = listOf(
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.0, completedAtUtc = "2026-03-08T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.0, completedAtUtc = "2026-03-10T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 6.5, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 6.5, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 8.5, completedAtUtc = "2026-03-08T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 8.5, completedAtUtc = "2026-03-10T12:00:00Z"),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(weekNumber = 4),
            completedSessions = sessions(3),
            performedWorkoutIds = (1L..3L).toList(),
            history = history,
        )
        // adherence = 3/(3+7) = 0.3 < 0.6 -> REDUCE_TO_MAINTAIN wins over TRIGGER_DELOAD
        assertEquals(CheckpointAction.REDUCE_TO_MAINTAIN, result.action)
    }

    // ── RPE fatigue takes precedence over stalled pivot ──

    @Test
    fun rpeFatigueTakesPrecedenceOverStalledPivot() {
        val repo = fakeRepo(
            completedCount = 8,
            skippedCount = 0,
            previousCheckpoints = listOf(
                ProgramCheckpoint(
                    id = 10L, programId = programId, weekNumber = 2,
                    checkpointType = CheckpointType.PROGRESS_REVIEW,
                    status = CheckpointStatus.COMPLETED,
                ),
                ProgramCheckpoint(
                    id = 11L, programId = programId, weekNumber = 4,
                    checkpointType = CheckpointType.PROGRESS_REVIEW,
                    status = CheckpointStatus.COMPLETED,
                ),
            ),
        )
        val engine = ReviewEngine(repo)
        // Stalled lifts (would trigger PIVOT) AND RPE fatigue (would trigger DELOAD)
        val history = listOf(
            // Stalled weights (well below target)
            historySet(exerciseId = 1L, weight = 80.0, rpe = 7.0, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 80.0, rpe = 7.0, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 80.0, rpe = 9.0, completedAtUtc = "2026-03-08T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 80.0, rpe = 9.0, completedAtUtc = "2026-03-10T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 45.0, rpe = 6.5, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 45.0, rpe = 6.5, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 45.0, rpe = 8.5, completedAtUtc = "2026-03-08T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 45.0, rpe = 8.5, completedAtUtc = "2026-03-10T12:00:00Z"),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(weekNumber = 6),
            completedSessions = sessions(8),
            performedWorkoutIds = (1L..8L).toList(),
            history = history,
        )
        // rpeFatigueTrigger is checked before stalledForMultipleCheckpoints in the when block
        assertEquals(CheckpointAction.TRIGGER_DELOAD, result.action)
    }
}
