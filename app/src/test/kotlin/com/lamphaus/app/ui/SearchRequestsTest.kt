package com.lamphaus.app.ui

import com.lamphaus.core.model.ProviderCatalog
import com.lamphaus.core.model.ProviderSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SHR-PROD-19: what search asks, and when it asks again. */
class SearchRequestsTest {
    @Test
    fun `search starts at two characters`() {
        assertNull(searchQueryOrNull(""))
        assertNull(searchQueryOrNull(" L "))
        assertEquals("Lo", searchQueryOrNull(" Lo "))
    }

    @Test
    fun `catalogs that take a search and need nothing else are searched`() {
        assertTrue(catalog(extras = setOf("search", "skip")).isSearchable())
        assertTrue(catalog(extras = setOf("Search"), required = setOf("search")).isSearchable())
        assertTrue(
            catalog(extras = setOf("search", "genre"), required = setOf("genre"), defaults = mapOf("genre" to "Drama"))
                .isSearchable(),
        )
    }

    @Test
    fun `catalogs without search or needing more than a search are left out`() {
        assertFalse(catalog(extras = setOf("genre")).isSearchable())
        assertFalse(catalog(extras = setOf("search", "genre"), required = setOf("genre")).isSearchable())
    }

    @Test
    fun `the same search for the same profile and add-ons has the same key`() {
        val providers = listOf(provider("a"), provider("b"))

        assertEquals(searchRequestKey("lost", "p1", providers), searchRequestKey("lost", "p1", providers))
    }

    @Test
    fun `query profile or add-ons changing asks again`() {
        val key = searchRequestKey("lost", "p1", listOf(provider("a")))

        assertNotEquals(key, searchRequestKey("lose", "p1", listOf(provider("a"))))
        assertNotEquals(key, searchRequestKey("lost", "p2", listOf(provider("a"))))
        assertNotEquals(key, searchRequestKey("lost", "p1", listOf(provider("a"), provider("b"))))
        assertNotEquals(key, searchRequestKey("lost", "p1", listOf(provider("a", url = "https://a.example/v2/manifest.json"))))
    }

    private fun catalog(
        extras: Set<String>,
        required: Set<String> = emptySet(),
        defaults: Map<String, String> = emptyMap(),
    ) = ProviderCatalog(
        type = "movie",
        id = "top",
        name = "Popular",
        extras = extras,
        requiredExtras = required,
        extraDefaults = defaults,
    )

    private fun provider(id: String, url: String = "https://$id.example/manifest.json") = ProviderSubscription(
        id = id,
        manifestUrl = url,
        displayName = id,
        sortOrder = 0,
    )
}
