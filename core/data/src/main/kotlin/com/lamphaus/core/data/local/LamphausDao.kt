package com.lamphaus.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LamphausDao {
    @Query("SELECT * FROM profiles ORDER BY kind, name")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun profile(profileId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("SELECT * FROM providers ORDER BY sortOrder, displayName")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :providerId")
    suspend fun provider(providerId: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(provider: ProviderEntity)

    @Delete
    suspend fun deleteProvider(provider: ProviderEntity)

    @Query("UPDATE providers SET enabled = :enabled, updatedAtEpochMillis = :updatedAt WHERE id = :providerId")
    suspend fun setProviderEnabled(providerId: String, enabled: Boolean, updatedAt: Long)

    @Query("SELECT * FROM library WHERE profileId = :profileId ORDER BY updatedAtEpochMillis DESC")
    fun observeLibrary(profileId: String): Flow<List<LibraryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibrary(entry: LibraryEntity)

    @Query("DELETE FROM library WHERE profileId = :profileId AND mediaKey = :mediaKey")
    suspend fun removeLibrary(profileId: String, mediaKey: String)

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId ORDER BY updatedAtEpochMillis DESC")
    fun observeProgress(profileId: String): Flow<List<WatchProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM profiles")
    suspend fun clearProfiles()

    @Query("DELETE FROM providers")
    suspend fun clearProviders()

    @Query("DELETE FROM library")
    suspend fun clearLibrary()

    @Query("DELETE FROM watch_progress")
    suspend fun clearProgress()
}

