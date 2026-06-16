package dev.toastlabs.toastlift.ui

import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.ExerciseDetail
import dev.toastlabs.toastlift.data.ExerciseSummary
import dev.toastlabs.toastlift.data.HistoryReuseMode
import dev.toastlabs.toastlift.data.LibraryFilters
import dev.toastlabs.toastlift.data.RecommendationSource
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionSet
import dev.toastlabs.toastlift.data.ThemePreference
import dev.toastlabs.toastlift.data.UserProfile
import dev.toastlabs.toastlift.data.WorkoutExercise
import dev.toastlabs.toastlift.data.WorkoutExerciseSetDraft
import dev.toastlabs.toastlift.data.WorkoutPlan
import dev.toastlabs.toastlift.data.ProgramSetupDraft
import dev.toastlabs.toastlift.data.normalizeWorkoutDurationMinutes
import dev.toastlabs.toastlift.data.pause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

class ToastLiftViewModelTest {
    @Test
    fun workoutGenerationRequestContext_reusesCurrentWorkoutFocusAndExercises() {
        val currentWorkout = WorkoutPlan(
            title = "Home Upper Day",
            subtitle = "Upper/Lower • Hypertrophy • 45 min",
            locationModeId = 1L,
            estimatedMinutes = 45,
            origin = "generated",
            focusKey = "upper_body",
            exercises = listOf(
                exercise(101L, "Bench Press"),
                exercise(202L, "Chest Supported Row"),
            ),
        )

        val context = workoutGenerationRequestContext(currentWorkout)

        assertEquals(setOf(101L, 202L), context.previousExerciseIds)
        assertEquals("upper_body", context.requestedFocus)
    }

    @Test
    fun workoutGenerationRequestContext_usesScheduledFocusWhenNoWorkoutExists() {
        val context = workoutGenerationRequestContext(currentWorkout = null)

        assertTrue(context.previousExerciseIds.isEmpty())
        assertEquals(null, context.requestedFocus)
    }

    @Test
    fun generatedActiveSessionExerciseExclusionIds_includeSeenSuggestionsForRerolls() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            focusKey = "upper_body",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false, false, false)),
                sessionExercise(id = 202L, name = "Cable Row", completedSets = listOf(false, false, false)),
            ),
        )

        val excludedIds = generatedActiveSessionExerciseExclusionIds(
            session = session,
            generatedState = ActiveSessionGeneratedExerciseState(
                exercise = exercise(303L, "Incline Dumbbell Press"),
                seenExerciseIds = setOf(303L, 404L),
            ),
        )

        assertEquals(setOf(101L, 202L, 303L, 404L), excludedIds)
    }

    @Test
    fun generatedAdditionalSessionExercise_returnsNullWhenEveryCandidateWasAlreadySeen() {
        val workout = WorkoutPlan(
            title = "Upper Body",
            subtitle = "Generated",
            locationModeId = 1L,
            estimatedMinutes = 45,
            origin = "generated",
            exercises = listOf(
                exercise(303L, "Incline Dumbbell Press"),
                exercise(404L, "Machine Chest Press"),
            ),
        )

        val suggestion = generatedAdditionalSessionExercise(
            workout = workout,
            excludedExerciseIds = setOf(303L, 404L),
        )

        assertNull(suggestion)
    }

    @Test
    fun syncProgramSetupDraftWithProfileDuration_tracksProfileAcrossFiveMinuteSweep() {
        for (minutes in 15..500 step 5) {
            val synced = syncProgramSetupDraftWithProfileDuration(
                currentDraft = ProgramSetupDraft(),
                profile = profile(durationMinutes = minutes),
            )

            assertEquals(
                "profile duration $minutes should feed schedule session time",
                normalizeWorkoutDurationMinutes(minutes),
                synced.sessionTimeMinutes,
            )
        }
    }

    @Test
    fun syncProgramSetupDraftWithProfileDuration_preservesExplicitSessionTimeOverride() {
        val synced = syncProgramSetupDraftWithProfileDuration(
            currentDraft = ProgramSetupDraft(sessionTimeMinutes = 75),
            profile = profile(durationMinutes = 30),
        )

        assertEquals(75, synced.sessionTimeMinutes)
    }

    @Test
    fun activeSessionSelectionAfterExerciseRemoval_clearsRemovedSelection() {
        assertNull(
            activeSessionSelectionAfterExerciseRemoval(
                selectedExerciseIndex = 1,
                removedExerciseIndex = 1,
                remainingExerciseCount = 2,
            ),
        )
    }

    @Test
    fun activeSessionSelectionAfterExerciseRemoval_shiftsSelectionAfterRemovedExercise() {
        assertEquals(
            1,
            activeSessionSelectionAfterExerciseRemoval(
                selectedExerciseIndex = 2,
                removedExerciseIndex = 1,
                remainingExerciseCount = 2,
            ),
        )
    }

    @Test
    fun activeSessionFreshnessLibraryFilters_mapsLowerBackToCatalogFacets() {
        val filters = activeSessionFreshnessLibraryFilters(
            muscleKey = "erector_spinae",
            muscleLabel = "Lower Back",
        )

        assertEquals(setOf("erector_spinae"), filters.freshnessMuscleKeys)
        assertTrue(filters.targetMuscles.isEmpty())
        assertTrue(filters.primeMovers.isEmpty())
    }

    @Test
    fun activeSessionFreshnessLibraryFilters_mapsCoreToAbdominalsCatalogFacet() {
        val filters = activeSessionFreshnessLibraryFilters(
            muscleKey = "core",
            muscleLabel = "Core",
        )

        assertEquals(setOf("core"), filters.freshnessMuscleKeys)
        assertTrue(filters.targetMuscles.isEmpty())
        assertTrue(filters.primeMovers.isEmpty())
    }

    @Test
    fun activeSessionFreshnessLibraryFilters_usesDisplayLabelForCatalogMatchingMuscles() {
        val filters = activeSessionFreshnessLibraryFilters(
            muscleKey = "hamstrings",
            muscleLabel = "Hamstrings",
        )

        assertEquals(setOf("hamstrings"), filters.freshnessMuscleKeys)
        assertTrue(filters.targetMuscles.isEmpty())
        assertTrue(filters.primeMovers.isEmpty())
    }

    @Test
    fun muscleTargetLibraryFilters_selectsOnlyMuscleTargetSubcategory() {
        val filters = muscleTargetLibraryFilters(
            bucketKey = null,
            subcategoryKey = "Chest",
        )

        assertEquals(setOf("chest"), filters.muscleTargetSubcategoryKeys)
        assertEquals(1, filters.activeCount())
        assertTrue(filters.muscleTargetBucketKeys.isEmpty())
        assertTrue(filters.equipment.isEmpty())
        assertTrue(filters.targetMuscles.isEmpty())
        assertTrue(filters.primeMovers.isEmpty())
        assertTrue(filters.freshnessMuscleKeys.isEmpty())
        assertTrue(filters.recommendationBiases.isEmpty())
        assertFalse(filters.favoritesOnly)
        assertFalse(filters.hasLoggedHistoryOnly)
    }

    @Test
    fun libraryFreshnessMuscleFilterLabel_resolvesKnownFreshnessSlot() {
        assertEquals("Lower Back", libraryFreshnessMuscleFilterLabel("erector_spinae"))
    }

    @Test
    fun libraryFreshnessMuscleFilterLabels_deduplicatesResolvedAliases() {
        val labels = libraryFreshnessMuscleFilterLabels(
            LibraryFilters(
                freshnessMuscleKeys = setOf("erector_spinae", "Lower Back"),
            ),
        )

        assertEquals(listOf("Lower Back"), labels)
    }

    @Test
    fun libraryFreshnessMuscleFilterKeys_normalizesKnownLabelsAndKeys() {
        val keys = libraryFreshnessMuscleFilterKeys(
            LibraryFilters(
                freshnessMuscleKeys = setOf("Lower Back", "erector_spinae", "Hamstrings"),
            ),
        )

        assertEquals(setOf("erector_spinae", "hamstrings"), keys)
    }

    @Test
    fun libraryFreshnessMuscleFilterLabel_formatsUnknownKeys() {
        assertEquals("Rear Delts", libraryFreshnessMuscleFilterLabel("rear_delts"))
    }

    @Test
    fun orderedSessionExercises_movesMostRecentlyLoggedExerciseToTopAheadOfCompletedAndUntouched() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false, false, false)),
                sessionExercise(id = 202L, name = "Cable Row", completedSets = listOf(true, true, true), activitySequence = 1, completionSequence = 1),
                sessionExercise(id = 303L, name = "Lateral Raise", completedSets = listOf(false, true, false), activitySequence = 2),
                sessionExercise(id = 404L, name = "Leg Press", completedSets = listOf(false, false, false)),
            ),
        )

        val orderedNames = orderedSessionExercises(session).map { it.value.name }

        assertEquals(listOf("Lateral Raise", "Cable Row", "Bench Press", "Leg Press"), orderedNames)
    }

    @Test
    fun orderedSessionExercises_keepsNewerPartialExerciseAboveOlderPartialAndCompletedExercises() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "A", completedSets = listOf(true, true, true), activitySequence = 1, completionSequence = 1),
                sessionExercise(id = 202L, name = "B", completedSets = listOf(false, false, false)),
                sessionExercise(id = 303L, name = "C", completedSets = listOf(true, true, false), activitySequence = 2),
                sessionExercise(id = 404L, name = "D", completedSets = listOf(true, false, false), activitySequence = 3),
            ),
        )

        val orderedNames = orderedSessionExercises(session).map { it.value.name }

        assertEquals(listOf("D", "C", "A", "B"), orderedNames)
    }

    @Test
    fun activeSessionEquipmentOptions_returnsDistinctInWorkoutOrder() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false), equipment = "Barbell"),
                sessionExercise(id = 202L, name = "Cable Row", completedSets = listOf(false), equipment = "Cable"),
                sessionExercise(id = 303L, name = "Incline Press", completedSets = listOf(false), equipment = "Barbell"),
            ),
        )

        assertEquals(listOf("Barbell", "Cable"), activeSessionEquipmentOptions(session))
    }

    @Test
    fun resolveActiveSessionEquipmentFilter_clearsUnknownEquipment() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false), equipment = "Barbell"),
                sessionExercise(id = 202L, name = "Cable Row", completedSets = listOf(false), equipment = "Cable"),
            ),
        )

        assertEquals("Cable", resolveActiveSessionEquipmentFilter(session, "cable"))
        assertNull(resolveActiveSessionEquipmentFilter(session, "Dumbbell"))
    }

    @Test
    fun orderedSessionExercises_withEquipmentFilter_keepsMatchingCompletedAndIncompleteExercises() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", equipment = "Barbell", completedSets = listOf(true, true, true), activitySequence = 1, completionSequence = 1),
                sessionExercise(id = 202L, name = "Cable Row", equipment = "Cable", completedSets = listOf(true, false, false), activitySequence = 2),
                sessionExercise(id = 303L, name = "Incline Press", equipment = "Barbell", completedSets = listOf(false, false, false)),
            ),
        )

        val orderedNames = orderedSessionExercises(
            session = session,
            equipmentFilter = "barbell",
        ).map { it.value.name }

        assertEquals(listOf("Bench Press", "Incline Press"), orderedNames)
    }

    @Test
    fun activeSessionBodyRegionFilterOptions_countsUpperLowerCoreAndFullBody() {
        val session = ActiveSession(
            title = "Mixed Day",
            origin = "manual",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false), bodyRegion = "Upper Body"),
                sessionExercise(id = 202L, name = "Squat", completedSets = listOf(false), bodyRegion = "Lower Body"),
                sessionExercise(id = 303L, name = "Plank", completedSets = listOf(false), bodyRegion = "Core"),
                sessionExercise(id = 404L, name = "Burpee", completedSets = listOf(false), bodyRegion = "Full Body"),
            ),
        )

        val countsByKey = activeSessionBodyRegionFilterOptions(session).associate { it.key to it.matchingExerciseCount }

        assertEquals(1, countsByKey["upper"])
        assertEquals(1, countsByKey["lower"])
        assertEquals(1, countsByKey["core"])
        assertEquals(1, countsByKey["full_body"])
    }

    @Test
    fun orderedSessionExercises_withBodyRegionFilter_keepsMatchingBodyAreaExercises() {
        val session = ActiveSession(
            title = "Mixed Day",
            origin = "manual",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false), bodyRegion = "Upper Body"),
                sessionExercise(id = 202L, name = "Squat", completedSets = listOf(false), bodyRegion = "Lower Body"),
                sessionExercise(id = 303L, name = "Plank", completedSets = listOf(false), bodyRegion = "Core"),
                sessionExercise(id = 404L, name = "Burpee", completedSets = listOf(false), bodyRegion = "Full Body"),
            ),
        )

        val orderedNames = orderedSessionExercises(
            session = session,
            equipmentFilter = null,
            bodyRegionFilterKey = "lower",
        ).map { it.value.name }

        assertEquals(listOf("Squat"), orderedNames)
    }

    @Test
    fun activeSessionMuscleFilterOptions_usesTrainingFreshnessMuscleCategories() {
        val session = ActiveSession(
            title = "Pull Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Wrist Curl", completedSets = listOf(false), targetMuscleGroup = "Forearms"),
                sessionExercise(id = 202L, name = "Cable Row", completedSets = listOf(false), targetMuscleGroup = "Back"),
            ),
        )
        val details = mapOf(
            101L to exerciseDetail(exercise = session.exercises[0], primeMover = "Forearms"),
            202L to exerciseDetail(exercise = session.exercises[1], primeMover = "Latissimus Dorsi", secondaryMuscle = "Biceps"),
        )

        val options = activeSessionMuscleFilterOptions(session, details)

        assertEquals(trainingFreshnessMuscleSlots().map { it.key }, options.map { it.key })
        assertEquals(1, options.first { it.key == "forearms" }.matchingExerciseCount)
        assertEquals(1, options.first { it.key == "back" }.matchingExerciseCount)
        assertEquals(1, options.first { it.key == "biceps" }.matchingExerciseCount)
        assertEquals(0, options.first { it.key == "triceps" }.matchingExerciseCount)
    }

    @Test
    fun orderedSessionExercises_withMuscleFilter_keepsMatchingFreshnessMuscleExercises() {
        val session = ActiveSession(
            title = "Pull Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false), targetMuscleGroup = "Chest"),
                sessionExercise(id = 202L, name = "Wrist Curl", completedSets = listOf(false), targetMuscleGroup = "Forearms"),
                sessionExercise(id = 303L, name = "Cable Row", completedSets = listOf(false), targetMuscleGroup = "Back"),
            ),
        )
        val details = mapOf(
            202L to exerciseDetail(exercise = session.exercises[1], primeMover = "Forearms"),
            303L to exerciseDetail(exercise = session.exercises[2], primeMover = "Latissimus Dorsi", secondaryMuscle = "Forearms"),
        )

        val orderedNames = orderedSessionExercises(
            session = session,
            equipmentFilter = null,
            muscleFilterKey = "forearms",
            exerciseDetailsById = details,
        ).map { it.value.name }

        assertEquals(listOf("Wrist Curl", "Cable Row"), orderedNames)
    }

    @Test
    fun orderedSessionExercises_withMuscleTargetBucket_keepsMatchingPushExercises() {
        val session = ActiveSession(
            title = "Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false), targetMuscleGroup = "Chest"),
                sessionExercise(id = 202L, name = "Cable Row", completedSets = listOf(false), targetMuscleGroup = "Back"),
                sessionExercise(id = 303L, name = "Triceps Pressdown", completedSets = listOf(false), targetMuscleGroup = "Triceps"),
            ),
        )

        val orderedNames = orderedSessionExercises(
            session = session,
            equipmentFilter = null,
            muscleTargetBucketKey = "push",
        ).map { it.value.name }

        assertEquals(listOf("Bench Press", "Triceps Pressdown"), orderedNames)
    }

    @Test
    fun orderedSessionExercises_withMuscleTargetSubcategoryTreatsRearDeltsAsPull() {
        val session = ActiveSession(
            title = "Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Reverse Fly", completedSets = listOf(false), targetMuscleGroup = "Shoulders"),
                sessionExercise(id = 202L, name = "Lateral Raise", completedSets = listOf(false), targetMuscleGroup = "Shoulders"),
            ),
        )
        val details = mapOf(
            101L to exerciseDetail(exercise = session.exercises[0], primeMover = "Rear Delts"),
            202L to exerciseDetail(exercise = session.exercises[1], primeMover = "Side Delts"),
        )

        val orderedNames = orderedSessionExercises(
            session = session,
            equipmentFilter = null,
            muscleTargetSubcategoryKey = "rear_delts",
            exerciseDetailsById = details,
        ).map { it.value.name }

        assertEquals(listOf("Reverse Fly"), orderedNames)
    }

    @Test
    fun pickNextSessionExerciseIndex_prioritizesSelectedMuscleTargetSubcategory() {
        val session = ActiveSession(
            title = "Leg Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Leg Extension", completedSets = listOf(false), targetMuscleGroup = "Quadriceps"),
                sessionExercise(id = 202L, name = "Hip Thrust", completedSets = listOf(false), targetMuscleGroup = "Glutes"),
                sessionExercise(id = 303L, name = "Leg Curl", completedSets = listOf(false), targetMuscleGroup = "Hamstrings"),
            ),
        )

        val pickedIndex = pickNextSessionExerciseIndex(
            session = session,
            smartTargetMuscle = "Quadriceps",
            exerciseDetailsById = emptyMap(),
            muscleTargetSubcategoryKey = "hamstrings",
            random = Random(0),
        )

        assertEquals(2, pickedIndex)
    }

    @Test
    fun smartPickExerciseScore_mapsBrachioradialisFallbackToForearms() {
        val exercise = sessionExercise(
            id = 101L,
            name = "Loaded Brachioradialis Hold",
            completedSets = listOf(false),
            targetMuscleGroup = "Grip",
        )
        val detail = exerciseDetail(exercise = exercise, primeMover = "Loaded Brachioradialis")

        val forearmScore = smartPickExerciseScore(exercise, detail, normalizedTargetMuscle = "forearms")
        val bicepsScore = smartPickExerciseScore(exercise, detail, normalizedTargetMuscle = "biceps")

        assertTrue(forearmScore > 0.0)
        assertEquals(0.0, bicepsScore, 0.001)
    }

    @Test
    fun pickNextSessionExerciseIndex_respectsBodyRegionFilter() {
        val session = ActiveSession(
            title = "Mixed Day",
            origin = "manual",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false), bodyRegion = "Upper Body"),
                sessionExercise(id = 202L, name = "Squat", completedSets = listOf(false), bodyRegion = "Lower Body"),
            ),
        )

        val pickedIndex = pickNextSessionExerciseIndex(
            session = session,
            smartTargetMuscle = null,
            exerciseDetailsById = emptyMap(),
            bodyRegionFilterKey = "lower",
            random = Random(0),
        )

        assertEquals(1, pickedIndex)
    }

    @Test
    fun reconcileSessionExerciseCompletionState_assignsSequenceOnlyWhenExerciseBecomesComplete() {
        val exercises = listOf(
            sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(true, true, true), activitySequence = 1, completionSequence = 1),
            sessionExercise(id = 202L, name = "Cable Row", completedSets = listOf(true, false, false)),
        )

        val updated = reconcileSessionExerciseCompletionState(
            exercises = exercises,
            exerciseIndex = 1,
            updatedExercise = exercises[1].copy(
                sets = exercises[1].sets.map { it.copy(completed = true) },
            ),
            promoteForLoggedSet = true,
        )

        assertEquals(2, updated.activitySequence)
        assertEquals(2, updated.completionSequence)
    }

    @Test
    fun reconcileSessionExerciseCompletionState_clearsSequenceWhenExerciseStopsBeingComplete() {
        val exercises = listOf(
            sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(true, true, true), activitySequence = 4, completionSequence = 3),
        )

        val updated = reconcileSessionExerciseCompletionState(
            exercises = exercises,
            exerciseIndex = 0,
            updatedExercise = exercises[0].copy(
                sets = exercises[0].sets.mapIndexed { index, set ->
                    if (index == 2) set.copy(completed = false) else set
                },
            ),
        )

        assertEquals(4, updated.activitySequence)
        assertNull(updated.completionSequence)
    }

    @Test
    fun reconcileSessionExerciseCompletionState_clearsActivitySequenceWhenExerciseHasNoLoggedSetsLeft() {
        val exercises = listOf(
            sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(true, false, false), activitySequence = 4),
        )

        val updated = reconcileSessionExerciseCompletionState(
            exercises = exercises,
            exerciseIndex = 0,
            updatedExercise = exercises[0].copy(
                sets = exercises[0].sets.map { it.copy(completed = false) },
            ),
        )

        assertNull(updated.activitySequence)
        assertNull(updated.completionSequence)
    }

    @Test
    fun canFinishActiveSession_requiresAtLeastOneExercise() {
        assertFalse(canFinishActiveSession(exerciseCount = 0))
        assertTrue(canFinishActiveSession(exerciseCount = 1))
    }

    @Test
    fun pickNextSessionExerciseIndex_onlyChoosesExercisesWithNoCompletedSets() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(true, false, false)),
                sessionExercise(id = 202L, name = "Cable Row", completedSets = listOf(false, false, false)),
                sessionExercise(id = 303L, name = "Lateral Raise", completedSets = listOf(false, false, false)),
                sessionExercise(id = 404L, name = "Leg Press", completedSets = listOf(true, true, true), completionSequence = 1),
            ),
        )

        repeat(20) {
            val pickedIndex = pickNextSessionExerciseIndex(session, Random(it))
            assertTrue(pickedIndex in setOf(1, 2))
        }
    }

    @Test
    fun pickNextSessionExerciseIndex_withEquipmentFilter_onlyChoosesMatchingUntouchedExercises() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", equipment = "Barbell", completedSets = listOf(false, false, false)),
                sessionExercise(id = 202L, name = "Machine Chest Press", equipment = "Machine", completedSets = listOf(false, false, false)),
                sessionExercise(id = 303L, name = "Machine Row", equipment = "Machine", completedSets = listOf(true, false, false)),
                sessionExercise(id = 404L, name = "Cable Row", equipment = "Cable", completedSets = listOf(false, false, false)),
            ),
        )

        repeat(20) {
            val pickedIndex = pickNextSessionExerciseIndex(
                session = session,
                equipmentFilter = "machine",
                random = Random(it),
            )

            assertEquals(1, pickedIndex)
        }
    }

    @Test
    fun pickNextSessionExerciseIndex_returnsNullWhenEveryExerciseHasStarted() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(true, false, false)),
                sessionExercise(id = 202L, name = "Cable Row", completedSets = listOf(true, true, true), completionSequence = 1),
            ),
        )

        assertNull(pickNextSessionExerciseIndex(session, Random(0)))
    }

    @Test
    fun pickNextSessionExerciseIndex_withEquipmentFilter_returnsNullWithoutMatchingUntouchedExercises() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", equipment = "Barbell", completedSets = listOf(false, false, false)),
                sessionExercise(id = 202L, name = "Machine Chest Press", equipment = "Machine", completedSets = listOf(true, false, false)),
            ),
        )

        assertNull(
            pickNextSessionExerciseIndex(
                session = session,
                equipmentFilter = "Machine",
                random = Random(0),
            ),
        )
    }

    @Test
    fun pickNextSessionExerciseIndex_withRemovedFilteredExercise_keepsEquipmentFilterApplied() {
        val sessionAfterDeletingMachineExercise = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", equipment = "Barbell", completedSets = listOf(false, false, false)),
                sessionExercise(id = 202L, name = "Cable Row", equipment = "Cable", completedSets = listOf(false, false, false)),
            ),
        )

        assertNull(
            pickNextSessionExerciseIndex(
                session = sessionAfterDeletingMachineExercise,
                equipmentFilter = "Machine",
                random = Random(0),
            ),
        )
    }

    @Test
    fun pickNextSessionExerciseIndex_prioritizesUntouchedExerciseThatBestMatchesSavedMuscleTarget() {
        val session = ActiveSession(
            title = "Pull Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Leg Press", completedSets = listOf(false, false, false), targetMuscleGroup = "Quadriceps"),
                sessionExercise(id = 202L, name = "Lat Pulldown", completedSets = listOf(false, false, false), targetMuscleGroup = "Back"),
                sessionExercise(id = 303L, name = "Cable Curl", completedSets = listOf(false, false, false), targetMuscleGroup = "Biceps"),
            ),
        )

        val pickedIndex = pickNextSessionExerciseIndex(
            session = session,
            smartTargetMuscle = "Latissimus Dorsi",
            exerciseDetailsById = mapOf(
                202L to exerciseDetail(
                    exercise = session.exercises[1],
                    primeMover = "Latissimus Dorsi",
                ),
            ),
            random = Random(0),
        )

        assertEquals(1, pickedIndex)
    }

    @Test
    fun pickNextSessionExerciseIndex_appliesEquipmentFilterBeforeSmartTargetScoring() {
        val session = ActiveSession(
            title = "Pull Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Machine Chest Press", equipment = "Machine", completedSets = listOf(false, false, false), targetMuscleGroup = "Chest"),
                sessionExercise(id = 202L, name = "Lat Pulldown", equipment = "Cable", completedSets = listOf(false, false, false), targetMuscleGroup = "Back"),
            ),
        )

        val pickedIndex = pickNextSessionExerciseIndex(
            session = session,
            smartTargetMuscle = "Latissimus Dorsi",
            exerciseDetailsById = mapOf(
                202L to exerciseDetail(
                    exercise = session.exercises[1],
                    primeMover = "Latissimus Dorsi",
                ),
            ),
            equipmentFilter = "Machine",
            random = Random(0),
        )

        assertEquals(0, pickedIndex)
    }

    @Test
    fun pickNextSessionExerciseIndex_fallsBackWhenUntouchedExercisesDoNotMatchSavedMuscleTarget() {
        val session = ActiveSession(
            title = "Leg Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Leg Press", completedSets = listOf(false, false, false), targetMuscleGroup = "Quadriceps"),
                sessionExercise(id = 202L, name = "Leg Curl", completedSets = listOf(false, false, false), targetMuscleGroup = "Hamstrings"),
            ),
        )

        val expectedFallback = pickNextSessionExerciseIndex(session, Random(7))
        val pickedIndex = pickNextSessionExerciseIndex(
            session = session,
            smartTargetMuscle = "Latissimus Dorsi",
            exerciseDetailsById = emptyMap(),
            random = Random(7),
        )

        assertEquals(expectedFallback, pickedIndex)
    }

    @Test
    fun firstSkippedExerciseFeedbackPrompt_returnsFirstExerciseWithNoCompletedSets() {
        val prompt = firstSkippedExerciseFeedbackPrompt(
            ActiveSession(
                title = "Gym Upper Day",
                origin = "generated",
                locationModeId = 2L,
                startedAtUtc = "2026-03-20T10:00:00Z",
                focusKey = "upper_body",
                exercises = listOf(
                    sessionExercise(
                        id = 101L,
                        name = "Bench Press",
                        completedSets = listOf(true, true, true),
                    ),
                    sessionExercise(
                        id = 202L,
                        name = "Cable Row",
                        completedSets = listOf(false, false, false),
                    ),
                    sessionExercise(
                        id = 303L,
                        name = "Lateral Raise",
                        completedSets = listOf(false, false, false),
                    ),
                ),
            ),
        )

        requireNotNull(prompt)
        assertEquals(202L, prompt.exerciseId)
        assertEquals("Cable Row", prompt.exerciseName)
        assertEquals("generated", prompt.workoutOrigin)
        assertEquals("upper_body", prompt.workoutFocusKey)
    }

    @Test
    fun historyReuseHelpers_applyModeSpecificLabels() {
        assertEquals("Leg Day Replay", historyReusePlanTitle("Leg Day", HistoryReuseMode.ExactCopy))
        assertEquals("Leg Day Refreshed", historyReusePlanTitle("Leg Day", HistoryReuseMode.RefreshPrescription))
        assertEquals(
            "History reuse • Exact copy • Edit before starting",
            historyReusePlanSubtitle(HistoryReuseMode.ExactCopy),
        )
        assertEquals("history_reuse_refreshed", historyReusePlanOrigin(HistoryReuseMode.RefreshPrescription))
        assertEquals(
            "Leg Day Replay added to My Plan as an exact copy.",
            historyReuseConfirmationMessage("Leg Day Replay", HistoryReuseMode.ExactCopy),
        )
    }

    @Test
    fun sessionSetFromHistoryReuseDraft_prefillsLoggedValuesForExactCopy() {
        val set = sessionSetFromHistoryReuseDraft(
            draft = WorkoutExerciseSetDraft(
                setNumber = 2,
                targetReps = "6-8",
                recommendedReps = 7,
                recommendedWeight = 135.0,
                reps = 8,
                weight = 140.0,
                recommendationSource = RecommendationSource.DIRECT_HISTORY,
                recommendationConfidence = 0.92,
            ),
            fallbackTargetReps = "8-10",
        )

        assertEquals(2, set.setNumber)
        assertEquals("6-8", set.targetReps)
        assertEquals(7, set.recommendedReps)
        assertEquals("135", set.recommendedWeight)
        assertEquals("8", set.reps)
        assertEquals("140", set.weight)
        assertEquals(RecommendationSource.DIRECT_HISTORY, set.recommendationSource)
        assertEquals(0.92, set.recommendationConfidence ?: 0.0, 0.0001)
    }

    @Test
    fun sessionSetFromHistoryReuseDraft_fallsBackWhenLoggedValuesAreMissing() {
        val set = sessionSetFromHistoryReuseDraft(
            draft = WorkoutExerciseSetDraft(
                setNumber = 1,
                targetReps = "",
                recommendedReps = null,
                recommendedWeight = 60.0,
                reps = null,
                weight = null,
            ),
            fallbackTargetReps = "10-12",
        )

        assertEquals("10-12", set.targetReps)
        assertEquals("10", set.reps)
        assertEquals("60", set.weight)
        assertEquals("60", set.recommendedWeight)
    }

    @Test
    fun sessionSetFromHistoryReuseDraft_preservesWorkUnitValuesWithoutRepPrefill() {
        val set = sessionSetFromHistoryReuseDraft(
            draft = WorkoutExerciseSetDraft(
                setNumber = 1,
                targetReps = "",
                workUnitValues = mapOf("duration_min" to "20", "speed_mph" to "5.0"),
                recommendationSource = RecommendationSource.GENERATED_PLAN,
                recommendationConfidence = 0.82,
            ),
            fallbackTargetReps = "20 min",
        )

        assertEquals(1, set.setNumber)
        assertEquals("", set.targetReps)
        assertEquals("", set.reps)
        assertEquals("", set.weight)
        assertEquals(mapOf("duration_min" to "20", "speed_mph" to "5.0"), set.workUnitValues)
        assertEquals(RecommendationSource.GENERATED_PLAN, set.recommendationSource)
    }

    @Test
    fun generatedSessionSetNumbers_preservesPlannedVolumeForWorkUnitExercises() {
        assertEquals(listOf(1, 2, 3), generatedSessionSetNumbers(3).toList())
        assertEquals(listOf(1), generatedSessionSetNumbers(0).toList())
    }

    @Test
    fun reorderActiveSessionSets_movesNewlyCompletedSetToFront_andRenumbersSequentially() {
        val setOne = SessionSet(id = 11L, setNumber = 1, targetReps = "8-10")
        val setTwo = SessionSet(id = 22L, setNumber = 2, targetReps = "8-10")
        val setThree = SessionSet(id = 33L, setNumber = 3, targetReps = "8-10", completed = true)

        val reordered = reorderActiveSessionSets(
            sets = listOf(setOne, setTwo, setThree),
            prioritizedCompletedSetId = 33L,
        )

        assertEquals(listOf(33L, 11L, 22L), reordered.map(SessionSet::id))
        assertEquals(listOf(1, 2, 3), reordered.map(SessionSet::setNumber))
        assertEquals(listOf(true, false, false), reordered.map(SessionSet::completed))
    }

    @Test
    fun reorderActiveSessionSets_appendsNewestCompletedSetAfterExistingDoneBlock() {
        val setOne = SessionSet(id = 11L, setNumber = 1, targetReps = "8-10", completed = true)
        val setTwo = SessionSet(id = 22L, setNumber = 2, targetReps = "8-10")
        val setThree = SessionSet(id = 33L, setNumber = 3, targetReps = "8-10", completed = true)

        val reordered = reorderActiveSessionSets(
            sets = listOf(setOne, setTwo, setThree),
            prioritizedCompletedSetId = 33L,
        )

        assertEquals(listOf(11L, 33L, 22L), reordered.map(SessionSet::id))
        assertEquals(listOf(1, 2, 3), reordered.map(SessionSet::setNumber))
    }

    @Test
    fun logNextSessionSetInActiveSession_autoResumesPausedWorkout() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false, false, false)),
            ),
        ).pause(Instant.parse("2026-03-20T10:05:00Z"))

        val updated = logNextSessionSetInActiveSession(
            session = session,
            exerciseIndex = 0,
            loggedAt = Instant.parse("2026-03-20T10:06:30Z"),
        )

        assertFalse(updated.isPaused)
        assertNull(updated.pausedAtUtc)
        assertEquals(90, updated.accumulatedPausedSeconds)
        assertTrue(updated.exercises[0].sets.first().completed)
    }

    @Test
    fun logAllSessionSetsInActiveSession_autoResumesPausedWorkout() {
        val session = ActiveSession(
            title = "Gym Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = "2026-03-20T10:00:00Z",
            exercises = listOf(
                sessionExercise(id = 101L, name = "Bench Press", completedSets = listOf(false, false, false)),
            ),
        ).pause(Instant.parse("2026-03-20T10:05:00Z"))

        val updated = logAllSessionSetsInActiveSession(
            session = session,
            exerciseIndex = 0,
            loggedAt = Instant.parse("2026-03-20T10:07:00Z"),
        )

        assertFalse(updated.isPaused)
        assertEquals(120, updated.accumulatedPausedSeconds)
        assertTrue(updated.exercises[0].sets.all(SessionSet::completed))
    }

    private fun exercise(id: Long, name: String): WorkoutExercise {
        return WorkoutExercise(
            exerciseId = id,
            name = name,
            bodyRegion = "Upper Body",
            targetMuscleGroup = "Chest",
            equipment = "Barbell",
            sets = 3,
            repRange = "8-10",
            restSeconds = 90,
            rationale = "Test fixture",
        )
    }

    private fun sessionExercise(
        id: Long,
        name: String,
        completedSets: List<Boolean>,
        activitySequence: Int? = null,
        completionSequence: Int? = null,
        bodyRegion: String = "Upper Body",
        targetMuscleGroup: String = "Chest",
        equipment: String = "Cable",
    ): SessionExercise {
        return SessionExercise(
            exerciseId = id,
            name = name,
            bodyRegion = bodyRegion,
            targetMuscleGroup = targetMuscleGroup,
            equipment = equipment,
            restSeconds = 90,
            activitySequence = activitySequence,
            completionSequence = completionSequence,
            sets = completedSets.mapIndexed { index, completed ->
                SessionSet(
                    setNumber = index + 1,
                    targetReps = "8-10",
                    reps = "8",
                    weight = "100",
                    recommendationSource = RecommendationSource.NONE,
                    completed = completed,
                )
            },
        )
    }

    private fun exerciseDetail(
        exercise: SessionExercise,
        primeMover: String? = null,
        secondaryMuscle: String? = null,
        tertiaryMuscle: String? = null,
    ): ExerciseDetail {
        return ExerciseDetail(
            summary = ExerciseSummary(
                id = exercise.exerciseId,
                name = exercise.name,
                difficulty = "Intermediate",
                bodyRegion = exercise.bodyRegion,
                targetMuscleGroup = exercise.targetMuscleGroup,
                equipment = exercise.equipment,
                secondaryEquipment = null,
                mechanics = null,
                favorite = false,
            ),
            notes = null,
            primeMover = primeMover,
            secondaryMuscle = secondaryMuscle,
            tertiaryMuscle = tertiaryMuscle,
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

    private fun profile(durationMinutes: Int): UserProfile {
        return UserProfile(
            goal = "General Fitness",
            experience = "Intermediate",
            durationMinutes = durationMinutes,
            weeklyFrequency = 4,
            splitProgramId = 1L,
            units = "imperial",
            activeLocationModeId = 2L,
            workoutStyle = "balanced",
            themePreference = ThemePreference.Dark,
        )
    }
}
