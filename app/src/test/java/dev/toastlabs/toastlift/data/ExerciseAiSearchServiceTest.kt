package dev.toastlabs.toastlift.data

import dev.toastlabs.toastlift.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ExerciseAiSearchServiceTest {
    @Test
    fun buildExerciseAiSearchPrompt_includesQueryCatalogAndRules() {
        val prompt = buildExerciseAiSearchPrompt(
            query = "reverse pec dec",
            catalog = listOf(
                ExerciseAiSearchCatalogEntry(id = 3279L, name = "Reverse Machine Flyes"),
                ExerciseAiSearchCatalogEntry(
                    id = 10L,
                    name = "Machine Chest Press",
                    synonyms = listOf("Seated Chest Press Machine"),
                ),
            ),
        )

        assertTrue(prompt.contains("\"reverse pec dec\""))
        assertTrue(prompt.contains("Reverse Machine Flyes"))
        assertTrue(prompt.contains("Seated Chest Press Machine"))
        assertTrue(prompt.contains("return an empty matches array"))
        assertTrue(prompt.contains("at most $EXERCISE_AI_SEARCH_MAX_MATCHES matches"))
        assertTrue(prompt.contains("exact_alias"))
    }

    @Test
    fun buildExerciseAiSearchCatalogJson_omitsEmptySynonyms() {
        val json = buildExerciseAiSearchCatalogJson(
            listOf(
                ExerciseAiSearchCatalogEntry(id = 1L, name = "Back Squat"),
                ExerciseAiSearchCatalogEntry(id = 2L, name = "Romanian Deadlift", synonyms = listOf("RDL")),
            ),
        ).toString()

        assertTrue(json.contains("\"Back Squat\""))
        assertTrue(json.contains("\"RDL\""))
        assertEquals(1, Regex("\"synonyms\"").findAll(json).count())
    }

    @Test
    fun parseExerciseAiSearchResponse_rejectsHallucinatedIdsAndDedupes() {
        val catalog = listOf(
            ExerciseAiSearchCatalogEntry(id = 1L, name = "Reverse Machine Flyes"),
            ExerciseAiSearchCatalogEntry(id = 2L, name = "Double Dumbbell Bent Over Reverse Fly"),
        )

        val matches = parseExerciseAiSearchResponse(
            rawText = """
                {
                  "matches": [
                    {"rank": 1, "exerciseId": 999, "matchType": "exact_alias", "confidence": 0.9, "explanation": "Hallucinated."},
                    {"rank": 2, "exerciseId": 1, "matchType": "exact_alias", "confidence": 0.95, "explanation": "Also known as the reverse pec deck."},
                    {"rank": 3, "exerciseId": 1, "matchType": "variant", "confidence": 0.5, "explanation": "Duplicate."},
                    {"rank": 4, "exerciseId": 2, "matchType": "variant", "confidence": 1.4, "explanation": "Free-weight version of the same movement."}
                  ]
                }
            """.trimIndent(),
            catalog = catalog,
        )

        assertEquals(listOf(1L, 2L), matches.map { it.exerciseId })
        assertEquals(listOf(1, 2), matches.map { it.rank })
        assertEquals("Also known as the reverse pec deck.", matches.first().explanation)
        assertEquals(1.0, matches.last().confidence, 0.0)
        assertEquals("variant", matches.last().matchType)
    }

    @Test
    fun parseExerciseAiSearchResponse_acceptsEmptyMatchesAndAltShapes() {
        val catalog = listOf(ExerciseAiSearchCatalogEntry(id = 1L, name = "Back Squat"))

        assertTrue(
            parseExerciseAiSearchResponse("""{"matches": []}""", catalog).isEmpty(),
        )
        val fenced = parseExerciseAiSearchResponse(
            rawText = """
                ```json
                {"ranked_matches": [{"rank": "1", "exercise_id": "1", "match_type": "Exact Alias", "score": "0.8", "reason": "Same lift."}]}
                ```
            """.trimIndent(),
            catalog = catalog,
        )
        assertEquals(1, fenced.size)
        assertEquals(1L, fenced.first().exerciseId)
        assertEquals("exact_alias", fenced.first().matchType)
        assertEquals(0.8, fenced.first().confidence, 0.0001)
        assertEquals("Same lift.", fenced.first().explanation)
    }

    @Test
    fun parseExerciseAiSearchResponse_capsMatchesAndNormalizesUnknownMatchType() {
        val catalog = (1L..12L).map { ExerciseAiSearchCatalogEntry(id = it, name = "Exercise $it") }
        val items = (1L..12L).joinToString(",") {
            """{"rank": $it, "exerciseId": $it, "matchType": "mystery", "confidence": 0.5, "explanation": "m"}"""
        }

        val matches = parseExerciseAiSearchResponse("""{"matches": [$items]}""", catalog)

        assertEquals(EXERCISE_AI_SEARCH_MAX_MATCHES, matches.size)
        assertEquals((1..EXERCISE_AI_SEARCH_MAX_MATCHES).toList(), matches.map { it.rank })
        assertTrue(matches.all { it.matchType == "related" })
    }

    @Test
    fun exerciseAiSearchService_returnsMatchesFromRemote() {
        val catalog = listOf(
            ExerciseAiSearchCatalogEntry(id = 3279L, name = "Reverse Machine Flyes"),
            ExerciseAiSearchCatalogEntry(id = 1764L, name = "Double Dumbbell Seated Reverse Fly"),
        )
        val service = ExerciseAiSearchService(
            remoteGenerator = FakeExerciseAiSearchRemoteGenerator(
                response = """
                    {"matches": [
                      {"rank": 1, "exerciseId": 3279, "matchType": "exact_alias", "confidence": 0.95, "explanation": "Also known as the reverse pec deck."}
                    ]}
                """.trimIndent(),
            ),
        )

        val result = service.search("reverse pec dec", catalog)

        assertEquals("reverse pec dec", result.query)
        assertEquals(listOf(3279L), result.matches.map { it.exerciseId })
        assertEquals("gemini-test", result.model)
        assertEquals(EXERCISE_AI_SEARCH_PROMPT_VERSION, result.promptVersion)
    }

    @Test
    fun exerciseAiSearchService_propagatesRemoteFailure() {
        val service = ExerciseAiSearchService(
            remoteGenerator = FakeExerciseAiSearchRemoteGenerator(error = IllegalStateException("offline")),
        )

        val failure = runCatching {
            service.search("reverse pec dec", listOf(ExerciseAiSearchCatalogEntry(id = 1L, name = "Reverse Machine Flyes")))
        }

        assertTrue(failure.isFailure)
    }

    @Test
    fun exerciseAiSearchService_shortCircuitsOnEmptyCatalog() {
        var remoteCalls = 0
        val service = ExerciseAiSearchService(
            remoteGenerator = object : ExerciseAiSearchRemoteGenerator {
                override val model: String = "gemini-test"
                override fun generate(prompt: String): String {
                    remoteCalls += 1
                    error("Should not be called.")
                }
            },
        )

        val result = service.search("reverse pec dec", emptyList())

        assertTrue(result.matches.isEmpty())
        assertNull(result.model)
        assertEquals(0, remoteCalls)
    }

    @Test
    fun exerciseAiSearchMatchTypeLabel_mapsKnownTypes() {
        assertEquals("Same exercise", exerciseAiSearchMatchTypeLabel("exact_alias"))
        assertEquals("Variant", exerciseAiSearchMatchTypeLabel("variant"))
        assertEquals("Related", exerciseAiSearchMatchTypeLabel("related"))
        assertEquals("Related", exerciseAiSearchMatchTypeLabel("unknown"))
    }

    @Test
    fun normalizeExerciseSynonym_cleansWhitespaceAndRejectsBlank() {
        assertEquals("reverse pec dec", normalizeExerciseSynonym("  reverse   pec dec "))
        assertNull(normalizeExerciseSynonym("   "))
    }

    @Test
    fun normalizedExerciseSynonymKey_matchesImportNormalization() {
        assertEquals("reverse pec dec", normalizedExerciseSynonymKey("Reverse Pec-Dec!"))
        assertEquals("iso lateral row", normalizedExerciseSynonymKey("Iso-Lateral Row"))
    }

    @Test
    fun liveSmokeGeminiExerciseAiSearch_findsReversePecDeckByAlias() {
        assumeLiveAiSmokeTestsEnabled()
        assertTrue("GEMINI_API_KEY must be configured for live smoke tests.", BuildConfig.GEMINI_API_KEY.isNotBlank())
        assertTrue("GEMINI_PRIMARY_MODEL must be configured for live smoke tests.", BuildConfig.GEMINI_PRIMARY_MODEL.isNotBlank())

        val catalog = listOf(
            ExerciseAiSearchCatalogEntry(id = 3279L, name = "Reverse Machine Flyes"),
            ExerciseAiSearchCatalogEntry(id = 2140L, name = "Double Dumbbell Bent Over Reverse Fly"),
            ExerciseAiSearchCatalogEntry(id = 1764L, name = "Double Dumbbell Seated Reverse Fly"),
            ExerciseAiSearchCatalogEntry(id = 100L, name = "Barbell Back Squat"),
            ExerciseAiSearchCatalogEntry(id = 101L, name = "Barbell Bench Press"),
            ExerciseAiSearchCatalogEntry(id = 102L, name = "Romanian Deadlift"),
            ExerciseAiSearchCatalogEntry(id = 103L, name = "Lat Pulldown"),
            ExerciseAiSearchCatalogEntry(id = 104L, name = "Seated Cable Row"),
        )

        val result = ExerciseAiSearchService().search("reverse pec dec", catalog)

        println("Gemini live smoke AI search matches for \"${result.query}\" (model=${result.model}):")
        result.matches.forEach { match ->
            println("  #${match.rank} exerciseId=${match.exerciseId} type=${match.matchType} confidence=${match.confidence} - ${match.explanation}")
        }
        assertTrue("Expected at least one live match.", result.matches.isNotEmpty())
        assertEquals(
            "Expected Reverse Machine Flyes as the top match.",
            3279L,
            result.matches.first().exerciseId,
        )
        assertTrue(result.matches.all { match -> catalog.any { it.id == match.exerciseId } })
    }

    @Test
    fun liveSmokeGeminiExerciseAiSearch_returnsEmptyForMissingExercise() {
        assumeLiveAiSmokeTestsEnabled()
        assertTrue("GEMINI_API_KEY must be configured for live smoke tests.", BuildConfig.GEMINI_API_KEY.isNotBlank())
        assertTrue("GEMINI_PRIMARY_MODEL must be configured for live smoke tests.", BuildConfig.GEMINI_PRIMARY_MODEL.isNotBlank())

        val catalog = listOf(
            ExerciseAiSearchCatalogEntry(id = 100L, name = "Barbell Back Squat"),
            ExerciseAiSearchCatalogEntry(id = 101L, name = "Barbell Bench Press"),
            ExerciseAiSearchCatalogEntry(id = 102L, name = "Romanian Deadlift"),
        )

        val result = ExerciseAiSearchService().search("freestyle swimming stroke", catalog)

        println("Gemini live smoke AI search matches for \"${result.query}\": ${result.matches.size}")
        result.matches.forEach { match ->
            println("  #${match.rank} exerciseId=${match.exerciseId} type=${match.matchType} confidence=${match.confidence} - ${match.explanation}")
        }
        assertTrue(
            "Expected no matches for an exercise absent from the catalog.",
            result.matches.isEmpty(),
        )
    }

    private class FakeExerciseAiSearchRemoteGenerator(
        private val response: String? = null,
        private val error: RuntimeException? = null,
    ) : ExerciseAiSearchRemoteGenerator {
        override val model: String = "gemini-test"

        override fun generate(prompt: String): String {
            error?.let { throw it }
            return response ?: error("Missing fake response.")
        }
    }

    private fun assumeLiveAiSmokeTestsEnabled() {
        assumeTrue(
            "Set RUN_LIVE_AI_SMOKE_TESTS=true to run live provider smoke tests.",
            System.getenv("RUN_LIVE_AI_SMOKE_TESTS").equals("true", ignoreCase = true),
        )
    }
}
