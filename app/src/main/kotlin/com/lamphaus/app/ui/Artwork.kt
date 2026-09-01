package com.lamphaus.app.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.toBitmap
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.lamphaus.app.R
import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkOverride
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.MediaPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    path?.trim()?.takeIf { it.isNotBlank() && it.startsWith("/") }?.let {
        "$TMDB_IMAGE_BASE_URL/$size/${it.trimStart('/')}"
    }

internal fun artworkImageUrl(asset: ArtworkAsset?, tmdbSize: String): String? {
    val value = asset?.reference?.trim()?.takeIf(String::isNotBlank) ?: return null
    if (asset.provider == ArtworkProviderId.TMDB && value.startsWith("/")) {
        return tmdbImageUrl(value, tmdbSize)
    }
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
    return value.takeIf {
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }
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

private const val AMBIENT_SAMPLE_SIZE = 96
private val ambientAccentCache = LruCache<String, Color>(32)

/**
 * Soft artwork-derived tint used to bleed artwork hues into dark surfaces.
 * Samples the same source [MediaArtwork] prefers (backdrop first) at a tiny
 * size and derives Material content-based color roles; null when there is no
 * artwork or extraction fails. Results are cached per artwork key.
 */
@Composable
internal fun rememberArtworkAmbient(media: MediaPreview): Color? {
    val context = LocalContext.current
    val resolver = LocalArtworkResolver.current
    val source = remember(media, resolver) {
        val resolution = resolver.resolve(media)
        val url = resolution.media.backgroundUrl?.takeIf(String::isNotBlank)
            ?: resolution.media.posterUrl?.takeIf(String::isNotBlank)
        when {
            url != null -> "url:$url" to url
            !resolution.hasOverride -> fixtureArtworkResource(media)?.let { id ->
                "resource:${context.packageName}:$id" to id
            }
            else -> null
        }
    } ?: return null
    var accent by remember(source) { mutableStateOf(ambientAccentCache.get(source.first)) }
    LaunchedEffect(source.first) {
        if (accent != null) return@LaunchedEffect
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val result = SingletonImageLoader.get(context).execute(
                    ImageRequest.Builder(context)
                        .data(source.second)
                        .size(AMBIENT_SAMPLE_SIZE, AMBIENT_SAMPLE_SIZE)
                        .allowHardware(false)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .build(),
                )
                if (result !is SuccessResult) return@runCatching null
                val bitmap = result.image.toBitmap(AMBIENT_SAMPLE_SIZE, AMBIENT_SAMPLE_SIZE)
                val seed = DynamicColorsOptions.Builder()
                    .setContentBasedSource(bitmap)
                    .build()
                    .contentBasedSeedColor
                    ?: return@runCatching null
                Color(MaterialColors.getColorRoles(seed, false).accentContainer)
            }.getOrNull()
        }
        if (extracted != null) {
            ambientAccentCache.put(source.first, extracted)
            accent = extracted
        }
    }
    return accent
}
