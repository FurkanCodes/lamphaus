package com.lamphaus.core.model

import kotlinx.serialization.Serializable

/**
 * One score from one rating source (SHR-ARC-16). [value] is expressed on
 * [scale] (IMDb 0-10, Rotten Tomatoes 0-100, Letterboxd 0-5, ...); consumers
 * normalize through [normalizedTen] instead of rescaling by hand.
 */
@Serializable
data class RatingSourceScore(
    val sourceId: String,
    val displayName: String,
    val value: Double,
    val scale: Double = 10.0,
    val voteCount: Long? = null,
    val logoUrl: String? = null,
    val detailsUrl: String? = null,
) {
    val normalizedTen: Double
        get() = ((value / scale) * 10.0).coerceIn(0.0, 10.0)
}

/** A cast or crew member resolved by TMDB. Renderers stay non-focusable unless Select performs an action. */
@Serializable
data class PersonCredit(
    val personId: String? = null,
    val name: String,
    val role: String? = null,
    val profileUrl: String? = null,
)

/** Production facts that the provider addon manifest cannot express. */
@Serializable
data class MediaFacts(
    val status: String? = null,
    val originalLanguage: String? = null,
    val budgetUsd: Long? = null,
    val revenueUsd: Long? = null,
)

/**
 * Enrichment merged from provider-neutral sources (TMDB facts/credits,
 * MDBList aggregate ratings). Cached under [mediaKey]; see
 * [MediaPreview.enrichmentMediaKey] for the canonical key shape.
 */
@Serializable
data class DetailEnrichment(
    val mediaKey: String,
    val cast: List<PersonCredit> = emptyList(),
    val crew: List<PersonCredit> = emptyList(),
    val similar: List<MediaPreview> = emptyList(),
    val ratings: List<RatingSourceScore> = emptyList(),
    val facts: MediaFacts? = null,
    val personalRating: Int? = null,
    val fetchedAtEpochMillis: Long,
)

@Serializable
data class DetailEnrichmentRequest(
    val mediaKey: String,
    val type: String,
    val id: String,
    val name: String? = null,
    val releaseYear: Int? = null,
)

/** Stored integration state; credential material never travels back (SHR-PROD-06). */
@Serializable
data class IntegrationStatus(
    val integration: String,
    val connected: Boolean,
    /** Null when the server could not verify the key this pass. */
    val valid: Boolean? = null,
    val enabledSources: List<String> = emptyList(),
)

/**
 * Canonical enrichment cache key. Provider ids that already carry an external
 * identity reuse it; anything else stays provider-scoped so remakes and
 * localized titles can never collide across providers.
 */
fun MediaPreview.enrichmentMediaKey(): String = when {
    id.startsWith("tt") -> "imdb:$id"
    id.startsWith("tmdb:") -> "tmdb:${type.wireName()}:${id.removePrefix("tmdb:")}"
    else -> "provider:${providerIds.minOrNull() ?: "unknown"}:${type.wireName()}:$id"
}

/** Stremio wire spelling ("movie"/"series") used by both edge contracts. */
fun MediaType.wireName(): String = when (this) {
    MediaType.MOVIE -> "movie"
    MediaType.SERIES -> "series"
    MediaType.UNKNOWN -> "unknown"
}
