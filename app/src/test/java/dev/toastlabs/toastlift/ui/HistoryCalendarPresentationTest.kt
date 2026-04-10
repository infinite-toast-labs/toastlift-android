package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.HistorySummary
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryCalendarPresentationTest {

    @Test
    fun buildHistoryCalendarWeekPages_groupsWorkoutsIntoSevenDayPages() {
        val pages = buildHistoryCalendarWeekPages(
            history = listOf(
                historySummary(id = 1L, completedAtUtc = "2026-03-31T08:00:00Z", totalVolume = 1200.0),
                historySummary(id = 2L, completedAtUtc = "2026-03-31T18:00:00Z", totalVolume = 1600.0),
                historySummary(id = 3L, completedAtUtc = "2026-04-07T12:00:00Z", totalVolume = 900.0),
                historySummary(id = 4L, completedAtUtc = "2026-04-08T12:00:00Z", totalVolume = 1100.0),
            ),
            zoneId = ZoneId.of("UTC"),
            today = LocalDate.of(2026, 4, 10),
        )

        assertEquals(2, pages.size)
        assertEquals(LocalDate.of(2026, 3, 29), pages.first().weekStart)
        assertEquals(2, pages.first().days.first { it.date == LocalDate.of(2026, 3, 31) }.workoutCount)
        assertEquals(listOf(2L, 1L), pages.first().workouts.map(HistorySummary::id))
        assertEquals(LocalDate.of(2026, 4, 5), pages.last().weekStart)
        assertEquals(listOf(4L, 3L), pages.last().workouts.map(HistorySummary::id))
        assertEquals(2000.0, pages.last().totalVolume, 0.001)
    }

    @Test
    fun buildHistoryCalendarMonthPages_buildsMonthGridFromSundayToSaturday() {
        val pages = buildHistoryCalendarMonthPages(
            history = listOf(
                historySummary(id = 1L, completedAtUtc = "2026-03-31T12:00:00Z"),
                historySummary(id = 2L, completedAtUtc = "2026-04-02T12:00:00Z"),
                historySummary(id = 3L, completedAtUtc = "2026-04-18T12:00:00Z"),
            ),
            zoneId = ZoneId.of("UTC"),
            today = LocalDate.of(2026, 4, 20),
        )

        assertEquals(2, pages.size)
        val aprilPage = pages.last()
        assertEquals(35, aprilPage.weeks.flatten().size)
        assertEquals(LocalDate.of(2026, 3, 29), aprilPage.weeks.first().first().date)
        assertEquals(LocalDate.of(2026, 5, 2), aprilPage.weeks.last().last().date)
        assertEquals(1, aprilPage.weeks.flatten().first { it.date == LocalDate.of(2026, 4, 2) }.workoutCount)
        assertEquals(listOf(3L, 2L), aprilPage.workouts.map(HistorySummary::id))
    }

    private fun historySummary(
        id: Long,
        completedAtUtc: String,
        totalVolume: Double = 1000.0,
    ) = HistorySummary(
        id = id,
        title = "Workout $id",
        completedAtUtc = completedAtUtc,
        durationSeconds = 1800,
        totalVolume = totalVolume,
        exerciseCount = 4,
        exerciseNames = listOf("Squat", "Bench"),
    )
}
