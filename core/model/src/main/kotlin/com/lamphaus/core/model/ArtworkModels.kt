package com.lamphaus.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ArtworkProvider {
    @SerialName("tmdb")
    TMDB,

    @SerialName("fanart")
    FANART,
}

@Serializable
data class ArtworkAsset(
    val provider: ArtworkProvider,
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
    @SerialName("success")
    SUCCESS,

    @SerialName("no_match")
    NO_MATCH,

    @SerialName("missing_external_id")
    MISSING_EXTERNAL_ID,

    @SerialName("invalid_key")
    INVALID_KEY,

    @SerialName("lookup_failed")
    LOOKUP_FAILED,
}

@Serializable
data class ArtworkProviderResult(
    val provider: ArtworkProvider,
    val status: ArtworkLookupStatus,
)

@Serializable
data class ArtworkProviderStatus(
    val provider: ArtworkProvider,
    val configured: Boolean,
)

@Serializable
data class ArtworkCandidates(
    val posters: List<ArtworkAsset> = emptyList(),
    val backdrops: List<ArtworkAsset> = emptyList(),
    val logos: List<ArtworkAsset> = emptyList(),
    val providerResults: List<ArtworkProviderResult> = emptyList(),
)
