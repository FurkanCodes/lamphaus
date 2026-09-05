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

private enum class PlayerPanel { AUDIO, SUBTITLES, SPEED, INFO }

private enum class PlayerEditor { TIMING, STYLE }

private data class PlayerSnapshot(
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
) {
    var snapshot by remember(player) { mutableStateOf(player?.snapshot() ?: PlayerSnapshot()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var editor by remember { mutableStateOf<PlayerEditor?>(null) }
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
                view.player = player
                view.keepScreenOn = player?.isPlaying == true
                // Keep captions above the control gradient while chrome is visible
                // (Netflix/Stremio pattern), at rest position during clean viewing.
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
                    text = "Loading…",
                    color = PlayerOnSurface,
                    fontFamily = PlayerFont,
                    fontSize = if (isTelevision) 16.sp else 14.sp,
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

        if (!inPictureInPicture && controlsVisible && snapshot.errorMessage == null) {
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
                PlayerStreamInfoPanel(streamInfo = streamInfo, onClose = ::closePanel)
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
                        editor = opened
                        revealControls()
                    },
                    onClose = ::closePanel,
                    subtitleDelayMillis = subtitleDelayMillis,
                    onSubtitleDelay = onSubtitleDelay,
                    subtitleStyle = subtitleStyle,
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(
    request: PlaybackRequest,
    snapshot: PlayerSnapshot,
    isTelevision: Boolean,
    onInteraction: () -> Unit,
    onTogglePlay: () -> Unit,
    onReplay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    pictureInPictureAvailable: Boolean,
    canPlayNext: Boolean,
    nextEpisodeLoading: Boolean,
    onNextEpisode: () -> Unit,
    focusPanel: PlayerPanel?,
    focusRequestVersion: Long,
    onPanel: (PlayerPanel) -> Unit,
    segments: List<PlaybackSegment> = emptyList(),
    onExit: () -> Unit = {},
) {
    val playFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val ended = snapshot.durationMillis > 0 && snapshot.positionMillis >= snapshot.durationMillis - 1_000
    val subtitlesActive = remember(snapshot.tracks) {
        snapshot.tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_TEXT && (0 until group.length).any(group::isTrackSelected)
        }
    }
    val speedActive = snapshot.speed != 1f
    LaunchedEffect(focusRequestVersion) {
        when (focusPanel) {
            PlayerPanel.AUDIO -> audioFocus
            PlayerPanel.SUBTITLES -> subtitlesFocus
            PlayerPanel.SPEED -> speedFocus
            else -> playFocus
        }.requestFocus()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (isTelevision) {
            // JetStream contract (plan §5): 844dp lower information/seeker
            // frame on the 960dp canvas = 58dp safe margins on each side.
            // Title, timeline with time labels, Info affordance, and the four
            // 40dp visual action icons (48dp touch targets). The CC/Audio/
            // Settings entries open the 216x320dp contextual menus.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(844.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlayerTitle(request, true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PlayerTime(snapshot.positionMillis)
                    PlayerProgress(
                        positionMillis = snapshot.positionMillis,
                        bufferedPositionMillis = snapshot.bufferedPositionMillis,
                        durationMillis = snapshot.durationMillis,
                        modifier = Modifier.weight(1f),
                        onSeekTo = onSeekTo,
                        onInteraction = onInteraction,
                        segments = segments,
                    )
                    PlayerRemaining(snapshot.positionMillis, snapshot.durationMillis)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    PlayerTextButton(
                        label = "Info",
                        onClick = { onPanel(PlayerPanel.INFO) },
                    )
                    Spacer(Modifier.weight(1f))
                    PlayerActionButton(
                        icon = Icons.Rounded.Replay10,
                        label = "Rewind 10 seconds",
                    ) {
                        onSeekBack(); onInteraction()
                    }
                    PlayerActionButton(
                        icon = when {
                            ended -> Icons.Rounded.Replay
                            snapshot.playing -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        label = when {
                            ended -> "Replay"
                            snapshot.playing -> "Pause"
                            else -> "Play"
                        },
                        visualSize = 30.dp,
                        containerSize = 56.dp,
                        primary = true,
                        modifier = Modifier.focusRequester(playFocus),
                    ) {
                        if (ended) onReplay() else onTogglePlay()
                        onInteraction()
                    }
                    PlayerActionButton(
                        icon = Icons.Rounded.FastForward,
                        label = "Forward 10 seconds",
                    ) {
                        onSeekForward(); onInteraction()
                    }
                    if (canPlayNext) {
                        PlayerActionButton(
                            icon = Icons.Rounded.SkipNext,
                            label = "Next episode",
                        ) {
                            if (!nextEpisodeLoading) onNextEpisode()
                            onInteraction()
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    PlayerActionButton(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = "Audio",
                        modifier = Modifier.focusRequester(audioFocus),
                    ) { onPanel(PlayerPanel.AUDIO) }
                    PlayerActionButton(
                        icon = Icons.Rounded.ClosedCaption,
                        label = if (subtitlesActive) "Subtitles, on" else "Subtitles",
                        active = subtitlesActive,
                        modifier = Modifier.focusRequester(subtitlesFocus),
                    ) { onPanel(PlayerPanel.SUBTITLES) }
                    PlayerActionButton(
                        icon = Icons.Rounded.Settings,
                        label = if (speedActive) "Playback speed, ${snapshot.speed} times" else "Playback speed",
                        active = speedActive,
                        modifier = Modifier.focusRequester(speedFocus),
                    ) { onPanel(PlayerPanel.SPEED) }
                    if (pictureInPictureAvailable) {
                        PlayerActionButton(
                            icon = Icons.Rounded.PictureInPictureAlt,
                            label = "Picture in picture",
                        ) {
                            onEnterPictureInPicture()
                            onInteraction()
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PlayerActionButton(
                    icon = Icons.Rounded.Close,
                    label = "Close player",
                    onClick = onExit,
                )
                PlayerTitle(request, false, Modifier.weight(1f))
                if (pictureInPictureAvailable) {
                    PlayerActionButton(
                        icon = Icons.Rounded.PictureInPictureAlt,
                        label = "Picture in picture",
                        onClick = {
                            onEnterPictureInPicture()
                            onInteraction()
                        },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerTime(snapshot.positionMillis)
                    PlayerProgress(
                        positionMillis = snapshot.positionMillis,
                        bufferedPositionMillis = snapshot.bufferedPositionMillis,
                        durationMillis = snapshot.durationMillis,
                        modifier = Modifier.weight(1f),
                        onSeekTo = onSeekTo,
                        onInteraction = onInteraction,
                        segments = segments,
                    )
                    PlayerRemaining(snapshot.positionMillis, snapshot.durationMillis)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    PlayerActionButton(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = "Audio",
                        modifier = Modifier.focusRequester(audioFocus),
                    ) { onPanel(PlayerPanel.AUDIO) }
                    PlayerActionButton(
                        icon = Icons.Rounded.ClosedCaption,
                        label = if (subtitlesActive) "Subtitles, on" else "Subtitles",
                        active = subtitlesActive,
                        modifier = Modifier.focusRequester(subtitlesFocus),
                    ) { onPanel(PlayerPanel.SUBTITLES) }
                    Spacer(Modifier.weight(1f))
                    PlayerActionButton(Icons.Rounded.Replay10, "Rewind 10 seconds") {
                        onSeekBack(); onInteraction()
                    }
                    PlayerActionButton(
                        icon = when {
                            ended -> Icons.Rounded.Replay
                            snapshot.playing -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        label = when {
                            ended -> "Replay"
                            snapshot.playing -> "Pause"
                            else -> "Play"
                        },
                        visualSize = 28.dp,
                        containerSize = 52.dp,
                        primary = true,
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = {
                            if (ended) onReplay() else onTogglePlay()
                            onInteraction()
                        },
                    )
                    PlayerActionButton(Icons.Rounded.FastForward, "Forward 10 seconds") {
                        onSeekForward(); onInteraction()
                    }
                    Spacer(Modifier.weight(1f))
                    PlayerActionButton(
                        icon = Icons.Rounded.Settings,
                        label = if (speedActive) "Playback speed, ${snapshot.speed} times" else "Playback speed",
                        active = speedActive,
                        modifier = Modifier.focusRequester(speedFocus),
                    ) { onPanel(PlayerPanel.SPEED) }
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
private fun PlayerTitle(request: PlaybackRequest, isTelevision: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = request.title,
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = if (isTelevision) 24.sp else 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        request.subtitle?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                fontSize = if (isTelevision) 16.sp else 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
private fun PlayerActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    visualSize: androidx.compose.ui.unit.Dp = 26.dp,
    active: Boolean = false,
    primary: Boolean = false,
    containerSize: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit,
) {
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
                contentDescription = if (active) "$label, on" else label
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
private fun PlayerSettingButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
                contentDescription = if (active) "$label, on" else label
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

@Composable
private fun PlayerProgress(
    positionMillis: Long,
    bufferedPositionMillis: Long,
    durationMillis: Long,
    modifier: Modifier,
    onSeekTo: (Long) -> Unit,
    onInteraction: () -> Unit,
    segments: List<PlaybackSegment> = emptyList(),
) {
    var focused by remember { mutableStateOf(false) }
    val played = if (durationMillis > 0) positionMillis.toFloat() / durationMillis else 0f
    val buffered = if (durationMillis > 0) bufferedPositionMillis.toFloat() / durationMillis else 0f
    Canvas(
        modifier
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                role = Role.Button
                contentDescription = "Seek, ${positionMillis.asPlaybackTime()} of ${durationMillis.asPlaybackTime()}"
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
private fun PlayerTime(milliseconds: Long) {
    Text(
        text = milliseconds.asPlaybackTime(),
        color = PlayerOnSurface,
        fontFamily = PlayerFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}

@Composable
private fun PlayerRemaining(positionMillis: Long, durationMillis: Long) {
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
    subtitleDelayMillis: Long = 0L,
    onSubtitleDelay: (Long) -> Unit = {},
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
) {
    val firstFocus = remember(panel) { FocusRequester() }
    val title = when (panel) {
        PlayerPanel.AUDIO -> "Audio"
        PlayerPanel.SUBTITLES -> "Subtitles"
        PlayerPanel.SPEED -> "Playback speed"
        else -> ""
    }
    val options = when (panel) {
        PlayerPanel.AUDIO -> snapshot.tracks.options(C.TRACK_TYPE_AUDIO)
        PlayerPanel.SUBTITLES -> snapshot.tracks.options(C.TRACK_TYPE_TEXT)
        else -> emptyList()
    }
    val audioUsesAutoSelection = player?.hasOverride(C.TRACK_TYPE_AUDIO) == false
    LaunchedEffect(panel, options.size) { firstFocus.requestFocus() }

    val panelWidth = when {
        !isTelevision -> null
        panel == PlayerPanel.AUDIO -> 724.dp
        panel == PlayerPanel.SUBTITLES -> 844.dp
        else -> 280.dp
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(if (isTelevision) Alignment.BottomStart else Alignment.BottomCenter)
                .padding(
                    start = if (isTelevision) 58.dp else 16.dp,
                    end = if (isTelevision) 0.dp else 16.dp,
                    bottom = if (isTelevision) 48.dp else 16.dp,
                )
                .then(panelWidth?.let(Modifier::width) ?: Modifier.fillMaxWidth())
                .heightIn(max = 420.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PlayerBackground.copy(alpha = 0.98f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .clickable(enabled = false) { }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = title,
                        color = PlayerOnSurface,
                        fontFamily = PlayerFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                    )
                    if (isTelevision && (panel == PlayerPanel.AUDIO || panel == PlayerPanel.SUBTITLES)) {
                        Text(
                            text = if (panel == PlayerPanel.AUDIO) {
                                "Choose a track and fine-tune output timing"
                            } else {
                                "Choose a track, then adjust timing or appearance"
                            },
                            color = PlayerOnSurfaceMuted,
                            fontFamily = PlayerFont,
                            fontSize = 13.sp,
                        )
                    }
                }
                PlayerTextButton(label = "Done", onClick = onClose)
            }

            when (panel) {
                PlayerPanel.AUDIO -> {
                    if (isTelevision) {
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            PlayerTrackList(
                                options = options,
                                trackType = C.TRACK_TYPE_AUDIO,
                                player = player,
                                includeAutomatic = true,
                                automaticSelected = audioUsesAutoSelection,
                                firstFocus = firstFocus,
                                modifier = Modifier.weight(1f),
                            )
                            PlayerAudioTimingControls(
                                delayMillis = audioDelayMillis,
                                onDelay = onAudioDelay,
                                modifier = Modifier.width(250.dp),
                            )
                        }
                    } else {
                        PlayerAudioTimingControls(
                            delayMillis = audioDelayMillis,
                            onDelay = onAudioDelay,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PlayerTrackList(
                            options = options,
                            trackType = C.TRACK_TYPE_AUDIO,
                            player = player,
                            includeAutomatic = true,
                            automaticSelected = audioUsesAutoSelection,
                            firstFocus = firstFocus,
                            compactRows = true,
                            modifier = Modifier.heightIn(max = 300.dp),
                        )
                    }
                }
                PlayerPanel.SUBTITLES -> {
                    if (isTelevision) {
                        TvSubtitleRailPanel(
                            options = options,
                            player = player,
                            firstFocus = firstFocus,
                            subtitleStyle = subtitleStyle,
                            subtitleDelayMillis = subtitleDelayMillis,
                            onSubtitleDelay = onSubtitleDelay,
                            onTiming = { onOpenEditor(PlayerEditor.TIMING) },
                            onStyle = { onOpenEditor(PlayerEditor.STYLE) },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    } else {
                        PlayerTrackList(
                            options = options,
                            trackType = C.TRACK_TYPE_TEXT,
                            player = player,
                            includeAutomatic = false,
                            automaticSelected = false,
                            firstFocus = firstFocus,
                            compactRows = true,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        MobileSubtitleToolbar(
                            subtitleStyle = subtitleStyle,
                            subtitleDelayMillis = subtitleDelayMillis,
                            onTiming = { onOpenEditor(PlayerEditor.TIMING) },
                            onStyle = { onOpenEditor(PlayerEditor.STYLE) },
                        )
                    }
                }
                PlayerPanel.SPEED -> {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        itemsIndexed(PLAYBACK_SPEEDS, key = { _, item -> item }) { index, speed ->
                            PlayerChoiceRow(
                                title = if (speed == 1f) "Normal" else "${speed}×",
                                supportingText = null,
                                selected = snapshot.speed == speed,
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            ) {
                                player?.setPlaybackSpeed(speed)
                            }
                        }
                    }
                }
                PlayerPanel.INFO -> Unit
            }
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
            label = "Sync · ${formatSignedDelay(subtitleDelayMillis)}",
            onClick = onTiming,
            modifier = Modifier.weight(1f),
        )
        PlayerTextButton(
            label = "Style · ${subtitleStyle.sizePercent}%",
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

    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerRailColumn(
            title = stringResource(R.string.player_subtitle_languages),
            modifier = Modifier.width(184.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 286.dp),
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
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        browsedLanguageKey = item.key
                        if (isOff) player?.clearTrackOverride(C.TRACK_TYPE_TEXT, disabled = true)
                    }
                }
            }
        }

        PlayerRailColumn(
            title = stringResource(R.string.player_subtitle_tracks),
            modifier = Modifier.width(300.dp),
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
                    modifier = Modifier.fillMaxWidth().heightIn(max = 286.dp),
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
            modifier = Modifier.width(300.dp),
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
    compactRows: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (includeAutomatic) {
            item("automatic") {
                PlayerChoiceRow(
                    title = "Automatic",
                    supportingText = options.firstOrNull(TrackOption::selected)?.let { selected ->
                        listOfNotNull(selected.title, selected.supportingText).joinToString(" · ")
                    } ?: "Best supported track",
                    selected = automaticSelected,
                    compact = compactRows,
                    modifier = Modifier.focusRequester(firstFocus),
                ) {
                    player?.clearTrackOverride(trackType, disabled = false)
                }
            }
        } else {
            item("off") {
                PlayerChoiceRow(
                    title = "Off",
                    supportingText = "No subtitles",
                    selected = options.none(TrackOption::selected),
                    compact = compactRows,
                    modifier = Modifier.focusRequester(firstFocus),
                ) {
                    player?.clearTrackOverride(trackType, disabled = true)
                }
            }
        }
        if (options.isEmpty()) {
            item("empty") {
                Text(
                    text = if (trackType == C.TRACK_TYPE_AUDIO) {
                        "No selectable audio tracks were reported by this source."
                    } else {
                        "No subtitle tracks were reported by this source."
                    },
                    color = PlayerOnSurfaceMuted,
                    fontFamily = PlayerFont,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
        }
        itemsIndexed(options, key = { _, item -> item.id }) { _, option ->
            PlayerChoiceRow(
                title = option.title,
                supportingText = option.supportingText,
                selected = option.selected && (!includeAutomatic || !automaticSelected),
                enabled = option.supported,
                compact = compactRows,
            ) {
                player?.selectTrack(trackType, option)
            }
        }
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
            text = "Audio timing",
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
        )
        Text(
            text = "Saved for the current output route",
            color = PlayerOnSurfaceMuted,
            fontFamily = PlayerFont,
            fontSize = 12.sp,
        )
        PlayerStepper(
            value = if (delayMillis == 0L) "0 ms" else "%+d ms".format(delayMillis),
            decreaseLabel = "Decrease audio delay",
            increaseLabel = "Increase audio delay",
            onDecrease = { onDelay(stepAudioDelay(delayMillis, -1)) },
            onReset = { onDelay(0L) },
            onIncrease = { onDelay(stepAudioDelay(delayMillis, 1)) },
        )
        Text(
            text = "Negative values play audio earlier; positive values play it later.",
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
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PlayerSurface.copy(alpha = 0.56f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlayerChoiceRow(
            title = "Timing and sync",
            supportingText = "Manual delay or sync by spoken line · ${formatSignedDelay(subtitleDelayMillis)}",
            selected = subtitleDelayMillis != 0L,
            onClick = onTiming,
        )
        PlayerChoiceRow(
            title = "Appearance",
            supportingText = "${subtitleStyle.sizePercent}% · " + if (subtitleStyle.preserveEmbeddedStyles) {
                "Original styling"
            } else {
                "Custom style"
            },
            selected = false,
            onClick = onStyle,
        )
        PlayerDelayRow(
            label = "Quick sync",
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
    onClick: () -> Unit,
) {
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
            .clickable(enabled = enabled, onClick = onClick)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            supportingText?.let {
                Text(
                    text = it,
                    color = if (focused) PlayerFocusedContent.copy(alpha = 0.74f) else PlayerOnSurfaceMuted,
                    fontFamily = PlayerFont,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = if (focused) PlayerFocusedContent else PlayerPrimary,
            )
        }
    }
}

@Composable
private fun PlayerStreamInfoPanel(
    streamInfo: String?,
    onClose: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .clickable(onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(560.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PlayerBackground)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Stream info",
                color = PlayerOnSurface,
                fontFamily = PlayerFont,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
            )
            Text(
                text = streamInfo ?: "Stream details are not available yet.",
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                fontSize = 15.sp,
            )
            PlayerTextButton(label = "Close", onClick = onClose, modifier = Modifier.focusRequester(closeFocus))
        }
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
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    LaunchedEffect(capturedPositionMillis, loadingCues, visibleCues.size) {
        if (capturedPositionMillis != null && !loadingCues && visibleCues.isNotEmpty()) {
            delay(80)
            initialFocus.requestFocus()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .clickable(onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(if (isTelevision) Alignment.Center else Alignment.BottomCenter)
                .padding(
                    horizontal = if (isTelevision) 58.dp else 16.dp,
                    vertical = if (isTelevision) 40.dp else 16.dp,
                )
                .then(if (isTelevision) Modifier.width(720.dp) else Modifier.fillMaxWidth())
                .heightIn(max = if (isTelevision) 430.dp else 560.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PlayerBackground)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .clickable(enabled = false) { }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Subtitle timing",
                        color = PlayerOnSurface,
                        fontFamily = PlayerFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                    )
                    Text(
                        text = "Changes apply immediately without restarting playback",
                        color = PlayerOnSurfaceMuted,
                        fontFamily = PlayerFont,
                        fontSize = 13.sp,
                    )
                }
                PlayerTextButton(label = "Done", onClick = onClose)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(PlayerSurface.copy(alpha = 0.56f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlayerDelayRow(
                    label = "Manual delay",
                    valueText = formatSignedDelay(subtitleDelayMillis),
                    onStep = { steps -> onSubtitleDelay(stepSubtitleDelay(subtitleDelayMillis, steps)) },
                    onReset = { onSubtitleDelay(0L) },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerTextButton(
                            label = "−5s",
                            onClick = { onSubtitleDelay(clampSubtitleDelayMillis(subtitleDelayMillis - 5_000L)) },
                            modifier = Modifier.weight(1f),
                        )
                        PlayerTextButton(
                            label = "−1s",
                            onClick = { onSubtitleDelay(clampSubtitleDelayMillis(subtitleDelayMillis - 1_000L)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerTextButton(
                            label = "+1s",
                            onClick = { onSubtitleDelay(clampSubtitleDelayMillis(subtitleDelayMillis + 1_000L)) },
                            modifier = Modifier.weight(1f),
                        )
                        PlayerTextButton(
                            label = "+5s",
                            onClick = { onSubtitleDelay(clampSubtitleDelayMillis(subtitleDelayMillis + 5_000L)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    "Negative shows subtitles earlier; positive shows them later.",
                    color = PlayerOnSurfaceMuted,
                    fontFamily = PlayerFont,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }

            if (capturedPositionMillis == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "When you hear a spoken line, press Sync now. Then choose that line below.",
                        color = PlayerOnSurfaceMuted,
                        fontFamily = PlayerFont,
                        fontSize = 14.sp,
                    )
                    PlayerTextButton(
                        label = "Sync now",
                        onClick = {
                            capturedPositionMillis = currentPositionMillis
                            loadingCues = true
                            onLoadSidecarCues { loaded ->
                                cues = loaded
                                loadingCues = false
                            }
                        },
                        modifier = Modifier.focusRequester(initialFocus),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Captured at ${capturedPositionMillis!!.asPlaybackTime()} · choose the line you heard",
                        color = PlayerOnSurface,
                        fontFamily = PlayerFont,
                        fontSize = 14.sp,
                    )
                    PlayerTextButton(label = "Capture again", onClick = { capturedPositionMillis = null })
                }
                when {
                    loadingCues -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = PlayerPrimary, strokeWidth = 2.dp)
                    }
                    cues.isEmpty() -> {
                        Text(
                            text = "No text sidecar available; use manual delay.",
                            color = PlayerOnSurfaceMuted,
                            fontFamily = PlayerFont,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(visibleCues, key = { _, item -> "${item.startMillis}:${item.endMillis}" }) { index, cue ->
                            PlayerChoiceRow(
                                title = cue.text.replace("\n", " ").replace(Regex("\\{\\\\[^{}]*}"), "").take(96),
                                supportingText = cue.startMillis.asPlaybackTime(),
                                selected = false,
                                modifier = if (index == visibleCues.size / 2) {
                                    Modifier.focusRequester(initialFocus)
                                } else {
                                    Modifier
                                },
                            ) {
                                onApplySyncByLine(capturedPositionMillis!!, cue.startMillis)
                            }
                        }
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
                    PlayerTextButton(label = "Reset", onClick = onReset)
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
                PlayerTextButton(label = "Reset", onClick = onReset)
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
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .clickable(onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(if (isTelevision) Alignment.Center else Alignment.BottomCenter)
                .padding(
                    horizontal = if (isTelevision) 58.dp else 16.dp,
                    vertical = if (isTelevision) 40.dp else 16.dp,
                )
                .then(if (isTelevision) Modifier.width(844.dp) else Modifier.fillMaxWidth())
                .heightIn(max = if (isTelevision) 460.dp else 600.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PlayerBackground)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .clickable(enabled = false) { }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Subtitle appearance",
                        color = PlayerOnSurface,
                        fontFamily = PlayerFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                    )
                    Text(
                        text = "Every change is previewed and saved to this profile",
                        color = PlayerOnSurfaceMuted,
                        fontFamily = PlayerFont,
                        fontSize = 13.sp,
                    )
                }
                PlayerTextButton(
                    label = "Reset all",
                    onClick = { onStyleChange(SubtitleStyle()) },
                    modifier = Modifier.focusRequester(firstFocus),
                )
                PlayerTextButton(label = "Done", onClick = onClose)
            }
            PlayerSubtitlePreview(style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerTextButton(
                    label = "Default",
                    onClick = { onStyleChange(SubtitleStyle()) },
                    modifier = Modifier.weight(1f),
                )
                PlayerTextButton(
                    label = "Cinema",
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
                    label = "Accessible",
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
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item("size") {
                    PlayerDelayRow(
                        label = "Text size (${style.sizePercent}%)",
                        valueText = "50 - 300",
                        onStep = { steps ->
                            onStyleChange(style.copy(sizePercent = SubtitleStylePolicy.clampSizePercent(style.sizePercent + steps * 10)))
                        },
                        onReset = { onStyleChange(style.copy(sizePercent = 100)) },
                    )
                }
                item("position") {
                    PlayerDelayRow(
                        label = "Position",
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
                        label = "Text opacity",
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
                        title = "Bold",
                        supportingText = null,
                        selected = style.bold,
                    ) {
                        onStyleChange(style.copy(bold = !style.bold))
                    }
                }
                item("text-color") {
                    SubtitleColorRow(
                        label = "Text color",
                        selected = style.textColor,
                        colors = SUBTITLE_TEXT_COLORS,
                    ) { onStyleChange(style.copy(textColor = it)) }
                }
                item("background-opacity") {
                    PlayerDelayRow(
                        label = "Background",
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
                        title = "Outline",
                        supportingText = if (style.outlineEnabled) "Enabled · ${style.outlineWidthDp} dp" else "Disabled",
                        selected = style.outlineEnabled,
                    ) {
                        onStyleChange(style.copy(outlineEnabled = !style.outlineEnabled))
                    }
                }
                if (style.outlineEnabled) {
                    item("outline-width") {
                        PlayerDelayRow(
                            label = "Outline width",
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
                            label = "Outline color",
                            selected = style.outlineColor,
                            colors = SUBTITLE_OUTLINE_COLORS,
                        ) { onStyleChange(style.copy(outlineColor = it)) }
                    }
                }
                item("preserve") {
                    PlayerChoiceRow(
                        title = "Keep original styling",
                        supportingText = "Embedded ASS/SSA look",
                        selected = style.preserveEmbeddedStyles,
                    ) {
                        onStyleChange(style.copy(preserveEmbeddedStyles = !style.preserveEmbeddedStyles))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSubtitlePreview(style: SubtitleStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(bottom = 14.dp, start = 16.dp, end = 16.dp),
        ) {
            Text(
                text = "This is how subtitles will look",
                color = Color(style.textColor).copy(alpha = SubtitleStylePolicy.clampOpacity(style.textOpacity)),
                fontFamily = PlayerFont,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontSize = (17f * style.sizePercent / 100f).coerceIn(11f, 34f).sp,
                lineHeight = (22f * style.sizePercent / 100f).coerceIn(14f, 40f).sp,
                modifier = Modifier
                    .background(
                        Color(style.backgroundColor).copy(
                            alpha = SubtitleStylePolicy.clampOpacity(style.backgroundOpacity),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Text(
                text = "${style.sizePercent}% · ${if (style.preserveEmbeddedStyles) "original styling kept" else "custom style"}",
                color = PlayerOnSurfaceMuted,
                fontFamily = PlayerFont,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SubtitleColorRow(
    label: String,
    selected: Long,
    colors: List<Long>,
    onSelected: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = PlayerOnSurface,
            fontFamily = PlayerFont,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        colors.forEach { value ->
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
                    .clickable { onSelected(value) }
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
