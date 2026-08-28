package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ExerciseDetail
import dev.toastlabs.toastlift.data.FreshnessPenaltyAdherenceSignal
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.UserProfile
import dev.toastlabs.toastlift.data.WeeklyMuscleTargetWorkoutRow
import dev.toastlabs.toastlift.data.normalizeTrainingFreshnessBucketExercises
import dev.toastlabs.toastlift.data.normalizeTrainingFreshnessThresholdDays
import dev.toastlabs.toastlift.data.normalizeMuscleTargetSubcategoryKey
import dev.toastlabs.toastlift.data.resolveMuscleTargetContributions
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

internal const val TRAINING_FRESHNESS_DUE_SOON_HOURS = 24
private const val BUCKET_RESET_WEIGHTED_SETS = 2.0
internal const val TRAINING_FRESHNESS_MUSCLE_RESET_WEIGHTED_SETS = 1.0

internal data class TrainingFreshnessSummary(
    val thresholdDays: Int,
    val dueSoonHours: Int,
    val generatedAtUtc: Instant,
    val cardMode: TrainingFreshnessCardMode,
    val headline: String,
    val supportingText: String,
    val bucketRows: List<TrainingFreshnessBucketRow>,
    val muscleRows: List<TrainingFreshnessMuscleRow>,
)

internal data class TrainingFreshnessBucketRow(
    val key: String,
    val label: String,
    val family: TrainingFreshnessFamily,
    val status: TrainingFreshnessStatus,
    val lastStimulusAtUtc: Instant?,
    val hoursSinceStimulus: Long?,
    val hoursUntilThreshold: Long?,
    val progressFraction: Float,
    val weightedCompletedSets: Double,
    val contributingMuscleLabels: List<String>,
)

internal data class TrainingFreshnessMuscleRow(
    val key: String,
    val label: String,
    val family: TrainingFreshnessFamily,
    val status: TrainingFreshnessStatus,
    val lastStimulusAtUtc: Instant?,
    val hoursSinceStimulus: Long?,
    val hoursUntilThreshold: Long?,
    val progressFraction: Float,
    val weightedCompletedSets: Double,
    val lastExerciseNames: List<String>,
)

internal enum class TrainingFreshnessStatus {
    Fresh,
    DueSoon,
    Overdue,
    Untracked,
}

internal enum class TrainingFreshnessCardMode {
    NoHistory,
    OnTrack,
    DueSoon,
    Overdue,
}

internal enum class TrainingFreshnessFamily {
    Upper,
    Lower,
    Core,
}

internal enum class TrainingFreshnessFilter(val label: String) {
    All("All"),
    Upper("Upper"),
    Lower("Lower"),
    Core("Core"),
    Due("Due"),
    Overdue("Overdue"),
}

internal enum class TrainingFreshnessSort(val label: String) {
    MostUrgent("Most urgent"),
    LeastRecent("Least recent"),
    Alphabetical("A-Z"),
}

internal data class TrainingFreshnessSlot(
    val key: String,
    val label: String,
    val family: TrainingFreshnessFamily,
)

internal data class TrainingFreshnessContribution(
    val key: String,
    val label: String,
    val family: TrainingFreshnessFamily,
    val weight: Double,
    val exerciseName: String,
)

private data class StimulusAccumulator(
    var weightedSets: Double = 0.0,
    val muscleLabels: MutableSet<String> = linkedSetOf(),
    val exerciseNames: MutableSet<String> = linkedSetOf(),
    val exerciseIds: MutableSet<Long> = linkedSetOf(),
)

private data class TrainingFreshnessEventMaps(
    val muscleEvents: Map<Pair<String, String>, StimulusAccumulator>,
    val bucketEvents: Map<Pair<String, String>, StimulusAccumulator>,
)

private val trackedMuscles = listOf(
    TrainingFreshnessSlot("chest", "Chest", TrainingFreshnessFamily.Upper),
    TrainingFreshnessSlot("shoulders", "Shoulders", TrainingFreshnessFamily.Upper),
    TrainingFreshnessSlot("triceps", "Triceps", TrainingFreshnessFamily.Upper),
    TrainingFreshnessSlot("back", "Back", TrainingFreshnessFamily.Upper),
    TrainingFreshnessSlot("biceps", "Biceps", TrainingFreshnessFamily.Upper),
    TrainingFreshnessSlot("forearms", "Forearms", TrainingFreshnessFamily.Upper),
    TrainingFreshnessSlot("quadriceps", "Quadriceps", TrainingFreshnessFamily.Lower),
    TrainingFreshnessSlot("hamstrings", "Hamstrings", TrainingFreshnessFamily.Lower),
    TrainingFreshnessSlot("glutes", "Glutes", TrainingFreshnessFamily.Lower),
    TrainingFreshnessSlot("calves", "Calves", TrainingFreshnessFamily.Lower),
    TrainingFreshnessSlot("adductors", "Adductors", TrainingFreshnessFamily.Lower),
    TrainingFreshnessSlot("abductors", "Abductors", TrainingFreshnessFamily.Lower),
    TrainingFreshnessSlot("core", "Core", TrainingFreshnessFamily.Core),
    TrainingFreshnessSlot("erector_spinae", "Lower Back", TrainingFreshnessFamily.Core),
)

private val trackedBuckets = listOf(
    TrainingFreshnessSlot("upper", "Upper", TrainingFreshnessFamily.Upper),
    TrainingFreshnessSlot("lower", "Lower", TrainingFreshnessFamily.Lower),
    TrainingFreshnessSlot("core", "Core", TrainingFreshnessFamily.Core),
)

internal fun trainingFreshnessMuscleSlots(): List<TrainingFreshnessSlot> = trackedMuscles

internal fun buildTrainingFreshnessSummary(
    profile: UserProfile,
    rows: List<WeeklyMuscleTargetWorkoutRow>,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
    nowUtc: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): TrainingFreshnessSummary {
    val thresholdDays = normalizeTrainingFreshnessThresholdDays(profile.trainingFreshnessThresholdDays)
    val minimumBucketExercises = normalizeTrainingFreshnessBucketExercises(profile.trainingFreshnessMinimumBucketExercises)
    val thresholdHours = thresholdDays * 24L
    val eventMaps = collectTrainingFreshnessEvents(rows, exerciseDetailsById, zoneId)

    val muscleRows = trackedMuscles.map { slot ->
        val latest = latestQualifyingEvent(
            slotKey = slot.key,
            events = eventMaps.muscleEvents,
            resetThreshold = TRAINING_FRESHNESS_MUSCLE_RESET_WEIGHTED_SETS,
        )
        val status = freshnessStatus(latest?.first, nowUtc, thresholdHours)
        TrainingFreshnessMuscleRow(
            key = slot.key,
            label = slot.label,
            family = slot.family,
            status = status,
            lastStimulusAtUtc = latest?.first,
            hoursSinceStimulus = latest?.first?.let { hoursBetween(it, nowUtc) },
            hoursUntilThreshold = latest?.first?.let { thresholdHours - hoursBetween(it, nowUtc) },
            progressFraction = latest?.first?.let { progressFraction(it, nowUtc, thresholdHours) } ?: 0f,
            weightedCompletedSets = latest?.second?.weightedSets ?: 0.0,
            lastExerciseNames = latest?.second?.exerciseNames.orEmpty().take(2),
        )
    }

    val bucketRows = trackedBuckets.map { slot ->
        val latest = latestQualifyingEvent(
            slotKey = slot.key,
            events = eventMaps.bucketEvents,
            resetThreshold = BUCKET_RESET_WEIGHTED_SETS,
            minimumDistinctExercises = minimumBucketExercises,
        )
        val status = freshnessStatus(latest?.first, nowUtc, thresholdHours)
        TrainingFreshnessBucketRow(
            key = slot.key,
            label = slot.label,
            family = slot.family,
            status = status,
            lastStimulusAtUtc = latest?.first,
            hoursSinceStimulus = latest?.first?.let { hoursBetween(it, nowUtc) },
            hoursUntilThreshold = latest?.first?.let { thresholdHours - hoursBetween(it, nowUtc) },
            progressFraction = latest?.first?.let { progressFraction(it, nowUtc, thresholdHours) } ?: 0f,
            weightedCompletedSets = latest?.second?.weightedSets ?: 0.0,
            contributingMuscleLabels = latest?.second?.muscleLabels.orEmpty().take(4),
        )
    }

    val primaryRows = bucketRows.filter { it.family != TrainingFreshnessFamily.Core }
    val mostUrgent = primaryRows.sortedWith(trainingFreshnessUrgencyComparator()).firstOrNull()
    val hasTrackedBucket = primaryRows.any { it.status != TrainingFreshnessStatus.Untracked }
    val mode = when {
        !hasTrackedBucket -> TrainingFreshnessCardMode.NoHistory
        primaryRows.any { it.status == TrainingFreshnessStatus.Overdue } -> TrainingFreshnessCardMode.Overdue
        primaryRows.any { it.status == TrainingFreshnessStatus.DueSoon } -> TrainingFreshnessCardMode.DueSoon
        else -> TrainingFreshnessCardMode.OnTrack
    }
    val headline = when (mode) {
        TrainingFreshnessCardMode.NoHistory -> "Log workouts to start freshness tracking."
        TrainingFreshnessCardMode.Overdue -> "${mostUrgent?.label ?: "A muscle group"} is past your $thresholdDays-day refresh target."
        TrainingFreshnessCardMode.DueSoon -> "${mostUrgent?.label ?: "A muscle group"} is due soon."
        TrainingFreshnessCardMode.OnTrack -> "Upper and lower are inside your $thresholdDays-day refresh target."
    }
    val supportingText = when (mode) {
        TrainingFreshnessCardMode.NoHistory -> "Completed sets power this view; planned exercises do not reset it."
        TrainingFreshnessCardMode.Overdue -> "Build or log a meaningful session to refresh the target."
        TrainingFreshnessCardMode.DueSoon -> mostUrgent?.hoursUntilThreshold?.let { "${formatTrainingFreshnessDuration(it)} left before the target." }
            ?: "Plan the next refresh before the target passes."
        TrainingFreshnessCardMode.OnTrack -> "Keep following the plan; no freshness warning right now."
    }

    return TrainingFreshnessSummary(
        thresholdDays = thresholdDays,
        dueSoonHours = TRAINING_FRESHNESS_DUE_SOON_HOURS,
        generatedAtUtc = nowUtc,
        cardMode = mode,
        headline = headline,
        supportingText = supportingText,
        bucketRows = bucketRows,
        muscleRows = muscleRows,
    )
}

internal fun buildTrainingFreshnessPenaltySignals(
    profile: UserProfile,
    rows: List<WeeklyMuscleTargetWorkoutRow>,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
    nowUtc: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<FreshnessPenaltyAdherenceSignal> {
    val thresholdDays = normalizeTrainingFreshnessThresholdDays(profile.trainingFreshnessThresholdDays)
    val thresholdHours = thresholdDays * 24L
    val minimumBucketExercises = normalizeTrainingFreshnessBucketExercises(profile.trainingFreshnessMinimumBucketExercises)
    val bucketEvents = collectTrainingFreshnessEvents(rows, exerciseDetailsById, zoneId).bucketEvents
    val candidates = trackedBuckets
        .filter { it.family == TrainingFreshnessFamily.Upper || it.family == TrainingFreshnessFamily.Lower }
        .flatMap { bucket ->
            val refreshInstants = qualifyingEventInstants(
                slotKey = bucket.key,
                events = bucketEvents,
                resetThreshold = BUCKET_RESET_WEIGHTED_SETS,
                minimumDistinctExercises = minimumBucketExercises,
            )
            buildBucketFreshnessPenaltyCandidates(
                bucketKey = bucket.key,
                refreshInstants = refreshInstants,
                thresholdHours = thresholdHours,
                nowUtc = nowUtc,
            )
        }

    return candidates
        .groupBy { candidate -> candidate.instant.atZone(zoneId).toLocalDate() }
        .toSortedMap()
        .map { (_, dayCandidates) ->
            val occurredAt = dayCandidates.minOf { it.instant }
            FreshnessPenaltyAdherenceSignal(
                occurredAtUtc = occurredAt.toString(),
                familyKeys = dayCandidates.mapTo(linkedSetOf()) { it.bucketKey },
            )
        }
}

private data class FreshnessPenaltyCandidate(
    val instant: Instant,
    val bucketKey: String,
)

private fun buildBucketFreshnessPenaltyCandidates(
    bucketKey: String,
    refreshInstants: List<Instant>,
    thresholdHours: Long,
    nowUtc: Instant,
): List<FreshnessPenaltyCandidate> {
    if (refreshInstants.isEmpty()) return emptyList()
    val penaltyStartOffset = Duration.ofHours(thresholdHours + 24L)
    val penaltyInterval = Duration.ofHours(24L)
    return buildList {
        refreshInstants.forEachIndexed { index, refreshInstant ->
            val nextRefresh = refreshInstants.getOrNull(index + 1)
            var penaltyInstant = refreshInstant.plus(penaltyStartOffset)
            while (!penaltyInstant.isAfter(nowUtc) && (nextRefresh == null || penaltyInstant.isBefore(nextRefresh))) {
                add(FreshnessPenaltyCandidate(instant = penaltyInstant, bucketKey = bucketKey))
                penaltyInstant = penaltyInstant.plus(penaltyInterval)
            }
        }
    }
}

private fun collectTrainingFreshnessEvents(
    rows: List<WeeklyMuscleTargetWorkoutRow>,
    exerciseDetailsById: Map<Long, ExerciseDetail>,
    zoneId: ZoneId,
): TrainingFreshnessEventMaps {
    val muscleEvents = linkedMapOf<Pair<String, String>, StimulusAccumulator>()
    val bucketEvents = linkedMapOf<Pair<String, String>, StimulusAccumulator>()

    rows.filter { it.completedSetCount > 0 }
        .forEach { row ->
            val occurredAtUtc = normalizedInstantString(row.workoutOccurredAtUtc, zoneId) ?: return@forEach
            val detail = exerciseDetailsById[row.exerciseId]
            val contributions = resolveTrainingFreshnessContributions(detail)
            contributions.forEach { contribution ->
                val muscleKey = occurredAtUtc to contribution.key
                val muscleAccumulator = muscleEvents.getOrPut(muscleKey) { StimulusAccumulator() }
                val weightedSets = row.completedSetCount * contribution.weight
                muscleAccumulator.weightedSets += weightedSets
                muscleAccumulator.exerciseNames += contribution.exerciseName
                muscleAccumulator.exerciseIds += row.exerciseId

                val bucketKey = occurredAtUtc to contribution.family.bucketKey()
                val bucketAccumulator = bucketEvents.getOrPut(bucketKey) { StimulusAccumulator() }
                bucketAccumulator.weightedSets += weightedSets
                bucketAccumulator.muscleLabels += contribution.label
                bucketAccumulator.exerciseNames += contribution.exerciseName
                bucketAccumulator.exerciseIds += row.exerciseId
            }
        }

    return TrainingFreshnessEventMaps(
        muscleEvents = muscleEvents,
        bucketEvents = bucketEvents,
    )
}

internal fun filterTrainingFreshnessMuscles(
    rows: List<TrainingFreshnessMuscleRow>,
    filter: TrainingFreshnessFilter,
    sort: TrainingFreshnessSort,
): List<TrainingFreshnessMuscleRow> {
    val filtered = rows.filter { row ->
        when (filter) {
            TrainingFreshnessFilter.All -> true
            TrainingFreshnessFilter.Upper -> row.family == TrainingFreshnessFamily.Upper
            TrainingFreshnessFilter.Lower -> row.family == TrainingFreshnessFamily.Lower
            TrainingFreshnessFilter.Core -> row.family == TrainingFreshnessFamily.Core
            TrainingFreshnessFilter.Due -> row.status == TrainingFreshnessStatus.DueSoon || row.status == TrainingFreshnessStatus.Overdue
            TrainingFreshnessFilter.Overdue -> row.status == TrainingFreshnessStatus.Overdue
        }
    }
    return when (sort) {
        TrainingFreshnessSort.MostUrgent -> filtered.sortedWith(trainingFreshnessUrgencyComparator())
        TrainingFreshnessSort.LeastRecent -> filtered.sortedWith(
            compareByDescending<TrainingFreshnessMuscleRow> { it.hoursSinceStimulus ?: Long.MAX_VALUE }
                .thenBy { it.label },
        )
        TrainingFreshnessSort.Alphabetical -> filtered.sortedBy { it.label }
    }
}

internal fun trainingFreshnessStatusLabel(status: TrainingFreshnessStatus): String = when (status) {
    TrainingFreshnessStatus.Fresh -> "Fresh"
    TrainingFreshnessStatus.DueSoon -> "Due soon"
    TrainingFreshnessStatus.Overdue -> "Past target"
    TrainingFreshnessStatus.Untracked -> "Untracked"
}

internal fun trainingFreshnessTimingLabel(row: TrainingFreshnessBucketRow): String {
    return trainingFreshnessTimingLabel(row.status, row.hoursSinceStimulus, row.hoursUntilThreshold)
}

internal fun trainingFreshnessTimingLabel(row: TrainingFreshnessMuscleRow): String {
    return trainingFreshnessTimingLabel(row.status, row.hoursSinceStimulus, row.hoursUntilThreshold)
}

private fun trainingFreshnessTimingLabel(
    status: TrainingFreshnessStatus,
    hoursSinceStimulus: Long?,
    hoursUntilThreshold: Long?,
): String {
    if (status == TrainingFreshnessStatus.Untracked || hoursSinceStimulus == null) {
        return "No logged stimulus yet"
    }
    return when (status) {
        TrainingFreshnessStatus.Fresh,
        TrainingFreshnessStatus.DueSoon,
        -> "${formatTrainingFreshnessDuration(hoursUntilThreshold?.coerceAtLeast(0) ?: 0)} left"
        TrainingFreshnessStatus.Overdue -> "${formatTrainingFreshnessDuration((-hoursUntilThreshold.orZero()).coerceAtLeast(0))} past"
        TrainingFreshnessStatus.Untracked -> "No logged stimulus yet"
    }
}

internal fun formatTrainingFreshnessDuration(hours: Long): String {
    val safeHours = hours.coerceAtLeast(0)
    val days = safeHours / 24
    val remainder = safeHours % 24
    return when {
        days > 0 && remainder > 0 -> "${days}d ${remainder}h"
        days > 0 -> "${days}d"
        else -> "${safeHours}h"
    }
}

internal fun trainingFreshnessProgressPercent(progress: Float): String {
    return "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%"
}

internal fun resolveTrainingFreshnessContributions(
    detail: ExerciseDetail?,
): List<TrainingFreshnessContribution> {
    if (detail == null) return emptyList()
    val contributions = linkedMapOf<String, TrainingFreshnessContribution>()

    fun addSlot(slot: TrainingFreshnessSlot, weight: Double) {
        val current = contributions[slot.key]
        if (current == null || weight > current.weight) {
            contributions[slot.key] = TrainingFreshnessContribution(
                key = slot.key,
                label = slot.label,
                family = slot.family,
                weight = weight,
                exerciseName = detail.summary.name,
            )
        }
    }

    fun add(muscleName: String?, weight: Double) {
        val slot = muscleName?.let(::mapTrainingFreshnessMuscle) ?: return
        addSlot(slot, weight)
    }

    resolveMuscleTargetContributions(detail).forEach { contribution ->
        mapMuscleTargetToTrainingFreshnessSlot(contribution.subcategoryKey)?.let { slot ->
            addSlot(slot, contribution.weight)
        }
    }

    add(detail.summary.targetMuscleGroup, 1.0)
    add(detail.primeMover, 1.0)
    add(detail.secondaryMuscle, 0.5)
    add(detail.tertiaryMuscle, 0.5)

    if (contributions.isNotEmpty()) {
        return contributions.values.toList()
    }

    return fallbackTrainingFreshnessContributions(detail).map { slot ->
        TrainingFreshnessContribution(
            key = slot.key,
            label = slot.label,
            family = slot.family,
            weight = if (slot.key == "triceps" || slot.key == "biceps" || slot.key == "glutes") 0.5 else 1.0,
            exerciseName = detail.summary.name,
        )
    }
}

internal fun mapTrainingFreshnessMuscleSlot(muscleName: String): TrainingFreshnessSlot? =
    mapTrainingFreshnessMuscle(muscleName)

internal fun resolveTrainingFreshnessContributions(
    exercise: SessionExercise,
    detail: ExerciseDetail?,
): List<TrainingFreshnessContribution> {
    val detailContributions = resolveTrainingFreshnessContributions(detail)
    if (detailContributions.isNotEmpty()) return detailContributions

    val slot = mapTrainingFreshnessMuscle(exercise.targetMuscleGroup) ?: return emptyList()
    return listOf(
        TrainingFreshnessContribution(
            key = slot.key,
            label = slot.label,
            family = slot.family,
            weight = 1.0,
            exerciseName = exercise.name,
        ),
    )
}

private fun mapMuscleTargetToTrainingFreshnessSlot(subcategoryKey: String): TrainingFreshnessSlot? {
    val slotKey = when (subcategoryKey) {
        "chest" -> "chest"
        "shoulders", "front_delts", "side_delts" -> "shoulders"
        "triceps" -> "triceps"
        "back", "lats", "upper_back", "rear_delts", "traps" -> "back"
        "biceps" -> "biceps"
        "forearms" -> "forearms"
        "quadriceps" -> "quadriceps"
        "hamstrings" -> "hamstrings"
        "glutes" -> "glutes"
        "calves" -> "calves"
        "adductors" -> "adductors"
        "abductors" -> "abductors"
        else -> return null
    }
    return slot(slotKey)
}

private fun fallbackTrainingFreshnessContributions(detail: ExerciseDetail): List<TrainingFreshnessSlot> {
    val patterns = detail.movementPatterns.map(::normalizeMuscleName)
    return when {
        patterns.any { "overhead" in it || "vertical press" in it } -> listOf(
            slot("shoulders"),
            slot("triceps"),
        )
        patterns.any { "fly" in it || "horizontal push" in it || "push up" in it || "bench" in it } -> listOf(
            slot("chest"),
            slot("triceps"),
        )
        patterns.any { "curl" in it } -> listOf(slot("biceps"))
        patterns.any { "pull" in it || "row" in it } -> listOf(
            slot("back"),
            slot("biceps"),
        )
        patterns.any { "hinge" in it || "deadlift" in it || "good morning" in it } -> listOf(
            slot("hamstrings"),
            slot("glutes"),
        )
        patterns.any { "bridge" in it || "thrust" in it || "abduction" in it } -> listOf(
            slot("glutes"),
            slot("hamstrings"),
        )
        patterns.any { "squat" in it || "lunge" in it || "step up" in it } -> listOf(
            slot("quadriceps"),
            slot("glutes"),
        )
        else -> emptyList()
    }.filterNotNull()
}

private fun mapTrainingFreshnessMuscle(muscleName: String): TrainingFreshnessSlot? {
    val normalized = normalizeMuscleName(muscleName)
    if (knownCatalogTrainingFreshnessSlotKeys.containsKey(normalized)) {
        return knownCatalogTrainingFreshnessSlotKeys.getValue(normalized)?.let(::slot)
    }
    normalizeMuscleTargetSubcategoryKey(muscleName)
        ?.let(::mapMuscleTargetToTrainingFreshnessSlot)
        ?.let { return it }
    return when {
        normalized.contains("pec") || normalized.contains("chest") -> slot("chest")
        normalized.contains("rear delt") || normalized.contains("posterior delt") -> slot("back")
        normalized.contains("shoulder") || normalized.contains("delt") -> slot("shoulders")
        normalized.contains("tricep") -> slot("triceps")
        normalized.contains("forearm") || normalized.contains("brachioradialis") ||
            normalized.contains("flexor carpi") -> slot("forearms")
        normalized.contains("back") || normalized.containsMuscleNameTerm("lat") || normalized.containsMuscleNameTerm("latissimus") ||
            normalized.containsMuscleNameTerm("lats") || normalized.contains("trap") || normalized.contains("rhomboid") -> slot("back")
        normalized.contains("quad") || normalized.contains("vastus") || normalized.contains("rectus femoris") -> slot("quadriceps")
        normalized.contains("hamstring") || normalized.contains("biceps femoris") ||
            normalized.contains("semitendinosus") || normalized.contains("semimembranosus") -> slot("hamstrings")
        normalized.contains("bicep") || normalized.contains("brachialis") -> slot("biceps")
        normalized.contains("glute") -> slot("glutes")
        normalized.contains("calf") || normalized.contains("gastrocnemius") || normalized.contains("soleus") -> slot("calves")
        normalized.contains("adductor") -> slot("adductors")
        normalized.contains("abductor") -> slot("abductors")
        normalized.contains("abdom") || normalized.contains("oblique") || normalized.contains("transverse abdominis") -> slot("core")
        normalized.contains("erector") || normalized.contains("lower back") -> slot("erector_spinae")
        else -> null
    }
}

private fun slot(key: String): TrainingFreshnessSlot? = trackedMuscles.firstOrNull { it.key == key }

private val knownCatalogTrainingFreshnessSlotKeys: Map<String, String?> = mapOf(
    "abdominals" to "core",
    "erector spinae" to "erector_spinae",
    "hip flexors" to null,
    "iliopsoas" to null,
    "obliques" to "core",
    "rectus abdominis" to "core",
    "shins" to null,
    "tibialis anterior" to null,
    "tibialis posterior" to null,
    "transverse abdominis" to "core",
)

private fun latestQualifyingEvent(
    slotKey: String,
    events: Map<Pair<String, String>, StimulusAccumulator>,
    resetThreshold: Double,
    minimumDistinctExercises: Int = 1,
): Pair<Instant, StimulusAccumulator>? {
    return events.asSequence()
        .filter { (key, value) ->
            key.second == slotKey &&
                value.weightedSets >= resetThreshold &&
                value.exerciseIds.size >= minimumDistinctExercises
        }
        .mapNotNull { (key, value) -> Instant.parse(key.first) to value }
        .maxByOrNull { it.first }
}

private fun qualifyingEventInstants(
    slotKey: String,
    events: Map<Pair<String, String>, StimulusAccumulator>,
    resetThreshold: Double,
    minimumDistinctExercises: Int = 1,
): List<Instant> {
    return events.asSequence()
        .filter { (key, value) ->
            key.second == slotKey &&
                value.weightedSets >= resetThreshold &&
                value.exerciseIds.size >= minimumDistinctExercises
        }
        .mapNotNull { (key, _) -> runCatching { Instant.parse(key.first) }.getOrNull() }
        .distinct()
        .sorted()
        .toList()
}

private fun freshnessStatus(
    lastStimulusAtUtc: Instant?,
    nowUtc: Instant,
    thresholdHours: Long,
): TrainingFreshnessStatus {
    if (lastStimulusAtUtc == null) return TrainingFreshnessStatus.Untracked
    val hoursSince = hoursBetween(lastStimulusAtUtc, nowUtc)
    return when {
        hoursSince >= thresholdHours -> TrainingFreshnessStatus.Overdue
        thresholdHours - hoursSince <= TRAINING_FRESHNESS_DUE_SOON_HOURS -> TrainingFreshnessStatus.DueSoon
        else -> TrainingFreshnessStatus.Fresh
    }
}

private fun progressFraction(
    lastStimulusAtUtc: Instant,
    nowUtc: Instant,
    thresholdHours: Long,
): Float {
    val hoursSince = hoursBetween(lastStimulusAtUtc, nowUtc)
    return (hoursSince.toFloat() / thresholdHours.toFloat()).coerceIn(0f, 1f)
}

private fun hoursBetween(start: Instant, end: Instant): Long {
    return Duration.between(start, end).toHours().coerceAtLeast(0)
}

private fun normalizedInstantString(value: String, zoneId: ZoneId): String? {
    return runCatching {
        Instant.parse(value).atZone(zoneId).toInstant().toString()
    }.getOrNull()
}

private fun normalizeMuscleName(value: String?): String {
    return value
        .orEmpty()
        .trim()
        .lowercase()
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")
}

private fun String.containsMuscleNameTerm(term: String): Boolean {
    val normalizedTerm = normalizeMuscleName(term)
    return Regex("(^|\\s)${Regex.escape(normalizedTerm)}($|\\s)").containsMatchIn(this)
}

private fun TrainingFreshnessFamily.bucketKey(): String = when (this) {
    TrainingFreshnessFamily.Upper -> "upper"
    TrainingFreshnessFamily.Lower -> "lower"
    TrainingFreshnessFamily.Core -> "core"
}

private fun <T> trainingFreshnessUrgencyComparator(): Comparator<T> where T : Any {
    return Comparator { left, right ->
        val leftStatus = left.trainingFreshnessStatus()
        val rightStatus = right.trainingFreshnessStatus()
        val statusCompare = urgencyRank(leftStatus).compareTo(urgencyRank(rightStatus))
        if (statusCompare != 0) {
            statusCompare
        } else {
            val leftHours = left.hoursSinceTrainingFreshnessStimulus()
            val rightHours = right.hoursSinceTrainingFreshnessStimulus()
            val hoursCompare = (rightHours ?: -1L).compareTo(leftHours ?: -1L)
            if (hoursCompare != 0) hoursCompare else left.trainingFreshnessLabel().compareTo(right.trainingFreshnessLabel())
        }
    }
}

private fun Any.trainingFreshnessStatus(): TrainingFreshnessStatus = when (this) {
    is TrainingFreshnessBucketRow -> status
    is TrainingFreshnessMuscleRow -> status
    else -> TrainingFreshnessStatus.Untracked
}

private fun Any.hoursSinceTrainingFreshnessStimulus(): Long? = when (this) {
    is TrainingFreshnessBucketRow -> hoursSinceStimulus
    is TrainingFreshnessMuscleRow -> hoursSinceStimulus
    else -> null
}

private fun Any.trainingFreshnessLabel(): String = when (this) {
    is TrainingFreshnessBucketRow -> label
    is TrainingFreshnessMuscleRow -> label
    else -> ""
}

private fun urgencyRank(status: TrainingFreshnessStatus): Int = when (status) {
    TrainingFreshnessStatus.Overdue -> 0
    TrainingFreshnessStatus.DueSoon -> 1
    TrainingFreshnessStatus.Fresh -> 2
    TrainingFreshnessStatus.Untracked -> 3
}

private fun Long?.orZero(): Long = this ?: 0L
