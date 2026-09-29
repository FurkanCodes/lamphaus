package com.lamphaus.app.tv

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.core.text.HtmlCompat
import com.lamphaus.app.R
import com.lamphaus.app.ui.LamphausWordmark
import androidx.compose.runtime.rememberCoroutineScope
import com.lamphaus.app.ui.upNextEpisodeLabel
import com.lamphaus.app.ui.upNextBadgeText
import com.lamphaus.app.ui.UpNextItem
import com.lamphaus.app.ui.ProfileAvatarArt
import com.lamphaus.app.ui.SelectionCheckmark
import com.lamphaus.app.ui.ContentMenuOrigin
import com.lamphaus.app.ui.ContentMenuTarget
import com.lamphaus.app.ui.MediaArtwork
import com.lamphaus.app.ui.metadataPresentation
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.app.ui.metadataImdbScore
import com.lamphaus.app.ui.RatingBadgeChip
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.Profile
import com.lamphaus.core.model.WatchProgress

internal enum class TvDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val showLabel: Boolean = true,
    /** Movies and Series show the Home layout filtered to this catalog type (TV-CNT-01). */
    val catalogType: String? = null,
) {
    HOME(R.string.home, Icons.Filled.Home),
    MOVIES(R.string.movies, Icons.Filled.Movie, catalogType = "movie"),
    SERIES(R.string.series, Icons.Filled.Tv, catalogType = "series"),
    DISCOVER(R.string.discover, Icons.Filled.Explore),
    LIBRARY(R.string.library, Icons.Filled.VideoLibrary),
    SEARCH(R.string.search, Icons.Filled.Search, showLabel = false),
    SETTINGS(R.string.settings, Icons.Filled.Settings, showLabel = false),
    ;

    companion object {
        /** TV-NAV-01: the centred tabs; the avatar sits at the start and Settings at the end. */
        val centerTabs = listOf(SEARCH, HOME, MOVIES, SERIES, DISCOVER, LIBRARY)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.tvContentFocusBoundary(
    topNavigationRequester: FocusRequester,
    leftNavigationRequester: FocusRequester = FocusRequester.Default,
): Modifier =
    focusProperties {
        exit = { direction ->
            when (direction) {
                FocusDirection.Up -> topNavigationRequester
                FocusDirection.Left -> leftNavigationRequester
                else -> FocusRequester.Default
            }
        }
    }
        // One group for the whole page: without it the exit rule applied to
        // every row and chip strip, so Up from any of them jumped straight to
        // the navigation and skipped the controls above (TV-NAV-05).
        .focusGroup()

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvEditableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onImeAction: () -> Unit = {},
    onNavigateDown: () -> Boolean = { false },
) {
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var imeWasVisible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val browseDescription = stringResource(R.string.press_select_to_edit)
    val editingDescription = stringResource(R.string.editing)
    val finishEditing: () -> Unit = {
        editing = false
        keyboardController?.hide()
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            keyboardController?.show()
        }
    }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible && editing) {
            finishEditing()
        }
    }
    BackHandler(enabled = editing, onBack = finishEditing)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = !editing,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onAny = {
            finishEditing()
            onImeAction()
        }),
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused && editing) finishEditing()
            }
            .onPreviewKeyEvent { event ->
                if (!editing) {
                    // Browsing: the D-pad moves between controls. A text field
                    // otherwise swallows arrows for its cursor, which trapped
                    // Up below the navigation (TV-NAV-03, TV-NAV-05).
                    val direction = when (event.key) {
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionLeft -> FocusDirection.Left
                        Key.DirectionRight -> FocusDirection.Right
                        else -> null
                    }
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown ->
                            onNavigateDown() || focusManager.moveFocus(FocusDirection.Down)

                        direction != null && event.type == KeyEventType.KeyDown -> {
                            focusManager.moveFocus(direction)
                            true
                        }

                        event.key == Key.DirectionCenter || event.key == Key.Enter -> {
                            if (event.type == KeyEventType.KeyUp) {
                                editing = true
                            }
                            true
                        }

                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    finishEditing()
                    true
                } else if (
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
                ) {
                    // Arrows reach the field only once the keyboard is gone. On TV
                    // the IME's own Back can hide it without the field ever seeing
                    // Back, which left it editing and swallowing Down, so results
                    // below were unreachable (TV-NAV-03, TV-NAV-05).
                    finishEditing()
                    if (event.key == Key.DirectionDown) {
                        onNavigateDown() || focusManager.moveFocus(FocusDirection.Down)
                    } else {
                        focusManager.moveFocus(FocusDirection.Up)
                    }
                    true
                } else if (event.key == Key.DirectionCenter) {
                    // Select on a field whose keyboard was dismissed brings it back.
                    if (event.type == KeyEventType.KeyUp) keyboardController?.show()
                    true
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
            .semantics {
                contentDescription = label
                stateDescription = if (editing) editingDescription else browseDescription
            }
            .padding(contentPadding),
        decorationBox = { input ->
            if (value.isBlank()) {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            input()
        },
    )
}

@Composable
internal fun TvTopNavigation(
    selectedDestination: TvDestination,
    activeProfile: Profile?,
    focusDestination: TvDestination?,
    requesters: Map<TvDestination, FocusRequester>,
    profileRequester: FocusRequester,
    contentDownRequester: FocusRequester,
    onFocusHandled: () -> Unit,
    onHasFocus: (Boolean) -> Unit,
    onDestination: (TvDestination) -> Unit,
    onProfileSwitcher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(focusDestination) {
        focusDestination?.let {
            requesters.getValue(it).requestFocus()
            onFocusHandled()
        }
    }
    // TV-NAV-01: avatar at the start, the tabs centred, Settings at the end.
    // All three share one row, so Left/Right traverse them spatially.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TvLayoutTokens.topBarHeight)
            .onFocusChanged { onHasFocus(it.hasFocus) }
            // Down enters the page at its entry control; when that control is
            // not on screen (an empty grid, a scrolled list), fall back to the
            // nearest control below instead of doing nothing (TV-NAV-05).
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    runCatching { contentDownRequester.requestFocus() }.getOrDefault(false)
            },
    ) {
        TvProfileNavigationItem(
            profile = activeProfile,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .focusRequester(profileRequester),
            onClick = onProfileSwitcher,
        )
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvDestination.centerTabs.forEach { destination ->
                TvTopNavigationItem(
                    destination = destination,
                    selected = selectedDestination == destination,
                    modifier = Modifier.focusRequester(requesters.getValue(destination)),
                    onFocused = { onDestination(destination) },
                    onClick = { onDestination(destination) },
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvTopNavigationItem(
                destination = TvDestination.SETTINGS,
                selected = selectedDestination == TvDestination.SETTINGS,
                modifier = Modifier.focusRequester(requesters.getValue(TvDestination.SETTINGS)),
                onFocused = { onDestination(TvDestination.SETTINGS) },
                onClick = { onDestination(TvDestination.SETTINGS) },
            )
            Row(
                modifier = Modifier.alpha(0.72f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_lamphaus_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                LamphausWordmark(
                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

/** Select opens the profile switcher; focusing it never changes the page. */
@Composable
private fun TvProfileNavigationItem(
    profile: Profile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val description = stringResource(R.string.switch_profile_current, profile?.name.orEmpty())
    Box(
        modifier = modifier
            .size(32.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        TvProfileAvatar(
            avatarKey = profile?.avatarKey.orEmpty(),
            focused = focused,
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
    }
}

@Composable
private fun TvTopNavigationItem(
    destination: TvDestination,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(destination.labelRes)
    Box(
        modifier = Modifier.height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = modifier
                .height(32.dp)
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
                    shape = TvShapeTokens.button,
                )
                .clip(TvShapeTokens.button)
                .clickable(role = Role.Tab, onClick = onClick)
                .focusable()
                .semantics {
                    this.selected = selected
                    contentDescription = label
                }
                .padding(horizontal = if (destination.showLabel) 16.dp else 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (destination.showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        focused -> TvFocusTokens.focusedContent
                        selected -> MaterialTheme.colorScheme.onBackground
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            } else {
                TvIcon(
                    icon = destination.icon,
                    contentDescription = null,
                    tint = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(24.dp)
                    .height(2.dp)
                    .background(TvFocusTokens.beam, CircleShape),
            )
        }
    }
}
/**
 * Hold-to-open-menu key handling for TV content cards (TV-FND-02): a short
 * Select stays a click; holding Select/Menu opens the context menu exactly
 * once and consumes the release click.
 */
internal fun Modifier.tvSelectHoldMenu(tracker: SelectHoldTracker?): Modifier =
    onFocusChanged { if (!it.isFocused) tracker?.cancel() }
        .onPreviewKeyEvent { event ->
        if (tracker == null) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionCenter, Key.Enter -> when (event.type) {
                KeyEventType.KeyDown -> tracker.onKeyDown()
                KeyEventType.KeyUp -> tracker.onKeyUp()
                else -> false
            }
            Key.Menu -> when (event.type) {
                KeyEventType.KeyDown -> tracker.onMenuKeyDown()
                KeyEventType.KeyUp -> tracker.onKeyUp()
                else -> false
            }
            else -> false
        }
    }

internal data class TvContentMenuEnvironment(
    val completedVideoIds: Set<String> = emptySet(),
    val onRequest: ((ContentMenuTarget, FocusRequester) -> Unit)? = null,
)

internal val LocalTvContentMenuEnvironment = staticCompositionLocalOf { TvContentMenuEnvironment() }

@Composable
internal fun TvMediaCard(
    media: MediaPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    showLabel: Boolean = true,
    revealLabelOnFocus: Boolean = false,
    compactLandscape: Boolean = false,
    watchProgress: Float? = null,
    onMenuRequest: ((FocusRequester) -> Unit)? = null,
    completed: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cardFocus = remember { FocusRequester() }
    val menuEnvironment = LocalTvContentMenuEnvironment.current
    val defaultMenuRequest = menuEnvironment.onRequest?.let { request ->
        { focus: FocusRequester ->
            request(ContentMenuTarget(media = media, origin = ContentMenuOrigin.POSTER), focus)
        }
    }
    val effectiveMenuRequest = onMenuRequest ?: defaultMenuRequest
    val currentMenuRequest by rememberUpdatedState(effectiveMenuRequest)
    val holdTracker = remember(scope, cardFocus) {
        SelectHoldTracker(scope) { currentMenuRequest?.invoke(cardFocus) }
    }.takeIf { effectiveMenuRequest != null }
    val effectivelyCompleted = completed ||
        (media.type == com.lamphaus.core.model.MediaType.MOVIE && media.id in menuEnvironment.completedVideoIds)
    val reducedMotion = rememberReducedMotion()
    // QA-08: unfocused cards never read the accent, so the accent change
    // after each focus move recomposes only the focused card; the halo reads
    // it in the layer block, which skips recomposition entirely.
    val ambient = LocalTvContentAccent.current
    val primary = MaterialTheme.colorScheme.primary
    // TV-ART-01: posters carry no rating overlay; ratings live in the
    // focused hero/Spotlight metadata and on the details page.
    val cardDescription = media.name
    val labelAlpha by animateFloatAsState(
        targetValue = if (!revealLabelOnFocus || focused) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(TvMotionTokens.focusDurationMillis),
        label = "card label",
    )
    val focusProgress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(TvMotionTokens.focusDurationMillis),
        label = "card focus",
    )
    val hasLocalPoster = media.id == "fixture:aurora" || media.id == "fixture:glass"
    val declaredLandscape = media.posterShape.equals("landscape", ignoreCase = true)
    val portrait = !declaredLandscape && (hasLocalPoster || !media.posterUrl.isNullOrBlank() || media.backgroundUrl.isNullOrBlank())
    val cardWidth = if (portrait || compactLandscape) {
        TvLayoutTokens.posterWidth
    } else {
        TvLayoutTokens.landscapeCardWidth
    }
    val cardHeight = when {
        portrait -> TvLayoutTokens.posterHeight
        compactLandscape -> 86.dp
        else -> TvLayoutTokens.landscapeCardHeight
    }
    Column(
        modifier = modifier
            .width(cardWidth)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvSelectHoldMenu(holdTracker)
            .focusRequester(cardFocus)
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics { contentDescription = cardDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .graphicsLayer {
                    val scale = 1f + ((TvMotionTokens.focusedArtworkScale - 1f) * focusProgress)
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 7.dp.toPx() * focusProgress
                    shape = TvShapeTokens.card
                    if (focusProgress > 0f) {
                        val halo = (ambient.accent ?: primary).copy(alpha = TvFocusTokens.halo.alpha)
                        ambientShadowColor = halo
                        spotShadowColor = halo
                    }
                }
                .border(
                    width = TvFocusTokens.outlineWidth,
                    color = if (focused) ambient.accent ?: primary else TvSurfaceTokens.subtleBorder,
                    shape = TvShapeTokens.card,
                )
                .padding(if (focused) TvFocusTokens.outlineWidth else 0.5.dp)
                .clip(TvShapeTokens.card),
        ) {
            MediaArtwork(
                media = media,
                modifier = Modifier.fillMaxWidth().height(cardHeight),
                contentScale = ContentScale.Crop,
            )
            watchProgress?.coerceIn(0f, 1f)?.takeIf { it > 0f }?.let { progress ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.44f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(TvFocusTokens.beam),
                    )
                }
            }
            if (effectivelyCompleted) {
                SelectionCheckmark(
                    selected = true,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }
        if (showLabel) {
            Text(
                text = media.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .padding(top = 4.dp)
                    .graphicsLayer {
                        // ModulateAlpha: no offscreen buffer per label fade (QA-08).
                        alpha = labelAlpha
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        translationY = (1f - labelAlpha) * 6.dp.toPx()
                    },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
@Composable
internal fun TvContinueWatchingCard(
    media: MediaPreview,
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onMenuRequest: ((FocusRequester) -> Unit)? = null,
    /** Present for an up-next card: the series' following episode replaces the progress. */
    upNext: UpNextItem? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cardFocus = remember { FocusRequester() }
    val menuEnvironment = LocalTvContentMenuEnvironment.current
    val defaultMenuRequest = menuEnvironment.onRequest?.let { request ->
        { focus: FocusRequester ->
            request(
                if (upNext != null) {
                    ContentMenuTarget(media = media, episode = upNext.episode, origin = ContentMenuOrigin.CONTINUE_WATCHING)
                } else {
                    ContentMenuTarget(
                        media = media,
                        progress = progress,
                        origin = ContentMenuOrigin.CONTINUE_WATCHING,
                    )
                },
                focus,
            )
        }
    }
    val effectiveMenuRequest = onMenuRequest ?: defaultMenuRequest
    val currentMenuRequest by rememberUpdatedState(effectiveMenuRequest)
    val holdTracker = remember(scope, cardFocus) {
        SelectHoldTracker(scope) { currentMenuRequest?.invoke(cardFocus) }
    }.takeIf { effectiveMenuRequest != null }
    val reducedMotion = rememberReducedMotion()
    // QA-08: unfocused cards never read the accent, so the accent change
    // after each focus move recomposes only the focused card; the halo reads
    // it in the layer block, which skips recomposition entirely.
    val ambient = LocalTvContentAccent.current
    val primary = MaterialTheme.colorScheme.primary
    val upNextLabel = upNext?.let { upNextEpisodeLabel(it.episode) }
    val upNextBadge = upNext?.let { upNextBadgeText(it) }
    val title = upNextLabel ?: progress.episodeLabel ?: media.name
    val percent = (progress.fraction * 100).toInt()
    val cardDescription = if (upNext != null) {
        stringResource(R.string.up_next_card_description, media.name, upNextLabel.orEmpty(), upNextBadge.orEmpty())
    } else {
        stringResource(R.string.media_card_description_progress, title, percent)
    }
    val remainingMillis = (progress.durationMillis - progress.positionMillis).coerceAtLeast(0)
    val hours = remainingMillis / 3_600_000
    val minutes = (remainingMillis % 3_600_000) / 60_000
    val timeLeft = when {
        upNext != null -> null
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min"
        else -> null
    }
    val focusProgress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(TvMotionTokens.focusDurationMillis),
        label = "continue watching focus",
    )
    Box(
        modifier = modifier
            .width(TvLayoutTokens.landscapeCardWidth)
            .height(TvLayoutTokens.landscapeCardHeight)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvSelectHoldMenu(holdTracker)
            .focusRequester(cardFocus)
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics { contentDescription = cardDescription },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f + ((TvMotionTokens.focusedArtworkScale - 1f) * focusProgress)
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 7.dp.toPx() * focusProgress
                    shape = TvShapeTokens.card
                    if (focusProgress > 0f) {
                        val halo = (ambient.accent ?: primary).copy(alpha = TvFocusTokens.halo.alpha)
                        ambientShadowColor = halo
                        spotShadowColor = halo
                    }
                }
                .border(
                    width = TvFocusTokens.outlineWidth,
                    color = if (focused) ambient.accent ?: primary else TvSurfaceTokens.subtleBorder,
                    shape = TvShapeTokens.card,
                )
                .padding(if (focused) TvFocusTokens.outlineWidth else 0.5.dp)
                .clip(TvShapeTokens.card),
        ) {
            MediaArtwork(
                media = media,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                preferBackdrop = true,
            )
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
            (upNextBadge ?: timeLeft?.let { stringResource(R.string.continue_watching_time_left, it) })?.let { badge ->
                Text(
                    text = badge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(TvShapeTokens.card)
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            if (progress.completed && upNext == null) {
                SelectionCheckmark(
                    selected = true,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 10.dp, bottom = 18.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (upNext == null) {
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
                            .background(TvFocusTokens.beam),
                    )
                }
            }
        }
    }
}

/**
 * A profile's drawn avatar (or account photo) in the brand's profile shape.
 * Focus adds the beam outline and keeps the artwork visible (TV-FOC-01).
 */
@Composable
internal fun TvProfileAvatar(
    avatarKey: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(TvShapeTokens.profile)
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = when {
                    focused -> TvFocusTokens.beam
                    selected -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                    else -> TvSurfaceTokens.subtleBorder
                },
                shape = TvShapeTokens.profile,
            ),
    ) {
        ProfileAvatarArt(avatarKey = avatarKey, modifier = Modifier.fillMaxSize().clip(TvShapeTokens.profile))
    }
}

@Composable
internal fun TvEmptyMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), TvShapeTokens.profile)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), TvShapeTokens.profile),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_lamphaus_monochrome),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
internal fun TvAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TvFocusableSurface(onClick = onClick, enabled = enabled, modifier = modifier) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIcon(
                icon = icon,
                contentDescription = null,
                tint = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = if (focused) TvFocusTokens.focusedContent else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
internal fun TvFocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = TvFocusTokens.defaultContainer,
    focusedContainerColor: Color = TvFocusTokens.focusedContainer,
    role: Role = Role.Button,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val restingOutline = LocalTvSurfaces.current.actionOutline
    val reducedMotion = rememberReducedMotion()
    val focusProgress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(TvMotionTokens.focusDurationMillis),
        label = "tv focusable surface",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f + ((TvMotionTokens.focusedArtworkScale - 1f) * focusProgress)
                scaleX = scale
                scaleY = scale
                // TV-FOC-02: restrained 7dp/28% halo, driven by focusProgress.
                shadowElevation = 7.dp.toPx() * focusProgress
                shape = TvShapeTokens.button
                ambientShadowColor = TvFocusTokens.halo
                spotShadowColor = TvFocusTokens.halo
            }
            .onFocusChanged { focused = it.isFocused }
            .background(
                color = when {
                    !enabled -> TvFocusTokens.disabledContainer
                    focused -> focusedContainerColor
                    else -> containerColor
                },
                shape = TvShapeTokens.button,
            )
            .border(
                width = when {
                    focused -> TvFocusTokens.outlineWidth
                    restingOutline != Color.Transparent -> 1.dp
                    else -> 0.dp
                },
                color = if (focused) TvFocusTokens.focusedCardOutline else restingOutline,
                shape = TvShapeTokens.button,
            )
            .clip(TvShapeTokens.button)
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .focusable(enabled)
            .semantics { this.role = role },
    ) {
        content(focused)
    }
}

/**
 * Reading surface that expands to its full content while focused and collapses
 * back to [collapsedLines] when focus moves on. It is only focusable when the
 * collapsed text actually truncates, so short descriptions never introduce a
 * dead focus stop between interactive elements.
 *
 * Focus restore rule: reading surfaces are transient stops. When this surface
 * takes focus, [returnFocusProvider] supplies the button that had focus
 * immediately before; pressing down on the surface returns to that button.
 */
@Composable
internal fun TvExpandableText(
    text: String,
    collapsedLines: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    returnFocusProvider: () -> FocusRequester? = { null },
) {
    val reducedMotion = rememberReducedMotion()
    var focused by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    var returnTarget by remember { mutableStateOf<FocusRequester?>(null) }
    val expansionSpec: FiniteAnimationSpec<IntSize> =
        if (reducedMotion) snap() else tween(TvMotionTokens.heroTransitionDurationMillis)

    Text(
        text = text,
        style = style,
        color = color,
        maxLines = if (focused) Int.MAX_VALUE else collapsedLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult ->
            // Only observe the collapsed layout; the expanded layout never
            // overflows and would clear the flag while the surface is focused.
            if (!focused) overflows = layoutResult.hasVisualOverflow
        },
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    val target = returnTarget
                    if (target != null) {
                        target.requestFocus()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) returnTarget = returnFocusProvider()
            }
            .animateContentSize(animationSpec = expansionSpec)
            .focusable(enabled = overflows || focused),
    )
}

/**
 * Cast / directors section that mirrors [TvExpandableText]: collapsed it keeps
 * the compact name line, focused it grows into a wrapped chip list that shows
 * every name instead of an ellipsized summary.
 *
 * Shares the same focus restore rule — see [TvExpandableText].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvExpandablePeopleSection(
    @StringRes labelRes: Int,
    people: List<String>,
    modifier: Modifier = Modifier,
    returnFocusProvider: () -> FocusRequester? = { null },
) {
    if (people.isEmpty()) return
    val names = remember(people) {
        people.map { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }
    }
    val reducedMotion = rememberReducedMotion()
    var focused by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    var returnTarget by remember { mutableStateOf<FocusRequester?>(null) }
    // The collapsed summary shows at most six names in two lines; anything
    // beyond that must stay reachable through focus expansion even when the
    // summary itself happens to fit.
    val collapsedNameCount = 6
    val truncates = overflows || names.size > collapsedNameCount
    val expansionSpec: FiniteAnimationSpec<IntSize> =
        if (reducedMotion) snap() else tween(TvMotionTokens.heroTransitionDurationMillis)

    Column(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    val target = returnTarget
                    if (target != null) {
                        target.requestFocus()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) returnTarget = returnFocusProvider()
            }
            .animateContentSize(animationSpec = expansionSpec)
            .focusable(enabled = truncates || focused)
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        if (focused && truncates) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                names.forEach { name ->
                    Text(
                        text = name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .background(TvSurfaceTokens.elevated, TvShapeTokens.button)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        } else {
            Text(
                text = names.take(6).joinToString("  •  "),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layoutResult ->
                    if (!focused) overflows = layoutResult.hasVisualOverflow
                },
            )
        }
    }
}


@Composable
internal fun TvIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(20.dp),
    )
}
