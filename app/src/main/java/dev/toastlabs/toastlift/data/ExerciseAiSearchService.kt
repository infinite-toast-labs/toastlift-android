package dev.toastlabs.toastlift.data

import dev.toastlabs.toastlift.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

internal const val EXERCISE_AI_SEARCH_PROMPT_VERSION = "exercise_ai_search_v1"
internal const val EXERCISE_AI_SEARCH_MAX_MATCHES = 8

private val EXERCISE_AI_SEARCH_MATCH_TYPES = setOf(
    "exact_alias",
    "variant",
    "related",
)

/** One catalog entry sent to the model: identity plus known alternative names. */
internal data class ExerciseAiSearchCatalogEntry(
    val id: Long,
    val name: String,
    val synonyms: List<String> = emptyList(),
)

/** A parsed model match before exercise summaries are resolved. */
internal data class ExerciseAiSearchParsedMatch(
    val rank: Int,
    val exerciseId: Long,
    val matchType: String,
    val confidence: Double,
    val explanation: String,
)

internal data class ExerciseAiSearchServiceResult(
    val query: String,
    val generatedAtUtc: Instant,
    val matches: List<ExerciseAiSearchParsedMatch>,
    val model: String?,
    val promptVersion: String = EXERCISE_AI_SEARCH_PROMPT_VERSION,
)

/** UI-facing match with the resolved exercise summary. */
data class ExerciseAiSearchMatch(
    val rank: Int,
    val exercise: ExerciseSummary,
    val matchType: String,
    val confidence: Double,
    val explanation: String,
)

data class ExerciseAiSearchResult(
    val query: String,
    val generatedAtUtc: Instant,
    val matches: List<ExerciseAiSearchMatch>,
    val model: String?,
    val promptVersion: String = EXERCISE_AI_SEARCH_PROMPT_VERSION,
)

internal interface ExerciseAiSearchRemoteGenerator {
    val model: String?
    fun generate(prompt: String): String
}

internal class GeminiExerciseAiSearchRemoteGenerator(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val configuredModel: String = BuildConfig.GEMINI_PRIMARY_MODEL,
) : ExerciseAiSearchRemoteGenerator {
    override val model: String?
        get() = configuredModel.takeIf { it.isNotBlank() }

    override fun generate(prompt: String): String {
        require(apiKey.isNotBlank()) { "Missing GEMINI_API_KEY for exercise AI search." }
        require(configuredModel.isNotBlank()) { "Missing GEMINI_PRIMARY_MODEL for exercise AI search." }

        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.1)
                    .put("responseMimeType", "application/json"),
            )

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$configuredModel:generateContent?key=$apiKey")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use(BufferedReader::readText)
                .orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Gemini request failed ($status): $body")
            }
            val responseText = JSONObject(body)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                .orEmpty()
            if (responseText.isBlank()) {
                throw IllegalStateException("Gemini returned an empty exercise AI search response.")
            }
            return responseText
        } finally {
            connection.disconnect()
        }
    }
}

internal class ExerciseAiSearchService internal constructor(
    private val remoteGenerator: ExerciseAiSearchRemoteGenerator = GeminiExerciseAiSearchRemoteGenerator(),
) {
    /**
     * Matches [query] against the full [catalog] using the model's knowledge of
     * alternative exercise names. Throws when the remote call or parsing fails;
     * an empty match list is a valid "not in the library" answer.
     */
    fun search(query: String, catalog: List<ExerciseAiSearchCatalogEntry>): ExerciseAiSearchServiceResult {
        val trimmedQuery = query.trim()
        require(trimmedQuery.isNotBlank()) { "Exercise AI search query must not be blank." }
        if (catalog.isEmpty()) {
            return ExerciseAiSearchServiceResult(
                query = trimmedQuery,
                generatedAtUtc = Instant.now(),
                matches = emptyList(),
                model = null,
            )
        }

        val prompt = buildExerciseAiSearchPrompt(trimmedQuery, catalog)
        val matches = parseExerciseAiSearchResponse(
            rawText = remoteGenerator.generate(prompt),
            catalog = catalog,
        )
        return ExerciseAiSearchServiceResult(
            query = trimmedQuery,
            generatedAtUtc = Instant.now(),
            matches = matches,
            model = remoteGenerator.model,
        )
    }
}

internal fun buildExerciseAiSearchPrompt(
    query: String,
    catalog: List<ExerciseAiSearchCatalogEntry>,
): String {
    return """
        You match a user's exercise search query against a fixed exercise catalog for a fitness app.
        The user typed the full name of an exercise they believe exists, but exact text search failed.
        Exercises often go by multiple names: machine or brand names, colloquial gym slang, abbreviations, regional variants, or misspellings.
        
        Task:
        - Find every catalog exercise that genuinely matches the query, ranked by confidence.
        - Return at most $EXERCISE_AI_SEARCH_MAX_MATCHES matches. Do not pad the list; a specific query usually has 1-3 real matches.
        - If the exercise is genuinely not in the catalog, return an empty matches array. Never force a match.
        
        Rules:
        - Every exerciseId must come from the catalog payload. Never invent IDs.
        - matchType must be one of: exact_alias (same exercise under a different name), variant (same movement with different equipment or position), related (close substitute only when nothing closer exists).
        - Rank exact_alias above variant, and variant above related.
        - explanation is one short sentence a lifter would understand, for example "Also known as the reverse pec deck.".
        
        Return only JSON in this exact shape:
        {"matches":[{"rank":1,"exerciseId":123,"matchType":"exact_alias","confidence":0.95,"explanation":"Also known as the reverse pec deck."}]}
        
        Query: ${JSONObject.quote(query)}
        
        Catalog:
        ${buildExerciseAiSearchCatalogJson(catalog)}
    """.trimIndent()
}

internal fun buildExerciseAiSearchCatalogJson(catalog: List<ExerciseAiSearchCatalogEntry>): JSONArray {
    return JSONArray().apply {
        catalog.forEach { entry ->
            val item = JSONObject()
                .put("id", entry.id)
                .put("name", entry.name)
            if (entry.synonyms.isNotEmpty()) {
                item.put("synonyms", JSONArray(entry.synonyms))
            }
            put(item)
        }
    }
}

internal fun parseExerciseAiSearchResponse(
    rawText: String,
    catalog: List<ExerciseAiSearchCatalogEntry>,
): List<ExerciseAiSearchParsedMatch> {
    val catalogById = catalog.associateBy { it.id }
    if (catalogById.isEmpty()) return emptyList()
    val payload = JSONObject(extractExerciseAiSearchJsonObject(rawText))
    val rawMatches = payload.optJSONArray("matches")
        ?: payload.optJSONArray("rankedMatches")
        ?: payload.optJSONArray("ranked_matches")
        ?: return emptyList()
    val seenIds = mutableSetOf<Long>()
    val matches = mutableListOf<ExerciseAiSearchParsedMatch>()
    for (index in 0 until rawMatches.length()) {
        val item = rawMatches.optJSONObject(index) ?: continue
        val exerciseId = item.optLongAny("exerciseId", "exercise_id", "id") ?: continue
        if (!seenIds.add(exerciseId)) continue
        val entry = catalogById[exerciseId] ?: continue
        val rank = item.optInt("rank", index + 1).coerceAtLeast(1)
        val matchType = cleanExerciseAiSearchMatchType(item.optString("matchType", item.optString("match_type")))
        val confidence = item.optDoubleAny("confidence", "score")
            ?.coerceIn(0.0, 1.0)
            ?: 0.7
        val explanation = cleanExerciseAiSearchText(
            item.optString("explanation", item.optString("reason")),
            "Possible match for this search.",
            160,
        )
        matches += ExerciseAiSearchParsedMatch(
            rank = rank,
            exerciseId = entry.id,
            matchType = matchType,
            confidence = confidence,
            explanation = explanation,
        )
    }
    return matches
        .sortedWith(compareBy({ it.rank }, { -it.confidence }))
        .take(EXERCISE_AI_SEARCH_MAX_MATCHES)
        .mapIndexed { index, match -> match.copy(rank = index + 1) }
}

private fun cleanExerciseAiSearchMatchType(value: String): String {
    val normalized = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return normalized.takeIf { it in EXERCISE_AI_SEARCH_MATCH_TYPES } ?: "related"
}

internal fun exerciseAiSearchMatchTypeLabel(matchType: String): String = when (matchType) {
    "exact_alias" -> "Same exercise"
    "variant" -> "Variant"
    else -> "Related"
}

private fun cleanExerciseAiSearchText(value: String, fallback: String, maxLength: Int): String {
    val cleaned = value
        .trim()
        .removePrefix("\"")
        .removeSuffix("\"")
        .replace(Regex("\\s+"), " ")
        .takeIf { it.isNotBlank() }
        ?: fallback
    return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength - 1).trimEnd() + "."
}

private fun JSONObject.optLongAny(vararg keys: String): Long? {
    keys.forEach { key ->
        if (has(key) && !isNull(key)) {
            val value = opt(key)
            when (value) {
                is Number -> return value.toLong()
                is String -> value.toLongOrNull()?.let { return it }
            }
        }
    }
    return null
}

private fun JSONObject.optDoubleAny(vararg keys: String): Double? {
    keys.forEach { key ->
        if (has(key) && !isNull(key)) {
            val value = opt(key)
            when (value) {
                is Number -> return value.toDouble()
                is String -> value.toDoubleOrNull()?.let { return it }
            }
        }
    }
    return null
}

private fun extractExerciseAiSearchJsonObject(rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
    val fenced = trimmed
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    if (fenced.startsWith("{") && fenced.endsWith("}")) return fenced

    val start = rawText.indexOf('{')
    if (start == -1) error("No JSON object found in exercise AI search response.")
    var depth = 0
    for (index in start until rawText.length) {
        when (rawText[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return rawText.substring(start, index + 1)
                }
            }
        }
    }
    error("Unterminated JSON object in exercise AI search response.")
}
