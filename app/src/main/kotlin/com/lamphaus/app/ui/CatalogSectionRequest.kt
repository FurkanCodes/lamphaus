package com.lamphaus.app.ui

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.ProviderCatalog

internal sealed interface CatalogHomePlan {
    data class Request(
        val title: String,
        val query: CatalogQuery,
    ) : CatalogHomePlan

    data class Unavailable(
        val title: String,
        val reason: String,
    ) : CatalogHomePlan
}

/**
 * Builds the first page for every provider catalog. The bundled catalog also gets a compact,
 * curated set of genre shelves so Home is useful before the user installs anything else.
 */
internal fun ProviderCatalog.homePlans(
    includeCuratedGenres: Boolean,
    currentYear: Int,
): List<CatalogHomePlan> {
    if (!showInHome || requiredExtras == setOf("search")) return emptyList()

    val unresolvedRequiredExtras = requiredExtras - extraDefaults.keys
    val yearGenreCatalog = includeCuratedGenres &&
        id == "year" &&
        requiredExtras == setOf("genre")
    val baseQuery = when {
        yearGenreCatalog -> CatalogQuery(
            type = type,
            catalogId = id,
            genre = currentYear.toString(),
            extras = extraDefaults,
            posterShape = posterShape,
        )
        unresolvedRequiredExtras.isEmpty() -> CatalogQuery(
            type = type,
            catalogId = id,
            extras = extraDefaults,
            posterShape = posterShape,
        )
        else -> return listOf(
            CatalogHomePlan.Unavailable(
                title = displayTitle(),
                reason = "Missing required extras: ${unresolvedRequiredExtras.sorted().joinToString(", ")}",
            ),
        )
    }

    val plans = mutableListOf<CatalogHomePlan>(CatalogHomePlan.Request(displayTitle(), baseQuery))
    if (!includeCuratedGenres || id != "top" || "genre" !in extras) return plans

    val supportedGenres = extraOptions["genre"].orEmpty().toSet()
    curatedGenres(type)
        .filter { supportedGenres.isEmpty() || it in supportedGenres }
        .forEach { genre ->
            plans += CatalogHomePlan.Request(
                title = "$genre ${typeLabel()}",
                query = baseQuery.copy(genre = genre),
            )
        }
    return plans
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
