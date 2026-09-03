package com.lamphaus.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LamphausDao : DetailEnrichmentDao {
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

    /** Serializes the read/write pair so concurrent final saves cannot clear completion. */
    @Transaction
    suspend fun upsertProgressSticky(progress: WatchProgressEntity): WatchProgressEntity {
        val effective = if (progressEntry(progress.profileId, progress.videoId)?.completed == true) {
            progress.copy(completed = true)
        } else {
            progress
        }
        upsertProgress(effective)
        return effective
    }

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND videoId = :videoId LIMIT 1")
    suspend fun progressEntry(profileId: String, videoId: String): WatchProgressEntity?

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND videoId = :videoId")
    suspend fun removeProgress(profileId: String, videoId: String)

    @Query("SELECT itemKey FROM cloud_sync_keys WHERE profileId = :profileId AND collection = :collection")
    suspend fun cloudSyncKeys(profileId: String, collection: String): List<String>

    @Query("DELETE FROM cloud_sync_keys WHERE profileId = :profileId AND collection = :collection")
    suspend fun clearCloudSyncKeys(profileId: String, collection: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCloudSyncKeys(keys: List<CloudSyncKeyEntity>)

    @Transaction
    suspend fun replaceCloudSyncKeys(profileId: String, collection: String, keys: Set<String>) {
        clearCloudSyncKeys(profileId, collection)
        if (keys.isNotEmpty()) {
            insertCloudSyncKeys(keys.map { CloudSyncKeyEntity(profileId, collection, it) })
        }
    }

    @Query("DELETE FROM profiles")
    suspend fun clearProfiles()

    @Query("DELETE FROM providers")
    suspend fun clearProviders()

    @Query("DELETE FROM library")
    suspend fun clearLibrary()

    @Query("DELETE FROM watch_progress")
    suspend fun clearProgress()

    @Query("DELETE FROM cloud_sync_keys")
    suspend fun clearAllCloudSyncKeys()
}
