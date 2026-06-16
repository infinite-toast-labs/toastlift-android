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
import org.junit.Assert.assertTrue
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
    fun activeWorkoutMuscleTargetSummary_keepsUnplannedBucketsAndSubcategoriesVisible() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "8", completed = true),
                        SessionSet(setNumber = 2, targetReps = "8"),
                    ),
                ),
            ),
        )
        val summary = buildActiveWorkoutMuscleTargetCoverageSummary(
            session = session,
            exerciseDetailsById = mapOf(
                "Bench Press".hashCode().toLong() to detail(
                    "Bench Press",
                    target = "Chest",
                    prime = "Chest",
                    secondary = "Triceps",
                ),
            ),
        )

        assertEquals(listOf("push", "pull", "legs"), summary.bucketRows.map { it.key })
        assertEquals(ActiveWorkoutMuscleTargetState.InProgress, summary.bucketRows.first { it.key == "push" }.state)
        assertEquals(ActiveWorkoutMuscleTargetState.NotPlanned, summary.bucketRows.first { it.key == "pull" }.state)
        assertEquals(ActiveWorkoutMuscleTargetState.NotPlanned, summary.bucketRows.first { it.key == "legs" }.state)

        val pushSubcategoryStates = summary.bucketRows.first { it.key == "push" }
            .subcategories
            .associate { it.key to it.state }
        assertTrue("chest should be present", pushSubcategoryStates.containsKey("chest"))
        assertTrue("shoulders should be present", pushSubcategoryStates.containsKey("shoulders"))
        assertTrue("triceps should be present", pushSubcategoryStates.containsKey("triceps"))
        assertEquals(ActiveWorkoutMuscleTargetState.InProgress, pushSubcategoryStates.getValue("chest"))
        assertEquals(ActiveWorkoutMuscleTargetState.NotPlanned, pushSubcategoryStates.getValue("shoulders"))

        val legSubcategoryStates = summary.bucketRows.first { it.key == "legs" }
            .subcategories
            .associate { it.key to it.state }
        assertEquals(ActiveWorkoutMuscleTargetState.NotPlanned, legSubcategoryStates.getValue("quadriceps"))
        assertEquals(ActiveWorkoutMuscleTargetState.NotPlanned, legSubcategoryStates.getValue("hamstrings"))
        assertEquals(ActiveWorkoutMuscleTargetState.NotPlanned, legSubcategoryStates.getValue("glutes"))
    }

    @Test
    fun activeWorkoutMuscleTargetSpotlightRows_showCurrentExerciseTargetImpact() {
        val exercise = exercise(
            "Machine Shoulder Press",
            target = "Shoulders",
            sets = listOf(
                SessionSet(setNumber = 1, targetReps = "8", completed = true),
                SessionSet(setNumber = 2, targetReps = "8"),
                SessionSet(setNumber = 3, targetReps = "8"),
            ),
        )
        val rows = activeWorkoutMuscleTargetSpotlightRows(
            exercise = exercise,
            detail = detail(
                "Machine Shoulder Press",
                target = "Shoulders",
                prime = "Anterior Deltoids",
                secondary = "Triceps Brachii",
            ),
        )

        assertEquals(listOf("shoulders", "triceps", "front_delts"), rows.map { it.key })
        assertEquals(1.0, rows.first { it.key == "shoulders" }.completedWeightedSets, 0.001)
        assertEquals(3.0, rows.first { it.key == "shoulders" }.plannedWeightedSets, 0.001)
        assertEquals(0.5, rows.first { it.key == "triceps" }.completedWeightedSets, 0.001)
    }

    @Test
    fun activeWorkoutMuscleTargetSpotlightRows_rollBackChildrenIntoBack() {
        val exercise = exercise(
            "Cable Row",
            target = "Back",
            sets = listOf(
                SessionSet(setNumber = 1, targetReps = "10", completed = true),
                SessionSet(setNumber = 2, targetReps = "10", completed = true),
            ),
        )
        val rows = activeWorkoutMuscleTargetSpotlightRows(
            exercise = exercise,
            detail = detail(
                "Cable Row",
                target = "Back",
                prime = "Latissimus Dorsi",
                secondary = "Rhomboids",
            ),
        )

        assertEquals(listOf("back", "lats", "upper_back"), rows.map { it.key })
        assertEquals(2.0, rows.first { it.key == "back" }.completedWeightedSets, 0.001)
        assertEquals(2.0, rows.first { it.key == "lats" }.completedWeightedSets, 0.001)
        assertEquals(1.0, rows.first { it.key == "upper_back" }.completedWeightedSets, 0.001)
    }

    @Test
    fun weeklyMuscleTargetRemainingLabel_matchesWeeklyTargetScreenCopy() {
        assertEquals("10 to go", weeklyMuscleTargetRemainingLabel(completedSets = 0.0, targetSets = 10.0))
        assertEquals("2.5 to go", weeklyMuscleTargetRemainingLabel(completedSets = 7.5, targetSets = 10.0))
        assertEquals("0 to go", weeklyMuscleTargetRemainingLabel(completedSets = 12.0, targetSets = 10.0))
        assertEquals(
            "2.5 to go • +1.5 done",
            weeklyMuscleTargetRemainingLabel(completedSets = 6.0, targetSets = 10.0, activeContributionSets = 1.5),
        )
    }

    @Test
    fun weeklyMuscleTargetOverageLabel_reportsOnlyCompletedSetsAboveTarget() {
        assertNull(weeklyMuscleTargetOverageLabel(completedSets = 7.5, targetSets = 10.0))
        assertNull(weeklyMuscleTargetOverageLabel(completedSets = 10.0, targetSets = 10.0))
        assertEquals("2 over", weeklyMuscleTargetOverageLabel(completedSets = 12.0, targetSets = 10.0))
        assertEquals("1.8 over", weeklyMuscleTargetOverageLabel(completedSets = 11.75, targetSets = 10.0))
        assertNull(weeklyMuscleTargetOverageLabel(completedSets = -4.0, targetSets = 10.0))
    }

    @Test
    fun activeWorkoutMuscleTargetWeeklyProgress_subtractsCurrentWorkoutContribution() {
        val progress = requireNotNull(
            activeWorkoutMuscleTargetWeeklyProgress(
                weeklyCompletedSets = 4.2,
                weeklyTargetSets = 10.0,
                activeCompletedWeightedSets = 1.5,
            ),
        )

        assertEquals(4.3, progress.remainingSets, 0.001)
        assertEquals(1.5, progress.activeContributionSets, 0.001)
        assertEquals(0.57f, progress.progressFraction, 0.001f)
        assertEquals("4.3 to go • +1.5 done", progress.label)
    }

    @Test
    fun activeWorkoutMuscleTargetWeeklyProgress_clampsOverTargetProgressAndNegativeInputs() {
        val overTarget = requireNotNull(
            activeWorkoutMuscleTargetWeeklyProgress(
                weeklyCompletedSets = 9.0,
                weeklyTargetSets = 10.0,
                activeCompletedWeightedSets = 3.0,
            ),
        )
        val negativeInputs = requireNotNull(
            activeWorkoutMuscleTargetWeeklyProgress(
                weeklyCompletedSets = -4.0,
                weeklyTargetSets = -10.0,
                activeCompletedWeightedSets = -2.0,
            ),
        )

        assertEquals(0.0, overTarget.remainingSets, 0.001)
        assertEquals(1.0f, overTarget.progressFraction, 0.001f)
        assertEquals("0 to go • +3 done", overTarget.label)
        assertEquals(0.0, negativeInputs.remainingSets, 0.001)
        assertEquals(0.0, negativeInputs.activeContributionSets, 0.001)
        assertEquals(0.0f, negativeInputs.progressFraction, 0.001f)
        assertEquals("0 to go", negativeInputs.label)
    }

    @Test
    fun sortedActiveWorkoutMuscleTargetBucketRows_ordersByWeeklyToGoAfterCurrentWorkoutContribution() {
        val rows = listOf(
            bucketRow(key = "push", label = "Push Muscles", completedWeightedSets = 0.0),
            bucketRow(key = "pull", label = "Pull Muscles", completedWeightedSets = 3.0),
            bucketRow(key = "legs", label = "Legs Muscles", completedWeightedSets = 0.0),
        )
        val weeklyGroups = listOf(
            weeklyGroup(key = "push", label = "Push Muscles", completedSets = 8.0, targetSets = 10.0),
            weeklyGroup(key = "pull", label = "Pull Muscles", completedSets = 0.0, targetSets = 10.0),
            weeklyGroup(key = "legs", label = "Legs Muscles", completedSets = 5.0, targetSets = 10.0),
        ).associateBy { it.key }

        val sorted = sortedActiveWorkoutMuscleTargetBucketRows(rows, weeklyGroups)

        assertEquals(listOf("pull", "legs", "push"), sorted.map { it.key })
    }

    @Test
    fun sortedActiveWorkoutMuscleTargetSubcategoryRows_ordersByWeeklyToGoAfterCurrentWorkoutContribution() {
        val rows = listOf(
            subcategoryRow(key = "chest", label = "Chest", bucketKey = "push", completedWeightedSets = 1.0),
            subcategoryRow(key = "shoulders", label = "Shoulders", bucketKey = "push", completedWeightedSets = 5.0),
            subcategoryRow(key = "triceps", label = "Triceps", bucketKey = "push", completedWeightedSets = 0.0),
        )
        val weeklyMuscles = listOf(
            weeklyMuscle(key = "chest", bucketKey = "push", label = "Chest", completedSets = 5.0, targetSets = 10.0),
            weeklyMuscle(key = "shoulders", bucketKey = "push", label = "Shoulders", completedSets = 2.0, targetSets = 10.0),
            weeklyMuscle(key = "triceps", bucketKey = "push", label = "Triceps", completedSets = 1.0, targetSets = 10.0),
        ).associateBy { it.key }

        val sorted = sortedActiveWorkoutMuscleTargetSubcategoryRows(rows, weeklyMuscles)

        assertEquals(listOf("triceps", "chest", "shoulders"), sorted.map { it.key })
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

    @Test
    fun buildActiveWorkoutFreshnessAction_continuesInProgressOverdueExercise() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Close-Grip Bench Press",
                    target = "Chest",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "10", completed = true),
                        SessionSet(setNumber = 2, targetReps = "10"),
                    ),
                ),
            ),
        )
        val details = mapOf(
            "Close-Grip Bench Press".hashCode().toLong() to detail(
                "Close-Grip Bench Press",
                target = "Chest",
                prime = "Chest",
                secondary = "Triceps",
            ),
        )
        val summary = buildActiveWorkoutMuscleRefreshSummary(
            session = session,
            exerciseDetailsById = details,
            trainingFreshness = freshnessSummary(mapOf("triceps" to TrainingFreshnessStatus.Overdue)),
        )

        val action = requireNotNull(buildActiveWorkoutFreshnessAction(session, summary, details))

        assertEquals(ActiveWorkoutFreshnessActionType.OpenExercise, action.type)
        assertEquals("triceps", action.muscleKey)
        assertEquals(0, action.exerciseIndex)
        assertEquals("Continue Close-Grip Bench Press", action.ctaLabel)
    }

    @Test
    fun buildActiveWorkoutFreshnessAction_opensPlannedOverdueExercise() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Cable Row",
                    target = "Back",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "10"),
                        SessionSet(setNumber = 2, targetReps = "10"),
                    ),
                ),
            ),
        )
        val details = mapOf(
            "Cable Row".hashCode().toLong() to detail("Cable Row", target = "Back", prime = "Back"),
        )
        val summary = buildActiveWorkoutMuscleRefreshSummary(
            session = session,
            exerciseDetailsById = details,
            trainingFreshness = freshnessSummary(mapOf("back" to TrainingFreshnessStatus.Overdue)),
        )

        val action = requireNotNull(buildActiveWorkoutFreshnessAction(session, summary, details))

        assertEquals(ActiveWorkoutFreshnessActionType.OpenExercise, action.type)
        assertEquals("back", action.muscleKey)
        assertEquals(0, action.exerciseIndex)
        assertEquals("Open Cable Row", action.ctaLabel)
    }

    @Test
    fun buildActiveWorkoutFreshnessAction_opensFilteredPickerForUntargetedOverdueMuscle() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    target = "Chest",
                    sets = listOf(SessionSet(setNumber = 1, targetReps = "8")),
                ),
            ),
        )
        val details = mapOf(
            "Bench Press".hashCode().toLong() to detail("Bench Press", target = "Chest", prime = "Chest"),
        )
        val summary = buildActiveWorkoutMuscleRefreshSummary(
            session = session,
            exerciseDetailsById = details,
            trainingFreshness = freshnessSummary(mapOf("hamstrings" to TrainingFreshnessStatus.Overdue)),
        )

        val action = requireNotNull(buildActiveWorkoutFreshnessAction(session, summary, details))

        assertEquals(ActiveWorkoutFreshnessActionType.OpenFilteredPicker, action.type)
        assertEquals("hamstrings", action.muscleKey)
        assertEquals("Hamstrings", action.muscleLabel)
        assertNull(action.exerciseIndex)
    }

    @Test
    fun buildActiveWorkoutFreshnessAction_skipsRefreshedMusclesAndPrioritizesOverdue() {
        val session = session(
            exercises = listOf(
                exercise(
                    "Bench Press",
                    target = "Chest",
                    sets = listOf(
                        SessionSet(setNumber = 1, targetReps = "8", completed = true),
                        SessionSet(setNumber = 2, targetReps = "8"),
                    ),
                ),
                exercise(
                    "Cable Row",
                    target = "Back",
                    sets = listOf(SessionSet(setNumber = 1, targetReps = "10")),
                ),
            ),
        )
        val details = mapOf(
            "Bench Press".hashCode().toLong() to detail("Bench Press", target = "Chest", prime = "Chest"),
            "Cable Row".hashCode().toLong() to detail("Cable Row", target = "Back", prime = "Back"),
        )
        val summary = buildActiveWorkoutMuscleRefreshSummary(
            session = session,
            exerciseDetailsById = details,
            trainingFreshness = freshnessSummary(
                mapOf(
                    "chest" to TrainingFreshnessStatus.DueSoon,
                    "back" to TrainingFreshnessStatus.Overdue,
                ),
            ),
        )

        val action = requireNotNull(buildActiveWorkoutFreshnessAction(session, summary, details))

        assertEquals("back", action.muscleKey)
        assertEquals(1, action.exerciseIndex)
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
        target: String = "Chest",
        sets: List<SessionSet>,
    ): SessionExercise {
        return SessionExercise(
            exerciseId = name.hashCode().toLong(),
            name = name,
            bodyRegion = "Upper Body",
            targetMuscleGroup = target,
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

    private fun bucketRow(
        key: String,
        label: String,
        completedWeightedSets: Double,
        plannedWeightedSets: Double = completedWeightedSets,
        subcategories: List<ActiveWorkoutMuscleTargetSubcategoryRow> = emptyList(),
    ): ActiveWorkoutMuscleTargetBucketRow {
        return ActiveWorkoutMuscleTargetBucketRow(
            key = key,
            label = label,
            plannedWeightedSets = plannedWeightedSets,
            completedWeightedSets = completedWeightedSets,
            remainingPlannedWeightedSets = (plannedWeightedSets - completedWeightedSets).coerceAtLeast(0.0),
            progressFraction = if (plannedWeightedSets > 0.0) {
                (completedWeightedSets / plannedWeightedSets).coerceIn(0.0, 1.0).toFloat()
            } else {
                0f
            },
            state = if (plannedWeightedSets > 0.0) ActiveWorkoutMuscleTargetState.InProgress else ActiveWorkoutMuscleTargetState.NotPlanned,
            subcategories = subcategories,
        )
    }

    private fun subcategoryRow(
        key: String,
        label: String,
        bucketKey: String,
        completedWeightedSets: Double,
        plannedWeightedSets: Double = completedWeightedSets,
    ): ActiveWorkoutMuscleTargetSubcategoryRow {
        return ActiveWorkoutMuscleTargetSubcategoryRow(
            key = key,
            label = label,
            bucketKey = bucketKey,
            plannedWeightedSets = plannedWeightedSets,
            completedWeightedSets = completedWeightedSets,
            remainingPlannedWeightedSets = (plannedWeightedSets - completedWeightedSets).coerceAtLeast(0.0),
            progressFraction = if (plannedWeightedSets > 0.0) {
                (completedWeightedSets / plannedWeightedSets).coerceIn(0.0, 1.0).toFloat()
            } else {
                0f
            },
            state = if (plannedWeightedSets > 0.0) ActiveWorkoutMuscleTargetState.InProgress else ActiveWorkoutMuscleTargetState.NotPlanned,
        )
    }

    private fun weeklyGroup(
        key: String,
        label: String,
        completedSets: Double,
        targetSets: Double,
        muscles: List<WeeklyMuscleTargetMuscleSummary> = emptyList(),
    ): WeeklyMuscleTargetGroupSummary {
        return WeeklyMuscleTargetGroupSummary(
            key = key,
            label = label,
            completedSets = completedSets,
            targetSets = targetSets,
            muscleSummaries = muscles,
        )
    }

    private fun weeklyMuscle(
        key: String,
        bucketKey: String,
        label: String,
        completedSets: Double,
        targetSets: Double,
    ): WeeklyMuscleTargetMuscleSummary {
        return WeeklyMuscleTargetMuscleSummary(
            key = key,
            bucketKey = bucketKey,
            label = label,
            completedSets = completedSets,
            targetSets = targetSets,
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
