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
        ProfilePlaybackPrefsEntity::class,
        MediaPlaybackSelectionEntity::class,
        SourcePlaybackSelectionEntity::class,
        AudioRouteSettingsEntity::class,
    ],
    version = 6,
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
         * v6: player V2 preference storage — profile defaults and semantic
         * per-title selections (both cloud-mirrored), plus device-local exact
         * track selections and per-audio-route timing (plan §5).
         */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profile_playback_prefs` (
                        `profileId` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_playback_selections` (
                        `profileId` TEXT NOT NULL,
                        `mediaKey` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `mediaKey`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_playback_selections_profileId_updatedAtEpochMillis` " +
                        "ON `media_playback_selections` (`profileId`, `updatedAtEpochMillis`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `source_playback_selections` (
                        `profileId` TEXT NOT NULL,
                        `mediaKey` TEXT NOT NULL,
                        `sourceFingerprint` TEXT NOT NULL,
                        `audioTrackId` TEXT,
                        `subtitleTrackId` TEXT,
                        `subtitleDelayMillis` INTEGER NOT NULL,
                        `audioDelayMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `mediaKey`, `sourceFingerprint`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audio_route_settings` (
                        `routeFingerprint` TEXT NOT NULL,
                        `audioDelayMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`routeFingerprint`)
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
            MIGRATION_5_6,
        )
    }
}
