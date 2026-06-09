package dev.toastlabs.toastlift.data

import dev.toastlabs.toastlift.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.math.sqrt

private const val EXERCISE_DISCOVERY_PROMPT_VERSION = "exercise_discovery_ranked_v1"
internal const val EXERCISE_DISCOVERY_PICK_COUNT = 5

enum class ExerciseDiscoverySource {
    GEMINI,
    DETERMINISTIC_FALLBACK,
}

data class ExerciseDiscoveryPick(
    val rank: Int,
    val exercise: ExerciseSummary,
    val reason: String,
    val whyNow: String,
    val discoveryAngle: String,
    val confidence: Double,
    val evidence: List<String> = emptyList(),
)

data class ExerciseDiscoveryResult(
    val generatedAtUtc: Instant,
    val source: ExerciseDiscoverySource,
    val picks: List<ExerciseDiscoveryPick>,
    val appliedFilterLabels: List<String>,
    val performedExerciseCount: Int,
    val candidateExerciseCount: Int,
    val zeroSessionCandidateCount: Int,
    val model: String?,
    val promptVersion: String = EXERCISE_DISCOVERY_PROMPT_VERSION,
    val emptyReason: String? = null,
)

internal data class ExerciseDiscoveryExercise(
    val summary: ExerciseSummary,
    val primeMover: String?,
    val secondaryMuscle: String?,
    val tertiaryMuscle: String?,
    val posture: String?,
    val laterality: String?,
    val classification: String?,
    val movementPatterns: List<String>,
    val planesOfMotion: List<String>,
)

internal data class ExerciseDiscoveryContext(
    val query: String,
    val filters: LibraryFilters,
    val appliedFilterLabels: List<String>,
    val performedExercises: List<ExerciseDiscoveryExercise>,
    val zeroSessionCandidates: List<ExerciseDiscoveryExercise>,
    val lowExposureCandidates: List<ExerciseDiscoveryExercise>,
    val totalPerformedExercises: Int,
    val totalMatchingExercises: Int,
    val totalZeroSessionCandidates: Int,
) {
    val candidateExercises: List<ExerciseDiscoveryExercise>
        get() = (zeroSessionCandidates + lowExposureCandidates)
            .distinctBy { it.summary.id }
}

internal interface ExerciseDiscoveryRemoteGenerator {
    val model: String?
    fun generate(prompt: String): String
}

internal class GeminiExerciseDiscoveryRemoteGenerator(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val configuredModel: String = BuildConfig.GEMINI_PRIMARY_MODEL,
) : ExerciseDiscoveryRemoteGenerator {
    override val model: String?
        get() = configuredModel.takeIf { it.isNotBlank() }

    override fun generate(prompt: String): String {
        require(apiKey.isNotBlank()) { "Missing GEMINI_API_KEY for exercise discovery." }
        require(configuredModel.isNotBlank()) { "Missing GEMINI_PRIMARY_MODEL for exercise discovery." }

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
                    .put("temperature", 0.35)
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
                throw IllegalStateException("Gemini returned an empty exercise discovery response.")
            }
            return responseText
        } finally {
            connection.disconnect()
        }
    }
}

internal class DeterministicExerciseDiscoveryGenerator {
    fun generate(context: ExerciseDiscoveryContext): List<ExerciseDiscoveryPick> {
        val candidates = context.candidateExercises
        if (candidates.isEmpty()) return emptyList()

        val targetCounts = context.performedExercises.weightedCounts { listOfNotNull(it.summary.targetMuscleGroup) }
        val primeCounts = context.performedExercises.weightedCounts { listOfNotNull(it.primeMover) }
        val equipmentCounts = context.performedExercises.weightedCounts {
            listOfNotNull(it.summary.equipment, it.summary.secondaryEquipment)
        }
        val movementCounts = context.performedExercises.weightedCounts { it.movementPatterns }

        return candidates
            .map { exercise ->
                val summary = exercise.summary
                val targetNovelty = noveltyScore(summary.targetMuscleGroup, targetCounts)
                val primeNovelty = noveltyScore(exercise.primeMover, primeCounts)
                val equipmentNovelty = noveltyScore(summary.equipment, equipmentCounts)
                val movementNovelty = exercise.movementPatterns.maxOfOrNull { noveltyScore(it, movementCounts) } ?: 0.35
                val zeroSessionBoost = if (summary.loggedSessionCount == 0) 3.0 else 1.15 / (summary.loggedSessionCount + 1)
                val preference = when (summary.recommendationBias) {
                    RecommendationBias.MoreOften -> 0.6
                    RecommendationBias.LessOften -> -1.0
                    RecommendationBias.Neutral -> 0.0
                }
                val filterFit = if (context.appliedFilterLabels.isNotEmpty()) 0.55 else 0.0
                val favorite = if (summary.favorite) 0.25 else 0.0
                val score = zeroSessionBoost +
                    (targetNovelty * 1.25) +
                    (primeNovelty * 1.05) +
                    (equipmentNovelty * 0.6) +
                    (movementNovelty * 0.8) +
                    preference +
                    filterFit +
                    favorite
                exercise to score
            }
            .sortedWith(
                compareByDescending<Pair<ExerciseDiscoveryExercise, Double>> { it.second }
                    .thenBy { it.first.summary.loggedSessionCount }
                    .thenBy { it.first.summary.name },
            )
            .take(EXERCISE_DISCOVERY_PICK_COUNT)
            .mapIndexed { index, (exercise, score) ->
                deterministicPick(
                    rank = index + 1,
                    exercise = exercise,
                    score = score,
                    context = context,
                    targetCounts = targetCounts,
                    primeCounts = primeCounts,
                    equipmentCounts = equipmentCounts,
                )
            }
    }

    private fun deterministicPick(
        rank: Int,
        exercise: ExerciseDiscoveryExercise,
        score: Double,
        context: ExerciseDiscoveryContext,
        targetCounts: Map<String, Int>,
        primeCounts: Map<String, Int>,
        equipmentCounts: Map<String, Int>,
    ): ExerciseDiscoveryPick {
        val summary = exercise.summary
        val target = summary.targetMuscleGroup.ifBlank { "this target" }
        val prime = exercise.primeMover?.takeIf { it.isNotBlank() }
        val filterSummary = context.appliedFilterLabels.take(2).joinToString(", ").takeIf { it.isNotBlank() }
        val underusedPrime = prime?.takeIf { (primeCounts[it.normalizedDiscoveryToken()] ?: 0) <= 1 }
        val angle = when {
            summary.loggedSessionCount == 0 -> "new_to_you"
            underusedPrime != null -> "undertrained_mover"
            (equipmentCounts[summary.equipment.normalizedDiscoveryToken()] ?: 0) <= 1 -> "equipment_variety"
            else -> "low_exposure_rotation"
        }
        val reason = when (angle) {
            "new_to_you" -> "${summary.name} is a new-to-you $target option that fits the current Library context."
            "undertrained_mover" -> "${summary.name} gives ${underusedPrime ?: target} more attention without forcing a big change to the session."
            "equipment_variety" -> "${summary.name} brings in ${summary.equipment} work that has not shown up much in your history."
            else -> "${summary.name} is still low-exposure in your log and has enough fit to be worth another look."
        }
        val whyNow = filterSummary?.let {
            "It matches $it while adding variety beyond your most repeated exercises."
        } ?: "It balances your exercise history by prioritizing underused muscles, equipment, and movement patterns."
        val evidence = buildList {
            add(if (summary.loggedSessionCount == 0) "0 logged sessions" else "${summary.loggedSessionCount} logged sessions")
            val targetCount = targetCounts[summary.targetMuscleGroup.normalizedDiscoveryToken()] ?: 0
            add("$target appears across $targetCount logged session${if (targetCount == 1) "" else "s"}")
            prime?.let { add("Primary mover: $it") }
            add("Equipment: ${summary.equipment}")
        }
        return ExerciseDiscoveryPick(
            rank = rank,
            exercise = summary,
            reason = cleanDiscoveryText(reason, "${summary.name} fits your current discovery context.", 180),
            whyNow = cleanDiscoveryText(whyNow, "It adds variety without leaving your current filters.", 180),
            discoveryAngle = angle,
            confidence = (0.54 + (score / 14.0)).coerceIn(0.55, 0.9),
            evidence = evidence.map { cleanDiscoveryText(it, "", 90) }.filter { it.isNotBlank() }.take(3),
        )
    }

    private fun noveltyScore(value: String?, counts: Map<String, Int>): Double {
        val key = value.normalizedDiscoveryToken()
        if (key.isBlank()) return 0.2
        val count = counts[key] ?: return 1.35
        return (1.0 / sqrt((count + 1).toDouble())).coerceIn(0.15, 1.0)
    }

    private fun List<ExerciseDiscoveryExercise>.weightedCounts(
        values: (ExerciseDiscoveryExercise) -> List<String>,
    ): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        forEach { exercise ->
            val weight = exercise.summary.loggedSessionCount.coerceAtLeast(1)
            values(exercise)
                .map { it.normalizedDiscoveryToken() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { key -> counts[key] = (counts[key] ?: 0) + weight }
        }
        return counts
    }
}

internal class ExerciseDiscoveryService internal constructor(
    private val remoteGenerator: ExerciseDiscoveryRemoteGenerator = GeminiExerciseDiscoveryRemoteGenerator(),
    private val fallbackGenerator: DeterministicExerciseDiscoveryGenerator = DeterministicExerciseDiscoveryGenerator(),
) {
    fun generate(context: ExerciseDiscoveryContext): ExerciseDiscoveryResult {
        if (context.candidateExercises.isEmpty()) {
            return result(
                source = ExerciseDiscoverySource.DETERMINISTIC_FALLBACK,
                context = context,
                picks = emptyList(),
                model = null,
                emptyReason = "No eligible exercises match these filters. Clear one filter or broaden the search.",
            )
        }

        val fallbackPicks = fallbackGenerator.generate(context)
        val prompt = buildExerciseDiscoveryPrompt(context)
        val livePicks = runCatching {
            parseExerciseDiscoveryGeminiResponse(
                rawText = remoteGenerator.generate(prompt),
                context = context,
            )
        }.getOrDefault(emptyList())

        val finalPicks = mergeExerciseDiscoveryPicks(livePicks, fallbackPicks)
        val source = if (livePicks.isNotEmpty()) {
            ExerciseDiscoverySource.GEMINI
        } else {
            ExerciseDiscoverySource.DETERMINISTIC_FALLBACK
        }
        return result(
            source = source,
            context = context,
            picks = finalPicks,
            model = if (source == ExerciseDiscoverySource.GEMINI) remoteGenerator.model else null,
            emptyReason = null,
        )
    }

    private fun result(
        source: ExerciseDiscoverySource,
        context: ExerciseDiscoveryContext,
        picks: List<ExerciseDiscoveryPick>,
        model: String?,
        emptyReason: String?,
    ): ExerciseDiscoveryResult {
        return ExerciseDiscoveryResult(
            generatedAtUtc = Instant.now(),
            source = source,
            picks = picks,
            appliedFilterLabels = context.appliedFilterLabels,
            performedExerciseCount = context.totalPerformedExercises,
            candidateExerciseCount = context.totalMatchingExercises,
            zeroSessionCandidateCount = context.totalZeroSessionCandidates,
            model = model,
            emptyReason = emptyReason,
        )
    }
}

internal fun buildExerciseDiscoveryPrompt(context: ExerciseDiscoveryContext): String {
    return """
        You rank exercise discovery picks for a fitness app user who is idly browsing the Library.
        Your job is not to pick the safest generic exercises. Find high-signal next things to try.
        
        Primary goal:
        - Return the top ${EXERCISE_DISCOVERY_PICK_COUNT} exercises the user should discover next, in ranked order.
        
        Ranking principles:
        - Prefer exercises with zero logged sessions when available.
        - Respect all active filters and search text. Every pick must come from candidate exercise IDs in the payload.
        - Use the user's performed exercise history to find useful novelty: underused muscles, movement patterns, equipment, and nearby alternatives to repeated habits.
        - Avoid novelty for its own sake. The pick should be practical, loggable, and compatible with the current Library context.
        - Penalize exercises marked as preference down unless the data gives a strong reason.
        - Do not mention facts not present in the payload.
        - Reasons should be specific enough to feel personally ranked, not generic fitness advice.
        
        Return only JSON in this exact shape:
        {
          "rankedPicks": [
            {
              "rank": 1,
              "exerciseId": 123,
              "reason": "One concise sentence explaining the fit.",
              "whyNow": "One concise sentence explaining why this is a good next discovery now.",
              "discoveryAngle": "new_to_you | undertrained_mover | equipment_variety | movement_pattern_gap | low_exposure_rotation",
              "confidence": 0.82,
              "evidence": ["short evidence string", "short evidence string"]
            }
          ]
        }
        
        Payload:
        ${buildExerciseDiscoveryPromptPayload(context).toString(2)}
    """.trimIndent()
}

internal fun buildExerciseDiscoveryPromptPayload(context: ExerciseDiscoveryContext): JSONObject {
    return JSONObject()
        .put("promptVersion", EXERCISE_DISCOVERY_PROMPT_VERSION)
        .put("query", context.query.takeIf { it.isNotBlank() })
        .put("activeFilters", JSONArray(context.appliedFilterLabels))
        .put(
            "rules",
            JSONObject()
                .put("targetPickCount", EXERCISE_DISCOVERY_PICK_COUNT)
                .put("validCandidateIds", JSONArray(context.candidateExercises.map { it.summary.id }))
                .put("preferZeroSessionCandidates", context.zeroSessionCandidates.isNotEmpty()),
        )
        .put(
            "catalogCounts",
            JSONObject()
                .put("performedExercisesWithCompletedSets", context.totalPerformedExercises)
                .put("matchingExercises", context.totalMatchingExercises)
                .put("matchingZeroSessionExercises", context.totalZeroSessionCandidates)
                .put("performedContextIncluded", context.performedExercises.size)
                .put("zeroSessionCandidatesIncluded", context.zeroSessionCandidates.size)
                .put("lowExposureCandidatesIncluded", context.lowExposureCandidates.size),
        )
        .put("performedExercises", exerciseDiscoveryJsonArray(context.performedExercises))
        .put("zeroSessionCandidates", exerciseDiscoveryJsonArray(context.zeroSessionCandidates))
        .put("lowExposureCandidates", exerciseDiscoveryJsonArray(context.lowExposureCandidates))
}

internal fun parseExerciseDiscoveryGeminiResponse(
    rawText: String,
    context: ExerciseDiscoveryContext,
): List<ExerciseDiscoveryPick> {
    val candidateById = context.candidateExercises.associateBy { it.summary.id }
    if (candidateById.isEmpty()) return emptyList()
    val payload = JSONObject(extractExerciseDiscoveryJsonObject(rawText))
    val rankedPicks = payload.optJSONArray("rankedPicks")
        ?: payload.optJSONArray("ranked_picks")
        ?: return emptyList()
    val seenIds = mutableSetOf<Long>()
    val picks = mutableListOf<ExerciseDiscoveryPick>()
    for (index in 0 until rankedPicks.length()) {
        val item = rankedPicks.optJSONObject(index) ?: continue
        val exerciseId = item.optLongAny("exerciseId", "exercise_id", "id") ?: continue
        if (!seenIds.add(exerciseId)) continue
        val exercise = candidateById[exerciseId] ?: continue
        val rank = item.optInt("rank", index + 1).coerceAtLeast(1)
        val reason = cleanDiscoveryText(
            item.optString("reason"),
            "${exercise.summary.name} fits your current discovery context.",
            180,
        )
        val whyNow = cleanDiscoveryText(
            item.optString("whyNow", item.optString("why_now")),
            "It adds variety without leaving your current filters.",
            180,
        )
        val angle = cleanDiscoveryAngle(item.optString("discoveryAngle", item.optString("discovery_angle")))
        val confidence = item.optDoubleAny("confidence", "score")
            ?.coerceIn(0.0, 1.0)
            ?: 0.72
        val evidence = item.optJSONArray("evidence")
            ?.let { array ->
                buildList {
                    for (evidenceIndex in 0 until array.length()) {
                        val value = cleanDiscoveryText(array.optString(evidenceIndex), "", 90)
                        if (value.isNotBlank()) add(value)
                    }
                }
            }
            .orEmpty()
            .take(3)
        picks += ExerciseDiscoveryPick(
            rank = rank,
            exercise = exercise.summary,
            reason = reason,
            whyNow = whyNow,
            discoveryAngle = angle,
            confidence = confidence,
            evidence = evidence,
        )
    }
    return picks
        .sortedBy { it.rank }
        .take(EXERCISE_DISCOVERY_PICK_COUNT)
        .mapIndexed { index, pick -> pick.copy(rank = index + 1) }
}

private fun mergeExerciseDiscoveryPicks(
    primary: List<ExerciseDiscoveryPick>,
    fallback: List<ExerciseDiscoveryPick>,
): List<ExerciseDiscoveryPick> {
    val seenIds = mutableSetOf<Long>()
    return (primary + fallback)
        .filter { seenIds.add(it.exercise.id) }
        .take(EXERCISE_DISCOVERY_PICK_COUNT)
        .mapIndexed { index, pick -> pick.copy(rank = index + 1) }
}

private fun exerciseDiscoveryJsonArray(exercises: List<ExerciseDiscoveryExercise>): JSONArray {
    return JSONArray().apply {
        exercises.forEach { put(it.toPromptJson()) }
    }
}

private fun ExerciseDiscoveryExercise.toPromptJson(): JSONObject {
    return JSONObject()
        .put("exerciseId", summary.id)
        .put("name", summary.name)
        .put("sessionCount", summary.loggedSessionCount)
        .put("difficulty", summary.difficulty)
        .put("bodyRegion", summary.bodyRegion)
        .put("targetMuscleGroup", summary.targetMuscleGroup)
        .put("primaryMover", primeMover)
        .put("secondaryMuscle", secondaryMuscle)
        .put("tertiaryMuscle", tertiaryMuscle)
        .put("equipment", summary.equipment)
        .put("secondaryEquipment", summary.secondaryEquipment)
        .put("mechanics", summary.mechanics)
        .put("posture", posture)
        .put("laterality", laterality)
        .put("classification", classification)
        .put("movementPatterns", JSONArray(movementPatterns))
        .put("planesOfMotion", JSONArray(planesOfMotion))
        .put("favorite", summary.favorite)
        .put("preference", summary.recommendationBias.name)
}

private fun cleanDiscoveryText(value: String, fallback: String, maxLength: Int): String {
    val cleaned = value
        .trim()
        .removePrefix("\"")
        .removeSuffix("\"")
        .replace(Regex("\\s+"), " ")
        .takeIf { it.isNotBlank() }
        ?: fallback
    return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength - 1).trimEnd() + "."
}

private fun cleanDiscoveryAngle(value: String): String {
    val normalized = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    return normalized.takeIf {
        it in setOf(
            "new_to_you",
            "undertrained_mover",
            "equipment_variety",
            "movement_pattern_gap",
            "low_exposure_rotation",
        )
    } ?: "new_to_you"
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

private fun extractExerciseDiscoveryJsonObject(rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
    val fenced = trimmed
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    if (fenced.startsWith("{") && fenced.endsWith("}")) return fenced

    val start = rawText.indexOf('{')
    if (start == -1) error("No JSON object found in exercise discovery response.")
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
    error("Unterminated JSON object in exercise discovery response.")
}

private fun String?.normalizedDiscoveryToken(): String {
    return this
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()
}
