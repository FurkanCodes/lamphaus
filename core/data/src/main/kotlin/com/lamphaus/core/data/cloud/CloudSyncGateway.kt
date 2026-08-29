package com.lamphaus.core.data.cloud

import com.lamphaus.core.data.preferences.SyncedSettings
import com.lamphaus.core.model.ArtworkCandidates
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProvider
import com.lamphaus.core.model.ArtworkProviderStatus
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

    // ── Artwork (BYOK metadata providers) ────────────────────────────────
    // API keys travel through encrypted server-side storage and are never
    // returned to the client; only provider configuration presence is exposed.

    /** Live profile-scoped artwork overrides keyed by media key upstream. */
    fun artworkOverrides(userId: String, profileId: String): Flow<List<ArtworkOverride>>
    suspend fun saveArtworkOverride(userId: String, override: ArtworkOverride): Result<Unit>
    suspend fun deleteArtworkOverride(userId: String, profileId: String, mediaKey: String): Result<Unit>

    suspend fun artworkProviderStatuses(userId: String): Result<List<ArtworkProviderStatus>>
    suspend fun saveArtworkKey(userId: String, provider: ArtworkProvider, apiKey: String): Result<Unit>
    suspend fun deleteArtworkKey(userId: String, provider: ArtworkProvider): Result<Unit>
    suspend fun artworkCandidates(
        userId: String,
        mediaKey: String,
        name: String,
        releaseYear: Int?,
        mediaType: com.lamphaus.core.model.MediaType,
    ): Result<ArtworkCandidates>
}
