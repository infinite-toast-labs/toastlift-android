package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DailyCoachServiceTest {
    @Test
    fun dailyCoachThesisPatterns_stayWithinDesignedRange() {
        assertTrue(dailyCoachThesisPatterns.size in 8..15)
    }

    @Test
    fun selectDailyCoachThesis_prefersRecoveryMessageWhenWorkoutAlreadyCompletedToday() {
        val thesis = selectDailyCoachThesis(
            snapshot(
                todayAlreadyCompleted = true,
                daysSinceLastWorkout = 0,
                workoutsLast7Days = 4,
            ),
        )

        assertEquals("today_win_recovery", thesis.id)
    }

    @Test
    fun selectDailyCoachThesis_prefersLongGapRestartAfterFiveDaysAway() {
        val thesis = selectDailyCoachThesis(
            snapshot(
                todayAlreadyCompleted = false,
                daysSinceLastWorkout = 6,
                missedDaysSinceLastWorkout = 5,
                workoutsLast14Days = 1,
            ),
        )

        assertEquals("long_gap_restart", thesis.id)
    }

    @Test
    fun buildDailyCoachPromptPayload_containsExactStructuredFields() {
        val snapshot = snapshot(
            generatedForDate = LocalDate.parse("2026-03-20"),
            todayAlreadyCompleted = false,
            workoutsLast7Days = 3,
            workoutsLast14Days = 5,
            consecutiveTrainingDayStreak = 2,
            daysSinceLastWorkout = 1,
            missedDaysSinceLastWorkout = 0,
            lastWorkout = DailyCoachRecentWorkout(
                title = "Push Day",
                localDate = LocalDate.parse("2026-03-19"),
                exerciseNames = listOf("Bench Press", "Incline Dumbbell Press", "Dip", "Cable Fly"),
            ),
            recentWorkouts = listOf(
                DailyCoachRecentWorkout(
                    title = "Push Day",
                    localDate = LocalDate.parse("2026-03-19"),
                    exerciseNames = listOf("Bench Press"),
                ),
                DailyCoachRecentWorkout(
                    title = "Lower Day",
                    localDate = LocalDate.parse("2026-03-17"),
                    exerciseNames = listOf("Back Squat"),
                ),
            ),
            plan = DailyCoachPlanSnapshot(
                activeProgramTitle = "Spring Block",
                activeProgramStatus = "ACTIVE",
                expectedFocus = "Lower Body",
                timeBudgetMinutes = 45,
                coachBrief = "Build quality volume on the main lower lift.",
                nextExercises = listOf("Back Squat", "Romanian Deadlift", "Walking Lunge", "Leg Curl"),
                recoverableSkippedFocus = "Push",
            ),
        )
        val thesis = dailyCoachThesisPatterns.first { it.id == "focus_day_attack" }

        val payload = buildDailyCoachPromptPayload(snapshot, thesis)

        assertEquals(LocalDate.parse("2026-03-20"), payload.generatedForDate)
        assertEquals("UTC", payload.timezone)
        assertEquals("focus_day_attack", payload.selectedThesisId)
        assertEquals("Strength", payload.goal)
        assertEquals(45, payload.preferredDurationMinutes)
        assertEquals(3, payload.workoutsLast7Days)
        assertEquals("Push Day", payload.lastWorkoutTitle)
        assertEquals(3, payload.lastWorkoutExerciseNames.size)
        assertEquals("Lower Body", payload.expectedFocus)
        assertEquals(3, payload.nextExercises.size)
        assertEquals("Push", payload.recoverableSkippedFocus)
    }

    @Test
    fun deterministicFallback_usesConcreteSessionNudgeExercises() {
        val generator = DeterministicDailyCoachGenerator()
        val message = generator.generate(
            DailyCoachPromptPayload(
                generatedForDate = LocalDate.parse("2026-03-20"),
                timezone = "UTC",
                selectedThesisId = "concrete_session_nudge",
                selectedThesisInstruction = "Name the first two exercises.",
                goal = "Hypertrophy",
                experience = "Intermediate",
                preferredDurationMinutes = 45,
                weeklyFrequencyTarget = 4,
                workoutStyle = "balanced",
                todayAlreadyCompleted = false,
                workoutsLast7Days = 3,
                workoutsLast14Days = 5,
                consecutiveTrainingDayStreak = 2,
                daysSinceLastWorkout = 1,
                missedDaysSinceLastWorkout = 0,
                lastWorkoutTitle = "Upper Day",
                lastWorkoutLocalDate = "2026-03-19",
                lastWorkoutExerciseNames = listOf("Bench Press", "Row"),
                recentWorkoutTitles = listOf("Upper Day", "Lower Day"),
                activeProgramTitle = "Spring Block",
                activeProgramStatus = "ACTIVE",
                expectedFocus = "Lower Body",
                sessionTimeBudgetMinutes = 45,
                coachBrief = "Build quality volume on the main lower lift.",
                nextExercises = listOf("Back Squat", "Romanian Deadlift", "Walking Lunge"),
                recoverableSkippedFocus = null,
            ),
        )

        assertTrue(message.contains("Back Squat") || message.contains("Romanian Deadlift"))
    }

    private fun snapshot(
        generatedForDate: LocalDate = LocalDate.parse("2026-03-20"),
        todayAlreadyCompleted: Boolean = false,
        workoutsLast7Days: Int = 1,
        workoutsLast14Days: Int = 2,
        consecutiveTrainingDayStreak: Int = 1,
        daysSinceLastWorkout: Int? = 1,
        missedDaysSinceLastWorkout: Int? = 0,
        lastWorkout: DailyCoachRecentWorkout? = DailyCoachRecentWorkout(
            title = "Full Body",
            localDate = LocalDate.parse("2026-03-19"),
            exerciseNames = listOf("Goblet Squat", "Push-Up"),
        ),
        recentWorkouts: List<DailyCoachRecentWorkout> = listOf(
            DailyCoachRecentWorkout(
                title = "Full Body",
                localDate = LocalDate.parse("2026-03-19"),
                exerciseNames = listOf("Goblet Squat", "Push-Up"),
            ),
        ),
        plan: DailyCoachPlanSnapshot = DailyCoachPlanSnapshot(),
    ): DailyCoachSnapshot {
        return DailyCoachSnapshot(
            generatedForDate = generatedForDate,
            zoneId = ZoneId.of("UTC"),
            profile = UserProfile(
                goal = "Strength",
                experience = "Intermediate",
                durationMinutes = 45,
                weeklyFrequency = 4,
                splitProgramId = 2L,
                units = "imperial",
                activeLocationModeId = 1L,
                workoutStyle = "balanced",
                themePreference = ThemePreference.Dark,
            ),
            todayAlreadyCompleted = todayAlreadyCompleted,
            workoutsLast7Days = workoutsLast7Days,
            workoutsLast14Days = workoutsLast14Days,
            consecutiveTrainingDayStreak = consecutiveTrainingDayStreak,
            daysSinceLastWorkout = daysSinceLastWorkout,
            missedDaysSinceLastWorkout = missedDaysSinceLastWorkout,
            lastWorkout = lastWorkout,
            recentWorkouts = recentWorkouts,
            plan = plan,
        )
    }
}
