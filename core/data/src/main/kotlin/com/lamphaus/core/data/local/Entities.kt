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
