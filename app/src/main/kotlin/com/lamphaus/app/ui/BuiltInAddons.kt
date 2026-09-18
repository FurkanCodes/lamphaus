package com.lamphaus.app.ui

import com.lamphaus.core.model.ProviderSubscription

internal const val CINEMETA_PROVIDER_ID = "com.linvo.cinemeta"
internal const val CINEMETA_MANIFEST_URL = "https://v3-cinemeta.strem.io/manifest.json"

internal const val OPEN_SUBTITLES_PROVIDER_ID = "org.stremio.opensubtitlesv3"
internal const val OPEN_SUBTITLES_MANIFEST_URL = "https://opensubtitles-v3.strem.io/manifest.json"

internal data class BuiltInAddon(
    val id: String,
    val manifestUrl: String,
    val displayName: String,
    val sortOrder: Int,
)

internal val BUILT_IN_ADDONS = listOf(
    BuiltInAddon(
        id = CINEMETA_PROVIDER_ID,
        manifestUrl = CINEMETA_MANIFEST_URL,
        displayName = "Cinemeta",
        sortOrder = -100,
    ),
    BuiltInAddon(
        id = OPEN_SUBTITLES_PROVIDER_ID,
        manifestUrl = OPEN_SUBTITLES_MANIFEST_URL,
        displayName = "OpenSubtitles v3",
        sortOrder = -90,
    ),
)

internal fun builtInAddonFor(id: String, manifestUrl: String): BuiltInAddon? =
    BUILT_IN_ADDONS.firstOrNull { addon ->
        addon.id == id || addon.manifestUrl == manifestUrl
    }

internal fun BuiltInAddon.subscription(now: Long): ProviderSubscription = ProviderSubscription(
    id = id,
    manifestUrl = manifestUrl,
    displayName = displayName,
    enabled = true,
    sortOrder = sortOrder,
    updatedAtEpochMillis = now,
)

/**
 * OpenSubtitles follows Stremio's IMDb episode identity (`tt…:season:episode`).
 * Other add-ons keep the video id supplied by their own metadata provider.
 */
internal fun ProviderSubscription.subtitleVideoId(
    imdbId: String,
    season: Int?,
    episode: Int?,
    fallbackVideoId: String,
): String {
    if (id != OPEN_SUBTITLES_PROVIDER_ID || !imdbId.matches(IMDB_ID)) return fallbackVideoId
    return if (season != null && episode != null) "$imdbId:$season:$episode" else imdbId
}

private val IMDB_ID = Regex("tt\\d+", RegexOption.IGNORE_CASE)
