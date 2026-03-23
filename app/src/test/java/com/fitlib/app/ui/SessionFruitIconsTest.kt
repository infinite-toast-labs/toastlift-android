package com.fitlib.app.ui

import com.fitlib.app.data.ActiveSession
import com.fitlib.app.data.SessionExercise
import com.fitlib.app.data.SessionSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFruitIconsTest {
    @Test
    fun assignSessionFruitIcons_assignsTwelveUniqueFruitIconsInOrder() {
        val assigned = assignSessionFruitIcons(
            (1L..12L).map { id -> exercise(id = id, name = "Exercise $id") },
        )

        assertEquals(sessionFruitIcons, assigned.map { it.fruitIcon })
        assertEquals(12, assigned.mapNotNull { it.fruitIcon }.distinct().size)
    }

    @Test
    fun assignSessionFruitIcons_preservesExistingAssignmentsAndFillsOnlyMissingOnes() {
        val exercises = listOf(
            exercise(id = 1L, name = "Bench Press", fruitIcon = "🍎"),
            exercise(id = 2L, name = "Cable Row", fruitIcon = "🍇"),
            exercise(id = 3L, name = "Lateral Raise"),
            exercise(id = 4L, name = "Leg Press"),
        )

        val assigned = assignSessionFruitIcons(exercises)

        assertEquals("🍎", assigned[0].fruitIcon)
        assertEquals("🍇", assigned[1].fruitIcon)
        assertEquals("🍌", assigned[2].fruitIcon)
        assertEquals("🍍", assigned[3].fruitIcon)
    }

    @Test
    fun ensureSessionFruitIcons_updatesActiveSessionWithoutChangingExistingIcons() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-23T10:00:00Z",
            exercises = listOf(
                exercise(id = 11L, name = "Bench Press", fruitIcon = "🍉"),
                exercise(id = 22L, name = "Cable Row"),
            ),
        )

        val updated = ensureSessionFruitIcons(session)

        assertEquals("🍉", updated.exercises[0].fruitIcon)
        assertEquals("🍎", updated.exercises[1].fruitIcon)
    }

    @Test
    fun sessionExerciseBadgeLabel_usesFruitIconOnlyWhenToggleIsEnabled() {
        val exercise = exercise(
            id = 1L,
            name = "Bench Press",
            equipment = "Barbell",
            fruitIcon = "🍓",
        )

        assertEquals("🍓", sessionExerciseBadgeLabel(exercise, fruitIconsEnabled = true))
        assertEquals("BB", sessionExerciseBadgeLabel(exercise, fruitIconsEnabled = false))
        assertEquals("🍓", sessionExerciseBadgeAccentKey(exercise, fruitIconsEnabled = true))
        assertEquals("Barbell", sessionExerciseBadgeAccentKey(exercise, fruitIconsEnabled = false))
    }

    @Test
    fun nextSessionFruitIcon_wrapsAfterAllFruitIconsAreUsed() {
        val wrapped = nextSessionFruitIcon(
            usedIcons = sessionFruitIcons.toSet(),
            assignedCount = sessionFruitIcons.size + 1,
        )

        assertTrue(wrapped in sessionFruitIcons)
        assertEquals("🍌", wrapped)
    }

    private fun exercise(
        id: Long,
        name: String,
        equipment: String = "Barbell",
        fruitIcon: String? = null,
    ): SessionExercise {
        return SessionExercise(
            exerciseId = id,
            name = name,
            bodyRegion = "Upper Body",
            targetMuscleGroup = "Chest",
            equipment = equipment,
            restSeconds = 90,
            sets = listOf(SessionSet(setNumber = 1, targetReps = "8-10")),
            fruitIcon = fruitIcon,
        )
    }
}
