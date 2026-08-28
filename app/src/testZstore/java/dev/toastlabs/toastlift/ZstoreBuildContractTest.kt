package dev.toastlabs.toastlift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZstoreBuildContractTest {
    @Test
    fun zstoreCandidateKeepsProductionBehaviorWithoutEmbeddedServices() {
        assertTrue(BuildConfig.DEBUG)
        assertTrue(BuildConfig.PRODUCTION_FEATURE_CONFIG)
        assertFalse(BuildConfig.INTERNAL_TOOLS_ENABLED)
        assertEquals("feature-config.production.json", BuildConfig.FEATURE_CONFIG_ASSET)
        assertEquals("", BuildConfig.GEMINI_API_KEY)
        assertEquals("", BuildConfig.GEMINI_PRIMARY_MODEL)
        assertEquals("", BuildConfig.CUSTOM_EXERCISE_AI_PROVIDER)
        assertEquals("", BuildConfig.OPENCODE_API_KEY)
        assertEquals("", BuildConfig.OPENCODE_MODEL)
        assertEquals("", BuildConfig.OPENCODE_CHAT_COMPLETIONS_URL)
        assertEquals("", BuildConfig.OPENROUTER_API_KEY)
        assertEquals("", BuildConfig.OPENROUTER_MODEL)
        assertEquals("", BuildConfig.OPENROUTER_CHAT_COMPLETIONS_URL)
        assertEquals("", BuildConfig.OPENROUTER_GENERATION_URL)
    }
}
