package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.ExerciseDetail
import dev.toastlabs.toastlift.data.ExerciseSummary
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionSet
import dev.toastlabs.toastlift.data.elapsedDurationSeconds
import dev.toastlabs.toastlift.data.pause
import dev.toastlabs.toastlift.data.resume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ActiveSessionWorkoutDetailsTest {
    @Test
    fun activeWorkoutProgressMetrics_reportsCompletedExerciseSetsAndVolumeLabels() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "5", reps = "5", weight = "185", completed = true),
                        SessionSet(setNumber = 2, targetReps = "5", reps = "5", weight = "185", completed = true),
                    ),
                ),
                exercise(
                    "Cable Row",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "10", reps = "10", weight = "120", completed = true),
                        SessionSet(setNumber = 2, targetReps = "10"),
                    ),
                ),
            ),
        )

        val metrics = activeWorkoutProgressMetrics(session)

        assertEquals(1, metrics.completedExercises)
        assertEquals(2, metrics.totalExercises)
        assertEquals(3, metrics.completedSets)
        assertEquals(4, metrics.totalSets)
        assertEquals(3050.0, metrics.completedVolume, 0.001)
        assertEquals("1/2 exercises", metrics.exerciseProgressLabel)
        assertEquals("3/4 sets", metrics.setProgressLabel)
        assertEquals("Volume 3.1k lb", metrics.volumeLabel)
    }

    @Test
    fun activeWorkoutProgressMetrics_averagesCompletionGapsWithinExercisesOnly() {
        val base = Instant.parse("2026-03-23T10:00:00Z")
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    sets = listOf(
                        completedSet(1, base.plusSeconds(0)),
                        completedSet(2, base.plusSeconds(60)),
                        completedSet(3, base.plusSeconds(180)),
                        completedSet(4, base.plusSeconds(360)),
                    ),
                ),
                exercise(
                    "Cable Row",
                    sets = listOf(
                        completedSet(1, base.plusSeconds(1_000)),
                        completedSet(2, base.plusSeconds(1_240)),
                        completedSet(3, base.plusSeconds(1_600)),
                    ),
                ),
            ),
        )

        val metrics = activeWorkoutProgressMetrics(session)

        assertEquals(192, metrics.averageTimeBetweenSetCompletionsSeconds)
    }

    @Test
    fun activeSessionExpectedRepSummary_sumsRepRangesAcrossPlannedSets() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "4-6"),
                        SessionSet(setNumber = 2, targetReps = "4-6"),
                    ),
                ),
                exercise(
                    "Incline Dumbbell Press",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "8-10"),
                        SessionSet(setNumber = 2, targetReps = "8-10"),
                        SessionSet(setNumber = 3, targetReps = "8-10"),
                    ),
                ),
            ),
        )

        assertEquals("32-42 total reps", activeSessionExpectedRepSummary(session))
    }

    @Test
    fun activeSessionExpectedLoadVolume_usesRecommendedWeightsAndReps() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "4-6", recommendedReps = 5, recommendedWeight = "185"),
                        SessionSet(setNumber = 2, targetReps = "4-6", recommendedReps = 5, recommendedWeight = "185"),
                    ),
                ),
                exercise(
                    "Leg Curl",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "10-12", recommendedWeight = "90"),
                    ),
                ),
            ),
        )

        val expectedVolume = requireNotNull(activeSessionExpectedLoadVolume(session))
        assertEquals(2840.0, expectedVolume, 0.001)
    }

    @Test
    fun activeSessionExpectedLoadVolume_returnsNullWithoutWeightTargets() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Push-Up",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "12-15"),
                        SessionSet(setNumber = 2, targetReps = "12-15"),
                    ),
                ),
            ),
        )

        assertNull(activeSessionExpectedLoadVolume(session))
    }

    @Test
    fun activeSessionIntensityLabel_usesFocusKeyWhenHeavyDayIsKnown() {
        val session = session(
            focusKey = "lower_strength",
            exercises = listOf(
                exercise(
                    "Back Squat",
                    sets = listOf(SessionSet(setNumber = 1, targetReps = "8-10")),
                ),
            ),
        )

        assertEquals("Heavy", activeSessionIntensityLabel(session))
    }

    @Test
    fun activeSessionIntensityLabel_fallsBackToRepRangesForManualSessions() {
        val session = session(
            focusKey = null,
            exercises = listOf(
                exercise(
                    "Lateral Raise",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "15-20"),
                        SessionSet(setNumber = 2, targetReps = "15-20"),
                    ),
                ),
            ),
        )

        assertEquals("High reps", activeSessionIntensityLabel(session))
    }

    @Test
    fun activeSessionElapsedDurationSeconds_freezesWhilePaused_andResumesAfterward() {
        val started = Instant.parse("2026-03-23T10:00:00Z")
        val paused = started.plusSeconds(300)
        val resumed = started.plusSeconds(600)
        val session = session(exercises = emptyList()).copy(startedAtUtc = started.toString())
            .pause(paused)

        assertEquals(300, session.elapsedDurationSeconds(started.plusSeconds(420)))

        val resumedSession = session.resume(resumed)

        assertEquals(420, resumedSession.elapsedDurationSeconds(started.plusSeconds(720)))
    }

    @Test
    fun activeWorkoutMuscleRefreshSummary_usesTrainingFreshnessContributions() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "8", completed = true),
                        SessionSet(setNumber = 2, targetReps = "8"),
                    ),
                ),
                exercise(
                    "Cable Row",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "10"),
                        SessionSet(setNumber = 2, targetReps = "10"),
                    ),
                ),
            ),
        )
        val details = mapOf(
            "Bench Press".hashCode().toLong() to detail("Bench Press", target = "Chest", prime = "Chest", secondary = "Triceps"),
            "Cable Row".hashCode().toLong() to detail("Cable Row", target = "Back", prime = "Back"),
        )

        val summary = buildActiveWorkoutMuscleRefreshSummary(
            session = session,
            exerciseDetailsById = details,
            trainingFreshness = null,
        )

        val chest = summary.rows.first { it.key == "chest" }
        val triceps = summary.rows.first { it.key == "triceps" }
        val back = summary.rows.first { it.key == "back" }
        val biceps = summary.rows.first { it.key == "biceps" }

        assertEquals(ActiveWorkoutMuscleRefreshState.Refreshed, chest.state)
        assertEquals(1.0f, chest.progressFraction, 0.001f)
        assertEquals(ActiveWorkoutMuscleRefreshState.Pending, triceps.state)
        assertEquals(0.5f, triceps.progressFraction, 0.001f)
        assertEquals(ActiveWorkoutMuscleRefreshState.Pending, back.state)
        assertEquals(ActiveWorkoutMuscleRefreshState.NotTargeted, biceps.state)
    }

    @Test
    fun filterActiveWorkoutMuscleRefreshRows_keepsDueOverdueNotTargetedMuscles() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Biceps Curl",
                    sets = listOf(SessionSet(setNumber = 1, targetReps = "8")),
                ),
            ),
        )
        val summary = buildActiveWorkoutMuscleRefreshSummary(
            session = session,
            exerciseDetailsById = mapOf(
                "Biceps Curl".hashCode().toLong() to detail("Biceps Curl", target = "Biceps", prime = "Biceps"),
            ),
            trainingFreshness = freshnessSummary(
                mapOf(
                    "chest" to TrainingFreshnessStatus.DueSoon,
                    "biceps" to TrainingFreshnessStatus.DueSoon,
                    "triceps" to TrainingFreshnessStatus.Overdue,
                ),
            ),
        )

        val dueRows = filterActiveWorkoutMuscleRefreshRows(summary.rows, TrainingFreshnessFilter.Due)
        val overdueRows = filterActiveWorkoutMuscleRefreshRows(summary.rows, TrainingFreshnessFilter.Overdue)

        assertEquals(listOf("biceps", "chest", "triceps"), dueRows.map { it.key })
        assertEquals(ActiveWorkoutMuscleRefreshState.Pending, dueRows.first { it.key == "biceps" }.state)
        assertEquals(ActiveWorkoutMuscleRefreshState.NotTargeted, dueRows.first { it.key == "chest" }.state)
        assertEquals(listOf("triceps"), overdueRows.map { it.key })
    }

    private fun session(
        focusKey: String? = "upper_body",
        exercises: List<SessionExercise>,
    ): ActiveSession {
        return ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-23T10:00:00Z",
            focusKey = focusKey,
            exercises = exercises,
        )
    }

    private fun exercise(
        name: String,
        sets: List<SessionSet>,
    ): SessionExercise {
        return SessionExercise(
            exerciseId = name.hashCode().toLong(),
            name = name,
            bodyRegion = "Upper Body",
            targetMuscleGroup = "Chest",
            equipment = "Barbell",
            restSeconds = 90,
            sets = sets,
        )
    }

    private fun completedSet(setNumber: Int, completedAt: Instant): SessionSet {
        return SessionSet(
            setNumber = setNumber,
            targetReps = "8",
            completed = true,
            completedAtUtc = completedAt.toString(),
        )
    }

    private fun detail(
        name: String,
        target: String,
        prime: String? = null,
        secondary: String? = null,
    ): ExerciseDetail {
        return ExerciseDetail(
            summary = ExerciseSummary(
                id = name.hashCode().toLong(),
                name = name,
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

    private fun freshnessSummary(statusByKey: Map<String, TrainingFreshnessStatus>): TrainingFreshnessSummary {
        val now = Instant.parse("2026-03-23T10:00:00Z")
        return TrainingFreshnessSummary(
            thresholdDays = 3,
            dueSoonHours = TRAINING_FRESHNESS_DUE_SOON_HOURS,
            generatedAtUtc = now,
            cardMode = TrainingFreshnessCardMode.DueSoon,
            headline = "",
            supportingText = "",
            bucketRows = emptyList(),
            muscleRows = trainingFreshnessMuscleSlots().map { slot ->
                TrainingFreshnessMuscleRow(
                    key = slot.key,
                    label = slot.label,
                    family = slot.family,
                    status = statusByKey[slot.key] ?: TrainingFreshnessStatus.Fresh,
                    lastStimulusAtUtc = now,
                    hoursSinceStimulus = 0,
                    hoursUntilThreshold = 72,
                    progressFraction = 0f,
                    weightedCompletedSets = 0.0,
                    lastExerciseNames = emptyList(),
                )
            },
        )
    }
}
