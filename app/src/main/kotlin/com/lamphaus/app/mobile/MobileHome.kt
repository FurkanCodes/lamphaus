package com.lamphaus.app.mobile

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

import com.lamphaus.app.ui.MediaArtwork
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import kotlin.math.roundToInt
import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.app.ui.HOME_CATALOG_SCROLL_SETTLE_MILLIS
import com.lamphaus.app.ui.isResumable
import com.lamphaus.app.ui.shouldPrefetchHomeCatalogBatch
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.app.R
import com.lamphaus.app.ui.mediaFocusRestore
import com.lamphaus.app.ui.metadataPresentation

@Composable
internal fun MobileHomeScreen(
    state: AppUiState,
    onMedia: (MediaPreview) -> Unit,
    onAddSource: () -> Unit,
    onSettings: () -> Unit,
    onLoadMore: (String) -> Unit,
    onRetry: (String) -> Unit,
    onLoadMoreHome: () -> Unit,
    onRetryHome: () -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
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
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(MobileTokens.sectionGap),
    ) {
        if (heroItems.isNotEmpty()) {
            item(key = "feature") {
                MobileHeroCarousel(heroItems, onMedia, onSettings, restoreMediaKey, onFocusRestored)
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
                MobileContinueWatchingRow(continueWatching, onMedia)
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
            CatalogRow(section, onMedia, onLoadMore, onRetry, restoreMediaKey, onFocusRestored)
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
    onSettings: () -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    val pagerState = rememberPagerState { items.size }
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
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
            ) {
                MediaArtwork(media, Modifier.fillMaxSize(), preferBackdrop = true)
                // Top scrim keeps status-bar icons legible on any artwork; the
                // bottom scrim carries the title block like the TV hero.
                // Ink wash behind the title block so text reads on any artwork.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to MobileTokens.ink),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                    0.16f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.88f),
                                ),
                            ),
                        ),
                )
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text(media.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    MobileMetadataLine(
                        presentation = media.metadataPresentation(maxGenres = 1),
                        includeGenres = true,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                    media.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.76f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // Manual paging only: nothing auto-advances while the user is reading.
        Row(
            Modifier.align(Alignment.BottomEnd).padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { index ->
                val active = index == pagerState.currentPage
                val width by animateDpAsState(targetValue = if (active) 18.dp else 6.dp, label = "hero indicator")
                Box(
                    Modifier
                        .size(width, 6.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.45f)),
                )
            }
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings_and_profiles),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun MobileContinueWatchingRow(
    items: List<Pair<MediaPreview, WatchProgress>>,
    onMedia: (MediaPreview) -> Unit,
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
                MobileContinueWatchingCard(media, progress, onMedia)
            }
        }
    }
}

@Composable
private fun MobileContinueWatchingCard(
    media: MediaPreview,
    progress: WatchProgress,
    onMedia: (MediaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            .clickable(role = Role.Button) { onMedia(media) }
            .semantics { contentDescription = description },
    ) {
        MediaArtwork(media, Modifier.fillMaxSize(), preferBackdrop = true)
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
                    PosterCard(
                        media = media,
                        onMedia = onMedia,
                        modifier = Modifier.mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
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

@Composable
private fun PosterCard(media: MediaPreview, onMedia: (MediaPreview) -> Unit, modifier: Modifier = Modifier) {
    val landscape = media.posterShape.equals("landscape", ignoreCase = true) ||
        (media.posterUrl.isNullOrBlank() && !media.backgroundUrl.isNullOrBlank())
    // Artwork carries the emotion: no name or metadata text on the card,
    // matching the TV design. The name stays available to TalkBack, and the
    // rating appears as a small neutral overlay per the design system.
    val ratingText = media.metadataPresentation().ratingText
    val cardDescription = ratingText
        ?.let { stringResource(R.string.media_card_description_rating, media.name, it) }
        ?: media.name
    Box(
        modifier
            .width(if (landscape) 220.dp else 138.dp)
            .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
            .clip(RoundedCornerShape(MobileTokens.radiusCard))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button) { onMedia(media) }
            .semantics { contentDescription = cardDescription },
    ) {
        MediaArtwork(media, Modifier.fillMaxSize())
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
) {
    if (media.isEmpty() && section?.supportsSkip != true) {
        EmptyProviders(Modifier.padding(24.dp))
        return
    }
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(MobileTokens.spacingScreen),
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
