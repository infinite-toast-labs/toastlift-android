package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AdherenceCurrencyTest {

    @Test
    fun repeatedSkipsStopAtTheConfiguredFloor() {
        val snapshot = buildAdherenceCurrencySnapshot(
            signals = (1..10).map { index ->
                AdherenceSessionSignal(
                    sequenceNumber = index,
                    status = SessionStatus.SKIPPED,
                    plannedSets = 16,
                )
            },
        )

        assertEquals(ADHERENCE_CURRENCY_FLOOR, snapshot.balance)
        assertEquals("-12", snapshot.displayValue)
        assertTrue(snapshot.detail.contains("Penalties stop"))
    }

    @Test
    fun repeatedSolidSessionsAccumulateButRespectTheCeiling() {
        val snapshot = buildAdherenceCurrencySnapshot(
            signals = (1..10).map { index ->
                AdherenceSessionSignal(
                    sequenceNumber = index,
                    status = SessionStatus.COMPLETED,
                    plannedSets = 12,
                    completedSetCount = 12,
                )
            },
        )

        assertEquals(ADHERENCE_CURRENCY_CEILING, snapshot.balance)
        assertEquals("+24", snapshot.displayValue)
        assertEquals("Banked", snapshot.statusLabel)
    }

    @Test
    fun negativeBalanceRecoversFasterAfterSolidCompletion() {
        val snapshot = buildAdherenceCurrencySnapshot(
            signals = listOf(
                skippedSignal(sequence = 1),
                skippedSignal(sequence = 2),
                skippedSignal(sequence = 3),
                completedSignal(sequence = 4, plannedSets = 10, completedSetCount = 10),
            ),
        )

        assertEquals(-5, snapshot.balance)
    }

    @Test
    fun partialCompletionBreaksTheSolidCompletionStreak() {
        val snapshot = buildAdherenceCurrencySnapshot(
            signals = listOf(
                completedSignal(sequence = 1, plannedSets = 10, completedSetCount = 10),
                completedSignal(sequence = 2, plannedSets = 10, completedSetCount = 10),
                completedSignal(sequence = 3, plannedSets = 10, completedSetCount = 5),
                completedSignal(sequence = 4, plannedSets = 10, completedSetCount = 10),
            ),
        )

        assertEquals(10, snapshot.balance)
    }

    @Test
    fun trendUsesDatedSignalsAcrossTheSelectedWindow() {
        val trend = buildAdherenceCurrencyTrend(
            signals = listOf(
                completedSignal(
                    sequence = 1,
                    plannedSets = 10,
                    completedSetCount = 10,
                    occurredAtUtc = "2026-03-20T12:00:00Z",
                ),
                skippedSignal(
                    sequence = 2,
                    occurredAtUtc = "2026-03-22T12:00:00Z",
                ),
                completedSignal(
                    sequence = 3,
                    plannedSets = 10,
                    completedSetCount = 10,
                    occurredAtUtc = "2026-03-24T12:00:00Z",
                ),
            ),
            today = LocalDate.parse("2026-03-25"),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(3, trend.snapshot.balance)
        assertEquals(3, trend.weeklyDelta)
        assertEquals(3, trend.monthlyDelta)
        assertEquals(3, trend.latestDelta)
        assertEquals(LocalDate.parse("2026-03-24"), trend.latestDate)
        assertEquals(3, trend.dailyPoints.last().balance)
    }

    @Test
    fun trendOffsetsUndatedSignalsSoChartEndsAtCurrentBalance() {
        val trend = buildAdherenceCurrencyTrend(
            signals = listOf(
                skippedSignal(sequence = 1),
                completedSignal(
                    sequence = 2,
                    plannedSets = 10,
                    completedSetCount = 10,
                    occurredAtUtc = "2026-03-24T12:00:00Z",
                ),
            ),
            today = LocalDate.parse("2026-03-25"),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(1, trend.snapshot.balance)
        assertEquals(1, trend.undatedSignalCount)
        assertEquals(1, trend.dailyPoints.last().balance)
        assertEquals(4, trend.latestDelta)
    }

    private fun skippedSignal(
        sequence: Int,
        occurredAtUtc: String? = null,
    ): AdherenceSessionSignal {
        return AdherenceSessionSignal(
            sequenceNumber = sequence,
            status = SessionStatus.SKIPPED,
            plannedSets = 16,
            occurredAtUtc = occurredAtUtc,
        )
    }

    private fun completedSignal(
        sequence: Int,
        plannedSets: Int,
        completedSetCount: Int,
        occurredAtUtc: String? = null,
    ): AdherenceSessionSignal {
        return AdherenceSessionSignal(
            sequenceNumber = sequence,
            status = SessionStatus.COMPLETED,
            plannedSets = plannedSets,
            completedSetCount = completedSetCount,
            occurredAtUtc = occurredAtUtc,
        )
    }
}
