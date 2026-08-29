package com.lamphaus.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ArtworkProvider {
    @SerialName("tmdb")
    TMDB,
}

/**
 * A per-profile artwork customization for one title. Paths are TMDB image
 * paths (e.g. "/abc123.jpg"); URLs are built per surface with the right size.
 * Either path may be null — a row can override any subset of poster, backdrop,
 * and logo artwork.
 */
@Serializable
data class ArtworkOverride(
    val profileId: String,
    val mediaKey: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val logoPath: String? = null,
    val updatedAtEpochMillis: Long = 0,
)

/**
 * Editor candidates for one title, normalized from the artwork provider.
 * Carries provider-relative paths, never URLs or key material.
 */
@Serializable
data class ArtworkCandidates(
    val provider: ArtworkProvider = ArtworkProvider.TMDB,
    val posters: List<String> = emptyList(),
    val backdrops: List<String> = emptyList(),
    val logos: List<String> = emptyList(),
)
