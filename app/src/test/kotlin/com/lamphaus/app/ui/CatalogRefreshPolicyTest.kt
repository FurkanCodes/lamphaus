package com.lamphaus.app.ui

import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull

import org.junit.Test

class CatalogRefreshPolicyTest {
    @Test
    fun `equivalent fingerprints coalesce while profile and provider changes invalidate`() {
        val gate = CatalogRefreshGate()
        val fingerprint = fingerprint()

        assertTrue(gate.shouldStart(fingerprint, force = false))
        assertFalse(gate.shouldStart(fingerprint, force = false))
        assertTrue(gate.shouldStart(fingerprint.copy(childFilterEnabled = true), force = false))
        assertTrue(
            gate.shouldStart(
                fingerprint.copy(providers = listOf(fingerprint.providers.single().copy(displayName = "Changed"))),
                force = false,
            ),
        )
    }

    @Test
    fun `forced refresh bypasses equivalent fingerprint`() {
        val gate = CatalogRefreshGate()
        val fingerprint = fingerprint()

        assertTrue(gate.shouldStart(fingerprint, force = false))
        assertTrue(gate.shouldStart(fingerprint, force = true))
        assertFalse(gate.shouldStart(fingerprint, force = false))
    }

    @Test
    fun `failed catalog row retains only matching previous content`() {
        val previous = listOf(
            section("a:movie:featured", "a", items = listOf(media("old-a"))),
            section("b:movie:featured", "b", items = listOf(media("old-b"))),
        )
        val refreshed = listOf(
            section("a:movie:featured", "a", error = "A failed"),
            section("b:movie:featured", "b", items = listOf(media("new-b"))),
        )

        val merged = mergeCatalogRefresh(previous, refreshed)

        assertEquals(listOf("old-a", "new-b"), merged.flatMap { it.items }.map(MediaPreview::id))
        assertEquals("A failed", merged.first().errorMessage)
    }

    @Test
    fun `manifest failure retains all previous rows for its provider only`() {
        val previous = listOf(
            section("a:one", "a", items = listOf(media("a-one"))),
            section("a:two", "a", items = listOf(media("a-two"))),
            section("b:one", "b", items = listOf(media("b-one"))),
        )

        val merged = mergeCatalogRefresh(previous, listOf(section("a:error", "a", error = "Manifest failed")))

        assertEquals(listOf("a-one", "a-two"), merged.flatMap { it.items }.map(MediaPreview::id))
        assertTrue(merged.all { it.providerId == "a" && it.errorMessage == "Manifest failed" })
    }

    @Test
    fun `valid empty success replaces stale data`() {
        val previous = listOf(section("a:movie:featured", "a", items = listOf(media("old"))))
        val refreshed = listOf(section("a:movie:featured", "a"))

        val merged = mergeCatalogRefresh(previous, refreshed)

        assertEquals(emptyList<MediaPreview>(), merged.single().items)
        assertEquals(null, merged.single().errorMessage)
    }

    @Test
    fun `first load failure remains an error-only section`() {
        val refreshed = listOf(section("a:movie:featured", "a", error = "Unavailable"))

        val merged = mergeCatalogRefresh(emptyList(), refreshed)

        assertEquals(1, merged.size)
        assertTrue(merged.single().items.isEmpty())
        assertEquals("Unavailable", merged.single().errorMessage)
    }

    @Test
    fun `unavailable catalog sections retain refreshed provider order`() {
        val refreshed = listOf(
            section("provider:movie:unavailable", "provider", error = "Missing required extras: region"),
            section("provider:series:featured", "provider", items = listOf(media("series"))),
        )

        val merged = mergeCatalogRefresh(emptyList(), refreshed)

        assertEquals(
            listOf("provider:movie:unavailable", "provider:series:featured"),
            merged.map(CatalogSection::id),
        )
        assertEquals("Missing required extras: region", merged.first().errorMessage)
    }

    @Test
    fun `first page learns smaller provider page size`() {
        val initial = section("paged", "a").copy(supportsSkip = true, skipStep = 100)

        val page = firstCatalogPage(initial, listOf(media("one"), media("two")))

        assertEquals(2, page.skipStep)
        assertEquals(2, page.nextSkip)
        assertTrue(page.hasMore)
    }

    @Test
    fun `next page appends unseen items and advances learned offset`() {
        val initial = firstCatalogPage(
            section("paged", "a").copy(supportsSkip = true, skipStep = 100),
            listOf(media("one"), media("two")),
        )

        val page = mergeCatalogPage(initial, listOf(media("two"), media("three")))

        assertEquals(listOf("one", "two", "three"), page.items.map(MediaPreview::id))
        assertEquals(4, page.nextSkip)
        assertTrue(page.hasMore)
    }

    @Test
    fun `empty or duplicate page terminates without losing content`() {
        val initial = firstCatalogPage(
            section("paged", "a").copy(supportsSkip = true, skipStep = 100),
            listOf(media("one")),
        )

        val page = mergeCatalogPage(initial, listOf(media("one")))

        assertEquals(listOf("one"), page.items.map(MediaPreview::id))
        assertFalse(page.hasMore)
    }

    @Test
    fun `page failure retains content and retry clears the error`() {
        val initial = firstCatalogPage(
            section("paged", "a").copy(supportsSkip = true, skipStep = 100),
            listOf(media("one")),
        )

        val failed = mergeCatalogPage(initial, null, errorMessage = "offline")
        val retried = mergeCatalogPage(failed, listOf(media("two")))

        assertEquals(listOf("one"), failed.items.map(MediaPreview::id))
        assertEquals("offline", failed.loadMoreError)
        assertEquals(listOf("one", "two"), retried.items.map(MediaPreview::id))
        assertEquals(null, retried.loadMoreError)
    }

    @Test
    fun `home catalog batches cover 980 sections in ordered 50-row slices`() {
        val bounds = buildList {
            var loaded = 0
            while (true) {
                val next = nextHomeCatalogBatch(980, loaded) ?: break
                add(next)
                loaded = next.toIndexExclusive
            }
        }

        assertEquals(20, bounds.size)
        assertEquals(HomeCatalogBatchBounds(0, 50), bounds.first())
        assertEquals(HomeCatalogBatchBounds(50, 100), bounds[1])
        assertEquals(HomeCatalogBatchBounds(950, 980), bounds.last())
        assertNull(nextHomeCatalogBatch(980, 980))
        assertEquals(HomeCatalogBatchBounds(0, 50), nextHomeCatalogBatch(80, -10))
        assertEquals(HomeCatalogBatchBounds(79, 80), nextHomeCatalogBatch(80, 79))
    }

    @Test
    fun `home catalog batches append in order and suppress duplicate IDs`() {
        val existing = listOf(section("one", "provider"), section("two", "provider"))
        val incoming = listOf(
            section("two", "provider"),
            section("three", "provider"),
            section("four", "provider"),
            section("three", "provider"),
        )

        val merged = appendHomeCatalogBatch(existing, incoming)

        assertEquals(listOf("one", "two", "three", "four"), merged.map(CatalogSection::id))
    }

    @Test
    fun `home catalog prefetch starts only near the end when idle`() {
        assertFalse(shouldPrefetchHomeCatalogBatch(3, 10, hasMore = true, loading = false, failed = false))
        assertTrue(shouldPrefetchHomeCatalogBatch(4, 10, hasMore = true, loading = false, failed = false))
        assertTrue(shouldPrefetchHomeCatalogBatch(9, 10, hasMore = true, loading = false, failed = false))
        assertFalse(shouldPrefetchHomeCatalogBatch(9, 10, hasMore = true, loading = true, failed = false))
        assertFalse(shouldPrefetchHomeCatalogBatch(9, 10, hasMore = true, loading = false, failed = true))
        assertFalse(shouldPrefetchHomeCatalogBatch(9, 10, hasMore = false, loading = false, failed = false))
    }
    @Test
    fun `TV renders only non-terminal or non-empty home sections`() {
        listOf(
            section("populated", "provider", items = listOf(media("item"))) to true,
            section("paged", "provider").copy(hasMore = true) to true,
            section("provider-error", "provider", error = "Manifest failed") to true,
            section("page-error", "provider").copy(loadMoreError = "Page failed") to true,
            section("empty-terminal", "provider") to false,
        ).forEach { (catalogSection, expected) ->
            assertEquals(expected, catalogSection.isRenderableHomeCatalogSection())
        }
    }


    private fun fingerprint() = CatalogRefreshFingerprint(
        userId = "user",
        childFilterEnabled = false,
        providers = listOf(
            CatalogProviderFingerprint("a", "https://a.example/manifest.json", "A", true, 0),
        ),
    )

    private fun section(id: String, providerId: String, items: List<MediaPreview> = emptyList(), error: String? = null) =
        CatalogSection(
            id = id,
            providerId = providerId,
            title = id,
            providerName = providerId,
            items = items,
            errorMessage = error,
        )

    private fun media(id: String) = MediaPreview(
        id = id,
        type = MediaType.MOVIE,
        rawType = "movie",
        name = id,
    )
}
