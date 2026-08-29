package com.lamphaus.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ArtworkProviderId(val value: String) {
    companion object {
        private val pattern = Regex("^[a-z][a-z0-9_-]{0,63}$")
        val TMDB = ArtworkProviderId("tmdb")
        val FANART = ArtworkProviderId("fanart")

        fun parseOrNull(value: String): ArtworkProviderId? =
            value.takeIf { pattern.matches(it) }?.let(::ArtworkProviderId)
    }
}

@Serializable
data class ArtworkAsset(
    val provider: ArtworkProviderId,
    val reference: String,
)

@Serializable
data class ArtworkOverride(
    val profileId: String,
    val mediaKey: String,
    val poster: ArtworkAsset? = null,
    val backdrop: ArtworkAsset? = null,
    val logo: ArtworkAsset? = null,
    val updatedAtEpochMillis: Long = 0,
)

@Serializable
enum class ArtworkLookupStatus {
    SUCCESS,
    NO_MATCH,
    MISSING_EXTERNAL_ID,
    INVALID_KEY,
    LOOKUP_FAILED,
}

@Serializable
data class ArtworkProviderResult(
    val provider: ArtworkProviderId,
    val status: ArtworkLookupStatus,
    val displayName: String = provider.value,
)

@Serializable
data class ArtworkProviderStatus(
    val provider: ArtworkProviderId,
    val displayName: String = provider.value,
    val purpose: String = "",
    val helpText: String = "",
    val keyPageUrl: String = "",
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val configured: Boolean = false,
)

@Serializable
data class ArtworkCandidates(
    val posters: List<ArtworkAsset> = emptyList(),
    val backdrops: List<ArtworkAsset> = emptyList(),
    val logos: List<ArtworkAsset> = emptyList(),
    val providerResults: List<ArtworkProviderResult> = emptyList(),
)
