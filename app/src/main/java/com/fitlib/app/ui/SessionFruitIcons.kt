package com.fitlib.app.ui

import com.fitlib.app.data.ActiveSession
import com.fitlib.app.data.SessionExercise

internal val sessionFruitIcons = listOf(
    "🍎",
    "🍌",
    "🍇",
    "🍍",
    "🍓",
    "🍉",
    "🍑",
    "🍐",
    "🍋",
    "🍒",
    "🍊",
    "🥝",
)

internal fun assignSessionFruitIcons(exercises: List<SessionExercise>): List<SessionExercise> {
    val usedIcons = linkedSetOf<String>()
    var assignedCount = 0
    exercises.forEach { exercise ->
        val existing = exercise.fruitIcon?.takeIf { it.isNotBlank() } ?: return@forEach
        usedIcons += existing
        assignedCount += 1
    }
    return exercises.map { exercise ->
        val existing = exercise.fruitIcon?.takeIf { it.isNotBlank() }
        if (existing != null) {
            exercise
        } else {
            val nextIcon = nextSessionFruitIcon(
                usedIcons = usedIcons,
                assignedCount = assignedCount,
            )
            usedIcons += nextIcon
            assignedCount += 1
            exercise.copy(fruitIcon = nextIcon)
        }
    }
}

internal fun ensureSessionFruitIcons(session: ActiveSession): ActiveSession {
    val updatedExercises = assignSessionFruitIcons(session.exercises)
    return if (updatedExercises == session.exercises) {
        session
    } else {
        session.copy(exercises = updatedExercises)
    }
}

internal fun nextSessionFruitIcon(
    usedIcons: Set<String>,
    assignedCount: Int = usedIcons.size,
): String {
    return sessionFruitIcons.firstOrNull { it !in usedIcons }
        ?: sessionFruitIcons[assignedCount % sessionFruitIcons.size]
}

internal fun sessionExerciseBadgeLabel(
    exercise: SessionExercise,
    fruitIconsEnabled: Boolean,
): String {
    return if (fruitIconsEnabled) {
        exercise.fruitIcon?.takeIf { it.isNotBlank() } ?: equipmentBadgeLabel(exercise.equipment)
    } else {
        equipmentBadgeLabel(exercise.equipment)
    }
}

internal fun sessionExerciseBadgeAccentKey(
    exercise: SessionExercise,
    fruitIconsEnabled: Boolean,
): String {
    return if (fruitIconsEnabled) {
        exercise.fruitIcon?.takeIf { it.isNotBlank() } ?: exercise.equipment
    } else {
        exercise.equipment
    }
}
