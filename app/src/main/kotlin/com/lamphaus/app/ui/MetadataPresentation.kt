package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import java.util.Locale

data class MediaMetadataPresentation(
    val year: Int?,
    val runtimeMinutes: Int?,
    val contentRating: String?,
    val ratingText: String?,
    val genres: List<String>,
)

internal fun MediaPreview.metadataPresentation(
    runtimeMinutes: Int? = null,
    maxGenres: Int = 2,
): MediaMetadataPresentation = MediaMetadataPresentation(
    year = releaseYear,
    runtimeMinutes = runtimeMinutes,
    contentRating = contentRating.cleanMetadataValue(),
    ratingText = rating.metadataRatingText(),
    genres = genres.cleanMetadataValues(maxGenres),
)

internal fun MediaDetail.metadataPresentation(maxGenres: Int = 2): MediaMetadataPresentation =
    preview.metadataPresentation(runtimeMinutes = runtimeMinutes, maxGenres = maxGenres)

internal fun Episode.numberParts(): EpisodeNumberParts = EpisodeNumberParts(season = season, episode = episode)

data class EpisodeNumberParts(
    val season: Int?,
    val episode: Int?,
) {
    val isPresent: Boolean get() = season != null || episode != null
}

private fun String?.cleanMetadataValue(): String? =
    this?.trim()?.takeIf(String::isNotBlank)

private fun List<String>.cleanMetadataValues(maxItems: Int): List<String> =
    asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .take(maxItems.coerceAtLeast(0))
        .toList()

private fun Double?.metadataRatingText(): String? =
    this?.takeIf { it in 0.0..10.0 }?.let { String.format(Locale.ROOT, "%.1f", it) }
