package com.lamphaus.core.data.cloud

import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class LocalCloudSyncGateway : CloudSyncGateway {
    override fun profiles(userId: String): Flow<List<Profile>> = emptyFlow()
    override fun library(userId: String, profileId: String): Flow<List<LibraryEntry>> = emptyFlow()
    override fun progress(userId: String, profileId: String): Flow<List<WatchProgress>> = emptyFlow()
    override suspend fun saveProfile(userId: String, profile: Profile) = Result.success(Unit)
    override suspend fun saveLibrary(userId: String, entry: LibraryEntry) = Result.success(Unit)
    override suspend fun saveProgress(userId: String, progress: WatchProgress) = Result.success(Unit)
    override suspend fun saveProvider(userId: String, provider: ProviderSubscription) = Result.success(Unit)
    override suspend fun providers(userId: String) = Result.success(emptyList<ProviderSubscription>())
}

