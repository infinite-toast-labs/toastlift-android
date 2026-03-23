package dev.toastlabs.toastlift.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.Instant
import java.util.Locale

class ToastLiftDatabase(private val context: Context) {
    private val databaseName = "toastlift.db"
    private val assetName = "functional_fitness_workout_generator.sqlite"
    private val appVersion = 12

    @Volatile
    private var database: SQLiteDatabase? = null

    fun open(): SQLiteDatabase {
        val existing = database
        if (existing != null && existing.isOpen) return existing

        synchronized(this) {
            val secondCheck = database
            if (secondCheck != null && secondCheck.isOpen) return secondCheck

            val dbFile = context.getDatabasePath(databaseName)
            if (!dbFile.exists()) {
                copyAssetDatabase(dbFile)
            }

            val opened = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            opened.execSQL("PRAGMA foreign_keys = ON")
            if (opened.version < appVersion) {
                migrate(opened)
                opened.version = appVersion
            } else {
                ensureUserTables(opened)
                syncBundledCatalogIfNeeded(opened)
            }
            database = opened
            return opened
        }
    }

    fun ensureGeneratedSynonyms() {
        synchronized(this) {
            seedGeneratedSynonymsIfEmpty(open())
        }
    }

    private fun copyAssetDatabase(target: File) {
        target.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun migrate(db: SQLiteDatabase) {
        ensureUserTables(db)
        ensureCatalogColumns(db)
        syncBundledCatalogIfNeeded(db)
    }

    private fun syncBundledCatalogIfNeeded(db: SQLiteDatabase) {
        val assetCopy = File(context.cacheDir, assetName)
        copyAssetDatabase(assetCopy)

        val assetDb = SQLiteDatabase.openDatabase(
            assetCopy.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )

        try {
            if (!catalogSyncNeeded(db, assetDb)) {
                return
            }

            db.beginTransaction()
            try {
                db.execSQL("ATTACH DATABASE ? AS asset_catalog", arrayOf(assetCopy.absolutePath))
                val tables = listOf(
                    "exercises",
                    "exercise_muscles",
                    "exercise_equipment",
                    "exercise_movement_patterns",
                    "exercise_planes_of_motion",
                    "exercise_synonyms",
                    "import_metadata",
                )
                tables.forEach { table ->
                    db.execSQL("INSERT OR REPLACE INTO $table SELECT * FROM asset_catalog.$table")
                }
                db.execSQL("DETACH DATABASE asset_catalog")
                canonicalizeBundledCustomExercises(db)
                seedSystemData(db)
                db.setTransactionSuccessful()
            } finally {
                if (db.inTransaction()) {
                    db.endTransaction()
                }
                safelyDetachCatalog(db)
            }
        } finally {
            assetDb.close()
            assetCopy.delete()
        }
    }

    private fun catalogSyncNeeded(currentDb: SQLiteDatabase, assetDb: SQLiteDatabase): Boolean {
        val currentCount = currentDb.simpleLongForQuery(
            """
            SELECT COUNT(*)
            FROM exercises
            WHERE COALESCE(is_post_install_llm_generated, 0) = 0
            """.trimIndent(),
        )
        val assetCount = assetDb.simpleLongForQuery(
            """
            SELECT COUNT(*)
            FROM exercises
            WHERE COALESCE(is_post_install_llm_generated, 0) = 0
            """.trimIndent(),
        )
        if (currentCount != assetCount) return true

        val currentMergeCount = currentDb.singleStringOrNull(
            """
            SELECT metadata_value
            FROM import_metadata
            WHERE metadata_key = 'merge_free_exercise_db_machine_imported_count'
            """.trimIndent(),
        )
        val assetMergeCount = assetDb.singleStringOrNull(
            """
            SELECT metadata_value
            FROM import_metadata
            WHERE metadata_key = 'merge_free_exercise_db_machine_imported_count'
            """.trimIndent(),
        )
        if (currentMergeCount != assetMergeCount) return true

        val currentSourceFilename = currentDb.singleStringOrNull(
            """
            SELECT metadata_value
            FROM import_metadata
            WHERE metadata_key = 'source_filename'
            """.trimIndent(),
        )
        val assetSourceFilename = assetDb.singleStringOrNull(
            """
            SELECT metadata_value
            FROM import_metadata
            WHERE metadata_key = 'source_filename'
            """.trimIndent(),
        )
        return currentSourceFilename != assetSourceFilename
    }

    private fun safelyDetachCatalog(db: SQLiteDatabase) {
        try {
            db.execSQL("DETACH DATABASE asset_catalog")
        } catch (_: Exception) {
        }
    }

    private fun ensureUserTables(db: SQLiteDatabase) {
        ensureCatalogColumns(db)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS training_split_programs (
                split_program_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                description TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS location_modes (
                location_mode_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                is_default INTEGER NOT NULL CHECK (is_default IN (0, 1))
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS equipment_inventory (
                location_mode_id INTEGER NOT NULL,
                equipment_name TEXT NOT NULL,
                is_available INTEGER NOT NULL CHECK (is_available IN (0, 1)),
                PRIMARY KEY (location_mode_id, equipment_name),
                FOREIGN KEY (location_mode_id) REFERENCES location_modes (location_mode_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_profile (
                user_id INTEGER PRIMARY KEY CHECK (user_id = 1),
                goal_primary TEXT NOT NULL,
                experience_level TEXT NOT NULL,
                default_duration_minutes INTEGER NOT NULL,
                weekly_frequency_target INTEGER NOT NULL,
                preferred_split_program_id INTEGER NOT NULL,
                units TEXT NOT NULL,
                active_location_mode_id INTEGER NOT NULL,
                preferred_workout_style TEXT NOT NULL,
                theme_preference TEXT NOT NULL DEFAULT 'dark',
                gym_machine_cable_bias_enabled INTEGER NOT NULL DEFAULT 1,
                history_workout_ab_flags_visible INTEGER NOT NULL DEFAULT 0,
                dev_pick_next_exercise_enabled INTEGER NOT NULL DEFAULT 0,
                dev_fruit_exercise_icons_enabled INTEGER NOT NULL DEFAULT 0,
                next_focus TEXT NOT NULL DEFAULT 'full_body',
                created_at_utc TEXT NOT NULL,
                updated_at_utc TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS experiment_assignments (
                experiment_key TEXT PRIMARY KEY,
                variant_key TEXT NOT NULL,
                assigned_at_utc TEXT NOT NULL
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "user_profile",
            column = "theme_preference",
            definition = "TEXT NOT NULL DEFAULT 'dark'",
        )
        ensureColumn(
            db = db,
            table = "user_profile",
            column = "gym_machine_cable_bias_enabled",
            definition = "INTEGER NOT NULL DEFAULT 1",
        )
        ensureColumn(
            db = db,
            table = "user_profile",
            column = "history_workout_ab_flags_visible",
            definition = "INTEGER NOT NULL DEFAULT 0",
        )
        ensureColumn(
            db = db,
            table = "user_profile",
            column = "dev_pick_next_exercise_enabled",
            definition = "INTEGER NOT NULL DEFAULT 0",
        )
        ensureColumn(
            db = db,
            table = "user_profile",
            column = "dev_fruit_exercise_icons_enabled",
            definition = "INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS movement_restrictions (
                restriction_id INTEGER PRIMARY KEY AUTOINCREMENT,
                restriction_scope TEXT NOT NULL,
                restriction_value TEXT NOT NULL,
                severity TEXT NOT NULL,
                notes TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exercise_preferences (
                exercise_id INTEGER PRIMARY KEY,
                is_favorite INTEGER NOT NULL CHECK (is_favorite IN (0, 1)) DEFAULT 0,
                is_hidden INTEGER NOT NULL CHECK (is_hidden IN (0, 1)) DEFAULT 0,
                is_banned INTEGER NOT NULL CHECK (is_banned IN (0, 1)) DEFAULT 0,
                preference_score_delta REAL NOT NULL DEFAULT 0,
                notes TEXT,
                updated_at_utc TEXT NOT NULL
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "exercise_preferences",
            column = "notes",
            definition = "TEXT",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exercise_synonyms (
                synonym_id INTEGER PRIMARY KEY AUTOINCREMENT,
                exercise_id INTEGER NOT NULL,
                synonym_name TEXT NOT NULL,
                synonym_name_normalized TEXT NOT NULL,
                synonym_type TEXT NOT NULL,
                source TEXT NOT NULL,
                confidence_score REAL,
                created_at_utc TEXT NOT NULL,
                UNIQUE (exercise_id, synonym_name_normalized),
                FOREIGN KEY (exercise_id) REFERENCES exercises (exercise_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_templates (
                template_id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                origin_type TEXT NOT NULL,
                created_at_utc TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_template_exercises (
                template_exercise_id INTEGER PRIMARY KEY AUTOINCREMENT,
                template_id INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                exercise_id INTEGER NOT NULL,
                set_count INTEGER NOT NULL,
                rep_range TEXT NOT NULL,
                rest_seconds INTEGER NOT NULL,
                rationale TEXT NOT NULL,
                FOREIGN KEY (template_id) REFERENCES workout_templates (template_id) ON DELETE CASCADE,
                FOREIGN KEY (exercise_id) REFERENCES exercises (exercise_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_feedback_signals (
                signal_id INTEGER PRIMARY KEY AUTOINCREMENT,
                signal_type TEXT NOT NULL,
                workout_origin_type TEXT NOT NULL,
                workout_title TEXT NOT NULL,
                workout_focus_key TEXT,
                session_started_at_utc TEXT,
                exercise_id INTEGER NOT NULL,
                exercise_name TEXT NOT NULL,
                signal_value REAL NOT NULL DEFAULT 0,
                resulting_preference_score_delta REAL NOT NULL DEFAULT 0,
                created_at_utc TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_workout_feedback_signals_exercise_created ON workout_feedback_signals (exercise_id, created_at_utc DESC)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS performed_workouts (
                performed_workout_id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                origin_type TEXT NOT NULL,
                location_mode_id INTEGER NOT NULL,
                started_at_utc TEXT NOT NULL,
                completed_at_utc TEXT NOT NULL,
                actual_duration_seconds INTEGER NOT NULL,
                ab_flags_snapshot_json TEXT
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "performed_workouts",
            column = "ab_flags_snapshot_json",
            definition = "TEXT",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS performed_exercises (
                performed_exercise_id INTEGER PRIMARY KEY AUTOINCREMENT,
                performed_workout_id INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                exercise_id INTEGER NOT NULL,
                exercise_name TEXT NOT NULL,
                FOREIGN KEY (performed_workout_id) REFERENCES performed_workouts (performed_workout_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "performed_exercises",
            column = "last_set_reps_in_reserve",
            definition = "INTEGER",
        )
        ensureColumn(
            db = db,
            table = "performed_exercises",
            column = "last_set_rpe",
            definition = "REAL",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS performed_sets (
                performed_set_id INTEGER PRIMARY KEY AUTOINCREMENT,
                performed_exercise_id INTEGER NOT NULL,
                set_number INTEGER NOT NULL,
                target_reps TEXT NOT NULL,
                actual_reps INTEGER,
                weight_value REAL,
                is_completed INTEGER NOT NULL CHECK (is_completed IN (0, 1)),
                FOREIGN KEY (performed_exercise_id) REFERENCES performed_exercises (performed_exercise_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "performed_sets",
            column = "recommended_reps",
            definition = "INTEGER",
        )
        ensureColumn(
            db = db,
            table = "performed_sets",
            column = "recommended_weight_value",
            definition = "REAL",
        )
        ensureColumn(
            db = db,
            table = "performed_sets",
            column = "recommendation_source",
            definition = "TEXT NOT NULL DEFAULT 'NONE'",
        )
        ensureColumn(
            db = db,
            table = "performed_sets",
            column = "recommendation_confidence",
            definition = "REAL",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_performed_exercises_exercise_id ON performed_exercises (exercise_id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_performed_sets_exercise_completed ON performed_sets (performed_exercise_id, is_completed)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS abandoned_workouts (
                abandoned_workout_id INTEGER PRIMARY KEY CHECK (abandoned_workout_id = 1),
                title TEXT NOT NULL,
                origin_type TEXT NOT NULL,
                location_mode_id INTEGER NOT NULL,
                started_at_utc TEXT NOT NULL,
                focus_key TEXT,
                subtitle TEXT NOT NULL DEFAULT '',
                estimated_minutes INTEGER,
                session_format TEXT
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "abandoned_workouts",
            column = "subtitle",
            definition = "TEXT NOT NULL DEFAULT ''",
        )
        ensureColumn(
            db = db,
            table = "abandoned_workouts",
            column = "estimated_minutes",
            definition = "INTEGER",
        )
        ensureColumn(
            db = db,
            table = "abandoned_workouts",
            column = "session_format",
            definition = "TEXT",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS abandoned_exercises (
                abandoned_exercise_id INTEGER PRIMARY KEY AUTOINCREMENT,
                abandoned_workout_id INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                exercise_id INTEGER NOT NULL,
                exercise_name TEXT NOT NULL,
                body_region TEXT NOT NULL,
                target_muscle_group TEXT NOT NULL,
                equipment TEXT NOT NULL,
                rest_seconds INTEGER NOT NULL,
                notes TEXT NOT NULL,
                fruit_icon TEXT,
                FOREIGN KEY (abandoned_workout_id) REFERENCES abandoned_workouts (abandoned_workout_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "abandoned_exercises",
            column = "last_set_reps_in_reserve",
            definition = "INTEGER",
        )
        ensureColumn(
            db = db,
            table = "abandoned_exercises",
            column = "fruit_icon",
            definition = "TEXT",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS abandoned_sets (
                abandoned_set_id INTEGER PRIMARY KEY AUTOINCREMENT,
                abandoned_exercise_id INTEGER NOT NULL,
                set_stable_id INTEGER NOT NULL,
                set_number INTEGER NOT NULL,
                target_reps TEXT NOT NULL,
                actual_reps TEXT NOT NULL,
                weight_value TEXT NOT NULL,
                is_completed INTEGER NOT NULL CHECK (is_completed IN (0, 1)),
                FOREIGN KEY (abandoned_exercise_id) REFERENCES abandoned_exercises (abandoned_exercise_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "abandoned_sets",
            column = "recommended_reps",
            definition = "INTEGER",
        )
        ensureColumn(
            db = db,
            table = "abandoned_sets",
            column = "recommended_weight_value",
            definition = "TEXT NOT NULL DEFAULT ''",
        )
        ensureColumn(
            db = db,
            table = "abandoned_sets",
            column = "recommendation_source",
            definition = "TEXT NOT NULL DEFAULT 'NONE'",
        )
        ensureColumn(
            db = db,
            table = "abandoned_sets",
            column = "recommendation_confidence",
            definition = "REAL",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS active_workouts (
                active_workout_id INTEGER PRIMARY KEY CHECK (active_workout_id = 1),
                title TEXT NOT NULL,
                origin_type TEXT NOT NULL,
                location_mode_id INTEGER NOT NULL,
                started_at_utc TEXT NOT NULL,
                focus_key TEXT,
                subtitle TEXT NOT NULL DEFAULT '',
                estimated_minutes INTEGER,
                session_format TEXT,
                selected_exercise_index INTEGER
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "active_workouts",
            column = "subtitle",
            definition = "TEXT NOT NULL DEFAULT ''",
        )
        ensureColumn(
            db = db,
            table = "active_workouts",
            column = "estimated_minutes",
            definition = "INTEGER",
        )
        ensureColumn(
            db = db,
            table = "active_workouts",
            column = "session_format",
            definition = "TEXT",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS active_exercises (
                active_exercise_id INTEGER PRIMARY KEY AUTOINCREMENT,
                active_workout_id INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                exercise_id INTEGER NOT NULL,
                exercise_name TEXT NOT NULL,
                body_region TEXT NOT NULL,
                target_muscle_group TEXT NOT NULL,
                equipment TEXT NOT NULL,
                rest_seconds INTEGER NOT NULL,
                notes TEXT NOT NULL,
                last_set_reps_in_reserve INTEGER,
                fruit_icon TEXT,
                FOREIGN KEY (active_workout_id) REFERENCES active_workouts (active_workout_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "active_exercises",
            column = "completion_sequence",
            definition = "INTEGER",
        )
        ensureColumn(
            db = db,
            table = "active_exercises",
            column = "fruit_icon",
            definition = "TEXT",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS active_sets (
                active_set_id INTEGER PRIMARY KEY AUTOINCREMENT,
                active_exercise_id INTEGER NOT NULL,
                set_stable_id INTEGER NOT NULL,
                set_number INTEGER NOT NULL,
                target_reps TEXT NOT NULL,
                actual_reps TEXT NOT NULL,
                weight_value TEXT NOT NULL,
                is_completed INTEGER NOT NULL CHECK (is_completed IN (0, 1)),
                FOREIGN KEY (active_exercise_id) REFERENCES active_exercises (active_exercise_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        ensureColumn(
            db = db,
            table = "active_sets",
            column = "recommended_reps",
            definition = "INTEGER",
        )
        ensureColumn(
            db = db,
            table = "active_sets",
            column = "recommended_weight_value",
            definition = "TEXT NOT NULL DEFAULT ''",
        )
        ensureColumn(
            db = db,
            table = "active_sets",
            column = "recommendation_source",
            definition = "TEXT NOT NULL DEFAULT 'NONE'",
        )
        ensureColumn(
            db = db,
            table = "active_sets",
            column = "recommendation_confidence",
            definition = "REAL",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_exercise_synonyms_norm ON exercise_synonyms (synonym_name_normalized)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_exercises_prime_mover ON exercises (prime_mover_muscle)",
        )

        // ── Adaptive Program Engine tables ──

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS training_programs (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                goal TEXT NOT NULL,
                primary_outcome_metric TEXT NOT NULL,
                program_archetype TEXT NOT NULL,
                periodization_model TEXT NOT NULL DEFAULT 'LINEAR',
                split_program_id INTEGER NOT NULL,
                total_weeks INTEGER NOT NULL,
                sessions_per_week INTEGER NOT NULL,
                success_criteria_json TEXT NOT NULL,
                adaptation_policy_json TEXT NOT NULL,
                confidence_score REAL NOT NULL DEFAULT 1.0,
                last_reviewed_at INTEGER,
                created_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'ACTIVE'
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS planned_weeks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                program_id TEXT NOT NULL REFERENCES training_programs(id),
                week_number INTEGER NOT NULL,
                week_type TEXT NOT NULL,
                volume_multiplier REAL NOT NULL DEFAULT 1.0,
                intensity_modifier REAL NOT NULL DEFAULT 1.0,
                UNIQUE(program_id, week_number)
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS planned_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                program_id TEXT NOT NULL REFERENCES training_programs(id),
                week_number INTEGER NOT NULL,
                day_index INTEGER NOT NULL,
                sequence_number INTEGER NOT NULL,
                focus_key TEXT NOT NULL,
                planned_sets INTEGER NOT NULL,
                time_budget_minutes INTEGER,
                status TEXT NOT NULL DEFAULT 'UPCOMING',
                actual_workout_id INTEGER,
                coach_brief TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_planned_sessions_program ON planned_sessions (program_id, sequence_number)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS planned_session_exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                planned_session_id INTEGER NOT NULL REFERENCES planned_sessions(id),
                exercise_id INTEGER NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                execution_style TEXT NOT NULL DEFAULT 'NORMAL'
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS program_exercise_slots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                program_id TEXT NOT NULL REFERENCES training_programs(id),
                exercise_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                baseline_weekly_set_target INTEGER NOT NULL,
                starting_sets INTEGER NOT NULL,
                sets_per_week_increment INTEGER NOT NULL DEFAULT 0,
                load_progression_percent REAL NOT NULL DEFAULT 0.025,
                rep_range_shift INTEGER NOT NULL DEFAULT 0,
                sfr_score REAL,
                evolution_target_exercise_id INTEGER
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS program_checkpoints (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                program_id TEXT NOT NULL REFERENCES training_programs(id),
                week_number INTEGER NOT NULL,
                checkpoint_type TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                completed_at INTEGER,
                summary TEXT
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS program_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                program_id TEXT NOT NULL REFERENCES training_programs(id),
                event_type TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        canonicalizeBundledCustomExercises(db)
        seedSystemData(db)
    }

    private fun ensureCatalogColumns(db: SQLiteDatabase) {
        ensureColumn(
            db = db,
            table = "exercises",
            column = "is_post_install_llm_generated",
            definition = "INTEGER NOT NULL DEFAULT 0",
        )
        ensureColumn(
            db = db,
            table = "exercises",
            column = "created_at_utc",
            definition = "TEXT",
        )
        ensureColumn(
            db = db,
            table = "exercises",
            column = "updated_at_utc",
            definition = "TEXT",
        )
        ensureColumn(
            db = db,
            table = "exercises",
            column = "generation_model",
            definition = "TEXT",
        )
        ensureColumn(
            db = db,
            table = "exercises",
            column = "generation_prompt_version",
            definition = "TEXT",
        )
    }

    private fun canonicalizeBundledCustomExercises(db: SQLiteDatabase) {
        val bundledByName = mutableMapOf<String, Pair<Long, String>>()
        db.rawQuery(
            """
            SELECT exercise_id, name
            FROM exercises
            WHERE COALESCE(is_post_install_llm_generated, 0) = 0
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                bundledByName[normalize(cursor.getString(1))] = cursor.getLong(0) to cursor.getString(1)
            }
        }

        val customRows = db.rawQuery(
            """
            SELECT exercise_id, name
            FROM exercises
            WHERE COALESCE(is_post_install_llm_generated, 0) = 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getLong(0) to cursor.getString(1))
                }
            }
        }

        customRows.forEach { (customId, customName) ->
            val bundled = bundledByName[normalize(customName)] ?: return@forEach
            val bundledId = bundled.first
            val bundledName = bundled.second
            if (bundledId == customId) return@forEach

            db.execSQL(
                "UPDATE workout_template_exercises SET exercise_id = ? WHERE exercise_id = ?",
                arrayOf(bundledId, customId),
            )
            db.execSQL(
                "UPDATE performed_exercises SET exercise_id = ?, exercise_name = ? WHERE exercise_id = ?",
                arrayOf(bundledId, bundledName, customId),
            )
            db.execSQL(
                "UPDATE active_exercises SET exercise_id = ?, exercise_name = ? WHERE exercise_id = ?",
                arrayOf(bundledId, bundledName, customId),
            )
            db.execSQL(
                "UPDATE abandoned_exercises SET exercise_id = ?, exercise_name = ? WHERE exercise_id = ?",
                arrayOf(bundledId, bundledName, customId),
            )
            db.execSQL(
                "DELETE FROM exercises WHERE exercise_id = ?",
                arrayOf(customId),
            )
        }
    }

    private fun ensureColumn(
        db: SQLiteDatabase,
        table: String,
        column: String,
        definition: String,
    ) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1).equals(column, ignoreCase = true)) {
                    found = true
                    break
                }
            }
            found
        }
        if (!exists) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    private fun SQLiteDatabase.singleStringOrNull(query: String): String? =
        rawQuery(query, null).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getString(0)
        }

    private fun seedSystemData(db: SQLiteDatabase) {
        val splitPrograms = listOf(
            Triple(1L, "Full Body", "Balanced full-body sessions."),
            Triple(2L, "Upper/Lower", "Alternate upper and lower emphasis."),
            Triple(3L, "Push Pull Legs", "Rotate push, pull, and lower-body work."),
            Triple(4L, "Body Part Split", "Single-muscle emphasis sessions."),
            Triple(5L, "Adaptive", "Generator chooses the best emphasis from recent history."),
            Triple(
                FORMULA_A_SPLIT_PROGRAM_ID,
                FORMULA_A_SPLIT_PROGRAM_NAME,
                "Formula A without rest days: alternating heavy and high-rep push/pull/legs sessions.",
            ),
            Triple(
                FORMULA_B_SPLIT_PROGRAM_ID,
                FORMULA_B_SPLIT_PROGRAM_NAME,
                "Formula B without rest days: alternating glutes/hamstrings, upper chest, and rear/side delt heavy and high-rep sessions.",
            ),
        )
        splitPrograms.forEach { (id, name, description) ->
            db.execSQL(
                "INSERT OR IGNORE INTO training_split_programs (split_program_id, name, description) VALUES (?, ?, ?)",
                arrayOf(id, name, description),
            )
            db.execSQL(
                "UPDATE training_split_programs SET name = ?, description = ? WHERE split_program_id = ?",
                arrayOf(name, description, id),
            )
        }

        val locations = listOf(
            Triple(1L, "home", "Home"),
            Triple(2L, "gym", "Gym"),
        )
        locations.forEachIndexed { index, (id, name, displayName) ->
            db.execSQL(
                "INSERT OR IGNORE INTO location_modes (location_mode_id, name, display_name, is_default) VALUES (?, ?, ?, ?)",
                arrayOf(id, name, displayName, if (index == 0) 1 else 0),
            )
        }

        val homeDefaults = setOf(
            "Bodyweight",
            "Dumbbell",
            "Kettlebell",
            "Sliders",
            "Suspension Trainer",
            "Stability Ball",
            "Gymnastic Rings",
        )
        val gymDefaults = setOf(
            "Bodyweight",
            "Dumbbell",
            "Kettlebell",
            "Barbell",
            "Cable",
            "Machine",
            "Landmine",
            "Gymnastic Rings",
            "Suspension Trainer",
            "Stability Ball",
            "Sliders",
        )

        val equipment = mutableSetOf<String>()
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
                equipment += cursor.getString(0)
            }
        }

        equipment.forEach { equipmentName ->
            db.execSQL(
                "INSERT OR IGNORE INTO equipment_inventory (location_mode_id, equipment_name, is_available) VALUES (?, ?, ?)",
                arrayOf(1L, equipmentName, if (equipmentName in homeDefaults) 1 else 0),
            )
            db.execSQL(
                "INSERT OR IGNORE INTO equipment_inventory (location_mode_id, equipment_name, is_available) VALUES (?, ?, ?)",
                arrayOf(2L, equipmentName, if (equipmentName in gymDefaults) 1 else 0),
            )
        }
    }

    private fun seedGeneratedSynonymsIfEmpty(db: SQLiteDatabase) {
        val existingCount = db.simpleLongForQuery("SELECT COUNT(*) FROM exercise_synonyms")
        if (existingCount > 0L) return

        val now = Instant.now().toString()
        db.beginTransaction()
        try {
            db.rawQuery("SELECT exercise_id, name FROM exercises", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val exerciseId = cursor.getLong(0)
                    val name = cursor.getString(1)
                    generateSynonyms(name).forEach { alias ->
                        db.execSQL(
                            """
                            INSERT OR IGNORE INTO exercise_synonyms
                                (exercise_id, synonym_name, synonym_name_normalized, synonym_type, source, confidence_score, created_at_utc)
                            VALUES (?, ?, ?, 'generated', 'heuristic_bootstrap', 0.55, ?)
                            """.trimIndent(),
                            arrayOf(exerciseId, alias, normalize(alias), now),
                        )
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun generateSynonyms(name: String): Set<String> {
        val values = linkedSetOf<String>()
        fun maybeAdd(candidate: String) {
            if (candidate.isNotBlank() && candidate != name) values += candidate
        }

        maybeAdd(name.replace("-", " "))
        maybeAdd(name.replace("Push-Up", "Pushup"))
        maybeAdd(name.replace("Pull-Up", "Pullup"))
        maybeAdd(name.replace("Chin-Up", "Chinup"))
        maybeAdd(name.replace("Dumbbell", "DB"))
        maybeAdd(name.replace("Kettlebell", "KB"))
        maybeAdd(name.replace("Barbell", "BB"))
        maybeAdd(name.replace("Romanian Deadlift", "RDL"))
        maybeAdd(name.replace("Single Arm", "One Arm"))
        maybeAdd(name.replace("Single-Leg", "One-Leg"))
        return values
    }

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun SQLiteDatabase.simpleLongForQuery(query: String): Long {
        rawQuery(query, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }
}

fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
