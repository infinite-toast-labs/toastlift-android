package dev.toastlabs.toastlift.data

import java.time.Instant

internal const val TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY = "today_completion_feedback_v1"
internal const val TODAY_COMPLETION_FEEDBACK_FLAG_NAME = "Today completion feedback"

enum class TodayCompletionFeedbackVariant(val storageKey: String) {
    DONE_TODAY_BADGE("done_today_badge"),
    PROGRESS_METER("progress_meter");

    companion object {
        fun fromStorageKey(storageKey: String?): TodayCompletionFeedbackVariant? {
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

internal fun serializeCompletedWorkoutAbFlags(abFlags: CompletedWorkoutAbFlags?): String? {
    val completionFeedbackFlag = abFlags?.completionFeedbackFlag ?: return null
    return buildString {
        append("{\"completionFeedbackFlag\":{")
        appendJsonField("experimentKey", completionFeedbackFlag.experimentKey)
        append(',')
        appendJsonField("flagName", completionFeedbackFlag.flagName)
        append(',')
        appendJsonField("flagDescription", completionFeedbackFlag.flagDescription)
        append(',')
        appendJsonField("variantKey", completionFeedbackFlag.variantKey)
        append(',')
        appendJsonField("variantName", completionFeedbackFlag.variantName)
        append(',')
        appendJsonField("enabledStatus", completionFeedbackFlag.enabledStatus)
        append("}}")
    }
}

internal fun deserializeCompletedWorkoutAbFlags(payload: String?): CompletedWorkoutAbFlags? {
    if (payload.isNullOrBlank()) return null
    return runCatching {
        val flagDescription = extractJsonField(payload, "flagDescription") ?: return@runCatching null
        val completionFeedbackFlag = WorkoutAbFlagSnapshot(
            experimentKey = extractJsonField(payload, "experimentKey") ?: TODAY_COMPLETION_FEEDBACK_EXPERIMENT_KEY,
            flagName = extractJsonField(payload, "flagName") ?: TODAY_COMPLETION_FEEDBACK_FLAG_NAME,
            flagDescription = flagDescription,
            variantKey = extractJsonField(payload, "variantKey") ?: "unknown",
            variantName = extractJsonField(payload, "variantName") ?: extractJsonField(payload, "variantKey") ?: "unknown",
            enabledStatus = extractJsonField(payload, "enabledStatus") ?: "enabled",
        )
        CompletedWorkoutAbFlags(completionFeedbackFlag = completionFeedbackFlag)
            .takeIf { it.completionFeedbackFlag != null }
    }.getOrNull()
}

private fun StringBuilder.appendJsonField(key: String, value: String) {
    append('"')
    append(key)
    append("\":\"")
    append(escapeJson(value))
    append('"')
}

private fun escapeJson(value: String): String {
    return buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}

private fun extractJsonField(payload: String, key: String): String? {
    val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
    val encoded = pattern.find(payload)?.groupValues?.getOrNull(1) ?: return null
    return unescapeJson(encoded)
}

private fun unescapeJson(value: String): String {
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
}
