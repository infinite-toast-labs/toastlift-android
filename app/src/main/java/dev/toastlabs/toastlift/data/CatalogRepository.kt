package dev.toastlabs.toastlift.data

import android.database.sqlite.SQLiteDatabase
import java.net.URI
import java.time.Instant

internal fun normalizeExerciseNote(rawValue: String): String? =
    rawValue.trim().takeIf { it.isNotEmpty() }

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
                    COALESCE(p.preference_score_delta, 0)
                FROM exercises e
                LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
                WHERE
                """.trimIndent(),
            )
            append('\n')
            append(clause.whereClause)
            append("\nORDER BY COALESCE(p.is_favorite, 0) DESC, e.name ASC")
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
                ${descriptionColumn?.let { "e.$it" } ?: "NULL"} AS exercise_description
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
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
                description = normalizeExerciseDescription(cursor.getStringOrNull(21)),
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

        return summary.copy(
            movementPatterns = movementPatterns,
            planesOfMotion = planes,
            synonyms = synonyms,
            defaultVideoLinks = defaultVideoLinks,
            userVideoLinks = userVideoLinks,
        )
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
                COALESCE(p.preference_score_delta, 0)
            FROM exercises e
            LEFT JOIN exercise_preferences p ON p.exercise_id = e.exercise_id
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
    return """
        EXISTS (
            SELECT 1
            FROM performed_exercises pe
            INNER JOIN performed_sets ps ON ps.performed_exercise_id = pe.performed_exercise_id
            WHERE pe.exercise_id = $exerciseIdColumn
              AND ps.is_completed = 1
        )
    """.trimIndent()
}
