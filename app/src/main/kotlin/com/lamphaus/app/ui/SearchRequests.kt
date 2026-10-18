package com.lamphaus.app.ui

import com.lamphaus.core.model.ProviderCatalog
import com.lamphaus.core.model.ProviderSubscription

/**
 * Search starts at two characters, as in Nuvio: one letter matches nearly
 * everything and costs a request to every add-on (SHR-PROD-19).
 */
internal const val MIN_SEARCH_QUERY_LENGTH = 2

/** What search runs for [input], or null while it is too short to search. */
internal fun searchQueryOrNull(input: String): String? =
    input.trim().takeIf { it.length >= MIN_SEARCH_QUERY_LENGTH }

/**
 * Whether search can ask this catalog: it takes a search, and whatever else
 * it requires has a default. Any other catalog would answer with an error or
 * nothing on every keystroke, so search leaves it out.
 */
internal fun ProviderCatalog.isSearchable(): Boolean =
    supportsExtra("search") &&
        requiredExtras.all { required ->
            required.canonicalExtraName() == "search" ||
                extraDefaults.keys.any { it.canonicalExtraName() == required.canonicalExtraName() }
        }

/**
 * Everything that changes what a search returns: the query, the profile and
 * its filters ([profileKey]), and the add-ons asked, in order. The same key
 * means the results already shown, or on their way, are still right.
 */
internal fun searchRequestKey(
    query: String,
    profileKey: String?,
    providers: List<ProviderSubscription>,
): String = buildString {
    append(query).append('|').append(profileKey)
    providers.forEach { append('|').append(it.id).append('@').append(it.manifestUrl) }
}
