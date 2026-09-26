package com.lamphaus.app.tv

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.lamphaus.app.ui.rememberReducedMotion
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.Image
import coil3.SingletonImageLoader
import coil3.compose.ImagePainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Size
import coil3.toBitmap
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.lamphaus.app.ui.ArtworkResolution
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.app.ui.fixtureArtworkResource
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// QA-08: the ambient image is drawn at TvAmbientTokens.imageAlpha under
// near-opaque scrims, so a quarter-resolution decode is visually identical to
// a full 1920x1080 one while using a quarter of the memory (about 2 MB instead
// of 8 MB per focus change) and leaving room in the image cache for posters.
private const val DISPLAY_WIDTH = 960
private const val DISPLAY_HEIGHT = 540
private const val PALETTE_SAMPLE_SIZE = 96
private const val PALETTE_CACHE_CAPACITY = 32

@Immutable
internal data class TvContentAmbientState(
    val focusedMediaKey: String? = null,
    val image: Image? = null,
    val accent: Color? = null,
    val accentContainer: Color? = null,
)

private data class AmbientArtworkSource(
    val key: String,
    val data: Any,
)

private data class TvAmbientPalette(
    val accent: Color,
    val accentContainer: Color,
)

private val paletteCache = LruCache<String, TvAmbientPalette>(PALETTE_CACHE_CAPACITY)

/**
 * Holds the ambient artwork and accent derived from the focused media.
 *
 * QA-08: the holder instance never changes, so providing it through a static
 * composition local does not recompose the tree. Only code that reads
 * [state] (the ambient background, the focused card, the focused hero)
 * reacts when the accent changes after focus settles.
 */
@Stable
internal class TvContentAmbient {
    var state: TvContentAmbientState by mutableStateOf(TvContentAmbientState())
        internal set

    val accent: Color? get() = state.accent
}

internal val LocalTvContentAccent = staticCompositionLocalOf { TvContentAmbient() }

/**
 * [focusedMedia] is read inside a snapshot flow rather than in composition, so
 * moving D-pad focus does not recompose the caller (QA-08). Timing is the
 * approved hero delay (TV-MOT-01).
 */
@Composable
internal fun rememberTvContentAmbient(focusedMedia: () -> MediaPreview?): TvContentAmbient {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolver by rememberUpdatedState(LocalArtworkResolver.current)
    val currentFocusedMedia by rememberUpdatedState(focusedMedia)
    val defaultAccent by rememberUpdatedState(androidx.tv.material3.MaterialTheme.colorScheme.primary)
    val defaultAccentContainer by rememberUpdatedState(
        androidx.tv.material3.MaterialTheme.colorScheme.primaryContainer,
    )
    val ambient = remember { TvContentAmbient() }

    LaunchedEffect(ambient) {
        snapshotFlow {
            val media = currentFocusedMedia()
            media?.stableKey to media?.let { selectArtworkSource(context, resolver.resolve(it)) }
        }
            .distinctUntilChanged { old, new -> old.first == new.first && old.second?.key == new.second?.key }
            .collectLatest { (focusedMediaKey, artwork) ->
                delay(TvMotionTokens.heroUpdateDelayMillis)
                if (focusedMediaKey == null || artwork == null) {
                    ambient.state = TvContentAmbientState()
                    return@collectLatest
                }
                val loaded = withContext(Dispatchers.IO) {
                    loadAmbient(context, artwork, defaultAccent, defaultAccentContainer)
                }
                ensureActive()
                ambient.state = loaded?.copy(focusedMediaKey = focusedMediaKey) ?: TvContentAmbientState()
            }
    }

    return ambient
}

@Composable
internal fun TvContentAmbientBackground(
    ambient: TvContentAmbient,
    modifier: Modifier = Modifier,
) {
    val state = ambient.state
    val reducedMotion = rememberReducedMotion()
    val background = androidx.tv.material3.MaterialTheme.colorScheme.background
    val accentContainer = state.accentContainer
        ?: androidx.tv.material3.MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        TvLayerlessCrossfade(
            targetState = state.image,
            reducedMotion = reducedMotion,
            label = "ambient artwork",
        ) { image ->
            image?.let {
                // Paint alpha instead of Modifier.alpha: the latter composites
                // a full-screen offscreen layer on every frame (QA-08).
                val painter = remember(it) { ImagePainter(it) }
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = TvAmbientTokens.imageAlpha,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to background.copy(alpha = TvAmbientTokens.horizontalScrimLeftAlpha),
                            0.52f to background.copy(alpha = TvAmbientTokens.horizontalScrimMiddleAlpha),
                            1f to accentContainer.copy(alpha = TvAmbientTokens.horizontalScrimRightAlpha),
                        ),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to background.copy(alpha = TvAmbientTokens.verticalScrimTopAlpha),
                            0.55f to Color.Transparent.copy(alpha = TvAmbientTokens.verticalScrimMiddleAlpha),
                            1f to background.copy(alpha = TvAmbientTokens.verticalScrimBottomAlpha),
                        ),
                    ),
                ),
        )
    }
}
private fun selectArtworkSource(context: Context, resolution: ArtworkResolution): AmbientArtworkSource? {
    val media = resolution.media
    media.backgroundUrl
        ?.takeIf(String::isNotBlank)
        ?.let { return AmbientArtworkSource("url:$it", it) }
    media.posterUrl
        ?.takeIf(String::isNotBlank)
        ?.let { return AmbientArtworkSource("url:$it", it) }
    if (!resolution.hasOverride) {
        fixtureArtworkResource(media)?.let { resourceId ->
            return AmbientArtworkSource(
                key = "resource:${context.packageName}:$resourceId",
                data = resourceId,
            )
        }
    }
    return null
}

private suspend fun loadAmbient(
    context: Context,
    artwork: AmbientArtworkSource,
    defaultAccent: Color,
    defaultAccentContainer: Color,
): TvContentAmbientState? {
    val imageLoader = SingletonImageLoader.get(context)
    // One software decode serves both the display image and the palette
    // sample, instead of decoding the source twice per focus change (QA-08).
    val displayResult = imageLoader.execute(
        ImageRequest.Builder(context)
            .data(artwork.data)
            .size(Size(DISPLAY_WIDTH, DISPLAY_HEIGHT))
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build(),
    )
    if (displayResult !is SuccessResult) return null

    val palette = paletteCache.get(artwork.key) ?: runCatching {
        val bitmap = displayResult.image.toBitmap().scale(PALETTE_SAMPLE_SIZE, PALETTE_SAMPLE_SIZE)
        val seed = DynamicColorsOptions.Builder()
            .setContentBasedSource(bitmap)
            .build()
            .contentBasedSeedColor
            ?: return@runCatching null
        val roles = MaterialColors.getColorRoles(seed, false)
        val accent = roles.accent
        val accentContainer = roles.accentContainer
        TvAmbientPalette(
            accent = Color(accent),
            accentContainer = Color(accentContainer),
        )
    }.getOrNull()?.also { paletteCache.put(artwork.key, it) }

    return TvContentAmbientState(
        image = displayResult.image,
        accent = palette?.accent ?: defaultAccent,
        accentContainer = palette?.accentContainer ?: defaultAccentContainer,
    )
}
