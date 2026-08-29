package com.lamphaus.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProvider
import com.lamphaus.core.model.MediaPreview

private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p"

internal data class ArtworkResolution(
    val media: MediaPreview,
    val hasOverride: Boolean,
)

internal class ArtworkResolver(
    private val overrides: Map<String, ArtworkOverride>,
) {
    fun resolve(media: MediaPreview): ArtworkResolution {
        val override = overrides[media.stableKey]
        val posterUrl = artworkImageUrl(override?.poster, "w500")
        val backdropUrl = artworkImageUrl(override?.backdrop, "original")
        val logoUrl = artworkImageUrl(override?.logo, "w500")
        return ArtworkResolution(
            media = media.copy(
                posterUrl = posterUrl ?: media.posterUrl,
                backgroundUrl = backdropUrl ?: media.backgroundUrl,
                logoUrl = logoUrl ?: media.logoUrl,
            ),
            hasOverride = posterUrl != null || backdropUrl != null || logoUrl != null,
        )
    }

    fun hasOverrideFor(media: MediaPreview, preferBackdrop: Boolean): Boolean {
        val override = overrides[media.stableKey] ?: return false
        val asset = if (preferBackdrop) override.backdrop else override.poster
        return artworkImageUrl(asset, if (preferBackdrop) "original" else "w500") != null
    }

    companion object {
        val Empty = ArtworkResolver(emptyMap())
    }
}

internal fun tmdbImageUrl(path: String?, size: String): String? =
    path?.trim()?.takeIf(String::isNotBlank)?.let { "$TMDB_IMAGE_BASE_URL/$size/${it.trimStart('/')}" }

internal fun artworkImageUrl(asset: ArtworkAsset?, tmdbSize: String): String? =
    when (asset?.provider) {
        ArtworkProvider.TMDB -> tmdbImageUrl(asset.reference, tmdbSize)
        ArtworkProvider.FANART -> asset.reference.takeIf { it.isNotBlank() && it.startsWith("https://") }
        null -> null
    }


internal val LocalArtworkResolver = staticCompositionLocalOf { ArtworkResolver.Empty }

internal fun fixtureArtworkResource(media: MediaPreview): Int? =
    when (media.id) {
        "fixture:aurora" -> R.drawable.poster_aurora
        "fixture:glass" -> R.drawable.poster_glass
        else -> null
    }

@Composable
fun MediaArtwork(
    media: MediaPreview,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    preferBackdrop: Boolean = false,
) {
    val resolver = LocalArtworkResolver.current
    val resolution = resolver.resolve(media)
    val resolvedMedia = resolution.media
    val local = if (resolver.hasOverrideFor(media, preferBackdrop)) {
        null
    } else {
        fixtureArtworkResource(media)
    }
    val remote = if (preferBackdrop) {
        resolvedMedia.backgroundUrl ?: resolvedMedia.posterUrl
    } else {
        resolvedMedia.posterUrl ?: resolvedMedia.backgroundUrl
    }
    when {
        local != null -> Image(
            painter = painterResource(local),
            contentDescription = resolvedMedia.name,
            modifier = modifier,
            contentScale = contentScale,
        )
        !remote.isNullOrBlank() -> AsyncImage(
            model = remote,
            contentDescription = resolvedMedia.name,
            modifier = modifier,
            contentScale = contentScale,
        )
        else -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_lamphaus_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.28f),
            )
        }
    }
}
