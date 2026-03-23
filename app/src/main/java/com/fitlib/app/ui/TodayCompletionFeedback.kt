package com.fitlib.app.ui

import com.fitlib.app.data.HistorySummary
import com.fitlib.app.data.TodayCompletionFeedbackVariant
import java.time.Instant
import java.time.ZoneId

data class TodayWorkoutCompletionState(
    val isCompletedToday: Boolean,
    val progressFraction: Float,
    val completedWorkoutTitle: String? = null,
    val completedAtUtc: String? = null,
)

internal data class TodayCompletionFeedbackModel(
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val progressFraction: Float,
    val showDoneBadge: Boolean,
)

internal fun buildTodayWorkoutCompletionState(
    history: List<HistorySummary>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): TodayWorkoutCompletionState {
    val today = now.atZone(zoneId).toLocalDate()
    val completedToday = history.firstOrNull { entry ->
        runCatching { Instant.parse(entry.completedAtUtc).atZone(zoneId).toLocalDate() }.getOrNull() == today
    }
    return if (completedToday != null) {
        TodayWorkoutCompletionState(
            isCompletedToday = true,
            progressFraction = 1f,
            completedWorkoutTitle = completedToday.title,
            completedAtUtc = completedToday.completedAtUtc,
        )
    } else {
        TodayWorkoutCompletionState(
            isCompletedToday = false,
            progressFraction = 0f,
        )
    }
}

internal fun buildTodayCompletionFeedbackModel(
    variant: TodayCompletionFeedbackVariant,
    completion: TodayWorkoutCompletionState,
): TodayCompletionFeedbackModel {
    val workoutLabel = completion.completedWorkoutTitle
        ?.takeIf { it.isNotBlank() }
        ?: "Workout"

    return when (variant) {
        TodayCompletionFeedbackVariant.DONE_TODAY_BADGE -> {
            if (completion.isCompletedToday) {
                TodayCompletionFeedbackModel(
                    title = "Daily Status: Complete 🎉🎉🎉",
                    subtitle = "$workoutLabel logged today.",
                    statusLabel = "Daily Status: Complete 🎉🎉🎉",
                    progressFraction = 1f,
                    showDoneBadge = true,
                )
            } else {
                TodayCompletionFeedbackModel(
                    title = "Today's workout",
                    subtitle = "Log a workout today to earn the badge.",
                    statusLabel = "Pending",
                    progressFraction = 0f,
                    showDoneBadge = false,
                )
            }
        }

        TodayCompletionFeedbackVariant.PROGRESS_METER -> TodayCompletionFeedbackModel(
            title = "Today's workout",
            subtitle = if (completion.isCompletedToday) {
                "$workoutLabel closed the day."
            } else {
                "Log a workout today to fill the meter."
            },
            statusLabel = if (completion.isCompletedToday) "Complete" else "Pending",
            progressFraction = completion.progressFraction,
            showDoneBadge = false,
        )
    }
}
