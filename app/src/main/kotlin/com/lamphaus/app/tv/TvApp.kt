package com.lamphaus.app.tv

import com.lamphaus.app.ui.withUpNext
import com.lamphaus.app.ui.UpNextItem
import com.lamphaus.app.ui.releaseCountdownText
import com.lamphaus.app.ui.ReleaseKind
import com.lamphaus.app.ui.CatalogBrowseState

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.lazy.grid.LazyGridState


import android.os.SystemClock
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.ui.unit.Dp
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
import com.lamphaus.core.data.perf.PerfTrace
import com.lamphaus.app.ui.StreamBadgeRow
import com.lamphaus.app.ui.StreamBadgeMatcher
import com.lamphaus.app.ui.LocalStreamBadges
import com.lamphaus.app.ui.rememberFitsTonightSection
import com.lamphaus.app.ui.BEDTIME_OPTIONS
import com.lamphaus.app.ui.bedtimeLabel
import com.lamphaus.app.ui.seasonTimeLeft
import com.lamphaus.app.ui.seasonTimeLeftText
import com.lamphaus.app.ui.recapEpisodeTitle
import com.lamphaus.app.ui.seriesRecap
import com.lamphaus.app.ui.LocalSourceFit
import com.lamphaus.app.ui.SourceFitAdvisor
import com.lamphaus.app.ui.sourceFitLabel
import com.lamphaus.app.ui.LocalAccountPhotoUrl
import com.lamphaus.app.ui.ArtworkResolver
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.app.ui.LocalArtworkResolver
import com.lamphaus.app.ui.ArtworkEditorState
import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.app.ui.TvHomeLayout
import com.lamphaus.app.ui.TvNavigationStyle
import com.lamphaus.app.ui.CatalogBrowseTarget
import com.lamphaus.app.ui.AppViewModel
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
import com.lamphaus.app.ui.sourceItemKeys
import com.lamphaus.app.ui.SpoilerBlurLayer
import com.lamphaus.app.ui.SpoilerContent
import com.lamphaus.app.ui.shouldBlur
import com.lamphaus.app.ui.nextUpEpisode
import com.lamphaus.app.ui.NextUpKind
import com.lamphaus.app.ui.continueWatchingItemsFromSections
import com.lamphaus.app.ui.firstDistinctMedia
import com.lamphaus.app.ui.homeSectionsOfType
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
import com.lamphaus.core.model.FrameRateMatching
import com.lamphaus.app.ui.PlaybackEngineOptions
import com.lamphaus.core.model.ResolutionMatching
import com.lamphaus.core.model.SubtitleDefaultMode
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.StreamCandidate
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged


@Composable
fun TvApp(
    viewModel: AppViewModel,
    initialSearch: String?,
    onPlay: (PlaybackRequest) -> Unit,
    onExternalPlay: (String) -> Unit,
    updateViewModel: com.lamphaus.app.update.UpdateViewModel? = null,
    /** Opened from a Google TV home card (TV-HOME-01), which goes straight to the title. */
    fromHomeScreen: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // TV-HOME-01: a signed-out TV leaves nothing on the home screen.
    if (state.account is AccountState.SignedOut) {
        val context = LocalContext.current
        LaunchedEffect(Unit) { WatchNextPublisher(context).clear() }
    }
    var menuReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var restoreMenuFocus by remember { mutableStateOf(false) }
    var suppressMenuOpeningKey by remember { mutableStateOf(false) }
    // The boot sequence plays over a cold start only; remove-animations and a
    // global-search launch go straight to content (TV-MOT-01).
    val boot = rememberTvBootState(enabled = !rememberReducedMotion() && initialSearch == null && !fromHomeScreen)
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
    LamphausTvTheme(blackBackground = state.tvBlackBackground) {
        val artworkResolver = remember(state.artworkOverrides) {
            ArtworkResolver(state.artworkOverrides.associateBy { it.mediaKey })
        }
        // QA-08: LocalTvContentMenuEnvironment is a static local, so a new
        // instance recomposes every card. TvApp recomposes on every state
        // emission; keep the environment stable unless completion changes.
        val menuEnvironment = remember(completedVideoIds, viewModel) {
            TvContentMenuEnvironment(
                completedVideoIds = completedVideoIds,
                onRequest = { target, returnFocus ->
                    menuReturnFocus = returnFocus
                    suppressMenuOpeningKey = true
                    viewModel.openContentMenu(target)
                },
            )
        }
        CompositionLocalProvider(
            LocalArtworkResolver provides artworkResolver,
            LocalTvContentMenuEnvironment provides menuEnvironment,
            LocalAccountPhotoUrl provides (state.account as? AccountState.SignedIn)?.avatarUrl,
            LocalStreamBadges provides remember(state.streamBadges) { StreamBadgeMatcher(state.streamBadges) },
            LocalSourceFit provides remember(state.playbackCapabilities, state.devicePlaybackConfig) {
                SourceFitAdvisor(state.playbackCapabilities.takeIf { state.sourceFit }, state.devicePlaybackConfig)
            },
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
        // Usable-content signal: Home/pairing content or an actionable
        // empty/error state, never a loading placeholder (plan §7/PERF-02).
        LaunchedEffect(Unit) {
            PerfTrace.beginStartupSpan()
        }
        LaunchedEffect(state.account, state.initialContentLoading) {
            val ready = state.account != AccountState.Loading &&
                (state.account !is AccountState.SignedIn || !state.initialContentLoading)
            if (ready) {
                PerfTrace.mark(
                    if (state.account is AccountState.SignedIn) {
                        PerfTrace.STARTUP_USABLE_CONTENT
                    } else {
                        PerfTrace.STARTUP_SETTLED
                    },
                )
                PerfTrace.endStartupSpan()
            }
        }
        androidx.activity.compose.ReportDrawnWhen {
            state.account != AccountState.Loading &&
                (state.account !is AccountState.SignedIn || !state.initialContentLoading)
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
                        if (boot.active) {
                            // Any key skips the boot sequence; none reach the content beneath.
                            if (event.type == KeyEventType.KeyDown) boot.skip()
                            return@onPreviewKeyEvent true
                        }
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
                    is AccountState.SignedIn -> TvSignedIn(state, viewModel, initialSearch, updateViewModel)
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
                // In-app update dialog (plan §4, TV-NAV-02/04/05). Shown only when
                // no content menu owns focus and playback is not active; origin
                // focus returns to the previously focused item on dismiss.
                if (updateViewModel != null && com.lamphaus.app.update.UpdateCoordinator.enabled()) {
                    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
                    com.lamphaus.app.update.UpdateInstallerHost(updateViewModel, updateState)
                    val openUpdateSettings = com.lamphaus.app.update.rememberUpdatePermissionLauncher(updateViewModel)
                    val modalOpen = state.contentMenu.target != null || state.sourcePicker != null || boot.active
                    LaunchedEffect(modalOpen) { updateViewModel.setPresentationBlocked(modalOpen) }
                    if (!modalOpen) {
                        com.lamphaus.app.update.TvUpdateDialog(
                            state = updateState,
                            installedVersion = com.lamphaus.app.BuildConfig.VERSION_NAME,
                            onUpdate = { updateViewModel.download() },
                            onLater = { updateViewModel.defer() },
                            onInstall = {
                                updateViewModel.install(hostResumed = true, playbackActive = false)
                            },
                            onCancel = { updateViewModel.cancel() },
                            onRetry = { updateViewModel.retry() },
                            onOpenSettings = openUpdateSettings,
                            onAllowMetered = { updateViewModel.download(allowMetered = true) },
                            onDismissError = { updateViewModel.cancel() },
                            onFocusRestored = { restoreMenuFocus = true },
                        )
                    }
                }
                TvBootOverlay(
                    state = boot,
                    contentReady = state.account != AccountState.Loading &&
                        (state.account !is AccountState.SignedIn || !state.initialContentLoading),
                )
            }
        }
        }
    }
}

private class TvMenuPressGuard(
    private val openedAt: Long,
) {
    private var acceptedKeyCode: Int? = null
    private var acceptedDeviceId: Int? = null
    private var acceptedDownTime = 0L

    var acceptedFreshDown = false
        private set

    fun consume(event: android.view.KeyEvent): Boolean {
        acceptedFreshDown = false
        if (!isTvMenuActivationKey(event)) return false

        return when (event.action) {
            android.view.KeyEvent.ACTION_DOWN -> {
                if (
                    event.repeatCount != 0 ||
                    event.isCanceled ||
                    event.downTime < openedAt ||
                    acceptedKeyCode != null
                ) {
                    true
                } else {
                    acceptedKeyCode = event.keyCode
                    acceptedDeviceId = event.deviceId
                    acceptedDownTime = event.downTime
                    acceptedFreshDown = true
                    false
                }
            }
            android.view.KeyEvent.ACTION_UP -> {
                val accepted = matches(event)
                if (accepted) clearAcceptedPress()
                !(accepted && !event.isCanceled)
            }
            else -> true
        }
    }

    private fun matches(event: android.view.KeyEvent): Boolean =
        acceptedKeyCode == event.keyCode &&
            acceptedDeviceId == event.deviceId &&
            acceptedDownTime == event.downTime

    private fun clearAcceptedPress() {
        acceptedKeyCode = null
        acceptedDeviceId = null
        acceptedDownTime = 0L
    }
}

private fun isTvMenuActivationKey(event: android.view.KeyEvent): Boolean =
    event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        event.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
        event.keyCode == android.view.KeyEvent.KEYCODE_MENU

/** Centered, D-pad-first renderer for the shared content-menu model. */
@Composable
internal fun TvContentMenuDialog(
    menu: ContentMenuState,
    inLibrary: Boolean,
    onOpeningKeyReleased: () -> Unit,
    onDismiss: () -> Unit,
    onAction: (ContentMenuAction) -> Unit,
) {
    val target = menu.target ?: return
    val actions = target.menuActions()
    val firstActionFocus = remember(target.media.stableKey, target.progress?.videoId) { FocusRequester() }
    val pressGuard = remember(target.media.stableKey, target.progress?.videoId) {
        TvMenuPressGuard(SystemClock.uptimeMillis())
    }
    LaunchedEffect(target.media.stableKey, target.progress?.videoId, menu.resolving) {
        if (!menu.resolving) {
            withFrameNanos { }
            firstActionFocus.requestFocus()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                val consumed = pressGuard.consume(nativeEvent)
                if (
                    pressGuard.acceptedFreshDown ||
                    (isTvMenuActivationKey(nativeEvent) &&
                        nativeEvent.action == android.view.KeyEvent.ACTION_UP &&
                        consumed)
                ) {
                    onOpeningKeyReleased()
                }
                consumed
            },
        ) {
            Surface(
                modifier = Modifier.width(520.dp),
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

/**
 * Account resolution under the boot sequence; on its own (remove animations,
 * a global-search launch) it is the boot lockup as a still frame, so the old
 * loading mark never appears (TV-MOT-01).
 */
@Composable
private fun TvLoading() {
    TvBootStill(Modifier.fillMaxSize())
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
    updateViewModel: com.lamphaus.app.update.UpdateViewModel? = null,
) {
    val initialDestination = if (initialSearch.isNullOrBlank()) TvDestination.HOME else TvDestination.SEARCH
    val sideRail = state.tvNavigationStyle == TvNavigationStyle.SIDE_RAIL
    var destination by rememberSaveable { mutableStateOf(initialDestination) }
    // TV-NAV-01: the top bar starts focused; the side rail starts closed, with
    // focus on the page.
    var focusDestination by remember { mutableStateOf<TvDestination?>(initialDestination.takeUnless { sideRail }) }
    var contentEntry by remember { mutableStateOf<TvDestination?>(initialDestination.takeIf { sideRail }) }
    val focusReturn = rememberTvContentFocusReturn()
    val focusManager = LocalFocusManager.current
    var pendingMediaKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusedMedia by remember { mutableStateOf(firstDistinctMedia(state.sections, limit = 1).firstOrNull()) }
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
    val profileFocus = remember { FocusRequester() }
    var profileSwitcherOpen by rememberSaveable { mutableStateOf(false) }
    var returnToProfileAvatar by remember { mutableStateOf(false) }
    var showProfilesSettings by remember { mutableStateOf(false) }
    val closeProfileSwitcher = {
        profileSwitcherOpen = false
        returnToProfileAvatar = true
    }
    // TV-NAV-02: dismissing the switcher returns focus to the avatar that opened it.
    LaunchedEffect(returnToProfileAvatar) {
        if (returnToProfileAvatar) {
            withFrameNanos { }
            runCatching { profileFocus.requestFocus() }
            returnToProfileAvatar = false
        }
    }
    val openMedia: (MediaPreview) -> Unit = { media ->
        pendingMediaKey = media.stableKey
        viewModel.loadDetail(media)
    }
    // TV-HOME-01: keep Google TV's Continue watching in step with this
    // profile's progress; settled for a moment so a burst of updates writes once.
    val context = LocalContext.current
    val watchNext = remember(context) { WatchNextPublisher(context) }
    LaunchedEffect(watchNext, state.googleTvHome, state.progress, state.upNext) {
        delay(WATCH_NEXT_SETTLE_MILLIS)
        if (state.googleTvHome) watchNext.publish(watchNextEntries(state.progress, state.upNext)) else watchNext.clear()
    }
    // The hero and a row card can show the same title; Back returns to the
    // one the viewer actually opened (TV-NAV-07).
    val openHeroMedia: (MediaPreview) -> Unit = { media ->
        pendingMediaKey = heroRestoreKey(media)
        viewModel.loadDetail(media)
    }
    // Focus moves update lastFocusedMedia on every D-pad press. It is read
    // only by the ambient's snapshot flow, never here in composition, so
    // moving focus does not recompose this whole screen (QA-08).
    // With background artwork off, nothing is loaded or drawn behind the
    // browsing screens, and focus uses the standard accent (QA-08).
    val backgroundArtwork = state.backgroundArtworkEnabled
    val ambient = rememberTvContentAmbient {
        when (destination) {
            TvDestination.HOME,
            TvDestination.MOVIES,
            TvDestination.SERIES,
            TvDestination.DISCOVER,
            TvDestination.LIBRARY,
            TvDestination.SEARCH,
            -> lastFocusedMedia.takeIf { backgroundArtwork }
            TvDestination.SETTINGS -> null
        }
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
            recapEnabled = state.seriesRecap,
            seasonTimeLeftEnabled = state.fitsTonight,
            onPlay = { viewModel.openSources(state.selectedDetail.preview, it) },
            onOpenMedia = openMedia,
            onFocusedMedia = { lastFocusedMedia = it },
            onLibrary = {
                val preview = state.selectedDetail.preview
                if (state.library.any { it.mediaKey == preview.stableKey }) {
                    viewModel.removeFromLibrary(preview.stableKey)
                } else {
                    viewModel.addToLibrary(preview)
                }
            },
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
        focusReturn.save()
        navHasFocus = true
        focusDestination = destination
    }
    // A page entered from the side rail may still be composing or loading;
    // keep trying its entry control briefly, then leave focus on the rail.
    LaunchedEffect(contentEntry) {
        val target = contentEntry ?: return@LaunchedEffect
        val entered = withTimeoutOrNull(CONTENT_ENTRY_TIMEOUT_MILLIS) {
            while (!runCatching { contentFocus.getValue(target).requestFocus() }.getOrDefault(false)) {
                withFrameNanos { }
            }
            true
        } ?: false
        contentEntry = null
        if (!entered) focusDestination = target
    }
    // Settings opens the rail only from its section menu, so it returns to the
    // selected section; walking back could pass other sections and switch the pane.
    val returnToContent: () -> Unit = {
        if (destination == TvDestination.SETTINGS || !focusReturn.restore()) contentEntry = destination
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
    // Home, Movies and Series share one layout (TV-CNT-01); only the catalog
    // type differs.
    @Composable
    fun SignedInHome(catalogType: String?) {
        TvHome(
            state = state,
            onMedia = openMedia,
            onHeroMedia = openHeroMedia,
            onFocused = { lastFocusedMedia = it },
            onAddSource = {
                destination = TvDestination.SETTINGS
                focusDestination = TvDestination.SETTINGS
            },
            onLoadMore = viewModel::loadMoreCatalog,
            onRetry = viewModel::retryCatalogPage,
            onLoadMoreHome = viewModel::loadMoreHomeCatalogSections,
            onRetryHome = viewModel::retryHomeCatalogSections,
            initialFocusRequester = contentFocus.getValue(destination),
            restoreMediaKey = pendingMediaKey,
            onFocusRestored = { pendingMediaKey = null },
            spotlight = state.tvHomeLayout == TvHomeLayout.SPOTLIGHT,
            catalogType = catalogType,
        )
    }

    if (profileSwitcherOpen) {
        TvProfileSwitcher(
            profiles = state.profiles,
            activeProfileId = state.activeProfileId,
            onProfile = { profileId ->
                viewModel.selectProfile(profileId)
                closeProfileSwitcher()
            },
            onManageProfiles = {
                profileSwitcherOpen = false
                showProfilesSettings = true
                destination = TvDestination.SETTINGS
                focusDestination = TvDestination.SETTINGS
            },
            onDismiss = closeProfileSwitcher,
        )
    }

    // TV-CNT-04: opt-in, Spotlight only; unset means off.
    val trailerPreviews = rememberTvTrailerPreviews(
        enabled = state.trailers == true && state.tvHomeLayout == TvHomeLayout.SPOTLIGHT,
        resolve = { media, maxHeight -> viewModel.trailerSource(media, maxHeight) },
        forget = viewModel::forgetTrailerSource,
    )
    CompositionLocalProvider(
        LocalTvContentAccent provides ambient,
        LocalTvTrailerPreviews provides trailerPreviews,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (backgroundArtwork && destination != TvDestination.SETTINGS) {
                TvContentAmbientBackground(ambient = ambient)
            }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (sideRail) TvRailTokens.contentStartOffset else 0.dp,
                    top = if (sideRail) TvRailTokens.contentTopPadding else TvLayoutTokens.contentTopPadding,
                )
                .tvContentFocusBoundary(
                    // With the rail, nothing sits above the page, and Left at
                    // the page's edge opens the rail through the key handler below.
                    topNavigationRequester = if (sideRail) FocusRequester.Cancel else navFocus.getValue(destination),
                    leftNavigationRequester = when {
                        sideRail -> FocusRequester.Cancel
                        destination == TvDestination.SETTINGS -> contentFocus.getValue(TvDestination.SETTINGS)
                        else -> FocusRequester.Default
                    },
                )
                .tvContentFocusMemory(focusReturn)
                .then(
                    if (sideRail) {
                        Modifier.tvRailOpensOnLeft(focusManager) {
                            focusReturn.save()
                            focusDestination = destination
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            // Per-destination saveable state (scroll positions, search query,
            // settings section) survives both detail round-trips and tab
            // switches, so returning re-composes the previously focused item
            // and mediaFocusRestore can reach it, per TV-NAV-02/TV-FOC-03.
            contentStates.SaveableStateProvider(destination.name) {
                when (destination) {
                    TvDestination.HOME -> SignedInHome(catalogType = null)

                    TvDestination.MOVIES,
                    TvDestination.SERIES,
                    -> SignedInHome(catalogType = destination.catalogType)

                    TvDestination.DISCOVER -> TvDiscover(
                        browse = state.browse,
                        hasEnabledProviders = state.providers.any { it.enabled },
                        onPrepare = viewModel::prepareDiscover,
                        onBrowseType = viewModel::selectBrowseType,
                        onBrowseCatalog = viewModel::selectBrowseCatalog,
                        onBrowseGenre = viewModel::selectBrowseGenre,
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
                        // QA-08: mapped once per library change, not on every state
                        // emission (a large library is thousands of entries).
                        media = remember(state.library) { state.library.map { it.preview } },
                        onMedia = openMedia,
                        onFocused = { lastFocusedMedia = it },
                        initialFocusRequester = contentFocus.getValue(TvDestination.LIBRARY),
                        restoreMediaKey = pendingMediaKey,
                        onFocusRestored = { pendingMediaKey = null },
                    )

                    TvDestination.SETTINGS -> TvSettings(
                        state = state,
                        viewModel = viewModel,
                        showProfiles = showProfilesSettings,
                        onProfilesShown = { showProfilesSettings = false },
                        sectionFocusRequester = contentFocus.getValue(TvDestination.SETTINGS),
                        topNavigationRequester = if (sideRail) {
                            FocusRequester.Cancel
                        } else {
                            navFocus.getValue(TvDestination.SETTINGS)
                        },
                        updateViewModel = updateViewModel,
                    )
                }
            }
            TvContentFocusAnchor(focusReturn, onFailed = { contentEntry = destination })
        }
        if (sideRail) {
            TvSideRail(
                selectedDestination = destination,
                activeProfile = state.activeProfile,
                focusDestination = focusDestination,
                requesters = navFocus,
                profileRequester = profileFocus,
                onFocusHandled = { focusDestination = null },
                onHasFocus = { hasFocus ->
                    navHasFocus = hasFocus
                    // When the focused page item goes away (a loading row
                    // replaced by content), Compose hands focus to the first
                    // focusable, which is the rail. Opening it uninvited would
                    // cover the page, so focus goes back into the page instead.
                    if (hasFocus && focusDestination == null && !returnToProfileAvatar) contentEntry = destination
                },
                onDestination = {
                    focusReturn.forget()
                    destination = it
                    contentEntry = it
                },
                onReturnToContent = returnToContent,
                onProfileSwitcher = { profileSwitcherOpen = true },
            )
        } else {
            TvTopNavigation(
                selectedDestination = destination,
                activeProfile = state.activeProfile,
                focusDestination = focusDestination,
                requesters = navFocus,
                profileRequester = profileFocus,
                contentDownRequester = contentFocus.getValue(destination),
                onFocusHandled = { focusDestination = null },
                onHasFocus = { navHasFocus = it },
                onDestination = { destination = it },
                onProfileSwitcher = { profileSwitcherOpen = true },
                modifier = Modifier.padding(
                    start = TvLayoutTokens.screenHorizontalPadding,
                    top = TvLayoutTokens.screenTopPadding,
                    end = TvLayoutTokens.screenHorizontalPadding,
                ),
            )
        }
        if (state.refreshing) {
            Text(
                text = stringResource(R.string.updating),
                modifier = if (sideRail) {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = TvLayoutTokens.screenBottomPadding, end = TvLayoutTokens.screenHorizontalPadding)
                } else {
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 76.dp, end = TvLayoutTokens.screenHorizontalPadding)
                },
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
    onHeroMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    onAddSource: () -> Unit,
    onLoadMore: (String) -> Unit,
    onRetry: (String) -> Unit,
    onLoadMoreHome: () -> Unit,
    onRetryHome: () -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
    spotlight: Boolean = false,
    catalogType: String? = null,
) {
    var focusedCandidate by remember { mutableStateOf<MediaPreview?>(null) }
    var contentHasFocus by remember { mutableStateOf(false) }
    // The carousel is a signature part of the Home screen, so it must never
    // disappear while the catalog loads progressively. The user's selection
    // survives catalog updates; only the displayed value falls back to the
    // first item when that selection is no longer part of the catalog.
    var featuredSelection by remember { mutableStateOf<MediaPreview?>(null) }
    // QA-08: never flatten every catalog title here; with 100+ addon rows
    // that ran on each row load. Both lookups stop as early as they can.
    // Movies and Series are this page filtered to one catalog type (TV-CNT-01).
    val sections = remember(state.sections, catalogType) { homeSectionsOfType(state.sections, catalogType) }
    val heroItems = remember(sections) { firstDistinctMedia(sections, limit = 5) }
    val featured = featuredSelection ?: heroItems.firstOrNull()
    // Up next follows the page's catalog type: series only on Series and Home.
    val upNext = remember(state.upNext, catalogType) {
        state.upNext.filter { catalogType == null || it.media.rawType.equals(catalogType, ignoreCase = true) }
    }
    val continueWatching = remember(state.progress, sections, upNext) {
        withUpNext(continueWatchingItemsFromSections(state.progress, sections), upNext)
    }
    val upNextByKey = remember(upNext) { upNext.associateBy { it.media.stableKey } }
    LaunchedEffect(focusedCandidate) {
        focusedCandidate?.let {
            delay(TvMotionTokens.heroUpdateDelayMillis)
            featuredSelection = it
        }
    }
    // SHR-PROD-14: movies only, so Home and Movies, never Series.
    val fitsTonight = rememberFitsTonightSection(
        sections = sections,
        completedVideoIds = LocalTvContentMenuEnvironment.current.completedVideoIds,
        enabled = state.fitsTonight && catalogType != "series",
        bedtimeMinutes = state.bedtimeMinutes,
    )
    val visibleHomeSections = remember(sections, fitsTonight) {
        listOfNotNull(fitsTonight) + sections.filter(CatalogSection::isRenderableHomeCatalogSection)
    }
    // TV-CNT-03 pins the focused row to the top, so the row above is always
    // fully off-screen and the row after next appears on every move. Keep
    // one row composed on each side so D-pad moves never build a row inside
    // the key press or the scroll frame (QA-08).
    val listState = if (spotlight) rememberSpotlightHomeListState() else rememberLazyListState()
    LaunchedEffect(
        listState,
        state.sections.size,
        state.homeCatalogBatch.hasMore,
        state.homeCatalogBatch.loadingMore,
        state.homeCatalogBatch.loadMoreFailed,
        state.homeCatalogBatch.pendingRowCount,
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to
                listState.layoutInfo.totalItemsCount
        }.distinctUntilChanged().collectLatest { (lastVisibleIndex, totalListItems) ->
            // No settle delay: the ViewModel caps windows in flight, and a
            // late request is what makes deep scrolling reach the footer.
            if (
                shouldPrefetchHomeCatalogBatch(
                    lastVisibleIndex = lastVisibleIndex,
                    totalListItems = totalListItems,
                    pendingRows = state.homeCatalogBatch.pendingRowCount,
                    hasMore = state.homeCatalogBatch.hasMore,
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
    val homeLoading = state.initialContentLoading ||
        state.homeCatalogBatch.loadingMore ||
        sections.any(CatalogSection::initialLoading)
    SpotlightColumnScrolling(enabled = spotlight) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { contentHasFocus = it.hasFocus },
            contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
            verticalArrangement = Arrangement.spacedBy(
                if (spotlight) SpotlightRowSpacing else TvLayoutTokens.rowSpacing,
            ),
        ) {
            if (spotlight) {
                // TV-CNT-03: no hero. While nothing has loaded yet, a placeholder
                // row keeps a focus target under the navigation (TV-CNT-02).
                if (heroItems.isEmpty() && continueWatching.isEmpty() && homeLoading) {
                    item("spotlight-loading") {
                        TvSpotlightLoadingRow(Modifier.focusRequester(initialFocusRequester))
                    }
                }
            } else if (featured != null) {
                val media = featured
                item("hero") {
                    TvHero(
                        kenBurnsEnabled = state.kenBurnsEnabled,
                        rowsHaveFocus = contentHasFocus,
                        media = media,
                        onMedia = onHeroMedia,
                        onFocused = { focusedCandidate = media; onFocused(media) },
                        carouselItems = heroItems,
                        onPrevious = { moveCarousel(-1) },
                        onNext = { moveCarousel(1) },
                        modifier = Modifier
                            .padding(horizontal = TvLayoutTokens.screenHorizontalPadding)
                            .mediaFocusRestore(heroRestoreKey(media), restoreMediaKey, onFocusRestored)
                            .focusRequester(initialFocusRequester),
                    )
                }
            } else if (homeLoading) {
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
            if (continueWatching.isNotEmpty() && spotlight) {
                item("continue-watching") {
                    TvSpotlightContinueWatchingRow(
                        items = continueWatching,
                        upNextByKey = upNextByKey,
                        contentHasFocus = contentHasFocus,
                        onMedia = onMedia,
                        onFocused = onFocused,
                        restoreMediaKey = restoreMediaKey,
                        onFocusRestored = onFocusRestored,
                        firstItemFocusRequester = initialFocusRequester,
                    )
                }
            } else if (continueWatching.isNotEmpty()) {
                item("continue-watching") {
                    TvContinueWatchingRow(
                        items = continueWatching,
                        upNextByKey = upNextByKey,
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
                sections.isEmpty()
            ) {
                item("empty") {
                    Column(
                        modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvEmptyMark()
                        Text(
                            stringResource(
                                when (catalogType) {
                                    null -> R.string.install_first_addon
                                    "series" -> R.string.no_series_catalogs
                                    else -> R.string.no_movie_catalogs
                                },
                            ),
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
                if (spotlight) {
                    val leadsHome = continueWatching.isEmpty() && section.id == visibleHomeSections.first().id
                    TvSpotlightRow(
                        section = section,
                        contentHasFocus = contentHasFocus,
                        onMedia = onMedia,
                        onFocused = onFocused,
                        onLoadMore = { onLoadMore(section.id) },
                        onRetry = { onRetry(section.id) },
                        restoreMediaKey = restoreMediaKey,
                        onFocusRestored = onFocusRestored,
                        firstItemFocusRequester = initialFocusRequester.takeIf { leadsHome },
                    )
                    return@items
                }
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
}

internal fun heroRestoreKey(media: MediaPreview): String = "hero:${media.stableKey}"

// QA-08: skeleton pulses run every frame while content loads. Reading the
// animated value only while drawing keeps them out of recomposition and
// layout; the pulse range and timing are unchanged (TV-CNT-02).
@Composable
internal fun rememberSkeletonPulse(label: String): State<Float> =
    rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0.58f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton pulse",
    )

internal fun Modifier.skeletonPulseBackground(color: Color, alpha: () -> Float): Modifier =
    drawBehind { drawRect(color.copy(alpha = alpha())) }

@Composable
private fun TvHomeCatalogLoadingSkeleton() {
    val loadingDescription = stringResource(R.string.loading_more_rows)
    val pulse = rememberSkeletonPulse(label = "home catalog loading")
    val skeletonColor = MaterialTheme.colorScheme.surfaceVariant

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
                        .skeletonPulseBackground(skeletonColor) { pulse.value },
                )
                TvCatalogItemsLoadingSkeleton(skeletonColor) { pulse.value }
            }
        }
    }
}

@Composable
private fun TvCatalogItemsLoadingSkeleton(
    skeletonColor: Color,
    pulse: () -> Float,
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
                        .skeletonPulseBackground(skeletonColor, pulse),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .skeletonPulseBackground(skeletonColor) { pulse() * 0.8f },
                )
            }
        }
    }
}

@Composable
private fun TvContinueWatchingRow(
    items: List<Pair<MediaPreview, WatchProgress>>,
    upNextByKey: Map<String, UpNextItem>,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    val row = rememberTvRowFocus()
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
            modifier = Modifier.tvRowFocus(row),
            horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
            contentPadding = PaddingValues(
                start = TvLayoutTokens.screenHorizontalPadding,
                end = TvLayoutTokens.screenHorizontalPadding,
                bottom = TvLayoutTokens.screenBottomPadding,
            ),
        ) {
            itemsIndexed(items, key = { _, item -> item.first.stableKey }) { index, (media, progress) ->
                TvContinueWatchingCard(
                    media = media,
                    progress = progress,
                    upNext = upNextByKey[media.stableKey],
                    onClick = { onMedia(media) },
                    onFocused = { onFocused(media) },
                    modifier = Modifier
                        .mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored)
                        .tvRowItem(row, index),
                )
            }
        }
    }
}

@Composable
private fun TvHeroLoadingSkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse(label = "hero loading")
    val skeletonColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TvLayoutTokens.heroHeight)
            .clip(TvShapeTokens.hero)
            .skeletonPulseBackground(skeletonColor) { pulse.value }
            .focusable(),
    )
}

@Composable
private fun TvHero(
    media: MediaPreview,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    kenBurnsEnabled: Boolean,
    rowsHaveFocus: Boolean,
    carouselItems: List<MediaPreview>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val ambient = LocalTvContentAccent.current
    val primary = MaterialTheme.colorScheme.primary
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
                // Only the focused hero subscribes to accent changes (QA-08).
                color = if (focused) ambient.accent ?: primary else Color.Transparent,
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
        TvLayerlessCrossfade(
            targetState = media,
            reducedMotion = reducedMotion,
            label = "hero artwork",
            modifier = Modifier.fillMaxSize(),
            enterOffsetFraction = 1f / 80f,
            exitOffsetFraction = -1f / 100f,
        ) { featuredMedia ->
            TvHeroArtwork(
                media = featuredMedia,
                userEnabled = kenBurnsEnabled,
                reducedMotion = reducedMotion,
                // Drift while the hero or the navigation above it has focus;
                // hold the frame while the viewer browses rows (QA-08).
                active = focused || !rowsHaveFocus,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .tvHeroScrim(
                    background = MaterialTheme.colorScheme.background,
                    primary = MaterialTheme.colorScheme.primary,
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
        (releaseCountdownText(presentation.upcomingReleaseMillis, ReleaseKind.TITLE) ?: presentation.year?.toString())
            ?.let(::add)
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
            val pulse = rememberSkeletonPulse(label = "catalog row loading")
            TvCatalogItemsLoadingSkeleton(
                skeletonColor = MaterialTheme.colorScheme.surfaceVariant,
                pulse = { pulse.value },
            )
        } else if (section.items.isNotEmpty() || section.hasMore || section.loadMoreError != null) {
            val row = rememberTvRowFocus()
            val showAction = section.loadMoreError != null || section.hasMore
            val trailing = rememberTrailingActionFocus(showAction, section.items.size)
            LazyRow(
                modifier = Modifier.tvRowFocus(row),
                horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
                contentPadding = PaddingValues(
                    start = TvLayoutTokens.screenHorizontalPadding,
                    end = TvLayoutTokens.screenHorizontalPadding,
                    bottom = TvLayoutTokens.screenBottomPadding,
                ),
            ) {
                itemsIndexed(section.items, key = { _, media -> media.stableKey }) { index, media ->
                    TvMediaCard(
                        media = media,
                        onClick = { onMedia(media) },
                        onFocused = { onFocused(media) },
                        modifier = Modifier
                            .mediaFocusRestore(media.stableKey, restoreMediaKey, onFocusRestored)
                            .tvRowItem(row, index)
                            .trailingItem(trailing, index),
                        showLabel = true,
                        revealLabelOnFocus = true,
                    )
                }
                if (trailing.visible(showAction)) {
                    item("catalog-action") {
                        TvAction(
                            label = stringResource(if (section.loadMoreError != null) R.string.retry else R.string.load_more),
                            icon = Icons.Outlined.Refresh,
                            modifier = Modifier.trailingAction(trailing),
                            onClick = {
                                trailing.onPressed(section.items.size)
                                if (section.loadMoreError != null) onRetry() else onLoadMore()
                            },
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
                itemsIndexed(media, key = { _, item -> item.stableKey }, contentType = { _, _ -> "poster" }) { index, item ->
                    TvMediaCard(
                        media = item,
                        onClick = { onMedia(item) },
                        onFocused = { onFocused(item) },
                        modifier = (if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            .mediaFocusRestore(item.stableKey, restoreMediaKey, onFocusRestored),
                        showLabel = true,
                        compactLandscape = true,
                    )
                }
                if (section?.loadMoreError != null || section?.hasMore == true) {
                    item("catalog-action", contentType = "action") {
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

/** Shown name for a catalog type; add-ons may declare types beyond movie and series. */
@Composable
private fun catalogTypeLabel(type: String): String = when (type.lowercase()) {
    "movie" -> stringResource(R.string.movies)
    "series" -> stringResource(R.string.series)
    else -> type.replaceFirstChar(Char::uppercase)
}

/** Posters left below the viewport when the next page is requested. */
private const val DISCOVER_PREFETCH_ITEMS = 10

/**
 * Discover is one scrolling page: a type and catalog strip, a genre strip, and
 * the poster grid for that selection. Choosing a chip filters in place, so
 * focus never leaves the strip it was on (TV-NAV-05, TV-CNT-02).
 */
@Composable
private fun TvDiscover(
    browse: CatalogBrowseState,
    hasEnabledProviders: Boolean,
    onPrepare: () -> Unit,
    onBrowseType: (String) -> Unit,
    onBrowseCatalog: (String) -> Unit,
    onBrowseGenre: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onMedia: (MediaPreview) -> Unit,
    onFocused: (MediaPreview) -> Unit,
    initialFocusRequester: FocusRequester,
    restoreMediaKey: String?,
    onFocusRestored: () -> Unit,
) {
    LaunchedEffect(Unit) { onPrepare() }
    // Catalogs that need an input Discover cannot supply (Cinemeta's
    // calendar and last-videos feeds) are not browsable here.
    val targets = remember(browse.targets) { browse.targets.filter { it.unavailableReason == null } }
    if (targets.isEmpty()) {
        TvDiscoverMessage(
            text = stringResource(
                if (browse.targets.isEmpty() && !hasEnabledProviders) {
                    R.string.install_first_addon
                } else {
                    R.string.nothing_here
                },
            ),
        )
        return
    }
    val types = remember(targets) { targets.map { it.catalog.type }.distinct() }
    val selectedType = browse.selectedType?.takeIf(types::contains) ?: types.first()
    val catalogs = remember(targets, selectedType) { targets.filter { it.catalog.type == selectedType } }
    val selectedTarget = catalogs.firstOrNull { it.id == browse.selectedCatalogId }
    val genres = selectedTarget?.genres.orEmpty()
    val result = browse.result
    val items = result?.items.orEmpty()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val typeRow = rememberTvRowFocus(types.indexOf(selectedType).coerceAtLeast(0))
    val genreRow = rememberTvRowFocus((genres.indexOf(browse.selectedGenre) + 1).coerceAtLeast(0))

    // Back from a scrolled grid returns to the filters first; the next Back
    // reaches the navigation (TV-NAV-02, TV-NAV-07).
    val scrolled by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    BackHandler(enabled = scrolled) {
        scope.launch {
            gridState.scrollToItem(0)
            withFrameNanos { }
            runCatching { typeRow.entry.requestFocus() }
        }
    }

    // Pages arrive as the viewer nears the end; no Load more stop (SHR-PROD-02).
    val currentResult by rememberUpdatedState(result)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - DISCOVER_PREFETCH_ITEMS
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                val section = currentResult ?: return@collect
                if (nearEnd && section.hasMore && !section.loadingMore && section.loadMoreError == null && section.items.isNotEmpty()) {
                    onLoadMore()
                }
            }
    }

    val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvLayoutTokens.screenHorizontalPadding,
            end = TvLayoutTokens.screenHorizontalPadding,
            bottom = TvLayoutTokens.bottomListPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "discover-selectors", span = fullSpan, contentType = "selectors") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Type first, then that type's catalogs, so "Popular" is never
                // ambiguous between movies and series. Lazy: with many add-ons
                // only the visible chips are composed and measured (QA-08).
                val multipleProviders = remember(catalogs) { catalogs.distinctBy { it.providerId }.size > 1 }
                LazyRow(
                    modifier = Modifier.tvRowFocus(typeRow),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(types, key = { _, type -> "type:$type" }, contentType = { _, _ -> "chip" }) { index, type ->
                        TvFilterChip(
                            label = catalogTypeLabel(type),
                            selected = type == selectedType,
                            onClick = { if (type != selectedType) onBrowseType(type) },
                            modifier = (if (type == selectedType) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                .tvRowItem(typeRow, index),
                        )
                    }
                    item(key = "type-divider", contentType = "divider") {
                        Box(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .size(width = 1.dp, height = 24.dp)
                                .background(TvSurfaceTokens.subtleBorder),
                        )
                    }
                    itemsIndexed(catalogs, key = { _, target -> "catalog:${target.id}" }, contentType = { _, _ -> "chip" }) { index, target ->
                        TvFilterChip(
                            label = if (multipleProviders) "${target.catalog.name} · ${target.providerName}" else target.catalog.name,
                            selected = target.id == selectedTarget?.id,
                            onClick = { if (target.id != selectedTarget?.id) onBrowseCatalog(target.id) },
                            modifier = Modifier.tvRowItem(typeRow, types.size + index),
                        )
                    }
                }
                if (genres.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.tvRowFocus(genreRow),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "all-genres") {
                            TvFilterChip(
                                label = stringResource(R.string.all_genres),
                                selected = browse.selectedGenre == null,
                                onClick = { if (browse.selectedGenre != null) onBrowseGenre(null) },
                                modifier = Modifier.tvRowItem(genreRow, 0),
                            )
                        }
                        itemsIndexed(genres, key = { _, genre -> "genre:$genre" }) { index, genre ->
                            TvFilterChip(
                                label = genre,
                                selected = browse.selectedGenre == genre,
                                onClick = { if (browse.selectedGenre != genre) onBrowseGenre(genre) },
                                modifier = Modifier.tvRowItem(genreRow, index + 1),
                            )
                        }
                    }
                }
            }
        }
        item(key = "discover-heading", span = fullSpan, contentType = "heading") {
            Column(Modifier.padding(top = 8.dp)) {
                Text(
                    text = listOfNotNull(selectedTarget?.catalog?.name, browse.selectedGenre)
                        .joinToString(" · ")
                        .ifEmpty { catalogTypeLabel(selectedType) },
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                selectedTarget?.providerName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    )
                }
            }
        }
        when {
            // Failure stays under the filters, which remain usable (SHR-PROD-04).
            result?.errorMessage != null && items.isEmpty() -> item(key = "discover-error", span = fullSpan) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = result.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TvAction(
                        label = stringResource(R.string.retry),
                        icon = Icons.Outlined.Refresh,
                        onClick = onRetry,
                    )
                }
            }
            items.isEmpty() && (browse.loading || result == null) -> items(10, key = { "discover-skeleton-$it" }, contentType = { "skeleton" }) {
                TvPosterSkeleton()
            }
            items.isEmpty() -> item(key = "discover-empty", span = fullSpan) {
                Text(
                    text = stringResource(R.string.nothing_here),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> {
                itemsIndexed(items, key = { _, item -> item.stableKey }, contentType = { _, _ -> "poster" }) { _, item ->
                    TvMediaCard(
                        media = item,
                        onClick = { onMedia(item) },
                        onFocused = { onFocused(item) },
                        showLabel = true,
                        revealLabelOnFocus = true,
                        modifier = Modifier.mediaFocusRestore(item.stableKey, restoreMediaKey, onFocusRestored),
                    )
                }
                if (result?.loadingMore == true) {
                    items(5, key = { "discover-more-$it" }, contentType = { "skeleton" }) { TvPosterSkeleton() }
                }
                if (result?.loadMoreError != null) {
                    item(key = "discover-retry", span = fullSpan) {
                        TvAction(
                            label = stringResource(R.string.retry),
                            icon = Icons.Outlined.Refresh,
                            onClick = onRetry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDiscoverMessage(text: String) {
    Column(
        modifier = Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvEmptyMark()
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** A poster-sized placeholder that keeps the final grid geometry (TV-CNT-02). */
@Composable
private fun TvPosterSkeleton() {
    val pulse = rememberSkeletonPulse(label = "poster loading")
    Box(
        modifier = Modifier
            .size(width = TvLayoutTokens.posterWidth, height = TvLayoutTokens.posterHeight)
            .clip(TvShapeTokens.card)
            .skeletonPulseBackground(Color.White) { 0.04f + 0.05f * pulse.value },
    )
}

@Composable
private fun TvSearch(
    initialSearch: String,
    state: AppUiState,
    onSearch: (String) -> Unit,
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
    val blank = query.isBlank()
    // Rows are titled by what they hold: every search catalog is named after
    // its browse feed ("Popular"), which read as a filter, not a result.
    val typeLabels = mapOf(
        "movie" to stringResource(R.string.movies),
        "series" to stringResource(R.string.series),
    )
    val sections = if (blank) {
        state.sections.filter { it.items.isNotEmpty() }.take(2)
    } else {
        state.searchSections.map { section ->
            section.copy(
                title = typeLabels[section.baseQuery.type.lowercase()]
                    ?: section.baseQuery.type.replaceFirstChar(Char::uppercase),
            )
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.rowSpacing),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
    ) {
        item(key = "search-field") {
            Box(Modifier.padding(horizontal = TvLayoutTokens.screenHorizontalPadding)) {
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
            }
        }
        when {
            !blank && state.searching && sections.isEmpty() -> item(key = "search-loading") {
                val pulse = rememberSkeletonPulse(label = "search loading")
                TvCatalogItemsLoadingSkeleton(
                    skeletonColor = MaterialTheme.colorScheme.surfaceVariant,
                    pulse = { pulse.value },
                )
            }
            !blank && sections.none { it.items.isNotEmpty() || it.errorMessage != null } && !state.searching ->
                item(key = "search-empty") {
                    TvDiscoverMessage(text = stringResource(R.string.search_no_results))
                }
            else -> items(sections, key = CatalogSection::id) { section ->
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

@Composable
private fun TvSourcePickerScreen(
    picker: SourcePickerState,
    onProvider: (String?) -> Unit,
    onSource: (StreamCandidate) -> Unit,
) {
    val filtersFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { filtersFocus.requestFocus() }
    Box(Modifier.fillMaxSize()) {
        TvBakedBackdrop(media = picker.media, style = TvBackdropStyle.SOURCES, modifier = Modifier.fillMaxSize())
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
                    val sourceKeys = remember(picker.visibleSources) { sourceItemKeys(picker.visibleSources) }
                    // Every filter opens its list at the first source; a fresh state per
                    // provider stops the keyed list from jumping to the old anchor.
                    val listState = remember(picker.selectedProviderId) { LazyListState() }
                    var listFocused by remember { mutableStateOf(false) }
                    // Slower providers can add sources above the first one; until the
                    // viewer enters the list, keep it pinned to the top.
                    LaunchedEffect(sourceKeys.firstOrNull()) {
                        if (!listFocused) listState.scrollToItem(0)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).onFocusChanged { listFocused = it.hasFocus },
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
                            val fitAdvisor = LocalSourceFit.current
                            val fit = remember(source, fitAdvisor) { fitAdvisor.fitFor(source) }
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
                                        // Imported badges replace Lamphaus's own labels, as in Nuvio.
                                        if (badgeMatcher.isActive) {
                                            StreamBadgeRow(importedBadges)
                                        } else if (presentation.badges.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                presentation.badges.forEach { badge ->
                                                    TvSourceBadge(badge, focused)
                                                }
                                            }
                                        }
                                        // Addon-formatted text is shown whole: the addon's
                                        // formatter owns the card's lines (as in Nuvio).
                                        Text(
                                            presentation.title,
                                            color = primaryColor,
                                            maxLines = if (presentation.usesProviderFormatting) Int.MAX_VALUE else 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        presentation.description?.let { description ->
                                            Text(
                                                description,
                                                color = secondaryColor,
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
                                                color = secondaryColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                        fit?.let {
                                            Text(
                                                sourceFitLabel(it),
                                                color = secondaryColor,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // Last item, so it appears and disappears without moving any
                        // source the viewer is looking at.
                        if (picker.pendingProviderCount > 0) {
                            item("pending-providers") {
                                Text(
                                    pluralStringResource(
                                        R.plurals.sources_still_loading,
                                        picker.pendingProviderCount,
                                        picker.pendingProviderCount,
                                    ),
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
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
    val seasonRow = rememberTvRowFocus(seasonNumbers.indexOf(selectedSeason).coerceAtLeast(0))
    LazyRow(
        modifier = modifier.tvRowFocus(seasonRow),
        contentPadding = PaddingValues(
            start = TvLayoutTokens.screenHorizontalPadding,
            end = TvLayoutTokens.screenHorizontalPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(seasonNumbers, key = { _, season -> season }) { index, season ->
            TvFilterChip(
                label = stringResource(R.string.season_format, season),
                selected = selectedSeason == season,
                onClick = { onSeasonSelected(season) },
                modifier = Modifier.tvRowItem(seasonRow, index),
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
/** "Previously": the last finished episode, for a viewer returning to a series (SHR-PROD-13). */
@Composable
private fun TvSeriesRecap(episode: Episode) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.recap_heading),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = recapEpisodeTitle(episode),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TvExpandableText(
            text = episode.overview.orEmpty(),
            collapsedLines = 3,
            // Below the actions, so Down continues to the episodes (TV-NAV-05).
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    recapEnabled: Boolean,
    seasonTimeLeftEnabled: Boolean,
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
    // Open on the episode to play next: its season, scrolled to it, and the
    // Play action resumes or starts it (SHR-PROD-02).
    val nextUp = remember(detail.episodes, progress, watchedEpisodeIds) {
        nextUpEpisode(detail.episodes, progress, watchedEpisodeIds)
    }
    val recap = remember(detail.episodes, progress, watchedEpisodeIds, recapEnabled) {
        if (recapEnabled) seriesRecap(detail.episodes, progress, watchedEpisodeIds) else null
    }
    val nextUpSeason = nextUp?.episode?.season?.takeIf { it in seasonNumbers }
    var selectedSeason by remember(detail.preview.stableKey, seasonNumbers) {
        mutableStateOf(nextUpSeason ?: seasonNumbers.firstOrNull())
    }
    var focusedEpisode by remember(detail.preview.stableKey) { mutableStateOf<Episode?>(null) }
    val visibleEpisodes = if (showSeasonChips && selectedSeason != null) {
        detail.episodes.filter { it.season == selectedSeason }
    } else {
        detail.episodes
    }
    LaunchedEffect(detail.preview.stableKey, seasonNumbers) {
        if (selectedSeason == null || selectedSeason !in seasonNumbers) {
            selectedSeason = nextUpSeason ?: seasonNumbers.firstOrNull()
        }
    }
    Box(Modifier.fillMaxSize()) {
        TvBakedBackdrop(media = detail.preview, style = TvBackdropStyle.DETAIL, modifier = Modifier.fillMaxSize())
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
                    val actionRow = rememberTvRowFocus()
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .tvRowFocus(actionRow, plainRow = true),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val nextUpLabel = nextUp?.episode?.numberParts()?.let { number ->
                            if (number.season != null && number.episode != null) {
                                stringResource(R.string.episode_format, number.season, number.episode)
                            } else {
                                null
                            }
                        }
                        TvAction(
                            label = when {
                                nextUp == null || nextUpLabel == null -> stringResource(R.string.play)
                                nextUp.kind == NextUpKind.RESUME -> stringResource(R.string.resume_episode_format, nextUpLabel)
                                else -> stringResource(R.string.play_episode_format, nextUpLabel)
                            },
                            icon = Icons.Outlined.PlayArrow,
                            modifier = Modifier
                                .focusRequester(playFocus)
                                .tvRowItem(actionRow, 0)
                                .onFocusChanged { if (it.isFocused) lastActionFocus = playFocus },
                            // A series plays its next episode, never the series id.
                            onClick = { onPlay(nextUp?.episode) },
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
                                .tvRowItem(actionRow, 1)
                                .onFocusChanged { if (it.isFocused) lastActionFocus = libraryFocus },
                            // A toggle, so the action stays reachable (TV-FOC-01, QA-07).
                            onClick = onLibrary,
                        )
                        TvAction(
                            label = stringResource(R.string.edit_artwork),
                            icon = Icons.Outlined.Palette,
                            modifier = Modifier.tvRowItem(actionRow, 2),
                            onClick = onEditArtwork,
                        )
                    }
                    recap?.let { episode ->
                        TvSeriesRecap(episode)
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
                    val timeLeftSeason = if (showSeasonChips) selectedSeason else seasonNumbers.singleOrNull()
                    val timeLeft = remember(detail, timeLeftSeason, progress, watchedEpisodeIds, seasonTimeLeftEnabled) {
                        if (seasonTimeLeftEnabled) {
                            seasonTimeLeft(detail.episodes, timeLeftSeason, progress, watchedEpisodeIds, detail.runtimeMinutes)
                        } else {
                            null
                        }
                    }
                    Column(
                        modifier = Modifier.padding(
                            start = TvLayoutTokens.screenHorizontalPadding,
                            end = TvLayoutTokens.screenHorizontalPadding,
                            bottom = if (showSeasonChips) 12.dp else 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.episodes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        // SHR-PROD-14: what is left of the season on view.
                        timeLeft?.let {
                            Text(
                                text = seasonTimeLeftText(it),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
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
                    val nextUpIndex = visibleEpisodes.indexOfFirst { it.id == nextUp?.episode?.id }
                    val episodeRowState = remember(selectedSeason) {
                        LazyListState(firstVisibleItemIndex = nextUpIndex.coerceAtLeast(0))
                    }
                    val episodeRow = remember(selectedSeason) { TvRowFocus(nextUpIndex.coerceAtLeast(0)) }
                    LazyRow(
                        state = episodeRowState,
                        modifier = Modifier.tvRowFocus(episodeRow),
                        contentPadding = PaddingValues(
                            start = TvLayoutTokens.screenHorizontalPadding,
                            end = TvLayoutTokens.screenHorizontalPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.itemSpacing),
                    ) {
                        itemsIndexed(visibleEpisodes, key = { _, episode -> episode.id }) { index, episode ->
                            TvEpisodeCard(
                                modifier = Modifier.tvRowItem(episodeRow, index),
                                media = detail.preview,
                                episode = episode,
                                watched = episode.id in watchedEpisodeIds,
                                progress = progress.firstOrNull { it.videoId == episode.id },
                                spoilerProtection = spoilerProtection,
                                nextUpKind = nextUp?.kind?.takeIf { episode.id == nextUp.episode.id },
                                onFocused = { focusedEpisode = episode },
                                onClick = { onPlay(episode) },
                            )
                        }
                    }
                }
                (focusedEpisode ?: nextUp?.episode)?.let { shown ->
                    item("episode-detail") {
                        val shownWatched = shown.id in watchedEpisodeIds
                        TvEpisodeDetailLine(
                            episode = shown,
                            watched = shownWatched,
                            progress = progress.firstOrNull { it.videoId == shown.id },
                            synopsisHidden = shown.id != nextUp?.episode?.id &&
                                spoilerProtection.shouldBlur(SpoilerContent.EPISODE_SYNOPSIS, shownWatched),
                            modifier = Modifier.padding(
                                start = TvLayoutTokens.screenHorizontalPadding,
                                end = TvLayoutTokens.screenHorizontalPadding,
                                top = 20.dp,
                            ),
                        )
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



private enum class TvSettingsSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    PROFILES(R.string.profiles, Icons.Outlined.Person),
    SOURCES(R.string.addons, Icons.Outlined.Add),
    INTEGRATIONS(R.string.integrations, Icons.Outlined.Extension),
    PLAYBACK(R.string.playback, Icons.Outlined.PlayArrow),
    APPEARANCE(R.string.appearance, Icons.Outlined.Palette),
    SPOILERS(R.string.spoiler_protection, Icons.Outlined.Visibility),
    ABOUT(R.string.about, Icons.Outlined.Info),
}
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvSettings(
    state: AppUiState,
    viewModel: AppViewModel,
    showProfiles: Boolean,
    onProfilesShown: () -> Unit,
    sectionFocusRequester: FocusRequester,
    topNavigationRequester: FocusRequester,
    updateViewModel: com.lamphaus.app.update.UpdateViewModel? = null,
) {
    var section by rememberSaveable { mutableStateOf(TvSettingsSection.PROFILES) }
    // QA-08: building a pane costs 100-175 ms on low-end TV CPUs, so the pane
    // follows menu focus once it settles instead of on every move. Selecting
    // or moving right into the pane switches at once.
    var shownSection by rememberSaveable { mutableStateOf(section) }
    var moveIntoPane by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // "Manage profiles" in the profile switcher opens this section directly.
    LaunchedEffect(showProfiles) {
        if (showProfiles) {
            section = TvSettingsSection.PROFILES
            shownSection = TvSettingsSection.PROFILES
            onProfilesShown()
        }
    }
    LaunchedEffect(section) {
        if (shownSection != section) {
            delay(TvMotionTokens.settingsPaneSettleMillis)
            shownSection = section
        }
    }
    LaunchedEffect(moveIntoPane) {
        if (moveIntoPane) {
            // Let the newly shown pane compose before focus searches it.
            withFrameNanos { }
            withFrameNanos { }
            focusManager.moveFocus(FocusDirection.Right)
            moveIntoPane = false
        }
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvLayoutTokens.screenHorizontalPadding),
    ) {
        Column(
            modifier = Modifier
                .width(TvLayoutTokens.settingsMenuWidth)
                .focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionRight &&
                        shownSection != section
                    ) {
                        shownSection = section
                        moveIntoPane = true
                        true
                    } else {
                        false
                    }
                },
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
                    onClick = {
                        section = item
                        shownSection = item
                    },
                )
            }
        }
        Spacer(Modifier.width(72.dp))
        Box(
            Modifier
                // Never pushes past the screen when the side rail shares the width (TV-TOK-01).
                .weight(1f, fill = false)
                .width(TvLayoutTokens.settingsContentWidth)
                .fillMaxHeight()
                // The pane is one group: Left returns to its section in the
                // menu, Up to the navigation, and Down at its end stays put
                // instead of landing on (and switching to) another section
                // (TV-NAV-05).
                .focusProperties {
                    exit = { direction ->
                        when (direction) {
                            FocusDirection.Left -> sectionFocusRequester
                            FocusDirection.Up -> topNavigationRequester
                            FocusDirection.Down, FocusDirection.Right -> FocusRequester.Cancel
                            else -> FocusRequester.Default
                        }
                    }
                }
                .focusGroup(),
        ) {
            when (shownSection) {
                TvSettingsSection.PROFILES -> TvProfilesSettings(state, viewModel)
                TvSettingsSection.SOURCES -> TvSourcesSettings(state, viewModel)
                TvSettingsSection.INTEGRATIONS -> TvIntegrationsSettings(state, viewModel)
                TvSettingsSection.PLAYBACK -> TvPlaybackSettings(state, viewModel)
                TvSettingsSection.APPEARANCE -> TvAppearanceSettings(state, viewModel)
                TvSettingsSection.SPOILERS -> TvSpoilerSettings(state, viewModel)
                TvSettingsSection.ABOUT -> TvAboutSettings(updateViewModel)
            }
        }
    }
}

@Composable
private fun TvPlaybackSettings(state: AppUiState, viewModel: AppViewModel) {
    val profile = state.profilePlaybackPreferences
    val device = state.devicePlaybackConfig
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
    ) {
        item {
            Text(stringResource(R.string.playback), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.ask_before_next),
                description = stringResource(R.string.ask_before_next_description),
                checked = state.playbackSettings.askBeforeNextEpisode,
                onCheckedChange = {
                    viewModel.setPlaybackSettings(state.playbackSettings.copy(askBeforeNextEpisode = it))
                },
            )
        }
        item {
            // Asking replaces auto-play while it is on.
            TvSettingsToggleRow(
                title = stringResource(R.string.auto_play_next),
                description = stringResource(
                    if (state.playbackSettings.askBeforeNextEpisode) {
                        R.string.auto_play_next_description_asking
                    } else {
                        R.string.auto_play_next_description
                    },
                ),
                checked = state.playbackSettings.autoPlayNextEpisode,
                enabled = !state.playbackSettings.askBeforeNextEpisode,
                onCheckedChange = {
                    viewModel.setPlaybackSettings(state.playbackSettings.copy(autoPlayNextEpisode = it))
                },
            )
        }
        item {
            TvSettingsChoiceRow(
                title = "Default audio",
                description = "Original first, then your preferred language",
                value = tvPlaybackLanguageLabel(profile.audioLanguageTag),
                onClick = {
                    val index = TV_PLAYER_LANGUAGE_OPTIONS.indexOfFirst { it.first == profile.audioLanguageTag }
                    val next = TV_PLAYER_LANGUAGE_OPTIONS[(index + 1).coerceAtLeast(0) % TV_PLAYER_LANGUAGE_OPTIONS.size]
                    viewModel.setProfilePlaybackPreferences(profile.copy(audioLanguageTag = next.first))
                },
            )
        }
        item {
            TvSettingsChoiceRow(
                title = "Default subtitles",
                description = "Applied when this title has no remembered choice",
                value = tvSubtitleDefaultLabel(profile.subtitleDefaultMode),
                onClick = {
                    val entries = SubtitleDefaultMode.entries
                    viewModel.setProfilePlaybackPreferences(
                        profile.copy(subtitleDefaultMode = entries[(entries.indexOf(profile.subtitleDefaultMode) + 1) % entries.size]),
                    )
                },
            )
        }
        item {
            TvSettingsToggleRow(
                title = "Original colors",
                description = "Keep HDR and Dolby Vision color mapping when this display supports it",
                checked = profile.originalColors,
                onCheckedChange = {
                    viewModel.setProfilePlaybackPreferences(profile.copy(originalColors = it))
                },
            )
        }
        item {
            TvSettingsChoiceRow(
                title = "Match frame rate",
                description = "Change refresh rate to reduce judder",
                value = tvFrameRateMatchingLabel(device.frameRateMatching),
                onClick = {
                    val entries = FrameRateMatching.entries
                    viewModel.setDevicePlaybackConfig(
                        device.copy(frameRateMatching = entries[(entries.indexOf(device.frameRateMatching) + 1) % entries.size]),
                    )
                },
            )
        }
        item {
            TvSettingsToggleRow(
                title = "Match resolution",
                description = "Switch to the source resolution when the display offers it",
                checked = device.resolutionMatching == ResolutionMatching.MATCH_SOURCE,
                onCheckedChange = {
                    viewModel.setDevicePlaybackConfig(
                        device.copy(
                            resolutionMatching = if (it) ResolutionMatching.MATCH_SOURCE else ResolutionMatching.OFF,
                        ),
                    )
                },
            )
        }
        item {
            Text(
                text = "Audio and video · ${PlaybackEngineOptions.APPLIES_NEXT_PLAYBACK}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item {
            TvSettingsChoiceRow(
                title = "Audio output",
                description = PlaybackEngineOptions.AUDIO_OUTPUT_DESCRIPTION,
                value = PlaybackEngineOptions.audioOutputLabel(device.audioOutputMode),
                onClick = {
                    viewModel.setDevicePlaybackConfig(
                        device.copy(
                            audioOutputMode = PlaybackEngineOptions.next(
                                PlaybackEngineOptions.audioOutputModes, device.audioOutputMode,
                            ),
                        ),
                    )
                },
            )
        }
        item {
            TvSettingsChoiceRow(
                title = "Downmix",
                description = PlaybackEngineOptions.DOWNMIX_DESCRIPTION,
                value = PlaybackEngineOptions.downmixLabel(device.downmixMode),
                onClick = {
                    viewModel.setDevicePlaybackConfig(
                        device.copy(
                            downmixMode = PlaybackEngineOptions.next(PlaybackEngineOptions.downmixModes, device.downmixMode),
                        ),
                    )
                },
            )
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.night_listening_setting),
                description = stringResource(R.string.night_listening_setting_description),
                checked = device.nightListening,
                onCheckedChange = { viewModel.setDevicePlaybackConfig(device.copy(nightListening = it)) },
            )
        }
        item {
            TvSettingsChoiceRow(
                title = "Dolby Vision",
                description = PlaybackEngineOptions.DOLBY_VISION_DESCRIPTION,
                value = PlaybackEngineOptions.dolbyVisionLabel(device.dolbyVisionHandling),
                onClick = {
                    viewModel.setDevicePlaybackConfig(
                        device.copy(
                            dolbyVisionHandling = PlaybackEngineOptions.next(
                                PlaybackEngineOptions.dolbyVisionModes, device.dolbyVisionHandling,
                            ),
                        ),
                    )
                },
            )
        }
        item {
            TvSettingsChoiceRow(
                title = "Decoder priority",
                description = PlaybackEngineOptions.DECODER_PRIORITY_DESCRIPTION,
                value = PlaybackEngineOptions.decoderPriorityLabel(device.decoderPriority),
                onClick = {
                    viewModel.setDevicePlaybackConfig(
                        device.copy(
                            decoderPriority = PlaybackEngineOptions.next(
                                PlaybackEngineOptions.decoderPriorities, device.decoderPriority,
                            ),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun TvSettingsChoiceRow(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
) {
    TvFocusableSurface(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) { focused ->
        val primary = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground
        val secondary = if (focused) {
            TvFocusTokens.focusedContent.copy(alpha = 0.76f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = primary)
                Text(description, style = MaterialTheme.typography.bodySmall, color = secondary)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, color = primary)
        }
    }
}

private val TV_PLAYER_LANGUAGE_OPTIONS = listOf(
    "" to "Original / device",
    "en" to "English",
    "tr" to "Turkish",
    "de" to "German",
    "es" to "Spanish",
    "fr" to "French",
    "ja" to "Japanese",
    "ko" to "Korean",
)

private fun tvPlaybackLanguageLabel(tag: String): String =
    TV_PLAYER_LANGUAGE_OPTIONS.firstOrNull { it.first == tag }?.second ?: java.util.Locale.forLanguageTag(tag).displayLanguage

private fun tvSubtitleDefaultLabel(mode: SubtitleDefaultMode): String = when (mode) {
    SubtitleDefaultMode.OFF -> "Off"
    SubtitleDefaultMode.FORCED_ONLY -> "Forced only"
    SubtitleDefaultMode.PREFERRED_LANGUAGE -> "Preferred language"
}

private fun tvFrameRateMatchingLabel(mode: FrameRateMatching): String = when (mode) {
    FrameRateMatching.OFF -> "Off"
    FrameRateMatching.SEAMLESS_ONLY -> "Seamless only"
    FrameRateMatching.ALWAYS -> "Always"
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
    var pickerProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    var returnFocusTo by remember { mutableStateOf<String?>(null) }
    // TV-NAV-02: closing the picker returns focus to the row that opened it.
    LaunchedEffect(returnFocusTo) {
        val id = returnFocusTo ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { rowFocus[id]?.requestFocus() }
        returnFocusTo = null
    }
    state.profiles.firstOrNull { it.id == pickerProfileId }?.let { profile ->
        val close = {
            pickerProfileId = null
            returnFocusTo = profile.id
        }
        TvAvatarPicker(
            profile = profile,
            accountPhotoAvailable = LocalAccountPhotoUrl.current != null,
            onAvatar = { key ->
                viewModel.setProfileAvatar(profile.id, key)
                close()
            },
            onDismiss = close,
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = TvLayoutTokens.bottomListPadding),
    ) {
        item {
            Text(stringResource(R.string.profiles), style = MaterialTheme.typography.headlineSmall)
        }
        items(state.profiles, key = { it.id }) { profile ->
            val requester = remember(profile.id) { FocusRequester().also { rowFocus[profile.id] = it } }
            // Switching profiles lives in the navigation avatar; here a row
            // edits that profile's avatar.
            TvSettingsRow(
                onClick = { pickerProfileId = profile.id },
                modifier = Modifier.focusRequester(requester),
                height = 64.dp,
            ) { focused ->
                TvProfileAvatar(
                    avatarKey = profile.avatarKey,
                    focused = focused,
                    selected = state.activeProfileId == profile.id,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.choose_avatar),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.background_artwork),
                description = stringResource(R.string.background_artwork_description),
                checked = state.backgroundArtworkEnabled,
                onCheckedChange = viewModel::setBackgroundArtworkEnabled,
            )
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.black_background),
                description = stringResource(R.string.black_background_description),
                checked = state.tvBlackBackground,
                onCheckedChange = viewModel::setTvBlackBackground,
            )
        }
        item {
            val spotlight = state.tvHomeLayout == TvHomeLayout.SPOTLIGHT
            TvSettingsChoiceRow(
                title = stringResource(R.string.home_layout),
                description = stringResource(
                    if (spotlight) R.string.home_layout_spotlight_description else R.string.home_layout_classic_description,
                ),
                value = stringResource(if (spotlight) R.string.home_layout_spotlight else R.string.home_layout_classic),
                onClick = {
                    viewModel.setTvHomeLayout(if (spotlight) TvHomeLayout.CLASSIC else TvHomeLayout.SPOTLIGHT)
                },
            )
        }
        item {
            val rail = state.tvNavigationStyle == TvNavigationStyle.SIDE_RAIL
            TvSettingsChoiceRow(
                title = stringResource(R.string.navigation_style),
                description = stringResource(
                    if (rail) R.string.navigation_style_side_rail_description else R.string.navigation_style_top_bar_description,
                ),
                value = stringResource(if (rail) R.string.navigation_style_side_rail else R.string.navigation_style_top_bar),
                onClick = {
                    viewModel.setTvNavigationStyle(if (rail) TvNavigationStyle.TOP_BAR else TvNavigationStyle.SIDE_RAIL)
                },
            )
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.trailer_previews),
                description = stringResource(R.string.trailer_previews_description),
                checked = state.trailers == true,
                onCheckedChange = viewModel::setTrailersEnabled,
            )
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.hide_unreleased),
                description = stringResource(R.string.hide_unreleased_description),
                checked = state.hideUnreleased,
                onCheckedChange = viewModel::setHideUnreleased,
            )
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.google_tv_home_setting),
                description = stringResource(R.string.google_tv_home_setting_description),
                checked = state.googleTvHome,
                onCheckedChange = viewModel::setGoogleTvHome,
            )
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.fits_tonight_setting),
                description = stringResource(R.string.fits_tonight_setting_description),
                checked = state.fitsTonight,
                onCheckedChange = viewModel::setFitsTonight,
            )
        }
        if (state.fitsTonight) {
            item {
                val context = LocalContext.current
                TvSettingsChoiceRow(
                    title = stringResource(R.string.bedtime_setting),
                    description = stringResource(R.string.bedtime_setting_description),
                    value = bedtimeLabel(context, state.bedtimeMinutes),
                    onClick = { viewModel.setBedtimeMinutes(PlaybackEngineOptions.next(BEDTIME_OPTIONS, state.bedtimeMinutes)) },
                )
            }
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.series_recap_setting),
                description = stringResource(R.string.series_recap_setting_description),
                checked = state.seriesRecap,
                onCheckedChange = viewModel::setSeriesRecap,
            )
        }
        item {
            TvSettingsToggleRow(
                title = stringResource(R.string.source_fit_setting),
                description = stringResource(R.string.source_fit_setting_description),
                checked = state.sourceFit,
                onCheckedChange = viewModel::setSourceFit,
            )
        }
        item { TvStreamBadgeSettings(state, viewModel) }
    }
}

/**
 * Nuvio-compatible stream badges: one imported `badges.json` whose image
 * badges decorate source cards. Stored on this device only.
 */
@Composable
private fun TvStreamBadgeSettings(state: AppUiState, viewModel: AppViewModel) {
    var address by rememberSaveable { mutableStateOf("") }
    val importFocus = remember { FocusRequester() }
    // Stays enabled while importing (the view model ignores repeats): disabling
    // the focused button would drop focus out of the page (TV-FOC-01).
    val importEnabled = address.trim().startsWith("https://", ignoreCase = true)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.stream_badges),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp).semantics { heading() },
            )
            Text(
                text = state.streamBadges?.let { rules ->
                    pluralStringResource(
                        R.plurals.stream_badges_status,
                        rules.enabledFilterCount,
                        rules.enabledFilterCount,
                        runCatching { java.net.URI(rules.sourceUrl).host }.getOrNull().orEmpty(),
                    )
                } ?: stringResource(R.string.stream_badges_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TvEditableTextField(
            value = address,
            onValueChange = { address = it },
            label = stringResource(R.string.stream_badges_address),
            placeholder = stringResource(R.string.stream_badges_placeholder),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            onImeAction = { if (importEnabled) viewModel.importStreamBadges(address) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvAction(
                label = stringResource(if (state.streamBadgesImporting) R.string.stream_badges_importing else R.string.stream_badges_import),
                icon = Icons.Outlined.Add,
                enabled = importEnabled,
                modifier = Modifier.focusRequester(importFocus),
                onClick = { viewModel.importStreamBadges(address) },
            )
            if (state.streamBadges != null) {
                TvAction(
                    label = stringResource(R.string.stream_badges_remove),
                    icon = Icons.Outlined.Delete,
                    onClick = viewModel::removeStreamBadges,
                )
            }
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
        // A setting that depends on a switched-off parent reads as disabled
        // (dimmed text and switch), not just unfocusable.
        val dim = if (enabled) 1f else 0.38f
        val primaryColor = if (focused) {
            TvFocusTokens.focusedContent
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = dim)
        }
        val secondaryColor = if (focused) {
            TvFocusTokens.focusedContent.copy(alpha = 0.76f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim)
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
                enabled = enabled,
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
                                    R.string.included_addon
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
private fun TvAboutSettings(updateViewModel: com.lamphaus.app.update.UpdateViewModel? = null) {
    val collected: androidx.compose.runtime.State<com.lamphaus.app.update.UpdateUiState>? =
        if (updateViewModel != null) {
            updateViewModel.state.collectAsStateWithLifecycle()
        } else {
            null
        }
    val updateState = collected?.value ?: com.lamphaus.app.update.UpdateUiState()
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
        // Manual update checks live in the existing About pane (plan §4).
        if (updateViewModel != null && com.lamphaus.app.update.UpdateCoordinator.enabled()) {
            val status = when (updateState.phase) {
                com.lamphaus.app.update.UpdatePhase.UpToDate ->
                    stringResource(R.string.update_up_to_date)
                com.lamphaus.app.update.UpdatePhase.CheckFailed ->
                    stringResource(R.string.update_check_failed)
                com.lamphaus.app.update.UpdatePhase.NoCompatible ->
                    stringResource(R.string.update_no_compatible)
                com.lamphaus.app.update.UpdatePhase.WaitingForStable ->
                    stringResource(R.string.update_waiting_stable)
                else -> null
            }
            if (status != null) {
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            TvSettingsRow(onClick = { updateViewModel.checkManual() }) {
                Text(stringResource(R.string.update_check), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun TvSettingsRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
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

private const val WATCH_NEXT_SETTLE_MILLIS = 2_000L

/** How long a page entered from the side rail may take to offer its entry control. */
private const val CONTENT_ENTRY_TIMEOUT_MILLIS = 3_000L
