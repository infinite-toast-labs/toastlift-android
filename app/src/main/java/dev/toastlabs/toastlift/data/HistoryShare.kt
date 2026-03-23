package dev.toastlabs.toastlift.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class HistoryShareFormat(
    val optionLabel: String,
    val chooserTitle: String,
) {
    FormattedText(
        optionLabel = "Formatted text",
        chooserTitle = "Share workout as formatted text",
    ),
    Json(
        optionLabel = "Pure JSON",
        chooserTitle = "Share workout as pure JSON",
    ),
}

data class HistoryWorkoutShareSet(
    val setNumber: Int,
    val targetReps: String,
    val recommendedReps: Int?,
    val recommendedWeight: Double?,
    val actualReps: Int?,
    val weight: Double?,
    val isCompleted: Boolean,
    val recommendationSource: RecommendationSource,
    val recommendationConfidence: Double?,
)

data class HistoryWorkoutShareExercise(
    val exerciseId: Long,
    val name: String,
    val targetReps: String,
    val loggedSets: Int,
    val totalSets: Int,
    val totalVolume: Double,
    val bestWeight: Double,
    val bestReps: Int,
    val lastSetRepsInReserve: Int?,
    val lastSetRpe: Double?,
    val sets: List<HistoryWorkoutShareSet>,
)

data class HistoryWorkoutShareDetail(
    val id: Long,
    val title: String,
    val origin: String,
    val locationModeId: Long,
    val startedAtUtc: String,
    val completedAtUtc: String,
    val durationSeconds: Int,
    val totalVolume: Double,
    val exerciseCount: Int,
    val exercises: List<HistoryWorkoutShareExercise>,
    val abFlags: CompletedWorkoutAbFlags? = null,
)

data class PendingWorkoutShare(
    val requestId: String,
    val chooserTitle: String,
    val subject: String,
    val mimeType: String,
    val contents: String,
)

fun HistoryWorkoutShareDetail.toPendingWorkoutShare(format: HistoryShareFormat): PendingWorkoutShare {
    return PendingWorkoutShare(
        requestId = Instant.now().toString(),
        chooserTitle = format.chooserTitle,
        subject = title,
        mimeType = "text/plain",
        contents = when (format) {
            HistoryShareFormat.FormattedText -> toFormattedShareText()
            HistoryShareFormat.Json -> toShareJson().toString(2)
        },
    )
}

private fun HistoryWorkoutShareDetail.toFormattedShareText(): String {
    return buildString {
        appendLine(title)
        appendLine("Completed: ${completedAtUtc.asShareTimestamp()}")
        appendLine("Origin: ${origin.replaceFirstChar { it.uppercase() }}")
        appendLine("Duration: ${durationSeconds.asShareDuration()}")
        appendLine("Volume: ${totalVolume.asShareWeight()}")
        appendLine("Exercises: $exerciseCount")

        exercises.forEachIndexed { index, exercise ->
            appendLine()
            appendLine("${index + 1}. ${exercise.name}")
            appendLine("Sets logged: ${exercise.loggedSets}/${exercise.totalSets}")
            if (exercise.targetReps.isNotBlank()) {
                appendLine("Target reps: ${exercise.targetReps}")
            }
            if (exercise.bestWeight > 0.0 || exercise.bestReps > 0) {
                appendLine("Best set: ${exercise.bestReps} reps @ ${exercise.bestWeight.asShareWeight()}")
            }
            exercise.sets.forEach { set ->
                appendLine("Set ${set.setNumber}: ${set.asShareLine()}")
            }
            exercise.lastSetRepsInReserve?.let { appendLine("Last set RIR: $it") }
            exercise.lastSetRpe?.let { appendLine("Last set RPE: ${it.asShareNumber()}") }
        }
    }.trim()
}

private fun HistoryWorkoutShareSet.asShareLine(): String {
    val targetSegment = targetReps.takeIf { it.isNotBlank() }?.let { "target $it" }
    val recommendationSegment = buildList {
        recommendedReps?.let { add("$it reps") }
        recommendedWeight?.let { add(it.asShareWeight()) }
    }.takeIf { it.isNotEmpty() }?.joinToString(" @ ")

    return if (isCompleted) {
        buildList {
            add(
                when {
                    weight != null && actualReps != null -> "${weight.asShareWeight()} x $actualReps"
                    actualReps != null -> "$actualReps reps"
                    weight != null -> weight.asShareWeight()
                    else -> "completed"
                },
            )
            targetSegment?.let { add("($it)") }
        }.joinToString(" ")
    } else {
        buildList {
            add("not completed")
            val details = buildList {
                targetSegment?.let { add(it) }
                recommendationSegment?.let { add("rec $it") }
            }
            if (details.isNotEmpty()) {
                add("(${details.joinToString(", ")})")
            }
        }.joinToString(" ")
    }
}

private fun HistoryWorkoutShareDetail.toShareJson(): JSONObject {
    return JSONObject()
        .put("performed_workout_id", id)
        .put("title", title)
        .put("origin_type", origin)
        .put("location_mode_id", locationModeId)
        .put("started_at_utc", startedAtUtc)
        .put("completed_at_utc", completedAtUtc)
        .put("actual_duration_seconds", durationSeconds)
        .put("total_volume", totalVolume)
        .put("exercise_count", exerciseCount)
        .putNullable("abFlags", abFlags?.toJson())
        .put(
            "exercises",
            JSONArray().apply {
                exercises.forEach { exercise ->
                    put(exercise.toJson())
                }
            },
        )
}

private fun HistoryWorkoutShareExercise.toJson(): JSONObject {
    return JSONObject()
        .put("exercise_id", exerciseId)
        .put("exercise_name", name)
        .put("target_reps", targetReps)
        .put("logged_sets", loggedSets)
        .put("total_sets", totalSets)
        .put("total_volume", totalVolume)
        .put("best_weight", bestWeight)
        .put("best_reps", bestReps)
        .putNullable("last_set_reps_in_reserve", lastSetRepsInReserve)
        .putNullable("last_set_rpe", lastSetRpe)
        .put(
            "sets",
            JSONArray().apply {
                sets.forEach { set ->
                    put(set.toJson())
                }
            },
        )
}

private fun HistoryWorkoutShareSet.toJson(): JSONObject {
    return JSONObject()
        .put("set_number", setNumber)
        .put("target_reps", targetReps)
        .putNullable("recommended_reps", recommendedReps)
        .putNullable("recommended_weight_value", recommendedWeight)
        .putNullable("actual_reps", actualReps)
        .putNullable("weight_value", weight)
        .put("is_completed", isCompleted)
        .put("recommendation_source", recommendationSource.name)
        .putNullable("recommendation_confidence", recommendationConfidence)
}

private fun CompletedWorkoutAbFlags.toJson(): JSONObject {
    return JSONObject().putNullable(
        "completionFeedbackFlag",
        completionFeedbackFlag?.let { flag ->
            JSONObject()
                .put("experimentKey", flag.experimentKey)
                .put("flagName", flag.flagName)
                .put("flagDescription", flag.flagDescription)
                .put("variantKey", flag.variantKey)
                .put("variantName", flag.variantName)
                .put("enabledStatus", flag.enabledStatus)
        },
    )
}

private fun String.asShareTimestamp(): String = replace("T", " ").removeSuffix("Z") + " UTC"

private fun Int.asShareDuration(): String {
    val totalMinutes = this / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes} min"
    }
}

private fun Double.asShareWeight(): String = "${formatRecommendedWeight(this)} lb"

private fun Double.asShareNumber(): String = if (this % 1.0 == 0.0) {
    toInt().toString()
} else {
    "%.1f".format(this)
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
    put(key, value ?: JSONObject.NULL)
    return this
}
