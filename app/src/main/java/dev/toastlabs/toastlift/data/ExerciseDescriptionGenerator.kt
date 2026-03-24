package dev.toastlabs.toastlift.data

import dev.toastlabs.toastlift.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal const val EXERCISE_DESCRIPTION_PROMPT_VERSION = "exercise_description_v1"

data class GeneratedExerciseDescriptionResult(
    val description: String,
    val generationModel: String?,
    val generationPromptVersion: String,
)

internal data class GeneratedExerciseDescriptionContent(
    val summary: String?,
    val steps: List<String>,
    val keyCues: List<String>,
)

internal interface ExerciseDescriptionRemoteGenerator {
    val generationModel: String?
    val generationPromptVersion: String

    fun generate(
        detail: ExerciseDetail,
        nearbyExercises: List<ExerciseSummary>,
    ): GeneratedExerciseDescriptionContent
}

internal class ExerciseDescriptionService(
    private val catalogRepository: CatalogRepository,
    private val remoteGenerator: ExerciseDescriptionRemoteGenerator = GeminiExerciseDescriptionRemoteGenerator(),
) {
    fun generate(exerciseId: Long): GeneratedExerciseDescriptionResult {
        val detail = catalogRepository.getExerciseDetail(exerciseId)
            ?: throw IllegalArgumentException("Exercise $exerciseId was not found.")
        val nearbyExercises = loadNearbyExercises(detail)
        val generated = remoteGenerator.generate(detail, nearbyExercises)
        return GeneratedExerciseDescriptionResult(
            description = formatGeneratedExerciseDescription(generated),
            generationModel = remoteGenerator.generationModel,
            generationPromptVersion = remoteGenerator.generationPromptVersion,
        )
    }

    private fun loadNearbyExercises(detail: ExerciseDetail): List<ExerciseSummary> {
        val byName = catalogRepository.searchExercises(
            query = detail.summary.name,
            limit = 4,
        )
        val byCategory = catalogRepository.searchExercises(
            query = "",
            filters = LibraryFilters(
                equipment = setOf(detail.summary.equipment),
                targetMuscles = setOf(detail.summary.targetMuscleGroup),
            ),
            limit = 6,
        )
        return (byName + byCategory)
            .distinctBy(ExerciseSummary::id)
            .filterNot { it.id == detail.summary.id }
            .take(6)
    }
}

internal class GeminiExerciseDescriptionRemoteGenerator(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val model: String = BuildConfig.GEMINI_PRIMARY_MODEL,
) : ExerciseDescriptionRemoteGenerator {
    override val generationModel: String
        get() = model

    override val generationPromptVersion: String
        get() = EXERCISE_DESCRIPTION_PROMPT_VERSION

    override fun generate(
        detail: ExerciseDetail,
        nearbyExercises: List<ExerciseSummary>,
    ): GeneratedExerciseDescriptionContent {
        require(apiKey.isNotBlank()) { "Missing GEMINI_API_KEY for exercise description generation." }
        require(model.isNotBlank()) { "Missing GEMINI_PRIMARY_MODEL for exercise description generation." }

        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", buildPrompt(detail, nearbyExercises)),
                        ),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.3)
                    .put("responseMimeType", "application/json"),
            )

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 45_000
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
                throw IllegalStateException("Gemini returned an empty exercise description response.")
            }
            return parseResponse(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPrompt(
        detail: ExerciseDetail,
        nearbyExercises: List<ExerciseSummary>,
    ): String {
        val payload = JSONObject()
            .put(
                "exercise",
                JSONObject()
                    .put("id", detail.summary.id)
                    .put("name", detail.summary.name)
                    .put("difficulty", detail.summary.difficulty)
                    .put("bodyRegion", detail.summary.bodyRegion)
                    .put("targetMuscleGroup", detail.summary.targetMuscleGroup)
                    .put("primaryEquipment", detail.summary.equipment)
                    .put("secondaryEquipment", detail.summary.secondaryEquipment ?: "")
                    .put("mechanics", detail.summary.mechanics ?: "")
                    .put("primeMover", detail.primeMover ?: "")
                    .put("secondaryMuscle", detail.secondaryMuscle ?: "")
                    .put("tertiaryMuscle", detail.tertiaryMuscle ?: "")
                    .put("posture", detail.posture)
                    .put("laterality", detail.laterality)
                    .put("classification", detail.classification)
                    .put("movementPatterns", JSONArray(detail.movementPatterns))
                    .put("planesOfMotion", JSONArray(detail.planesOfMotion))
                    .put("synonyms", JSONArray(detail.synonyms)),
            )
            .put(
                "nearbyExercises",
                JSONArray().apply {
                    nearbyExercises.forEach { exercise ->
                        put(
                            JSONObject()
                                .put("name", exercise.name)
                                .put("bodyRegion", exercise.bodyRegion)
                                .put("targetMuscleGroup", exercise.targetMuscleGroup)
                                .put("equipment", exercise.equipment)
                                .put("mechanics", exercise.mechanics ?: ""),
                        )
                    }
                },
            )

        return """
            You write concise, step-by-step exercise descriptions for a workout app.
            Use the structured exercise context below to infer how the movement is typically performed.
            
            Rules:
            - Output JSON only.
            - Keep the response practical and scannable, not a paragraph.
            - Use 3 to 6 short steps in plain language.
            - Each step should describe one action or phase of the movement.
            - Add 0 to 3 short key cues when they improve execution.
            - If details are uncertain, stay generic and safe instead of inventing niche setup specifics.
            - Do not mention reps, sets, loading percentages, or medical disclaimers.
            - Do not say "consult a professional" or similar filler.
            - Avoid markdown inside JSON string values.
            
            Return JSON in this exact shape:
            {
              "summary": "string",
              "steps": ["string"],
              "keyCues": ["string"]
            }
            
            Additional formatting requirements:
            - summary is optional but, when present, must be one short sentence.
            - steps must be ordered and actionable.
            - keyCues must be short fragments, not full paragraphs.
            
            Context:
            ${payload.toString(2)}
        """.trimIndent()
    }

    private fun parseResponse(rawText: String): GeneratedExerciseDescriptionContent {
        val payload = JSONObject(extractExerciseDescriptionJsonObject(rawText))
        val steps = payload.optJSONArray("steps").toStringList()
        if (steps.isEmpty()) {
            throw IllegalStateException("Generated exercise description did not include any steps.")
        }
        return GeneratedExerciseDescriptionContent(
            summary = payload.optString("summary").trim().ifBlank { null },
            steps = steps,
            keyCues = payload.optJSONArray("keyCues").toStringList().take(3),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index)
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let(::add)
            }
        }
    }
}

internal fun formatGeneratedExerciseDescription(content: GeneratedExerciseDescriptionContent): String {
    val sections = buildList {
        content.summary?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        if (content.steps.isNotEmpty()) {
            add(
                content.steps.mapIndexed { index, step ->
                    "${index + 1}. ${step.trim()}"
                }.joinToString("\n\n"),
            )
        }
        if (content.keyCues.isNotEmpty()) {
            add(
                buildString {
                    append("Key cues:\n")
                    append(
                        content.keyCues.joinToString("\n") { cue ->
                            "- ${cue.trim()}"
                        },
                    )
                },
            )
        }
    }
    return normalizeExerciseDescription(sections.joinToString("\n\n"))
        ?: throw IllegalStateException("Generated exercise description was blank after formatting.")
}

private fun extractExerciseDescriptionJsonObject(rawText: String): String {
    val start = rawText.indexOf('{')
    if (start == -1) error("No JSON object found in exercise description response.")
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
    error("Unterminated JSON object in exercise description response.")
}
