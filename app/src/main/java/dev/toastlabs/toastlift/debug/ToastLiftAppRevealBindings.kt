package dev.toastlabs.toastlift.debug

import android.content.Intent
import com.appreveal.AppReveal
import com.appreveal.debug.DebugFeatureFlagMutating
import com.appreveal.debug.DebugProfile
import com.appreveal.debug.DebugSession
import com.appreveal.debug.DebugSessionEndResult
import com.appreveal.debug.DebugSessionHosting
import com.appreveal.debug.OpenScreenResult
import com.appreveal.debug.ScreenDescriptor
import com.appreveal.debug.ScreenFixture
import com.appreveal.debug.ScreenFixtureProviding
import com.appreveal.debug.ScreenNavigating
import com.appreveal.debug.ScreenRegistryProviding
import dev.toastlabs.toastlift.MainActivity
import dev.toastlabs.toastlift.ToastLiftApplication
import dev.toastlabs.toastlift.data.ActiveSession
import dev.toastlabs.toastlift.data.DataEnvironment
import dev.toastlabs.toastlift.data.ExerciseSummary
import dev.toastlabs.toastlift.data.OnboardingDraft
import dev.toastlabs.toastlift.data.RecommendationSource
import dev.toastlabs.toastlift.data.SessionExercise
import dev.toastlabs.toastlift.data.SessionSet
import dev.toastlabs.toastlift.data.WorkUnitDefinition
import dev.toastlabs.toastlift.data.nextSessionSetId
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

object ToastLiftAppRevealBindings {
    private var sessionHost: ToastLiftDebugSessionHost? = null
    private var screenRegistry: ToastLiftScreenRegistry? = null
    private var screenNavigator: ToastLiftScreenNavigator? = null
    private var featureFlags: ToastLiftDebugFeatureFlags? = null
    private var exerciseFixture: ExerciseLoggingFixtureProvider? = null
    private var workoutOverviewFixture: ActiveWorkoutOverviewFixtureProvider? = null

    fun install(app: ToastLiftApplication) {
        sessionHost = ToastLiftDebugSessionHost(app).also(AppReveal::registerDebugSessionHost)
        screenRegistry = ToastLiftScreenRegistry().also(AppReveal::registerScreenRegistry)
        screenNavigator = ToastLiftScreenNavigator(app).also(AppReveal::registerScreenNavigator)
        featureFlags = ToastLiftDebugFeatureFlags(app).also(AppReveal::registerDebugFeatureFlagMutator)
        exerciseFixture = ExerciseLoggingFixtureProvider(app).also(AppReveal::registerFixtureProvider)
        workoutOverviewFixture = ActiveWorkoutOverviewFixtureProvider(app).also(AppReveal::registerFixtureProvider)
    }
}

private class ToastLiftDebugSessionHost(
    private val app: ToastLiftApplication,
) : DebugSessionHosting {
    private var active: ActiveDebugSession? = null

    @Synchronized
    override fun begin(profile: DebugProfile): DebugSession {
        check(active == null) { "A ToastLift debug session is already active." }
        val id = UUID.randomUUID().toString()
        val realDatabase = app.container.toastLiftDatabase
        realDatabase.open()
        realDatabase.close()

        val realFile = realDatabase.databaseFile()
        val beforeHash = realFile.sha256OrMissing()
        val sessionDir = File(app.cacheDir, "appreveal-debug/$id").apply { mkdirs() }
        val debugDbFile = File(sessionDir, "toastlift.db")
        if (realFile.exists()) {
            realFile.copyTo(debugDbFile, overwrite = true)
            copySidecar(realFile, debugDbFile, "-wal")
            copySidecar(realFile, debugDbFile, "-shm")
        }

        active = ActiveDebugSession(
            id = id,
            realDatabaseHash = beforeHash,
            sessionDir = sessionDir,
            featureFlags = profile.featureFlags,
        )
        app.replaceContainerForDebug(
            DataEnvironment.debugEphemeral(
                databaseFile = debugDbFile,
                debugSessionId = id,
                featureFlags = profile.featureFlags,
                frozenTimeIso = profile.frozenTimeIso,
                randomSeed = profile.randomSeed,
            ),
        )
        app.ensureDefaultDebugProfile()
        app.applyEphemeralDebugSettings(profile.featureFlags)
        launchMain(tab = "Home", clearTask = true)
        return DebugSession(
            id = id,
            metadata = mapOf(
                "database" to "disposable_copy",
                "realDatabaseHash" to beforeHash,
                "featureFlags" to profile.featureFlags,
            ),
        )
    }

    @Synchronized
    override fun end(sessionId: String): DebugSessionEndResult {
        val current = active ?: error("No ToastLift debug session is active.")
        require(current.id == sessionId) { "Unknown debug session: $sessionId" }
        app.restoreRealContainerAfterDebug()
        val afterHash = app.container.toastLiftDatabase.databaseFile().sha256OrMissing()
        val unchanged = current.realDatabaseHash == afterHash
        current.sessionDir.deleteRecursively()
        active = null
        launchMain(tab = "Home", clearTask = true)
        return DebugSessionEndResult(
            id = sessionId,
            restored = true,
            realStateUnchanged = unchanged,
            metadata = mapOf(
                "beforeHash" to current.realDatabaseHash,
                "afterHash" to afterHash,
            ),
        )
    }

    private fun launchMain(
        tab: String,
        clearTask: Boolean,
    ) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_CLEAR_TOP
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .addFlags(flags)
                .putExtra(EXTRA_DEBUG_TAB, tab),
        )
    }
}

private class ToastLiftScreenRegistry : ScreenRegistryProviding {
    override fun screens(): List<ScreenDescriptor> = listOf(
        ScreenDescriptor("home.today", "Home / Today"),
        ScreenDescriptor("generate.main", "Generate"),
        ScreenDescriptor("explore.library", "Explore / Library"),
        ScreenDescriptor("profile.main", "Profile"),
        ScreenDescriptor("sheet.library_filters", "Sheet / Library filters"),
        ScreenDescriptor("sheet.exercise_detail", "Sheet / Exercise detail"),
        ScreenDescriptor("sheet.exercise_description", "Sheet / Exercise description"),
        ScreenDescriptor("sheet.exercise_family", "Sheet / Exercise family"),
        ScreenDescriptor("sheet.exercise_history", "Sheet / Exercise history"),
        ScreenDescriptor("sheet.exercise_videos", "Sheet / Exercise videos"),
        ScreenDescriptor("sheet.history_detail", "Sheet / History workout detail"),
        ScreenDescriptor("sheet.profile_equipment_home", "Sheet / Profile home equipment"),
        ScreenDescriptor("sheet.profile_equipment_gym", "Sheet / Profile gym equipment"),
        ScreenDescriptor("sheet.profile_delete_data", "Sheet / Profile delete data"),
        ScreenDescriptor("sheet.active_workout_details", "Sheet / Active workout details"),
        ScreenDescriptor("sheet.active_session_filters", "Sheet / Active session filters"),
        ScreenDescriptor("sheet.generated_workout_swap", "Sheet / Generated workout swap"),
        ScreenDescriptor("sheet.manual_builder", "Sheet / Manual builder"),
        ScreenDescriptor("sheet.skipped_exercise_feedback", "Sheet / Skipped exercise feedback"),
        ScreenDescriptor("sheet.sfr_debrief", "Sheet / Program SFR debrief"),
        ScreenDescriptor("sheet.checkpoint_review", "Sheet / Program checkpoint review"),
        ScreenDescriptor("history.dashboard", "History / Dashboard"),
        ScreenDescriptor("history.workouts", "History / Workouts"),
        ScreenDescriptor("history.stats", "History / Stats"),
        ScreenDescriptor("history.milestones", "History / Milestones"),
        ScreenDescriptor("history.streak", "History / Streak"),
        ScreenDescriptor("history.calendar", "History / Calendar"),
        ScreenDescriptor("history.weekly-muscles", "History / Weekly muscle targets"),
        ScreenDescriptor("history.token-balance", "History / Token balance"),
        ScreenDescriptor("history.bounty-cards", "History / Bounty cards"),
        ScreenDescriptor(
            key = "active.exercise_logging",
            title = "Active exercise logging",
            description = "Debug-created active workout with one exercise and configurable completed sets.",
            supportsFixtures = true,
        ),
        ScreenDescriptor(
            key = "active.workout_overview",
            title = "Active workout overview",
            description = "Captured active workout overview with the full active exercise list and set state.",
            supportsFixtures = true,
        ),
    )
}

private class ToastLiftScreenNavigator(
    private val app: ToastLiftApplication,
) : ScreenNavigating {
    override fun open(
        screenKey: String,
        params: Map<String, String>,
        sessionId: String?,
    ): OpenScreenResult {
        val screenRoute = when (screenKey) {
            "home.today" -> ScreenRoute(tab = "Home")
            "generate.main" -> ScreenRoute(tab = "Generate")
            "explore.library" -> ScreenRoute(tab = "Explore")
            "profile.main" -> ScreenRoute(tab = "Home", debugSurface = "profile")
            "sheet.library_filters" -> ScreenRoute(tab = "Explore", debugSurface = "sheet.library_filters")
            "sheet.exercise_detail" -> ScreenRoute(tab = "Explore", debugSurface = "sheet.exercise_detail")
            "sheet.exercise_description" -> ScreenRoute(tab = "Explore", debugSurface = "sheet.exercise_description")
            "sheet.exercise_family" -> ScreenRoute(tab = "Explore", debugSurface = "sheet.exercise_family")
            "sheet.exercise_history" -> ScreenRoute(tab = "Explore", debugSurface = "sheet.exercise_history")
            "sheet.exercise_videos" -> ScreenRoute(tab = "Explore", debugSurface = "sheet.exercise_videos")
            "sheet.history_detail" -> ScreenRoute(tab = "Explore", debugSurface = "sheet.history_detail")
            "sheet.profile_equipment_home" -> ScreenRoute(tab = "Home", debugSurface = "sheet.profile_equipment_home")
            "sheet.profile_equipment_gym" -> ScreenRoute(tab = "Home", debugSurface = "sheet.profile_equipment_gym")
            "sheet.profile_delete_data" -> ScreenRoute(tab = "Home", debugSurface = "sheet.profile_delete_data")
            "sheet.active_workout_details" -> ScreenRoute(tab = "Home", debugSurface = "sheet.active_workout_details")
            "sheet.active_session_filters" -> ScreenRoute(tab = "Home", debugSurface = "sheet.active_session_filters")
            "sheet.generated_workout_swap" -> ScreenRoute(tab = "Generate", debugSurface = "sheet.generated_workout_swap")
            "sheet.manual_builder" -> ScreenRoute(tab = "Generate", debugSurface = "sheet.manual_builder")
            "sheet.skipped_exercise_feedback" -> ScreenRoute(tab = "Home", debugSurface = "sheet.skipped_exercise_feedback")
            "sheet.sfr_debrief" -> ScreenRoute(tab = "Home", debugSurface = "sheet.sfr_debrief")
            "sheet.checkpoint_review" -> ScreenRoute(tab = "Home", debugSurface = "sheet.checkpoint_review")
            "history.dashboard" -> ScreenRoute(tab = "Explore", debugSurface = "history.dashboard")
            "history.workouts" -> ScreenRoute(tab = "Explore", debugSurface = "history.workouts")
            "history.stats" -> ScreenRoute(tab = "Explore", debugSurface = "history.stats")
            "history.milestones" -> ScreenRoute(tab = "Explore", debugSurface = "history.milestones")
            "history.streak" -> ScreenRoute(tab = "Explore", debugSurface = "history.streak")
            "history.calendar" -> ScreenRoute(tab = "Explore", debugSurface = "history.calendar")
            "history.weekly-muscles" -> ScreenRoute(tab = "Explore", debugSurface = "history.weekly-muscles")
            "history.token-balance" -> ScreenRoute(tab = "Explore", debugSurface = "history.token-balance")
            "history.bounty-cards" -> ScreenRoute(tab = "Explore", debugSurface = "history.bounty-cards")
            "active.exercise_logging" -> ScreenRoute(tab = "Home", debugSurface = "active.exercise_logging")
            "active.workout_overview" -> ScreenRoute(tab = "Home", debugSurface = "active.workout_overview")
            else -> return OpenScreenResult(
                screenKey = screenKey,
                opened = false,
                ready = false,
                message = "Unknown ToastLift screen key.",
            )
        }
        val flags = if (screenRoute.debugSurface != null) {
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        } else {
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .addFlags(flags)
                .putExtra(EXTRA_DEBUG_TAB, screenRoute.tab)
                .apply {
                    screenRoute.debugSurface?.let { putExtra(EXTRA_DEBUG_SURFACE, it) }
                },
        )
        return OpenScreenResult(
            screenKey = screenKey,
            opened = true,
            metadata = mapOf(
                "tab" to screenRoute.tab,
                "debugSurface" to screenRoute.debugSurface,
                "debugSessionId" to sessionId,
            ),
        )
    }

    private fun ensureExerciseLoggingSession(params: Map<String, String>) {
        val completedSets = params["completed_sets"]?.toIntOrNull()?.coerceIn(0, 8) ?: 1
        val setCount = params["set_count"]?.toIntOrNull()?.coerceIn(1, 8) ?: 4
        val exercise = firstCatalogExercise()
        val session = buildExerciseLoggingSession(
            exercise = exercise,
            locationModeId = app.container.userRepository.loadProfile()?.activeLocationModeId ?: 1L,
            setCount = setCount,
            completedSets = completedSets,
        )
        app.container.workoutRepository.saveActiveSession(
            session = session,
            selectedExerciseIndex = params["exercise_index"]?.toIntOrNull() ?: 0,
        )
    }

    private fun ensureCompletedWorkoutHistory(params: Map<String, String>) {
        if (app.container.workoutRepository.loadHistory().isNotEmpty()) return
        val completedSets = params["completed_sets"]?.toIntOrNull()?.coerceIn(1, 8) ?: 4
        val setCount = params["set_count"]?.toIntOrNull()?.coerceIn(completedSets, 8) ?: completedSets
        val exercise = firstCatalogExercise()
        app.container.workoutRepository.saveCompletedWorkout(
            session = buildExerciseLoggingSession(
                exercise = exercise,
                title = "Debug Completed Workout",
                locationModeId = app.container.userRepository.loadProfile()?.activeLocationModeId ?: 1L,
                setCount = setCount,
                completedSets = completedSets,
            ),
        )
    }

    private fun firstCatalogExercise(): ExerciseSummary =
        app.container.catalogRepository.searchExercises(query = "", limit = 1).firstOrNull()
            ?: ExerciseSummary(
                id = 1L,
                name = "Goblet Squat",
                difficulty = "Beginner",
                bodyRegion = "Lower Body",
                targetMuscleGroup = "Quadriceps",
                equipment = "Dumbbell",
                secondaryEquipment = null,
                mechanics = null,
                favorite = false,
            )
}

private data class ScreenRoute(
    val tab: String,
    val debugSurface: String? = null,
)

private class ExerciseLoggingFixtureProvider(
    private val app: ToastLiftApplication,
) : ScreenFixtureProviding {
    override val screenKey: String = "active.exercise_logging"

    override fun capture(sessionId: String): ScreenFixture {
        val persisted = app.container.workoutRepository.loadActiveSession()
        val session = persisted?.session
        val exercise = session?.exercises?.getOrNull(persisted.selectedExerciseIndex ?: 0)
        return ScreenFixture(
            schemaVersion = 1,
            screenKey = screenKey,
            appVersion = dev.toastlabs.toastlift.BuildConfig.VERSION_NAME,
            appVersionCode = dev.toastlabs.toastlift.BuildConfig.VERSION_CODE.toLong(),
            featureFlags = app.container.dataEnvironment.featureFlags,
            capturedAt = Instant.now().toString(),
            inputs = mapOf(
                "debugSessionId" to sessionId,
                "title" to (session?.title ?: "Debug Workout"),
                "exerciseName" to (exercise?.name ?: "Debug Exercise"),
                "exerciseId" to (exercise?.exerciseId ?: 1L),
                "bodyRegion" to (exercise?.bodyRegion ?: "Lower Body"),
                "targetMuscleGroup" to (exercise?.targetMuscleGroup ?: "Quadriceps"),
                "equipment" to (exercise?.equipment ?: "Dumbbell"),
                "locationModeId" to (session?.locationModeId ?: 1L),
                "setCount" to (exercise?.sets?.size ?: 4),
                "completedSets" to (exercise?.sets?.count { it.completed } ?: 1),
                "selectedExerciseIndex" to (persisted?.selectedExerciseIndex ?: 0),
            ),
        )
    }

    override fun restore(
        sessionId: String,
        fixture: ScreenFixture,
    ) {
        val inputs = fixture.inputs
        val setCount = inputs["setCount"].asInt(defaultValue = 4).coerceIn(1, 8)
        val completedSets = inputs["completedSets"].asInt(defaultValue = 1).coerceIn(0, setCount)
        val exercise = ExerciseSummary(
            id = inputs["exerciseId"].asLong(defaultValue = 1L),
            name = inputs["exerciseName"]?.toString() ?: "Debug Exercise",
            difficulty = "Intermediate",
            bodyRegion = inputs["bodyRegion"]?.toString() ?: "Lower Body",
            targetMuscleGroup = inputs["targetMuscleGroup"]?.toString() ?: "Quadriceps",
            equipment = inputs["equipment"]?.toString() ?: "Dumbbell",
            secondaryEquipment = null,
            mechanics = null,
            favorite = false,
        )
        app.container.workoutRepository.saveActiveSession(
            session = buildExerciseLoggingSession(
                exercise = exercise,
                title = inputs["title"]?.toString() ?: "Debug Workout",
                locationModeId = inputs["locationModeId"].asLong(defaultValue = 1L),
                setCount = setCount,
                completedSets = completedSets,
            ),
            selectedExerciseIndex = inputs["selectedExerciseIndex"].asInt(defaultValue = 0),
        )
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_DEBUG_TAB, "Home"),
        )
    }
}

private class ActiveWorkoutOverviewFixtureProvider(
    private val app: ToastLiftApplication,
) : ScreenFixtureProviding {
    override val screenKey: String = "active.workout_overview"

    override fun capture(sessionId: String): ScreenFixture {
        val persisted = app.container.workoutRepository.loadActiveSession()
        val session = persisted?.session
        return ScreenFixture(
            schemaVersion = 1,
            screenKey = screenKey,
            appVersion = dev.toastlabs.toastlift.BuildConfig.VERSION_NAME,
            appVersionCode = dev.toastlabs.toastlift.BuildConfig.VERSION_CODE.toLong(),
            featureFlags = app.container.dataEnvironment.featureFlags,
            capturedAt = Instant.now().toString(),
            inputs = mapOf(
                "debugSessionId" to sessionId,
                "hasActiveSession" to (session != null),
                "selectedExerciseIndex" to persisted?.selectedExerciseIndex,
                "session" to session?.toFixtureMap(),
            ),
        )
    }

    override fun restore(
        sessionId: String,
        fixture: ScreenFixture,
    ) {
        val inputs = fixture.inputs
        val session = (inputs["session"] as? Map<*, *>)?.toActiveSession()
            ?: buildExerciseLoggingSession(
                exercise = ExerciseSummary(
                    id = 1L,
                    name = "Debug Exercise",
                    difficulty = "Intermediate",
                    bodyRegion = "Lower Body",
                    targetMuscleGroup = "Quadriceps",
                    equipment = "Dumbbell",
                    secondaryEquipment = null,
                    mechanics = null,
                    favorite = false,
                ),
                title = "Debug Workout",
                locationModeId = 1L,
                setCount = 4,
                completedSets = 1,
            )
        app.container.workoutRepository.saveActiveSession(
            session = session,
            selectedExerciseIndex = inputs["selectedExerciseIndex"].asIntOrNull(),
        )
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_DEBUG_TAB, "Home"),
        )
    }
}

private class ToastLiftDebugFeatureFlags(
    private val app: ToastLiftApplication,
) : DebugFeatureFlagMutating {
    override fun flags(): Map<String, Any?> = app.container.dataEnvironment.featureFlags

    override fun setEphemeral(
        flags: Map<String, Any?>,
        sessionId: String,
    ) {
        val environment = app.container.dataEnvironment
        require(environment.debugSessionId == sessionId) { "Unknown debug session: $sessionId" }
        val mergedFlags = environment.featureFlags + flags
        app.replaceContainerForDebug(
            DataEnvironment.debugEphemeral(
                databaseFile = requireNotNull(environment.databaseFile),
                debugSessionId = sessionId,
                featureFlags = mergedFlags,
                frozenTimeIso = environment.frozenTimeIso,
                randomSeed = environment.randomSeed,
            ),
        )
        app.applyEphemeralDebugSettings(mergedFlags)
        app.startActivity(
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_DEBUG_TAB, "Home"),
        )
    }
}

private data class ActiveDebugSession(
    val id: String,
    val realDatabaseHash: String,
    val sessionDir: File,
    val featureFlags: Map<String, Any?>,
)

private fun buildExerciseLoggingSession(
    exercise: ExerciseSummary,
    title: String = "Debug Workout",
    locationModeId: Long = 1L,
    setCount: Int,
    completedSets: Int,
): ActiveSession {
    val now = Instant.now()
    val sets = (1..setCount).map { setNumber ->
        val completed = setNumber <= completedSets
        SessionSet(
            setNumber = setNumber,
            targetReps = "8-10",
            recommendedReps = 8,
            recommendedWeight = "45",
            reps = if (completed) "8" else "",
            weight = if (completed) "45" else "",
            recommendationSource = RecommendationSource.GENERATED_PLAN,
            recommendationConfidence = 0.82,
            completed = completed,
            completedAtUtc = if (completed) now.plusSeconds(setNumber.toLong()).toString() else null,
        )
    }
    return ActiveSession(
        title = title,
        origin = "debug",
        locationModeId = locationModeId,
        startedAtUtc = now.toString(),
        subtitle = "AppReveal debug fixture",
        estimatedMinutes = 45,
        sessionFormat = "Debug",
        exercises = listOf(
            SessionExercise(
                exerciseId = exercise.id,
                name = exercise.name,
                bodyRegion = exercise.bodyRegion,
                targetMuscleGroup = exercise.targetMuscleGroup,
                equipment = exercise.equipment,
                restSeconds = 90,
                sets = sets,
            ),
        ),
    )
}

private fun ToastLiftApplication.ensureDefaultDebugProfile() {
    if (!container.dataEnvironment.isDebugEphemeral) return
    if (container.userRepository.loadProfile() != null) return
    container.userRepository.saveProfile(
        draft = OnboardingDraft(),
        activeLocationModeId = HOME_LOCATION_MODE_ID,
    )
}

private fun ToastLiftApplication.applyEphemeralDebugSettings(flags: Map<String, Any?>) {
    resolveActiveLocationModeId(flags)?.let(container.userRepository::setActiveLocation)
    flags["gym_machine_cable_bias_enabled"]
        .asBooleanOrNull()
        ?.let(container.userRepository::saveGymMachineCableBiasEnabled)
}

private fun ToastLiftApplication.resolveActiveLocationModeId(flags: Map<String, Any?>): Long? {
    flags["gym_mode"].asBooleanOrNull()?.let { gymMode ->
        if (gymMode) return GYM_LOCATION_MODE_ID
    }
    flags["active_location_mode_id"].asLongOrNull()?.let { return it }
    flags["activeLocationModeId"].asLongOrNull()?.let { return it }
    return resolveLocationModeName(flags["active_location"] ?: flags["activeLocation"])
}

private fun ToastLiftApplication.resolveLocationModeName(rawValue: Any?): Long? {
    val requested = rawValue?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return container.userRepository.loadLocationModes().firstOrNull { mode ->
        mode.name.equals(requested, ignoreCase = true) ||
            mode.displayName.equals(requested, ignoreCase = true)
    }?.id
}

private fun copySidecar(
    sourceDb: File,
    targetDb: File,
    suffix: String,
) {
    val source = File(sourceDb.absolutePath + suffix)
    if (source.exists()) {
        source.copyTo(File(targetDb.absolutePath + suffix), overwrite = true)
    }
}

private fun File.sha256OrMissing(): String {
    if (!exists()) return "missing"
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}

private fun Any?.asInt(defaultValue: Int): Int =
    when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: defaultValue
        else -> defaultValue
    }

private fun Any?.asLong(defaultValue: Long): Long =
    when (this) {
        is Number -> toLong()
        is String -> toLongOrNull() ?: defaultValue
        else -> defaultValue
    }

private fun Any?.asLongOrNull(): Long? =
    when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }

private fun Any?.asBooleanOrNull(): Boolean? =
    when (this) {
        is Boolean -> this
        is String -> when {
            equals("true", ignoreCase = true) -> true
            equals("false", ignoreCase = true) -> false
            else -> null
        }
        is Number -> toInt() != 0
        else -> null
    }

private fun Any?.asString(defaultValue: String = ""): String =
    this?.toString() ?: defaultValue

private fun Any?.asNullableString(): String? =
    this?.toString()?.takeIf { it.isNotBlank() }

private fun Any?.asDoubleOrNull(): Double? =
    when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }

private fun Any?.asBoolean(defaultValue: Boolean = false): Boolean =
    when (this) {
        is Boolean -> this
        is String -> when {
            equals("true", ignoreCase = true) -> true
            equals("false", ignoreCase = true) -> false
            else -> defaultValue
        }
        is Number -> toInt() != 0
        else -> defaultValue
    }

private fun ActiveSession.toFixtureMap(): Map<String, Any?> =
    mapOf(
        "title" to title,
        "origin" to origin,
        "locationModeId" to locationModeId,
        "startedAtUtc" to startedAtUtc,
        "focusKey" to focusKey,
        "subtitle" to subtitle,
        "estimatedMinutes" to estimatedMinutes,
        "sessionFormat" to sessionFormat,
        "isPaused" to isPaused,
        "pausedAtUtc" to pausedAtUtc,
        "accumulatedPausedSeconds" to accumulatedPausedSeconds,
        "exercises" to exercises.map(SessionExercise::toFixtureMap),
    )

private fun SessionExercise.toFixtureMap(): Map<String, Any?> =
    mapOf(
        "exerciseId" to exerciseId,
        "name" to name,
        "bodyRegion" to bodyRegion,
        "targetMuscleGroup" to targetMuscleGroup,
        "equipment" to equipment,
        "restSeconds" to restSeconds,
        "sets" to sets.map(SessionSet::toFixtureMap),
        "workUnits" to workUnits.map(WorkUnitDefinition::toFixtureMap),
        "activitySequence" to activitySequence,
        "completionSequence" to completionSequence,
        "lastSetRepsInReserve" to lastSetRepsInReserve,
        "notes" to notes,
        "fruitIcon" to fruitIcon,
    )

private fun SessionSet.toFixtureMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "setNumber" to setNumber,
        "targetReps" to targetReps,
        "recommendedReps" to recommendedReps,
        "recommendedWeight" to recommendedWeight,
        "reps" to reps,
        "weight" to weight,
        "workUnitValues" to workUnitValues,
        "recommendationSource" to recommendationSource.name,
        "recommendationConfidence" to recommendationConfidence,
        "completed" to completed,
        "completedAtUtc" to completedAtUtc,
    )

private fun WorkUnitDefinition.toFixtureMap(): Map<String, Any?> =
    mapOf(
        "key" to key,
        "label" to label,
        "valueType" to valueType,
        "unitLabel" to unitLabel,
        "defaultValue" to defaultValue,
        "minValue" to minValue,
        "maxValue" to maxValue,
        "stepValue" to stepValue,
        "isPrimary" to isPrimary,
        "isRequired" to isRequired,
        "tracksEffort" to tracksEffort,
    )

private fun Map<*, *>.toActiveSession(): ActiveSession =
    ActiveSession(
        title = this["title"].asString("Debug Workout"),
        origin = this["origin"].asString("debug"),
        locationModeId = this["locationModeId"].asLong(defaultValue = 1L),
        startedAtUtc = this["startedAtUtc"].asString(Instant.now().toString()),
        focusKey = this["focusKey"].asNullableString(),
        subtitle = this["subtitle"].asString(),
        estimatedMinutes = this["estimatedMinutes"].asIntOrNull(),
        sessionFormat = this["sessionFormat"].asNullableString(),
        exercises = (this["exercises"] as? List<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.toSessionExercise() }
            .orEmpty(),
        isPaused = this["isPaused"].asBoolean(),
        pausedAtUtc = this["pausedAtUtc"].asNullableString(),
        accumulatedPausedSeconds = this["accumulatedPausedSeconds"].asInt(defaultValue = 0),
    )

private fun Map<*, *>.toSessionExercise(): SessionExercise =
    SessionExercise(
        exerciseId = this["exerciseId"].asLong(defaultValue = 1L),
        name = this["name"].asString("Debug Exercise"),
        bodyRegion = this["bodyRegion"].asString("Lower Body"),
        targetMuscleGroup = this["targetMuscleGroup"].asString("Quadriceps"),
        equipment = this["equipment"].asString("Dumbbell"),
        restSeconds = this["restSeconds"].asInt(defaultValue = 90),
        sets = (this["sets"] as? List<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.toSessionSet() }
            .orEmpty(),
        workUnits = (this["workUnits"] as? List<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.toWorkUnitDefinition() }
            .orEmpty(),
        activitySequence = this["activitySequence"].asIntOrNull(),
        completionSequence = this["completionSequence"].asIntOrNull(),
        lastSetRepsInReserve = this["lastSetRepsInReserve"].asIntOrNull(),
        notes = this["notes"].asString(),
        fruitIcon = this["fruitIcon"].asNullableString(),
    )

private fun Map<*, *>.toSessionSet(): SessionSet =
    SessionSet(
        id = this["id"].asLong(defaultValue = 0L).takeIf { it > 0L } ?: nextSessionSetId(),
        setNumber = this["setNumber"].asInt(defaultValue = 1),
        targetReps = this["targetReps"].asString("8-10"),
        recommendedReps = this["recommendedReps"].asIntOrNull(),
        recommendedWeight = this["recommendedWeight"].asString(),
        reps = this["reps"].asString(),
        weight = this["weight"].asString(),
        workUnitValues = (this["workUnitValues"] as? Map<*, *>)
            ?.mapNotNull { (key, value) -> key?.toString()?.let { it to value.asString() } }
            ?.toMap()
            .orEmpty(),
        recommendationSource = this["recommendationSource"].asRecommendationSource(),
        recommendationConfidence = this["recommendationConfidence"].asDoubleOrNull(),
        completed = this["completed"].asBoolean(),
        completedAtUtc = this["completedAtUtc"].asNullableString(),
    )

private fun Map<*, *>.toWorkUnitDefinition(): WorkUnitDefinition =
    WorkUnitDefinition(
        key = this["key"].asString(),
        label = this["label"].asString(),
        valueType = this["valueType"].asString(),
        unitLabel = this["unitLabel"].asNullableString(),
        defaultValue = this["defaultValue"].asNullableString(),
        minValue = this["minValue"].asDoubleOrNull(),
        maxValue = this["maxValue"].asDoubleOrNull(),
        stepValue = this["stepValue"].asDoubleOrNull(),
        isPrimary = this["isPrimary"].asBoolean(),
        isRequired = this["isRequired"].asBoolean(),
        tracksEffort = this["tracksEffort"].asBoolean(),
    )

private fun Any?.asIntOrNull(): Int? =
    when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }

private fun Any?.asRecommendationSource(): RecommendationSource =
    asNullableString()
        ?.let { runCatching { RecommendationSource.valueOf(it) }.getOrNull() }
        ?: RecommendationSource.NONE

private const val GYM_LOCATION_MODE_ID = 2L
private const val HOME_LOCATION_MODE_ID = 1L
private const val EXTRA_DEBUG_TAB = "dev.toastlabs.toastlift.extra.DEBUG_TAB"
private const val EXTRA_DEBUG_SURFACE = "dev.toastlabs.toastlift.extra.DEBUG_SURFACE"
