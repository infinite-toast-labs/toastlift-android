package dev.toastlabs.toastlift.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureConfigTest {
    @Test
    fun productionConfig_keepsCoreSignalsAndDisablesAiAndPlans() {
        val config = FeatureConfigLoader.fromJson(
            json = """
                {
                  "schemaVersion": 1,
                  "releaseName": "test",
                  "global": {
                    "ai": false,
                    "workoutPrograms": false,
                    "workoutTemplates": false,
                    "manualWorkoutBuilder": false
                  },
                  "screens": {
                    "home": { "trainingFreshness": true, "programs": false, "templates": false },
                    "explore": {
                      "library": { "exerciseFamily": false },
                      "history": { "tokenSystem": true, "weeklyMuscleTargets": true }
                    },
                    "profile": { "smartPickerTarget": false }
                  }
                }
            """.trimIndent(),
            productionDefaults = true,
        )

        assertFalse(config.aiEnabled)
        assertFalse(config.global.workoutPrograms)
        assertFalse(config.global.workoutTemplates)
        assertFalse(config.global.manualWorkoutBuilder)
        assertTrue(config.home.trainingFreshness)
        assertTrue(config.history.tokenSystem)
        assertTrue(config.history.weeklyMuscleTargets)
        assertFalse(config.library.exerciseFamily)
        assertFalse(config.profile.smartPickerTarget)
    }

    @Test
    fun developmentDefaults_keepTheExistingFullSurface() {
        val config = FeatureConfigLoader.fromJson(
            json = """{ "schemaVersion": 1, "releaseName": "debug" }""",
            productionDefaults = false,
        )

        assertTrue(config.aiEnabled)
        assertTrue(config.global.workoutPrograms)
        assertTrue(config.global.workoutTemplates)
        assertTrue(config.home.trainingFreshness)
        assertTrue(config.history.tokenSystem)
        assertTrue(config.history.weeklyMuscleTargets)
        assertTrue(config.library.exerciseFamily)
    }
}
