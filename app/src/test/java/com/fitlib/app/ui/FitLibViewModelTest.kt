package com.fitlib.app.ui

import com.fitlib.app.data.ActiveSession
import com.fitlib.app.data.HistoryReuseMode
import com.fitlib.app.data.RecommendationSource
import com.fitlib.app.data.SessionExercise
import com.fitlib.app.data.SessionSet
import com.fitlib.app.data.ThemePreference
import com.fitlib.app.data.UserProfile
import com.fitlib.app.data.WorkoutExercise
import com.fitlib.app.data.WorkoutExerciseSetDraft
import com.fitlib.app.data.WorkoutPlan
import com.fitlib.app.data.ProgramSetupDraft
import com.fitlib.app.data.normalizeWorkoutDurationMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FitLibViewModelTest {
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
    ): SessionExercise {
        return SessionExercise(
            exerciseId = id,
            name = name,
            bodyRegion = "Upper Body",
            targetMuscleGroup = "Chest",
            equipment = "Cable",
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
