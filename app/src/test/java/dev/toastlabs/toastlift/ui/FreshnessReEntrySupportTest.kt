package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ExerciseDetail
import dev.toastlabs.toastlift.data.ExerciseSummary
import dev.toastlabs.toastlift.data.HistorySummary
import dev.toastlabs.toastlift.data.ThemePreference
import dev.toastlabs.toastlift.data.UserProfile
import dev.toastlabs.toastlift.data.WeeklyMuscleTargetWorkoutRow
import dev.toastlabs.toastlift.data.WorkoutExercise
import dev.toastlabs.toastlift.data.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class FreshnessReEntrySupportTest {

    @Test
    fun buildFreshnessReEntryState_usesFullReEntryForEightDayGapAndBothBucketsOverdue() {
        val now = Instant.parse("2026-06-17T12:00:00Z")
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(
                row("2026-06-09T12:00:00Z", 101L, 3),
                row("2026-06-09T12:00:00Z", 301L, 3),
            ),
            exerciseDetailsById = mapOf(
                101L to detail(101L, target = "Chest", prime = "Chest"),
                301L to detail(301L, target = "Hamstrings", prime = "Hamstrings"),
            ),
            nowUtc = now,
            zoneId = ZoneOffset.UTC,
        )

        val state = requireNotNull(
            buildFreshnessReEntryState(
                profile = profile(),
                history = listOf(history("2026-06-09T12:30:00Z")),
                trainingFreshness = summary,
                locationLabel = "Home",
                nowUtc = now,
                zoneId = ZoneOffset.UTC,
            ),
        )

        assertEquals(FreshnessReEntryMode.ReEntry, state.mode)
        assertEquals(8L, state.gapDays)
        assertEquals("full_body", state.focusKey)
        assertEquals("Home", state.locationLabel)
        assertEquals("Start Re-Entry Workout", state.ctaLabel)
        assertTrue(state.targetLabels.contains("Chest"))
        assertTrue(state.targetLabels.contains("Hamstrings"))
    }

    @Test
    fun buildFreshnessReEntryState_usesMaintenanceForTwoDayGap() {
        val now = Instant.parse("2026-06-17T12:00:00Z")
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(row("2026-06-15T00:00:00Z", 101L, 3)),
            exerciseDetailsById = mapOf(101L to detail(101L, target = "Chest", prime = "Chest")),
            nowUtc = now,
            zoneId = ZoneOffset.UTC,
        )

        val state = requireNotNull(
            buildFreshnessReEntryState(
                profile = profile(),
                history = listOf(history("2026-06-15T10:00:00Z")),
                trainingFreshness = summary,
                locationLabel = "Gym",
                nowUtc = now,
                zoneId = ZoneOffset.UTC,
            ),
        )

        assertEquals(FreshnessReEntryMode.MaintenanceSave, state.mode)
        assertEquals("Start Maintenance Session", state.ctaLabel)
        assertEquals("Gym", state.locationLabel)
        assertTrue(state.suggestedDurationMinutes <= 12)
    }

    @Test
    fun buildFreshnessReEntryState_usesWorkoutStartForGapWhenFinishedToday() {
        val now = Instant.parse("2026-06-19T12:00:00Z")
        val startedAt = "2026-06-09T12:00:00Z"
        val summary = buildTrainingFreshnessSummary(
            profile = profile(),
            rows = listOf(
                row(
                    startedAtUtc = startedAt,
                    completedAtUtc = now.toString(),
                    exerciseId = 101L,
                    completedSetCount = 3,
                ),
            ),
            exerciseDetailsById = mapOf(101L to detail(101L, target = "Chest", prime = "Chest")),
            nowUtc = now,
            zoneId = ZoneOffset.UTC,
        )

        val state = requireNotNull(
            buildFreshnessReEntryState(
                profile = profile(),
                history = listOf(history(completedAtUtc = now.toString(), startedAtUtc = startedAt)),
                trainingFreshness = summary,
                locationLabel = "Home",
                nowUtc = now,
                zoneId = ZoneOffset.UTC,
            ),
        )

        assertEquals(10L, state.gapDays)
        assertEquals(FreshnessReEntryMode.ReEntry, state.mode)
    }

    @Test
    fun shapeFreshnessReEntryWorkout_marksOriginAndTrimsVolume() {
        val state = FreshnessReEntryState(
            mode = FreshnessReEntryMode.ReEntry,
            gapDays = 8,
            locationLabel = "Home",
            focusKey = "upper_body",
            suggestedDurationMinutes = 16,
            headline = "8 days since your last session.",
            supportingText = "Restart small.",
            ctaLabel = "Start Re-Entry Workout",
            targetLabels = listOf("Chest"),
        )
        val workout = WorkoutPlan(
            title = "Home Upper",
            subtitle = "Generated",
            locationModeId = 1L,
            estimatedMinutes = 45,
            origin = "generated",
            focusKey = "upper_body",
            exercises = listOf(
                exercise(1L, "Cable Row", "Back", sets = 4),
                exercise(2L, "Push-Up", "Chest", sets = 4),
                exercise(3L, "Overhead Press", "Shoulders", sets = 3),
                exercise(4L, "Triceps Extension", "Triceps", sets = 3),
            ),
        )

        val shaped = shapeFreshnessReEntryWorkout(workout, state)

        assertEquals(FRESHNESS_REENTRY_ORIGIN, shaped.origin)
        assertEquals("Home Re-Entry", shaped.title)
        assertEquals(16, shaped.estimatedMinutes)
        assertTrue(shaped.exercises.size <= 3)
        assertTrue(shaped.exercises.all { it.sets <= 3 })
        assertTrue(shaped.exercises.any { it.targetMuscleGroup == "Chest" })
        assertTrue(shaped.exercises.all { it.overloadStrategy == "HOLD_STEADY" })
    }

    @Test
    fun currentReturnStreak_countsNewestConsecutiveReEntryWorkouts() {
        val streak = currentReturnStreak(
            listOf(
                history("2026-06-17T12:00:00Z", origin = FRESHNESS_REENTRY_ORIGIN),
                history("2026-06-16T12:00:00Z", origin = FRESHNESS_REENTRY_ORIGIN),
                history("2026-06-12T12:00:00Z", origin = "generated"),
                history("2026-06-10T12:00:00Z", origin = FRESHNESS_REENTRY_ORIGIN),
            ),
        )

        assertEquals(2, streak)
    }

    @Test
    fun currentReturnStreak_fallsBackToCompletionForMalformedImportedStart() {
        val streak = currentReturnStreak(
            listOf(
                history(
                    completedAtUtc = "2026-06-17T12:00:00Z",
                    startedAtUtc = "!malformed",
                    origin = FRESHNESS_REENTRY_ORIGIN,
                ),
                history("2026-06-16T12:00:00Z", origin = "generated"),
                history("2026-06-15T12:00:00Z", origin = FRESHNESS_REENTRY_ORIGIN),
            ),
        )

        assertEquals(1, streak)
    }

    private fun profile(): UserProfile {
        return UserProfile(
            goal = "Hypertrophy",
            experience = "Intermediate",
            durationMinutes = 45,
            weeklyFrequency = 4,
            splitProgramId = 2L,
            units = "imperial",
            activeLocationModeId = 1L,
            workoutStyle = "balanced",
            themePreference = ThemePreference.Dark,
            trainingFreshnessMinimumBucketExercises = 1,
        )
    }

    private fun row(
        startedAtUtc: String,
        exerciseId: Long,
        completedSetCount: Int,
        completedAtUtc: String = startedAtUtc,
    ): WeeklyMuscleTargetWorkoutRow {
        return WeeklyMuscleTargetWorkoutRow(
            startedAtUtc = startedAtUtc,
            completedAtUtc = completedAtUtc,
            exerciseId = exerciseId,
            completedSetCount = completedSetCount,
        )
    }

    private fun history(
        completedAtUtc: String,
        origin: String = "generated",
        startedAtUtc: String = completedAtUtc,
    ): HistorySummary {
        return HistorySummary(
            id = completedAtUtc.hashCode().toLong(),
            title = "Workout",
            origin = origin,
            completedAtUtc = completedAtUtc,
            startedAtUtc = startedAtUtc,
            durationSeconds = 1200,
            totalVolume = 1000.0,
            exerciseCount = 2,
            exerciseNames = listOf("Exercise"),
        )
    }

    private fun exercise(
        id: Long,
        name: String,
        target: String,
        sets: Int,
    ): WorkoutExercise {
        return WorkoutExercise(
            exerciseId = id,
            name = name,
            bodyRegion = "Upper Body",
            targetMuscleGroup = target,
            equipment = "Bodyweight",
            sets = sets,
            repRange = "8-12",
            restSeconds = 75,
            rationale = "Generated",
        )
    }

    private fun detail(
        exerciseId: Long,
        target: String,
        prime: String? = null,
    ): ExerciseDetail {
        return ExerciseDetail(
            summary = ExerciseSummary(
                id = exerciseId,
                name = "Exercise $exerciseId",
                difficulty = "Intermediate",
                bodyRegion = if (target == "Hamstrings") "Lower Body" else "Upper Body",
                targetMuscleGroup = target,
                equipment = "Bodyweight",
                secondaryEquipment = null,
                mechanics = "Compound",
                favorite = false,
            ),
            notes = null,
            primeMover = prime,
            secondaryMuscle = null,
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
