package com.lamphaus.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lamphaus.app.R
import com.lamphaus.app.ui.episodeAirDateText
import com.lamphaus.app.ui.upNextEpisodeLabel
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.hasAired

/** The episode picked in the player's list while its source is being found. */
internal data class EpisodeSwitchState(
    val episodeId: String,
    val failed: Boolean = false,
)

/**
 * The series' episodes inside the player (Nuvio's episodes side panel,
 * PLY-EPS-01). Picking one starts it on the closest source as a fresh
 * playback; the list stays open with the search status until it starts.
 * Unaired episodes are listed with their countdown but cannot be picked.
 */
@Composable
internal fun PlayerEpisodesPanel(
    episodes: List<Episode>,
    currentVideoId: String,
    switch: EpisodeSwitchState?,
    isTelevision: Boolean,
    onSelect: (Episode) -> Unit,
    onClose: () -> Unit,
) {
    val currentIndex = episodes.indexOfFirst { it.id == currentVideoId }.coerceAtLeast(0)
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isTelevision) runCatching { currentFocus.requestFocus() } }
    PlayerOverlayLayout(
        title = stringResource(R.string.player_episodes),
        isTelevision = isTelevision,
        onClose = onClose,
        tvWidth = 724.dp,
        tvScrim = true,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = if (isTelevision) 420.dp else 560.dp),
            state = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0)),
        ) {
            itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
                val playing = episode.id == currentVideoId
                val aired = remember(episode) { episode.hasAired() }
                val status = when {
                    playing -> stringResource(R.string.player_source_playing)
                    switch?.episodeId == episode.id && switch.failed -> stringResource(R.string.episode_source_unavailable)
                    switch?.episodeId == episode.id -> stringResource(R.string.next_episode_finding_source)
                    else -> episodeAirDateText(episode.releasedAtEpochMillis)
                }
                PlayerChoiceRow(
                    title = upNextEpisodeLabel(episode),
                    supportingText = status,
                    selected = playing,
                    enabled = aired || playing,
                    modifier = Modifier
                        .then(if (index == currentIndex) Modifier.focusRequester(currentFocus) else Modifier)
                        .semantics { if (switch?.episodeId == episode.id) liveRegion = LiveRegionMode.Polite },
                ) { onSelect(episode) }
            }
        }
    }
}
