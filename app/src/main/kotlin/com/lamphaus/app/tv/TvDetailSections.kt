package com.lamphaus.app.tv

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.core.model.MediaFacts
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.PersonCredit
import com.lamphaus.core.model.RatingSourceScore
import java.util.Locale

/**
 * Detail enrichment sections (TV-CNT-01 order: episodes → cast → similar →
 * ratings → facts). Sections render only when their data exists; a section
 * with no Select action is never focusable (TV-FOC-01).
 */

private val PersonTileShape = RoundedCornerShape(8.dp)

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
        style = MaterialTheme.typography.titleMedium,
    )
}

/** Cast rail with portraits. Display-only: Select performs no action here. */
@Composable
internal fun TvPeopleRail(cast: List<PersonCredit>) {
    Column(verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.sectionTitleSpacing)) {
        SectionTitle(stringResource(R.string.cast))
        LazyRow(
            contentPadding = PaddingValues(
                start = TvLayoutTokens.screenHorizontalPadding,
                end = TvLayoutTokens.screenHorizontalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
        ) {
            items(cast, key = { credit -> "${credit.personId ?: credit.name}|${credit.role.orEmpty()}" }) { credit ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 180.dp)
                            .clip(PersonTileShape)
                            .background(TvSurfaceTokens.card),
                    ) {
                        if (credit.profileUrl != null) {
                            AsyncImage(
                                model = credit.profileUrl,
                                contentDescription = null,
                                modifier = Modifier.size(width = 120.dp, height = 180.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = credit.name.take(1).uppercase(),
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = credit.name,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(120.dp),
                    )
                    credit.role?.takeIf(String::isNotBlank)?.let { role ->
                        Text(
                            text = role,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Recommended titles; every card opens the matching detail destination. */
@Composable
internal fun TvSimilarRail(
    similar: List<MediaPreview>,
    onOpenMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.sectionTitleSpacing)) {
        SectionTitle(stringResource(R.string.similar_title))
        LazyRow(
            contentPadding = PaddingValues(
                start = TvLayoutTokens.screenHorizontalPadding,
                end = TvLayoutTokens.screenHorizontalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
        ) {
            items(similar, key = MediaPreview::stableKey) { media ->
                TvMediaCard(
                    media = media,
                    onClick = { onOpenMedia(media) },
                    onFocused = { onFocused(media) },
                    showLabel = true,
                    revealLabelOnFocus = true,
                )
            }
        }
    }
}

@Composable
internal fun TvRatingsSection(
    ratings: List<RatingSourceScore>,
    onSelect: (RatingSourceScore) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.sectionTitleSpacing)) {
        SectionTitle(stringResource(R.string.ratings_title))
        LazyRow(
            contentPadding = PaddingValues(
                start = TvLayoutTokens.screenHorizontalPadding,
                end = TvLayoutTokens.screenHorizontalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
        ) {
            items(ratings, key = RatingSourceScore::sourceId) { rating ->
                // Two 412dp cards + 20dp gap fill the 844dp safe width exactly.
                val valueText = "%.1f".format(Locale.ROOT, rating.normalizedTen)
                val description = stringResource(R.string.rating_source_description, rating.displayName, valueText)
                TvFocusableSurface(
                    onClick = { onSelect(rating) },
                    modifier = Modifier
                        .width(412.dp)
                        .height(100.dp)
                        .semantics { contentDescription = description },
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = rating.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (focused) {
                                TvFocusTokens.focusedContent
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = valueText,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (focused) {
                                TvFocusTokens.focusedContent
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Select affordance for a rating card: value, scale, votes, attribution and
 * data freshness. Never gated behind Back (TV-NAV-04): the dialog carries its
 * own visible Close action.
 */
@Composable
internal fun TvRatingDetailsDialog(
    rating: RatingSourceScore,
    fetchedAtEpochMillis: Long,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(640.dp),
            colors = SurfaceDefaults.colors(containerColor = TvSurfaceTokens.elevated),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = rating.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                )
                val valueText = if (rating.scale == 10.0) {
                    "%.1f / 10".format(Locale.ROOT, rating.value)
                } else {
                    "%.0f%%".format(Locale.ROOT, rating.normalizedTen * 10.0)
                }
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                rating.voteCount?.let { votes ->
                    Text(
                        text = stringResource(
                            R.string.rating_votes_count,
                            "%,d".format(Locale.ROOT, votes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.rating_attribution, rating.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.rating_last_updated,
                        DateUtils.getRelativeTimeSpanString(fetchedAtEpochMillis).toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvAction(
                        label = stringResource(R.string.close),
                        icon = Icons.Outlined.Close,
                        modifier = Modifier.focusRequester(closeFocus),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

/** Production facts that provider addons do not carry. Display-only. */
@Composable
internal fun TvFactsSection(facts: MediaFacts) {
    val rows = buildList {
        facts.status?.let { add(stringResource(R.string.facts_status) to it) }
        facts.originalLanguage?.let { language ->
            Locale(language).displayLanguage.takeIf(String::isNotBlank)?.let {
                add(stringResource(R.string.facts_language) to it)
            }
        }
        facts.budgetUsd?.takeIf { it > 0 }?.let {
            add(stringResource(R.string.facts_budget) to "$%,d".format(Locale.US, it))
        }
        facts.revenueUsd?.takeIf { it > 0 }?.let {
            add(stringResource(R.string.facts_revenue) to "$%,d".format(Locale.US, it))
        }
    }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.sectionTitleSpacing)) {
        SectionTitle(stringResource(R.string.facts_title))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(180.dp),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

/** Row-level recovery: the failure never replaces usable content (SHR-PROD-04). */
@Composable
internal fun TvInlineError(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.detail_enrichment_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TvAction(
            label = stringResource(R.string.retry),
            icon = Icons.Outlined.Refresh,
            onClick = onRetry,
        )
    }
}
