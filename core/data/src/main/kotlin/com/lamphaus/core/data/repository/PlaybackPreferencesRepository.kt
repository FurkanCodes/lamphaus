package com.lamphaus.core.data.repository

import com.lamphaus.core.data.local.AudioRouteSettingsEntity
import com.lamphaus.core.data.local.LamphausDao
import com.lamphaus.core.data.local.MediaPlaybackSelectionEntity
import com.lamphaus.core.data.local.PlaybackPrefsDao
import com.lamphaus.core.data.local.ProfilePlaybackPrefsEntity
import com.lamphaus.core.data.local.SourcePlaybackSelectionEntity
import com.lamphaus.core.model.AudioRouteSettings
import com.lamphaus.core.model.MediaPlaybackSelection
import com.lamphaus.core.model.ProfilePlaybackPreferences
import com.lamphaus.core.model.SourcePlaybackSelection
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Room-backed player V2 preference storage (SHR-ARC-05/13): one owner for
 * profile defaults, semantic per-title choices, exact source selections, and
 * per-route audio timing. The cloud mirror lives in the sync gateway; this
 * store is always the local source of truth.
 */
interface PlaybackPreferencesRepository {
    suspend fun profilePreferences(profileId: String): ProfilePlaybackPreferences
    suspend fun saveProfilePreferences(profileId: String, prefs: ProfilePlaybackPreferences)

    suspend fun mediaSelection(profileId: String, mediaKey: String): MediaPlaybackSelection?
    suspend fun saveMediaSelection(profileId: String, mediaKey: String, selection: MediaPlaybackSelection)
    suspend fun clearMediaSelection(profileId: String, mediaKey: String)

    suspend fun sourceSelection(
        profileId: String,
        mediaKey: String,
        sourceFingerprint: String,
    ): SourcePlaybackSelection?

    suspend fun saveSourceSelection(selection: SourcePlaybackSelection)

    suspend fun audioRouteSettings(routeFingerprint: String): AudioRouteSettings
    suspend fun saveAudioRouteSettings(settings: AudioRouteSettings)
}

class DefaultPlaybackPreferencesRepository(
    private val dao: PlaybackPrefsDao,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : PlaybackPreferencesRepository {

    override suspend fun profilePreferences(profileId: String): ProfilePlaybackPreferences =
        dao.profilePlaybackPrefs(profileId)?.decodeOrNull() ?: ProfilePlaybackPreferences()

    override suspend fun saveProfilePreferences(profileId: String, prefs: ProfilePlaybackPreferences) {
        dao.upsertProfilePlaybackPrefs(
            ProfilePlaybackPrefsEntity(
                profileId = profileId,
                payloadJson = json.encodeToString(ProfilePlaybackPreferences.serializer(), prefs),
                updatedAtEpochMillis = prefs.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun mediaSelection(profileId: String, mediaKey: String): MediaPlaybackSelection? =
        dao.mediaPlaybackSelection(profileId, mediaKey)?.decodeOrNull()

    override suspend fun saveMediaSelection(profileId: String, mediaKey: String, selection: MediaPlaybackSelection) {
        dao.upsertMediaPlaybackSelection(
            MediaPlaybackSelectionEntity(
                profileId = profileId,
                mediaKey = mediaKey,
                payloadJson = json.encodeToString(MediaPlaybackSelection.serializer(), selection),
                updatedAtEpochMillis = selection.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun clearMediaSelection(profileId: String, mediaKey: String) {
        dao.deleteMediaPlaybackSelection(profileId, mediaKey)
    }

    override suspend fun sourceSelection(
        profileId: String,
        mediaKey: String,
        sourceFingerprint: String,
    ): SourcePlaybackSelection? =
        dao.sourcePlaybackSelection(profileId, mediaKey, sourceFingerprint)?.toModel()

    override suspend fun saveSourceSelection(selection: SourcePlaybackSelection) {
        dao.upsertSourcePlaybackSelection(
            SourcePlaybackSelectionEntity(
                profileId = selection.profileId,
                mediaKey = selection.mediaKey,
                sourceFingerprint = selection.sourceFingerprint,
                audioTrackId = selection.audioTrackId,
                subtitleTrackId = selection.subtitleTrackId,
                subtitleDelayMillis = selection.subtitleDelayMillis,
                audioDelayMillis = selection.audioDelayMillis,
                updatedAtEpochMillis = selection.updatedAtEpochMillis,
            ),
        )
    }

    override suspend fun audioRouteSettings(routeFingerprint: String): AudioRouteSettings =
        dao.audioRouteSettings(routeFingerprint)?.toModel() ?: AudioRouteSettings(routeFingerprint)

    override suspend fun saveAudioRouteSettings(settings: AudioRouteSettings) {
        dao.upsertAudioRouteSettings(
            AudioRouteSettingsEntity(
                routeFingerprint = settings.routeFingerprint,
                audioDelayMillis = settings.audioDelayMillis,
                updatedAtEpochMillis = settings.updatedAtEpochMillis,
            ),
        )
    }

    private fun ProfilePlaybackPrefsEntity.decodeOrNull(): ProfilePlaybackPreferences? = try {
        json.decodeFromString(ProfilePlaybackPreferences.serializer(), payloadJson)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun MediaPlaybackSelectionEntity.decodeOrNull(): MediaPlaybackSelection? = try {
        json.decodeFromString(MediaPlaybackSelection.serializer(), payloadJson)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun SourcePlaybackSelectionEntity.toModel() = SourcePlaybackSelection(
        profileId = profileId,
        mediaKey = mediaKey,
        sourceFingerprint = sourceFingerprint,
        audioTrackId = audioTrackId,
        subtitleTrackId = subtitleTrackId,
        subtitleDelayMillis = subtitleDelayMillis,
        audioDelayMillis = audioDelayMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun AudioRouteSettingsEntity.toModel() = AudioRouteSettings(
        routeFingerprint = routeFingerprint,
        audioDelayMillis = audioDelayMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
