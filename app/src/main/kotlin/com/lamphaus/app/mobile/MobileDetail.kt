package com.lamphaus.app.mobile

import androidx.annotation.StringRes
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.core.text.HtmlCompat
import com.lamphaus.app.ui.StreamBadgeRow
import com.lamphaus.app.ui.LocalStreamBadges
import com.lamphaus.app.ui.artworkImageUrl
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.outlined.MoreVert
import com.lamphaus.app.ui.ContentMenuTarget
import com.lamphaus.app.ui.ContentMenuOrigin
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.ArtworkEditorState
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.SelectionCheckmark
import com.lamphaus.app.ui.SpoilerBlurLayer
import com.lamphaus.app.ui.SpoilerContent
import com.lamphaus.app.ui.shouldBlur
import com.lamphaus.app.ui.metadataImdbScore
import com.lamphaus.core.model.RatingSourceScore
import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderResult
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.app.R
import com.lamphaus.app.ui.metadataPresentation
import com.lamphaus.app.ui.numberParts
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.app.ui.sourcePresentation
import com.lamphaus.app.ui.sourceItemKeys

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileArtworkEditorScreen(
    editor: ArtworkEditorState,
    onBack: () -> Unit,
    onPosterSelected: (ArtworkAsset?) -> Unit,
    onBackdropSelected: (ArtworkAsset?) -> Unit,
    onLogoSelected: (ArtworkAsset?) -> Unit,
    onProviderSelected: (ArtworkProviderId?) -> Unit,
    onSave: () -> Unit,
) {
    if (editor.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.edit_artwork),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            item {
                if (!editor.media.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = editor.media.logoUrl,
                        contentDescription = editor.media.name,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                    )
                } else {
                    Text(editor.media.name, style = MaterialTheme.typography.headlineSmall)
                }
            }
            item {
                Text(
                    text = stringResource(R.string.artwork_choose),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editor.availableProviders.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MobileFilterChip(
                            selected = editor.providerFilter == null,
                            onClick = { onProviderSelected(null) },
                            label = { Text(stringResource(R.string.artwork_all_sources)) },
                        )
                        editor.availableProviders.forEach { provider ->
                            MobileFilterChip(
                                selected = editor.providerFilter == provider,
                                onClick = { onProviderSelected(provider) },
                                label = { Text(provider.value) },
                            )
                        }
                    }
                }
            }
            editor.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            editor.candidates?.providerResults
                ?.takeIf { results -> results.any { it.status != ArtworkLookupStatus.SUCCESS } }
                ?.let { results ->
                    item { MobileArtworkProviderMessages(results) }
                }
            item { Text(stringResource(R.string.artwork_logos), style = MaterialTheme.typography.titleLarge) }
            item {
                val logos = editor.filteredLogos
                if (logos.isEmpty()) {
                    MobileArtworkEmptyMessage(editor, stringResource(R.string.artwork_logo_kind))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(logos, key = { "${it.provider}:${it.reference}" }) { asset ->
                            val selected = editor.selectedLogo == asset
                            Card(
                                modifier = Modifier
                                    .width(228.dp)
                                    .height(96.dp)
                                    .clickable { onLogoSelected(asset) }
                                    .semantics { this.selected = selected },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = artworkImageUrl(asset, "w500"),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                                                RoundedCornerShape(8.dp),
                                            )
                                            .padding(10.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                    SelectionCheckmark(
                                        selected = selected,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    )
                                    Text(
                                        text = asset.provider.value,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.artwork_posters), style = MaterialTheme.typography.titleLarge) }
            item {
                val posters = editor.filteredPosters
                if (posters.isEmpty()) {
                    MobileArtworkEmptyMessage(editor, stringResource(R.string.artwork_poster_kind))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(posters, key = { "${it.provider}:${it.reference}" }) { asset ->
                            val selected = editor.selectedPoster == asset
                            Card(
                                modifier = Modifier
                                    .width(112.dp)
                                    .height(168.dp)
                                    .clickable { onPosterSelected(asset) }
                                    .semantics { this.selected = selected },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = artworkImageUrl(asset, "w342"),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                    SelectionCheckmark(
                                        selected = selected,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    )
                                    Text(
                                        text = asset.provider.value,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.artwork_backdrops), style = MaterialTheme.typography.titleLarge) }
            item {
                val backdrops = editor.filteredBackdrops
                if (backdrops.isEmpty()) {
                    MobileArtworkEmptyMessage(editor, stringResource(R.string.artwork_backdrop_kind))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(backdrops, key = { "${it.provider}:${it.reference}" }) { asset ->
                            val selected = editor.selectedBackdrop == asset
                            Card(
                                modifier = Modifier
                                    .width(228.dp)
                                    .height(128.dp)
                                    .clickable { onBackdropSelected(asset) }
                                    .semantics { this.selected = selected },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = artworkImageUrl(asset, "w780"),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                    SelectionCheckmark(
                                        selected = selected,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    )
                                    Text(
                                        text = asset.provider.value,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onSave,
                    enabled = editor.selectedPoster != null ||
                        editor.selectedBackdrop != null ||
                        editor.selectedLogo != null,
                ) {
                    Text(stringResource(R.string.save_artwork))
                }
            }
        }
    }
}

@Composable
private fun MobileArtworkEmptyMessage(editor: ArtworkEditorState, artworkKind: String) {
    val candidates = editor.candidates
    val noCandidatesFromAnyProvider = candidates != null &&
        candidates.posters.isEmpty() &&
        candidates.backdrops.isEmpty() &&
        candidates.logos.isEmpty()
    val text = editor.providerFilter?.let { provider ->
        stringResource(
            R.string.artwork_no_source_candidates,
            artworkKind,
            provider.value,
        )
    } ?: if (noCandidatesFromAnyProvider) {
        stringResource(R.string.artwork_no_connected_candidates)
    } else {
        stringResource(R.string.artwork_no_candidates)
    }
    Text(text)
}

@Composable
private fun MobileArtworkProviderMessages(results: List<ArtworkProviderResult>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        results.filter { it.status != ArtworkLookupStatus.SUCCESS }.forEach { result ->
            val providerName = result.displayName
            val text = when (result.status) {
                ArtworkLookupStatus.INVALID_KEY ->
                    stringResource(R.string.artwork_provider_invalid_key, providerName)
                ArtworkLookupStatus.MISSING_EXTERNAL_ID ->
                    stringResource(R.string.artwork_provider_missing_external_id, providerName)
                ArtworkLookupStatus.LOOKUP_FAILED ->
                    stringResource(R.string.artwork_provider_lookup_failed, providerName)
                ArtworkLookupStatus.NO_MATCH ->
                    stringResource(R.string.artwork_provider_no_match, providerName)
                ArtworkLookupStatus.SUCCESS -> null
            }
            text?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileSourcePickerScreen(
    picker: com.lamphaus.app.ui.SourcePickerState,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onProvider: (String?) -> Unit,
    onSource: (StreamCandidate) -> Unit,
) {
    if (picker.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!picker.media.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = picker.media.logoUrl,
                        contentDescription = picker.media.name,
                        modifier = Modifier.height(52.dp).fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(picker.media.name, style = MaterialTheme.typography.headlineSmall)
                }
                picker.episode?.title?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    MobileFilterChip(
                        selected = picker.selectedProviderId == null,
                        onClick = { onProvider(null) },
                        label = { Text(stringResource(R.string.all_sources)) },
                    )
                }
                items(picker.providerIds, key = { it }) { providerId ->
                    MobileFilterChip(
                        selected = picker.selectedProviderId == providerId,
                        onClick = { onProvider(providerId) },
                        label = { Text(picker.providerLabels[providerId] ?: providerId) },
                    )
                }
            }
            picker.failures.values.forEach { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (picker.loading) {
                Text(stringResource(R.string.loading_sources), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (picker.visibleSources.isEmpty()) {
                Text(stringResource(R.string.no_sources), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val sourceKeys = remember(picker.visibleSources) { sourceItemKeys(picker.visibleSources) }
                // Every filter opens its list at the first source; a fresh state per
                // provider stops the keyed list from jumping to the old anchor.
                val listState = remember(picker.selectedProviderId) { LazyListState() }
                // Slower providers can add sources above the first one; unless the
                // viewer has scrolled away from the top, keep the list at the start.
                LaunchedEffect(sourceKeys.firstOrNull()) {
                    if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        picker.visibleSources,
                        key = { index, _ -> sourceKeys[index] },
                    ) { _, source ->
                        val providerLabel = picker.providerLabels[source.providerId]
                        val presentation = remember(source, providerLabel) {
                            source.sourcePresentation(providerLabel)
                        }
                        val badgeMatcher = LocalStreamBadges.current
                        val importedBadges = remember(source, badgeMatcher) { badgeMatcher.badgesFor(source) }
                        Card(
                            onClick = { onSource(source) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    if (badgeMatcher.isActive) {
                                        StreamBadgeRow(importedBadges)
                                    } else if (presentation.badges.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            presentation.badges.forEach { badge ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(4.dp),
                                                ) {
                                                    Text(
                                                        badge,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        presentation.title,
                                        maxLines = if (presentation.usesProviderFormatting) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    presentation.description?.let { description ->
                                        Text(
                                            description,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = if (presentation.usesProviderFormatting) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (!presentation.usesProviderFormatting) {
                                        Text(
                                            buildList {
                                                providerLabel?.let(::add)
                                                presentation.size?.let(::add)
                                                add(stringResource(presentation.transport.labelRes))
                                            }.distinct().joinToString("  ·  "),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                                Icon(Icons.Outlined.PlayArrow, stringResource(R.string.play))
                            }
                        }
                    }
                }
            }
        }
    }
    if (widthSizeClass == WindowWidthSizeClass.Expanded) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.35f).fillMaxHeight()) {
                MediaArtwork(picker.media, Modifier.fillMaxSize(), preferBackdrop = true)
            }
            Box(Modifier.weight(0.65f).fillMaxHeight()) { content() }
        }
    } else {
        Box(Modifier.fillMaxSize()) { content() }
    }
}
