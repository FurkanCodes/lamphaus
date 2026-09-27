package com.lamphaus.app.ui

import com.lamphaus.core.model.CatalogQuery
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
}
