package com.fitlib.app.data

import android.content.Context

class AppContainer(context: Context) {
    internal val fitLibDatabase = FitLibDatabase(context.applicationContext)

    val catalogRepository = CatalogRepository(fitLibDatabase)
    val customExerciseRepository = CustomExerciseRepository(
        context = context.applicationContext,
        database = fitLibDatabase,
        catalogRepository = catalogRepository,
    )
    val experimentRepository = ExperimentRepository(fitLibDatabase)
    val userRepository = UserRepository(fitLibDatabase)
    val workoutRepository = WorkoutRepository(fitLibDatabase, catalogRepository)
    val generatorRepository = GeneratorRepository(fitLibDatabase, userRepository)
    val programRepository = ProgramRepository(fitLibDatabase)
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
            fitLibDatabase = fitLibDatabase,
        )
    }
    val reviewEngine by lazy { ReviewEngine(programRepository) }
}
