package com.lamphaus.app.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lamphaus.app.R
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.app.ui.ContentMenuOrigin
import com.lamphaus.app.ui.ContentMenuTarget
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.MediaMetadataPresentation
import com.lamphaus.app.ui.SelectionCheckmark
import com.lamphaus.app.ui.mediaFocusRestore
import com.lamphaus.app.ui.metadataPresentation
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.WatchProgress
import kotlin.math.roundToInt

/*
 * Spotlight Home (TV-CNT-03): no hero. The focused row pins below the top
 * navigation, its focused card pins at the row start and widens into the
 * title's still, and a fixed details slot under the row describes it.
 *
 * Every row keeps the same height whether or not it has focus, so a focus
 * move never changes the column's layout (TV-CNT-02, QA-08).
 */

/** Header line plus the gap under it; the pinned row keeps it visible. */
private val SpotlightHeaderBlock = 32.dp
private val SpotlightDetailsGap = 16.dp
private val SpotlightDetailsHeight = 64.dp
private val SpotlightDetailsMaxWidth = 560.dp
private val SpotlightLogoWidth = 180.dp
private val SpotlightLogoHeight = 56.dp
internal val SpotlightRowSpacing = 28.dp
private const val SpotlightDimmedAlpha = 0.5f

/**
 * Scrolls so the focused child's leading edge lands [leadingEdgePx] from the
 * container's start. The lazy list clamps the distance at either end.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class PinnedBringIntoViewSpec(private val leadingEdgePx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - leadingEdgePx
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberPinnedBringIntoViewSpec(leadingEdge: Dp): BringIntoViewSpec {
    val density = LocalDensity.current
    return remember(density, leadingEdge) {
        PinnedBringIntoViewSpec(with(density) { leadingEdge.toPx() })
    }
}

/** Spotlight row block (header, cards, details) plus the gap to the next row. */
private val SpotlightRowPitch = SpotlightHeaderBlock + TvLayoutTokens.posterHeight +
    SpotlightDetailsGap + SpotlightDetailsHeight + SpotlightRowSpacing

@OptIn(ExperimentalFoundationApi::class)
private val SpotlightHomeCacheWindow = LazyLayoutCacheWindow(ahead = SpotlightRowPitch, behind = SpotlightRowPitch)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberSpotlightHomeListState(): LazyListState =
    rememberLazyListState(cacheWindow = SpotlightHomeCacheWindow)

/** Pins the focused row under the top navigation, keeping its header visible. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpotlightColumnScrolling(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides rememberPinnedBringIntoViewSpec(SpotlightHeaderBlock),
        content = content,
    )
}

/** Pins the focused card at the row's start padding. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotlightRowScrolling(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides rememberPinnedBringIntoViewSpec(TvLayoutTokens.screenHorizontalPadding),
        content = content,
    )
}

/** The meta line under the focused row: genres, year, type, content rating, rating. */
internal fun spotlightMetaParts(
    presentation: MediaMetadataPresentation,
    typeLabel: String?,
): List<String> = buildList {
    if (presentation.genres.isNotEmpty()) add(presentation.genres.joinToString(", "))
    presentation.year?.let { add(it.toString()) }
    typeLabel?.let(::add)
    presentation.contentRating?.let(::add)
    presentation.ratingText?.let { add("★ $it") }
}

@Composable
private fun MediaPreview.spotlightMetaText(): String {
    val typeLabel = when (type) {
        MediaType.MOVIE -> stringResource(R.string.media_type_movie)
        MediaType.SERIES -> stringResource(R.string.media_type_series)
        else -> null
    }
    val presentation = remember(this) { metadataPresentation(maxGenres = 2) }
    return spotlightMetaParts(presentation, typeLabel).joinToString("  •  ")
}

@Composable
internal fun TvSpotlightRow(
    section: CatalogSection,
    contentHasFocus: Boolean,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
) {
    var focusedMedia by remember { mutableStateOf<MediaPreview?>(null) }
    val prefetchStill = rememberStillPrefetcher()
    val row = rememberTvRowFocus()
    TvSpotlightRowFrame(
        title = section.title,
        subtitle = section.providerName,
        error = section.errorMessage,
        contentHasFocus = contentHasFocus,
        focusedMedia = focusedMedia,
    ) {
        if (section.initialLoading && section.items.isEmpty()) {
            TvSpotlightCardsSkeleton()
        } else if (section.items.isNotEmpty() || section.hasMore || section.loadMoreError != null) {
            val showAction = section.loadMoreError != null || section.hasMore
            val trailing = rememberTrailingActionFocus(showAction, section.items.size)
            SpotlightRowScrolling {
                LazyRow(
                    modifier = Modifier.tvRowFocus(row),
                    horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
                    contentPadding = PaddingValues(horizontal = TvLayoutTokens.screenHorizontalPadding),
                ) {
                    itemsIndexed(section.items, key = { _, media -> media.stableKey }) { index, media ->
                        TvSpotlightCard(
                            media = media,
                            onClick = { onMedia(media) },
                            onFocused = {
                                focusedMedia = media
                                onFocused(media)
                                section.items.getOrNull(index + 1)?.let(prefetchStill)
                            },
                            modifier = Modifier
                                .mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored)
                                .tvRowItem(row, index)
                                .homeEntry(index, firstItemFocusRequester)
                                .trailingItem(trailing, index),
                        )
                    }
                    if (trailing.visible(showAction)) {
                        item("catalog-action") {
                            TvAction(
                                label = stringResource(if (section.loadMoreError != null) R.string.retry else R.string.load_more),
                                icon = Icons.Outlined.Refresh,
                                onClick = {
                                    trailing.onPressed(section.items.size)
                                    if (section.loadMoreError != null) onRetry() else onLoadMore()
                                },
                                modifier = Modifier.trailingAction(trailing)
                                    .onFocusChanged { if (it.isFocused) focusedMedia = null },
                            )
                        }
                    }
                }
            }
        } else if (section.errorMessage != null) {
            // An error-only row keeps one focusable element so D-pad traversal
            // passes through it (SHR-PROD-04).
            TvAction(
                label = stringResource(R.string.retry),
                icon = Icons.Outlined.Refresh,
                onClick = onRetry,
                modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
            )
        }
    }
}

@Composable
internal fun TvSpotlightContinueWatchingRow(
    items: List<Pair<MediaPreview, WatchProgress>>,
    contentHasFocus: Boolean,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
) {
    var focusedMedia by remember { mutableStateOf<MediaPreview?>(null) }
    val row = rememberTvRowFocus()
    TvSpotlightRowFrame(
        title = stringResource(R.string.continue_watching),
        subtitle = null,
        error = null,
        contentHasFocus = contentHasFocus,
        focusedMedia = focusedMedia,
    ) {
        SpotlightRowScrolling {
            LazyRow(
                modifier = Modifier.tvRowFocus(row),
                horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
                contentPadding = PaddingValues(horizontal = TvLayoutTokens.screenHorizontalPadding),
            ) {
                itemsIndexed(items, key = { _, item -> item.first.stableKey }) { index, (media, progress) ->
                    TvContinueWatchingCard(
                        media = media,
                        progress = progress,
                        onClick = { onMedia(media) },
                        onFocused = {
                            focusedMedia = media
                            onFocused(media)
                        },
                        modifier = Modifier
                            .mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored)
                            .tvRowItem(row, index)
                                .homeEntry(index, firstItemFocusRequester),
                    )
                }
            }
        }
    }
}

/** The Home entry requester sits on the first card of the first row. */
private fun Modifier.homeEntry(index: Int, homeEntry: FocusRequester?): Modifier =
    if (index == 0 && homeEntry != null) focusRequester(homeEntry) else this

/** A focusable placeholder row while the first catalog window loads (TV-CNT-02). */
@Composable
internal fun TvSpotlightLoadingRow(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse(label = "spotlight row loading")
    val skeletonColor = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier.focusable()) {
        Box(
            modifier = Modifier
                .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                .width(220.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .skeletonPulseBackground(skeletonColor) { pulse.value },
        )
        Spacer(Modifier.height(SpotlightHeaderBlock - 20.dp))
        TvSpotlightCardsSkeleton()
        Spacer(Modifier.height(SpotlightDetailsGap + SpotlightDetailsHeight))
    }
}

@Composable
private fun TvSpotlightCardsSkeleton() {
    val pulse = rememberSkeletonPulse(label = "spotlight cards loading")
    val skeletonColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .size(TvLayoutTokens.posterWidth, TvLayoutTokens.posterHeight)
                    .clip(TvShapeTokens.card)
                    .skeletonPulseBackground(skeletonColor) { pulse.value },
            )
        }
    }
}

/**
 * Header, cards and the fixed details slot. Rows without focus dim while
 * the viewer browses (TV-CNT-03); the dim modulates each draw instead of
 * allocating an offscreen layer (QA-08).
 */
@Composable
private fun TvSpotlightRowFrame(
    title: String,
    subtitle: String?,
    error: String?,
    contentHasFocus: Boolean,
    focusedMedia: MediaPreview?,
    cards: @Composable () -> Unit,
) {
    var rowHasFocus by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val rowAlpha by animateFloatAsState(
        targetValue = if (contentHasFocus && !rowHasFocus) SpotlightDimmedAlpha else 1f,
        animationSpec = if (reducedMotion) snap() else tween(TvMotionTokens.focusDurationMillis),
        label = "spotlight row dim",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { rowHasFocus = it.hasFocus }
            .graphicsLayer {
                alpha = rowAlpha
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                .height(SpotlightHeaderBlock - 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = "  ·  $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            error?.let {
                Text(
                    text = "  ·  $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        cards()
        Spacer(Modifier.height(SpotlightDetailsGap))
        Box(
            modifier = Modifier
                .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                .widthIn(max = SpotlightDetailsMaxWidth)
                .fillMaxWidth()
                .height(SpotlightDetailsHeight),
        ) {
            if (rowHasFocus && focusedMedia != null) {
                TvSpotlightDetails(focusedMedia)
            }
        }
    }
}

@Composable
private fun TvSpotlightDetails(media: MediaPreview) {
    val resolver = LocalArtworkResolver.current
    val resolved = remember(media, resolver) { resolver.resolve(media).media }
    val meta = resolved.spotlightMetaText()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        resolved.description?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A poster that widens into the title's 16:9 still when focused (TV-CNT-03).
 * The width animates in the layout phase only, so each frame is a row
 * relayout without recomposition (QA-08). The still is laid out at its full
 * expanded size and revealed by the widening clip, so Coil decodes it once
 * at display size instead of at the first, narrow frame.
 */
@Composable
internal fun TvSpotlightCard(
    media: MediaPreview,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cardFocus = remember { FocusRequester() }
    val menuEnvironment = LocalTvContentMenuEnvironment.current
    val menuRequest = menuEnvironment.onRequest?.let { request ->
        { focus: FocusRequester ->
            request(ContentMenuTarget(media = media, origin = ContentMenuOrigin.POSTER), focus)
        }
    }
    val currentMenuRequest by rememberUpdatedState(menuRequest)
    val holdTracker = remember(scope, cardFocus) {
        SelectHoldTracker(scope) { currentMenuRequest?.invoke(cardFocus) }
    }.takeIf { menuRequest != null }
    val completed = media.type == MediaType.MOVIE && media.id in menuEnvironment.completedVideoIds
    val reducedMotion = rememberReducedMotion()
    val ambient = LocalTvContentAccent.current
    val primary = MaterialTheme.colorScheme.primary
    val meta = media.spotlightMetaText()
    val cardDescription = if (meta.isEmpty()) media.name else "${media.name}, $meta"
    val expansion by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(TvMotionTokens.focusDurationMillis),
        label = "spotlight card width",
    )
    Box(
        modifier = modifier
            .layout { measurable, _ ->
                val width = lerp(
                    TvLayoutTokens.posterWidth.toPx(),
                    TvLayoutTokens.spotlightExpandedWidth.toPx(),
                    expansion,
                ).roundToInt()
                val height = TvLayoutTokens.posterHeight.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvSelectHoldMenu(holdTracker)
            .focusRequester(cardFocus)
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics { contentDescription = cardDescription },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // TV-FOC-02 outline and halo; the widening replaces the scale.
                    shadowElevation = 7.dp.toPx() * expansion
                    shape = TvShapeTokens.card
                    if (expansion > 0f) {
                        val halo = (ambient.accent ?: primary).copy(alpha = TvFocusTokens.halo.alpha)
                        ambientShadowColor = halo
                        spotShadowColor = halo
                    }
                }
                .border(
                    width = TvFocusTokens.outlineWidth,
                    color = if (focused) ambient.accent ?: primary else TvSurfaceTokens.subtleBorder,
                    shape = TvShapeTokens.card,
                )
                .padding(if (focused) TvFocusTokens.outlineWidth else 0.5.dp)
                .clip(TvShapeTokens.card),
        ) {
            MediaArtwork(
                media = media,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(TvLayoutTokens.posterWidth, TvLayoutTokens.posterHeight),
                contentScale = ContentScale.Crop,
            )
            if (focused) {
                TvSpotlightStill(
                    media = media,
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(TvLayoutTokens.spotlightExpandedWidth, TvLayoutTokens.posterHeight)
                        .graphicsLayer {
                            alpha = expansion
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                )
            }
            if (completed) {
                SelectionCheckmark(
                    selected = true,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun TvSpotlightStill(media: MediaPreview, modifier: Modifier = Modifier) {
    val resolver = LocalArtworkResolver.current
    val resolved = remember(media, resolver) { resolver.resolve(media).media }
    var logoFailed by remember(resolved.logoUrl) { mutableStateOf(false) }
    Box(modifier) {
        MediaArtwork(
            media = media,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            preferBackdrop = true,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        val logoUrl = resolved.logoUrl
        val logoModifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, bottom = 14.dp)
        if (!logoUrl.isNullOrBlank() && !logoFailed) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = logoModifier.size(SpotlightLogoWidth, SpotlightLogoHeight),
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onError = { logoFailed = true },
            )
        } else {
            Text(
                text = resolved.name,
                modifier = logoModifier.widthIn(max = TvLayoutTokens.spotlightExpandedWidth - 32.dp),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Warms the next card's still so it is ready when focus moves on. The
 * request matches the displayed size, so the memory cache can serve it.
 */
@Composable
private fun rememberStillPrefetcher(): (MediaPreview) -> Unit {
    val context = LocalContext.current
    val resolver = LocalArtworkResolver.current
    val density = LocalDensity.current
    return remember(context, resolver, density) {
        val width = with(density) { TvLayoutTokens.spotlightExpandedWidth.roundToPx() }
        val height = with(density) { TvLayoutTokens.posterHeight.roundToPx() }
        val prefetch: (MediaPreview) -> Unit = { media ->
            val url = resolver.resolve(media).media.backgroundUrl
            if (!url.isNullOrBlank()) {
                SingletonImageLoader.get(context).enqueue(
                    ImageRequest.Builder(context).data(url).size(width, height).build(),
                )
            }
        }
        prefetch
    }
}
