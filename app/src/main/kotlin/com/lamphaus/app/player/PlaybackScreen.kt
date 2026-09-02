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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
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
internal val PlayerFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
)

private enum class PlayerPanel { AUDIO, SUBTITLES, SPEED }

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
    val group: TrackGroup,
    val trackIndex: Int,
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
) {
    var snapshot by remember(player) { mutableStateOf(player?.snapshot() ?: PlayerSnapshot()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
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
        controlsFocusVersion++
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

    BackHandler {
        when {
            panel != null -> closePanel()
            nextEpisodeCardVisible -> onDismissNextEpisodeCard()
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
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setUserDefaultTextSize()
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> onPlayerViewLayout(view) }
                }
            },
            update = { view ->
                view.player = player
                view.keepScreenOn = player?.isPlaying == true
                onPlayerViewLayout(view)
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (snapshot.buffering && snapshot.errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(if (isTelevision) 52.dp else 42.dp),
                color = PlayerPrimary,
                strokeWidth = 3.dp,
            )
        }

        if (controlsVisible || panel != null || snapshot.errorMessage != null) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.20f),
                        0.45f to Color.Transparent,
                        1f to PlayerBackground.copy(alpha = 0.96f),
                    ),
                ),
            )
        }

        if (controlsVisible && snapshot.errorMessage == null) {
            PlayerControls(
                request = request,
                snapshot = snapshot,
                isTelevision = isTelevision,
                onInteraction = ::revealControls,
                onTogglePlay = { if (snapshot.playing) player?.pause() else player?.play() },
                onReplay = { player?.seekTo(0); player?.play() },
                onSeekBack = { player?.seekBack() },
                onSeekForward = { player?.seekForward() },
                canPlayNext = settings.nextEpisodeEnabled && nextEpisode != null && nextEpisode.hasAired(),
                nextEpisodeLoading = nextEpisodeLoading,
                onNextEpisode = onNextEpisode,
                focusPanel = returnFocusPanel,
                focusRequestVersion = controlsFocusVersion,
                onPanel = {
                    returnFocusPanel = it
                    panel = it
                    revealControls()
                },
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
            visibleSegment != null ||
            (nextEpisodeReady && isTelevision) ||
            (nextEpisodeMessage != null && !nextEpisodeCardVisible)
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
            visible = nextEpisodeCardVisible,
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

        snapshot.errorMessage?.let { message ->
            PlayerErrorPanel(
                message = message,
                onRetry = { player?.prepare(); player?.play() },
                onOpenExternally = onOpenExternally,
            )
        }

        panel?.let { activePanel ->
            PlayerSettingsPanel(
                panel = activePanel,
                player = player,
                snapshot = snapshot,
                isTelevision = isTelevision,
                onClose = ::closePanel,
            )
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
    canPlayNext: Boolean,
    nextEpisodeLoading: Boolean,
    onNextEpisode: () -> Unit,
    focusPanel: PlayerPanel?,
    focusRequestVersion: Long,
    onPanel: (PlayerPanel) -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val ended = snapshot.durationMillis > 0 && snapshot.positionMillis >= snapshot.durationMillis - 1_000
    LaunchedEffect(focusRequestVersion) {
        when (focusPanel) {
            PlayerPanel.AUDIO -> audioFocus
            PlayerPanel.SUBTITLES -> subtitlesFocus
            PlayerPanel.SPEED -> speedFocus
            null -> playFocus
        }.requestFocus()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding = if (isTelevision) 56.dp else 20.dp
        val bottomPadding = if (isTelevision) 32.dp else 20.dp
        val wideLayout = maxWidth > 720.dp
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(if (isTelevision) 18.dp else 12.dp),
        ) {
            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    PlayerTitle(request, isTelevision, Modifier.weight(1f))
                    PlayerSettingsActions(onPanel, audioFocus, subtitlesFocus, speedFocus)
                }
            } else {
                PlayerTitle(request, isTelevision)
                PlayerSettingsActions(onPanel, audioFocus, subtitlesFocus, speedFocus)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTelevision) 12.dp else 8.dp),
            ) {
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
                    modifier = Modifier.focusRequester(playFocus),
                    onClick = {
                        if (ended) onReplay() else onTogglePlay()
                        onInteraction()
                    },
                )
                PlayerActionButton(Icons.Rounded.Replay10, "Rewind 10 seconds") {
                    onSeekBack(); onInteraction()
                }
                PlayerActionButton(Icons.Rounded.FastForward, "Forward 10 seconds") {
                    onSeekForward(); onInteraction()
                }
                if (canPlayNext) {
                    PlayerActionButton(Icons.Rounded.SkipNext, "Next episode") {
                        if (!nextEpisodeLoading) onNextEpisode()
                        onInteraction()
                    }
                }
                if (wideLayout) {
                    PlayerTime(snapshot.positionMillis)
                    PlayerProgress(
                        positionMillis = snapshot.positionMillis,
                        bufferedPositionMillis = snapshot.bufferedPositionMillis,
                        durationMillis = snapshot.durationMillis,
                        modifier = Modifier.weight(1f),
                    )
                    PlayerTime(snapshot.durationMillis)
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (!wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlayerTime(snapshot.positionMillis)
                    PlayerProgress(
                        positionMillis = snapshot.positionMillis,
                        bufferedPositionMillis = snapshot.bufferedPositionMillis,
                        durationMillis = snapshot.durationMillis,
                        modifier = Modifier.weight(1f),
                    )
                    PlayerTime(snapshot.durationMillis)
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
private fun PlayerSettingsActions(
    onPanel: (PlayerPanel) -> Unit,
    audioFocus: FocusRequester,
    subtitlesFocus: FocusRequester,
    speedFocus: FocusRequester,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerActionButton(
            Icons.AutoMirrored.Rounded.VolumeUp,
            "Audio tracks",
            Modifier.focusRequester(audioFocus),
        ) {
            onPanel(PlayerPanel.AUDIO)
        }
        PlayerActionButton(
            Icons.Rounded.ClosedCaption,
            "Subtitles",
            Modifier.focusRequester(subtitlesFocus),
        ) {
            onPanel(PlayerPanel.SUBTITLES)
        }
        PlayerActionButton(
            Icons.Rounded.Settings,
            "Playback speed",
            Modifier.focusRequester(speedFocus),
        ) {
            onPanel(PlayerPanel.SPEED)
        }
    }
}

@Composable
private fun PlayerActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) PlayerFocused else PlayerSurface.copy(alpha = 0.86f))
            .border(if (focused) 3.dp else 0.dp, if (focused) PlayerPrimary else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) PlayerFocusedContent else PlayerOnSurface,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun PlayerProgress(
    positionMillis: Long,
    bufferedPositionMillis: Long,
    durationMillis: Long,
    modifier: Modifier,
) {
    val played = if (durationMillis > 0) positionMillis.toFloat() / durationMillis else 0f
    val buffered = if (durationMillis > 0) bufferedPositionMillis.toFloat() / durationMillis else 0f
    Canvas(modifier.height(12.dp)) {
        val centerY = size.height / 2
        val barHeight = 4.dp.toPx()
        val radius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        val origin = androidx.compose.ui.geometry.Offset(0f, centerY - barHeight / 2)
        drawRoundRect(Color.White.copy(alpha = 0.25f), origin, androidx.compose.ui.geometry.Size(size.width, barHeight), radius)
        drawRoundRect(
            Color.White.copy(alpha = 0.5f),
            origin,
            androidx.compose.ui.geometry.Size(size.width * buffered.coerceIn(0f, 1f), barHeight),
            radius,
        )
        drawRoundRect(
            PlayerPrimary,
            origin,
            androidx.compose.ui.geometry.Size(size.width * played.coerceIn(0f, 1f), barHeight),
            radius,
        )
    }
}

@Composable
private fun PlayerTime(milliseconds: Long) {
    Text(
        text = milliseconds.asPlaybackTime(),
        color = PlayerOnSurfaceMuted,
        fontFamily = PlayerFont,
        fontSize = 13.sp,
    )
}

@Composable
private fun PlayerSettingsPanel(
    panel: PlayerPanel,
    player: Player?,
    snapshot: PlayerSnapshot,
    isTelevision: Boolean,
    onClose: () -> Unit,
) {
    val firstFocus = remember(panel) { FocusRequester() }
    val title = when (panel) {
        PlayerPanel.AUDIO -> "Audio"
        PlayerPanel.SUBTITLES -> "Subtitles"
        PlayerPanel.SPEED -> "Playback speed"
    }
    val options = when (panel) {
        PlayerPanel.AUDIO -> snapshot.tracks.options(C.TRACK_TYPE_AUDIO)
        PlayerPanel.SUBTITLES -> snapshot.tracks.options(C.TRACK_TYPE_TEXT)
        PlayerPanel.SPEED -> emptyList()
    }
    val audioUsesAutoSelection = player?.hasOverride(C.TRACK_TYPE_AUDIO) == false
    LaunchedEffect(panel, options.size) { firstFocus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .clickable(onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(if (isTelevision) Alignment.CenterEnd else Alignment.BottomCenter)
                .padding(if (isTelevision) 48.dp else 16.dp)
                .fillMaxWidth(if (isTelevision) 0.38f else 1f)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PlayerBackground)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .clickable(enabled = false) { }
                .padding(vertical = 18.dp),
        ) {
            Text(
                text = title,
                color = PlayerOnSurface,
                fontFamily = PlayerFont,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = if (isTelevision) 480.dp else 380.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                when (panel) {
                    PlayerPanel.AUDIO -> {
                        item("auto") {
                            PlayerChoiceRow(
                                title = "Auto",
                                supportingText = options.firstOrNull(TrackOption::selected)?.let { selected ->
                                    listOfNotNull(selected.title, selected.supportingText).joinToString(" · ")
                                } ?: "Best supported track",
                                selected = audioUsesAutoSelection,
                                modifier = Modifier.focusRequester(firstFocus),
                            ) {
                                player?.clearTrackOverride(C.TRACK_TYPE_AUDIO, disabled = false)
                                onClose()
                            }
                        }
                        itemsIndexed(options, key = { _, item -> item.id }) { _, option ->
                            PlayerChoiceRow(
                                option.title,
                                option.supportingText,
                                option.selected && !audioUsesAutoSelection,
                                enabled = option.supported,
                            ) {
                                player?.selectTrack(C.TRACK_TYPE_AUDIO, option)
                                onClose()
                            }
                        }
                    }
                    PlayerPanel.SUBTITLES -> {
                        item("off") {
                            PlayerChoiceRow(
                                title = "Off",
                                supportingText = null,
                                selected = options.none(TrackOption::selected),
                                modifier = Modifier.focusRequester(firstFocus),
                            ) {
                                player?.clearTrackOverride(C.TRACK_TYPE_TEXT, disabled = true)
                                onClose()
                            }
                        }
                        itemsIndexed(options, key = { _, item -> item.id }) { _, option ->
                            PlayerChoiceRow(
                                option.title,
                                option.supportingText,
                                option.selected,
                                enabled = option.supported,
                            ) {
                                player?.selectTrack(C.TRACK_TYPE_TEXT, option)
                                onClose()
                            }
                        }
                    }
                    PlayerPanel.SPEED -> {
                        itemsIndexed(PLAYBACK_SPEEDS, key = { _, item -> item }) { index, speed ->
                            PlayerChoiceRow(
                                title = if (speed == 1f) "Normal" else "${speed}×",
                                supportingText = null,
                                selected = snapshot.speed == speed,
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            ) {
                                player?.setPlaybackSpeed(speed)
                                onClose()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerChoiceRow(
    title: String,
    supportingText: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) PlayerFocused else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
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
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) PlayerFocused else PlayerSurface)
            .border(if (focused) 3.dp else 0.dp, PlayerPrimary, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
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
    if (trackType == C.TRACK_TYPE_AUDIO && channelCount > 0) {
        add(when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "$channelCount channels"
        })
    }
    sampleMimeType?.substringAfter('/')?.uppercase(Locale.ROOT)?.let(::add)
}.joinToString(" · ").ifBlank { null }

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
