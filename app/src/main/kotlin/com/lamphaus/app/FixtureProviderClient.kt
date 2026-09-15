package com.lamphaus.app

import com.lamphaus.app.ui.PreviewMedia
import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.ProviderBehaviorHints
import com.lamphaus.core.model.ProviderCatalog
import com.lamphaus.core.model.ProviderFailureKind
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResource
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.SubtitleTrack
import com.lamphaus.core.provider.ProviderClient

/**
 * Deterministic, network-free provider for benchmark fixtures (PERF-02). The
 * fixture APK uses this instead of the HTTP client so Home, search, details,
 * and playback-start timings are reproducible and never touch a personal
 * account, provider URL, or the network (SHR-PROD-06).
 *
 * Only the benchmark/debug fixture build paths construct this; production
 * always uses [com.lamphaus.core.provider.HttpProviderClient].
 */
class FixtureProviderClient : ProviderClient {

    override suspend fun manifest(manifestUrl: String): ProviderResult<ProviderManifest> =
        ProviderResult.Success(FIXTURE_MANIFEST)

    override suspend fun discoverProviderUrls(catalogUrl: String): ProviderResult<List<String>> =
        ProviderResult.Success(listOf(FIXTURE_MANIFEST_URL))

    override suspend fun catalog(
        manifestUrl: String,
        providerId: String,
        query: CatalogQuery,
    ): ProviderResult<List<MediaPreview>> {
        val type = query.type.lowercase()
        val search = query.search
        val items = PreviewMedia.items
            .filter { it.rawType == type }
            .filter { media ->
                search.isNullOrBlank() || media.name.contains(search, ignoreCase = true)
            }
        return ProviderResult.Success(items)
    }

    override suspend fun meta(
        manifestUrl: String,
        providerId: String,
        type: String,
        id: String,
    ): ProviderResult<MediaDetail> {
        val media = PreviewMedia.items.firstOrNull { it.id == id }
            ?: return ProviderResult.Failure(ProviderFailureKind.MALFORMED_RESPONSE, "Unknown fixture title.")
        return ProviderResult.Success(
            MediaDetail(
                preview = media,
                runtimeMinutes = if (media.rawType == "movie") 104 else null,
                episodes = if (media.rawType == "series") PreviewMedia.episodes else emptyList(),
            ),
        )
    }

    override suspend fun streams(
        manifestUrl: String,
        providerId: String,
        type: String,
        id: String,
    ): ProviderResult<List<StreamCandidate>> = ProviderResult.Success(emptyList())

    override suspend fun subtitles(
        manifestUrl: String,
        type: String,
        id: String,
        extras: Map<String, String>,
    ): ProviderResult<List<SubtitleTrack>> = ProviderResult.Success(emptyList())

    companion object {
        const val PROVIDER_ID = "local-fixture"
        const val FIXTURE_MANIFEST_URL = "https://fixture.lamphaus.invalid/manifest.json"

        val FIXTURE_MANIFEST = ProviderManifest(
            id = PROVIDER_ID,
            name = "Local fixture",
            version = "1",
            description = "Deterministic benchmark fixture catalog.",
            resources = listOf(
                ProviderResource("catalog", setOf("movie", "series")),
                ProviderResource("meta", setOf("movie", "series")),
                ProviderResource("stream", setOf("movie", "series")),
            ),
            types = setOf("movie", "series"),
            idPrefixes = setOf("fixture:"),
            catalogs = listOf(
                ProviderCatalog(
                    type = "movie",
                    id = "featured",
                    name = "Featured",
                    extras = setOf("search", "skip"),
                    extraWireNames = mapOf("search" to "search", "skip" to "skip"),
                ),
                ProviderCatalog(
                    type = "series",
                    id = "featured",
                    name = "Featured",
                    extras = setOf("search", "skip"),
                    extraWireNames = mapOf("search" to "search", "skip" to "skip"),
                ),
            ),
            behaviorHints = ProviderBehaviorHints(),
        )
    }
}
