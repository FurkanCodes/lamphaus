package com.lamphaus.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        ProviderEntity::class,
        LibraryEntity::class,
        WatchProgressEntity::class,
    ],
    version = 3,
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
    }
}

