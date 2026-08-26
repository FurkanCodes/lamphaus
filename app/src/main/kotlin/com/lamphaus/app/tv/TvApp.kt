package com.lamphaus.app.tv

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.lamphaus.app.BuildConfig
import com.lamphaus.app.R
import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.AppViewModel
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.SourcePickerState
import com.lamphaus.app.ui.mediaFocusRestore
import com.lamphaus.app.ui.sourcePresentation
import com.lamphaus.app.ui.sourceItemKey
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.StreamCandidate
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun TvApp(
    viewModel: AppViewModel,
    initialSearch: String?,
    onPlay: (PlaybackRequest) -> Unit,
    onExternalPlay: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LamphausTvTheme {
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                when (state.account) {
                    AccountState.Loading -> TvLoading()
                    AccountState.SignedOut -> TvPairingScreen(state, viewModel)
                    is AccountState.SignedIn -> if (state.initialContentLoading) {
                        TvLoading()
                    } else {
                        TvSignedIn(state, viewModel, initialSearch)
                    }
                }
                state.message?.let { message ->
                    LaunchedEffect(message) {
                        delay(3_000)
                        viewModel.dismissMessage()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = TvLayoutTokens.screenHorizontalPadding,
                                vertical = TvLayoutTokens.screenBottomPadding,
                            ),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier
                                .background(TvSurfaceTokens.elevated, TvShapeTokens.card)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLoading() {
    val reducedMotion = rememberReducedMotion()
    val sweep = remember { Animatable(-0.12f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            sweep.snapTo(1.12f)
        } else {
            sweep.animateTo(
                targetValue = 1.12f,
                animationSpec = tween(
                    durationMillis = TvMotionTokens.startupSweepDurationMillis,
                    easing = LinearEasing,
                ),
            )
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_lamphaus_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = stringResource(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 1.6.sp),
                    )
                }
                if (!reducedMotion) {
                    Canvas(Modifier.fillMaxSize()) {
                        val x = size.width * sweep.value
                        drawLine(
                            color = TvFocusTokens.beam.copy(alpha = 0.12f),
                            start = androidx.compose.ui.geometry.Offset(x, 4.dp.toPx()),
                            end = androidx.compose.ui.geometry.Offset(x, size.height - 4.dp.toPx()),
                            strokeWidth = 12.dp.toPx(),
                        )
                        drawLine(
                            color = TvFocusTokens.beam.copy(alpha = 0.72f),
                            start = androidx.compose.ui.geometry.Offset(x, 8.dp.toPx()),
                            end = androidx.compose.ui.geometry.Offset(x, size.height - 8.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.loading_library),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TvPairingScreen(state: AppUiState, viewModel: AppViewModel) {
    LaunchedEffect(Unit) { viewModel.createPairingSession() }
    TvPairingContent(
        shortCode = state.pairingSession?.shortCode,
        qrPayload = state.pairingSession?.qrPayload,
        expiresAtEpochMillis = state.pairingSession?.expiresAtEpochMillis,
        showDevelopmentAction = BuildConfig.DEBUG && !BuildConfig.CLOUD_CONFIGURED,
        onRefresh = viewModel::createPairingSession,
        onDevelopmentSession = viewModel::openDevelopmentSession,
    )
}

@Composable
internal fun TvPairingContent(
    shortCode: String?,
    qrPayload: String?,
    expiresAtEpochMillis: Long? = null,
    showDevelopmentAction: Boolean,
    onRefresh: () -> Unit,
    onDevelopmentSession: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }

    // Live "time left" for the link code, driven by the server-provided
    // expiry. Reaching zero regenerates immediately instead of waiting for
    // the next poll tick to notice.
    var secondsLeft by remember(expiresAtEpochMillis) {
        mutableStateOf(
            expiresAtEpochMillis
                ?.let { ((it - System.currentTimeMillis()) / 1000L).toInt().coerceIn(0, 599) }
                ?: -1,
        )
    }
    LaunchedEffect(expiresAtEpochMillis) {
        if (expiresAtEpochMillis == null) return@LaunchedEffect
        while (isActive && secondsLeft > 0) {
            delay(1000)
            secondsLeft =
                ((expiresAtEpochMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        }
        if (secondsLeft == 0) onRefresh()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 92.dp, vertical = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.pairing_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            shortCode?.let { code ->
                Text(code.chunked(3).joinToString("  "), style = MaterialTheme.typography.displayMedium)
                if (secondsLeft >= 0) {
                    val mm = (secondsLeft / 60).toString()
                    val ss = (secondsLeft % 60).toString().padStart(2, '0')
                    Text(
                        stringResource(R.string.pairing_time_left, "$mm:$ss"),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (secondsLeft <= 30) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.pairing_expiry),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            TvAction(
                label = stringResource(R.string.refresh_code),
                icon = Icons.Outlined.Refresh,
                modifier = Modifier
                    .focusRequester(initialFocus)
                    .testTag(TvTestTags.PairingRefresh),
                onClick = onRefresh,
            )
            if (showDevelopmentAction) {
                TvAction(
                    label = stringResource(R.string.open_development_session),
                    icon = Icons.Outlined.Person,
                    modifier = Modifier.testTag(TvTestTags.PairingDevelopment),
                    onClick = onDevelopmentSession,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            qrPayload?.let { PairingQrCode(it, Modifier.fillMaxSize()) }
        }
    }
}

internal object TvTestTags {
    const val PairingRefresh = "pairing_refresh"
    const val PairingDevelopment = "pairing_development"
}

@Composable
private fun TvSignedIn(
    state: AppUiState,
    viewModel: AppViewModel,
    initialSearch: String?,
) {
    val initialDestination = if (initialSearch.isNullOrBlank()) TvDestination.HOME else TvDestination.SEARCH
    var destination by rememberSaveable { mutableStateOf(initialDestination) }
    var focusDestination by remember { mutableStateOf<TvDestination?>(initialDestination) }
    var pendingMediaKey by rememberSaveable { mutableStateOf<String?>(null) }
    val contentFocus = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val navFocus = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val contentStates = rememberSaveableStateHolder()
    var navHasFocus by remember { mutableStateOf(true) }
    val openMedia: (MediaPreview) -> Unit = { media ->
        pendingMediaKey = media.stableKey
        viewModel.loadDetail(media)
    }

    if (state.sourcePicker != null) {
        BackHandler { viewModel.closeSourcePicker() }
        TvSourcePickerScreen(
            picker = state.sourcePicker,
            onProvider = viewModel::selectSourceProvider,
            onSource = viewModel::playSource,
        )
        return
    }

    if (state.selectedDetail != null) {
        BackHandler {
            viewModel.clearDetail()
            if (pendingMediaKey == null) focusDestination = destination
        }
        TvDetailScreen(
            detail = state.selectedDetail,
            inLibrary = state.library.any { it.mediaKey == state.selectedDetail.preview.stableKey },
            onPlay = { viewModel.openSources(state.selectedDetail.preview, it) },
            onLibrary = { viewModel.addToLibrary(state.selectedDetail.preview) },
        )
        return
    }

    // Back from page content keeps the active destination and re-activates its
    // top-navigation item, per TV-NAV-03. Back with the navigation focused
    // (including the Home root) leaves the app, so repeated Back can never
    // loop, per TV-NAV-04.
    BackHandler(enabled = !navHasFocus) {
        navHasFocus = true
        focusDestination = destination
    }
    // Catalog data can change while a details screen is open. If the
    // originating item never re-composes to consume its restore key, hand
    // focus to the active navigation item so focus is never left unset.
    LaunchedEffect(pendingMediaKey) {
        if (pendingMediaKey == null) return@LaunchedEffect
        delay(1_000)
        if (pendingMediaKey != null) {
            pendingMediaKey = null
            focusDestination = destination
        }
    }
    Box(Modifier.fillMaxSize()) {
        TvTopNavigation(
            selectedDestination = destination,
            activeProfile = state.activeProfile,
            focusDestination = focusDestination,
            requesters = navFocus,
            contentDownRequester = contentFocus.getValue(destination),
            onFocusHandled = { focusDestination = null },
            onHasFocus = { navHasFocus = it },
            onDestination = { destination = it },
            modifier = Modifier.padding(
                start = TvLayoutTokens.screenHorizontalPadding,
                top = TvLayoutTokens.screenTopPadding,
                end = TvLayoutTokens.screenHorizontalPadding,
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = TvLayoutTokens.contentTopPadding),
        ) {
            // Per-destination saveable state (scroll positions, search query,
            // settings section) survives both detail round-trips and tab
            // switches, so returning re-composes the previously focused item
            // and mediaFocusRestore can reach it, per TV-NAV-02/TV-FOC-03.
            contentStates.SaveableStateProvider(destination.name) {
                when (destination) {
                    TvDestination.HOME -> TvHome(
                        state = state,
                        onMedia = openMedia,
                        initialFocusRequester = contentFocus.getValue(TvDestination.HOME),
                        onAddSource = {
                            destination = TvDestination.SETTINGS
                            focusDestination = TvDestination.SETTINGS
                        },
                        restoreMediaKey = pendingMediaKey,
                        onFocusRestored = { pendingMediaKey = null },
                    )

                    TvDestination.DISCOVER -> TvMediaGrid(
                        title = stringResource(R.string.discover),
                        media = state.allMedia,
                        onMedia = openMedia,
                        initialFocusRequester = contentFocus.getValue(TvDestination.DISCOVER),
                        restoreMediaKey = pendingMediaKey,
                        onFocusRestored = { pendingMediaKey = null },
                    )

                    TvDestination.SEARCH -> TvSearch(
                        initialSearch = initialSearch.orEmpty(),
                        state = state,
                        onSearch = viewModel::searchContent,
                        onMedia = openMedia,
                        initialFocusRequester = contentFocus.getValue(TvDestination.SEARCH),
                        restoreMediaKey = pendingMediaKey,
                        onFocusRestored = { pendingMediaKey = null },
                    )

                    TvDestination.LIBRARY -> TvMediaGrid(
                        title = stringResource(R.string.library),
                        media = state.library.map { it.preview },
                        onMedia = openMedia,
                        initialFocusRequester = contentFocus.getValue(TvDestination.LIBRARY),
                        restoreMediaKey = pendingMediaKey,
                        onFocusRestored = { pendingMediaKey = null },
                    )

                    TvDestination.SETTINGS -> TvSettings(
                        state = state,
                        viewModel = viewModel,
                        initialFocusRequester = contentFocus.getValue(TvDestination.SETTINGS),
                    )
                }
            }
        }
        if (state.refreshing) {
            Text(
                text = stringResource(R.string.updating),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 76.dp, end = TvLayoutTokens.screenHorizontalPadding),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TvHome(
    state: AppUiState,
    onMedia: (MediaPreview) -> Unit,
    onAddSource: () -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    var focusedCandidate by remember { mutableStateOf<MediaPreview?>(null) }
    var featured by remember(state.allMedia) { mutableStateOf(state.allMedia.firstOrNull()) }
    val allMedia = state.allMedia
    val continueWatching = remember(state.progress, allMedia) {
        val mediaByKey = allMedia.associateBy(MediaPreview::stableKey)
        state.progress
            .asSequence()
            .filter { !it.completed && it.fraction in 0.01f..0.98f }
            .sortedByDescending { it.updatedAtEpochMillis }
            .mapNotNull { progress ->
                mediaByKey[progress.mediaKey]?.let { media -> media to progress.fraction }
            }
            .toList()
    }
    LaunchedEffect(focusedCandidate) {
        focusedCandidate?.let {
            delay(TvMotionTokens.heroUpdateDelayMillis)
            featured = it
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
        verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.rowSpacing),
    ) {
        featured?.let { media ->
            item("hero") {
                TvHero(
                    media = media,
                    onMedia = onMedia,
                    modifier = Modifier
                        .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                        .mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored)
                        .focusRequester(initialFocusRequester),
                )
            }
        }
        if (continueWatching.isNotEmpty()) {
            item("continue-watching") {
                TvContinueWatchingRow(
                    items = continueWatching,
                    onMedia = onMedia,
                    onFocused = { focusedCandidate = it },
                    restoreMediaKey = restoreMediaKey,
                    onFocusRestored = onFocusRestored,
                )
            }
        }
        if (state.sections.isEmpty()) {
            item("empty") {
                Column(
                    modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvEmptyMark()
                    Text(
                        stringResource(R.string.install_first_addon),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.install_addon_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TvAction(
                        label = stringResource(R.string.install_addon),
                        icon = Icons.Outlined.Add,
                        modifier = Modifier.focusRequester(initialFocusRequester),
                        onClick = onAddSource,
                    )
                }
            }
        }
        items(state.sections, key = CatalogSection::id) { section ->
            TvCatalogRow(
                section = section,
                onMedia = onMedia,
                onFocused = { focusedCandidate = it },
                restoreMediaKey = restoreMediaKey,
                onFocusRestored = onFocusRestored,
            )
        }
    }
}

@Composable
private fun TvContinueWatchingRow(
    items: List<Pair<MediaPreview, Float>>,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.sectionTitleSpacing)) {
        Text(
            text = stringResource(R.string.continue_watching),
            modifier = Modifier
                .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
            contentPadding = PaddingValues(
                start = TvLayoutTokens.screenHorizontalPadding,
                end = TvLayoutTokens.screenHorizontalPadding,
                bottom = TvLayoutTokens.screenBottomPadding,
            ),
        ) {
            items(items, key = { it.first.stableKey }) { (media, progress) ->
                TvMediaCard(
                    media = media,
                    onClick = { onMedia(media) },
                    onFocused = { onFocused(media) },
                    modifier = Modifier.mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
                    showLabel = true,
                    revealLabelOnFocus = true,
                    watchProgress = progress,
                )
            }
        }
    }
}

@Composable
private fun TvHero(
    media: MediaPreview,
    onMedia: (MediaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TvLayoutTokens.heroHeight)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) TvFocusTokens.outlineWidth else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                shape = TvShapeTokens.hero,
            )
            .clip(TvShapeTokens.hero)
            .clickable(role = Role.Button) { onMedia(media) }
            .focusable()
            .semantics { contentDescription = media.name },
    ) {
        AnimatedContent(
            targetState = media,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    (
                        fadeIn(tween(TvMotionTokens.heroTransitionDurationMillis)) +
                            slideInHorizontally(tween(TvMotionTokens.heroTransitionDurationMillis)) { width -> width / 80 }
                        ) togetherWith (
                        fadeOut(tween(TvMotionTokens.heroTransitionDurationMillis)) +
                            slideOutHorizontally(tween(TvMotionTokens.heroTransitionDurationMillis)) { width -> -width / 100 }
                        )
                }
            },
            label = "hero artwork",
        ) { featuredMedia ->
            MediaArtwork(
                media = featuredMedia,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                preferBackdrop = true,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            0.48f to MaterialTheme.colorScheme.background.copy(alpha = 0.58f),
                            0.76f to Color.Transparent,
                        ),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.42f)),
                    ),
                )
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            0.42f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(520.dp)
                .padding(32.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = media.name,
                style = MaterialTheme.typography.displayMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            media.description?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(visible = focused) {
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .background(TvFocusTokens.focusedContainer, TvShapeTokens.button)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvIcon(
                        icon = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = TvFocusTokens.focusedContent,
                    )
                    Text(
                        text = stringResource(R.string.view_details),
                        style = MaterialTheme.typography.titleSmall,
                        color = TvFocusTokens.focusedContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvCatalogRow(
    section: CatalogSection,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.sectionTitleSpacing)) {
        Text(
            text = section.title,
            modifier = Modifier
                .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        section.errorMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        } ?: LazyRow(
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
            contentPadding = PaddingValues(
                start = TvLayoutTokens.screenHorizontalPadding,
                end = TvLayoutTokens.screenHorizontalPadding,
                bottom = TvLayoutTokens.screenBottomPadding,
            ),
        ) {
            items(section.items, key = MediaPreview::stableKey) { media ->
                TvMediaCard(
                    media = media,
                    onClick = { onMedia(media) },
                    onFocused = { onFocused(media) },
                    modifier = Modifier.mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
                    showLabel = true,
                    revealLabelOnFocus = true,
                )
            }
        }
    }
}

@Composable
private fun TvMediaGrid(
    title: String,
    media: List<MediaPreview>,
    onMedia: (MediaPreview) -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = title,
            modifier = Modifier
                .padding(
                    start = TvLayoutTokens.screenHorizontalPadding,
                    end = TvLayoutTokens.screenHorizontalPadding,
                    bottom = 16.dp,
                )
                .semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        if (media.isEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvEmptyMark()
                Text(
                    stringResource(R.string.nothing_here),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = TvLayoutTokens.screenHorizontalPadding,
                    end = TvLayoutTokens.screenHorizontalPadding,
                    bottom = TvLayoutTokens.bottomListPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                itemsIndexed(media, key = { _, item -> item.stableKey }) { index, item ->
                    TvMediaCard(
                        media = item,
                        onClick = { onMedia(item) },
                        modifier = (if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            .mediaFocusRestore(item.stableKey, restoreMediaKey, onFocusRestored),
                        showLabel = true,
                        compactLandscape = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSearch(
    initialSearch: String,
    state: AppUiState,
    onSearch: (String) -> Unit,
    onMedia: (MediaPreview) -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(initialSearch) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(query) { onSearch(query) }
    val matches = if (query.isBlank()) state.allMedia else state.searchResults
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .focusRequester(initialFocusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        focusManager.moveFocus(FocusDirection.Down)
                    } else {
                        false
                    }
                }
                .background(MaterialTheme.colorScheme.background, TvShapeTokens.card)
                .border(
                    width = if (focused) TvFocusTokens.outlineWidth else 1.dp,
                    color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border,
                    shape = TvShapeTokens.card,
                )
                .padding(horizontal = 28.dp, vertical = 20.dp),
            decorationBox = { input ->
                if (query.isBlank()) {
                    Text(
                        stringResource(R.string.search_tv_hint),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                input()
            },
        )
        Spacer(Modifier.height(32.dp))
        if (matches.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TvEmptyMark()
                Text(
                    stringResource(R.string.nothing_here),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
                contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
            ) {
                items(matches, key = MediaPreview::stableKey) { media ->
                    TvMediaCard(
                        media = media,
                        onClick = { onMedia(media) },
                        modifier = Modifier.mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
                        showLabel = true,
                        revealLabelOnFocus = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSourcePickerScreen(
    picker: SourcePickerState,
    onProvider: (String?) -> Unit,
    onSource: (StreamCandidate) -> Unit,
) {
    val filtersFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { filtersFocus.requestFocus() }
    Box(Modifier.fillMaxSize()) {
        MediaArtwork(
            media = picker.media,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            preferBackdrop = true,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                            0.34f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                            0.58f to MaterialTheme.colorScheme.background.copy(alpha = 0.38f),
                            0.76f to MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                            1f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                        ),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.34f),
                            0.64f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = TvLayoutTokens.screenHorizontalPadding,
                    top = TvLayoutTokens.screenTopPadding,
                    end = TvLayoutTokens.screenHorizontalPadding,
                    bottom = TvLayoutTokens.screenBottomPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
        ) {
            TvSourceMediaSummary(
                picker = picker,
                modifier = Modifier.width(350.dp).fillMaxHeight(),
            )
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item("all") {
                        TvSourceFilterChip(
                            label = stringResource(R.string.all_sources),
                            selected = picker.selectedProviderId == null,
                            onClick = { onProvider(null) },
                            modifier = Modifier.focusRequester(filtersFocus),
                        )
                    }
                    items(picker.providerIds, key = { it }) { providerId ->
                        TvSourceFilterChip(
                            label = picker.providerLabels[providerId] ?: providerId,
                            selected = picker.selectedProviderId == providerId,
                            onClick = { onProvider(providerId) },
                        )
                    }
                }
                picker.failures.values.forEach { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                if (picker.loading) {
                    Text(stringResource(R.string.loading_sources), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (picker.visibleSources.isEmpty()) {
                    TvEmptyMark()
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
                            TvFocusableSurface(
                                onClick = { onSource(source) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 104.dp),
                                containerColor = TvSurfaceTokens.card,
                            ) { focused ->
                                val primaryColor = if (focused) {
                                    TvFocusTokens.focusedContent
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                }
                                val secondaryColor = primaryColor.copy(alpha = 0.76f)
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    TvIcon(
                                        Icons.Outlined.PlayArrow,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (presentation.badges.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                presentation.badges.forEach { badge ->
                                                    TvSourceBadge(badge, focused)
                                                }
                                            }
                                        }
                                        Text(
                                            presentation.title,
                                            color = primaryColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        presentation.description?.let { description ->
                                            Text(
                                                description,
                                                color = secondaryColor,
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
                                                color = secondaryColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSourceFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        containerColor = if (selected) TvSurfaceTokens.selectedFilter else TvSurfaceTokens.card,
    ) { focused ->
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvSourceBadge(label: String, focused: Boolean) {
    Text(
        text = label,
        modifier = Modifier
            .background(
                color = if (focused) {
                    TvFocusTokens.focusedContent.copy(alpha = 0.10f)
                } else {
                    TvFocusTokens.beam.copy(alpha = 0.16f)
                },
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
private fun TvSourceMediaSummary(
    picker: SourcePickerState,
    modifier: Modifier = Modifier,
) {
    val episodeNumber = picker.episode?.let { episode ->
        listOfNotNull(
            episode.season?.let { "S$it" },
            episode.episode?.let { "E$it" },
        ).joinToString(" • ").ifBlank { null }
    }
    val metadata = buildList {
        episodeNumber?.let(::add)
        picker.media.contentRating?.takeIf(String::isNotBlank)?.let(::add)
        picker.media.releaseYear?.let { add(it.toString()) }
        picker.media.genres.take(2).joinToString(", ").takeIf(String::isNotBlank)?.let(::add)
        picker.media.rating?.let { add("★ $it") }
    }.joinToString("  •  ")
    val description = picker.episode?.overview ?: picker.media.description

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!picker.media.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = picker.media.logoUrl,
                contentDescription = picker.media.name,
                modifier = Modifier.width(300.dp).height(92.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
            )
        } else {
            Text(
                text = picker.media.name,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        picker.episode?.title?.let { title ->
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metadata.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        description?.takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvDetailScreen(
    detail: MediaDetail?,
    inLibrary: Boolean,
    onPlay: (Episode?) -> Unit,
    onLibrary: () -> Unit,
) {
    if (detail == null) return
    val playFocus = remember(detail.preview.stableKey) { FocusRequester() }
    val reducedMotion = rememberReducedMotion()
    val libraryPulse = remember(detail.preview.stableKey) { Animatable(1f) }
    var previousLibraryState by remember(detail.preview.stableKey) { mutableStateOf(inLibrary) }
    LaunchedEffect(detail.preview.stableKey) { playFocus.requestFocus() }
    LaunchedEffect(inLibrary) {
        if (inLibrary && !previousLibraryState && !reducedMotion) {
            libraryPulse.animateTo(
                1.04f,
                tween(TvMotionTokens.confirmationPulseDurationMillis),
            )
            libraryPulse.animateTo(
                1f,
                tween(TvMotionTokens.confirmationPulseDurationMillis),
            )
        } else {
            libraryPulse.snapTo(1f)
        }
        previousLibraryState = inLibrary
    }
    Box(Modifier.fillMaxSize()) {
        MediaArtwork(
            media = detail.preview,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            preferBackdrop = true,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                            0.50f to MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                            0.78f to MaterialTheme.colorScheme.background.copy(alpha = 0.20f),
                        ),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
        ) {
            item("details-header") {
                Column(
                    modifier = Modifier
                        .height(440.dp)
                        .width(520.dp)
                        .padding(start = TvLayoutTokens.screenHorizontalPadding, top = 138.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!detail.preview.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = detail.preview.logoUrl,
                            contentDescription = detail.preview.name,
                            modifier = Modifier.width(330.dp).height(78.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = detail.preview.name,
                            style = MaterialTheme.typography.displaySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    detail.preview.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = listOfNotNull(
                            detail.preview.contentRating,
                            detail.preview.releaseYear?.toString(),
                            detail.preview.genres.take(2).joinToString(", ").ifBlank { null },
                            detail.runtimeMinutes?.let { stringResource(R.string.minutes_format, it) },
                            detail.preview.rating?.let { "★ $it" },
                        ).joinToString("  •  "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvAction(
                            label = stringResource(R.string.play),
                            icon = Icons.Outlined.PlayArrow,
                            modifier = Modifier.focusRequester(playFocus),
                            onClick = { onPlay(null) },
                        )
                        TvAction(
                            label = stringResource(if (inLibrary) R.string.in_library else R.string.add_to_library),
                            icon = if (inLibrary) Icons.Outlined.Check else Icons.Outlined.Add,
                            modifier = Modifier.graphicsLayer {
                                scaleX = libraryPulse.value
                                scaleY = libraryPulse.value
                            },
                            enabled = !inLibrary,
                            onClick = onLibrary,
                        )
                    }
                    if (detail.cast.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cast),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = detail.cast.take(6).joinToString("  •  ") { name ->
                                    HtmlCompat.fromHtml(name, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (detail.episodes.isNotEmpty()) {
                item("episodes-title") {
                    Text(
                        text = stringResource(R.string.episodes),
                        modifier = Modifier.padding(
                            start = TvLayoutTokens.screenHorizontalPadding,
                            end = TvLayoutTokens.screenHorizontalPadding,
                            bottom = 16.dp,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item("episodes") {
                    LazyRow(
                        contentPadding = PaddingValues(
                            start = TvLayoutTokens.screenHorizontalPadding,
                            end = TvLayoutTokens.screenHorizontalPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
                    ) {
                        items(detail.episodes, key = Episode::id) { episode ->
                            TvEpisodeCard(
                                episode = episode,
                                fallbackArtworkUrl = detail.preview.backgroundUrl ?: detail.preview.posterUrl,
                                onClick = { onPlay(episode) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEpisodeCard(
    episode: Episode,
    fallbackArtworkUrl: String?,
    onClick: () -> Unit,
) {
    val artworkUrl = episode.thumbnailUrl ?: fallbackArtworkUrl
    TvFocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .width(TvLayoutTokens.landscapeCardWidth)
            .height(TvLayoutTokens.landscapeCardHeight),
        containerColor = TvSurfaceTokens.elevated,
        focusedContainerColor = Color.Transparent,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvSurfaceTokens.elevated),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            val footerColor = if (focused) TvFocusTokens.focusedContainer else TvSurfaceTokens.elevated
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                footerColor.copy(alpha = 0.90f),
                                footerColor,
                            ),
                        ),
                    )
                    .padding(start = 16.dp, top = 28.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(
                        R.string.episode_format,
                        episode.season ?: 0,
                        episode.episode ?: 0,
                    ),
                    color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    episode.title,
                    color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class TvSettingsSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    PROFILES(R.string.profiles, Icons.Outlined.Person),
    SOURCES(R.string.addons, Icons.Outlined.Add),
    ABOUT(R.string.about, Icons.Outlined.Info),
}

@Composable
private fun TvSettings(
    state: AppUiState,
    viewModel: AppViewModel,
    initialFocusRequester: FocusRequester,
) {
    var section by rememberSaveable { mutableStateOf(TvSettingsSection.PROFILES) }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
    ) {
        Column(
            modifier = Modifier.width(TvLayoutTokens.settingsMenuWidth),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TvSettingsSection.entries.forEachIndexed { index, item ->
                TvSettingsMenuItem(
                    section = item,
                    selected = section == item,
                    modifier = if (index == 0) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    },
                    onFocused = { section = item },
                    onClick = { section = item },
                )
            }
        }
        Spacer(Modifier.width(72.dp))
        Box(Modifier.width(TvLayoutTokens.settingsContentWidth).fillMaxHeight()) {
            when (section) {
                TvSettingsSection.PROFILES -> TvProfilesSettings(state, viewModel)
                TvSettingsSection.SOURCES -> TvSourcesSettings(state, viewModel)
                TvSettingsSection.ABOUT -> TvAboutSettings()
            }
        }
    }
}

@Composable
private fun TvSettingsMenuItem(
    section: TvSettingsSection,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(section.labelRes)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .background(
                color = when {
                    focused -> TvFocusTokens.selectedNavigationContainer
                    selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f)
                    else -> Color.Transparent
                },
                shape = TvShapeTokens.card,
            )
            .clip(TvShapeTokens.card)
            .clickable(role = Role.Tab, onClick = onClick)
            .focusable()
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvIcon(
            icon = section.icon,
            contentDescription = null,
            tint = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TvProfilesSettings(state: AppUiState, viewModel: AppViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
    ) {
        item {
            Text(stringResource(R.string.profiles), style = MaterialTheme.typography.headlineSmall)
        }
        items(state.profiles, key = { it.id }) { profile ->
            TvSettingsRow(onClick = { viewModel.selectProfile(profile.id) }) { focused ->
                TvProfileAvatar(
                    name = profile.name,
                    avatarKey = profile.avatarKey,
                    focused = focused,
                    selected = state.activeProfileId == profile.id,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = profile.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurface,
                )
                if (state.activeProfileId == profile.id) {
                    Text(
                        stringResource(R.string.active),
                        color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSourcesSettings(state: AppUiState, viewModel: AppViewModel) {
    var providerAddress by rememberSaveable { mutableStateOf("") }
    var addressFocused by remember { mutableStateOf(false) }
    val installFocusRequester = remember { FocusRequester() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
    ) {
        item {
            Text(stringResource(R.string.addons), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvSurfaceTokens.elevated, TvShapeTokens.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.add_sources_phone_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.add_sources_phone_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (BuildConfig.DEBUG) {
            item {
                BasicTextField(
                    value = providerAddress,
                    onValueChange = { providerAddress = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .onFocusChanged { addressFocused = it.isFocused }
                        .focusProperties { down = installFocusRequester }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                installFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                        .background(MaterialTheme.colorScheme.background, TvShapeTokens.card)
                        .border(
                            width = if (addressFocused) TvFocusTokens.outlineWidth else 1.dp,
                            color = if (addressFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border,
                            shape = TvShapeTokens.card,
                        )
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    decorationBox = { input ->
                        if (providerAddress.isBlank()) {
                            Text(
                                stringResource(R.string.https_manifest_address),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        input()
                    },
                )
            }
            item {
                TvAction(
                    label = stringResource(R.string.install_developer_source),
                    icon = Icons.Outlined.Add,
                    enabled = providerAddress.startsWith("https://") || providerAddress.startsWith("http://"),
                    modifier = Modifier.focusRequester(installFocusRequester),
                    onClick = { viewModel.addProvider(providerAddress) },
                )
            }
        }
        item {
            TvAction(
                label = stringResource(R.string.refresh_catalogs),
                icon = Icons.Outlined.Refresh,
                enabled = state.providers.isNotEmpty() && !state.refreshing,
                onClick = viewModel::refreshContent,
            )
        }
        if (state.providers.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_addons_installed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.providers, key = { it.id }) { provider ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvSurfaceTokens.elevated, TvShapeTokens.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(provider.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                if (provider.sortOrder < 0) {
                                    R.string.included_catalog
                                } else if (provider.enabled) {
                                    R.string.enabled
                                } else {
                                    R.string.disabled
                                },
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (provider.sortOrder >= 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvAction(
                            label = stringResource(if (provider.enabled) R.string.disable else R.string.enable),
                            icon = Icons.Outlined.Refresh,
                            onClick = { viewModel.toggleProvider(provider.id, !provider.enabled) },
                        )
                        TvAction(
                            label = stringResource(R.string.remove),
                            icon = Icons.Outlined.Delete,
                            onClick = { viewModel.removeProvider(provider.id) },
                        )
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.addon_addresses_hidden),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TvAboutSettings() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.about), style = MaterialTheme.typography.headlineSmall)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TvSurfaceTokens.elevated, TvShapeTokens.card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.about_lamphaus_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TvSettingsRow(
    onClick: () -> Unit,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        containerColor = TvSurfaceTokens.elevated,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = { content(focused) },
        )
    }
}
