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
    suspend fun saveProgress(progress: WatchProgress)
    suspend fun clearLocalAccountData()
}
