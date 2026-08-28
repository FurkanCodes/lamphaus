package com.lamphaus.app.tv

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.lamphaus.app.ui.fixtureArtworkResource
import com.lamphaus.core.model.MediaPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val DISPLAY_WIDTH = 1920
private const val DISPLAY_HEIGHT = 1080
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

internal val LocalTvContentAccent = compositionLocalOf<Color?> { null }

@Composable
internal fun rememberTvContentAmbient(media: MediaPreview?): TvContentAmbientState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val defaultAccent = androidx.tv.material3.MaterialTheme.colorScheme.primary
    val defaultAccentContainer = androidx.tv.material3.MaterialTheme.colorScheme.primaryContainer
    val artwork = remember(media) { media?.let { selectArtworkSource(context, it) } }
    var state by remember { mutableStateOf(TvContentAmbientState()) }

    LaunchedEffect(media?.stableKey, artwork?.key) {
        kotlinx.coroutines.delay(TvMotionTokens.heroUpdateDelayMillis)
        val focusedMediaKey = media?.stableKey
        if (focusedMediaKey == null || artwork == null) {
            state = TvContentAmbientState()
            return@LaunchedEffect
        }

        val loaded = withContext(Dispatchers.IO) {
            loadAmbient(context, artwork, defaultAccent, defaultAccentContainer)
        }
        ensureActive()
        state = loaded?.copy(focusedMediaKey = focusedMediaKey) ?: TvContentAmbientState()
    }

    return state
}

@Composable
internal fun TvContentAmbientBackground(
    state: TvContentAmbientState,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val background = androidx.tv.material3.MaterialTheme.colorScheme.background
    val accentContainer = state.accentContainer
        ?: androidx.tv.material3.MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        AnimatedContent(
            targetState = state.image,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    fadeIn(tween(TvMotionTokens.heroTransitionDurationMillis)) togetherWith
                        fadeOut(tween(TvMotionTokens.heroTransitionDurationMillis))
                }
            },
            label = "ambient artwork",
        ) { image ->
            image?.let {
                Image(
                    painter = ImagePainter(it),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(TvAmbientTokens.imageAlpha),
                    contentScale = ContentScale.Crop,
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

private fun selectArtworkSource(context: Context, media: MediaPreview): AmbientArtworkSource? {
    media.backgroundUrl
        ?.takeIf(String::isNotBlank)
        ?.let { return AmbientArtworkSource("url:$it", it) }
    media.posterUrl
        ?.takeIf(String::isNotBlank)
        ?.let { return AmbientArtworkSource("url:$it", it) }
    fixtureArtworkResource(media)?.let { resourceId ->
        return AmbientArtworkSource(
            key = "resource:${context.packageName}:$resourceId",
            data = resourceId,
        )
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
    val displayResult = imageLoader.execute(
        ImageRequest.Builder(context)
            .data(artwork.data)
            .size(Size(DISPLAY_WIDTH, DISPLAY_HEIGHT))
            .build(),
    )
    if (displayResult !is SuccessResult) return null

    val palette = paletteCache.get(artwork.key) ?: runCatching {
        val sampleResult = imageLoader.execute(
            ImageRequest.Builder(context)
                .data(artwork.data)
                .size(PALETTE_SAMPLE_SIZE, PALETTE_SAMPLE_SIZE)
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .build(),
        )
        if (sampleResult !is SuccessResult) return@runCatching null
        val bitmap = sampleResult.image.toBitmap(PALETTE_SAMPLE_SIZE, PALETTE_SAMPLE_SIZE)
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
