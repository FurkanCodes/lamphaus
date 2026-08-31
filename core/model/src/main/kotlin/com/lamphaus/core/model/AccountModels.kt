package com.lamphaus.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProfileKind { ADULT, CHILD }

/**
 * A TV paired to the account, as surfaced in mobile settings (plan D3).
 * [createdAt] is the raw ISO timestamp from Postgres — display-only.
 */
@Serializable
data class PairedDevice(
    val id: String,
    val label: String,
    val platform: String = "android-tv",
    val createdAt: String? = null,
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val avatarKey: String,
    val kind: ProfileKind,
    val hasPin: Boolean = false,
    val hideUnrated: Boolean = kind == ProfileKind.CHILD,
    val updatedAtEpochMillis: Long = 0,
)

@Serializable
data class ProviderSubscription(
    val id: String,
    val manifestUrl: String,
    val displayName: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val updatedAtEpochMillis: Long = 0,
)

@Serializable
data class LibraryEntry(
    val profileId: String,
    val mediaKey: String,
    val preview: MediaPreview,
    val addedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class WatchProgress(
    val profileId: String,
    val mediaKey: String,
    val videoId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
    /**
     * Snapshot of the catalog item this progress belongs to. Continue Watching
     * renders from this directly so entries surface even when the title is not
     * present in any loaded home catalog section (NuvioTV-style hydration).
     */
    val preview: MediaPreview? = null,
    /** Episode identifier shown on Continue Watching entries (e.g. "S1 · E4 · Pilot"); null for movies. */
    val episodeLabel: String? = null,
) {
    val fraction: Float
        get() = if (durationMillis <= 0) 0f else (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
}

@Serializable
data class DeviceRegistration(
    val id: String,
    val label: String,
    val platform: String,
    val lastSeenAtEpochMillis: Long,
    val revoked: Boolean = false,
)

@Serializable
data class PairingSession(
    val id: String,
    val shortCode: String,
    val qrPayload: String,
    val expiresAtEpochMillis: Long,
    val claimed: Boolean = false,
)

/**
 * Result of polling [com.lamphaus.core.data.cloud.PairingGateway.exchangeDeviceGrant]
 * during TV pairing. [Granted] carries single-use GoTrue login material that
 * must be consumed immediately — the server burns it on handoff.
 * [Expired]/[Consumed] are terminal: the poll loop regenerates (Expired) or
 * demands a fresh QR (Consumed) instead of polling until the local timer dies.
 */
sealed interface DeviceGrant {
    data object Pending : DeviceGrant
    data object Expired : DeviceGrant
    data object Consumed : DeviceGrant
    data class Granted(val email: String, val otp: String, val deviceId: String) : DeviceGrant
}

@Serializable
data class DiagnosticsConsent(
    val crashReports: Boolean = false,
    val performanceMetrics: Boolean = false,
    val updatedAtEpochMillis: Long = 0,
)

@Serializable
data class SpoilerProtectionSettings(
    val enabled: Boolean = true,
    val blurEpisodeArtwork: Boolean = true,
    val blurEpisodeSynopsis: Boolean = true,
)

