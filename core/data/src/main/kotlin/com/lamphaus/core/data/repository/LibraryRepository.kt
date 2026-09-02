package com.lamphaus.core.data.repository

import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun profiles(): Flow<List<Profile>>
    suspend fun saveProfile(profile: Profile, pin: CharArray?)
    suspend fun verifyPin(profileId: String, pin: CharArray): Boolean
    suspend fun deleteProfile(profileId: String)

    fun providers(): Flow<List<ProviderSubscription>>
    suspend fun saveProvider(provider: ProviderSubscription)
    suspend fun setProviderEnabled(providerId: String, enabled: Boolean)
    suspend fun removeProvider(providerId: String)

    fun library(profileId: String): Flow<List<LibraryEntry>>
    suspend fun saveLibrary(entry: LibraryEntry)
    suspend fun removeLibrary(profileId: String, mediaKey: String)

    fun progress(profileId: String): Flow<List<WatchProgress>>

    /** Single progress row, or null when the profile has none for the video. */
    suspend fun progressEntry(profileId: String, videoId: String): WatchProgress?

    /**
     * Persists progress with sticky completion: an already-completed row stays
     * completed until an explicit Mark unwatched deletes it. Returns the row
     * as persisted, so callers push the effective state to the cloud.
     */
    suspend fun saveProgress(progress: WatchProgress): WatchProgress

    suspend fun removeProgress(profileId: String, videoId: String)

    /** Keys present in the last successful cloud snapshot for this profile/collection. */
    suspend fun cloudSyncKeys(profileId: String, collection: CloudSyncCollection): Set<String>

    suspend fun replaceCloudSyncKeys(profileId: String, collection: CloudSyncCollection, keys: Set<String>)

    suspend fun clearLocalAccountData()
}

enum class CloudSyncCollection {
    LIBRARY,
    PROGRESS,
}
