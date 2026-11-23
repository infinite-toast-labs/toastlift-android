package dev.toastlabs.toastlift.data

import android.content.Context
import dev.toastlabs.toastlift.BuildConfig
import dev.toastlabs.toastlift.config.AppFeatureConfig
import dev.toastlabs.toastlift.config.FeatureConfigLoader

class AppContainer(
    context: Context,
    val dataEnvironment: DataEnvironment = DataEnvironment.real(),
) {
    val featureConfig: AppFeatureConfig = FeatureConfigLoader.load(context.applicationContext)

    internal val toastLiftDatabase = ToastLiftDatabase(
        context = context.applicationContext,
        databaseFileOverride = dataEnvironment.databaseFile,
    )

    val catalogRepository = CatalogRepository(toastLiftDatabase)
    internal val exerciseDescriptionService = ExerciseDescriptionService(catalogRepository)
    val userRepository = UserRepository(toastLiftDatabase)
    internal val geminiSearchCredentials = GeminiSearchCredentials(context.applicationContext)
    val customExerciseRepository = CustomExerciseRepository(
        context = context.applicationContext,
        database = toastLiftDatabase,
        catalogRepository = catalogRepository,
        userRepository = userRepository,
        metadataGeneratorFactory = if (BuildConfig.BUILD_TYPE == "zstore") {
            {
                GeminiExerciseMetadataGenerator(
                    apiKey = geminiSearchCredentials.read().orEmpty(),
                    model = "gemini-3.5-flash-lite",
                )
            }
        } else {
            ::createExerciseMetadataGeneratorForOption
        },
    )
    val personalDataImporter = PersonalDataImporter(toastLiftDatabase, customExerciseRepository)
    internal val exerciseDiscoveryService = ExerciseDiscoveryService()
    internal val exerciseAiSearchService = if (BuildConfig.BUILD_TYPE == "zstore") {
        ExerciseAiSearchService(object : ExerciseAiSearchRemoteGenerator {
            override val model = "gemini-3.5-flash-lite"
            override fun generate(prompt: String): String = GeminiExerciseAiSearchRemoteGenerator(
                apiKey = geminiSearchCredentials.read().orEmpty(),
                configuredModel = model,
            ).generate(prompt)
        })
    } else ExerciseAiSearchService()
    val experimentRepository = ExperimentRepository(toastLiftDatabase)
    val workoutRepository = WorkoutRepository(toastLiftDatabase, catalogRepository)
    val restTimerNotifier = RestTimerNotifier(context.applicationContext).also { it.ensureNotificationChannel() }
    val workoutNotificationNotifier = WorkoutNotificationNotifier(context.applicationContext).also { it.ensureNotificationChannel() }
    val generatorRepository = GeneratorRepository(toastLiftDatabase, userRepository)
    val programRepository = ProgramRepository(toastLiftDatabase)
    val dailyCoachService = DailyCoachService(
        userRepository = userRepository,
        workoutRepository = workoutRepository,
        programRepository = programRepository,
        catalogRepository = catalogRepository,
    )
    val programEngine by lazy {
        ProgramEngine(
            programRepository = programRepository,
            generatorRepository = generatorRepository,
            userRepository = userRepository,
            toastLiftDatabase = toastLiftDatabase,
        )
    }
    val reviewEngine by lazy { ReviewEngine(programRepository) }
}
