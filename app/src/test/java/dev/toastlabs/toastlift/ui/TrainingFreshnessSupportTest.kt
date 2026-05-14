package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ExerciseDetail
import dev.toastlabs.toastlift.data.ExerciseSummary
import dev.toastlabs.toastlift.data.ThemePreference
import dev.toastlabs.toastlift.data.UserProfile
import dev.toastlabs.toastlift.data.WeeklyMuscleTargetWorkoutRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TrainingFreshnessSupportTest {

    @Test
    fun buildTrainingFreshnessSummary_marksBucketFreshBeforeDueSoonWindow() {
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(row("2026-05-12T01:00:00Z", 101L, 3)),
            exerciseDetailsById = mapOf(101L to detail(101L, target = "Chest", prime = "Chest", secondary = "Triceps")),
            nowUtc = Instant.parse("2026-05-13T23:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        val upper = summary.bucketRows.first { it.key == "upper" }
        assertEquals(TrainingFreshnessStatus.Fresh, upper.status)
        assertEquals(46L, upper.hoursSinceStimulus)
    }

    @Test
    fun buildTrainingFreshnessSummary_marksBucketDueSoonInFinalTwentyFourHours() {
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(row("2026-05-12T00:00:00Z", 101L, 3)),
            exerciseDetailsById = mapOf(101L to detail(101L, target = "Chest", prime = "Chest")),
            nowUtc = Instant.parse("2026-05-14T12:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        val upper = summary.bucketRows.first { it.key == "upper" }
        assertEquals(TrainingFreshnessStatus.DueSoon, upper.status)
        assertEquals(12L, upper.hoursUntilThreshold)
    }

    @Test
    fun buildTrainingFreshnessSummary_marksBucketOverdueAtThreshold() {
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(row("2026-05-12T00:00:00Z", 101L, 3)),
            exerciseDetailsById = mapOf(101L to detail(101L, target = "Chest", prime = "Chest")),
            nowUtc = Instant.parse("2026-05-15T00:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        val upper = summary.bucketRows.first { it.key == "upper" }
        assertEquals(TrainingFreshnessStatus.Overdue, upper.status)
        assertEquals(TrainingFreshnessCardMode.Overdue, summary.cardMode)
    }

    @Test
    fun buildTrainingFreshnessSummary_ignoresSecondaryOnlyWorkBelowBucketResetThreshold() {
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(row("2026-05-14T00:00:00Z", 101L, 1)),
            exerciseDetailsById = mapOf(101L to detail(101L, target = "Chest", prime = "Chest", secondary = "Triceps")),
            nowUtc = Instant.parse("2026-05-14T12:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        val upper = summary.bucketRows.first { it.key == "upper" }
        val triceps = summary.muscleRows.first { it.key == "triceps" }
        assertEquals(TrainingFreshnessStatus.Untracked, upper.status)
        assertEquals(TrainingFreshnessStatus.Untracked, triceps.status)
    }

    @Test
    fun buildTrainingFreshnessSummary_resetsDirectTricepsStimulus() {
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(row("2026-05-14T00:00:00Z", 201L, 1)),
            exerciseDetailsById = mapOf(201L to detail(201L, target = "Triceps", prime = "Triceps")),
            nowUtc = Instant.parse("2026-05-14T12:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        val triceps = summary.muscleRows.first { it.key == "triceps" }
        assertEquals(TrainingFreshnessStatus.Fresh, triceps.status)
        assertTrue(triceps.lastExerciseNames.contains("Exercise 201"))
    }

    @Test
    fun filterTrainingFreshnessMuscles_filtersDueAndSortsByUrgency() {
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(
                row("2026-05-12T00:00:00Z", 101L, 3),
                row("2026-05-14T20:00:00Z", 301L, 3),
            ),
            exerciseDetailsById = mapOf(
                101L to detail(101L, target = "Chest", prime = "Chest"),
                301L to detail(301L, target = "Quadriceps", prime = "Quadriceps"),
            ),
            nowUtc = Instant.parse("2026-05-15T00:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        val rows = filterTrainingFreshnessMuscles(
            rows = summary.muscleRows,
            filter = TrainingFreshnessFilter.Due,
            sort = TrainingFreshnessSort.MostUrgent,
        )

        assertEquals("Chest", rows.first().label)
        assertEquals(TrainingFreshnessStatus.Overdue, rows.first().status)
    }

    private fun row(completedAtUtc: String, exerciseId: Long, completedSetCount: Int): WeeklyMuscleTargetWorkoutRow {
        return WeeklyMuscleTargetWorkoutRow(
            completedAtUtc = completedAtUtc,
            exerciseId = exerciseId,
            completedSetCount = completedSetCount,
        )
    }

    private fun profile(thresholdDays: Int = 3): UserProfile {
        return UserProfile(
            goal = "Hypertrophy",
            experience = "Intermediate",
            durationMinutes = 60,
            weeklyFrequency = 4,
            splitProgramId = 2L,
            units = "imperial",
            activeLocationModeId = 1L,
            workoutStyle = "balanced",
            themePreference = ThemePreference.Dark,
            trainingFreshnessThresholdDays = thresholdDays,
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
