package dev.toastlabs.toastlift.data

internal fun loggedRepSignalClause(setAlias: String = "ps"): String =
    """
    $setAlias.is_completed = 1
    AND COALESCE($setAlias.actual_reps, 0) > 0
    """.trimIndent()

internal fun HistoricalExerciseSet.hasLoggedRepSignal(): Boolean =
    completed && (actualReps ?: 0) > 0
