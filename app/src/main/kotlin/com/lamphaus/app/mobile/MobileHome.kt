package com.lamphaus.app.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Button
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

import coil3.compose.AsyncImage
import com.lamphaus.app.ui.MediaArtwork
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlin.math.roundToInt
import kotlin.math.absoluteValue
import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.app.ui.HOME_CATALOG_SCROLL_SETTLE_MILLIS
import com.lamphaus.app.ui.isResumable
import com.lamphaus.app.ui.shouldPrefetchHomeCatalogBatch
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.app.R
import com.lamphaus.app.ui.KenBurnsArtwork
import com.lamphaus.app.ui.mediaFocusRestore
import com.lamphaus.app.ui.metadataPresentation
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.app.ui.ContentMenuAction
import com.lamphaus.app.ui.ContentMenuOrigin
import com.lamphaus.app.ui.ContentMenuTarget
import com.lamphaus.app.ui.SelectionCheckmark
import com.lamphaus.app.ui.menuActions
import com.lamphaus.core.model.MediaType

/** Zoom-settle applied to hero artwork while it crossfades during a swipe. */
private const val HERO_ARTWORK_ZOOM = 0.06f

/** Soft bottom fade carried by each hero artwork layer; it transforms with the layer. */
private val HERO_LAYER_FADE_STOPS = arrayOf(
    0.50f to Color.Transparent,
    0.72f to Color.Black.copy(alpha = 0.32f),
    1f to Color.Black.copy(alpha = 0.58f),
)

/** Fixed bottom gradient height ending in opaque black behind the title and dots. */
private val HERO_BOTTOM_ANCHOR_HEIGHT = 120.dp

/** Crossfade timing for the detached hero title block (MOB-MOT-02: 150-250ms). */
private const val HERO_CONTENT_FADE_MILLIS = 220

@Composable
internal fun MobileHomeScreen(
    state: AppUiState,
    onMedia: (MediaPreview) -> Unit,
    onAddSource: () -> Unit,
    onLoadMore: (String) -> Unit,
    onRetry: (String) -> Unit,
    onLoadMoreHome: () -> Unit,
    onRetryHome: () -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    onPlay: (MediaPreview) -> Unit,
    inLibrary: (MediaPreview) -> Boolean,
    onToggleLibrary: (MediaPreview) -> Unit,
    onOpenMenu: (ContentMenuTarget) -> Unit,
    onMenuAction: (ContentMenuTarget, ContentMenuAction) -> Unit,
) {
    val allMedia = state.allMedia
    // Same feature selection as the TV home: first five distinct catalog items.
    val heroItems = remember(allMedia) { allMedia.distinctBy(MediaPreview::stableKey).take(5) }
    // Progress is synced with the Supabase watch_progress table both ways
    // (player writes -> Room + cloud; cloud -> Room on sign-in), so this row
    // follows whatever was last watched on any device.
    val continueWatching = remember(state.progress, allMedia) {
        val mediaByKey = allMedia.associateBy(MediaPreview::stableKey)
        state.progress
            .asSequence()
            .filter { it.isResumable() }
            .sortedByDescending { it.updatedAtEpochMillis }
            .mapNotNull { progress ->
                // Catalog rows only cover titles a loaded section lists; the
                // persisted preview snapshot hydrates everything else.
                val media = mediaByKey[progress.mediaKey] ?: progress.preview
                    ?: return@mapNotNull null
                media to progress
            }
            .toList()
    }
    val progressByVideo = remember(state.progress) { state.progress.associateBy { it.videoId } }
    val completedVideoIds = remember(state.progress) {
        state.progress.filter { it.completed }.mapTo(mutableSetOf()) { it.videoId }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(
        listState,
        state.sections.size,
        state.homeCatalogBatch.hasMore,
        state.homeCatalogBatch.loadingMore,
        state.homeCatalogBatch.loadMoreFailed,
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to
                listState.layoutInfo.totalItemsCount
        }.distinctUntilChanged().collectLatest { (lastVisibleIndex, totalListItems) ->
            delay(HOME_CATALOG_SCROLL_SETTLE_MILLIS)
            if (
                shouldPrefetchHomeCatalogBatch(
                    lastVisibleIndex = lastVisibleIndex,
                    totalListItems = totalListItems,
                    hasMore = state.homeCatalogBatch.hasMore,
                    loading = state.homeCatalogBatch.loadingMore,
                    failed = state.homeCatalogBatch.loadMoreFailed,
                )
            ) {
                onLoadMoreHome()
            }
        }
    }
    Box(Modifier.fillMaxSize().background(MobileTokens.black)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navBarClearancePadding()),
            verticalArrangement = Arrangement.spacedBy(MobileTokens.sectionGap),
        ) {
            if (heroItems.isNotEmpty()) {
                item(key = "feature") {
                    MobileHeroCarousel(
                        heroItems,
                        onMedia,
                        onPlay,
                        inLibrary,
                        onToggleLibrary,
                        restoreMediaKey,
                        onFocusRestored,
                        state.kenBurnsEnabled,
                    )
                }
            } else if (
                state.initialContentLoading ||
                state.homeCatalogBatch.loadingMore ||
                state.sections.any(CatalogSection::initialLoading)
            ) {
                // Keep the hero slot present while the first catalog window loads,
                // so the big card never disappears during progressive loading.
                item(key = "feature") { MobileHeroLoadingSkeleton() }
            }
            if (continueWatching.isNotEmpty()) {
                item(key = "continue-watching") {
                    MobileContinueWatchingRow(
                        continueWatching,
                        onMedia,
                        inLibrary,
                        onOpenMenu,
                        onMenuAction,
                    )
                }
            }
            if (
                !state.initialContentLoading &&
                !state.homeCatalogBatch.loadingMore &&
                !state.homeCatalogBatch.loadMoreFailed &&
                !state.homeCatalogBatch.hasMore &&
                state.providers.isEmpty() &&
                state.sections.isEmpty()
            ) {
                item { EmptyProviders(Modifier.padding(horizontal = 16.dp), onAddSource) }
            }
            items(state.sections, key = CatalogSection::id) { section ->
                CatalogRow(
                    section,
                    onMedia,
                    onLoadMore,
                    onRetry,
                    restoreMediaKey,
                    onFocusRestored,
                    progressByVideo,
                    completedVideoIds,
                    inLibrary,
                    onOpenMenu,
                    onMenuAction,
                )
            }
            when {
                state.homeCatalogBatch.loadMoreFailed -> item(key = "home-catalog-retry") {
                    OutlinedButton(
                        onClick = onRetryHome,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
                state.homeCatalogBatch.loadingMore && state.sections.none(CatalogSection::initialLoading) -> {
                    item(key = "home-catalog-loading") {
                        MobileHomeCatalogLoadingSkeleton()
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileHomeCatalogLoadingSkeleton() {
    val loadingDescription = stringResource(R.string.loading_more_rows)
    val pulse by rememberInfiniteTransition(label = "home catalog loading").animateFloat(
        initialValue = 0.58f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton pulse",
    )
    val skeletonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = loadingDescription },
        verticalArrangement = Arrangement.spacedBy(MobileTokens.sectionGap),
    ) {
        Text(
            text = stringResource(R.string.loading_more_rows),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(skeletonColor),
                    )
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(skeletonColor.copy(alpha = pulse * 0.8f)),
                    )
                }
                MobileCatalogItemsLoadingSkeleton(skeletonColor, pulse)
            }
        }
    }
}

@Composable
private fun MobileCatalogItemsLoadingSkeleton(
    skeletonColor: Color,
    pulse: Float,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(4) {
            Box(
                modifier = Modifier
                    .width(138.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(MobileTokens.radiusCard))
                    .background(skeletonColor),
            )
        }
    }
}

@Composable
private fun MobileHeroCarousel(
    items: List<MediaPreview>,
    onMedia: (MediaPreview) -> Unit,
    onPlay: (MediaPreview) -> Unit,
    inLibrary: (MediaPreview) -> Boolean,
    onToggleLibrary: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    kenBurnsEnabled: Boolean,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState { items.size }
    val reducedMotion = rememberReducedMotion()
    Box(
        Modifier
            .fillMaxWidth()
            .height(400.dp)
            .clipToBounds(),
    ) {
        // Artwork stage: each full-bleed layer carries its own soft bottom
        // fade inside one graphics layer, so the swipe alpha and zoom always
        // transform image and fade together and the fade can never drift off
        // its artwork (MOB-CLR-07, MOB-GFX-05). Nothing translates with the
        // pager, and the viewport is clipped so compounded scaling stays
        // inside the hero.
        val settledIndex = pagerState.currentPage.coerceIn(0, items.lastIndex)
        val swipeProgress = { pagerState.currentPageOffsetFraction.absoluteValue }
        HeroArtworkLayer(
            media = items[settledIndex],
            enabled = kenBurnsEnabled,
            reducedMotion = reducedMotion,
            layerTransform = { heroSettledLayerTransform(swipeProgress(), reducedMotion) },
            modifier = Modifier.fillMaxSize(),
        )
        // Neighbor layer mounts only while a swipe is in progress; its alpha
        // and zoom mirror the settled layer so the roles swap without a pop.
        val neighborIndex by remember(items) {
            derivedStateOf {
                val fraction = pagerState.currentPageOffsetFraction
                when {
                    fraction > 0.001f -> (pagerState.currentPage + 1).takeIf { it < items.size }
                    fraction < -0.001f -> (pagerState.currentPage - 1).takeIf { it >= 0 }
                    else -> null
                }
            }
        }
        neighborIndex?.let { neighbor ->
            HeroArtworkLayer(
                media = items[neighbor],
                enabled = kenBurnsEnabled,
                reducedMotion = reducedMotion,
                layerTransform = { heroNeighborLayerTransform(swipeProgress(), reducedMotion) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Fixed protections above the transformed layers: the top status-bar
        // guard, plus a short bottom gradient ending in opaque black so the
        // title, actions, and dots never sit on a moving brightness seam and
        // the scrims cannot breathe during a swipe.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            0.16f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(HERO_BOTTOM_ANCHOR_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MobileTokens.black,
                    ),
                ),
        )
        // Pager is a gesture surface only: per-item click, focus restore, and
        // semantics; all visuals live in the detached layers around it.
        androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val media = items[page]
            val pageDescription = stringResource(
                R.string.hero_carousel_description_touch, media.name, page + 1, items.size,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored)
                    .clickable(role = Role.Button) { onMedia(media) }
                    .semantics { contentDescription = pageDescription },
            )
        }
        // Detached overlay: the title block crossfades and the page indicator
        // holds still instead of sliding with the pager pages.
        val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]
        AnimatedContent(
            targetState = current,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    fadeIn(tween(HERO_CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(HERO_CONTENT_FADE_MILLIS / 2))
                }
            },
            label = "hero content",
        ) { media ->
            HeroOverlayContent(
                media = media,
                saved = inLibrary(media),
                onMedia = onMedia,
                onPlay = onPlay,
                onToggleLibrary = onToggleLibrary,
            )
        }
        // Page indicator: dots morph continuously with the swipe fraction.
        // Pager state is read inside the draw scope, so the gesture drives
        // the dots without recomposing the carousel.
        val activeDotColor = MaterialTheme.colorScheme.primary
        val idleDotColor = Color.White.copy(alpha = 0.45f)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .height(6.dp)
                .fillMaxWidth()
                .drawBehind {
                    val dotHeight = 6.dp.toPx()
                    val minDot = 6.dp.toPx()
                    val maxDot = 18.dp.toPx()
                    val gap = 6.dp.toPx()
                    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    val widths = FloatArray(items.size) { index ->
                        val raw = (1f - (index - position).absoluteValue).coerceIn(0f, 1f)
                        val eased = raw * raw * (3f - 2f * raw)
                        minDot + (maxDot - minDot) * eased
                    }
                    var x = (size.width - (widths.sum() + gap * (items.size - 1))) / 2f
                    widths.forEachIndexed { index, dotWidth ->
                        val raw = (1f - (index - position).absoluteValue).coerceIn(0f, 1f)
                        val eased = raw * raw * (3f - 2f * raw)
                        drawRoundRect(
                            color = lerp(idleDotColor, activeDotColor, eased),
                            topLeft = Offset(x, 0f),
                            size = Size(dotWidth, dotHeight),
                            cornerRadius = CornerRadius(dotHeight / 2f),
                        )
                        x += dotWidth + gap
                    }
                },
        )
    }
}

/**
 * One hero carousel artwork layer: the image and its soft bottom fade wrapped
 * in a single graphics layer, so the swipe alpha and zoom always transform
 * both together and the fade can never drift off its artwork (MOB-CLR-07,
 * MOB-GFX-05). Reduced motion drops the shared swipe zoom for the whole layer
 * at once while the crossfade survives (MOB-MOT-03).
 */
@Composable
private fun HeroArtworkLayer(
    media: MediaPreview,
    enabled: Boolean,
    reducedMotion: Boolean,
    layerTransform: () -> HeroLayerTransform,
    modifier: Modifier = Modifier,
) {
    Box(modifier.heroLayerTransform(layerTransform)) {
        KenBurnsArtwork(
            media = media,
            enabled = enabled,
            reducedMotion = reducedMotion,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(*HERO_LAYER_FADE_STOPS)),
        )
    }
}

/** Alpha and scale shared by a hero artwork layer and its attached fade. */
internal data class HeroLayerTransform(
    val alpha: Float,
    val scale: Float,
)

/** Settled page: fully opaque, zooming up while the neighbor layer arrives. */
internal fun heroSettledLayerTransform(progress: Float, reducedMotion: Boolean): HeroLayerTransform =
    HeroLayerTransform(
        alpha = 1f,
        scale = if (reducedMotion) 1f else 1f + HERO_ARTWORK_ZOOM * progress,
    )

/** Incoming neighbor: fades in with the swipe while its zoom settles downward. */
internal fun heroNeighborLayerTransform(progress: Float, reducedMotion: Boolean): HeroLayerTransform =
    HeroLayerTransform(
        alpha = progress,
        scale = if (reducedMotion) 1f else 1f + HERO_ARTWORK_ZOOM * (1f - progress),
    )

private fun Modifier.heroLayerTransform(layerTransform: () -> HeroLayerTransform): Modifier =
    graphicsLayer {
        val layer = layerTransform()
        alpha = layer.alpha
        scaleX = layer.scale
        scaleY = layer.scale
    }

/**
 * Title, metadata, and actions for the settled hero page. Lives outside the
 * pager so it crossfades on page change instead of sliding with the artwork.
 */
@Composable
private fun HeroOverlayContent(
    media: MediaPreview,
    saved: Boolean,
    onMedia: (MediaPreview) -> Unit,
    onPlay: (MediaPreview) -> Unit,
    onToggleLibrary: (MediaPreview) -> Unit,
) {
    val heroLogo = LocalArtworkResolver.current.resolve(media).media.logoUrl
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp), // clearance for the detached page indicator
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (heroLogo.isNullOrBlank()) {
            Text(
                text = media.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        } else {
            AsyncImage(
                model = heroLogo,
                contentDescription = media.name,
                modifier = Modifier.heightIn(max = 64.dp),
                contentScale = ContentScale.Fit,
            )
        }
        MobileMetadataLine(
            presentation = media.metadataPresentation(maxGenres = 2),
            includeGenres = true,
            color = Color.White.copy(alpha = 0.88f),
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroIconAction(
                icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = stringResource(if (saved) R.string.saved else R.string.save),
                tint = if (saved) MaterialTheme.colorScheme.primary else Color.White,
                onClick = { onToggleLibrary(media) },
            )
            Button(
                onClick = { onPlay(media) },
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MobileTokens.textPrimary,
                    contentColor = Color.Black,
                ),
            ) {
                Icon(Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.play), style = MaterialTheme.typography.titleMedium)
            }
            HeroIconAction(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.info),
                tint = Color.White,
                onClick = { onMedia(media) },
            )
        }
    }
}

@Composable
private fun HeroIconAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun MobileContinueWatchingRow(
    items: List<Pair<MediaPreview, WatchProgress>>,
    onMedia: (MediaPreview) -> Unit,
    inLibrary: (MediaPreview) -> Boolean,
    onOpenMenu: (ContentMenuTarget) -> Unit,
    onMenuAction: (ContentMenuTarget, ContentMenuAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.continue_watching),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.first.stableKey }) { (media, progress) ->
                MobileContinueWatchingCard(
                    media,
                    progress,
                    onMedia,
                    inLibrary(media),
                    onOpenMenu,
                    onMenuAction,
                )
            }
        }
    }
}

@Composable
private fun MobileContinueWatchingCard(
    media: MediaPreview,
    progress: WatchProgress,
    onMedia: (MediaPreview) -> Unit,
    inLibrary: Boolean,
    onOpenMenu: (ContentMenuTarget) -> Unit,
    onMenuAction: (ContentMenuTarget, ContentMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = ContentMenuTarget(
        media = media,
        progress = progress,
        origin = ContentMenuOrigin.CONTINUE_WATCHING,
    )
    val haptics = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val title = progress.episodeLabel ?: media.name
    val percent = (progress.fraction * 100).roundToInt()
    val description = stringResource(R.string.media_card_description_progress, title, percent)
    val remainingMillis = (progress.durationMillis - progress.positionMillis).coerceAtLeast(0)
    val hours = remainingMillis / 3_600_000
    val minutes = (remainingMillis % 3_600_000) / 60_000
    val timeLeft = when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min"
        else -> null
    }
    Box(
        modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(MobileTokens.radiusResume))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                role = Role.Button,
                onClick = { onMedia(media) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenMenu(target)
                },
            )
            .semantics {
                contentDescription = description
                customActions = target.menuActions().map { action ->
                    CustomAccessibilityAction(action.menuLabel(target, inLibrary, context)) {
                        onMenuAction(target, action)
                        true
                    }
                }
            },
    ) {
        MediaArtwork(media, Modifier.fillMaxSize(), preferBackdrop = true)
        IconButton(
            onClick = { onOpenMenu(target) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50)),
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.content_menu_more),
                tint = Color.White,
            )
        }
        if (progress.completed) {
            SelectionCheckmark(
                selected = true,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        timeLeft?.let { left ->
            Text(
                text = stringResource(R.string.continue_watching_time_left, left),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = 8.dp, bottom = 15.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(7.dp)
                .background(Color.Black.copy(alpha = 0.55f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.fraction)
                    .height(7.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun MobileHeroLoadingSkeleton() {
    val pulse by rememberInfiniteTransition(label = "hero loading").animateFloat(
        initialValue = 0.58f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton pulse",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)),
    )
}

@Composable
internal fun CatalogRow(
    section: CatalogSection,
    onMedia: (MediaPreview) -> Unit,
    onLoadMore: (String) -> Unit,
    onRetry: (String) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    progressByVideo: Map<String, WatchProgress>,
    completedVideoIds: Set<String>,
    inLibrary: (MediaPreview) -> Boolean,
    onOpenMenu: (ContentMenuTarget) -> Unit,
    onMenuAction: (ContentMenuTarget, ContentMenuAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(section.providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        section.errorMessage?.let { error ->
            Text(
                error,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (section.initialLoading && section.items.isEmpty()) {
            val pulse by rememberInfiniteTransition(label = "catalog row loading").animateFloat(
                initialValue = 0.58f,
                targetValue = 0.78f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "skeleton pulse",
            )
            MobileCatalogItemsLoadingSkeleton(
                skeletonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse),
                pulse = pulse,
            )
        } else if (section.items.isNotEmpty() || section.supportsSkip) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(section.items, key = MediaPreview::stableKey) { media ->
                    val menuTarget = ContentMenuTarget(media, progress = progressByVideo[media.id])
                    PosterCard(
                        media = media,
                        onMedia = onMedia,
                        modifier = Modifier.mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
                        menuTarget = menuTarget,
                        onOpenMenu = onOpenMenu,
                        onMenuAction = onMenuAction,
                        inLibrary = inLibrary(media),
                        completed = media.type == MediaType.MOVIE && media.id in completedVideoIds,
                    )
                }
                if (section.supportsSkip && (section.hasMore || section.loadingMore || section.loadMoreError != null)) {
                    item(key = "catalog-action") {
                        OutlinedButton(
                            onClick = {
                                if (section.loadMoreError != null) onRetry(section.id) else onLoadMore(section.id)
                            },
                            enabled = !section.loadingMore,
                            modifier = Modifier.sizeIn(minWidth = 132.dp, minHeight = 56.dp),
                        ) {
                            if (section.loadingMore) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(if (section.loadMoreError != null) R.string.retry else R.string.load_more))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(
    media: MediaPreview,
    onMedia: (MediaPreview) -> Unit,
    modifier: Modifier = Modifier,
    menuTarget: ContentMenuTarget? = null,
    onOpenMenu: ((ContentMenuTarget) -> Unit)? = null,
    onMenuAction: ((ContentMenuTarget, ContentMenuAction) -> Unit)? = null,
    inLibrary: Boolean = false,
    completed: Boolean = false,
) {
    val landscape = media.posterShape.equals("landscape", ignoreCase = true) ||
        (media.posterUrl.isNullOrBlank() && !media.backgroundUrl.isNullOrBlank())
    // Artwork carries the emotion: no name or metadata text on the card,
    // matching the TV design. The name stays available to TalkBack, and the
    // rating appears as a small neutral overlay per the design system.
    val ratingText = media.metadataPresentation().ratingText
    val cardDescription = ratingText
        ?.let { stringResource(R.string.media_card_description_rating, media.name, it) }
        ?: media.name
    val haptics = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // Direct accessibility actions mirror the sheet; when no action handler
    // is available, an explicit More action still reaches the menu so
    // long-press is never the only route (MOB-A11Y-03).
    val accessibilityActions = when {
        menuTarget != null && onMenuAction != null -> menuTarget.menuActions().map { action ->
            CustomAccessibilityAction(action.menuLabel(menuTarget, inLibrary, context)) {
                onMenuAction(menuTarget, action); true
            }
        }
        menuTarget != null && onOpenMenu != null -> listOf(
            CustomAccessibilityAction(stringResource(R.string.content_menu_more)) {
                onOpenMenu(menuTarget); true
            },
        )
        else -> emptyList()
    }
    Box(
        modifier
            .width(if (landscape) 220.dp else 138.dp)
            .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
            .clip(RoundedCornerShape(MobileTokens.radiusCard))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                role = Role.Button,
                onClick = { onMedia(media) },
                onLongClick = menuTarget?.let { target -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenMenu?.invoke(target)
                } },
            )
            .semantics {
                contentDescription = cardDescription
                customActions = accessibilityActions
            },
    ) {
        MediaArtwork(media, Modifier.fillMaxSize())
        if (menuTarget != null && onOpenMenu != null) {
            IconButton(
                onClick = { onOpenMenu(menuTarget) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50)),
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.content_menu_more),
                    tint = Color.White,
                )
            }
        }
        if (completed) {
            SelectionCheckmark(
                selected = true,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        ratingText?.let { rating ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("★", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(rating, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
internal fun MobileMetadataLine(
    presentation: com.lamphaus.app.ui.MediaMetadataPresentation,
    includeGenres: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val values = buildList {
        presentation.year?.let { add(it.toString()) }
        presentation.contentRating?.let(::add)
        presentation.runtimeMinutes?.let { add(stringResource(R.string.minutes_format, it)) }
        presentation.ratingText?.let { add(stringResource(R.string.rating_format, it)) }
        if (includeGenres && presentation.genres.isNotEmpty()) add(presentation.genres.joinToString(", "))
    }
    if (values.isNotEmpty()) {
        Text(
            text = values.joinToString(" · "),
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MediaGrid(
    media: List<MediaPreview>,
    onMedia: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    modifier: Modifier = Modifier,
    section: CatalogSection? = null,
    onLoadMore: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
    progressByVideo: Map<String, WatchProgress> = emptyMap(),
    completedVideoIds: Set<String> = emptySet(),
    inLibrary: (MediaPreview) -> Boolean = { false },
    onOpenMenu: ((ContentMenuTarget) -> Unit)? = null,
    onMenuAction: ((ContentMenuTarget, ContentMenuAction) -> Unit)? = null,
) {
    if (media.isEmpty() && section?.supportsSkip != true) {
        EmptyProviders(Modifier.padding(24.dp))
        return
    }
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(
            start = MobileTokens.spacingScreen,
            end = MobileTokens.spacingScreen,
            top = MobileTokens.spacingScreen,
            bottom = navBarClearancePadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(media, key = MediaPreview::stableKey) { item ->
            PosterCard(
                media = item,
                onMedia = onMedia,
                modifier = Modifier
                    .fillMaxWidth()
                    .mediaFocusRestore(item.stableKey, restoreMediaKey, onFocusRestored),
                menuTarget = ContentMenuTarget(item, progress = progressByVideo[item.id]),
                onOpenMenu = onOpenMenu,
                onMenuAction = onMenuAction,
                inLibrary = inLibrary(item),
                completed = item.type == MediaType.MOVIE && item.id in completedVideoIds,
            )
        }
        if (section?.supportsSkip == true && (section.hasMore || section.loadingMore || section.loadMoreError != null)) {
            item(key = "catalog-action") {
                OutlinedButton(
                    onClick = { if (section.loadMoreError != null) onRetry(section.id) else onLoadMore(section.id) },
                    enabled = !section.loadingMore,
                    modifier = Modifier.sizeIn(minWidth = 132.dp, minHeight = 56.dp),
                ) {
                    if (section.loadingMore) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(if (section.loadMoreError != null) R.string.retry else R.string.load_more))
                }
            }
        }
    }
}
