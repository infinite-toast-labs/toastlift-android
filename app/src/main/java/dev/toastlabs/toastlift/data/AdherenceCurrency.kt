package dev.toastlabs.toastlift.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal const val ADHERENCE_CURRENCY_FLOOR = -12
internal const val ADHERENCE_CURRENCY_CEILING = 24

private const val ADHERENCE_SKIP_PENALTY = -3
private const val ADHERENCE_PARTIAL_COMPLETION_REWARD = 1
private const val ADHERENCE_SOLID_COMPLETION_REWARD = 2
private const val ADHERENCE_FULL_COMPLETION_REWARD = 3
private const val ADHERENCE_STREAK_BONUS = 1
private const val ADHERENCE_RECOVERY_BONUS = 1
private const val ADHERENCE_SOLID_COMPLETION_THRESHOLD = 0.6
private const val ADHERENCE_FULL_COMPLETION_THRESHOLD = 1.0
private const val ADHERENCE_STREAK_BONUS_THRESHOLD = 2

data class AdherenceCurrencySnapshot(
    val balance: Int,
    val floor: Int,
    val ceiling: Int,
    val displayValue: String,
    val statusLabel: String,
    val detail: String,
)

data class AdherenceCurrencyTrendPoint(
    val date: LocalDate,
    val balance: Int,
    val delta: Int,
    val completedSessions: Int,
    val skippedSessions: Int,
)

data class AdherenceCurrencyTrend(
    val snapshot: AdherenceCurrencySnapshot,
    val dailyPoints: List<AdherenceCurrencyTrendPoint>,
    val latestDelta: Int,
    val latestDate: LocalDate?,
    val weeklyDelta: Int,
    val monthlyDelta: Int,
    val bestBalance: Int,
    val worstBalance: Int,
    val undatedSignalCount: Int,
)

internal data class AdherenceSessionSignal(
    val sequenceNumber: Int,
    val status: SessionStatus,
    val plannedSets: Int,
    val completedSetCount: Int = 0,
    val occurredAtUtc: String? = null,
)

internal fun buildAdherenceCurrencySnapshot(signals: List<AdherenceSessionSignal>): AdherenceCurrencySnapshot {
    return buildAdherenceCurrencyLedger(signals).snapshot
}

internal fun buildAdherenceCurrencyTrend(
    signals: List<AdherenceSessionSignal>,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): AdherenceCurrencyTrend {
    val ledger = buildAdherenceCurrencyLedger(signals)
    val snapshot = ledger.snapshot
    val datedEntries = ledger.entries
        .mapNotNull { entry ->
            entry.occurredAtUtc?.let { occurredAtUtc ->
                runCatching { Instant.parse(occurredAtUtc) }.getOrNull()?.let { instant ->
                    DatedAdherenceLedgerEntry(
                        ledgerEntry = entry,
                        instant = instant,
                        date = instant.atZone(zoneId).toLocalDate(),
                    )
                }
            }
        }
        .sortedWith(compareBy<DatedAdherenceLedgerEntry> { it.instant }.thenBy { it.ledgerEntry.sequenceNumber })

    val startDate = today.minusDays(29)
    val entriesWithinWindow = datedEntries.filter { it.date >= startDate && it.date <= today }
    val entriesBeforeWindow = datedEntries.filter { it.date < startDate }
    val datedFinalBalance = datedEntries.lastOrNull()?.ledgerEntry?.balanceAfter ?: 0
    val balanceOffset = snapshot.balance - datedFinalBalance
    val groupedEntries = entriesWithinWindow.groupBy { it.date }
    var runningBalance = entriesBeforeWindow.lastOrNull()?.ledgerEntry?.balanceAfter?.plus(balanceOffset) ?: balanceOffset

    val dailyPoints = buildList {
        var currentDate = startDate
        while (!currentDate.isAfter(today)) {
            val dayEntries = groupedEntries[currentDate].orEmpty()
            if (dayEntries.isNotEmpty()) {
                runningBalance = dayEntries.last().ledgerEntry.balanceAfter + balanceOffset
            }
            add(
                AdherenceCurrencyTrendPoint(
                    date = currentDate,
                    balance = runningBalance,
                    delta = dayEntries.sumOf { it.ledgerEntry.delta },
                    completedSessions = dayEntries.count { it.ledgerEntry.status == SessionStatus.COMPLETED },
                    skippedSessions = dayEntries.count { it.ledgerEntry.status == SessionStatus.SKIPPED },
                ),
            )
            currentDate = currentDate.plusDays(1)
        }
    }

    val latestChangedPoint = dailyPoints.lastOrNull { it.delta != 0 }
    return AdherenceCurrencyTrend(
        snapshot = snapshot,
        dailyPoints = dailyPoints,
        latestDelta = latestChangedPoint?.delta ?: 0,
        latestDate = latestChangedPoint?.date,
        weeklyDelta = dailyPoints.takeLast(7).sumOf(AdherenceCurrencyTrendPoint::delta),
        monthlyDelta = dailyPoints.sumOf(AdherenceCurrencyTrendPoint::delta),
        bestBalance = dailyPoints.maxOfOrNull(AdherenceCurrencyTrendPoint::balance) ?: snapshot.balance,
        worstBalance = dailyPoints.minOfOrNull(AdherenceCurrencyTrendPoint::balance) ?: snapshot.balance,
        undatedSignalCount = ledger.entries.count { it.occurredAtUtc == null },
    )
}

private data class AdherenceLedgerEntry(
    val sequenceNumber: Int,
    val status: SessionStatus,
    val delta: Int,
    val balanceAfter: Int,
    val occurredAtUtc: String?,
)

private data class AdherenceLedger(
    val snapshot: AdherenceCurrencySnapshot,
    val entries: List<AdherenceLedgerEntry>,
)

private data class DatedAdherenceLedgerEntry(
    val ledgerEntry: AdherenceLedgerEntry,
    val instant: Instant,
    val date: LocalDate,
)

private fun buildAdherenceCurrencyLedger(signals: List<AdherenceSessionSignal>): AdherenceLedger {
    var balance = 0
    var consecutiveSolidCompletions = 0
    val entries = mutableListOf<AdherenceLedgerEntry>()

    signals
        .sortedBy { it.sequenceNumber }
        .forEach { signal ->
            when (signal.status) {
                SessionStatus.COMPLETED -> {
                    val completionRatio = plannedSessionCompletionRatio(
                        completedSetCount = signal.completedSetCount,
                        plannedSetCount = signal.plannedSets,
                    )
                    val reward = completedSessionReward(
                        currentBalance = balance,
                        completionRatio = completionRatio,
                        consecutiveSolidCompletionsBefore = consecutiveSolidCompletions,
                    )
                    balance = clampAdherenceBalance(balance + reward)
                    entries += AdherenceLedgerEntry(
                        sequenceNumber = signal.sequenceNumber,
                        status = signal.status,
                        delta = reward,
                        balanceAfter = balance,
                        occurredAtUtc = signal.occurredAtUtc,
                    )
                    consecutiveSolidCompletions = if (completionRatio >= ADHERENCE_SOLID_COMPLETION_THRESHOLD && reward > 0) {
                        consecutiveSolidCompletions + 1
                    } else {
                        0
                    }
                }

                SessionStatus.SKIPPED -> {
                    balance = clampAdherenceBalance(balance + ADHERENCE_SKIP_PENALTY)
                    entries += AdherenceLedgerEntry(
                        sequenceNumber = signal.sequenceNumber,
                        status = signal.status,
                        delta = ADHERENCE_SKIP_PENALTY,
                        balanceAfter = balance,
                        occurredAtUtc = signal.occurredAtUtc,
                    )
                    consecutiveSolidCompletions = 0
                }

                SessionStatus.MIGRATED,
                SessionStatus.UPCOMING,
                SessionStatus.IN_PROGRESS,
                -> Unit
            }
        }

    return AdherenceLedger(
        snapshot = AdherenceCurrencySnapshot(
            balance = balance,
            floor = ADHERENCE_CURRENCY_FLOOR,
            ceiling = ADHERENCE_CURRENCY_CEILING,
            displayValue = if (balance > 0) "+$balance" else balance.toString(),
            statusLabel = adherenceStatusLabel(balance),
            detail = adherenceDetail(balance),
        ),
        entries = entries,
    )
}

private fun completedSessionReward(
    currentBalance: Int,
    completionRatio: Double,
    consecutiveSolidCompletionsBefore: Int,
): Int {
    val baseReward = when {
        completionRatio >= ADHERENCE_FULL_COMPLETION_THRESHOLD -> ADHERENCE_FULL_COMPLETION_REWARD
        completionRatio >= ADHERENCE_SOLID_COMPLETION_THRESHOLD -> ADHERENCE_SOLID_COMPLETION_REWARD
        completionRatio > 0.0 -> ADHERENCE_PARTIAL_COMPLETION_REWARD
        else -> 0
    }
    if (baseReward == 0) return 0

    var reward = baseReward
    if (completionRatio >= ADHERENCE_SOLID_COMPLETION_THRESHOLD && currentBalance < 0) {
        reward += ADHERENCE_RECOVERY_BONUS
    }
    if (completionRatio >= ADHERENCE_SOLID_COMPLETION_THRESHOLD &&
        consecutiveSolidCompletionsBefore >= ADHERENCE_STREAK_BONUS_THRESHOLD
    ) {
        reward += ADHERENCE_STREAK_BONUS
    }
    return reward
}

private fun plannedSessionCompletionRatio(
    completedSetCount: Int,
    plannedSetCount: Int,
): Double {
    if (plannedSetCount <= 0) return 1.0
    return completedSetCount.toDouble() / plannedSetCount.toDouble()
}

private fun clampAdherenceBalance(balance: Int): Int {
    return balance.coerceIn(ADHERENCE_CURRENCY_FLOOR, ADHERENCE_CURRENCY_CEILING)
}

private fun adherenceStatusLabel(balance: Int): String = when {
    balance >= 12 -> "Banked"
    balance >= 0 -> "Steady"
    else -> "Rebuilding"
}

private fun adherenceDetail(balance: Int): String = when {
    balance >= 12 -> "Consistency buffer is stocked. Misses are still capped."
    balance >= 0 -> "Solid sessions bank flexibility before skips spend it."
    else -> "Recent misses spent the buffer. Penalties stop at -12 and solid sessions recover it faster."
}
