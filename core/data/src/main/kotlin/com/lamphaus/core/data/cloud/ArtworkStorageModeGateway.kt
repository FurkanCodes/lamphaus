package com.lamphaus.core.data.cloud

import com.lamphaus.core.data.preferences.UserPreferences
import com.lamphaus.core.data.security.LocalArtworkKeyStore
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderStatus
import com.lamphaus.core.model.MediaType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps artwork credentials on-device when the user selects local-only mode. */
class ArtworkStorageModeGateway(
    private val delegate: CloudSyncGateway,
    private val preferences: UserPreferences,
    private val localKeys: LocalArtworkKeyStore,
    private val localArtwork: LocalArtworkClient,
) : CloudSyncGateway by delegate {
    private val artworkStorageMutex = Mutex()

    override suspend fun artworkProviderStatuses(userId: String): Result<List<ArtworkProviderStatus>> {
        if (!preferences.current().localOnlyArtworkKeys) return delegate.artworkProviderStatuses(userId)
        val providers = delegate.artworkProviderStatuses(userId).getOrElse {
            localArtwork.providerStatuses()
        }
        return try {
            Result.success(providers.map { provider ->
                provider.copy(configured = localKeys.has(userId, provider.provider))
            })
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun changeLocalOnlyMode(userId: String, enabled: Boolean): Result<Unit> = artworkStorageMutex.withLock {
        val current = preferences.current().localOnlyArtworkKeys
        performArtworkStorageModeChange(
            currentLocalOnly = current,
            targetLocalOnly = enabled,
            clearCloud = { delegate.clearArtworkKeys(userId).getOrThrow() },
            clearLocal = { localKeys.clearUser(userId) },
            persistMode = preferences::setLocalOnlyArtworkKeys,
        )
    }

    override suspend fun saveArtworkKey(
        userId: String,
        provider: ArtworkProviderId,
        apiKey: String,
    ): Result<Unit> = artworkStorageMutex.withLock {
        if (preferences.current().localOnlyArtworkKeys) {
            try {
                localKeys.save(userId, provider, apiKey)
                Result.success(Unit)
            } catch (error: Throwable) {
                Result.failure(error)
            }
        } else {
            delegate.saveArtworkKey(userId, provider, apiKey)
        }
    }

    override suspend fun deleteArtworkKey(
        userId: String,
        provider: ArtworkProviderId,
    ): Result<Unit> = artworkStorageMutex.withLock {
        if (preferences.current().localOnlyArtworkKeys) {
            try {
                localKeys.delete(userId, provider)
                Result.success(Unit)
            } catch (error: Throwable) {
                Result.failure(error)
            }
        } else {
            delegate.deleteArtworkKey(userId, provider)
        }
    }

    override suspend fun artworkCandidates(
        userId: String,
        mediaKey: String,
        name: String,
        releaseYear: Int?,
        mediaType: MediaType,
    ) = if (preferences.current().localOnlyArtworkKeys) {
        localArtwork.candidates(localKeys.all(userId), mediaKey, name, releaseYear, mediaType)
    } else {
        delegate.artworkCandidates(userId, mediaKey, name, releaseYear, mediaType)
    }
}

internal suspend fun performArtworkStorageModeChange(
    currentLocalOnly: Boolean,
    targetLocalOnly: Boolean,
    clearCloud: suspend () -> Unit,
    clearLocal: suspend () -> Unit,
    persistMode: suspend (Boolean) -> Unit,
): Result<Unit> {
    if (currentLocalOnly == targetLocalOnly) return Result.success(Unit)
    return runCatching {
        if (targetLocalOnly) {
            clearCloud()
            clearLocal()
            persistMode(true)
        } else {
            clearLocal()
            persistMode(false)
        }
    }
}
