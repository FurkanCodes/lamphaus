package com.lamphaus.core.data.cloud

import com.lamphaus.core.data.preferences.SyncedSettings
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface CloudSyncGateway {
    fun profiles(userId: String): Flow<List<Profile>>
    fun library(userId: String, profileId: String): Flow<List<LibraryEntry>>
    fun progress(userId: String, profileId: String): Flow<List<WatchProgress>>

    /** Emits the account's settings row, or null while the account has none. */
    fun settings(userId: String): Flow<SyncedSettings?>

    suspend fun saveProfile(userId: String, profile: Profile): Result<Unit>
    suspend fun saveLibrary(userId: String, entry: LibraryEntry): Result<Unit>
    suspend fun saveProgress(userId: String, progress: WatchProgress): Result<Unit>
    suspend fun saveSettings(userId: String, settings: SyncedSettings): Result<Unit>
    suspend fun saveProvider(userId: String, provider: ProviderSubscription): Result<Unit>
    suspend fun deleteProvider(userId: String, providerId: String): Result<Unit>
    suspend fun providers(userId: String): Result<List<ProviderSubscription>>
}
