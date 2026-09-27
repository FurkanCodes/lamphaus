package com.lamphaus.app.ui

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.ProviderCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class TypedHomeTest {
    private fun section(id: String, type: String) = CatalogSection(
        id = id,
        providerId = "provider",
        title = id,
        providerName = "Provider",
        items = emptyList(),
        baseQuery = CatalogQuery(type = type, catalogId = id),
    )

    private fun target(id: String, type: String, genres: List<String>, unavailable: String? = null) =
        CatalogBrowseTarget(
            providerId = "provider",
            providerName = "Provider",
            manifestUrl = "https://example.test/manifest.json",
            catalog = ProviderCatalog(
                type = type,
                id = id,
                name = id,
                extras = setOf("genre"),
                extraOptions = mapOf("genre" to genres),
            ),
            unavailableReason = unavailable,
        )

    private val sections = listOf(
        section("popular-movies", "movie"),
        section("popular-series", "series"),
        section("anime", "anime"),
        section("top-movies", "Movie"),
    )

    @Test
    fun `TV-CNT-01 Home keeps every catalog`() {
        assertEquals(sections, homeSectionsOfType(sections, catalogType = null))
    }

    @Test
    fun `TV-CNT-01 Movies and Series keep only their catalog type`() {
        assertEquals(
            listOf("popular-movies", "top-movies"),
            homeSectionsOfType(sections, "movie").map { it.id },
        )
        assertEquals(listOf("popular-series"), homeSectionsOfType(sections, "series").map { it.id })
    }

    @Test
    fun `genre strip merges genres of available catalogs of one type in order`() {
        val targets = listOf(
            target("a", "movie", listOf("Action", "Drama")),
            target("b", "series", listOf("Crime")),
            target("c", "movie", listOf("Drama", "Comedy")),
            target("d", "movie", listOf("Horror"), unavailable = "Offline"),
        )

        assertEquals(listOf("Action", "Drama", "Comedy"), browseGenresOfType(targets, "movie"))
        assertEquals(listOf("Crime"), browseGenresOfType(targets, "series"))
    }
}
