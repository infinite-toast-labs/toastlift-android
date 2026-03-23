package dev.toastlabs.toastlift.data

import android.content.Context

class AppContainer(context: Context) {
    internal val toastLiftDatabase = ToastLiftDatabase(context.applicationContext)

    val catalogRepository = CatalogRepository(toastLiftDatabase)
    val customExerciseRepository = CustomExerciseRepository(
        context = context.applicationContext,
        database = toastLiftDatabase,
        catalogRepository = catalogRepository,
    )
    val experimentRepository = ExperimentRepository(toastLiftDatabase)
    val userRepository = UserRepository(toastLiftDatabase)
    val workoutRepository = WorkoutRepository(toastLiftDatabase, catalogRepository)
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
