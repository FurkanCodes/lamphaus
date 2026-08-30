package com.lamphaus.app.ui

import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
