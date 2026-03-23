package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentRepositoryTest {
    @Test
    fun assignTodayCompletionFeedbackVariant_mapsKnownSeedsIntoStableBuckets() {
        assertEquals(
            TodayCompletionFeedbackVariant.DONE_TODAY_BADGE,
            assignTodayCompletionFeedbackVariant("control"),
        )
        assertEquals(
            TodayCompletionFeedbackVariant.PROGRESS_METER,
            assignTodayCompletionFeedbackVariant("alpha-seed"),
        )
    }

    @Test
    fun assignTodayCompletionFeedbackVariant_treatsBlankSeedAsDeterministicFallback() {
        assertEquals(
            assignTodayCompletionFeedbackVariant(""),
            assignTodayCompletionFeedbackVariant("   "),
        )
    }

    @Test
    fun buildTodayCompletionFeedbackAbFlags_freezesHumanReadableMeaningForProgressMeterVariant() {
        val snapshot = buildTodayCompletionFeedbackAbFlags(TodayCompletionFeedbackVariant.PROGRESS_METER)
        val flag = snapshot.completionFeedbackFlag

        assertNotNull(flag)
        assertEquals(TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY, flag?.experimentKey)
        assertEquals(TODAY_COMPLETION_FEEDBACK_FLAG_NAME, flag?.flagName)
        assertEquals(TodayCompletionFeedbackVariant.PROGRESS_METER.storageKey, flag?.variantKey)
        assertEquals("Variant B: Animated progress meter", flag?.variantName)
        assertEquals("enabled", flag?.enabledStatus)
        assertTrue(flag?.flagDescription.orEmpty().contains("animated progress meter"))
    }

    @Test
    fun completedWorkoutAbFlags_storageSerialization_roundTripsSnapshotFields() {
        val original = buildTodayCompletionFeedbackAbFlags(TodayCompletionFeedbackVariant.DONE_TODAY_BADGE)

        val restored = deserializeCompletedWorkoutAbFlags(serializeCompletedWorkoutAbFlags(original))

        assertEquals(original, restored)
    }
}
