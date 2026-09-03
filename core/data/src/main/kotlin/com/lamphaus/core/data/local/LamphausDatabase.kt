package com.lamphaus.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        ProviderEntity::class,
        LibraryEntity::class,
        WatchProgressEntity::class,
        CloudSyncKeyEntity::class,
        DetailEnrichmentEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class LamphausDatabase : RoomDatabase() {
    abstract fun dao(): LamphausDao

    companion object {
        /** v2: watch_progress.previewJson carries the Continue Watching artwork snapshot. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_progress ADD COLUMN previewJson TEXT")
            }
        }

        /** v3: watch_progress.episodeLabel names the episode on series entries. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_progress ADD COLUMN episodeLabel TEXT")
            }
        }

        /** v4: remember cloud-confirmed keys so remote deletes cannot erase unsynced local rows. */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cloud_sync_keys (
                        profileId TEXT NOT NULL,
                        collection TEXT NOT NULL,
                        itemKey TEXT NOT NULL,
                        PRIMARY KEY(profileId, collection, itemKey)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cloud_sync_keys_profileId_collection " +
                        "ON cloud_sync_keys(profileId, collection)",
                )
            }
        }

        /** v5: detail_enrichment caches provider-neutral detail enrichment locally. */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS detail_enrichment (
                        mediaKey TEXT NOT NULL PRIMARY KEY,
                        payloadJson TEXT NOT NULL,
                        fetchedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Complete non-destructive migration graph for production database builders and tests.
         * Keeping the registry beside the migrations prevents a schema version bump from being
         * implemented but omitted at the application wiring boundary.
         */
        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        )
    }
}
