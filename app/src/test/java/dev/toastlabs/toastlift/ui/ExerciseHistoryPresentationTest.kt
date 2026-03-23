package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ExerciseHistoryDetail
import dev.toastlabs.toastlift.data.ExerciseHistoryEntry
import dev.toastlabs.toastlift.data.ExerciseHistorySet
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseHistoryPresentationTest {
    @Test
    fun exerciseHistorySummary_describesPrOnlyMode() {
        val detail = historyDetail(
            entries = listOf(historyEntry(hasPersonalRecord = true)),
            isPrOnlyFilterEnabled = true,
            totalEntries = 4,
            prEntryCount = 1,
        )

        assertEquals("Showing 1 PR session", exerciseHistorySummary(detail))
    }

    @Test
    fun exerciseHistoryEmptyState_usesPrSpecificCopyWhenFilterHidesResults() {
        val detail = historyDetail(
            entries = emptyList(),
            isPrOnlyFilterEnabled = true,
            totalEntries = 3,
            prEntryCount = 0,
        )

        assertEquals(
            ExerciseHistoryEmptyStateContent(
                title = "No PR entries yet",
                subtitle = "Turn off PRs only to view every logged session for this exercise.",
            ),
            exerciseHistoryEmptyState(detail),
        )
    }

    private fun historyDetail(
        entries: List<ExerciseHistoryEntry>,
        isPrOnlyFilterEnabled: Boolean,
        totalEntries: Int,
        prEntryCount: Int,
    ): ExerciseHistoryDetail {
        return ExerciseHistoryDetail(
            exerciseId = 9L,
            exerciseName = "Bench Press",
            entries = entries,
            isPrOnlyFilterEnabled = isPrOnlyFilterEnabled,
            totalEntries = totalEntries,
            prEntryCount = prEntryCount,
        )
    }

    private fun historyEntry(hasPersonalRecord: Boolean): ExerciseHistoryEntry {
        return ExerciseHistoryEntry(
            completedAtUtc = "2026-03-05T12:00:00Z",
            workoutTitle = "Heavy Day",
            targetReps = "5",
            estimatedOneRepMax = 200.0,
            totalVolume = 1500.0,
            bestWeight = 150.0,
            lastSetRepsInReserve = 1,
            lastSetRpe = 9.0,
            workingSets = listOf(
                ExerciseHistorySet(
                    setNumber = 1,
                    reps = 5,
                    weight = 150.0,
                    isRepPr = hasPersonalRecord,
                    isWeightPr = hasPersonalRecord,
                    isVolumePr = hasPersonalRecord,
                ),
            ),
            hasPersonalRecord = hasPersonalRecord,
        )
    }
}
