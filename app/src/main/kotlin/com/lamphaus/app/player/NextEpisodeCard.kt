package com.lamphaus.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.app.ui.releaseCountdownText
import com.lamphaus.app.ui.ReleaseKind
import com.lamphaus.app.ui.SpoilerBlurLayer
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.hasAired

/** Where the next-episode hand-off stands; drives the card's status line. */
internal sealed interface NextEpisodeProgress {
    data object Idle : NextEpisodeProgress
    data object Searching : NextEpisodeProgress
    data class Starting(val sourceName: String, val secondsLeft: Int) : NextEpisodeProgress
}

private val CardShape = RoundedCornerShape(14.dp)
private val CardContainer = Color(0xE3191919)

/**
 * Nuvio's next-episode card: thumbnail, "Next episode", the episode code and
 * title on one line, a status line, and a Play pill. The whole card plays;
 * status moves from finding a source to "Playing via … in 3s" without
 * changing geometry (MOB-CMP-09, TV-CNT-02). TV takes the approved focus
 * outline (TV-FOC-02); mobile adds a 48dp close action (MOB-A11Y-04).
 */
@Composable
internal fun NextEpisodeCard(
    episode: Episode,
    progress: NextEpisodeProgress,
    failureMessage: String?,
    blurArtwork: Boolean,
    isTelevision: Boolean,
    wide: Boolean,
    showSkipCredits: Boolean,
    onPlayNext: () -> Unit,
    onSkipCredits: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val aired = remember(episode) { episode.hasAired() }
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .then(if (wide || isTelevision) Modifier.width(420.dp) else Modifier.fillMaxWidth())
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clip(CardShape)
            .background(CardContainer)
            .border(
                width = if (focused && isTelevision) 3.dp else 1.dp,
                color = if (focused && isTelevision) PlayerPrimary else Color.White.copy(alpha = 0.16f),
                shape = CardShape,
            )
            .clickable(enabled = aired, role = Role.Button, onClick = onPlayNext),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = if (isTelevision) 10.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NextEpisodeThumbnail(episode, blurArtwork, Modifier.size(width = 112.dp, height = 64.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.next_episode_label),
                    color = PlayerOnSurface.copy(alpha = 0.8f),
                    fontFamily = PlayerFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = episodeDisplayLabel(episode),
                    color = PlayerOnSurface,
                    fontFamily = PlayerFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                statusText(episode, aired, progress, failureMessage)?.let { status ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = status,
                        color = PlayerOnSurfaceMuted,
                        fontFamily = PlayerFont,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            PlayPill(aired, Modifier.padding(start = 8.dp))
            if (!isTelevision) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.next_episode_close),
                        tint = PlayerOnSurfaceMuted,
                    )
                }
            }
        }
        if (showSkipCredits) {
            OutlinedButton(
                onClick = onSkipCredits,
                modifier = Modifier.padding(start = 10.dp, bottom = 9.dp).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.next_episode_skip_credits), fontFamily = PlayerFont)
            }
        }
    }
}

@Composable
private fun PlayPill(aired: Boolean, modifier: Modifier = Modifier) {
    val tint = if (aired) PlayerOnSurface else PlayerOnSurface.copy(alpha = 0.7f)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(if (aired) R.string.next_episode_play else R.string.next_episode_unaired),
            color = tint,
            fontFamily = PlayerFont,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

@Composable
private fun statusText(
    episode: Episode,
    aired: Boolean,
    progress: NextEpisodeProgress,
    failureMessage: String?,
): String? = when {
    !aired -> releaseCountdownText(episode.releasedAtEpochMillis, ReleaseKind.EPISODE)
    progress is NextEpisodeProgress.Searching -> stringResource(R.string.next_episode_finding_source)
    progress is NextEpisodeProgress.Starting ->
        stringResource(R.string.next_episode_playing_via, progress.sourceName, progress.secondsLeft)
    failureMessage != null -> failureMessage
    else -> null
}

/** "S1 · E2 • Title", or the bare title when the episode carries no numbering. */
@Composable
private fun episodeDisplayLabel(episode: Episode): String {
    val season = episode.season
    val number = episode.episode
    val code = when {
        season != null && number != null -> stringResource(R.string.episode_format, season, number)
        number != null -> stringResource(R.string.episode_number_format, number)
        else -> null
    }
    return listOfNotNull(code, episode.title.takeIf(String::isNotBlank)).joinToString(" • ")
}

/** 16:9-ish thumbnail with Nuvio's bottom shade and the shared spoiler veil. */
@Composable
private fun NextEpisodeThumbnail(episode: Episode, blurArtwork: Boolean, modifier: Modifier = Modifier) {
    SpoilerBlurLayer(
        hidden = blurArtwork,
        veilColor = PlayerSurface,
        semanticLabel = stringResource(R.string.spoiler_hidden),
        modifier = modifier.clip(RoundedCornerShape(9.dp)),
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
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f))),
                ),
            )
        },
    )
}
