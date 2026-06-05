package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.HistorySummary
import dev.toastlabs.toastlift.data.HistoryWorkoutMetric
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryStatsPresentationTest {

    @Test
    fun dashboardWorkoutCountUsesUncappedMetricsInsteadOfRecentFeedSize() {
        val today = LocalDate.of(2026, 4, 30)
        val allMetrics = (1L..25L).map { id ->
            historyMetric(
                id = id,
                completedDate = today.minusDays(25L - id),
            )
        }
        val recentHistory = allMetrics.takeLast(20).map { metric ->
            historySummary(
                id = metric.id,
                completedAtUtc = metric.completedAtUtc,
                startedAtUtc = metric.startedAtUtc,
            )
        }

        val dashboard = buildHistoryDashboardData(
            history = recentHistory,
            historyWorkoutMetrics = allMetrics,
            weeklyGoal = 3,
            topExercise = null,
            topEquipment = null,
            strengthScore = null,
            today = today,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(20, recentHistory.size)
        assertEquals(25, dashboard.totalWorkouts)
        assertEquals(25, dashboard.milestoneProgress.first { it.title == "Workouts" }.current)
        assertEquals(2, dashboard.milestoneProgress.first { it.title == "Workouts" }.achievedCount)
    }

    @Test
    fun historyStatsPeriodDataFiltersAllTimeLastSevenAndLastThirtyDays() {
        val today = LocalDate.of(2026, 4, 30)
        val metrics = (0L until 35L).map { offset ->
            historyMetric(
                id = offset + 1L,
                completedDate = today.minusDays(offset),
                durationSeconds = 1_800,
                totalVolume = 1_000.0,
                exerciseCount = 4,
                setCount = 12,
            )
        }

        val allTime = buildHistoryStatsPeriodData(metrics, HistoryStatsFilter.AllTime, today, ZoneId.of("UTC"))
        val lastSeven = buildHistoryStatsPeriodData(metrics, HistoryStatsFilter.Last7Days, today, ZoneId.of("UTC"))
        val lastThirty = buildHistoryStatsPeriodData(metrics, HistoryStatsFilter.Last30Days, today, ZoneId.of("UTC"))

        assertEquals(35, allTime.workoutCount)
        assertEquals(7, lastSeven.workoutCount)
        assertEquals(30, lastThirty.workoutCount)
        assertEquals(84, lastSeven.completedSets)
        assertEquals(30_000.0, lastThirty.totalVolume, 0.001)
    }

    private fun historySummary(
        id: Long,
        completedAtUtc: String,
        startedAtUtc: String = completedAtUtc,
    ) = HistorySummary(
        id = id,
        title = "Workout $id",
        completedAtUtc = completedAtUtc,
        startedAtUtc = startedAtUtc,
        durationSeconds = 1_800,
        totalVolume = 1_000.0,
        exerciseCount = 4,
        setCount = 12,
        exerciseNames = listOf("Squat", "Bench"),
    )

    private fun historyMetric(
        id: Long,
        completedDate: LocalDate,
        durationSeconds: Int = 1_800,
        totalVolume: Double = 1_000.0,
        exerciseCount: Int = 4,
        setCount: Int = 12,
    ): HistoryWorkoutMetric {
        val completedAtUtc = completedDate
            .atTime(12, 0)
            .toInstant(ZoneOffset.UTC)
            .toString()
        return HistoryWorkoutMetric(
            id = id,
            completedAtUtc = completedAtUtc,
            startedAtUtc = completedAtUtc,
            durationSeconds = durationSeconds,
            totalVolume = totalVolume,
            exerciseCount = exerciseCount,
            setCount = setCount,
        )
    }
}
