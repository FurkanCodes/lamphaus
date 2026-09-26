package com.lamphaus.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PERF-02/PERF-11: the stress fixture is deterministic and offline. */
class FixtureProviderClientStressTest {
    @Test
    fun `stress items are deterministic and unique per provider`() {
        val first = FixtureProviderClient.fixtureStressItems(provider = 3, count = 100)
        val second = FixtureProviderClient.fixtureStressItems(provider = 3, count = 100)
        val other = FixtureProviderClient.fixtureStressItems(provider = 4, count = 100)

        assertEquals(first, second)
        assertEquals(100, first.size)
        assertEquals(100, first.map { it.id }.distinct().size)
        assertTrue(first.first().id.startsWith(FixtureProviderClient.STRESS_ID_PREFIX))
        assertEquals(setOf(FixtureProviderClient.stressProviderId(3)), first.first().providerIds)
        // Catalog ids are shared across providers on purpose: the aggregator
        // dedupes the same title while keeping both provider memberships.
        assertEquals(first.first().id, other.first().id)
        assertNotEquals(first.first().providerIds, other.first().providerIds)
    }

    @Test
    fun `series stress rows keep the series shape`() {
        val item = FixtureProviderClient.fixtureStressItem(provider = 0, index = 5, type = "series")

        assertEquals("series", item.rawType)
        assertEquals("TV-14", item.contentRating)
    }

    @Test
    fun `stress provider identity round-trips`() {
        for (index in listOf(0, 1, 19)) {
            val id = FixtureProviderClient.stressProviderId(index)
            assertEquals(index, FixtureProviderClient.providerIndex(id))
            assertEquals(
                "${FixtureProviderClient.FIXTURE_MANIFEST_URL}?provider=$index",
                FixtureProviderClient.stressProviderManifestUrl(index),
            )
        }
    }

    @Test
    fun `stress fixture keeps one failing and one delayed provider`() = kotlinx.coroutines.runBlocking {
        val client = FixtureProviderClient(stress = true)
        val query = com.lamphaus.core.model.CatalogQuery("movie", "featured")

        val failing = client.catalog(
            FixtureProviderClient.stressProviderManifestUrl(18),
            FixtureProviderClient.stressProviderId(18),
            query,
        )
        val successful = client.catalog(
            FixtureProviderClient.stressProviderManifestUrl(0),
            FixtureProviderClient.stressProviderId(0),
            query,
        )

        assertTrue(failing is com.lamphaus.core.model.ProviderResult.Failure)
        assertTrue(successful is com.lamphaus.core.model.ProviderResult.Success)
        assertTrue((successful as com.lamphaus.core.model.ProviderResult.Success).value.size >= FixtureProviderClient.STRESS_ITEMS_PER_CATALOG)
    }

    @Test
    fun `stress manifest adds six home catalogs per provider`() = kotlinx.coroutines.runBlocking {
        val client = FixtureProviderClient(stress = true)
        val manifest = client.manifest(FixtureProviderClient.stressProviderManifestUrl(0))

        val catalogs = (manifest as com.lamphaus.core.model.ProviderResult.Success).value.catalogs
        assertEquals(6, catalogs.size)
        assertEquals(6, catalogs.map { "${it.type}:${it.id}" }.distinct().size)
    }

    @Test
    fun `stress catalogs have bounded deterministic latency and some empty rows`() {
        val latencies = (0 until FixtureProviderClient.STRESS_PROVIDER_COUNT).flatMap { provider ->
            listOf("featured", "trending", "fresh").map { FixtureProviderClient.stressLatencyMillis(provider, it) }
        }
        assertTrue(latencies.all { it in 150L..1_200L })
        assertEquals(latencies, (0 until FixtureProviderClient.STRESS_PROVIDER_COUNT).flatMap { provider ->
            listOf("featured", "trending", "fresh").map { FixtureProviderClient.stressLatencyMillis(provider, it) }
        })
        assertTrue(FixtureProviderClient.stressCatalogIsEmpty(1, "fresh"))
        assertTrue(!FixtureProviderClient.stressCatalogIsEmpty(1, "featured"))
    }

    @Test
    fun `stress streams are unique and belong to their provider`() {
        val streams = FixtureProviderClient.fixtureStressStreams(provider = 2, videoId = "fixture:stress:movie:1")

        assertEquals(FixtureProviderClient.STRESS_STREAMS_PER_PROVIDER, streams.size)
        assertEquals(streams.size, streams.map { it.url }.distinct().size)
        assertTrue(streams.all { it.providerId == FixtureProviderClient.stressProviderId(2) })
        assertTrue(streams.all { it.url!!.startsWith("https://") })
    }
}
