package com.lamphaus.app.player

import android.content.res.Configuration
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lamphaus.app.R
import com.lamphaus.app.ui.SpoilerContent
import com.lamphaus.app.ui.rememberReducedMotion
import com.lamphaus.app.ui.shouldBlur
import com.lamphaus.core.model.PlaybackRequest
import com.lamphaus.core.model.SubtitleCue
import com.lamphaus.core.model.SubtitleStyle
import com.lamphaus.core.model.SubtitleStylePolicy
import com.lamphaus.core.model.stepAudioDelay
import com.lamphaus.core.model.stepSubtitleDelay
import com.lamphaus.core.model.clampSubtitleDelayMillis
import com.lamphaus.core.model.PlaybackSegment
import com.lamphaus.core.model.PlaybackSegmentType
import com.lamphaus.core.model.PlaybackSettings
import com.lamphaus.core.model.NextEpisodePolicy
import com.lamphaus.core.model.SpoilerProtectionSettings
import com.lamphaus.core.model.hasAired
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal val PlayerBackground = Color(0xFF121316)
internal val PlayerOnSurface = Color(0xFFE3E2E6)
internal val PlayerOnSurfaceMuted = Color(0xFFC4C6CF)
internal val PlayerSurface = Color(0xFF292A2D)
internal val PlayerFocused = Color(0xFFE3E2E6)
internal val PlayerFocusedContent = Color(0xFF2F3033)
internal val PlayerPrimary = Color(0xFFA8C8FF)
internal val PlayerOnPrimary = Color(0xFF003062)
// Tonal control container (TV-TOK-02 default 10% white) keeps ghost buttons legible
// over video without adding glass or saturated chrome (SHR-PROD-01/03).
internal val PlayerControlContainer = Color.White.copy(alpha = 0.10f)
internal val PlayerTrack = Color.White.copy(alpha = 0.28f)
internal val PlayerBuffered = Color.White.copy(alpha = 0.45f)
internal val PlayerFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
)

internal enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, DISPLAY, MORE, INFO }

private enum class PlayerEditor { TIMING, STYLE }

internal data class PlayerSnapshot(
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMillis: Long = 0,
    val bufferedPositionMillis: Long = 0,
    val durationMillis: Long = 0,
    val tracks: Tracks = Tracks.EMPTY,
    val speed: Float = 1f,
    val errorMessage: String? = null,
)

private data class TrackOption(
    val id: String,
    val title: String,
    val supportingText: String?,
    val selected: Boolean,
    val supported: Boolean,
    val languageKey: String,
    val group: TrackGroup,
    val trackIndex: Int,
)

private data class SubtitleLanguageRailItem(
    val key: String,
    val label: String,
    val trackCount: Int,
)

@OptIn(UnstableApi::class)
@Composable
internal fun PlaybackScreen(
    request: PlaybackRequest,
    player: Player?,
    isTelevision: Boolean,
    settings: PlaybackSettings,
    segments: List<PlaybackSegment>,
    nextEpisodeLoading: Boolean,
    nextEpisodeMessage: String?,
    onExit: () -> Unit,
    onOpenExternally: () -> Unit,
    onNextEpisode: () -> Unit,
    onDismissNextEpisodeMessage: () -> Unit,
    spoilerProtection: SpoilerProtectionSettings,
    nextEpisodeDismissed: Boolean,
    onDismissNextEpisodeCard: () -> Unit,
    onPlayerViewLayout: (android.view.View) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    pictureInPictureAvailable: Boolean,
    inPictureInPicture: Boolean,
    subtitleStyle: SubtitleStyle,
    subtitleDelayMillis: Long,
    audioDelayMillis: Long,
    streamInfo: String?,
    onSubtitleDelay: (Long) -> Unit,
    onAudioDelay: (Long) -> Unit,
    onSubtitleStyle: (SubtitleStyle) -> Unit,
    onLoadSidecarCues: ((List<SubtitleCue>) -> Unit) -> Unit,
    onApplySyncByLine: (Long, Long) -> Unit,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
) {
    var snapshot by remember(player) { mutableStateOf(player?.snapshot() ?: PlayerSnapshot()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var resizeMode by rememberSaveable { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var editor by remember { mutableStateOf<PlayerEditor?>(null) }
    var editorReturnTarget by remember { mutableStateOf<PlayerEditor?>(null) }
    var panelParent by remember { mutableStateOf<PlayerPanel?>(null) }
    var returnFocusPanel by remember { mutableStateOf<PlayerPanel?>(null) }
    var controlsFocusVersion by remember { mutableLongStateOf(0L) }
    var interactionVersion by remember { mutableLongStateOf(0L) }
    val rootFocus = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotion()
    val windowWidthDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val wideLayout = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        windowWidthDp >= 600.dp
    val activeSegment = segments.firstOrNull { segment ->
        val enabled = when (segment.type) {
            PlaybackSegmentType.INTRO -> settings.skipIntroEnabled
            PlaybackSegmentType.ENDING -> settings.skipEndingEnabled
        }
        val end = segment.endMillis ?: snapshot.durationMillis.takeIf { it > 0 } ?: Long.MAX_VALUE
        enabled && snapshot.positionMillis in segment.startMillis until end
    }
    val nextEpisode = request.nextEpisode
    val timingReady = NextEpisodePolicy.shouldShowCard(
        positionMillis = snapshot.positionMillis,
        durationMillis = snapshot.durationMillis,
        segments = segments,
        thresholdMode = settings.nextEpisodeThresholdMode,
        thresholdPercent = settings.nextEpisodeThresholdPercent,
        thresholdMinutesBeforeEnd = settings.nextEpisodeThresholdMinutesBeforeEnd,
    )
    val nextEpisodeReady = settings.nextEpisodeEnabled &&
        nextEpisode != null &&
        nextEpisode.hasAired() &&
        timingReady
    // The card is mobile-only; TV keeps its existing cue pill.
    val nextEpisodeCardVisible = nextEpisodeReady && !isTelevision && !nextEpisodeDismissed
    val nextEpisodeSkipInCard = nextEpisodeCardVisible && !wideLayout &&
        activeSegment?.type == PlaybackSegmentType.ENDING

    fun revealControls() {
        controlsVisible = true
        interactionVersion++
    }

    fun closePanel() {
        if (panelParent != null) {
            panel = panelParent
            panelParent = null
            return
        }
        panel = null
        editor = null
        controlsFocusVersion++
        revealControls()
    }

    fun closeEditor() {
        editor = null
        revealControls()
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                snapshot = player.snapshot()
            }

            override fun onPlayerError(error: PlaybackException) {
                snapshot = player.snapshot().copy(errorMessage = error.safeMessage())
                controlsVisible = true
            }
        }
        player.addListener(listener)
        snapshot = player.snapshot()
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (isActive && player != null) {
            snapshot = player.snapshot()
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, panel, snapshot.playing, interactionVersion) {
        if (controlsVisible && panel == null && snapshot.playing && snapshot.errorMessage == null) {
            delay(4_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, panel) {
        if (!controlsVisible && panel == null) rootFocus.requestFocus()
    }

    LaunchedEffect(controlsVisible, inPictureInPicture, panel) {
        onControlsVisibilityChanged(!inPictureInPicture && (controlsVisible || panel != null))
    }

    LaunchedEffect(inPictureInPicture) {
        if (inPictureInPicture) {
            controlsVisible = false
            panel = null
            editor = null
        }
    }

    BackHandler {
        when {
            // Back unwinds editor -> submenu -> controls -> exit (plan §5),
            // restoring the originating focus at each layer.
            editor != null -> closeEditor()
            panel != null -> closePanel()
            nextEpisodeCardVisible -> onDismissNextEpisodeCard()
            snapshot.errorMessage != null -> onExit()
            controlsVisible -> controlsVisible = false
            else -> onExit()
        }
    }

    PlaybackTheme(isTelevision) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || player == null) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> {
                        if (snapshot.playing) player.pause() else player.play()
                        revealControls()
                        true
                    }
                    Key.MediaPlay -> {
                        player.play()
                        revealControls()
                        true
                    }
                    Key.MediaPause -> {
                        player.pause()
                        revealControls()
                        true
                    }
                    Key.MediaRewind -> {
                        player.seekBack()
                        revealControls()
                        true
                    }
                    Key.MediaFastForward -> {
                        player.seekForward()
                        revealControls()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> if (!controlsVisible) {
                        revealControls()
                        true
                    } else false
                    Key.DirectionLeft -> if (!controlsVisible) {
                        player.seekBack()
                        revealControls()
                        true
                    } else false
                    Key.DirectionRight -> if (!controlsVisible) {
                        player.seekForward()
                        revealControls()
                        true
                    } else false
                    Key.DirectionUp, Key.DirectionDown -> if (!controlsVisible) {
                        // Keep every D-pad direction live: hidden controls are
                        // revealed instead of the key press landing nowhere.
                        revealControls()
                        true
                    } else false
                    else -> false
                }
            }
            .pointerInput(player) {
                detectTapGestures(onTap = {
                    controlsVisible = !controlsVisible
                    panel = null
                    interactionVersion++
                })
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    subtitleView?.setUserDefaultTextSize()
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> onPlayerViewLayout(view) }
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
                view.player = player
                view.keepScreenOn = player?.isPlaying == true
                // Keep captions above the control gradient while chrome is visible
                // (common streaming pattern), at rest position during clean viewing.
                val liftedStyle = if (controlsVisible || panel != null) {
                    subtitleStyle.copy(
                        verticalPositionFraction = (subtitleStyle.verticalPositionFraction - 0.10f).coerceIn(0f, 1f),
                    )
                } else {
                    subtitleStyle
                }
                SubtitleStyleApplier.apply(view.subtitleView!!, liftedStyle)
                onPlayerViewLayout(view)
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPictureInPicture && snapshot.buffering && snapshot.errorMessage == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (isTelevision) 52.dp else 42.dp),
                    color = PlayerPrimary,
                    strokeWidth = 3.dp,
                )
                Text(
                    text = stringResource(R.string.player_loading),
                    color = PlayerOnSurface,
                    fontFamily = PlayerFont,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (!inPictureInPicture && (controlsVisible || panel != null || snapshot.errorMessage != null)) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to PlayerBackground.copy(alpha = 0.92f),
                    ),
                ),
            )
        }

        if (!inPictureInPicture && controlsVisible && panel == null && snapshot.errorMessage == null) {
            PlayerControls(
                request = request,
                snapshot = snapshot,
                isTelevision = isTelevision,
                onInteraction = ::revealControls,
                onTogglePlay = { if (snapshot.playing) player?.pause() else player?.play() },
                onReplay = { player?.seekTo(0); player?.play() },
                onSeekBack = { player?.seekBack() },
                onSeekForward = { player?.seekForward() },
                onSeekTo = { player?.seekTo(it) },
                onEnterPictureInPicture = onEnterPictureInPicture,
                pictureInPictureAvailable = pictureInPictureAvailable,
                canPlayNext = settings.nextEpisodeEnabled && nextEpisode != null && nextEpisode.hasAired(),
                nextEpisodeLoading = nextEpisodeLoading,
                onNextEpisode = onNextEpisode,
                focusPanel = returnFocusPanel,
                focusRequestVersion = controlsFocusVersion,
                onPanel = {
                    returnFocusPanel = it
                    panel = it
                    panelParent = null
                    editorReturnTarget = null
                    editor = null
                    revealControls()
                },
                segments = segments,
                onExit = onExit,
            )
        }

        val visibleSegment = if (nextEpisodeSkipInCard) null else activeSegment
        val skipSegment: () -> Unit = {
            when (activeSegment?.type) {
                PlaybackSegmentType.INTRO -> activeSegment.endMillis?.let { player?.seekTo(it) }
                PlaybackSegmentType.ENDING -> {
                    (activeSegment.endMillis ?: snapshot.durationMillis.takeIf { it > 0 })
                        ?.let { player?.seekTo(it) }
                }
                null -> Unit
            }
        }
        if (
            !inPictureInPicture && (
                visibleSegment != null ||
                    (nextEpisodeReady && isTelevision) ||
                    (nextEpisodeMessage != null && !nextEpisodeCardVisible)
                )
        ) {
            PlaybackCueActions(
                modifier = when {
                    nextEpisodeCardVisible && wideLayout -> Modifier.align(Alignment.BottomStart)
                    else -> Modifier.align(Alignment.BottomCenter)
                },
                horizontalAlignment = if (nextEpisodeCardVisible && wideLayout) {
                    Alignment.Start
                } else {
                    Alignment.End
                },
                segment = visibleSegment,
                showNextEpisode = nextEpisodeReady && isTelevision,
                loadingNextEpisode = nextEpisodeLoading,
                message = if (nextEpisodeCardVisible) null else nextEpisodeMessage,
                controlsVisible = controlsVisible,
                isTelevision = isTelevision,
                onSkip = skipSegment,
                onNextEpisode = onNextEpisode,
                onDismissMessage = onDismissNextEpisodeMessage,
            )
        }
        AnimatedVisibility(
            visible = nextEpisodeCardVisible && !inPictureInPicture,
            enter = when {
                reducedMotion -> EnterTransition.None
                wideLayout -> slideInHorizontally(tween(220)) { it } + fadeIn(tween(220))
                else -> slideInVertically(tween(220)) { it } + fadeIn(tween(220))
            },
            exit = when {
                reducedMotion -> ExitTransition.None
                wideLayout -> slideOutHorizontally(tween(160)) { it } + fadeOut(tween(160))
                else -> slideOutVertically(tween(160)) { it } + fadeOut(tween(160))
            },
            modifier = when {
                wideLayout -> Modifier.align(Alignment.BottomEnd).padding(horizontal = 16.dp)
                else -> Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp)
            }.windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ),
        ) {
            nextEpisode?.let { current ->
                NextEpisodeCard(
                    episode = current,
                    loading = nextEpisodeLoading,
                    failureMessage = nextEpisodeMessage,
                    blurArtwork = spoilerProtection.shouldBlur(
                        SpoilerContent.EPISODE_ARTWORK,
                        watched = false,
                    ),
                    wide = wideLayout,
                    showSkipCredits = nextEpisodeSkipInCard,
                    onPlayNext = onNextEpisode,
                    onSkipCredits = skipSegment,
                    onDismiss = onDismissNextEpisodeCard,
                    modifier = Modifier.padding(
                        bottom = if (controlsVisible) 190.dp else 28.dp,
                    ),
                )
            }
        }

        snapshot.errorMessage?.takeUnless { inPictureInPicture }?.let { message ->
            PlayerErrorPanel(
                message = message,
                onRetry = { player?.prepare(); player?.play() },
                onOpenExternally = onOpenExternally,
            )
        }

        panel?.takeUnless { inPictureInPicture }?.let { activePanel ->
            if (activePanel == PlayerPanel.INFO) {
                PlayerStreamInfoPanel(streamInfo = streamInfo, isTelevision = isTelevision, onClose = ::closePanel)
            } else if (editor == PlayerEditor.TIMING) {
                PlayerTimingEditor(
                    isTelevision = isTelevision,
                    currentPositionMillis = snapshot.positionMillis,
                    subtitleDelayMillis = subtitleDelayMillis,
                    onSubtitleDelay = onSubtitleDelay,
                    onLoadSidecarCues = onLoadSidecarCues,
                    onApplySyncByLine = { capturedPosition, cueStart ->
                        onApplySyncByLine(capturedPosition, cueStart)
                        closeEditor()
                    },
                    onClose = ::closeEditor,
                )
            } else if (editor == PlayerEditor.STYLE) {
                PlayerStyleEditor(
                    style = subtitleStyle,
                    isTelevision = isTelevision,
                    onStyleChange = onSubtitleStyle,
                    onClose = ::closeEditor,
                )
            } else {
                PlayerSettingsPanel(
                    panel = activePanel,
                    player = player,
                    snapshot = snapshot,
                    isTelevision = isTelevision,
                    audioDelayMillis = audioDelayMillis,
                    onAudioDelay = onAudioDelay,
                    onOpenEditor = { opened ->
                        editorReturnTarget = opened
                        editor = opened
                        revealControls()
                    },
                    onClose = ::closePanel,
                    subtitleDelayMillis = subtitleDelayMillis,
                    onSubtitleDelay = onSubtitleDelay,
                    subtitleStyle = subtitleStyle,
                    resizeMode = resizeMode,
                    onResizeMode = { resizeMode = it },
                    onPanel = { panelParent = panel; panel = it },
                    editorReturnTarget = editorReturnTarget,
                    onOpenExternally = onOpenExternally,
                )
            }
        }
    }
    }
}

@Composable
private fun PlaybackCueActions(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    segment: PlaybackSegment?,
    showNextEpisode: Boolean,
    loadingNextEpisode: Boolean,
    message: String?,
    controlsVisible: Boolean,
    isTelevision: Boolean,
    onSkip: () -> Unit,
    onNextEpisode: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTelevision) 56.dp else 20.dp)
            .padding(bottom = if (controlsVisible) 190.dp else 28.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        message?.let {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PlayerBackground.copy(alpha = 0.96f))
                    .clickable(onClick = onDismissMessage)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(it, color = PlayerOnSurface, fontFamily = PlayerFont, fontSize = 14.sp)
                Text("Dismiss", color = PlayerPrimary, fontFamily = PlayerFont, fontWeight = FontWeight.Medium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            segment?.let {
                PlayerTextButton(
                    label = if (it.type == PlaybackSegmentType.INTRO) "Skip intro" else "Skip ending",
                    onClick = onSkip,
                )
            }
            if (showNextEpisode) {
                if (loadingNextEpisode) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PlayerSurface)
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = PlayerPrimary, strokeWidth = 2.dp)
                    }
                } else {
                    PlayerTextButton(
                        label = "Next episode",
                        onClick = onNextEpisode,
                    )
                }
            }
        }
    }
}


@Composable
internal fun PlayerTitle(request: PlaybackRequest, isTelevision: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = request.title,
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            style = if (isTelevision) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        request.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
internal fun PlayerActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    visualSize: androidx.compose.ui.unit.Dp = 26.dp,
    active: Boolean = false,
    primary: Boolean = false,
    containerSize: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit,
) {
    if (!LocalPlayerTelevision.current) {
        FilledIconButton(onClick = onClick, modifier = modifier.size(containerSize),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (primary) PlayerFocused else Color.Black.copy(alpha = .48f),
                contentColor = if (primary) PlayerFocusedContent else if (active) PlayerPrimary else PlayerOnSurface,
            )) { Icon(icon, label, Modifier.size(visualSize)) }
        return
    }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(containerSize)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    focused -> PlayerFocused
                    active -> PlayerPrimary.copy(alpha = 0.18f)
                    primary -> PlayerControlContainer.copy(alpha = 0.16f)
                    else -> PlayerControlContainer
                },
            )
            .border(
                if (focused) 3.dp else 0.dp,
                if (focused) PlayerPrimary else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                focused -> PlayerFocusedContent
                active -> PlayerPrimary
                else -> PlayerOnSurface
            },
            modifier = Modifier.size(visualSize),
        )
        if (active && !focused && !primary) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(PlayerPrimary),
            )
        }
    }
}
@Composable
internal fun PlayerSettingButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (!LocalPlayerTelevision.current) {
        TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = if (active) PlayerPrimary else PlayerOnSurface)) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    focused -> PlayerFocused
                    active -> PlayerPrimary.copy(alpha = 0.18f)
                    else -> PlayerControlContainer
                },
            )
            .border(
                if (focused) 3.dp else 0.dp,
                if (focused) PlayerPrimary else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                selected = active
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                focused -> PlayerFocusedContent
                active -> PlayerPrimary
                else -> PlayerOnSurface
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = if (focused) PlayerFocusedContent else PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (active && !focused) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(PlayerPrimary),
            )
        }
    }
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerProgress(
    positionMillis: Long,
    bufferedPositionMillis: Long,
    durationMillis: Long,
    modifier: Modifier,
    onSeekTo: (Long) -> Unit,
    onInteraction: () -> Unit,
    segments: List<PlaybackSegment> = emptyList(),
) {
    val seekDescription = stringResource(R.string.player_seek)
    if (!LocalPlayerTelevision.current) {
        var scrubPosition by remember { mutableStateOf<Float?>(null) }
        Slider(
            value = scrubPosition ?: positionMillis.toFloat().coerceIn(0f, durationMillis.coerceAtLeast(1L).toFloat()),
            onValueChange = { scrubPosition = it; onInteraction() },
            onValueChangeFinished = { scrubPosition?.let { onSeekTo(it.toLong()) }; scrubPosition = null },
            valueRange = 0f..durationMillis.coerceAtLeast(1L).toFloat(),
            enabled = durationMillis > 0,
            modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = seekDescription },
            thumb = { Box(Modifier.size(12.dp).background(PlayerPrimary, CircleShape)) },
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val radius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    val played = state.value / durationMillis.coerceAtLeast(1L)
                    val buffered = bufferedPositionMillis.toFloat() / durationMillis.coerceAtLeast(1L)
                    drawRoundRect(PlayerTrack, cornerRadius = radius)
                    drawRoundRect(PlayerBuffered, size = size.copy(width = size.width * buffered.coerceIn(0f, 1f)), cornerRadius = radius)
                    drawRoundRect(PlayerPrimary, size = size.copy(width = size.width * played.coerceIn(0f, 1f)), cornerRadius = radius)
                }
            },
        )
        return
    }
    var focused by remember { mutableStateOf(false) }
    val played = if (durationMillis > 0) positionMillis.toFloat() / durationMillis else 0f
    val buffered = if (durationMillis > 0) bufferedPositionMillis.toFloat() / durationMillis else 0f
    Canvas(
        modifier
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = seekDescription
                progressBarRangeInfo = ProgressBarRangeInfo(played.coerceIn(0f, 1f), 0f..1f)
                setProgress { fraction ->
                    if (durationMillis <= 0) false else {
                        onSeekTo((durationMillis * fraction.coerceIn(0f, 1f)).toLong())
                        onInteraction()
                        true
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || durationMillis <= 0L) return@onPreviewKeyEvent false
                val target = when (event.key) {
                    Key.DirectionLeft -> positionMillis - 10_000L
                    Key.DirectionRight -> positionMillis + 10_000L
                    else -> return@onPreviewKeyEvent false
                }
                onSeekTo(target.coerceIn(0L, durationMillis))
                onInteraction()
                true
            }
            .focusable()
            .pointerInput(durationMillis) {
                detectTapGestures { offset ->
                    if (durationMillis > 0 && size.width > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekTo((durationMillis * fraction).toLong())
                        onInteraction()
                    }
                }
            },
    ) {
        val centerY = size.height / 2
        val barHeight = if (focused) 6.dp.toPx() else 4.dp.toPx()
        val radius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        val origin = androidx.compose.ui.geometry.Offset(0f, centerY - barHeight / 2)
        drawRoundRect(PlayerTrack, origin, androidx.compose.ui.geometry.Size(size.width, barHeight), radius)
        drawRoundRect(PlayerBuffered, origin, androidx.compose.ui.geometry.Size(size.width * buffered.coerceIn(0f, 1f), barHeight), radius)
        drawRoundRect(PlayerPrimary, origin, androidx.compose.ui.geometry.Size(size.width * played.coerceIn(0f, 1f), barHeight), radius)
        if (durationMillis > 0) {
            segments.forEach { segment ->
                val end = segment.endMillis ?: durationMillis
                if (end <= 0L) return@forEach
                listOf(segment.startMillis, end).forEach { edge ->
                    val x = size.width * (edge.toFloat() / durationMillis).coerceIn(0f, 1f)
                    drawRect(
                        color = PlayerBackground,
                        topLeft = androidx.compose.ui.geometry.Offset(x - 1.dp.toPx() / 2, centerY - barHeight / 2),
                        size = androidx.compose.ui.geometry.Size(1.dp.toPx().coerceAtLeast(2f), barHeight),
                    )
                }
            }
        }
        val knobX = size.width * played.coerceIn(0f, 1f)
        if (focused) {
            drawCircle(
                color = Color.White.copy(alpha = 0.24f),
                radius = 12.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(knobX, centerY),
            )
        }
        drawCircle(
            color = if (focused) PlayerFocused else PlayerPrimary,
            radius = if (focused) 8.dp.toPx() else 6.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(knobX, centerY),
        )
        if (focused) {
            drawCircle(
                color = PlayerPrimary,
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(knobX, centerY),
            )
        }
    }
}

@Composable
internal fun PlayerTime(milliseconds: Long) {
    Text(
        text = milliseconds.asPlaybackTime(),
        color = PlayerOnSurface,
        fontFamily = PlayerFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}

@Composable
internal fun PlayerRemaining(positionMillis: Long, durationMillis: Long) {
    val remaining = (durationMillis - positionMillis).coerceAtLeast(0L)
    Text(
        text = "−${remaining.asPlaybackTime()}",
        color = PlayerOnSurfaceMuted,
        fontFamily = PlayerFont,
        fontSize = 14.sp,
    )
}

@Composable
private fun PlayerSettingsPanel(
    panel: PlayerPanel,
    player: Player?,
    snapshot: PlayerSnapshot,
    isTelevision: Boolean,
    audioDelayMillis: Long,
    onAudioDelay: (Long) -> Unit,
    onOpenEditor: (PlayerEditor) -> Unit,
    onClose: () -> Unit,
    subtitleDelayMillis: Long,
    onSubtitleDelay: (Long) -> Unit,
    subtitleStyle: SubtitleStyle,
    resizeMode: Int,
    onResizeMode: (Int) -> Unit,
    onPanel: (PlayerPanel) -> Unit,
    onOpenExternally: () -> Unit,
    editorReturnTarget: PlayerEditor?,
) {
    val firstFocus = remember(panel) { FocusRequester() }
    val title = stringResource(when (panel) {
        PlayerPanel.AUDIO -> R.string.player_audio
        PlayerPanel.SUBTITLES -> R.string.player_subtitles
        PlayerPanel.SPEED -> R.string.player_speed
        PlayerPanel.DISPLAY -> R.string.player_display
        PlayerPanel.MORE -> R.string.player_more
        PlayerPanel.INFO -> R.string.player_info
    })
    val options = when (panel) {
        PlayerPanel.AUDIO -> snapshot.tracks.options(C.TRACK_TYPE_AUDIO)
        PlayerPanel.SUBTITLES -> snapshot.tracks.options(C.TRACK_TYPE_TEXT)
        else -> emptyList()
    }
    val audioUsesAutoSelection = player?.hasOverride(C.TRACK_TYPE_AUDIO) == false
    LaunchedEffect(panel) { if (isTelevision) firstFocus.requestFocus() }
    PlayerOverlayLayout(title, isTelevision, onClose,
        tvWidth = if (panel == PlayerPanel.SUBTITLES) 844.dp else 724.dp) {
        when (panel) {
            PlayerPanel.AUDIO -> {
                if (isTelevision) {
                    Row(Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        PlayerTrackList(options, C.TRACK_TYPE_AUDIO, player, true,
                            audioUsesAutoSelection, firstFocus, modifier = Modifier.weight(1f))
                        PlayerAudioTimingControls(audioDelayMillis, onAudioDelay,
                            Modifier.width(268.dp).verticalScroll(rememberScrollState()))
                    }
                } else {
                    PlayerTrackList(options, C.TRACK_TYPE_AUDIO, player, true,
                        audioUsesAutoSelection, firstFocus, compactRows = true,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        footer = { PlayerAudioTimingControls(audioDelayMillis, onAudioDelay) })
                }
            }
            PlayerPanel.SUBTITLES -> {
                if (isTelevision) {
                    TvSubtitleRailPanel(options, player, firstFocus, subtitleStyle, subtitleDelayMillis,
                        onSubtitleDelay, { onOpenEditor(PlayerEditor.TIMING) },
                        { onOpenEditor(PlayerEditor.STYLE) }, Modifier.fillMaxWidth().height(360.dp), editorReturnTarget)
                } else {
                    PlayerTrackList(options, C.TRACK_TYPE_TEXT, player, false, false, firstFocus,
                        compactRows = true, modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        footer = {
                            MobileSubtitleToolbar(subtitleStyle, subtitleDelayMillis,
                                { onOpenEditor(PlayerEditor.TIMING) }, { onOpenEditor(PlayerEditor.STYLE) })
                        })
                }
            }
            PlayerPanel.SPEED -> {
                val selectedIndex = PLAYBACK_SPEEDS.indexOf(snapshot.speed).coerceAtLeast(0)
                LazyColumn(Modifier.heightIn(max = 360.dp), state = rememberLazyListState(selectedIndex)) {
                    itemsIndexed(PLAYBACK_SPEEDS, key = { _, speed -> speed }) { index, speed ->
                        PlayerChoiceRow(
                            title = if (speed == 1f) stringResource(R.string.player_speed_normal) else "${speed}×",
                            supportingText = null, selected = snapshot.speed == speed,
                            modifier = if (index == selectedIndex) Modifier.focusRequester(firstFocus) else Modifier,
                        ) { player?.setPlaybackSpeed(speed) }
                    }
                }
            }
            PlayerPanel.DISPLAY -> {
                val modes = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)
                val labels = listOf(R.string.player_fit, R.string.player_zoom, R.string.player_fill)
                val details = listOf(R.string.player_fit_detail, R.string.player_zoom_detail, R.string.player_fill_detail)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(modes) { index, mode ->
                        PlayerChoiceRow(stringResource(labels[index]), stringResource(details[index]), resizeMode == mode,
                            modifier = if (resizeMode == mode) Modifier.focusRequester(firstFocus) else Modifier,
                        ) { onResizeMode(mode) }
                    }
                }
            }
            PlayerPanel.MORE -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    PlayerChoiceRow(stringResource(R.string.player_speed), "${snapshot.speed}×", false,
                        Modifier.focusRequester(firstFocus), actionRole = Role.Button) { onPanel(PlayerPanel.SPEED) }
                }
                item { PlayerChoiceRow(stringResource(R.string.player_display), null, false, actionRole = Role.Button) { onPanel(PlayerPanel.DISPLAY) } }
                item { PlayerChoiceRow(stringResource(R.string.player_info), null, false, actionRole = Role.Button) { onPanel(PlayerPanel.INFO) } }
                item { PlayerChoiceRow(stringResource(R.string.player_external), null, false, actionRole = Role.Button, onClick = onOpenExternally) }
            }
            PlayerPanel.INFO -> Unit
        }
    }
}

@Composable
private fun MobileSubtitleToolbar(
    subtitleStyle: SubtitleStyle,
    subtitleDelayMillis: Long,
    onTiming: () -> Unit,
    onStyle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface.copy(alpha = 0.56f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlayerTextButton(
            label = stringResource(R.string.player_sync_toolbar, formatSignedDelay(subtitleDelayMillis)),
            onClick = onTiming,
            modifier = Modifier.weight(1f),
        )
        PlayerTextButton(
            label = stringResource(R.string.player_style_toolbar, subtitleStyle.sizePercent),
            onClick = onStyle,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * TV caption selection follows a progressive rail model: choose a language,
 * choose the concrete stream track, then tune the rendered result. This keeps
 * the entire flow visible without turning a remote interaction into a stack of
 * dialogs (TV-NAV-05, TV-FOC-01, PLY-IMM-04).
 */
@Composable
private fun TvSubtitleRailPanel(
    options: List<TrackOption>,
    player: Player?,
    firstFocus: FocusRequester,
    subtitleStyle: SubtitleStyle,
    subtitleDelayMillis: Long,
    onSubtitleDelay: (Long) -> Unit,
    onTiming: () -> Unit,
    onStyle: () -> Unit,
    modifier: Modifier = Modifier,
    editorReturnTarget: PlayerEditor? = null,
) {
    val configuration = LocalConfiguration.current
    val displayLocale = configuration.locales[0]
    val unknownLabel = stringResource(R.string.player_subtitle_language_unknown)
    val activeLanguageKey = options.firstOrNull(TrackOption::selected)?.languageKey
        ?: SUBTITLE_LANGUAGE_OFF
    val groupedOptions = options.groupBy(TrackOption::languageKey)
    val languageItems = buildList {
        add(
            SubtitleLanguageRailItem(
                key = SUBTITLE_LANGUAGE_OFF,
                label = stringResource(R.string.player_subtitle_off),
                trackCount = 0,
            ),
        )
        groupedOptions.entries
            .map { (languageKey, tracks) ->
                SubtitleLanguageRailItem(
                    key = languageKey,
                    label = subtitleLanguageDisplayName(languageKey, displayLocale, unknownLabel),
                    trackCount = tracks.size,
                )
            }
            .sortedBy { it.label.lowercase(displayLocale) }
            .forEach(::add)
    }
    var browsedLanguageKey by remember { mutableStateOf(activeLanguageKey) }
    LaunchedEffect(activeLanguageKey, languageItems.map(SubtitleLanguageRailItem::key)) {
        if (languageItems.none { it.key == browsedLanguageKey }) {
            browsedLanguageKey = activeLanguageKey
        }
    }
    val visibleOptions = groupedOptions[browsedLanguageKey].orEmpty()
    val activeLanguageIndex = languageItems.indexOfFirst { it.key == activeLanguageKey }.coerceAtLeast(0)

    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerRailColumn(
            title = stringResource(R.string.player_subtitle_languages),
            modifier = Modifier.weight(0.23f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = rememberLazyListState(activeLanguageIndex),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(languageItems, key = { _, item -> item.key }) { index, item ->
                    val isOff = item.key == SUBTITLE_LANGUAGE_OFF
                    PlayerChoiceRow(
                        title = item.label,
                        supportingText = when {
                            isOff -> stringResource(R.string.player_subtitle_no_captions)
                            item.key == activeLanguageKey -> pluralStringResource(
                                R.plurals.player_subtitle_track_count_active,
                                item.trackCount,
                                item.trackCount,
                            )
                            else -> pluralStringResource(
                                R.plurals.player_subtitle_track_count,
                                item.trackCount,
                                item.trackCount,
                            )
                        },
                        selected = item.key == activeLanguageKey,
                        modifier = if (index == activeLanguageIndex && editorReturnTarget == null) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        browsedLanguageKey = item.key
                        if (isOff) player?.clearTrackOverride(C.TRACK_TYPE_TEXT, disabled = true)
                    }
                }
            }
        }

        PlayerRailColumn(
            title = stringResource(R.string.player_subtitle_tracks),
            modifier = Modifier.weight(0.385f),
        ) {
            when {
                browsedLanguageKey == SUBTITLE_LANGUAGE_OFF -> PlayerRailEmptyState(
                    title = stringResource(R.string.player_subtitle_off),
                    body = stringResource(R.string.player_subtitle_off_description),
                )
                visibleOptions.isEmpty() -> PlayerRailEmptyState(
                    title = stringResource(R.string.player_subtitle_no_tracks_title),
                    body = stringResource(R.string.player_subtitle_no_tracks_body),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(visibleOptions, key = { _, item -> item.id }) { _, option ->
                        PlayerChoiceRow(
                            title = option.title,
                            supportingText = option.supportingText
                                ?: stringResource(R.string.player_subtitle_embedded_track),
                            selected = option.selected,
                            enabled = option.supported,
                        ) {
                            player?.selectTrack(C.TRACK_TYPE_TEXT, option)
                        }
                    }
                }
            }
        }

        PlayerRailColumn(
            title = stringResource(R.string.player_caption_lab),
            modifier = Modifier.weight(0.385f).verticalScroll(rememberScrollState()),
        ) {
            PlayerCaptionPreviewCard(
                style = subtitleStyle,
                delayMillis = subtitleDelayMillis,
            )
            PlayerSubtitleTools(
                onTiming = onTiming,
                onStyle = onStyle,
                subtitleStyle = subtitleStyle,
                subtitleDelayMillis = subtitleDelayMillis,
                onSubtitleDelay = onSubtitleDelay,
                timingModifier = if (editorReturnTarget == PlayerEditor.TIMING) Modifier.focusRequester(firstFocus) else Modifier,
                styleModifier = if (editorReturnTarget == PlayerEditor.STYLE) Modifier.focusRequester(firstFocus) else Modifier,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlayerRailColumn(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

@Composable
private fun PlayerRailEmptyState(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface.copy(alpha = 0.44f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
        Text(
            text = body,
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PlayerCaptionPreviewCard(
    style: SubtitleStyle,
    delayMillis: Long,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics {
                contentDescription = "${style.sizePercent} percent captions, ${formatSignedDelay(delayMillis)} delay"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.player_caption_preview),
            color = Color(style.textColor).copy(
                alpha = SubtitleStylePolicy.clampOpacity(style.textOpacity),
            ),
            fontFamily = PlayerFont,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = (14f * style.sizePercent / 100f).coerceIn(11f, 22f).sp,
            lineHeight = (18f * style.sizePercent / 100f).coerceIn(14f, 26f).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(
                    Color(style.backgroundColor).copy(
                        alpha = SubtitleStylePolicy.clampOpacity(style.backgroundOpacity),
                    ),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            text = stringResource(
                R.string.player_caption_preview_details,
                style.sizePercent,
                formatSignedDelay(delayMillis),
            ),
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun PlayerTrackList(
    options: List<TrackOption>,
    trackType: Int,
    player: Player?,
    includeAutomatic: Boolean,
    automaticSelected: Boolean,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
    compactRows: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    val initialIndex = remember(options.map(TrackOption::id)) {
        if (automaticSelected) 0 else (options.indexOfFirst(TrackOption::selected) + 1).coerceAtLeast(0)
    }
    LazyColumn(
        modifier = modifier,
        state = rememberLazyListState(initialIndex),
        contentPadding = PaddingValues(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (includeAutomatic) {
            item("automatic") {
                PlayerChoiceRow(
                    title = stringResource(R.string.player_automatic),
                    supportingText = options.firstOrNull(TrackOption::selected)?.let { selected ->
                        listOfNotNull(selected.title, selected.supportingText).joinToString(" · ")
                    } ?: stringResource(R.string.player_best_track),
                    selected = automaticSelected,
                    compact = compactRows,
                    modifier = if (initialIndex == 0) Modifier.focusRequester(firstFocus) else Modifier,
                ) {
                    player?.clearTrackOverride(trackType, disabled = false)
                }
            }
        } else {
            item("off") {
                PlayerChoiceRow(
                    title = stringResource(R.string.player_subtitle_off),
                    supportingText = stringResource(R.string.player_subtitle_no_captions),
                    selected = options.none(TrackOption::selected),
                    compact = compactRows,
                    modifier = if (initialIndex == 0) Modifier.focusRequester(firstFocus) else Modifier,
                ) {
                    player?.clearTrackOverride(trackType, disabled = true)
                }
            }
        }
        if (options.isEmpty()) {
            item("empty") {
                Text(
                    text = if (trackType == C.TRACK_TYPE_AUDIO) {
                        stringResource(R.string.player_no_audio)
                    } else {
                        stringResource(R.string.player_no_subtitles)
                    },
                    color = PlayerOnSurfaceMuted,
                    fontFamily = PlayerFont,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
        }
        itemsIndexed(options, key = { _, item -> item.id }) { index, option ->
            PlayerChoiceRow(
                title = option.title,
                supportingText = option.supportingText,
                selected = option.selected && (!includeAutomatic || !automaticSelected),
                enabled = option.supported,
                compact = compactRows,
                modifier = if (index + 1 == initialIndex) Modifier.focusRequester(firstFocus) else Modifier,
            ) {
                player?.selectTrack(trackType, option)
            }
        }
        if (footer != null) item("tools") { footer() }
    }
}

@Composable
private fun PlayerAudioTimingControls(
    delayMillis: Long,
    onDelay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface.copy(alpha = 0.56f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.player_audio_timing),
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.player_audio_route),
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontSize = 12.sp,
        )
        PlayerStepper(
            value = if (delayMillis == 0L) "0 ms" else "%+d ms".format(delayMillis),
            decreaseLabel = stringResource(R.string.player_audio_earlier),
            increaseLabel = stringResource(R.string.player_audio_later),
            onDecrease = { onDelay(stepAudioDelay(delayMillis, -1)) },
            onReset = { onDelay(0L) },
            onIncrease = { onDelay(stepAudioDelay(delayMillis, 1)) },
        )
        Text(
            text = stringResource(R.string.player_audio_delay_help),
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PlayerSubtitleTools(
    onTiming: () -> Unit,
    onStyle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
    subtitleDelayMillis: Long = 0L,
    onSubtitleDelay: (Long) -> Unit = {},
    timingModifier: Modifier = Modifier,
    styleModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface.copy(alpha = 0.56f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlayerChoiceRow(
            title = stringResource(R.string.player_sync),
            supportingText = stringResource(R.string.player_sync_details, formatSignedDelay(subtitleDelayMillis)),
            selected = subtitleDelayMillis != 0L,
            onClick = onTiming,
            modifier = timingModifier,
            actionRole = Role.Button,
        )
        PlayerChoiceRow(
            title = stringResource(R.string.player_appearance),
            supportingText = stringResource(R.string.player_style_details, subtitleStyle.sizePercent,
                stringResource(if (subtitleStyle.preserveEmbeddedStyles) R.string.player_original_style else R.string.player_custom_style)),
            selected = false,
            onClick = onStyle,
            modifier = styleModifier,
            actionRole = Role.Button,
        )
        PlayerDelayRow(
            label = stringResource(R.string.player_quick_sync),
            valueText = formatSignedDelay(subtitleDelayMillis),
            onStep = { steps -> onSubtitleDelay(stepSubtitleDelay(subtitleDelayMillis, steps * 10)) },
            onReset = { onSubtitleDelay(0L) },
        )
    }
}

@Composable
private fun PlayerChoiceRow(
    title: String,
    supportingText: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    actionRole: Role = Role.RadioButton,
    onClick: () -> Unit,
) {
    val interactionModifier = when (actionRole) {
        Role.Button -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        Role.Checkbox -> Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() }
        else -> Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    if (!LocalPlayerTelevision.current) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = supportingText?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
            trailingContent = {
                when (actionRole) {
                    Role.Button -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    Role.Checkbox -> Checkbox(selected, onCheckedChange = null, enabled = enabled)
                    else -> RadioButton(selected, onClick = null, enabled = enabled)
                }
            },
            colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent),
            modifier = modifier.then(interactionModifier),
        )
        return
    }
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    focused -> PlayerFocused
                    selected -> PlayerPrimary.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
            )
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) PlayerPrimary else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
            )
            .then(interactionModifier)
            .padding(
                horizontal = if (compact) 16.dp else 22.dp,
                vertical = if (compact) 9.dp else 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = when {
                    !enabled -> PlayerOnSurfaceMuted.copy(alpha = 0.45f)
                    focused -> PlayerFocusedContent
                    else -> PlayerOnSurface
                },
                fontFamily = PlayerFont,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            supportingText?.let {
                Text(
                    text = it,
                    color = if (focused) PlayerFocusedContent.copy(alpha = 0.74f) else PlayerOnSurfaceMuted,
                    fontFamily = PlayerFont,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (focused) PlayerFocusedContent else PlayerPrimary,
            )
        }
    }
}

@Composable
private fun PlayerStreamInfoPanel(streamInfo: String?, isTelevision: Boolean, onClose: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isTelevision) closeFocus.requestFocus() }
    PlayerOverlayLayout(stringResource(R.string.player_info), isTelevision, onClose, tvWidth = 560.dp) {
        Text(streamInfo ?: stringResource(R.string.player_info_empty),
            style = MaterialTheme.typography.bodyLarge, color = PlayerOnSurfaceMuted)
        PlayerTextButton(stringResource(R.string.player_done), onClose, Modifier.focusRequester(closeFocus))
    }
}

@Composable
private fun PlayerTimingEditor(
    isTelevision: Boolean,
    currentPositionMillis: Long,
    subtitleDelayMillis: Long,
    onSubtitleDelay: (Long) -> Unit,
    onLoadSidecarCues: ((List<SubtitleCue>) -> Unit) -> Unit,
    onApplySyncByLine: (Long, Long) -> Unit,
    onClose: () -> Unit,
) {
    var cues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var capturedPositionMillis by remember { mutableStateOf<Long?>(null) }
    var loadingCues by remember { mutableStateOf(false) }
    val initialFocus = remember { FocusRequester() }
    val visibleCues = remember(cues, capturedPositionMillis) {
        capturedPositionMillis?.let { nearbySubtitleCues(cues, it) }.orEmpty()
    }
    LaunchedEffect(Unit) { if (isTelevision) initialFocus.requestFocus() }
    PlayerOverlayLayout(stringResource(R.string.player_timing), isTelevision, onClose, tvWidth = 560.dp) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("delay") {
                PlayerDelayRow(stringResource(R.string.player_manual_delay), formatSignedDelay(subtitleDelayMillis),
                    { onSubtitleDelay(stepSubtitleDelay(subtitleDelayMillis, it)) }, { onSubtitleDelay(0) })
            }
            item("help") {
                Text(stringResource(R.string.player_subtitle_delay_help), color = PlayerOnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium)
            }
            item("sync") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.player_sync_help), color = PlayerOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium)
                    PlayerTextButton(stringResource(if (capturedPositionMillis == null) R.string.player_sync_now
                        else R.string.player_capture_again), onClick = {
                        capturedPositionMillis = currentPositionMillis
                        loadingCues = true
                        onLoadSidecarCues { cues = it; loadingCues = false }
                    }, modifier = Modifier.focusRequester(initialFocus))
                }
            }
            if (capturedPositionMillis != null) {
                item("captured") {
                    Text(stringResource(R.string.player_sync_capture, capturedPositionMillis!!.asPlaybackTime()),
                        color = PlayerOnSurface, style = MaterialTheme.typography.bodyMedium)
                }
                if (loadingCues) item("loading") { CircularProgressIndicator(Modifier.size(28.dp), color = PlayerPrimary) }
                else if (cues.isEmpty()) item("empty") {
                    Text(stringResource(R.string.player_sync_no_sidecar), color = PlayerOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium)
                }
                else itemsIndexed(visibleCues, key = { index, cue -> "$index:${cue.startMillis}" }) { _, cue ->
                    PlayerChoiceRow(cue.text.replace("\n", " "), cue.startMillis.asPlaybackTime(), false) {
                        onApplySyncByLine(capturedPositionMillis!!, cue.startMillis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerStepper(
    value: String,
    decreaseLabel: String,
    increaseLabel: String,
    onDecrease: () -> Unit,
    onReset: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlayerTextButton(
            label = "−",
            onClick = onDecrease,
            modifier = Modifier.semantics { contentDescription = decreaseLabel },
        )
        PlayerTextButton(label = value, onClick = onReset)
        PlayerTextButton(
            label = "+",
            onClick = onIncrease,
            modifier = Modifier.semantics { contentDescription = increaseLabel },
        )
    }
}

@Composable
private fun PlayerDelayRow(
    label: String,
    valueText: String,
    onStep: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!LocalPlayerTelevision.current) {
        Row(modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayerDelayLabel(label, valueText, Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onStep(-1) }) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.player_decrease_value, label))
            }
            FilledTonalIconButton(onClick = onReset) {
                Icon(Icons.Rounded.Replay, stringResource(R.string.player_reset_value, label))
            }
            FilledTonalIconButton(onClick = { onStep(1) }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.player_increase_value, label))
            }
        }
        return
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        val compact = maxWidth < 480.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerDelayLabel(label, valueText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerTextButton(label = "−", onClick = { onStep(-1) })
                    PlayerTextButton(label = stringResource(R.string.player_reset), onClick = onReset)
                    PlayerTextButton(label = "+", onClick = { onStep(1) })
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerDelayLabel(label, valueText, Modifier.weight(1f))
                PlayerTextButton(label = "−", onClick = { onStep(-1) })
                PlayerTextButton(label = stringResource(R.string.player_reset), onClick = onReset)
                PlayerTextButton(label = "+", onClick = { onStep(1) })
            }
        }
    }
}

@Composable
private fun PlayerDelayLabel(label: String, valueText: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text = label, color = PlayerOnSurface, fontFamily = PlayerFont, fontSize = 15.sp)
        Text(text = valueText, color = PlayerOnSurfaceMuted, fontFamily = PlayerFont, fontSize = 13.sp)
    }
}

@Composable
private fun PlayerStyleEditor(
    style: SubtitleStyle,
    isTelevision: Boolean,
    onStyleChange: (SubtitleStyle) -> Unit,
    onClose: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    PlayerOverlayLayout(stringResource(R.string.player_appearance), isTelevision, onClose, tvWidth = 560.dp) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("preview") { PlayerSubtitlePreview(style) }
            item("reset") {
                PlayerTextButton(stringResource(R.string.player_reset_all),
                    { onStyleChange(SubtitleStyle()) }, Modifier.focusRequester(firstFocus))
            }
            item("presets") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerTextButton(
                    label = stringResource(R.string.player_preset_default),
                    onClick = { onStyleChange(SubtitleStyle()) },
                    modifier = Modifier.weight(1f),
                )
                PlayerTextButton(
                    label = stringResource(R.string.player_preset_cinema),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onStyleChange(
                            SubtitleStyle(
                                sizePercent = 110,
                                verticalPositionFraction = style.verticalPositionFraction,
                                bold = false,
                                textColor = 0xFFFFFFFFL,
                                textOpacity = 1f,
                                backgroundColor = 0xFF000000L,
                                backgroundOpacity = 0.65f,
                                outlineEnabled = true,
                                outlineColor = 0xFF000000L,
                                outlineWidthDp = 2f,
                                preserveEmbeddedStyles = false,
                            ),
                        )
                    },
                )
                PlayerTextButton(
                    label = stringResource(R.string.player_preset_accessible),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onStyleChange(
                            SubtitleStyle(
                                sizePercent = 130,
                                verticalPositionFraction = style.verticalPositionFraction,
                                bold = true,
                                textColor = 0xFFFFFFFFL,
                                textOpacity = 1f,
                                backgroundColor = 0xFF000000L,
                                backgroundOpacity = 0.75f,
                                outlineEnabled = true,
                                outlineColor = 0xFF000000L,
                                outlineWidthDp = 2f,
                                preserveEmbeddedStyles = false,
                            ),
                        )
                    },
                )
            }
            }
                item("size") {
                    PlayerDelayRow(
                        label = stringResource(R.string.player_text_size),
                        valueText = "${style.sizePercent}%",
                        onStep = { steps ->
                            onStyleChange(style.copy(sizePercent = SubtitleStylePolicy.clampSizePercent(style.sizePercent + steps * 10)))
                        },
                        onReset = { onStyleChange(style.copy(sizePercent = 100)) },
                    )
                }
                item("position") {
                    PlayerDelayRow(
                        label = stringResource(R.string.player_position),
                        valueText = "${(style.verticalPositionFraction * 100).toInt()}%",
                        onStep = { steps ->
                            onStyleChange(
                                style.copy(
                                    verticalPositionFraction = SubtitleStylePolicy.clampPositionFraction(
                                        style.verticalPositionFraction + steps * 0.05f,
                                    ),
                                ),
                            )
                        },
                        onReset = { onStyleChange(style.copy(verticalPositionFraction = 0.92f)) },
                    )
                }
                item("text-opacity") {
                    PlayerDelayRow(
                        label = stringResource(R.string.player_text_opacity),
                        valueText = "${(SubtitleStylePolicy.clampOpacity(style.textOpacity) * 100).toInt()}%",
                        onStep = { steps ->
                            onStyleChange(
                                style.copy(textOpacity = SubtitleStylePolicy.clampOpacity(style.textOpacity + steps * 0.1f)),
                            )
                        },
                        onReset = { onStyleChange(style.copy(textOpacity = 1f)) },
                    )
                }
                item("bold") {
                    PlayerChoiceRow(
                        title = stringResource(R.string.player_bold),
                        supportingText = null,
                        selected = style.bold,
                        actionRole = Role.Checkbox,
                    ) {
                        onStyleChange(style.copy(bold = !style.bold))
                    }
                }
                item("text-color") {
                    SubtitleColorRow(
                        label = stringResource(R.string.player_text_color),
                        selected = style.textColor,
                        colors = SUBTITLE_TEXT_COLORS,
                    ) { onStyleChange(style.copy(textColor = it)) }
                }
                item("background-opacity") {
                    PlayerDelayRow(
                        label = stringResource(R.string.player_caption_background),
                        valueText = "${(SubtitleStylePolicy.clampOpacity(style.backgroundOpacity) * 100).toInt()}%",
                        onStep = { steps ->
                            onStyleChange(
                                style.copy(
                                    backgroundColor = 0xFF000000L,
                                    backgroundOpacity = SubtitleStylePolicy.clampOpacity(
                                        style.backgroundOpacity + steps * 0.1f,
                                    ),
                                ),
                            )
                        },
                        onReset = { onStyleChange(style.copy(backgroundOpacity = 0f)) },
                    )
                }
                item("outline") {
                    PlayerChoiceRow(
                        title = stringResource(R.string.player_outline),
                        supportingText = if (style.outlineEnabled) "Enabled · ${style.outlineWidthDp} dp" else "Disabled",
                        selected = style.outlineEnabled,
                        actionRole = Role.Checkbox,
                    ) {
                        onStyleChange(style.copy(outlineEnabled = !style.outlineEnabled))
                    }
                }
                if (style.outlineEnabled) {
                    item("outline-width") {
                        PlayerDelayRow(
                            label = stringResource(R.string.player_outline_width),
                            valueText = "${style.outlineWidthDp} dp",
                            onStep = { steps ->
                                onStyleChange(
                                    style.copy(
                                        outlineWidthDp = SubtitleStylePolicy.clampOutlineWidthDp(
                                            style.outlineWidthDp + steps * 0.5f,
                                        ),
                                    ),
                                )
                            },
                            onReset = { onStyleChange(style.copy(outlineWidthDp = 1.5f)) },
                        )
                    }
                    item("outline-color") {
                        SubtitleColorRow(
                            label = stringResource(R.string.player_outline_color),
                            selected = style.outlineColor,
                            colors = SUBTITLE_OUTLINE_COLORS,
                        ) { onStyleChange(style.copy(outlineColor = it)) }
                    }
                }
                item("preserve") {
                    PlayerChoiceRow(
                        title = stringResource(R.string.player_preserve_styles),
                        supportingText = stringResource(R.string.player_preserve_styles_detail),
                        selected = style.preserveEmbeddedStyles,
                        actionRole = Role.Checkbox,
                    ) {
                        onStyleChange(style.copy(preserveEmbeddedStyles = !style.preserveEmbeddedStyles))
                    }
                }
        }
    }
}

@Composable
private fun PlayerSubtitlePreview(style: SubtitleStyle) {
    val sample = stringResource(R.string.player_caption_preview)
    AndroidView(
        factory = { context -> androidx.media3.ui.SubtitleView(context) },
        update = { view ->
            SubtitleStyleApplier.apply(view, style)
            view.setCues(listOf(androidx.media3.common.text.Cue.Builder().setText(sample).build()))
        },
        modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.Black)
            .semantics { contentDescription = sample },
    )
}

@Composable
private fun SubtitleColorRow(
    label: String,
    selected: Long,
    colors: List<Long>,
    onSelected: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        colors.forEach { value ->
            val colorName = stringResource(when (value) {
                0xFFFFFFFFL -> R.string.player_color_white
                0xFFFFD54FL -> R.string.player_color_yellow
                0xFF80DEEDL -> R.string.player_color_cyan
                0xFFA5D6A7L -> R.string.player_color_green
                0xFF000000L -> R.string.player_color_black
                else -> R.string.player_color_charcoal
            })
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        width = if (focused || value == selected) 3.dp else 1.dp,
                        color = if (focused) PlayerPrimary else if (value == selected) PlayerOnSurface else PlayerOnSurfaceMuted.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .selectable(selected = value == selected, role = Role.RadioButton) { onSelected(value) }
                    .semantics { contentDescription = "$label: $colorName" }
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(value)),
                )
            }
        }
        }
    }
}

@Composable
private fun PlayerErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = message,
                color = PlayerOnSurface,
                fontFamily = PlayerFont,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerTextButton("Retry", onRetry, Modifier.focusRequester(retryFocus))
                PlayerTextButton("Open in another player", onClick = onOpenExternally)
            }
        }
    }
}

@Composable
private fun PlayerTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!LocalPlayerTelevision.current) {
        TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) PlayerFocused else PlayerControlContainer)
            .border(if (focused) 3.dp else 0.dp, PlayerPrimary, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) PlayerFocusedContent else PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
    }
}

private fun Player.snapshot(): PlayerSnapshot {
    val tracks = currentTracks
    val capabilityError = when {
        tracks.containsType(C.TRACK_TYPE_VIDEO) && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO) ->
            "This TV cannot decode this video's format or resolution. Try another source or player."
        tracks.containsType(C.TRACK_TYPE_AUDIO) && !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO) ->
            "This TV cannot decode this source's audio. Try another source or player."
        else -> null
    }
    return PlayerSnapshot(
        playing = isPlaying,
        buffering = playbackState == Player.STATE_BUFFERING,
        positionMillis = currentPosition.coerceAtLeast(0),
        bufferedPositionMillis = bufferedPosition.coerceAtLeast(0),
        durationMillis = duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
        tracks = tracks,
        speed = playbackParameters.speed,
        errorMessage = playerError?.safeMessage() ?: capabilityError,
    )
}

private fun PlaybackException.safeMessage(): String = when (errorCode) {
    in 2000..2999 -> "The stream could not be loaded. Check the connection or try another source."
    in 3000..3999 -> "This source uses a container the player could not read."
    in 4000..4999 -> "This TV cannot decode this video format. Try another source or player."
    in 5000..5999 -> "This TV cannot play the source's audio format. Try another source or player."
    else -> "Playback stopped unexpectedly. Try again or choose another source."
}

private fun Tracks.options(trackType: Int): List<TrackOption> = groups
    .filter { it.type == trackType }
    .flatMapIndexed { groupIndex, group ->
        (0 until group.length).map { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            TrackOption(
                id = "$trackType:$groupIndex:$trackIndex:${format.id.orEmpty()}",
                title = format.trackTitle(trackIndex),
                supportingText = format.trackDetails(trackType),
                selected = group.isTrackSelected(trackIndex),
                supported = group.isTrackSupported(trackIndex, true),
                languageKey = normalizedSubtitleLanguageKey(format.language),
                group = group.mediaTrackGroup,
                trackIndex = trackIndex,
            )
        }
    }

private fun Format.trackTitle(index: Int): String {
    label?.takeIf(String::isNotBlank)?.let { return it }
    language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
        Locale.forLanguageTag(code).displayLanguage.takeIf(String::isNotBlank)?.let { return it }
    }
    return "Track ${index + 1}"
}

private fun Format.trackDetails(trackType: Int): String? = buildList {
    if (selectionFlags and C.SELECTION_FLAG_DEFAULT != 0) add("Default")
    if (selectionFlags and C.SELECTION_FLAG_FORCED != 0) add("Forced")
    if (roleFlags and C.ROLE_FLAG_CAPTION != 0) add("Captions")
    if (roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) add("SDH")
    if (roleFlags and C.ROLE_FLAG_COMMENTARY != 0) add("Commentary")
    if (roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) add("Audio description")
    if (trackType == C.TRACK_TYPE_AUDIO && channelCount > 0) {
        add(when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "$channelCount channels"
        })
    }
    sampleMimeType?.friendlyCodecName()?.let(::add)
    if (trackType == C.TRACK_TYPE_AUDIO && sampleRate > 0) add("${sampleRate / 1_000.0} kHz")
    if (bitrate > 0) add("${bitrate / 1_000} kbps")
}.joinToString(" · ").ifBlank { null }

private fun String.friendlyCodecName(): String = when (lowercase(Locale.ROOT)) {
    "audio/eac3-joc" -> "Dolby Atmos"
    "audio/eac3" -> "Dolby Digital Plus"
    "audio/ac3" -> "Dolby Digital"
    "audio/true-hd" -> "Dolby TrueHD"
    "audio/vnd.dts", "audio/dts" -> "DTS"
    "audio/vnd.dts.hd", "audio/dts-hd" -> "DTS-HD"
    "audio/mp4a-latm" -> "AAC"
    "audio/opus" -> "Opus"
    "audio/flac" -> "FLAC"
    "audio/mpeg" -> "MP3"
    "text/vtt" -> "WebVTT"
    "application/x-subrip" -> "SRT"
    "text/x-ssa", "text/x-ass" -> "ASS/SSA"
    "application/ttml+xml" -> "TTML"
    else -> substringAfter('/').uppercase(Locale.ROOT)
}

private fun Player.selectTrack(trackType: Int, option: TrackOption) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(trackType, false)
        .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
        .build()
}

private fun Player.clearTrackOverride(trackType: Int, disabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(trackType)
        .setTrackTypeDisabled(trackType, disabled)
        .build()
}

private fun Player.hasOverride(trackType: Int): Boolean =
    trackSelectionParameters.overrides.values.any { it.type == trackType }

private fun Long.asPlaybackTime(): String {
    val totalSeconds = coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private val PLAYBACK_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SUBTITLE_TEXT_COLORS = listOf(
    0xFFFFFFFFL,
    0xFFFFD54FL,
    0xFF80DEEDL,
    0xFFA5D6A7L,
)
private val SUBTITLE_OUTLINE_COLORS = listOf(
    0xFF000000L,
    0xFFFFFFFFL,
    0xFF263238L,
)
