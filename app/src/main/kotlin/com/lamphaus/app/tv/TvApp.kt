package com.lamphaus.app.tv

import androidx.activity.compose.BackHandler
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.core.text.HtmlCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.Switch
import com.lamphaus.app.BuildConfig
import com.lamphaus.app.R
import com.lamphaus.app.ui.ArtworkResolver
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.ArtworkEditorState
import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.app.ui.CatalogBrowseTarget
import com.lamphaus.app.ui.AppViewModel
import com.lamphaus.app.ui.CINEMETA_PROVIDER_ID
import com.lamphaus.app.ui.ContentMenuAction
import com.lamphaus.app.ui.ContentMenuOrigin
import com.lamphaus.app.ui.ContentMenuState
import com.lamphaus.app.ui.ContentMenuTarget
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.MediaMetadataPresentation
import com.lamphaus.app.ui.SelectionCheckmark
import com.lamphaus.app.ui.SourcePickerState
import com.lamphaus.app.ui.artworkImageUrl
import com.lamphaus.app.ui.mediaFocusRestore
import com.lamphaus.app.ui.metadataPresentation
import com.lamphaus.app.ui.numberParts
import com.lamphaus.app.ui.sourcePresentation
import com.lamphaus.app.ui.sourceItemKey
import com.lamphaus.app.ui.SpoilerBlurLayer
import com.lamphaus.app.ui.SpoilerContent
import com.lamphaus.app.ui.shouldBlur
import com.lamphaus.app.ui.HOME_CATALOG_SCROLL_SETTLE_MILLIS
import com.lamphaus.app.ui.isResumable
import com.lamphaus.app.ui.shouldPrefetchHomeCatalogBatch
import com.lamphaus.app.ui.isRenderableHomeCatalogSection
import com.lamphaus.app.ui.menuActions
import com.lamphaus.app.ui.RatingBadge
import com.lamphaus.app.ui.RatingBadgeChip
import com.lamphaus.app.ui.metadataImdbScore
import com.lamphaus.app.ui.orderedRatingScores
import com.lamphaus.app.ui.ratingValueText

import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.model.ArtworkAsset
import com.lamphaus.core.model.ArtworkLookupStatus
import com.lamphaus.core.model.PersonCredit
import com.lamphaus.core.model.ArtworkProviderId
import com.lamphaus.core.model.ArtworkProviderResult
import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.WatchProgress
import com.lamphaus.core.model.DetailEnrichment
import com.lamphaus.core.model.RatingSourceScore
import com.lamphaus.core.model.MediaType
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.StreamCandidate
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged


@Composable
fun TvApp(
    viewModel: AppViewModel,
    initialSearch: String?,
    onPlay: (PlaybackRequest) -> Unit,
    onExternalPlay: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var restoreMenuFocus by remember { mutableStateOf(false) }
    var suppressMenuOpeningKey by remember { mutableStateOf(false) }
    val completedVideoIds = remember(state.progress) {
        state.progress.asSequence().filter { it.completed }.map { it.videoId }.toSet()
    }
    LaunchedEffect(state.contentMenu.target, restoreMenuFocus) {
        if (state.contentMenu.target == null && restoreMenuFocus) {
            restoreMenuFocus = false
            withFrameNanos { }
            menuReturnFocus?.let { requester -> runCatching { requester.requestFocus() } }
            menuReturnFocus = null
        }
    }
    LamphausTvTheme {
        val artworkResolver = remember(state.artworkOverrides) {
            ArtworkResolver(state.artworkOverrides.associateBy { it.mediaKey })
        }
        CompositionLocalProvider(
            LocalArtworkResolver provides artworkResolver,
            LocalTvContentMenuEnvironment provides TvContentMenuEnvironment(
                completedVideoIds = completedVideoIds,
                onRequest = { target, returnFocus ->
                    menuReturnFocus = returnFocus
                    suppressMenuOpeningKey = true
                    viewModel.openContentMenu(target)
                },
            ),
        ) {
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
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        val opensMenu = event.key == Key.DirectionCenter ||
                            event.key == Key.Enter ||
                            event.key == Key.Menu
                        if (suppressMenuOpeningKey && opensMenu) {
                            if (event.type == KeyEventType.KeyUp) suppressMenuOpeningKey = false
                            true
                        } else {
                            false
                        }
                    },
            ) {
                when (state.account) {
                    AccountState.Loading -> TvLoading()
                    AccountState.SignedOut -> TvPairingScreen(state, viewModel)
                    is AccountState.SignedIn -> TvSignedIn(state, viewModel, initialSearch)
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
                        contentAlignment = Alignment.BottomCenter,
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
                if (state.account is AccountState.SignedIn && state.contentMenu.target != null) {
                    TvContentMenuDialog(
                        menu = state.contentMenu,
                        inLibrary = state.contentMenu.target?.let { target ->
                            state.library.any { it.mediaKey == target.media.stableKey }
                        } == true,
                        suppressOpeningKey = suppressMenuOpeningKey,
                        onOpeningKeyReleased = { suppressMenuOpeningKey = false },
                        onDismiss = {
                            suppressMenuOpeningKey = false
                            restoreMenuFocus = true
                            viewModel.dismissContentMenu()
                        },
                        onAction = { action ->
                            restoreMenuFocus = action == ContentMenuAction.ToggleLibrary ||
                                action == ContentMenuAction.MarkWatched ||
                                action == ContentMenuAction.MarkUnwatched ||
                                action == ContentMenuAction.RemoveFromContinueWatching
                            viewModel.onContentMenuAction(action)
                        },
                    )
                }
            }
        }
        }
    }
}

/** Centered, D-pad-first renderer for the shared content-menu model. */
@Composable
private fun TvContentMenuDialog(
    menu: ContentMenuState,
    inLibrary: Boolean,
    suppressOpeningKey: Boolean,
    onOpeningKeyReleased: () -> Unit,
    onDismiss: () -> Unit,
    onAction: (ContentMenuAction) -> Unit,
) {
    val target = menu.target ?: return
    val actions = target.menuActions()
    val firstActionFocus = remember(target.media.stableKey, target.progress?.videoId) { FocusRequester() }
    LaunchedEffect(target.media.stableKey, target.progress?.videoId, menu.resolving) {
        if (!menu.resolving) {
            withFrameNanos { }
            firstActionFocus.requestFocus()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(520.dp)
                .onPreviewKeyEvent { event ->
                    val opensMenu = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.Menu
                    if (suppressOpeningKey && opensMenu) {
                        if (event.type == KeyEventType.KeyUp) onOpeningKeyReleased()
                        true
                    } else {
                        false
                    }
                },
            shape = TvShapeTokens.hero,
            colors = SurfaceDefaults.colors(
                containerColor = TvSurfaceTokens.elevated,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 80.dp, height = 120.dp)
                            .clip(TvShapeTokens.card)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        MediaArtwork(target.media, Modifier.fillMaxSize())
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = target.media.name,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        target.progress?.episodeLabel?.let { label ->
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (menu.resolving) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.content_menu_resolving))
                    }
                }
                if (menu.resolutionError) {
                    Text(
                        text = stringResource(R.string.content_menu_resolution_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TvAction(
                        label = stringResource(R.string.retry),
                        icon = Icons.Outlined.Refresh,
                        onClick = { onAction(ContentMenuAction.StartFromBeginning) },
                    )
                }
                actions.forEachIndexed { index, action ->
                    TvAction(
                        label = tvContentMenuLabel(action, target, inLibrary),
                        icon = tvContentMenuIcon(action, inLibrary),
                        enabled = !menu.resolving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstActionFocus) else Modifier),
                        onClick = { onAction(action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun tvContentMenuLabel(
    action: ContentMenuAction,
    target: ContentMenuTarget,
    inLibrary: Boolean,
): String = when (action) {
    ContentMenuAction.ViewDetails -> stringResource(
        if (target.episode != null ||
            target.origin == ContentMenuOrigin.CONTINUE_WATCHING && target.media.type == MediaType.SERIES
        ) {
            R.string.content_menu_view_series_details
        } else {
            R.string.content_menu_view_details
        },
    )
    ContentMenuAction.ToggleLibrary -> stringResource(
        if (inLibrary) R.string.content_menu_remove_library else R.string.content_menu_add_library,
    )
    ContentMenuAction.MarkWatched -> stringResource(
        if (target.episode != null) R.string.content_menu_mark_episode_watched else R.string.content_menu_mark_watched,
    )
    ContentMenuAction.MarkUnwatched -> stringResource(R.string.content_menu_mark_unwatched)
    ContentMenuAction.RemoveFromContinueWatching -> stringResource(R.string.content_menu_remove_continue)
    ContentMenuAction.StartFromBeginning -> stringResource(R.string.content_menu_start_beginning)
}

private fun tvContentMenuIcon(action: ContentMenuAction, inLibrary: Boolean): ImageVector = when (action) {
    ContentMenuAction.ViewDetails -> Icons.Outlined.Info
    ContentMenuAction.ToggleLibrary -> if (inLibrary) Icons.Outlined.Check else Icons.Outlined.BookmarkBorder
    ContentMenuAction.MarkWatched -> Icons.Outlined.Check
    ContentMenuAction.MarkUnwatched -> Icons.Outlined.Close
    ContentMenuAction.RemoveFromContinueWatching -> Icons.Outlined.Delete
    ContentMenuAction.StartFromBeginning -> Icons.Outlined.Replay
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

private const val PAIRING_SESSION_TTL_MILLIS = 5 * 60_000L

internal fun pairingCountdownExpiry(expiresAtEpochMillis: Long?, nowEpochMillis: Long): Long? =
    expiresAtEpochMillis?.coerceAtMost(nowEpochMillis + PAIRING_SESSION_TTL_MILLIS)

internal fun pairingSecondsLeft(expiresAtEpochMillis: Long?, nowEpochMillis: Long): Int =
    expiresAtEpochMillis
        ?.let { ((it - nowEpochMillis) / 1000L).toInt().coerceAtLeast(0) }
        ?: -1

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
    val countdownExpiry = remember(expiresAtEpochMillis) {
        pairingCountdownExpiry(expiresAtEpochMillis, System.currentTimeMillis())
    }
    var secondsLeft by remember(countdownExpiry) {
        mutableStateOf(pairingSecondsLeft(countdownExpiry, System.currentTimeMillis()))
    }
    LaunchedEffect(countdownExpiry) {
        if (countdownExpiry == null) return@LaunchedEffect
        while (isActive && secondsLeft > 0) {
            delay(1000)
            secondsLeft = pairingSecondsLeft(countdownExpiry, System.currentTimeMillis())
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
    var lastFocusedMedia by remember { mutableStateOf(state.allMedia.firstOrNull()) }
    val contentFocus = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val navFocus = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val contentStates = rememberSaveableStateHolder()
    val watchedEpisodeIds = remember(state.progress) {
        state.progress.asSequence()
            .filter { it.completed }
            .map { it.videoId }
            .toSet()
    }
    var navHasFocus by remember { mutableStateOf(true) }
    val openMedia: (MediaPreview) -> Unit = { media ->
        pendingMediaKey = media.stableKey
        viewModel.loadDetail(media)
    }
    val ambientMedia = when (destination) {
        TvDestination.HOME,
        TvDestination.DISCOVER,
        TvDestination.LIBRARY,
        TvDestination.SEARCH,
        -> lastFocusedMedia
        TvDestination.SETTINGS -> null
    }
    val ambientState = rememberTvContentAmbient(ambientMedia)

    if (state.sourcePicker != null) {
        BackHandler { viewModel.closeSourcePicker() }
        TvSourcePickerScreen(
            picker = state.sourcePicker,
            onProvider = viewModel::selectSourceProvider,
            onSource = viewModel::playSource,
        )
        return
    }

    if (state.artworkEditor != null) {
        BackHandler { viewModel.closeArtworkEditor() }
        TvArtworkEditorScreen(
            editor = state.artworkEditor,
            onBack = viewModel::closeArtworkEditor,
            onPosterSelected = viewModel::selectArtworkPoster,
            onBackdropSelected = viewModel::selectArtworkBackdrop,
            onLogoSelected = viewModel::selectArtworkLogo,
            onProviderSelected = viewModel::selectArtworkProvider,
            onSave = viewModel::saveArtworkSelection,
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
            enrichment = state.detailEnrichment,
            enrichmentFailed = state.detailEnrichmentFailed,
            inLibrary = state.library.any { it.mediaKey == state.selectedDetail.preview.stableKey },
            watchedEpisodeIds = watchedEpisodeIds,
            spoilerProtection = state.spoilerProtection,
            progress = state.progress,
            onPlay = { viewModel.openSources(state.selectedDetail.preview, it) },
            onOpenMedia = openMedia,
            onFocusedMedia = { lastFocusedMedia = it },
            onLibrary = { viewModel.addToLibrary(state.selectedDetail.preview) },
            onEditArtwork = { viewModel.openArtworkEditor(state.selectedDetail.preview) },
            onRetryEnrichment = viewModel::retryDetailEnrichment,
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
    CompositionLocalProvider(LocalTvContentAccent provides ambientState.accent) {
        Box(Modifier.fillMaxSize()) {
            if (destination != TvDestination.SETTINGS) {
                TvContentAmbientBackground(state = ambientState)
            }
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
                .padding(top = TvLayoutTokens.contentTopPadding)
                .tvContentFocusBoundary(
                    topNavigationRequester = navFocus.getValue(destination),
                    leftNavigationRequester = if (destination == TvDestination.SETTINGS) {
                        contentFocus.getValue(TvDestination.SETTINGS)
                    } else {
                        FocusRequester.Default
                    },
                ),
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
                        onFocused = { lastFocusedMedia = it },
                        onAddSource = {
                            destination = TvDestination.SETTINGS
                            focusDestination = TvDestination.SETTINGS
                        },
                        onLoadMore = viewModel::loadMoreCatalog,
                        onRetry = viewModel::retryCatalogPage,
                        onLoadMoreHome = viewModel::loadMoreHomeCatalogSections,
                        onRetryHome = viewModel::retryHomeCatalogSections,
                        initialFocusRequester = contentFocus.getValue(TvDestination.HOME),
                        restoreMediaKey = pendingMediaKey,
                        onFocusRestored = { pendingMediaKey = null },
                    )


                    TvDestination.DISCOVER -> TvDiscover(
                        state = state,
                        onPrepare = viewModel::prepareDiscover,
                        onBrowseCatalog = viewModel::selectBrowseCatalog,
                        onBrowseGenre = viewModel::selectBrowseGenre,
                        onClearGenre = viewModel::clearBrowseGenre,
                        onLoadMore = viewModel::loadMoreBrowse,
                        onRetry = viewModel::retryBrowse,
                        onMedia = openMedia,
                        onFocused = { lastFocusedMedia = it },
                        initialFocusRequester = contentFocus.getValue(TvDestination.DISCOVER),
                        restoreMediaKey = pendingMediaKey,
                        onFocusRestored = { pendingMediaKey = null },
                    )

                    TvDestination.SEARCH -> TvSearch(
                        initialSearch = initialSearch.orEmpty(),
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
                        onFocused = { lastFocusedMedia = it },
                        initialFocusRequester = contentFocus.getValue(TvDestination.SEARCH),
                        restoreMediaKey = pendingMediaKey,
                        onFocusRestored = { pendingMediaKey = null },
                    )

                    TvDestination.LIBRARY -> TvMediaGrid(
                        title = stringResource(R.string.library),
                        media = state.library.map { it.preview },
                        onMedia = openMedia,
                        onFocused = { lastFocusedMedia = it },
                        initialFocusRequester = contentFocus.getValue(TvDestination.LIBRARY),
                        restoreMediaKey = pendingMediaKey,
                        onFocusRestored = { pendingMediaKey = null },
                    )

                    TvDestination.SETTINGS -> TvSettings(
                        state = state,
                        viewModel = viewModel,
                        sectionFocusRequester = contentFocus.getValue(TvDestination.SETTINGS),
                        topNavigationRequester = navFocus.getValue(TvDestination.SETTINGS),
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
}

@Composable
private fun TvHome(
    state: AppUiState,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    onAddSource: () -> Unit,
    onLoadMore: (String) -> Unit,
    onRetry: (String) -> Unit,
    onLoadMoreHome: () -> Unit,
    onRetryHome: () -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    var focusedCandidate by remember { mutableStateOf<MediaPreview?>(null) }
    val allMedia = state.allMedia
    // The carousel is a signature part of the Home screen, so it must never
    // disappear while the catalog loads progressively. The user's selection
    // survives catalog updates; only the displayed value falls back to the
    // first item when that selection is no longer part of the catalog.
    var featuredSelection by remember { mutableStateOf<MediaPreview?>(null) }
    val featured = featuredSelection ?: allMedia.firstOrNull()
    val heroItems = remember(allMedia) { allMedia.distinctBy(MediaPreview::stableKey).take(5) }
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
    LaunchedEffect(focusedCandidate) {
        focusedCandidate?.let {
            delay(TvMotionTokens.heroUpdateDelayMillis)
            featuredSelection = it
        }
    }
    val visibleHomeSections = remember(state.sections) {
        state.sections.filter(CatalogSection::isRenderableHomeCatalogSection)
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
    val moveCarousel: (Int) -> Unit = { delta ->
        if (heroItems.size > 1) {
            val currentIndex = heroItems.indexOfFirst { it.stableKey == featured?.stableKey }.coerceAtLeast(0)
            val next = heroItems[(currentIndex + delta + heroItems.size) % heroItems.size]
            featuredSelection = next
            focusedCandidate = next
            onFocused(next)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
        verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.rowSpacing),
    ) {
        if (featured != null) {
            val media = featured
            item("hero") {
                TvHero(
                    kenBurnsEnabled = state.kenBurnsEnabled,
                    media = media,
                    onMedia = onMedia,
                    onFocused = { focusedCandidate = media; onFocused(media) },
                    carouselItems = heroItems,
                    onPrevious = { moveCarousel(-1) },
                    onNext = { moveCarousel(1) },
                    modifier = Modifier
                        .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                        .mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored)
                        .focusRequester(initialFocusRequester),
                )
            }
        } else if (
            state.initialContentLoading ||
            state.homeCatalogBatch.loadingMore ||
            state.sections.any(CatalogSection::initialLoading)
        ) {
            // Keep the hero slot present (and focusable) while the first
            // catalog window loads, so D-pad Down from the top navigation
            // always has a focus target inside the content area.
            item("hero") {
                TvHeroLoadingSkeleton(
                    modifier = Modifier
                        .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                        .focusRequester(initialFocusRequester),
                )
            }
        }
        if (continueWatching.isNotEmpty()) {
            item("continue-watching") {
                TvContinueWatchingRow(
                    items = continueWatching,
                    onMedia = onMedia,
                    onFocused = { focusedCandidate = it; onFocused(it) },
                    restoreMediaKey = restoreMediaKey,
                    onFocusRestored = onFocusRestored,
                )
            }
        }
        if (
            !state.initialContentLoading &&
            !state.homeCatalogBatch.loadingMore &&
            !state.homeCatalogBatch.loadMoreFailed &&
            !state.homeCatalogBatch.hasMore &&
            state.sections.isEmpty()
        ) {
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
        items(visibleHomeSections, key = CatalogSection::id) { section ->
            TvCatalogRow(
                section = section,
                onMedia = onMedia,
                onFocused = { focusedCandidate = it; onFocused(it) },
                onLoadMore = { onLoadMore(section.id) },
                onRetry = { onRetry(section.id) },
                restoreMediaKey = restoreMediaKey,
                onFocusRestored = onFocusRestored,
            )
        }
        when {
            state.homeCatalogBatch.loadMoreFailed -> item("home-catalog-retry") {
                TvAction(
                    label = stringResource(R.string.retry),
                    icon = Icons.Outlined.Refresh,
                    modifier = Modifier.focusRequester(initialFocusRequester),
                    onClick = onRetryHome,
                )
            }
            state.homeCatalogBatch.loadingMore && state.sections.none(CatalogSection::initialLoading) -> {
                item("home-catalog-loading") {
                    TvHomeCatalogLoadingSkeleton()
                }
            }
        }
    }
}

@Composable
private fun TvHomeCatalogLoadingSkeleton() {
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
        verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.rowSpacing),
    ) {
        Text(
            text = stringResource(R.string.loading_more_rows),
            modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
        )
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.sectionTitleSpacing)) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                        .width(220.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(skeletonColor),
                )
                TvCatalogItemsLoadingSkeleton(skeletonColor, pulse)
            }
        }
    }
}

@Composable
private fun TvCatalogItemsLoadingSkeleton(
    skeletonColor: Color,
    pulse: Float,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
        contentPadding = PaddingValues(
            start = TvLayoutTokens.screenHorizontalPadding,
            end = TvLayoutTokens.screenHorizontalPadding,
            bottom = TvLayoutTokens.screenBottomPadding,
        ),
        userScrollEnabled = false,
    ) {
        items(4) {
            Column(
                modifier = Modifier.width(TvLayoutTokens.posterWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(TvLayoutTokens.posterWidth)
                        .height(TvLayoutTokens.posterHeight)
                        .clip(TvShapeTokens.card)
                        .background(skeletonColor),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(skeletonColor.copy(alpha = pulse * 0.8f)),
                )
            }
        }
    }
}

@Composable
private fun TvContinueWatchingRow(
    items: List<Pair<MediaPreview, WatchProgress>>,
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
                TvContinueWatchingCard(
                    media = media,
                    progress = progress,
                    onClick = { onMedia(media) },
                    onFocused = { onFocused(media) },
                    modifier = Modifier.mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
                )
            }
        }
    }
}

@Composable
private fun TvHeroLoadingSkeleton(modifier: Modifier = Modifier) {
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
        modifier = modifier
            .fillMaxWidth()
            .height(TvLayoutTokens.heroHeight)
            .clip(TvShapeTokens.hero)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse))
            .focusable(),
    )
}

@Composable
private fun TvHero(
    media: MediaPreview,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    kenBurnsEnabled: Boolean,
    carouselItems: List<MediaPreview>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val ambientAccent = LocalTvContentAccent.current ?: MaterialTheme.colorScheme.primary
    val artworkResolver = LocalArtworkResolver.current
    val resolvedMedia = remember(media, artworkResolver) {
        artworkResolver.resolve(media).media
    }
    val currentIndex = carouselItems.indexOfFirst { it.stableKey == media.stableKey }
    val hasCarousel = carouselItems.size > 1 && currentIndex >= 0
    val carouselPosition = currentIndex + 1
    val heroDescription = if (hasCarousel) {
        stringResource(R.string.hero_carousel_description, resolvedMedia.name, carouselPosition, carouselItems.size)
    } else {
        resolvedMedia.name
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TvLayoutTokens.heroHeight)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(media)
            }
            .border(
                width = if (focused) TvFocusTokens.outlineWidth else 0.dp,
                color = if (focused) ambientAccent else Color.Transparent,
                shape = TvShapeTokens.hero,
            )
            .clip(TvShapeTokens.hero)
            .clickable(role = Role.Button) { onMedia(media) }
            .onPreviewKeyEvent { event ->
                if (!hasCarousel || event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onPrevious()
                            true
                        }
                        Key.DirectionRight -> {
                            onNext()
                            true
                        }
                        else -> false
                    }
                }
            }
            .focusable()
            .semantics { contentDescription = heroDescription },
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
            TvHeroArtwork(
                media = featuredMedia,
                userEnabled = kenBurnsEnabled,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize(),
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
        if (hasCarousel) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.hero_carousel_position, carouselPosition, carouselItems.size),
                    color = Color.White.copy(alpha = 0.80f),
                    style = MaterialTheme.typography.labelSmall,
                )
                carouselItems.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .width(if (index == currentIndex) 22.dp else 6.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index == currentIndex) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.48f)
                                },
                            ),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(520.dp)
                .padding(32.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (!resolvedMedia.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = resolvedMedia.logoUrl,
                    contentDescription = resolvedMedia.name,
                    modifier = Modifier.width(330.dp).height(78.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                )
            } else {
                Text(
                    text = resolvedMedia.name,
                    style = MaterialTheme.typography.displayMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            resolvedMedia.description?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvMetadataLine(
                presentation = resolvedMedia.metadataPresentation(maxGenres = 2),
                includeGenres = true,
                ratings = listOfNotNull(
                    metadataImdbScore(
                        resolvedMedia.rating,
                        resolvedMedia.ratingSource,
                        stringResource(R.string.source_imdb),
                    ),
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
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
private fun TvMetadataLine(
    presentation: MediaMetadataPresentation,
    includeGenres: Boolean,
    modifier: Modifier = Modifier,
    ratings: List<RatingSourceScore> = emptyList(),
    onSelectRating: ((RatingSourceScore) -> Unit)? = null,
) {
    val values = buildList {
        presentation.year?.let { add(it.toString()) }
        presentation.contentRating?.let(::add)
        presentation.runtimeMinutes?.let { add(stringResource(R.string.minutes_format, it)) }
        if (includeGenres && presentation.genres.isNotEmpty()) add(presentation.genres.joinToString(", "))
    }
    if (values.isEmpty() && ratings.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (values.isNotEmpty()) {
            Text(
                text = values.joinToString("  •  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (ratings.isNotEmpty()) {
            if (onSelectRating != null) {
                TvRatingBadgeStrip(ratings = ratings, onSelect = onSelectRating)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ratings.forEach { score -> RatingBadge(score) }
                }
            }
        }
    }
}

/**
 * D-pad-reachable rating badges next to the detail metadata. Select opens the
 * source's rating details dialog, so focus only lands on actionable items
 * (TV-FOC-01); the pale-container focus treatment matches TV-FOC-02.
 */
@Composable
private fun TvRatingBadgeStrip(
    ratings: List<RatingSourceScore>,
    onSelect: (RatingSourceScore) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(ratings, key = RatingSourceScore::sourceId) { score ->
            val valueText = ratingValueText(score)
            val description = stringResource(R.string.rating_badge_description, score.displayName, valueText)
            TvFocusableSurface(
                onClick = { onSelect(score) },
                modifier = Modifier.semantics { contentDescription = description },
            ) { focused ->
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RatingBadgeChip(score)
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
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
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
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
        Text(
            text = section.providerName,
            modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
        )
        section.errorMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
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
            TvCatalogItemsLoadingSkeleton(
                skeletonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse),
                pulse = pulse,
            )
        } else if (section.items.isNotEmpty() || section.hasMore || section.loadMoreError != null) {
            LazyRow(
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
                        showRating = section.providerId == CINEMETA_PROVIDER_ID,
                        modifier = Modifier.mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored),
                        showLabel = true,
                        revealLabelOnFocus = true,
                    )
                }
                if (section.loadMoreError != null || section.hasMore) {
                    item("catalog-action") {
                        TvAction(
                            label = stringResource(if (section.loadMoreError != null) R.string.retry else R.string.load_more),
                            icon = Icons.Outlined.Refresh,
                            onClick = if (section.loadMoreError != null) onRetry else onLoadMore,
                        )
                    }
                }
            }
        } else if (section.errorMessage != null) {
            // Keep at least one focusable element in the row so D-pad focus
            // traversal can pass through error-only sections instead of dying
            // when the next row is not composed yet.
            TvAction(
                label = stringResource(R.string.retry),
                icon = Icons.Outlined.Refresh,
                onClick = onRetry,
                modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
            )
        }
    }
}

@Composable
private fun TvMediaGrid(
    title: String,
    media: List<MediaPreview>,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    section: CatalogSection? = null,
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
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
        if (media.isEmpty() && section?.loadMoreError == null && section?.hasMore != true) {
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
                        onFocused = { onFocused(item) },
                        showRating = section?.providerId == CINEMETA_PROVIDER_ID,
                        modifier = (if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            .mediaFocusRestore(item.stableKey, restoreMediaKey, onFocusRestored),
                        showLabel = true,
                        compactLandscape = true,
                    )
                }
                if (section?.loadMoreError != null || section?.hasMore == true) {
                    item("catalog-action") {
                        TvAction(
                            label = stringResource(if (section?.loadMoreError != null) R.string.retry else R.string.load_more),
                            icon = Icons.Outlined.Refresh,
                            onClick = if (section?.loadMoreError != null) onRetry else onLoadMore,
                        )
                    }
                }
            }
        }
    }
}
/**
 * Discover (TV-NAV-01): addon-declared category overview in a four-column
 * grid, then five-column poster results. Categories come from the selected
 * addon catalog's declared genre options — never a hard-coded list.
 */
private val categoryGradientStarts = listOf(
    Color(0xFF1E2023),
    Color(0xFF232733),
    Color(0xFF20272A),
    Color(0xFF262331),
    Color(0xFF1F262E),
)
private val categoryGradientEnds = listOf(
    Color(0xFF354964),
    Color(0xFF2F3B52),
    Color(0xFF31424D),
    Color(0xFF3A3450),
    Color(0xFF2E3A4A),
)

/** Deterministic muted instrument-blue gradient per category id (SHR-PROD-03). */
private fun categoryGradient(id: String): Brush {
    val seed = id.fold(0) { acc, char -> acc * 31 + char.code }
    val index = Math.floorMod(seed, categoryGradientStarts.size)
    return Brush.linearGradient(listOf(categoryGradientStarts[index], categoryGradientEnds[index]))
}

@Composable
private fun TvDiscover(
    state: AppUiState,
    onPrepare: () -> Unit,
    onBrowseCatalog: (String) -> Unit,
    onBrowseGenre: (String?) -> Unit,
    onClearGenre: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    LaunchedEffect(Unit) { onPrepare() }
    val browse = state.browse
    val selectedTarget = browse.targets.firstOrNull { it.id == browse.selectedCatalogId }
    val activeGenre = browse.selectedGenre
    var showResults by rememberSaveable { mutableStateOf(false) }
    // Back reverses the latest layer: results → overview → destination (TV-NAV-02).
    BackHandler(enabled = showResults) {
        showResults = false
        if (activeGenre != null) onClearGenre()
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.discover),
            modifier = Modifier
                .padding(
                    start = TvLayoutTokens.screenHorizontalPadding,
                    end = TvLayoutTokens.screenHorizontalPadding,
                    bottom = 16.dp,
                )
                .semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        if (browse.targets.isEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvEmptyMark()
                Text(
                    stringResource(R.string.install_first_addon),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = TvLayoutTokens.screenHorizontalPadding),
        ) {
            items(browse.targets, key = CatalogBrowseTarget::id) { target ->
                TvFilterChip(
                    label = target.catalog.name,
                    selected = browse.selectedCatalogId == target.id,
                    onClick = { onBrowseCatalog(target.id) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        if (showResults) {
            TvCategoryResults(
                genreLabel = activeGenre ?: stringResource(R.string.all_categories),
                result = browse.result,
                loading = browse.loading,
                onMedia = onMedia,
                onFocused = onFocused,
                initialFocusRequester = initialFocusRequester,
                restoreMediaKey = restoreMediaKey,
                onFocusRestored = onFocusRestored,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
            )
        } else {
            val genres = selectedTarget?.genres.orEmpty()
            if (genres.isEmpty()) {
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
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = TvLayoutTokens.screenHorizontalPadding,
                        end = TvLayoutTokens.screenHorizontalPadding,
                        bottom = TvLayoutTokens.bottomListPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item(key = "all") {
                        TvCategoryTile(
                            label = stringResource(R.string.all_categories),
                            gradient = categoryGradient("all"),
                            onClick = {
                                onBrowseGenre(null)
                                showResults = true
                            },
                            modifier = Modifier.focusRequester(initialFocusRequester),
                        )
                    }
                    items(genres, key = { it }) { genre ->
                        TvCategoryTile(
                            label = genre,
                            gradient = categoryGradient(genre),
                            onClick = {
                                onBrowseGenre(genre)
                                showResults = true
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvCategoryTile(
    label: String,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(196f / 110f),
        containerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(TvShapeTokens.card)
                .background(gradient)
                .padding(20.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Five-column poster grid; the header keeps a stable height while results refresh (TV-CNT-02). */
@Composable
private fun TvCategoryResults(
    genreLabel: String,
    result: CatalogSection?,
    loading: Boolean,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = genreLabel,
            modifier = Modifier
                .padding(
                    start = TvLayoutTokens.screenHorizontalPadding,
                    end = TvLayoutTokens.screenHorizontalPadding,
                    bottom = 16.dp,
                )
                .semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        when {
            result?.errorMessage != null -> {
                Text(
                    text = result.errorMessage,
                    modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
                    color = MaterialTheme.colorScheme.error,
                )
                TvAction(
                    label = stringResource(R.string.retry),
                    icon = Icons.Outlined.Refresh,
                    onClick = onRetry,
                )
            }
            result != null -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = TvLayoutTokens.screenHorizontalPadding,
                        end = TvLayoutTokens.screenHorizontalPadding,
                        bottom = TvLayoutTokens.bottomListPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    itemsIndexed(result.items, key = { _, item -> item.stableKey }) { index, item ->
                        TvMediaCard(
                            media = item,
                            onClick = { onMedia(item) },
                            onFocused = { onFocused(item) },
                            showLabel = true,
                            revealLabelOnFocus = true,
                            showRating = true,
                            modifier = (if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                .mediaFocusRestore(item.stableKey, restoreMediaKey, onFocusRestored),
                        )
                    }
                    if (result.loadMoreError != null || result.hasMore) {
                        item("catalog-action") {
                            TvAction(
                                label = stringResource(
                                    if (result.loadMoreError != null) R.string.retry else R.string.load_more,
                                ),
                                icon = Icons.Outlined.Refresh,
                                onClick = if (result.loadMoreError != null) onRetry else onLoadMore,
                            )
                        }
                    }
                }
            }
            loading -> TvCategoryResultsSkeleton(modifier = Modifier.weight(1f))
        }
    }
}

/** Preserves the final grid geometry while the first category page loads. */
@Composable
private fun TvCategoryResultsSkeleton(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "category results loading").animateFloat(
        initialValue = 0.58f,
        targetValue = 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "category results pulse",
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = TvLayoutTokens.screenHorizontalPadding,
            end = TvLayoutTokens.screenHorizontalPadding,
            bottom = TvLayoutTokens.bottomListPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        items(10, key = { "category-skeleton-$it" }) {
            Box(
                modifier = Modifier
                    .size(width = TvLayoutTokens.posterWidth, height = TvLayoutTokens.posterHeight)
                    .clip(TvShapeTokens.card)
                    .background(Color.White.copy(alpha = 0.04f + 0.05f * pulse)),
            )
        }
    }
}

@Composable
private fun TvSearch(
    initialSearch: String,
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
    onFocused: (MediaPreview) -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(initialSearch) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(query) { onSearch(query) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
    ) {
        TvEditableTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.search_label),
            placeholder = stringResource(R.string.search_tv_hint),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onNavigateDown = { focusManager.moveFocus(FocusDirection.Down) },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .focusRequester(initialFocusRequester),
        )
        Spacer(Modifier.height(24.dp))
        if (query.isBlank()) {
            val browse = state.browse
            val selectedTarget = browse.targets.firstOrNull { it.id == browse.selectedCatalogId }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(browse.targets.map { it.catalog.type }.distinct(), key = { it }) { type ->
                    TvFilterChip(
                        label = type.replaceFirstChar(Char::uppercase),
                        selected = browse.selectedType == type,
                        onClick = { onBrowseType(type) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(browse.targets.filter { it.catalog.type == browse.selectedType }, key = { it.id }) { target ->
                    TvFilterChip(
                        label = target.catalog.name,
                        selected = browse.selectedCatalogId == target.id,
                        onClick = { onBrowseCatalog(target.id) },
                    )
                }
            }
            selectedTarget?.genres?.takeIf(List<String>::isNotEmpty)?.let { genres ->
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        TvFilterChip(
                            label = stringResource(R.string.all_genres),
                            selected = browse.selectedGenre == null,
                            onClick = { onBrowseGenre(null) },
                        )
                    }
                    items(genres, key = { it }) { genre ->
                        TvFilterChip(
                            label = genre,
                            selected = browse.selectedGenre == genre,
                            onClick = { onBrowseGenre(genre) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val result = browse.result
            when {
                result?.errorMessage != null -> Text(result.errorMessage, color = MaterialTheme.colorScheme.error)
                result != null -> TvMediaGrid(
                    title = result.title,
                    media = result.items,
                    onMedia = onMedia,
                    onFocused = onFocused,
                    initialFocusRequester = initialFocusRequester,
                    restoreMediaKey = restoreMediaKey,
                    onFocusRestored = onFocusRestored,
                    section = result,
                    onLoadMore = onLoadMore,
                    onRetry = onRetry,
                )
                else -> {
                    TvEmptyMark()
                    Text(stringResource(R.string.install_first_addon), style = MaterialTheme.typography.titleMedium)
                }
            }
        } else if (state.searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (state.searchSections.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TvEmptyMark()
                Text(
                    stringResource(R.string.search_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.rowSpacing),
                contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
            ) {
                items(state.searchSections, key = CatalogSection::id) { section ->
                    TvCatalogRow(
                        section = section,
                        onMedia = onMedia,
                        onFocused = onFocused,
                        onLoadMore = { onCatalogLoadMore(section.id) },
                        onRetry = { onCatalogRetry(section.id) },
                        restoreMediaKey = restoreMediaKey,
                        onFocusRestored = onFocusRestored,
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
                        TvFilterChip(
                            label = stringResource(R.string.all_sources),
                            selected = picker.selectedProviderId == null,
                            onClick = { onProvider(null) },
                            modifier = Modifier.focusRequester(filtersFocus),
                        )
                    }
                    items(picker.providerIds, key = { it }) { providerId ->
                        TvFilterChip(
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
private fun TvFilterChip(
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
internal fun TvSeasonChips(
    seasonNumbers: List<Int>,
    selectedSeason: Int?,
    onSeasonSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = TvLayoutTokens.screenHorizontalPadding,
            end = TvLayoutTokens.screenHorizontalPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasonNumbers, key = { it }) { season ->
            TvFilterChip(
                label = stringResource(R.string.season_format, season),
                selected = selectedSeason == season,
                onClick = { onSeasonSelected(season) },
            )
        }
    }
}

@Composable
private fun TvSourceBadge(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier = modifier
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
    val presentation = picker.media.metadataPresentation()
    val number = picker.episode?.numberParts()
    val imdbScore = metadataImdbScore(
        picker.media.rating,
        picker.media.ratingSource,
        stringResource(R.string.source_imdb),
    )
    val metadata = buildList {
        number?.let {
            when {
                it.season != null && it.episode != null ->
                    add(stringResource(R.string.episode_format, it.season, it.episode))
                it.season != null -> add(stringResource(R.string.season_format, it.season))
                it.episode != null -> add(stringResource(R.string.episode_number_format, it.episode))
            }
        }
        presentation.contentRating?.let(::add)
        presentation.year?.let { add(it.toString()) }
        presentation.genres.joinToString(", ").takeIf(String::isNotBlank)?.let(::add)
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
        imdbScore?.let { score ->
            Spacer(Modifier.height(10.dp))
            RatingBadge(score, valueColor = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun TvDetailScreen(
    detail: MediaDetail?,
    enrichment: DetailEnrichment?,
    enrichmentFailed: Boolean,
    inLibrary: Boolean,
    watchedEpisodeIds: Set<String>,
    spoilerProtection: SpoilerProtectionSettings,
    progress: List<WatchProgress>,
    onPlay: (Episode?) -> Unit,
    onOpenMedia: (MediaPreview) -> Unit,
    onFocusedMedia: (MediaPreview) -> Unit,
    onLibrary: () -> Unit,
    onEditArtwork: () -> Unit,
    onRetryEnrichment: () -> Unit,
) {
    if (detail == null) return
    val artworkResolver = LocalArtworkResolver.current
    val resolvedPreview = remember(detail.preview, artworkResolver) {
        artworkResolver.resolve(detail.preview).media
    }
    val playFocus = remember(detail.preview.stableKey) { FocusRequester() }
    val libraryFocus = remember(detail.preview.stableKey) { FocusRequester() }
    var lastActionFocus by remember(detail.preview.stableKey) { mutableStateOf<FocusRequester?>(null) }
    var selectedRating by remember(detail.preview.stableKey) { mutableStateOf<RatingSourceScore?>(null) }
    val libraryPulse = remember(detail.preview.stableKey) { Animatable(1f) }
    var previousLibraryState by remember(detail.preview.stableKey) { mutableStateOf(inLibrary) }
    val reducedMotion = rememberReducedMotion()
    LaunchedEffect(detail.preview.stableKey) { playFocus.requestFocus() }
    LaunchedEffect(inLibrary) {
        if (inLibrary && !previousLibraryState && !reducedMotion) {
            libraryPulse.animateTo(1.04f, tween(TvMotionTokens.confirmationPulseDurationMillis))
            libraryPulse.animateTo(1f, tween(TvMotionTokens.confirmationPulseDurationMillis))
        } else {
            libraryPulse.snapTo(1f)
        }
        previousLibraryState = inLibrary
    }
    val seasonNumbers = detail.episodes
        .asSequence()
        .mapNotNull { it.season }
        .distinct()
        .sorted()
        .toList()
    val showSeasonChips = detail.preview.type == MediaType.SERIES && seasonNumbers.size > 1
    var selectedSeason by remember(detail.preview.stableKey, seasonNumbers) {
        mutableStateOf(seasonNumbers.firstOrNull())
    }
    val visibleEpisodes = if (showSeasonChips && selectedSeason != null) {
        detail.episodes.filter { it.season == selectedSeason }
    } else {
        detail.episodes
    }
    LaunchedEffect(detail.preview.stableKey, seasonNumbers) {
        if (selectedSeason == null || selectedSeason !in seasonNumbers) {
            selectedSeason = seasonNumbers.firstOrNull()
        }
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
                        .heightIn(min = 440.dp)
                        .width(520.dp)
                        .padding(start = TvLayoutTokens.screenHorizontalPadding, top = 138.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!resolvedPreview.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = resolvedPreview.logoUrl,
                            contentDescription = resolvedPreview.name,
                            modifier = Modifier.width(330.dp).height(78.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = resolvedPreview.name,
                            style = MaterialTheme.typography.displaySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    detail.preview.description
                        ?.takeIf(String::isNotBlank)
                        ?.let { overview ->
                            TvExpandableText(
                                text = overview,
                                collapsedLines = 2,
                                returnFocusProvider = { lastActionFocus },
                            )
                        }
                    TvMetadataLine(
                        presentation = detail.metadataPresentation(maxGenres = 2),
                        includeGenres = true,
                        ratings = orderedRatingScores(
                            metadata = metadataImdbScore(
                                detail.preview.rating,
                                detail.preview.ratingSource,
                                stringResource(R.string.source_imdb),
                            ),
                            enrichment = enrichment?.ratings.orEmpty(),
                        ),
                        onSelectRating = { selectedRating = it },
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvAction(
                            label = stringResource(R.string.play),
                            icon = Icons.Outlined.PlayArrow,
                            modifier = Modifier
                                .focusRequester(playFocus)
                                .onFocusChanged { if (it.isFocused) lastActionFocus = playFocus },
                            onClick = { onPlay(null) },
                        )
                        TvAction(
                            label = stringResource(if (inLibrary) R.string.in_library else R.string.add_to_library),
                            icon = if (inLibrary) Icons.Outlined.Check else Icons.Outlined.Add,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = libraryPulse.value
                                    scaleY = libraryPulse.value
                                }
                                .focusRequester(libraryFocus)
                                .onFocusChanged { if (it.isFocused) lastActionFocus = libraryFocus },
                            enabled = !inLibrary,
                            onClick = onLibrary,
                        )
                        TvAction(
                            label = stringResource(R.string.edit_artwork),
                            icon = Icons.Outlined.Palette,
                            onClick = onEditArtwork,
                        )
                    }
                    TvExpandablePeopleSection(
                        labelRes = R.string.directors,
                        people = detail.directors,
                        returnFocusProvider = { lastActionFocus },
                    )
                }
            }
            if (detail.episodes.isNotEmpty()) {
                item("episodes-title") {
                    Text(
                        text = stringResource(R.string.episodes),
                        modifier = Modifier.padding(
                            start = TvLayoutTokens.screenHorizontalPadding,
                            end = TvLayoutTokens.screenHorizontalPadding,
                            bottom = if (showSeasonChips) 12.dp else 16.dp,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (showSeasonChips) {
                    item("season-filters") {
                        TvSeasonChips(
                            seasonNumbers = seasonNumbers,
                            selectedSeason = selectedSeason,
                            onSeasonSelected = { selectedSeason = it },
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                }
                item("episodes") {
                    LazyRow(
                        contentPadding = PaddingValues(
                            start = TvLayoutTokens.screenHorizontalPadding,
                            end = TvLayoutTokens.screenHorizontalPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
                    ) {
                        items(visibleEpisodes, key = Episode::id) { episode ->
                            TvEpisodeCard(
                                media = detail.preview,
                                episode = episode,
                                watched = episode.id in watchedEpisodeIds,
                                progress = progress.firstOrNull { it.videoId == episode.id },
                                spoilerProtection = spoilerProtection,
                                fallbackArtworkUrl = detail.preview.backgroundUrl ?: detail.preview.posterUrl,
                                onClick = { onPlay(episode) },
                            )
                        }
                    }
                }
            }
            // Enrichment upgrades the rail with portraits; the addon's own cast
            // list keeps it useful without any integration configured.
            val castCredits = enrichment?.cast?.takeIf(List<PersonCredit>::isNotEmpty)
                ?: detail.cast.map { name -> PersonCredit(name = HtmlCompat.fromHtml(name, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()) }
            if (castCredits.isNotEmpty()) {
                item("cast") { TvPeopleRail(castCredits) }
            }
            if (enrichment?.similar.isNullOrEmpty() == false) {
                item("similar") {
                    TvSimilarRail(
                        similar = enrichment!!.similar,
                        onOpenMedia = onOpenMedia,
                        onFocused = onFocusedMedia,
                    )
                }
            }
            enrichment?.facts?.takeIf { facts ->
                facts.status != null || facts.originalLanguage != null ||
                    (facts.budgetUsd ?: 0) > 0 || (facts.revenueUsd ?: 0) > 0
            }?.let { facts ->
                item("facts") { TvFactsSection(facts) }
            }
            if (enrichmentFailed) {
                item("enrichment-error") { TvInlineError(onRetry = onRetryEnrichment) }
            }
        }
    }
    selectedRating?.let { rating ->
        TvRatingDetailsDialog(
            rating = rating,
            fetchedAtEpochMillis = enrichment?.fetchedAtEpochMillis,
            onDismiss = { selectedRating = null },
        )
    }
}
@Composable
private fun TvArtworkEditorScreen(
    editor: ArtworkEditorState,
    onBack: () -> Unit,
    onPosterSelected: (ArtworkAsset?) -> Unit,
    onBackdropSelected: (ArtworkAsset?) -> Unit,
    onLogoSelected: (ArtworkAsset?) -> Unit,
    onProviderSelected: (ArtworkProviderId?) -> Unit,
    onSave: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvLayoutTokens.screenHorizontalPadding,
            end = TvLayoutTokens.screenHorizontalPadding,
            top = 48.dp,
            bottom = TvLayoutTokens.bottomListPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (!editor.media.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = editor.media.logoUrl,
                    contentDescription = editor.media.name,
                    modifier = Modifier.width(360.dp).height(96.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                )
            } else {
                Text(editor.media.name, style = MaterialTheme.typography.displaySmall)
            }
        }
        item {
            Text(
                stringResource(R.string.artwork_choose),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editor.availableProviders.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        TvFilterChip(
                            label = stringResource(R.string.artwork_all_sources),
                            selected = editor.providerFilter == null,
                            onClick = { onProviderSelected(null) },
                        )
                    }
                    items(editor.availableProviders, key = { it.value }) { provider ->
                        TvFilterChip(
                            label = provider.value,
                            selected = editor.providerFilter == provider,
                            onClick = { onProviderSelected(provider) },
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
                item { TvArtworkProviderMessages(results) }
            }
        if (editor.loading) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item { Text(stringResource(R.string.artwork_logos), style = MaterialTheme.typography.headlineSmall) }
            item {
                val logos = editor.filteredLogos
                if (logos.isEmpty()) {
                    TvArtworkEmptyMessage(editor, stringResource(R.string.artwork_logo_kind))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(logos, key = { "${it.provider}:${it.reference}" }) { asset ->
                            val selected = editor.selectedLogo == asset
                            TvFocusableSurface(
                                onClick = { onLogoSelected(asset) },
                                modifier = Modifier
                                    .size(300.dp, 112.dp)
                                    .semantics { this.selected = selected },
                                containerColor = if (selected) {
                                    TvSurfaceTokens.selectedFilter
                                } else {
                                    TvSurfaceTokens.card
                                },
                                focusedContainerColor = TvFocusTokens.selectedNavigationContainer,
                            ) { focused ->
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = artworkImageUrl(asset, "w500"),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                                                TvShapeTokens.button,
                                            )
                                            .padding(12.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                    SelectionCheckmark(
                                        selected = selected,
                                        selectedContainerColor = if (focused) {
                                            TvFocusTokens.focusedContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        selectedContentColor = if (focused) {
                                            TvFocusTokens.focusedContent
                                        } else {
                                            MaterialTheme.colorScheme.onPrimary
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                    )
                                    TvSourceBadge(
                                        label = asset.provider.value,
                                        focused = focused,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.artwork_posters), style = MaterialTheme.typography.headlineSmall) }
            item {
                val posters = editor.filteredPosters
                if (posters.isEmpty()) {
                    TvArtworkEmptyMessage(editor, stringResource(R.string.artwork_poster_kind))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(posters, key = { "${it.provider}:${it.reference}" }) { asset ->
                            val selected = editor.selectedPoster == asset
                            TvFocusableSurface(
                                onClick = { onPosterSelected(asset) },
                                modifier = Modifier
                                    .size(150.dp, 225.dp)
                                    .semantics { this.selected = selected },
                                containerColor = if (selected) {
                                    TvSurfaceTokens.selectedFilter
                                } else {
                                    TvSurfaceTokens.card
                                },
                                focusedContainerColor = TvFocusTokens.selectedNavigationContainer,
                            ) { focused ->
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = artworkImageUrl(asset, "w500"),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                    SelectionCheckmark(
                                        selected = selected,
                                        selectedContainerColor = if (focused) {
                                            TvFocusTokens.focusedContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        selectedContentColor = if (focused) {
                                            TvFocusTokens.focusedContent
                                        } else {
                                            MaterialTheme.colorScheme.onPrimary
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                    )
                                    TvSourceBadge(
                                        label = asset.provider.value,
                                        focused = focused,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.artwork_backdrops), style = MaterialTheme.typography.headlineSmall) }
            item {
                val backdrops = editor.filteredBackdrops
                if (backdrops.isEmpty()) {
                    TvArtworkEmptyMessage(editor, stringResource(R.string.artwork_backdrop_kind))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(backdrops, key = { "${it.provider}:${it.reference}" }) { asset ->
                            val selected = editor.selectedBackdrop == asset
                            TvFocusableSurface(
                                onClick = { onBackdropSelected(asset) },
                                modifier = Modifier
                                    .size(270.dp, 152.dp)
                                    .semantics { this.selected = selected },
                                containerColor = if (selected) {
                                    TvSurfaceTokens.selectedFilter
                                } else {
                                    TvSurfaceTokens.card
                                },
                                focusedContainerColor = TvFocusTokens.selectedNavigationContainer,
                            ) { focused ->
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = artworkImageUrl(asset, "w780"),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                    SelectionCheckmark(
                                        selected = selected,
                                        selectedContainerColor = if (focused) {
                                            TvFocusTokens.focusedContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        selectedContentColor = if (focused) {
                                            TvFocusTokens.focusedContent
                                        } else {
                                            MaterialTheme.colorScheme.onPrimary
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                    )
                                    TvSourceBadge(
                                        label = asset.provider.value,
                                        focused = focused,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvAction(
                    label = stringResource(R.string.save_artwork),
                    icon = Icons.Outlined.Check,
                    enabled = editor.selectedPoster != null ||
                        editor.selectedBackdrop != null ||
                        editor.selectedLogo != null,
                    onClick = onSave,
                )
                TvAction(
                    label = stringResource(R.string.back),
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    onClick = onBack,
                )
            }
        }
    }
}
@Composable
private fun TvArtworkEmptyMessage(editor: ArtworkEditorState, artworkKind: String) {
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
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TvArtworkProviderMessages(results: List<ArtworkProviderResult>) {
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
internal fun TvEpisodeCard(
    media: MediaPreview,
    episode: Episode,
    watched: Boolean,
    progress: WatchProgress?,
    spoilerProtection: SpoilerProtectionSettings,
    fallbackArtworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val cardFocus = remember { FocusRequester() }
    val menuRequest = LocalTvContentMenuEnvironment.current.onRequest
    val currentMenuRequest by rememberUpdatedState(menuRequest)
    val menuTarget = remember(media, episode, progress) {
        ContentMenuTarget(
            media = media,
            progress = progress,
            episode = episode,
            origin = ContentMenuOrigin.EPISODE,
        )
    }
    val holdTracker = remember(scope, cardFocus, menuTarget) {
        SelectHoldTracker(scope) { currentMenuRequest?.invoke(menuTarget, cardFocus) }
    }.takeIf { menuRequest != null }
    val artworkUrl = episode.thumbnailUrl ?: fallbackArtworkUrl
    val number = episode.numberParts()
    val artworkHidden = spoilerProtection.shouldBlur(SpoilerContent.EPISODE_ARTWORK, watched)
    val synopsisHidden = spoilerProtection.shouldBlur(SpoilerContent.EPISODE_SYNOPSIS, watched)
    val hiddenAny = artworkHidden || synopsisHidden
    val watchedDescription = if (watched) stringResource(R.string.watched) else ""
    TvFocusableSurface(
        onClick = onClick,
        modifier = Modifier
            .then(modifier)
            .tvSelectHoldMenu(holdTracker)
            .focusRequester(cardFocus)
            .width(TvLayoutTokens.landscapeCardWidth)
            .height(TvLayoutTokens.landscapeCardHeight)
            .semantics {
                stateDescription = watchedDescription
            },
        containerColor = TvSurfaceTokens.elevated,
        focusedContainerColor = Color.Transparent,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvSurfaceTokens.elevated),
        ) {
            if (!artworkUrl.isNullOrBlank() || artworkHidden) {
                SpoilerBlurLayer(
                    hidden = artworkHidden,
                    veilColor = TvSurfaceTokens.elevated,
                    semanticLabel = stringResource(R.string.spoiler_hidden),
                    modifier = Modifier.fillMaxSize(),
                    veilContent = { TvSpoilerBadge() },
                    content = {
                        if (!artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
                )
            }
            SelectionCheckmark(
                selected = watched,
                selectedContainerColor = if (focused) {
                    TvFocusTokens.focusedContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                selectedContentColor = if (focused) {
                    TvFocusTokens.focusedContent
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
            val footerColor = if (focused) TvFocusTokens.focusedContainer else TvSurfaceTokens.elevated
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                footerColor.copy(alpha = if (focused) 0.52f else 0.90f),
                                footerColor.copy(alpha = if (focused) 0.68f else 1f),
                            ),
                        ),
                    )
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (number.isPresent) {
                    when {
                        number.season != null && number.episode != null ->
                            Text(
                                stringResource(R.string.episode_format, number.season, number.episode),
                                color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        number.season != null ->
                            Text(
                                stringResource(R.string.season_format, number.season),
                                color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        number.episode != null ->
                            Text(
                                stringResource(R.string.episode_number_format, number.episode),
                                color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                    }
                }
                Text(
                    episode.title,
                    color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hiddenAny) {
                    AnimatedVisibility(visible = focused) {
                        Text(
                            text = stringResource(
                                if (synopsisHidden) R.string.synopsis_hidden else R.string.spoiler_hidden,
                            ),
                            color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                } else {
                    episode.overview
                        ?.takeIf(String::isNotBlank)
                        ?.let { overview ->
                            AnimatedVisibility(visible = focused) {
                                Text(
                                    text = overview,
                                    color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun TvSpoilerBadge() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TvIcon(
            icon = Icons.Outlined.Visibility,
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

private enum class TvSettingsSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    PROFILES(R.string.profiles, Icons.Outlined.Person),
    SOURCES(R.string.addons, Icons.Outlined.Add),
    INTEGRATIONS(R.string.integrations, Icons.Outlined.Extension),
    APPEARANCE(R.string.appearance, Icons.Outlined.Palette),
    SPOILERS(R.string.spoiler_protection, Icons.Outlined.Visibility),
    ABOUT(R.string.about, Icons.Outlined.Info),
}
@Composable
private fun TvSettings(
    state: AppUiState,
    viewModel: AppViewModel,
    sectionFocusRequester: FocusRequester,
    topNavigationRequester: FocusRequester,
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
            TvSettingsSection.entries.forEach { item ->
                TvSettingsMenuItem(
                    section = item,
                    selected = section == item,
                    modifier = Modifier
                        .then(
                            if (section == item) {
                                Modifier.focusRequester(sectionFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (item == TvSettingsSection.PROFILES) {
                                Modifier.focusProperties { up = topNavigationRequester }
                            } else {
                                Modifier
                            },
                        ),
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
                TvSettingsSection.INTEGRATIONS -> TvIntegrationsSettings(state, viewModel)
                TvSettingsSection.APPEARANCE -> TvAppearanceSettings(state, viewModel)
                TvSettingsSection.SPOILERS -> TvSpoilerSettings(state, viewModel)
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
private fun TvAppearanceSettings(state: AppUiState, viewModel: AppViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
    ) {
        item {
            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.ken_burns_effect),
                description = stringResource(R.string.ken_burns_effect_description),
                checked = state.kenBurnsEnabled,
                onCheckedChange = viewModel::setKenBurnsEnabled,
            )
        }
    }
}
@Composable
private fun TvSpoilerSettings(state: AppUiState, viewModel: AppViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
    ) {
        item {
            Text(stringResource(R.string.spoiler_protection), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.protect_spoilers),
                description = stringResource(R.string.spoiler_protection_description),
                checked = state.spoilerProtection.enabled,
                onCheckedChange = {
                    viewModel.setSpoilerProtection(state.spoilerProtection.copy(enabled = it))
                },
            )
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.blur_episode_artwork),
                description = stringResource(R.string.blur_episode_artwork_description),
                checked = state.spoilerProtection.blurEpisodeArtwork,
                enabled = state.spoilerProtection.enabled,
                onCheckedChange = {
                    viewModel.setSpoilerProtection(state.spoilerProtection.copy(blurEpisodeArtwork = it))
                },
            )
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.blur_episode_synopsis),
                description = stringResource(R.string.blur_episode_synopsis_description),
                checked = state.spoilerProtection.blurEpisodeSynopsis,
                enabled = state.spoilerProtection.enabled,
                onCheckedChange = {
                    viewModel.setSpoilerProtection(state.spoilerProtection.copy(blurEpisodeSynopsis = it))
                },
            )
        }
    }
}
/** Aggregated rating sources offered by the MDBList integration (spec: recommended set). */
private val ratingSourceOptions = listOf(
    R.string.source_imdb,
    R.string.source_tmdb,
    R.string.source_trakt,
    R.string.source_rt_critics,
    R.string.source_rt_audience,
    R.string.source_metacritic,
    R.string.source_letterboxd,
)
private val ratingSourceIds = listOf("imdb", "tmdb", "trakt", "tomatoes", "popcorn", "metacritic", "letterboxd")

@Composable
private fun TvIntegrationsSettings(state: AppUiState, viewModel: AppViewModel) {
    var mdblistKey by rememberSaveable { mutableStateOf("") }
    var artworkKeys by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pendingArtworkStorageMode by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        viewModel.refreshIntegrations()
        viewModel.refreshArtworkKeyStatus()
    }
    val mdblist = state.integrations.firstOrNull { it.integration == "mdblist" }
    val connected = mdblist?.connected == true
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
    ) {
        item {
            Text(stringResource(R.string.integrations), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Text(
                stringResource(R.string.integrations_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.account !is AccountState.SignedIn) {
            item {
                Text(
                    stringResource(R.string.integrations_sign_in_required),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@LazyColumn
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.local_only_artwork_keys),
                description = stringResource(R.string.local_only_artwork_keys_description),
                checked = state.localOnlyArtworkKeys,
                onCheckedChange = { pendingArtworkStorageMode = it },
                enabled = !state.artworkStorageModeChanging,
            )
        }
        if (state.artworkProviders.isEmpty() && state.artworkProviderCatalogError != null) {
            item {
                Text(state.artworkProviderCatalogError, color = MaterialTheme.colorScheme.error)
                TvAction(
                    label = stringResource(R.string.retry),
                    icon = Icons.Outlined.Refresh,
                    onClick = viewModel::refreshArtworkKeyStatus,
                )
            }
        }
        // Artwork providers double as enrichment sources: a TMDB key powers
        // artwork, cast, and ratings; Fanart.tv covers artwork only.
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(providerName, style = MaterialTheme.typography.titleMedium)
                if (!provider.enabled) {
                    Text(
                        stringResource(R.string.integration_provider_retired),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TvAction(
                        label = stringResource(R.string.remove_artwork_key),
                        icon = Icons.Outlined.Delete,
                        enabled = provider.configured && !state.artworkStorageModeChanging,
                        onClick = { viewModel.deleteArtworkKey(providerId) },
                    )
                } else {
                    Text(
                        provider.purpose,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TvAction(
                        label = stringResource(R.string.artwork_get_key, providerName),
                        icon = Icons.Outlined.Info,
                        enabled = !state.artworkStorageModeChanging,
                        onClick = { viewModel.openArtworkProviderKeyPage(providerId) },
                    )
                    TvEditableTextField(
                        value = apiKey,
                        onValueChange = { value -> artworkKeys = artworkKeys + (providerId.value to value) },
                        label = stringResource(R.string.artwork_api_key),
                        placeholder = stringResource(R.string.artwork_api_key_placeholder),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                        enabled = !state.artworkStorageModeChanging,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        onImeAction = {
                            viewModel.saveArtworkKey(providerId, apiKey)
                            artworkKeys = artworkKeys - providerId.value
                        },
                    )
                    TvAction(
                        label = stringResource(R.string.artwork_get_help),
                        icon = Icons.Outlined.Info,
                        enabled = !state.artworkStorageModeChanging,
                        onClick = { viewModel.reportMessage(provider.helpText) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvAction(
                            label = stringResource(R.string.save_artwork_key),
                            icon = Icons.Outlined.Check,
                            enabled = !state.artworkStorageModeChanging && apiKey.isNotBlank(),
                            onClick = {
                                viewModel.saveArtworkKey(providerId, apiKey)
                                artworkKeys = artworkKeys - providerId.value
                            },
                        )
                        TvAction(
                            label = stringResource(R.string.remove_artwork_key),
                            icon = Icons.Outlined.Delete,
                            enabled = provider.configured && !state.artworkStorageModeChanging,
                            onClick = { viewModel.deleteArtworkKey(providerId) },
                        )
                    }
                    Text(
                        when {
                            state.artworkKeyStatusLoading -> stringResource(R.string.artwork_key_loading, providerName)
                            provider.configured -> stringResource(R.string.artwork_key_active, providerName)
                            else -> stringResource(R.string.artwork_key_not_configured, providerName)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    failure?.let {
                        Text(
                            stringResource(R.string.artwork_key_last_lookup_failed, it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvSurfaceTokens.elevated, TvShapeTokens.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("MDBList", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        state.integrationsLoading -> stringResource(R.string.integration_status_checking)
                        mdblist == null -> stringResource(R.string.integration_not_connected)
                        mdblist.valid == false -> stringResource(R.string.integration_key_rejected)
                        connected -> stringResource(R.string.integration_connected)
                        else -> stringResource(R.string.integration_not_connected)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                TvEditableTextField(
                    value = mdblistKey,
                    onValueChange = { mdblistKey = it },
                    label = stringResource(R.string.integration_api_key),
                    placeholder = stringResource(R.string.integration_api_key_placeholder),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    onImeAction = {
                        viewModel.saveIntegrationCredential("mdblist", mdblistKey)
                        mdblistKey = ""
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvAction(
                        label = stringResource(
                            if (connected) R.string.integration_replace_key else R.string.integration_connect,
                        ),
                        icon = Icons.Outlined.Check,
                        enabled = mdblistKey.isNotBlank(),
                        onClick = {
                            viewModel.saveIntegrationCredential("mdblist", mdblistKey)
                            mdblistKey = ""
                        },
                    )
                    if (connected) {
                        TvAction(
                            label = stringResource(R.string.integration_remove),
                            icon = Icons.Outlined.Delete,
                            onClick = { viewModel.removeIntegration("mdblist") },
                        )
                    }
                }
                if (connected) {
                    Text(
                        stringResource(R.string.integration_rating_sources),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // Only the sources the server actually returns stay enabled-checkable;
                    // unknown ids are ignored server-side when filtering ratings.
                    ratingSourceOptions.forEachIndexed { index, labelRes ->
                        val sourceId = ratingSourceIds[index]
                        val enabled = mdblist.enabledSources.contains(sourceId)
                        TvSettingsToggleRow(
                            title = stringResource(labelRes),
                            description = stringResource(R.string.integration_source_toggle_description),
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val next = if (checked) {
                                    (mdblist.enabledSources + sourceId).distinct()
                                } else {
                                    mdblist.enabledSources - sourceId
                                }
                                viewModel.setIntegrationSources("mdblist", next)
                            },
                        )
                    }
                }
            }
        }
        if (state.integrationsFailed) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.integrations_load_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TvAction(
                        label = stringResource(R.string.retry),
                        icon = Icons.Outlined.Refresh,
                        onClick = { viewModel.refreshIntegrations() },
                    )
                }
            }
        }
    }
    pendingArtworkStorageMode?.let { target ->
        TvArtworkStorageModeDialog(
            target = target,
            onDismiss = { pendingArtworkStorageMode = null },
            onConfirm = {
                pendingArtworkStorageMode = null
                viewModel.changeArtworkKeyStorageMode(target)
            },
        )
    }
}

@Composable
private fun TvArtworkStorageModeDialog(
    target: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) { cancelFocusRequester.requestFocus() }
        Surface(
            modifier = Modifier.width(720.dp),
            colors = SurfaceDefaults.colors(containerColor = TvSurfaceTokens.elevated),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(
                        if (target) R.string.artwork_storage_enable_title
                        else R.string.artwork_storage_disable_title,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(
                        if (target) R.string.artwork_storage_enable_body
                        else R.string.artwork_storage_disable_body,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvAction(
                        label = stringResource(R.string.cancel),
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        modifier = Modifier.focusRequester(cancelFocusRequester),
                        onClick = onDismiss,
                    )
                    TvAction(
                        label = stringResource(R.string.delete_keys_and_switch),
                        icon = Icons.Outlined.Delete,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    TvFocusableSurface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        role = Role.Switch,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics { toggleableState = ToggleableState(checked) },
    ) { focused ->
        val primaryColor = if (focused) {
            TvFocusTokens.focusedContent
        } else {
            MaterialTheme.colorScheme.onBackground
        }
        val secondaryColor = if (focused) {
            TvFocusTokens.focusedContent.copy(alpha = 0.76f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = primaryColor)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun TvSourcesSettings(state: AppUiState, viewModel: AppViewModel) {
    var providerAddress by rememberSaveable { mutableStateOf("") }
    val installFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    val installEnabled = providerAddress.startsWith("https://") || providerAddress.startsWith("http://")
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
                TvEditableTextField(
                    value = providerAddress,
                    onValueChange = { providerAddress = it },
                    label = stringResource(R.string.addon_address),
                    placeholder = stringResource(R.string.https_manifest_address),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    onNavigateDown = {
                        val nextFocusRequester = if (installEnabled) {
                            installFocusRequester
                        } else {
                            refreshFocusRequester
                        }
                        nextFocusRequester.requestFocus()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                )
            }
            item {
                TvAction(
                    label = stringResource(R.string.install_developer_source),
                    icon = Icons.Outlined.Add,
                    enabled = installEnabled,
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
                modifier = Modifier.focusRequester(refreshFocusRequester),
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
