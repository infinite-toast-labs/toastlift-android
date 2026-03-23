package com.fitlib.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun skippedSignal(sequence: Int): AdherenceSessionSignal {
        return AdherenceSessionSignal(
            sequenceNumber = sequence,
            status = SessionStatus.SKIPPED,
            plannedSets = 16,
        )
    }

    private fun completedSignal(
        sequence: Int,
        plannedSets: Int,
        completedSetCount: Int,
    ): AdherenceSessionSignal {
        return AdherenceSessionSignal(
            sequenceNumber = sequence,
            status = SessionStatus.COMPLETED,
            plannedSets = plannedSets,
            completedSetCount = completedSetCount,
        )
    }
}
