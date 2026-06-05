package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.AdherenceCurrencyTrendPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenBalanceChartPresentationTest {

    @Test
    fun tokenChartNearestPointIndexMapsTouchPositionToNearestPlottedDay() {
        assertEquals(-1, tokenChartNearestPointIndex(pointCount = 0, touchX = 40f, chartWidth = 120f))
        assertEquals(-1, tokenChartNearestPointIndex(pointCount = 3, touchX = 40f, chartWidth = 0f))
        assertEquals(0, tokenChartNearestPointIndex(pointCount = 1, touchX = 40f, chartWidth = 120f))

        assertEquals(0, tokenChartNearestPointIndex(pointCount = 7, touchX = -16f, chartWidth = 300f))
        assertEquals(0, tokenChartNearestPointIndex(pointCount = 7, touchX = 24f, chartWidth = 300f))
        assertEquals(3, tokenChartNearestPointIndex(pointCount = 7, touchX = 150f, chartWidth = 300f))
        assertEquals(6, tokenChartNearestPointIndex(pointCount = 7, touchX = 340f, chartWidth = 300f))
    }

    @Test
    fun tokenBalancePointActivityLabelSummarizesWalletMovementSources() {
        assertEquals(
            "1 workout • 2 skips • 1 freshness",
            tokenBalancePointActivityLabel(
                tokenPoint(
                    delta = -4,
                    completedSessions = 1,
                    skippedSessions = 2,
                    freshnessPenalties = 1,
                ),
            ),
        )
        assertEquals("Wallet adjustment", tokenBalancePointActivityLabel(tokenPoint(delta = 2)))
        assertEquals("No wallet movement", tokenBalancePointActivityLabel(tokenPoint(delta = 0)))
    }

    private fun tokenPoint(
        delta: Int,
        completedSessions: Int = 0,
        skippedSessions: Int = 0,
        freshnessPenalties: Int = 0,
    ) = AdherenceCurrencyTrendPoint(
        date = LocalDate.of(2026, 6, 5),
        balance = 8 + delta,
        delta = delta,
        completedSessions = completedSessions,
        skippedSessions = skippedSessions,
        freshnessPenalties = freshnessPenalties,
    )
}
