package com.fitlib.app.ui

internal val equipmentBadgeLabels: Map<String, String> = linkedMapOf(
    "Ab Wheel" to "AW",
    "Barbell" to "BB",
    "Battle Ropes" to "BR",
    "Bench (Decline)" to "BD",
    "Bench (Flat)" to "BF",
    "Bench (Incline)" to "BI",
    "Bodyweight" to "BW",
    "Bulgarian Bag" to "BG",
    "Cable" to "CA",
    "Climbing Rope" to "CR",
    "Clubbell" to "CU",
    "Dumbbell" to "DB",
    "EZ Bar" to "EZ",
    "Gravity Boots" to "GB",
    "Gymnastic Rings" to "GR",
    "Heavy Sandbag" to "HS",
    "Indian Club" to "IC",
    "Kettlebell" to "KB",
    "Landmine" to "LM",
    "Macebell" to "MC",
    "Machine" to "MA",
    "Medicine Ball" to "MB",
    "Miniband" to "MI",
    "Parallette Bars" to "PB",
    "Plyo Box" to "PX",
    "Pull Up Bar" to "PU",
    "Resistance Band" to "RB",
    "Sandbag" to "SB",
    "Slam Ball" to "SL",
    "Slant Board" to "SN",
    "Sled" to "SD",
    "Sledge Hammer" to "SH",
    "Sliders" to "SR",
    "Stability Ball" to "ST",
    "Superband" to "SU",
    "Suspension Trainer" to "TR",
    "Tire" to "TI",
    "Trap Bar" to "TB",
    "Wall Ball" to "WB",
    "Weight Plate" to "WP",
)

private val equipmentBadgeStopWords = setOf("a", "an", "and", "for", "of", "the", "to", "with")

internal fun equipmentBadgeLabel(equipmentName: String?): String {
    val normalized = equipmentName.orEmpty().trim()
    if (normalized.isBlank()) return "EX"
    equipmentBadgeLabels[normalized]?.let { return it }

    val tokens = normalized
        .replace(Regex("[()\\[\\]{}]"), " ")
        .split(Regex("[^A-Za-z0-9]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.lowercase() in equipmentBadgeStopWords }

    return when {
        tokens.size >= 2 -> "${tokens[0].first()}${tokens[1].first()}".uppercase()
        tokens.size == 1 -> tokens[0].filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { "EX" }
        else -> normalized.filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { "EX" }
    }
}
