package dev.toastlabs.toastlift.data

import android.database.sqlite.SQLiteDatabase
import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

internal fun normalizeExerciseNote(rawValue: String): String? =
    rawValue.trim().takeIf { it.isNotEmpty() }

internal fun normalizeExerciseSynonym(rawValue: String): String? =
    rawValue.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }

internal fun normalizedExerciseSynonymKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

internal fun normalizeExerciseDescription(rawValue: String?): String? =
    rawValue?.trim()?.takeIf { it.isNotEmpty() }

internal fun resolveExerciseDescriptionColumn(columnNames: Collection<String>): String? {
    val normalized = columnNames.associateBy { it.lowercase() }
    return listOf("description", "instructions", "exercise_description")
        .firstNotNullOfOrNull { candidate -> normalized[candidate] }
}

internal fun normalizeExerciseVideoLinkLabel(rawValue: String): String? =
    rawValue.trim().takeIf { it.isNotEmpty() }

internal fun normalizeExerciseVideoLinkUrl(rawValue: String): String? {
    val trimmed = rawValue.trim()
    if (trimmed.isEmpty()) return null
    val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
    val parsed = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.lowercase()
    if (scheme !in setOf("http", "https")) return null
    val host = parsed.host?.lowercase()?.removePrefix("www.") ?: return null
    if (!isSupportedExerciseVideoHost(host)) return null
    return candidate
}

private fun isSupportedExerciseVideoHost(host: String): Boolean = host == "youtube.com" ||
    host == "m.youtube.com" ||
    host == "youtu.be" ||
    host == "tiktok.com" ||
    host.endsWith(".tiktok.com")

private fun loggedSessionCountJoin(exerciseIdColumn: String = "e.exercise_id"): String = """
    LEFT JOIN (
        SELECT
            pe.exercise_id,
            COUNT(DISTINCT pe.performed_workout_id) AS logged_session_count
        FROM performed_exercises pe
        INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
        WHERE ps.is_completed = 1
          AND COALESCE(ps.actual_reps, 0) > 0
        GROUP BY pe.exercise_id
    ) logged_history ON logged_history.exercise_id = $exerciseIdColumn
""".trimIndent()

private const val EXERCISE_DISCOVERY_PERFORMED_CONTEXT_LIMIT = 2_000
private const val EXERCISE_DISCOVERY_ZERO_SESSION_CANDIDATE_LIMIT = 2_000
private const val EXERCISE_DISCOVERY_LOW_EXPOSURE_CANDIDATE_LIMIT = 500

class CatalogRepository(private val database: ToastLiftDatabase) {
    private enum class FacetDimension {
        Equipment,
        TargetMuscle,
        PrimeMover,
        RecommendationBias,
        LoggedHistory,
    }

    private data class SqlClause(
        val whereClause: String,
        val args: Array<String>,
    )

    fun ensureSynonymsSeeded() {
        database.ensureGeneratedSynonyms()
    }

    fun loadEquipmentOptions(): List<String> {
        val db = database.open()
        val results = mutableListOf<String>()
        db.rawQuery(
            """
            SELECT DISTINCT equipment_name
            FROM exercise_equipment
            WHERE equipment_name IS NOT NULL AND trim(equipment_name) != ''
            ORDER BY equipment_name
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += cursor.getString(0)
            }
        }
        return results
    }

    fun loadTargetMuscleOptions(): List<String> = loadDistinctColumnValues("target_muscle_group")

    fun loadPrimeMoverOptions(): List<String> = loadDistinctColumnValues("prime_mover_muscle")

    fun loadSmartPickerTargetOptions(): List<SmartPickerMuscleTargetOption> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT
                em.muscle_name,
                COUNT(DISTINCT e.exercise_id) AS exercise_count,
                COUNT(DISTINCT CASE WHEN e.body_region = 'Upper Body' THEN e.exercise_id END) AS upper_body_exercise_count,
                COUNT(DISTINCT CASE WHEN e.body_region = 'Lower Body' THEN e.exercise_id END) AS lower_body_exercise_count,
                COUNT(DISTINCT CASE WHEN e.body_region = 'Core' THEN e.exercise_id END) AS core_exercise_count
            FROM exercise_muscles em
            INNER JOIN exercises e ON e.exercise_id = em.exercise_id
            WHERE em.muscle_name IS NOT NULL
              AND trim(em.muscle_name) != ''
            GROUP BY em.muscle_name
            ORDER BY em.muscle_name COLLATE NOCASE
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SmartPickerMuscleTargetOption(
                            name = cursor.getString(0),
                            exerciseCount = cursor.getInt(1),
                            upperBodyExerciseCount = cursor.getInt(2),
                            lowerBodyExerciseCount = cursor.getInt(3),
                            coreExerciseCount = cursor.getInt(4),
                        ),
                    )
                }
            }
        }
    }

    fun loadLibraryPayload(query: String, filters: LibraryFilters): LibrarySearchPayload {
        return LibrarySearchPayload(
            results = searchExercises(query = query, filters = filters),
            facets = loadLibraryFacets(query = query, filters = filters),
        )
    }

    internal fun loadExerciseAiSearchCatalog(): List<ExerciseAiSearchCatalogEntry> {
        val db = database.open()
        val synonymsByExerciseId = mutableMapOf<Long, MutableList<String>>()
        db.rawQuery(
            "SELECT exercise_id, synonym_name FROM exercise_synonyms ORDER BY exercise_id, synonym_name",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                synonymsByExerciseId
                    .getOrPut(cursor.getLong(0)) { mutableListOf() }
                    .add(cursor.getString(1))
            }
        }
        return db.rawQuery(
            """
            SELECT e.exercise_id, e.name
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            WHERE COALESCE(p.is_hidden, 0) = 0
              AND COALESCE(p.is_banned, 0) = 0
            ORDER BY e.name
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val exerciseId = cursor.getLong(0)
                    add(
                        ExerciseAiSearchCatalogEntry(
                            id = exerciseId,
                            name = cursor.getString(1),
                            synonyms = synonymsByExerciseId[exerciseId].orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    fun getExerciseSummariesByIds(exerciseIds: List<Long>): Map<Long, ExerciseSummary> {
        if (exerciseIds.isEmpty()) return emptyMap()
        val db = database.open()
        val placeholders = exerciseIds.joinToString(",") { "?" }
        return db.rawQuery(
            """
            SELECT
                e.exercise_id,
                e.name,
                e.difficulty_level,
                e.body_region,
                e.target_muscle_group,
                COALESCE(e.primary_equipment, 'Bodyweight'),
                e.secondary_equipment,
                e.mechanics,
                COALESCE(p.is_favorite, 0),
                COALESCE(p.is_hidden, 0),
                COALESCE(p.is_banned, 0),
                COALESCE(p.preference_score_delta, 0),
                COALESCE(logged_history.logged_session_count, 0)
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            ${loggedSessionCountJoin()}
            WHERE e.exercise_id IN ($placeholders)
            """.trimIndent(),
            exerciseIds.map { it.toString() }.toTypedArray(),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val summary = ExerciseSummary(
                        id = cursor.getLong(0),
                        name = cursor.getString(1),
                        difficulty = cursor.getString(2),
                        bodyRegion = cursor.getString(3),
                        targetMuscleGroup = cursor.getString(4),
                        equipment = cursor.getString(5),
                        secondaryEquipment = cursor.getStringOrNull(6),
                        mechanics = cursor.getStringOrNull(7),
                        favorite = cursor.getInt(8) == 1,
                        hidden = cursor.getInt(9) == 1,
                        banned = cursor.getInt(10) == 1,
                        preferenceScoreDelta = cursor.getDouble(11),
                        recommendationBias = RecommendationBias.fromScoreDelta(cursor.getDouble(11)),
                        loggedSessionCount = cursor.getInt(12),
                    )
                    put(summary.id, summary)
                }
            }
        }
    }

    /**
     * Persists a user-confirmed synonym. Returns false when the synonym is blank
     * or already exists for this exercise (matching the normalized unique key).
     */
    fun addExerciseSynonym(exerciseId: Long, synonym: String, source: String = "user_confirmed_ai_search"): Boolean {
        val normalizedName = normalizeExerciseSynonym(synonym) ?: return false
        val normalizedKey = normalizedExerciseSynonymKey(normalizedName)
        if (normalizedKey.isBlank()) return false
        val db = database.open()
        val existing = db.rawQuery(
            "SELECT 1 FROM exercise_synonyms WHERE exercise_id = ? AND synonym_name_normalized = ?",
            arrayOf(exerciseId.toString(), normalizedKey),
        ).use { cursor -> cursor.moveToFirst() }
        if (existing) return false
        db.execSQL(
            """
            INSERT OR IGNORE INTO exercise_synonyms
                (exercise_id, synonym_name, synonym_name_normalized, synonym_type, source, confidence_score, created_at_utc)
            VALUES (?, ?, ?, 'custom', ?, 1.0, ?)
            """.trimIndent(),
            arrayOf(exerciseId, normalizedName, normalizedKey, source, Instant.now().toString()),
        )
        return true
    }

    internal fun loadExerciseDiscoveryContext(query: String, filters: LibraryFilters): ExerciseDiscoveryContext {
        val normalizedQuery = normalizeQuery(query)
        val clause = buildExerciseFilterClause(normalizedQuery, filters)
        return ExerciseDiscoveryContext(
            query = query.trim(),
            filters = filters,
            appliedFilterLabels = buildExerciseDiscoveryFilterLabels(query.trim(), filters),
            performedExercises = loadPerformedExerciseDiscoveryExercises(
                limit = EXERCISE_DISCOVERY_PERFORMED_CONTEXT_LIMIT,
            ),
            zeroSessionCandidates = loadFilteredExerciseDiscoveryExercises(
                clause = clause,
                loggedSessionPredicate = "COALESCE(logged_history.logged_session_count, 0) = 0",
                orderBy = librarySearchOrderBy(filters),
                limit = EXERCISE_DISCOVERY_ZERO_SESSION_CANDIDATE_LIMIT,
            ),
            lowExposureCandidates = loadFilteredExerciseDiscoveryExercises(
                clause = clause,
                loggedSessionPredicate = "COALESCE(logged_history.logged_session_count, 0) > 0",
                orderBy = "COALESCE(logged_history.logged_session_count, 0) ASC, ${librarySearchOrderBy(filters)}",
                limit = EXERCISE_DISCOVERY_LOW_EXPOSURE_CANDIDATE_LIMIT,
            ),
            totalPerformedExercises = countPerformedExerciseDiscoveryExercises(),
            totalMatchingExercises = countFilteredExerciseDiscoveryExercises(clause),
            totalZeroSessionCandidates = countFilteredExerciseDiscoveryExercises(
                clause = clause,
                loggedSessionPredicate = "COALESCE(logged_history.logged_session_count, 0) = 0",
            ),
        )
    }

    fun searchExercises(query: String, filters: LibraryFilters = LibraryFilters(), limit: Int? = null): List<ExerciseSummary> {
        val db = database.open()
        val normalizedQuery = normalizeQuery(query)
        val clause = buildExerciseFilterClause(normalizedQuery, filters)
        val sql = buildString {
            append(
                """
                SELECT
                    e.exercise_id,
                    e.name,
                    e.difficulty_level,
                    e.body_region,
                    e.target_muscle_group,
                    COALESCE(e.primary_equipment, 'Bodyweight'),
                    e.secondary_equipment,
                    e.mechanics,
                    COALESCE(p.is_favorite, 0),
                    COALESCE(p.is_hidden, 0),
                    COALESCE(p.is_banned, 0),
                    COALESCE(p.preference_score_delta, 0),
                    COALESCE(logged_history.logged_session_count, 0)
                FROM exercises e
                LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
                ${loggedSessionCountJoin()}
                WHERE
                """.trimIndent(),
            )
            append('\n')
            append(clause.whereClause)
            append("\nORDER BY ${librarySearchOrderBy(filters)}")
            if (limit != null) append("\nLIMIT ?")
        }
        val args = if (limit != null) clause.args + limit.toString() else clause.args

        return db.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ExerciseSummary(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            difficulty = cursor.getString(2),
                            bodyRegion = cursor.getString(3),
                            targetMuscleGroup = cursor.getString(4),
                            equipment = cursor.getString(5),
                            secondaryEquipment = cursor.getStringOrNull(6),
                            mechanics = cursor.getStringOrNull(7),
                            favorite = cursor.getInt(8) == 1,
                            hidden = cursor.getInt(9) == 1,
                            banned = cursor.getInt(10) == 1,
                            preferenceScoreDelta = cursor.getDouble(11),
                            recommendationBias = RecommendationBias.fromScoreDelta(cursor.getDouble(11)),
                            loggedSessionCount = cursor.getInt(12),
                        ),
                    )
                }
            }
        }
    }

    fun loadLibraryFacets(query: String, filters: LibraryFilters): LibraryFacets {
        val normalizedQuery = normalizeQuery(query)
        return LibraryFacets(
            equipment = loadFacetCounts(normalizedQuery, filters, FacetDimension.Equipment),
            targetMuscles = loadFacetCounts(normalizedQuery, filters, FacetDimension.TargetMuscle),
            primeMovers = loadFacetCounts(normalizedQuery, filters, FacetDimension.PrimeMover),
            recommendationBiases = loadRecommendationBiasFacetCounts(normalizedQuery, filters),
            loggedHistoryCount = loadLoggedHistoryFacetCount(normalizedQuery, filters),
        )
    }

    fun getExerciseDetail(exerciseId: Long): ExerciseDetail? {
        val db = database.open()
        val descriptionColumn = resolveExerciseDescriptionColumn(db.columnNames("exercises"))
        val summary = db.rawQuery(
            """
            SELECT
                e.exercise_id,
                e.name,
                e.difficulty_level,
                e.body_region,
                e.target_muscle_group,
                COALESCE(e.primary_equipment, 'Bodyweight'),
                e.secondary_equipment,
                e.mechanics,
                COALESCE(p.is_favorite, 0),
                COALESCE(p.is_hidden, 0),
                COALESCE(p.is_banned, 0),
                COALESCE(p.preference_score_delta, 0),
                p.notes,
                e.prime_mover_muscle,
                e.secondary_muscle,
                e.tertiary_muscle,
                e.posture,
                e.laterality,
                e.primary_exercise_classification,
                e.short_demo_url,
                e.in_depth_url,
                COALESCE(logged_history.logged_session_count, 0),
                ${descriptionColumn?.let { "e.$it" } ?: "NULL"} AS exercise_description
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            ${loggedSessionCountJoin()}
            WHERE e.exercise_id = ?
            """.trimIndent(),
            arrayOf(exerciseId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            ExerciseDetail(
                summary = ExerciseSummary(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    difficulty = cursor.getString(2),
                    bodyRegion = cursor.getString(3),
                    targetMuscleGroup = cursor.getString(4),
                    equipment = cursor.getString(5),
                    secondaryEquipment = cursor.getStringOrNull(6),
                    mechanics = cursor.getStringOrNull(7),
                    favorite = cursor.getInt(8) == 1,
                    hidden = cursor.getInt(9) == 1,
                    banned = cursor.getInt(10) == 1,
                    preferenceScoreDelta = cursor.getDouble(11),
                    recommendationBias = RecommendationBias.fromScoreDelta(cursor.getDouble(11)),
                    loggedSessionCount = cursor.getInt(21),
                ),
                notes = cursor.getStringOrNull(12),
                primeMover = cursor.getStringOrNull(13),
                secondaryMuscle = cursor.getStringOrNull(14),
                tertiaryMuscle = cursor.getStringOrNull(15),
                posture = cursor.getString(16),
                laterality = cursor.getString(17),
                classification = cursor.getString(18),
                movementPatterns = emptyList(),
                planesOfMotion = emptyList(),
                demoUrl = cursor.getStringOrNull(19),
                explanationUrl = cursor.getStringOrNull(20),
                canonicalDescription = normalizeExerciseDescription(cursor.getStringOrNull(22)),
                synonyms = emptyList(),
            )
        }

        val movementPatterns = db.listOfStrings(
            "SELECT movement_pattern FROM exercise_movement_patterns WHERE exercise_id = ? ORDER BY sequence_no",
            exerciseId,
        )
        val planes = db.listOfStrings(
            "SELECT plane_of_motion FROM exercise_planes_of_motion WHERE exercise_id = ? ORDER BY sequence_no",
            exerciseId,
        )
        val synonyms = db.listOfStrings(
            "SELECT synonym_name FROM exercise_synonyms WHERE exercise_id = ? ORDER BY synonym_name",
            exerciseId,
        )
        val defaultVideoLinks = buildDefaultExerciseVideoLinks(summary.demoUrl, summary.explanationUrl)
        val userVideoLinks = db.rawQuery(
            """
            SELECT user_video_link_id, label, url
            FROM exercise_user_video_links
            WHERE exercise_id = ?
            ORDER BY updated_at_utc DESC, user_video_link_id DESC
            """.trimIndent(),
            arrayOf(exerciseId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val normalizedLabel = normalizeExerciseVideoLinkLabel(cursor.getString(1))
                    val normalizedUrl = normalizeExerciseVideoLinkUrl(cursor.getString(2))
                    if (normalizedLabel != null && normalizedUrl != null) {
                        add(
                            ExerciseVideoLink(
                                id = cursor.getLong(0),
                                label = normalizedLabel,
                                url = normalizedUrl,
                            ),
                        )
                    }
                }
            }
        }
        val generatedDescription = db.rawQuery(
            """
            SELECT description, generation_model, generation_prompt_version, created_at_utc, updated_at_utc
            FROM exercise_generated_descriptions
            WHERE exercise_id = ?
            """.trimIndent(),
            arrayOf(exerciseId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                normalizeExerciseDescription(cursor.getStringOrNull(0))?.let { description ->
                    UserGeneratedExerciseDescription(
                        description = description,
                        generationModel = cursor.getStringOrNull(1),
                        generationPromptVersion = cursor.getStringOrNull(2),
                        createdAtUtc = cursor.getString(3),
                        updatedAtUtc = cursor.getString(4),
                    )
                }
            }
        }

        return summary.copy(
            movementPatterns = movementPatterns,
            planesOfMotion = planes,
            synonyms = synonyms,
            generatedDescription = generatedDescription,
            defaultVideoLinks = defaultVideoLinks,
            userVideoLinks = userVideoLinks,
        )
    }

    fun loadExerciseFamily(exerciseId: Long): ExerciseFamily? {
        val anchor = getExerciseDetail(exerciseId) ?: return null
        val candidates = loadExerciseFamilyCandidateProfiles(exerciseId)
        return buildExerciseFamily(
            anchor = anchor,
            candidates = candidates,
        )
    }

    private fun loadExerciseFamilyCandidateProfiles(anchorExerciseId: Long): List<ExerciseFamilyProfile> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT
                e.exercise_id,
                e.name,
                e.difficulty_level,
                e.body_region,
                e.target_muscle_group,
                COALESCE(e.primary_equipment, 'Bodyweight'),
                e.secondary_equipment,
                e.mechanics,
                COALESCE(p.is_favorite, 0),
                COALESCE(p.is_hidden, 0),
                COALESCE(p.is_banned, 0),
                COALESCE(p.preference_score_delta, 0),
                COALESCE(logged_history.logged_session_count, 0),
                e.prime_mover_muscle,
                e.secondary_muscle,
                e.tertiary_muscle,
                e.posture,
                e.laterality,
                e.primary_exercise_classification,
                (
                    SELECT GROUP_CONCAT(mp.movement_pattern, '||')
                    FROM exercise_movement_patterns mp
                    WHERE mp.exercise_id = e.exercise_id
                    ORDER BY mp.sequence_no
                ) AS movement_patterns,
                (
                    SELECT GROUP_CONCAT(pm.plane_of_motion, '||')
                    FROM exercise_planes_of_motion pm
                    WHERE pm.exercise_id = e.exercise_id
                    ORDER BY pm.sequence_no
                ) AS planes_of_motion
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            ${loggedSessionCountJoin()}
            WHERE e.exercise_id != ?
              AND COALESCE(p.is_hidden, 0) = 0
              AND COALESCE(p.is_banned, 0) = 0
            ORDER BY e.name
            """.trimIndent(),
            arrayOf(anchorExerciseId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ExerciseFamilyProfile(
                            summary = ExerciseSummary(
                                id = cursor.getLong(0),
                                name = cursor.getString(1),
                                difficulty = cursor.getString(2),
                                bodyRegion = cursor.getString(3),
                                targetMuscleGroup = cursor.getString(4),
                                equipment = cursor.getString(5),
                                secondaryEquipment = cursor.getStringOrNull(6),
                                mechanics = cursor.getStringOrNull(7),
                                favorite = cursor.getInt(8) == 1,
                                hidden = cursor.getInt(9) == 1,
                                banned = cursor.getInt(10) == 1,
                                preferenceScoreDelta = cursor.getDouble(11),
                                recommendationBias = RecommendationBias.fromScoreDelta(cursor.getDouble(11)),
                                loggedSessionCount = cursor.getInt(12),
                            ),
                            primeMover = cursor.getStringOrNull(13),
                            secondaryMuscle = cursor.getStringOrNull(14),
                            tertiaryMuscle = cursor.getStringOrNull(15),
                            posture = cursor.getStringOrNull(16),
                            laterality = cursor.getStringOrNull(17),
                            classification = cursor.getStringOrNull(18),
                            movementPatterns = splitCatalogList(cursor.getStringOrNull(19)),
                            planesOfMotion = splitCatalogList(cursor.getStringOrNull(20)),
                        ),
                    )
                }
            }
        }
    }

    fun toggleFavorite(exerciseId: Long, favorite: Boolean) {
        val db = database.open()
        val now = java.time.Instant.now().toString()
        db.execSQL(
            """
            INSERT INTO exercise_preferences (exercise_id, is_favorite, is_hidden, is_banned, preference_score_delta, notes, updated_at_utc)
            VALUES (?, ?, 0, 0, 0, NULL, ?)
            ON CONFLICT(exercise_id) DO UPDATE SET is_favorite = excluded.is_favorite, updated_at_utc = excluded.updated_at_utc
            """.trimIndent(),
            arrayOf(exerciseId, if (favorite) 1 else 0, now),
        )
    }

    fun setRecommendationBias(exerciseId: Long, bias: RecommendationBias) {
        val db = database.open()
        val now = java.time.Instant.now().toString()
        db.execSQL(
            """
            INSERT INTO exercise_preferences (exercise_id, is_favorite, is_hidden, is_banned, preference_score_delta, notes, updated_at_utc)
            VALUES (?, 0, 0, 0, ?, NULL, ?)
            ON CONFLICT(exercise_id) DO UPDATE SET
                preference_score_delta = excluded.preference_score_delta,
                updated_at_utc = excluded.updated_at_utc
            """.trimIndent(),
            arrayOf(exerciseId, bias.scoreDelta, now),
        )
    }

    fun resetRecommendationPreferenceScore(exerciseId: Long) {
        val db = database.open()
        val now = java.time.Instant.now().toString()
        db.execSQL(
            """
            INSERT INTO exercise_preferences (exercise_id, is_favorite, is_hidden, is_banned, preference_score_delta, notes, updated_at_utc)
            VALUES (?, 0, 0, 0, 0, NULL, ?)
            ON CONFLICT(exercise_id) DO UPDATE SET
                preference_score_delta = 0,
                updated_at_utc = excluded.updated_at_utc
            """.trimIndent(),
            arrayOf(exerciseId, now),
        )
    }

    fun setExerciseNote(exerciseId: Long, note: String?) {
        val db = database.open()
        val now = Instant.now().toString()
        db.execSQL(
            """
            INSERT INTO exercise_preferences (exercise_id, is_favorite, is_hidden, is_banned, preference_score_delta, notes, updated_at_utc)
            VALUES (?, 0, 0, 0, 0, ?, ?)
            ON CONFLICT(exercise_id) DO UPDATE SET
                notes = excluded.notes,
                updated_at_utc = excluded.updated_at_utc
            """.trimIndent(),
            arrayOf(exerciseId, note, now),
        )
    }

    fun saveExerciseVideoLink(
        exerciseId: Long,
        linkId: Long?,
        labelInput: String,
        urlInput: String,
    ): Boolean {
        val label = normalizeExerciseVideoLinkLabel(labelInput) ?: return false
        val url = normalizeExerciseVideoLinkUrl(urlInput) ?: return false
        val db = database.open()
        val now = Instant.now().toString()
        if (linkId == null) {
            db.execSQL(
                """
                INSERT INTO exercise_user_video_links (
                    exercise_id,
                    label,
                    url,
                    created_at_utc,
                    updated_at_utc
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(exerciseId, label, url, now, now),
            )
        } else {
            db.execSQL(
                """
                UPDATE exercise_user_video_links
                SET label = ?, url = ?, updated_at_utc = ?
                WHERE user_video_link_id = ? AND exercise_id = ?
                """.trimIndent(),
                arrayOf(label, url, now, linkId, exerciseId),
            )
        }
        return true
    }

    fun saveGeneratedExerciseDescription(
        exerciseId: Long,
        description: String,
        generationModel: String?,
        generationPromptVersion: String?,
    ) {
        val normalizedDescription = normalizeExerciseDescription(description)
            ?: throw IllegalArgumentException("Generated description cannot be blank.")
        val db = database.open()
        val existingCreatedAtUtc = db.rawQuery(
            """
            SELECT created_at_utc
            FROM exercise_generated_descriptions
            WHERE exercise_id = ?
            """.trimIndent(),
            arrayOf(exerciseId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0)
        }
        val now = Instant.now().toString()
        db.execSQL(
            """
            INSERT INTO exercise_generated_descriptions (
                exercise_id,
                description,
                generation_model,
                generation_prompt_version,
                created_at_utc,
                updated_at_utc
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(exercise_id) DO UPDATE SET
                description = excluded.description,
                generation_model = excluded.generation_model,
                generation_prompt_version = excluded.generation_prompt_version,
                updated_at_utc = excluded.updated_at_utc
            """.trimIndent(),
            arrayOf(
                exerciseId,
                normalizedDescription,
                generationModel,
                generationPromptVersion,
                existingCreatedAtUtc ?: now,
                now,
            ),
        )
    }

    fun deleteExerciseVideoLink(exerciseId: Long, linkId: Long) {
        val db = database.open()
        db.execSQL(
            "DELETE FROM exercise_user_video_links WHERE user_video_link_id = ? AND exercise_id = ?",
            arrayOf(linkId, exerciseId),
        )
    }

    fun loadRecommendationBiases(): Map<Long, RecommendationBias> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT exercise_id, preference_score_delta
            FROM exercise_preferences
            WHERE ABS(preference_score_delta) >= 0.5
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getLong(0), RecommendationBias.fromScoreDelta(cursor.getDouble(1)))
                }
            }
        }
    }

    fun exerciseById(exerciseId: Long): ExerciseSummary? = searchExercisesByIds(listOf(exerciseId)).firstOrNull()

    fun loadExerciseWorkUnits(exerciseId: Long): List<WorkUnitDefinition> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT
                unit_key,
                display_label,
                value_type,
                unit_label,
                default_value,
                min_value,
                max_value,
                step_value,
                is_primary,
                is_required,
                tracks_effort
            FROM exercise_work_units
            WHERE exercise_id = ?
            ORDER BY sequence_no
            """.trimIndent(),
            arrayOf(exerciseId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        WorkUnitDefinition(
                            key = cursor.getString(0),
                            label = cursor.getString(1),
                            valueType = cursor.getString(2),
                            unitLabel = cursor.getStringOrNull(3),
                            defaultValue = cursor.getStringOrNull(4),
                            minValue = if (cursor.isNull(5)) null else cursor.getDouble(5),
                            maxValue = if (cursor.isNull(6)) null else cursor.getDouble(6),
                            stepValue = if (cursor.isNull(7)) null else cursor.getDouble(7),
                            isPrimary = cursor.getInt(8) == 1,
                            isRequired = cursor.getInt(9) == 1,
                            tracksEffort = cursor.getInt(10) == 1,
                        ),
                    )
                }
            }
        }
    }

    private fun buildDefaultExerciseVideoLinks(
        demoUrl: String?,
        explanationUrl: String?,
    ): List<ExerciseVideoLink> {
        val links = linkedMapOf<String, ExerciseVideoLink>()
        demoUrl?.let { url ->
            normalizeExerciseVideoLinkUrl(url)?.let { normalizedUrl ->
                links[normalizedUrl] = ExerciseVideoLink(
                    label = "Default demo",
                    url = normalizedUrl,
                    isReadOnly = true,
                )
            }
        }
        explanationUrl?.let { url ->
            normalizeExerciseVideoLinkUrl(url)?.let { normalizedUrl ->
                links[normalizedUrl] = ExerciseVideoLink(
                    label = "Default explanation",
                    url = normalizedUrl,
                    isReadOnly = true,
                )
            }
        }
        return links.values.toList()
    }

    fun searchExercisesByIds(ids: List<Long>): List<ExerciseSummary> {
        if (ids.isEmpty()) return emptyList()
        val db = database.open()
        val placeholders = ids.joinToString(",") { "?" }
        return db.rawQuery(
            """
            SELECT
                e.exercise_id,
                e.name,
                e.difficulty_level,
                e.body_region,
                e.target_muscle_group,
                COALESCE(e.primary_equipment, 'Bodyweight'),
                e.secondary_equipment,
                e.mechanics,
                COALESCE(p.is_favorite, 0),
                COALESCE(p.is_hidden, 0),
                COALESCE(p.is_banned, 0),
                COALESCE(p.preference_score_delta, 0),
                COALESCE(logged_history.logged_session_count, 0)
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            ${loggedSessionCountJoin()}
            WHERE e.exercise_id IN ($placeholders)
            ORDER BY e.name
            """.trimIndent(),
            ids.map { it.toString() }.toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ExerciseSummary(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            difficulty = cursor.getString(2),
                            bodyRegion = cursor.getString(3),
                            targetMuscleGroup = cursor.getString(4),
                            equipment = cursor.getString(5),
                            secondaryEquipment = cursor.getStringOrNull(6),
                            mechanics = cursor.getStringOrNull(7),
                            favorite = cursor.getInt(8) == 1,
                            hidden = cursor.getInt(9) == 1,
                            banned = cursor.getInt(10) == 1,
                            preferenceScoreDelta = cursor.getDouble(11),
                            recommendationBias = RecommendationBias.fromScoreDelta(cursor.getDouble(11)),
                            loggedSessionCount = cursor.getInt(12),
                        ),
                    )
                }
            }
        }
    }

    private fun SQLiteDatabase.listOfStrings(query: String, exerciseId: Long): List<String> =
        rawQuery(query, arrayOf(exerciseId.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun splitCatalogList(value: String?): List<String> =
        value
            ?.split("||")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun SQLiteDatabase.columnNames(table: String): Set<String> =
        rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(1))
                }
            }
        }

    private fun loadFacetCounts(
        normalizedQuery: String,
        filters: LibraryFilters,
        dimension: FacetDimension,
    ): List<FilterOptionCount> {
        val db = database.open()
        val allOptions = when (dimension) {
            FacetDimension.Equipment -> loadEquipmentOptions()
            FacetDimension.TargetMuscle -> loadTargetMuscleOptions()
            FacetDimension.PrimeMover -> loadPrimeMoverOptions()
            FacetDimension.LoggedHistory -> error("Logged history facets use a dedicated loader.")
            FacetDimension.RecommendationBias -> error("Recommendation bias facets use a dedicated loader.")
        }
        val clause = buildExerciseFilterClause(normalizedQuery, filters, excludeDimension = dimension)
        val query = when (dimension) {
            FacetDimension.Equipment -> """
                SELECT eq.equipment_name, COUNT(DISTINCT e.exercise_id)
                FROM exercises e
                JOIN exercise_equipment eq ON eq.exercise_id = e.exercise_id
                LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
                WHERE ${clause.whereClause}
                  AND eq.equipment_name IS NOT NULL
                  AND trim(eq.equipment_name) != ''
                GROUP BY eq.equipment_name
                ORDER BY eq.equipment_name
            """.trimIndent()
            FacetDimension.TargetMuscle -> """
                SELECT e.target_muscle_group, COUNT(DISTINCT e.exercise_id)
                FROM exercises e
                LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
                WHERE ${clause.whereClause}
                  AND e.target_muscle_group IS NOT NULL
                  AND trim(e.target_muscle_group) != ''
                GROUP BY e.target_muscle_group
                ORDER BY e.target_muscle_group
            """.trimIndent()
            FacetDimension.PrimeMover -> """
                SELECT e.prime_mover_muscle, COUNT(DISTINCT e.exercise_id)
                FROM exercises e
                LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
                WHERE ${clause.whereClause}
                  AND e.prime_mover_muscle IS NOT NULL
                  AND trim(e.prime_mover_muscle) != ''
                GROUP BY e.prime_mover_muscle
                ORDER BY e.prime_mover_muscle
            """.trimIndent()
            FacetDimension.LoggedHistory -> error("Logged history facets use a dedicated loader.")
            FacetDimension.RecommendationBias -> error("Recommendation bias facets use a dedicated loader.")
        }

        val counts = db.rawQuery(query, clause.args).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getString(0), cursor.getInt(1))
                }
            }
        }

        return allOptions.map { option ->
            FilterOptionCount(label = option, count = counts[option] ?: 0)
        }
    }

    private fun loadRecommendationBiasFacetCounts(
        normalizedQuery: String,
        filters: LibraryFilters,
    ): List<RecommendationBiasFilterOptionCount> {
        val db = database.open()
        val clause = buildExerciseFilterClause(
            normalizedQuery = normalizedQuery,
            filters = filters,
            excludeDimension = FacetDimension.RecommendationBias,
        )
        val counts = db.rawQuery(
            """
            SELECT
                CASE
                    WHEN COALESCE(p.preference_score_delta, 0) >= $RECOMMENDATION_BIAS_THRESHOLD THEN 'more_often'
                    WHEN COALESCE(p.preference_score_delta, 0) <= -$RECOMMENDATION_BIAS_THRESHOLD THEN 'less_often'
                END AS bias_key,
                COUNT(DISTINCT e.exercise_id)
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            WHERE ${clause.whereClause}
              AND ABS(COALESCE(p.preference_score_delta, 0)) >= $RECOMMENDATION_BIAS_THRESHOLD
            GROUP BY bias_key
            """.trimIndent(),
            clause.args,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val bias = when (cursor.getString(0)) {
                        "more_often" -> RecommendationBias.MoreOften
                        "less_often" -> RecommendationBias.LessOften
                        else -> null
                    } ?: continue
                    put(bias, cursor.getInt(1))
                }
            }
        }

        return recommendationBiasFacetOptions(counts)
    }

    private fun loadLoggedHistoryFacetCount(
        normalizedQuery: String,
        filters: LibraryFilters,
    ): Int {
        val db = database.open()
        val clause = buildExerciseFilterClause(
            normalizedQuery = normalizedQuery,
            filters = filters,
            excludeDimension = FacetDimension.LoggedHistory,
        )
        return db.rawQuery(
            """
            SELECT COUNT(DISTINCT e.exercise_id)
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            WHERE ${clause.whereClause}
              AND ${loggedHistoryFilterClause()}
            """.trimIndent(),
            clause.args,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private data class ExerciseDiscoveryRow(
        val summary: ExerciseSummary,
        val primeMover: String?,
        val secondaryMuscle: String?,
        val tertiaryMuscle: String?,
        val posture: String?,
        val laterality: String?,
        val classification: String?,
    )

    private fun loadPerformedExerciseDiscoveryExercises(limit: Int): List<ExerciseDiscoveryExercise> {
        val db = database.open()
        return loadExerciseDiscoveryExercises(
            sql = """
                ${exerciseDiscoverySelectSql()}
                WHERE COALESCE(p.is_hidden, 0) = 0
                  AND COALESCE(p.is_banned, 0) = 0
                  AND COALESCE(logged_history.logged_session_count, 0) > 0
                ORDER BY COALESCE(logged_history.logged_session_count, 0) DESC, COALESCE(p.is_favorite, 0) DESC, e.name ASC
                LIMIT ?
            """.trimIndent(),
            args = arrayOf(limit.toString()),
            db = db,
        )
    }

    private fun loadFilteredExerciseDiscoveryExercises(
        clause: SqlClause,
        loggedSessionPredicate: String,
        orderBy: String,
        limit: Int,
    ): List<ExerciseDiscoveryExercise> {
        val db = database.open()
        return loadExerciseDiscoveryExercises(
            sql = """
                ${exerciseDiscoverySelectSql()}
                WHERE ${clause.whereClause}
                  AND $loggedSessionPredicate
                ORDER BY $orderBy
                LIMIT ?
            """.trimIndent(),
            args = clause.args + limit.toString(),
            db = db,
        )
    }

    private fun countPerformedExerciseDiscoveryExercises(): Int {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT COUNT(DISTINCT e.exercise_id)
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            ${loggedSessionCountJoin()}
            WHERE COALESCE(p.is_hidden, 0) = 0
              AND COALESCE(p.is_banned, 0) = 0
              AND COALESCE(logged_history.logged_session_count, 0) > 0
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun countFilteredExerciseDiscoveryExercises(
        clause: SqlClause,
        loggedSessionPredicate: String? = null,
    ): Int {
        val db = database.open()
        return db.rawQuery(
            buildString {
                append(
                    """
                    SELECT COUNT(DISTINCT e.exercise_id)
                    FROM exercises e
                    LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
                    """.trimIndent(),
                )
                if (loggedSessionPredicate != null) {
                    append('\n')
                    append(loggedSessionCountJoin())
                }
                append("\nWHERE ${clause.whereClause}")
                if (loggedSessionPredicate != null) {
                    append("\n  AND $loggedSessionPredicate")
                }
            },
            clause.args,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun loadExerciseDiscoveryExercises(
        sql: String,
        args: Array<String>,
        db: SQLiteDatabase,
    ): List<ExerciseDiscoveryExercise> {
        val rows = db.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ExerciseDiscoveryRow(
                            summary = ExerciseSummary(
                                id = cursor.getLong(0),
                                name = cursor.getString(1),
                                difficulty = cursor.getString(2),
                                bodyRegion = cursor.getString(3),
                                targetMuscleGroup = cursor.getString(4),
                                equipment = cursor.getString(5),
                                secondaryEquipment = cursor.getStringOrNull(6),
                                mechanics = cursor.getStringOrNull(7),
                                favorite = cursor.getInt(8) == 1,
                                hidden = cursor.getInt(9) == 1,
                                banned = cursor.getInt(10) == 1,
                                preferenceScoreDelta = cursor.getDouble(11),
                                recommendationBias = RecommendationBias.fromScoreDelta(cursor.getDouble(11)),
                                loggedSessionCount = cursor.getInt(12),
                            ),
                            primeMover = cursor.getStringOrNull(13),
                            secondaryMuscle = cursor.getStringOrNull(14),
                            tertiaryMuscle = cursor.getStringOrNull(15),
                            posture = cursor.getStringOrNull(16),
                            laterality = cursor.getStringOrNull(17),
                            classification = cursor.getStringOrNull(18),
                        ),
                    )
                }
            }
        }
        val exerciseIds = rows.map { it.summary.id }
        val movementPatterns = loadExerciseDiscoveryStringLists(
            db = db,
            tableName = "exercise_movement_patterns",
            valueColumn = "movement_pattern",
            orderColumn = "sequence_no",
            exerciseIds = exerciseIds,
        )
        val planesOfMotion = loadExerciseDiscoveryStringLists(
            db = db,
            tableName = "exercise_planes_of_motion",
            valueColumn = "plane_of_motion",
            orderColumn = "sequence_no",
            exerciseIds = exerciseIds,
        )
        return rows.map { row ->
            ExerciseDiscoveryExercise(
                summary = row.summary,
                primeMover = row.primeMover,
                secondaryMuscle = row.secondaryMuscle,
                tertiaryMuscle = row.tertiaryMuscle,
                posture = row.posture,
                laterality = row.laterality,
                classification = row.classification,
                movementPatterns = movementPatterns[row.summary.id].orEmpty(),
                planesOfMotion = planesOfMotion[row.summary.id].orEmpty(),
            )
        }
    }

    private fun loadExerciseDiscoveryStringLists(
        db: SQLiteDatabase,
        tableName: String,
        valueColumn: String,
        orderColumn: String,
        exerciseIds: List<Long>,
    ): Map<Long, List<String>> {
        if (exerciseIds.isEmpty()) return emptyMap()
        val values = mutableMapOf<Long, MutableList<String>>()
        exerciseIds.distinct().chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            db.rawQuery(
                """
                SELECT exercise_id, $valueColumn
                FROM $tableName
                WHERE exercise_id IN ($placeholders)
                  AND $valueColumn IS NOT NULL
                  AND trim($valueColumn) != ''
                ORDER BY exercise_id, $orderColumn
                """.trimIndent(),
                chunk.map(Long::toString).toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    values.getOrPut(cursor.getLong(0)) { mutableListOf() } += cursor.getString(1)
                }
            }
        }
        return values
    }

    private fun exerciseDiscoverySelectSql(): String {
        return """
            SELECT
                e.exercise_id,
                e.name,
                e.difficulty_level,
                e.body_region,
                e.target_muscle_group,
                COALESCE(e.primary_equipment, 'Bodyweight'),
                e.secondary_equipment,
                e.mechanics,
                COALESCE(p.is_favorite, 0),
                COALESCE(p.is_hidden, 0),
                COALESCE(p.is_banned, 0),
                COALESCE(p.preference_score_delta, 0),
                COALESCE(logged_history.logged_session_count, 0),
                e.prime_mover_muscle,
                e.secondary_muscle,
                e.tertiary_muscle,
                e.posture,
                e.laterality,
                e.primary_exercise_classification
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
            ${loggedSessionCountJoin()}
        """.trimIndent()
    }

    private fun buildExerciseDiscoveryFilterLabels(query: String, filters: LibraryFilters): List<String> {
        return buildList {
            query.takeIf { it.isNotBlank() }?.let { add("Search: $it") }
            filters.equipmentLocation?.let { add("${it.displayName} equipment") }
            filters.equipment.sorted().forEach { add("Equipment: $it") }
            filters.targetMuscles.sorted().forEach { add("Target: $it") }
            filters.primeMovers.sorted().forEach { add("Primary mover: $it") }
            filters.freshnessMuscleKeys.sorted().forEach { add("Freshness muscle: $it") }
            filters.muscleTargetBucketKeys.sorted().forEach { add("Muscle target bucket: $it") }
            filters.muscleTargetSubcategoryKeys.sorted().forEach { add("Muscle target: $it") }
            filters.recommendationBiases.sortedBy { it.name }.forEach { add(it.filterLabel) }
            if (filters.hasLoggedHistoryOnly) add("Logged before")
            if (filters.favoritesOnly) add("Favorites only")
        }
    }

    private fun buildExerciseFilterClause(
        normalizedQuery: String,
        filters: LibraryFilters,
        excludeDimension: FacetDimension? = null,
    ): SqlClause {
        val clauses = mutableListOf(
            "COALESCE(p.is_hidden, 0) = 0",
            "COALESCE(p.is_banned, 0) = 0",
        )
        val args = mutableListOf<String>()

        if (filters.favoritesOnly) {
            clauses += "COALESCE(p.is_favorite, 0) = 1"
        }

        if (normalizedQuery.isNotBlank()) {
            val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
            clauses += queryTokens.joinToString("\nAND ") {
                """
                (
                    lower(e.name) LIKE '%' || ? || '%'
                    OR replace(replace(lower(e.name), '-', ' '), '  ', ' ') LIKE '%' || ? || '%'
                    OR EXISTS (
                        SELECT 1
                        FROM exercise_synonyms s
                        WHERE s.exercise_id = e.exercise_id
                          AND s.synonym_name_normalized LIKE '%' || ? || '%'
                    )
                )
                """.trimIndent()
            }
            queryTokens.forEach { token ->
                repeat(3) { args += token }
            }
        }

        if (excludeDimension != FacetDimension.Equipment && filters.equipment.isNotEmpty()) {
            val placeholders = filters.equipment.joinToString(",") { "?" }
            clauses += """
                EXISTS (
                    SELECT 1
                    FROM exercise_equipment eq
                    WHERE eq.exercise_id = e.exercise_id
                      AND eq.equipment_name IN ($placeholders)
                )
            """.trimIndent()
            args += filters.equipment.sorted()
        }

        if (filters.equipmentLocation != null) {
            val locationEquipment = normalizeLibraryEquipmentLocationEquipment(filters.equipmentLocationEquipment)
            clauses += libraryEquipmentLocationFilterClause(locationEquipment)
            if (locationEquipment.isNotEmpty()) {
                repeat(3) {
                    args += locationEquipment
                }
            }
        }

        if (excludeDimension != FacetDimension.TargetMuscle && filters.targetMuscles.isNotEmpty()) {
            val placeholders = filters.targetMuscles.joinToString(",") { "?" }
            clauses += "e.target_muscle_group IN ($placeholders)"
            args += filters.targetMuscles.sorted()
        }

        if (excludeDimension != FacetDimension.PrimeMover && filters.primeMovers.isNotEmpty()) {
            val placeholders = filters.primeMovers.joinToString(",") { "?" }
            clauses += "COALESCE(e.prime_mover_muscle, '') IN ($placeholders)"
            args += filters.primeMovers.sorted()
        }

        libraryFreshnessMuscleFilterClause(filters.freshnessMuscleKeys)?.let { clauses += it }
        libraryMuscleTargetFilterClause(
            bucketKeys = filters.muscleTargetBucketKeys,
            subcategoryKeys = filters.muscleTargetSubcategoryKeys,
        )?.let { clauses += it }

        if (excludeDimension != FacetDimension.RecommendationBias && filters.recommendationBiases.isNotEmpty()) {
            recommendationBiasFilterClause(filters.recommendationBiases)?.let { clauses += it }
        }

        if (excludeDimension != FacetDimension.LoggedHistory && filters.hasLoggedHistoryOnly) {
            clauses += loggedHistoryFilterClause()
        }

        return SqlClause(
            whereClause = clauses.joinToString("\nAND "),
            args = args.toTypedArray(),
        )
    }

    private fun loadDistinctColumnValues(columnName: String): List<String> {
        val db = database.open()
        return db.rawQuery(
            """
            SELECT DISTINCT $columnName
            FROM exercises
            WHERE $columnName IS NOT NULL AND trim($columnName) != ''
            ORDER BY $columnName
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun normalizeQuery(query: String): String =
        query.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}

internal fun libraryFreshnessMuscleFilterClause(
    muscleKeys: Set<String>,
    tableAlias: String = "e",
): String? {
    val clauses = muscleKeys
        .mapNotNull { key -> freshnessMuscleSqlTerms[key.trim().lowercase()] }
        .map { terms -> freshnessMuscleTermsClause(terms, tableAlias) }
        .distinct()
    if (clauses.isEmpty()) return null
    return clauses.joinToString(
        prefix = "(",
        separator = " OR ",
        postfix = ")",
    )
}

private fun freshnessMuscleTermsClause(
    terms: List<String>,
    tableAlias: String,
): String {
    val columns = listOf(
        "$tableAlias.target_muscle_group",
        "$tableAlias.prime_mover_muscle",
        "$tableAlias.secondary_muscle",
        "$tableAlias.tertiary_muscle",
    )
    val termClauses = terms.flatMap { term ->
        columns.map { column -> "lower(COALESCE($column, '')) LIKE '%$term%'" }
    }
    return termClauses.joinToString(
        prefix = "(",
        separator = " OR ",
        postfix = ")",
    )
}

internal fun librarySearchOrderBy(filters: LibraryFilters): String {
    val originalOrder = "COALESCE(p.is_favorite, 0) DESC, e.name ASC"
    val targetOrder = libraryMuscleTargetOrderBy(filters)
    return if (filters.activeCount() > 0) {
        listOfNotNull(
            targetOrder,
            "COALESCE(logged_history.logged_session_count, 0) DESC",
            originalOrder,
        ).joinToString(", ")
    } else {
        originalOrder
    }
}

internal fun normalizeLibraryEquipmentLocationEquipment(equipment: Collection<String>): List<String> {
    return equipment
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
}

internal fun libraryEquipmentLocationFilterClause(
    equipment: Collection<String>,
    tableAlias: String = "e",
): String {
    val normalizedEquipment = normalizeLibraryEquipmentLocationEquipment(equipment)
    if (normalizedEquipment.isEmpty()) return "0 = 1"
    val placeholders = normalizedEquipment.joinToString(",") { "?" }
    return """
        (
            NOT EXISTS (
                SELECT 1
                FROM exercise_equipment location_eq
                WHERE location_eq.exercise_id = $tableAlias.exercise_id
                  AND location_eq.equipment_name IS NOT NULL
                  AND trim(location_eq.equipment_name) != ''
                  AND location_eq.equipment_name NOT IN ($placeholders)
            )
            AND (
                COALESCE($tableAlias.primary_equipment, 'Bodyweight') IN ($placeholders)
                OR EXISTS (
                    SELECT 1
                    FROM exercise_equipment location_match_eq
                    WHERE location_match_eq.exercise_id = $tableAlias.exercise_id
                      AND location_match_eq.equipment_name IN ($placeholders)
                )
            )
        )
    """.trimIndent()
}

internal fun libraryMuscleTargetFilterClause(
    bucketKeys: Set<String>,
    subcategoryKeys: Set<String>,
    tableAlias: String = "e",
): String? {
    val clauses = selectedMuscleTargetSqlTermGroups(bucketKeys, subcategoryKeys)
        .map { terms -> muscleTargetTermsClause(terms, tableAlias) }
        .distinct()
    if (clauses.isEmpty()) return null
    return clauses.joinToString(
        prefix = "(",
        separator = " OR ",
        postfix = ")",
    )
}

private fun libraryMuscleTargetOrderBy(filters: LibraryFilters): String? {
    val subcategories = selectedMuscleTargetSubcategories(
        bucketKeys = filters.muscleTargetBucketKeys,
        subcategoryKeys = filters.muscleTargetSubcategoryKeys,
    )
    val terms = subcategories.flatMap(MuscleTargetSubcategory::sqlTerms).distinct()
    if (terms.isEmpty()) return null
    val exactTargetGroups = subcategories.map { it.label.lowercase() }.distinct()

    fun columnsClause(columns: List<String>): String {
        return terms.flatMap { term ->
            columns.map { column -> "lower(COALESCE($column, '')) LIKE '%$term%'" }
        }.joinToString(prefix = "(", separator = " OR ", postfix = ")")
    }

    val exactTarget = exactTargetGroups
        .joinToString(prefix = "(", separator = " OR ", postfix = ")") { targetGroup ->
            "lower(COALESCE(e.target_muscle_group, '')) = '$targetGroup'"
        }
    val prime = columnsClause(listOf("e.prime_mover_muscle"))
    val target = columnsClause(listOf("e.target_muscle_group"))
    val secondary = columnsClause(listOf("e.secondary_muscle"))
    val tertiary = columnsClause(listOf("e.tertiary_muscle"))
    return """
        CASE
            WHEN $exactTarget THEN 0
            WHEN $prime THEN 1
            WHEN $target THEN 2
            WHEN $secondary THEN 3
            WHEN $tertiary THEN 4
            ELSE 5
        END ASC
    """.trimIndent().replace("\n", " ")
}

private fun selectedMuscleTargetSqlTermGroups(
    bucketKeys: Set<String>,
    subcategoryKeys: Set<String>,
): List<List<String>> {
    return selectedMuscleTargetSubcategories(bucketKeys, subcategoryKeys)
        .map(MuscleTargetSubcategory::sqlTerms)
        .filter { it.isNotEmpty() }
}

private fun selectedMuscleTargetSubcategories(
    bucketKeys: Set<String>,
    subcategoryKeys: Set<String>,
): List<MuscleTargetSubcategory> {
    return muscleTargetBucketSubcategoryKeys(bucketKeys, subcategoryKeys)
        .mapNotNull(::muscleTargetSubcategory)
}

private fun muscleTargetTermsClause(
    terms: List<String>,
    tableAlias: String,
): String {
    val columns = listOf(
        "$tableAlias.target_muscle_group",
        "$tableAlias.prime_mover_muscle",
        "$tableAlias.secondary_muscle",
        "$tableAlias.tertiary_muscle",
    )
    val termClauses = terms.flatMap { term ->
        columns.map { column -> "lower(COALESCE($column, '')) LIKE '%$term%'" }
    }
    return termClauses.joinToString(
        prefix = "(",
        separator = " OR ",
        postfix = ")",
    )
}

private val freshnessMuscleSqlTerms = mapOf(
    "chest" to listOf("pec", "chest"),
    "back" to listOf("back", "latissimus", "lats", "trap", "rhomboid", "rear delt", "posterior delt"),
    "shoulders" to listOf("shoulder", "delt"),
    "triceps" to listOf("tricep"),
    "biceps" to listOf("bicep", "brachialis", "brachioradialis"),
    "forearms" to listOf("forearm"),
    "quadriceps" to listOf("quad", "vastus", "rectus femoris"),
    "hamstrings" to listOf("hamstring", "biceps femoris", "semitendinosus", "semimembranosus"),
    "glutes" to listOf("glute"),
    "calves" to listOf("calf", "gastrocnemius", "soleus"),
    "adductors" to listOf("adductor"),
    "abductors" to listOf("abductor"),
    "core" to listOf("abdom", "oblique", "transverse abdominis"),
    "erector_spinae" to listOf("erector", "lower back"),
)

internal fun recommendationBiasFilterClause(
    selectedBiases: Set<RecommendationBias>,
    scoreColumn: String = "COALESCE(p.preference_score_delta, 0)",
): String? {
    if (selectedBiases.isEmpty()) return null

    val recommendationClauses = buildList {
        if (RecommendationBias.MoreOften in selectedBiases) {
            add("$scoreColumn >= $RECOMMENDATION_BIAS_THRESHOLD")
        }
        if (RecommendationBias.LessOften in selectedBiases) {
            add("$scoreColumn <= -$RECOMMENDATION_BIAS_THRESHOLD")
        }
    }
    if (recommendationClauses.isEmpty()) return null

    return recommendationClauses.joinToString(
        prefix = "(",
        separator = " OR ",
        postfix = ")",
    )
}

internal fun recommendationBiasFacetOptions(
    counts: Map<RecommendationBias, Int>,
): List<RecommendationBiasFilterOptionCount> {
    return RecommendationBias.filterableEntries.map { bias ->
        RecommendationBiasFilterOptionCount(bias = bias, count = counts[bias] ?: 0)
    }
}

internal fun loggedHistoryFilterClause(
    exerciseIdColumn: String = "e.exercise_id",
): String {
    return listOf(
        "EXISTS (",
        "    SELECT 1",
        "    FROM performed_exercises pe",
        "    INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id",
        "    WHERE pe.exercise_id = $exerciseIdColumn",
        "      AND ps.is_completed = 1",
        "      AND COALESCE(ps.actual_reps, 0) > 0",
        ")",
    ).joinToString("\n")
}
