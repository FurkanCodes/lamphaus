package com.lamphaus.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** PERF-06: incremental search results keep deterministic provider/catalog order. */
class SearchPublicationOrderTest {
    private fun section(id: String) = CatalogSection(
        id = id,
        providerId = id.substringBefore(':'),
        title = id,
        providerName = id,
        items = emptyList(),
    )

    @Test
    fun `slow provider does not delay publishing a fast one`() {
        val orderedIds = listOf("slow:one", "fast:two")

        val firstPublication = orderedResolvedSections(
            orderedIds,
            mapOf("fast:two" to section("fast:two")),
        )

        assertEquals(listOf("fast:two"), firstPublication.map(CatalogSection::id))
    }

    @Test
    fun `publication order stays deterministic when results arrive out of order`() {
        val orderedIds = listOf("a:one", "b:two", "c:three")

        val publication = orderedResolvedSections(
            orderedIds,
            mapOf(
                "c:three" to section("c:three"),
                "a:one" to section("a:one"),
                "b:two" to section("b:two"),
            ),
        )

        assertEquals(listOf("a:one", "b:two", "c:three"), publication.map(CatalogSection::id))
    }

    @Test
    fun `failed provider keeps its slot with a local error`() {
        val orderedIds = listOf("a:one", "b:two")

        val publication = orderedResolvedSections(
            orderedIds,
            mapOf(
                "a:one" to section("a:one"),
                "b:two" to section("b:two").copy(errorMessage = "Provider timed out."),
            ),
        )

        assertEquals(listOf("a:one", "b:two"), publication.map(CatalogSection::id))
        assertEquals("Provider timed out.", publication.last().errorMessage)
    }
}
