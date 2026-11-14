package com.lamphaus.app.ui

import androidx.core.text.HtmlCompat
import com.lamphaus.core.model.DetailEnrichment
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.PersonCredit

/** TMDB's job for a director; the enrichment's crew also carries writers. */
private const val TMDB_DIRECTOR = "Director"

/**
 * The people on a details page, in the one Cast row (TV and mobile): directors
 * first, labelled [directorRole], then the cast. TMDB enrichment brings
 * portraits and person pages; the add-on's own names keep the row useful
 * without it. A person can appear twice when they direct and act.
 */
internal fun detailCredits(
    detail: MediaDetail,
    enrichment: DetailEnrichment?,
    directorRole: String,
    plainName: (String) -> String = ::plainCreditName,
): List<PersonCredit> {
    val cast = enrichment?.cast?.takeIf { it.isNotEmpty() }
        ?: detail.cast.map { PersonCredit(name = plainName(it)) }
    val directors = enrichment?.crew.orEmpty()
        .filter { it.role == TMDB_DIRECTOR }
        .map { it.copy(role = directorRole) }
        .ifEmpty { detail.directors.map { PersonCredit(name = plainName(it), role = directorRole) } }
    return (directors + cast).filter { it.name.isNotBlank() }
}

internal fun plainCreditName(value: String): String =
    HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
