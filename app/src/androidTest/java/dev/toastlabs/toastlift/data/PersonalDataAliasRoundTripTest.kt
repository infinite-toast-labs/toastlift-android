package dev.toastlabs.toastlift.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonalDataAliasRoundTripTest {
    private val databases = mutableListOf<ToastLiftDatabase>()
    private val databaseFiles = mutableListOf<File>()

    @After
    fun cleanUp() {
        databases.forEach(ToastLiftDatabase::close)
        databaseFiles.forEach { file ->
            listOf(file, File("${file.path}-journal"), File("${file.path}-shm"), File("${file.path}-wal"))
                .forEach(File::delete)
        }
    }

    @Test
    fun exportAndImportPreserveUserConfirmedExerciseAliases() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDatabase = newDatabase(context, "alias-source")
        val sourceCatalog = CatalogRepository(sourceDatabase)
        val sourceUser = UserRepository(sourceDatabase)
        sourceUser.saveProfile(OnboardingDraft(), activeLocationModeId = 1)
        val exerciseId = firstBundledExerciseId(sourceDatabase)
        assertTrue(sourceCatalog.addExerciseSynonym(exerciseId, "  Incline   press test alias  "))

        val export = sourceUser.exportPersonalDataJson()
        val exportJson = JSONObject(export.contents)
        val aliases = exportJson
            .getJSONObject("personal_data")
            .getJSONArray("user_confirmed_exercise_aliases")

        assertEquals(PERSONAL_DATA_EXPORT_SCHEMA_VERSION, exportJson.getInt("schema_version"))
        assertEquals(1, aliases.length())
        assertEquals(exerciseId, aliases.getJSONObject(0).getLong("exercise_id"))
        assertEquals("Incline press test alias", aliases.getJSONObject(0).getString("synonym_name"))
        assertEquals(USER_CONFIRMED_EXERCISE_SYNONYM_SOURCE, aliases.getJSONObject(0).getString("source"))

        val destinationDatabase = newDatabase(context, "alias-destination")
        val destinationCatalog = CatalogRepository(destinationDatabase)
        assertTrue(destinationCatalog.addExerciseSynonym(exerciseId, "Alias that must be replaced"))
        val destinationUser = UserRepository(destinationDatabase)
        val destinationCustomExercises = CustomExerciseRepository(
            context = context,
            database = destinationDatabase,
            catalogRepository = destinationCatalog,
            userRepository = destinationUser,
        )

        PersonalDataImporter(destinationDatabase, destinationCustomExercises).import(export.contents)

        destinationDatabase.open().rawQuery(
            """
            SELECT synonym_name, synonym_name_normalized, synonym_type, source, confidence_score
            FROM exercise_synonyms
            WHERE source = ?
            """.trimIndent(),
            arrayOf(USER_CONFIRMED_EXERCISE_SYNONYM_SOURCE),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Incline press test alias", cursor.getString(0))
            assertEquals("incline press test alias", cursor.getString(1))
            assertEquals("custom", cursor.getString(2))
            assertEquals(USER_CONFIRMED_EXERCISE_SYNONYM_SOURCE, cursor.getString(3))
            assertEquals(1.0, cursor.getDouble(4), 0.0)
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun exportAliasSectionMatchesTheInstalledAppDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = ToastLiftDatabase(context).also { databases += it }
        val expected = database.open().rawQuery(
            """
            SELECT exercise_id, synonym_name, synonym_name_normalized, synonym_type,
                   source, confidence_score, created_at_utc
            FROM exercise_synonyms
            WHERE source = ?
            ORDER BY synonym_id
            """.trimIndent(),
            arrayOf(USER_CONFIRMED_EXERCISE_SYNONYM_SOURCE),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        listOf(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.getString(4),
                            cursor.getDouble(5),
                            cursor.getString(6),
                        ),
                    )
                }
            }
        }

        val exportJson = JSONObject(UserRepository(database).exportPersonalDataJson().contents)
        val aliases = exportJson
            .getJSONObject("personal_data")
            .getJSONArray("user_confirmed_exercise_aliases")
        val actual = buildList {
            for (index in 0 until aliases.length()) {
                val alias = aliases.getJSONObject(index)
                add(
                    listOf(
                        alias.getLong("exercise_id"),
                        alias.getString("synonym_name"),
                        alias.getString("synonym_name_normalized"),
                        alias.getString("synonym_type"),
                        alias.getString("source"),
                        alias.getDouble("confidence_score"),
                        alias.getString("created_at_utc"),
                    ),
                )
            }
        }

        assertEquals(PERSONAL_DATA_EXPORT_SCHEMA_VERSION, exportJson.getInt("schema_version"))
        assertEquals(expected, actual)
    }

    @Test
    fun legacyBackupDoesNotEraseAliasesThatItsSchemaCouldNotRepresent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDatabase = newDatabase(context, "legacy-alias-source")
        val sourceUser = UserRepository(sourceDatabase)
        sourceUser.saveProfile(OnboardingDraft(), activeLocationModeId = 1)
        val legacyExport = JSONObject(sourceUser.exportPersonalDataJson().contents).apply {
            put("schema_version", 7)
            getJSONObject("personal_data").remove("user_confirmed_exercise_aliases")
        }

        val destinationDatabase = newDatabase(context, "legacy-alias-destination")
        val destinationCatalog = CatalogRepository(destinationDatabase)
        val exerciseId = firstBundledExerciseId(destinationDatabase)
        assertTrue(destinationCatalog.addExerciseSynonym(exerciseId, "Alias retained for legacy import"))
        val destinationUser = UserRepository(destinationDatabase)
        val destinationCustomExercises = CustomExerciseRepository(
            context = context,
            database = destinationDatabase,
            catalogRepository = destinationCatalog,
            userRepository = destinationUser,
        )

        PersonalDataImporter(destinationDatabase, destinationCustomExercises).import(legacyExport.toString())

        destinationDatabase.open().rawQuery(
            """
            SELECT synonym_name
            FROM exercise_synonyms
            WHERE source = ?
            """.trimIndent(),
            arrayOf(USER_CONFIRMED_EXERCISE_SYNONYM_SOURCE),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Alias retained for legacy import", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun newDatabase(context: Context, label: String): ToastLiftDatabase {
        val file = File(context.cacheDir, "$label-${UUID.randomUUID()}.db")
        databaseFiles += file
        return ToastLiftDatabase(context, file).also { databases += it }
    }

    private fun firstBundledExerciseId(database: ToastLiftDatabase): Long = database.open().rawQuery(
        """
        SELECT exercise_id
        FROM exercises
        WHERE is_post_install_llm_generated = 0
        ORDER BY exercise_id
        LIMIT 1
        """.trimIndent(),
        null,
    ).use { cursor ->
        check(cursor.moveToFirst()) { "Bundled exercise catalog is empty." }
        cursor.getLong(0)
    }
}
