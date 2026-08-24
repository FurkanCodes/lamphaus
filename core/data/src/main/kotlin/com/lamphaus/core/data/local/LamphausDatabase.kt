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
    version = 1,
    exportSchema = true,
)
abstract class LamphausDatabase : RoomDatabase() {
    abstract fun dao(): LamphausDao
}

