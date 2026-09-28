package com.lamphaus.app.mobile

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.app.ui.LocalStreamBadges
import com.lamphaus.app.ui.LocalSourceFit
import com.lamphaus.app.ui.sourceFitLabel
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.SourcePickerState
import com.lamphaus.app.ui.StreamBadgeRow
import com.lamphaus.app.ui.sourceItemKeys
import com.lamphaus.app.ui.sourcePresentation
import com.lamphaus.core.model.StreamCandidate

private val SourcesMaxWidth = 720.dp

/**
 * Sources, Nuvio-style: the title's backdrop and logo head the page, the
 * add-on filter stays pinned while the list scrolls, and sources appear as
 * each add-on answers (the rest keep loading underneath, never a blank
 * spinner). Each card leads with a quality tile, then the add-on's own
 * formatting, and a play button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MobileSourcePickerScreen(
    picker: SourcePickerState,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onProvider: (String?) -> Unit,
    onSource: (StreamCandidate) -> Unit,
) {
    val sources = picker.visibleSources
    val sourceKeys = remember(sources) { sourceItemKeys(sources) }
    // Every filter opens its list at the first source (a fresh state per
    // provider), and sources arriving above the first keep the list at the top.
    val listState = remember(picker.selectedProviderId) { LazyListState() }
    LaunchedEffect(sourceKeys.firstOrNull()) {
        if (listState.firstVisibleItemIndex <= 2) listState.scrollToItem(0)
    }
    val stillLoading = picker.loading || picker.pendingProviderCount > 0

    Box(Modifier.fillMaxSize().background(MobileTokens.ink)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header", contentType = "header") { SourcesHeader(picker) }
            if (picker.providerIds.size > 1) {
                stickyHeader(key = "filters", contentType = "filters") {
                    // Once pinned, the bar clears the status bar.
                    val stuck by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex >= 1 } }
                    ProviderFilters(picker, onProvider, stuck)
                }
            }
            picker.failures.values.forEach { error ->
                item(key = "failure:$error", contentType = "failure") {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.constrainedSources(),
                    )
                }
            }
            itemsIndexed(sources, key = { index, _ -> sourceKeys[index] }, contentType = { _, _ -> "source" }) { _, source ->
                SourceCard(
                    source = source,
                    providerLabel = picker.providerLabels[source.providerId],
                    onClick = { onSource(source) },
                    modifier = Modifier.animateItem().constrainedSources(),
                )
            }
            if (stillLoading) {
                if (sources.isEmpty()) {
                    items(4, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
                        SourceSkeleton(Modifier.constrainedSources())
                    }
                }
                item(key = "pending", contentType = "pending") {
                    Text(
                        if (picker.pendingProviderCount > 0) {
                            pluralStringResource(
                                R.plurals.sources_still_loading,
                                picker.pendingProviderCount,
                                picker.pendingProviderCount,
                            )
                        } else {
                            stringResource(R.string.loading_sources)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MobileTokens.textMuted,
                        modifier = Modifier.constrainedSources().padding(vertical = 8.dp),
                    )
                }
            } else if (sources.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        stringResource(R.string.no_sources),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MobileTokens.textMuted,
                        modifier = Modifier.constrainedSources().padding(vertical = 24.dp),
                    )
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
                .size(48.dp),
        ) {
            Box(Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.42f), CircleShape))
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
    }
}

private fun Modifier.constrainedSources(): Modifier =
    fillMaxWidth()
        .wrapContentWidth()
        .widthIn(max = SourcesMaxWidth)
        .padding(horizontal = MobileTokens.spacingScreen)

@Composable
private fun SourcesHeader(picker: SourcePickerState) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        MediaArtwork(picker.media, Modifier.fillMaxSize(), preferBackdrop = true)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to MobileTokens.ink.copy(alpha = 0.6f),
                    0.25f to MobileTokens.ink.copy(alpha = 0.15f),
                    0.65f to MobileTokens.ink.copy(alpha = 0.7f),
                    1f to MobileTokens.ink,
                ),
            ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!picker.media.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = picker.media.logoUrl,
                    contentDescription = picker.media.name,
                    modifier = Modifier.fillMaxWidth(0.66f).heightIn(max = 84.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    picker.media.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            }
            val episodeLine = picker.episode?.let { episode ->
                listOfNotNull(
                    if (episode.season != null && episode.episode != null) {
                        stringResource(R.string.episode_format, episode.season!!, episode.episode!!)
                    } else {
                        null
                    },
                    episode.title.takeIf(String::isNotBlank),
                ).joinToString("  ·  ")
            }
            Text(
                episodeLine ?: stringResource(R.string.sources_choose),
                style = MaterialTheme.typography.labelLarge,
                color = MobileTokens.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProviderFilters(picker: SourcePickerState, onProvider: (String?) -> Unit, stuck: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MobileTokens.ink)
            .then(if (stuck) Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 48.dp) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = MobileTokens.spacingScreen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                FilterPill(
                    label = stringResource(R.string.all_sources),
                    count = picker.sources.size,
                    selected = picker.selectedProviderId == null,
                    onClick = { onProvider(null) },
                )
            }
            items(picker.providerIds, key = { it }) { providerId ->
                FilterPill(
                    label = picker.providerLabels[providerId] ?: providerId,
                    count = picker.sources.count { it.providerId == providerId },
                    selected = picker.selectedProviderId == providerId,
                    onClick = { onProvider(providerId) },
                )
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MobileTokens.textPrimary else MobileTokens.surfaceRaised)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.Black else MobileTokens.textPrimary,
            maxLines = 1,
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.Black.copy(alpha = 0.6f) else MobileTokens.textMuted,
        )
    }
}

/** The resolution a source advertises in its name or title, as a short tile label. */
internal fun sourceQuality(source: StreamCandidate): String? {
    val text = listOfNotNull(source.name, source.title, source.description, source.quality).joinToString(" ").lowercase()
    return when {
        Regex("""\b(2160p|4k|uhd)\b""").containsMatchIn(text) -> "4K"
        Regex("""\b1440p\b""").containsMatchIn(text) -> "1440p"
        Regex("""\b1080p\b""").containsMatchIn(text) -> "1080p"
        Regex("""\b720p\b""").containsMatchIn(text) -> "720p"
        Regex("""\b(480p|576p|sd)\b""").containsMatchIn(text) -> "SD"
        else -> null
    }
}

private fun qualityColors(quality: String?): Pair<Color, Color> = when (quality) {
    "4K" -> Color(0xFFE9C46A) to Color(0xFF2B1F00)
    "1440p", "1080p" -> Color(0xFFA8C8FF) to Color(0xFF002A5C)
    "720p" -> Color(0xFF9FD8C8) to Color(0xFF00382E)
    else -> MobileTokens.surface to MobileTokens.textMuted
}

@Composable
private fun SourceCard(
    source: StreamCandidate,
    providerLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = remember(source, providerLabel) { source.sourcePresentation(providerLabel) }
    val badgeMatcher = LocalStreamBadges.current
    val importedBadges = remember(source, badgeMatcher) { badgeMatcher.badgesFor(source) }
    val fitAdvisor = LocalSourceFit.current
    val fit = remember(source, fitAdvisor) { fitAdvisor.fitFor(source) }
    val quality = remember(source) { sourceQuality(source) }
    val (tileColor, tileInk) = qualityColors(quality)
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MobileTokens.surfaceRaised)
            .border(1.dp, MobileTokens.hairline, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(58.dp)
                .height(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                quality ?: stringResource(presentation.transport.labelRes).take(3).uppercase(),
                color = tileInk,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            providerLabel?.let {
                Text(
                    it.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                    color = MobileTokens.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badgeMatcher.isActive) {
                StreamBadgeRow(importedBadges)
            } else if (presentation.badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    presentation.badges.take(4).forEach { badge ->
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MobileTokens.textPrimary,
                            modifier = Modifier
                                .border(1.dp, MobileTokens.textMuted.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Text(
                presentation.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = if (presentation.usesProviderFormatting) 4 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            presentation.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MobileTokens.textMuted,
                    maxLines = if (presentation.usesProviderFormatting) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            presentation.size?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MobileTokens.textMuted)
            }
            fit?.let {
                Text(
                    sourceFitLabel(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MobileTokens.textMuted,
                )
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .background(MobileTokens.textPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = Color.Black, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SourceSkeleton(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "source skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MobileTokens.surfaceRaised)
            .padding(12.dp)
            .graphicsLayer { alpha = pulse },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(MobileTokens.surface))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(MobileTokens.surface))
            Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(MobileTokens.surface))
            Box(Modifier.fillMaxWidth(0.6f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(MobileTokens.surface))
        }
    }
}
