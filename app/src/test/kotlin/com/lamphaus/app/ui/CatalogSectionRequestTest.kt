package com.lamphaus.app.ui

import com.lamphaus.core.model.ProviderCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogSectionRequestTest {
    @Test
    fun `installed provider catalog produces one unmodified home request`() {
        val catalog = genreCatalog("movie")

        val requests = catalog.homeRequests(includeCuratedGenres = false, currentYear = 2026)

        assertEquals(1, requests.size)
        assertEquals("Popular Movies", requests.single().title)
        assertEquals(null, requests.single().query.genre)
    }

    @Test
    fun `bundled catalog adds only curated supported genres`() {
        val catalog = genreCatalog("series")

        val requests = catalog.homeRequests(includeCuratedGenres = true, currentYear = 2026)

        assertEquals(listOf("Popular Series", "Drama Series", "Comedy Series"), requests.map { it.title })
        assertEquals(listOf(null, "Drama", "Comedy"), requests.map { it.query.genre })
    }

    @Test
    fun `required year catalog uses the current year`() {
        val catalog = ProviderCatalog(
            type = "movie",
            id = "year",
            name = "New",
            extras = setOf("genre", "skip"),
            requiredExtras = setOf("genre"),
        )

        val request = catalog.homeRequests(includeCuratedGenres = true, currentYear = 2026).single()

        assertEquals("New Movies", request.title)
        assertEquals("2026", request.query.genre)
    }

    private fun genreCatalog(type: String) = ProviderCatalog(
        type = type,
        id = "top",
        name = "Popular",
        extras = setOf("genre", "search", "skip"),
        extraOptions = mapOf("genre" to listOf("Action", "Drama", "Comedy")),
    )
}
