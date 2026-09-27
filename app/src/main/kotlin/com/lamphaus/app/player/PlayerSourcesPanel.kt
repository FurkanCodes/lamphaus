package com.lamphaus.app.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lamphaus.app.R
import com.lamphaus.app.mobile.sourceQuality
import com.lamphaus.app.ui.sourcePresentation
import com.lamphaus.core.model.StreamCandidate

/** A source the player can switch to: the add-on's stream and its resolved playable address. */
internal data class PlayerSourceOption(
    val stream: StreamCandidate,
    val url: String,
    val providerName: String?,
) {
    val key: String get() = listOf(stream.providerId, url).joinToString("|")
}

/** Sources for the title playing now, filled in as each add-on answers. */
internal data class PlayerSourcesState(
    val loading: Boolean = false,
    val options: List<PlayerSourceOption> = emptyList(),
    val pendingProviders: Int = 0,
    val failed: Boolean = false,
)

/**
 * In-player source switcher (Nuvio's stream side panel): the sources for the
 * current movie or episode over the video, the playing one marked, each row
 * leading with its quality and add-on. Choosing one continues at the same
 * position in the same session (PLY-IMM-04). Loading never blocks the list:
 * rows arrive per add-on while the rest keep loading.
 */
@Composable
internal fun PlayerSourcesPanel(
    state: PlayerSourcesState,
    currentUri: String,
    isTelevision: Boolean,
    onSelect: (PlayerSourceOption) -> Unit,
    onClose: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val playingIndex = state.options.indexOfFirst { it.url == currentUri }
    LaunchedEffect(state.options.isNotEmpty()) {
        if (isTelevision && state.options.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }
    PlayerOverlayLayout(
        title = stringResource(R.string.player_sources),
        isTelevision = isTelevision,
        onClose = onClose,
        tvWidth = 724.dp,
        tvScrim = true,
    ) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = if (isTelevision) 420.dp else 560.dp)) {
            itemsIndexed(state.options, key = { _, option -> option.key }) { index, option ->
                val presentation = remember(option) { option.stream.sourcePresentation(option.providerName) }
                val quality = remember(option) { sourceQuality(option.stream) }
                val playing = index == playingIndex
                val headline = listOfNotNull(quality, option.providerName).joinToString("  ·  ")
                    .ifBlank { presentation.title }
                val detail = listOfNotNull(
                    presentation.title.takeIf { it != headline },
                    presentation.description,
                    presentation.size,
                    if (playing) stringResource(R.string.player_source_playing) else null,
                ).joinToString("\n").takeIf(String::isNotBlank)
                PlayerChoiceRow(
                    title = headline,
                    supportingText = detail,
                    selected = playing,
                    modifier = if (index == (playingIndex.takeIf { it >= 0 } ?: 0)) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { if (!playing) onSelect(option) },
                )
            }
            item(key = "status") {
                when {
                    state.loading || state.pendingProviders > 0 -> androidx.compose.foundation.layout.Row(
                        Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.padding(2.dp).heightIn(max = 18.dp), color = PlayerPrimary, strokeWidth = 2.dp)
                        Text(
                            if (state.pendingProviders > 0) {
                                pluralStringResource(R.plurals.sources_still_loading, state.pendingProviders, state.pendingProviders)
                            } else {
                                stringResource(R.string.loading_sources)
                            },
                            color = PlayerOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.options.isEmpty() -> Text(
                        stringResource(R.string.no_sources),
                        color = PlayerOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
