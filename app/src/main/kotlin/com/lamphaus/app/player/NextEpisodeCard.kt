package com.lamphaus.app.player

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.app.ui.SpoilerBlurLayer
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.hasAired

/**
 * Mobile next-episode card (MOB-CMP-08/09). Geometry-stable across its ready,
 * finding-source, failure, and unaired states; the 16:9 thumbnail honours
 * spoiler protection (MOB-GFX-03/04). Placement is adaptive: a full-width
 * card with safe margins in portrait and a 420dp bottom-end card on wide
 * screens, kept clear of player controls and system insets (PLY-IMM-03/04).
 * Advancing stays manual and reuses the current Media3 session (PLY-PIP-03).
 */
@Composable
internal fun NextEpisodeCard(
    episode: Episode,
    loading: Boolean,
    failureMessage: String?,
    blurArtwork: Boolean,
    wide: Boolean,
    showSkipCredits: Boolean,
    onPlayNext: () -> Unit,
    onSkipCredits: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aired = remember(episode) { episode.hasAired() }
    Card(
        modifier = modifier
            .then(if (wide) Modifier.widthIn(max = 420.dp) else Modifier.fillMaxWidth()),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PlayerBackground.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NextEpisodeThumbnail(
                    episode = episode,
                    blurArtwork = blurArtwork,
                    modifier = Modifier.size(width = 128.dp, height = 72.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.next_episode_up_next),
                        color = PlayerOnSurfaceMuted,
                        fontFamily = PlayerFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    episodeCode(episode)?.let { code ->
                        Text(
                            text = code,
                            color = PlayerOnSurfaceMuted,
                            fontFamily = PlayerFont,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = episode.title,
                        color = PlayerOnSurface,
                        fontFamily = PlayerFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 48dp touch target for the dismissal action (MOB-A11Y-04).
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.next_episode_close),
                        tint = PlayerOnSurface,
                    )
                }
            }
            NextEpisodeStatus(
                episode = episode,
                aired = aired,
                loading = loading,
                failureMessage = failureMessage,
                onRetry = onPlayNext,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showSkipCredits) {
                    OutlinedButton(onClick = onSkipCredits) {
                        Text(stringResource(R.string.next_episode_skip_credits))
                    }
                }
                Button(onClick = onPlayNext, enabled = aired && !loading) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.next_episode_play_next))
                }
            }
        }
    }
}

/** Localized season/episode code; null when the episode carries no numbering. */
@Composable
private fun episodeCode(episode: Episode): String? {
    val season = episode.season
    val number = episode.episode
    return when {
        season != null && number != null ->
            stringResource(R.string.episode_format, season, number)
        season != null -> stringResource(R.string.season_format, season)
        number != null -> stringResource(R.string.episode_number_format, number)
        else -> null
    }
}

/**
 * Inline state line: finding-source progress, source failure with retry, or
 * the unaired release status. The ready state adds nothing so the card never
 * reflows between swipes of playback state.
 */
@Composable
private fun NextEpisodeStatus(
    episode: Episode,
    aired: Boolean,
    loading: Boolean,
    failureMessage: String?,
    onRetry: () -> Unit,
) {
    when {
        loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.size(width = 72.dp, height = 3.dp).clip(RoundedCornerShape(50)),
                color = PlayerPrimary,
                trackColor = PlayerSurface,
            )
            Text(
                text = stringResource(R.string.next_episode_finding_source),
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                fontSize = 12.sp,
            )
        }
        failureMessage != null -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = failureMessage,
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(
                    stringResource(R.string.retry),
                    color = PlayerPrimary,
                    fontFamily = PlayerFont,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        !aired -> {
            val releaseDate = episode.releasedAtEpochMillis?.let {
                DateUtils.formatDateTime(LocalContext.current, it, DateUtils.FORMAT_SHOW_DATE)
            }
            Text(
                text = releaseDate
                    ?.let { stringResource(R.string.next_episode_unaired_with_date, it) }
                    ?: stringResource(R.string.next_episode_unaired),
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                fontSize = 12.sp,
            )
        }
    }
}

/** 16:9 thumbnail with the shared spoiler veil; null artwork keeps geometry. */
@Composable
private fun NextEpisodeThumbnail(
    episode: Episode,
    blurArtwork: Boolean,
    modifier: Modifier = Modifier,
) {
    SpoilerBlurLayer(
        hidden = blurArtwork,
        veilColor = PlayerSurface,
        semanticLabel = stringResource(R.string.spoiler_hidden),
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        veilContent = {},
        content = {
            if (episode.thumbnailUrl.isNullOrBlank()) {
                Box(Modifier.fillMaxSize().background(PlayerSurface))
            } else {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        },
    )
}
