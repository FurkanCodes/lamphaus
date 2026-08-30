package com.lamphaus.core.provider

import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAggregatorTest {
    private val aggregator = ProviderAggregator(UnusedProviderClient())

    @Test
    fun `resource types inherit manifest filters`() {
        val manifest = ProviderManifest(
            id = "fixture",
            name = "Fixture",
            version = "1",
            types = setOf("movie"),
            resources = listOf(ProviderResource("meta")),
        )

        assertTrue(aggregator.supports(manifest, "meta", "movie", "tt123"))
        assertFalse(aggregator.supports(manifest, "meta", "series", "tt123"))
    }

    @Test
    fun `empty resource filters and custom types remain supported`() {
        val manifest = ProviderManifest(
            id = "fixture",
            name = "Fixture",
            version = "1",
            types = setOf("movie"),
            resources = listOf(
                ProviderResource("catalog", types = emptySet()),
                ProviderResource("stream", types = emptySet(), idPrefixes = setOf("custom:")),
            ),
        )

        assertTrue(aggregator.supports(manifest, "catalog", "anime"))
        assertTrue(aggregator.supports(manifest, "stream", "anime", "custom:123"))
        assertFalse(aggregator.supports(manifest, "stream", "anime", "tt123"))
    }

    @Test
    fun `singular subtitle resource matches standard plural capability`() {
        val manifest = ProviderManifest(
            id = "fixture",
            name = "Fixture",
            version = "1",
            resources = listOf(ProviderResource("subtitle", types = setOf("movie"))),
        )

        assertTrue(aggregator.supports(manifest, "subtitles", "movie"))
        assertTrue(aggregator.supports(manifest, "subtitle", "movie"))
    }

    private class UnusedProviderClient : ProviderClient {
        override suspend fun manifest(manifestUrl: String) = error("Not used")
        override suspend fun discoverProviderUrls(catalogUrl: String) = error("Not used")
        override suspend fun catalog(
            manifestUrl: String,
            providerId: String,
            query: com.lamphaus.core.model.CatalogQuery,
        ) = error("Not used")
        override suspend fun meta(
            manifestUrl: String,
            providerId: String,
            type: String,
            id: String,
        ) = error("Not used")
        override suspend fun streams(
            manifestUrl: String,
            providerId: String,
            type: String,
            id: String,
        ) = error("Not used")
        override suspend fun subtitles(
            manifestUrl: String,
            type: String,
            id: String,
            extras: Map<String, String>,
        ) = error("Not used")
    }
}
