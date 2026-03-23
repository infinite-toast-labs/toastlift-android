package com.fitlib.app.data

import com.fitlib.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class DailyCoachMessageSource {
    GEMINI,
    DETERMINISTIC_FALLBACK,
}

data class DailyCoachMessage(
    val generatedForDate: LocalDate,
    val text: String,
    val thesisId: String,
    val source: DailyCoachMessageSource,
)

internal data class DailyCoachRecentWorkout(
    val title: String,
    val localDate: LocalDate,
    val exerciseNames: List<String>,
)

internal data class DailyCoachPlanSnapshot(
    val activeProgramTitle: String? = null,
    val activeProgramStatus: String? = null,
    val expectedFocus: String? = null,
    val timeBudgetMinutes: Int? = null,
    val coachBrief: String? = null,
    val nextExercises: List<String> = emptyList(),
    val recoverableSkippedFocus: String? = null,
)

internal data class DailyCoachSnapshot(
    val generatedForDate: LocalDate,
    val zoneId: ZoneId,
    val profile: UserProfile?,
    val todayAlreadyCompleted: Boolean,
    val workoutsLast7Days: Int,
    val workoutsLast14Days: Int,
    val consecutiveTrainingDayStreak: Int,
    val daysSinceLastWorkout: Int?,
    val missedDaysSinceLastWorkout: Int?,
    val lastWorkout: DailyCoachRecentWorkout?,
    val recentWorkouts: List<DailyCoachRecentWorkout>,
    val plan: DailyCoachPlanSnapshot,
)

internal data class DailyCoachThesisPattern(
    val id: String,
    val instruction: String,
    val score: (DailyCoachSnapshot) -> Int,
)

internal data class DailyCoachPromptPayload(
    val generatedForDate: LocalDate,
    val timezone: String,
    val selectedThesisId: String,
    val selectedThesisInstruction: String,
    val goal: String?,
    val experience: String?,
    val preferredDurationMinutes: Int?,
    val weeklyFrequencyTarget: Int?,
    val workoutStyle: String?,
    val todayAlreadyCompleted: Boolean,
    val workoutsLast7Days: Int,
    val workoutsLast14Days: Int,
    val consecutiveTrainingDayStreak: Int,
    val daysSinceLastWorkout: Int?,
    val missedDaysSinceLastWorkout: Int?,
    val lastWorkoutTitle: String?,
    val lastWorkoutLocalDate: String?,
    val lastWorkoutExerciseNames: List<String>,
    val recentWorkoutTitles: List<String>,
    val activeProgramTitle: String?,
    val activeProgramStatus: String?,
    val expectedFocus: String?,
    val sessionTimeBudgetMinutes: Int?,
    val coachBrief: String?,
    val nextExercises: List<String>,
    val recoverableSkippedFocus: String?,
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("local_date", generatedForDate.toString())
            .put("timezone", timezone)
            .put(
                "selected_thesis",
                JSONObject()
                    .put("id", selectedThesisId)
                    .put("instruction", selectedThesisInstruction),
            )
            .put(
                "profile",
                JSONObject()
                    .put("goal", goal)
                    .put("experience", experience)
                    .put("preferred_duration_minutes", preferredDurationMinutes)
                    .put("weekly_frequency_target", weeklyFrequencyTarget)
                    .put("workout_style", workoutStyle),
            )
            .put(
                "recent_history",
                JSONObject()
                    .put("today_already_completed", todayAlreadyCompleted)
                    .put("workouts_last_7_days", workoutsLast7Days)
                    .put("workouts_last_14_days", workoutsLast14Days)
                    .put("consecutive_training_day_streak", consecutiveTrainingDayStreak)
                    .put("days_since_last_workout", daysSinceLastWorkout)
                    .put("missed_days_since_last_workout", missedDaysSinceLastWorkout)
                    .put(
                        "last_workout",
                        JSONObject()
                            .put("title", lastWorkoutTitle)
                            .put("local_date", lastWorkoutLocalDate)
                            .put("exercise_names", JSONArray(lastWorkoutExerciseNames)),
                    )
                    .put("recent_workout_titles", JSONArray(recentWorkoutTitles)),
            )
            .put(
                "today_plan",
                JSONObject()
                    .put("active_program_title", activeProgramTitle)
                    .put("active_program_status", activeProgramStatus)
                    .put("expected_focus", expectedFocus)
                    .put("session_time_budget_minutes", sessionTimeBudgetMinutes)
                    .put("coach_brief", coachBrief)
                    .put("next_exercises", JSONArray(nextExercises))
                    .put("recoverable_skipped_focus", recoverableSkippedFocus),
            )
    }
}

internal val dailyCoachThesisPatterns = listOf(
    DailyCoachThesisPattern(
        id = "today_win_recovery",
        instruction = "Acknowledge that today's workout is already done and frame the rest of the day as recovery that protects momentum.",
    ) { snapshot ->
        if (snapshot.todayAlreadyCompleted) 100 else 0
    },
    DailyCoachThesisPattern(
        id = "long_gap_restart",
        instruction = "Treat a long gap as a clean restart. Remove shame, lower the stakes, and make the first step feel small.",
    ) { snapshot ->
        if ((snapshot.daysSinceLastWorkout ?: 0) >= 5 && !snapshot.todayAlreadyCompleted) 94 else 0
    },
    DailyCoachThesisPattern(
        id = "missed_day_recovery",
        instruction = "Normalize a missed day or skipped session and steer the user toward a smooth return instead of compensation.",
    ) { snapshot ->
        val gap = snapshot.daysSinceLastWorkout ?: 0
        if (!snapshot.todayAlreadyCompleted && (gap in 2..4 || snapshot.plan.recoverableSkippedFocus != null)) 88 else 0
    },
    DailyCoachThesisPattern(
        id = "focus_day_attack",
        instruction = "Anchor the message around the expected focus for today and make the main work feel clear and specific.",
    ) { snapshot ->
        if (!snapshot.todayAlreadyCompleted && snapshot.plan.expectedFocus != null) 78 else 0
    },
    DailyCoachThesisPattern(
        id = "concrete_session_nudge",
        instruction = "Reduce friction by naming the first one or two exercises or the first concrete step in the session.",
    ) { snapshot ->
        if (!snapshot.todayAlreadyCompleted && snapshot.plan.nextExercises.isNotEmpty()) 74 else 0
    },
    DailyCoachThesisPattern(
        id = "momentum_guard",
        instruction = "Protect recent consistency by reminding the user that another ordinary session keeps momentum alive.",
    ) { snapshot ->
        if (!snapshot.todayAlreadyCompleted && snapshot.workoutsLast7Days >= 3) 72 else 0
    },
    DailyCoachThesisPattern(
        id = "minimum_dose_counts",
        instruction = "If time is tight, sell the minimum effective session and keep the ask realistic.",
    ) { snapshot ->
        val duration = snapshot.profile?.durationMinutes ?: snapshot.plan.timeBudgetMinutes
            ?: 0
        if (!snapshot.todayAlreadyCompleted && duration in 1..35) 67 else 0
    },
    DailyCoachThesisPattern(
        id = "strength_progress_echo",
        instruction = "For strength-oriented users, reinforce crisp quality work and confidence built from recent sessions.",
    ) { snapshot ->
        if (!snapshot.todayAlreadyCompleted && snapshot.profile?.goal == "Strength" && snapshot.lastWorkout != null) 66 else 0
    },
    DailyCoachThesisPattern(
        id = "weekly_target_close",
        instruction = "Tie today's work to the weekly frequency target and show how one session keeps the week on track.",
    ) { snapshot ->
        val target = snapshot.profile?.weeklyFrequency ?: 0
        if (!snapshot.todayAlreadyCompleted && target > 0 &&
            snapshot.workoutsLast7Days in (target - 2).coerceAtLeast(0)..target
        ) {
            70
        } else {
            0
        }
    },
    DailyCoachThesisPattern(
        id = "habit_rebuild",
        instruction = "When recent training is sparse, emphasize a small repeatable action over intensity or perfection.",
    ) { snapshot ->
        if (!snapshot.todayAlreadyCompleted && snapshot.workoutsLast14Days <= 1) 64 else 0
    },
)

internal fun selectDailyCoachThesis(snapshot: DailyCoachSnapshot): DailyCoachThesisPattern {
    val scoredPatterns = dailyCoachThesisPatterns
        .map { pattern -> pattern to pattern.score(snapshot) }
        .filter { (_, score) -> score > 0 }

    if (scoredPatterns.isEmpty()) {
        return dailyCoachThesisPatterns.first { it.id == "habit_rebuild" }
    }

    val maxScore = scoredPatterns.maxOf { it.second }
    val finalists = scoredPatterns
        .filter { (_, score) -> score >= maxScore - 12 }
        .sortedWith(compareByDescending<Pair<DailyCoachThesisPattern, Int>> { it.second }.thenBy { it.first.id })

    val seed = listOf(
        snapshot.generatedForDate.toString(),
        snapshot.profile?.goal.orEmpty(),
        snapshot.daysSinceLastWorkout?.toString().orEmpty(),
        snapshot.plan.expectedFocus.orEmpty(),
        snapshot.lastWorkout?.title.orEmpty(),
    ).joinToString("|")
    val index = positiveHash(seed) % finalists.size
    return finalists[index].first
}

internal fun buildDailyCoachPromptPayload(
    snapshot: DailyCoachSnapshot,
    thesis: DailyCoachThesisPattern,
): DailyCoachPromptPayload {
    return DailyCoachPromptPayload(
        generatedForDate = snapshot.generatedForDate,
        timezone = snapshot.zoneId.id,
        selectedThesisId = thesis.id,
        selectedThesisInstruction = thesis.instruction,
        goal = snapshot.profile?.goal,
        experience = snapshot.profile?.experience,
        preferredDurationMinutes = snapshot.profile?.durationMinutes ?: snapshot.plan.timeBudgetMinutes,
        weeklyFrequencyTarget = snapshot.profile?.weeklyFrequency,
        workoutStyle = snapshot.profile?.workoutStyle,
        todayAlreadyCompleted = snapshot.todayAlreadyCompleted,
        workoutsLast7Days = snapshot.workoutsLast7Days,
        workoutsLast14Days = snapshot.workoutsLast14Days,
        consecutiveTrainingDayStreak = snapshot.consecutiveTrainingDayStreak,
        daysSinceLastWorkout = snapshot.daysSinceLastWorkout,
        missedDaysSinceLastWorkout = snapshot.missedDaysSinceLastWorkout,
        lastWorkoutTitle = snapshot.lastWorkout?.title,
        lastWorkoutLocalDate = snapshot.lastWorkout?.localDate?.toString(),
        lastWorkoutExerciseNames = snapshot.lastWorkout?.exerciseNames?.take(3).orEmpty(),
        recentWorkoutTitles = snapshot.recentWorkouts.map { it.title }.take(3),
        activeProgramTitle = snapshot.plan.activeProgramTitle,
        activeProgramStatus = snapshot.plan.activeProgramStatus,
        expectedFocus = snapshot.plan.expectedFocus,
        sessionTimeBudgetMinutes = snapshot.plan.timeBudgetMinutes ?: snapshot.profile?.durationMinutes,
        coachBrief = snapshot.plan.coachBrief,
        nextExercises = snapshot.plan.nextExercises.take(3),
        recoverableSkippedFocus = snapshot.plan.recoverableSkippedFocus,
    )
}

internal interface DailyCoachRemoteGenerator {
    fun generate(payload: DailyCoachPromptPayload): String
}

internal class GeminiDailyCoachRemoteGenerator(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val model: String = BuildConfig.GEMINI_PRIMARY_MODEL,
) : DailyCoachRemoteGenerator {
    override fun generate(payload: DailyCoachPromptPayload): String {
        require(apiKey.isNotBlank()) { "Missing GEMINI_API_KEY for daily coaching." }
        require(model.isNotBlank()) { "Missing GEMINI_PRIMARY_MODEL for daily coaching." }

        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", buildPrompt(payload)),
                        ),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.7)
                    .put("responseMimeType", "application/json"),
            )

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use(BufferedReader::readText)
                .orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Gemini request failed ($status): $body")
            }
            val responseText = JSONObject(body)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                .orEmpty()
            if (responseText.isBlank()) {
                throw IllegalStateException("Gemini returned an empty daily coaching response.")
            }
            val payloadJson = JSONObject(extractJsonObject(responseText))
            return sanitizeCoachMessage(payloadJson.optString("message"))
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPrompt(payload: DailyCoachPromptPayload): String {
        return """
            You write one short daily coaching message for a workout app user when they open the app.
            Use only the structured data below.
            
            Rules:
            - Write exactly one sentence.
            - Maximum 28 words.
            - Sound direct, calm, and specific.
            - No emojis, hashtags, exclamation marks, or quotation marks.
            - Do not mention metrics or facts that are not present in the payload.
            - If the user missed days, normalize it without guilt.
            - If today's workout is already complete, acknowledge the win and pivot to recovery or momentum protection.
            
            Return only JSON in this exact shape:
            {"message":"..."}
            
            Payload:
            ${payload.toJson().toString(2)}
        """.trimIndent()
    }
}

internal class DeterministicDailyCoachGenerator {
    fun generate(payload: DailyCoachPromptPayload): String {
        val focus = payload.expectedFocus
        val firstExercises = payload.nextExercises.take(2)
        val exerciseLead = when (firstExercises.size) {
            0 -> null
            1 -> firstExercises.first()
            else -> "${firstExercises[0]} and ${firstExercises[1]}"
        }
        val focusSentence = focus?.let { "$it is the main target today." }
            ?: payload.recoverableSkippedFocus?.let { "Pick back up with $it and keep it steady." }
            ?: "Keep the first block simple and controlled."
        val weeklyTarget = payload.weeklyFrequencyTarget
        val duration = payload.sessionTimeBudgetMinutes ?: payload.preferredDurationMinutes ?: 30
        val gapDays = payload.daysSinceLastWorkout ?: 0

        val message = when (payload.selectedThesisId) {
            "today_win_recovery" -> choose(
                payload,
                "You already banked today's ${payload.lastWorkoutTitle ?: "session"}. Let the win stand and recover well enough to come back sharp.",
                "Today's work is already done with ${payload.lastWorkoutTitle ?: "your session"}. Protect the momentum now by keeping recovery easy and deliberate.",
            )
            "long_gap_restart" -> choose(
                payload,
                "A $gapDays-day gap does not erase your progress. Make today a calm re-entry and let the first 10 minutes do the heavy lifting.",
                "After $gapDays days away, the win is simply starting again. Keep today's effort clean, light, and easy to repeat.",
            )
            "missed_day_recovery" -> choose(
                payload,
                "One missed day only needs a smooth return. $focusSentence",
                "The fastest recovery from a missed day is a normal session, not a punishment one. $focusSentence",
            )
            "focus_day_attack" -> choose(
                payload,
                "Today is ${focus ?: "your main work"}. Win the first hard sets early, then leave with something clean still in the tank.",
                "Today's lane is ${focus ?: "the main session"}. Put your best attention on the first working sets and let the rest support that.",
            )
            "concrete_session_nudge" -> choose(
                payload,
                "If starting is the hard part, make it concrete: ${exerciseLead ?: "your first movement"} first, then keep rolling.",
                "Open with ${exerciseLead ?: "the first movement"}, and the rest of the session usually feels easier to carry.",
            )
            "momentum_guard" -> choose(
                payload,
                "You've logged ${payload.workoutsLast7Days} sessions in the last 7 days. Protect that momentum with another solid, ordinary day.",
                "Recent consistency is already doing the work. Another normal session today keeps the trend moving in your favor.",
            )
            "minimum_dose_counts" -> choose(
                payload,
                "A $duration-minute session is enough today. Get the main lift and one support movement done, and count that as a real win.",
                "Keep today's bar low and clear it: $duration minutes, the main movement, and one accessory still moves the week forward.",
            )
            "strength_progress_echo" -> choose(
                payload,
                "Recent work says the base is there. Chase one crisp top set today, then shut it down before form gets noisy.",
                "Strength builds on clean repeats. Hit one high-quality top effort today and make the rest look controlled.",
            )
            "weekly_target_close" -> choose(
                payload,
                "Today's session keeps you moving toward ${weeklyTarget ?: 0} sessions this week. ${focusSentence.removeSuffix(".")}.",
                "You're one session closer to the ${weeklyTarget ?: 0}-day rhythm you set. Bank today's work before the week gets away from you.",
            )
            else -> choose(
                payload,
                "Small counts today. Start one short session and give yourself permission to stop after the first two exercises if needed.",
                "When rhythm is thin, the job is repetition, not heroics. Show up, do a small session, and let the habit rebuild.",
            )
        }
        return sanitizeCoachMessage(message)
    }

    private fun choose(payload: DailyCoachPromptPayload, vararg options: String): String {
        val seed = "${payload.localDateSeed()}|${payload.selectedThesisId}|${payload.lastWorkoutTitle.orEmpty()}|${payload.expectedFocus.orEmpty()}"
        return options[positiveHash(seed) % options.size]
    }
}

class DailyCoachService internal constructor(
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val programRepository: ProgramRepository,
    private val catalogRepository: CatalogRepository,
    private val remoteGenerator: DailyCoachRemoteGenerator = GeminiDailyCoachRemoteGenerator(),
    private val fallbackGenerator: DeterministicDailyCoachGenerator = DeterministicDailyCoachGenerator(),
) {
    fun generateForToday(
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DailyCoachMessage {
        val snapshot = loadSnapshot(now = now, zoneId = zoneId)
        val thesis = selectDailyCoachThesis(snapshot)
        val payload = buildDailyCoachPromptPayload(snapshot, thesis)
        val liveMessage = runCatching { remoteGenerator.generate(payload) }.getOrNull()
        val messageText = liveMessage ?: fallbackGenerator.generate(payload)
        return DailyCoachMessage(
            generatedForDate = snapshot.generatedForDate,
            text = messageText,
            thesisId = thesis.id,
            source = if (liveMessage != null) DailyCoachMessageSource.GEMINI else DailyCoachMessageSource.DETERMINISTIC_FALLBACK,
        )
    }

    internal fun loadSnapshot(
        now: Instant,
        zoneId: ZoneId,
    ): DailyCoachSnapshot {
        val today = now.atZone(zoneId).toLocalDate()
        val profile = userRepository.loadProfile()
        val history = workoutRepository.loadHistory()
        val recentWorkouts = history.mapNotNull { summary ->
            val localDate = runCatching { Instant.parse(summary.completedAtUtc).atZone(zoneId).toLocalDate() }.getOrNull() ?: return@mapNotNull null
            DailyCoachRecentWorkout(
                title = summary.title,
                localDate = localDate,
                exerciseNames = summary.exerciseNames.distinct().take(3),
            )
        }
        val workoutDates = recentWorkouts.map { it.localDate }.distinct()
        val lastWorkout = recentWorkouts.firstOrNull()
        val todayAlreadyCompleted = lastWorkout?.localDate == today
        val daysSinceLastWorkout = lastWorkout?.let { ChronoUnit.DAYS.between(it.localDate, today).toInt() }
        val missedDays = daysSinceLastWorkout?.minus(1)?.coerceAtLeast(0)

        val activeProgram = runCatching { programRepository.loadActiveProgram() }.getOrNull()
        val nextSession = activeProgram?.let { programRepository.loadUpcomingSessions(it.id, 1).firstOrNull() }
        val skippedSession = activeProgram?.let { programRepository.loadMostRecentSkippedSession(it.id) }
        val nextExercises = nextSession?.let { session ->
            val plannedExercises = programRepository.loadExercisesForSession(session.id)
            catalogRepository.searchExercisesByIds(plannedExercises.map { it.exerciseId })
                .associateBy { it.id }
                .let { byId ->
                    plannedExercises
                        .sortedBy { it.sortOrder }
                        .mapNotNull { byId[it.exerciseId]?.name }
                        .distinct()
                        .take(3)
                }
        }.orEmpty()

        val expectedFocus = when {
            nextSession != null -> dailyCoachFocusLabel(SessionFocus.toFocusKey(nextSession.focusKey))
            else -> userRepository.loadNextFocus()?.let(::dailyCoachFocusLabel)
        }

        return DailyCoachSnapshot(
            generatedForDate = today,
            zoneId = zoneId,
            profile = profile,
            todayAlreadyCompleted = todayAlreadyCompleted,
            workoutsLast7Days = workoutDates.count { it >= today.minusDays(6) },
            workoutsLast14Days = workoutDates.count { it >= today.minusDays(13) },
            consecutiveTrainingDayStreak = consecutiveTrainingDayStreak(workoutDates),
            daysSinceLastWorkout = daysSinceLastWorkout,
            missedDaysSinceLastWorkout = missedDays,
            lastWorkout = lastWorkout,
            recentWorkouts = recentWorkouts.take(3),
            plan = DailyCoachPlanSnapshot(
                activeProgramTitle = activeProgram?.title,
                activeProgramStatus = activeProgram?.status?.name,
                expectedFocus = expectedFocus,
                timeBudgetMinutes = nextSession?.timeBudgetMinutes ?: profile?.durationMinutes,
                coachBrief = nextSession?.coachBrief,
                nextExercises = nextExercises,
                recoverableSkippedFocus = skippedSession?.let { dailyCoachFocusLabel(SessionFocus.toFocusKey(it.focusKey)) },
            ),
        )
    }
}

private fun DailyCoachPromptPayload.localDateSeed(): String = generatedForDate.toString()

private fun dailyCoachFocusLabel(focus: String): String = when (focus) {
    "upper_body" -> "Upper Body"
    "lower_body" -> "Lower Body"
    "push_day" -> "Push"
    "pull_day" -> "Pull"
    "legs_day" -> "Legs"
    "chest_day" -> "Chest"
    "back_day" -> "Back"
    "shoulders_arms_day" -> "Shoulders + Arms"
    FORMULA_A_UPPER_PUSH_STRENGTH_FOCUS_KEY -> "UPPER-PUSH-S (chest/delts/tri HEAVY)"
    FORMULA_A_LOWER_HIGH_REPS_FOCUS_KEY -> "LOWER-H (legs/abs HIGH REPS)"
    FORMULA_A_UPPER_PULL_STRENGTH_FOCUS_KEY -> "UPPER-PULL-S (back/biceps HEAVY)"
    FORMULA_A_UPPER_PUSH_HIGH_REPS_FOCUS_KEY -> "UPPER-PUSH-H (chest/delts/tri HIGH REPS)"
    FORMULA_A_LOWER_STRENGTH_FOCUS_KEY -> "LOWER-S (legs/abs HEAVY)"
    FORMULA_A_UPPER_PULL_HIGH_REPS_FOCUS_KEY -> "UPPER-PULL-H (back/biceps HIGH REPS)"
    FORMULA_B_GLUTES_HAMSTRINGS_STRENGTH_FOCUS_KEY -> "GLUTES+HAMSTRINGS-S (HEAVY)"
    FORMULA_B_UPPER_CHEST_HIGH_REPS_FOCUS_KEY -> "UPPER CHEST-H (HIGH REPS)"
    FORMULA_B_REAR_SIDE_DELTS_STRENGTH_FOCUS_KEY -> "REAR DELTS + SIDE DELTS-S (HEAVY)"
    FORMULA_B_GLUTES_HAMSTRINGS_HIGH_REPS_FOCUS_KEY -> "GLUTES+HAMSTRINGS-H (HIGH REPS)"
    FORMULA_B_UPPER_CHEST_STRENGTH_FOCUS_KEY -> "UPPER CHEST-S (HEAVY)"
    FORMULA_B_REAR_SIDE_DELTS_HIGH_REPS_FOCUS_KEY -> "REAR DELTS + SIDE DELTS-H (HIGH REPS)"
    else -> "Full Body"
}

private fun consecutiveTrainingDayStreak(dates: List<LocalDate>): Int {
    if (dates.isEmpty()) return 0
    val sortedDates = dates.distinct().sortedDescending()
    var streak = 1
    for (index in 1 until sortedDates.size) {
        if (sortedDates[index] == sortedDates[index - 1].minusDays(1)) {
            streak += 1
        } else {
            break
        }
    }
    return streak
}

private fun positiveHash(value: String): Int {
    return value.fold(17) { acc, char -> (acc * 31) + char.code } and Int.MAX_VALUE
}

private fun sanitizeCoachMessage(message: String): String {
    return message
        .trim()
        .removePrefix("\"")
        .removeSuffix("\"")
        .replace(Regex("\\s+"), " ")
}

private fun extractJsonObject(rawText: String): String {
    val start = rawText.indexOf('{')
    if (start == -1) error("No JSON object found in daily coach response.")
    var depth = 0
    for (index in start until rawText.length) {
        when (rawText[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return rawText.substring(start, index + 1)
                }
            }
        }
    }
    error("Unterminated JSON object in daily coach response.")
}
