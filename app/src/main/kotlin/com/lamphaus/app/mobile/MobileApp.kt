package com.lamphaus.app.mobile

import androidx.activity.compose.BackHandler
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

import androidx.core.text.HtmlCompat
import com.lamphaus.app.ui.artworkImageUrl
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.ArtworkEditorState
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import kotlin.math.roundToInt
import com.lamphaus.app.ui.ArtworkResolver
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.AppViewModel
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.app.ui.SelectionCheckmark
import com.lamphaus.app.ui.SpoilerBlurLayer
import com.lamphaus.app.ui.SpoilerContent
import com.lamphaus.app.ui.shouldBlur
import com.lamphaus.app.ui.HOME_CATALOG_SCROLL_SETTLE_MILLIS
import com.lamphaus.app.ui.isResumable
import com.lamphaus.app.ui.shouldPrefetchHomeCatalogBatch
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderResult
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.PairedDevice
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.ProfileKind
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.app.R
import com.lamphaus.app.ui.mediaFocusRestore
import com.lamphaus.app.ui.metadataPresentation
import com.lamphaus.app.ui.numberParts
import com.lamphaus.app.ui.sourcePresentation
import com.lamphaus.app.ui.sourceItemKey
private enum class MobileDestination(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME(R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
    DISCOVER(R.string.discover, Icons.Filled.Explore, Icons.Outlined.Explore),
    LIBRARY(R.string.library, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    SEARCH(R.string.search, Icons.Filled.Search, Icons.Outlined.Search),
}

@Composable
fun MobileApp(
    viewModel: AppViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onGoogleSignIn: () -> Unit,
    onEmailLink: (String) -> Unit,
    onPlay: (PlaybackRequest) -> Unit,
    onExternalPlay: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LamphausMobileTheme {
        val artworkResolver = remember(state.artworkOverrides) {
            ArtworkResolver(state.artworkOverrides.associateBy { it.mediaKey })
        }
        CompositionLocalProvider(LocalArtworkResolver provides artworkResolver) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                viewModel.dismissMessage()
            }
        }
        LaunchedEffect(state.playbackRequest) {
            state.playbackRequest?.let {
                onPlay(it)
                viewModel.playbackLaunchHandled()
            }
        }
        LaunchedEffect(state.externalPlaybackUrl) {
            state.externalPlaybackUrl?.let {
                onExternalPlay(it)
                viewModel.playbackLaunchHandled()
            }
        }
        LaunchedEffect(state.configurationUrl) {
            state.configurationUrl?.let {
                onExternalPlay(it)
                viewModel.configurationLaunchHandled()
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = state.account,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "account",
                ) { account ->
                    when (account) {
                        AccountState.Loading -> LoadingScreen()
                        AccountState.SignedOut -> MobileSignInScreen(
                            cloudConfigured = com.lamphaus.app.BuildConfig.CLOUD_CONFIGURED,
                            onGoogleSignIn = onGoogleSignIn,
                            onEmailLink = onEmailLink,
                            onDevelopmentSession = viewModel::openDevelopmentSession,
                        )
                        is AccountState.SignedIn -> MobileSignedInApp(state, viewModel, widthSizeClass)
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_lamphaus_foreground),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.titleLarge,
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .width(160.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(50)),
            )
            Text(
                text = stringResource(R.string.loading_library),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun MobileSignInScreen(
    cloudConfigured: Boolean,
    onGoogleSignIn: () -> Unit,
    onEmailLink: (String) -> Unit,
    onDevelopmentSession: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.sign_in_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGoogleSignIn, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.continue_with_google))
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email_address)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { onEmailLink(email) },
            enabled = '@' in email,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.email_sign_in_link))
        }
        if (!cloudConfigured) {
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.cloud_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDevelopmentSession) { Text(stringResource(R.string.open_development_session)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSignedInApp(
    state: AppUiState,
    viewModel: AppViewModel,
    widthSizeClass: WindowWidthSizeClass,
) {
    var destination by rememberSaveable { mutableStateOf(MobileDestination.HOME) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var pendingMediaKey by remember { mutableStateOf<String?>(null) }
    val openMedia: (MediaPreview) -> Unit = { media ->
        pendingMediaKey = media.stableKey
        viewModel.loadDetail(media)
    }
    val watchedEpisodeIds = remember(state.progress) {
        state.progress.asSequence()
            .filter { it.completed }
            .map { it.videoId }
            .toSet()
    }

    val completedMovieKeys = remember(state.progress) {
        state.progress.asSequence()
            .filter { it.completed }
            .map { it.mediaKey }
            .toSet()
    }

    when {
        state.sourcePicker != null -> {
            BackHandler { viewModel.closeSourcePicker() }
            MobileSourcePickerScreen(
                picker = state.sourcePicker,
                widthSizeClass = widthSizeClass,
                onBack = viewModel::closeSourcePicker,
                onProvider = viewModel::selectSourceProvider,
                onSource = viewModel::playSource,
            )
        }
        state.artworkEditor != null -> {
            BackHandler { viewModel.closeArtworkEditor() }
            MobileArtworkEditorScreen(
                editor = state.artworkEditor,
                onBack = viewModel::closeArtworkEditor,
                onPosterSelected = viewModel::selectArtworkPoster,
                onBackdropSelected = viewModel::selectArtworkBackdrop,
                onLogoSelected = viewModel::selectArtworkLogo,
                onProviderSelected = viewModel::selectArtworkProvider,
                onSave = viewModel::saveArtworkSelection,
            )
        }
        state.selectedDetail != null -> {
            BackHandler { viewModel.clearDetail() }
            MobileDetailScreen(
                detail = state.selectedDetail,
                expanded = widthSizeClass == WindowWidthSizeClass.Expanded,
                inLibrary = state.library.any { it.mediaKey == state.selectedDetail.preview.stableKey },
                watchedEpisodeIds = watchedEpisodeIds,
                spoilerProtection = state.spoilerProtection,
                onBack = viewModel::clearDetail,
                onPlay = { episode -> viewModel.openSources(state.selectedDetail.preview, episode) },
                onLibrary = { viewModel.addToLibrary(state.selectedDetail.preview) },
                onEditArtwork = { viewModel.openArtworkEditor(state.selectedDetail.preview) },
            )
        }
        settingsOpen -> {
            BackHandler { settingsOpen = false }
            MobileSettingsScreen(state, viewModel, onBack = { settingsOpen = false })
        }
        else -> {
            val compact = widthSizeClass == WindowWidthSizeClass.Compact
            val content: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = destination,
                        animationSpec = tween(180),
                        label = "tab",
                    ) { tab ->
                        when (tab) {
                            MobileDestination.HOME -> MobileHomeScreen(
                                state = state,
                                onMedia = openMedia,
                                onAddSource = { settingsOpen = true },
                                onSettings = { settingsOpen = true },
                                onLoadMore = viewModel::loadMoreCatalog,
                                onRetry = viewModel::retryCatalogPage,
                                onLoadMoreHome = viewModel::loadMoreHomeCatalogSections,
                                onRetryHome = viewModel::retryHomeCatalogSections,
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                            )

                            MobileDestination.DISCOVER -> DiscoverScreen(
                                state = state,
                                onMedia = openMedia,
                                onSettings = { settingsOpen = true },
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                            )
                            MobileDestination.LIBRARY -> LibraryScreen(
                                state = state,
                                onMedia = openMedia,
                                onSettings = { settingsOpen = true },
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                            )
                            MobileDestination.SEARCH -> SearchScreen(
                                state = state,
                                onSearch = viewModel::searchContent,
                                onBrowseType = viewModel::selectBrowseType,
                                onBrowseCatalog = viewModel::selectBrowseCatalog,
                                onBrowseGenre = viewModel::selectBrowseGenre,
                                onLoadMore = viewModel::loadMoreBrowse,
                                onRetry = viewModel::retryBrowse,
                                onCatalogLoadMore = viewModel::loadMoreCatalog,
                                onCatalogRetry = viewModel::retryCatalogPage,
                                onMedia = openMedia,
                                onSettings = { settingsOpen = true },
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                            )
                        }
                        }
                        if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding())
                    }
            }
            if (compact) {
                Scaffold(
                    bottomBar = { MobileNavBar(destination) { destination = it } },
                ) { outer -> Box(Modifier.padding(outer)) { content() } }
            } else {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        Spacer(Modifier.height(24.dp))
                        MobileDestination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Icon(if (destination == item) item.selectedIcon else item.icon, null) },
                            label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) { content() }
                }
            }
        }
    }
}

@Composable
private fun MobileNavBar(
    destination: MobileDestination,
    onSelect: (MobileDestination) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp)
            .background(MobileTokens.surface.copy(alpha = 0.92f), RoundedCornerShape(28.dp))
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MobileDestination.entries.forEach { item ->
            val isSelected = destination == item
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(role = Role.Tab) { onSelect(item) }
                    .semantics { if (isSelected) selected = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (isSelected) item.selectedIcon else item.icon,
                    contentDescription = stringResource(item.labelRes),
                    tint = if (isSelected) MobileTokens.accent else MobileTokens.textMuted,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(item.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MobileTokens.accent else MobileTokens.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MobileScreenHeader(
    title: String,
    showSettings: Boolean,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 24.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
            modifier = Modifier.weight(1f),
        )
        if (showSettings) {
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.settings_and_profiles),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MobileFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MobileTokens.surfaceRaised,
            labelColor = MobileTokens.textMuted,
            selectedContainerColor = MobileTokens.accent.copy(alpha = 0.18f),
            selectedLabelColor = MobileTokens.accent,
        ),
    )
}

@Composable
private fun DiscoverScreen(
    state: AppUiState,
    onMedia: (MediaPreview) -> Unit,
    onSettings: () -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MobileScreenHeader(stringResource(R.string.discover), showSettings = true, onSettings = onSettings)
        Box(Modifier.weight(1f)) {
            MediaGrid(
                media = state.allMedia,
                onMedia = onMedia,
                restoreMediaKey = restoreMediaKey,
                onFocusRestored = onFocusRestored,
            )
        }
    }
}

@Composable
private fun MobileHomeScreen(
    state: AppUiState,
    onMedia: (MediaPreview) -> Unit,
    onAddSource: () -> Unit,
    onSettings: () -> Unit,
    onLoadMore: (String) -> Unit,
    onRetry: (String) -> Unit,
    onLoadMoreHome: () -> Unit,
    onRetryHome: () -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    val allMedia = state.allMedia
    // Same feature selection as the TV home: first five distinct catalog items.
    val heroItems = remember(allMedia) { allMedia.distinctBy(MediaPreview::stableKey).take(5) }
    // Progress is synced with the Supabase watch_progress table both ways
    // (player writes -> Room + cloud; cloud -> Room on sign-in), so this row
    // follows whatever was last watched on any device.
    val continueWatching = remember(state.progress, allMedia) {
        val mediaByKey = allMedia.associateBy(MediaPreview::stableKey)
        state.progress
            .asSequence()
            .filter { it.isResumable() }
            .sortedByDescending { it.updatedAtEpochMillis }
            .mapNotNull { progress ->
                // Catalog rows only cover titles a loaded section lists; the
                // persisted preview snapshot hydrates everything else.
                val media = mediaByKey[progress.mediaKey] ?: progress.preview
                    ?: return@mapNotNull null
                media to progress
            }
            .toList()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(
        listState,
        state.sections.size,
        state.homeCatalogBatch.hasMore,
        state.homeCatalogBatch.loadingMore,
        state.homeCatalogBatch.loadMoreFailed,
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to
                listState.layoutInfo.totalItemsCount
        }.distinctUntilChanged().collectLatest { (lastVisibleIndex, totalListItems) ->
            delay(HOME_CATALOG_SCROLL_SETTLE_MILLIS)
            if (
                shouldPrefetchHomeCatalogBatch(
                    lastVisibleIndex = lastVisibleIndex,
                    totalListItems = totalListItems,
                    hasMore = state.homeCatalogBatch.hasMore,
                    loading = state.homeCatalogBatch.loadingMore,
                    failed = state.homeCatalogBatch.loadMoreFailed,
                )
            ) {
                onLoadMoreHome()
            }
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(MobileTokens.sectionGap),
    ) {
        if (heroItems.isNotEmpty()) {
            item(key = "feature") {
                MobileHeroCarousel(heroItems, onMedia, onSettings, restoreMediaKey, onFocusRestored)
            }
        } else if (
            state.initialContentLoading ||
            state.homeCatalogBatch.loadingMore ||
            state.sections.any(CatalogSection::initialLoading)
        ) {
            // Keep the hero slot present while the first catalog window loads,
            // so the big card never disappears during progressive loading.
            item(key = "feature") { MobileHeroLoadingSkeleton() }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue-watching") {
                MobileContinueWatchingRow(continueWatching, onMedia)
            }
        }
        if (
            !state.initialContentLoading &&
            !state.homeCatalogBatch.loadingMore &&
            !state.homeCatalogBatch.loadMoreFailed &&
            !state.homeCatalogBatch.hasMore &&
            state.providers.isEmpty() &&
            state.sections.isEmpty()
        ) {
            item { EmptyProviders(Modifier.padding(horizontal = 16.dp), onAddSource) }
        }
        items(state.sections, key = CatalogSection::id) { section ->
            CatalogRow(section, onMedia, onLoadMore, onRetry, restoreMediaKey, onFocusRestored)
        }
        when {
            state.homeCatalogBatch.loadMoreFailed -> item(key = "home-catalog-retry") {
                OutlinedButton(
                    onClick = onRetryHome,
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
            state.homeCatalogBatch.loadingMore && state.sections.none(CatalogSection::initialLoading) -> {
                item(key = "home-catalog-loading") {
                    MobileHomeCatalogLoadingSkeleton()
                }
            }
        }
    }
}

@Composable
private fun MobileHomeCatalogLoadingSkeleton() {
    val loadingDescription = stringResource(R.string.loading_more_rows)
    val pulse by rememberInfiniteTransition(label = "home catalog loading").animateFloat(
        initialValue = 0.58f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton pulse",
    )
    val skeletonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = loadingDescription },
        verticalArrangement = Arrangement.spacedBy(MobileTokens.sectionGap),
    ) {
        Text(
            text = stringResource(R.string.loading_more_rows),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(skeletonColor),
                    )
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(skeletonColor.copy(alpha = pulse * 0.8f)),
                    )
                }
                MobileCatalogItemsLoadingSkeleton(skeletonColor, pulse)
            }
        }
    }
}

@Composable
private fun MobileCatalogItemsLoadingSkeleton(
    skeletonColor: Color,
    pulse: Float,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(4) {
            Box(
                modifier = Modifier
                    .width(138.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(MobileTokens.radiusCard))
                    .background(skeletonColor),
            )
        }
    }
}

@Composable
private fun MobileHeroCarousel(
    items: List<MediaPreview>,
    onMedia: (MediaPreview) -> Unit,
    onSettings: () -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    val pagerState = rememberPagerState { items.size }
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val media = items[page]
            val pageDescription = stringResource(
                R.string.hero_carousel_description_touch, media.name, page + 1, items.size,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored)
                    .clickable(role = Role.Button) { onMedia(media) }
                    .semantics { contentDescription = pageDescription },
            ) {
                MediaArtwork(media, Modifier.fillMaxSize(), preferBackdrop = true)
                // Top scrim keeps status-bar icons legible on any artwork; the
                // bottom scrim carries the title block like the TV hero.
                // Ink wash behind the title block so text reads on any artwork.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to MobileTokens.ink),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                    0.16f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.88f),
                                ),
                            ),
                        ),
                )
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text(media.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    MobileMetadataLine(
                        presentation = media.metadataPresentation(maxGenres = 1),
                        includeGenres = true,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                    media.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.76f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // Manual paging only: nothing auto-advances while the user is reading.
        Row(
            Modifier.align(Alignment.BottomEnd).padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { index ->
                val active = index == pagerState.currentPage
                val width by animateDpAsState(targetValue = if (active) 18.dp else 6.dp, label = "hero indicator")
                Box(
                    Modifier
                        .size(width, 6.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.45f)),
                )
            }
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings_and_profiles),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun MobileContinueWatchingRow(
    items: List<Pair<MediaPreview, WatchProgress>>,
    onMedia: (MediaPreview) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.continue_watching),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.first.stableKey }) { (media, progress) ->
                MobileContinueWatchingCard(media, progress, onMedia)
            }
        }
    }
}

@Composable
private fun MobileContinueWatchingCard(
    media: MediaPreview,
    progress: WatchProgress,
    onMedia: (MediaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = progress.episodeLabel ?: media.name
    val percent = (progress.fraction * 100).roundToInt()
    val description = stringResource(R.string.media_card_description_progress, title, percent)
    val remainingMillis = (progress.durationMillis - progress.positionMillis).coerceAtLeast(0)
    val hours = remainingMillis / 3_600_000
    val minutes = (remainingMillis % 3_600_000) / 60_000
    val timeLeft = when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min"
        else -> null
    }
    Box(
        modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(MobileTokens.radiusResume))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button) { onMedia(media) }
            .semantics { contentDescription = description },
    ) {
        MediaArtwork(media, Modifier.fillMaxSize(), preferBackdrop = true)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        timeLeft?.let { left ->
            Text(
                text = stringResource(R.string.continue_watching_time_left, left),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = 8.dp, bottom = 15.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(7.dp)
                .background(Color.Black.copy(alpha = 0.55f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.fraction)
                    .height(7.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun MobileHeroLoadingSkeleton() {
    val pulse by rememberInfiniteTransition(label = "hero loading").animateFloat(
        initialValue = 0.58f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton pulse",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)),
    )
}

@Composable
private fun CatalogRow(
    section: CatalogSection,
    onMedia: (MediaPreview) -> Unit,
    onLoadMore: (String) -> Unit,
    onRetry: (String) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(section.providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        section.errorMessage?.let { error ->
            Text(
                error,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (section.initialLoading && section.items.isEmpty()) {
            val pulse by rememberInfiniteTransition(label = "catalog row loading").animateFloat(
                initialValue = 0.58f,
                targetValue = 0.78f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "skeleton pulse",
            )
            MobileCatalogItemsLoadingSkeleton(
                skeletonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse),
                pulse = pulse,
            )
        } else if (section.items.isNotEmpty() || section.supportsSkip) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(section.items, key = MediaPreview::stableKey) { media ->
                    PosterCard(
                        media = media,
                        onMedia = onMedia,
                        modifier = Modifier.mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
                    )
                }
                if (section.supportsSkip && (section.hasMore || section.loadingMore || section.loadMoreError != null)) {
                    item(key = "catalog-action") {
                        OutlinedButton(
                            onClick = {
                                if (section.loadMoreError != null) onRetry(section.id) else onLoadMore(section.id)
                            },
                            enabled = !section.loadingMore,
                            modifier = Modifier.sizeIn(minWidth = 132.dp, minHeight = 56.dp),
                        ) {
                            if (section.loadingMore) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(if (section.loadMoreError != null) R.string.retry else R.string.load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterCard(media: MediaPreview, onMedia: (MediaPreview) -> Unit, modifier: Modifier = Modifier) {
    val landscape = media.posterShape.equals("landscape", ignoreCase = true) ||
        (media.posterUrl.isNullOrBlank() && !media.backgroundUrl.isNullOrBlank())
    // Artwork carries the emotion: no name or metadata text on the card,
    // matching the TV design. The name stays available to TalkBack, and the
    // rating appears as a small neutral overlay per the design system.
    val ratingText = media.metadataPresentation().ratingText
    val cardDescription = ratingText
        ?.let { stringResource(R.string.media_card_description_rating, media.name, it) }
        ?: media.name
    Box(
        modifier
            .width(if (landscape) 220.dp else 138.dp)
            .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
            .clip(RoundedCornerShape(MobileTokens.radiusCard))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button) { onMedia(media) }
            .semantics { contentDescription = cardDescription },
    ) {
        MediaArtwork(media, Modifier.fillMaxSize())
        ratingText?.let { rating ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("★", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(rating, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun MobileMetadataLine(
    presentation: com.lamphaus.app.ui.MediaMetadataPresentation,
    includeGenres: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val values = buildList {
        presentation.year?.let { add(it.toString()) }
        presentation.contentRating?.let(::add)
        presentation.runtimeMinutes?.let { add(stringResource(R.string.minutes_format, it)) }
        presentation.ratingText?.let { add(stringResource(R.string.rating_format, it)) }
        if (includeGenres && presentation.genres.isNotEmpty()) add(presentation.genres.joinToString(", "))
    }
    if (values.isNotEmpty()) {
        Text(
            text = values.joinToString(" · "),
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MediaGrid(
    media: List<MediaPreview>,
    onMedia: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    modifier: Modifier = Modifier,
    section: CatalogSection? = null,
    onLoadMore: (String) -> Unit = {},
    onRetry: (String) -> Unit = {},
) {
    if (media.isEmpty() && section?.supportsSkip != true) {
        EmptyProviders(Modifier.padding(24.dp))
        return
    }
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(MobileTokens.spacingScreen),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(media, key = MediaPreview::stableKey) { item ->
            PosterCard(
                media = item,
                onMedia = onMedia,
                modifier = Modifier
                    .fillMaxWidth()
                    .mediaFocusRestore(item.stableKey, restoreMediaKey, onFocusRestored),
            )
        }
        if (section?.supportsSkip == true && (section.hasMore || section.loadingMore || section.loadMoreError != null)) {
            item(key = "catalog-action") {
                OutlinedButton(
                    onClick = { if (section.loadMoreError != null) onRetry(section.id) else onLoadMore(section.id) },
                    enabled = !section.loadingMore,
                    modifier = Modifier.sizeIn(minWidth = 132.dp, minHeight = 56.dp),
                ) {
                    if (section.loadingMore) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(if (section.loadMoreError != null) R.string.retry else R.string.load_more))
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: AppUiState,
    onMedia: (MediaPreview) -> Unit,
    onSettings: () -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    val media = state.library.map { it.preview }
    Column(Modifier.fillMaxSize()) {
        MobileScreenHeader(stringResource(R.string.library), showSettings = true, onSettings = onSettings)
        if (media.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.library_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Box(Modifier.weight(1f)) {
                MediaGrid(media, onMedia, restoreMediaKey, onFocusRestored)
            }
        }
    }
}

@Composable
private fun SearchScreen(
    state: AppUiState,
    onSearch: (String) -> Unit,
    onBrowseType: (String) -> Unit,
    onBrowseCatalog: (String) -> Unit,
    onBrowseGenre: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onCatalogLoadMore: (String) -> Unit,
    onCatalogRetry: (String) -> Unit,
    onMedia: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    onSettings: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { onSearch(query) }
    Column(Modifier.fillMaxSize().imePadding()) {
        MobileScreenHeader(stringResource(R.string.search), showSettings = true, onSettings = onSettings)
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_movies_series), color = MobileTokens.textMuted) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MobileTokens.textMuted) },
            singleLine = true,
            shape = RoundedCornerShape(MobileTokens.radiusField),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MobileTokens.surfaceRaised,
                unfocusedContainerColor = MobileTokens.surfaceRaised,
                focusedTextColor = MobileTokens.textPrimary,
                unfocusedTextColor = MobileTokens.textPrimary,
                cursorColor = MobileTokens.accent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = MobileTokens.spacingScreen),
        )
        if (state.searching || state.browse.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (query.isBlank()) {
            val browse = state.browse
            val selectedTarget = browse.targets.firstOrNull { it.id == browse.selectedCatalogId }
            LazyRow(contentPadding = PaddingValues(horizontal = MobileTokens.spacingScreen), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(browse.targets.map { it.catalog.type }.distinct(), key = { it }) { type ->
                    MobileFilterChip(
                        selected = browse.selectedType == type,
                        onClick = { onBrowseType(type) },
                        label = { Text(type.replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = MobileTokens.spacingScreen), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(browse.targets.filter { it.catalog.type == browse.selectedType }, key = { it.id }) { target ->
                    MobileFilterChip(
                        selected = browse.selectedCatalogId == target.id,
                        onClick = { onBrowseCatalog(target.id) },
                        label = { Text(target.catalog.name) },
                        enabled = target.unavailableReason == null,
                    )
                }
            }
            selectedTarget?.genres?.takeIf(List<String>::isNotEmpty)?.let { genres ->
                LazyRow(contentPadding = PaddingValues(horizontal = MobileTokens.spacingScreen), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        MobileFilterChip(
                            selected = browse.selectedGenre == null,
                            onClick = { onBrowseGenre(null) },
                            label = { Text(stringResource(R.string.all_genres)) },
                        )
                    }
                    items(genres, key = { it }) { genre ->
                        MobileFilterChip(
                            selected = browse.selectedGenre == genre,
                            onClick = { onBrowseGenre(genre) },
                            label = { Text(genre) },
                        )
                    }
                }
            }
            val result = browse.result
            Box(Modifier.weight(1f)) {
                when {
                    result?.errorMessage != null -> Text(result.errorMessage, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                    result != null -> MediaGrid(
                        result.items,
                        onMedia,
                        restoreMediaKey,
                        onFocusRestored,
                        section = result,
                        onLoadMore = { onLoadMore() },
                        onRetry = { onRetry() },
                    )
                    else -> EmptyProviders(Modifier.padding(24.dp))
                }
            }
        } else if (state.searching) {
            Box(Modifier.weight(1f))
        } else if (state.searchSections.isEmpty()) {
            Text(stringResource(R.string.search_no_results), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(state.searchSections, key = CatalogSection::id) { section ->
                    CatalogRow(section, onMedia, onCatalogLoadMore, onCatalogRetry, restoreMediaKey, onFocusRestored)
                }
            }
        }
    }
}

@Composable
private fun EmptyProviders(modifier: Modifier = Modifier, onAddSource: (() -> Unit)? = null) {
    Column(modifier.padding(vertical = 48.dp), horizontalAlignment = Alignment.Start) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.install_first_addon), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.install_addon_explanation),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (onAddSource != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAddSource) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.install_addon))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MobileDetailScreen(
    detail: MediaDetail?,
    expanded: Boolean,
    inLibrary: Boolean,
    watchedEpisodeIds: Set<String>,
    spoilerProtection: SpoilerProtectionSettings,
    onBack: () -> Unit,
    onPlay: (com.lamphaus.core.model.Episode?) -> Unit,
    onLibrary: () -> Unit,
    onEditArtwork: () -> Unit,
) {
    if (detail == null) return
    val artworkResolver = LocalArtworkResolver.current
    val resolvedPreview = remember(detail.preview, artworkResolver) {
        artworkResolver.resolve(detail.preview).media
    }

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
            Text(detail.preview.name, style = MaterialTheme.typography.headlineLarge)
            MobileMetadataLine(
                presentation = detail.metadataPresentation(),
                includeGenres = true,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detail.preview.description
                ?.takeIf(String::isNotBlank)
                ?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onPlay(null) }) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play))
                }
                OutlinedButton(onClick = onLibrary, enabled = !inLibrary) {
                    Text(stringResource(if (inLibrary) R.string.in_library else R.string.add_to_library))
                }
                OutlinedButton(onClick = onEditArtwork) {
                    Text(stringResource(R.string.edit_artwork))
                }
            }
            val fullGenres = detail.preview.metadataPresentation(maxGenres = Int.MAX_VALUE).genres
            if (fullGenres.isNotEmpty()) {
                MobileDetailMetadataSection(R.string.genres, fullGenres.joinToString(", "))
            }
            if (detail.cast.isNotEmpty()) {
                MobileDetailMetadataSection(
                    labelRes = R.string.cast,
                    value = detail.cast.joinToString("  •  ", transform = ::plainPersonName),
                )
            }
            if (detail.directors.isNotEmpty()) {
                MobileDetailMetadataSection(
                    labelRes = R.string.directors,
                    value = detail.directors.joinToString("  •  ", transform = ::plainPersonName),
                )
            }
        }
    }
    if (expanded) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(detail.preview.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                MediaArtwork(
                    detail.preview,
                    Modifier.fillMaxHeight().weight(0.44f),
                    preferBackdrop = true,
                )
                LazyColumn(Modifier.weight(0.56f)) {
                    item { info() }
                    items(detail.episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            watched = episode.id in watchedEpisodeIds,
                            spoilerProtection = spoilerProtection,
                            onPlay = onPlay,
                        )
                    }
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
            item(key = "hero") { MobileDetailHero(detail, resolvedPreview, onBack) }
            item(key = "actions") {
                MobileDetailActions(inLibrary, onPlay = { onPlay(null) }, onLibrary, onEditArtwork)
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
                        episode = episode,
                        watched = episode.id in watchedEpisodeIds,
                        spoilerProtection = spoilerProtection,
                        onPlay = onPlay,
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
                        MobileDetailMetadataSection(
                            labelRes = R.string.cast,
                            value = detail.cast.joinToString("  •  ", transform = ::plainPersonName),
                        )
                    }
                    if (detail.directors.isNotEmpty()) {
                        MobileDetailMetadataSection(
                            labelRes = R.string.directors,
                            value = detail.directors.joinToString("  •  ", transform = ::plainPersonName),
                        )
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
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(420.dp)) {
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
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .background(MobileTokens.surfaceRaised.copy(alpha = 0.68f), CircleShape),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
            )
        }
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
            } else {
                Text(preview.name, style = MaterialTheme.typography.displayLarge, color = MobileTokens.textPrimary)
            }
            Spacer(Modifier.height(8.dp))
            MobileMetadataLine(
                presentation = preview.metadataPresentation(),
                includeGenres = false,
                color = MobileTokens.textMuted,
            )
        }
    }
}

@Composable
private fun MobileDetailActions(
    inLibrary: Boolean,
    onPlay: () -> Unit,
    onLibrary: () -> Unit,
    onEditArtwork: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileTokens.spacingScreen),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MobileTokens.textPrimary,
                contentColor = Color.Black,
            ),
        ) {
            Icon(Icons.Outlined.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.play), style = MaterialTheme.typography.titleMedium)
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
private fun MobileArtworkEditorScreen(
    editor: ArtworkEditorState,
    onBack: () -> Unit,
    onPosterSelected: (ArtworkAsset?) -> Unit,
    onBackdropSelected: (ArtworkAsset?) -> Unit,
    onLogoSelected: (ArtworkAsset?) -> Unit,
    onProviderSelected: (ArtworkProviderId?) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_artwork)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (editor.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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
@Composable
internal fun EpisodeRow(
    episode: com.lamphaus.core.model.Episode,
    watched: Boolean,
    spoilerProtection: SpoilerProtectionSettings,
    onPlay: (com.lamphaus.core.model.Episode?) -> Unit,
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
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MobileTokens.radiusCard))
            .clickable(role = Role.Button) { onPlay(episode) }
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
            if (numberLabel.isNotEmpty()) {
                Text(
                    numberLabel,
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
private fun MobileSourcePickerScreen(
    picker: com.lamphaus.app.ui.SourcePickerState,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onProvider: (String?) -> Unit,
    onSource: (StreamCandidate) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (picker.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            Row(Modifier.fillMaxSize().padding(padding)) {
                Box(Modifier.weight(0.35f).fillMaxHeight()) {
                    MediaArtwork(picker.media, Modifier.fillMaxSize(), preferBackdrop = true)
                }
                Box(Modifier.weight(0.65f).fillMaxHeight()) { content() }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSettingsScreen(state: AppUiState, viewModel: AppViewModel, onBack: () -> Unit) {
    var providerUrl by rememberSaveable { mutableStateOf("") }
    var artworkKeys by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var deviceToRevoke by remember { mutableStateOf<PairedDevice?>(null) }
    var deleteAccountOpen by remember { mutableStateOf(false) }
    var pendingArtworkStorageMode by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshArtworkKeyStatus() }
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = MobileTokens.spacingScreen, end = MobileTokens.spacingScreen, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(MobileTokens.sectionGap),
        ) {
            item {
                SettingsCard(stringResource(R.string.profiles)) {
                    state.profiles.forEachIndexed { index, profile ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                        ListItem(
                            headlineContent = { Text(profile.name) },
                            supportingContent = {
                                Text(stringResource(if (profile.kind == ProfileKind.CHILD) R.string.child_profile else R.string.adult_profile))
                            },
                            leadingContent = { Icon(Icons.Outlined.Person, null) },
                            trailingContent = {
                                TextButton(onClick = { viewModel.selectProfile(profile.id) }, enabled = state.activeProfileId != profile.id) {
                                    Text(stringResource(if (state.activeProfileId == profile.id) R.string.active else R.string.switch_profile))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    val kidsName = stringResource(R.string.kids_profile_name)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { viewModel.addProfile(kidsName, true) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = MobileTokens.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_child_profile), color = MobileTokens.accent)
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.addons)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = providerUrl,
                            onValueChange = { providerUrl = it },
                            label = { Text(stringResource(R.string.addon_address)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { viewModel.addProvider(providerUrl) },
                            enabled = providerUrl.isNotBlank(),
                        ) { Text(stringResource(R.string.install_addon)) }
                        OutlinedButton(
                            onClick = viewModel::refreshContent,
                            enabled = state.providers.isNotEmpty() && !state.refreshing,
                        ) {
                            Text(stringResource(R.string.refresh_catalogs))
                        }
                    }
                    state.providers.forEachIndexed { index, provider ->
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                        ListItem(
                            headlineContent = { Text(provider.displayName) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (provider.sortOrder < 0) R.string.included_catalog
                                        else if (provider.enabled) R.string.enabled
                                        else R.string.disabled,
                                    ),
                                )
                            },
                            trailingContent = {
                                if (provider.sortOrder >= 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = provider.enabled,
                                            onCheckedChange = { viewModel.toggleProvider(provider.id, it) },
                                        )
                                        IconButton(onClick = { viewModel.removeProvider(provider.id) }) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.remove_provider_format, provider.displayName),
                                            )
                                        }
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (index < state.providers.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                        }
                    }
                }
            }
            if (com.lamphaus.app.BuildConfig.CLOUD_CONFIGURED) {
                item {
                    LaunchedEffect(Unit) { viewModel.loadDevices() }
                }
                item {
                    SettingsCard(stringResource(R.string.paired_devices)) {
                        if (state.pairedDevices.isEmpty()) {
                            Text(
                                stringResource(R.string.no_paired_devices),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.pairedDevices.forEachIndexed { index, device ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                            ListItem(
                                headlineContent = { Text(device.label) },
                                supportingContent = { Text(device.createdAt?.take(10) ?: device.platform) },
                                leadingContent = { Icon(Icons.Outlined.Tv, null) },
                                trailingContent = {
                                    TextButton(onClick = {
                                        deviceToRevoke = device
                                    }) { Text(stringResource(R.string.disconnect)) }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.appearance)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.ken_burns_effect)) },
                        supportingContent = { Text(stringResource(R.string.ken_burns_effect_description)) },
                        trailingContent = {
                            Switch(checked = state.kenBurnsEnabled, onCheckedChange = viewModel::setKenBurnsEnabled)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                SettingsCard(stringResource(R.string.spoiler_protection)) {
                    Text(
                        stringResource(R.string.spoiler_protection_description),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.protect_spoilers)) },
                        supportingContent = { Text(stringResource(R.string.spoiler_protection_description)) },
                        leadingContent = { Icon(Icons.Outlined.Visibility, null) },
                        trailingContent = {
                            Switch(
                                checked = state.spoilerProtection.enabled,
                                onCheckedChange = {
                                    viewModel.setSpoilerProtection(state.spoilerProtection.copy(enabled = it))
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.blur_episode_artwork)) },
                        supportingContent = { Text(stringResource(R.string.blur_episode_artwork_description)) },
                        trailingContent = {
                            Switch(
                                checked = state.spoilerProtection.blurEpisodeArtwork,
                                enabled = state.spoilerProtection.enabled,
                                onCheckedChange = {
                                    viewModel.setSpoilerProtection(state.spoilerProtection.copy(blurEpisodeArtwork = it))
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.blur_episode_synopsis)) },
                        supportingContent = { Text(stringResource(R.string.blur_episode_synopsis_description)) },
                        trailingContent = {
                            Switch(
                                checked = state.spoilerProtection.blurEpisodeSynopsis,
                                enabled = state.spoilerProtection.enabled,
                                onCheckedChange = {
                                    viewModel.setSpoilerProtection(state.spoilerProtection.copy(blurEpisodeSynopsis = it))
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                SettingsCard(stringResource(R.string.artwork)) {
                    Text(
                        stringResource(R.string.artwork_settings_description),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.local_only_artwork_keys)) },
                        supportingContent = { Text(stringResource(R.string.local_only_artwork_keys_description)) },
                        trailingContent = {
                            Switch(
                                checked = state.localOnlyArtworkKeys,
                                onCheckedChange = { pendingArtworkStorageMode = it },
                                enabled = !state.artworkStorageModeChanging,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    if (state.artworkProviders.isEmpty() && state.artworkProviderCatalogError != null) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.artworkProviderCatalogError, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::refreshArtworkKeyStatus) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
            items(state.artworkProviders, key = { it.provider.value }) { provider ->
                val providerId = provider.provider
                val providerName = provider.displayName
                val apiKey = artworkKeys[providerId.value].orEmpty()
                val failure = state.lastArtworkLookupFailures[providerId]?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MobileTokens.radiusSection),
                    colors = CardDefaults.cardColors(containerColor = MobileTokens.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(providerName, style = MaterialTheme.typography.titleLarge)
                        if (!provider.enabled) {
                            Text("This provider is no longer available. You can remove its saved key.")
                            if (provider.configured) {
                                OutlinedButton(
                                    onClick = { viewModel.deleteArtworkKey(providerId) },
                                    enabled = !state.artworkStorageModeChanging,
                                ) {
                                    Text(stringResource(R.string.remove_artwork_key))
                                }
                            }
                        } else {
                            Text(provider.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(
                                onClick = { viewModel.openArtworkProviderKeyPage(providerId) },
                                modifier = Modifier.sizeIn(minHeight = 48.dp),
                            ) {
                                Text(stringResource(R.string.artwork_get_key, providerName))
                            }
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { value -> artworkKeys = artworkKeys + (providerId.value to value) },
                                label = { Text(stringResource(R.string.artwork_api_key)) },
                                placeholder = { Text(stringResource(R.string.artwork_api_key_placeholder)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                onClick = { viewModel.reportMessage(provider.helpText) },
                                modifier = Modifier.sizeIn(minHeight = 48.dp),
                            ) {
                                Text(stringResource(R.string.artwork_get_help))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.saveArtworkKey(providerId, apiKey)
                                        artworkKeys = artworkKeys - providerId.value
                                    },
                                    enabled = !state.artworkStorageModeChanging && apiKey.isNotBlank(),
                                ) {
                                    Text(stringResource(R.string.save_artwork_key))
                                }
                                if (provider.configured) {
                                    OutlinedButton(
                                        onClick = { viewModel.deleteArtworkKey(providerId) },
                                        enabled = !state.artworkStorageModeChanging,
                                    ) {
                                        Text(stringResource(R.string.remove_artwork_key))
                                    }
                                }
                            }
                            Text(
                                when {
                                    state.artworkKeyStatusLoading -> stringResource(R.string.artwork_key_loading, providerName)
                                    provider.configured -> stringResource(R.string.artwork_key_active, providerName)
                                    else -> stringResource(R.string.artwork_key_not_configured, providerName)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            failure?.let {
                                Text(
                                    stringResource(R.string.artwork_key_last_lookup_failed, it),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.privacy)) {
                    ConsentRow(stringResource(R.string.crash_reports), state.diagnostics.crashReports) {
                        viewModel.setDiagnostics(state.diagnostics.copy(crashReports = it))
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                    ConsentRow(stringResource(R.string.performance_metrics), state.diagnostics.performanceMetrics) {
                        viewModel.setDiagnostics(state.diagnostics.copy(performanceMetrics = it))
                    }
                    Text(
                        stringResource(R.string.diagnostics_explanation),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (com.lamphaus.app.BuildConfig.CLOUD_CONFIGURED) {
                item {
                    SettingsCard(stringResource(R.string.account)) {
                        Text(
                            stringResource(R.string.delete_account_explanation),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MobileTokens.hairline)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { deleteAccountOpen = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.delete_account),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    deviceToRevoke?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToRevoke = null },
            title = { Text(stringResource(R.string.disconnect_title)) },
            text = { Text(stringResource(R.string.disconnect_body_format, device.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeDevice(device.id, device.label)
                    deviceToRevoke = null
                }) {
                    Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRevoke = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (deleteAccountOpen) {
        AlertDialog(
            onDismissRequest = { deleteAccountOpen = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteAccountOpen = false
                    viewModel.deleteAccount()
                }) {
                    Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAccountOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    pendingArtworkStorageMode?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingArtworkStorageMode = null },
            title = {
                Text(
                    stringResource(
                        if (target) R.string.artwork_storage_enable_title
                        else R.string.artwork_storage_disable_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (target) R.string.artwork_storage_enable_body
                        else R.string.artwork_storage_disable_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingArtworkStorageMode = null
                    viewModel.changeArtworkKeyStorageMode(target)
                }) {
                    Text(stringResource(R.string.delete_keys_and_switch), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingArtworkStorageMode = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MobileTokens.radiusSection))
                .background(MobileTokens.surfaceRaised),
            content = content,
        )
    }
}

@Composable
private fun ConsentRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked, onChecked) },
        modifier = Modifier.sizeIn(minHeight = 48.dp).semantics(mergeDescendants = true) {},
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
