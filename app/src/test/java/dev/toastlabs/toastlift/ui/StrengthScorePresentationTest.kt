package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.StrengthScorePoint
import dev.toastlabs.toastlift.data.StrengthScoreSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthScorePresentationTest {

    @Test
    fun strengthScoreDeltaLabel_describesFirstTrackedWorkout() {
        val summary = summary(previousScore = null, deltaFromPrevious = 0)

        assertEquals("First tracked workout", strengthScoreDeltaLabel(summary))
    }

    @Test
    fun strengthScoreDeltaLabel_describesPositiveAndNegativeMovement() {
        assertEquals("+8 vs last", strengthScoreDeltaLabel(summary(previousScore = 240, deltaFromPrevious = 8)))
        assertEquals("-5 vs last", strengthScoreDeltaLabel(summary(previousScore = 240, deltaFromPrevious = -5)))
        assertEquals("Flat vs last", strengthScoreDeltaLabel(summary(previousScore = 240, deltaFromPrevious = 0)))
    }

    private fun summary(previousScore: Int?, deltaFromPrevious: Int) = StrengthScoreSummary(
        currentScore = 248,
        previousScore = previousScore,
        bestScore = 248,
        totalTrackedWorkouts = 3,
        deltaFromPrevious = deltaFromPrevious,
        timeline = listOf(
            StrengthScorePoint(
                workoutId = 1L,
                workoutTitle = "Session 1",
                completedAtUtc = "2026-03-01T12:00:00Z",
                sessionScore = 240,
                runningScore = 240,
            ),
            StrengthScorePoint(
                workoutId = 2L,
                workoutTitle = "Session 2",
                completedAtUtc = "2026-03-03T12:00:00Z",
                sessionScore = 248,
                runningScore = 248,
            ),
        ),
    )
}
