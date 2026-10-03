package com.lamphaus.app.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lamphaus.app.R
import com.lamphaus.app.mobile.FilterPill
import com.lamphaus.app.mobile.SourceCard
import com.lamphaus.app.mobile.SourceSkeleton
import com.lamphaus.app.tv.LamphausTvTheme
import com.lamphaus.app.tv.TvFilterChip
import com.lamphaus.app.tv.TvSourceCard
import com.lamphaus.app.tv.TvSourceSkeletons
import com.lamphaus.core.model.SourceFit
import com.lamphaus.core.model.StreamCandidate

/** A source the player can switch to: the add-on's stream and its resolved playable address. */
internal data class PlayerSourceOption(
    val stream: StreamCandidate,
    val url: String,
    val providerName: String?,
    /** How it will play on this device; null when there is nothing worth saying. */
    val fit: SourceFit? = null,
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

/** One add-on tab: its sources, in add-on order. A null label is the episode's own (built-in) streams. */
internal data class PlayerSourceGroup(
    val id: String,
    val label: String?,
    val options: List<PlayerSourceOption>,
)

/** Groups sources by add-on in the order the add-ons first answered. */
internal fun List<PlayerSourceOption>.sourceGroups(): List<PlayerSourceGroup> =
    groupBy { it.stream.providerId }
        .map { (id, options) -> PlayerSourceGroup(id, options.first().providerName, options) }

/**
 * In-player source switcher, drawn like the details screen's source list so
 * the two read the same: a counted heading, add-on tabs with their counts,
 * and the same quality-led cards with the fit line (SHR-PROD-12). The
 * playing source is marked and takes focus on TV; switching continues at the
 * same position (PLY-IMM-04). Rows arrive per add-on while the rest load,
 * and never reorder under the viewer (TV-CNT-02, MOB-CMP-09).
 */
@Composable
internal fun PlayerSourcesPanel(
    state: PlayerSourcesState,
    currentUri: String,
    isTelevision: Boolean,
    onSelect: (PlayerSourceOption) -> Unit,
    onClose: () -> Unit,
) {
    val groups = remember(state.options) { state.options.sourceGroups() }
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
    val visible = selectedGroup?.options ?: state.options
    val showProvider = selectedGroup == null && groups.size > 1
    val builtInLabel = stringResource(R.string.player_sources_built_in)
    val content = PlayerSourcesContent(
        state = state,
        groups = groups,
        selectedGroupId = selectedGroup?.id,
        visible = visible,
        showProvider = showProvider,
        currentUri = currentUri,
        groupLabel = { it.label ?: builtInLabel },
        onGroup = { selectedGroupId = it },
        onSelect = onSelect,
    )
    PlayerOverlayLayout(
        title = stringResource(R.string.player_sources),
        isTelevision = isTelevision,
        onClose = onClose,
        tvWidth = 844.dp,
        tvScrim = true,
    ) {
        if (isTelevision) {
            // The TV source components are the details screen's, in its theme.
            LamphausTvTheme { TvPlayerSources(content) }
        } else {
            MobilePlayerSources(content)
        }
    }
}

private class PlayerSourcesContent(
    val state: PlayerSourcesState,
    val groups: List<PlayerSourceGroup>,
    val selectedGroupId: String?,
    val visible: List<PlayerSourceOption>,
    val showProvider: Boolean,
    val currentUri: String,
    val groupLabel: (PlayerSourceGroup) -> String,
    val onGroup: (String?) -> Unit,
    val onSelect: (PlayerSourceOption) -> Unit,
) {
    val stillLoading: Boolean get() = state.loading || state.pendingProviders > 0
    fun isPlaying(option: PlayerSourceOption): Boolean = option.url == currentUri
}

@Composable
private fun ColumnScope.TvPlayerSources(content: PlayerSourcesContent) {
    val state = content.state
    val playingFocus = remember { FocusRequester() }
    val firstChipFocus = remember { FocusRequester() }
    // Each tab opens at its first source; a fresh state per tab stops the
    // keyed list from jumping back to the previous tab's anchor.
    val listState = remember(content.selectedGroupId) { LazyListState() }
    val playingIndex = content.visible.indexOfFirst(content::isPlaying)
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(content.visible.isNotEmpty()) {
        if (initialFocusDone || content.visible.isEmpty()) return@LaunchedEffect
        initialFocusDone = true
        // Open on the playing source, scrolled into view and focused (TV-FOC-01).
        listState.scrollToItem(playingIndex.coerceAtLeast(0))
        withFrameNanos { }
        runCatching { playingFocus.requestFocus() }
    }
    LaunchedEffect(Unit) {
        // Nothing to focus yet: hold focus on the tabs so Back and D-pad work while sources load.
        withFrameNanos { }
        if (!initialFocusDone && content.groups.size > 1) runCatching { firstChipFocus.requestFocus() }
    }
    SourcesCountLine(content)
    if (content.groups.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item("all") {
                TvFilterChip(
                    label = stringResource(R.string.all_sources),
                    count = state.options.size,
                    selected = content.selectedGroupId == null,
                    onClick = { content.onGroup(null) },
                    modifier = Modifier.focusRequester(firstChipFocus),
                )
            }
            items(content.groups, key = { it.id }) { group ->
                TvFilterChip(
                    label = content.groupLabel(group),
                    count = group.options.size,
                    selected = content.selectedGroupId == group.id,
                    onClick = { content.onGroup(group.id) },
                )
            }
        }
    }
    SourcesFailure(content)
    when {
        content.visible.isEmpty() && content.stillLoading -> TvSourceSkeletons()
        content.visible.isEmpty() -> SourcesEmpty()
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(content.visible, key = { _, option -> option.key }) { index, option ->
                TvSourceCard(
                    source = option.stream,
                    providerLabel = option.providerName,
                    showProvider = content.showProvider,
                    onClick = { content.onSelect(option) },
                    fit = option.fit,
                    playing = content.isPlaying(option),
                    modifier = if (index == playingIndex.coerceAtLeast(0)) {
                        Modifier.focusRequester(playingFocus)
                    } else {
                        Modifier
                    },
                )
            }
            // Last, so it appears and leaves without moving a source the viewer is on.
            if (state.pendingProviders > 0) item("pending") { SourcesPendingLine(state.pendingProviders) }
        }
    }
}

@Composable
private fun ColumnScope.MobilePlayerSources(content: PlayerSourcesContent) {
    val state = content.state
    val listState = remember(content.selectedGroupId) { LazyListState() }
    SourcesCountLine(content)
    if (content.groups.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item("all") {
                FilterPill(
                    label = stringResource(R.string.all_sources),
                    count = state.options.size,
                    selected = content.selectedGroupId == null,
                    onClick = { content.onGroup(null) },
                )
            }
            items(content.groups, key = { it.id }) { group ->
                FilterPill(
                    label = content.groupLabel(group),
                    count = group.options.size,
                    selected = content.selectedGroupId == group.id,
                    onClick = { content.onGroup(group.id) },
                )
            }
        }
    }
    SourcesFailure(content)
    when {
        content.visible.isEmpty() && content.stillLoading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { SourceSkeleton(Modifier.fillMaxWidth()) }
        }
        content.visible.isEmpty() -> SourcesEmpty()
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(content.visible, key = { it.key }) { option ->
                SourceCard(
                    source = option.stream,
                    providerLabel = option.providerName,
                    onClick = { content.onSelect(option) },
                    modifier = Modifier.fillMaxWidth(),
                    fit = option.fit,
                    showProvider = content.showProvider,
                    playing = content.isPlaying(option),
                )
            }
            if (state.pendingProviders > 0) item("pending") { SourcesPendingLine(state.pendingProviders) }
        }
    }
}

@Composable
private fun SourcesCountLine(content: PlayerSourcesContent) {
    val count = content.state.options.size
    Text(
        text = when {
            count > 0 -> pluralStringResource(R.plurals.sources_count, count, count)
            content.stillLoading -> stringResource(R.string.loading_sources)
            else -> stringResource(R.string.no_sources)
        },
        color = PlayerOnSurfaceMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SourcesFailure(content: PlayerSourcesContent) {
    if (content.state.failed && content.state.options.isEmpty()) {
        Text(
            stringResource(R.string.player_sources_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SourcesEmpty() {
    Text(
        stringResource(R.string.no_sources),
        color = PlayerOnSurfaceMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun SourcesPendingLine(pending: Int) {
    Text(
        pluralStringResource(R.plurals.sources_still_loading, pending, pending),
        color = PlayerOnSurfaceMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
