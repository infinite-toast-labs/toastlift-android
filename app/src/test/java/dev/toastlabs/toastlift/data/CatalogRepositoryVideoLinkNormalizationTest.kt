package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogRepositoryVideoLinkNormalizationTest {
    @Test
    fun normalizeExerciseVideoLinkLabel_trimsAndRejectsBlankValues() {
        assertEquals("Coach cue", normalizeExerciseVideoLinkLabel("  Coach cue  "))
        assertNull(normalizeExerciseVideoLinkLabel("   "))
    }

    @Test
    fun normalizeExerciseVideoLinkUrl_addsHttpsWhenSchemeMissing() {
        assertEquals(
            "https://youtube.com/watch?v=demo123",
            normalizeExerciseVideoLinkUrl("youtube.com/watch?v=demo123"),
        )
    }

    @Test
    fun normalizeExerciseVideoLinkUrl_acceptsTiktokAndRejectsUnsupportedOrMalformedUrls() {
        assertEquals(
            "https://www.tiktok.com/@coach/video/12345",
            normalizeExerciseVideoLinkUrl("https://www.tiktok.com/@coach/video/12345"),
        )
        assertNull(normalizeExerciseVideoLinkUrl("ftp://example.com/video"))
        assertNull(normalizeExerciseVideoLinkUrl("https://example.com/demo.jpg"))
        assertNull(normalizeExerciseVideoLinkUrl("https://github.com/example/repo"))
        assertNull(normalizeExerciseVideoLinkUrl("not a url"))
    }
}
