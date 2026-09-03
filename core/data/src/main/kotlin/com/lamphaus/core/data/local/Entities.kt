package com.lamphaus.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarKey: String,
    val kind: String,
    val pinSalt: String?,
    val pinHash: String?,
    val hideUnrated: Boolean,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "providers",
    indices = [Index(value = ["sortOrder"])],
)
data class ProviderEntity(
    @PrimaryKey val id: String,
    val manifestUrl: String,
    val displayName: String,
    val enabled: Boolean,
    val sortOrder: Int,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "library",
    primaryKeys = ["profileId", "mediaKey"],
    indices = [Index(value = ["profileId", "updatedAtEpochMillis"])],
)
data class LibraryEntity(
    val profileId: String,
    val mediaKey: String,
    val previewJson: String,
    val addedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "watch_progress",
    primaryKeys = ["profileId", "videoId"],
    indices = [Index(value = ["profileId", "updatedAtEpochMillis"])],
)
data class WatchProgressEntity(
    val profileId: String,
    val mediaKey: String,
    val videoId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
    /** Serialized [com.lamphaus.core.model.MediaPreview] snapshot; null for legacy rows. */
    val previewJson: String? = null,
    /** Episode label for series entries ("S1 · E4 · Pilot"); null for movies. */
    val episodeLabel: String? = null,
)

/**
 * Keys confirmed by the last successful cloud snapshot. Persisting this set
 * lets reconciliation distinguish a remote deletion from a local row whose
 * upload has not succeeded yet.
 */
@Entity(
    tableName = "cloud_sync_keys",
    primaryKeys = ["profileId", "collection", "itemKey"],
    indices = [Index(value = ["profileId", "collection"])],
)
data class CloudSyncKeyEntity(
    val profileId: String,
    val collection: String,
    val itemKey: String,
)

/**
 * Provider-neutral detail enrichment (TMDB credits/facts, MDBList ratings),
 * cached as serialized [com.lamphaus.core.model.DetailEnrichment] keyed by
 * the canonical media key (SHR-ARC-13). Never synced to the cloud.
 */
@Entity(tableName = "detail_enrichment")
data class DetailEnrichmentEntity(
    @PrimaryKey val mediaKey: String,
    val payloadJson: String,
    val fetchedAtEpochMillis: Long,
)

/**
 * Profile-owned playback defaults (languages, subtitle mode/style, original
 * colors), stored as the serialized [com.lamphaus.core.model.ProfilePlaybackPreferences]
 * payload so the shape evolves without migrations. Mirrored to the cloud
 * `profile_playback_preferences` row for the signed-in profile.
 */
@Entity(tableName = "profile_playback_prefs")
data class ProfilePlaybackPrefsEntity(
    @PrimaryKey val profileId: String,
    val payloadJson: String,
    val updatedAtEpochMillis: Long,
)

/**
 * Semantic per-title choice ("this series plays with Japanese audio and
 * English subtitles") synced per profile; never carries exact track ids.
 */
@Entity(
    tableName = "media_playback_selections",
    primaryKeys = ["profileId", "mediaKey"],
    indices = [Index(value = ["profileId", "updatedAtEpochMillis"])],
)
data class MediaPlaybackSelectionEntity(
    val profileId: String,
    val mediaKey: String,
    val payloadJson: String,
    val updatedAtEpochMillis: Long,
)

/**
 * Exact track selection and timing remembered for one source fingerprint.
 * Provider-shaped and device-local: never synced (plan §5, SHR-PROD-06).
 */
@Entity(
    tableName = "source_playback_selections",
    primaryKeys = ["profileId", "mediaKey", "sourceFingerprint"],
)
data class SourcePlaybackSelectionEntity(
    val profileId: String,
    val mediaKey: String,
    val sourceFingerprint: String,
    val audioTrackId: String?,
    val subtitleTrackId: String?,
    val subtitleDelayMillis: Long,
    val audioDelayMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** Per-output-route audio timing, remembered locally (plan §2). */
@Entity(tableName = "audio_route_settings")
data class AudioRouteSettingsEntity(
    @PrimaryKey val routeFingerprint: String,
    val audioDelayMillis: Long,
    val updatedAtEpochMillis: Long,
)
