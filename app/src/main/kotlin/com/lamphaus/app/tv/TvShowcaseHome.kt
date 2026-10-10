package com.lamphaus.app.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.core.model.MediaPreview
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * Showcase Home (TV-CNT-05): the focused title's backdrop fills the screen
 * and its details sit at the top start, above a single Spotlight row. The
 * backdrop and details follow focus once it rests (TV-MOT-01); the rows are
 * the Spotlight rows without their details slot.
 */

/**
 * The title Showcase describes: [target] once focus has rested on it for the
 * approved hero delay. The first title shows at once so the page never opens
 * blank. [target] is read in a snapshot flow, so focus moves recompose only
 * the readers of the returned state (QA-08).
 */
@Composable
internal fun rememberSettledShowcaseMedia(target: () -> MediaPreview?): State<MediaPreview?> {
    val currentTarget by rememberUpdatedState(target)
    val settled = remember { mutableStateOf<MediaPreview?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { currentTarget() }
            .distinctUntilChanged { old, new -> old?.stableKey == new?.stableKey }
            .collectLatest { media ->
                if (settled.value != null) delay(TvMotionTokens.heroUpdateDelayMillis)
                settled.value = media
            }
    }
    return settled
}

/**
 * The full-bleed backdrop. It is placed inside the page but draws over the
 * whole screen, reaching back over the page's [startInset] and [topInset],
 * so the navigation sits on top of it.
 */
@Composable
internal fun TvShowcaseBackdrop(
    media: State<MediaPreview?>,
    startInset: Dp,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val dx = startInset.roundToPx()
                val dy = topInset.roundToPx()
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.maxWidth + dx, constraints.maxHeight + dy),
                )
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-dx, -dy) }
            },
    ) {
        TvLayerlessCrossfade(
            targetState = media.value,
            reducedMotion = reducedMotion,
            label = "showcase backdrop",
            modifier = Modifier.fillMaxSize(),
            enterOffsetFraction = 1f / 80f,
            exitOffsetFraction = -1f / 100f,
        ) { shown ->
            if (shown != null) {
                MediaArtwork(
                    media = shown,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    preferBackdrop = true,
                )
            }
        }
        Box(Modifier.fillMaxSize().tvShowcaseScrim(background, primary))
    }
}

/** Title (or logo), the Spotlight meta line, and two lines of summary. */
@Composable
internal fun TvShowcaseDetails(media: State<MediaPreview?>, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    TvLayerlessCrossfade(
        targetState = media.value,
        reducedMotion = reducedMotion,
        label = "showcase details",
        modifier = modifier.width(TvShowcaseTokens.detailsWidth),
    ) { shown ->
        if (shown != null) TvShowcaseDetailsContent(shown)
    }
}

@Composable
private fun TvShowcaseDetailsContent(media: MediaPreview) {
    val resolver = LocalArtworkResolver.current
    val resolved = remember(media, resolver) { resolver.resolve(media).media }
    var logoFailed by remember(resolved.logoUrl) { mutableStateOf(false) }
    val meta = resolved.spotlightMetaText()
    Column {
        val logoUrl = resolved.logoUrl
        if (!logoUrl.isNullOrBlank() && !logoFailed) {
            AsyncImage(
                model = logoUrl,
                contentDescription = resolved.name,
                modifier = Modifier.size(TvShowcaseTokens.logoWidth, TvShowcaseTokens.logoHeight),
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onError = { logoFailed = true },
            )
        } else {
            Text(
                text = resolved.name,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        resolved.description?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Keeps the details and rows legible over the backdrop (SHR-PROD-01). As with
 * the hero, the gradients are baked once into a half-resolution bitmap and
 * drawn in a single pass, because each full-screen blended layer costs
 * milliseconds per frame on TV GPUs (QA-08).
 */
private fun Modifier.tvShowcaseScrim(background: Color, primary: Color): Modifier = drawWithCache {
    val width = (size.width / 2f).roundToInt().coerceAtLeast(1)
    val height = (size.height / 2f).roundToInt().coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bitmap)
    listOf(
        ScrimLayer(
            arrayOf(
                0f to background.copy(alpha = 0.97f),
                0.28f to background.copy(alpha = 0.92f),
                0.52f to background.copy(alpha = 0.66f),
                0.78f to Color.Transparent,
            ),
            horizontal = true,
        ),
        ScrimLayer(
            arrayOf(
                0f to Color.Transparent,
                0.30f to Color.Transparent,
                0.48f to background.copy(alpha = 0.72f),
                0.74f to background,
            ),
            horizontal = false,
        ),
        ScrimLayer(arrayOf(0f to primary.copy(alpha = 0.08f), 0.42f to Color.Transparent), horizontal = true),
    ).forEach { canvas.drawScrim(it, width, height) }
    val scrim = bitmap.asImageBitmap()
    val destination = IntSize(size.width.roundToInt(), size.height.roundToInt())
    onDrawBehind {
        drawImage(scrim, dstSize = destination, filterQuality = FilterQuality.Low)
    }
}
