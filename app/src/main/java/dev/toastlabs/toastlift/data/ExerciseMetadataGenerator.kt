package dev.toastlabs.toastlift.data

import dev.toastlabs.toastlift.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

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

data class GeneratedCustomExerciseResult(
    val metadata: GeneratedCustomExerciseMetadata,
    val report: CustomExerciseGenerationReport,
)

data class CustomExerciseGenerationReport(
    val providerLabel: String,
    val model: String,
    val endpoint: String?,
    val promptVersion: String,
    val tokenUsage: CustomExerciseTokenUsage?,
    val rawModelResponse: String,
    val prettyModelResponse: String,
) {
    val clipboardText: String
        get() = formatCustomExerciseGenerationReportForClipboard(this)
}

data class CustomExerciseTokenUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val totalTokens: Long? = null,
    val inputCostUsd: Double? = null,
    val outputCostUsd: Double? = null,
    val cachedInputCostUsd: Double? = null,
    val totalCostUsd: Double? = null,
    val costSource: String? = null,
    val rawUsageJson: String? = null,
)

private data class TokenPricing(
    val inputPerMillionUsd: Double,
    val outputPerMillionUsd: Double,
    val cachedReadPerMillionUsd: Double? = null,
    val source: String,
)

interface ExerciseMetadataGenerator {
    val generationModel: String?
    val generationPromptVersion: String

    fun generate(
        exerciseName: String,
        taxonomy: CustomExerciseTaxonomy,
        nearbyExercises: List<ExerciseSummary>,
    ): GeneratedCustomExerciseMetadata = generateWithReport(exerciseName, taxonomy, nearbyExercises).metadata

    fun generateWithReport(
        exerciseName: String,
        taxonomy: CustomExerciseTaxonomy,
        nearbyExercises: List<ExerciseSummary>,
    ): GeneratedCustomExerciseResult
}

internal const val CUSTOM_EXERCISE_PROMPT_VERSION = "custom_exercise_v1"
const val DEFAULT_CUSTOM_EXERCISE_AI_MODEL_ID = "gemini_primary"

data class CustomExerciseAiModelOption(
    val id: String,
    val label: String,
    val providerLabel: String,
    val modelLabel: String,
    val supportingText: String,
)

val customExerciseAiModelOptions: List<CustomExerciseAiModelOption> = listOf(
    CustomExerciseAiModelOption(
        id = DEFAULT_CUSTOM_EXERCISE_AI_MODEL_ID,
        label = "Gemini",
        providerLabel = "Google Gemini",
        modelLabel = BuildConfig.GEMINI_PRIMARY_MODEL.ifBlank { "Configured primary model" },
        supportingText = "Uses the direct Gemini API configured in .env.",
    ),
    CustomExerciseAiModelOption(
        id = "opencode_deepseek_v4_flash",
        label = "OpenCode DeepSeek V4 Flash",
        providerLabel = "OpenCode",
        modelLabel = "deepseek-v4-flash",
        supportingText = "Uses OpenCode Zen chat completions with DeepSeek V4 Flash and high reasoning effort.",
    ),
    CustomExerciseAiModelOption(
        id = "opencode_glm_5_2",
        label = "OpenCode GLM 5.2",
        providerLabel = "OpenCode",
        modelLabel = "glm-5.2",
        supportingText = "Uses OpenCode Zen chat completions with GLM 5.2 and high reasoning effort.",
    ),
    CustomExerciseAiModelOption(
        id = "openrouter_z_ai_glm_5_2",
        label = "OpenRouter GLM 5.2",
        providerLabel = "OpenRouter",
        modelLabel = "z-ai/glm-5.2",
        supportingText = "Uses OpenRouter chat completions with the Z.ai GLM 5.2 model. Provider is part of the option ID.",
    ),
    CustomExerciseAiModelOption(
        id = "openrouter_deepseek_v4_pro",
        label = "OpenRouter DeepSeek V4 Pro",
        providerLabel = "OpenRouter",
        modelLabel = "deepseek/deepseek-v4-pro",
        supportingText = "Uses OpenRouter chat completions with DeepSeek V4 Pro. Distinct from the OpenCode DeepSeek option.",
    ),
    CustomExerciseAiModelOption(
        id = "openrouter_owl_alpha",
        label = "OpenRouter Owl Alpha",
        providerLabel = "OpenRouter",
        modelLabel = "openrouter/owl-alpha",
        supportingText = "Uses OpenRouter chat completions with Owl Alpha. Provider is part of the model ID.",
    ),
    CustomExerciseAiModelOption(
        id = "openrouter_openai_gpt_latest",
        label = "OpenRouter GPT Latest",
        providerLabel = "OpenRouter",
        modelLabel = "~openai/gpt-latest",
        supportingText = "Uses OpenRouter's OpenAI latest alias with high reasoning effort.",
    ),
    CustomExerciseAiModelOption(
        id = "openrouter_google_gemini_pro_latest",
        label = "OpenRouter Gemini Pro Latest",
        providerLabel = "OpenRouter",
        modelLabel = "~google/gemini-pro-latest",
        supportingText = "Uses OpenRouter's Google Gemini Pro latest alias with mandatory high reasoning effort.",
    ),
    CustomExerciseAiModelOption(
        id = "openrouter_anthropic_claude_opus_latest",
        label = "OpenRouter Claude Opus Latest",
        providerLabel = "OpenRouter",
        modelLabel = "~anthropic/claude-opus-latest",
        supportingText = "Uses OpenRouter's Anthropic Claude Opus latest alias with high reasoning effort.",
    ),
)

fun customExerciseAiModelOptionForId(id: String?): CustomExerciseAiModelOption {
    val normalized = id?.trim().orEmpty()
    return customExerciseAiModelOptions.firstOrNull { it.id == normalized }
        ?: customExerciseAiModelOptions.first()
}

enum class CustomExerciseAiProvider {
    GEMINI,
    OPENCODE,
    OPENROUTER,
}

fun selectedCustomExerciseAiProvider(rawValue: String = BuildConfig.CUSTOM_EXERCISE_AI_PROVIDER): CustomExerciseAiProvider {
    return when (rawValue.trim().lowercase()) {
        "pi",
        "pi_sdk",
        "pisdk",
        "opencode",
        "opencode_zen",
        -> CustomExerciseAiProvider.OPENCODE
        "openrouter",
        -> CustomExerciseAiProvider.OPENROUTER
        else -> CustomExerciseAiProvider.GEMINI
    }
}

fun selectedCustomExerciseAiModelOption(
    rawProvider: String = BuildConfig.CUSTOM_EXERCISE_AI_PROVIDER,
    rawOpenCodeModel: String = BuildConfig.OPENCODE_MODEL,
    rawOpenRouterModel: String = BuildConfig.OPENROUTER_MODEL,
): CustomExerciseAiModelOption {
    val normalizedProvider = rawProvider.trim().lowercase()
    val normalizedModel = normalizeOpenCodeModelForRequest(rawOpenCodeModel).lowercase(Locale.US)
    val normalizedOpenRouterModel = rawOpenRouterModel.trim().lowercase()
    return when {
        normalizedProvider == "gemini" || normalizedProvider.isBlank() -> customExerciseAiModelOptionForId(DEFAULT_CUSTOM_EXERCISE_AI_MODEL_ID)
        normalizedProvider == "openrouter" && normalizedOpenRouterModel == "z-ai/glm-5.2" -> customExerciseAiModelOptionForId("openrouter_z_ai_glm_5_2")
        normalizedProvider == "openrouter" && normalizedOpenRouterModel == "deepseek/deepseek-v4-pro" -> customExerciseAiModelOptionForId("openrouter_deepseek_v4_pro")
        normalizedProvider == "openrouter" && normalizedOpenRouterModel == "openrouter/owl-alpha" -> customExerciseAiModelOptionForId("openrouter_owl_alpha")
        normalizedProvider == "openrouter" && normalizedOpenRouterModel == "~openai/gpt-latest" -> customExerciseAiModelOptionForId("openrouter_openai_gpt_latest")
        normalizedProvider == "openrouter" && normalizedOpenRouterModel == "~google/gemini-pro-latest" -> customExerciseAiModelOptionForId("openrouter_google_gemini_pro_latest")
        normalizedProvider == "openrouter" && normalizedOpenRouterModel == "~anthropic/claude-opus-latest" -> customExerciseAiModelOptionForId("openrouter_anthropic_claude_opus_latest")
        normalizedProvider == "openrouter" -> customExerciseAiModelOptionForId("openrouter_z_ai_glm_5_2")
        normalizedModel == "glm-5.2" -> customExerciseAiModelOptionForId("opencode_glm_5_2")
        else -> customExerciseAiModelOptionForId("opencode_deepseek_v4_flash")
    }
}

fun createExerciseMetadataGenerator(
    provider: CustomExerciseAiProvider = selectedCustomExerciseAiProvider(),
): ExerciseMetadataGenerator {
    return when (provider) {
        CustomExerciseAiProvider.GEMINI -> GeminiExerciseMetadataGenerator()
        CustomExerciseAiProvider.OPENCODE -> OpenCodeExerciseMetadataGenerator()
        CustomExerciseAiProvider.OPENROUTER -> OpenRouterExerciseMetadataGenerator()
    }
}

fun createExerciseMetadataGeneratorForOption(
    option: CustomExerciseAiModelOption = selectedCustomExerciseAiModelOption(),
): ExerciseMetadataGenerator {
    return when (option.id) {
        DEFAULT_CUSTOM_EXERCISE_AI_MODEL_ID -> GeminiExerciseMetadataGenerator()
        "opencode_deepseek_v4_flash",
        "opencode_glm_5_2",
        -> OpenCodeExerciseMetadataGenerator(model = option.modelLabel)
        "openrouter_z_ai_glm_5_2",
        "openrouter_deepseek_v4_pro",
        "openrouter_owl_alpha",
        "openrouter_openai_gpt_latest",
        "openrouter_google_gemini_pro_latest",
        "openrouter_anthropic_claude_opus_latest",
        -> OpenRouterExerciseMetadataGenerator(model = option.modelLabel)
        else -> GeminiExerciseMetadataGenerator()
    }
}

class GeminiExerciseMetadataGenerator : ExerciseMetadataGenerator {
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
    private val model: String = BuildConfig.GEMINI_PRIMARY_MODEL

    override val generationModel: String?
        get() = model.takeIf { it.isNotBlank() }

    override val generationPromptVersion: String
        get() = CUSTOM_EXERCISE_PROMPT_VERSION

    override fun generateWithReport(
        exerciseName: String,
        taxonomy: CustomExerciseTaxonomy,
        nearbyExercises: List<ExerciseSummary>,
    ): GeneratedCustomExerciseResult {
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
                            JSONObject().put(
                                "text",
                                buildCustomExerciseMetadataPrompt(exerciseName.trim(), taxonomy, nearbyExercises),
                            ),
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
            return GeneratedCustomExerciseResult(
                metadata = parseCustomExerciseMetadataResponse(responseText),
                report = CustomExerciseGenerationReport(
                    providerLabel = "Google Gemini",
                    model = model,
                    endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    promptVersion = generationPromptVersion,
                    tokenUsage = parseGeminiTokenUsage(root.optJSONObject("usageMetadata")),
                    rawModelResponse = responseText,
                    prettyModelResponse = prettyPrintCustomExerciseModelResponse(responseText),
                ),
            )
        } finally {
            connection.disconnect()
        }
    }
}

class OpenCodeExerciseMetadataGenerator(
    private val apiKey: String = BuildConfig.OPENCODE_API_KEY,
    private val model: String = BuildConfig.OPENCODE_MODEL,
    private val chatCompletionsUrl: String = BuildConfig.OPENCODE_CHAT_COMPLETIONS_URL,
) : ExerciseMetadataGenerator {
    override val generationModel: String?
        get() = openCodeGenerationModelLabel(model)

    override val generationPromptVersion: String
        get() = CUSTOM_EXERCISE_PROMPT_VERSION

    override fun generateWithReport(
        exerciseName: String,
        taxonomy: CustomExerciseTaxonomy,
        nearbyExercises: List<ExerciseSummary>,
    ): GeneratedCustomExerciseResult {
        require(exerciseName.isNotBlank()) { "Exercise name is required." }
        require(apiKey.isNotBlank()) { "Missing OPENCODE_API_KEY for OpenCode custom exercise generation." }
        require(model.isNotBlank()) { "Missing OPENCODE_MODEL for OpenCode custom exercise generation." }
        require(chatCompletionsUrl.isNotBlank()) { "Missing OPENCODE_CHAT_COMPLETIONS_URL for OpenCode custom exercise generation." }

        val requestModel = normalizeOpenCodeModelForRequest(model)
        val reasoningEffort = openCodeReasoningEffortForModel(requestModel)
        val requestBody = JSONObject()
            .put("model", requestModel)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", "Return only valid JSON. Do not include Markdown fences."),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                buildCustomExerciseMetadataPrompt(exerciseName.trim(), taxonomy, nearbyExercises),
                            ),
                    ),
            )
            .put("temperature", 0.2)
            .put("response_format", JSONObject().put("type", "json_object"))
        if (reasoningEffort != null) {
            requestBody.put("reasoning_effort", reasoningEffort)
        }

        val connection = (URL(chatCompletionsUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
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
                throw IllegalStateException("OpenCode request failed ($status): $body")
            }
            val root = JSONObject(body)
            val responseText = root
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (responseText.isBlank()) {
                throw IllegalStateException("OpenCode returned an empty response.")
            }
            return GeneratedCustomExerciseResult(
                metadata = parseCustomExerciseMetadataResponse(responseText),
                report = CustomExerciseGenerationReport(
                    providerLabel = "OpenCode",
                    model = openCodeReportModelLabel(requestModel, reasoningEffort),
                    endpoint = chatCompletionsUrl,
                    promptVersion = generationPromptVersion,
                    tokenUsage = parseOpenCodeTokenUsage(root.optJSONObject("usage"), requestModel),
                    rawModelResponse = responseText,
                    prettyModelResponse = prettyPrintCustomExerciseModelResponse(responseText),
                ),
            )
        } finally {
            connection.disconnect()
        }
    }
}

class OpenRouterExerciseMetadataGenerator(
    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY,
    private val model: String = BuildConfig.OPENROUTER_MODEL,
    private val chatCompletionsUrl: String = BuildConfig.OPENROUTER_CHAT_COMPLETIONS_URL,
    private val generationUrl: String = BuildConfig.OPENROUTER_GENERATION_URL,
) : ExerciseMetadataGenerator {
    override val generationModel: String?
        get() = openRouterGenerationModelLabel(model)

    override val generationPromptVersion: String
        get() = CUSTOM_EXERCISE_PROMPT_VERSION

    override fun generateWithReport(
        exerciseName: String,
        taxonomy: CustomExerciseTaxonomy,
        nearbyExercises: List<ExerciseSummary>,
    ): GeneratedCustomExerciseResult {
        require(exerciseName.isNotBlank()) { "Exercise name is required." }
        require(apiKey.isNotBlank()) { "Missing OPENROUTER_API_KEY for OpenRouter custom exercise generation." }
        require(model.isNotBlank()) { "Missing OPENROUTER_MODEL for OpenRouter custom exercise generation." }
        require(chatCompletionsUrl.isNotBlank()) { "Missing OPENROUTER_CHAT_COMPLETIONS_URL for OpenRouter custom exercise generation." }

        val reasoningEffort = openRouterReasoningEffortForModel(model)
        val requestBody = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", "Return only valid JSON. Do not include Markdown fences."),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                buildCustomExerciseMetadataPrompt(exerciseName.trim(), taxonomy, nearbyExercises),
                            ),
                    ),
            )
            .put("temperature", 0.2)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("usage", JSONObject().put("include", true))
        if (reasoningEffort != null) {
            requestBody.put("reasoning", JSONObject().put("effort", reasoningEffort))
        }

        val connection = (URL(chatCompletionsUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("HTTP-Referer", "https://toastlift.local")
            setRequestProperty("X-Title", "ToastLift")
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
                throw IllegalStateException("OpenRouter request failed ($status): $body")
            }
            val root = JSONObject(body)
            val responseText = root
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (responseText.isBlank()) {
                throw IllegalStateException("OpenRouter returned an empty response.")
            }
            val generationId = root.optString("id").takeIf { it.isNotBlank() }
            val usage = parseOpenRouterTokenUsage(root.optJSONObject("usage"))
                .withOpenRouterGenerationStats(generationId)
                ?.withEstimatedCost(openRouterPricingForModel(model))
            return GeneratedCustomExerciseResult(
                metadata = parseCustomExerciseMetadataResponse(responseText),
                report = CustomExerciseGenerationReport(
                    providerLabel = "OpenRouter",
                    model = openRouterReportModelLabel(model, reasoningEffort),
                    endpoint = chatCompletionsUrl,
                    promptVersion = generationPromptVersion,
                    tokenUsage = usage,
                    rawModelResponse = responseText,
                    prettyModelResponse = prettyPrintCustomExerciseModelResponse(responseText),
                ),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun CustomExerciseTokenUsage?.withOpenRouterGenerationStats(generationId: String?): CustomExerciseTokenUsage? {
        if (generationId.isNullOrBlank() || generationUrl.isBlank() || this?.totalCostUsd != null) return this
        return runCatching {
            val separator = if (generationUrl.contains("?")) "&" else "?"
            val url = "$generationUrl${separator}id=${URLEncoder.encode(generationId, "UTF-8")}"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            try {
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use(BufferedReader::readText)
                    .orEmpty()
                if (status !in 200..299) return@runCatching this
                parseOpenRouterGenerationTokenUsage(JSONObject(body).optJSONObject("data"), this)
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: this
    }
}

internal fun parseCustomExerciseMetadataResponse(rawText: String): GeneratedCustomExerciseMetadata {
    val jsonText = extractCustomExerciseJsonObject(rawText)
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

fun formatCustomExerciseGenerationReportForClipboard(report: CustomExerciseGenerationReport): String {
    val usage = report.tokenUsage
    return buildString {
        appendLine("Custom Exercise AI Generation Report")
        appendLine()
        appendLine("Provider: ${report.providerLabel}")
        appendLine("Model: ${report.model}")
        appendLine("Prompt version: ${report.promptVersion}")
        appendLine("Endpoint: ${report.endpoint ?: "Not available"}")
        appendLine()
        appendLine("Token usage")
        appendLine("Input tokens: ${usage?.inputTokens?.toString() ?: "Not returned"}")
        appendLine("Output tokens: ${usage?.outputTokens?.toString() ?: "Not returned"}")
        appendLine("Cached input tokens: ${usage?.cachedInputTokens?.toString() ?: "Not returned"}")
        appendLine("Reasoning tokens: ${usage?.reasoningTokens?.toString() ?: "Not returned"}")
        appendLine("Total tokens: ${usage?.totalTokens?.toString() ?: "Not returned"}")
        appendLine()
        appendLine("USD token cost")
        appendLine("Cost source: ${usage?.costSource ?: "Not returned"}")
        appendLine("Input USD cost: ${formatUsdOrUnavailable(usage?.inputCostUsd)}")
        appendLine("Output USD cost: ${formatUsdOrUnavailable(usage?.outputCostUsd)}")
        appendLine("Cached input USD cost: ${formatUsdOrUnavailable(usage?.cachedInputCostUsd)}")
        appendLine("Total token USD cost: ${formatUsdOrUnavailable(usage?.totalCostUsd)}")
        if (!usage?.rawUsageJson.isNullOrBlank()) {
            appendLine()
            appendLine("Raw usage JSON")
            appendLine(usage?.rawUsageJson)
        }
        appendLine()
        appendLine("Model response")
        appendLine(report.prettyModelResponse.ifBlank { report.rawModelResponse })
    }
}

private fun parseGeminiTokenUsage(usage: JSONObject?): CustomExerciseTokenUsage? {
    if (usage == null) return null
    return CustomExerciseTokenUsage(
        inputTokens = usage.firstLongOrNull("promptTokenCount"),
        outputTokens = usage.firstLongOrNull("candidatesTokenCount"),
        cachedInputTokens = usage.firstLongOrNull("cachedContentTokenCount"),
        reasoningTokens = usage.firstLongOrNull("thoughtsTokenCount"),
        totalTokens = usage.firstLongOrNull("totalTokenCount"),
        rawUsageJson = usage.toString(2),
    ).withEstimatedCost(geminiPricingForModel(BuildConfig.GEMINI_PRIMARY_MODEL))
}

private fun parseOpenCodeTokenUsage(usage: JSONObject?, model: String): CustomExerciseTokenUsage? {
    if (usage == null) return null
    val promptDetails = usage.optJSONObject("prompt_tokens_details")
    val completionDetails = usage.optJSONObject("completion_tokens_details")
    val cost = usage.optJSONObject("cost")
    return CustomExerciseTokenUsage(
        inputTokens = usage.firstLongOrNull("prompt_tokens", "input_tokens"),
        outputTokens = usage.firstLongOrNull("completion_tokens", "output_tokens"),
        cachedInputTokens = promptDetails?.firstLongOrNull("cached_tokens", "cached_input_tokens"),
        reasoningTokens = completionDetails?.firstLongOrNull("reasoning_tokens"),
        totalTokens = usage.firstLongOrNull("total_tokens"),
        inputCostUsd = usage.firstDoubleOrNull("prompt_cost", "input_cost", "input_cost_usd")
            ?: cost?.firstDoubleOrNull("prompt", "input", "input_usd"),
        outputCostUsd = usage.firstDoubleOrNull("completion_cost", "output_cost", "output_cost_usd")
            ?: cost?.firstDoubleOrNull("completion", "output", "output_usd"),
        cachedInputCostUsd = usage.firstDoubleOrNull("cached_prompt_cost", "cached_input_cost", "cache_cost", "cache_read_cost")
            ?: cost?.firstDoubleOrNull("cached_prompt", "cached_input", "cache", "cache_read"),
        totalCostUsd = usage.firstDoubleOrNull("total_cost", "total_cost_usd", "estimated_cost", "cost_usd")
            ?: cost?.firstDoubleOrNull("total", "total_usd", "estimated"),
        costSource = if (usage.hasAny("prompt_cost", "input_cost", "input_cost_usd", "completion_cost", "output_cost", "output_cost_usd", "total_cost", "total_cost_usd", "estimated_cost", "cost_usd") || cost != null) {
            "Provider usage response"
        } else {
            null
        },
        rawUsageJson = usage.toString(2),
    ).withEstimatedCost(openCodePricingForModel(model))
}

private const val OPENCODE_HIGH_REASONING_EFFORT = "high"

private fun normalizeOpenCodeModelForRequest(model: String): String {
    val trimmed = model.trim()
    val withoutProviderPrefix = trimmed.removePrefix("opencode/")
    return withoutProviderPrefix.substringBefore(':').trim()
}

private fun openCodeReasoningEffortForModel(model: String): String? {
    val normalized = normalizeOpenCodeModelForRequest(model).lowercase(Locale.US)
    return when {
        normalized == "glm-5.2" || normalized == "glm-5-2" || normalized == "glm-5p2" -> OPENCODE_HIGH_REASONING_EFFORT
        normalized.startsWith("deepseek-v4") -> OPENCODE_HIGH_REASONING_EFFORT
        else -> null
    }
}

private fun openCodeReportModelLabel(model: String, reasoningEffort: String?): String {
    return if (reasoningEffort == null) {
        model
    } else {
        "$model (reasoning_effort=$reasoningEffort)"
    }
}

private fun openCodeGenerationModelLabel(model: String): String? {
    val requestModel = normalizeOpenCodeModelForRequest(model).takeIf { it.isNotBlank() } ?: return null
    return "opencode/${openCodeReportModelLabel(requestModel, openCodeReasoningEffortForModel(requestModel))}"
}

private const val OPENROUTER_HIGH_REASONING_EFFORT = "high"

private fun openRouterReasoningEffortForModel(model: String): String? {
    return when (model.trim().lowercase(Locale.US)) {
        "~openai/gpt-latest",
        "~google/gemini-pro-latest",
        "~anthropic/claude-opus-latest",
        -> OPENROUTER_HIGH_REASONING_EFFORT
        else -> null
    }
}

private fun openRouterReportModelLabel(model: String, reasoningEffort: String?): String {
    return if (reasoningEffort == null) {
        model
    } else {
        "$model (reasoning.effort=$reasoningEffort)"
    }
}

private fun openRouterGenerationModelLabel(model: String): String? {
    val trimmed = model.trim().takeIf { it.isNotBlank() } ?: return null
    return "openrouter/${openRouterReportModelLabel(trimmed, openRouterReasoningEffortForModel(trimmed))}"
}

private fun parseOpenRouterTokenUsage(usage: JSONObject?): CustomExerciseTokenUsage? {
    if (usage == null) return null
    val promptDetails = usage.optJSONObject("prompt_tokens_details")
    val completionDetails = usage.optJSONObject("completion_tokens_details")
    val costDetails = usage.optJSONObject("cost_details")
    val totalCost = usage.firstDoubleOrNull("cost", "total_cost", "total_cost_usd")
    val inputCost = usage.firstDoubleOrNull("prompt_cost", "input_cost", "input_cost_usd")
        ?: costDetails?.firstDoubleOrNull("upstream_inference_prompt_cost", "prompt_cost", "input_cost")
    val outputCost = usage.firstDoubleOrNull("completion_cost", "output_cost", "output_cost_usd")
        ?: costDetails?.firstDoubleOrNull("upstream_inference_completions_cost", "completion_cost", "output_cost")
    val cachedCost = usage.firstDoubleOrNull("cached_prompt_cost", "cached_input_cost", "cache_cost", "cache_read_cost")
        ?: costDetails?.firstDoubleOrNull("upstream_inference_cached_prompt_cost", "cached_prompt_cost", "cached_input_cost", "cache_read_cost")
    return CustomExerciseTokenUsage(
        inputTokens = usage.firstLongOrNull("prompt_tokens", "input_tokens"),
        outputTokens = usage.firstLongOrNull("completion_tokens", "output_tokens"),
        cachedInputTokens = promptDetails?.firstLongOrNull("cached_tokens", "cached_input_tokens"),
        reasoningTokens = completionDetails?.firstLongOrNull("reasoning_tokens"),
        totalTokens = usage.firstLongOrNull("total_tokens"),
        inputCostUsd = inputCost,
        outputCostUsd = outputCost,
        cachedInputCostUsd = cachedCost,
        totalCostUsd = totalCost,
        costSource = if (totalCost != null || inputCost != null || outputCost != null || cachedCost != null) {
            "OpenRouter usage response, USD credits"
        } else {
            null
        },
        rawUsageJson = usage.toString(2),
    )
}

private fun parseOpenRouterGenerationTokenUsage(
    data: JSONObject?,
    base: CustomExerciseTokenUsage?,
): CustomExerciseTokenUsage? {
    if (data == null) return base
    val inputTokens = data.firstLongOrNull("tokens_prompt", "native_tokens_prompt") ?: base?.inputTokens
    val outputTokens = data.firstLongOrNull("tokens_completion", "native_tokens_completion") ?: base?.outputTokens
    val reasoningTokens = data.firstLongOrNull("tokens_reasoning", "native_tokens_reasoning") ?: base?.reasoningTokens
    val totalTokens = data.firstLongOrNull("total_tokens", "native_tokens_total")
        ?: base?.totalTokens
        ?: listOfNotNull(inputTokens, outputTokens, reasoningTokens).takeIf { it.isNotEmpty() }?.sum()
    val totalCost = data.firstDoubleOrNull("total_cost", "cost", "usage")
    val rawUsageJson = JSONObject()
        .put("chat_completion_usage", base?.rawUsageJson ?: JSONObject.NULL)
        .put("openrouter_generation", data)
        .toString(2)
    return CustomExerciseTokenUsage(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cachedInputTokens = base?.cachedInputTokens,
        reasoningTokens = reasoningTokens,
        totalTokens = totalTokens,
        inputCostUsd = base?.inputCostUsd,
        outputCostUsd = base?.outputCostUsd,
        cachedInputCostUsd = base?.cachedInputCostUsd,
        totalCostUsd = totalCost ?: base?.totalCostUsd,
        costSource = if (totalCost != null) "OpenRouter generation stats response, USD credits" else base?.costSource,
        rawUsageJson = rawUsageJson,
    )
}

private fun CustomExerciseTokenUsage.withEstimatedCost(pricing: TokenPricing?): CustomExerciseTokenUsage {
    if (pricing == null) return this
    val input = inputTokens ?: 0L
    val output = outputTokens ?: 0L
    val cached = cachedInputTokens ?: 0L
    val billableInput = (input - cached).coerceAtLeast(0L)
    val inputCost = billableInput * pricing.inputPerMillionUsd / 1_000_000.0
    val outputCost = output * pricing.outputPerMillionUsd / 1_000_000.0
    val cachedCost = pricing.cachedReadPerMillionUsd?.let { cached * it / 1_000_000.0 }
    val needsEstimatedBreakdown = inputCostUsd == null || outputCostUsd == null || (cached > 0L && cachedInputCostUsd == null)
    val resolvedCostSource = when {
        totalCostUsd == null -> "Estimated from ${pricing.source}"
        needsEstimatedBreakdown -> "${costSource ?: "Provider response"}; missing component USD costs estimated from ${pricing.source}"
        else -> costSource
    }
    return copy(
        inputCostUsd = inputCostUsd ?: inputCost,
        outputCostUsd = outputCostUsd ?: outputCost,
        cachedInputCostUsd = cachedInputCostUsd ?: cachedCost,
        totalCostUsd = totalCostUsd ?: inputCost + outputCost + (cachedCost ?: 0.0),
        costSource = resolvedCostSource,
    )
}

private fun geminiPricingForModel(model: String): TokenPricing? {
    val normalized = model.lowercase()
    return when {
        normalized.contains("gemini-3.1-flash-lite") -> TokenPricing(
            inputPerMillionUsd = 0.25,
            outputPerMillionUsd = 1.50,
            cachedReadPerMillionUsd = 0.025,
            source = "Gemini API pricing page, paid tier per 1M text tokens",
        )
        normalized.contains("gemini-3-flash") -> TokenPricing(
            inputPerMillionUsd = 0.50,
            outputPerMillionUsd = 3.00,
            cachedReadPerMillionUsd = 0.05,
            source = "Gemini API pricing page, paid tier per 1M text tokens",
        )
        normalized.contains("gemini-3.1-pro") -> TokenPricing(
            inputPerMillionUsd = 2.00,
            outputPerMillionUsd = 12.00,
            cachedReadPerMillionUsd = 0.20,
            source = "Gemini API pricing page, paid tier per 1M text tokens up to 200K input tokens",
        )
        else -> null
    }
}

private fun openRouterPricingForModel(model: String?): TokenPricing? {
    return when (model?.lowercase()) {
        "z-ai/glm-5.2" -> TokenPricing(
            inputPerMillionUsd = 0.95,
            outputPerMillionUsd = 3.00,
            source = "OpenRouter Z.ai GLM 5.2 model page, per 1M tokens in USD",
        )
        "deepseek/deepseek-v4-pro" -> TokenPricing(
            inputPerMillionUsd = 0.435,
            outputPerMillionUsd = 0.87,
            source = "OpenRouter DeepSeek V4 Pro model catalog, per 1M tokens in USD",
        )
        "openrouter/owl-alpha" -> TokenPricing(
            inputPerMillionUsd = 0.0,
            outputPerMillionUsd = 0.0,
            source = "OpenRouter Owl Alpha model catalog, free model",
        )
        "~openai/gpt-latest" -> TokenPricing(
            inputPerMillionUsd = 5.0,
            outputPerMillionUsd = 30.0,
            cachedReadPerMillionUsd = 0.5,
            source = "OpenRouter GPT Latest alias model catalog, per 1M tokens in USD",
        )
        "~google/gemini-pro-latest" -> TokenPricing(
            inputPerMillionUsd = 2.0,
            outputPerMillionUsd = 12.0,
            cachedReadPerMillionUsd = 0.2,
            source = "OpenRouter Gemini Pro Latest alias model catalog, per 1M tokens in USD",
        )
        "~anthropic/claude-opus-latest" -> TokenPricing(
            inputPerMillionUsd = 5.0,
            outputPerMillionUsd = 25.0,
            cachedReadPerMillionUsd = 0.5,
            source = "OpenRouter Claude Opus Latest alias model catalog, per 1M tokens in USD",
        )
        else -> null
    }
}

private fun openCodePricingForModel(model: String?): TokenPricing? {
    return when (model?.let(::normalizeOpenCodeModelForRequest)?.lowercase(Locale.US)) {
        "deepseek-v4-flash" -> TokenPricing(
            inputPerMillionUsd = 0.14,
            outputPerMillionUsd = 0.28,
            cachedReadPerMillionUsd = 0.028,
            source = "OpenCode Zen pricing page, per 1M tokens",
        )
        "deepseek-v4-flash-free" -> TokenPricing(
            inputPerMillionUsd = 0.0,
            outputPerMillionUsd = 0.0,
            cachedReadPerMillionUsd = 0.0,
            source = "OpenCode Zen pricing page, free model",
        )
        "glm-5.2" -> TokenPricing(
            inputPerMillionUsd = 1.40,
            outputPerMillionUsd = 4.40,
            cachedReadPerMillionUsd = 0.26,
            source = "OpenCode Zen pricing page, per 1M tokens",
        )
        else -> null
    }
}

private fun prettyPrintCustomExerciseModelResponse(rawText: String): String {
    val jsonText = runCatching { extractCustomExerciseJsonObject(rawText) }.getOrNull() ?: return rawText.trim()
    return runCatching { JSONObject(jsonText).toString(2) }.getOrElse { rawText.trim() }
}

private fun JSONObject.firstLongOrNull(vararg keys: String): Long? {
    for (key in keys) {
        optNumberOrNull(key)?.let { return it.toLong() }
    }
    return null
}

private fun JSONObject.firstDoubleOrNull(vararg keys: String): Double? {
    for (key in keys) {
        optNumberOrNull(key)?.let { return it }
    }
    return null
}

private fun JSONObject.hasAny(vararg keys: String): Boolean = keys.any { has(it) && !isNull(it) }

private fun JSONObject.optNumberOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.trim().removePrefix("$").toDoubleOrNull()
        else -> null
    }
}

private fun formatUsdOrUnavailable(value: Double?): String {
    return value?.let { String.format(Locale.US, "USD $%.6f", it) } ?: "Not returned"
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

private fun extractCustomExerciseJsonObject(rawText: String): String {
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
    throw IllegalStateException("Could not parse custom exercise JSON response: $rawText")
}

internal fun buildCustomExerciseMetadataPrompt(
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
