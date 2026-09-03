package com.lamphaus.core.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LamphausDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LamphausDatabase::class.java,
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(MIGRATION_DATABASE_NAME)
    }

    @Test
    fun migration4To5_preservesUserDataAndCreatesEnrichmentCache() {
        migrationHelper.createDatabase(MIGRATION_DATABASE_NAME, 4).apply {
            insertVersion4Fixtures()
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            5,
            true,
            *LamphausDatabase.ALL_MIGRATIONS,
        )

        assertSingleValue(migrated, "SELECT name FROM profiles WHERE id = 'profile-1'", "Living Room")
        assertSingleValue(migrated, "SELECT displayName FROM providers WHERE id = 'provider-1'", "Example")
        assertSingleValue(migrated, "SELECT mediaKey FROM library WHERE profileId = 'profile-1'", "movie:1")
        assertSingleValue(migrated, "SELECT positionMillis FROM watch_progress WHERE videoId = 'video-1'", 42_000L)

        migrated.execSQL(
            """
            INSERT INTO detail_enrichment(mediaKey, payloadJson, fetchedAtEpochMillis)
            VALUES (?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("movie:1", "{\"ratings\":[]}", 1_000L),
        )
        assertSingleValue(
            migrated,
            "SELECT payloadJson FROM detail_enrichment WHERE mediaKey = 'movie:1'",
            "{\"ratings\":[]}",
        )
    }

    @Test
    fun freshVersion5Database_opensWithEnrichmentCache() {
        val database = Room.inMemoryDatabaseBuilder(context, LamphausDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO detail_enrichment(mediaKey, payloadJson, fetchedAtEpochMillis)
            VALUES (?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("movie:fresh", "{}", 2_000L),
        )

        assertSingleValue(
            database.openHelper.writableDatabase,
            "SELECT payloadJson FROM detail_enrichment WHERE mediaKey = 'movie:fresh'",
            "{}",
        )
        database.close()
    }

    private fun SupportSQLiteDatabase.insertVersion4Fixtures() {
        execSQL(
            """
            INSERT INTO profiles(
                id, name, avatarKey, kind, pinSalt, pinHash, hideUnrated, updatedAtEpochMillis
            ) VALUES (?, ?, ?, ?, NULL, NULL, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("profile-1", "Living Room", "aurora", "ADULT", 0, 100L),
        )
        execSQL(
            """
            INSERT INTO providers(id, manifestUrl, displayName, enabled, sortOrder, updatedAtEpochMillis)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("provider-1", "https://example.com/manifest.json", "Example", 1, 0, 101L),
        )
        execSQL(
            """
            INSERT INTO library(profileId, mediaKey, previewJson, addedAtEpochMillis, updatedAtEpochMillis)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("profile-1", "movie:1", "{}", 102L, 103L),
        )
        execSQL(
            """
            INSERT INTO watch_progress(
                profileId, mediaKey, videoId, positionMillis, durationMillis, completed,
                updatedAtEpochMillis, previewJson, episodeLabel
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("profile-1", "movie:1", "video-1", 42_000L, 120_000L, 0, 104L, "{}", null),
        )
    }

    private fun assertSingleValue(
        database: SupportSQLiteDatabase,
        query: String,
        expected: Any,
    ) {
        database.query(query).use { cursor ->
            assertTrue("Expected one row for query: $query", cursor.moveToFirst())
            when (expected) {
                is Long -> assertEquals(expected, cursor.getLong(0))
                else -> assertEquals(expected, cursor.getString(0))
            }
        }
    }

    private companion object {
        const val MIGRATION_DATABASE_NAME = "lamphaus-migration-test"
    }
}
