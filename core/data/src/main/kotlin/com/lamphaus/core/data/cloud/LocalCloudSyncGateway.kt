package com.lamphaus.core.data.cloud

import com.lamphaus.core.data.preferences.SyncedSettings
import com.lamphaus.core.model.ArtworkCandidates
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderStatus
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class LocalCloudSyncGateway : CloudSyncGateway {
    override fun profiles(userId: String): Flow<List<Profile>> = emptyFlow()
    override fun library(userId: String, profileId: String): Flow<List<LibraryEntry>> = emptyFlow()
    override fun progress(userId: String, profileId: String): Flow<List<WatchProgress>> = emptyFlow()
    override fun settings(userId: String): Flow<SyncedSettings?> = emptyFlow()
    override suspend fun saveProfile(userId: String, profile: Profile) = Result.success(Unit)
    override suspend fun saveLibrary(userId: String, entry: LibraryEntry) = Result.success(Unit)
    override suspend fun deleteLibraryEntry(userId: String, profileId: String, mediaKey: String) =
        Result.success(Unit)
    override suspend fun saveProgress(userId: String, progress: WatchProgress) = Result.success(Unit)
    override suspend fun deleteProgress(userId: String, profileId: String, videoId: String) =
        Result.success(Unit)
    override suspend fun saveSettings(userId: String, settings: SyncedSettings) = Result.success(Unit)
    override suspend fun saveProvider(userId: String, provider: ProviderSubscription) = Result.success(Unit)
    override suspend fun deleteProvider(userId: String, providerId: String) = Result.success(Unit)
    override suspend fun providers(userId: String) = Result.success(emptyList<ProviderSubscription>())
    override fun artworkOverrides(userId: String, profileId: String): Flow<List<ArtworkOverride>> = emptyFlow()
    override suspend fun saveArtworkOverride(userId: String, override: ArtworkOverride) = Result.success(Unit)
    override suspend fun deleteArtworkOverride(userId: String, profileId: String, mediaKey: String) = Result.success(Unit)
    override suspend fun artworkProviderStatuses(userId: String) =
        Result.failure<List<ArtworkProviderStatus>>(CloudNotConfiguredException())
    override suspend fun saveArtworkKey(userId: String, provider: ArtworkProviderId, apiKey: String) =
        Result.failure<Unit>(CloudNotConfiguredException())
    override suspend fun deleteArtworkKey(userId: String, provider: ArtworkProviderId) =
        Result.failure<Unit>(CloudNotConfiguredException())
    override suspend fun clearArtworkKeys(userId: String) = Result.success(Unit)
    override suspend fun artworkCandidates(
        userId: String,
        mediaKey: String,
        name: String,
        releaseYear: Int?,
        mediaType: MediaType,
    ) = Result.failure<ArtworkCandidates>(CloudNotConfiguredException())
}
