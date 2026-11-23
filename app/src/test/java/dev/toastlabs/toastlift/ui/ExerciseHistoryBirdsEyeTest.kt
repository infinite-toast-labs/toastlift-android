package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ExerciseHistoryEntry
import dev.toastlabs.toastlift.data.ExerciseHistorySet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ExerciseHistoryBirdsEyeTest {
    @Test
    fun buildRows_ordersSetsChronologicallyAndComputesDoubleProgressionDeltas() {
        // Entries are newest-first (as returned by ExerciseHistoryDetail),
        // and workingSets are stored last-set-first (as returned by buildExerciseHistoryDetail).
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

        // Newest row (#3): weight jump from 75 -> 80 lb, sets in chronological order 10 / 9 / 8
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

        // Middle row (#2): same weight 75 lb, +1 rep (28 -> 29)
        val middle = rowsNewestFirst[1]
        assertEquals(2, middle.sessionNumber)
        assertEquals("75 lb", middle.weightLabel)
        assertEquals("10 / 10 / 9", middle.repsLabel)
        assertEquals("+1 rep", middle.deltaLabel)
        assertEquals(OverloadDeltaKind.RepsUp, middle.deltaKind)
        assertFalse(middle.isWeightJump)

        // Oldest row (#1): baseline, no delta
        val oldest = rowsNewestFirst[2]
        assertEquals(1, oldest.sessionNumber)
        assertEquals("75 lb", oldest.weightLabel)
        assertEquals("10 / 10 / 8", oldest.repsLabel)
        assertNull(oldest.deltaLabel)
        assertEquals(OverloadDeltaKind.Baseline, oldest.deltaKind)

        // OldestFirst reverses row display order while keeping identical session numbers and deltas
        val rowsOldestFirst = buildExerciseHistoryBirdsEyeRows(
            entries = entries,
            sortOrder = BirdsEyeSortOrder.OldestFirst,
            zoneId = ZoneOffset.UTC,
        )
        assertEquals(listOf(1, 2, 3), rowsOldestFirst.map { it.sessionNumber })
        assertEquals(listOf(null, "+1 rep", "+5 lb"), rowsOldestFirst.map { it.deltaLabel })
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

        val summary = buildExerciseHistoryBirdsEyeSummary(entries)
        assertEquals("82.5 lb \u00B7 10/9", summary?.latestLabel)
        assertEquals("82.5 lb", summary?.bestWeightLabel)
        assertEquals("+7.5 lb", summary?.netChangeLabel)
        assertTrue(summary?.netChangePositive == true)
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
