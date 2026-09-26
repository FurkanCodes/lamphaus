package com.lamphaus.app

import com.lamphaus.app.ui.PreviewMedia
import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.ProviderBehaviorHints
import com.lamphaus.core.model.ProviderCatalog
import com.lamphaus.core.model.ProviderFailureKind
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResource
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.SubtitleTrack
import com.lamphaus.core.provider.ProviderClient
import kotlinx.coroutines.delay

/**
 * Deterministic, network-free provider for benchmark fixtures (PERF-02). The
 * fixture APK uses this instead of the HTTP client so Home, search, details,
 * and playback-start timings are reproducible and never touch a personal
 * account, provider URL, or the network (SHR-PROD-06).
 *
 * With [stress] enabled (build property `lamphaus.benchmarkStress`):
 * - every provider exposes [STRESS_MANIFEST]'s six catalogs, so the 20 seeded
 *   providers add 120 Home rows;
 * - each catalog answers after a deterministic 150-1200 ms and returns
 *   [STRESS_ITEMS_PER_CATALOG] synthetic rows, while some catalogs are
 *   empty;
 * - each provider offers [STRESS_STREAMS_PER_PROVIDER] sources after a
 *   staggered delay;
 * - one provider times out and the last answers after
 *   [STRESS_SLOW_PROVIDER_DELAY_MILLIS].
 * The matrix can therefore exercise large addon sets without a real network.
 *
 * Only the benchmark/debug fixture build paths construct this; production
 * always uses [com.lamphaus.core.provider.HttpProviderClient].
 */
class FixtureProviderClient(
    private val stress: Boolean = BuildConfig.BENCHMARK_STRESS,
) : ProviderClient {

    override suspend fun manifest(manifestUrl: String): ProviderResult<ProviderManifest> =
        ProviderResult.Success(if (stress) STRESS_MANIFEST else FIXTURE_MANIFEST)

    override suspend fun discoverProviderUrls(catalogUrl: String): ProviderResult<List<String>> =
        ProviderResult.Success(listOf(FIXTURE_MANIFEST_URL))

    override suspend fun catalog(
        manifestUrl: String,
        providerId: String,
        query: CatalogQuery,
    ): ProviderResult<List<MediaPreview>> {
        if (stress) {
            val index = providerIndex(providerId)
            if (index == stressProviderCount() - 2) {
                return ProviderResult.Failure(
                    ProviderFailureKind.TIMEOUT,
                    "Stress fixture provider timed out.",
                )
            }
            delay(
                if (index == stressProviderCount() - 1) {
                    STRESS_SLOW_PROVIDER_DELAY_MILLIS
                } else {
                    stressLatencyMillis(index, query.catalogId)
                },
            )
            if (query.search.isNullOrBlank() && stressCatalogIsEmpty(index, query.catalogId)) {
                return ProviderResult.Success(emptyList())
            }
            return ProviderResult.Success(stressItems(providerId, query))
        }
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
            ?: stressMedia(id, providerId)
            ?: return ProviderResult.Failure(ProviderFailureKind.MALFORMED_RESPONSE, "Unknown fixture title.")
        return ProviderResult.Success(
            MediaDetail(
                preview = media,
                runtimeMinutes = if (media.rawType == "movie") 104 else null,
                episodes = if (media.rawType == "series") fixtureEpisodes() else emptyList(),
            ),
        )
    }

    override suspend fun streams(
        manifestUrl: String,
        providerId: String,
        type: String,
        id: String,
    ): ProviderResult<List<StreamCandidate>> {
        if (!stress) return ProviderResult.Success(emptyList())
        val index = providerIndex(providerId)
        if (index == stressProviderCount() - 2) {
            return ProviderResult.Failure(ProviderFailureKind.TIMEOUT, "Stress fixture provider timed out.")
        }
        delay(
            if (index == stressProviderCount() - 1) {
                STRESS_SLOW_PROVIDER_DELAY_MILLIS
            } else {
                // Staggered so later providers can answer before earlier ones.
                STRESS_STREAM_MIN_DELAY_MILLIS + (index * 373L) % STRESS_STREAM_DELAY_SPREAD_MILLIS
            },
        )
        return ProviderResult.Success(fixtureStressStreams(index, id))
    }

    override suspend fun subtitles(
        manifestUrl: String,
        type: String,
        id: String,
        extras: Map<String, String>,
    ): ProviderResult<List<SubtitleTrack>> = ProviderResult.Success(emptyList())

    private fun stressItems(providerId: String, query: CatalogQuery): List<MediaPreview> {
        val type = query.type.lowercase().takeIf { it == "movie" || it == "series" } ?: "movie"
        val search = query.search
        // Provider zero keeps the named fixture titles so existing journeys
        // still find "The Last Aurora" while the stress rows load around it.
        val base = if (providerIndex(providerId) == 0) PreviewMedia.items.filter { it.rawType == type } else emptyList()
        val generated = fixtureStressItems(providerIndex(providerId), STRESS_ITEMS_PER_CATALOG, type)
        return (base + generated).filter { media ->
            search.isNullOrBlank() || media.name.contains(search, ignoreCase = true)
        }
    }

    private fun stressMedia(id: String, providerId: String): MediaPreview? {
        val parts = id.removePrefix(STRESS_ID_PREFIX).split(':')
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val type = parts.getOrNull(0)?.takeIf { it == "series" } ?: "movie"
        return fixtureStressItems(providerIndex(providerId), STRESS_ITEMS_PER_CATALOG, type)
            .firstOrNull { it.id == id }
            ?: fixtureStressItem(providerIndex(providerId), index, type)
    }

    companion object {
        const val PROVIDER_ID = "local-fixture"
        const val FIXTURE_MANIFEST_URL = "https://fixture.lamphaus.invalid/manifest.json"
        const val STRESS_PROVIDER_COUNT = 20
        const val STRESS_ITEMS_PER_CATALOG = 100
        const val STRESS_LIBRARY_ROWS = 10_000
        const val STRESS_SLOW_PROVIDER_DELAY_MILLIS = 5_000L
        const val STRESS_STREAMS_PER_PROVIDER = 15
        private const val STRESS_CATALOG_MIN_DELAY_MILLIS = 150L
        private const val STRESS_CATALOG_DELAY_SPREAD_MILLIS = 1_050L
        private const val STRESS_STREAM_MIN_DELAY_MILLIS = 150L
        private const val STRESS_STREAM_DELAY_SPREAD_MILLIS = 2_400L
        private val STRESS_CATALOG_IDS = listOf("featured", "trending", "fresh")

        internal const val STRESS_ID_PREFIX = "fixture:stress:"

        /** Fixture episodes carry local artwork so episode cards render like real ones. */
        internal fun fixtureEpisodes() = PreviewMedia.episodes.map { episode ->
            episode.copy(thumbnailUrl = "android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.poster_glass}")
        }

        fun stressProviderId(index: Int): String = "$PROVIDER_ID-$index"

        fun stressProviderManifestUrl(index: Int): String = "$FIXTURE_MANIFEST_URL?provider=$index"

        internal fun providerIndex(providerId: String): Int =
            providerId.substringAfterLast('-').toIntOrNull()?.coerceAtLeast(0) ?: 0

        internal fun stressProviderCount(): Int = STRESS_PROVIDER_COUNT

        /** Deterministic per-catalog latency for the stress fixture. */
        internal fun stressLatencyMillis(provider: Int, catalogId: String): Long =
            STRESS_CATALOG_MIN_DELAY_MILLIS +
                Math.floorMod((provider * 31 + catalogId.hashCode()).toLong(), STRESS_CATALOG_DELAY_SPREAD_MILLIS)

        /** A quarter of providers have an empty "fresh" catalog. */
        internal fun stressCatalogIsEmpty(provider: Int, catalogId: String): Boolean =
            catalogId == "fresh" && provider % 4 == 1

        /** Deterministic synthetic sources for the stress fixture. */
        internal fun fixtureStressStreams(provider: Int, videoId: String): List<StreamCandidate> {
            val qualities = listOf("2160p", "1080p", "720p", "480p")
            return List(STRESS_STREAMS_PER_PROVIDER) { index ->
                val quality = qualities[index % qualities.size]
                StreamCandidate(
                    providerId = stressProviderId(provider),
                    name = "Fixture ${provider + 1}\n$quality",
                    title = "Stress source ${index + 1} for $videoId",
                    description = "Synthetic $quality source used to exercise long source lists.",
                    url = "https://fixture.lamphaus.invalid/stream/$provider/$index.mp4",
                    videoSize = 700_000_000L + index * 150_000_000L,
                )
            }
        }

        /** Deterministic synthetic catalog rows for the stress fixture. */
        internal fun fixtureStressItems(provider: Int, count: Int, type: String = "movie"): List<MediaPreview> =
            List(count) { index -> fixtureStressItem(provider, index, type) }

        internal fun fixtureStressItem(provider: Int, index: Int, type: String = "movie"): MediaPreview {
            val isSeries = type == "series"
            return MediaPreview(
                id = "$STRESS_ID_PREFIX$type:$index",
                type = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
                rawType = type,
                name = "Stress fixture ${provider + 1} title ${index + 1}",
                description = "Synthetic row used to exercise large catalogs without a network.",
                releaseYear = 2020 + (index % 7),
                genres = listOf("Drama", "Science fiction"),
                contentRating = if (isSeries) "TV-14" else "PG-13",
                providerIds = setOf(stressProviderId(provider)),
            )
        }

        /** Six catalogs per provider so 20 stress providers add 120 Home rows. */
        val STRESS_MANIFEST: ProviderManifest by lazy {
            FIXTURE_MANIFEST.copy(
                catalogs = listOf("movie", "series").flatMap { type ->
                    STRESS_CATALOG_IDS.map { catalogId ->
                        ProviderCatalog(
                            type = type,
                            id = catalogId,
                            name = catalogId.replaceFirstChar(Char::uppercase),
                            extras = setOf("search", "skip"),
                            extraWireNames = mapOf("search" to "search", "skip" to "skip"),
                        )
                    }
                },
            )
        }

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
