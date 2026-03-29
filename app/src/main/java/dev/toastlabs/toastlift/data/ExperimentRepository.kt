package dev.toastlabs.toastlift.data

import org.json.JSONObject
import java.time.Instant

internal const val TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY = "today_completion_feedback_v1"
internal const val TODAY_COMPLETION_FEEDBACK_FLAG_NAME = "Today completion feedback"
internal const val COMPLETION_RECEIPT_EXPERIMENT_KEY = "completion_receipt_v1"
internal const val COMPLETION_RECEIPT_FLAG_NAME = "Completion receipt"

enum class TodayCompletionFeedbackVariant(val storageKey: String) {
    DONE_TODAY_BADGE("done_today_badge"),
    PROGRESS_METER("progress_meter");

    companion object {
        fun fromStorageKey(storageKey: String?): TodayCompletionFeedbackVariant? {
            return entries.firstOrNull { it.storageKey == storageKey }
        }
    }
}

enum class CompletionReceiptExperienceVariant(val storageKey: String) {
    MULTI_LAYER_RECEIPT("multi_layer_receipt");

    companion object {
        fun fromStorageKey(storageKey: String?): CompletionReceiptExperienceVariant? {
            return entries.firstOrNull { it.storageKey == storageKey }
        }
    }
}

internal fun assignTodayCompletionFeedbackVariant(seed: String): TodayCompletionFeedbackVariant {
    val normalizedSeed = seed.trim().ifBlank { TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY }
    val bucket = normalizedSeed.fold(17) { acc, char -> (acc * 31) + char.code }
    return if ((bucket and 1) == 0) {
        TodayCompletionFeedbackVariant.DONE_TODAY_BADGE
    } else {
        TodayCompletionFeedbackVariant.PROGRESS_METER
    }
}

internal fun todayCompletionFeedbackVariantName(variant: TodayCompletionFeedbackVariant): String = when (variant) {
    TodayCompletionFeedbackVariant.DONE_TODAY_BADGE -> "Variant A: Done today badge"
    TodayCompletionFeedbackVariant.PROGRESS_METER -> "Variant B: Animated progress meter"
}

internal fun todayCompletionFeedbackVariantDescription(variant: TodayCompletionFeedbackVariant): String = when (variant) {
    TodayCompletionFeedbackVariant.DONE_TODAY_BADGE ->
        "Shows a done today badge on the Today screen after the day's workout is completed."
    TodayCompletionFeedbackVariant.PROGRESS_METER ->
        "Shows an animated progress meter on the Today screen that fills when the day's workout is completed."
}

internal fun completionReceiptVariantName(variant: CompletionReceiptExperienceVariant): String = when (variant) {
    CompletionReceiptExperienceVariant.MULTI_LAYER_RECEIPT -> "Variant A: Multi-layer completion receipt"
}

internal fun completionReceiptVariantDescription(variant: CompletionReceiptExperienceVariant): String = when (variant) {
    CompletionReceiptExperienceVariant.MULTI_LAYER_RECEIPT ->
        "Shows the full-screen multi-layer completion receipt after finishing a workout."
}

internal fun buildTodayCompletionFeedbackAbFlags(variant: TodayCompletionFeedbackVariant): CompletedWorkoutAbFlags {
    return CompletedWorkoutAbFlags(
        completionFeedbackFlag = WorkoutAbFlagSnapshot(
            experimentKey = TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY,
            flagName = TODAY_COMPLETION_FEEDBACK_FLAG_NAME,
            flagDescription = todayCompletionFeedbackVariantDescription(variant),
            variantKey = variant.storageKey,
            variantName = todayCompletionFeedbackVariantName(variant),
            enabledStatus = "enabled",
        ),
    )
}

internal fun buildCompletionReceiptAbFlags(
    variant: CompletionReceiptExperienceVariant,
    legacyTodayVariant: TodayCompletionFeedbackVariant? = null,
): CompletedWorkoutAbFlags {
    return CompletedWorkoutAbFlags(
        completionFeedbackFlag = legacyTodayVariant?.let {
            WorkoutAbFlagSnapshot(
                experimentKey = TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY,
                flagName = TODAY_COMPLETION_FEEDBACK_FLAG_NAME,
                flagDescription = todayCompletionFeedbackVariantDescription(it),
                variantKey = it.storageKey,
                variantName = todayCompletionFeedbackVariantName(it),
                enabledStatus = "legacy",
            )
        },
        receiptExperienceFlag = WorkoutAbFlagSnapshot(
            experimentKey = COMPLETION_RECEIPT_EXPERIMENT_KEY,
            flagName = COMPLETION_RECEIPT_FLAG_NAME,
            flagDescription = completionReceiptVariantDescription(variant),
            variantKey = variant.storageKey,
            variantName = completionReceiptVariantName(variant),
            enabledStatus = "enabled",
        ),
    )
}

internal fun serializeCompletedWorkoutAbFlags(abFlags: CompletedWorkoutAbFlags?): String? {
    abFlags ?: return null
    val root = JSONObject()
    abFlags.completionFeedbackFlag?.let { root.put("completionFeedbackFlag", it.toJson()) }
    abFlags.receiptExperienceFlag?.let { root.put("receiptExperienceFlag", it.toJson()) }
    return root.takeIf { it.length() > 0 }?.toString()
}

internal fun deserializeCompletedWorkoutAbFlags(payload: String?): CompletedWorkoutAbFlags? {
    if (payload.isNullOrBlank()) return null
    return runCatching {
        val root = JSONObject(payload)
        val completionFeedbackFlag = root.optJSONObject("completionFeedbackFlag")?.toWorkoutAbFlagSnapshot()
        val receiptExperienceFlag = root.optJSONObject("receiptExperienceFlag")?.toWorkoutAbFlagSnapshot()
        CompletedWorkoutAbFlags(
            completionFeedbackFlag = completionFeedbackFlag,
            receiptExperienceFlag = receiptExperienceFlag,
        ).takeIf { completionFeedbackFlag != null || receiptExperienceFlag != null }
    }.getOrElse {
        deserializeLegacyCompletedWorkoutAbFlags(payload)
    }
}

private fun deserializeLegacyCompletedWorkoutAbFlags(payload: String): CompletedWorkoutAbFlags? {
    val flagDescription = extractLegacyJsonField(payload, "flagDescription") ?: return null
    val completionFeedbackFlag = WorkoutAbFlagSnapshot(
        experimentKey = extractLegacyJsonField(payload, "experimentKey") ?: TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY,
        flagName = extractLegacyJsonField(payload, "flagName") ?: TODAY_COMPLETION_FEEDBACK_FLAG_NAME,
        flagDescription = flagDescription,
        variantKey = extractLegacyJsonField(payload, "variantKey") ?: "unknown",
        variantName = extractLegacyJsonField(payload, "variantName") ?: extractLegacyJsonField(payload, "variantKey") ?: "unknown",
        enabledStatus = extractLegacyJsonField(payload, "enabledStatus") ?: "enabled",
    )
    return CompletedWorkoutAbFlags(completionFeedbackFlag = completionFeedbackFlag)
}

private fun WorkoutAbFlagSnapshot.toJson(): JSONObject {
    return JSONObject()
        .put("experimentKey", experimentKey)
        .put("flagName", flagName)
        .put("flagDescription", flagDescription)
        .put("variantKey", variantKey)
        .put("variantName", variantName)
        .put("enabledStatus", enabledStatus)
}

private fun JSONObject.toWorkoutAbFlagSnapshot(): WorkoutAbFlagSnapshot {
    return WorkoutAbFlagSnapshot(
        experimentKey = optString("experimentKey", ""),
        flagName = optString("flagName", ""),
        flagDescription = optString("flagDescription", ""),
        variantKey = optString("variantKey", ""),
        variantName = optString("variantName", optString("variantKey", "")),
        enabledStatus = optString("enabledStatus", "enabled"),
    )
}

private fun extractLegacyJsonField(payload: String, key: String): String? {
    val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
    val encoded = pattern.find(payload)?.groupValues?.getOrNull(1) ?: return null
    return unescapeLegacyJson(encoded)
}

private fun unescapeLegacyJson(value: String): String {
    val decoded = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char != '\\') {
            decoded.append(char)
            index += 1
            continue
        }
        if (index + 1 >= value.length) {
            decoded.append('\\')
            break
        }
        when (val escaped = value[index + 1]) {
            '\\' -> decoded.append('\\')
            '"' -> decoded.append('"')
            '/' -> decoded.append('/')
            'b' -> decoded.append('\b')
            'f' -> decoded.append('\u000C')
            'n' -> decoded.append('\n')
            'r' -> decoded.append('\r')
            't' -> decoded.append('\t')
            'u' -> {
                val hex = value.substring(index + 2, (index + 6).coerceAtMost(value.length))
                if (hex.length == 4) {
                    decoded.append(hex.toInt(16).toChar())
                    index += 4
                }
            }
            else -> decoded.append(escaped)
        }
        index += 2
    }
    return decoded.toString()
}

class ExperimentRepository(private val database: ToastLiftDatabase) {
    fun loadTodayCompletionFeedbackVariant(seed: String = Instant.now().toString()): TodayCompletionFeedbackVariant {
        val db = database.open()
        val existing = db.rawQuery(
            """
            SELECT variant_key
            FROM experiment_assignments
            WHERE experiment_key = ?
            """.trimIndent(),
            arrayOf(TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else TodayCompletionFeedbackVariant.fromStorageKey(cursor.getString(0))
        }
        if (existing != null) return existing

        val assigned = assignTodayCompletionFeedbackVariant(seed)
        db.execSQL(
            """
            INSERT OR REPLACE INTO experiment_assignments (experiment_key, variant_key, assigned_at_utc)
            VALUES (?, ?, ?)
            """.trimIndent(),
            arrayOf(
                TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY,
                assigned.storageKey,
                Instant.now().toString(),
            ),
        )
        return assigned
    }

    fun loadCompletionReceiptVariant(): CompletionReceiptExperienceVariant {
        val db = database.open()
        val existing = db.rawQuery(
            """
            SELECT variant_key
            FROM experiment_assignments
            WHERE experiment_key = ?
            """.trimIndent(),
            arrayOf(COMPLETION_RECEIPT_EXPERIMENT_KEY),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else CompletionReceiptExperienceVariant.fromStorageKey(cursor.getString(0))
        }
        if (existing != null) return existing

        val assigned = CompletionReceiptExperienceVariant.MULTI_LAYER_RECEIPT
        db.execSQL(
            """
            INSERT OR REPLACE INTO experiment_assignments (experiment_key, variant_key, assigned_at_utc)
            VALUES (?, ?, ?)
            """.trimIndent(),
            arrayOf(
                COMPLETION_RECEIPT_EXPERIMENT_KEY,
                assigned.storageKey,
                Instant.now().toString(),
            ),
        )
        return assigned
    }
}
