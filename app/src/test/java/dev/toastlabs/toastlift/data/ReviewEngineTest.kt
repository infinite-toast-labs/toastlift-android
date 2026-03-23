package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReviewEngineTest {

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
    ) = HistoricalExerciseSet(
        completedAtUtc = Instant.parse(completedAtUtc),
        exerciseId = exerciseId,
        exerciseName = "Test Exercise",
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
            override fun loadSlotsForProgram(programId: String) = emptyList<ProgramExerciseSlot>()
            override fun logEvent(event: ProgramEvent) {}
            override fun completeCheckpoint(checkpointId: Long, summary: String) {}
            override fun updateConfidenceScore(programId: String, score: Double) {}
        }
    }

    @Test
    fun lowAdherenceTriggersReduceToMaintain() {
        val repo = fakeRepo(completedCount = 2, skippedCount = 5)
        val engine = ReviewEngine(repo)
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(),
            completedSessions = (1..2).map { session() },
            performedWorkoutIds = listOf(1L, 2L),
            history = emptyList(),
        )
        assertEquals(CheckpointAction.REDUCE_TO_MAINTAIN, result.action)
    }

    @Test
    fun highAdherenceWithProgressAheadTriggersIntensify() {
        val repo = fakeRepo(completedCount = 7, skippedCount = 1)
        val engine = ReviewEngine(repo)
        val history = listOf(
            historySet(exerciseId = 1L, weight = 110.0),
            historySet(exerciseId = 2L, weight = 65.0),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(),
            completedSessions = (1..7).map { session() },
            performedWorkoutIds = (1L..7L).toList(),
            history = history,
        )
        assertEquals(CheckpointAction.INTENSIFY, result.action)
    }

    @Test
    fun confidenceDegradationFromSkippedSessions() {
        val repo = fakeRepo(completedCount = 5, skippedCount = 3)
        val engine = ReviewEngine(repo)
        val result = engine.runCheckpoint(
            program = program(confidenceScore = 1.0),
            checkpoint = checkpoint(),
            completedSessions = (1..5).map { session() },
            performedWorkoutIds = (1L..5L).toList(),
            history = emptyList(),
        )
        // confidence = 1.0 - (3 * 0.1) = 0.7
        assertEquals(0.7, result.newConfidenceScore, 0.01)
    }

    @Test
    fun pivotRequiredWhenConfidenceBelowFloor() {
        val repo = fakeRepo(completedCount = 3, skippedCount = 7)
        val engine = ReviewEngine(repo)
        val result = engine.runCheckpoint(
            program = program(confidenceScore = 0.5),
            checkpoint = checkpoint(),
            completedSessions = (1..3).map { session() },
            performedWorkoutIds = (1L..3L).toList(),
            history = emptyList(),
        )
        // adherence = 3/(3+7) = 0.3 < 0.6, so REDUCE_TO_MAINTAIN
        // confidence = 0.5 - (7 * 0.1) = -0.2 -> clamped to 0.0
        assertTrue(result.pivotRequired)
        assertEquals(0.0, result.newConfidenceScore, 0.01)
    }

    @Test
    fun rpeFatigueTriggerDetectedAcrossMultipleMuscleGroups() {
        val repo = fakeRepo(completedCount = 6, skippedCount = 0)
        val engine = ReviewEngine(repo)
        val history = listOf(
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 7.0, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.0, completedAtUtc = "2026-03-10T12:00:00Z"),
            historySet(exerciseId = 1L, weight = 100.0, rpe = 9.0, completedAtUtc = "2026-03-12T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 6.5, completedAtUtc = "2026-03-01T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 6.5, completedAtUtc = "2026-03-03T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 8.5, completedAtUtc = "2026-03-10T12:00:00Z"),
            historySet(exerciseId = 2L, weight = 60.0, rpe = 8.5, completedAtUtc = "2026-03-12T12:00:00Z"),
        )
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(),
            completedSessions = (1..6).map { session() },
            performedWorkoutIds = (1L..6L).toList(),
            history = history,
        )
        assertEquals(CheckpointAction.TRIGGER_DELOAD, result.action)
    }

    @Test
    fun stalledLiftsAcrossMultipleCheckpointsTriggersPivot() {
        val repo = fakeRepo(
            completedCount = 8,
            skippedCount = 0,
            previousCheckpoints = listOf(
                ProgramCheckpoint(id = 10L, programId = "test-program", weekNumber = 2, checkpointType = CheckpointType.PROGRESS_REVIEW, status = CheckpointStatus.COMPLETED),
                ProgramCheckpoint(id = 11L, programId = "test-program", weekNumber = 4, checkpointType = CheckpointType.PROGRESS_REVIEW, status = CheckpointStatus.COMPLETED),
            ),
        )
        val engine = ReviewEngine(repo)
        val history = listOf(
            historySet(exerciseId = 1L, weight = 80.0),
            historySet(exerciseId = 2L, weight = 50.0),
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

    @Test
    fun summaryTextContainsAdherenceInfo() {
        val repo = fakeRepo(completedCount = 4, skippedCount = 1)
        val engine = ReviewEngine(repo)
        val result = engine.runCheckpoint(
            program = program(),
            checkpoint = checkpoint(),
            completedSessions = (1..4).map { session() },
            performedWorkoutIds = (1L..4L).toList(),
            history = emptyList(),
        )
        assertTrue(result.summaryText.contains("80%"))
        assertTrue(result.summaryText.contains("4 sessions"))
    }
}
