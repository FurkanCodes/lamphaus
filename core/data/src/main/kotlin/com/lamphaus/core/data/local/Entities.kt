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
)

