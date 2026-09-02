package com.lamphaus.app.mobile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lamphaus.app.ui.isResumable
import com.lamphaus.app.ui.rememberReducedMotion
import kotlinx.coroutines.delay
import com.lamphaus.app.ui.ContentMenuAction
import com.lamphaus.app.ui.ContentMenuTarget

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

internal enum class MobileDestination(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME(R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
    DISCOVER(R.string.discover, Icons.Filled.Explore, Icons.Outlined.Explore),
    LIBRARY(R.string.library, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    SEARCH(R.string.search, Icons.Filled.Search, Icons.Outlined.Search),
}

/** Rows with content required before the signed-in home is revealed. */
private const val STARTUP_READY_ROWS = 2

/** Warm-up window so the first rows' images decode behind the loading cover. */
private const val STARTUP_WARM_MILLIS = 900L

/** Hard cap so slow or failed home loads still reveal the app. */
private const val STARTUP_MAX_WAIT_MILLIS = 8_000L

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
        // Cold-start gate: the branded loading surface is the only thing
        // rendered until authentication resolves and, for returning users,
        // Home is meaningfully ready. The handoff happens exactly once and
        // never regresses (SHR-PROD-04, SHR-ARC-05/06/09).
        val reducedMotion = rememberReducedMotion()
        val readyRows = state.sections.count { section -> section.items.isNotEmpty() }
        val settledWithoutContent = homeStartupSettledWithoutContent(state)
        val startupGate = remember {
            MobileStartupGate(initiallyResident = readyRows >= STARTUP_READY_ROWS)
        }
        LaunchedEffect(state.account) {
            startupGate.onAccountChanged(state.account)
        }
        LaunchedEffect(readyRows, settledWithoutContent, startupGate.awaitingContent) {
            startupGate.onHomeContent(readyRows, settledWithoutContent)
        }
        LaunchedEffect(startupGate.contentReady) {
            if (startupGate.contentReady) {
                if (!startupGate.initiallyResident) {
                    // Images need a decode window on a cold start; content
                    // already resident in a warm process reveals immediately.
                    delay(STARTUP_WARM_MILLIS)
                }
                startupGate.onWarmUpElapsed()
            }
        }
        LaunchedEffect(startupGate.awaitingContent) {
            if (startupGate.awaitingContent) {
                delay(STARTUP_MAX_WAIT_MILLIS)
                startupGate.onContentTimeout()
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize()) {
                MobileStartupSurface(
                    phase = startupGate.phase,
                    reducedMotion = reducedMotion,
                    signIn = {
                        MobileSignInScreen(
                            cloudConfigured = com.lamphaus.app.BuildConfig.CLOUD_CONFIGURED,
                            onGoogleSignIn = onGoogleSignIn,
                            onEmailLink = onEmailLink,
                            onDevelopmentSession = viewModel::openDevelopmentSession,
                        )
                    },
                    signedIn = { MobileSignedInApp(state, viewModel, widthSizeClass) },
                )
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                MobileContentMenuSheet(
                    menu = state.contentMenu,
                    inLibrary = state.contentMenu.target?.let { target ->
                        state.library.any { it.mediaKey == target.media.stableKey }
                    } == true,
                    onDismiss = viewModel::dismissContentMenu,
                    onAction = viewModel::onContentMenuAction,
                )
            }
        }
        }
    }
}

@Composable
internal fun LoadingScreen() {
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

/** Cold-start rendering phases for the mobile host. */
internal enum class MobileStartupPhase { Startup, SignedOut, SignedIn }

/**
 * One-shot cold-start gate (SHR-ARC-05/06/09). Holds the branded loading
 * surface above the entire application until Home is meaningfully ready for
 * a returning signed-in user, ends startup immediately when authentication
 * resolves signed-out, and hands off exactly once: later transient
 * AccountState.Loading emissions can never regress the UI to startup.
 *
 * The policy is snapshot state with no side effects or timing of its own, so
 * it is unit testable on the JVM (SHR-ARC-15); the composable owns delays.
 */
internal class MobileStartupGate(initiallyResident: Boolean) {

    /** Rendering phase; [MobileStartupPhase.Startup] shows only LoadingScreen. */
    var phase by mutableStateOf(MobileStartupPhase.Startup)
        private set

    /** True once a signed-in account resolved and content is awaited. */
    var awaitingContent by mutableStateOf(false)
        private set

    /** True once enough rows hold content; the warm-up effect follows. */
    var contentReady by mutableStateOf(false)
        private set

    /**
     * True when meaningful content predated startup (warm process): the
     * artwork warm-up is skipped and the handoff is immediate.
     */
    val initiallyResident: Boolean = initiallyResident

    /**
     * Authentication resolution. Idempotent per state; a completed startup
     * never regresses to [MobileStartupPhase.Startup] on transient Loading.
     */
    fun onAccountChanged(account: AccountState) {
        if (phase != MobileStartupPhase.Startup) {
            when (account) {
                is AccountState.SignedIn -> phase = MobileStartupPhase.SignedIn
                AccountState.SignedOut -> phase = MobileStartupPhase.SignedOut
                AccountState.Loading -> Unit // keep the current phase
            }
            return
        }
        when (account) {
            AccountState.Loading -> Unit // authentication still unresolved
            AccountState.SignedOut -> phase = MobileStartupPhase.SignedOut
            is AccountState.SignedIn -> awaitingContent = true
        }
    }

    /** Home content observation while the signed-in gate is holding. */
    fun onHomeContent(readyRows: Int, settledWithoutContent: Boolean) {
        if (phase != MobileStartupPhase.Startup || !awaitingContent) return
        if (readyRows >= STARTUP_READY_ROWS) contentReady = true
        if (settledWithoutContent) complete()
    }

    /** Artwork warm-up finished after content readiness. */
    fun onWarmUpElapsed() {
        if (phase != MobileStartupPhase.Startup || !contentReady) return
        complete()
    }

    /** Content timeout measured from signed-in resolution. */
    fun onContentTimeout() {
        if (phase != MobileStartupPhase.Startup || !awaitingContent) return
        complete()
    }

    private fun complete() {
        phase = MobileStartupPhase.SignedIn
    }
}

/**
 * Terminal states of Home's initial pipeline that carry no usable rows: an
 * empty provider set with nothing left to load, a failed initial window, or
 * every resolved section having failed its initial load. Startup reveals the
 * app in each case so recovery stays local (SHR-PROD-04).
 */
internal fun homeStartupSettledWithoutContent(state: AppUiState): Boolean {
    if (
        state.initialContentLoading ||
        state.homeCatalogBatch.loadingMore ||
        state.sections.any(CatalogSection::initialLoading)
    ) {
        return false
    }
    if (state.sections.any { section -> section.items.isNotEmpty() }) return false
    val terminalEmpty = state.providers.isEmpty() &&
        state.sections.isEmpty() &&
        !state.homeCatalogBatch.loadMoreFailed &&
        !state.homeCatalogBatch.hasMore
    val initialWindowFailed = state.homeCatalogBatch.loadMoreFailed
    val allSectionsFailed = state.sections.isNotEmpty() &&
        state.sections.all { section ->
            !section.initialLoading && section.errorMessage != null
        }
    return terminalEmpty || initialWindowFailed || allSectionsFailed
}

/**
 * Root phase surface: only the branded loading screen during startup, then a
 * single fade into the sign-in or signed-in app. Normal motion keeps the
 * existing account-switch fade timing (MOB-MOT-02); reduced motion makes the
 * handoff instant (MOB-MOT-03).
 */
@Composable
internal fun MobileStartupSurface(
    phase: MobileStartupPhase,
    reducedMotion: Boolean,
    signIn: @Composable () -> Unit,
    signedIn: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            }
        },
        label = "startup",
    ) { target ->
        when (target) {
            MobileStartupPhase.Startup -> LoadingScreen()
            MobileStartupPhase.SignedOut -> signIn()
            MobileStartupPhase.SignedIn -> signedIn()
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
                progress = state.progress,
                onOpenMenu = viewModel::openContentMenu,
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
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 90, easing = LinearOutSlowInEasing)) + scaleIn(
                                initialScale = 0.92f,
                                animationSpec = tween(220, delayMillis = 90, easing = LinearOutSlowInEasing),
                            )) togetherWith fadeOut(tween(90, easing = FastOutLinearInEasing))
                        },
                        label = "destination",
                    ) { tab ->
                        when (tab) {
                            MobileDestination.HOME -> MobileHomeScreen(
                                state = state,
                                onMedia = openMedia,
                                onAddSource = { settingsOpen = true },
                                onLoadMore = viewModel::loadMoreCatalog,
                                onRetry = viewModel::retryCatalogPage,
                                onPlay = { media -> viewModel.openSources(media, null) },
                                onLoadMoreHome = viewModel::loadMoreHomeCatalogSections,
                                onRetryHome = viewModel::retryHomeCatalogSections,
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                                inLibrary = { media -> state.library.any { it.mediaKey == media.stableKey } },
                                onToggleLibrary = viewModel::addToLibrary,
                                onMenuAction = viewModel::onContentMenuAction,
                                onOpenMenu = viewModel::openContentMenu,
                            )

                            MobileDestination.DISCOVER -> DiscoverScreen(
                                state = state,
                                onMedia = openMedia,
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                                onOpenMenu = viewModel::openContentMenu,
                                onMenuAction = viewModel::onContentMenuAction,
                            )
                            MobileDestination.LIBRARY -> LibraryScreen(
                                state = state,
                                onMedia = openMedia,
                                restoreMediaKey = pendingMediaKey,
                                onFocusRestored = { pendingMediaKey = null },
                                onOpenMenu = viewModel::openContentMenu,
                                onMenuAction = viewModel::onContentMenuAction,
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
                                onOpenMenu = viewModel::openContentMenu,
                                onMenuAction = viewModel::onContentMenuAction,
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
internal fun MobileNavBar(
    destination: MobileDestination,
    onProfile: () -> Unit,
    onSelect: (MobileDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MobileTokens.hairline, RoundedCornerShape(20.dp)),
        containerColor = MobileTokens.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        MobileDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = destination == item,
                onClick = { onSelect(item) },
                icon = { MobileNavIcon(item, destination == item) },
                label = { Text(stringResource(item.labelRes), style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        NavigationBarItem(
            selected = false,
            onClick = onProfile,
            icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            label = { Text(stringResource(R.string.profile), style = MaterialTheme.typography.labelSmall) },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun MobileNavIcon(item: MobileDestination, selected: Boolean) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (fadeIn(tween(140, delayMillis = 40)) + scaleIn(
                initialScale = 0.72f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
            )) togetherWith fadeOut(tween(80))
        },
        label = "navIcon",
    ) { isSelected ->
        Icon(if (isSelected) item.selectedIcon else item.icon, contentDescription = null)
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
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp

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
    onOpenMenu: (ContentMenuTarget) -> Unit,
    onMenuAction: (ContentMenuTarget, ContentMenuAction) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MobileScreenHeader(stringResource(R.string.discover))
        Box(Modifier.weight(1f)) {
            MediaGrid(
                media = state.allMedia,
                onMedia = onMedia,
                restoreMediaKey = restoreMediaKey,
                onFocusRestored = onFocusRestored,
                progressByVideo = state.progress.associateBy { it.videoId },
                completedVideoIds = state.progress.filter { it.completed }.mapTo(mutableSetOf()) { it.videoId },
                inLibrary = { media -> state.library.any { it.mediaKey == media.stableKey } },
                onOpenMenu = onOpenMenu,
                onMenuAction = onMenuAction,
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
    onOpenMenu: (ContentMenuTarget) -> Unit,
    onMenuAction: (ContentMenuTarget, ContentMenuAction) -> Unit,
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
                MediaGrid(
                    media,
                    onMedia,
                    restoreMediaKey,
                    onFocusRestored,
                    progressByVideo = state.progress.associateBy { it.videoId },
                    completedVideoIds = state.progress.filter { it.completed }.mapTo(mutableSetOf()) { it.videoId },
                    inLibrary = { media -> state.library.any { it.mediaKey == media.stableKey } },
                    onOpenMenu = onOpenMenu,
                    onMenuAction = onMenuAction,
                )
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
    onOpenMenu: (ContentMenuTarget) -> Unit,
    onMenuAction: (ContentMenuTarget, ContentMenuAction) -> Unit,
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
                        progressByVideo = state.progress.associateBy { it.videoId },
                        completedVideoIds = state.progress.filter { it.completed }.mapTo(mutableSetOf()) { it.videoId },
                        inLibrary = { media -> state.library.any { it.mediaKey == media.stableKey } },
                        onOpenMenu = onOpenMenu,
                        onMenuAction = onMenuAction,
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
                    CatalogRow(
                        section,
                        onMedia,
                        onCatalogLoadMore,
                        onCatalogRetry,
                        restoreMediaKey,
                        onFocusRestored,
                        progressByVideo = state.progress.associateBy { it.videoId },
                        completedVideoIds = state.progress.filter { it.completed }.mapTo(mutableSetOf()) { it.videoId },
                        inLibrary = { media -> state.library.any { it.mediaKey == media.stableKey } },
                        onOpenMenu = onOpenMenu,
                        onMenuAction = onMenuAction,
                    )
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
