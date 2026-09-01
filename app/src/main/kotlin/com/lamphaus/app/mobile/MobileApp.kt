package com.lamphaus.app.mobile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lamphaus.app.ui.isResumable

import androidx.compose.ui.unit.Dp
import com.lamphaus.app.ui.ArtworkResolver
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.AppViewModel
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.app.R

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
            val detailProgress = state.progress.lastOrNull {
                it.mediaKey == state.selectedDetail.preview.stableKey && it.isResumable()
            }
            MobileDetailScreen(
                detail = state.selectedDetail,
                expanded = widthSizeClass == WindowWidthSizeClass.Expanded,
                inLibrary = state.library.any { it.mediaKey == state.selectedDetail.preview.stableKey },
                watchedEpisodeIds = watchedEpisodeIds,
                spoilerProtection = state.spoilerProtection,
                onBack = viewModel::clearDetail,
                resumeProgress = detailProgress,
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
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                            )
                            MobileDestination.LIBRARY -> LibraryScreen(
                                state = state,
                                onMedia = openMedia,
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
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                            )
                        }
                        }
                        if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding())
                    }
            }
            if (compact) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    MobileNavBar(
                        destination = destination,
                        onProfile = { settingsOpen = true },
                        onSelect = { destination = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
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
                        NavigationRailItem(
                            selected = false,
                            onClick = { settingsOpen = true },
                            icon = { Icon(Icons.Outlined.Person, null) },
                            label = { Text(stringResource(R.string.profile)) },
                        )
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
    onProfile: () -> Unit,
    onSelect: (MobileDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
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
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .clickable(role = Role.Button) { onProfile() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = stringResource(R.string.profile),
                tint = MobileTokens.textMuted,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.profile),
                style = MaterialTheme.typography.labelMedium,
                color = MobileTokens.textMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun MobileScreenHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp)
            .padding(horizontal = 16.dp),
    )
}

@Composable
internal fun navBarClearancePadding(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 92.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MobileFilterChip(
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
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MobileScreenHeader(stringResource(R.string.discover))
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
private fun LibraryScreen(
    state: AppUiState,
    onMedia: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    val media = state.library.map { it.preview }
    Column(Modifier.fillMaxSize()) {
        MobileScreenHeader(stringResource(R.string.library))
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
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { onSearch(query) }
    Column(Modifier.fillMaxSize().imePadding()) {
        MobileScreenHeader(stringResource(R.string.search))
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
            LazyColumn(contentPadding = PaddingValues(bottom = navBarClearancePadding()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(state.searchSections, key = CatalogSection::id) { section ->
                    CatalogRow(section, onMedia, onCatalogLoadMore, onCatalogRetry, restoreMediaKey, onFocusRestored)
                }
            }
        }
    }
}

@Composable
internal fun EmptyProviders(modifier: Modifier = Modifier, onAddSource: (() -> Unit)? = null) {
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
