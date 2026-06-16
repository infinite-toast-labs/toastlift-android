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
    fun mapTrainingFreshnessMuscleSlot_avoidsUpperBodyFalsePositivesForLowerBodyAnatomy() {
        assertEquals("abductors", requireNotNull(mapTrainingFreshnessMuscleSlot("Tensor Fasciae Latae")).key)
        assertEquals("hamstrings", requireNotNull(mapTrainingFreshnessMuscleSlot("Biceps Femoris")).key)
        assertEquals("back", requireNotNull(mapTrainingFreshnessMuscleSlot("Latissimus Dorsi")).key)
        assertEquals("biceps", requireNotNull(mapTrainingFreshnessMuscleSlot("Biceps Brachii")).key)
        assertEquals("forearms", requireNotNull(mapTrainingFreshnessMuscleSlot("Loaded Brachioradialis")).key)
    }

    @Test
    fun buildTrainingFreshnessSummary_marksBucketFreshBeforeDueSoonWindow() {
        val summary = buildTrainingFreshnessSummary(
            profile = profile(minBucketExercises = 1),
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
            profile = profile(minBucketExercises = 1),
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
            profile = profile(minBucketExercises = 1),
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
    fun buildTrainingFreshnessSummary_requiresDistinctExercisesForUpperAndLowerBuckets() {
        val completedAt = "2026-05-14T00:00:00Z"
        val now = Instant.parse("2026-05-14T12:00:00Z")

        assertBucketStatuses(
            rows = exerciseRows(completedAt, upperCount = 7, lowerCount = 2),
            details = exerciseDetails(upperCount = 7, lowerCount = 2),
            now = now,
            expectedUpper = TrainingFreshnessStatus.Fresh,
            expectedLower = TrainingFreshnessStatus.Untracked,
        )
        assertBucketStatuses(
            rows = exerciseRows(completedAt, upperCount = 3, lowerCount = 3),
            details = exerciseDetails(upperCount = 3, lowerCount = 3),
            now = now,
            expectedUpper = TrainingFreshnessStatus.Fresh,
            expectedLower = TrainingFreshnessStatus.Fresh,
        )
        assertBucketStatuses(
            rows = exerciseRows(completedAt, upperCount = 2, lowerCount = 1),
            details = exerciseDetails(upperCount = 2, lowerCount = 1),
            now = now,
            expectedUpper = TrainingFreshnessStatus.Untracked,
            expectedLower = TrainingFreshnessStatus.Untracked,
        )
        assertBucketStatuses(
            rows = exerciseRows(completedAt, upperCount = 2, lowerCount = 5),
            details = exerciseDetails(upperCount = 2, lowerCount = 5),
            now = now,
            expectedUpper = TrainingFreshnessStatus.Untracked,
            expectedLower = TrainingFreshnessStatus.Fresh,
        )
    }

    @Test
    fun buildTrainingFreshnessSummary_defaultRequiresTwoDistinctExercisesForBucketRefresh() {
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(
                row("2026-05-14T00:00:00Z", 101L, 3),
                row("2026-05-14T00:00:00Z", 102L, 3),
                row("2026-05-14T00:00:00Z", 301L, 3),
            ),
            exerciseDetailsById = mapOf(
                101L to detail(101L, target = "Chest", prime = "Chest"),
                102L to detail(102L, target = "Back", prime = "Back"),
                301L to detail(301L, target = "Quadriceps", prime = "Quadriceps"),
            ),
            nowUtc = Instant.parse("2026-05-14T12:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(TrainingFreshnessStatus.Fresh, summary.bucketRows.first { it.key == "upper" }.status)
        assertEquals(TrainingFreshnessStatus.Untracked, summary.bucketRows.first { it.key == "lower" }.status)
    }

    @Test
    fun buildTrainingFreshnessPenaltySignals_coalescesUpperAndLowerIntoOneDailyPenalty() {
        val completedAt = "2026-05-01T00:00:00Z"
        val signals = buildTrainingFreshnessPenaltySignals(
            profile = profile(minBucketExercises = 1),
            rows = listOf(
                row(completedAt, 101L, 3),
                row(completedAt, 301L, 3),
            ),
            exerciseDetailsById = mapOf(
                101L to detail(101L, target = "Chest", prime = "Chest"),
                301L to detail(301L, target = "Quadriceps", prime = "Quadriceps"),
            ),
            nowUtc = Instant.parse("2026-05-05T00:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(1, signals.size)
        assertEquals("2026-05-05T00:00:00Z", signals.single().occurredAtUtc)
        assertEquals(setOf("upper", "lower"), signals.single().familyKeys)
    }

    @Test
    fun buildTrainingFreshnessPenaltySignals_continuesEveryTwentyFourHoursUntilRefreshed() {
        val signals = buildTrainingFreshnessPenaltySignals(
            profile = profile(minBucketExercises = 1),
            rows = listOf(row("2026-05-01T00:00:00Z", 101L, 3)),
            exerciseDetailsById = mapOf(101L to detail(101L, target = "Chest", prime = "Chest")),
            nowUtc = Instant.parse("2026-05-06T00:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(listOf("2026-05-05T00:00:00Z", "2026-05-06T00:00:00Z"), signals.map { it.occurredAtUtc })
        assertTrue(signals.all { it.familyKeys == setOf("upper") })
    }

    @Test
    fun buildTrainingFreshnessPenaltySignals_stopsWhenBucketRefreshesBeforePenalty() {
        val signals = buildTrainingFreshnessPenaltySignals(
            profile = profile(minBucketExercises = 1),
            rows = listOf(
                row("2026-05-01T00:00:00Z", 101L, 3),
                row("2026-05-04T12:00:00Z", 102L, 3),
            ),
            exerciseDetailsById = mapOf(
                101L to detail(101L, target = "Chest", prime = "Chest"),
                102L to detail(102L, target = "Back", prime = "Back"),
            ),
            nowUtc = Instant.parse("2026-05-06T00:00:00Z"),
            zoneId = ZoneOffset.UTC,
        )

        assertTrue(signals.isEmpty())
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

    private fun assertBucketStatuses(
        rows: List<WeeklyMuscleTargetWorkoutRow>,
        details: Map<Long, ExerciseDetail>,
        now: Instant,
        expectedUpper: TrainingFreshnessStatus,
        expectedLower: TrainingFreshnessStatus,
    ) {
        val summary = buildTrainingFreshnessSummary(
            profile = profile(minBucketExercises = 3),
            rows = rows,
            exerciseDetailsById = details,
            nowUtc = now,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(expectedUpper, summary.bucketRows.first { it.key == "upper" }.status)
        assertEquals(expectedLower, summary.bucketRows.first { it.key == "lower" }.status)
    }

    private fun exerciseRows(
        completedAtUtc: String,
        upperCount: Int,
        lowerCount: Int,
    ): List<WeeklyMuscleTargetWorkoutRow> {
        return (1..upperCount).map { index -> row(completedAtUtc, 100L + index, 1) } +
            (1..lowerCount).map { index -> row(completedAtUtc, 300L + index, 1) }
    }

    private fun exerciseDetails(
        upperCount: Int,
        lowerCount: Int,
    ): Map<Long, ExerciseDetail> {
        return ((1..upperCount).map { index ->
            val exerciseId = 100L + index
            exerciseId to detail(exerciseId, target = "Chest", prime = "Chest")
        } + (1..lowerCount).map { index ->
            val exerciseId = 300L + index
            exerciseId to detail(exerciseId, target = "Quadriceps", prime = "Quadriceps")
        }).toMap()
    }

    private fun row(completedAtUtc: String, exerciseId: Long, completedSetCount: Int): WeeklyMuscleTargetWorkoutRow {
        return WeeklyMuscleTargetWorkoutRow(
            completedAtUtc = completedAtUtc,
            exerciseId = exerciseId,
            completedSetCount = completedSetCount,
        )
    }

    private fun profile(
        thresholdDays: Int = 3,
        minBucketExercises: Int = 2,
    ): UserProfile {
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
            trainingFreshnessMinimumBucketExercises = minBucketExercises,
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
