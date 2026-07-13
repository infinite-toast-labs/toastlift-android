package dev.toastlabs.toastlift.config

import android.content.Context
import android.util.Log
import dev.toastlabs.toastlift.BuildConfig
import org.json.JSONObject

/**
 * A deliberately small, build-time selected product surface.
 *
 * The checked-in JSON files are the source of truth. Debug selects the complete
 * development surface; staging and release select the Play Store surface. Keep
 * defaults aligned with [BuildConfig.PRODUCTION_FEATURE_CONFIG] so a malformed
 * asset never accidentally enables AI or program features in production.
 */
data class AppFeatureConfig(
    val releaseName: String,
    val navigation: Navigation = Navigation(),
    val global: Global = Global(),
    val home: Home = Home(),
    val generate: Generate = Generate(),
    val library: Library = Library(),
    val history: History = History(),
    val profile: Profile = Profile(),
) {
    data class Navigation(
        val home: Boolean = true,
        val generate: Boolean = true,
        val explore: Boolean = true,
        val profile: Boolean = true,
    )

    data class Global(
        val ai: Boolean = true,
        val customExercises: Boolean = true,
        val workoutPrograms: Boolean = true,
        val workoutTemplates: Boolean = true,
        val manualWorkoutBuilder: Boolean = true,
    )

    data class Home(
        val adHocGenerator: Boolean = true,
        val trainingFreshness: Boolean = true,
        val recoveryStory: Boolean = true,
        val completionReceipt: Boolean = true,
        val programs: Boolean = true,
        val templates: Boolean = true,
        val dailyCoach: Boolean = true,
    )

    data class Generate(
        val adHocGenerator: Boolean = true,
        val workoutEditing: Boolean = true,
        val manualBuilder: Boolean = true,
        val savedTemplates: Boolean = true,
        val customExercises: Boolean = true,
        val aiSearch: Boolean = true,
        val aiDiscovery: Boolean = true,
    )

    data class Library(
        val search: Boolean = true,
        val favorites: Boolean = true,
        val filters: Boolean = true,
        val exerciseFamily: Boolean = true,
        val customExercises: Boolean = true,
        val aiSearch: Boolean = true,
        val aiDiscovery: Boolean = true,
    )

    data class History(
        val workouts: Boolean = true,
        val tokenSystem: Boolean = true,
        val weeklyMuscleTargets: Boolean = true,
        val overviewDashboard: Boolean = true,
        val advancedStats: Boolean = true,
        val muscleIndex: Boolean = true,
        val movementBalance: Boolean = true,
        val bountyCards: Boolean = true,
    )

    data class Profile(
        val aiSettings: Boolean = true,
        val programSettings: Boolean = true,
        val smartPickerTarget: Boolean = true,
        val developerSettings: Boolean = true,
        val dataControls: Boolean = true,
    )

    val aiEnabled: Boolean
        get() = global.ai
}

object FeatureConfigLoader {
    private const val tag = "ToastLiftFeatureConfig"

    fun load(context: Context): AppFeatureConfig {
        val config = runCatching {
            context.assets.open(BuildConfig.FEATURE_CONFIG_ASSET)
                .bufferedReader()
                .use { reader -> fromJson(reader.readText(), productionDefaults = BuildConfig.PRODUCTION_FEATURE_CONFIG) }
        }.getOrElse { error ->
            Log.e(tag, "Could not load ${BuildConfig.FEATURE_CONFIG_ASSET}; using safe defaults.", error)
            defaults(production = BuildConfig.PRODUCTION_FEATURE_CONFIG)
        }
        Log.i(
            tag,
            "Loaded ${BuildConfig.FEATURE_CONFIG_ASSET}: ${config.releaseName}; " +
                "ai=${config.aiEnabled}, programs=${config.global.workoutPrograms}, templates=${config.global.workoutTemplates}",
        )
        return config
    }

    internal fun fromJson(json: String, productionDefaults: Boolean): AppFeatureConfig {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion") == 1) { "Unsupported feature config schema." }
        val fallback = defaults(productionDefaults)
        val navigation = root.objectOrEmpty("navigation")
        val global = root.objectOrEmpty("global")
        val screens = root.objectOrEmpty("screens")
        val home = screens.objectOrEmpty("home")
        val generate = screens.objectOrEmpty("generate")
        val explore = screens.objectOrEmpty("explore")
        val library = explore.objectOrEmpty("library")
        val history = explore.objectOrEmpty("history")
        val profile = screens.objectOrEmpty("profile")

        return AppFeatureConfig(
            releaseName = root.optString("releaseName").ifBlank { fallback.releaseName },
            navigation = AppFeatureConfig.Navigation(
                home = navigation.boolean("home", fallback.navigation.home),
                generate = navigation.boolean("generate", fallback.navigation.generate),
                explore = navigation.boolean("explore", fallback.navigation.explore),
                profile = navigation.boolean("profile", fallback.navigation.profile),
            ),
            global = AppFeatureConfig.Global(
                ai = global.boolean("ai", fallback.global.ai),
                customExercises = global.boolean("customExercises", fallback.global.customExercises),
                workoutPrograms = global.boolean("workoutPrograms", fallback.global.workoutPrograms),
                workoutTemplates = global.boolean("workoutTemplates", fallback.global.workoutTemplates),
                manualWorkoutBuilder = global.boolean("manualWorkoutBuilder", fallback.global.manualWorkoutBuilder),
            ),
            home = AppFeatureConfig.Home(
                adHocGenerator = home.boolean("adHocGenerator", fallback.home.adHocGenerator),
                trainingFreshness = home.boolean("trainingFreshness", fallback.home.trainingFreshness),
                recoveryStory = home.boolean("recoveryStory", fallback.home.recoveryStory),
                completionReceipt = home.boolean("completionReceipt", fallback.home.completionReceipt),
                programs = home.boolean("programs", fallback.home.programs),
                templates = home.boolean("templates", fallback.home.templates),
                dailyCoach = home.boolean("dailyCoach", fallback.home.dailyCoach),
            ),
            generate = AppFeatureConfig.Generate(
                adHocGenerator = generate.boolean("adHocGenerator", fallback.generate.adHocGenerator),
                workoutEditing = generate.boolean("workoutEditing", fallback.generate.workoutEditing),
                manualBuilder = generate.boolean("manualBuilder", fallback.generate.manualBuilder),
                savedTemplates = generate.boolean("savedTemplates", fallback.generate.savedTemplates),
                customExercises = generate.boolean("customExercises", fallback.generate.customExercises),
                aiSearch = generate.boolean("aiSearch", fallback.generate.aiSearch),
                aiDiscovery = generate.boolean("aiDiscovery", fallback.generate.aiDiscovery),
            ),
            library = AppFeatureConfig.Library(
                search = library.boolean("search", fallback.library.search),
                favorites = library.boolean("favorites", fallback.library.favorites),
                filters = library.boolean("filters", fallback.library.filters),
                exerciseFamily = library.boolean("exerciseFamily", fallback.library.exerciseFamily),
                customExercises = library.boolean("customExercises", fallback.library.customExercises),
                aiSearch = library.boolean("aiSearch", fallback.library.aiSearch),
                aiDiscovery = library.boolean("aiDiscovery", fallback.library.aiDiscovery),
            ),
            history = AppFeatureConfig.History(
                workouts = history.boolean("workouts", fallback.history.workouts),
                tokenSystem = history.boolean("tokenSystem", fallback.history.tokenSystem),
                weeklyMuscleTargets = history.boolean("weeklyMuscleTargets", fallback.history.weeklyMuscleTargets),
                overviewDashboard = history.boolean("overviewDashboard", fallback.history.overviewDashboard),
                advancedStats = history.boolean("advancedStats", fallback.history.advancedStats),
                muscleIndex = history.boolean("muscleIndex", fallback.history.muscleIndex),
                movementBalance = history.boolean("movementBalance", fallback.history.movementBalance),
                bountyCards = history.boolean("bountyCards", fallback.history.bountyCards),
            ),
            profile = AppFeatureConfig.Profile(
                aiSettings = profile.boolean("aiSettings", fallback.profile.aiSettings),
                programSettings = profile.boolean("programSettings", fallback.profile.programSettings),
                smartPickerTarget = profile.boolean("smartPickerTarget", fallback.profile.smartPickerTarget),
                developerSettings = profile.boolean("developerSettings", fallback.profile.developerSettings),
                dataControls = profile.boolean("dataControls", fallback.profile.dataControls),
            ),
        )
    }

    private fun JSONObject.objectOrEmpty(name: String): JSONObject = optJSONObject(name) ?: JSONObject()

    private fun JSONObject.boolean(name: String, defaultValue: Boolean): Boolean =
        if (has(name)) optBoolean(name, defaultValue) else defaultValue

    private fun defaults(production: Boolean): AppFeatureConfig {
        if (!production) return AppFeatureConfig(releaseName = "development")
        return AppFeatureConfig(
            releaseName = "safe-production-defaults",
            global = AppFeatureConfig.Global(
                ai = false,
                customExercises = false,
                workoutPrograms = false,
                workoutTemplates = false,
                manualWorkoutBuilder = false,
            ),
            home = AppFeatureConfig.Home(
                recoveryStory = false,
                programs = false,
                templates = false,
                dailyCoach = false,
            ),
            generate = AppFeatureConfig.Generate(
                manualBuilder = false,
                savedTemplates = false,
                customExercises = false,
                aiSearch = false,
                aiDiscovery = false,
            ),
            library = AppFeatureConfig.Library(
                exerciseFamily = false,
                customExercises = false,
                aiSearch = false,
                aiDiscovery = false,
            ),
            history = AppFeatureConfig.History(
                overviewDashboard = false,
                advancedStats = false,
                muscleIndex = false,
                movementBalance = false,
                bountyCards = false,
            ),
            profile = AppFeatureConfig.Profile(
                aiSettings = false,
                programSettings = false,
                smartPickerTarget = false,
                developerSettings = false,
            ),
        )
    }
}
