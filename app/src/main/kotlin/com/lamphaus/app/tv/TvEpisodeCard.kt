package com.lamphaus.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.app.ui.episodeAirDateText
import com.lamphaus.app.ui.ContentMenuOrigin
import com.lamphaus.app.ui.ContentMenuTarget
import com.lamphaus.app.ui.NextUpKind
import com.lamphaus.app.ui.SelectionCheckmark
import com.lamphaus.app.ui.SpoilerBlurLayer
import com.lamphaus.app.ui.SpoilerContent
import com.lamphaus.app.ui.isResumable
import com.lamphaus.app.ui.numberParts
import com.lamphaus.app.ui.shouldBlur
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.WatchProgress

/**
 * An episode in the details row (TV-TOK-01 landscape card).
 *
 * The card stays dark when focused and signals focus with the approved
 * outline, scale and halo (TV-FOC-02); only its text band turns into a solid
 * focused-content surface, so labels never sit on a half-transparent wash
 * (about 11:1 contrast focused, 9:1 unfocused). The synopsis moves to
 * [TvEpisodeDetailLine] below the row, where it can be read in full.
 */
@Composable
internal fun TvEpisodeCard(
    media: MediaPreview,
    episode: Episode,
    watched: Boolean,
    progress: WatchProgress?,
    spoilerProtection: SpoilerProtectionSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set when this is the series' next episode to play; it is never hidden. */
    nextUpKind: NextUpKind? = null,
    onFocused: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val cardFocus = remember { FocusRequester() }
    val menuRequest = LocalTvContentMenuEnvironment.current.onRequest
    val currentMenuRequest by rememberUpdatedState(menuRequest)
    val menuTarget = remember(media, episode, progress) {
        ContentMenuTarget(media = media, progress = progress, episode = episode, origin = ContentMenuOrigin.EPISODE)
    }
    val holdTracker = remember(scope, cardFocus, menuTarget) {
        SelectHoldTracker(scope) { currentMenuRequest?.invoke(menuTarget, cardFocus) }
    }.takeIf { menuRequest != null }
    val number = episode.numberParts()
    // The next episode to play is shown in full; spoiler protection is for
    // the episodes beyond it.
    val artworkHidden = nextUpKind == null && spoilerProtection.shouldBlur(SpoilerContent.EPISODE_ARTWORK, watched)
    val resumeFraction = progress?.takeIf(WatchProgress::isResumable)?.fraction?.coerceIn(0f, 1f)
    val watchedDescription = if (watched) stringResource(R.string.watched) else ""

    TvFocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .then(modifier)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvSelectHoldMenu(holdTracker)
            .focusRequester(cardFocus)
            .size(TvLayoutTokens.landscapeCardWidth, TvLayoutTokens.landscapeCardHeight)
            .semantics { stateDescription = watchedDescription },
        containerColor = TvSurfaceTokens.elevated,
        focusedContainerColor = TvSurfaceTokens.elevated,
    ) { focused ->
        Box(Modifier.fillMaxSize()) {
            val thumbnail = episode.thumbnailUrl?.takeIf(String::isNotBlank)
            if (thumbnail != null) {
                SpoilerBlurLayer(
                    hidden = artworkHidden,
                    veilColor = TvSurfaceTokens.elevated,
                    semanticLabel = stringResource(R.string.spoiler_hidden),
                    modifier = Modifier.fillMaxSize(),
                    // Light veil: the blurred artwork keeps its colours.
                    veilOpacity = 0.35f,
                    veilContent = {
                        TvIcon(
                            icon = Icons.Outlined.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(20.dp),
                        )
                    },
                    content = {
                        AsyncImage(
                            model = thumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    },
                )
            } else {
                // No episode artwork: a large episode number instead of the
                // series backdrop repeated on every card.
                Text(
                    text = number.episode?.let { "E$it" } ?: episode.title.take(1),
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 36.dp),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }

            nextUpKind?.let { kind ->
                Text(
                    text = stringResource(if (kind == NextUpKind.RESUME) R.string.resume else R.string.up_next),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(MaterialTheme.colorScheme.primary, TvShapeTokens.card)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SelectionCheckmark(
                selected = watched,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            )

            val labelColor = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurfaceVariant
            val titleColor = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurface
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        if (focused) {
                            Brush.verticalGradient(listOf(TvFocusTokens.focusedContainer, TvFocusTokens.focusedContainer))
                        } else {
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.28f to TvSurfaceTokens.elevated.copy(alpha = 0.94f),
                                1f to TvSurfaceTokens.elevated,
                            )
                        },
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = if (focused) 8.dp else 18.dp,
                        bottom = 8.dp,
                    ),
                ) {
                    if (number.season != null && number.episode != null) {
                        Text(
                            stringResource(R.string.episode_format, number.season, number.episode),
                            color = labelColor,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text(
                        episode.title,
                        color = titleColor,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                resumeFraction?.let { fraction ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                if (focused) TvFocusTokens.focusedContent.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.44f),
                            ),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .background(if (focused) MaterialTheme.colorScheme.onPrimary else TvFocusTokens.beam),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Details of the focused (or next) episode below the row: full title, air
 * date, progress and a readable synopsis, which a 256dp card cannot hold.
 * Reserves its height so moving along the row never shifts the screen.
 */
@Composable
internal fun TvEpisodeDetailLine(
    episode: Episode,
    watched: Boolean,
    progress: WatchProgress?,
    synopsisHidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val number = episode.numberParts()
    val heading = if (number.season != null && number.episode != null) {
        stringResource(R.string.episode_format, number.season, number.episode) + " · " + episode.title
    } else {
        episode.title
    }
    // Unaired: "Airs in 3 days"; aired: the date (Nuvio's air-date badge).
    val airDate = episodeAirDateText(episode.releasedAtEpochMillis)
    val minutesLeft = progress?.takeIf(WatchProgress::isResumable)?.let { row ->
        ((row.durationMillis - row.positionMillis).coerceAtLeast(0) / 60_000).toInt().coerceAtLeast(1)
    }
    val meta = listOfNotNull(
        airDate,
        minutesLeft?.let { pluralStringResource(R.plurals.episode_minutes_left, it, it) },
        stringResource(R.string.watched).takeIf { watched },
    ).joinToString("  ·  ")

    Column(
        modifier = modifier.widthIn(max = 760.dp).heightIn(min = 118.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            heading,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (synopsisHidden) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvIcon(
                    icon = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.synopsis_hidden),
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            episode.overview?.takeIf(String::isNotBlank)?.let { overview ->
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
