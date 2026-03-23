package dev.toastlabs.toastlift.data

import kotlin.math.max
import kotlin.math.round

data class ExercisePrescriptionRequest(
    val workoutExercise: WorkoutExercise,
    val exerciseDetail: ExerciseDetail?,
    val profile: UserProfile?,
    val history: List<HistoricalExerciseSet>,
    val plannedLoadTarget: Double? = null,
    val plannedSetCount: Int? = null,
)

class ExercisePrescriptionEngine {
    fun prescribe(request: ExercisePrescriptionRequest): ExercisePrescription {
        val repRange = parseRepRange(request.workoutExercise.repRange)
        val plannedSetCount = request.plannedSetCount ?: request.workoutExercise.sets
        val directHistory = request.history
            .filter { it.exerciseId == request.workoutExercise.exerciseId && it.completed }
            .sortedByDescending { it.completedAtUtc }
        val similarityAnchors = request.history
            .filter { it.exerciseId != request.workoutExercise.exerciseId && it.completed && it.weight != null }
            .map { it to similarityScore(request.exerciseDetail, request.workoutExercise, it) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }

        val recommendedReps = recommendRepCount(
            repRange = repRange,
            goal = request.profile?.goal,
            overloadStrategy = request.workoutExercise.overloadStrategy,
            directHistory = directHistory,
        )

        if (!supportsNumericWeight(request.exerciseDetail, request.workoutExercise)) {
            return ExercisePrescription(
                repRange = request.workoutExercise.repRange,
                recommendedRepCount = recommendedReps,
                recommendedWeight = null,
                setCount = plannedSetCount,
                source = if (request.workoutExercise.equipment.equals("Bodyweight", ignoreCase = true)) {
                    RecommendationSource.BODYWEIGHT
                } else {
                    RecommendationSource.NONE
                },
                confidence = if (request.workoutExercise.equipment.equals("Bodyweight", ignoreCase = true)) 1.0 else 0.0,
                rationale = listOf("Numeric external load is not meaningful for this movement in the current model."),
            )
        }

        val directRecommendation = recommendFromDirectHistory(
            request = request,
            repRange = repRange,
            directHistory = directHistory,
        )
        if (directRecommendation != null) {
            return directRecommendation.copy(recommendedRepCount = recommendedReps ?: directRecommendation.recommendedRepCount)
        }

        val similarRecommendation = recommendFromSimilarHistory(
            request = request,
            repRange = repRange,
            recommendedReps = recommendedReps,
            anchors = similarityAnchors,
        )
        if (similarRecommendation != null) {
            return similarRecommendation
        }

        return recommendFromColdStart(
            request = request,
            repRange = repRange,
            recommendedReps = recommendedReps,
        )
    }

    private fun recommendRepCount(
        repRange: IntRange?,
        goal: String?,
        overloadStrategy: String?,
        directHistory: List<HistoricalExerciseSet>,
    ): Int? {
        repRange ?: return null
        val midpoint = (repRange.first + repRange.last) / 2
        val lowerMiddle = max(repRange.first, (repRange.first + midpoint) / 2)
        val latestTargetMiss = directHistory
            .takeWhile { it.completedAtUtc == directHistory.firstOrNull()?.completedAtUtc }
            .mapNotNull(HistoricalExerciseSet::actualReps)
            .minOrNull()
            ?.let { it < repRange.first }
            ?: false

        return when {
            overloadStrategy == "INCREASE_LOAD" -> repRange.first
            overloadStrategy == "REGRESS_LOAD" -> repRange.first
            latestTargetMiss -> repRange.first
            goal == "Strength" -> lowerMiddle
            goal == "Conditioning" -> midpoint
            else -> midpoint
        }
    }

    private fun recommendFromDirectHistory(
        request: ExercisePrescriptionRequest,
        repRange: IntRange?,
        directHistory: List<HistoricalExerciseSet>,
    ): ExercisePrescription? {
        if (directHistory.isEmpty()) return null
        val latestSession = directHistory.takeWhile { it.completedAtUtc == directHistory.first().completedAtUtc }
        val latestWeight = latestSession.mapNotNull(HistoricalExerciseSet::weight).maxOrNull()
            ?: directHistory.mapNotNull(HistoricalExerciseSet::weight).maxOrNull()
            ?: request.workoutExercise.suggestedWeight
            ?: return null
        val baseWeight = request.plannedLoadTarget ?: request.workoutExercise.suggestedWeight ?: latestWeight
        val adjustedWeight = when (request.workoutExercise.overloadStrategy) {
            "INCREASE_LOAD" -> baseWeight * 1.025
            "REGRESS_LOAD" -> baseWeight * 0.9
            else -> baseWeight
        }
        val latestMinReps = latestSession.mapNotNull(HistoricalExerciseSet::actualReps).minOrNull()
        val recommendedReps = recommendRepCount(
            repRange = repRange,
            goal = request.profile?.goal,
            overloadStrategy = request.workoutExercise.overloadStrategy,
            directHistory = directHistory,
        ) ?: latestMinReps
        return ExercisePrescription(
            repRange = request.workoutExercise.repRange,
            recommendedRepCount = recommendedReps,
            recommendedWeight = roundToEquipmentIncrement(
                value = adjustedWeight,
                equipment = request.workoutExercise.equipment,
                units = request.profile?.units ?: "imperial",
            ),
            setCount = request.plannedSetCount ?: request.workoutExercise.sets,
            source = RecommendationSource.DIRECT_HISTORY,
            confidence = 0.92,
            rationale = listOf("Used direct exercise history as the primary anchor."),
        )
    }

    private fun recommendFromSimilarHistory(
        request: ExercisePrescriptionRequest,
        repRange: IntRange?,
        recommendedReps: Int?,
        anchors: List<Pair<HistoricalExerciseSet, Double>>,
    ): ExercisePrescription? {
        val topAnchors = anchors.take(5)
        if (topAnchors.isEmpty()) return null
        val weightedAverage = topAnchors.sumOf { (set, score) -> (set.weight ?: 0.0) * score } /
            topAnchors.sumOf { it.second }
        val bestKnownForEquipment = request.history
            .filter { it.completed && it.weight != null && it.exerciseId != request.workoutExercise.exerciseId }
            .filter { normalize(it.equipmentClass()) == normalize(request.workoutExercise.equipment) }
            .maxOfOrNull { it.weight ?: 0.0 }
        val conservativeEstimate = weightedAverage * 0.82
        val capped = bestKnownForEquipment?.let { minOf(conservativeEstimate, it * 0.95) } ?: conservativeEstimate
        val rounded = roundToEquipmentIncrement(
            value = capped,
            equipment = request.workoutExercise.equipment,
            units = request.profile?.units ?: "imperial",
        )
        val confidence = (topAnchors.first().second * 0.65).coerceIn(0.4, 0.72)
        return ExercisePrescription(
            repRange = request.workoutExercise.repRange,
            recommendedRepCount = recommendedReps ?: recommendRepCount(repRange, request.profile?.goal, null, emptyList()),
            recommendedWeight = rounded,
            setCount = request.plannedSetCount ?: request.workoutExercise.sets,
            source = RecommendationSource.SIMILAR_EXERCISE_HISTORY,
            confidence = confidence,
            rationale = listOf("Estimated from similar exercises using equipment, muscle, and movement overlap."),
        )
    }

    private fun recommendFromColdStart(
        request: ExercisePrescriptionRequest,
        repRange: IntRange?,
        recommendedReps: Int?,
    ): ExercisePrescription {
        val baseline = coldStartWeight(request.exerciseDetail, request.workoutExercise)
        return ExercisePrescription(
            repRange = request.workoutExercise.repRange,
            recommendedRepCount = recommendedReps ?: recommendRepCount(repRange, request.profile?.goal, null, emptyList()),
            recommendedWeight = roundToEquipmentIncrement(
                value = baseline,
                equipment = request.workoutExercise.equipment,
                units = request.profile?.units ?: "imperial",
            ),
            setCount = request.plannedSetCount ?: request.workoutExercise.sets,
            source = RecommendationSource.COLD_START_HEURISTIC,
            confidence = 0.35,
            rationale = listOf("No user history anchor existed, so a conservative starter load was used."),
        )
    }

    private fun coldStartWeight(detail: ExerciseDetail?, exercise: WorkoutExercise): Double {
        val equipment = normalize(exercise.equipment)
        val classification = normalize(detail?.classification)
        val bodyRegion = normalize(exercise.bodyRegion)
        return when (equipment) {
            "barbell", "trap bar" -> when {
                bodyRegion == "lower body" -> 65.0
                classification.contains("isolation") -> 35.0
                else -> 45.0
            }
            "dumbbell", "kettlebell" -> when {
                bodyRegion == "lower body" -> 25.0
                classification.contains("isolation") -> 15.0
                else -> 20.0
            }
            "machine", "cable", "landmine", "sled" -> if (bodyRegion == "lower body") 40.0 else 25.0
            else -> 20.0
        }
    }

    private fun similarityScore(
        detail: ExerciseDetail?,
        exercise: WorkoutExercise,
        anchor: HistoricalExerciseSet,
    ): Double {
        var score = 0.0
        if (normalize(anchor.equipmentClass()) == normalize(exercise.equipment)) score += 0.45
        if (normalize(anchor.targetMuscleGroup) == normalize(exercise.targetMuscleGroup)) score += 0.25
        if (normalize(anchor.classification) == normalize(detail?.classification)) score += 0.1
        if (normalize(anchor.laterality) == normalize(detail?.laterality)) score += 0.05
        val currentPatterns = detail?.movementPatterns.orEmpty().map(::normalize).toSet()
        val anchorPatterns = anchor.movementPatterns.map(::normalize).toSet()
        if (currentPatterns.isNotEmpty()) {
            val overlap = currentPatterns.intersect(anchorPatterns).size.toDouble() / currentPatterns.size
            score += overlap * 0.15
        }
        return score
    }

    private fun supportsNumericWeight(detail: ExerciseDetail?, exercise: WorkoutExercise): Boolean {
        val equipment = normalize(exercise.equipment)
        val classification = normalize(detail?.classification)
        if (classification.contains("stretch") || classification.contains("yoga") || classification.contains("pose")) {
            return false
        }
        return equipment in loadableEquipmentFamilies
    }

    private fun parseRepRange(repRange: String): IntRange? {
        val start = repRange.substringBefore('-').trim().toIntOrNull() ?: return null
        val end = repRange.substringAfter('-', missingDelimiterValue = repRange).trim().toIntOrNull() ?: start
        return minOf(start, end)..maxOf(start, end)
    }

    private fun roundToEquipmentIncrement(value: Double, equipment: String, units: String): Double {
        val increment = when (normalize(equipment)) {
            "barbell", "trap bar", "landmine", "machine", "cable", "sled" -> if (units == "metric") 2.5 else 5.0
            "dumbbell", "kettlebell", "medicine ball", "slam ball", "wall ball", "sandbag", "heavy sandbag" -> if (units == "metric") 2.0 else 5.0
            else -> if (units == "metric") 1.0 else 2.5
        }
        return round(value / increment) * increment
    }

    private fun HistoricalExerciseSet.equipmentClass(): String? {
        return equipmentGuessFromName(exerciseName)
    }

    private fun equipmentGuessFromName(name: String): String {
        val normalizedName = normalize(name)
        return when {
            normalizedName.contains("barbell") -> "barbell"
            normalizedName.contains("dumbbell") -> "dumbbell"
            normalizedName.contains("kettlebell") -> "kettlebell"
            normalizedName.contains("cable") -> "cable"
            normalizedName.contains("machine") -> "machine"
            normalizedName.contains("sled") -> "sled"
            else -> ""
        }
    }

    private fun normalize(value: String?): String = value?.trim()?.lowercase().orEmpty()

    private companion object {
        val loadableEquipmentFamilies = setOf(
            "barbell",
            "dumbbell",
            "ez bar",
            "kettlebell",
            "landmine",
            "machine",
            "cable",
            "trap bar",
            "weight plate",
            "medicine ball",
            "slam ball",
            "wall ball",
            "sandbag",
            "heavy sandbag",
            "bulgarian bag",
            "clubbell",
            "macebell",
            "indian club",
            "tire",
            "sled",
        )
    }
}

fun formatRecommendedWeight(value: Double?): String {
    value ?: return ""
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%.1f".format(value)
    }
}
