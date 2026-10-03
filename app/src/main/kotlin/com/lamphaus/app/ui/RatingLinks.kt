package com.lamphaus.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.RatingSourceScore
import java.net.URLEncoder

// Where a rating badge leads: the title's page on the rating source. Shared,
// non-visual logic for mobile and TV; each platform owns how it opens it.
//
// Only public identities travel in these links — an IMDb or TMDB id, or the
// title's name for a source's own search. Provider-scoped ids, add-on URLs,
// and stream locations never do (SHR-PROD-06).

private val imdbIdPattern = Regex("tt\\d+")

/**
 * The source's page for [media], or null when the source is unknown. Exact
 * pages where the id is known (IMDb, TMDB, Letterboxd, Trakt); otherwise the
 * source's own search for the title, so a badge never leads somewhere wrong.
 */
fun ratingDetailsUrl(score: RatingSourceScore, media: MediaPreview): String? {
    score.detailsUrl?.takeIf { it.startsWith("https://") }?.let { return it }
    val imdbId = media.id.substringBefore(':').takeIf(imdbIdPattern::matches)
    val tmdbId = media.id.takeIf { it.startsWith("tmdb:") }
        ?.substringAfterLast(':')
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    val isSeries = media.type == MediaType.SERIES
    val query = media.name.trim().takeIf(String::isNotEmpty)?.let { URLEncoder.encode(it, "UTF-8") }
    val pathQuery = query?.replace("+", "%20")
    return when (score.sourceId) {
        "imdb" -> when {
            imdbId != null -> "https://www.imdb.com/title/$imdbId/"
            query != null -> "https://www.imdb.com/find/?q=$query"
            else -> null
        }
        "tmdb" -> when {
            tmdbId != null -> "https://www.themoviedb.org/${if (isSeries) "tv" else "movie"}/$tmdbId"
            query != null -> "https://www.themoviedb.org/search?query=$query"
            else -> null
        }
        "letterboxd" -> when {
            imdbId != null && !isSeries -> "https://letterboxd.com/imdb/$imdbId/"
            tmdbId != null && !isSeries -> "https://letterboxd.com/tmdb/$tmdbId/"
            pathQuery != null -> "https://letterboxd.com/search/films/$pathQuery/"
            else -> null
        }
        "trakt" -> when {
            imdbId != null -> "https://trakt.tv/search/imdb/$imdbId"
            tmdbId != null -> "https://trakt.tv/search/tmdb/$tmdbId?id_type=${if (isSeries) "show" else "movie"}"
            query != null -> "https://trakt.tv/search?query=$query"
            else -> null
        }
        "tomatoes", "popcorn" -> query?.let { "https://www.rottentomatoes.com/search?search=$it" }
        "metacritic", "metacriticuser" -> pathQuery?.let { "https://www.metacritic.com/search/$it/" }
        "myanimelist" -> query?.let { "https://myanimelist.net/search/all?q=$it" }
        "rogerebert" -> query?.let { "https://www.rogerebert.com/search?q=$it" }
        else -> null
    }
}

/** Opens [url] in the device's browser; false when no app can show it. */
fun openRatingPage(context: Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
