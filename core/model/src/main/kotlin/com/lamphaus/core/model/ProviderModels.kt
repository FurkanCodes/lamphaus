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
    /** Null means inherit the manifest-level filter. An empty array means all values. */
    val types: Set<String>? = null,
    /** Null means inherit the manifest-level filter. An empty array means all values. */
    val idPrefixes: Set<String>? = null,
)

@Serializable
data class ProviderCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extras: Set<String> = emptySet(),
    val requiredExtras: Set<String> = emptySet(),
    /** Canonical extra name to the spelling declared by the provider. */
    val extraWireNames: Map<String, String> = emptyMap(),
    val extraOptions: Map<String, List<String>> = emptyMap(),
    val extraDefaults: Map<String, String> = emptyMap(),
    val extraOptionsLimits: Map<String, Int> = emptyMap(),
    val pageSize: Int? = null,
    val showInHome: Boolean = true,
    val posterShape: String? = null,
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
    val idPrefixes: Set<String> = emptySet(),
    val catalogs: List<ProviderCatalog> = emptyList(),
    val behaviorHints: ProviderBehaviorHints = ProviderBehaviorHints(),
)

@Serializable
data class ProviderBehaviorHints(
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
    val adult: Boolean = false,
    val p2p: Boolean = false,
)

@Serializable
data class CatalogQuery(
    val type: String,
    val catalogId: String,
    val search: String? = null,
    val genre: String? = null,
    val skip: Int = 0,
    /** Provider-defined catalog filters, encoded as the protocol's extra path segment. */
    val extras: Map<String, String> = emptyMap(),
    val posterShape: String? = null,
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
    /**
     * Which field supplied [rating]: "imdb" when the addon exposed an IMDb
     * score, "provider" for a generic provider rating, null when unknown.
     * A generic rating must never be labeled IMDb (SHR-PROD-05).
     */
    val ratingSource: String? = null,
    val providerIds: Set<String> = emptySet(),
    val posterShape: String? = null,
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
    val streams: List<StreamCandidate> = emptyList(),
)

@Serializable
data class MediaDetail(
    val preview: MediaPreview,
    val runtimeMinutes: Int? = null,
    val cast: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val embeddedStreams: List<StreamCandidate> = emptyList(),
)

@Serializable
data class StreamFile(
    val url: String,
    val bytes: Long? = null,
)

@Serializable
data class StreamCandidate(
    val providerId: String,
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val externalUrl: String? = null,
    val infoHash: String? = null,
    val fileIndex: Int? = null,
    val ytId: String? = null,
    val sourceUrls: List<String> = emptyList(),
    val nzbUrl: String? = null,
    val servers: List<String> = emptyList(),
    val rarFiles: List<StreamFile> = emptyList(),
    val zipFiles: List<StreamFile> = emptyList(),
    val sevenZipFiles: List<StreamFile> = emptyList(),
    val tgzFiles: List<StreamFile> = emptyList(),
    val tarFiles: List<StreamFile> = emptyList(),
    val fileMustInclude: String? = null,
    val filename: String? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val bingeGroup: String? = null,
    val mimeType: String? = null,
    val quality: String? = null,
    val tags: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val notWebReady: Boolean = false,
    val countryWhitelist: List<String> = emptyList(),
) {
    val isPlayableInternally: Boolean get() = url?.startsWith("https://", ignoreCase = true) == true
    val sourceLabel: String get() = title?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        .ifBlank { description?.lineSequence()?.firstOrNull()?.trim().orEmpty() }
        .ifBlank { name.ifBlank { "Source" } }

    val archiveFiles: List<StreamFile>
        get() = rarFiles + zipFiles + sevenZipFiles + tgzFiles + tarFiles
}

@Serializable
data class SubtitleTrack(
    val id: String,
    val language: String,
    val url: String,
    val format: String? = null,
    val headers: Map<String, String> = emptyMap(),
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
