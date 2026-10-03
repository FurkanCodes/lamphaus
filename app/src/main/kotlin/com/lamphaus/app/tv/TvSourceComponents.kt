package com.lamphaus.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lamphaus.app.R
import com.lamphaus.app.ui.LocalSourceFit
import com.lamphaus.app.ui.LocalStreamBadges
import com.lamphaus.app.ui.StreamBadgeRow
import com.lamphaus.app.ui.sourceFitLabel
import com.lamphaus.app.ui.sourcePresentation
import com.lamphaus.app.ui.sourceQuality
import com.lamphaus.core.model.SourceFit
import com.lamphaus.core.model.StreamCandidate

/*
 * Source cards, quality tiles, and filter chips shared by the details-screen
 * source list and the player's Sources panel, so both read the same
 * (TV-FOC-01, TV-CNT-02, SHR-PROD-12).
 */

@Composable
internal fun TvSourceCard(
    source: StreamCandidate,
    providerLabel: String?,
    showProvider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** How it plays here; the player passes its own, the details screen asks [LocalSourceFit]. */
    fit: SourceFit? = LocalSourceFit.current.let { advisor -> remember(source, advisor) { advisor.fitFor(source) } },
    /** The source playing now, in the player's Sources panel: marked, and choosing it does nothing. */
    playing: Boolean = false,
) {
    val presentation = remember(source, providerLabel) { source.sourcePresentation(providerLabel) }
    val badgeMatcher = LocalStreamBadges.current
    val importedBadges = remember(source, badgeMatcher) { badgeMatcher.badgesFor(source) }
    val quality = remember(source) { sourceQuality(source) }
    // Provider names often carry their own line breaks and open with the
    // add-on's name, which the card and filters already show; the title keeps
    // only what tells this source apart, on one line.
    val title = remember(presentation, providerLabel) {
        val lines = presentation.title.lines().map(String::trim).filter(String::isNotEmpty)
        val distinct = if (lines.size > 1 && lines.first().equals(providerLabel, ignoreCase = true)) lines.drop(1) else lines
        distinct.joinToString("  ·  ")
    }
    val badges = remember(presentation, quality) {
        presentation.badges.filterNot { it.equals(quality, ignoreCase = true) }.take(4)
    }
    val transportLabel = stringResource(presentation.transport.labelRes)
    TvFocusableSurface(
        onClick = { if (!playing) onClick() },
        modifier = modifier.fillMaxWidth().semantics { selected = playing },
        containerColor = if (playing) TvSurfaceTokens.selectedFilter else TvSurfaceTokens.card,
    ) { focused ->
        val primaryColor = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground
        val secondaryColor = primaryColor.copy(alpha = 0.72f)
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TvQualityTile(quality = quality, fallback = transportLabel)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (playing) {
                    Text(
                        stringResource(R.string.player_source_playing).uppercase(),
                        color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                    )
                }
                if (showProvider && providerLabel != null) {
                    Text(
                        providerLabel.uppercase(),
                        color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    title,
                    color = primaryColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Imported badges replace Lamphaus's own labels, as in Nuvio.
                if (badgeMatcher.isActive) {
                    StreamBadgeRow(importedBadges)
                } else if (badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        badges.forEach { badge -> TvSourceBadge(badge, focused) }
                    }
                }
                presentation.description?.let { description ->
                    Text(
                        description,
                        color = secondaryColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    listOfNotNull(presentation.size, transportLabel).joinToString("  ·  "),
                    color = secondaryColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                fit?.let {
                    Text(
                        sourceFitLabel(it),
                        color = secondaryColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TvIcon(
                if (playing) Icons.Outlined.Check else Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = when {
                    focused -> primaryColor
                    playing -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
                modifier = Modifier.align(Alignment.CenterVertically).size(24.dp),
            )
        }
    }
}

/** Resolution at a glance: 4K gold, HD instrument blue, 720p teal, otherwise the transport. */
@Composable
internal fun TvQualityTile(quality: String?, fallback: String) {
    val (container, content) = when (quality) {
        "4K" -> Color(0xFFE9C46A) to Color(0xFF2B1F00)
        "1440p", "1080p" -> TvFocusTokens.beam to Color(0xFF003062)
        "720p" -> Color(0xFF9FD8C8) to Color(0xFF00382E)
        else -> Color.White.copy(alpha = 0.10f) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier.size(56.dp).background(container, TvShapeTokens.card),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            quality ?: fallback,
            color = content,
            style = if (quality != null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Placeholder cards in the final card geometry while the first add-on answers (TV-CNT-02). */
@Composable
internal fun TvSourceSkeletons() {
    val pulse = rememberSkeletonPulse(label = "source loading")
    val color = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(TvShapeTokens.card)
                    .background(TvSurfaceTokens.card)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(56.dp).clip(TvShapeTokens.card).skeletonPulseBackground(color) { pulse.value })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.3f).height(10.dp).skeletonPulseBackground(color) { pulse.value })
                    Box(Modifier.fillMaxWidth(0.8f).height(14.dp).skeletonPulseBackground(color) { pulse.value })
                    Box(Modifier.fillMaxWidth(0.55f).height(10.dp).skeletonPulseBackground(color) { pulse.value })
                }
            }
        }
    }
}

@Composable
internal fun TvFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        containerColor = if (selected) TvSurfaceTokens.selectedFilter else TvSurfaceTokens.card,
    ) { focused ->
        val color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            count?.let {
                Text(
                    it.toString(),
                    color = color.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}
@Composable
internal fun TvSourceBadge(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier = modifier
            .background(
                color = if (focused) {
                    TvFocusTokens.focusedContent.copy(alpha = 0.10f)
                } else {
                    TvFocusTokens.beam.copy(alpha = 0.16f)
                },
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}
