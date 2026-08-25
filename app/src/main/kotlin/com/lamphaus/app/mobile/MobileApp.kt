package com.lamphaus.app.mobile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lamphaus.app.R
import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.AppViewModel
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.mediaFocusRestore
import com.lamphaus.app.ui.sourcePresentation
import com.lamphaus.app.ui.sourceItemKey
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.data.preferences.ThemePreference
import com.lamphaus.core.model.DiagnosticsConsent
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.PairedDevice
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.ProfileKind

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
    LamphausMobileTheme(state.theme, state.dynamicColor) {
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
                        is AccountState.SignedIn -> if (state.initialContentLoading) {
                            LoadingScreen()
                        } else {
                            MobileSignedInApp(state, viewModel, widthSizeClass)
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
        state.selectedDetail != null -> {
            BackHandler { viewModel.clearDetail() }
            MobileDetailScreen(
                detail = state.selectedDetail,
                expanded = widthSizeClass == WindowWidthSizeClass.Expanded,
                inLibrary = state.library.any { it.mediaKey == state.selectedDetail.preview.stableKey },
                onBack = viewModel::clearDetail,
                onPlay = { episode -> viewModel.openSources(state.selectedDetail.preview, episode) },
                onLibrary = { viewModel.addToLibrary(state.selectedDetail.preview) },
            )
        }
        settingsOpen -> {
            BackHandler { settingsOpen = false }
            MobileSettingsScreen(state, viewModel, onBack = { settingsOpen = false })
        }
        else -> {
            val compact = widthSizeClass == WindowWidthSizeClass.Compact
            val content: @Composable () -> Unit = {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(destination.labelRes)) },
                            actions = {
                                IconButton(onClick = { settingsOpen = true }) {
                                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_and_profiles))
                                }
                            },
                        )
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (destination) {
                            MobileDestination.HOME -> MobileHomeScreen(
                                state = state,
                                onMedia = openMedia,
                                onAddSource = { settingsOpen = true },
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                            )
                            MobileDestination.DISCOVER -> MediaGrid(
                                media = state.allMedia,
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
                                onMedia = openMedia,
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                            )
                        }
                        if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
            if (compact) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            MobileDestination.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = destination == item,
                                    onClick = { destination = item },
                                    icon = { Icon(if (destination == item) item.selectedIcon else item.icon, null) },
                            label = { Text(stringResource(item.labelRes)) },
                                )
                            }
                        }
                    },
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
private fun MobileHomeScreen(
    state: AppUiState,
    onMedia: (MediaPreview) -> Unit,
    onAddSource: () -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    val feature = state.allMedia.firstOrNull()
    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (feature != null) item(key = "feature") { MobileHero(feature, onMedia, restoreMediaKey, onFocusRestored) }
        if (state.providers.isEmpty() && state.sections.isEmpty()) {
            item { EmptyProviders(Modifier.padding(horizontal = 24.dp), onAddSource) }
        }
        items(state.sections, key = CatalogSection::id) { section ->
            CatalogRow(section, onMedia, restoreMediaKey, onFocusRestored)
        }
    }
}

@Composable
private fun MobileHero(
    media: MediaPreview,
    onMedia: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    Card(
        onClick = { onMedia(media) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(310.dp)
            .mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            MediaArtwork(media, Modifier.fillMaxSize(), preferBackdrop = true)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.82f)),
                    ),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(media.name, style = MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.White)
                Text(
                    listOfNotNull(
                        media.releaseYear?.toString(),
                        media.genres.firstOrNull(),
                        media.rating?.let { "★ $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f),
                )
            }
        }
    }
}

@Composable
private fun CatalogRow(
    section: CatalogSection,
    onMedia: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(section.providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (section.errorMessage != null) {
            Text(
                section.errorMessage,
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(section.items, key = MediaPreview::stableKey) { media ->
                    PosterCard(
                        media = media,
                        onMedia = onMedia,
                        modifier = Modifier.mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterCard(media: MediaPreview, onMedia: (MediaPreview) -> Unit, modifier: Modifier = Modifier) {
    val landscape = media.posterShape.equals("landscape", ignoreCase = true) ||
        (media.posterUrl.isNullOrBlank() && !media.backgroundUrl.isNullOrBlank())
    Card(
        onClick = { onMedia(media) },
        modifier = modifier.width(if (landscape) 220.dp else 138.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        MediaArtwork(media, Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f))
        Column(Modifier.padding(10.dp)) {
            Text(media.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val metadata = listOfNotNull(media.releaseYear?.toString(), media.rating?.let { "★ $it" }).joinToString(" · ")
            if (metadata.isNotBlank()) {
                Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MediaGrid(
    media: List<MediaPreview>,
    onMedia: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    if (media.isEmpty()) {
        EmptyProviders(Modifier.padding(24.dp))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
    if (media.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.library_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else MediaGrid(media, onMedia, restoreMediaKey, onFocusRestored)
}

@Composable
private fun SearchScreen(
    state: AppUiState,
    onSearch: (String) -> Unit,
    onMedia: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { onSearch(query) }
    val matches = if (query.isBlank()) state.allMedia else state.searchResults
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_movies_series)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f)) {
            if (query.isNotBlank() && !state.searching && matches.isEmpty()) {
                Text(
                    stringResource(R.string.search_no_results),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MediaGrid(matches, onMedia, restoreMediaKey, onFocusRestored)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileDetailScreen(
    detail: MediaDetail?,
    expanded: Boolean,
    inLibrary: Boolean,
    onBack: () -> Unit,
    onPlay: (com.lamphaus.core.model.Episode?) -> Unit,
    onLibrary: () -> Unit,
) {
    if (detail == null) return
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
        val info: @Composable () -> Unit = {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!detail.preview.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = detail.preview.logoUrl,
                        contentDescription = detail.preview.name,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                    )
                }
                Text(detail.preview.name, style = MaterialTheme.typography.headlineLarge)
                Text(
                    listOfNotNull(
                        detail.preview.releaseYear?.toString(),
                        detail.runtimeMinutes?.let { stringResource(R.string.minutes_format, it) },
                        detail.preview.rating?.let { "★ $it" },
                        detail.preview.contentRating,
                    )
                        .joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                detail.preview.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onPlay(null) }) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.play))
                    }
                    OutlinedButton(onClick = onLibrary, enabled = !inLibrary) {
                        Text(stringResource(if (inLibrary) R.string.in_library else R.string.add_to_library))
                    }
                }
            }
        }
        if (expanded) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                MediaArtwork(detail.preview, Modifier.fillMaxHeight().weight(0.44f), preferBackdrop = true)
                LazyColumn(Modifier.weight(0.56f)) {
                    item { info() }
                    items(detail.episodes, key = { it.id }) { episode -> EpisodeRow(episode, onPlay) }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item { MediaArtwork(detail.preview, Modifier.fillMaxWidth().height(300.dp), preferBackdrop = true) }
                item { info() }
                items(detail.episodes, key = { it.id }) { episode -> EpisodeRow(episode, onPlay) }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: com.lamphaus.core.model.Episode, onPlay: (com.lamphaus.core.model.Episode?) -> Unit) {
    ListItem(
        headlineContent = { Text(episode.title) },
        supportingContent = { episode.overview?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        overlineContent = {
            Text(stringResource(R.string.episode_format, episode.season ?: 0, episode.episode ?: 0))
        },
        trailingContent = {
            IconButton(onClick = { onPlay(episode) }) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.play_title_format, episode.title))
            }
        },
    )
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
                        FilterChip(
                            selected = picker.selectedProviderId == null,
                            onClick = { onProvider(null) },
                            label = { Text(stringResource(R.string.all_sources)) },
                        )
                    }
                    items(picker.providerIds, key = { it }) { providerId ->
                        FilterChip(
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
    var deviceToRevoke by remember { mutableStateOf<PairedDevice?>(null) }
    var deleteAccountOpen by remember { mutableStateOf(false) }
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SettingsHeading(stringResource(R.string.profiles)) }
            items(state.profiles, key = { it.id }) { profile ->
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
                )
            }
            item {
                val kidsName = stringResource(R.string.kids_profile_name)
                OutlinedButton(onClick = { viewModel.addProfile(kidsName, true) }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_child_profile))
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SettingsHeading(stringResource(R.string.addons)) }
            item {
                OutlinedTextField(
                    value = providerUrl,
                    onValueChange = { providerUrl = it },
                    label = { Text(stringResource(R.string.addon_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = { viewModel.addProvider(providerUrl) },
                    enabled = providerUrl.startsWith("https://") || (com.lamphaus.app.BuildConfig.DEBUG && providerUrl.startsWith("http://")),
                ) { Text(stringResource(R.string.install_addon)) }
            }
            item {
                OutlinedButton(onClick = viewModel::refreshContent, enabled = state.providers.isNotEmpty() && !state.refreshing) {
                    Text(stringResource(R.string.refresh_catalogs))
                }
            }
            items(state.providers, key = { it.id }) { provider ->
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
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            if (com.lamphaus.app.BuildConfig.CLOUD_CONFIGURED) {
                item { SettingsHeading(stringResource(R.string.paired_devices)) }
                item {
                    LaunchedEffect(Unit) { viewModel.loadDevices() }
                }
                if (state.pairedDevices.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_paired_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.pairedDevices, key = { it.id }) { device ->
                    ListItem(
                        headlineContent = { Text(device.label) },
                        supportingContent = { Text(device.createdAt?.take(10) ?: device.platform) },
                        leadingContent = { Icon(Icons.Outlined.Tv, null) },
                        trailingContent = {
                            TextButton(onClick = {
                                deviceToRevoke = device
                            }) { Text(stringResource(R.string.disconnect)) }
                        },
                    )
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SettingsHeading(stringResource(R.string.appearance)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePreference.entries.forEach { theme ->
                        FilterChip(
                            selected = state.theme == theme,
                            onClick = { viewModel.setTheme(theme) },
                            label = { Text(theme.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.use_device_colors)) },
                    supportingContent = { Text(stringResource(R.string.device_colors_requirement)) },
                    trailingContent = {
                        Switch(checked = state.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                    },
                )
            }
            item { SettingsHeading(stringResource(R.string.privacy)) }
            item {
                ConsentRow(stringResource(R.string.crash_reports), state.diagnostics.crashReports) {
                    viewModel.setDiagnostics(state.diagnostics.copy(crashReports = it))
                }
            }
            item {
                ConsentRow(stringResource(R.string.performance_metrics), state.diagnostics.performanceMetrics) {
                    viewModel.setDiagnostics(state.diagnostics.copy(performanceMetrics = it))
                }
            }
            item {
                Text(
                    stringResource(R.string.diagnostics_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SettingsHeading(stringResource(R.string.account)) }
            item {
                Text(
                    stringResource(R.string.delete_account_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                TextButton(onClick = { deleteAccountOpen = true }) {
                    Text(
                        stringResource(R.string.delete_account),
                        color = MaterialTheme.colorScheme.error,
                    )
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
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
}

@Composable
private fun ConsentRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked, onChecked) },
        modifier = Modifier.sizeIn(minHeight = 48.dp).semantics(mergeDescendants = true) {},
    )
}
