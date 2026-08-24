package com.lamphaus.app.ui

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.ProviderCatalog

internal data class CatalogSectionRequest(
    val title: String,
    val query: CatalogQuery,
)

/**
 * Builds the first page for every provider catalog. The bundled catalog also gets a compact,
 * curated set of genre shelves so Home is useful before the user installs anything else.
 */
internal fun ProviderCatalog.homeRequests(
    includeCuratedGenres: Boolean,
    currentYear: Int,
): List<CatalogSectionRequest> {
    val baseQuery = when {
        requiredExtras.isEmpty() -> CatalogQuery(type, id, posterShape = posterShape)
        id == "year" && requiredExtras == setOf("genre") -> CatalogQuery(
            type = type,
            catalogId = id,
            genre = currentYear.toString(),
            posterShape = posterShape,
        )
        else -> null
    } ?: return emptyList()

    val requests = mutableListOf(CatalogSectionRequest(displayTitle(), baseQuery))
    if (!includeCuratedGenres || id != "top" || "genre" !in extras) return requests

    val supportedGenres = extraOptions["genre"].orEmpty().toSet()
    curatedGenres(type)
        .filter { supportedGenres.isEmpty() || it in supportedGenres }
        .forEach { genre ->
            requests += CatalogSectionRequest(
                title = "$genre ${typeLabel()}",
                query = baseQuery.copy(genre = genre),
            )
        }
    return requests
}

private fun ProviderCatalog.displayTitle(): String = when (type.lowercase()) {
    "movie" -> "$name Movies"
    "series" -> "$name Series"
    else -> name
}

private fun ProviderCatalog.typeLabel(): String = when (type.lowercase()) {
    "movie" -> "Movies"
    "series" -> "Series"
    else -> name
}

private fun curatedGenres(type: String): List<String> = when (type.lowercase()) {
    "movie" -> listOf("Action", "Comedy", "Horror", "Sci-Fi", "Animation", "Documentary")
    "series" -> listOf("Drama", "Crime", "Comedy", "Sci-Fi", "Reality-TV", "Animation")
    else -> emptyList()
}
