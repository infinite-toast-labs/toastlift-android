package dev.toastlabs.toastlift.data

enum class MuscleTargetBucketKey(val storageKey: String, val label: String) {
    Push("push", "Push"),
    Pull("pull", "Pull"),
    Legs("legs", "Legs"),
}

data class MuscleTargetBucket(
    val key: String,
    val label: String,
    val subcategoryKeys: List<String>,
)

data class MuscleTargetSubcategory(
    val key: String,
    val label: String,
    val bucketKey: String,
    val sqlTerms: List<String>,
    val targetMultiplier: Double,
)

data class MuscleTargetContribution(
    val bucketKey: String,
    val subcategoryKey: String,
    val subcategoryLabel: String,
    val weight: Double,
)

private val pushSubcategories = listOf(
    MuscleTargetSubcategory(
        key = "chest",
        label = "Chest",
        bucketKey = MuscleTargetBucketKey.Push.storageKey,
        sqlTerms = listOf("pec", "chest"),
        targetMultiplier = 1.15,
    ),
    MuscleTargetSubcategory(
        key = "shoulders",
        label = "Shoulders",
        bucketKey = MuscleTargetBucketKey.Push.storageKey,
        sqlTerms = listOf(
            "shoulder",
            "front delt",
            "anterior delt",
            "side delt",
            "lateral delt",
            "middle delt",
            "medial delt",
        ),
        targetMultiplier = 0.4,
    ),
    MuscleTargetSubcategory(
        key = "front_delts",
        label = "Front Delts",
        bucketKey = MuscleTargetBucketKey.Push.storageKey,
        sqlTerms = listOf("front delt", "anterior delt"),
        targetMultiplier = 0.3,
    ),
    MuscleTargetSubcategory(
        key = "side_delts",
        label = "Side Delts",
        bucketKey = MuscleTargetBucketKey.Push.storageKey,
        sqlTerms = listOf(
            "side delt",
            "lateral delt",
            "middle delt",
            "medial delt",
        ),
        targetMultiplier = 0.3,
    ),
    MuscleTargetSubcategory(
        key = "triceps",
        label = "Triceps",
        bucketKey = MuscleTargetBucketKey.Push.storageKey,
        sqlTerms = listOf("tricep"),
        targetMultiplier = 0.8,
    ),
)

private val pullSubcategories = listOf(
    MuscleTargetSubcategory(
        key = "lats",
        label = "Lats",
        bucketKey = MuscleTargetBucketKey.Pull.storageKey,
        sqlTerms = listOf("latissimus", "lats"),
        targetMultiplier = 0.6,
    ),
    MuscleTargetSubcategory(
        key = "upper_back",
        label = "Upper Back",
        bucketKey = MuscleTargetBucketKey.Pull.storageKey,
        sqlTerms = listOf("upper back", "rhomboid", "middle trap", "mid trap", "lower trap"),
        targetMultiplier = 0.45,
    ),
    MuscleTargetSubcategory(
        key = "back",
        label = "Back",
        bucketKey = MuscleTargetBucketKey.Pull.storageKey,
        sqlTerms = listOf(
            "back",
            "latissimus",
            "lats",
            "rhomboid",
            "upper back",
            "middle trap",
            "mid trap",
            "lower trap",
            "trap",
            "trapezius",
            "rear delt",
            "posterior delt",
        ),
        targetMultiplier = 0.35,
    ),
    MuscleTargetSubcategory(
        key = "rear_delts",
        label = "Rear Delts",
        bucketKey = MuscleTargetBucketKey.Pull.storageKey,
        sqlTerms = listOf("rear delt", "posterior delt"),
        targetMultiplier = 0.25,
    ),
    MuscleTargetSubcategory(
        key = "traps",
        label = "Traps",
        bucketKey = MuscleTargetBucketKey.Pull.storageKey,
        sqlTerms = listOf("trap", "trapezius"),
        targetMultiplier = 0.2,
    ),
    MuscleTargetSubcategory(
        key = "biceps",
        label = "Biceps",
        bucketKey = MuscleTargetBucketKey.Pull.storageKey,
        sqlTerms = listOf("bicep", "brachialis", "brachioradialis"),
        targetMultiplier = 0.8,
    ),
    MuscleTargetSubcategory(
        key = "forearms",
        label = "Forearms",
        bucketKey = MuscleTargetBucketKey.Pull.storageKey,
        sqlTerms = listOf("forearm", "grip"),
        targetMultiplier = 0.15,
    ),
)

private val legSubcategories = listOf(
    MuscleTargetSubcategory(
        key = "quadriceps",
        label = "Quadriceps",
        bucketKey = MuscleTargetBucketKey.Legs.storageKey,
        sqlTerms = listOf("quad", "vastus", "rectus femoris"),
        targetMultiplier = 1.15,
    ),
    MuscleTargetSubcategory(
        key = "hamstrings",
        label = "Hamstrings",
        bucketKey = MuscleTargetBucketKey.Legs.storageKey,
        sqlTerms = listOf("hamstring", "biceps femoris", "semitendinosus", "semimembranosus"),
        targetMultiplier = 1.15,
    ),
    MuscleTargetSubcategory(
        key = "glutes",
        label = "Glutes",
        bucketKey = MuscleTargetBucketKey.Legs.storageKey,
        sqlTerms = listOf("glute"),
        targetMultiplier = 1.15,
    ),
    MuscleTargetSubcategory(
        key = "calves",
        label = "Calves",
        bucketKey = MuscleTargetBucketKey.Legs.storageKey,
        sqlTerms = listOf("calf", "gastrocnemius", "soleus"),
        targetMultiplier = 0.25,
    ),
    MuscleTargetSubcategory(
        key = "adductors",
        label = "Adductors",
        bucketKey = MuscleTargetBucketKey.Legs.storageKey,
        sqlTerms = listOf("adductor"),
        targetMultiplier = 0.15,
    ),
    MuscleTargetSubcategory(
        key = "abductors",
        label = "Abductors",
        bucketKey = MuscleTargetBucketKey.Legs.storageKey,
        sqlTerms = listOf("abductor"),
        targetMultiplier = 0.15,
    ),
)

private val targetSubcategories = pushSubcategories + pullSubcategories + legSubcategories

private val targetBuckets = MuscleTargetBucketKey.entries.map { bucket ->
    MuscleTargetBucket(
        key = bucket.storageKey,
        label = bucket.label,
        subcategoryKeys = targetSubcategories
            .filter { it.bucketKey == bucket.storageKey }
            .map(MuscleTargetSubcategory::key),
    )
}

fun muscleTargetBuckets(): List<MuscleTargetBucket> = targetBuckets

fun muscleTargetSubcategories(): List<MuscleTargetSubcategory> = targetSubcategories

fun muscleTargetSubcategoriesForBucket(bucketKey: String): List<MuscleTargetSubcategory> {
    val normalizedBucket = normalizeMuscleTargetBucketKey(bucketKey) ?: return emptyList()
    return targetSubcategories.filter { it.bucketKey == normalizedBucket }
}

fun muscleTargetSubcategory(key: String): MuscleTargetSubcategory? {
    val normalized = normalizeMuscleTargetSubcategoryKey(key) ?: return null
    return targetSubcategories.firstOrNull { it.key == normalized }
}

fun normalizeMuscleTargetBucketKey(value: String?): String? {
    val normalized = normalizeMuscleTargetToken(value)
    if (normalized.isBlank()) return null
    return when {
        normalized == "push" || normalized == "push muscles" -> MuscleTargetBucketKey.Push.storageKey
        normalized == "pull" || normalized == "pull muscles" -> MuscleTargetBucketKey.Pull.storageKey
        normalized == "legs" || normalized == "leg" || normalized == "leg muscles" || normalized == "lower" -> MuscleTargetBucketKey.Legs.storageKey
        else -> null
    }
}

fun normalizeMuscleTargetSubcategoryKey(value: String?): String? {
    val normalized = normalizeMuscleTargetToken(value)
    if (normalized.isBlank()) return null
    return when {
        normalized in targetSubcategories.map(MuscleTargetSubcategory::key) -> normalized
        normalized.contains("pec") || normalized.contains("chest") -> "chest"
        normalized.contains("front delt") || normalized.contains("anterior delt") -> "front_delts"
        normalized.contains("side delt") || normalized.contains("lateral delt") || normalized.contains("middle delt") ||
            normalized.contains("medial delt") -> "side_delts"
        normalized.contains("rear delt") || normalized.contains("posterior delt") -> "rear_delts"
        normalized.contains("shoulder") || normalized.contains("delt") || normalized.contains("deltoid") -> "shoulders"
        normalized.contains("tricep") -> "triceps"
        normalized.contains("lat") -> "lats"
        normalized.contains("rhomboid") || normalized.contains("upper back") || normalized.contains("middle trap") ||
            normalized.contains("mid trap") || normalized.contains("lower trap") -> "upper_back"
        normalized.contains("trap") -> "traps"
        normalized.contains("back") -> "back"
        normalized.contains("bicep") || normalized.contains("brachialis") || normalized.contains("brachioradialis") -> "biceps"
        normalized.contains("forearm") || normalized.contains("grip") -> "forearms"
        normalized.contains("quad") || normalized.contains("vastus") || normalized.contains("rectus femoris") -> "quadriceps"
        normalized.contains("hamstring") || normalized.contains("biceps femoris") || normalized.contains("semitendinosus") ||
            normalized.contains("semimembranosus") -> "hamstrings"
        normalized.contains("glute") -> "glutes"
        normalized.contains("calf") || normalized.contains("gastrocnemius") || normalized.contains("soleus") -> "calves"
        normalized.contains("adductor") -> "adductors"
        normalized.contains("abductor") -> "abductors"
        else -> null
    }
}

fun muscleTargetBucketLabel(bucketKey: String): String {
    val normalized = normalizeMuscleTargetBucketKey(bucketKey) ?: bucketKey
    return targetBuckets.firstOrNull { it.key == normalized }?.label ?: bucketKey.toReadableMuscleTargetLabel()
}

fun muscleTargetSubcategoryLabel(subcategoryKey: String): String {
    val normalized = normalizeMuscleTargetSubcategoryKey(subcategoryKey) ?: subcategoryKey
    return targetSubcategories.firstOrNull { it.key == normalized }?.label ?: subcategoryKey.toReadableMuscleTargetLabel()
}

fun muscleTargetFilterLabel(bucketKey: String?, subcategoryKey: String?): String {
    val normalizedSubcategory = normalizeMuscleTargetSubcategoryKey(subcategoryKey)
    val normalizedBucket = normalizeMuscleTargetBucketKey(bucketKey)
        ?: normalizedSubcategory?.let { muscleTargetSubcategory(it)?.bucketKey }
    return when {
        normalizedBucket != null && normalizedSubcategory != null ->
            "${muscleTargetBucketLabel(normalizedBucket)} > ${muscleTargetSubcategoryLabel(normalizedSubcategory)}"
        normalizedBucket != null -> muscleTargetBucketLabel(normalizedBucket)
        normalizedSubcategory != null -> muscleTargetSubcategoryLabel(normalizedSubcategory)
        else -> ""
    }
}

fun resolveMuscleTargetContributions(detail: ExerciseDetail?): List<MuscleTargetContribution> {
    if (detail == null) return emptyList()
    return resolveMuscleTargetContributions(
        targetMuscleGroup = detail.summary.targetMuscleGroup,
        primeMover = detail.primeMover,
        secondaryMuscle = detail.secondaryMuscle,
        tertiaryMuscle = detail.tertiaryMuscle,
        movementPatterns = detail.movementPatterns,
    )
}

fun resolveMuscleTargetContributions(
    exercise: SessionExercise,
    detail: ExerciseDetail?,
): List<MuscleTargetContribution> {
    return resolveMuscleTargetContributions(
        targetMuscleGroup = detail?.summary?.targetMuscleGroup ?: exercise.targetMuscleGroup,
        primeMover = detail?.primeMover,
        secondaryMuscle = detail?.secondaryMuscle,
        tertiaryMuscle = detail?.tertiaryMuscle,
        movementPatterns = detail?.movementPatterns.orEmpty(),
    )
}

fun resolveMuscleTargetContributions(
    targetMuscleGroup: String?,
    primeMover: String?,
    secondaryMuscle: String?,
    tertiaryMuscle: String?,
    movementPatterns: List<String>,
): List<MuscleTargetContribution> {
    val contributions = linkedMapOf<String, MuscleTargetContribution>()

    fun add(muscleName: String?, weight: Double) {
        val key = normalizeMuscleTargetSubcategoryKey(muscleName) ?: return
        val subcategory = muscleTargetSubcategory(key) ?: return
        val existing = contributions[key]
        if (existing == null || weight > existing.weight) {
            contributions[key] = MuscleTargetContribution(
                bucketKey = subcategory.bucketKey,
                subcategoryKey = subcategory.key,
                subcategoryLabel = subcategory.label,
                weight = weight,
            )
        }
    }

    add(targetMuscleGroup, 1.0)
    add(primeMover, 1.0)
    add(secondaryMuscle, 0.5)
    add(tertiaryMuscle, 0.5)

    addRollupContributions(contributions)

    if (contributions.isNotEmpty()) return contributions.values.toList()

    return fallbackMuscleTargetContributions(movementPatterns.map(::normalizeMuscleTargetToken))
}

private fun addRollupContributions(contributions: MutableMap<String, MuscleTargetContribution>) {
    rollupChildKeys.forEach { (rollupKey, childKeys) ->
        val rollupSubcategory = muscleTargetSubcategory(rollupKey) ?: return@forEach
        val rollupWeight = childKeys
            .mapNotNull { childKey -> contributions[childKey]?.weight }
            .maxOrNull()
            ?: return@forEach
        val existing = contributions[rollupKey]
        if (existing == null || rollupWeight > existing.weight) {
            contributions[rollupKey] = MuscleTargetContribution(
                bucketKey = rollupSubcategory.bucketKey,
                subcategoryKey = rollupSubcategory.key,
                subcategoryLabel = rollupSubcategory.label,
                weight = rollupWeight,
            )
        }
    }
}

private val rollupChildKeys = linkedMapOf(
    "shoulders" to setOf("front_delts", "side_delts"),
    "back" to setOf("lats", "upper_back", "rear_delts", "traps"),
)

fun muscleTargetBucketSubcategoryKeys(bucketKeys: Set<String>, subcategoryKeys: Set<String>): Set<String> {
    val selectedSubcategories = subcategoryKeys.mapNotNull(::normalizeMuscleTargetSubcategoryKey).toSet()
    val selectedBucketSubcategories = bucketKeys
        .mapNotNull(::normalizeMuscleTargetBucketKey)
        .flatMap { bucket -> muscleTargetSubcategoriesForBucket(bucket).map(MuscleTargetSubcategory::key) }
    return (selectedSubcategories + selectedBucketSubcategories).toSet()
}

private fun fallbackMuscleTargetContributions(patterns: List<String>): List<MuscleTargetContribution> {
    fun contribution(key: String, weight: Double): MuscleTargetContribution {
        val subcategory = requireNotNull(muscleTargetSubcategory(key))
        return MuscleTargetContribution(
            bucketKey = subcategory.bucketKey,
            subcategoryKey = subcategory.key,
            subcategoryLabel = subcategory.label,
            weight = weight,
        )
    }

    return when {
        patterns.any { "overhead" in it || "vertical press" in it } -> listOf(
            contribution("shoulders", 1.0),
            contribution("triceps", 0.5),
        )
        patterns.any { "fly" in it || "horizontal push" in it || "push up" in it || "bench" in it } -> listOf(
            contribution("chest", 1.0),
            contribution("triceps", 0.5),
        )
        patterns.any { "curl" in it } -> listOf(contribution("biceps", 1.0))
        patterns.any { "vertical pull" in it || "pulldown" in it || "pull up" in it } -> listOf(
            contribution("lats", 1.0),
            contribution("biceps", 0.5),
        )
        patterns.any { "row" in it || "horizontal pull" in it } -> listOf(
            contribution("upper_back", 1.0),
            contribution("lats", 0.5),
            contribution("biceps", 0.5),
        )
        patterns.any { "face pull" in it || "reverse fly" in it } -> listOf(
            contribution("rear_delts", 1.0),
            contribution("upper_back", 0.5),
        )
        patterns.any { "hinge" in it || "deadlift" in it || "good morning" in it } -> listOf(
            contribution("hamstrings", 1.0),
            contribution("glutes", 0.5),
        )
        patterns.any { "bridge" in it || "thrust" in it || "abduction" in it } -> listOf(
            contribution("glutes", 1.0),
            contribution("hamstrings", 0.5),
        )
        patterns.any { "squat" in it || "lunge" in it || "step up" in it } -> listOf(
            contribution("quadriceps", 1.0),
            contribution("glutes", 0.5),
        )
        else -> emptyList()
    }
}

private fun normalizeMuscleTargetToken(value: String?): String {
    return value
        .orEmpty()
        .trim()
        .lowercase()
        .replace("_", " ")
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")
}

private fun String.toReadableMuscleTargetLabel(): String {
    return replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
}
