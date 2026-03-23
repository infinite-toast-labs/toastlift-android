package com.fitlib.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.fitlib.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

class CustomExerciseRepository(
    context: Context,
    private val database: FitLibDatabase,
    private val catalogRepository: CatalogRepository,
    private val metadataGenerator: ExerciseMetadataGenerator = GeminiExerciseMetadataGenerator(),
) {
    private val appContext = context.applicationContext
    private val snapshotFile = File(appContext.filesDir, "custom_exercises_snapshot.json")

    fun newDraft(name: String = ""): CustomExerciseDraft {
        val taxonomy = loadTaxonomy()
        val trimmed = name.trim()
        return defaultDraft(taxonomy = taxonomy, name = trimmed).copy(
            existingMatches = loadMatches(trimmed),
        )
    }

    fun updateDraftName(draft: CustomExerciseDraft, name: String): CustomExerciseDraft {
        val trimmed = name.trimStart()
        return draft.copy(
            name = trimmed,
            existingMatches = loadMatches(trimmed),
            errorMessage = null,
        )
    }

    fun loadExistingMatches(name: String): List<ExerciseSummary> = loadMatches(name.trimStart())

    fun generateDetails(draft: CustomExerciseDraft): CustomExerciseDraft {
        val name = draft.name.trim()
        require(name.isNotBlank()) { "Enter an exercise name first." }
        val taxonomy = draft.taxonomy.takeIf { it.difficultyLevels.isNotEmpty() } ?: loadTaxonomy()
        val nearby = loadMatches(name)
        val generated = metadataGenerator.generate(name, taxonomy, nearby)

        return draft.copy(
            name = generated.name.ifBlank { name },
            difficultyLevel = canonicalOrFallback(generated.difficultyLevel, taxonomy.difficultyLevels, taxonomy.difficultyLevels.pick("Intermediate")),
            bodyRegion = canonicalOrFallback(generated.bodyRegion, taxonomy.bodyRegions, taxonomy.bodyRegions.pick("Upper Body")),
            targetMuscleGroup = canonicalOrFallback(generated.targetMuscleGroup, taxonomy.targetMuscles, taxonomy.targetMuscles.pick("Chest")),
            primeMoverMuscle = generated.primeMoverMuscle.orEmpty(),
            secondaryMuscle = generated.secondaryMuscle.orEmpty(),
            tertiaryMuscle = generated.tertiaryMuscle.orEmpty(),
            primaryEquipment = canonicalOrFallback(generated.primaryEquipment, taxonomy.equipmentOptions, taxonomy.equipmentOptions.pick("Machine")),
            primaryItemCount = (generated.primaryItemCount ?: 1).toString(),
            secondaryEquipment = canonicalOrBlank(generated.secondaryEquipment, taxonomy.equipmentOptions),
            secondaryItemCount = generated.secondaryItemCount?.toString().orEmpty(),
            posture = canonicalOrFallback(generated.posture, taxonomy.postures, taxonomy.postures.pick("Seated")),
            armUsage = canonicalOrFallback(generated.armUsage, taxonomy.armUsageOptions, taxonomy.armUsageOptions.pick("Double Arm")),
            armPattern = canonicalOrFallback(generated.armPattern, taxonomy.armPatternOptions, taxonomy.armPatternOptions.pick("Continuous")),
            grip = softCanonicalOrFallback(generated.grip, taxonomy.gripOptions, taxonomy.gripOptions.pick("Neutral")),
            loadPositionEnding = softCanonicalOrFallback(generated.loadPositionEnding, taxonomy.loadPositionOptions, taxonomy.loadPositionOptions.pick("Other")),
            legPattern = canonicalOrFallback(generated.legPattern, taxonomy.legPatternOptions, taxonomy.legPatternOptions.pick("Continuous")),
            footElevation = softCanonicalOrFallback(generated.footElevation, taxonomy.footElevationOptions, taxonomy.footElevationOptions.pick("No Elevation")),
            combinationType = canonicalOrFallback(generated.combinationType, taxonomy.combinationTypeOptions, taxonomy.combinationTypeOptions.pick("Single Exercise")),
            forceType = canonicalOrFallback(generated.forceType, taxonomy.forceTypeOptions, taxonomy.forceTypeOptions.pick("Push")),
            mechanics = canonicalOrFallback(generated.mechanics, taxonomy.mechanicsOptions, taxonomy.mechanicsOptions.pick("Compound")),
            laterality = canonicalOrFallback(generated.laterality, taxonomy.lateralityOptions, taxonomy.lateralityOptions.pick("Bilateral")),
            classification = canonicalOrFallback(generated.classification, taxonomy.classificationOptions, taxonomy.classificationOptions.pick("Bodybuilding")),
            movementPatternsInput = canonicalizeMany(
                generated.movementPatterns,
                taxonomy.movementPatternOptions,
                fallback = listOf(taxonomy.movementPatternOptions.pick("Other")),
            ).joinToString(", "),
            planesOfMotionInput = canonicalizeMany(
                generated.planesOfMotion,
                taxonomy.planeOfMotionOptions,
                fallback = listOf(taxonomy.planeOfMotionOptions.pick("Sagittal Plane")),
            ).joinToString(", "),
            shortDemoLabel = generated.shortDemoLabel.orEmpty(),
            shortDemoUrl = generated.shortDemoUrl.orEmpty(),
            inDepthLabel = generated.inDepthLabel.orEmpty(),
            inDepthUrl = generated.inDepthUrl.orEmpty(),
            synonymsInput = generated.synonyms.distinct().joinToString(", "),
            taxonomy = taxonomy,
            existingMatches = nearby,
            generatedWithAi = true,
            errorMessage = null,
        )
    }

    fun saveCustomExercise(draft: CustomExerciseDraft): ExerciseSummary {
        val taxonomy = draft.taxonomy.takeIf { it.difficultyLevels.isNotEmpty() } ?: loadTaxonomy()
        val prepared = prepareForSave(draft, taxonomy)
        val existing = findExistingByNormalizedName(prepared.name)
        if (existing != null) return existing

        val db = database.open()
        val ids = allocateIdentity(db, prepared.name)
        val now = Instant.now().toString()

        db.beginTransaction()
        try {
            db.execSQL(
                """
                INSERT INTO exercises (
                    exercise_id, source_row, slug, name, short_demo_label, short_demo_url, in_depth_label, in_depth_url,
                    difficulty_level, target_muscle_group, prime_mover_muscle, secondary_muscle, tertiary_muscle,
                    primary_equipment, primary_item_count, secondary_equipment, secondary_item_count, posture, arm_usage,
                    arm_pattern, grip, load_position_ending, leg_pattern, foot_elevation, combination_type,
                    movement_pattern_1, movement_pattern_2, movement_pattern_3,
                    plane_of_motion_1, plane_of_motion_2, plane_of_motion_3,
                    body_region, force_type, mechanics, laterality, primary_exercise_classification,
                    equipment_slot_count, muscle_slot_count, has_short_demo, has_in_depth_explanation,
                    is_post_install_llm_generated, created_at_utc, updated_at_utc, generation_model, generation_prompt_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    ids.exerciseId,
                    ids.sourceRow,
                    ids.slug,
                    prepared.name,
                    prepared.shortDemoLabel,
                    prepared.shortDemoUrl,
                    prepared.inDepthLabel,
                    prepared.inDepthUrl,
                    prepared.difficultyLevel,
                    prepared.targetMuscleGroup,
                    prepared.primeMoverMuscle.nullIfBlank(),
                    prepared.secondaryMuscle.nullIfBlank(),
                    prepared.tertiaryMuscle.nullIfBlank(),
                    prepared.primaryEquipment,
                    prepared.primaryItemCount,
                    prepared.secondaryEquipment.nullIfBlank(),
                    prepared.secondaryItemCount,
                    prepared.posture,
                    prepared.armUsage,
                    prepared.armPattern,
                    prepared.grip,
                    prepared.loadPositionEnding,
                    prepared.legPattern,
                    prepared.footElevation,
                    prepared.combinationType,
                    prepared.movementPatterns.getOrNull(0),
                    prepared.movementPatterns.getOrNull(1),
                    prepared.movementPatterns.getOrNull(2),
                    prepared.planesOfMotion.getOrNull(0),
                    prepared.planesOfMotion.getOrNull(1),
                    prepared.planesOfMotion.getOrNull(2),
                    prepared.bodyRegion,
                    prepared.forceType,
                    prepared.mechanics.nullIfBlank(),
                    prepared.laterality,
                    prepared.classification,
                    prepared.equipmentSlotCount,
                    prepared.muscleSlotCount,
                    if (prepared.shortDemoUrl.isNotBlank()) 1 else 0,
                    if (prepared.inDepthUrl.isNotBlank()) 1 else 0,
                    1,
                    now,
                    now,
                    prepared.generationModel,
                    prepared.generationPromptVersion,
                ),
            )
            prepared.muscles.forEachIndexed { index, muscle ->
                db.execSQL(
                    """
                    INSERT INTO exercise_muscles (exercise_id, sequence_no, muscle_role, muscle_name)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(ids.exerciseId, index + 1, muscle.first, muscle.second),
                )
            }
            prepared.equipment.forEachIndexed { index, equipment ->
                db.execSQL(
                    """
                    INSERT INTO exercise_equipment (exercise_id, sequence_no, equipment_role, equipment_name, item_count)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(ids.exerciseId, index + 1, equipment.first, equipment.second, equipment.third),
                )
            }
            prepared.movementPatterns.forEachIndexed { index, pattern ->
                db.execSQL(
                    """
                    INSERT INTO exercise_movement_patterns (exercise_id, sequence_no, movement_pattern)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                    arrayOf(ids.exerciseId, index + 1, pattern),
                )
            }
            prepared.planesOfMotion.forEachIndexed { index, plane ->
                db.execSQL(
                    """
                    INSERT INTO exercise_planes_of_motion (exercise_id, sequence_no, plane_of_motion)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                    arrayOf(ids.exerciseId, index + 1, plane),
                )
            }
            prepared.synonyms.forEach { synonym ->
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO exercise_synonyms
                        (exercise_id, synonym_name, synonym_name_normalized, synonym_type, source, confidence_score, created_at_utc)
                    VALUES (?, ?, ?, 'custom', 'llm_generated_post_install', 0.9, ?)
                    """.trimIndent(),
                    arrayOf(ids.exerciseId, synonym, normalizeText(synonym), now),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        writeSnapshotFromDatabase()

        return ExerciseSummary(
            id = ids.exerciseId,
            name = prepared.name,
            difficulty = prepared.difficultyLevel,
            bodyRegion = prepared.bodyRegion,
            targetMuscleGroup = prepared.targetMuscleGroup,
            equipment = prepared.primaryEquipment,
            secondaryEquipment = prepared.secondaryEquipment.nullIfBlank(),
            mechanics = prepared.mechanics.nullIfBlank(),
            favorite = false,
            recommendationBias = RecommendationBias.Neutral,
        )
    }

    fun exportCustomExercisesJson(): JSONArray {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT
                exercise_id, source_row, slug, name, short_demo_label, short_demo_url, in_depth_label, in_depth_url,
                difficulty_level, target_muscle_group, prime_mover_muscle, secondary_muscle, tertiary_muscle,
                primary_equipment, primary_item_count, secondary_equipment, secondary_item_count, posture, arm_usage,
                arm_pattern, grip, load_position_ending, leg_pattern, foot_elevation, combination_type,
                movement_pattern_1, movement_pattern_2, movement_pattern_3,
                plane_of_motion_1, plane_of_motion_2, plane_of_motion_3,
                body_region, force_type, mechanics, laterality, primary_exercise_classification,
                equipment_slot_count, muscle_slot_count, has_short_demo, has_in_depth_explanation,
                is_post_install_llm_generated, created_at_utc, updated_at_utc, generation_model, generation_prompt_version
            FROM exercises
            WHERE is_post_install_llm_generated = 1
            ORDER BY created_at_utc DESC, exercise_id DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val exerciseId = cursor.getLong(0)
                    put(
                        JSONObject()
                            .put("exercise_id", exerciseId)
                            .put("source_row", cursor.getLong(1))
                            .put("slug", cursor.getString(2))
                            .put("name", cursor.getString(3))
                            .put("short_demo_label", cursor.getStringOrNull(4))
                            .put("short_demo_url", cursor.getStringOrNull(5))
                            .put("in_depth_label", cursor.getStringOrNull(6))
                            .put("in_depth_url", cursor.getStringOrNull(7))
                            .put("difficulty_level", cursor.getString(8))
                            .put("target_muscle_group", cursor.getString(9))
                            .put("prime_mover_muscle", cursor.getStringOrNull(10))
                            .put("secondary_muscle", cursor.getStringOrNull(11))
                            .put("tertiary_muscle", cursor.getStringOrNull(12))
                            .put("primary_equipment", cursor.getString(13))
                            .put("primary_item_count", if (cursor.isNull(14)) JSONObject.NULL else cursor.getInt(14))
                            .put("secondary_equipment", cursor.getStringOrNull(15))
                            .put("secondary_item_count", if (cursor.isNull(16)) JSONObject.NULL else cursor.getInt(16))
                            .put("posture", cursor.getString(17))
                            .put("arm_usage", cursor.getString(18))
                            .put("arm_pattern", cursor.getString(19))
                            .put("grip", cursor.getString(20))
                            .put("load_position_ending", cursor.getString(21))
                            .put("leg_pattern", cursor.getString(22))
                            .put("foot_elevation", cursor.getString(23))
                            .put("combination_type", cursor.getString(24))
                            .put("movement_pattern_1", cursor.getStringOrNull(25))
                            .put("movement_pattern_2", cursor.getStringOrNull(26))
                            .put("movement_pattern_3", cursor.getStringOrNull(27))
                            .put("plane_of_motion_1", cursor.getStringOrNull(28))
                            .put("plane_of_motion_2", cursor.getStringOrNull(29))
                            .put("plane_of_motion_3", cursor.getStringOrNull(30))
                            .put("body_region", cursor.getString(31))
                            .put("force_type", cursor.getString(32))
                            .put("mechanics", cursor.getStringOrNull(33))
                            .put("laterality", cursor.getString(34))
                            .put("primary_exercise_classification", cursor.getString(35))
                            .put("equipment_slot_count", cursor.getInt(36))
                            .put("muscle_slot_count", cursor.getInt(37))
                            .put("has_short_demo", cursor.getInt(38) == 1)
                            .put("has_in_depth_explanation", cursor.getInt(39) == 1)
                            .put("is_post_install_llm_generated", cursor.getInt(40) == 1)
                            .put("created_at_utc", cursor.getStringOrNull(41))
                            .put("updated_at_utc", cursor.getStringOrNull(42))
                            .put("generation_model", cursor.getStringOrNull(43))
                            .put("generation_prompt_version", cursor.getStringOrNull(44))
                            .put("muscles", exportChildRows(db, "exercise_muscles", "muscle_name", exerciseId, "muscle_role"))
                            .put("equipment", exportEquipmentRows(db, exerciseId))
                            .put("movement_patterns", exportChildRows(db, "exercise_movement_patterns", "movement_pattern", exerciseId))
                            .put("planes_of_motion", exportChildRows(db, "exercise_planes_of_motion", "plane_of_motion", exerciseId))
                            .put("synonyms", exportSynonyms(db, exerciseId)),
                    )
                }
            }
        }
    }

    fun restoreSnapshotIfNeeded(): Boolean {
        if (!snapshotFile.exists()) return false
        val db = database.open()
        val existingCount = db.rawQuery(
            "SELECT COUNT(*) FROM exercises WHERE is_post_install_llm_generated = 1",
            null,
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
        if (existingCount > 0L) return false

        val snapshot = snapshotFile.readText(Charsets.UTF_8).trim()
        if (snapshot.isBlank()) return false
        val payload = JSONArray(snapshot)

        db.beginTransaction()
        try {
            for (index in 0 until payload.length()) {
                insertFromSnapshot(db, payload.getJSONObject(index))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return payload.length() > 0
    }

    fun deleteAllCustomExercisesAndSnapshot() {
        val db = database.open()
        db.execSQL("DELETE FROM exercises WHERE is_post_install_llm_generated = 1")
        if (snapshotFile.exists()) snapshotFile.delete()
    }

    fun clearSnapshot() {
        if (snapshotFile.exists()) snapshotFile.delete()
    }

    private fun writeSnapshotFromDatabase() {
        snapshotFile.parentFile?.mkdirs()
        snapshotFile.writeText(exportCustomExercisesJson().toString(2), Charsets.UTF_8)
    }

    private fun loadTaxonomy(): CustomExerciseTaxonomy {
        val db = database.open()
        return CustomExerciseTaxonomy(
            difficultyLevels = loadDistinct(db, "exercises", "difficulty_level"),
            bodyRegions = loadDistinct(db, "exercises", "body_region"),
            targetMuscles = loadDistinct(db, "exercises", "target_muscle_group"),
            primeMovers = loadDistinct(db, "exercises", "prime_mover_muscle"),
            equipmentOptions = catalogRepository.loadEquipmentOptions(),
            postures = loadDistinct(db, "exercises", "posture"),
            armUsageOptions = loadDistinct(db, "exercises", "arm_usage"),
            armPatternOptions = loadDistinct(db, "exercises", "arm_pattern"),
            gripOptions = loadDistinct(db, "exercises", "grip"),
            loadPositionOptions = loadDistinct(db, "exercises", "load_position_ending"),
            legPatternOptions = loadDistinct(db, "exercises", "leg_pattern"),
            footElevationOptions = loadDistinct(db, "exercises", "foot_elevation"),
            combinationTypeOptions = loadDistinct(db, "exercises", "combination_type"),
            forceTypeOptions = loadDistinct(db, "exercises", "force_type"),
            mechanicsOptions = loadDistinct(db, "exercises", "mechanics"),
            lateralityOptions = loadDistinct(db, "exercises", "laterality"),
            classificationOptions = loadDistinct(db, "exercises", "primary_exercise_classification"),
            movementPatternOptions = loadDistinct(db, "exercise_movement_patterns", "movement_pattern"),
            planeOfMotionOptions = loadDistinct(db, "exercise_planes_of_motion", "plane_of_motion"),
        )
    }

    private fun loadMatches(query: String): List<ExerciseSummary> {
        if (query.isBlank()) return emptyList()
        return catalogRepository.searchExercises(query = query, limit = 5)
    }

    private fun loadDistinct(db: SQLiteDatabase, table: String, column: String): List<String> =
        db.rawQuery(
            """
            SELECT DISTINCT $column
            FROM $table
            WHERE $column IS NOT NULL AND trim($column) != ''
            ORDER BY $column
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun defaultDraft(taxonomy: CustomExerciseTaxonomy, name: String): CustomExerciseDraft =
        CustomExerciseDraft(
            name = name,
            difficultyLevel = taxonomy.difficultyLevels.pick("Intermediate"),
            bodyRegion = taxonomy.bodyRegions.pick("Upper Body"),
            targetMuscleGroup = taxonomy.targetMuscles.pick("Chest"),
            primaryEquipment = taxonomy.equipmentOptions.pick("Machine"),
            posture = taxonomy.postures.pick("Seated"),
            armUsage = taxonomy.armUsageOptions.pick("Double Arm"),
            armPattern = taxonomy.armPatternOptions.pick("Continuous"),
            grip = taxonomy.gripOptions.pick("Neutral"),
            loadPositionEnding = taxonomy.loadPositionOptions.pick("Other"),
            legPattern = taxonomy.legPatternOptions.pick("Continuous"),
            footElevation = taxonomy.footElevationOptions.pick("No Elevation"),
            combinationType = taxonomy.combinationTypeOptions.pick("Single Exercise"),
            forceType = taxonomy.forceTypeOptions.pick("Push"),
            mechanics = taxonomy.mechanicsOptions.pick("Compound"),
            laterality = taxonomy.lateralityOptions.pick("Bilateral"),
            classification = taxonomy.classificationOptions.pick("Bodybuilding"),
            movementPatternsInput = taxonomy.movementPatternOptions.firstOrNull { it.equals("Other", ignoreCase = true) }.orEmpty(),
            planesOfMotionInput = taxonomy.planeOfMotionOptions.pick("Sagittal Plane"),
            taxonomy = taxonomy,
        )

    private fun prepareForSave(
        draft: CustomExerciseDraft,
        taxonomy: CustomExerciseTaxonomy,
    ): PreparedDraft {
        val name = draft.name.trim()
        require(name.isNotBlank()) { "Exercise name is required." }
        val movementPatterns = canonicalizeMany(
            splitValues(draft.movementPatternsInput),
            taxonomy.movementPatternOptions,
            fallback = listOf(taxonomy.movementPatternOptions.pick("Other")),
        ).take(3)
        val planes = canonicalizeMany(
            splitValues(draft.planesOfMotionInput),
            taxonomy.planeOfMotionOptions,
            fallback = listOf(taxonomy.planeOfMotionOptions.pick("Sagittal Plane")),
        ).take(3)
        val primaryEquipment = canonicalOrFallback(draft.primaryEquipment, taxonomy.equipmentOptions, taxonomy.equipmentOptions.pick("Machine"))
        val secondaryEquipment = canonicalOrBlank(draft.secondaryEquipment, taxonomy.equipmentOptions)
        val muscles = buildList {
            draft.primeMoverMuscle.trim().takeIf { it.isNotBlank() }?.let { add("prime_mover" to it) }
            draft.secondaryMuscle.trim().takeIf { it.isNotBlank() }?.let { add("secondary" to it) }
            draft.tertiaryMuscle.trim().takeIf { it.isNotBlank() }?.let { add("tertiary" to it) }
        }.take(3)
        return PreparedDraft(
            name = name,
            difficultyLevel = canonicalOrFallback(draft.difficultyLevel, taxonomy.difficultyLevels, taxonomy.difficultyLevels.pick("Intermediate")),
            bodyRegion = canonicalOrFallback(draft.bodyRegion, taxonomy.bodyRegions, taxonomy.bodyRegions.pick("Upper Body")),
            targetMuscleGroup = canonicalOrFallback(draft.targetMuscleGroup, taxonomy.targetMuscles, taxonomy.targetMuscles.pick("Chest")),
            primeMoverMuscle = draft.primeMoverMuscle.trim(),
            secondaryMuscle = draft.secondaryMuscle.trim(),
            tertiaryMuscle = draft.tertiaryMuscle.trim(),
            primaryEquipment = primaryEquipment,
            primaryItemCount = draft.primaryItemCount.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
            secondaryEquipment = secondaryEquipment,
            secondaryItemCount = draft.secondaryItemCount.trim().toIntOrNull()?.takeIf { secondaryEquipment.isNotBlank() && it > 0 },
            posture = softCanonicalOrFallback(draft.posture, taxonomy.postures, taxonomy.postures.pick("Seated")),
            armUsage = canonicalOrFallback(draft.armUsage, taxonomy.armUsageOptions, taxonomy.armUsageOptions.pick("Double Arm")),
            armPattern = canonicalOrFallback(draft.armPattern, taxonomy.armPatternOptions, taxonomy.armPatternOptions.pick("Continuous")),
            grip = softCanonicalOrFallback(draft.grip, taxonomy.gripOptions, taxonomy.gripOptions.pick("Neutral")),
            loadPositionEnding = softCanonicalOrFallback(draft.loadPositionEnding, taxonomy.loadPositionOptions, taxonomy.loadPositionOptions.pick("Other")),
            legPattern = canonicalOrFallback(draft.legPattern, taxonomy.legPatternOptions, taxonomy.legPatternOptions.pick("Continuous")),
            footElevation = softCanonicalOrFallback(draft.footElevation, taxonomy.footElevationOptions, taxonomy.footElevationOptions.pick("No Elevation")),
            combinationType = canonicalOrFallback(draft.combinationType, taxonomy.combinationTypeOptions, taxonomy.combinationTypeOptions.pick("Single Exercise")),
            forceType = canonicalOrFallback(draft.forceType, taxonomy.forceTypeOptions, taxonomy.forceTypeOptions.pick("Push")),
            mechanics = canonicalOrFallback(draft.mechanics, taxonomy.mechanicsOptions, taxonomy.mechanicsOptions.pick("Compound")),
            laterality = canonicalOrFallback(draft.laterality, taxonomy.lateralityOptions, taxonomy.lateralityOptions.pick("Bilateral")),
            classification = canonicalOrFallback(draft.classification, taxonomy.classificationOptions, taxonomy.classificationOptions.pick("Bodybuilding")),
            movementPatterns = movementPatterns,
            planesOfMotion = planes,
            shortDemoLabel = draft.shortDemoLabel.trim().ifBlank { if (draft.shortDemoUrl.isBlank()) "" else "Demo" },
            shortDemoUrl = draft.shortDemoUrl.trim(),
            inDepthLabel = draft.inDepthLabel.trim().ifBlank { if (draft.inDepthUrl.isBlank()) "" else "Learn More" },
            inDepthUrl = draft.inDepthUrl.trim(),
            synonyms = splitValues(draft.synonymsInput).filterNot { normalizeText(it) == normalizeText(name) }.distinct(),
            muscles = muscles,
            equipment = buildEquipment(primaryEquipment, secondaryEquipment, draft.primaryItemCount.trim(), draft.secondaryItemCount.trim()),
            equipmentSlotCount = if (secondaryEquipment.isBlank()) 1 else 2,
            muscleSlotCount = muscles.size,
            generationModel = BuildConfig.GEMINI_PRIMARY_MODEL.takeIf { draft.generatedWithAi && it.isNotBlank() },
            generationPromptVersion = if (draft.generatedWithAi) "custom_exercise_v1" else "manual_custom_exercise_v1",
        )
    }

    private fun buildEquipment(
        primaryEquipment: String,
        secondaryEquipment: String,
        primaryItemCount: String,
        secondaryItemCount: String,
    ): List<Triple<String, String, Int?>> {
        val rows = mutableListOf<Triple<String, String, Int?>>()
        rows += Triple("primary", primaryEquipment, primaryItemCount.toIntOrNull()?.coerceAtLeast(1) ?: 1)
        secondaryEquipment.trim().takeIf { it.isNotBlank() }?.let {
            rows += Triple("secondary", it, secondaryItemCount.toIntOrNull()?.takeIf { value -> value > 0 })
        }
        return rows
    }

    private fun splitValues(input: String): List<String> =
        input.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun canonicalizeMany(values: List<String>, options: List<String>, fallback: List<String>): List<String> {
        val canonical = values.mapNotNull { canonical(options, it) }.distinct()
        return if (canonical.isEmpty()) fallback.filter { it.isNotBlank() }.distinct() else canonical
    }

    private fun canonicalOrFallback(value: String?, options: List<String>, fallback: String): String =
        canonical(options, value).orEmpty().ifBlank { fallback }

    private fun canonicalOrBlank(value: String?, options: List<String>): String =
        canonical(options, value).orEmpty()

    private fun softCanonicalOrFallback(value: String?, options: List<String>, fallback: String): String {
        val canonical = canonical(options, value)
        if (canonical != null) return canonical
        val cleaned = value?.trim().orEmpty()
        return if (cleaned.isBlank()) fallback else cleaned
    }

    private fun canonical(options: List<String>, candidate: String?): String? {
        val trimmed = candidate?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return options.firstOrNull { it.equals(trimmed, ignoreCase = true) }
            ?: options.firstOrNull { normalizeText(it) == normalizeText(trimmed) }
    }

    private fun exportChildRows(
        db: SQLiteDatabase,
        table: String,
        valueColumn: String,
        exerciseId: Long,
        roleColumn: String? = null,
    ): JSONArray =
        db.rawQuery(
            buildString {
                append("SELECT sequence_no")
                roleColumn?.let { append(", $it") }
                append(", $valueColumn FROM $table WHERE exercise_id = ? ORDER BY sequence_no")
            },
            arrayOf(exerciseId.toString()),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val payload = JSONObject().put("sequence_no", cursor.getInt(0))
                    if (roleColumn != null) {
                        payload.put(roleColumn, cursor.getString(1))
                        payload.put(valueColumn, cursor.getString(2))
                    } else {
                        payload.put(valueColumn, cursor.getString(1))
                    }
                    put(payload)
                }
            }
        }

    private fun exportEquipmentRows(db: SQLiteDatabase, exerciseId: Long): JSONArray =
        db.rawQuery(
            """
            SELECT sequence_no, equipment_role, equipment_name, item_count
            FROM exercise_equipment
            WHERE exercise_id = ?
            ORDER BY sequence_no
            """.trimIndent(),
            arrayOf(exerciseId.toString()),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("sequence_no", cursor.getInt(0))
                            .put("equipment_role", cursor.getString(1))
                            .put("equipment_name", cursor.getString(2))
                            .put("item_count", if (cursor.isNull(3)) JSONObject.NULL else cursor.getInt(3)),
                    )
                }
            }
        }

    private fun exportSynonyms(db: SQLiteDatabase, exerciseId: Long): JSONArray =
        db.rawQuery(
            """
            SELECT synonym_name, synonym_name_normalized, synonym_type, source, confidence_score, created_at_utc
            FROM exercise_synonyms
            WHERE exercise_id = ?
            ORDER BY synonym_name
            """.trimIndent(),
            arrayOf(exerciseId.toString()),
        ).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(
                        JSONObject()
                            .put("synonym_name", cursor.getString(0))
                            .put("synonym_name_normalized", cursor.getString(1))
                            .put("synonym_type", cursor.getString(2))
                            .put("source", cursor.getString(3))
                            .put("confidence_score", if (cursor.isNull(4)) JSONObject.NULL else cursor.getDouble(4))
                            .put("created_at_utc", cursor.getString(5)),
                    )
                }
            }
        }

    private fun insertFromSnapshot(db: SQLiteDatabase, row: JSONObject) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO exercises (
                exercise_id, source_row, slug, name, short_demo_label, short_demo_url, in_depth_label, in_depth_url,
                difficulty_level, target_muscle_group, prime_mover_muscle, secondary_muscle, tertiary_muscle,
                primary_equipment, primary_item_count, secondary_equipment, secondary_item_count, posture, arm_usage,
                arm_pattern, grip, load_position_ending, leg_pattern, foot_elevation, combination_type,
                movement_pattern_1, movement_pattern_2, movement_pattern_3,
                plane_of_motion_1, plane_of_motion_2, plane_of_motion_3,
                body_region, force_type, mechanics, laterality, primary_exercise_classification,
                equipment_slot_count, muscle_slot_count, has_short_demo, has_in_depth_explanation,
                is_post_install_llm_generated, created_at_utc, updated_at_utc, generation_model, generation_prompt_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                row.getLong("exercise_id"),
                row.getLong("source_row"),
                row.getString("slug"),
                row.getString("name"),
                row.optString("short_demo_label").nullIfBlank(),
                row.optString("short_demo_url").nullIfBlank(),
                row.optString("in_depth_label").nullIfBlank(),
                row.optString("in_depth_url").nullIfBlank(),
                row.getString("difficulty_level"),
                row.getString("target_muscle_group"),
                row.optString("prime_mover_muscle").nullIfBlank(),
                row.optString("secondary_muscle").nullIfBlank(),
                row.optString("tertiary_muscle").nullIfBlank(),
                row.getString("primary_equipment"),
                row.optInt("primary_item_count").takeIf { it > 0 },
                row.optString("secondary_equipment").nullIfBlank(),
                row.optInt("secondary_item_count").takeIf { it > 0 },
                row.getString("posture"),
                row.getString("arm_usage"),
                row.getString("arm_pattern"),
                row.getString("grip"),
                row.getString("load_position_ending"),
                row.getString("leg_pattern"),
                row.getString("foot_elevation"),
                row.getString("combination_type"),
                row.optString("movement_pattern_1").nullIfBlank(),
                row.optString("movement_pattern_2").nullIfBlank(),
                row.optString("movement_pattern_3").nullIfBlank(),
                row.optString("plane_of_motion_1").nullIfBlank(),
                row.optString("plane_of_motion_2").nullIfBlank(),
                row.optString("plane_of_motion_3").nullIfBlank(),
                row.getString("body_region"),
                row.getString("force_type"),
                row.optString("mechanics").nullIfBlank(),
                row.getString("laterality"),
                row.getString("primary_exercise_classification"),
                row.optInt("equipment_slot_count"),
                row.optInt("muscle_slot_count"),
                if (row.optBoolean("has_short_demo")) 1 else 0,
                if (row.optBoolean("has_in_depth_explanation")) 1 else 0,
                if (row.optBoolean("is_post_install_llm_generated")) 1 else 0,
                row.optString("created_at_utc").nullIfBlank(),
                row.optString("updated_at_utc").nullIfBlank(),
                row.optString("generation_model").nullIfBlank(),
                row.optString("generation_prompt_version").nullIfBlank(),
            ),
        )
        val exerciseId = row.getLong("exercise_id")
        row.optJSONArray("muscles")?.let { muscles ->
            for (index in 0 until muscles.length()) {
                val item = muscles.getJSONObject(index)
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO exercise_muscles (exercise_id, sequence_no, muscle_role, muscle_name)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(exerciseId, item.getInt("sequence_no"), item.getString("muscle_role"), item.getString("muscle_name")),
                )
            }
        }
        row.optJSONArray("equipment")?.let { equipment ->
            for (index in 0 until equipment.length()) {
                val item = equipment.getJSONObject(index)
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO exercise_equipment (exercise_id, sequence_no, equipment_role, equipment_name, item_count)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        exerciseId,
                        item.getInt("sequence_no"),
                        item.getString("equipment_role"),
                        item.getString("equipment_name"),
                        if (item.isNull("item_count")) null else item.getInt("item_count"),
                    ),
                )
            }
        }
        row.optJSONArray("movement_patterns")?.let { patterns ->
            for (index in 0 until patterns.length()) {
                val item = patterns.getJSONObject(index)
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO exercise_movement_patterns (exercise_id, sequence_no, movement_pattern)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                    arrayOf(exerciseId, item.getInt("sequence_no"), item.getString("movement_pattern")),
                )
            }
        }
        row.optJSONArray("planes_of_motion")?.let { planes ->
            for (index in 0 until planes.length()) {
                val item = planes.getJSONObject(index)
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO exercise_planes_of_motion (exercise_id, sequence_no, plane_of_motion)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                    arrayOf(exerciseId, item.getInt("sequence_no"), item.getString("plane_of_motion")),
                )
            }
        }
        row.optJSONArray("synonyms")?.let { synonyms ->
            for (index in 0 until synonyms.length()) {
                val item = synonyms.getJSONObject(index)
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO exercise_synonyms
                        (exercise_id, synonym_name, synonym_name_normalized, synonym_type, source, confidence_score, created_at_utc)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        exerciseId,
                        item.getString("synonym_name"),
                        item.getString("synonym_name_normalized"),
                        item.optString("synonym_type", "custom"),
                        item.optString("source", "snapshot_restore"),
                        if (item.isNull("confidence_score")) null else item.getDouble("confidence_score"),
                        item.optString("created_at_utc", Instant.now().toString()),
                    ),
                )
            }
        }
    }

    private fun findExistingByNormalizedName(name: String): ExerciseSummary? {
        val matches = loadMatches(name)
        val normalized = normalizeText(name)
        return matches.firstOrNull { normalizeText(it.name) == normalized }
    }

    private fun allocateIdentity(db: SQLiteDatabase, name: String): Identity {
        var candidateSlug = slugify(name)
        var suffix = 2
        while (slugExists(db, candidateSlug)) {
            candidateSlug = "${slugify(name)}-$suffix"
            suffix += 1
        }
        val exerciseId = stableIdForSlug(candidateSlug)
        return Identity(
            exerciseId = exerciseId,
            sourceRow = exerciseId,
            slug = candidateSlug,
        )
    }

    private fun slugExists(db: SQLiteDatabase, slug: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM exercises WHERE slug = ? LIMIT 1",
            arrayOf(slug),
        ).use { it.moveToFirst() }

    private fun stableIdForSlug(slug: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest("custom:$slug".toByteArray(Charsets.UTF_8))
        val raw = ByteBuffer.wrap(digest.copyOfRange(0, 8)).long and Long.MAX_VALUE
        return 9_000_000_000_000_000L + (raw % 900_000_000_000_000L)
    }

    private fun slugify(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.US)
        return normalized
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "custom-exercise" }
    }

    private fun normalizeText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private data class Identity(
        val exerciseId: Long,
        val sourceRow: Long,
        val slug: String,
    )

    private data class PreparedDraft(
        val name: String,
        val difficultyLevel: String,
        val bodyRegion: String,
        val targetMuscleGroup: String,
        val primeMoverMuscle: String,
        val secondaryMuscle: String,
        val tertiaryMuscle: String,
        val primaryEquipment: String,
        val primaryItemCount: Int,
        val secondaryEquipment: String,
        val secondaryItemCount: Int?,
        val posture: String,
        val armUsage: String,
        val armPattern: String,
        val grip: String,
        val loadPositionEnding: String,
        val legPattern: String,
        val footElevation: String,
        val combinationType: String,
        val forceType: String,
        val mechanics: String,
        val laterality: String,
        val classification: String,
        val movementPatterns: List<String>,
        val planesOfMotion: List<String>,
        val shortDemoLabel: String,
        val shortDemoUrl: String,
        val inDepthLabel: String,
        val inDepthUrl: String,
        val synonyms: List<String>,
        val muscles: List<Pair<String, String>>,
        val equipment: List<Triple<String, String, Int?>>,
        val equipmentSlotCount: Int,
        val muscleSlotCount: Int,
        val generationModel: String?,
        val generationPromptVersion: String?,
    )

    private fun List<String>.pick(preferred: String): String {
        return firstOrNull { it.equals(preferred, ignoreCase = true) } ?: firstOrNull().orEmpty()
    }
}
