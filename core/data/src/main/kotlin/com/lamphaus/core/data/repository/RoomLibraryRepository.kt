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
import android.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomLibraryRepository(
    private val dao: LamphausDao,
    private val stringCipher: StringCipher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LibraryRepository {
    override fun profiles(): Flow<List<Profile>> = dao.observeProfiles().map { rows -> rows.map { it.toModel() } }

    override suspend fun saveProfile(profile: Profile, pin: CharArray?) {
        val existing = dao.profile(profile.id)
        val salt = if (pin != null) ByteArray(16).also(SecureRandom()::nextBytes) else null
        dao.upsertProfile(
            ProfileEntity(
                id = profile.id,
                name = profile.name,
                avatarKey = profile.avatarKey,
                kind = profile.kind.name,
                pinSalt = salt?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: existing?.pinSalt,
                pinHash = if (pin != null && salt != null) hashPin(pin, salt) else existing?.pinHash,
                hideUnrated = profile.hideUnrated,
                updatedAtEpochMillis = profile.updatedAtEpochMillis,
            ),
        )
        pin?.fill('\u0000')
    }

    override suspend fun verifyPin(profileId: String, pin: CharArray): Boolean {
        val profile = dao.profile(profileId) ?: return false
        val salt = profile.pinSalt?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val expected = profile.pinHash ?: return false
        val actual = hashPin(pin, salt)
        pin.fill('\u0000')
        return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    }

    override suspend fun deleteProfile(profileId: String) {
        dao.profile(profileId)?.let { dao.deleteProfile(it) }
    }

    override fun providers(): Flow<List<ProviderSubscription>> =
        dao.observeProviders().map { rows -> rows.map { it.toModel() } }

    override suspend fun saveProvider(provider: ProviderSubscription) = dao.upsertProvider(provider.toEntity())

    override suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        dao.setProviderEnabled(providerId, enabled, System.currentTimeMillis())
    }

    override suspend fun removeProvider(providerId: String) {
        dao.provider(providerId)?.let { dao.deleteProvider(it) }
    }

    override fun library(profileId: String): Flow<List<LibraryEntry>> = dao.observeLibrary(profileId).map { rows ->
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

    override suspend fun saveLibrary(entry: LibraryEntry) = dao.upsertLibrary(
        LibraryEntity(
            profileId = entry.profileId,
            mediaKey = entry.mediaKey,
            previewJson = json.encodeToString(entry.preview),
            addedAtEpochMillis = entry.addedAtEpochMillis,
            updatedAtEpochMillis = entry.updatedAtEpochMillis,
        ),
    )

    override suspend fun removeLibrary(profileId: String, mediaKey: String) = dao.removeLibrary(profileId, mediaKey)

    override fun progress(profileId: String): Flow<List<WatchProgress>> = dao.observeProgress(profileId).map { rows ->
        rows.map { it.toModel() }
    }

    override suspend fun saveProgress(progress: WatchProgress) {
        dao.upsertProgress(progress.toEntity())
    }

    override suspend fun clearLocalAccountData() {
        dao.clearProgress()
        dao.clearLibrary()
        dao.clearProviders()
        dao.clearProfiles()
    }

    private fun hashPin(pin: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(pin, salt, 120_000, 256)
        return try {
            Base64.encodeToString(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                Base64.NO_WRAP,
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
