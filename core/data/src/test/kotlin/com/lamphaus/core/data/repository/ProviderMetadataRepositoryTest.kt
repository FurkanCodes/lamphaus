package com.lamphaus.core.data.repository

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.ProviderFailureKind
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResource
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.SubtitleTrack
import com.lamphaus.core.provider.ProviderAggregator
import com.lamphaus.core.provider.ProviderClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SHR-ARC-04: background readers ask add-ons in the same order the app does. */
class ProviderMetadataRepositoryTest {
    private val series = MediaPreview(
        id = "tt1",
        type = MediaType.SERIES,
        rawType = "series",
        name = "Show",
        providerIds = setOf("own"),
    )

    private class FakeProviderClient(
        private val episodesByProvider: Map<String, List<Episode>>,
        private val metaTypes: Set<String> = setOf("series"),
    ) : ProviderClient {
        val metaCalls = mutableListOf<String>()

        override suspend fun manifest(manifestUrl: String): ProviderResult<ProviderManifest> =
            ProviderResult.Success(
                ProviderManifest(
                    id = manifestUrl,
                    name = manifestUrl,
                    version = "1",
                    types = metaTypes,
                    resources = listOf(ProviderResource("meta")),
                ),
            )

        override suspend fun meta(manifestUrl: String, providerId: String, type: String, id: String): ProviderResult<MediaDetail> {
            metaCalls += providerId
            val episodes = episodesByProvider[providerId]
                ?: return ProviderResult.Failure(ProviderFailureKind.HTTP, "missing")
            return ProviderResult.Success(
                MediaDetail(MediaPreview(id, MediaType.SERIES, type, "Show"), episodes = episodes),
            )
        }

        override suspend fun discoverProviderUrls(catalogUrl: String): ProviderResult<List<String>> = error("unused")
        override suspend fun catalog(manifestUrl: String, providerId: String, query: CatalogQuery): ProviderResult<List<MediaPreview>> =
            error("unused")
        override suspend fun streams(manifestUrl: String, providerId: String, type: String, id: String): ProviderResult<List<StreamCandidate>> =
            error("unused")
        override suspend fun subtitles(
            manifestUrl: String,
            type: String,
            id: String,
            extras: Map<String, String>,
        ): ProviderResult<List<SubtitleTrack>> = error("unused")
    }

    private fun repository(
        client: FakeProviderClient,
        providers: List<ProviderSubscription>,
        clock: () -> Long = { 0L },
    ) = ProviderMetadataRepository(client, ProviderAggregator(client), { providers }, clock, episodesTtlMillis = 1_000)

    private fun subscription(id: String, order: Int, enabled: Boolean = true) =
        ProviderSubscription(id = id, manifestUrl = "https://$id.test/manifest.json", displayName = id, enabled = enabled, sortOrder = order)

    @Test
    fun titleOwnAddonIsAskedFirstAndDisabledOnesNever() = runTest {
        val client = FakeProviderClient(mapOf("other" to listOf(Episode("e1", "One")), "own" to listOf(Episode("e2", "Two"))))
        val episodes = repository(
            client,
            listOf(subscription("off", 0, enabled = false), subscription("other", 1), subscription("own", 2)),
        ).getSeriesEpisodes(series)

        assertEquals(listOf("own"), client.metaCalls)
        assertEquals("e2", episodes.single().id)
    }

    @Test
    fun emptyAnswersFallThroughToTheNextAddon() = runTest {
        val client = FakeProviderClient(mapOf("own" to emptyList(), "other" to listOf(Episode("e1", "One"))))
        val episodes = repository(client, listOf(subscription("own", 0), subscription("other", 1))).getSeriesEpisodes(series)

        assertEquals(listOf("own", "other"), client.metaCalls)
        assertEquals("e1", episodes.single().id)
    }

    @Test
    fun episodesAreKeptUntilTheyExpire() = runTest {
        val client = FakeProviderClient(mapOf("own" to listOf(Episode("e1", "One"))))
        var now = 0L
        val repository = repository(client, listOf(subscription("own", 0)), clock = { now })

        repository.getSeriesEpisodes(series)
        now = 999
        repository.getSeriesEpisodes(series)
        assertEquals(1, client.metaCalls.size)

        now = 1_000
        repository.getSeriesEpisodes(series)
        assertEquals(2, client.metaCalls.size)
    }

    @Test
    fun rememberedEpisodesSkipTheNetwork() = runTest {
        val client = FakeProviderClient(emptyMap())
        val repository = repository(client, listOf(subscription("own", 0)))

        repository.rememberEpisodes(series, listOf(Episode("e9", "Nine")))

        assertEquals("e9", repository.getSeriesEpisodes(series).single().id)
        assertTrue(client.metaCalls.isEmpty())
    }

    @Test
    fun unsupportedTypesAreSkipped() = runTest {
        val client = FakeProviderClient(mapOf("own" to listOf(Episode("e1", "One"))), metaTypes = setOf("movie"))

        assertTrue(repository(client, listOf(subscription("own", 0))).getSeriesEpisodes(series).isEmpty())
        assertTrue(client.metaCalls.isEmpty())
    }
}
