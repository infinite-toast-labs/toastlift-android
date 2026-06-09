package dev.toastlabs.toastlift.data

import java.time.Instant
import kotlin.math.abs

private const val EXERCISE_FAMILY_SECTION_LIMIT = 5

enum class ExerciseFamilySectionKind {
    Progressions,
    Regressions,
    SameMuscleDifferentSetup,
    SameSetupDifferentTarget,
    MovementCousins,
    FamiliarFavorites,
}

data class ExerciseFamily(
    val anchor: ExerciseDetail,
    val sections: List<ExerciseFamilySection>,
    val relatedExerciseCount: Int,
    val generatedAtUtc: Instant = Instant.now(),
)

data class ExerciseFamilySection(
    val kind: ExerciseFamilySectionKind,
    val title: String,
    val subtitle: String,
    val candidates: List<ExerciseFamilyCandidate>,
)

data class ExerciseFamilyCandidate(
    val exercise: ExerciseSummary,
    val relationshipLabel: String,
    val reason: String,
    val sharedSignals: List<String>,
    val score: Double,
)

internal data class ExerciseFamilyProfile(
    val summary: ExerciseSummary,
    val primeMover: String?,
    val secondaryMuscle: String?,
    val tertiaryMuscle: String?,
    val posture: String?,
    val laterality: String?,
    val classification: String?,
    val movementPatterns: List<String>,
    val planesOfMotion: List<String>,
)

internal fun buildExerciseFamily(
    anchor: ExerciseDetail,
    candidates: List<ExerciseFamilyProfile>,
    generatedAtUtc: Instant = Instant.now(),
): ExerciseFamily {
    val anchorProfile = anchor.toExerciseFamilyProfile()
    val eligible = candidates
        .filter { it.summary.id != anchor.summary.id }
        .filterNot { it.summary.hidden || it.summary.banned }
        .filter { familyBaseScore(anchorProfile, it) > 0.0 }
    val usedExerciseIds = mutableSetOf<Long>()
    val sections = buildList {
        addSection(
            kind = ExerciseFamilySectionKind.Progressions,
            title = "Level up",
            subtitle = "Harder siblings that keep the same training lane.",
            anchor = anchorProfile,
            candidates = eligible,
            usedExerciseIds = usedExerciseIds,
            predicate = { isProgression(anchorProfile, it) },
        )
        addSection(
            kind = ExerciseFamilySectionKind.Regressions,
            title = "Scale it down",
            subtitle = "Lower-friction versions for warmups, tired days, or cleaner reps.",
            anchor = anchorProfile,
            candidates = eligible,
            usedExerciseIds = usedExerciseIds,
            predicate = { isRegression(anchorProfile, it) },
        )
        addSection(
            kind = ExerciseFamilySectionKind.SameMuscleDifferentSetup,
            title = "Same muscle, new setup",
            subtitle = "Change the equipment or posture without changing the main target.",
            anchor = anchorProfile,
            candidates = eligible,
            usedExerciseIds = usedExerciseIds,
            predicate = { samePrimaryMuscle(anchorProfile, it) && !sameEquipment(anchorProfile, it) },
        )
        addSection(
            kind = ExerciseFamilySectionKind.SameSetupDifferentTarget,
            title = "Same setup, different target",
            subtitle = "Keep familiar equipment in hand and point it somewhere else.",
            anchor = anchorProfile,
            candidates = eligible,
            usedExerciseIds = usedExerciseIds,
            predicate = { sameEquipment(anchorProfile, it) && !samePrimaryMuscle(anchorProfile, it) },
        )
        addSection(
            kind = ExerciseFamilySectionKind.MovementCousins,
            title = "Movement cousins",
            subtitle = "Similar patterns that branch into a different feel or muscle bias.",
            anchor = anchorProfile,
            candidates = eligible,
            usedExerciseIds = usedExerciseIds,
            predicate = { sharedMovementPatterns(anchorProfile, it).isNotEmpty() },
        )
        addSection(
            kind = ExerciseFamilySectionKind.FamiliarFavorites,
            title = "Already in your orbit",
            subtitle = "Logged or saved relatives worth remembering before you wander too far.",
            anchor = anchorProfile,
            candidates = eligible,
            usedExerciseIds = usedExerciseIds,
            predicate = { it.summary.favorite || it.summary.loggedSessionCount > 0 },
        )
    }
    return ExerciseFamily(
        anchor = anchor,
        sections = sections,
        relatedExerciseCount = eligible.size,
        generatedAtUtc = generatedAtUtc,
    )
}

private fun MutableList<ExerciseFamilySection>.addSection(
    kind: ExerciseFamilySectionKind,
    title: String,
    subtitle: String,
    anchor: ExerciseFamilyProfile,
    candidates: List<ExerciseFamilyProfile>,
    usedExerciseIds: MutableSet<Long>,
    predicate: (ExerciseFamilyProfile) -> Boolean,
) {
    val picked = candidates
        .asSequence()
        .filter(predicate)
        .filterNot { it.summary.id in usedExerciseIds }
        .map { profile ->
            val score = familySectionScore(anchor, profile, kind)
            profile to score
        }
        .filter { it.second > 0.0 }
        .sortedWith(
            compareByDescending<Pair<ExerciseFamilyProfile, Double>> { it.second }
                .thenByDescending { it.first.summary.favorite }
                .thenByDescending { it.first.summary.loggedSessionCount }
                .thenBy { it.first.summary.name },
        )
        .take(EXERCISE_FAMILY_SECTION_LIMIT)
        .map { (profile, score) -> profile.toFamilyCandidate(anchor, kind, score) }
        .toList()
    if (picked.isEmpty()) return
    picked.forEach { usedExerciseIds += it.exercise.id }
    add(
        ExerciseFamilySection(
            kind = kind,
            title = title,
            subtitle = subtitle,
            candidates = picked,
        ),
    )
}

private fun ExerciseDetail.toExerciseFamilyProfile(): ExerciseFamilyProfile =
    ExerciseFamilyProfile(
        summary = summary,
        primeMover = primeMover,
        secondaryMuscle = secondaryMuscle,
        tertiaryMuscle = tertiaryMuscle,
        posture = posture,
        laterality = laterality,
        classification = classification,
        movementPatterns = movementPatterns,
        planesOfMotion = planesOfMotion,
    )

private fun ExerciseFamilyProfile.toFamilyCandidate(
    anchor: ExerciseFamilyProfile,
    kind: ExerciseFamilySectionKind,
    score: Double,
): ExerciseFamilyCandidate {
    return ExerciseFamilyCandidate(
        exercise = summary,
        relationshipLabel = relationshipLabel(kind),
        reason = familyReason(anchor, this, kind),
        sharedSignals = sharedFamilySignals(anchor, this),
        score = score,
    )
}

private fun ExerciseFamilyProfile.relationshipLabel(kind: ExerciseFamilySectionKind): String {
    return when (kind) {
        ExerciseFamilySectionKind.Progressions -> "Harder sibling"
        ExerciseFamilySectionKind.Regressions -> "Easier sibling"
        ExerciseFamilySectionKind.SameMuscleDifferentSetup -> "Same muscle"
        ExerciseFamilySectionKind.SameSetupDifferentTarget -> "Same setup"
        ExerciseFamilySectionKind.MovementCousins -> "Movement cousin"
        ExerciseFamilySectionKind.FamiliarFavorites -> if (summary.favorite) "Saved relative" else "Logged relative"
    }
}

private fun familyReason(
    anchor: ExerciseFamilyProfile,
    candidate: ExerciseFamilyProfile,
    kind: ExerciseFamilySectionKind,
): String {
    val anchorName = anchor.summary.name
    val target = candidate.summary.targetMuscleGroup
    return when (kind) {
        ExerciseFamilySectionKind.Progressions ->
            "${candidate.summary.name} keeps the ${anchor.summary.targetMuscleGroup} lane but asks for a bigger setup or skill step than $anchorName."
        ExerciseFamilySectionKind.Regressions ->
            "${candidate.summary.name} stays close to $anchorName while lowering the setup cost or difficulty."
        ExerciseFamilySectionKind.SameMuscleDifferentSetup ->
            "${candidate.summary.name} still points at $target, but swaps in ${candidate.summary.equipment} for a different feel."
        ExerciseFamilySectionKind.SameSetupDifferentTarget ->
            "${candidate.summary.name} keeps ${candidate.summary.equipment} familiar while moving the focus toward $target."
        ExerciseFamilySectionKind.MovementCousins -> {
            val pattern = sharedMovementPatterns(anchor, candidate).firstOrNull() ?: "movement pattern"
            "${candidate.summary.name} shares the $pattern pattern, so it feels related without being the same exercise."
        }
        ExerciseFamilySectionKind.FamiliarFavorites ->
            "${candidate.summary.name} is already in your history, making it a familiar branch from $anchorName."
    }
}

private fun sharedFamilySignals(
    anchor: ExerciseFamilyProfile,
    candidate: ExerciseFamilyProfile,
): List<String> {
    return buildList {
        if (sameText(anchor.summary.targetMuscleGroup, candidate.summary.targetMuscleGroup)) {
            add("Target: ${candidate.summary.targetMuscleGroup}")
        }
        val sharedMuscles = anchor.muscles().intersect(candidate.muscles().toSet()).take(2)
        sharedMuscles.forEach { add("Muscle: $it") }
        if (sameEquipment(anchor, candidate)) {
            add("Equipment: ${candidate.summary.equipment}")
        }
        sharedMovementPatterns(anchor, candidate).take(2).forEach { add("Pattern: $it") }
        if (candidate.summary.loggedSessionCount > 0) {
            add("${candidate.summary.loggedSessionCount} logged session${if (candidate.summary.loggedSessionCount == 1) "" else "s"}")
        }
    }.distinct().take(4)
}

private fun familySectionScore(
    anchor: ExerciseFamilyProfile,
    candidate: ExerciseFamilyProfile,
    kind: ExerciseFamilySectionKind,
): Double {
    val base = familyBaseScore(anchor, candidate)
    val historySignal = when {
        kind == ExerciseFamilySectionKind.FamiliarFavorites && candidate.summary.favorite -> 1.7
        kind == ExerciseFamilySectionKind.FamiliarFavorites && candidate.summary.loggedSessionCount > 0 -> 1.2
        candidate.summary.loggedSessionCount == 0 -> 0.7
        else -> 0.25
    }
    val preference = when (candidate.summary.recommendationBias) {
        RecommendationBias.MoreOften -> 0.45
        RecommendationBias.LessOften -> -1.1
        RecommendationBias.Neutral -> 0.0
    }
    val sectionFit = when (kind) {
        ExerciseFamilySectionKind.Progressions,
        ExerciseFamilySectionKind.Regressions -> difficultySeparation(anchor, candidate) * 0.7
        ExerciseFamilySectionKind.SameMuscleDifferentSetup -> if (!sameEquipment(anchor, candidate)) 1.1 else 0.0
        ExerciseFamilySectionKind.SameSetupDifferentTarget -> if (sameEquipment(anchor, candidate)) 1.0 else 0.0
        ExerciseFamilySectionKind.MovementCousins -> sharedMovementPatterns(anchor, candidate).size * 0.85
        ExerciseFamilySectionKind.FamiliarFavorites -> historySignal
    }
    return base + sectionFit + historySignal + preference
}

private fun familyBaseScore(anchor: ExerciseFamilyProfile, candidate: ExerciseFamilyProfile): Double {
    if (anchor.summary.id == candidate.summary.id) return 0.0
    var coreScore = 0.0
    if (sameText(anchor.summary.targetMuscleGroup, candidate.summary.targetMuscleGroup)) coreScore += 2.2
    if (sameText(anchor.primeMover, candidate.primeMover)) coreScore += 2.0
    coreScore += anchor.muscles().intersect(candidate.muscles().toSet()).size * 0.8
    coreScore += sharedMovementPatterns(anchor, candidate).size * 1.25
    if (sameEquipment(anchor, candidate)) coreScore += 0.7
    if (coreScore == 0.0) return 0.0

    var score = coreScore
    if (sameText(anchor.summary.bodyRegion, candidate.summary.bodyRegion)) score += 0.5
    score += anchor.planesOfMotion.normalizedSet().intersect(candidate.planesOfMotion.normalizedSet()).size * 0.35
    if (sameText(anchor.summary.mechanics, candidate.summary.mechanics)) score += 0.35
    if (sameText(anchor.classification, candidate.classification)) score += 0.3
    score += (1.0 / (1 + abs(difficultyRank(anchor.summary.difficulty) - difficultyRank(candidate.summary.difficulty)))).coerceIn(0.25, 1.0)
    return score
}

private fun isProgression(anchor: ExerciseFamilyProfile, candidate: ExerciseFamilyProfile): Boolean {
    if (!sameMotorFamily(anchor, candidate)) return false
    val harderDifficulty = difficultyRank(candidate.summary.difficulty) > difficultyRank(anchor.summary.difficulty)
    val biggerSetup = equipmentDemandRank(candidate.summary.equipment) > equipmentDemandRank(anchor.summary.equipment) + 1
    val broaderMechanics = !anchor.summary.mechanics.equals("Compound", ignoreCase = true) &&
        candidate.summary.mechanics.equals("Compound", ignoreCase = true)
    return harderDifficulty || biggerSetup || broaderMechanics
}

private fun isRegression(anchor: ExerciseFamilyProfile, candidate: ExerciseFamilyProfile): Boolean {
    if (!sameMotorFamily(anchor, candidate)) return false
    val easierDifficulty = difficultyRank(candidate.summary.difficulty) < difficultyRank(anchor.summary.difficulty)
    val simplerSetup = equipmentDemandRank(candidate.summary.equipment) + 1 < equipmentDemandRank(anchor.summary.equipment)
    val simplerMechanics = anchor.summary.mechanics.equals("Compound", ignoreCase = true) &&
        !candidate.summary.mechanics.equals("Compound", ignoreCase = true)
    return easierDifficulty || simplerSetup || simplerMechanics
}

private fun sameMotorFamily(anchor: ExerciseFamilyProfile, candidate: ExerciseFamilyProfile): Boolean {
    return samePrimaryMuscle(anchor, candidate) ||
        sameText(anchor.primeMover, candidate.primeMover) ||
        sharedMovementPatterns(anchor, candidate).isNotEmpty()
}

private fun samePrimaryMuscle(anchor: ExerciseFamilyProfile, candidate: ExerciseFamilyProfile): Boolean {
    return sameText(anchor.summary.targetMuscleGroup, candidate.summary.targetMuscleGroup) ||
        sameText(anchor.primeMover, candidate.primeMover)
}

private fun sameEquipment(anchor: ExerciseFamilyProfile, candidate: ExerciseFamilyProfile): Boolean =
    sameText(anchor.summary.equipment, candidate.summary.equipment) ||
        sameText(anchor.summary.secondaryEquipment, candidate.summary.equipment) ||
        sameText(anchor.summary.equipment, candidate.summary.secondaryEquipment)

private fun sharedMovementPatterns(anchor: ExerciseFamilyProfile, candidate: ExerciseFamilyProfile): List<String> {
    val candidatePatterns = candidate.movementPatterns.normalizedSet()
    return anchor.movementPatterns
        .filter { it.normalizedFamilyToken() in candidatePatterns }
        .distinctBy { it.normalizedFamilyToken() }
}

private fun difficultySeparation(anchor: ExerciseFamilyProfile, candidate: ExerciseFamilyProfile): Double =
    abs(difficultyRank(anchor.summary.difficulty) - difficultyRank(candidate.summary.difficulty)).toDouble()

private fun difficultyRank(value: String?): Int = when (value.normalizedFamilyToken()) {
    "beginner" -> 1
    "novice" -> 1
    "intermediate" -> 2
    "advanced" -> 3
    "expert" -> 4
    else -> 2
}

private fun equipmentDemandRank(value: String?): Int {
    return when (value.normalizedFamilyToken()) {
        "", "bodyweight" -> 1
        "resistance band", "mini band", "loop band" -> 2
        "dumbbell", "kettlebell", "medicine ball", "stability ball" -> 3
        "cable", "suspension trainer", "landmine" -> 4
        "barbell", "smith machine" -> 5
        "machine", "lever machine" -> 4
        else -> 3
    }
}

private fun ExerciseFamilyProfile.muscles(): List<String> =
    listOfNotNull(
        summary.targetMuscleGroup,
        primeMover,
        secondaryMuscle,
        tertiaryMuscle,
    )
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.normalizedFamilyToken() }

private fun List<String>.normalizedSet(): Set<String> =
    map { it.normalizedFamilyToken() }.filter { it.isNotBlank() }.toSet()

private fun sameText(first: String?, second: String?): Boolean {
    val normalizedFirst = first.normalizedFamilyToken()
    return normalizedFirst.isNotBlank() && normalizedFirst == second.normalizedFamilyToken()
}

private fun String?.normalizedFamilyToken(): String =
    orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
