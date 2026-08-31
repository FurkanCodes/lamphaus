package com.lamphaus.app.ui

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.ProviderCatalog
internal const val CINEMETA_PROVIDER_ID = "com.linvo.cinemeta"
internal const val CINEMETA_MANIFEST_URL = "https://v3-cinemeta.strem.io/manifest.json"

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

data class CatalogBrowseTarget(
    val providerId: String,
    val providerName: String,
    val manifestUrl: String,
    val catalog: ProviderCatalog,
    val unavailableReason: String? = null,
) {
    val id: String get() = "$providerId:${catalog.type}:${catalog.id}"
    val genres: List<String>
        get() = catalog.extraOptions[catalog.wireExtraName("genre") ?: "genre"].orEmpty()
}

internal fun ProviderCatalog.homePlans(
    includeCuratedGenres: Boolean,
    currentYear: Int,
): List<CatalogHomePlan> {
    if (!showInHome || requiredExtras.any { it.canonicalExtraName() == "search" }) return emptyList()

    val unresolvedRequiredExtras = requiredExtras.filterNot { required ->
        extraDefaults.keys.any { it.canonicalExtraName() == required.canonicalExtraName() }
    }
    val yearGenreCatalog = includeCuratedGenres &&
        id == "year" &&
        requiredExtras.any { it.canonicalExtraName() == "genre" } &&
        requiredExtras.size == 1
    if (unresolvedRequiredExtras.isNotEmpty() && !yearGenreCatalog) {
        return listOf(
            CatalogHomePlan.Unavailable(
                title = displayTitle(),
                reason = "Missing required extras: ${unresolvedRequiredExtras.sorted().joinToString(", ")}",
            ),
        )
    }

    val baseQuery = request(
        genre = if (yearGenreCatalog) currentYear.toString() else null,
    )
    val plans = mutableListOf(CatalogHomePlan.Request(displayTitle(), baseQuery))
    if (!includeCuratedGenres || id != "top" || !supportsExtra("genre")) return plans

    val supportedGenres = extraOptions[wireExtraName("genre") ?: "genre"].orEmpty().toSet()
    curatedGenres(type)
        .filter { supportedGenres.isEmpty() || it in supportedGenres }
        .forEach { genre ->
            plans += CatalogHomePlan.Request(
                title = "$genre ${typeLabel()}",
                query = request(genre = genre),
            )
        }
    return plans
}

internal fun ProviderCatalog.supportsExtra(name: String): Boolean = extras.any { it.canonicalExtraName() == name }

internal fun ProviderCatalog.wireExtraName(name: String): String? =
    extraWireNames[name.canonicalExtraName()]
        ?: extras.firstOrNull { it.canonicalExtraName() == name.canonicalExtraName() }

internal fun ProviderCatalog.supportsSkip(): Boolean = supportsExtra("skip")

internal fun ProviderCatalog.initialSkipStep(): Int = pageSize?.takeIf { it > 0 } ?: 100

internal fun ProviderCatalog.request(
    search: String? = null,
    genre: String? = null,
    skip: Int = 0,
): CatalogQuery {
    val mergedExtras = extraDefaults.toMutableMap()
    val searchKey = wireExtraName("search")
    val genreKey = wireExtraName("genre")
    if (searchKey != null && searchKey != "search" && !search.isNullOrBlank()) {
        mergedExtras[searchKey] = search
    }
    if (genreKey != null && genreKey != "genre" && !genre.isNullOrBlank()) {
        mergedExtras[genreKey] = genre
    }
    return CatalogQuery(
        type = type,
        catalogId = id,
        search = search?.takeIf { searchKey == "search" },
        genre = genre?.takeIf { genreKey == "genre" },
        skip = skip,
        extras = mergedExtras,
        posterShape = posterShape,
    )
}

internal fun canonicalCatalogRequestIdentity(providerId: String, query: CatalogQuery): String = buildString {
    append(providerId).append('|').append(query.type).append('|').append(query.catalogId)
    query.search?.let { append("|search=").append(it) }
    query.genre?.let { append("|genre=").append(it) }
    if (query.skip > 0) append("|skip=").append(query.skip)
    query.extras.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (key, value) ->
        append('|').append(key).append('=').append(value)
    }
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

internal fun String.canonicalExtraName(): String = trim().lowercase()
private fun curatedGenres(type: String): List<String> = when (type.lowercase()) {
    "movie" -> listOf("Action", "Comedy", "Horror", "Sci-Fi", "Animation", "Documentary")
    "series" -> listOf("Drama", "Crime", "Comedy", "Sci-Fi", "Reality-TV", "Animation")
    else -> emptyList()
}
