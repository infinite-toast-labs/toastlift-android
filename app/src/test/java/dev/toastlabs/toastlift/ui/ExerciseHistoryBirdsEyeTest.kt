package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ExerciseHistoryEntry
import dev.toastlabs.toastlift.data.ExerciseHistorySet
import dev.toastlabs.toastlift.data.ExerciseSummary
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ExerciseHistoryBirdsEyeTest {
    @Test
    fun viewModeOrder_placesBirdsEyeLeftOfDetails() {
        assertEquals(
            listOf(ExerciseHistoryViewMode.BirdsEye, ExerciseHistoryViewMode.Details),
            ExerciseHistoryViewMode.entries,
        )
    }

    @Test
    fun buildRows_ordersSetsChronologicallyAndComputesDoubleProgressionDeltas() {
        val entries = listOf(
            entry(
                completedAtUtc = "2026-04-10T12:00:00Z",
                hasPr = true,
                sets = listOf(
                    set(3, reps = 8, weight = 80.0),
                    set(2, reps = 9, weight = 80.0),
                    set(1, reps = 10, weight = 80.0),
                ),
            ),
            entry(
                completedAtUtc = "2026-04-06T12:00:00Z",
                hasPr = false,
                sets = listOf(
                    set(3, reps = 9, weight = 75.0),
                    set(2, reps = 10, weight = 75.0),
                    set(1, reps = 10, weight = 75.0),
                ),
            ),
            entry(
                completedAtUtc = "2026-04-01T12:00:00Z",
                hasPr = false,
                sets = listOf(
                    set(3, reps = 8, weight = 75.0),
                    set(2, reps = 10, weight = 75.0),
                    set(1, reps = 10, weight = 75.0),
                ),
            ),
        )

        val rowsNewestFirst = buildExerciseHistoryBirdsEyeRows(
            entries = entries,
            sortOrder = BirdsEyeSortOrder.NewestFirst,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(3, rowsNewestFirst.size)

        val newest = rowsNewestFirst[0]
        assertEquals(3, newest.sessionNumber)
        assertEquals("Apr 10", newest.dateLabel)
        assertEquals("80 lb", newest.weightLabel)
        assertEquals("10 / 9 / 8", newest.repsLabel)
        assertNull(newest.perSetWeightsLabel)
        assertEquals("+5 lb", newest.deltaLabel)
        assertEquals(OverloadDeltaKind.WeightUp, newest.deltaKind)
        assertTrue(newest.isWeightJump)
        assertTrue(newest.hasPersonalRecord)

        val middle = rowsNewestFirst[1]
        assertEquals(2, middle.sessionNumber)
        assertEquals("75 lb", middle.weightLabel)
        assertEquals("10 / 10 / 9", middle.repsLabel)
        assertEquals("+1 rep", middle.deltaLabel)
        assertEquals(OverloadDeltaKind.RepsUp, middle.deltaKind)
        assertFalse(middle.isWeightJump)

        val oldest = rowsNewestFirst[2]
        assertEquals(1, oldest.sessionNumber)
        assertEquals("75 lb", oldest.weightLabel)
        assertEquals("10 / 10 / 8", oldest.repsLabel)
        assertNull(oldest.deltaLabel)
        assertEquals(OverloadDeltaKind.Baseline, oldest.deltaKind)

        // Default sortOrder is OldestFirst (ascending)
        val rowsDefault = buildExerciseHistoryBirdsEyeRows(
            entries = entries,
            zoneId = ZoneOffset.UTC,
        )
        assertEquals(listOf(1, 2, 3), rowsDefault.map { it.sessionNumber })
        assertEquals(listOf(null, "+1 rep", "+5 lb"), rowsDefault.map { it.deltaLabel })
    }

    @Test
    fun paginateRows_sixWorkoutsAsc_putsLastFiveOnDefaultLastPageAndOldestOnFirstPage() {
        val entries = (6 downTo 1).map { day ->
            entry(
                completedAtUtc = "2026-04-0${day}T12:00:00Z",
                hasPr = false,
                sets = listOf(set(1, reps = 10, weight = 70.0 + day)),
            )
        }
        val rowsAsc = buildExerciseHistoryBirdsEyeRows(
            entries = entries,
            sortOrder = BirdsEyeSortOrder.OldestFirst,
            zoneId = ZoneOffset.UTC,
        )

        val pagination = paginateBirdsEyeRows(rowsAsc, BirdsEyeSortOrder.OldestFirst)

        assertEquals(2, pagination.pages.size)
        assertEquals(1, pagination.defaultPageIndex)
        assertEquals(listOf(1), pagination.pages[0].map { it.sessionNumber })
        assertEquals(listOf(2, 3, 4, 5, 6), pagination.pages[1].map { it.sessionNumber })
    }

    @Test
    fun paginateRows_twelveWorkoutsAsc_chunksFromNewestEndAndOpensOnLastPage() {
        val entries = (12 downTo 1).map { day ->
            val dayStr = day.toString().padStart(2, '0')
            entry(
                completedAtUtc = "2026-04-${dayStr}T12:00:00Z",
                hasPr = false,
                sets = listOf(set(1, reps = 10, weight = 70.0)),
            )
        }
        val rowsAsc = buildExerciseHistoryBirdsEyeRows(
            entries = entries,
            sortOrder = BirdsEyeSortOrder.OldestFirst,
            zoneId = ZoneOffset.UTC,
        )

        val pagination = paginateBirdsEyeRows(rowsAsc, BirdsEyeSortOrder.OldestFirst)

        assertEquals(3, pagination.pages.size)
        assertEquals(2, pagination.defaultPageIndex)
        assertEquals(listOf(1, 2), pagination.pages[0].map { it.sessionNumber })
        assertEquals(listOf(3, 4, 5, 6, 7), pagination.pages[1].map { it.sessionNumber })
        assertEquals(listOf(8, 9, 10, 11, 12), pagination.pages[2].map { it.sessionNumber })
    }

    @Test
    fun paginateRows_sixWorkoutsDesc_putsMostRecentFiveOnPageZero() {
        val entries = (6 downTo 1).map { day ->
            entry(
                completedAtUtc = "2026-04-0${day}T12:00:00Z",
                hasPr = false,
                sets = listOf(set(1, reps = 10, weight = 70.0)),
            )
        }
        val rowsDesc = buildExerciseHistoryBirdsEyeRows(
            entries = entries,
            sortOrder = BirdsEyeSortOrder.NewestFirst,
            zoneId = ZoneOffset.UTC,
        )

        val pagination = paginateBirdsEyeRows(rowsDesc, BirdsEyeSortOrder.NewestFirst)

        assertEquals(2, pagination.pages.size)
        assertEquals(0, pagination.defaultPageIndex)
        assertEquals(listOf(6, 5, 4, 3, 2), pagination.pages[0].map { it.sessionNumber })
        assertEquals(listOf(1), pagination.pages[1].map { it.sessionNumber })
    }

    @Test
    fun buildRows_handlesMixedPyramidWeightsAndBodyweightAndMultiYearDates() {
        val entries = listOf(
            entry(
                completedAtUtc = "2026-01-15T12:00:00Z",
                hasPr = false,
                sets = listOf(
                    set(3, reps = 8, weight = 80.0),
                    set(2, reps = 9, weight = 75.0),
                    set(1, reps = 10, weight = 70.0),
                ),
            ),
            entry(
                completedAtUtc = "2025-12-20T12:00:00Z",
                hasPr = false,
                sets = listOf(
                    set(2, reps = 12, weight = null),
                    set(1, reps = 15, weight = 0.0),
                ),
            ),
        )

        val rows = buildExerciseHistoryBirdsEyeRows(
            entries = entries,
            sortOrder = BirdsEyeSortOrder.OldestFirst,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals("Dec 20 \u002725", rows[0].dateLabel)
        assertEquals("BW", rows[0].weightLabel)
        assertEquals("15 / 12", rows[0].repsLabel)
        assertNull(rows[0].perSetWeightsLabel)

        assertEquals("Jan 15 \u002726", rows[1].dateLabel)
        assertEquals("70\u201380 lb", rows[1].weightLabel)
        assertEquals("10 / 9 / 8", rows[1].repsLabel)
        assertEquals("70 / 75 / 80 lb", rows[1].perSetWeightsLabel)
        assertEquals("+80 lb", rows[1].deltaLabel)
    }

    @Test
    fun buildSummary_computesLatestBestAndNetProgression() {
        val entries = listOf(
            entry(
                completedAtUtc = "2026-04-10T12:00:00Z",
                hasPr = true,
                sets = listOf(
                    set(2, reps = 9, weight = 82.5),
                    set(1, reps = 10, weight = 82.5),
                ),
            ),
            entry(
                completedAtUtc = "2026-04-01T12:00:00Z",
                hasPr = false,
                sets = listOf(
                    set(2, reps = 10, weight = 75.0),
                    set(1, reps = 10, weight = 75.0),
                ),
            ),
        )

        val summary = buildExerciseHistoryBirdsEyeSummary(entries, zoneId = ZoneId.of("UTC"))
        assertEquals("Apr 10", summary?.lastWorkoutDateLabel)
        assertEquals("82.5 lb \u00D7 10", summary?.topSetLabel)
        assertEquals("2 sets \u00B7 82.5 lb", summary?.setsOverviewLabel)
        assertEquals("+7.5 lb", summary?.comparisonDeltaLabel)
        assertEquals("in top weight since Apr 1", summary?.comparisonContextLabel)
        assertTrue(summary?.comparisonPositive == true)
    }

    @Test
    fun `representative debug fixture exercises all birds-eye states and pagination`() {
        val exercise = ExerciseSummary(
            id = 42L,
            name = "Bench Press",
            difficulty = "Intermediate",
            bodyRegion = "Upper Body",
            targetMuscleGroup = "Chest",
            equipment = "Barbell",
            secondaryEquipment = null,
            mechanics = "Compound",
            favorite = false,
        )
        val detail = buildRepresentativeDebugExerciseHistoryDetail(exercise)
        assertEquals(8, detail.totalEntries)
        assertEquals(4, detail.prEntryCount)

        val rows = buildExerciseHistoryBirdsEyeRows(
            entries = detail.entries,
            sortOrder = BirdsEyeSortOrder.OldestFirst,
            zoneId = ZoneId.of("UTC"),
        )
        assertEquals(8, rows.size)

        // All OverloadDeltaKind states covered
        val kinds = rows.map { it.deltaKind }.toSet()
        assertEquals(OverloadDeltaKind.entries.toSet(), kinds)

        // Cross-year dates formatted with year suffix
        assertTrue(rows.first().dateLabel.contains("'25"))
        assertTrue(rows.last().dateLabel.contains("'26"))

        // Edge cases: BW-only, mixed BW-weighted, fractional weight, missing rep, 4-set pyramid
        assertEquals("BW", rows[0].weightLabel)
        assertEquals("BW\u201325 lb", rows[1].weightLabel)
        assertEquals("BW / 25 / 25 lb", rows[1].perSetWeightsLabel)
        assertEquals("137.5 lb", rows[3].weightLabel)
        assertEquals(null, rows[0].deltaColumn)
        assertEquals("+2.5 lb", rows[3].deltaLabel)
        assertEquals(BirdsEyeDeltaColumn.Weight, rows[3].deltaColumn)
        assertEquals("+3 reps", rows[4].deltaLabel)
        assertEquals(BirdsEyeDeltaColumn.Reps, rows[4].deltaColumn)
        assertEquals("=", rows[5].deltaLabel)
        assertEquals(BirdsEyeDeltaColumn.Reps, rows[5].deltaColumn)
        assertEquals("-7.5 lb", rows[6].deltaLabel)
        assertEquals(BirdsEyeDeltaColumn.Weight, rows[6].deltaColumn)
        assertEquals(BirdsEyeDeltaColumn.Weight, rows[7].deltaColumn)
        assertEquals("10 / 9 / --", rows[6].repsLabel)
        assertEquals("135\u2013155 lb", rows[7].weightLabel)
        assertEquals("135 / 145 / 155 / 155 lb", rows[7].perSetWeightsLabel)
        assertEquals("10 / 9 / 8 / 7", rows[7].repsLabel)

        // Pagination: 2 pages, default page is page 2 (5 newest rows)
        val pagination = paginateBirdsEyeRows(rows, BirdsEyeSortOrder.OldestFirst)
        assertEquals(2, pagination.pages.size)
        assertEquals(1, pagination.defaultPageIndex)
        assertEquals(3, pagination.pages[0].size)
        assertEquals(5, pagination.pages[1].size)

        val summary = buildExerciseHistoryBirdsEyeSummary(detail.entries, zoneId = ZoneId.of("UTC"))
        assertEquals("Nov 22", summary?.lastWorkoutDateLabel)
        assertEquals("155 lb \u00D7 8", summary?.topSetLabel)
        assertEquals("4 sets \u00B7 135\u2013155 lb", summary?.setsOverviewLabel)
        assertEquals("+25 lb", summary?.comparisonDeltaLabel)
        assertEquals("in top weight since Nov 15", summary?.comparisonContextLabel)
        assertTrue(summary?.comparisonPositive == true)
    }

    private fun entry(
        completedAtUtc: String,
        hasPr: Boolean,
        sets: List<ExerciseHistorySet>,
    ): ExerciseHistoryEntry = ExerciseHistoryEntry(
        completedAtUtc = completedAtUtc,
        workoutTitle = "Session",
        targetReps = "8-12",
        estimatedOneRepMax = 100.0,
        totalVolume = sets.sumOf { (it.reps ?: 0) * (it.weight ?: 0.0) },
        bestWeight = sets.maxOfOrNull { it.weight ?: 0.0 } ?: 0.0,
        lastSetRepsInReserve = 2,
        lastSetRpe = 8.0,
        workingSets = sets,
        hasPersonalRecord = hasPr,
    )

    private fun set(setNumber: Int, reps: Int?, weight: Double?): ExerciseHistorySet =
        ExerciseHistorySet(
            setNumber = setNumber,
            reps = reps,
            weight = weight,
            isRepPr = false,
            isWeightPr = false,
            isVolumePr = false,
        )
}
