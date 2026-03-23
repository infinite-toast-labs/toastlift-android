package dev.toastlabs.toastlift.data

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

internal data class AdherenceSessionSignal(
    val sequenceNumber: Int,
    val status: SessionStatus,
    val plannedSets: Int,
    val completedSetCount: Int = 0,
)

internal fun buildAdherenceCurrencySnapshot(signals: List<AdherenceSessionSignal>): AdherenceCurrencySnapshot {
    var balance = 0
    var consecutiveSolidCompletions = 0

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
                    consecutiveSolidCompletions = if (completionRatio >= ADHERENCE_SOLID_COMPLETION_THRESHOLD && reward > 0) {
                        consecutiveSolidCompletions + 1
                    } else {
                        0
                    }
                }

                SessionStatus.SKIPPED -> {
                    balance = clampAdherenceBalance(balance + ADHERENCE_SKIP_PENALTY)
                    consecutiveSolidCompletions = 0
                }

                SessionStatus.MIGRATED,
                SessionStatus.UPCOMING,
                SessionStatus.IN_PROGRESS,
                -> Unit
            }
        }

    return AdherenceCurrencySnapshot(
        balance = balance,
        floor = ADHERENCE_CURRENCY_FLOOR,
        ceiling = ADHERENCE_CURRENCY_CEILING,
        displayValue = if (balance > 0) "+$balance" else balance.toString(),
        statusLabel = adherenceStatusLabel(balance),
        detail = adherenceDetail(balance),
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
