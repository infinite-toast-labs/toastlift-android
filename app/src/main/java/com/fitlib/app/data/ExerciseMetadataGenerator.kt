package com.fitlib.app.data

import com.fitlib.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class GeneratedCustomExerciseMetadata(
    val name: String,
    val difficultyLevel: String?,
    val bodyRegion: String?,
    val targetMuscleGroup: String?,
    val primeMoverMuscle: String?,
    val secondaryMuscle: String?,
    val tertiaryMuscle: String?,
    val primaryEquipment: String?,
    val primaryItemCount: Int?,
    val secondaryEquipment: String?,
    val secondaryItemCount: Int?,
    val posture: String?,
    val armUsage: String?,
    val armPattern: String?,
    val grip: String?,
    val loadPositionEnding: String?,
    val legPattern: String?,
    val footElevation: String?,
    val combinationType: String?,
    val forceType: String?,
    val mechanics: String?,
    val laterality: String?,
    val classification: String?,
    val movementPatterns: List<String>,
    val planesOfMotion: List<String>,
    val shortDemoLabel: String?,
    val shortDemoUrl: String?,
    val inDepthLabel: String?,
    val inDepthUrl: String?,
    val synonyms: List<String>,
)

interface ExerciseMetadataGenerator {
    fun generate(
        exerciseName: String,
        taxonomy: CustomExerciseTaxonomy,
        nearbyExercises: List<ExerciseSummary>,
    ): GeneratedCustomExerciseMetadata
}

class GeminiExerciseMetadataGenerator : ExerciseMetadataGenerator {
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
    private val model: String = BuildConfig.GEMINI_PRIMARY_MODEL

    override fun generate(
        exerciseName: String,
        taxonomy: CustomExerciseTaxonomy,
        nearbyExercises: List<ExerciseSummary>,
    ): GeneratedCustomExerciseMetadata {
        require(exerciseName.isNotBlank()) { "Exercise name is required." }
        require(apiKey.isNotBlank()) { "Missing GEMINI_API_KEY for custom exercise generation." }
        require(model.isNotBlank()) { "Missing GEMINI_PRIMARY_MODEL for custom exercise generation." }

        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", buildPrompt(exerciseName.trim(), taxonomy, nearbyExercises)),
                        ),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
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
            val root = JSONObject(body)
            val responseText = root
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                .orEmpty()
            if (responseText.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
            return parseResponse(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPrompt(
        exerciseName: String,
        taxonomy: CustomExerciseTaxonomy,
        nearbyExercises: List<ExerciseSummary>,
    ): String {
        val nearby = if (nearbyExercises.isEmpty()) {
            "None"
        } else {
            nearbyExercises.joinToString("\n") {
                "- ${it.name} | bodyRegion=${it.bodyRegion} | target=${it.targetMuscleGroup} | equipment=${it.equipment}"
            }
        }
        return """
            You are helping fill a local SQLite exercise catalog for a workout app.
            Given only an exercise name, infer the best structured metadata for the exercise.
            
            Exercise name:
            $exerciseName
            
            Nearby existing exercises:
            $nearby
            
            Use existing canonical values when possible.
            Do not invent new values for these closed-set fields:
            - difficultyLevel
            - bodyRegion
            - targetMuscleGroup
            - primaryEquipment
            - secondaryEquipment
            - forceType
            - mechanics
            - laterality
            - armUsage
            - armPattern
            - legPattern
            - combinationType
            - classification
            - movementPatterns
            - planesOfMotion
            
            Closed-set options:
            difficultyLevel=${taxonomy.difficultyLevels}
            bodyRegion=${taxonomy.bodyRegions}
            targetMuscleGroup=${taxonomy.targetMuscles}
            primaryEquipment=${taxonomy.equipmentOptions}
            secondaryEquipment=${taxonomy.equipmentOptions + ""}
            posture=${taxonomy.postures}
            armUsage=${taxonomy.armUsageOptions}
            armPattern=${taxonomy.armPatternOptions}
            grip=${taxonomy.gripOptions}
            loadPositionEnding=${taxonomy.loadPositionOptions}
            legPattern=${taxonomy.legPatternOptions}
            footElevation=${taxonomy.footElevationOptions}
            combinationType=${taxonomy.combinationTypeOptions}
            forceType=${taxonomy.forceTypeOptions}
            mechanics=${taxonomy.mechanicsOptions}
            laterality=${taxonomy.lateralityOptions}
            classification=${taxonomy.classificationOptions}
            movementPatterns=${taxonomy.movementPatternOptions}
            planesOfMotion=${taxonomy.planeOfMotionOptions}
            primeMovers=${taxonomy.primeMovers}
            
            Return only JSON with this exact shape:
            {
              "name": "string",
              "difficultyLevel": "string",
              "bodyRegion": "string",
              "targetMuscleGroup": "string",
              "primeMoverMuscle": "string",
              "secondaryMuscle": "string",
              "tertiaryMuscle": "string",
              "primaryEquipment": "string",
              "primaryItemCount": 1,
              "secondaryEquipment": "string",
              "secondaryItemCount": 0,
              "posture": "string",
              "armUsage": "string",
              "armPattern": "string",
              "grip": "string",
              "loadPositionEnding": "string",
              "legPattern": "string",
              "footElevation": "string",
              "combinationType": "string",
              "forceType": "string",
              "mechanics": "string",
              "laterality": "string",
              "classification": "string",
              "movementPatterns": ["string"],
              "planesOfMotion": ["string"],
              "shortDemoLabel": "string",
              "shortDemoUrl": "string",
              "inDepthLabel": "string",
              "inDepthUrl": "string",
              "synonyms": ["string"]
            }
            
            Requirements:
            - Prefer generic equipment categories like "Machine" instead of brand-specific equipment.
            - Keep URLs blank unless you are reasonably confident.
            - Use 1-3 movement patterns.
            - Use 1-3 planes of motion.
            - If the exercise is a machine chest press, likely mechanics is Compound and forceType is Push.
            - Return empty strings instead of nulls for optional scalar fields.
        """.trimIndent()
    }

    private fun parseResponse(rawText: String): GeneratedCustomExerciseMetadata {
        val jsonText = extractJsonObject(rawText)
        val payload = JSONObject(jsonText)
        return GeneratedCustomExerciseMetadata(
            name = payload.optString("name"),
            difficultyLevel = payload.optString("difficultyLevel").ifBlank { null },
            bodyRegion = payload.optString("bodyRegion").ifBlank { null },
            targetMuscleGroup = payload.optString("targetMuscleGroup").ifBlank { null },
            primeMoverMuscle = payload.optString("primeMoverMuscle").ifBlank { null },
            secondaryMuscle = payload.optString("secondaryMuscle").ifBlank { null },
            tertiaryMuscle = payload.optString("tertiaryMuscle").ifBlank { null },
            primaryEquipment = payload.optString("primaryEquipment").ifBlank { null },
            primaryItemCount = payload.optInt("primaryItemCount").takeIf { it > 0 },
            secondaryEquipment = payload.optString("secondaryEquipment").ifBlank { null },
            secondaryItemCount = payload.optInt("secondaryItemCount").takeIf { it > 0 },
            posture = payload.optString("posture").ifBlank { null },
            armUsage = payload.optString("armUsage").ifBlank { null },
            armPattern = payload.optString("armPattern").ifBlank { null },
            grip = payload.optString("grip").ifBlank { null },
            loadPositionEnding = payload.optString("loadPositionEnding").ifBlank { null },
            legPattern = payload.optString("legPattern").ifBlank { null },
            footElevation = payload.optString("footElevation").ifBlank { null },
            combinationType = payload.optString("combinationType").ifBlank { null },
            forceType = payload.optString("forceType").ifBlank { null },
            mechanics = payload.optString("mechanics").ifBlank { null },
            laterality = payload.optString("laterality").ifBlank { null },
            classification = payload.optString("classification").ifBlank { null },
            movementPatterns = payload.optJSONArray("movementPatterns").toStringList(),
            planesOfMotion = payload.optJSONArray("planesOfMotion").toStringList(),
            shortDemoLabel = payload.optString("shortDemoLabel").ifBlank { null },
            shortDemoUrl = payload.optString("shortDemoUrl").ifBlank { null },
            inDepthLabel = payload.optString("inDepthLabel").ifBlank { null },
            inDepthUrl = payload.optString("inDepthUrl").ifBlank { null },
            synonyms = payload.optJSONArray("synonyms").toStringList(),
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index)
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }
    }

    private fun extractJsonObject(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val fenced = trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (fenced.startsWith("{") && fenced.endsWith("}")) return fenced

        val start = rawText.indexOf('{')
        val end = rawText.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return rawText.substring(start, end + 1)
        }
        throw IllegalStateException("Could not parse Gemini JSON response: $rawText")
    }
}
