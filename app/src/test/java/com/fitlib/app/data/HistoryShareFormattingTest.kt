package com.fitlib.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryShareFormattingTest {
    @Test
    fun formattedTextShare_includesWorkoutSummaryAndSetLines() {
        val payload = shareDetail().toPendingWorkoutShare(HistoryShareFormat.FormattedText)

        assertEquals("text/plain", payload.mimeType)
        assertTrue(payload.contents.contains("Push Day"))
        assertTrue(payload.contents.contains("Completed: 2026-03-20 10:30:00 UTC"))
        assertTrue(payload.contents.contains("Set 1: 135 lb x 8 (target 8-10)"))
        assertTrue(payload.contents.contains("Set 2: not completed (target 8-10, rec 8 reps @ 135 lb)"))
        assertTrue(payload.contents.contains("Last set RPE: 9.5"))
    }

    @Test
    fun jsonShare_serializesStructuredWorkoutPayload() {
        val payload = shareDetail().toPendingWorkoutShare(HistoryShareFormat.Json)
        val json = JSONObject(payload.contents)

        assertEquals("Push Day", json.getString("title"))
        assertEquals(2L, json.getJSONArray("exercises").getJSONObject(0).getJSONArray("sets").length().toLong())
        assertEquals(
            "DIRECT_HISTORY",
            json.getJSONArray("exercises")
                .getJSONObject(0)
                .getJSONArray("sets")
                .getJSONObject(0)
                .getString("recommendation_source"),
        )
    }

    private fun shareDetail(): HistoryWorkoutShareDetail {
        return HistoryWorkoutShareDetail(
            id = 7L,
            title = "Push Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T09:45:00Z",
            completedAtUtc = "2026-03-20T10:30:00Z",
            durationSeconds = 2700,
            totalVolume = 2160.0,
            exerciseCount = 1,
            exercises = listOf(
                HistoryWorkoutShareExercise(
                    exerciseId = 101L,
                    name = "Bench Press",
                    targetReps = "8-10",
                    loggedSets = 1,
                    totalSets = 2,
                    totalVolume = 1080.0,
                    bestWeight = 135.0,
                    bestReps = 8,
                    lastSetRepsInReserve = 1,
                    lastSetRpe = 9.5,
                    sets = listOf(
                        HistoryWorkoutShareSet(
                            setNumber = 1,
                            targetReps = "8-10",
                            recommendedReps = 8,
                            recommendedWeight = 135.0,
                            actualReps = 8,
                            weight = 135.0,
                            isCompleted = true,
                            recommendationSource = RecommendationSource.DIRECT_HISTORY,
                            recommendationConfidence = 0.92,
                        ),
                        HistoryWorkoutShareSet(
                            setNumber = 2,
                            targetReps = "8-10",
                            recommendedReps = 8,
                            recommendedWeight = 135.0,
                            actualReps = null,
                            weight = null,
                            isCompleted = false,
                            recommendationSource = RecommendationSource.DIRECT_HISTORY,
                            recommendationConfidence = 0.92,
                        ),
                    ),
                ),
            ),
            abFlags = null,
        )
    }
}
