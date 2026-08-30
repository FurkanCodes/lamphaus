package com.lamphaus.app.ui

import com.lamphaus.core.model.ProviderCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogSectionRequestTest {
    @Test
    fun `installed provider catalog produces one unmodified home request`() {
        val catalog = genreCatalog("movie")

        val requests = catalog.homePlans(includeCuratedGenres = false, currentYear = 2026)
            .filterIsInstance<CatalogHomePlan.Request>()

        assertEquals(1, requests.size)
        assertEquals("Popular Movies", requests.single().title)
        assertEquals(null, requests.single().query.genre)
    }

    @Test
    fun `bundled catalog adds only curated supported genres`() {
        val catalog = genreCatalog("series")

        val requests = catalog.homePlans(includeCuratedGenres = true, currentYear = 2026)
            .filterIsInstance<CatalogHomePlan.Request>()

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

        val request = catalog.homePlans(includeCuratedGenres = true, currentYear = 2026)
            .filterIsInstance<CatalogHomePlan.Request>()
            .single()

        assertEquals("New Movies", request.title)
        assertEquals("2026", request.query.genre)
    }

    @Test
    fun `hidden catalog produces no home plan`() {
        val plans = genreCatalog("movie").copy(showInHome = false)

        assertEquals(emptyList<CatalogHomePlan>(), plans.homePlans(false, 2026))
    }

    @Test
    fun `required extra with default produces a request using that default`() {
        val catalog = ProviderCatalog(
            type = "movie",
            id = "provider",
            name = "Provider",
            requiredExtras = setOf("region"),
            extraDefaults = mapOf("region" to "US"),
        )

        val request = catalog.homePlans(false, 2026).single() as CatalogHomePlan.Request

        assertEquals(mapOf("region" to "US"), request.query.extras)
    }

    @Test
    fun `required extra without default produces an unavailable plan`() {
        val catalog = ProviderCatalog(
            type = "movie",
            id = "provider",
            name = "Provider",
            requiredExtras = setOf("region"),
        )

        val unavailable = catalog.homePlans(false, 2026).single() as CatalogHomePlan.Unavailable

        assertEquals("Provider Movies", unavailable.title)
        assertEquals("Missing required extras: region", unavailable.reason)
    }

    @Test
    fun `search-only catalog remains outside home`() {
        val catalog = ProviderCatalog(
            type = "movie",
            id = "search",
            name = "Search",
            requiredExtras = setOf("search"),
        )

        assertEquals(emptyList<CatalogHomePlan>(), catalog.homePlans(false, 2026))
    }

    @Test
    fun `custom catalog type gets a generic home shelf`() {
        val catalog = ProviderCatalog(type = "anime", id = "featured", name = "Anime")

        val request = catalog.homePlans(false, 2026).single() as CatalogHomePlan.Request

        assertEquals("Anime", request.title)
        assertEquals("anime", request.query.type)
        assertEquals("featured", request.query.catalogId)
    }

    @Test
    fun `custom wire spelling is preserved for search and genre extras`() {
        val catalog = ProviderCatalog(
            type = "movie",
            id = "filtered",
            name = "Filtered",
            extras = setOf("Search", "genre", "skip"),
            extraWireNames = mapOf("search" to "Search", "genre" to "genre"),
        )

        val request = catalog.request(search = "term", genre = "Drama", skip = 50)

        assertEquals(null, request.search)
        assertEquals("term", request.extras["Search"])
        assertEquals("Drama", request.genre)
        assertEquals(50, request.skip)
    }

    private fun genreCatalog(type: String) = ProviderCatalog(
        type = type,
        id = "top",
        name = "Popular",
        extras = setOf("genre", "search", "skip"),
        extraOptions = mapOf("genre" to listOf("Action", "Drama", "Comedy")),
    )
}
