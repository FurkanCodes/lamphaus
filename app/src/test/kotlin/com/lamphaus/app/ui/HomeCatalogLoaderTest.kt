package com.lamphaus.app.ui

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.ProviderCatalog
import com.lamphaus.core.model.ProviderFailureKind
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.ProviderSubscription
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.SubtitleTrack
import com.lamphaus.core.provider.ProviderClient
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeCatalogLoaderTest {
    @Test
    fun `first window prepares four ordered placeholders and resolves completion immediately`() = runTest {
        val providers = providers(50)
        val gates = providers.associate { provider ->
            provider.id to CompletableDeferred<ProviderResult<List<MediaPreview>>>()
        }
        val client = FakeProviderClient(
            manifests = providers.associate { it.manifestUrl to manifest(it) },
            catalogHandler = { providerId, _, _ -> gates.getValue(providerId).await() },
        )
        val loader = HomeCatalogLoader(client, providers, currentYear = 2026)
        var prepared: HomeCatalogWindow? = null
        val resolved = mutableListOf<CatalogSection>()

        val loading = async {
            loader.loadNextWindow(
                childFilterEnabled = false,
                onPrepared = { prepared = it },
                onResolved = resolved::add,
            )
        }
        runCurrent()

        val firstWindow = assertNotNull(prepared).let { prepared!! }
        assertEquals(4, firstWindow.sections.size)
        assertEquals(4, firstWindow.consumedTargetCount)
        assertTrue(firstWindow.hasMore)
        assertTrue(firstWindow.sections.all(CatalogSection::initialLoading))
        assertEquals(
            listOf("provider-0", "provider-1", "provider-2", "provider-3"),
            firstWindow.sections.map(CatalogSection::providerId),
        )
        assertEquals(4, client.catalogCalls.size)
        assertFalse(client.catalogCalls.any { it.providerId == "provider-4" })

        gates.getValue("provider-3").complete(ProviderResult.Success(listOf(media("fourth"))))
        runCurrent()

        assertEquals(listOf("provider-3"), resolved.map(CatalogSection::providerId))
        assertEquals("home:provider-3|movie|catalog-3", resolved.single().id)
        assertFalse(resolved.single().initialLoading)
        assertEquals(
            listOf("provider-0", "provider-1", "provider-2", "provider-3"),
            firstWindow.sections.map(CatalogSection::providerId),
        )

        listOf("provider-0", "provider-1", "provider-2").forEach { providerId ->
            gates.getValue(providerId).complete(ProviderResult.Success(listOf(media(providerId))))
        }
        loading.await()
    }

    @Test
    fun `repeated windows visit fifty targets once within concurrency bounds`() = runTest {
        val providers = providers(50).reversed()
        val client = FakeProviderClient(
            manifests = providers.associate { it.manifestUrl to manifest(it) },
            catalogHandler = { providerId, _, _ -> ProviderResult.Success(listOf(media(providerId))) },
        )
        val loader = HomeCatalogLoader(client, providers, currentYear = 2026)
        val preparedWindows = mutableListOf<HomeCatalogWindow>()
        val resolved = mutableListOf<CatalogSection>()

        do {
            val window = loader.loadNextWindow(
                childFilterEnabled = false,
                onPrepared = preparedWindows::add,
                onResolved = resolved::add,
            )
        } while (window.hasMore)

        assertEquals(13, preparedWindows.size)
        assertEquals(2, preparedWindows.last().sections.size)
        assertEquals(50, preparedWindows.last().consumedTargetCount)
        assertFalse(preparedWindows.last().hasMore)
        assertEquals((0 until 50).map { "provider-$it" }, preparedWindows.flatMap { it.sections }.map(CatalogSection::providerId))
        assertEquals(50, client.catalogCalls.size)
        assertEquals(50, client.catalogCalls.map(CatalogCall::providerId).distinct().size)
        assertEquals(50, resolved.size)
        assertTrue(client.maximumActiveManifests in 1..HOME_CATALOG_MAX_CONCURRENCY)
        assertTrue(client.maximumActiveCatalogs in 1..HOME_CATALOG_MAX_CONCURRENCY)
    }

    @Test
    fun `duplicates and ready errors occupy ordered slots without duplicate IO`() = runTest {
        val duplicateCatalog = catalog(id = "duplicate")
        val unavailableCatalog = catalog(id = "locked").copy(requiredExtras = setOf("region"))
        val ordinaryFailureCatalog = catalog(id = "ordinary-failure")
        val providers = listOf(
            provider("duplicate", 0),
            provider("manifest-error", 1),
            provider("unavailable", 2),
            provider("catalog-error", 3),
        )
        val manifests = mapOf(
            providers[0].manifestUrl to ProviderResult.Success(providerManifest(providers[0], listOf(duplicateCatalog, duplicateCatalog))),
            providers[1].manifestUrl to ProviderResult.Failure(ProviderFailureKind.NETWORK, "Manifest failed"),
            providers[2].manifestUrl to ProviderResult.Success(providerManifest(providers[2], listOf(unavailableCatalog))),
            providers[3].manifestUrl to ProviderResult.Success(providerManifest(providers[3], listOf(ordinaryFailureCatalog))),
        )
        val client = FakeProviderClient(
            manifests = manifests,
            catalogHandler = { providerId, _, _ ->
                if (providerId == providers[3].id) {
                    ProviderResult.Failure(ProviderFailureKind.NETWORK, "Catalog failed")
                } else {
                    ProviderResult.Success(listOf(media(providerId)))
                }
            },
        )
        val loader = HomeCatalogLoader(client, providers, currentYear = 2026)
        var prepared: HomeCatalogWindow? = null
        val resolved = mutableListOf<CatalogSection>()

        val window = loader.loadNextWindow(
            childFilterEnabled = false,
            onPrepared = { prepared = it },
            onResolved = resolved::add,
        )

        assertEquals(
            listOf(
                "home:duplicate|movie|duplicate",
                "manifest-error:error",
                "unavailable:movie:locked:unavailable",
                "home:catalog-error|movie|ordinary-failure",
            ),
            prepared!!.sections.map(CatalogSection::id),
        )
        assertEquals(listOf(true, false, false, true), prepared!!.sections.map(CatalogSection::initialLoading))
        assertEquals(4, window.consumedTargetCount)
        assertFalse(window.hasMore)
        assertEquals(listOf("duplicate", "catalog-error"), client.catalogCalls.map(CatalogCall::providerId))
        assertEquals(2, resolved.size)
        assertEquals("Catalog failed", resolved.single { it.providerId == "catalog-error" }.errorMessage)
        assertFalse(resolved.any(CatalogSection::initialLoading))
    }

    @Test
    fun `unexpected failure retains only unresolved slot for retry`() = runTest {
        val providers = providers(3)
        val client = FakeProviderClient(
            manifests = providers.associate { it.manifestUrl to manifest(it) },
            catalogHandler = { providerId, _, attempt ->
                if (providerId == "provider-1" && attempt == 1) error("unexpected")
                ProviderResult.Success(listOf(media("$providerId-$attempt")))
            },
        )
        val loader = HomeCatalogLoader(client, providers, currentYear = 2026)
        var preparedCount = 0
        var preparedWindow: HomeCatalogWindow? = null
        val resolved = mutableListOf<CatalogSection>()
        var failure: Throwable? = null

        try {
            loader.loadNextWindow(
                childFilterEnabled = false,
                onPrepared = {
                    preparedCount++
                    preparedWindow = it
                },
                onResolved = resolved::add,
            )
        } catch (error: Throwable) {
            failure = error
        }

        assertEquals("unexpected", failure?.message)
        assertEquals(1, preparedCount)
        assertEquals(3, preparedWindow!!.consumedTargetCount)
        assertEquals(listOf("provider-0", "provider-2"), resolved.map(CatalogSection::providerId).sorted())
        assertEquals(1, client.catalogAttempts.getValue("provider-0"))
        assertEquals(1, client.catalogAttempts.getValue("provider-1"))
        assertEquals(1, client.catalogAttempts.getValue("provider-2"))

        val retried = loader.loadNextWindow(
            childFilterEnabled = false,
            onPrepared = { preparedCount++ },
            onResolved = resolved::add,
        )

        assertEquals(1, preparedCount)
        assertEquals(3, retried.consumedTargetCount)
        assertFalse(retried.hasMore)
        assertEquals(1, client.catalogAttempts.getValue("provider-0"))
        assertEquals(2, client.catalogAttempts.getValue("provider-1"))
        assertEquals(1, client.catalogAttempts.getValue("provider-2"))
        assertEquals(listOf("provider-0", "provider-1", "provider-2"), resolved.map(CatalogSection::providerId).sorted())
    }

    private fun providers(count: Int): List<ProviderSubscription> =
        (0 until count).map { index -> provider("provider-$index", index) }

    private fun provider(id: String, sortOrder: Int) = ProviderSubscription(
        id = id,
        manifestUrl = "https://$id.example/manifest.json",
        displayName = id,
        sortOrder = sortOrder,
    )

    private fun manifest(provider: ProviderSubscription): ProviderResult<ProviderManifest> {
        val index = provider.id.substringAfterLast('-').toIntOrNull()
        return ProviderResult.Success(
            providerManifest(provider, listOf(catalog(id = index?.let { "catalog-$it" } ?: "catalog"))),
        )
    }

    private fun providerManifest(
        provider: ProviderSubscription,
        catalogs: List<ProviderCatalog>,
    ) = ProviderManifest(
        id = provider.id,
        name = provider.displayName,
        version = "1.0.0",
        catalogs = catalogs,
    )

    private fun catalog(id: String) = ProviderCatalog(
        type = "movie",
        id = id,
        name = id,
    )

    private fun media(id: String) = MediaPreview(
        id = id,
        type = MediaType.MOVIE,
        rawType = "movie",
        name = id,
    )

    private data class CatalogCall(
        val providerId: String,
        val query: CatalogQuery,
    )

    private class FakeProviderClient(
        private val manifests: Map<String, ProviderResult<ProviderManifest>>,
        private val catalogHandler: suspend (providerId: String, query: CatalogQuery, attempt: Int) ->
            ProviderResult<List<MediaPreview>>,
    ) : ProviderClient {
        private val activeManifests = AtomicInteger(0)
        private val activeCatalogs = AtomicInteger(0)
        private val maximumManifestCounter = AtomicInteger(0)
        private val maximumCatalogCounter = AtomicInteger(0)
        val catalogCalls = mutableListOf<CatalogCall>()
        val catalogAttempts = mutableMapOf<String, Int>()

        val maximumActiveManifests: Int get() = maximumManifestCounter.get()
        val maximumActiveCatalogs: Int get() = maximumCatalogCounter.get()

        override suspend fun manifest(manifestUrl: String): ProviderResult<ProviderManifest> {
            val active = activeManifests.incrementAndGet()
            maximumManifestCounter.updateAndGet { maxOf(it, active) }
            return try {
                yield()
                manifests.getValue(manifestUrl)
            } finally {
                activeManifests.decrementAndGet()
            }
        }

        override suspend fun catalog(
            manifestUrl: String,
            providerId: String,
            query: CatalogQuery,
        ): ProviderResult<List<MediaPreview>> {
            catalogCalls += CatalogCall(providerId, query)
            val attempt = catalogAttempts.getOrDefault(providerId, 0) + 1
            catalogAttempts[providerId] = attempt
            val active = activeCatalogs.incrementAndGet()
            maximumCatalogCounter.updateAndGet { maxOf(it, active) }
            return try {
                yield()
                catalogHandler(providerId, query, attempt)
            } finally {
                activeCatalogs.decrementAndGet()
            }
        }

        override suspend fun discoverProviderUrls(catalogUrl: String): ProviderResult<List<String>> =
            error("Not used")

        override suspend fun meta(
            manifestUrl: String,
            providerId: String,
            type: String,
            id: String,
        ): ProviderResult<MediaDetail> = error("Not used")

        override suspend fun streams(
            manifestUrl: String,
            providerId: String,
            type: String,
            id: String,
        ): ProviderResult<List<StreamCandidate>> = error("Not used")

        override suspend fun subtitles(
            manifestUrl: String,
            type: String,
            id: String,
            extras: Map<String, String>,
        ): ProviderResult<List<SubtitleTrack>> = error("Not used")
    }
}
