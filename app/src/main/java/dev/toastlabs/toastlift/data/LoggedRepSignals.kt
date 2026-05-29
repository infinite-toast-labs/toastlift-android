package dev.toastlabs.toastlift.data

internal fun loggedRepSignalClause(setAlias: String = "ps"): String =
    """
    $setAlias.is_completed = 1
    AND (
        COALESCE($setAlias.actual_reps, 0) > 0
        OR COALESCE(NULLIF(TRIM($setAlias.work_unit_values_json), ''), '{}') != '{}'
    )
    """.trimIndent()

internal fun HistoricalExerciseSet.hasLoggedRepSignal(): Boolean =
    completed && (actualReps ?: 0) > 0

internal fun HistoricalExerciseSet.hasLoggedWorkUnitSignal(): Boolean =
    completed && encodeWorkUnitValues(workUnitValues) != null

internal fun HistoricalExerciseSet.hasHistoricalTrainingSignal(): Boolean =
    hasLoggedRepSignal() || hasLoggedWorkUnitSignal()
