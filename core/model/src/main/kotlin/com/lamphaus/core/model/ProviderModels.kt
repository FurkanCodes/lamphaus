package com.lamphaus.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MediaType {
    @SerialName("movie")
    MOVIE,

    @SerialName("series")
    SERIES,

    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
data class ProviderResource(
    val name: String,
    val types: Set<String> = emptySet(),
    val idPrefixes: Set<String> = emptySet(),
)

@Serializable
data class ProviderCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extras: Set<String> = emptySet(),
    val requiredExtras: Set<String> = emptySet(),
)

@Serializable
data class ProviderManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val backgroundUrl: String? = null,
    val resources: List<ProviderResource> = emptyList(),
    val types: Set<String> = emptySet(),
    val catalogs: List<ProviderCatalog> = emptyList(),
)

@Serializable
data class CatalogQuery(
    val type: String,
    val catalogId: String,
    val search: String? = null,
    val genre: String? = null,
    val skip: Int = 0,
)

@Serializable
data class MediaPreview(
    val id: String,
    val type: MediaType,
    val rawType: String,
    val name: String,
    val posterUrl: String? = null,
    val backgroundUrl: String? = null,
    val logoUrl: String? = null,
    val description: String? = null,
    val releaseYear: Int? = null,
    val genres: List<String> = emptyList(),
    val contentRating: String? = null,
    val rating: Double? = null,
    val providerIds: Set<String> = emptySet(),
) {
    val stableKey: String get() = "${rawType.lowercase()}:$id"
}

@Serializable
data class Episode(
    val id: String,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val thumbnailUrl: String? = null,
    val releasedAtEpochMillis: Long? = null,
)

@Serializable
data class MediaDetail(
    val preview: MediaPreview,
    val runtimeMinutes: Int? = null,
    val cast: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val episodes: List<Episode> = emptyList(),
)

@Serializable
data class StreamCandidate(
    val providerId: String,
    val name: String,
    val title: String? = null,
    val url: String? = null,
    val externalUrl: String? = null,
    val quality: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    val isPlayableInternally: Boolean get() = url?.startsWith("https://") == true
}

@Serializable
data class SubtitleTrack(
    val id: String,
    val language: String,
    val url: String,
)

sealed interface ProviderResult<out T> {
    data class Success<T>(val value: T, val isStale: Boolean = false) : ProviderResult<T>
    data class Failure(val kind: ProviderFailureKind, val safeMessage: String) : ProviderResult<Nothing>
}

enum class ProviderFailureKind {
    INVALID_URL,
    UNSUPPORTED_CAPABILITY,
    TIMEOUT,
    NETWORK,
    MALFORMED_RESPONSE,
    HTTP,
}
