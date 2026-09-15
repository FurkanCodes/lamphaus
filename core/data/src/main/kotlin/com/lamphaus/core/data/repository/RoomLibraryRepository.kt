package com.lamphaus.core.data.repository

import com.lamphaus.core.data.local.LamphausDao
import com.lamphaus.core.data.local.LibraryEntity
import com.lamphaus.core.data.local.ProfileEntity
import com.lamphaus.core.data.local.ProviderEntity
import com.lamphaus.core.data.local.WatchProgressEntity
import com.lamphaus.core.data.security.StringCipher
import com.lamphaus.core.model.LibraryEntry
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.ProfileKind
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.WatchProgress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room plus keystore persistence (SHR-ARC-04, SHR-ARC-11). Every entry point is
 * main-safe: PBKDF2 hashing and JSON mapping run on the injected CPU
 * dispatcher, keystore encryption and decryption on the injected IO
 * dispatcher, and Room keeps scheduling its own SQL. `flowOn` sits after the
 * expensive upstream transformation but before state publication (PERF-03).
 * PIN work-factor strength is unchanged at 120,000 PBKDF2 rounds.
 */
class RoomLibraryRepository(
    private val dao: LamphausDao,
    private val stringCipher: StringCipher,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LibraryRepository {
    override fun profiles(): Flow<List<Profile>> = dao.observeProfiles().map { rows -> rows.map { it.toModel() } }

    override suspend fun saveProfile(profile: Profile, pin: CharArray?) {
        try {
            val existing = dao.profile(profile.id)
            val salt = if (pin != null) {
                withContext(cpuDispatcher) { ByteArray(16).also(SecureRandom()::nextBytes) }
            } else {
                null
            }
            val pinHash = if (pin != null && salt != null) {
                withContext(cpuDispatcher) { hashPin(pin, salt) }
            } else {
                existing?.pinHash
            }
            dao.upsertProfile(
                ProfileEntity(
                    id = profile.id,
                    name = profile.name,
                    avatarKey = profile.avatarKey,
                    kind = profile.kind.name,
                    pinSalt = salt?.let { Base64.getEncoder().encodeToString(it) } ?: existing?.pinSalt,
                    pinHash = pinHash,
                    hideUnrated = profile.hideUnrated,
                    updatedAtEpochMillis = profile.updatedAtEpochMillis,
                ),
            )
        } finally {
            // Clear on success, failure, and early exits alike.
            pin?.fill('\u0000')
        }
    }

    override suspend fun verifyPin(profileId: String, pin: CharArray): Boolean {
        try {
            val profile = dao.profile(profileId) ?: return false
            val salt = profile.pinSalt?.let { Base64.getDecoder().decode(it) } ?: return false
            val expected = profile.pinHash ?: return false
            val actual = withContext(cpuDispatcher) { hashPin(pin, salt) }
            return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
        } finally {
            pin.fill('\u0000')
        }
    }

    override suspend fun deleteProfile(profileId: String) {
        dao.profile(profileId)?.let { dao.deleteProfile(it) }
    }

    override fun providers(): Flow<List<ProviderSubscription>> =
        dao.observeProviders()
            .map { rows -> rows.map { it.toModel() } }
            // Keystore decryption is blocking IO, not just mapping (PERF-03).
            .flowOn(ioDispatcher)

    override suspend fun saveProvider(provider: ProviderSubscription) = withContext(ioDispatcher) {
        dao.upsertProvider(provider.toEntity())
    }

    override suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        dao.setProviderEnabled(providerId, enabled, System.currentTimeMillis())
    }

    override suspend fun removeProvider(providerId: String) {
        dao.provider(providerId)?.let { dao.deleteProvider(it) }
    }

    override fun library(profileId: String): Flow<List<LibraryEntry>> = dao.observeLibrary(profileId)
        .map { rows ->
            rows.mapNotNull { row ->
                runCatching {
                    LibraryEntry(
                        profileId = row.profileId,
                        mediaKey = row.mediaKey,
                        preview = json.decodeFromString<MediaPreview>(row.previewJson),
                        addedAtEpochMillis = row.addedAtEpochMillis,
                        updatedAtEpochMillis = row.updatedAtEpochMillis,
                    )
                }.getOrNull()
            }
        }
        .flowOn(cpuDispatcher)

    override suspend fun saveLibrary(entry: LibraryEntry) = withContext(cpuDispatcher) {
        dao.upsertLibrary(
            LibraryEntity(
                profileId = entry.profileId,
                mediaKey = entry.mediaKey,
                previewJson = json.encodeToString(entry.preview),
                addedAtEpochMillis = entry.addedAtEpochMillis,
                updatedAtEpochMillis = entry.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun removeLibrary(profileId: String, mediaKey: String) = dao.removeLibrary(profileId, mediaKey)

    override fun progress(profileId: String): Flow<List<WatchProgress>> = dao.observeProgress(profileId)
        .map { rows -> rows.map { it.toModel() } }
        .flowOn(cpuDispatcher)

    override suspend fun saveProgress(progress: WatchProgress): WatchProgress {
        // The DAO transaction makes completion sticky even when periodic,
        // natural-end, and onStop saves race each other.
        val entity = withContext(cpuDispatcher) { progress.toEntity() }
        return dao.upsertProgressSticky(entity).toModel()
    }

    override suspend fun progressEntry(profileId: String, videoId: String): WatchProgress? =
        dao.progressEntry(profileId, videoId)?.let { row -> withContext(cpuDispatcher) { row.toModel() } }

    override suspend fun removeProgress(profileId: String, videoId: String) =
        dao.removeProgress(profileId, videoId)

    override suspend fun removeProgress(profileId: String, videoIds: List<String>) {
        if (videoIds.isNotEmpty()) dao.removeProgress(profileId, videoIds)
    }

    override suspend fun cloudSyncKeys(
        profileId: String,
        collection: CloudSyncCollection,
    ): Set<String> = dao.cloudSyncKeys(profileId, collection.name).toSet()

    override suspend fun replaceCloudSyncKeys(
        profileId: String,
        collection: CloudSyncCollection,
        keys: Set<String>,
    ) = dao.replaceCloudSyncKeys(profileId, collection.name, keys)

    override suspend fun clearLocalAccountData() {
        dao.clearProgress()
        dao.clearLibrary()
        dao.clearAllCloudSyncKeys()
        dao.clearProviders()
        dao.clearProfiles()
    }

    private fun hashPin(pin: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(pin, salt, 120_000, 256)
        return try {
            Base64.getEncoder().encodeToString(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
            )
        } finally {
            spec.clearPassword()
        }
    }

    private fun ProfileEntity.toModel() = Profile(
        id = id,
        name = name,
        avatarKey = avatarKey,
        kind = ProfileKind.valueOf(kind),
        hasPin = pinHash != null,
        hideUnrated = hideUnrated,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun ProviderEntity.toModel() = ProviderSubscription(
        id,
        runCatching { stringCipher.decrypt(manifestUrl) }.getOrDefault(""),
        displayName,
        enabled,
        sortOrder,
        updatedAtEpochMillis,
    )

    private fun ProviderSubscription.toEntity() = ProviderEntity(
        id,
        stringCipher.encrypt(manifestUrl),
        displayName,
        enabled,
        sortOrder,
        updatedAtEpochMillis,
    )

    private fun WatchProgressEntity.toModel() = WatchProgress(
        profileId, mediaKey, videoId, positionMillis, durationMillis, completed, updatedAtEpochMillis,
        preview = previewJson?.let { serialized -> runCatching { json.decodeFromString<MediaPreview>(serialized) }.getOrNull() },
        episodeLabel = episodeLabel,
    )

    private fun WatchProgress.toEntity() = WatchProgressEntity(
        profileId, mediaKey, videoId, positionMillis, durationMillis, completed, updatedAtEpochMillis,
        previewJson = preview?.let { json.encodeToString(it) },
        episodeLabel = episodeLabel,
    )
}
