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
import com.lamphaus.app.ui.sourceItemKey

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MobileDetailScreen(
    detail: MediaDetail?,
    expanded: Boolean,
    inLibrary: Boolean,
    watchedEpisodeIds: Set<String>,
    spoilerProtection: SpoilerProtectionSettings,
    resumeProgress: WatchProgress?,
    onBack: () -> Unit,
    onPlay: (com.lamphaus.core.model.Episode?) -> Unit,
    onLibrary: () -> Unit,
    onEditArtwork: () -> Unit,
    progress: List<WatchProgress>,
    onOpenMenu: (ContentMenuTarget) -> Unit,
) {
    if (detail == null) return
    val artworkResolver = LocalArtworkResolver.current
    val resolvedPreview = remember(detail.preview, artworkResolver) {
        artworkResolver.resolve(detail.preview).media
    }
    val detailMenuTarget = ContentMenuTarget(
        media = detail.preview,
        progress = progress.firstOrNull { it.videoId == detail.preview.id },
        origin = ContentMenuOrigin.DETAIL,
    )

    val seasons = remember(detail) { detail.episodes.mapNotNull { it.season }.distinct().sorted() }
    var selectedSeason by rememberSaveable(detail.preview.stableKey) { mutableStateOf(seasons.firstOrNull()) }
    val info: @Composable () -> Unit = {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!resolvedPreview.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = resolvedPreview.logoUrl,
                    contentDescription = resolvedPreview.name,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                )
            }
            MobileMetadataLine(
                presentation = detail.metadataPresentation(),
                includeGenres = true,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                ratings = listOfNotNull(
                    metadataImdbScore(
                        detail.preview.rating,
                        detail.preview.ratingSource,
                        stringResource(R.string.source_imdb),
                    ),
                ),
            )
            detail.preview.description
                ?.takeIf(String::isNotBlank)
                ?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            MobileDetailActions(
                inLibrary = inLibrary,
                resumeProgress = resumeProgress,
                resumeEpisode = detail.episodes.firstOrNull { it.id == resumeProgress?.videoId },
                onPlay = onPlay,
                onLibrary = onLibrary,
                onEditArtwork = onEditArtwork,
                onOpenMenu = { onOpenMenu(detailMenuTarget) },
            )
            val fullGenres = detail.preview.metadataPresentation(maxGenres = Int.MAX_VALUE).genres
            if (fullGenres.isNotEmpty()) {
                MobileDetailMetadataSection(R.string.genres, fullGenres.joinToString(", "))
            }
            if (detail.cast.isNotEmpty()) {
                MobilePersonChips(labelRes = R.string.cast, names = detail.cast)
            }
            if (detail.directors.isNotEmpty()) {
                MobilePersonChips(labelRes = R.string.directors, names = detail.directors)
            }
        }
    }
    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            MediaArtwork(
                detail.preview,
                Modifier.fillMaxHeight().weight(0.44f),
                preferBackdrop = true,
            )
            LazyColumn(Modifier.weight(0.56f).statusBarsPadding()) {
                item { info() }
                items(detail.episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        media = detail.preview,
                        episode = episode,
                        watched = episode.id in watchedEpisodeIds,
                        spoilerProtection = spoilerProtection,
                        progress = progress.firstOrNull { it.videoId == episode.id },
                        onPlay = onPlay,
                        onOpenMenu = onOpenMenu,
                    )
                }
            }
        }
    } else {
        val visibleEpisodes = remember(detail, selectedSeason) {
            detail.episodes.filter { selectedSeason == null || it.season == selectedSeason }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "hero") {
                MobileDetailHero(
                    detail = detail,
                    preview = resolvedPreview,
                    resumeProgress = resumeProgress,
                    resumeEpisode = detail.episodes.firstOrNull { it.id == resumeProgress?.videoId },
                    inLibrary = inLibrary,
                    onPlay = onPlay,
                    onLibrary = onLibrary,
                    onEditArtwork = onEditArtwork,
                    onOpenMenu = { onOpenMenu(detailMenuTarget) },
                )
            }
            detail.preview.description
                ?.takeIf(String::isNotBlank)
                ?.let { overview ->
                    item(key = "overview") {
                        Text(
                            overview,
                            Modifier.padding(horizontal = MobileTokens.spacingScreen),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            if (seasons.size > 1) {
                item(key = "seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = MobileTokens.spacingScreen),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasons, key = { it }) { season ->
                            MobileFilterChip(
                                selected = selectedSeason == season,
                                onClick = { selectedSeason = season },
                                label = { Text(stringResource(R.string.season_format, season)) },
                            )
                        }
                    }
                }
            }
            items(visibleEpisodes, key = { it.id }) { episode ->
                Box(Modifier.padding(horizontal = MobileTokens.spacingScreen)) {
                    EpisodeRow(
                        media = detail.preview,
                        episode = episode,
                        watched = episode.id in watchedEpisodeIds,
                        spoilerProtection = spoilerProtection,
                        progress = progress.firstOrNull { it.videoId == episode.id },
                        onPlay = onPlay,
                        onOpenMenu = onOpenMenu,
                    )
                }
            }
            item(key = "metadata") {
                Column(
                    Modifier.padding(horizontal = MobileTokens.spacingScreen),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val fullGenres = detail.preview.metadataPresentation(maxGenres = Int.MAX_VALUE).genres
                    if (fullGenres.isNotEmpty()) {
                        MobileDetailMetadataSection(R.string.genres, fullGenres.joinToString(", "))
                    }
                    if (detail.cast.isNotEmpty()) {
                        MobilePersonChips(labelRes = R.string.cast, names = detail.cast)
                    }
                    if (detail.directors.isNotEmpty()) {
                        MobilePersonChips(labelRes = R.string.directors, names = detail.directors)
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileDetailHero(
    detail: MediaDetail,
    preview: MediaPreview,
    resumeProgress: WatchProgress?,
    resumeEpisode: com.lamphaus.core.model.Episode?,
    inLibrary: Boolean,
    onPlay: (com.lamphaus.core.model.Episode?) -> Unit,
    onLibrary: () -> Unit,
    onEditArtwork: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(480.dp)) {
        MediaArtwork(preview, Modifier.fillMaxSize(), preferBackdrop = true)
        // Top scrim keeps status-bar icons legible; the bottom ink wash carries
        // the title block on any artwork.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MobileTokens.ink.copy(alpha = 0.6f),
                        0.25f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to MobileTokens.ink),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = MobileTokens.spacingScreen, vertical = 20.dp),
        ) {
            if (!preview.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = preview.logoUrl,
                    contentDescription = preview.name,
                    modifier = Modifier.fillMaxWidth(0.72f).heightIn(max = 56.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                )
            }
            MobileMetadataLine(
                presentation = preview.metadataPresentation(),
                includeGenres = false,
                color = MobileTokens.textMuted,
                ratings = listOfNotNull(
                    metadataImdbScore(
                        preview.rating,
                        preview.ratingSource,
                        stringResource(R.string.source_imdb),
                    ),
                ),
            )
            resumeProgress?.let { progress ->
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.fraction)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            MobileDetailActions(
                inLibrary = inLibrary,
                resumeProgress = resumeProgress,
                resumeEpisode = resumeEpisode,
                onPlay = onPlay,
                onLibrary = onLibrary,
                onEditArtwork = onEditArtwork,
                onOpenMenu = onOpenMenu,
            )
        }
    }
}

@Composable
internal fun MobileDetailActions(
    inLibrary: Boolean,
    resumeProgress: WatchProgress?,
    resumeEpisode: com.lamphaus.core.model.Episode?,
    onPlay: (com.lamphaus.core.model.Episode?) -> Unit,
    onLibrary: () -> Unit,
    onEditArtwork: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { onPlay(resumeEpisode) },
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MobileTokens.textPrimary,
                contentColor = Color.Black,
            ),
        ) {
            Icon(Icons.Outlined.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    resumeProgress == null -> stringResource(R.string.play)
                    resumeProgress.episodeLabel != null ->
                        stringResource(R.string.resume_episode_format, resumeProgress.episodeLabel ?: "")
                    else -> stringResource(R.string.resume)
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (inLibrary) {
                SelectionCheckmark(
                    selected = true,
                    selectedContainerColor = MobileTokens.accent,
                    selectedContentColor = Color.Black,
                )
            } else {
                IconButton(
                    onClick = onLibrary,
                    modifier = Modifier
                        .size(44.dp)
                        .background(MobileTokens.surfaceRaised.copy(alpha = 0.68f), CircleShape),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.add_to_library),
                        tint = Color.White,
                    )
                }
            }
        }
        IconButton(
            onClick = onEditArtwork,
            modifier = Modifier
                .size(44.dp)
                .background(MobileTokens.surfaceRaised.copy(alpha = 0.68f), CircleShape),
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.edit_artwork),
                tint = Color.White,
            )
        }
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier
                .size(48.dp)
                .background(MobileTokens.surfaceRaised.copy(alpha = 0.68f), CircleShape),
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.content_menu_more),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun MobileSpoilerBadge() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.spoiler_hidden),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

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

@Composable
private fun MobileDetailMetadataSection(@StringRes labelRes: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MobileTokens.textMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun plainPersonName(value: String): String =
    HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MobilePersonChips(@StringRes labelRes: Int, names: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MobileTokens.textMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            names.forEach { name ->
                Text(
                    text = plainPersonName(name),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MobileTokens.surfaceRaised)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EpisodeRow(
    episode: com.lamphaus.core.model.Episode,
    media: MediaPreview,
    watched: Boolean,
    spoilerProtection: SpoilerProtectionSettings,
    progress: WatchProgress?,
    onPlay: (com.lamphaus.core.model.Episode?) -> Unit,
    onOpenMenu: (ContentMenuTarget) -> Unit,
) {
    val number = episode.numberParts()
    val artworkHidden = spoilerProtection.shouldBlur(SpoilerContent.EPISODE_ARTWORK, watched)
    val synopsisHidden = spoilerProtection.shouldBlur(SpoilerContent.EPISODE_SYNOPSIS, watched)
    val watchedDescription = if (watched) stringResource(R.string.watched) else ""
    val numberLabel = when {
        number.season != null && number.episode != null ->
            stringResource(R.string.episode_format, number.season, number.episode)
        number.season != null -> stringResource(R.string.season_format, number.season)
        number.episode != null -> stringResource(R.string.episode_number_format, number.episode)
        else -> ""
    }
    val airDate = episode.releasedAtEpochMillis?.let {
        DateUtils.formatDateTime(LocalContext.current, it, DateUtils.FORMAT_SHOW_DATE)
    }
    val episodeLine = when {
        numberLabel.isNotEmpty() && airDate != null -> "$numberLabel · $airDate"
        numberLabel.isNotEmpty() -> numberLabel
        else -> airDate ?: ""
    }
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MobileTokens.radiusCard))
            .combinedClickable(
                role = Role.Button,
                onClick = { onPlay(episode) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenMenu(
                        ContentMenuTarget(
                            media = media,
                            episode = episode,
                            progress = progress,
                            origin = ContentMenuOrigin.EPISODE,
                        ),
                    )
                },
            )
            .semantics { stateDescription = watchedDescription }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(112.dp)
                .height(63.dp)
                .clip(RoundedCornerShape(MobileTokens.radiusCard))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!episode.thumbnailUrl.isNullOrBlank()) {
                SpoilerBlurLayer(
                    hidden = artworkHidden,
                    veilColor = MobileTokens.surface,
                    semanticLabel = stringResource(R.string.spoiler_hidden),
                    modifier = Modifier.fillMaxSize(),
                    veilContent = { MobileSpoilerBadge() },
                    content = {
                        AsyncImage(
                            model = episode.thumbnailUrl,
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    },
                )
            }
            SelectionCheckmark(
                selected = watched,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .graphicsLayer { alpha = if (watched) 0.55f else 1f },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (episodeLine.isNotEmpty()) {
                Text(
                    episodeLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                episode.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (synopsisHidden) {
                Text(
                    stringResource(R.string.synopsis_hidden),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                episode.overview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
            if (picker.visibleSources.isEmpty()) {
                Text(stringResource(R.string.no_sources), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        picker.visibleSources,
                        key = { index, source -> sourceItemKey(source, index) },
                    ) { _, source ->
                        val providerLabel = picker.providerLabels[source.providerId]
                        val presentation = remember(source, providerLabel) {
                            source.sourcePresentation(providerLabel)
                        }
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
                                    if (presentation.badges.isNotEmpty()) {
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
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    presentation.description?.let { description ->
                                        Text(
                                            description,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
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
