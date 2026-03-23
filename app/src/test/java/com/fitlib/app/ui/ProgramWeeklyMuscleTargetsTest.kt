package com.fitlib.app.ui

import com.fitlib.app.data.ExerciseDetail
import com.fitlib.app.data.ExerciseSummary
import com.fitlib.app.data.ThemePreference
import com.fitlib.app.data.UserProfile
import com.fitlib.app.data.WeeklyMuscleTargetWorkoutRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProgramWeeklyMuscleTargetsTest {

    @Test
    fun buildWeeklyMuscleTargetSummary_aggregatesAllCompletedWorkoutsIntoWeeklyProgress() {
        val summary = buildWeeklyMuscleTargetSummary(
            profile = profile(),
            rows = listOf(
                row("2026-03-16T10:00:00Z", 101L, 4),
                row("2026-03-17T10:00:00Z", 102L, 3),
                row("2026-03-18T10:00:00Z", 201L, 5),
                row("2026-03-19T10:00:00Z", 301L, 4),
                row("2026-03-20T10:00:00Z", 302L, 3),
                row("2026-03-10T10:00:00Z", 202L, 3),
                row("2026-03-12T10:00:00Z", 303L, 2),
            ),
            exerciseDetailsById = mapOf(
                101L to detail(exerciseId = 101L, target = "Chest", prime = "Chest", secondary = "Triceps"),
                102L to detail(exerciseId = 102L, target = "Shoulders", prime = "Shoulders", secondary = "Triceps"),
                201L to detail(exerciseId = 201L, target = "Back", prime = "Lats", secondary = "Biceps"),
                202L to detail(exerciseId = 202L, target = "Biceps", prime = "Biceps"),
                301L to detail(exerciseId = 301L, target = "Quadriceps", prime = "Quadriceps", secondary = "Glutes"),
                302L to detail(exerciseId = 302L, target = "Glutes", prime = "Glutes", secondary = "Hamstrings"),
                303L to detail(exerciseId = 303L, target = "Hamstrings", prime = "Hamstrings"),
            ),
            now = LocalDate.of(2026, 3, 20),
            zoneId = ZoneOffset.UTC,
            historyLimit = 2,
        )

        assertEquals("15 Mar - 21 Mar", weeklyMuscleTargetRangeLabel(summary.range))
        assertEquals(28.5, summary.completedSets, 0.001)
        assertEquals(100.2, summary.targetSets, 0.001)
        assertEquals(2, summary.history.size)
        assertEquals(summary.overallCompletionRatio, summary.history.last().completionRatio, 0.001)
        assertEquals(5.0 / 100.2, summary.history.first().completionRatio, 0.001)

        val push = summary.groupSummaries.first { it.key == "push" }
        assertEquals(10.5, push.completedSets, 0.001)
        assertEquals(35.4, push.targetSets, 0.001)

        val pull = summary.groupSummaries.first { it.key == "pull" }
        assertEquals(7.5, pull.completedSets, 0.001)
        assertEquals(23.4, pull.targetSets, 0.001)

        val legs = summary.groupSummaries.first { it.key == "legs" }
        assertEquals(10.5, legs.completedSets, 0.001)
        assertEquals(41.4, legs.targetSets, 0.001)
    }

    @Test
    fun buildWeeklyMuscleTargetSummary_keepsTargetsEvenWithoutWorkoutRows() {
        val summary = buildWeeklyMuscleTargetSummary(
            profile = profile(),
            rows = emptyList(),
            exerciseDetailsById = emptyMap(),
            now = LocalDate.of(2026, 3, 20),
            zoneId = ZoneOffset.UTC,
            historyLimit = 1,
        )

        assertEquals(0.0, summary.completedSets, 0.001)
        assertTrue(summary.targetSets > 0.0)
        assertEquals(1, summary.history.size)
        assertEquals(0.0, summary.overallCompletionRatio, 0.001)
    }

    private fun row(completedAtUtc: String, exerciseId: Long, completedSetCount: Int): WeeklyMuscleTargetWorkoutRow {
        return WeeklyMuscleTargetWorkoutRow(
            completedAtUtc = completedAtUtc,
            exerciseId = exerciseId,
            completedSetCount = completedSetCount,
        )
    }

    private fun profile(): UserProfile {
        return UserProfile(
            goal = "Hypertrophy",
            experience = "Intermediate",
            durationMinutes = 60,
            weeklyFrequency = 4,
            splitProgramId = 3L,
            units = "imperial",
            activeLocationModeId = 1L,
            workoutStyle = "balanced",
            themePreference = ThemePreference.Dark,
        )
    }

    private fun detail(
        exerciseId: Long,
        target: String,
        prime: String? = null,
        secondary: String? = null,
    ): ExerciseDetail {
        return ExerciseDetail(
            summary = ExerciseSummary(
                id = exerciseId,
                name = "Exercise $exerciseId",
                difficulty = "Intermediate",
                bodyRegion = "Upper Body",
                targetMuscleGroup = target,
                equipment = "Barbell",
                secondaryEquipment = null,
                mechanics = "Compound",
                favorite = false,
            ),
            notes = null,
            primeMover = prime,
            secondaryMuscle = secondary,
            tertiaryMuscle = null,
            posture = "Standing",
            laterality = "Bilateral",
            classification = "Compound",
            movementPatterns = emptyList(),
            planesOfMotion = emptyList(),
            demoUrl = null,
            explanationUrl = null,
            synonyms = emptyList(),
        )
    }
}
