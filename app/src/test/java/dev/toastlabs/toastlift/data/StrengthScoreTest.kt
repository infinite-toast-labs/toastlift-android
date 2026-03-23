package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrengthScoreTest {

    @Test
    fun buildStrengthScoreSummary_blendsStrengthVolumeAndRepOnlyWorkIntoSmoothedTrend() {
        val summary = buildStrengthScoreSummary(
            listOf(
                row(workoutId = 1L, workoutTitle = "Session 1", completedAtUtc = "2026-03-01T12:00:00Z", exerciseId = 10L, reps = 5, weight = 100.0),
                row(workoutId = 1L, workoutTitle = "Session 1", completedAtUtc = "2026-03-01T12:00:00Z", exerciseId = 10L, reps = 5, weight = 100.0),
                row(workoutId = 1L, workoutTitle = "Session 1", completedAtUtc = "2026-03-01T12:00:00Z", exerciseId = 20L, reps = 8, weight = 60.0),
                row(workoutId = 2L, workoutTitle = "Session 2", completedAtUtc = "2026-03-03T12:00:00Z", exerciseId = 10L, reps = 5, weight = 110.0),
                row(workoutId = 2L, workoutTitle = "Session 2", completedAtUtc = "2026-03-03T12:00:00Z", exerciseId = 20L, reps = 8, weight = 62.0),
                row(workoutId = 2L, workoutTitle = "Session 2", completedAtUtc = "2026-03-03T12:00:00Z", exerciseId = 30L, reps = 20, weight = null),
            ),
        )

        requireNotNull(summary)
        assertEquals(253, summary.currentScore)
        assertEquals(247, summary.previousScore)
        assertEquals(253, summary.bestScore)
        assertEquals(2, summary.totalTrackedWorkouts)
        assertEquals(6, summary.deltaFromPrevious)
        assertEquals(listOf(247, 253), summary.timeline.map { it.runningScore })
        assertEquals(listOf(247, 264), summary.timeline.map { it.sessionScore })
    }

    @Test
    fun applyStrengthScores_matchesRunningScoresBackToHistoryEntries() {
        val history = listOf(
            historySummary(id = 2L, title = "Session 2"),
            historySummary(id = 1L, title = "Session 1"),
            historySummary(id = 3L, title = "Session 3"),
        )
        val strengthScoreSummary = StrengthScoreSummary(
            currentScore = 253,
            previousScore = 247,
            bestScore = 253,
            totalTrackedWorkouts = 2,
            deltaFromPrevious = 6,
            timeline = listOf(
                StrengthScorePoint(
                    workoutId = 1L,
                    workoutTitle = "Session 1",
                    completedAtUtc = "2026-03-01T12:00:00Z",
                    sessionScore = 247,
                    runningScore = 247,
                ),
                StrengthScorePoint(
                    workoutId = 2L,
                    workoutTitle = "Session 2",
                    completedAtUtc = "2026-03-03T12:00:00Z",
                    sessionScore = 264,
                    runningScore = 253,
                ),
            ),
        )

        val scoredHistory = applyStrengthScores(history, strengthScoreSummary)

        assertEquals(253, scoredHistory[0].strengthScore)
        assertEquals(247, scoredHistory[1].strengthScore)
        assertNull(scoredHistory[2].strengthScore)
    }

    private fun row(
        workoutId: Long,
        workoutTitle: String,
        completedAtUtc: String,
        exerciseId: Long,
        reps: Int?,
        weight: Double?,
        isCompleted: Boolean = true,
    ) = StrengthScoreSetRow(
        workoutId = workoutId,
        workoutTitle = workoutTitle,
        completedAtUtc = completedAtUtc,
        exerciseId = exerciseId,
        reps = reps,
        weight = weight,
        isCompleted = isCompleted,
    )

    private fun historySummary(id: Long, title: String) = HistorySummary(
        id = id,
        title = title,
        completedAtUtc = "2026-03-01T12:00:00Z",
        durationSeconds = 1800,
        totalVolume = 1200.0,
        exerciseCount = 3,
        exerciseNames = listOf("Bench Press", "Row"),
    )
}
