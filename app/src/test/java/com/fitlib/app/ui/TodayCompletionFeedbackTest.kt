package com.fitlib.app.ui

import com.fitlib.app.data.HistorySummary
import com.fitlib.app.data.TodayCompletionFeedbackVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TodayCompletionFeedbackTest {
    @Test
    fun buildTodayWorkoutCompletionState_marksWorkoutCompletedOnSameLocalDay() {
        val state = buildTodayWorkoutCompletionState(
            history = listOf(
                historySummary(
                    title = "Friday Full Body",
                    completedAtUtc = "2026-03-20T07:30:00Z",
                ),
            ),
            now = Instant.parse("2026-03-20T18:00:00Z"),
            zoneId = ZoneId.of("UTC"),
        )

        assertTrue(state.isCompletedToday)
        assertEquals(1f, state.progressFraction, 0.0001f)
        assertEquals("Friday Full Body", state.completedWorkoutTitle)
    }

    @Test
    fun buildTodayWorkoutCompletionState_respectsLocalDateBoundary() {
        val state = buildTodayWorkoutCompletionState(
            history = listOf(
                historySummary(
                    title = "Late Session",
                    completedAtUtc = "2026-03-20T01:00:00Z",
                ),
            ),
            now = Instant.parse("2026-03-20T18:00:00Z"),
            zoneId = ZoneId.of("America/Los_Angeles"),
        )

        assertFalse(state.isCompletedToday)
        assertEquals(0f, state.progressFraction, 0.0001f)
    }

    @Test
    fun buildTodayCompletionFeedbackModel_showsBadgeOnlyForBadgeVariant() {
        val model = buildTodayCompletionFeedbackModel(
            variant = TodayCompletionFeedbackVariant.DONE_TODAY_BADGE,
            completion = TodayWorkoutCompletionState(
                isCompletedToday = true,
                progressFraction = 1f,
                completedWorkoutTitle = "Upper Push",
                completedAtUtc = "2026-03-20T09:00:00Z",
            ),
        )

        assertTrue(model.showDoneBadge)
        assertEquals("Done today", model.statusLabel)
        assertEquals(1f, model.progressFraction, 0.0001f)
        assertTrue(model.subtitle.contains("Upper Push"))
    }

    @Test
    fun buildTodayCompletionFeedbackModel_keepsMeterVisibleBeforeCompletion() {
        val model = buildTodayCompletionFeedbackModel(
            variant = TodayCompletionFeedbackVariant.PROGRESS_METER,
            completion = TodayWorkoutCompletionState(
                isCompletedToday = false,
                progressFraction = 0f,
            ),
        )

        assertFalse(model.showDoneBadge)
        assertEquals("Pending", model.statusLabel)
        assertEquals(0f, model.progressFraction, 0.0001f)
        assertTrue(model.subtitle.contains("fill the meter"))
    }

    private fun historySummary(
        title: String,
        completedAtUtc: String,
    ): HistorySummary {
        return HistorySummary(
            id = 1L,
            title = title,
            completedAtUtc = completedAtUtc,
            durationSeconds = 1800,
            totalVolume = 1200.0,
            exerciseCount = 5,
            exerciseNames = listOf("Bench Press"),
        )
    }
}
