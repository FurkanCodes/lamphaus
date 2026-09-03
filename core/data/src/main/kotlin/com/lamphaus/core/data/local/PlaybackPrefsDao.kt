package com.lamphaus.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** Narrow seam for player V2 preference storage (SHR-ARC-15); implemented by [LamphausDao]. */
@Dao
interface PlaybackPrefsDao {
    @Query("SELECT * FROM profile_playback_prefs WHERE profileId = :profileId")
    suspend fun profilePlaybackPrefs(profileId: String): ProfilePlaybackPrefsEntity?

    @Upsert
    suspend fun upsertProfilePlaybackPrefs(entity: ProfilePlaybackPrefsEntity)

    @Query(
        "SELECT * FROM media_playback_selections " +
            "WHERE profileId = :profileId AND mediaKey = :mediaKey",
    )
    suspend fun mediaPlaybackSelection(profileId: String, mediaKey: String): MediaPlaybackSelectionEntity?

    @Upsert
    suspend fun upsertMediaPlaybackSelection(entity: MediaPlaybackSelectionEntity)

    @Query(
        "DELETE FROM media_playback_selections " +
            "WHERE profileId = :profileId AND mediaKey = :mediaKey",
    )
    suspend fun deleteMediaPlaybackSelection(profileId: String, mediaKey: String)

    @Query(
        "SELECT * FROM source_playback_selections " +
            "WHERE profileId = :profileId AND mediaKey = :mediaKey AND sourceFingerprint = :sourceFingerprint",
    )
    suspend fun sourcePlaybackSelection(
        profileId: String,
        mediaKey: String,
        sourceFingerprint: String,
    ): SourcePlaybackSelectionEntity?

    @Upsert
    suspend fun upsertSourcePlaybackSelection(entity: SourcePlaybackSelectionEntity)

    @Query(
        "SELECT * FROM source_playback_selections " +
            "WHERE profileId = :profileId AND mediaKey = :mediaKey",
    )
    suspend fun sourceSelectionsForMedia(profileId: String, mediaKey: String): List<SourcePlaybackSelectionEntity>

    @Query("SELECT * FROM audio_route_settings WHERE routeFingerprint = :routeFingerprint")
    suspend fun audioRouteSettings(routeFingerprint: String): AudioRouteSettingsEntity?

    @Upsert
    suspend fun upsertAudioRouteSettings(entity: AudioRouteSettingsEntity)

    @Query("DELETE FROM media_playback_selections WHERE profileId = :profileId")
    suspend fun clearMediaPlaybackSelections(profileId: String)

    @Query("DELETE FROM source_playback_selections WHERE profileId = :profileId")
    suspend fun clearSourcePlaybackSelections(profileId: String)
}
