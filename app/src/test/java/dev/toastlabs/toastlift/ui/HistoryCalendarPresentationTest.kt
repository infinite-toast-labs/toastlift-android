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

    @Test
    fun buildHistoryDateSections_groupsWorkoutsByLocalDayWithMessageThreadLabels() {
        val sections = buildHistoryDateSections(
            history = listOf(
                historySummary(id = 1L, completedAtUtc = "2026-04-03T06:00:00Z"),
                historySummary(id = 2L, completedAtUtc = "2026-04-03T19:00:00Z"),
                historySummary(id = 3L, completedAtUtc = "2026-04-04T05:00:00Z"),
            ),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(listOf("Friday, Apr 3", "Saturday, Apr 4"), sections.map(HistoryDateSection::label))
        assertEquals(listOf(1L, 2L), sections.first().entries.map(HistorySummary::id))
        assertEquals(listOf(3L), sections.last().entries.map(HistorySummary::id))
    }

    @Test
    fun historyCalendarAndSections_useWorkoutStartDayForOvernightSessions() {
        val overnight = historySummary(
            id = 1L,
            startedAtUtc = "2026-03-31T23:00:00Z",
            completedAtUtc = "2026-04-01T01:00:00Z",
        )

        val sections = buildHistoryDateSections(listOf(overnight), zoneId = ZoneId.of("UTC"))
        val weekPages = buildHistoryCalendarWeekPages(
            history = listOf(overnight),
            zoneId = ZoneId.of("UTC"),
            today = LocalDate.of(2026, 4, 1),
        )
        val monthPages = buildHistoryCalendarMonthPages(
            history = listOf(overnight),
            zoneId = ZoneId.of("UTC"),
            today = LocalDate.of(2026, 4, 1),
        )

        assertEquals(listOf("Tuesday, Mar 31"), sections.map(HistoryDateSection::label))
        assertEquals(1, weekPages.first().days.first { it.date == LocalDate.of(2026, 3, 31) }.workoutCount)
        assertEquals(0, weekPages.first().days.first { it.date == LocalDate.of(2026, 4, 1) }.workoutCount)
        assertEquals(listOf(1L), monthPages.first().workouts.map(HistorySummary::id))
        assertEquals(emptyList<Long>(), monthPages.last().workouts.map(HistorySummary::id))
    }

    @Test
    fun formatHistoryEntryTime_usesStartedHourMinuteAmPmOnly() {
        val morning = historySummary(
            id = 1L,
            startedAtUtc = "2026-04-03T06:00:00Z",
            completedAtUtc = "2026-04-03T08:30:00Z",
        )
        val evening = historySummary(id = 2L, completedAtUtc = "2026-04-03T19:05:00Z")

        assertEquals("6:00 AM", formatHistoryEntryTime(morning, ZoneId.of("UTC")))
        assertEquals("7:05 PM", formatHistoryEntryTime(evening, ZoneId.of("UTC")))
    }

    @Test
    fun historyCalendarWorkoutsForDate_returnsAllWorkoutsStartedOnSelectedDay() {
        val workouts = listOf(
            historySummary(id = 1L, completedAtUtc = "2026-04-03T08:00:00Z", startedAtUtc = "2026-04-03T06:00:00Z", setCount = 8),
            historySummary(id = 2L, completedAtUtc = "2026-04-03T20:00:00Z", startedAtUtc = "2026-04-03T18:00:00Z", setCount = 10),
            historySummary(id = 3L, completedAtUtc = "2026-04-04T01:00:00Z", startedAtUtc = "2026-04-03T23:00:00Z", setCount = 6),
            historySummary(id = 4L, completedAtUtc = "2026-04-04T12:00:00Z", startedAtUtc = "2026-04-04T10:00:00Z", setCount = 4),
        )

        val selected = historyCalendarWorkoutsForDate(
            workouts = workouts,
            date = LocalDate.of(2026, 4, 3),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(listOf(1L, 2L, 3L), selected.map(HistorySummary::id))
        assertEquals(listOf(8, 10, 6), selected.map(HistorySummary::setCount))
    }

    @Test
    fun buildHistoryCalendarDailySummary_combinesSelectedDayWorkouts() {
        val summary = buildHistoryCalendarDailySummary(
            listOf(
                historySummary(id = 1L, completedAtUtc = "2026-04-03T08:00:00Z", totalVolume = 1200.0, setCount = 8),
                historySummary(id = 2L, completedAtUtc = "2026-04-03T20:00:00Z", totalVolume = 1800.0, setCount = 10),
            ),
        )

        assertEquals(2, summary.workoutCount)
        assertEquals(8, summary.exerciseCount)
        assertEquals(18, summary.setCount)
        assertEquals(3000.0, summary.totalVolume, 0.001)
    }

    private fun historySummary(
        id: Long,
        completedAtUtc: String,
        startedAtUtc: String = completedAtUtc,
        totalVolume: Double = 1000.0,
        setCount: Int = 12,
    ) = HistorySummary(
        id = id,
        title = "Workout $id",
        completedAtUtc = completedAtUtc,
        startedAtUtc = startedAtUtc,
        durationSeconds = 1800,
        totalVolume = totalVolume,
        exerciseCount = 4,
        setCount = setCount,
        exerciseNames = listOf("Squat", "Bench"),
    )
}
