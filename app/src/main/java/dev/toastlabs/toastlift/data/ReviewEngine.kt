package dev.toastlabs.toastlift.data

import kotlin.math.max

data class CheckpointResult(
    val action: CheckpointAction,
    val newConfidenceScore: Double,
    val summaryText: String,
    val exerciseEvolutionSuggestions: List<EvolutionSuggestion>,
    val pivotRequired: Boolean,
)

enum class CheckpointAction {
    CONTINUE,
    INTENSIFY,
    EXTEND_BLOCK,
    TRIGGER_DELOAD,
    PIVOT_EXERCISES,
    REDUCE_TO_MAINTAIN,
}

data class EvolutionSuggestion(
    val slotId: Long,
    val currentExerciseId: Long,
    val currentExerciseName: String,
    val suggestedExerciseId: Long,
    val suggestedExerciseName: String,
    val reason: String,
)

class ReviewEngine(private val programRepository: ProgramRepository) {

    fun runCheckpoint(
        program: TrainingProgram,
        checkpoint: ProgramCheckpoint,
        completedSessions: List<PlannedSession>,
        performedWorkoutIds: List<Long>,
        history: List<HistoricalExerciseSet>,
    ): CheckpointResult {
        val allSessions = programRepository.loadSessionsForProgram(program.id)
        val skippedCount = programRepository.countSessionsByStatus(program.id, SessionStatus.SKIPPED)
        val completedCount = completedSessions.size
        val totalScheduled = completedCount + skippedCount +
            programRepository.countSessionsByStatus(program.id, SessionStatus.UPCOMING) +
            programRepository.countSessionsByStatus(program.id, SessionStatus.MIGRATED)

        // 1. Adherence rate
        val adherenceRate = if (completedCount + skippedCount > 0) {
            completedCount.toDouble() / (completedCount + skippedCount)
        } else {
            1.0
        }

        // 2. RPE fatigue analysis
        val rpeFatigueTrigger = checkRelativeRpeFatigue(history, allSessions)

        // 3. Progress vs success criteria
        val progressStatus = assessProgress(program.successCriteria, history)

        // 4. Check for stalled lifts across checkpoints
        val previousCheckpoints = programRepository.loadAllCheckpoints(program.id)
            .filter { it.status == CheckpointStatus.COMPLETED }
        val stalledForMultipleCheckpoints = previousCheckpoints.size >= 2 &&
            progressStatus == ProgressStatus.FLAT

        // 5. Determine action
        val action = when {
            adherenceRate < 0.6 -> CheckpointAction.REDUCE_TO_MAINTAIN
            rpeFatigueTrigger -> CheckpointAction.TRIGGER_DELOAD
            stalledForMultipleCheckpoints -> CheckpointAction.PIVOT_EXERCISES
            progressStatus == ProgressStatus.AHEAD -> CheckpointAction.INTENSIFY
            checkpoint.checkpointType == CheckpointType.BLOCK_WRAP -> CheckpointAction.CONTINUE
            else -> CheckpointAction.CONTINUE
        }

        // 6. Confidence scoring
        var confidence = program.confidenceScore
        confidence -= skippedCount * 0.1
        if (progressStatus == ProgressStatus.FLAT) confidence -= 0.15
        if (rpeFatigueTrigger) confidence -= 0.2
        confidence = confidence.coerceIn(0.0, 1.0)

        val pivotRequired = confidence < program.adaptationPolicy.confidenceFloorForAutonomousChanges

        // 7. Exercise evolution suggestions
        val evolutionSuggestions = checkExerciseEvolution(program, history)

        // 8. Build summary
        val summaryText = buildSummaryText(
            action = action,
            adherenceRate = adherenceRate,
            progressStatus = progressStatus,
            rpeFatigueTrigger = rpeFatigueTrigger,
            completedCount = completedCount,
            totalScheduled = totalScheduled,
            confidence = confidence,
        )

        // 9. Log event and complete checkpoint
        programRepository.logEvent(
            ProgramEvent(
                programId = program.id,
                eventType = ProgramEventType.REVIEW_COMPLETED,
                payloadJson = """{"action":"${action.name}","confidence":$confidence,"adherence":$adherenceRate}""",
                createdAt = System.currentTimeMillis(),
            ),
        )
        programRepository.completeCheckpoint(checkpoint.id, summaryText)
        programRepository.updateConfidenceScore(program.id, confidence)

        return CheckpointResult(
            action = action,
            newConfidenceScore = confidence,
            summaryText = summaryText,
            exerciseEvolutionSuggestions = evolutionSuggestions,
            pivotRequired = pivotRequired,
        )
    }

    private enum class ProgressStatus { AHEAD, ON_TRACK, FLAT }

    private fun assessProgress(
        successCriteria: SuccessCriteria,
        history: List<HistoricalExerciseSet>,
    ): ProgressStatus {
        if (successCriteria.targetLifts.isEmpty()) return ProgressStatus.ON_TRACK

        var aheadCount = 0
        var flatCount = 0
        for ((exerciseId, target) in successCriteria.targetLifts) {
            val bestWeight = history
                .filter { it.exerciseId == exerciseId && it.completed && it.weight != null }
                .maxOfOrNull { it.weight ?: 0.0 }
                ?: continue

            when {
                bestWeight >= target.targetValue -> aheadCount++
                bestWeight < target.targetValue * 0.95 -> flatCount++
            }
        }

        return when {
            aheadCount > 0 && flatCount == 0 -> ProgressStatus.AHEAD
            flatCount >= successCriteria.targetLifts.size / 2 -> ProgressStatus.FLAT
            else -> ProgressStatus.ON_TRACK
        }
    }

    private fun checkRelativeRpeFatigue(
        history: List<HistoricalExerciseSet>,
        allSessions: List<PlannedSession>,
    ): Boolean {
        // Check if RPE for the same load is 1.5+ higher than earlier in the block
        val exerciseSessions = history
            .filter { it.completed && it.lastSetRpe != null && it.weight != null }
            .groupBy { it.exerciseId }

        var triggerCount = 0
        for ((_, sets) in exerciseSessions) {
            val sortedSets = sets.sortedBy { it.completedAtUtc }
            if (sortedSets.size < 4) continue

            val earlySets = sortedSets.take(sortedSets.size / 2)
            val lateSets = sortedSets.drop(sortedSets.size / 2)

            // Compare RPE at similar weights
            val earlyRpeAvg = earlySets.mapNotNull { it.lastSetRpe }.takeIf { it.isNotEmpty() }?.average() ?: continue
            val lateRpeAvg = lateSets.mapNotNull { it.lastSetRpe }.takeIf { it.isNotEmpty() }?.average() ?: continue

            if (lateRpeAvg - earlyRpeAvg >= 1.5) {
                triggerCount++
            }
        }

        // Need 2+ muscle groups showing fatigue
        return triggerCount >= 2
    }

    private fun checkExerciseEvolution(
        program: TrainingProgram,
        history: List<HistoricalExerciseSet>,
    ): List<EvolutionSuggestion> {
        val slots = programRepository.loadSlotsForProgram(program.id)
        val suggestions = mutableListOf<EvolutionSuggestion>()

        for (slot in slots) {
            if (slot.role != ExerciseRole.PRIMARY) continue
            if (slot.progressionTrack.evolutionTargetExerciseId == null) continue

            // Check if last 2 sessions had RPE <= 6.5 at top load
            val recentSets = history
                .filter { it.exerciseId == slot.exerciseId && it.completed && it.lastSetRpe != null }
                .groupBy { it.completedAtUtc }
                .entries
                .sortedByDescending { it.key }
                .take(2)

            if (recentSets.size < 2) continue
            val allEasy = recentSets.all { (_, sets) ->
                sets.mapNotNull { it.lastSetRpe }.average() <= 6.5
            }

            if (allEasy) {
                val currentName = history.firstOrNull { it.exerciseId == slot.exerciseId }?.exerciseName ?: "Current exercise"
                suggestions += EvolutionSuggestion(
                    slotId = slot.id,
                    currentExerciseId = slot.exerciseId,
                    currentExerciseName = currentName,
                    suggestedExerciseId = slot.progressionTrack.evolutionTargetExerciseId,
                    suggestedExerciseName = "Next progression", // Would need catalog lookup for real name
                    reason = "You've mastered this movement at current loads. Ready for the next challenge.",
                )
            }
        }

        return suggestions
    }

    private fun buildSummaryText(
        action: CheckpointAction,
        adherenceRate: Double,
        progressStatus: ProgressStatus,
        rpeFatigueTrigger: Boolean,
        completedCount: Int,
        totalScheduled: Int,
        confidence: Double,
    ): String = buildString {
        append("Completed $completedCount sessions (${(adherenceRate * 100).toInt()}% adherence). ")
        when (progressStatus) {
            ProgressStatus.AHEAD -> append("Progress is ahead of plan. ")
            ProgressStatus.ON_TRACK -> append("Progress is on track. ")
            ProgressStatus.FLAT -> append("Progress has stalled on key lifts. ")
        }
        if (rpeFatigueTrigger) {
            append("Fatigue indicators are elevated — same loads are feeling harder. ")
        }
        when (action) {
            CheckpointAction.CONTINUE -> append("Continuing as planned.")
            CheckpointAction.INTENSIFY -> append("Recommending increased intensity for the next phase.")
            CheckpointAction.EXTEND_BLOCK -> append("Recommending extending the block to capitalize on momentum.")
            CheckpointAction.TRIGGER_DELOAD -> append("Recommending a deload week to manage accumulated fatigue.")
            CheckpointAction.PIVOT_EXERCISES -> append("Recommending exercise changes to break through the plateau.")
            CheckpointAction.REDUCE_TO_MAINTAIN -> append("Adherence has been low. Recommending a reduced maintenance block.")
        }
    }
}
