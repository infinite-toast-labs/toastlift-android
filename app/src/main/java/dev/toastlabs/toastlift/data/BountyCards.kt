package dev.toastlabs.toastlift.data

import java.time.Duration
import java.time.Instant

private const val BOUNTY_ELIGIBLE_REVEAL_PERCENT = 30
private const val BOUNTY_DRY_SPELL_MISS_LIMIT = 4

enum class BountyCardRarity(val storageKey: String, val label: String) {
    CHALK("chalk", "Chalk"),
    STEEL("steel", "Steel"),
    EMBER("ember", "Ember"),
    GOLD("gold", "Gold"),
    PRISM("prism", "Prism"),
    ;

    companion object {
        fun fromStorageKey(storageKey: String?): BountyCardRarity =
            entries.firstOrNull { it.storageKey == storageKey } ?: CHALK
    }
}

enum class BountyCardFamily(val storageKey: String, val label: String) {
    CLOSEOUT("closeout", "Closeout"),
    REST_WINDOW("rest_window", "Rest Window"),
    CONTINUITY("continuity", "Continuity"),
    CONSISTENCY("consistency", "Consistency"),
    HONESTY("honesty", "Honesty"),
    ;

    companion object {
        fun fromStorageKey(storageKey: String?): BountyCardFamily =
            entries.firstOrNull { it.storageKey == storageKey } ?: CONTINUITY
    }
}

enum class BountyResolutionScope(val storageKey: String, val label: String) {
    SET("set", "Set"),
    EXERCISE("exercise", "Exercise"),
    CHAIN("chain", "Chain"),
    REST_WINDOW("rest_window", "Rest Window"),
    ;

    companion object {
        fun fromStorageKey(storageKey: String?): BountyResolutionScope =
            entries.firstOrNull { it.storageKey == storageKey } ?: SET
    }
}

enum class BountyType(
    val storageKey: String,
    val title: String,
    val family: BountyCardFamily,
    val resolutionScope: BountyResolutionScope,
    val flavorText: String,
) {
    CLOSEOUT_STAMP(
        storageKey = "closeout_stamp",
        title = "Closeout Stamp",
        family = BountyCardFamily.CLOSEOUT,
        resolutionScope = BountyResolutionScope.EXERCISE,
        flavorText = "The last useful thing got signed and filed.",
    ),
    REST_SNIPER(
        storageKey = "rest_sniper",
        title = "The Good Minute",
        family = BountyCardFamily.REST_WINDOW,
        resolutionScope = BountyResolutionScope.REST_WINDOW,
        flavorText = "The clock opened the door and you walked through it.",
    ),
    NO_SKIP_LINE(
        storageKey = "no_skip_line",
        title = "No Skip Line",
        family = BountyCardFamily.CONTINUITY,
        resolutionScope = BountyResolutionScope.SET,
        flavorText = "One planned set stayed on the rails.",
    ),
    LOAD_LOCK(
        storageKey = "load_lock",
        title = "Weight Remembered",
        family = BountyCardFamily.CONSISTENCY,
        resolutionScope = BountyResolutionScope.SET,
        flavorText = "The bar kept its promise.",
    ),
    ;

    companion object {
        fun fromStorageKey(storageKey: String?): BountyType =
            entries.firstOrNull { it.storageKey == storageKey } ?: NO_SKIP_LINE
    }
}

data class ActiveWorkoutBounty(
    val bountyId: String,
    val type: BountyType,
    val title: String,
    val family: BountyCardFamily,
    val rarity: BountyCardRarity,
    val resolutionScope: BountyResolutionScope,
    val sessionStartedAtUtc: String,
    val exerciseId: Long,
    val exerciseName: String,
    val targetSetId: Long?,
    val targetSetNumber: Int?,
    val createdAtUtc: String,
    val guidance: String,
    val proofPrompt: String,
    val flavorText: String,
    val sourceCompletedAtUtc: String? = null,
    val lowerRestSeconds: Int? = null,
    val upperRestSeconds: Int? = null,
    val artSeed: String,
)

data class EarnedBountyCard(
    val cardId: Long = 0,
    val bountyId: String,
    val bountyType: BountyType,
    val title: String,
    val family: BountyCardFamily,
    val rarity: BountyCardRarity,
    val resolutionScope: BountyResolutionScope,
    val earnedAtUtc: String,
    val sessionStartedAtUtc: String,
    val workoutId: Long? = null,
    val exerciseId: Long,
    val exerciseName: String,
    val proofLine: String,
    val flavorText: String,
    val artSeed: String,
    val sourceSetNumber: Int?,
)

data class BountyEvaluationResult(
    val activeBounty: ActiveWorkoutBounty?,
    val earnedCard: EarnedBountyCard?,
    val eligibleMissCount: Int,
)

fun evaluateBountyAfterSetCompletion(
    afterSession: ActiveSession,
    exerciseIndex: Int,
    completedSetId: Long,
    activeBounty: ActiveWorkoutBounty?,
    eligibleMissCount: Int,
    earnedCardsThisSessionCount: Int,
    now: Instant = Instant.now(),
): BountyEvaluationResult {
    val afterExercise = afterSession.exercises.getOrNull(exerciseIndex)
        ?: return BountyEvaluationResult(activeBounty, null, eligibleMissCount)
    val completedSet = afterExercise.sets.firstOrNull { it.id == completedSetId && it.completed }
        ?: return BountyEvaluationResult(activeBounty, null, eligibleMissCount)

    activeBounty?.let { bounty ->
        val resolvedCard = resolveActiveBounty(
            bounty = bounty,
            exercise = afterExercise,
            completedSet = completedSet,
            now = now,
        )
        if (resolvedCard != null) {
            return BountyEvaluationResult(
                activeBounty = null,
                earnedCard = resolvedCard,
                eligibleMissCount = 0,
            )
        }
        if (bounty.exerciseId != afterExercise.exerciseId) {
            return BountyEvaluationResult(
                activeBounty = bounty,
                earnedCard = null,
                eligibleMissCount = eligibleMissCount,
            )
        }
        if (isBountyExpired(bounty, afterExercise)) {
            return BountyEvaluationResult(
                activeBounty = null,
                earnedCard = null,
                eligibleMissCount = 0,
            )
        }
        return BountyEvaluationResult(
            activeBounty = bounty,
            earnedCard = null,
            eligibleMissCount = eligibleMissCount,
        )
    }

    val plannedSets = afterSession.exercises.sumOf { it.sets.size }
    val sessionCap = if (plannedSets >= 17) 5 else 4
    if (earnedCardsThisSessionCount >= sessionCap) {
        return BountyEvaluationResult(null, null, eligibleMissCount)
    }

    val candidates = buildBountyCandidates(
        session = afterSession,
        exercise = afterExercise,
        completedSet = completedSet,
        now = now,
    )
    if (candidates.isEmpty()) {
        return BountyEvaluationResult(null, null, eligibleMissCount)
    }

    val drySpellGuarantee = eligibleMissCount >= BOUNTY_DRY_SPELL_MISS_LIMIT
    val roll = deterministicBucket(
        seed = "${afterSession.startedAtUtc}:${completedSet.id}:${afterExercise.exerciseId}:${completedSet.completedAtUtc}",
        modulo = 100,
    )
    if (!drySpellGuarantee && roll >= BOUNTY_ELIGIBLE_REVEAL_PERCENT) {
        return BountyEvaluationResult(null, null, eligibleMissCount + 1)
    }

    val selected = candidates[deterministicBucket("${afterSession.startedAtUtc}:${completedSet.id}:pick", candidates.size)]
    return BountyEvaluationResult(
        activeBounty = selected,
        earnedCard = null,
        eligibleMissCount = 0,
    )
}

private fun buildBountyCandidates(
    session: ActiveSession,
    exercise: SessionExercise,
    completedSet: SessionSet,
    now: Instant,
): List<ActiveWorkoutBounty> {
    val remainingSets = exercise.sets.filterNot(SessionSet::completed)
    val nextSet = remainingSets.firstOrNull()
    if (nextSet == null) return emptyList()

    return buildList {
        if (remainingSets.size == 1) {
            add(
                bountyFor(
                    type = BountyType.CLOSEOUT_STAMP,
                    rarity = if (session.exercises.all { it.exerciseId == exercise.exerciseId || it.sets.all(SessionSet::completed) }) {
                        BountyCardRarity.GOLD
                    } else {
                        BountyCardRarity.STEEL
                    },
                    session = session,
                    exercise = exercise,
                    targetSet = nextSet,
                    completedSet = completedSet,
                    now = now,
                    guidance = "Finish ${exercise.name}'s final set.",
                    proofPrompt = "Close the exercise cleanly.",
                ),
            )
        }

        if (remainingSets.size >= 1) {
            add(
                bountyFor(
                    type = BountyType.NO_SKIP_LINE,
                    rarity = BountyCardRarity.CHALK,
                    session = session,
                    exercise = exercise,
                    targetSet = nextSet,
                    completedSet = completedSet,
                    now = now,
                    guidance = "Log the next planned set.",
                    proofPrompt = "Keep this exercise moving.",
                ),
            )
        }

        val completedAt = completedSet.completedAtUtc
        if (!completedAt.isNullOrBlank() && exercise.restSeconds > 0) {
            val lower = (exercise.restSeconds * 0.75).toInt().coerceAtLeast(20)
            val upper = (exercise.restSeconds * 1.35).toInt().coerceAtLeast(lower + 10)
            add(
                bountyFor(
                    type = BountyType.REST_SNIPER,
                    rarity = BountyCardRarity.EMBER,
                    session = session,
                    exercise = exercise,
                    targetSet = nextSet,
                    completedSet = completedSet,
                    now = now,
                    guidance = "Start set ${nextSet.setNumber} after ${formatBountySeconds(lower)}-${formatBountySeconds(upper)} rest.",
                    proofPrompt = "Catch the rest window.",
                    sourceCompletedAtUtc = completedAt,
                    lowerRestSeconds = lower,
                    upperRestSeconds = upper,
                ),
            )
        }

        val completedWeight = completedSet.weight.trim()
        if (completedWeight.isNotBlank() && nextSet.weight.trim() == completedWeight) {
            add(
                bountyFor(
                    type = BountyType.LOAD_LOCK,
                    rarity = BountyCardRarity.STEEL,
                    session = session,
                    exercise = exercise,
                    targetSet = nextSet,
                    completedSet = completedSet,
                    now = now,
                    guidance = "Hold ${completedWeight} on set ${nextSet.setNumber}.",
                    proofPrompt = "Match the load without drama.",
                ),
            )
        }
    }
}

private fun bountyFor(
    type: BountyType,
    rarity: BountyCardRarity,
    session: ActiveSession,
    exercise: SessionExercise,
    targetSet: SessionSet,
    completedSet: SessionSet,
    now: Instant,
    guidance: String,
    proofPrompt: String,
    sourceCompletedAtUtc: String? = null,
    lowerRestSeconds: Int? = null,
    upperRestSeconds: Int? = null,
): ActiveWorkoutBounty {
    val seed = "${session.startedAtUtc}:${exercise.exerciseId}:${targetSet.id}:${type.storageKey}"
    return ActiveWorkoutBounty(
        bountyId = "bounty-${Math.floorMod(seed.hashCode(), Int.MAX_VALUE)}",
        type = type,
        title = type.title,
        family = type.family,
        rarity = rarity,
        resolutionScope = type.resolutionScope,
        sessionStartedAtUtc = session.startedAtUtc,
        exerciseId = exercise.exerciseId,
        exerciseName = exercise.name,
        targetSetId = targetSet.id,
        targetSetNumber = targetSet.setNumber,
        createdAtUtc = now.toString(),
        guidance = guidance,
        proofPrompt = proofPrompt,
        flavorText = type.flavorText,
        sourceCompletedAtUtc = sourceCompletedAtUtc,
        lowerRestSeconds = lowerRestSeconds,
        upperRestSeconds = upperRestSeconds,
        artSeed = "card-${Math.floorMod("${seed}:${completedSet.id}".hashCode(), Int.MAX_VALUE)}",
    )
}

private fun resolveActiveBounty(
    bounty: ActiveWorkoutBounty,
    exercise: SessionExercise,
    completedSet: SessionSet,
    now: Instant,
): EarnedBountyCard? {
    if (bounty.exerciseId != exercise.exerciseId) return null
    val targetSetId = bounty.targetSetId ?: return null
    if (completedSet.id != targetSetId) return null

    val proofLine = when (bounty.type) {
        BountyType.CLOSEOUT_STAMP -> {
            if (!exercise.sets.all(SessionSet::completed)) return null
            "Closed ${exercise.sets.count(SessionSet::completed)}/${exercise.sets.size} sets for ${exercise.name}."
        }
        BountyType.NO_SKIP_LINE -> "Logged set ${completedSet.setNumber} for ${exercise.name}."
        BountyType.LOAD_LOCK -> {
            val previousCompleted = exercise.sets
                .filter { it.completed && it.id != completedSet.id && it.weight.isNotBlank() }
                .maxByOrNull { it.completedAtUtc.orEmpty() }
            if (previousCompleted == null || previousCompleted.weight.trim() != completedSet.weight.trim()) return null
            "Held ${completedSet.weight.trim()} on set ${completedSet.setNumber}."
        }
        BountyType.REST_SNIPER -> {
            val sourceInstant = bounty.sourceCompletedAtUtc?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return null
            val completedInstant = completedSet.completedAtUtc?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: now
            val elapsedSeconds = Duration.between(sourceInstant, completedInstant).seconds.toInt().coerceAtLeast(0)
            val lower = bounty.lowerRestSeconds ?: return null
            val upper = bounty.upperRestSeconds ?: return null
            if (elapsedSeconds !in lower..upper) return null
            "Started at ${formatBountySeconds(elapsedSeconds)} rest."
        }
    }

    return EarnedBountyCard(
        bountyId = bounty.bountyId,
        bountyType = bounty.type,
        title = bounty.title,
        family = bounty.family,
        rarity = bounty.rarity,
        resolutionScope = bounty.resolutionScope,
        earnedAtUtc = completedSet.completedAtUtc ?: now.toString(),
        sessionStartedAtUtc = bounty.sessionStartedAtUtc,
        exerciseId = bounty.exerciseId,
        exerciseName = bounty.exerciseName,
        proofLine = proofLine,
        flavorText = bounty.flavorText,
        artSeed = bounty.artSeed,
        sourceSetNumber = completedSet.setNumber,
    )
}

private fun isBountyExpired(bounty: ActiveWorkoutBounty, exercise: SessionExercise): Boolean {
    val targetSetId = bounty.targetSetId ?: return true
    val targetSet = exercise.sets.firstOrNull { it.id == targetSetId } ?: return true
    return targetSet.completed
}

fun formatBountySeconds(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes > 0) {
        "$minutes:${remainder.toString().padStart(2, '0')}"
    } else {
        "${remainder}s"
    }
}

private fun deterministicBucket(seed: String, modulo: Int): Int {
    if (modulo <= 1) return 0
    var hash = 17
    seed.forEach { char -> hash = (hash * 31) + char.code }
    return Math.floorMod(hash, modulo)
}
